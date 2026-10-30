#!/usr/bin/env python3
"""Anthropic Message Batches API client shared by claude_audit.py and claude_review.py.

Brian Fisher, 2026-10-30: send the Claude audits and the PR reviews through the Message Batches API, which
bills input and output tokens (cache writes and reads too) at 50% of the standard price (BATCH_FACTOR and the
price table in claude_review.py). Kept after the promotional credit expires (2026-11-08).

A batch is created with POST /v1/messages/batches ({"requests": [{"custom_id", "params"}]}, params = the same
body as /v1/messages), polled with GET /v1/messages/batches/{id} until processing_status is "ended", and its
results are read from results_url (JSONL, one line per request, in any order: {"custom_id", "result": {"type":
"succeeded" | "errored" | "canceled" | "expired", ...}}). Most batches end within minutes to an hour; the API
expires a request that has not been processed 24 hours after creation (not billed). Errored, canceled and
expired requests are not billed.

Nothing here retries in a loop: a create that failed with a quick error (rate limit, overloaded, 5xx: no batch
was made) is tried once more; a poll that fails is tried again at the next poll; the caller decides what to do
when the time limit is reached. Standard library only. Tests: tests/unit/test_claude_batch.py (fake HTTP layer).
"""

import http.client
import json
import re
import time
import urllib.error
import urllib.request

BATCH_URL = "https://api.anthropic.com/v1/messages/batches"
API_VERSION = "2023-06-01"
CUSTOM_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
RETRY_CODES = (429, 500, 502, 503, 529)


class BatchError(Exception):
    """The API refused or could not be reached; the message is safe for a job summary or a PR comment."""


def send(method, url, api_key, body=None, timeout=120):
    """(HTTP status, response text). Raises OSError / http.client.HTTPException on network errors. The tests
    replace this function."""
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"x-api-key": api_key, "anthropic-version": API_VERSION,
                                          "content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def describe_error(status, text):
    try:
        err = json.loads(text).get("error", {})
        return f"HTTP {status} {err.get('type', '')}: {err.get('message', text[:300])}"
    except (ValueError, AttributeError):
        return f"HTTP {status}: {text[:300]}"


def submit(api_key, requests, sleep=time.sleep):
    """Create one batch from [(custom_id, params)]; returns the batch object (its "id" is the batch id).
    Raises BatchError. A quick refusal (429, 5xx) is tried once more after 20 s; a network error is not
    (the batch may have been created)."""
    for custom_id, _ in requests:
        if not CUSTOM_ID.match(custom_id):
            raise BatchError(f"Invalid custom_id {custom_id!r}")
    body = {"requests": [{"custom_id": c, "params": p} for c, p in requests]}
    for attempt in range(2):
        try:
            status, text = send("POST", BATCH_URL, api_key, body)
        except (OSError, http.client.HTTPException) as e:
            raise BatchError(f"Network error while creating the batch: {e!r}") from e
        if status == 200:
            batch = json.loads(text)
            if not batch.get("id"):
                raise BatchError("The API answered without a batch id")
            return batch
        error = describe_error(status, text)
        if status not in RETRY_CODES or attempt == 1:
            raise BatchError(error)
        sleep(20)
    raise BatchError("unreachable")


def retrieve(api_key, batch_id):
    """The batch object; raises BatchError (HTTP error) or OSError (network)."""
    status, text = send("GET", f"{BATCH_URL}/{batch_id}", api_key)
    if status != 200:
        raise BatchError(describe_error(status, text))
    return json.loads(text)


def poll(api_key, batch_id, limit_s, interval_s=60, sleep=time.sleep, clock=time.monotonic, log=print):
    """Wait until the batch has ended or limit_s seconds have passed. Returns (batch object or None, ended?).
    A failed poll (network, 5xx) is logged and tried again at the next interval; a 404 (unknown batch) raises."""
    deadline = clock() + limit_s
    batch = None
    while True:
        try:
            batch = retrieve(api_key, batch_id)
            if batch.get("processing_status") == "ended":
                return batch, True
        except BatchError as e:
            if str(e).startswith("HTTP 404") or str(e).startswith("HTTP 401") or str(e).startswith("HTTP 403"):
                raise
            log(f"::warning::Polling batch {batch_id} failed ({e}); trying again")
        except (OSError, http.client.HTTPException, ValueError) as e:
            log(f"::warning::Polling batch {batch_id} failed ({e!r}); trying again")
        left = deadline - clock()
        if left <= 0:
            return batch, False
        sleep(min(interval_s, left))


def results(api_key, batch):
    """{custom_id: result} from the batch's results_url (JSONL). Raises BatchError or OSError."""
    url = batch.get("results_url")
    if not url:
        raise BatchError("The batch has ended but has no results_url")
    status, text = send("GET", url, api_key, timeout=300)
    if status != 200:
        raise BatchError(describe_error(status, text))
    out = {}
    for line in text.splitlines():
        if line.strip():
            item = json.loads(line)
            out[item.get("custom_id")] = item.get("result") or {}
    return out


def outcome(result):
    """(message, error text) of one result line: the Messages API answer when it succeeded, else why not."""
    kind = (result or {}).get("type")
    if kind == "succeeded":
        return result.get("message") or {}, None
    if kind == "errored":
        err = ((result.get("error") or {}).get("error")) or {}
        return None, f"the request errored ({err.get('type') or '?'}: {err.get('message') or 'no message'}; not billed)"
    if kind in ("expired", "canceled"):
        return None, f"the request {kind} (not processed, not billed)"
    return None, f"no result for this request (type {kind!r})"


def cancel(api_key, batch_id):
    """Ask the API to cancel a batch (requests not yet processed are not billed). Never raises."""
    try:
        status, text = send("POST", f"{BATCH_URL}/{batch_id}/cancel", api_key)
        return status == 200
    except (OSError, http.client.HTTPException):
        return False

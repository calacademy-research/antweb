#!/usr/bin/env python3
"""The Message Batches API client (.github/scripts/claude_batch.py) and its use by the Claude audit and the Claude
review (2026-10-30, Brian Fisher: both go through the Batch API at 50% of the standard price). No network: the HTTP
layer, the clock and sleep are replaced by fakes.

Checks: submit, then poll (in_progress, then ended), then the results (succeeded, errored, expired); a quick refusal
is tried once more, a network error on create is not; a failed poll is tried again; the time limit; the cost at the
batch price (0.5 x standard); the audit's budget guard runs before anything is sent; the audit's ledger line for a
success, an errored request and a timeout (batch canceled, upper estimate); the review collects a batch named in its
comment for the same head commit instead of sending a new one, and writes "still being processed" when the time
limit is reached.
Run: python3 tests/unit/test_claude_batch.py
"""
import io
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("REPO_ROOT", str(ROOT))
sys.path.insert(0, str(ROOT / ".github" / "scripts"))
import claude_audit as a  # noqa: E402
import claude_batch as cb  # noqa: E402
import claude_review as cr  # noqa: E402

a.ROOT = ROOT
URL = cb.BATCH_URL
BID = "msgbatch_01TestBatch"


def close(x, y):
    return abs(x - y) < 1e-9


class FakeAPI:
    """Answers the batch endpoints from a script: the poll answers in order (the last one repeats)."""

    def __init__(self, polls, results=None, create=None):
        self.polls, self.results, self.create = list(polls), results or [], list(create or [(200, {"id": BID})])
        self.calls = []

    def __call__(self, method, url, api_key, body=None, timeout=120):
        self.calls.append((method, url, body))
        assert api_key == "test-key", "every call sends the API key"
        if method == "POST" and url == URL:
            status, answer = self.create.pop(0) if len(self.create) > 1 else self.create[0]
            if isinstance(answer, Exception):
                raise answer
            return status, json.dumps(answer)
        if method == "GET" and url == f"{URL}/{BID}":
            status, answer = self.polls.pop(0) if len(self.polls) > 1 else self.polls[0]
            if isinstance(answer, Exception):
                raise answer
            return status, json.dumps(answer)
        if method == "GET" and url == f"{URL}/{BID}/results":
            return 200, "\n".join(json.dumps(r) for r in self.results) + "\n"
        if method == "POST" and url == f"{URL}/{BID}/cancel":
            return 200, json.dumps({"id": BID, "processing_status": "canceling"})
        raise AssertionError(f"unexpected call {method} {url}")

    def count(self, method, url):
        return sum(1 for m, u, _ in self.calls if m == method and u == url)


class Clock:
    def __init__(self):
        self.t = 0.0
        self.sleeps = []

    def __call__(self):
        return self.t

    def sleep(self, s):
        self.sleeps.append(s)
        self.t += s


RUNNING = (200, {"id": BID, "processing_status": "in_progress", "request_counts": {"processing": 1}})
ENDED = (200, {"id": BID, "processing_status": "ended", "results_url": f"{URL}/{BID}/results",
               "request_counts": {"succeeded": 1}})
USAGE = {"input_tokens": 100_000, "output_tokens": 20_000}
MESSAGE = {"type": "message", "model": cr.MODEL, "stop_reason": "end_turn", "usage": USAGE,
           "content": [{"type": "thinking", "thinking": "x"}, {"type": "text", "text": "No blocking issues"}]}


def ok_line(cid):
    return {"custom_id": cid, "result": {"type": "succeeded", "message": MESSAGE}}


def install(api):
    cb.send = api
    return api


real_poll = cb.poll


def fast_poll(clock):
    """cb.poll with the fake clock and sleep, whatever limit and interval the caller passes."""
    def poll(api_key, batch_id, limit_s, interval_s=60, **_):
        return real_poll(api_key, batch_id, limit_s, interval_s, sleep=clock.sleep, clock=clock, log=lambda m: None)
    return poll


# --- Prices: one table (claude_review.py), the batch at half ------------------------------------------------
assert cr.BATCH_FACTOR == 0.5
assert close(cr.cost(USAGE), 0.80) and close(cr.cost(USAGE, batch=True), 0.40)
assert close(cr.cost({"cache_read_input_tokens": 1_000_000}, batch=True), cr.PRICE_CACHE_READ / 2)
assert close(cr.upper_estimate(350_000, 32000), (100_000 * 4 + 32000 * 20) / 1e6 / 2)
assert close(cr.upper_estimate(350_000, 32000, batch=False), (100_000 * 4 + 32000 * 20) / 1e6)
params = cr.request_params("hello")
assert params["model"] == cr.MODEL and params["max_tokens"] == cr.MAX_TOKENS and params["system"] == cr.SYSTEM
assert params["messages"] == [{"role": "user", "content": "hello"}]

# --- submit, poll (in_progress, then ended), results ---------------------------------------------------------
clock = Clock()
api = install(FakeAPI([RUNNING, RUNNING, ENDED], [ok_line("review-pr1-abc")]))
batch = cb.submit("test-key", [("review-pr1-abc", params)], sleep=clock.sleep)
assert batch["id"] == BID
sent = api.calls[0][2]
assert sent == {"requests": [{"custom_id": "review-pr1-abc", "params": params}]}, sent
got, ended = cb.poll("test-key", BID, 600, 60, sleep=clock.sleep, clock=clock, log=lambda m: None)
assert ended and got["processing_status"] == "ended" and clock.sleeps == [60, 60], clock.sleeps
found = cb.results("test-key", got)
message, error = cb.outcome(found["review-pr1-abc"])
assert error is None and cr.extract_text(message) == "No blocking issues"

# Outcomes that are not billed.
m, e = cb.outcome({"type": "errored", "error": {"type": "error", "error": {"type": "invalid_request_error",
                                                                          "message": "max_tokens too large"}}})
assert m is None and "invalid_request_error" in e and "max_tokens too large" in e, e
m, e = cb.outcome({"type": "expired"})
assert m is None and "expired" in e and "not billed" in e
m, e = cb.outcome({"type": "canceled"})
assert m is None and "canceled" in e
m, e = cb.outcome(None)
assert m is None and e

# Invalid custom_id: refused before any call (the API allows ^[a-zA-Z0-9_-]{1,64}$).
api = install(FakeAPI([ENDED]))
for bad in ("has space", "x" * 65, "", "a.b"):
    try:
        cb.submit("test-key", [(bad, params)])
        raise AssertionError(f"custom_id {bad!r} accepted")
    except cb.BatchError:
        pass
assert not api.calls

# A quick refusal (529) is tried once more after 20 s; a second one gives up. 400 is not tried again.
clock = Clock()
api = install(FakeAPI([ENDED], create=[(529, {"error": {"type": "overloaded_error", "message": "busy"}}),
                                        (200, {"id": BID})]))
assert cb.submit("test-key", [("a", params)], sleep=clock.sleep)["id"] == BID and clock.sleeps == [20]
api = install(FakeAPI([ENDED], create=[(529, {"error": {"type": "overloaded_error", "message": "busy"}})]))
try:
    cb.submit("test-key", [("a", params)], sleep=lambda s: None)
    raise AssertionError("two 529s must fail")
except cb.BatchError as ex:
    assert "overloaded_error" in str(ex) and api.count("POST", URL) == 2
api = install(FakeAPI([ENDED], create=[(400, {"error": {"type": "invalid_request_error", "message": "bad"}})]))
try:
    cb.submit("test-key", [("a", params)], sleep=lambda s: None)
    raise AssertionError("400 must fail")
except cb.BatchError as ex:
    assert str(ex).startswith("HTTP 400") and api.count("POST", URL) == 1
# A create request with no answer is not sent again (the batch may exist).
api = install(FakeAPI([ENDED], create=[(0, ConnectionResetError("reset"))]))
try:
    cb.submit("test-key", [("a", params)], sleep=lambda s: None)
    raise AssertionError("network error must fail")
except cb.BatchError as ex:
    assert str(ex).startswith("Network error") and api.count("POST", URL) == 1

# A failed poll (network, 500) is tried again at the next interval; 404 raises at once.
clock = Clock()
api = install(FakeAPI([(0, OSError("down")), (500, {"error": {"type": "api_error", "message": "x"}}), ENDED]))
got, ended = cb.poll("test-key", BID, 600, 60, sleep=clock.sleep, clock=clock, log=lambda m: None)
assert ended and api.count("GET", f"{URL}/{BID}") == 3
api = install(FakeAPI([(404, {"error": {"type": "not_found_error", "message": "no batch"}})]))
try:
    cb.poll("test-key", BID, 600, 60, sleep=clock.sleep, clock=clock, log=lambda m: None)
    raise AssertionError("404 must raise")
except cb.BatchError as ex:
    assert "not_found_error" in str(ex)

# Time limit: polls every interval, the last sleep is cut to the limit, then (batch, False).
clock = Clock()
api = install(FakeAPI([RUNNING]))
got, ended = cb.poll("test-key", BID, 150, 60, sleep=clock.sleep, clock=clock, log=lambda m: None)
assert not ended and got["processing_status"] == "in_progress" and clock.sleeps == [60, 60, 30], clock.sleeps
assert api.count("GET", f"{URL}/{BID}") == 4

# --- The audit ---------------------------------------------------------------------------------------------
# The scripts print ::error:: and ::warning:: lines; kept out of the Tests job's annotations (failures go to stderr).
real_stdout, sys.stdout = sys.stdout, io.StringIO()
name, files = a.chunks()[0]
cid = "audit-" + name
real_budget_check = a.budget_check


def run_audit(api, budget=(True, ["- Estimated credit left before this run: $40.00", "- since"])):
    """main() with the fake API; returns (exit code, ledger line or None, files written, summary text)."""
    install(api)
    clock = Clock()
    a.cb.poll = fast_poll(clock)
    a.budget_check = lambda now: budget
    out = Path(tempfile.mkdtemp())
    summary = out / "summary.md"
    os.environ.update({"ANTHROPIC_API_KEY": "test-key", "AREA": name, "OUT": str(out / "o"), "GITHUB_RUN_ID": "77",
                       "GITHUB_STEP_SUMMARY": str(summary)})
    try:
        code = a.main()
        ledger = out / "o" / "ledger-line.tsv"
        return (code, ledger.read_text() if ledger.exists() else None, sorted(p.name for p in (out / "o").iterdir()),
                summary.read_text() if summary.exists() else "")
    finally:
        a.cb.poll = real_poll
        a.budget_check = real_budget_check
        shutil.rmtree(out)


# Budget guard: paused before anything is sent.
api = FakeAPI([ENDED])
code, line, written, text = run_audit(api, (False, ["- Audit paused: estimated credit left $9.00, reserve $10.00"]))
assert code == 0 and line is None and not api.calls and "Audit paused" in text, (code, api.calls)

# Success: one batch of one request, ledger at the batch price with the batch id, encrypted report.
api = FakeAPI([RUNNING, ENDED], [ok_line(cid)])
code, line, written, text = run_audit(api)
assert code == 0, text
assert api.count("POST", URL) == 1 and len(api.calls[0][2]["requests"]) == 1
assert api.calls[0][2]["requests"][0]["custom_id"] == cid
assert api.calls[0][2]["requests"][0]["params"]["max_tokens"] == 32000
assert api.calls[0][2]["requests"][0]["params"]["system"] == a.SYSTEM
f = line.rstrip("\n").split("\t")
assert f[1:3] == ["77", "audit"] and f[3] == "0.4000" and f[4] == f"{name} batch {BID}", f
assert any(w.endswith(".md.cms") for w in written) and "report.md" not in written, written
assert "50% of the standard price" in text and BID in text

# Errored request: $0 in the ledger, the run fails, nothing sent again.
api = FakeAPI([ENDED], [{"custom_id": cid, "result": {"type": "errored", "error": {"type": "error", "error": {
    "type": "api_error", "message": "internal"}}}}])
code, line, written, text = run_audit(api)
f = line.rstrip("\n").split("\t")
assert code == 1 and f[2] == "audit-failed" and f[3] == "0.0000" and BID in f[4] and "api_error" in f[4], f
assert api.count("POST", URL) == 1 and not any(w.endswith(".cms") for w in written)

# Expired: the same.
api = FakeAPI([ENDED], [{"custom_id": cid, "result": {"type": "expired"}}])
code, line, written, text = run_audit(api)
assert code == 1 and line.split("\t")[2:4] == ["audit-failed", "0.0000"], line

# Not ended within WAIT_S: canceled, upper estimate in the ledger, the run fails, nothing sent again.
api = FakeAPI([RUNNING])
code, line, written, text = run_audit(api)
f = line.rstrip("\n").split("\t")
assert code == 1 and f[2] == "audit-timeout" and float(f[3]) > 0 and BID in f[4], f
assert api.count("POST", f"{URL}/{BID}/cancel") == 1 and api.count("POST", URL) == 1
assert api.count("GET", f"{URL}/{BID}") == a.WAIT_S // a.POLL_S + 1
assert a.WAIT_S == 5 * 3600

# Refused at create (HTTP status): nothing was made, no ledger line.
api = FakeAPI([ENDED], create=[(400, {"error": {"type": "invalid_request_error", "message": "too long"}})])
code, line, written, text = run_audit(api)
assert code == 1 and line is None and "too long" in text

# --- The review --------------------------------------------------------------------------------------------
SHA = "0123456789abcdef0123456789abcdef01234567"
banner = cr.pending_banner(SHA, BID)
assert f"({SHA[:7]}).**" in banner, "the workflow's last step must see that this commit has a notice"
assert banner.startswith(cr.STALE_START) and banner.rstrip().endswith(cr.STALE_END)
comment = {"body": f"{cr.MARKER}\n{banner}\n### Claude review (earlier push)\n\nold"}
assert cr.pending_batch(comment, SHA) == BID
assert cr.pending_batch(comment, "f" * 40) is None, "a batch for another commit is not collected"
assert cr.pending_batch(None, SHA) is None and cr.pending_batch({"body": "x"}, SHA) is None
waited = cr.pending_banner(SHA, BID, waited=True)
assert "still being processed" in waited and BID in waited and cr.pending_batch({"body": waited}, SHA) == BID

banners = []
cr.set_banner = lambda repo, pr, token, text: banners.append(text)
rid = f"review-pr5-{SHA[:12]}"

# New review: one batch sent, "in progress" notice with the batch id, then the answer.
cr.find_comment = lambda repo, pr, token: None
clock = Clock()
cr.cb.poll = fast_poll(clock)
api = install(FakeAPI([RUNNING, ENDED], [ok_line(rid)]))
lines = []
msg, err, got_id = cr.run_batch("test-key", "o/r", "5", "t", SHA, "diff", lines)
assert err is None and got_id == BID and cr.extract_text(msg) == "No blocking issues"
assert api.count("POST", URL) == 1 and api.calls[0][2]["requests"][0]["custom_id"] == rid
assert len(banners) == 1 and "in progress" in banners[0] and BID in banners[0]

# The comment names a batch for this commit: collected, nothing sent.
banners.clear()
cr.find_comment = lambda repo, pr, token: comment
api = install(FakeAPI([ENDED], [ok_line(rid)]))
msg, err, got_id = cr.run_batch("test-key", "o/r", "5", "t", SHA, "diff", lines)
assert err is None and got_id == BID and api.count("POST", URL) == 0 and not banners

# Not ended within REVIEW_WAIT_S: "still being processed" with the batch id, no second request, no cancel.
cr.find_comment = lambda repo, pr, token: None
api = install(FakeAPI([RUNNING]))
msg, err, got_id = cr.run_batch("test-key", "o/r", "5", "t", SHA, "diff", lines)
assert msg is None and err == "pending" and got_id == BID
assert api.count("POST", URL) == 1 and api.count("POST", f"{URL}/{BID}/cancel") == 0
assert "still being processed" in banners[-1] and BID in banners[-1]
assert cr.REVIEW_WAIT_S == 3600

# Errored: the error comes back (the caller marks the comment), nothing sent again.
banners.clear()
api = install(FakeAPI([ENDED], [{"custom_id": rid, "result": {"type": "errored", "error": {"type": "error", "error": {
    "type": "overloaded_error", "message": "busy"}}}}]))
msg, err, got_id = cr.run_batch("test-key", "o/r", "5", "t", SHA, "diff", lines)
assert msg is None and "overloaded_error" in err and api.count("POST", URL) == 1

# review() end to end with fake GitHub calls: the answer is posted as the comment, the cost at the batch price.
posted = []
pull = {"title": "t", "body": "b", "head": {"sha": SHA, "repo": {"full_name": "o/r"}}}
cr.gh = lambda method, path, token, body=None, accept=None: (pull, "")
cr.gh_paged = lambda path, token: [{"filename": "a.java", "status": "modified", "additions": 1, "deletions": 0,
                                    "changes": 1, "patch": "@@ -1 +1 @@\n-x\n+y"}]
cr.gh_raw = lambda path, token: ("class A {}", None)
cr.find_comment = lambda repo, pr, token: None
cr.write_comment = lambda repo, pr, token, body, existing: posted.append(body)
api = install(FakeAPI([ENDED], [ok_line(rid)]))
summary_path = Path(tempfile.mkdtemp()) / "s.md"
os.environ["GITHUB_STEP_SUMMARY"] = str(summary_path)
assert cr.review("test-key", "t", "o/r", "5", SHA[:7]) == 0
assert len(posted) == 1 and posted[0].startswith(cr.MARKER) and "No blocking issues" in posted[0], posted
assert "$0.400" in summary_path.read_text() and BID in summary_path.read_text()
# A PR from a fork is refused before anything is sent.
pull["head"]["repo"]["full_name"] = "someone/fork"
api = install(FakeAPI([ENDED]))
assert cr.review("test-key", "t", "o/r", "5", SHA[:7]) == 1 and not api.calls
shutil.rmtree(summary_path.parent)
cr.cb.poll = real_poll
sys.stdout = real_stdout

print("claude batch OK: submit, poll, results, retries, time limit, 50% cost, audit ledger lines, review pick-up")

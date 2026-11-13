#!/usr/bin/env python3
"""Read-only checks of production AntWeb: the smoke run after a deploy and the morning daily-update check.

Release gate checks 9 and 10 (docs/runbook.md, "Production smoke run and morning check").
  deploy   a few public pages and the API answer: HTTP 200, no Java stack trace or error page, the text each
           page should show, and the page header shows the commit that was just deployed.
  morning  the daily update that started today (Pacific) ended and did not fail (dailyUpdate.do?format=json).

READ-ONLY, ALWAYS: this file only sends GET calls, never signs in, keeps no session state and sends no form data. Production
never gets a test that writes. tests/unit/test_smoke_read_only.py checks this file for that, and
tests/unit/test_prod_smoke_logic.py runs it against a local stand-in server.

Standard library only (urllib), so the workflows need no pip install.

Settings (environment):
  SMOKE_SITE_URL       where the HTML pages are read. Default https://www.antweb.org. The workflows use
                       http://127.0.0.1:18081, an SSH tunnel to Tomcat's local port on the production server,
                       because Cloudflare's Under Attack Mode challenges robots on the public pages.
  SMOKE_API_URL        where /v3.1/ (and, once v4 is open, /v4/) is read. Default https://www.antweb.org (Cloudflare
                       lets /v3.1/ through; /v4/ must be let through before v4 opens).
  SMOKE_EXPECT_COMMIT  full or short hash the header must show (deploy check); empty: header not compared.
  SMOKE_TODAY          YYYY-MM-DD to use as "today" in Pacific time (tests only).
  SMOKE_TIMEOUT        seconds per request, default 60.

  python3 tests/smoke/prod_smoke.py deploy
  python3 tests/smoke/prod_smoke.py morning
  python3 tests/smoke/prod_smoke.py wait --max-seconds 420    (until the home page answers after a restart)

Exit 0: every check passed or was skipped (a skip says why). Exit 1: at least one check failed.
"""
import datetime as dt
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from zoneinfo import ZoneInfo

PACIFIC = ZoneInfo("America/Los_Angeles")
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AntWebSmoke/1.0 (+https://github.com/calacademy-research/antweb)"
SITE = os.environ.get("SMOKE_SITE_URL", "https://www.antweb.org").rstrip("/")
API = os.environ.get("SMOKE_API_URL", "https://www.antweb.org").rstrip("/")
TIMEOUT = float(os.environ.get("SMOKE_TIMEOUT", "60"))

# Same pattern as the e2e suite's antweb.py (not imported: the e2e helpers can sign in).
STACK_TRACE = re.compile(r"(Exception|Error)\b.{0,300}?\bat (org\.calacademy|org\.apache|java\.|javax\.)[\w.$]+\(", re.S)
ERROR_PAGE_TEXT = "We'll be right back!"
HEADER_VERSION = re.compile(r'id="version">.*?>\s*(?:&nbsp;)*\s*v(\d+\.\d+\.\d+)\s*</a>', re.S)
HEADER_COMMIT = re.compile(r'class="aw-commit">.*?<a [^>]*>\s*([0-9a-f]{7,40})\s*</a>', re.S)

# (name, path, texts the page must contain, case-insensitive). Every one is a public page, read with GET.
PAGES = [
    ("home page", "/", ["AntWeb in numbers"]),
    ("genus list of Madagascar", "/taxonomicPage.do?rank=genus&countryName=Madagascar", ["Genera", "Madagascar"]),
    ("species page", "/description.do?genus=camponotus&species=maculatus&rank=species", ["maculatus"]),
    ("specimen page", "/specimen.do?name=casent0103190", ["casent0103190"]),
    ("specimen search", "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae"
                        "&country=Comoros&resultRank=specimen", ["Comoros"]),
    ("statistics page", "/statsPage.do", ["Statistics"]),
]


class Fail(Exception):
    """A real failure: the workflow goes red."""


class Skip(Exception):
    """The check could not be made (e.g. Cloudflare challenge, endpoint not deployed yet); says why."""


class Resp:
    def __init__(self, status, text, url, headers):
        self.status, self.text, self.url, self.headers = status, text, url, headers


def get(url, timeout=None):
    """One GET call, no session state, no body. HTTP errors come back as a Resp too."""
    req = urllib.request.Request(url, method="GET", headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    try:
        with urllib.request.urlopen(req, timeout=timeout or TIMEOUT) as r:
            return Resp(r.status, r.read().decode("utf-8", "replace"), r.geturl(), dict(r.headers))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace") if e.fp else ""
        return Resp(e.code, body, url, dict(e.headers or {}))


def is_cloudflare_challenge(r):
    h = {k.lower(): v for k, v in r.headers.items()}
    if h.get("cf-mitigated", "").lower() == "challenge":
        return True
    return r.status in (403, 429, 503) and any(t in r.text for t in ("Just a moment", "cf-chl", "challenge-platform"))


def check_page(base, name, path, markers):
    url = base + path
    try:
        r = get(url)
    except Exception as e:  # timeout, refused, DNS
        raise Fail(f"{name}: no answer from {url} ({e})")
    if is_cloudflare_challenge(r):
        raise Skip(f"{name}: not reachable through Cloudflare challenge (HTTP {r.status}); "
                   f"read it through the SSH tunnel (SMOKE_SITE_URL) to check it")
    if r.status != 200:
        raise Fail(f"{name}: HTTP {r.status} for {path}")
    if STACK_TRACE.search(r.text):
        raise Fail(f"{name}: the page shows a Java stack trace ({path})")
    if ERROR_PAGE_TEXT in r.text:
        raise Fail(f"{name}: AntWeb's error page ({path})")
    if "Server is initializing" in r.text:
        raise Fail(f"{name}: AntWeb is still initializing ({path})")
    low = r.text.lower()
    missing = [m for m in markers if m.lower() not in low]
    if missing:
        raise Fail(f"{name}: text {missing} not on the page ({path})")
    return r


def check_header(html, expect_commit):
    """The header shows vX.Y.Z and, after a deploy, the deployed commit."""
    v = HEADER_VERSION.search(html)
    c = HEADER_COMMIT.search(html)
    shown = f"v{v.group(1) if v else '?'} · {c.group(1) if c else '?'}"
    if not v:
        raise Fail("header: no version number (vX.Y.Z) in the page header")
    if expect_commit:
        if not c:
            raise Fail(f"header: shows {shown}, no commit; expected {expect_commit[:7]}")
        if not expect_commit.startswith(c.group(1)) and not c.group(1).startswith(expect_commit):
            raise Fail(f"header: shows {shown}; expected commit {expect_commit[:7]} (just deployed)")
    return shown


def api_state(api):
    """(HTTP status, text, "open" | "closed" | None) of /v3.1/: what the front door says about v4."""
    r = get(api + "/v3.1/")
    state = None
    try:
        body = json.loads(r.text)
        if isinstance(body, dict) and isinstance(body.get("v4"), dict):
            state = "open" if body["v4"].get("status") == "open" else "closed"
    except ValueError:
        pass
    return r, state


def check_api_root(api):
    r, state = api_state(api)
    if r.status != 200 or "Antweb API" not in r.text:
        raise Fail(f"API /v3.1/: HTTP {r.status}, {r.text[:120]!r}")
    if state != "open":
        return "API /v3.1/ answers"
    # v4 is open: its front door must answer and say so (v3.1's own front door stays 200 with status "ended")
    v4 = get(api + "/v4/")
    try:
        body = json.loads(v4.text)
    except ValueError:
        body = None
    if v4.status != 200 or not isinstance(body, dict) or body.get("status") != "open":
        raise Fail(f"API /v4/: /v3.1/ says v4 is open, but /v4/ answers HTTP {v4.status}, {v4.text[:120]!r}")
    return "API /v3.1/ answers (ended); /v4/ answers (open)"


def check_api_taxa(api):
    # Two states, told apart by what /v3.1/ says (one switch on the server, v4Open in etc/api-db.properties):
    # - v4 closed (2026-11-09): the data routes answer 503 with the terms (api.py apiDown); the old admin switch is
    #   refused before it reaches the server (Cloudflare rule "API: admin switch closed").
    # - v4 open: v3.1 has ended, its data routes answer 410 Gone with a signpost to v4 (api.py apiEnded).
    # Either way the answer must come from the API itself (JSON, not a proxy error). No key is sent.
    _, state = api_state(api)
    r = get(api + "/v3.1/taxa?genus=camponotus&limit=1")
    try:
        body = json.loads(r.text)
    except ValueError:
        body = None
    if state == "open":
        if r.status != 410:
            raise Fail(f"API /v3.1/taxa: HTTP {r.status} (expected 410, v3.1 has ended since v4 is open), {r.text[:120]!r}")
        if not isinstance(body, dict) or "/v4/" not in str(body.get("v4")) or "terms" not in body:
            raise Fail(f"API /v3.1/taxa: 410 without the signpost to v4: {r.text[:120]!r}")
        return "API /v3.1/taxa answers as ended (410 with the signpost to v4)"
    if r.status != 503:
        raise Fail(f"API /v3.1/taxa: HTTP {r.status} (expected 503 while the API is closed), {r.text[:120]!r}")
    if body is None:
        raise Fail(f"API /v3.1/taxa: 503 but not the API's JSON (proxy or server error?): {r.text[:120]!r}")
    if not isinstance(body, dict) or "terms" not in body:
        raise Fail(f"API /v3.1/taxa: 503 without the terms link: {r.text[:120]!r}")
    return "API /v3.1/taxa answers as closed (503 with the terms)"


# ---------- morning check (check 10) ----------

def _when(value):
    """A time from the JSON: ISO 8601, 'YYYY-MM-DD HH:MM:SS' (Pacific when no zone), or epoch seconds/ms."""
    if value in (None, "", 0):
        return None
    if isinstance(value, (int, float)):
        return dt.datetime.fromtimestamp(value / 1000 if value > 1e11 else value, PACIFIC)
    s = str(value).strip().replace("Z", "+00:00")
    try:
        t = dt.datetime.fromisoformat(s)
    except ValueError:
        return None
    return t.replace(tzinfo=PACIFIC) if t.tzinfo is None else t.astimezone(PACIFIC)


def _pick(d, *keys):
    for k in keys:
        if isinstance(d, dict) and d.get(k) not in (None, ""):
            return d[k]
    return None


def _runs(data):
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for k in ("runs", "history"):
            if isinstance(data.get(k), list):
                return data[k]
        for k in ("lastRun", "last", "latest", "run"):
            if isinstance(data.get(k), dict):
                return [data[k]]
        if _pick(data, "started", "start", "startedAt", "startTime"):
            return [data]
    return []


FAILED = {"failed", "failure", "error", "errored", "aborted", "timeout", "timed out", "killed"}
DONE = {"ok", "done", "success", "succeeded", "completed", "complete", "finished", "passed"}


def _state(x):
    return str(_pick(x, "state", "status", "result") or "").strip().lower()


def describe_steps(run):
    out = []
    for s in run.get("steps") or run.get("sets") or []:
        if not isinstance(s, dict):
            out.append(str(s))
            continue
        name = _pick(s, "name", "step", "set", "id") or "?"
        st = _state(s) or "?"
        started, ended = _when(_pick(s, "started", "start", "startedAt")), _when(_pick(s, "ended", "end", "endedAt", "finished"))
        span = f" {started:%H:%M}" if started else ""
        span += f"-{ended:%H:%M}" if ended else (" (not ended)" if started else "")
        msg = _pick(s, "message", "error", "detail")
        out.append(f"{name}: {st}{span}" + (f" ({str(msg)[:200]})" if msg else ""))
    return out


def evaluate_daily(data, today):
    """(ok, message, step lines) for the run that started on `today` (a date, Pacific)."""
    runs = [r for r in _runs(data) if isinstance(r, dict)]
    todays = [r for r in runs if (_when(_pick(r, "started", "start", "startedAt", "startTime")) or
                                  dt.datetime.min.replace(tzinfo=PACIFIC)).date() == today]
    if not todays:
        seen = sorted((_when(_pick(r, "started", "start", "startedAt", "startTime")) for r in runs
                       if _when(_pick(r, "started", "start", "startedAt", "startTime"))), reverse=True)
        last = f"last one started {seen[0]:%Y-%m-%d %H:%M} Pacific" if seen else "no runs listed"
        return False, f"no daily update started today ({today}, Pacific); {last}", []
    run = max(todays, key=lambda r: _when(_pick(r, "started", "start", "startedAt", "startTime")))
    started = _when(_pick(run, "started", "start", "startedAt", "startTime"))
    ended = _when(_pick(run, "ended", "end", "endedAt", "finished", "endTime"))
    state = _state(run)
    steps = describe_steps(run)
    failed_steps = [s for s in run.get("steps") or run.get("sets") or [] if isinstance(s, dict) and _state(s) in FAILED]
    if state in FAILED or failed_steps:
        return False, f"today's daily update (started {started:%H:%M} Pacific) failed (state {state or '?'})", steps
    if not ended or state in ("running", "started", "in progress", "active"):
        return False, f"today's daily update (started {started:%H:%M} Pacific) has not ended (state {state or '?'})", steps
    took = ended - started
    return True, (f"today's daily update ran {started:%H:%M}-{ended:%H:%M} Pacific "
                  f"({int(took.total_seconds() // 60)} min), state {state or 'ended'}"), steps


def check_daily_update(base, today):
    url = base + "/dailyUpdate.do?format=json"
    try:
        r = get(url)
    except Exception as e:
        raise Fail(f"daily update: no answer from {url} ({e})")
    if is_cloudflare_challenge(r):
        raise Skip(f"daily update: not reachable through Cloudflare challenge (HTTP {r.status})")
    if r.status == 404:
        raise Skip("daily update: dailyUpdate.do is not deployed yet (HTTP 404); nothing to check")
    if r.status != 200:
        raise Fail(f"daily update: HTTP {r.status} for {url}")
    try:
        data = json.loads(r.text)
    except ValueError:
        raise Fail(f"daily update: not JSON: {r.text[:120]!r}")
    ok, msg, steps = evaluate_daily(data, today)
    detail = msg + ("".join("\n    " + s for s in steps) if steps else "")
    if not ok:
        raise Fail(detail)
    return detail


# ---------- running ----------

def run_checks(checks):
    results = []
    for name, fn in checks:
        t = time.time()
        try:
            results.append(("pass", name, fn(), time.time() - t))
        except Skip as e:
            results.append(("skip", name, str(e), time.time() - t))
        except Fail as e:
            results.append(("FAIL", name, str(e), time.time() - t))
        except Exception as e:  # a bug in a check is a failure too, never a silent pass
            results.append(("FAIL", name, f"{type(e).__name__}: {e}", time.time() - t))
    return results


def report(title, results):
    lines = [f"## {title}", "", f"Site: {SITE}  API: {API}", ""]
    for status, name, msg, secs in results:
        print(f"{status:4}  {name} ({secs:.1f} s): {msg}")
        if status == "skip":
            print(f"::warning title=Skipped: {name}::{msg.splitlines()[0]}")
        elif status == "FAIL":
            print(f"::error title=Failed: {name}::{msg.splitlines()[0]}")
        lines.append(f"- **{status}** {name} ({secs:.1f} s): " + msg.replace("\n", "<br>"))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    failed = [r for r in results if r[0] == "FAIL"]
    print(f"{len(results) - len(failed)} of {len(results)} checks passed or skipped; {len(failed)} failed.")
    return 1 if failed else 0


def deploy_checks(expect_commit):
    checks = []
    home = {}

    def home_check():
        r = check_page(SITE, "home page", "/", PAGES[0][2])
        home["html"] = r.text
        return "HTTP 200, 'AntWeb in numbers'"

    checks.append(("home page", home_check))
    for name, path, markers in PAGES[1:]:
        checks.append((name, lambda n=name, p=path, m=markers: (check_page(SITE, n, p, m), f"HTTP 200, {m}")[1]))

    def header_check():
        if "html" not in home:
            raise Skip("header: home page not read")
        return "shows " + check_header(home["html"], expect_commit)

    checks.append(("header version and commit", header_check))
    checks.append(("API /v3.1/", lambda: check_api_root(API)))
    checks.append(("API /v3.1/taxa", lambda: check_api_taxa(API)))
    return checks


def wait_until_up(max_seconds, poll=15):
    """After a restart AntWeb needs 2 to 4 minutes; wait until the home page shows its numbers."""
    end = time.time() + max_seconds
    while True:
        try:
            r = get(SITE + "/", timeout=30)
            if r.status == 200 and "AntWeb in numbers" in r.text:
                print("AntWeb answers.")
                return 0
            print(f"not yet: HTTP {r.status}")
        except Exception as e:
            print(f"not yet: {e}")
        if time.time() > end:
            print(f"::error::AntWeb did not answer within {max_seconds} seconds after the deploy")
            return 1
        time.sleep(poll)


def main(argv):
    mode = argv[1] if len(argv) > 1 else ""
    if mode == "deploy":
        return report("Production smoke run (read-only)", run_checks(deploy_checks(os.environ.get("SMOKE_EXPECT_COMMIT", "").strip())))
    if mode == "morning":
        t = os.environ.get("SMOKE_TODAY")
        today = dt.date.fromisoformat(t) if t else dt.datetime.now(PACIFIC).date()
        return report(f"Morning check: daily update of {today}", run_checks([("daily update", lambda: check_daily_update(SITE, today))]))
    if mode == "wait":
        secs = int(argv[argv.index("--max-seconds") + 1]) if "--max-seconds" in argv else 420
        return wait_until_up(secs)
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))

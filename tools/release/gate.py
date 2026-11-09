#!/usr/bin/env python3
"""Release gate: may this pull request be merged to master (which deploys production)?

    python3 tools/release/gate.py PR_NUMBER [--nightly-checked] [--no-review "reason"]

Prints one line per check and then GATE OPEN or GATE CLOSED (exit 0 or 1). Claude runs it before every merge
and pastes the result into the pull request. The checks (decided by Brian Fisher, 2026-11-05; docs in
tools/release/README.md):

 1. tests     The "Tests" workflow (compile, unit tests) passed on the PR head.
 2. staging   The PR head was deployed to staging ("Deploy Staging" passed for that commit) and the FULL end-to-end
              run for that commit finished; a failed test is allowed only if check 3 lists it.
 3. known     Every failure of that run is in tests/e2e/known-failures.toml, and no entry there has expired.
 4. nightly   A PR that changes nightly code (NIGHTLY_PATHS) has run through a staging nightly: --nightly-checked
              says Claude read the morning compute.log for this commit and the morning tests passed.
 5. review    The latest Claude review comment is for the head commit and starts "No blocking issues";
              --no-review "reason" when there is none (for example, the review credit is used up).
 6. window    Not between 04:45 and 08:15 Pacific (the daily update).
 7. rollback  The PR text has a "Rollback:" line.
 8. daily     The daily update is not running on production now (dailyUpdate.do?format=json, the latest run's state).
              When the page cannot be read (Cloudflare challenge, no network, unexpected answer) the check fails as
              "unknown - check dailyUpdate.do by hand"; --nightly-checked (the page was read by hand) lets it pass.

Read-only: it only reads the GitHub API (through the gh command line tool), the repository, and one GET of the
production daily update status (no sign-in).
"""
import argparse
import datetime as dt
import fnmatch
import json
import os
import re
import subprocess
import sys
import tomllib
import urllib.error
import urllib.request
from pathlib import Path
from zoneinfo import ZoneInfo

REPO = "calacademy-research/antweb"
ROOT = Path(__file__).resolve().parents[2]
KNOWN_FAILURES = ROOT / "tests/e2e/known-failures.toml"
PACIFIC = ZoneInfo("America/Los_Angeles")
CLOSED_FROM, CLOSED_UNTIL = dt.time(4, 45), dt.time(8, 15)
REVIEW_MARKER = "<!-- claude-review -->"
REVIEW_BOT = "github-actions[bot]"   # the account the review workflow posts as
MAX_KNOWN_DAYS = 7
# Check 8. The JSON form needs no sign-in (DailyUpdateAction); the HTML page needs a curator login and, like every page,
# is behind Cloudflare's challenge, so the gate reads the JSON. ANTWEB_DAILY_UPDATE_URL points it elsewhere (for
# example an SSH tunnel to Tomcat's local port, http://127.0.0.1:18081/dailyUpdate.do?format=json).
DAILY_UPDATE_URL = os.environ.get("ANTWEB_DAILY_UPDATE_URL", "https://www.antweb.org/dailyUpdate.do?format=json")
DAILY_UPDATE_TIMEOUT = 20
UNKNOWN_DAILY = "unknown - check dailyUpdate.do by hand"

# Code the nightly run executes (UtilDataAction's sets, the Scheduler, the counts and lists it fills). A PR that
# changes one of these waits for a staging nightly (check 4): 2026-11-03 count check, 2026-11-04 subfamilies,
# 2026-11-06 set 4 past the pool's 60 minutes were all found that way.
NIGHTLY_PATHS = [
    "src/org/calacademy/antweb/util/UtilDataAction.java",
    "src/org/calacademy/antweb/util/Scheduler.java",
    "src/org/calacademy/antweb/util/SchedulerCall.java",
    "src/org/calacademy/antweb/home/*Count*Db.java",
    "src/org/calacademy/antweb/home/Place*Db.java",
    "src/org/calacademy/antweb/home/GeolocaleDb.java",
    "src/org/calacademy/antweb/home/BioregionDb.java",
    "src/org/calacademy/antweb/home/MuseumDb.java",
    "src/org/calacademy/antweb/home/StatisticsDb.java",
    "src/org/calacademy/antweb/home/TaxonSetDb.java",
    "src/org/calacademy/antweb/home/ParentRows.java",
    "src/org/calacademy/antweb/upload/SpecimenNameUpdate.java",
    "src/org/calacademy/antweb/upload/SpeciesListUploader.java",
    "src/org/calacademy/antweb/upload/TaxonWorks*.java",
    "src/org/calacademy/antweb/ListView.java",
    "WEB-INF/struts-configDbAnt.xml",
]


# ---- the checks, as plain functions (tests/unit/test_release_gate.py) ----

def window_closed(now):
    """True between 04:45 and 08:15 Pacific, when the nightly run works and nothing is merged."""
    t = now.astimezone(PACIFIC).time()
    return CLOSED_FROM <= t < CLOSED_UNTIL


def daily_update_state(status, headers, text):
    """("running" | "not running" | "unknown", detail) from one answer of dailyUpdate.do (format=json, or the HTML page).

    Conservative: only an answer that clearly says the latest run is not running gives "not running"; anything else
    (a Cloudflare challenge, a login page, an error, a state the gate does not know, a run with no end) is "unknown"
    or "running"."""
    h = {str(k).lower(): str(v) for k, v in (headers or {}).items()}
    text = text or ""
    if h.get("cf-mitigated", "").lower() == "challenge" or any(
            t in text for t in ("Just a moment", "cf-chl", "challenge-platform")):
        return "unknown", f"Cloudflare challenge (HTTP {status})"
    if status != 200:
        return "unknown", f"HTTP {status}"
    try:
        data = json.loads(text)
    except ValueError:
        data = None
    if data is None:
        # the HTML page (util/dailyUpdate-body.jsp, DailyUpdateAction.runCard): the latest run's state is in
        # <p id="daily-update-state" data-state="running|ok|failed">
        m = re.search(r'<[^>]*\bid="daily-update-state"[^>]*>', text)
        if m:
            sm = re.search(r'\bdata-state="([a-z]+)"', m.group(0))
            state = sm.group(1) if sm else ""
        elif 'id="daily-update-none"' in text:
            return "not running", "no daily update recorded yet"
        else:
            return "unknown", "the answer is neither the daily update JSON nor its page (a login page?)"
        if state == "running":
            return "running", "the page says the daily update is running"
        if state in ("ok", "failed"):
            return "not running", f"latest daily update {'finished' if state == 'ok' else 'failed'} (page)"
        return "unknown", f"unexpected state {state!r} on the page"
    runs = data.get("runs") if isinstance(data, dict) else None
    if not isinstance(runs, list):
        return "unknown", "no runs list in the JSON"
    if not runs:
        return "not running", "no daily update recorded yet"
    run = runs[0]   # newest first (DailyUpdateAction.load)
    if not isinstance(run, dict):
        return "unknown", "unexpected run in the JSON"
    state, started, ended = run.get("state"), run.get("started"), run.get("ended")
    if state == "running":
        return "running", f"the daily update started {started} Pacific is running"
    if state == "ok" and ended:
        return "not running", f"latest daily update {started} to {ended} Pacific finished"
    if state == "failed":
        # a failed run has ended (or stopped with the server); merging does not race it
        return "not running", f"latest daily update (started {started} Pacific) failed, not running"
    return "unknown", f"unexpected state {state!r} (ended {ended!r}) in the JSON"


def fetch_daily_update(url=None):
    """One GET of the daily update status; (status, headers, text). Errors (no network, DNS, timeout) raise OSError."""
    req = urllib.request.Request(url or DAILY_UPDATE_URL, method="GET",
                                 headers={"User-Agent": "antweb-release-gate", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=DAILY_UPDATE_TIMEOUT) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers or {}), (e.read().decode("utf-8", "replace") if e.fp else "")


def daily_check(fetch, checked_by_hand):
    """(ok, text) for check 8. fetch() returns (status, headers, text) or raises."""
    try:
        state, detail = daily_update_state(*fetch())
    except Exception as e:  # no network, DNS, timeout, proxy refusal
        state, detail = "unknown", f"no answer ({e.__class__.__name__}: {str(e)[:120]})"
    if state == "running":
        return False, f"the daily update is running on production ({detail}); wait until it ends"
    if state == "not running":
        return True, detail
    if checked_by_hand:
        return True, f"{UNKNOWN_DAILY} ({detail}); passed with --nightly-checked (read by hand)"
    return False, f"{UNKNOWN_DAILY} ({detail}); when it is not running, rerun with --nightly-checked"


def has_rollback(body):
    return bool(re.search(r"(?im)^\W*rollback\W*:", body or ""))


def nightly_files(files):
    return [f for f in files if any(fnmatch.fnmatch(f, p) for p in NIGHTLY_PATHS)]


def review_state(comments, head_sha):
    """(ok, text) for the latest Claude review comment of the PR."""
    # only the review workflow's own comments (review of #330: anyone could post one in that form)
    reviews = [c for c in comments if (c.get("body") or "").startswith(REVIEW_MARKER)
               and (c.get("user") or {}).get("login") == REVIEW_BOT]
    if not reviews:
        return False, "no Claude review comment"
    body = reviews[-1]["body"]
    m = re.search(r"Automatic review of ([0-9a-f]{7,40})", body)
    if not m or not head_sha.startswith(m.group(1)):
        return False, f"the review is of {m.group(1) if m else 'an unknown commit'}, not the head {head_sha[:7]}"
    text = body[len(REVIEW_MARKER):].replace("### Claude review", "", 1).strip()
    if not text.startswith("No blocking issues"):
        return False, "the review lists blocking issues: " + text.splitlines()[0][:120]
    return True, f"no blocking issues ({head_sha[:7]})"


def read_known_failures(path=KNOWN_FAILURES):
    if not path.exists():
        return []
    return tomllib.loads(path.read_text()).get("failure", [])


def known_failure_problems(entries, today):
    """Entries that are incomplete, expired, or allowed for longer than MAX_KNOWN_DAYS."""
    problems = []
    for e in entries:
        name = e.get("test", "?")
        missing = [k for k in ("test", "reason", "decided_by", "decided", "expires") if not e.get(k)]
        if missing:
            problems.append(f"{name}: missing {', '.join(missing)}")
            continue
        decided, expires = e["decided"], e["expires"]
        # dates only (a TOML datetime is a date subclass and would break the comparisons; review of #330)
        if not all(isinstance(x, dt.date) and not isinstance(x, dt.datetime) for x in (decided, expires)):
            problems.append(f"{name}: decided and expires must be dates (YYYY-MM-DD)")
        elif decided > today:
            problems.append(f"{name}: decided in the future ({decided})")
        elif expires < today:
            problems.append(f"{name}: expired {expires}")
        elif (expires - decided).days > MAX_KNOWN_DAYS:
            problems.append(f"{name}: allowed for {(expires - decided).days} days (at most {MAX_KNOWN_DAYS})")
    return problems


def failed_tests(annotations):
    """The names of the failed tests of an e2e run, from its annotations ("e2e failed tests" notice, "e2e
    failure" errors)."""
    names = set()
    for a in annotations:
        title, msg = a.get("title") or "", a.get("message") or ""
        if title == "e2e failed tests":
            m = re.match(r"\d+ failed: (.*)", msg, re.S)
            if m:
                names.update(n.strip() for n in re.split(r"[,;]", m.group(1)) if n.strip())
        elif title == "e2e failure":
            names.add(msg.split(":", 1)[0].strip())
    return names


def unlisted(failed, entries):
    allowed = {e.get("test", "").split("::")[-1] for e in entries} | {e.get("test", "") for e in entries}
    return sorted(n for n in failed if n not in allowed and n.split("::")[-1] not in allowed)


# ---- GitHub ----

def gh(path):
    """One GET of the GitHub API."""
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True, check=True).stdout
    return json.loads(out) if out.strip() else None


def gh_all(path, key=None):
    """Every page of a list (review of #330: one page of 100 missed files, comments and runs on large PRs). key: the
    list inside each page's object ("workflow_runs", "jobs"); None for a plain list."""
    out = subprocess.run(["gh", "api", "--paginate", path], capture_output=True, text=True, check=True).stdout
    items, dec, i = [], json.JSONDecoder(), 0
    while i < len(out):
        while i < len(out) and out[i].isspace():
            i += 1
        if i >= len(out):
            break
        page, i = dec.raw_decode(out, i)
        items += (page.get(key) or []) if key else (page or [])
    return items


def runs_for(sha, name):
    runs = gh_all(f"repos/{REPO}/actions/runs?head_sha={sha}&per_page=100", "workflow_runs")
    return sorted([r for r in runs if r["name"] == name], key=lambda r: r["created_at"])


def annotations_of(run_id):
    out = []
    for job in gh_all(f"repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100", "jobs"):
        out += gh_all(f"repos/{REPO}/check-runs/{job['id']}/annotations?per_page=100")
    return out


def full_e2e_run(sha):
    """The latest finished FULL e2e run of this commit and its annotations, or (None, [])."""
    for run in reversed(runs_for(sha, "End-to-end tests")):
        if run["status"] != "completed" or run["conclusion"] == "cancelled":
            continue
        notes = annotations_of(run["id"])
        if any((a.get("title") == "e2e timing" and (a.get("message") or "").startswith("full run")) for a in notes):
            return run, notes
    return None, []


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("pr", type=int)
    ap.add_argument("--nightly-checked", action="store_true",
                    help="the morning compute.log and morning tests were read for this commit (check 4); also: "
                         "dailyUpdate.do was read by hand and the daily update is not running (check 8)")
    ap.add_argument("--no-review", metavar="REASON", help="no Claude review, and why (check 5)")
    args = ap.parse_args(argv)

    pr = gh(f"repos/{REPO}/pulls/{args.pr}")
    head = pr["head"]["sha"]
    files = [f["filename"] for f in gh_all(f"repos/{REPO}/pulls/{args.pr}/files?per_page=100")]
    results = []

    def check(n, name, ok, text):
        results.append(ok)
        print(f"{n} {name:9} {'PASS' if ok else 'FAIL'}  {text}")

    print(f"Release gate, PR #{args.pr} ({pr['title']}), head {head[:7]}, "
          f"{dt.datetime.now(PACIFIC):%Y-%m-%d %H:%M} Pacific")

    tests = runs_for(head, "Tests")
    check(1, "tests", bool(tests) and tests[-1]["conclusion"] == "success",
          f"Tests {tests[-1]['conclusion'] or tests[-1]['status']}" if tests else "no Tests run for the head")

    deploys = runs_for(head, "Deploy Staging")
    run, notes = full_e2e_run(head)
    failed = failed_tests(notes) if run else set()
    entries = read_known_failures()
    if not deploys or deploys[-1]["conclusion"] != "success":
        check(2, "staging", False, "the head was not deployed to staging")
    elif not run:
        check(2, "staging", False, "no finished full e2e run for the head (add the full-e2e label)")
    else:
        # a failed run passes only with named failures that are all listed: a run that failed without naming its
        # tests (a crash, a collection error) never passes (review of #330)
        check(2, "staging", run["conclusion"] == "success" or (bool(failed) and not unlisted(failed, entries)),
              f"full e2e {run['conclusion']} ({len(failed)} failed), run {run['id']}")

    problems = known_failure_problems(entries, dt.date.today())
    rest = unlisted(failed, entries)
    check(3, "known", not problems and not rest,
          "; ".join(problems + [f"not listed: {', '.join(rest)}"] if rest else problems)
          or f"{len(entries)} known failures listed, none expired")

    nightly = nightly_files(files)
    check(4, "nightly", not nightly or args.nightly_checked,
          "no nightly code changed" if not nightly else
          ("nightly checked (--nightly-checked)" if args.nightly_checked else
           f"nightly code changed ({', '.join(Path(f).name for f in nightly[:4])}): wait for a staging nightly, "
           "then --nightly-checked"))

    if args.no_review:
        check(5, "review", True, f"no review: {args.no_review}")
    else:
        ok, text = review_state(gh_all(f"repos/{REPO}/issues/{args.pr}/comments?per_page=100"), head)
        check(5, "review", ok, text)

    closed = window_closed(dt.datetime.now(dt.timezone.utc))
    check(6, "window", not closed, "04:45 to 08:15 Pacific: the daily update is running" if closed
          else "outside the daily update window (04:45 to 08:15 Pacific)")

    check(7, "rollback", has_rollback(pr.get("body")), "Rollback: line found" if has_rollback(pr.get("body"))
          else "no 'Rollback:' line in the PR text")

    check(8, "daily", *daily_check(fetch_daily_update, args.nightly_checked))

    ok = all(results)
    print("GATE OPEN" if ok else "GATE CLOSED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

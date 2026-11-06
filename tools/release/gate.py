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
 6. window    Not between 04:45 and 08:15 Pacific (the nightly run).
 7. rollback  The PR text has a "Rollback:" line.

Read-only: it only reads the GitHub API (through the gh command line tool) and the repository.
"""
import argparse
import datetime as dt
import fnmatch
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path
from zoneinfo import ZoneInfo

REPO = "calacademy-research/antweb"
ROOT = Path(__file__).resolve().parents[2]
KNOWN_FAILURES = ROOT / "tests/e2e/known-failures.toml"
PACIFIC = ZoneInfo("America/Los_Angeles")
CLOSED_FROM, CLOSED_UNTIL = dt.time(4, 45), dt.time(8, 15)
REVIEW_MARKER = "<!-- claude-review -->"
MAX_KNOWN_DAYS = 7

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


def has_rollback(body):
    return bool(re.search(r"(?im)^\W*rollback\W*:", body or ""))


def nightly_files(files):
    return [f for f in files if any(fnmatch.fnmatch(f, p) for p in NIGHTLY_PATHS)]


def review_state(comments, head_sha):
    """(ok, text) for the latest Claude review comment of the PR."""
    reviews = [c for c in comments if (c.get("body") or "").startswith(REVIEW_MARKER)]
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
        if not isinstance(decided, dt.date) or not isinstance(expires, dt.date):
            problems.append(f"{name}: decided and expires must be dates (YYYY-MM-DD)")
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
    """One GET of the GitHub API (lists are asked for with per_page=100, enough for one PR)."""
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True, check=True).stdout
    return json.loads(out) if out.strip() else None


def runs_for(sha, name):
    runs = gh(f"repos/{REPO}/actions/runs?head_sha={sha}&per_page=100")["workflow_runs"]
    return sorted([r for r in runs if r["name"] == name], key=lambda r: r["created_at"])


def annotations_of(run_id):
    out = []
    for job in gh(f"repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100")["jobs"]:
        out += gh(f"repos/{REPO}/check-runs/{job['id']}/annotations?per_page=100") or []
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
                    help="the morning compute.log and morning tests were read for this commit (check 4)")
    ap.add_argument("--no-review", metavar="REASON", help="no Claude review, and why (check 5)")
    args = ap.parse_args(argv)

    pr = gh(f"repos/{REPO}/pulls/{args.pr}")
    head = pr["head"]["sha"]
    files = [f["filename"] for f in gh(f"repos/{REPO}/pulls/{args.pr}/files?per_page=100")]
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
        check(2, "staging", run["conclusion"] == "success" or not unlisted(failed, entries),
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
        ok, text = review_state(gh(f"repos/{REPO}/issues/{args.pr}/comments?per_page=100") or [], head)
        check(5, "review", ok, text)

    closed = window_closed(dt.datetime.now(dt.timezone.utc))
    check(6, "window", not closed, "04:45 to 08:15 Pacific: the nightly is running" if closed else "outside the nightly")

    check(7, "rollback", has_rollback(pr.get("body")), "Rollback: line found" if has_rollback(pr.get("body"))
          else "no 'Rollback:' line in the PR text")

    ok = all(results)
    print("GATE OPEN" if ok else "GATE CLOSED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

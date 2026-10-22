"""Admin pages and the nightly health check, as the staging admin test login (read-only).

Admin login: ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD (tools/staging/create-test-accounts.py).
Staging only; every test skips without the secrets. Nothing is changed: page loads and log reads.

1. Admin pages load (no login page, no error page, no stack trace, their heading shown): the
   Technical page, the Integrity Check, the Log Manager, Server Status, the admin reports.
2. Nightly health (docs/flows/test-gaps-uploads-nightly.md, test 1): compute.log, read through
   logMgr.do, has the nightly run ("Begin run" at or after 05:00 ... "End run") of the run day
   (today; yesterday before 08:30 Pacific, while today's run may still be going), with no
   "FAILED" and no "still running after 210 minutes" in between; a failure quotes those lines.
   taxonworksNightly.log and admin.log have a line from that day (the TaxonWorks job logs one
   line per night). Where the job is off on a server (no etc/taxonworks.properties) that
   line reads "skipped: no .../taxonworks.properties" and the TaxonWorks checks skip.
"""
import datetime
import re
from html import unescape

import pytest

from antweb import BASE_URL, classify
from staging_accounts import IS_STAGING, login, needs

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only"), needs("admin")]

PAGE_TIMEOUT = 300   # the Integrity Check runs every check

ADMIN_PAGES = [
    ("/technical.do", "Technical page"),
    ("/query.do?action=checkIntegrity", "Integrity Check"),
    ("/logMgr.do?action=list", "Log Manager"),
    ("/serverStatus.do", "Server Status"),
    ("/query.do?action=homonyms", "What this shows:"),
    ("/list.do?action=casentDAnomalies", "What this shows:"),
    ("/listSpecimenUploads.do", "Group"),
    ("/viewGroups.do", "ZZ Test Group (staging only)"),
]


@pytest.fixture(scope="module")
def admin():
    s, _token, _id = login("admin")
    return s


@pytest.mark.parametrize("path,marker", ADMIN_PAGES)
def test_admin_page_loads(admin, path, marker):
    r = admin.get(BASE_URL + path, timeout=PAGE_TIMEOUT)
    outcome = classify(r)
    assert outcome.category == "ok", (path, outcome)
    assert marker in r.text, f"{path}: '{marker}' not on the page"
    assert "Please Log In" not in r.text[:5000] and "must be an administrator" not in r.text


# ---------- nightly health ----------

def _pacific_now():
    from zoneinfo import ZoneInfo
    return datetime.datetime.now(ZoneInfo("America/Los_Angeles"))


def _run_day():
    """The day whose nightly run must be complete: today, or yesterday before 08:30 Pacific."""
    now = _pacific_now()
    day = now.date()
    if (now.hour, now.minute) < (8, 30):
        day -= datetime.timedelta(days=1)
    return day.isoformat()


STAMP = r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d "
NIGHTLY_START = "05:00"   # Scheduler: the nightly run starts at 05:00 server time (Pacific)


def _log(admin, name, lines=3000):
    r = admin.get(f"{BASE_URL}/logMgr.do?action=get&fileName={name}&ext=log&lines={lines}", timeout=120)
    assert r.status_code == 200, (name, r.status_code)
    m = re.search(r"<pre>(.*?)</pre>", r.text, re.S)
    if not m:
        if "File empty or not found" in r.text:
            return []
        pytest.fail(f"logMgr.do did not show {name}.log: " + re.sub(r"<[^>]+>", " ", r.text)[:300])
    text = unescape(m.group(1))
    # Before the logMgr.do fix of 2026-10-19 the lines came as one line, each after a
    # literal "<br>": read both forms.
    if text.startswith("<br>"):
        return text[len("<br>"):].split("<br>")
    return text.splitlines()


def _quote(lines, n=8):
    return "\n".join("    " + l for l in lines[-n:])


def test_nightly_run_completed(admin):
    lines = _log(admin, "compute")
    day = _run_day()
    begins = [i for i, l in enumerate(lines) if re.match(STAMP + r"Begin run\b", l)]
    assert begins, (f"no 'Begin run' line in the last {len(lines)} lines of compute.log; its last lines:\n"
                    + _quote(lines))
    nightly = [i for i in begins if lines[i].startswith(day) and lines[i][11:16] >= NIGHTLY_START]
    assert nightly, (f"no nightly run (a 'Begin run' at or after {NIGHTLY_START}) on {day} in compute.log; "
                     "the latest runs began: " + ", ".join(lines[i][:19] for i in begins[-3:]))
    start = nightly[0]
    begin_at = lines[start][:19]
    run = lines[start:]
    end = next((k for k in range(1, len(run)) if re.match(STAMP + r"(End|Begin) run\b", run[k])), None)
    if end is None or run[end][20:].startswith("Begin"):
        body = run if end is None else run[:end]
        state = ("still running" if end is None and day == _pacific_now().date().isoformat()
                 else "stopped without an 'End run' line (a container restart or deploy during the run)")
        pytest.fail(f"the nightly run that began {begin_at} is {state}; its lines in compute.log:\n" + _quote(body))
    body = run[:end + 1]
    problems = [l for l in body if "FAILED" in l or "still running after" in l]
    assert not problems, (
        f"the nightly run of {day} ({begin_at} to {run[end][:19]}) reports a problem in compute.log:\n"
        + _quote(problems, 10)
        + "\n(On staging a deploy or restart between 04:45 and 08:15 cuts the run: 'Scheduler FAILED' with a"
          " connection error. The next night's run shows whether the code is at fault.)")


@pytest.mark.parametrize("name,marker", [("taxonworksNightly", ""), ("admin", "taxonWorksNightly:")])
def test_taxonworks_job_logged_that_day(admin, name, marker):
    day = _run_day()
    lines = [l for l in _log(admin, name, 2000) if l.startswith(day) and marker in l]
    assert lines, (f"no line from {day}{' with ' + marker if marker else ''} in {name}.log: set 1 of that"
                   " night's run did not reach the TaxonWorks upload (see compute.log)")
    off = [l for l in lines if re.search(r"skipped: no \S*taxonworks\.properties", l)]
    if len(off) == len(lines):
        pytest.skip(f"the TaxonWorks nightly upload is off on this server (no etc/taxonworks.properties): {off[-1]}")
    failed = [l for l in lines if "FAILED" in l]
    assert not failed, f"{name}.log, {day}:\n" + _quote(failed, 5)

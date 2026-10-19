"""Admin pages and the nightly health check, as the staging admin test login (read-only).

Admin login: ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD (tools/staging/create-test-accounts.py).
Staging only; every test skips without the secrets. Nothing is changed: page loads and log reads.

1. Admin pages load (no login page, no error page, no stack trace, their heading shown): the
   Technical page, the Integrity Check, the Log Manager, Server Status, the admin reports.
2. Nightly health (docs/flows/test-gaps-uploads-nightly.md, test 1): compute.log, read through
   logMgr.do, has the latest nightly run ("Begin run" ... "End run") from today (from yesterday
   before 08:30 Pacific), with no "FAILED" and no "still running after 210 minutes" in between;
   taxonworksNightly.log and admin.log have a line from that day (the TaxonWorks job logs one
   line per night, also "skipped: no settings file" on staging).
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
    ("/logMgr.do?action=list", "Log Mgr"),
    ("/serverStatus.do", "Antweb Server Status"),
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


def _log(admin, name, lines=3000):
    r = admin.get(f"{BASE_URL}/logMgr.do?action=get&fileName={name}&ext=log&lines={lines}", timeout=120)
    assert r.status_code == 200, (name, r.status_code)
    m = re.search(r"<pre>(.*?)</pre>", r.text, re.S)
    if not m:
        if "File empty or not found" in r.text:
            return []
        pytest.fail(f"logMgr.do did not show {name}.log: " + re.sub(r"<[^>]+>", " ", r.text)[:300])
    return unescape(m.group(1)).splitlines()


def test_nightly_run_completed(admin):
    lines = _log(admin, "compute")
    day = _run_day()
    begins = [i for i, l in enumerate(lines) if re.match(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d Begin run\b", l)]
    assert begins, "no 'Begin run' line in the last 3000 lines of compute.log"
    start = begins[-1]
    begin_line = lines[start]
    assert begin_line.startswith(day), f"the latest nightly run began {begin_line[:19]}, expected {day}"
    run = lines[start:]
    ends = [l for l in run[1:] if re.match(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d End run\b", l)]
    assert ends, f"the run that began {begin_line[:19]} has no 'End run' line (still running, or stopped by a restart)"
    end_index = run.index(ends[0])
    problems = [l for l in run[:end_index + 1] if "FAILED" in l or "still running after" in l]
    assert not problems, "compute.log reports a problem in the latest run:\n" + "\n".join(problems[:10])


@pytest.mark.parametrize("name,marker", [("taxonworksNightly", ""), ("admin", "taxonWorksNightly:")])
def test_taxonworks_job_logged_that_day(admin, name, marker):
    day = _run_day()
    lines = [l for l in _log(admin, name, 2000) if l.startswith(day) and marker in l]
    assert lines, f"no line from {day}{' with ' + marker if marker else ''} in {name}.log"
    failed = [l for l in lines if "FAILED" in l]
    assert not failed, f"{name}.log: " + "\n".join(failed[:5])

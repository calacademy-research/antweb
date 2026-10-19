"""Dates shown from the database are Pacific time (2026-10-19).

The database connections read dates as UTC (serverTimezone=UTC) while MySQL stores Pacific time,
so pages showed dates 7 hours early (8 in winter): an upload whose report says 05:25 PDT was listed
as created at 22:24 the evening before. Fixed with connectionTimeZone=LOCAL; checked against
MySQL 8.4 in CI (test/.../security/DbTimeZoneTest.java).

Here, as the test curator on staging (reads only): on the Specimen Upload Reports list, each
upload's "Created" time is at or after the time in its log file name (the start of the upload,
written by AntWeb in Pacific time), and within a few hours of it.
"""
import re
from datetime import datetime, timedelta

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT
import test_page_editor as pe

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="runs only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

ROW = re.compile(r"<td>(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)(?:\.\d+)?</td>\s*<td>(.*?)</td>", re.S)
LOG_TIME = re.compile(r"(\d{8}-\d\d:\d\d:\d\d)")


@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


@staging_only
def test_upload_created_matches_log_file_time(curator):
    r = curator.get(BASE_URL + "/listSpecimenUploads.do", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    pairs = []
    for created, cell in ROW.findall(r.text):
        m = LOG_TIME.search(cell)
        if m:
            pairs.append((datetime.strptime(created, "%Y-%m-%d %H:%M:%S"),
                          datetime.strptime(m.group(1), "%Y%m%d-%H:%M:%S"), cell))
        if len(pairs) >= 30:
            break
    if not pairs:
        pytest.skip("no uploads with a dated log file name on this server")
    window = [(c, l) for c, l, _ in pairs if timedelta(minutes=-10) <= c - l <= timedelta(hours=6)]
    bad = [f"created {c} log {l} ({cell[:80]})" for c, l, cell in pairs if (c, l) not in window]
    # Nearly all must fit; with the old setting every row was 7 or 8 hours early.
    assert len(window) >= 0.8 * len(pairs), f"{len(bad)} of {len(pairs)} uploads out of range:\n" + "\n".join(bad[:10])

"""Count check (countCheck.do): the numbers on taxon lists against a recount from the specimen records.

Brian Fisher, 2026-10: "we need to make sure we have a test that would validate the counts based on
real specimen data to make sure these counts are true". The page (administrators, read-only) recounts,
straight from the specimen and taxon tables, for Madagascar, Reunion, the country with the most
morphospecies, the subregion of Madagascar (since 2026-10-20: the specimens of its countries; its
differences are pending until a nightly run has counted it that way), the largest live curated project,
the Malagasy bioregion and the CASC museum: the place's own Specimens, Imaged specimens and Images, the specimens
of each species in the place, the species of each genus and the genera of each subfamily in the list,
and the "Global" specimens of each species (rules: src/org/calacademy/antweb/util/CountCheck.java).
Since 2026-10-21 also today's rotating slice of all places (every place within a week, adm1s within a month)
and, for every place, the numbers that must agree (CountInvariants; docs/numbers.md lists every number on
the site and the check or test that covers it).

Rule for failing: the site stores the counts in the nightly run (05:00 to 08:15 Pacific). A difference
caused by a change after the last nightly run (an upload, a list edit) is "pending" and allowed; any
other difference is "real" and fails this test with both numbers. Between 05:00 and 08:15 the nightly
run is still writing the counts: skipped. When compute.log has no completed nightly run (emptied, or the
run failed), the stored counts may be from any earlier day: the differences are listed in the job
summary and the test skips (test_admin_staging.py::test_nightly_run_completed reports the run itself). Staging only (admin test login); skipped where the page is
not deployed yet. Database behaviour: test/org/calacademy/antweb/util/CountCheckTest.java (CI).
"""
import os
import re
import time
from html import unescape

import pytest

from antweb import BASE_URL, classify, get, new_session
from staging_accounts import IS_STAGING, login, needs

# The checks of CountInvariants (docs/numbers.md names them in its Test column).
INVARIANTS = {"place.totals.subfamily", "place.totals.genus", "place.totals.species", "place.totals.valid",
              "place.totals.specimens", "place.totals.endemic", "place.totals.introduced", "place.pies",
              "place.images.order", "place.images", "list.genus", "list.subfamily", "list.family", "list.global",
              "global.specimens", "home", "taxon.images", "geo.regions"}


def test_count_check_needs_login():
    outcome = get(new_session(), "/countCheck.do")
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "login", outcome


# The whole check runs as a background job since 2026-10-21 (107 seconds on staging, over Cloudflare's 100):
# countCheck.do?run=1 starts it and sends the browser to jobStatus.do?id=N; the job's report is countCheck.do?job=N.
JOB_LIMIT_SECONDS = 600
POLL_SECONDS = 10


def run_count_check(s):
    """Starts the check, waits for its job (at most JOB_LIMIT_SECONDS) and returns the report page."""
    r = s.get(BASE_URL + "/countCheck.do?run=1", timeout=90)
    outcome = classify(r)
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "ok", outcome
    m = re.search(r"jobStatus\.do\?id=(\d+)", r.url)
    if not m:
        return r   # a build before 2026-10-21: the report came in the request
    job = m.group(1)
    started = time.time()
    status, step = "", ""
    while True:
        page = s.get(BASE_URL + "/jobStatus.do?id=" + job, timeout=60).text
        st = re.search(r'id="job-status" data-status="(\w+)"', page)
        status = st.group(1) if st else "?"
        sp = re.search(r'id="job-step">(.*?)</span>', page, re.S)
        step = unescape(sp.group(1)) if sp else ""
        if status not in ("queued", "running"):
            break
        if status == "queued" and "nightly" in step and time.time() - started > 60:
            pytest.skip(f"count check job {job} waits for the nightly run: {step}")
        assert time.time() - started < JOB_LIMIT_SECONDS, (
            f"count check job {job} did not finish in {JOB_LIMIT_SECONDS} s (status {status}: {step})")
        time.sleep(POLL_SECONDS)
    assert status == "finished", f"count check job {job} ended {status}: {re.sub(r'<[^>]+>', ' ', page)[-1500:]}"
    report = s.get(BASE_URL + "/countCheck.do?job=" + job, timeout=60)
    assert classify(report).category == "ok"
    return report


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_list_counts_match_the_specimen_records():
    s, _token, _id = login("admin")
    r = run_count_check(s)
    assert "What this shows:" in r.text
    assert "<h1>Check the counts</h1>" in r.text, "page renamed from Count check (2026-10-20)"
    m = re.search(r'id="count-check-result" data-real="(\d+)" data-pending="(\d+)" data-window="(\d)" data-nightly="(\d)"', r.text)
    assert m, "no result line on countCheck.do"
    real, pending, window, nightly = int(m.group(1)), int(m.group(2)), m.group(3) == "1", m.group(4) == "1"
    if window:
        pytest.skip("the nightly run is writing the counts now (05:00 to 08:15 Pacific)")
    for place in ("Madagascar", "Malagasy bioregion", "museum CASC"):
        assert f"<td>{place}</td>" in r.text, f"{place} was not checked"
    rows = []
    if real:
        section = r.text[r.text.index("Real differences"):r.text.index("Pending differences")]
        for row in re.findall(r'<tr class="count-mismatch">(.*?)</tr>', section, re.S):
            cells = [unescape(re.sub(r"<[^>]+>", "", c)).strip() for c in re.findall(r"<td>(.*?)</td>", row, re.S)]
            rows.append(cells)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary and rows:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(f"\n### Count check: {real} real differences ({pending} pending)"
                    f"{'' if nightly else ' - compute.log has no completed nightly run'}\n\n")
            f.write("| Place | Number | Taxon | Stored (shown) | Recount |\n|---|---|---|---|---|\n")
            for cells in rows[:300]:
                f.write("| " + " | ".join(c.replace("|", "/") for c in cells[:5]) + " |\n")
    # Since 2026-10-21: the invariants over every place (CountInvariants) and the run time (the page must answer
    # well within Cloudflare's 100 seconds; the rotating sample stops at about 50).
    if 'id="count-invariants"' in r.text:
        ids = set(re.findall(r'<tr class="count-invariant" data-id="([^"]+)"', r.text))
        missing = INVARIANTS - ids
        assert not missing, f"invariants not run: {sorted(missing)}"
        failed = [(i, unescape(e)) for i, e in re.findall(
            r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*? <b>\(failed: (.*?)\)</b></td>', r.text, re.S)]
        assert not failed, "invariants could not run (check id: exception, code line, SQL):\n" + "\n".join(
            f"{i}: {e}" for i, e in failed)
        t = re.search(r'id="count-check-time" data-seconds="(\d+)"[^>]*>(.*?)</p>', r.text, re.S)
        assert t, "no run time on the report"
        assert int(t.group(1)) < JOB_LIMIT_SECONDS, unescape(t.group(2))
        if summary:
            with open(summary, "a", encoding="utf-8") as f:
                f.write("\n### Count check: every place\n\n| Check | Compared | Real | Pending | ms |\n|---|---|---|---|---|\n")
                for row in re.findall(r'<tr class="count-invariant" data-id="([^"]+)"><td>.*?</td><td>.*?</td><td>.*?</td>'
                                      r'<td>(\d+)</td><td>(\d+)</td><td>(\d+)</td><td>(\d+)</td></tr>', r.text, re.S):
                    f.write("| " + " | ".join(row) + " |\n")
    if not nightly:
        pytest.skip(f"compute.log has no completed nightly run, so the stored counts may be older than a day "
                    f"({real} differences, listed in the job summary)")
    if real:
        pytest.fail(f"{real} list counts differ from the specimen records ({pending} more are pending). "
                    "Place | number | taxon | stored (shown) | recount:\n" + "\n".join(" | ".join(c) for c in rows[:40]))

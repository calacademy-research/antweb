"""Count check (countCheck.do): the numbers on taxon lists against a recount from the specimen records.

Brian Fisher, 2026-10: "we need to make sure we have a test that would validate the counts based on
real specimen data to make sure these counts are true". The page (administrators, read-only) recounts,
straight from the specimen and taxon tables, for Madagascar, Reunion, the country with the most
morphospecies, the largest live curated project (only live projects are recounted each night), the Malagasy bioregion and the CASC museum: the specimens
of each species in the place, the species of each genus and the genera of each subfamily in the list,
and the "Global" specimens of each species (rules: src/org/calacademy/antweb/util/CountCheck.java).

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
from html import unescape

import pytest

from antweb import BASE_URL, classify, get, new_session
from staging_accounts import IS_STAGING, login, needs


def test_count_check_needs_login():
    outcome = get(new_session(), "/countCheck.do")
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "login", outcome


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_list_counts_match_the_specimen_records():
    s, _token, _id = login("admin")
    r = s.get(BASE_URL + "/countCheck.do", timeout=600)
    outcome = classify(r)
    if outcome.category == "not_found":
        pytest.skip("countCheck.do is not deployed on this server yet")
    assert outcome.category == "ok", outcome
    assert "What this shows:" in r.text
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
    if not nightly:
        pytest.skip(f"compute.log has no completed nightly run, so the stored counts may be older than a day "
                    f"({real} differences, listed in the job summary)")
    if real:
        pytest.fail(f"{real} list counts differ from the specimen records ({pending} more are pending). "
                    "Place | number | taxon | stored (shown) | recount:\n" + "\n".join(" | ".join(c) for c in rows[:40]))

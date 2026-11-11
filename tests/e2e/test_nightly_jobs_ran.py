"""Each nightly job wrote its own result line in the last nightly run (road map step 5, catalog section 4).

Decision by Brian Fisher (2026-11-04): the nightly and system jobs of the function catalog
(docs/catalog/coverage-gaps.md, section 4) are covered by a morning test that reads the last nightly
run in compute.log and requires the line each job writes. Admin test login, staging only, read-only
(logMgr.do); every test skips without the admin secret or when compute.log has no complete nightly run.

The run is the last "Begin run" ... "End run" of compute.log that began at the scheduled hour (05:xx,
Scheduler); a run cut short (a restart or deploy during the run) has no "End run" and is not used
(test_admin_staging.py::test_nightly_run_completed reports it).

Which line proves which job (checked in the Java source, 2026-11-04):
- set 1 (UtilDataAction set1): "AntCat import done" after worldantsFetchAndReload succeeded, then the name
  update ("name update: ...", nightlyNameUpdate) and the taxon parents repair ("taxon parents (B36): ...",
  nightlyTaxonParents), its last step;
- set 2: dataCleanup writes "Data cleanup done" (since 2026-11-04, UtilDataAction.jobLine), then recountGlobal
  "Global column recounted after the clean-up" and recountAntCatList "AntCat list (worldants) recounted after set 1";
  generateGeolocaleTaxaFromSpecimens and updateTaxonSets write "Geolocale taxa from specimens generated" and "Taxon
  sets updated" (also since 2026-11-04); then geolocaleCountCrawl's "Populating Geolocales"
  (GeolocaleDb.updateCounts, then "Adm1s populated:", "Countries populated", "Subregions populated",
  "Regions populated"), follows the AntCat list line; "Set 2 done" ends the set;
- set 3: projectCountCrawl "Project species-list counts crawled:" and "Projects counts computed",
  populateMuseum "Museums populated", populateBioregion "Bioregions populated", the group counts
  "Group counts:", crawlForType "Types Crawl completed", the group specimen counts "Group specimen counts:" (since
  2026-11-11, GroupDb.recountUploadSpecimens, the set's last statement; see test_group_specimen_counts_line), then
  "Set 3 done";
- set 4: imageCountCrawl "AntWeb-wide list Images counted" (ProjTaxonCountDb) and "Image Count Crawl
  completed" (ImageCountDb), placeImageCounts "Place image counts:", placeGenusCuration "Not curated
  counted:", imagingProgress "Imaging progress counted:" (since 2026-11-08), then "Set 4 done"; then, outside set 4 (Scheduler.runPlaceListTotals, since 2026-11-06),
  placeViewTotals "Place list totals counted:";
- set 5 is skipped in staging's nightly ("set5 skipped on staging (memory)", Scheduler); on production it
  runs but writes no line of its own. This test runs on staging only, so set 5 and its jobs
  (deleteConflictedDefaultImages, genObjectMaps, cleanupSpeciesListProxyRecords, verifyProjTaxon) are not
  counted as covered here;
- after the sets (Scheduler): "statistics page tables counted", "API taxon counts:", "count check started"
  (only when the nightly restart is off), "End run".
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL
from staging_accounts import IS_STAGING, login, needs

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only"), needs("admin")]

STAMP = r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d "
NIGHTLY_HOUR = "05"   # Scheduler: the nightly run starts at 05:00 server time (Pacific)
LOG_LINES = 5000      # one night is far fewer lines

# The lines of a complete nightly run, in the order the run writes them. A tuple: any one of them.
# The set 5 slot is empty on production (set 5 writes no line), so it is optional.
RUN_LINES = [
    "Begin run",
    "AntCat import done",
    "name update:",
    "taxon parents (B36):",
    "Global column recounted after the clean-up",
    "AntCat list (worldants) recounted after set 1",
    "Populating Geolocales",
    "Adm1s populated:",
    "Countries populated",
    "Subregions populated",
    "Regions populated",
    "Set 2 done",
    "Project species-list counts crawled:",
    "Projects counts computed",
    "Museums populated",
    "Bioregions populated",
    "Group counts:",
    "Types Crawl completed",
    "Set 3 done",
    "AntWeb-wide list Images counted",
    "Image Count Crawl completed",
    "Place image counts:",
    "Not curated counted:",
    # the imaging progress page (2026-11-08), after the statistics row
    "Imaging progress counted:",
    "Set 4 done",
    # after set 4, on its own connection (Scheduler.runPlaceListTotals, 2026-11-06)
    "Place list totals counted:",
    "statistics page tables counted",
    "API taxon counts:",
    ("count check started", "Nightly restart requested"),
    "End run",
]
SET5_SKIPPED = "set5 skipped on staging (memory)"

# Catalog rows (docs/catalog/actions.csv) and the line in the run that proves each job ran. Jobs without a
# line of their own name the line of the step that follows them in the same set (the set stops at a failure,
# Scheduler: "FAILED"), and the order check (test_nightly_lines_in_order) makes that line come after the
# line before them.
JOB_LINES = {
    "/utilData.do?action=set1": "taxon parents (B36):",
    "/utilData.do?action=worldantsFetchAndReload": "AntCat import done",
    # the Curate page's "Import AntCat names now" runs the same import (SpeciesListUploader.worldantsFetchAndReload)
    "/upload.do?action=fetchAndReloadWorldants": "AntCat import done",
    # the admin's "run the name update now" runs the nightly's step (SpecimenNameUpdate with etc/name-update.properties)
    "/utilData.do?action=specimenNameUpdate": "name update:",
    "/utilData.do?action=set2": "Set 2 done",
    # their own lines since 2026-11-04 (UtilDataAction.jobLine); presence only, not in the ordered list above
    "/utilData.do?action=dataCleanup": "Data cleanup done",
    "/utilData.do?action=generateGeolocaleTaxaFromSpecimens": "Geolocale taxa from specimens generated",
    "/utilData.do?action=updateTaxonSets": "Taxon sets updated",
    "/utilData.do?action=geolocaleCountCrawl": "Regions populated",
    "/utilData.do?action=set3": "Set 3 done",
    "/utilData.do?action=projectCountCrawl": "Projects counts computed",
    "/utilData.do?action=populateMuseum": "Museums populated",
    "/utilData.do?action=populateBioregion": "Bioregions populated",
    "/utilData.do?action=crawlForType": "Types Crawl completed",
    "/utilData.do?action=set4": "Set 4 done",
    "/utilData.do?action=imageCountCrawl": "Image Count Crawl completed",
    "/utilData.do?action=placeImageCounts": "Place image counts:",
    "/utilData.do?action=placeGenusCuration": "Not curated counted:",
    "/utilData.do?action=placeViewTotals": "Place list totals counted:",
    "/utilData.do?action=imagingProgress": "Imaging progress counted:",
}

# Words a job writes on its line when it did not do its work.
FAILED = re.compile(r"FAILED|\bNOT\b|not recounted|not copied|not written|failed \(")


@pytest.fixture(scope="module")
def run_lines():
    """The lines of the last complete nightly run in compute.log (without their time stamps)."""
    s, _token, _id = login("admin")
    r = s.get(f"{BASE_URL}/logMgr.do?action=get&fileName=compute&ext=log&lines={LOG_LINES}", timeout=120)
    assert r.status_code == 200, r.status_code
    m = re.search(r"<pre>(.*?)</pre>", r.text, re.S)
    if not m:
        pytest.skip("compute.log is empty or not found")
    text = unescape(m.group(1))
    lines = text[len("<br>"):].split("<br>") if text.startswith("<br>") else text.splitlines()
    run = last_nightly_run(lines)
    if run is None:
        pytest.skip(f"no complete nightly run (Begin run at {NIGHTLY_HOUR}:xx ... End run) in the last"
                    f" {len(lines)} lines of compute.log")
    return run


def last_nightly_run(lines):
    """[(stamp, text)] of the last run that began at the nightly hour and has its End run, or None."""
    begins = [i for i, l in enumerate(lines) if re.match(STAMP + r"Begin run\b", l) and l[11:13] == NIGHTLY_HOUR]
    for start in reversed(begins):
        for k in range(start + 1, len(lines)):
            if re.match(STAMP + r"Begin run\b", lines[k]):
                break   # cut short: a new run began before this one ended
            if re.match(STAMP + r"End run\b", lines[k]):
                return [(l[:19], l[20:].strip()) if re.match(STAMP, l) else ("", l.strip())
                        for l in lines[start:k + 1]]
    return None


def _find(run, marker, start=0):
    markers = marker if isinstance(marker, tuple) else (marker,)
    for i in range(start, len(run)):
        if any(run[i][1].startswith(m) for m in markers):
            return i
    return None


def _quote(run, n=12):
    return "\n".join(f"    {stamp} {text}" for stamp, text in run[-n:])


def test_nightly_lines_in_order(run_lines):
    """Every line of a complete nightly run is there, in the order the run writes them."""
    pos, missing = 0, []
    for marker in RUN_LINES:
        i = _find(run_lines, marker, pos)
        if i is None:
            missing.append(marker if isinstance(marker, str) else " or ".join(marker))
        else:
            pos = i + 1
    begin = run_lines[0][0]
    assert not missing, (f"the nightly run that began {begin} lacks these lines (or has them out of order): "
                         + "; ".join(missing) + "\nits last lines:\n" + _quote(run_lines))


def test_set5_skipped_on_staging(run_lines):
    """Staging skips set 5 in the nightly run (memory) and says so between set 4 and the steps after the sets.
    (Production runs set 5, which writes no line of its own; this test runs on staging only.)"""
    set4 = _find(run_lines, "Set 4 done")
    after = _find(run_lines, "statistics page tables counted")
    assert set4 is not None and after is not None, "the run did not reach the end of set 4 and the steps after it"
    skipped = _find(run_lines, SET5_SKIPPED, set4)
    assert skipped is not None and skipped < after, (
        f"no '{SET5_SKIPPED}' between 'Set 4 done' and the steps after the sets:\n" + _quote(run_lines[set4:after + 1]))


def test_group_specimen_counts_line(run_lines):
    """Set 3 recounts every group's stored specimen count (ant_group.upload_specimens, GroupDb.recountUploadSpecimens,
    2026-11-11: a stale number was a real difference of the count check every day, production group 52) and writes
    "Group specimen counts: N groups recounted, M numbers changed (...)[, K left as stored (...)] in S s" as its last
    statement, after the type crawl and before "Set 3 done" (the rows it changes are locked until the set
    commits). A run from before the change has no such line: skipped then. Once staging's daily update has written
    it, the line can join RUN_LINES (before "Set 3 done")."""
    i = _find(run_lines, "Group specimen counts:")
    if i is None:
        pytest.skip(f"the nightly run that began {run_lines[0][0]} has no 'Group specimen counts:' line: it ran before "
                    "the change of 2026-11-11 was deployed")
    line = run_lines[i][1]
    assert not FAILED.search(line), line[:300]
    m = re.match(r"Group specimen counts: (\d+) groups recounted, (\d+) numbers changed", line)
    assert m and int(m.group(1)) > 0 and int(m.group(2)) <= int(m.group(1)), line[:300]
    before, after = _find(run_lines, "Types Crawl completed"), _find(run_lines, "Set 3 done")
    # (that nothing of the set runs between it and the commit is checked in the source: tests/unit/test_group_specimen_count_daily.py)
    assert before is not None and after is not None and before < i < after, (
        "the line is not between 'Types Crawl completed' and 'Set 3 done':\n" + _quote(run_lines[max(0, i - 3):i + 4]))


def test_each_job_wrote_its_line(run_lines):
    """Each catalog job of the nightly has its result line in the run, and the line does not report a failure."""
    problems = []
    for url, marker in JOB_LINES.items():
        i = _find(run_lines, marker)
        if i is None:
            problems.append(f"{url}: no line starting '{marker}'")
        elif FAILED.search(run_lines[i][1]):
            problems.append(f"{url}: {run_lines[i][1][:200]}")
    assert not problems, (f"nightly run of {run_lines[0][0]}:\n  " + "\n  ".join(problems))


def test_place_list_totals_counted_every_place(run_lines):
    """The nightly count of the place pages' Subfamilies, Genera and Species ("/utilData.do?action=placeViewTotals",
    PlaceViewTotalDb) counted places and none failed. 2026-11-05: every place failed with the same error and the line
    read "Place list totals counted: 0 places ...; not counted: Aargau (...)", which the presence check let through.
    Since 2026-11-10 the line goes on after the counts: "(M recounted, K unchanged); recounted because: data changed 12
    (list rows 3, ...), new place 1; comparing 20 s, counting 4 s" (the reason "not counted yet" is now "new place"); a
    place that fails adds "; FAILED for N places ...". Only the number of places and the word FAILED are read here;
    tests/unit/test_place_totals_connection.py runs these patterns against the line as the Java code builds it."""
    i = _find(run_lines, "Place list totals counted:")
    assert i is not None, "no 'Place list totals counted:' line in the nightly run:\n" + _quote(run_lines)
    line = run_lines[i][1]
    m = re.match(r"Place list totals counted: (?:FAILED, )?(\d+) places", line)
    assert m and int(m.group(1)) > 0, f"no place counted: {line[:300]}"
    assert "FAILED" not in line and "not counted:" not in line, f"places not counted: {line[:400]}"


# The long pool (longConPool, WEB-INF/struts-configDbAnt.xml) closes a connection held over 60 minutes
# (unreturnedConnectionTimeout 3600). On 2026-11-06 set 4 ran 60 minutes on staging and production, lost its
# connection and set 5 was skipped. Staging is faster than production (06:17 vs 06:58 that day), so a step must
# stay well under the limit here.
# The place list totals run on a direct connection (no 60-minute limit; 36 minutes on staging, over 42 on
# production on 2026-11-06), so they are not in this list.
STEP_LIMIT_MINUTES = 40
STEP_ENDS = [("set 1", "Begin run", "taxon parents (B36):"), ("set 2", "taxon parents (B36):", "Set 2 done"),
             ("set 3", "Set 2 done", "Set 3 done"), ("set 4", "Set 3 done", "Set 4 done")]


def test_each_set_well_under_the_connection_limit(run_lines):
    """Each set of the nightly (one long-pool connection each) took under STEP_LIMIT_MINUTES on staging, so none
    comes near the pool's 60 minutes on production."""
    from datetime import datetime
    def at(marker, start=0):
        i = _find(run_lines, marker, start)
        return (i, datetime.strptime(run_lines[i][0].strip(), "%Y-%m-%d %H:%M:%S")) if i is not None else (None, None)
    slow = []
    for name, begin, end in STEP_ENDS:
        i, t0 = at(begin)
        j, t1 = at(end, (i or 0) + 1) if i is not None else (None, None)
        if t0 is None or t1 is None:
            slow.append(f"{name}: no '{begin}' ... '{end}' lines")
            continue
        minutes = (t1 - t0).total_seconds() / 60
        if minutes > STEP_LIMIT_MINUTES:
            slow.append(f"{name}: {minutes:.0f} minutes ({run_lines[i][0].strip()} to {run_lines[j][0].strip()})")
    assert not slow, (f"nightly run of {run_lines[0][0]}: steps over {STEP_LIMIT_MINUTES} minutes "
                      "(the long pool closes a connection at 60):\n  " + "\n  ".join(slow))

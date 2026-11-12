"""The place list totals run outside set 4, on a connection of their own (2026-11-06).

On 2026-11-06 the place list totals (PlaceViewTotalDb.fillAll, about 40 minutes) ran inside set 4's request and kept
set 4's long-pool connection past 60 minutes (unreturnedConnectionTimeout 3600): c3p0 closed it, set 4 failed and
set 5 and the steps after the run were skipped, on staging and production. These checks read the Java source.

2026-11-10 (review of PR #341): the line the totals write to compute.log got longer ("; recounted because: ...;
comparing ... s, counting ... s") and the reason "not counted yet" became "new place". The morning check
(tests/e2e/test_nightly_jobs_ran.py) reads that line: its patterns, taken from its source, are run here against
the line as the Java code now builds it, and the reasons are those docs/flows/nightly.md names.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UTIL = ROOT / "src/org/calacademy/antweb/util"


def _set4_block(src):
    start = src.index('if ("set4".equals(action))')
    end = src.index('if ("set5".equals(action))', start)
    return src[start:end]


def test_set4_does_not_count_place_list_totals():
    src = (UTIL / "UtilDataAction.java").read_text()
    assert 'doAction("placeViewTotals"' not in _set4_block(src)


def test_scheduler_counts_them_after_set4_on_a_direct_connection():
    src = (UTIL / "Scheduler.java").read_text()
    set4 = src.index('"/utilData.do?action=set4&param=allow"')
    set5 = src.index('"/utilData.do?action=set5&param=allow')
    call = src.index("runPlaceListTotals();", set4)
    assert set4 < call < set5
    body = src[src.index("static void runPlaceListTotals()"):]
    body = body[:body.index("\n    }\n")]
    assert "DBUtilSimple.getDataSource().getConnection()" in body
    assert "PlaceViewTotalDb(connection).fillAll()" in body
    # a failure is logged in a form the morning test reads as failed, and does not stop the run
    assert re.search(r'LOG \+ ": FAILED \(', body)
    assert "catch (Exception e)" in body and "throw" not in body
    assert 'setInComputeProcess("placeViewTotals")' in body and "setInComputeProcess(null)" in body


def test_long_pool_limit_is_what_the_e2e_check_assumes():
    xml = (ROOT / "WEB-INF/struts-configDbAnt.xml").read_text()
    long_pool = xml[xml.index('key="longConPool"'):]
    m = re.search(r'unreturnedConnectionTimeout" value="(\d+)"', long_pool)
    assert m and int(m.group(1)) == 3600, "tests/e2e/test_nightly_jobs_ran.py STEP_LIMIT_MINUTES assumes 60 minutes"


def _java_strings(src, start, end):
    """The string literals of the Java source between two markers."""
    body = src[src.index(start):]
    return re.findall(r'"((?:[^"\\]|\\.)*)"', body[:body.index(end)])


def test_the_morning_check_reads_the_line_as_it_is_written_now():
    java = (ROOT / "src/org/calacademy/antweb/home/PlaceViewTotalDb.java").read_text()
    e2e = (ROOT / "tests/e2e/test_nightly_jobs_ran.py").read_text()
    # the reasons a place is counted again, as recountReason and fill() write them
    reasons = [x for x in _java_strings(java, "static String recountReason(", "static final String DATA_CHANGED") if x != " days ago"]
    assert reasons == ["new place", "totals missing", "rules changed", "counted over ", "specimen names not stored again since the last count"], reasons
    assert 'DATA_CHANGED = "data changed"' in java and '? "full count" : recountReason(' in java
    assert "not counted yet" not in _java_strings(java, "static String recountReason(", "static final String DATA_CHANGED")
    parts = re.findall(r'\{"[a-z]", "([a-z ]+)"\}', java[java.index("PARTS"):])
    assert parts and "fingerprint of an earlier version" in java
    # the line, built as fill() builds it
    assert ('return LOG + ": " + (done == 0 && !failed.isEmpty() ? "FAILED, " : "") + done + " places in "' in java
            and '+ " (" + recounted + " recounted, " + unchanged + " unchanged)"' in java and 'new StringBuilder("; recounted because: ")' in java
            and '"; comparing " + comparing / 1000 + " s, counting " + counting / 1000 + " s"' in java)
    because = ("; recounted because: data changed 10 (" + ", ".join(p + " 3" for p in parts) + ", fingerprint of an earlier version 2), new place 1, "
               "totals missing 1, rules changed 1, counted over 7 days ago 1, specimen names not stored again since the last count 1, full count 1")
    good = "Place list totals counted: 1987 places in 584 s (17 recounted, 1970 unchanged)" + because + "; comparing 20 s, counting 4 s"
    some_failed = good + "; FAILED for 3 places (their stored totals are shown), e.g. Aargau (Deadlock found)"
    none_counted = "Place list totals counted: FAILED, 0 places in 2 s (0 recounted, 0 unchanged); FAILED for 1987 places (their stored totals are shown), e.g. Aargau (x)"
    scheduler_failed = "Place list totals counted: FAILED (java.sql.SQLException: x)"
    # the morning check's own patterns, from its source
    marker = re.search(r'^    "(Place list totals counted:)",$', e2e, re.M).group(1)
    counted = re.compile(re.search(r're\.match\(r"(Place list totals counted: [^"]+)", line\)', e2e).group(1))
    failed = re.compile(re.search(r'^FAILED = re\.compile\(r"([^"]+)"\)$', e2e, re.M).group(1))
    assert 'assert "FAILED" not in line and "not counted:" not in line' in e2e
    for line in (good, some_failed, none_counted, scheduler_failed):
        assert line.startswith(marker)
    # a good run: counted, and nothing in the new suffix reads as a failure (no reason holds FAILED, NOT or "not counted:")
    assert counted.match(good).group(1) == "1987" and not failed.search(good) and "not counted:" not in good
    # places that failed, every place failed, the step itself failed: each is reported
    assert counted.match(some_failed) and failed.search(some_failed) and "FAILED" in some_failed
    assert counted.match(none_counted).group(1) == "0" and failed.search(none_counted)
    assert not counted.match(scheduler_failed) and failed.search(scheduler_failed)
    # the docs name the line and the reasons as they are written
    doc = (ROOT / "docs/flows/nightly.md").read_text()
    assert '"Place list totals counted: N places in S s (M recounted, K unchanged); recounted because: ' in doc
    section = doc[doc.index("### Place list totals (after set 4)"):]
    section = " ".join(section[:section.index("\n## ")].split()) if "\n## " in section else " ".join(section.split())
    for reason in ["data changed", "new place", "totals missing", "rules changed", "counted over 7 days ago",
                   "specimen names not stored again since the last count", "full count", "fingerprint of an earlier version"] + parts:
        assert reason in section, reason
    assert "not counted yet" not in section


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")

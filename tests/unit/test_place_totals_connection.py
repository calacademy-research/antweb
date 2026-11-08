"""The place list totals run outside set 4, on a connection of their own (2026-11-06).

On 2026-11-06 the place list totals (PlaceViewTotalDb.fillAll, about 40 minutes) ran inside set 4's request and kept
set 4's long-pool connection past 60 minutes (unreturnedConnectionTimeout 3600): c3p0 closed it, set 4 failed and
set 5 and the steps after the run were skipped, on staging and production. These checks read the Java source.
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


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")

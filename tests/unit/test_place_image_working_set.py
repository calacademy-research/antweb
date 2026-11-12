#!/usr/bin/env python3
"""The place image counts read the imaged specimens, and the morning check reads their line as it is written (2026-11-11).

The daily update's "place image counts" step (PlaceImageCountDb.updateAll, set 4) read every specimen of every place,
imaged or not, twice: 13.6 minutes on production on 2026-11-10. It now reads the specimens from a working set of the
specimens that have an image (session-private TEMPORARY tables). These checks read the source: the working set is made
without a statement that commits set 4's transaction or locks rows of the specimen table, the old statements are kept
for the comparison and for a run without the working set, the comparison job is declared, and the patterns of the
morning check (tests/e2e/test_nightly_jobs_ran.py) match the line as the Java code builds it. The numbers themselves
are proved against a database in test/org/calacademy/antweb/home/PlaceImageCountDbTest.java.

Run: python3 tests/unit/test_place_image_working_set.py   (also in .github/workflows/tests.yaml)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = (ROOT / "src/org/calacademy/antweb/home/PlaceImageCountDb.java").read_text(encoding="utf-8")
E2E = (ROOT / "tests/e2e/test_nightly_jobs_ran.py").read_text(encoding="utf-8")


def _code(src):
    """The Java source without its comments (statements named in a comment are not statements the class runs)."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return "\n".join(re.sub(r"//.*$", "", line) for line in src.splitlines())


CODE = _code(JAVA)


def test_working_tables_are_temporary_and_nothing_commits():
    # every CREATE and DROP of the working tables says TEMPORARY (a plain CREATE or DROP TABLE commits the transaction)
    assert '"create temporary table " + table' in CODE
    assert '"drop temporary table if exists " + table' in CODE
    lowered = CODE.lower()
    for statement in ("alter table", "create index", "create table tmp", "drop table", "truncate", "lock tables"):
        assert statement not in lowered, f"PlaceImageCountDb runs '{statement}': it would commit set 4's transaction"
    # the one CREATE TABLE of the class is place_image_count itself, made only when missing (ensureSchema)
    assert lowered.count("create table") == 1 and '"CREATE TABLE IF NOT EXISTS place_image_count ("' in CODE
    assert "commit(" not in CODE and "rollback(" not in CODE and "setAutoCommit(" not in CODE


def test_specimens_are_read_with_a_plain_select():
    # INSERT ... SELECT from specimen would put shared locks on the specimen rows until set 4 commits
    assert re.search(r'IMAGED_SPECIMENS = "select " \+ specimenColumns\("s"\) \+ " from specimen s"\s*\+ " where exists '
                     r'\(select 1 from image i where i\.image_of_id = s\.code\)"', CODE)
    inserts = re.findall(r'"insert into "[^;]*;', CODE)
    assert len(inserts) == 2, inserts
    values = [i for i in inserts if ") values " in i]
    copies = [i for i in inserts if " select " in i]
    assert len(values) == 1 and len(copies) == 1
    assert "WORK_TABLES[0] + \" w\"" in copies[0] and "specimen s" not in copies[0], "the copies read the first working table only"
    # the tables are made with the columns of specimen and no row
    assert '" as select " + specimenColumns("s") + " from specimen s where 1 = 0"' in CODE


def test_old_statements_are_kept_and_one_place_uses_them():
    assert "public Map<String, PlaceImages> countReference(String type)" in CODE
    assert "return count(sources(type, false), type, null);" in CODE
    assert "return count(sources(type, false, WORK_TABLES), type, null);" in CODE
    # one place (Recalculate button): the statements over the specimen table
    one = CODE[CODE.index("Map<String, PlaceImages> count(String type, String key)"):]
    assert "return count(sources(type, key != null), type, key);" in one[:300]
    update_place = CODE[CODE.index("public PlaceImages updatePlace("):]
    assert "count(type, key)" in update_place[:300] and "WorkingSet" not in update_place[:400]
    # all places: the working set, or the old statements when it could not be made; the tables are dropped either way
    for method in ("public String updateAll()", "public int updateType(String type)"):
        body = CODE[CODE.index(method):]
        body = body[:body.index("\n    }\n")]
        assert "tryOpenWorkingSet()" in body and "fromWorkingSet ? countFromWorkingSet(type) : countReference(type)" in body
        assert "finally" in body and "closeWorkingSet();" in body


def test_comparison_job_is_declared_and_writes_nothing():
    action = (ROOT / "src/org/calacademy/antweb/util/UtilDataAction.java").read_text(encoding="utf-8")
    actions = action[action.index("static final java.util.Set<String> ACTIONS"):]
    assert '"placeImageCountCompare"' in actions[:actions.index(");")]
    in_request = action[action.index("static final java.util.Set<String> IN_REQUEST"):]
    assert "placeImageCountCompare" not in in_request[:in_request.index(");")], "a long job: it runs in the background"
    block = action[action.index('if (action.equals("placeImageCountCompare"))'):]
    block = block[:block.index("\n\t\t}\n")]
    assert ".compare()" in block and "Html.esc(line)" in block and "update" not in block.lower()
    compare = CODE[CODE.index("public Comparison compare()"):CODE.index("public PlaceImages read(")]
    assert "write(" not in compare and "executeUpdate" not in compare and "executeBatch" not in compare
    catalog = (ROOT / "docs/catalog/actions.csv").read_text(encoding="utf-8")
    row = [l for l in catalog.splitlines() if l.startswith("/utilData.do?action=placeImageCountCompare,")]
    assert len(row) == 1 and "POST-only changes" in row[0] and ",manual," in row[0]


def test_the_morning_check_reads_the_line_as_it_is_written_now():
    # the line, as updateAll() and openWorkingSet() build it
    assert 'new StringBuilder("Place image counts:")' in CODE
    assert ('workingSetLine = "imaged specimens " + stored + " in " + seconds(t5 - t0) + " s (read " + seconds(t1 - t0)' in CODE
            and '" s, stored " + seconds(t2 - t1) + " s, checked " + seconds(t3 - t2) + " s, copies " + seconds(t4 - t3)' in CODE
            and '" s, statements prepared " + seconds(t5 - t4) + " s)"' in CODE)
    assert 'b.append(" ").append(workingSetLine).append(";");' in CODE
    not_used = re.search(r'WORKING_SET_NOT_USED = "([^"]+)"', CODE).group(1)
    assert 'workingSetLine = WORKING_SET_NOT_USED + " (" + why + "), the old statements ran";' in CODE
    good = ("Place image counts: imaged specimens 67012 in 4.2 s (read 2.7 s, stored 0.8 s, checked 0.3 s, copies 0.2 s,"
            " statements prepared 0.2 s);"
            " geolocale 5123 places with images in 30.1 s (islands 1.0 s, countries 5.0 s, adm1s 6.0 s, regions and subregions"
            " 15.0 s, written 3.1 s); bioregion 8 places with images in 3.0 s (counted 2.9 s, written 0.1 s);"
            " museum 60 places with images in 3.0 s (counted 2.9 s, written 0.1 s); project 90 places with images in 9.0 s"
            " (counted 8.9 s, written 0.1 s);")
    old_statements = ("Place image counts: " + not_used + " (CREATE TEMPORARY TABLES command denied to user 'antweb'@'%'), the old"
                      " statements ran; geolocale 5123 places with images in 639.0 s (islands 30.0 s, countries 200.0 s, adm1s 180.0 s,"
                      " regions and subregions 220.0 s, written 3.1 s);")
    before = "Place image counts: geolocale 5123 places with images in 639.0 s (islands 30.0 s, countries 200.0 s, adm1s 180.0 s, regions and subregions 229.0 s);"
    # the morning check's own patterns, from its source
    pattern = re.compile(re.search(r'^WORKING_SET = re\.compile\(r"([^"]+)"\)$', E2E, re.M).group(1))
    marker = re.search(r'^WORKING_SET_NOT_USED = "([^"]+)"$', E2E, re.M).group(1)
    failed = re.compile(re.search(r'^FAILED = re\.compile\(r"([^"]+)"\)$', E2E, re.M).group(1))
    assert marker == not_used, "the e2e check and PlaceImageCountDb name the fallback with different words"
    assert pattern.match(good).group(1) == "67012" and marker not in good and not failed.search(good)
    assert marker in old_statements and not pattern.match(old_statements)
    assert "imaged specimens" not in before, "a run before the change is skipped, not failed"
    assert 'if "imaged specimens" not in line:' in E2E and "pytest.skip(" in E2E
    for line in (good, old_statements, before):
        assert line.startswith("Place image counts:")   # the presence check (JOB_LINES) and the morning check still find it
    doc = (ROOT / "docs/flows/nightly.md").read_text(encoding="utf-8")
    assert '"Place image counts: imaged specimens N in S s (read S s, stored S s, checked S s, copies S s, statements prepared S s); geolocale N' in " ".join(doc.split())
    assert not_used in doc and "placeImageCountCompare" in doc


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    failed = 0
    for name, fn in tests:
        try:
            fn()
        except AssertionError as e:
            failed += 1
            print(f"FAIL: {name}: {e}")
    if failed:
        sys.exit(1)
    print(f"OK ({len(tests)} tests): the place image counts read the imaged specimens; the morning check reads their line.")

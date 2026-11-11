"""tools/place/place_fix.py against a real MySQL: --apply and --rollback never overwrite newer values (2026-11-10).

Review of PR #341. The update and the rollback matched a specimen by its code alone:
  - a specimen that an upload loaded again between the backup and the update got the country and bioregion worked
    out for its OLD country, state and bioregion (the CAS TaxonWorks upload reloads every day);
  - a rollback, run days later, put the old values back over whatever an upload or a curator had stored since.
Here the tool runs against a database of its own, through the same `docker exec ... mysql` it uses on the servers:
  1. apply, with one specimen changed between the backup and the update: it is left as it is and counted;
  2. rollback, with one specimen changed and one deleted after the apply: both are left alone and listed;
     a second rollback restores nothing;
  3. rollback of a backup table as versions 1 to 3 wrote it (no column applied; production place_fix_20261110_0901,
     staging _0840 and _0857), and of one without the new values, which is joined to its _map table;
  4. a specimen table in latin1: "México" is found and corrected (the bytes are compared as UTF-8);
  5. an --apply that fails before the update leaves no table behind and no specimen changed; values as long as the
     columns allow (64 letters of four bytes each) fit the work table.

Needs a MySQL (8.4 in CI) reachable as `docker exec <container> mysql`:
  PLACE_FIX_TEST_CONTAINER   the container (CI: pfmysql); without it the tests are skipped
  PLACE_FIX_TEST_USER        default root (it creates and drops the databases place_fix_test_*)
  PLACE_FIX_TEST_PASSWORD    its password (read from the environment, written to a db.properties in a temporary folder)

Run: PLACE_FIX_TEST_CONTAINER=pfmysql PLACE_FIX_TEST_PASSWORD=... python3 tests/unit/test_place_fix_db.py
(.github/workflows/tests.yaml starts the container for it).
"""
import csv
import importlib.util
import io
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("place_fix", os.path.join(ROOT, "tools", "place", "place_fix.py"))
pf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pf)

CONTAINER = os.environ.get("PLACE_FIX_TEST_CONTAINER", "")
USER = os.environ.get("PLACE_FIX_TEST_USER", "root")
PASSWORD = os.environ.get("PLACE_FIX_TEST_PASSWORD", "")

PLACES = [  # name, georank, parent, is_valid, valid_name, bioregion, alt_bioregion, is_island
    ("Canada", "country", "Northern America", 1, None, "Nearctic", None, 0),
    ("Mexico", "country", "Central America", 1, None, "Neotropical", "Nearctic", 0),
    ("United States", "country", "Northern America", 1, None, "Nearctic", "Oceania", 0),
    ("USA", "country", "Northern America", 0, "United States", None, None, 0),
    ("Hawaii", "country", "Polynesia", 1, None, "Oceania", None, 1),
    ("Chihuahua", "adm1", "Mexico", 1, None, "Nearctic", "none", 0),
    ("Jalisco", "adm1", "Mexico", 1, None, "Neotropical", "Nearctic", 0),
    ("California", "adm1", "United States", 1, None, "Nearctic", None, 0),
    ("Ontario", "adm1", "Canada", 1, None, None, None, 0),
]
SPECIMENS = [  # code, country, adm1, bioregion, island_country
    ("c1", "Mexico", "Chihuahua", "", None),            # the state's bioregion
    ("c2", "México", "Chihuahua", None, None),     # the country's spelling, and the state's bioregion
    ("c3", "USA", "California", "Nearctic", None),      # the country's old name
    ("c4", "Canada", "", "", None),                     # the country's bioregion
    ("c5", "Mexico", "Chihuahua", "", None),            # as c1; an upload loads it again before the update
    ("c6", "Canada", "Ontario", "Nearctic", None),      # nothing to change
    ("c7", "USA", "California", "", None),              # both
    ("c8", "United States", "", "", "Hawaii"),          # an island-country specimen: never touched
]


def mysql(statement, database=None):
    """Rows of a statement sent the way the tool sends its own."""
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-u", USER] + ([database] if database else []) + [
        "--default-character-set=utf8mb4", "--batch", "--quick", "-N"]
    r = subprocess.run(cmd, input=statement.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=300)
    assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def value(v):
    return "null" if v is None else pf.sql_string(v) if isinstance(v, str) else str(v)


class World:
    """A database of its own with the place rows and the specimens above, and a project folder whose db.properties
    names the test login. charset: the character set of the specimen table; by default utf8mb4 with a collation that
    is the default of neither MySQL 8.4 nor MariaDB, so the specimen table and the tool's work table differ in it."""

    def __init__(self, name, charset="utf8mb4 collate utf8mb4_unicode_ci", specimens=SPECIMENS):
        self.database = "place_fix_test_" + name
        mysql("drop database if exists " + self.database + "; create database " + self.database + " default character set utf8mb4")
        self.sql("create table geolocale (name varchar(250), georank varchar(20), parent varchar(250), is_valid tinyint, valid_name varchar(250), "
                 "bioregion varchar(64), alt_bioregion varchar(64), is_island tinyint) default charset = utf8mb4")
        self.sql("create table specimen (code varchar(128) not null primary key, country varchar(64), adm1 varchar(128), bioregion varchar(64), "
                 "island_country varchar(64)) default charset = " + charset)
        self.sql("insert into geolocale values " + ", ".join("(" + ", ".join(value(v) for v in row) + ")" for row in PLACES))
        self.sql("insert into specimen values " + ", ".join("(" + ", ".join(value(v) for v in row) + ")" for row in specimens))
        self.project = tempfile.mkdtemp()
        os.makedirs(os.path.join(self.project, "etc"))
        with open(os.path.join(self.project, "etc", "db.properties"), "w", encoding="utf-8") as f:
            f.write("user=" + USER + "\npassword=" + PASSWORD + "\n")
        self.out = os.path.join(self.project, "out")
        self.runs = 0

    def sql(self, statement):
        return mysql(statement, self.database)

    def tool(self, *argv, before=None):
        """Runs the tool: (exit code or message, what it printed, the folder it wrote). before(sql): called before
        each statement the tool sends; it may change the database (an upload in between) or raise pf.SqlFailed."""
        self.runs += 1
        folder = self.out + str(self.runs)
        real, out = pf.run_sql, sys.stdout

        def run_sql(db, statement):
            if before is not None:
                before(statement)
            return real(db, statement)

        pf.run_sql, sys.stdout = run_sql, io.StringIO()
        try:
            try:
                result = pf.main(["--project", self.project, "--container", CONTAINER, "--database", self.database, "--out", folder] + list(argv))
            except SystemExit as e:
                result = e.code
            printed = sys.stdout.getvalue()
        finally:
            pf.run_sql, sys.stdout = real, out
        return result, printed, folder

    def specimens(self):
        """{code: (country, state, bioregion)} as stored; None for null."""
        rows = self.sql("select code, ifnull(country, '<null>'), ifnull(adm1, '<null>'), ifnull(bioregion, '<null>'), 'end' from specimen order by code")
        return {r[0]: tuple(None if x == "<null>" else x for x in r[1:4]) for r in rows}

    def tables(self):
        return sorted(r[0] for r in self.sql("show tables like 'place_fix%'"))

    def close(self):
        mysql("drop database if exists " + self.database)
        shutil.rmtree(self.project, ignore_errors=True)


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.reader(f))


def test_apply_and_rollback_leave_newer_values_alone():
    w = World("a")
    try:
        before = w.specimens()

        # 1. --apply. Between the backup and the update an upload loads c5 again, with another state.
        def an_upload(statement):
            if statement.startswith("update specimen s join place_fix_"):
                w.sql("update specimen set adm1 = 'Jalisco' where code = 'c5'")

        result, printed, folder = w.tool("--apply", before=an_upload)
        assert result == 0, (result, printed)
        now = w.specimens()
        assert now["c1"] == ("Mexico", "Chihuahua", "Nearctic") and now["c2"] == ("Mexico", "Chihuahua", "Nearctic"), now
        assert now["c3"] == ("United States", "California", "Nearctic") and now["c4"] == ("Canada", "", "Nearctic"), now
        assert now["c7"] == ("United States", "California", "Nearctic"), now
        # c5 is as the upload left it: in Jalisco, which has two bioregions, with no bioregion. Version 3 made it Nearctic.
        assert now["c5"] == ("Mexico", "Jalisco", ""), now["c5"]
        assert now["c6"] == before["c6"] and now["c8"] == before["c8"]
        table = [t for t in w.tables() if not t.endswith("_map")][0]
        assert w.tables() == [table, table + "_map"]
        assert w.sql("select code, applied from " + table + " order by code") == [["c1", "1"], ["c2", "1"], ["c3", "1"], ["c4", "1"], ["c5", "0"], ["c7", "1"]]
        assert "Specimens changed: 5. 1 changed since the backup, left as they are (an upload loaded them again" in printed, printed
        assert "still differ from the rule: 0 (as expected)" in printed, printed
        applied = {r[0]: r[6] for r in read_csv(os.path.join(folder, "specimens.csv"))[1:]}
        assert applied["c5"] == "no: changed since the backup, left as it is" and applied["c1"] == "yes" and len(applied) == 6, applied

        # 2. --rollback, days later. A curator has given c4 another bioregion; c7 is gone; c5 was never changed by the run.
        w.sql("update specimen set bioregion = 'Neotropical' where code = 'c4'; delete from specimen where code = 'c7'")
        result, printed, folder = w.tool("--rollback", table)
        assert result == 0, (result, printed)
        now = w.specimens()
        assert now["c1"] == before["c1"] and now["c3"] == before["c3"], now
        assert now["c2"] == ("México", "Chihuahua", None), now["c2"]              # the accent and the null are back
        assert now["c4"] == ("Canada", "", "Neotropical"), "the curator's value is overwritten: " + repr(now["c4"])
        assert now["c5"] == ("Mexico", "Jalisco", "") and "c7" not in now and now["c6"] == before["c6"] and now["c8"] == before["c8"], now
        assert "Specimens in the backup table: 6\n" in printed and "as they were before the fix): 3\n" in printed, printed
        assert "(rollback_skipped.csv): 3\n" in printed and "NOTE" not in printed, printed
        skipped = read_csv(os.path.join(folder, "rollback_skipped.csv"))
        assert skipped[0] == ["code", "old_country", "old_bioregion", "applied_country", "applied_bioregion", "country_now", "bioregion_now", "why"]
        assert skipped[1:] == [
            ["c4", "Canada", "", "Canada", "Nearctic", "Canada", "Neotropical", "has other values now than the run gave it: a later upload or a curator; left as it is"],
            ["c5", "Mexico", "", "Mexico", "Nearctic", "Mexico", "", "never changed by this run (it had changed between the backup and the update)"],
            ["c7", "USA", "", "United States", "Nearctic", "", "", "the specimen is no longer in AntWeb"]], skipped
        assert os.path.exists(os.path.join(folder, "report.txt")) and w.tables() == [table, table + "_map"]

        # a second rollback restores nothing and changes nothing
        result, printed, folder = w.tool("--rollback", table)
        assert result == 0 and "as they were before the fix): 0\n" in printed and "(rollback_skipped.csv): 6\n" in printed, printed
        assert w.specimens() == now
        why = {r[0]: r[7] for r in read_csv(os.path.join(folder, "rollback_skipped.csv"))[1:]}
        assert why["c1"] == why["c2"] == why["c3"] == "holds the old values already", why
    finally:
        w.close()


def version_3_apply(w, table):
    """What version 3 of the tool did on the servers on 2026-11-10: the same tables, a key on the code, and an update
    by the code alone. (Its hex comparisons had no convert; on a utf8mb4 table the bytes are the same.)"""
    places = pf.read_places(w.sql(pf.PLACES_SQL))
    changes, _ = pf.plan(places, pf.read_combinations(w.sql(pf.COMBINATIONS_SQL)))
    w.sql(";\n".join(pf.map_sql(table, changes)))
    w.sql(pf.backup_sql(table))
    w.sql("alter table " + table + " add primary key (code)")
    w.sql("update specimen s join " + table + " f on f.code = s.code set s.country = if(f.set_country = 1, f.new_country, s.country), "
          "s.bioregion = if(f.set_bioregion = 1, f.new_bioregion, s.bioregion)")


def test_rollback_of_the_tables_of_version_3():
    for name, without_new_values in (("b", False), ("c", True)):
        w = World(name)
        try:
            before = w.specimens()
            table = "place_fix_20261110_0901"
            version_3_apply(w, table)
            columns = [r[0] for r in w.sql("show columns from " + table)]
            assert columns == ["code", "old_country", "adm1", "old_bioregion", "set_country", "new_country", "set_bioregion", "new_bioregion", "reason"]
            assert w.specimens()["c5"] == ("Mexico", "Chihuahua", "Nearctic") and w.specimens()["c2"][0] == "Mexico"
            if without_new_values:          # a backup table that does not say what was applied: its _map table does
                w.sql("alter table " + table + " drop column set_country, drop column new_country, drop column set_bioregion, drop column new_bioregion")
            # since the fix: an upload has loaded c1 again as the curator's file has it now, and c3 with the old country name
            w.sql("update specimen set adm1 = 'Jalisco', bioregion = 'Neotropical' where code = 'c1'; update specimen set country = 'USA' where code = 'c3'")
            result, printed, folder = w.tool("--rollback", table)
            assert result == 0, (result, printed)
            now = w.specimens()
            assert now["c1"] == ("Mexico", "Jalisco", "Neotropical"), "newer values overwritten: " + repr(now["c1"])
            assert now["c2"] == before["c2"] and now["c4"] == before["c4"] and now["c5"] == before["c5"] and now["c7"] == before["c7"], now
            assert now["c3"] == before["c3"] and now["c6"] == before["c6"] and now["c8"] == before["c8"], now
            assert "Specimens in the backup table: 6\n" in printed and "as they were before the fix): 4\n" in printed, printed
            skipped = read_csv(os.path.join(folder, "rollback_skipped.csv"))[1:]
            assert [(r[0], r[7]) for r in skipped] == [
                ("c1", "has other values now than the run gave it: a later upload or a curator; left as it is"), ("c3", "holds the old values already")], skipped
            assert skipped[0][1:7] == ["Mexico", "", "Mexico", "Nearctic", "Mexico", "Neotropical"], skipped[0]
            # the table itself is as it was: no column was added to it
            assert [r[0] for r in w.sql("show columns from " + table)] == [c for c in columns if not (without_new_values and c in pf.NEW_COLUMNS)]
        finally:
            w.close()


def test_a_latin1_table_and_values_as_long_as_the_columns():
    long_country = "\U0001F41C" * 64                    # 64 characters of four bytes: 512 hex digits
    long_state = "é" * 128
    w = World("d", "latin1")
    try:
        # the bytes of the accent differ between the table (latin1: E9) and Python (UTF-8: C3A9); converted, they are one
        assert w.sql("select hex(country) from specimen where code = 'c2'") == [["4DE97869636F"]]
        assert w.sql("select " + pf.hx("country") + " from specimen where code = 'c2'") == [["4DC3A97869636F"]]
        result, printed, _ = w.tool("--apply")
        assert result == 0, (result, printed)
        now = w.specimens()
        assert now["c2"] == ("Mexico", "Chihuahua", "Nearctic") and now["c7"] == ("United States", "California", "Nearctic"), now
        assert "Specimens changed: 6. 0 changed since the backup, left as they are.\n" in printed, printed
        table = [t for t in w.tables() if not t.endswith("_map")][0]
        result, printed, _ = w.tool("--rollback", table)
        assert "as they were before the fix): 6\n" in printed and w.specimens()["c2"] == ("México", "Chihuahua", None), printed
    finally:
        w.close()
    # the longest values the specimen columns can hold fit the work table (hc was varchar(400): 512 hex digits did not)
    w = World("e", specimens=SPECIMENS + [("c9", "Canada", long_state, "", None)])
    try:
        w.sql("insert into geolocale values (" + value(long_country) + ", 'country', 'X', 0, 'Canada', null, null, 0)")
        w.sql("insert into specimen values ('c10', " + value(long_country) + ", " + value(long_state) + ", " + value("x" * 64) + ", null)")
        result, printed, _ = w.tool("--apply")
        assert result == 0, (result, printed)
        now = w.specimens()
        assert now["c10"] == ("Canada", long_state, "Nearctic") and now["c9"] == ("Canada", long_state, "Nearctic"), (now["c9"], now["c10"])
        table = [t for t in w.tables() if not t.endswith("_map")][0]
        assert w.sql("select max(length(hc)), max(length(ha)), max(length(hb)) from " + table + "_map") == [["512", "512", "128"]]
    finally:
        w.close()


def test_an_apply_that_fails_before_the_update_leaves_nothing():
    w = World("f")
    try:
        before = w.specimens()
        for step in (" as select s.code", "alter table place_fix_"):

            def fail(statement, step=step):
                if step in statement:
                    raise pf.SqlFailed("ERROR 1114 (HY000): The table is full")

            result, printed, _ = w.tool("--apply", before=fail)
            assert isinstance(result, str) and result.startswith("Nothing changed: the backup could not be made (ERROR 1114"), result
            assert result.endswith("The tables of this run were dropped."), result
            assert w.tables() == [] and w.specimens() == before, (w.tables(), w.specimens())
        # the count differs (a specimen arrives between the report and the backup): the same
        def arrive(statement):
            if statement.startswith("create table place_fix_") and "_map" in statement and "insert into" in statement:
                w.sql("insert into specimen values ('c11', 'Canada', '', '', null)")

        result, printed, _ = w.tool("--apply", before=arrive)
        assert "the backup holds 6 specimens, the report counted 5" not in result and "the backup holds 7 specimens, the report counted 6" in result, result
        assert result.endswith("The tables of this run were dropped.") and w.tables() == []
        assert {k: v for k, v in w.specimens().items() if k != "c11"} == before
    finally:
        w.close()


if __name__ == "__main__":
    if not CONTAINER:
        print("skipped: no database (set PLACE_FIX_TEST_CONTAINER to a MySQL container; see the top of this file)")
        sys.exit(0)
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed (MySQL " + mysql("select version()")[0][0] + ")")

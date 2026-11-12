"""tools/place/place_fix.py: the country and bioregion stored with a specimen, brought up to today's upload rule
(2026-11-10, Brian Fisher: the place data must be right and reproducible before an extract goes to collaborators).

The rule is tested without a database, on the cases production showed on 2026-11-10: a state whose second
bioregion is the text "none" (Chihuahua: 1,340 specimens blank), a state filed under an old name (Nuevo Leon),
a state with two bioregions (Jalisco: stays), a country spelled with an accent (México), a country with two
bioregions and no state, an island country, no country. The SQL of --apply is tested for what it must contain:
the backup before the update, exact (hex) matching, and a rollback that restores both fields.

Review of PR #341 (2026-11-10): the update and the rollback change a specimen only when it still holds what the
backup recorded (the update) or what the run gave it (the rollback); the bytes are compared as UTF-8 whatever the
column's character set; the work table's hex columns cannot overflow; an --apply that fails before the update leaves
no table behind. Here: the SQL and the steps of the tool against a pretended database. The same against a real
MySQL, with a row changed between the backup and the update and one changed before the rollback:
tests/unit/test_place_fix_db.py.

Run: python3 tests/unit/test_place_fix.py   (also in .github/workflows/tests.yaml)
"""
import csv
import importlib.util
import os
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
spec = importlib.util.spec_from_file_location("place_fix", os.path.join(ROOT, "tools", "place", "place_fix.py"))
pf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pf)
KEEP = pf.KEEP


def places():
    P = pf.Place
    return pf.Places([
        P("Canada", "country", "Northern America", 1, "", "Nearctic", "", 0),
        P("Mexico", "country", "Central America", 1, "", "Neotropical", "Nearctic", 0),
        P("United States", "country", "Northern America", 1, "", "Nearctic", "Oceania", 0),
        P("USA", "country", "Northern America", 0, "United States", "", "", 0),
        P("Panama", "country", "Central America", 1, "", "Neotropical", "", 0),
        P("Hawaii", "country", "Polynesia", 1, "", "Oceania", "", 1),
        P("Zaire", "country", "Middle Africa", 0, "", "Afrotropical", "", 0),
        P("Kanada", "country", "Northern America", 0, "Canada", "", "", 0),
        P("Old Panama", "country", "Central America", 0, "Panama", "Nearctic", "", 0),
        P("Lost Name", "country", "Middle Africa", 0, "Zaire", "Nearctic", "", 0),
        P("Chihuahua", "adm1", "Mexico", 1, "", "Nearctic", "none", 0),
        P("Jalisco", "adm1", "Mexico", 1, "", "Neotropical", "Nearctic", 0),
        P("Nuevo León", "adm1", "Mexico", 1, "", "Nearctic", "none", 0),
        P("Nuevo Leon", "adm1", "Mexico", 0, "Nuevo León", "Neotropical", "", 0),
        P("Baja California", "adm1", "Mexico", 1, "none", "Nearctic", "none", 0),
        P("Baja California Norte", "adm1", "Mexico", 0, "Baja California", "Neotropical", "", 0),
        P("Old Lost State", "adm1", "Mexico", 0, "", "Neotropical", "", 0),
        P("California", "adm1", "United States", 1, "", "Nearctic", "", 0),
        P("Ontario", "adm1", "Canada", 1, "", "", "", 0),
        P("Old Canadian Name", "adm1", "Canada", 0, "", "Oceania", "", 0),
    ])


def d(country, adm1, bioregion, island=False):
    return pf.decide(places(), country, adm1, bioregion, island)


def test_none_text_is_no_second_bioregion():
    r = d("Mexico", "Chihuahua", "")
    assert r.new_country is KEEP and r.new_bioregion == "Nearctic" and r.problem is None, r
    assert r.reasons == ["bioregion: the state's (was blank)"], r.reasons


def test_old_state_name_takes_the_valid_states_bioregion_not_its_own():
    assert d("Mexico", "Nuevo Leon", "").new_bioregion == "Nearctic"
    assert d("Mexico", "Baja California Norte", "Neotropical").new_bioregion == "Nearctic"
    # an old name that points to no valid state defines nothing (Irian Jaya said Indomalaya, the map Australasia)
    r = d("Mexico", "Old Lost State", "")
    assert r.new_bioregion is KEEP, r
    assert r.problem == "no bioregion: an old state name with no valid name in AntWeb, and the country has two bioregions", r
    assert d("Mexico", "Old Lost State", "Nearctic") == pf.Decision(KEEP, KEEP, [], None)
    # in a country with one bioregion the country decides, not the old row's own value
    assert d("Canada", "Old Canadian Name", "").new_bioregion == "Nearctic"


def test_state_with_two_bioregions_keeps_what_is_stored():
    r = d("Mexico", "Jalisco", "")
    assert r.new_bioregion is KEEP and r.problem == "no bioregion: the state has two bioregions", r
    r = d("Mexico", "Jalisco", "Nearctic")
    assert r.new_bioregion is KEEP and r.problem is None, r


def test_country_with_two_bioregions_and_no_or_unknown_state():
    assert d("United States", "", "").problem == "no bioregion: no state, and the country has two bioregions"
    assert d("Mexico", "Cancun", "").problem == "no bioregion: state not known to AntWeb, and the country has two bioregions"
    r = d("United States", "", "Indomalaya")
    assert r.new_bioregion is KEEP and r.problem == "bioregion Indomalaya is not one of the country's", r
    assert d("United States", "", "Oceania").problem is None


def test_country_with_one_bioregion():
    assert d("Canada", "", "").new_bioregion == "Nearctic"
    assert d("Canada", "Ontario", "Nearctic") == pf.Decision(KEEP, KEEP, [], None)
    r = d("Canada", "Nowhere", "Neotropical")
    assert r.new_bioregion == "Nearctic" and r.reasons == ["bioregion: the country's (was Neotropical)"], r


def test_country_spelling_and_old_name():
    r = d("México", "Chihuahua", "")
    assert r.new_country == "Mexico" and r.new_bioregion == "Nearctic", r
    assert r.reasons == ["country: spelling of Mexico", "bioregion: the state's (was blank)"], r.reasons
    r = d("USA", "California", "Nearctic")
    assert r.new_country == "United States" and r.new_bioregion is KEEP and r.reasons == ["country: old name of United States"], r
    assert d("  panamá ", "", "Neotropical").new_country == "Panama"
    # nothing is guessed: a different word, an old name that points nowhere, a blank
    assert d("Equador", "", "") == pf.Decision(KEEP, KEEP, [], "country not known to AntWeb")
    assert d("Zaire", "", "") == pf.Decision(KEEP, KEEP, [], "country not known to AntWeb")
    assert d("", "Chihuahua", "") == pf.Decision(KEEP, KEEP, [], "no country")


def test_old_country_name_takes_the_valid_countrys_bioregion():
    """Review of PR #341: the country's bioregion is read from the VALID country's row, whatever name the record has.
    The same cases, with the same answers, in PlaceBioregionTest.testOldCountryNameTakesTheValidCountrysBioregion
    (GeolocaleMgr.geolocaleBioregion): the upload and this tool are pinned to one answer."""
    # an old name whose own row has no bioregion: the valid country's one bioregion
    r = d("Kanada", "", "")
    assert r.new_country == "Canada" and r.new_bioregion == "Nearctic" and r.problem is None, r
    assert d("Kanada", "Ontario", "").new_bioregion == "Nearctic" and d("Kanada", "Nowhere", "").new_bioregion == "Nearctic"
    # an old name whose own row holds another bioregion than the valid country: the valid country's, not the old row's
    r = d("Old Panama", "", "")
    assert r.new_country == "Panama" and r.new_bioregion == "Neotropical", r
    assert d("Old Panama", "", "Nearctic").new_bioregion == "Neotropical"
    # an old name of a country with two bioregions and no state: nothing (the stored value stays)
    assert d("USA", "", "").new_bioregion is KEEP and d("USA", "California", "").new_bioregion == "Nearctic"
    # a row that is not valid and points to no valid country defines nothing, whatever bioregion it holds
    assert d("Zaire", "", "").new_bioregion is KEEP and d("Zaire", "", "").problem == "country not known to AntWeb"
    assert d("Lost Name", "", "") == pf.Decision(KEEP, KEEP, [], "country not known to AntWeb")     # an old name of a row that is not valid


def test_islands_are_left_alone():
    assert d("United States", "", "Oceania", island=True) == pf.Decision(KEEP, KEEP, [], None)
    assert d("United States", "Hawaii", "").problem.startswith("an island AntWeb treats as a country")


def test_the_state_is_never_changed():
    for adm1 in ("Nuevo Leon", "Baja California Norte", "Cancun"):
        r = d("Mexico", adm1, "")
        assert not hasattr(r, "new_adm1")
    assert "adm1 =" not in pf.update_sql("place_fix_20261110_0900").split(" set ", 1)[1].split(" where ")[0]


def test_plan_and_report():
    C = pf.Combination
    combos = [C("Mexico", "Chihuahua", "", False, 1340), C("México", "", "", False, 3), C("Mexico", "Jalisco", "", False, 174),
              C("", "", "", False, 1909), C("Canada", "Ontario", "Nearctic", False, 7000), C("United States", "", "Oceania", True, 248)]
    changes, not_fixed = pf.plan(places(), combos)
    assert [(c.country, c.specimens) for c, _ in changes] == [("Mexico", 1340), ("México", 3)], changes
    assert sorted(c.specimens for c, _ in not_fixed) == [3, 174, 1909], not_fixed
    text = pf.report_text("2026-11-10 09:00", "abc1234", changes, not_fixed)
    assert "differs from what an upload today would store: 1,343" in text, text
    assert "1,340  bioregion: the state's" in text and "3  country: spelling of Mexico" in text, text
    assert "1,909  no country" in text, text
    applied = pf.report_text("2026-11-10 09:00", "abc1234", changes, not_fixed, after=0, table="place_fix_20261110_0900")
    assert "--rollback place_fix_20261110_0900" in applied and "still differ from the rule: 0 (as expected)" in applied, applied
    assert "NOT ZERO" in pf.report_text("x", "y", changes, not_fixed, after=5, table="place_fix_20261110_0900")


def test_only_some_countries():
    C = pf.Combination
    combos = [C("Mexico", "Chihuahua", "", False, 1340), C("México", "Chihuahua", "", False, 3), C("Canada", "", "", False, 2),
              C("", "", "", False, 9)]
    changes, not_fixed = pf.plan(places(), combos, only={"Mexico"})
    # México becomes Mexico, so it belongs to Mexico; Canada is left for another day; the curators' list is whole
    assert sorted(c.specimens for c, _ in changes) == [3, 1340], changes
    assert [c.specimens for c, _ in not_fixed] == [9], not_fixed
    text = pf.report_text("x", "y", changes, not_fixed, only={"Mexico"})
    assert "Limited to these countries (--countries): Mexico." in text, text
    assert len(pf.plan(places(), combos)[0]) == 3


HX = "hex(convert(ifnull(%s, '') using utf8mb4))"
T = "place_fix_20261110_0900"
V3_COLUMNS = ["code", "old_country", "adm1", "old_bioregion", "set_country", "new_country", "set_bioregion", "new_bioregion", "reason"]


def test_sql_of_apply():
    C = pf.Combination
    changes, _ = pf.plan(places(), [C("M\u00e9xico", "Chihuahua", "", False, 3), C("Mexico", "O'Brien", "x", False, 1)])
    stmts = pf.map_sql(T, changes)
    assert stmts[0].startswith("create table place_fix_20261110_0900_map ")
    assert len(stmts) == 2, stmts
    # the stored values go in as hex: M\u00e9xico is not Mexico, and no quote of a name reaches the SQL
    assert "'4DC3A97869636F', '436869687561687561', ''" in stmts[1], stmts[1]
    assert "1, 'Mexico', 1, 'Nearctic'" in stmts[1], stmts[1]
    # the hex columns are text: specimen.country is varchar(64), up to 512 hex digits in utf8mb4; adm1 varchar(128), 1,024
    assert "hc text not null, ha text not null, hb text not null" in stmts[0] and "reason text" in stmts[0] and "varchar(4" not in stmts[0]
    assert "key (hc(100), ha(100))" in stmts[0]
    long_country = "\U0001F41C" * 64                              # 64 characters of four bytes each
    assert len(pf.hexed(long_country)) == 512
    backup = pf.backup_sql(T)
    assert "old_country" in backup and "old_bioregion" in backup
    assert "m.hc = " + HX % "s.country" + " and m.ha = " + HX % "s.adm1" + " and m.hb = " + HX % "s.bioregion" in backup, backup
    assert "where not (s.island_country is not null and s.island_country <> '')" in backup
    assert pf.backup_key_sql(T) == "alter table " + T + " add primary key (code), add column applied tinyint not null default 0"
    assert pf.TABLE_NAME.match(T) and not pf.TABLE_NAME.match("specimen; drop table x")
    assert pf.sql_string("O'Br\\ien") == "'O''Br\\\\ien'"


def test_bytes_are_compared_as_utf8_whatever_the_columns_character_set():
    """Review of PR #341: hex(ifnull(col, '')) gives the bytes of the column's own character set (latin1: E9 for
    the \u00e9 of M\u00e9xico), Python reads and writes UTF-8 (C3A9), and no accented combination would match. Every
    place the tool compares or carries place text as hex converts to utf8mb4 first, as specimen_extract.count_sql does."""
    assert pf.hx("s.country") == HX % "s.country"
    statements = [pf.PLACES_SQL, pf.COMBINATIONS_SQL, pf.backup_sql(T), pf.update_sql(T), pf.rollback_sql(T), pf.rollback_skipped_sql(T),
                  pf.rollback_sql(T, ["code", "old_country", "adm1", "old_bioregion"]), pf.rollback_skipped_sql(T, V3_COLUMNS + ["applied"])]
    for sql in statements:
        assert "hex(ifnull(" not in sql, sql
        assert sql.count("hex(") == sql.count("hex(convert(ifnull(") > 0, sql
    assert pf.COMBINATIONS_SQL.startswith("select " + HX % "s.country" + ", " + HX % "s.adm1" + ", " + HX % "s.bioregion" + ", ")
    assert pf.PLACES_SQL.startswith("select " + HX % "name" + ", georank, " + HX % "parent" + ", is_valid, " + HX % "valid_name")
    source = open(os.path.join(ROOT, "tools", "place", "place_fix.py"), encoding="utf-8").read()
    assert "hex(ifnull(" not in source.split('"""', 2)[2]


def test_the_update_changes_only_rows_that_are_as_the_backup_has_them():
    """Review of PR #341: the update matched on the code alone. A specimen that an upload loaded again between the
    backup and the update (the CAS TaxonWorks upload, every day) got the country and bioregion worked out for its
    old values. Now its country, state and bioregion must still be, byte for byte, what the backup recorded."""
    sql = pf.update_sql(T)
    assert sql.startswith("update specimen s join " + T + " f on f.code = s.code set ")
    where = sql.split(" where ", 1)[1]
    assert where == (HX % "s.country" + " = " + HX % "f.old_country" + " and " + HX % "s.adm1" + " = " + HX % "f.adm1" + " and "
                     + HX % "s.bioregion" + " = " + HX % "f.old_bioregion" + " and not (s.island_country is not null and s.island_country <> '')"), where
    # the row is marked in the backup table when, and only when, the specimen was changed
    assert "f.applied = 1 where " in sql
    assert ("set s.country = if(f.set_country = 1, convert(f.new_country using utf8mb4), convert(s.country using utf8mb4)), "
            "s.bioregion = if(f.set_bioregion = 1, convert(f.new_bioregion using utf8mb4), convert(s.bioregion using utf8mb4)), f.applied = 1") in sql


def test_the_rollback_restores_only_rows_that_still_hold_what_the_run_gave_them():
    """Review of PR #341: the rollback put the old values back on every code of the table, days later, over whatever
    an upload or a curator had stored since. Now the specimen must still hold the country and bioregion the run gave it."""
    # the choice between the new and the old value is made between their hex forms (the two columns may differ in collation)
    applied_country = "if(f.set_country = 1, " + HX % "f.new_country" + ", " + HX % "f.old_country" + ")"
    applied_bioregion = "if(f.set_bioregion = 1, " + HX % "f.new_bioregion" + ", " + HX % "f.old_bioregion" + ")"
    # and it must still be in the state the backup recorded (review of 2026-11-10: another state can give the same bioregion)
    guard = (HX % "s.country" + " = " + applied_country + " and " + HX % "s.adm1" + " = " + HX % "f.adm1" + " and "
             + HX % "s.bioregion" + " = " + applied_bioregion)
    # a table of versions 1 to 3 (production place_fix_20261110_0901, staging _0840 and _0857): the new values are in the backup table itself
    sql = pf.rollback_sql(T, V3_COLUMNS)
    assert sql == ("update specimen s join " + T + " f on f.code = s.code set s.country = f.old_country, s.bioregion = f.old_bioregion where "
                   + guard + "; select row_count()"), sql
    # a table of version 4: also only the rows the update did change
    assert pf.rollback_sql(T, V3_COLUMNS + ["applied"]).endswith(guard + " and f.applied = 1; select row_count()")
    # a backup table without the new values: joined to its _map table by the old country, state and bioregion
    joined = pf.rollback_sql(T, ["code", "old_country", "adm1", "old_bioregion"])
    assert (" join " + T + "_map m on m.hc = " + HX % "f.old_country" + " and m.ha = " + HX % "f.adm1" + " and m.hb = " + HX % "f.old_bioregion"
            + " set s.country = f.old_country") in joined, joined
    assert "if(m.set_country = 1, " + HX % "m.new_country" + ", " + HX % "f.old_country" + ")" in joined and "f.set_country" not in joined
    # what is left alone is read first: the specimen is gone, or the condition of the update does not hold
    skipped = pf.rollback_skipped_sql(T, V3_COLUMNS)
    assert " left join specimen s on s.code = f.code where s.code is null or not (" + guard + ") order by f.code" in skipped, skipped
    h = pf.hexed
    row = lambda gone, country, bioregion, applied="1": ["c1", h("USA"), h(""), h("United States"), h("Nearctic"), gone, h(country), h(bioregion), applied]
    assert pf.skipped_reason(row("1", "", "")) == "the specimen is no longer in AntWeb"
    assert pf.skipped_reason(row("0", "USA", "")) == "holds the old values already"
    assert pf.skipped_reason(row("0", "United States", "Oceania")) == "has other values now than the run gave it: a later upload or a curator; left as it is"
    assert pf.skipped_reason(row("0", "USA", "Neotropical", "0")).startswith("never changed by this run")
    # the tenth field: the state is still the one of the backup (1) or not (0)
    assert ", " + HX % "s.adm1" + " = " + HX % "f.adm1" + " from " + T + " f" in skipped, skipped
    assert pf.skipped_reason(row("0", "USA", "") + ["1"]) == "holds the old values already"
    assert pf.skipped_reason(row("0", "United States", "Nearctic") + ["0"]) == "is in another state now than when the fix ran: a later upload or a curator; left as it is"
    assert pf.skipped_reason(row("1", "", "") + ["NULL"]) == "the specimen is no longer in AntWeb"


class Pretend:
    """A database that answers the tool's statements from a list of (how the statement begins, or a test of it; rows
    or an error)."""

    def __init__(self, answers):
        self.answers, self.seen = answers, []

    def __call__(self, db, sql):
        self.seen.append(sql)
        for begins, answer in self.answers:
            if begins(sql) if callable(begins) else sql.startswith(begins):
                if isinstance(answer, Exception):
                    raise answer
                return answer
        return []

    def ran(self, begins):
        return [x for x in self.seen if x.startswith(begins)]


def pretend(answers, *argv):
    """Runs the tool with a pretended database: (exit code or message, what it printed is not kept, the Pretend, the folder)."""
    h = pf.hexed
    base = [(pf.PLACES_SQL, [[h("Canada"), "country", h(""), "1", h(""), h("Nearctic"), h(""), "0"],
                             [h("Ontario"), "adm1", h("Canada"), "1", h(""), h(""), h(""), "0"]]),
            (pf.COMBINATIONS_SQL, [[h("Canada"), h("Ontario"), h(""), "0", "3"]]), ("show tables like", [])]
    db = Pretend(answers + base)
    folder = tempfile.mkdtemp()
    saved, out = pf.run_sql, sys.stdout
    pf.run_sql = db
    sys.stdout = open(os.devnull, "w")
    try:
        try:
            result = pf.main(["--project", folder, "--out", os.path.join(folder, "out")] + list(argv))
        except SystemExit as e:
            result = e.code
    finally:
        sys.stdout.close()
        pf.run_sql, sys.stdout = saved, out
    return result, db, os.path.join(folder, "out")


def test_a_failed_apply_leaves_nothing_half_made():
    """Review of PR #341: a statement that failed half way through --apply (a value too long for the work table, in
    strict mode) left a half-filled _map table behind. Whatever fails before the update, the tables of the run are
    dropped, and the update is never sent."""
    the_map = lambda sql: sql.startswith("create table place_fix_") and "_map (hc text" in sql and "insert into" in sql
    the_backup = lambda sql: sql.startswith("create table place_fix_") and " as select s.code" in sql
    failures = [(the_map, "ERROR 1406 (22001): Data too long for column 'hc' at row 2", 1),      # the map and its inserts are one batch
                (the_backup, "ERROR 1114 (HY000): The table is full", 2),
                ("alter table", "ERROR 1062 (23000): Duplicate entry 'casent1' for key 'PRIMARY'", 4)]
    for failing, error, sent in failures:
        result, db, _ = pretend([(failing, pf.SqlFailed(error)), ("select count(*) from place_fix_", [["3"]])], "--apply")
        assert len(db.ran("create table place_fix_")) + len(db.ran("select count(*) from place_fix_")) + len(db.ran("alter table")) == sent, db.seen
        assert isinstance(result, str) and result.startswith("Nothing changed: the backup could not be made (ERROR "), result
        assert result.endswith("The tables of this run were dropped."), result
        dropped = db.ran("drop table if exists place_fix_")
        assert len(dropped) == 1 and dropped[0].count("drop table if exists") == 2 and "_map" in dropped[0], db.seen
        assert not db.ran("update specimen"), db.seen
        assert db.seen.index(dropped[0]) == len(db.seen) - 1          # the last thing sent
    # the backup holds another number of specimens than the report counted: the same, as before
    result, db, _ = pretend([("select count(*) from place_fix_", [["2"]])], "--apply")
    assert "the backup holds 2 specimens, the report counted 3" in result and result.endswith("The tables of this run were dropped."), result
    assert not db.ran("update specimen") and not db.ran("alter table") and len(db.ran("drop table if exists")) == 1
    # the tables cannot be dropped either: said, with their names
    result, db, _ = pretend([("create table place_fix_", pf.SqlFailed("ERROR 1")), ("drop table", pf.SqlFailed("ERROR 2"))], "--apply")
    assert "could NOT be dropped (ERROR 2): drop place_fix_" in result and "_map by hand." in result, result
    # a statement that fails anywhere else ends the run with mysql's message, not a traceback
    result, _, _ = pretend([(pf.COMBINATIONS_SQL, pf.SqlFailed("ERROR 2002: no server"))])
    assert result == "mysql failed: ERROR 2002: no server", result


def test_an_apply_counts_the_rows_that_changed_since_the_backup():
    """The steps of a good run, in order, and what the report says when the update left a row alone."""
    h = pf.hexed
    rows = [["casent1", h("Canada"), h(""), "0", h(""), h("Nearctic"), "1", h("bioregion: the country's (was blank)"), "1"],
            ["casent2", h("Canada"), h(""), "0", h(""), h("Nearctic"), "1", h("bioregion: the country's (was blank)"), "0"],
            ["casent3", h("Canada"), h(""), "0", h(""), h("Nearctic"), "1", h("bioregion: the country's (was blank)"), "1"]]
    result, db, out = pretend([("select count(*) from place_fix_20", [["3"]]), ("select code, ", rows)], "--apply")
    # 3 still differ in the pretended database, so the run ends with 1 (the check after the change)
    assert result == 1, result
    order = [next(i for i, x in enumerate(db.seen) if x.startswith(b)) for b in
             ("create table place_fix_", "alter table place_fix_", "update specimen s join place_fix_", "select code, ")]
    assert order == sorted(order) and db.seen[order[0] + 1].startswith("create table place_fix_") and " as select s.code" in db.seen[order[0] + 1]
    left = [x for x in db.seen if x.endswith(" where applied = 0")]
    assert len(left) == 1 and db.seen.index(left[0]) > order[2]
    # the pretended count answers 3 for "applied = 0" too: the report says so
    text = open(os.path.join(out, "report.txt"), encoding="utf-8").read()
    assert "Specimens changed: 0. 3 changed since the backup, left as they are (an upload loaded them again" in text, text
    assert "Run the tool again for them." in text
    with open(os.path.join(out, "specimens.csv"), encoding="utf-8", newline="") as f:
        lines = list(csv.reader(f))
    assert lines[0] == ["code", "old_country", "new_country", "old_bioregion", "new_bioregion", "reason", "applied"]
    assert [x[0] + ":" + x[6] for x in lines[1:]] == ["casent1:yes", "casent2:no: changed since the backup, left as it is", "casent3:yes"]
    # none left: one plain sentence
    plain = pf.report_text("x", "y", [], [], after=0, table=T, left=0)
    assert "Specimens changed: 0. 0 changed since the backup, left as they are.\n" in plain and "Run the tool again" not in plain


def test_a_rollback_counts_and_lists_what_it_leaves_alone():
    h = pf.hexed
    skipped = [["casent2", h("USA"), h(""), h("United States"), h("Nearctic"), "0", h("United States"), h("Oceania"), "1"],
               ["casent9", h("USA"), h(""), h("United States"), h("Nearctic"), "1", h(""), h(""), "1"]]
    answers = [("show columns from " + T, [[c, "varchar(64)"] for c in V3_COLUMNS]), ("select count(*) from " + T, [["5"]]),
               ("select f.code, ", skipped), ("update specimen s join " + T, [["3"]])]
    result, db, out = pretend(answers, "--rollback", T)
    assert result == 0, result
    # what is left alone is read before the update; a version 3 table needs no join and no column applied
    assert db.seen.index(db.ran("select f.code, ")[0]) < db.seen.index(db.ran("update specimen")[0])
    assert "_map" not in db.ran("update specimen")[0] and "applied" not in db.ran("update specimen")[0]
    text = open(os.path.join(out, "report.txt"), encoding="utf-8").read()
    assert "Specimens in the backup table: 5\n" in text and "as they were before the fix): 3\n" in text, text
    assert "(rollback_skipped.csv): 2\n" in text and "Table " + T + " kept." in text and "NOTE" not in text, text
    assert "          1  the specimen is no longer in AntWeb\n" in text
    with open(os.path.join(out, "rollback_skipped.csv"), encoding="utf-8", newline="") as f:
        lines = list(csv.reader(f))
    assert lines == [["code", "old_country", "old_bioregion", "applied_country", "applied_bioregion", "country_now", "bioregion_now", "why"],
                     ["casent2", "USA", "", "United States", "Nearctic", "United States", "Oceania",
                      "has other values now than the run gave it: a later upload or a curator; left as it is"],
                     ["casent9", "USA", "", "United States", "Nearctic", "", "", "the specimen is no longer in AntWeb"]], lines
    # restored and left do not add up: said
    answers[3] = ("update specimen s join " + T, [["2"]])
    _, _, out = pretend(answers, "--rollback", T)
    assert "NOTE: restored and left do not add up to the table (4 of 5)" in open(os.path.join(out, "report.txt"), encoding="utf-8").read()
    # a table that does not say what was applied, and no _map table: nothing is sent
    result, db, _ = pretend([("show columns from " + T, [["code", "varchar(128)"], ["old_country", "varchar(64)"]])], "--rollback", T)
    assert "does not say what the run applied" in result and not db.ran("update specimen"), result
    result, _, _ = pretend([], "--rollback", "specimen")
    assert result == "--rollback: not a backup table name: 'specimen'"
    assert pretend([], "--database", "ant; drop table x")[0].startswith("--database: not a database name")


def test_names():
    assert pf.normal("  MÉXICO ") == "mexico" and pf.normal("Côte  d'Ivoire") == "cote d'ivoire"
    assert pf.clean_bioregion("none") is None and pf.clean_bioregion(" None ") is None and pf.clean_bioregion("null") is None
    assert pf.clean_bioregion("") is None and pf.clean_bioregion("Nearctic") == "Nearctic"
    assert pf.hexed("México") == "4DC3A97869636F" and pf.unhex("4DC3A97869636F") == "México" and pf.unhex("") == ""


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed")

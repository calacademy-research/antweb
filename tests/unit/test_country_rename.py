"""tools/place/country_rename.py: a country takes its current name everywhere AntWeb keeps the name (2026-11-10,
Brian Fisher: "we should update antweb to the most current names").

Without a database: what is decided for a pair of names (rename, swap with the row AntWeb already has for the new
name, already done, old name missing, and every case where nothing is done), the SQL of a run (one transaction, the
row of the new name steps aside before the country takes the name, names compared exactly, an apostrophe quoted,
the state of a specimen never changed) and of the rollback, and the report.

With a MySQL container (CI: the MySQL 8.4 of the staging refresh step; COUNTRY_RENAME_DB_CONTAINER and
COUNTRY_RENAME_DB_PASSWORD, database `ant`, utf8mb3_general_ci as production), on rows modelled on production's of
2026-11-10: the report changes nothing; --apply renames the seven countries (ids kept, old names kept as not-valid
rows, states, specimens, curators, page text, counts by name); a second run finds them done and carries a specimen
an upload stored under the old name meanwhile; a busy AntWeb stops --apply; a statement that fails undoes the whole
run; --rollback puts every table back as it was.

Run: python3 tests/unit/test_country_rename.py   (also in .github/workflows/tests.yaml)
"""
import http.server
import importlib.util
import os
import subprocess
import sys
import tempfile
import threading

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
TOOL = os.path.join(ROOT, "tools", "place", "country_rename.py")
spec = importlib.util.spec_from_file_location("country_rename", TOOL)
cr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cr)

R = cr.Row
IVORY = ("Ivory Coast", "Côte d'Ivoire")


def schema(tables=("geolocale", "specimen", "login_country", "object_edit", "object_hist", "place_genus_curation",
                   "place_specimen_name", "imaging_progress"), extra=()):
    rows = [(t, "InnoDB", "") for t in tables + tuple(extra)]
    rows += [("geolocale", "", c) for c in cr.REQUIRED["geolocale"] + ["authorimage"]]
    rows += [("specimen", "", c) for c in ("code", "country", "island_country", "adm1")]
    return cr.Schema(rows)


# ---------------------------------------------------------------- the decision

def test_the_seven_renames():
    assert len(cr.RENAMES) == 7 and len({o for o, _ in cr.RENAMES}) == 7 and len({n for _, n in cr.RENAMES}) == 7
    assert dict(cr.RENAMES)["Ivory Coast"] == "Côte d'Ivoire" and dict(cr.RENAMES)["Turkey"] == "Türkiye"
    assert dict(cr.RENAMES)["Democratic Republic of Congo"] == "Democratic Republic of the Congo"
    for old, new in cr.RENAMES:
        assert old != new and old == old.strip() and new == new.strip()


def test_decide_rename_swap_done():
    # no row of the new name: the country is renamed and the old name added
    assert cr.decide([R(40, "Swaziland", 1, "", 0)], "Swaziland", "Eswatini") == cr.Decision(cr.RENAME, 40, None, None)
    # AntWeb has the new name as a not-valid row that points to the old one (production: Czechia, id 8681): swap
    rows = [R(163, "Czech Republic", 1, "", 0), R(8681, "Czechia", 0, "Czech Republic", 0)]
    assert cr.decide(rows, "Czech Republic", "Czechia") == cr.Decision(cr.SWAP, 163, 8681, None)
    # ... also when that row points nowhere ("none" is stored as text)
    rows = [R(163, "Czech Republic", 1, "none", 0), R(8681, "Czechia", 0, "none", 0)]
    assert cr.decide(rows, "Czech Republic", "Czechia").state == cr.SWAP
    # after the rename: done; the country's row is the one of the new name
    rows = [R(8681, "Czech Republic", 0, "Czechia", 0), R(163, "Czechia", 1, "", 0)]
    assert cr.decide(rows, "Czech Republic", "Czechia") == cr.Decision(cr.DONE, 163, 8681, None)
    # renamed in Edit Geolocale, which keeps no row for the old name: it is added
    assert cr.decide([R(40, "Eswatini", 1, "", 0)], "Swaziland", "Eswatini") == cr.Decision(cr.ADD_OLD_NAME, 40, None, None)


def test_decide_nothing_is_done():
    def why(rows, old="Turkey", new="Türkiye"):
        d = cr.decide(rows, old, new)
        assert d.state is None and d.valid_id is None, d
        return d.problem
    assert why([]) == "AntWeb has no valid country named Turkey"
    assert why([R(1, "Turkey", 0, "", 0)]) == "AntWeb has no valid country named Turkey"
    assert why([R(1, "Türkiye", 0, "Turkey", 0)]) == "AntWeb has no valid country named Turkey"
    assert why([R(1, "Turkey", 1, "", 0), R(2, "Türkiye", 1, "", 0)]).startswith("both names are valid countries")
    assert why([R(1, "Turkey", 1, "", 0), R(2, "Türkiye", 0, "Turkmenistan", 0)]) == "Türkiye is an old name of Turkmenistan in AntWeb, not of Turkey"
    assert why([R(1, "Turkey", 0, "Cyprus", 0), R(2, "Türkiye", 1, "", 0)]).startswith("Turkey is a not-valid row that points to Cyprus")
    assert why([R(1, "Turkey", 1, "", 0), R(3, "Turkey", 0, "", 0)]) == "AntWeb has two country rows named Turkey"
    # AntWeb's island places are never renamed
    assert why([R(423, "Hawaii", 1, "", 1)], "Hawaii", "Hawai'i").startswith("an island place")
    assert why([R(1, "Turkey", 1, "", 0), R(2, "Türkiye", 0, "", 1)]).startswith("an island place")
    assert why([R(1, "Turkey", 1, "", 0)], "Turkey", "Turkey") == "the old and the new name must be two names"
    # names are compared exactly: another spelling is another name
    assert why([R(1, "turkey", 1, "", 0)]) == "AntWeb has no valid country named Turkey"


def plan(old, new, rows, **counts):
    return cr.Plan(old, new, cr.decide(rows, old, new), counts)


def test_plan_problems_and_page_text():
    rows = [R(48, "Ivory Coast", 1, "", 0), R(245, "Côte d'Ivoire", 0, "Ivory Coast", 0)]
    p = plan(*IVORY, rows, object_edit=3, object_hist=5)
    assert p.problem() is None and p.moves_text() is True and p.work()
    # the new name has page text already: nothing is renamed (as Edit Geolocale refuses)
    p = plan(*IVORY, rows, object_edit=3, text_new=1)
    assert p.moves_text() is None and "already has page text (1 fields)" in p.problem() and not p.work()
    # another place is also named so: the text stays with the name, the rename goes on (as Edit Geolocale)
    p = plan(*IVORY, rows, object_edit=3, same_name_other=1)
    assert p.moves_text() is False and p.problem() is None
    assert plan(*IVORY, rows).moves_text() is False
    # a state of the same name under both names
    p = plan(*IVORY, rows, adm1_same_name=2)
    assert p.problem().startswith("2 states of the same name exist under Ivory Coast and under Côte d'Ivoire")
    # already renamed: nothing to do, unless something is still under the old name
    done = [R(245, "Ivory Coast", 0, "Côte d'Ivoire", 0), R(48, "Côte d'Ivoire", 1, "", 0)]
    assert not plan(*IVORY, done).work()
    assert plan(*IVORY, done, specimen_rows=4).work()
    # ... page text written under the old name meanwhile stays there when the new name has its own
    p = plan(*IVORY, done, object_edit=1, text_new=2)
    assert p.problem() is None and p.moves_text() is False and not p.work()


# ---------------------------------------------------------------- the SQL

def test_names_are_compared_exactly_and_quoted():
    e = cr.exact("country", "Côte d'Ivoire")
    assert e == "(country = 'Côte d''Ivoire' and hex(convert(country using utf8mb4)) = '43C3B4746520642749766F697265')", e
    assert cr.unhex(cr.hexed("Türkiye")) == "Türkiye" and cr.unhex("NULL") == "" and cr.unhex("") == ""
    assert cr.sql_string("a\\b'c") == "'a\\\\b''c'"


def test_apply_sql_swap():
    s = schema()
    rows = [R(48, "Ivory Coast", 1, "", 0), R(245, "Côte d'Ivoire", 0, "Ivory Coast", 0)]
    p = plan(*IVORY, rows, object_edit=3, object_hist=5)
    sql = cr.apply_sql("country_rename_backup_20261110_1700", [p], s)
    assert sql[0] == "start transaction" and sql[-1] == "commit"
    assert not [q for q in sql if q.split()[0] in ("create", "alter", "drop", "truncate")], "a table statement would end the transaction"
    # names are unique per parent and rank: the row of the new name steps aside, the country takes the name, the row takes the old one
    assert sql[1] == "update geolocale set name = concat('~country_rename~', id) where id = 245"
    assert sql[2] == "update geolocale set name = 'Côte d''Ivoire' where id = 48"
    assert sql[3] == "update geolocale set name = 'Ivory Coast', valid_name = 'Côte d''Ivoire', is_valid = 0 where id = 245"
    assert not [q for q in sql if q.startswith("insert into geolocale")]
    text = ";\n".join(sql)
    for needed in ("update geolocale set valid_name = 'Côte d''Ivoire' where georank = 'country' and (valid_name = 'Ivory Coast'",
                   "update geolocale set parent = 'Côte d''Ivoire' where georank = 'adm1' and (parent = 'Ivory Coast'",
                   "update geolocale set country = 'Côte d''Ivoire' where (country = 'Ivory Coast'",
                   "update specimen set country = 'Côte d''Ivoire' where (country = 'Ivory Coast'",
                   "update specimen set island_country = 'Côte d''Ivoire' where (island_country = 'Ivory Coast'",
                   "delete a from login_country a join login_country b on b.login_id = a.login_id",
                   "update login_country set country = 'Côte d''Ivoire' where (country = 'Ivory Coast'",
                   "update object_edit set object_key = 'Côte d''Ivoire', created = created where (object_key = 'Ivory Coast'",
                   "update object_hist set object_key = 'Côte d''Ivoire', created = created where (object_key = 'Ivory Coast'",
                   "update ignore place_genus_curation set list_key = 'Côte d''Ivoire' where list_type = 'country' and (list_key = 'Ivory Coast'",
                   "delete from place_specimen_name where list_type = 'country' and (list_key = 'Ivory Coast'",
                   "update imaging_progress set place_name = 'Côte d''Ivoire' where place_type = 'country' and place_key = '48'",
                   "update country_rename_backup_20261110_1700 set text_moved = 1 where hex(old_name) = '49766F727920436F617374'"):
        assert needed in text, needed
    # every name match is exact; the state of a specimen is never changed
    for q in sql[1:-1]:
        if "Ivory Coast'" in q and " where " in q and "id = " not in q.split(" where ")[1][:8]:
            assert "hex(convert(" in q, q
    assert "adm1 =" not in text and "set adm1" not in text


def test_apply_sql_rename_adds_the_old_name():
    s = schema(tables=("geolocale", "specimen"))      # a small database: only the two tables every AntWeb has
    p = plan("Swaziland", "Eswatini", [R(40, "Swaziland", 1, "", 0)])
    sql = cr.apply_sql("country_rename_backup_20261110_1700", [p], s)
    assert sql[1] == "update geolocale set name = 'Eswatini' where id = 40"
    assert sql[2] == ("insert into geolocale (name, georank, is_valid, valid_name, parent, region, bioregion, alt_bioregion, source) "
                      "select 'Swaziland', 'country', 0, 'Eswatini', parent, region, bioregion, alt_bioregion, 'country_rename' from geolocale where id = 40")
    assert sql[3] == "update country_rename_backup_20261110_1700 set alias_id = last_insert_id(), alias_added = 1 where hex(old_name) = '5377617A696C616E64'"
    text = ";\n".join(sql)
    for absent in ("login_country", "object_edit", "place_genus_curation", "imaging_progress"):
        assert absent not in text, absent
    # already renamed: the country's row is not touched again
    done = plan("Swaziland", "Eswatini", [R(900, "Swaziland", 0, "Eswatini", 0), R(40, "Eswatini", 1, "", 0)], specimen_rows=2)
    sql = cr.apply_sql("t", [done], s)
    assert not [q for q in sql if "set name =" in q or q.startswith("insert")], sql
    assert [q for q in sql if q.startswith("update specimen set country = 'Eswatini'")]


def test_backup_sql():
    s = schema()
    rows = [R(48, "Ivory Coast", 1, "", 0), R(245, "Côte d'Ivoire", 0, "Ivory Coast", 0)]
    p = plan(*IVORY, rows, specimen_rows=2210, object_edit=3)
    q = plan("Swaziland", "Eswatini", [R(40, "Swaziland", 1, "", 0)], specimen_rows=255)
    sql = cr.backup_sql("country_rename_backup_20261110_1700", [p, q], s, "2026-11-10 17:00")
    assert sql[0].startswith("create table country_rename_backup_20261110_1700 (old_name")
    assert sql[1].endswith("values ('Ivory Coast', 'Côte d''Ivoire', 'swap', 48, 245, 2210, '2026-11-10 17:00')"), sql[1]
    assert sql[2].endswith("values ('Swaziland', 'Eswatini', 'rename', 40, null, 255, '2026-11-10 17:00')"), sql[2]
    made = [x.split()[2] for x in sql if x.startswith("create table")]
    assert made == cr.backup_tables("country_rename_backup_20261110_1700", s), made
    assert made[1:] == ["country_rename_backup_20261110_1700_" + t for t in ("geolocale", "specimen", "login_country", "object_edit",
                                                                       "object_hist", "place_genus_curation", "place_specimen_name")]
    geo = sql[3]
    assert "select id, name, valid_name, parent, country, is_valid from geolocale where" in geo and "id in (48, 245)" in geo and "id in (40)" in geo
    assert "select code, country, island_country from specimen where" in sql[4]
    # only the page text that moves is copied: Swaziland has none
    assert "Swaziland" not in sql[6] and "Ivory Coast" in sql[6]
    assert cr.TABLE_NAME.match("country_rename_backup_20261110_1700") and not cr.TABLE_NAME.match("specimen; drop table x")
    # the names say "dated backup" to the schema check (tools/datamodel/list_tables.py BACKUP_PATTERN), as its other backups do
    sys.path.insert(0, os.path.join(ROOT, "tools", "datamodel"))
    import list_tables
    for name in made:
        assert list_tables.BACKUP.search(name), name


def test_rollback_sql():
    t = "country_rename_backup_20261110_1700"
    s = schema(extra=(t + "_login_country", t + "_place_genus_curation", t + "_place_specimen_name"))
    record = cr.read_record([[cr.hexed("Ivory Coast"), cr.hexed("Côte d'Ivoire"), "swap", "48", "245", "0", "1"],
                             [cr.hexed("Swaziland"), cr.hexed("Eswatini"), "rename", "40", "9001", "1", "0"]])
    assert record[0]["text_moved"] and not record[0]["alias_added"] and record[1]["alias_added"] and record[1]["alias_id"] == 9001
    sql = cr.rollback_sql(t, record, s)
    assert sql[0] == "start transaction" and sql[-1] == "commit"
    # the added row goes; the swapped row steps aside, the countries take their old names, then it takes its own
    assert sql[1] == "delete from geolocale where id in (9001) and is_valid = 0 and georank = 'country'"
    assert sql[2] == "update geolocale set name = concat('~country_rename~', id) where id in (245)"
    assert sql[3].startswith("update geolocale g join " + t + "_geolocale b on b.id = g.id set g.name = b.name") and sql[3].endswith("where g.id not in (245)")
    assert sql[4].endswith("where g.id in (245)")
    text = ";\n".join(sql)
    # a specimen goes back only when it still has the new name (a curator may have corrected it since)
    assert ("update specimen s join " + t + "_specimen b on b.code = s.code set s.country = b.country where (b.country = 'Ivory Coast'") in text
    assert "and (s.country = 'Côte d''Ivoire' and hex(convert(s.country using utf8mb4))" in text
    assert "update object_edit set object_key = 'Ivory Coast', created = created where (object_key = 'Côte d''Ivoire'" in text
    assert "update object_edit set object_key = 'Swaziland'" not in text      # its text was not moved
    assert text.count("insert into login_country select * from " + t + "_login_country") == 1
    assert text.count("insert into place_specimen_name select * from " + t + "_place_specimen_name") == 1
    assert "update imaging_progress set place_name = 'Swaziland' where place_type = 'country' and place_key = '40'" in text


def test_report():
    rows = [R(48, "Ivory Coast", 1, "", 0), R(245, "Côte d'Ivoire", 0, "Ivory Coast", 0)]
    p = plan(*IVORY, rows, old_names=0, adm1_parent=34, geolocale_country=34, specimen_country=2210, specimen_rows=2210, specimen_island=0,
             login_country=2, object_edit=3, object_hist=5, place_genus_curation=61, place_specimen_name=540, imaging_progress=1,
             specimen_new=4, specimen_other_spelling=3, left_taxon=12)
    p.author_image = "author.jpg"
    q = plan("Turkey", "Türkiye", [])
    text = cr.report_text("2026-11-10 17:00", "abc1234", [p, q])
    assert "Renames: 2; possible: 1; specimens that change: 2,210" in text, text
    for line in ("Ivory Coast  ->  Côte d'Ivoire",
                 "the country's row and the row AntWeb has for the new name swap names (the country's row: id 48; the other row: id 245)",
                 "        1  geolocale             the country's row: its name",
                 "       34  geolocale             its states: their parent",
                 "    2,210  specimen              specimens: country",
                 "        2  login_country         curators assigned to the country",
                 "      540  place_specimen_name   specimen names counted for the country (by country name)",
                 "note: 4 specimens are stored under Côte d'Ivoire already",
                 "note: 3 specimens write Ivory Coast with other capitals or accents: not changed here",
                 "copy the folder web/speciesList/ivorycoast/ to web/speciesList/côted'ivoire/",
                 "note: 12 rows of taxon name Ivory Coast: not changed (AntCat's text, written again by the daily update)",
                 "Turkey  ->  Türkiye", "  NOT RENAMED: AntWeb has no valid country named Turkey"):
        assert line in text, line
    lines, counted = cr.rename_lines(p)
    assert ("specimen", "specimens: country", 2210) in counted and len(counted) == 13, counted
    applied = cr.report_text("2026-11-10 17:00", "abc1234", [p], table="country_rename_backup_20261110_1700", left_after=0)
    assert "python3 country_rename.py --rollback country_rename_backup_20261110_1700" in applied and "old name: 0 (as expected)" in applied
    assert "NOT ZERO" in cr.report_text("x", "", [p], table="t", left_after=2)
    assert "technical.do" in cr.RELOAD_TEXT and "logins" in cr.RELOAD_TEXT


def test_schema():
    s = schema(tables=("geolocale", "specimen", "object_edit"))
    assert s.has("object_edit") and not s.has("login_country") and s.has("specimen", "island_country") and not s.has("specimen", "nothing")
    assert s.missing() == [] and s.not_transactional() == []
    old = cr.Schema([("geolocale", "MyISAM", ""), ("specimen", "InnoDB", ""), ("geolocale", "", "id"), ("specimen", "", "code")])
    assert "geolocale.name" in old.missing() and "specimen.country" in old.missing() and old.not_transactional() == ["geolocale"]
    keys = [k for k, _ in cr.count_selects(plan("Swaziland", "Eswatini", [R(40, "Swaziland", 1, "", 0)]), s)]
    assert "object_edit" in keys and "text_new" in keys and "login_country" not in keys and "imaging_progress" not in keys


def test_chosen_renames():
    class A:
        rename, only = None, None
    assert cr.chosen_renames(A) == cr.RENAMES
    A.only = "Turkey, Swaziland"
    assert cr.chosen_renames(A) == [("Swaziland", "Eswatini"), ("Turkey", "Türkiye")]
    A.only, A.rename = "Burma", ["Burma=Myanmar"]
    assert cr.chosen_renames(A) == [("Burma", "Myanmar")]
    A.only, A.rename = None, ["Turkey = Turkiye"]
    assert cr.chosen_renames(A)[-1] == ("Turkey", "Turkiye") and len(cr.chosen_renames(A)) == 7


# ---------------------------------------------------------------- with a database

CONTAINER = os.environ.get("COUNTRY_RENAME_DB_CONTAINER")
PASSWORD = os.environ.get("COUNTRY_RENAME_DB_PASSWORD", "ci-root")


def mysql(sql, check=True):
    r = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-uroot", "ant",
                        "--default-character-set=utf8mb4", "-B", "-N"],
                       input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=120)
    if check:
        assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


# The place table as production has it for names: exact (binary) names, unique per parent and rank; the other tables
# compare text without capitals and accents. Ids and old-name rows as in production on 2026-11-10.
DB_SCHEMA = """
drop table if exists geolocale, specimen, login_country, object_edit, object_hist, place_genus_curation, place_specimen_name,
  imaging_progress, taxon, geolocale_taxon;
create table geolocale (id int not null auto_increment primary key, name varchar(250) character set utf8mb3 collate utf8mb3_bin,
  georank varchar(20), is_valid tinyint, is_un tinyint, source varchar(250), valid_name varchar(250), parent varchar(250),
  region varchar(250), bioregion varchar(64), alt_bioregion varchar(64), is_live tinyint, country varchar(250), is_island tinyint,
  authorimage varchar(100), specimen_count int, created timestamp null default current_timestamp,
  unique key geolocale_idx (name, parent(50), georank)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table specimen (code varchar(64) not null primary key, taxon_name varchar(128), country varchar(64), adm1 varchar(128),
  island_country varchar(64), bioregion varchar(64), key (country)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table login_country (login_id int not null, country varchar(250) not null, primary key (login_id, country))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table object_edit (object_key varchar(128) not null, title varchar(64) not null, content text,
  created timestamp not null default current_timestamp, unique key (object_key, title)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table object_hist (object_key varchar(128) not null, title varchar(64) not null, content text,
  created timestamp not null default current_timestamp on update current_timestamp) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table place_genus_curation (list_type varchar(16) not null, list_key varchar(128) not null, taxon_name varchar(128) not null,
  specimens int not null default 0, primary key (list_type, list_key, taxon_name)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table place_specimen_name (list_type varchar(16) not null, list_key varchar(128) not null, taxon_name varchar(128) not null,
  specimens int not null default 0, primary key (list_type, list_key, taxon_name)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table imaging_progress (place_type varchar(16) not null, place_key varchar(128) not null, place_name varchar(250),
  total int not null default 0, primary key (place_type, place_key)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table taxon (taxon_name varchar(128) not null primary key, country varchar(128)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table geolocale_taxon (geolocale_id int not null, taxon_name varchar(128) not null, primary key (geolocale_id, taxon_name))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
"""

DB_DATA = """
insert into geolocale (id, name, georank, is_valid, valid_name, parent, region, bioregion, country, is_island, authorimage) values
  (40, 'Swaziland', 'country', 1, null, 'Southern Africa', 'Africa', 'Afrotropical', null, null, null),
  (257, 'Macedonia', 'country', 1, null, 'Southern Europe', 'Europe', 'Palearctic', null, null, null),
  (1731, 'Republic of Macedonia', 'country', 0, 'Macedonia', 'Southern Europe', 'Europe', 'Palearctic', null, null, null),
  (203, 'The former Yugoslav Republic of Macedonia', 'country', 0, 'Macedonia', 'Southern Europe', 'Europe', 'Palearctic', null, null, null),
  (43, 'Cape Verde', 'country', 1, null, 'Western Africa', 'Africa', 'Afrotropical', null, null, null),
  (7265, 'Cabo Verde', 'country', 0, 'Cape Verde', 'Western Africa', 'Africa', 'Afrotropical', null, null, null),
  (163, 'Czech Republic', 'country', 1, null, 'Eastern Europe', 'Europe', 'Palearctic', null, null, null),
  (8681, 'Czechia', 'country', 0, 'Czech Republic', 'Eastern Europe', 'Europe', 'Palearctic', null, null, null),
  (48, 'Ivory Coast', 'country', 1, null, 'Western Africa', 'Africa', 'Afrotropical', null, null, 'author.jpg'),
  (245, 'Côte d''Ivoire', 'country', 0, 'Ivory Coast', 'Western Africa', 'Africa', 'Afrotropical', null, null, null),
  (25, 'Democratic Republic of Congo', 'country', 1, null, 'Middle Africa', 'Africa', 'Afrotropical', null, null, null),
  (247, 'Democratic Republic of the Congo', 'country', 0, 'Democratic Republic of Congo', 'Middle Africa', 'Africa', 'Afrotropical', null, null, null),
  (7298, 'Congo, Democratic Republic of the', 'country', 0, 'Democratic Republic of Congo', 'Middle Africa', 'Africa', 'Afrotropical', null, null, null),
  (24, 'Congo', 'country', 1, null, 'Middle Africa', 'Africa', 'Afrotropical', null, null, null),
  (158, 'Turkey', 'country', 1, null, 'Western Asia', 'Asia', 'Palearctic', null, null, null),
  (115, 'Turkmenistan', 'country', 1, null, 'Central Asia', 'Asia', 'Palearctic', null, null, null),
  (423, 'Hawaii', 'country', 1, 'none', 'Polynesia', 'Oceania', 'Oceania', 'United States', 1, null),
  (500, 'Lagunes', 'adm1', 1, null, 'Ivory Coast', null, 'Afrotropical', 'Ivory Coast', null, null),
  (501, 'Montagnes', 'adm1', 1, null, 'Ivory Coast', null, 'Afrotropical', 'Ivory Coast', null, null),
  (502, 'Praha', 'adm1', 1, null, 'Czech Republic', null, 'Palearctic', 'Czech Republic', null, null),
  (503, 'Hhohho', 'adm1', 1, null, 'Swaziland', null, 'Afrotropical', 'Swaziland', null, null),
  (504, 'Izmir', 'adm1', 1, null, 'Turkey', null, 'Palearctic', 'Turkey', null, null),
  (505, 'Lebap', 'adm1', 1, null, 'Turkmenistan', null, 'Palearctic', 'Turkmenistan', null, null),
  (506, 'Kongo Central', 'adm1', 1, null, 'Democratic Republic of Congo', null, 'Afrotropical', 'Democratic Republic of Congo', null, null),
  (507, 'Bas-Congo', 'adm1', 0, 'Kongo Central', 'Democratic Republic of Congo', null, 'Afrotropical', 'Democratic Republic of Congo', null, null),
  (508, 'Likouala', 'adm1', 1, null, 'Congo', null, 'Afrotropical', 'Congo', null, null),
  (509, 'Skopje', 'adm1', 1, null, 'Macedonia', null, 'Palearctic', 'Macedonia', null, null),
  (510, 'Praia', 'adm1', 1, null, 'Cape Verde', null, 'Afrotropical', 'Cape Verde', null, null);
insert into specimen (code, taxon_name, country, adm1, island_country, bioregion) values
  ('ci1', 'a', 'Ivory Coast', 'Lagunes', null, 'Afrotropical'), ('ci2', 'a', 'Ivory Coast', 'Montagnes', null, 'Afrotropical'),
  ('ci3', 'b', 'Ivory Coast', null, null, 'Afrotropical'), ('ci4', 'b', 'IVORY COAST', null, null, null),
  ('ci5', 'b', 'Côte d''Ivoire', null, null, null), ('ci6', 'b', 'Cote d''Ivoire', null, null, null),
  ('cz1', 'a', 'Czech Republic', 'Praha', null, 'Palearctic'), ('cz2', 'a', 'Czechia', null, null, null),
  ('sz1', 'a', 'Swaziland', 'Hhohho', null, 'Afrotropical'),
  ('tr1', 'a', 'Turkey', 'Izmir', null, 'Palearctic'), ('tr2', 'c', 'Turkey', null, null, 'Palearctic'),
  ('tm1', 'a', 'Turkmenistan', 'Lebap', null, 'Palearctic'),
  ('cd1', 'a', 'Democratic Republic of Congo', 'Kongo Central', null, 'Afrotropical'), ('cg1', 'a', 'Congo', 'Likouala', null, 'Afrotropical'),
  ('mk1', 'a', 'Macedonia', 'Skopje', null, 'Palearctic'), ('cv1', 'a', 'Cape Verde', 'Praia', null, 'Afrotropical'),
  ('cv2', 'a', 'Cabo Verde', null, null, 'Afrotropical'),
  ('hi1', 'a', 'United States', null, 'Hawaii', 'Oceania'), ('us1', 'a', 'United States', 'California', null, 'Nearctic');
insert into login_country values (7, 'Ivory Coast'), (8, 'Ivory Coast'), (8, 'Côte d''Ivoire'), (8, 'Turkey'), (9, 'Turkmenistan'), (9, 'Congo');
insert into object_edit (object_key, title, content, created) values
  ('Ivory Coast', 'overview', 'text of the country', '2020-01-02 03:04:05'), ('Ivory Coast', 'history', 'more', '2021-01-02 03:04:05'),
  ('Turkey', 'overview', 'text', '2019-01-02 03:04:05'), ('Turkmenistan', 'overview', 'stays', '2019-01-02 03:04:05'),
  ('Congo', 'overview', 'stays', '2019-01-02 03:04:05');
insert into object_hist (object_key, title, content, created) values
  ('Ivory Coast', 'overview', 'older text', '2018-05-06 07:08:09'), ('Congo', 'overview', 'older', '2018-05-06 07:08:09');
insert into place_genus_curation values ('country', 'Ivory Coast', 'myrmicinaepheidole', 3), ('country', 'Ivory Coast', 'formicinaecamponotus', 1),
  ('country', 'Turkey', 'myrmicinaepheidole', 2), ('country', 'Turkmenistan', 'myrmicinaepheidole', 1), ('adm1', '504', 'myrmicinaepheidole', 1),
  ('museum', 'Turkey', 'myrmicinaepheidole', 9);
insert into place_specimen_name values ('country', 'Ivory Coast', 'a', 2), ('country', 'Ivory Coast', 'b', 1), ('country', 'Côte d''Ivoire', 'b', 1),
  ('country', 'Turkey', 'a', 1), ('country', 'Congo', 'a', 1), ('country', 'Democratic Republic of Congo', 'a', 1);
insert into imaging_progress values ('country', '48', 'Ivory Coast', 10), ('country', '158', 'Turkey', 20), ('country', '115', 'Turkmenistan', 5),
  ('bioregion', 'Afrotropical', 'Afrotropical', 99);
insert into taxon values ('x', 'Ivory Coast'), ('y', 'Turkey');
insert into geolocale_taxon values (43, 'a'), (7265, 'a'), (7265, 'b'), (158, 'a');
"""

TABLES = ["geolocale", "specimen", "login_country", "object_edit", "object_hist", "place_genus_curation", "place_specimen_name",
          "imaging_progress", "taxon", "geolocale_taxon"]


def snapshot():
    """Every row of the tables, as text, in a fixed order (created of geolocale left out: the test's own clock)."""
    out = {}
    for t in TABLES:
        cols = [c[0] for c in mysql("select column_name from information_schema.columns where table_schema = database() and table_name = '"
                                    + t + "' order by ordinal_position") if not (t == "geolocale" and c[0] == "created")]
        out[t] = sorted(tuple(r) for r in mysql("select " + ", ".join("ifnull(hex(" + c + "), 'NULL')" for c in cols) + " from " + t))
    return out


def one(sql):
    rows = mysql(sql)
    return rows[0][0] if rows else None


def tool(project, *args):
    return subprocess.run([sys.executable, TOOL, "--project", project, "--container", CONTAINER, "--out", os.path.join(project, "out"),
                           "--app", "http://127.0.0.1:9"] + list(args), capture_output=True, text=True, timeout=300)


def drop_run_tables():
    names = [r[0] for r in mysql("show tables like 'country_rename_%'")]
    if names:
        mysql("drop table " + ", ".join(names))
    return names


class Busy(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b"busy: upload"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def test_with_a_database():
    if not CONTAINER:
        print("MySQL part skipped (COUNTRY_RENAME_DB_CONTAINER not set).")
        return
    mysql(DB_SCHEMA + DB_DATA)
    drop_run_tables()
    before = snapshot()
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=root\npassword=" + PASSWORD + "\n")

        # 1. the report: every rename is possible, every table and number is named, nothing is changed
        out = tool(d)
        assert out.returncode == 0, out.stderr + out.stdout
        assert "Renames: 7; possible: 7; specimens that change: 10" in out.stdout, out.stdout
        assert "Report only: nothing was changed." in out.stdout
        for line in ("Swaziland  ->  Eswatini", "the country's row takes the new name; a row is added for the old name (the country's row: id 40)",
                     "Czech Republic  ->  Czechia", "swap names (the country's row: id 163; the other row: id 8681)",
                     "        2  geolocale             other old names of the country: their valid name",      # Macedonia's two
                     "        3  specimen              specimens: country",                                    # ci1 to ci3, written exactly so
                     "note: 1 specimens are stored under Côte d'Ivoire already",                                # ci5 (ci6 has no accent)
                     "note: 1 specimens write Ivory Coast with other capitals or accents",                      # ci4
                     "copy the folder web/speciesList/ivorycoast/",
                     "note: the other row (id 7265) has 2 species list rows of its own",                        # Cabo Verde's old row
                     "note: 1 rows of taxon name Turkey: not changed"):
            assert line in out.stdout, (line, out.stdout)
        assert snapshot() == before and not mysql("show tables like 'country_rename_%'")
        assert os.path.exists(os.path.join(d, "out", "report.txt")) and os.path.exists(os.path.join(d, "out", "counts.csv"))

        # 2. AntWeb says an upload is running: --apply stops before anything is written
        server = http.server.HTTPServer(("127.0.0.1", 0), Busy)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        out = subprocess.run([sys.executable, TOOL, "--project", d, "--container", CONTAINER, "--out", os.path.join(d, "out"),
                              "--app", "http://127.0.0.1:" + str(server.server_address[1]), "--apply"], capture_output=True, text=True, timeout=300)
        server.shutdown()
        assert out.returncode != 0 and "AntWeb says \"busy: upload\"" in out.stderr, out.stderr + out.stdout
        assert snapshot() == before and not mysql("show tables like 'country_rename_%'")

        # 3. a statement fails in the middle (here: the database refuses the specimens, after the places were renamed):
        #    the whole run is undone
        mysql("create trigger country_rename_test before update on specimen for each row signal sqlstate '45000' set message_text = 'refused for the test'")
        out = tool(d, "--apply", "--only", "Swaziland,Turkey")
        mysql("drop trigger country_rename_test")
        assert out.returncode != 0 and "Nothing changed (the transaction was undone and the backup tables dropped)" in out.stderr, (out.stderr + out.stdout)[-600:]
        assert "refused for the test" in out.stderr
        assert snapshot() == before and not mysql("show tables like 'country_rename_%'")

        # 4. --apply
        out = tool(d, "--apply")
        assert out.returncode == 0, out.stderr + out.stdout
        assert "Applied. Record and backup tables: country_rename_backup_" in out.stdout and "old name: 0 (as expected)" in out.stdout, out.stdout
        assert "AntWeb did not answer at http://127.0.0.1:9" in out.stdout and "NOW reload the names AntWeb keeps in memory" in out.stdout
        table = [r[0] for r in mysql("show tables like 'country_rename_%'") if cr.TABLE_NAME.match(r[0])][0]
        # the countries keep their ids; the old names are not-valid rows that point to the new ones
        valid = {r[0]: r[1] for r in mysql("select id, name from geolocale where georank = 'country' and is_valid = 1")}
        assert valid["40"] == "Eswatini" and valid["257"] == "North Macedonia" and valid["43"] == "Cabo Verde" and valid["163"] == "Czechia"
        assert valid["48"] == "Côte d'Ivoire" and valid["25"] == "Democratic Republic of the Congo" and valid["158"] == "Türkiye"
        assert valid["24"] == "Congo" and valid["115"] == "Turkmenistan" and valid["423"] == "Hawaii" and len(valid) == 10
        old = {r[0]: (r[1], r[2], r[3]) for r in mysql("select name, valid_name, id, ifnull(source, '') from geolocale where georank = 'country' and is_valid = 0")}
        assert old["Czech Republic"][:2] == ("Czechia", "8681") and old["Ivory Coast"][:2] == ("Côte d'Ivoire", "245")
        assert old["Cape Verde"][:2] == ("Cabo Verde", "7265") and old["Democratic Republic of Congo"][:2] == ("Democratic Republic of the Congo", "247")
        assert old["Swaziland"][0] == "Eswatini" and old["Swaziland"][2] == "country_rename" and int(old["Swaziland"][1]) > 8681
        assert old["Macedonia"][0] == "North Macedonia" and old["Turkey"][0] == "Türkiye"
        assert old["Republic of Macedonia"][0] == "North Macedonia" and old["The former Yugoslav Republic of Macedonia"][0] == "North Macedonia"
        assert old["Congo, Democratic Republic of the"][0] == "Democratic Republic of the Congo" and len(old) == 10
        assert one("select concat(parent, '|', region, '|', bioregion) from geolocale where name = 'Swaziland'") == "Southern Africa|Africa|Afrotropical"
        # the states follow; a state is never renamed
        states = {r[0]: (r[1], r[2]) for r in mysql("select name, parent, country from geolocale where georank = 'adm1'")}
        assert states["Lagunes"] == ("Côte d'Ivoire", "Côte d'Ivoire") and states["Izmir"] == ("Türkiye", "Türkiye")
        assert states["Bas-Congo"] == ("Democratic Republic of the Congo",) * 2 and states["Likouala"] == ("Congo", "Congo")
        assert states["Lebap"] == ("Turkmenistan", "Turkmenistan") and states["Skopje"][0] == "North Macedonia" and len(states) == 11
        # the specimens: only those written exactly as the old name; state and bioregion as they were
        sp = {r[0]: (r[1], r[2]) for r in mysql("select code, country, ifnull(adm1, '') from specimen")}
        assert sp["ci1"] == ("Côte d'Ivoire", "Lagunes") and sp["ci3"][0] == "Côte d'Ivoire" and sp["ci5"][0] == "Côte d'Ivoire"
        assert sp["ci4"][0] == "IVORY COAST" and sp["ci6"][0] == "Cote d'Ivoire"
        assert sp["cz1"] == ("Czechia", "Praha") and sp["sz1"][0] == "Eswatini" and sp["tr2"][0] == "Türkiye" and sp["tm1"][0] == "Turkmenistan"
        assert sp["cd1"][0] == "Democratic Republic of the Congo" and sp["cg1"][0] == "Congo" and sp["mk1"][0] == "North Macedonia"
        assert sp["cv1"][0] == "Cabo Verde" and sp["hi1"][0] == "United States" and sp["us1"] == ("United States", "California")
        assert one("select count(*) from specimen where island_country = 'Hawaii'") == "1"
        # the curators: one who had both names keeps one row
        assert sorted(tuple(r) for r in mysql("select login_id, country from login_country")) == [
            ("7", "Côte d'Ivoire"), ("8", "Côte d'Ivoire"), ("8", "Türkiye"), ("9", "Congo"), ("9", "Turkmenistan")]
        # the page text and its history move, with their dates
        assert sorted(tuple(r) for r in mysql("select object_key, title, created from object_edit")) == [
            ("Congo", "overview", "2019-01-02 03:04:05"), ("Côte d'Ivoire", "history", "2021-01-02 03:04:05"),
            ("Côte d'Ivoire", "overview", "2020-01-02 03:04:05"), ("Turkmenistan", "overview", "2019-01-02 03:04:05"),
            ("Türkiye", "overview", "2019-01-02 03:04:05")]
        assert sorted(tuple(r) for r in mysql("select object_key, created from object_hist")) == [
            ("Congo", "2018-05-06 07:08:09"), ("Côte d'Ivoire", "2018-05-06 07:08:09")]
        # the counts by country name: the rows follow; a row the new name already had stays; other kinds of place are not touched
        assert sorted(tuple(r) for r in mysql("select list_type, list_key, taxon_name, specimens from place_specimen_name")) == [
            ("country", "Congo", "a", "1"), ("country", "Côte d'Ivoire", "a", "2"), ("country", "Côte d'Ivoire", "b", "1"),
            ("country", "Democratic Republic of the Congo", "a", "1"), ("country", "Türkiye", "a", "1")]
        assert sorted(tuple(r[:2]) for r in mysql("select list_type, list_key from place_genus_curation")) == [
            ("adm1", "504"), ("country", "Côte d'Ivoire"), ("country", "Côte d'Ivoire"), ("country", "Turkmenistan"), ("country", "Türkiye"),
            ("museum", "Turkey")]
        assert sorted(tuple(r) for r in mysql("select place_key, place_name from imaging_progress")) == [
            ("115", "Turkmenistan"), ("158", "Türkiye"), ("48", "Côte d'Ivoire"), ("Afrotropical", "Afrotropical")]
        assert sorted(r[0] for r in mysql("select country from taxon")) == ["Ivory Coast", "Turkey"]      # AntCat's text: not changed
        # the species lists are stored by the id: untouched, so each country keeps its list
        assert sorted(tuple(r) for r in mysql("select geolocale_id, taxon_name from geolocale_taxon")) == [("158", "a"), ("43", "a"), ("7265", "a"), ("7265", "b")]
        # the record and the backups
        rec = {r[0]: r[1:] for r in mysql("select old_name, state, valid_id, alias_added, text_moved, specimens from " + table)}
        assert rec["Ivory Coast"] == ["swap", "48", "0", "1", "3"] and rec["Swaziland"] == ["rename", "40", "1", "0", "1"], rec
        assert rec["Turkey"] == ["rename", "158", "1", "1", "2"] and len(rec) == 7
        assert one("select count(*) from " + table + "_specimen") == "10" and one("select country from " + table + "_specimen where code = 'tr1'") == "Turkey"
        after = snapshot()

        # 5. a second run: all done, nothing to change, no new tables
        out = tool(d, "--apply")
        assert out.returncode == 0 and "Nothing to change." in out.stdout and out.stdout.count("already renamed; only what is still") == 7, out.stdout
        assert "possible: 7; specimens that change: 0" in out.stdout
        assert snapshot() == after and len([r for r in mysql("show tables like 'country_rename_%'") if cr.TABLE_NAME.match(r[0])]) == 1

        # 6. --rollback: every table is as it was before the run
        out = tool(d, "--rollback", "specimen")
        assert out.returncode != 0 and "not a record table name" in out.stderr
        out = tool(d, "--rollback", table)
        assert out.returncode == 0, out.stderr + out.stdout
        assert "Côte d'Ivoire is Ivory Coast again" in out.stdout and "NOW reload the names" in out.stdout, out.stdout
        back = snapshot()
        for t in TABLES:
            assert back[t] == before[t], (t, sorted(set(back[t]) ^ set(before[t]))[:6])
        # ... except a specimen a curator changed after the rename: it keeps what the curator wrote
        assert drop_run_tables()
        assert tool(d, "--apply", "--only", "Turkey").returncode == 0
        mysql("update specimen set country = 'Greece' where code = 'tr2'")
        table = [r[0] for r in mysql("show tables like 'country_rename_%'") if cr.TABLE_NAME.match(r[0])][0]
        assert tool(d, "--rollback", table).returncode == 0
        assert one("select country from specimen where code = 'tr1'") == "Turkey" and one("select country from specimen where code = 'tr2'") == "Greece"
        mysql("update specimen set country = 'Turkey' where code = 'tr2'")
        assert snapshot() == before and drop_run_tables()

        # 7. one rename at a time; then an upload stores the old name (AntWeb's memory not yet reloaded): the next run carries it
        out = tool(d, "--apply", "--only", "Ivory Coast")
        assert out.returncode == 0 and "Renames: 1; possible: 1; specimens that change: 3" in out.stdout, out.stdout
        assert one("select name from geolocale where id = 158") == "Turkey" and one("select name from geolocale where id = 48") == "Côte d'Ivoire"
        assert drop_run_tables()
        mysql("insert into specimen (code, taxon_name, country) values ('ci9', 'a', 'Ivory Coast'); "
              "insert into place_specimen_name values ('country', 'Ivory Coast', 'a', 1), ('country', 'Ivory Coast', 'z', 1)")
        out = tool(d, "--only", "Ivory Coast")
        assert "already renamed; only what is still under the old name is carried" in out.stdout and "specimens that change: 1" in out.stdout, out.stdout
        out = tool(d, "--apply", "--only", "Ivory Coast")
        assert out.returncode == 0 and "old name: 0 (as expected)" in out.stdout, out.stdout
        assert one("select country from specimen where code = 'ci9'") == "Côte d'Ivoire"
        assert sorted(tuple(r) for r in mysql("select taxon_name, specimens from place_specimen_name where list_key = 'Côte d''Ivoire'")) == [
            ("a", "2"), ("b", "1"), ("z", "1")]
        assert one("select count(*) from geolocale where name = 'Ivory Coast'") == "1"      # no second row for the old name

        # 8. nothing is renamed when it cannot be done cleanly; the other renames go on
        assert drop_run_tables()
        mysql(DB_SCHEMA + DB_DATA)
        mysql("insert into object_edit (object_key, title, content) values ('Türkiye', 'overview', 'already there'); "
              "insert into geolocale (id, name, georank, is_valid, parent) values (600, 'Hhohho', 'adm1', 1, 'Eswatini'), "
              "(601, 'Eswatini', 'country', 0, 'Southern Africa'); update geolocale set valid_name = 'Swaziland' where id = 601; "
              "update geolocale set is_valid = 1 where id = 8681")
        out = tool(d, "--apply")
        assert out.returncode == 0, out.stderr + out.stdout
        assert "Renames: 7; possible: 4" in out.stdout, out.stdout
        for line in ("NOT RENAMED: the name Türkiye already has page text (1 fields)",
                     "NOT RENAMED: 1 states of the same name exist under Swaziland and under Eswatini",
                     "NOT RENAMED: both names are valid countries in AntWeb"):
            assert line in out.stdout, (line, out.stdout)
        names = {r[0]: r[1] for r in mysql("select id, name from geolocale where id in (158, 40, 163, 48, 257)")}
        assert names == {"158": "Turkey", "40": "Swaziland", "163": "Czech Republic", "48": "Côte d'Ivoire", "257": "North Macedonia"}, names
        assert one("select count(*) from specimen where country in ('Turkey', 'Swaziland', 'Czech Republic')") == "4"
        drop_run_tables()
    print("With a database: report, busy AntWeb, failed statement undone, apply, second run, rollback, carried specimen, refusals.")


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok " + t.__name__)
    print("OK (" + str(len(tests)) + " tests)")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        raise

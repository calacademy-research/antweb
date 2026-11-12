"""tools/place/island_countries.py: the specimens of an island that is its own country are filed under the island
(Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent."). This round: Hawaii and Galapagos Islands.

Without a database: the bounding box rule (the upload's, LocalityOverview.isWithinBounds), what is done with an
old-name row, the SQL (a specimen moves only when its island_country names a valid flagged island and its country is
another; the change is one transaction and touches a specimen only while it still has the backed-up country; the
rollback only while it still has the new one; island_country, adm1 and bioregion are never set), and the report.

With a MySQL container (CI: the MySQL 8.4 of the staging refresh step; ISLAND_COUNTRIES_DB_CONTAINER and
ISLAND_COUNTRIES_DB_PASSWORD), on rows modelled on production's of 2026-11-11 (Hawaii 423 of United States, Galapagos
Islands 1721 of Ecuador and their old-name rows, Reunion, Mayotte, Rodrigues 8865 and the row Rodrigues of Mauritius
691): the report changes nothing; a busy AntWeb stops --apply; --apply moves exactly the island specimens under their
parent, with region and subregion, and gives the row Rodrigues of Mauritius its valid name; a second run changes
nothing and writes no table; a specimen an upload loaded again between the backup and the update is left alone; a
failed statement undoes the run; --rollback puts everything back, except a specimen changed since.

Run: python3 tests/unit/test_island_countries.py   (also in .github/workflows/tests.yaml)
"""
import csv
import http.server
import importlib.util
import os
import subprocess
import sys
import tempfile
import threading

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
TOOL = os.path.join(ROOT, "tools", "place", "island_countries.py")
spec = importlib.util.spec_from_file_location("island_countries", TOOL)
ic = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ic)

T = "island_countries_backup_20261111_1700"


# ---------------------------------------------------------------- without a database

def test_bounding_box_rule_is_the_uploads():
    hawaii = "-160.5, 18.9, -154.8, 22.3"          # left, bottom, right, top
    assert ic.within_box(hawaii, 21.3, -157.8)                       # Honolulu
    assert ic.within_box(hawaii, 22.45, -159.5)                      # 0.2 degrees of grace
    assert not ic.within_box(hawaii, 22.6, -159.5)
    assert not ic.within_box(hawaii, 37.7, -122.4)                   # San Francisco: inside the parent's box, not the island's
    assert not ic.within_box(hawaii, 21.3, 157.8)                    # the sign of the longitude lost
    assert ic.within_box("", 37.7, -122.4) and ic.within_box(None, 1, 1) and ic.within_box("0, 0, 0, 0", 50, 50), "no box refuses nothing"
    assert ic.within_box("(-92.1, -1.5, -89.2, 1.7)", -0.74, -90.3), "parentheses are ignored"
    fiji = "174.8662, -21.0171, -178.2032, -12.4662"                 # around the 180th meridian: left > right
    assert ic.within_box(fiji, -17.8, 178.0) and ic.within_box(fiji, -17.0, -179.5) and not ic.within_box(fiji, -17.8, 170.0)
    assert ic.numbers("1, x, 3") == [1.0, 0.0, 3.0, 0.0]


def test_old_name_row_states():
    assert ic.ALIASES == [("Rodrigues", "Mauritius", "Rodrigues")]
    # production 2026-11-11: row 691, adm1 Rodrigues of Mauritius, not valid, no valid name; the country Rodrigues is valid
    assert ic.alias_state((691, "0", ""), ("1",)) == ("set", "its valid name will be set")
    assert ic.alias_state((691, "0", "none"), ("1",))[0] == "set"
    assert ic.alias_state((691, "0", "Rodrigues"), ("1",)) == (None, "it already names Rodrigues")
    assert ic.alias_state((691, "0", "Port Louis"), ("1",)) == (None, "it already names Port Louis")
    assert ic.alias_state((691, "1", ""), ("1",))[0] is None, "a valid state is not changed"
    assert ic.alias_state(None, ("1",)) == (None, "AntWeb has no such row")
    assert ic.alias_state((691, "0", ""), None)[0] is None and ic.alias_state((691, "0", ""), ("0",))[0] is None


def test_islands_and_blank_values():
    i = ic.Island("423", "Hawaii", "Polynesia", "Oceania", "Polynesia", "United States", "Oceania", "")
    assert (i.id, i.political_parent, i.subregion, i.region) == (423, "United States", "Polynesia", "Oceania")
    # a row that names itself as country (Reunion-style) or the text "none" has no political parent
    assert ic.Island("1", "X", "none", "", "null", "X", "none", "none").political_parent == ""
    j = ic.Island("1", "X", "none", "", "null", "none", "none", "none")
    assert (j.political_parent, j.place_parent, j.region, j.subregion, j.bioregion, j.box) == ("", "", "", "", "", "")
    assert ic.blank(None) and ic.blank(" None ") and ic.blank("null") and not ic.blank("Null Island")


def test_which_specimens_move():
    assert ic.ISLAND_ROW == "g.georank = 'country' and g.is_island = 1 and g.is_valid = 1"
    assert "s.island_country = g.name" in ic.TO_MOVE and "not (s.country <=> g.name)" in ic.TO_MOVE
    assert ic.TO_MOVE in ic.TO_MOVE_SQL and ic.ISLAND_ROW in ic.TO_MOVE_SQL
    backup = ic.backup_sql(T, [691])
    assert backup[0].startswith("create table " + T + " as select s.code, s.country as old_country, s.region as old_region, s.subregion as old_subregion,")
    assert ic.TO_MOVE in backup[0] and ic.ISLAND_ROW in backup[0] and "g.name as new_country" in backup[0]
    assert "if(coalesce(g.region, '') in ('', 'none', 'null'), s.region, g.region) as new_region" in backup[0], "no region on the island row: unchanged"
    assert backup[1] == "create table " + T + "_geolocale as select id, name, parent, valid_name from geolocale where id in (691)"
    assert ic.backup_sql(T, [])[1].endswith("where id in (0)")
    assert ic.TABLE_NAME.match(T) and not ic.TABLE_NAME.match("specimen") and not ic.TABLE_NAME.match(T + "; drop table x")
    # the names say "dated backup" to the schema check (tools/datamodel/list_tables.py), as the other tools' backups do
    sys.path.insert(0, os.path.join(ROOT, "tools", "datamodel"))
    import list_tables
    assert list_tables.BACKUP.search(T) and list_tables.BACKUP.search(T + "_geolocale")


def test_apply_and_rollback_sql():
    sql = ic.apply_sql(T, [(691, "Rodrigues")])
    assert sql[0] == "start transaction" and sql[-1] == "commit" and len(sql) == 4
    assert not [q for q in sql if q.split()[0] in ("create", "alter", "drop", "truncate")], "a table statement would end the transaction"
    # a specimen only while it still has the country and island the backup recorded (an upload may have loaded it again)
    assert sql[1] == ("update specimen s join " + T + " b on b.code = s.code set s.country = b.new_country, s.region = b.new_region, "
                      "s.subregion = b.new_subregion, b.applied = 1 where hex(convert(ifnull(s.country, '') using utf8mb4)) = "
                      "hex(convert(ifnull(b.old_country, '') using utf8mb4)) and hex(convert(ifnull(s.island_country, '') using utf8mb4)) = "
                      "hex(convert(ifnull(b.island_country, '') using utf8mb4))")
    for never in ("island_country =", "s.adm1", "bioregion"):
        assert never not in sql[1].split(" set ")[1].split(" where ")[0], never
    assert sql[2] == ("update geolocale set valid_name = 'Rodrigues' where id = 691 and coalesce(is_valid, 0) = 0 "
                      "and coalesce(valid_name, '') in ('', 'none', 'null')")
    assert len(ic.apply_sql(T, [])) == 3
    back = ic.rollback_sql(T, True)
    assert back[0] == "start transaction" and back[3] == "commit"
    assert "set s.country = b.old_country, s.region = b.old_region, s.subregion = b.old_subregion where b.applied = 1 and " in back[1]
    assert "hex(convert(ifnull(s.country, '') using utf8mb4)) = hex(convert(ifnull(b.new_country, '') using utf8mb4))" in back[1]
    assert back[2] == "update geolocale g join " + T + "_geolocale b on b.id = g.id set g.valid_name = b.valid_name"
    assert len(ic.rollback_sql(T, False)) == 4 and "geolocale" not in ic.rollback_sql(T, False)[1]


def test_parent_list_sql_and_sources():
    sql = ic.parent_list_sql("Hawaii", "United States")
    assert "pg.name = 'United States'" in sql and "t.taxarank in ('species', 'subspecies')" in sql
    assert "exists (select 1 from specimen s where s.taxon_name = gt.taxon_name and s.island_country = 'Hawaii')" in sql
    # "in the parent" once the islands' specimens are moved: country the parent and not marked as a flagged island's
    assert ("not exists (select 1 from specimen s where s.taxon_name = gt.taxon_name and s.country = 'United States' "
            "and coalesce(s.island_country, '') not in (select g.name from geolocale g where " + ic.ISLAND_ROW + "))") in sql
    assert "delete" not in sql and "update" not in sql
    assert ic.parent_list_sql("O'ahu", "X").count("'O''ahu'") == 2
    # the rows the daily update deletes and builds again (GeolocaleTaxonDb.deleteSource("specimen"))
    assert ic.from_specimens("specimen") and ic.from_specimens("specimen, curator") and not ic.from_specimens("curator")
    assert not ic.from_specimens("antcat") and not ic.from_specimens("") and not ic.from_specimens(None)


def found():
    f = ic.Found()
    f.islands = [ic.Island("1721", "Galapagos Islands", "South America", "Americas", "South America", "Ecuador", "Neotropical", "-92.1, -1.5, -89.2, 1.7"),
                 ic.Island("423", "Hawaii", "Polynesia", "", "", "United States", "Oceania", "")]
    f.to_move = [("Galapagos Islands", "Ecuador", 1, "California Academy of Sciences", 697), ("Galapagos Islands", "Ecuador", 21, "UCD", 66),
                 ("Hawaii", "United States", 1, "California Academy of Sciences", 174), ("Hawaii", "United States", 30, "", 74)]
    f.filed = {"Galapagos Islands": (763, 0), "Hawaii": (251, 3)}
    f.no_marker = {"Hawaii": 2}
    f.by_adm1 = [("United States", "Oahu", "Hawaii", 5)]
    f.outside = [("Galapagos Islands", -2.2, -80.9, 4)]
    f.parent_rows = [("United States", "Hawaii", "ponerinaeponera swezeyi", "valid", "antcat", 3),
                     ("United States", "Hawaii", "formicinaeplagiolepis alluaudi", "valid", "specimen", 9),
                     ("Ecuador", "Galapagos Islands", "myrmicinaepheidole williamsi", "valid", "curator", 12)]
    f.aliases = [("Rodrigues", "Mauritius", "Rodrigues", 691, "set", "its valid name will be set")]
    return f


def test_report():
    f = found()
    assert f.moving() == 1011
    text = ic.report_text("2026-11-11 17:00", "abc1234", f)
    for line in ("Islands (valid places flagged as islands): Galapagos Islands, Hawaii",
                 "Galapagos Islands (id 1721; page under South America; political parent Ecuador; bioregion Neotropical)",
                 "  specimens with island_country Galapagos Islands: 763; already with country Galapagos Islands: 0; to move: 763",
                 "        697  now country Ecuador, group California Academy of Sciences (1)",
                 "         74  now country United States, group (no name) (30)",
                 "  region and subregion they get: Americas / South America",
                 "  region and subregion they get: (the row has none: unchanged) / (the row has none: unchanged)",
                 "specimens with coordinates outside it: 4 <-- their next upload would red-flag them",
                 "  bounding box used at upload: none (nothing is refused); specimens with coordinates outside it: 0",
                 "  note: 2 specimens have country Hawaii and no island_country: not changed",
                 "Specimens to move in all: 1,011",
                 "          5  United States / Oahu  ->  Hawaii",
                 "  Rodrigues of Mauritius -> the country Rodrigues: its valid name will be set (row id 691)",
                 "          1  on the list of Ecuador, island Galapagos Islands, source curator",
                 "          1  on the list of United States, island Hawaii, source antcat",
                 "they leave the list with the next daily update",
                 "          1  on the list of United States, island Hawaii"):
        assert line in text, (line, text)
    assert "Applied" not in text
    applied = ic.report_text("x", "", f, table=T, changed=1009, left=2, aliases_set=1)
    assert "Specimens changed: 1,009. 2 had changed since the backup and were left as they are; run the tool again for them." in applied
    assert "python3 island_countries.py --rollback " + T in applied and "Old-name rows changed: 1." in applied
    empty = ic.report_text("x", "", ic.Found())
    assert "Islands (valid places flagged as islands): none" in empty and "Specimens to move in all: 0" in empty
    assert "technical.do" in ic.AFTER_TEXT and "daily update" in ic.AFTER_TEXT and "nightly" not in ic.AFTER_TEXT + text


# ---------------------------------------------------------------- with a database

CONTAINER = os.environ.get("ISLAND_COUNTRIES_DB_CONTAINER")
PASSWORD = os.environ.get("ISLAND_COUNTRIES_DB_PASSWORD", "ci-root")
DATABASE = "island_countries_test"


def mysql(sql, database=DATABASE, check=True):
    r = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-uroot", database,
                        "--default-character-set=utf8mb4", "-B", "-N"],
                       input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=120)
    if check:
        assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


# The place table as production has it for names (exact, binary); the specimen table without capitals and accents.
SCHEMA = """
drop table if exists geolocale, specimen, geolocale_taxon, taxon, ant_group;
create table geolocale (id int not null primary key, name varchar(250) character set utf8mb3 collate utf8mb3_bin, georank varchar(20),
  parent varchar(250), region varchar(250), subregion varchar(250), country varchar(250), is_island tinyint, is_valid tinyint,
  valid_name varchar(250), is_live tinyint, bioregion varchar(64), bounding_box varchar(100), bounding_box_fixed varchar(100))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table specimen (code varchar(64) not null primary key, taxon_name varchar(128), status varchar(40), country varchar(64),
  adm1 varchar(128), island_country varchar(64), region varchar(30), subregion varchar(30), bioregion varchar(64), access_group int,
  decimal_latitude float, decimal_longitude float, key (country, adm1), key (island_country), key (taxon_name))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table geolocale_taxon (geolocale_id int not null, taxon_name varchar(128) not null, source varchar(40), primary key (geolocale_id, taxon_name))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table taxon (taxon_name varchar(128) not null primary key, taxarank varchar(20), status varchar(40))
  engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
create table ant_group (id int not null primary key, name varchar(100)) engine = InnoDB default charset = utf8mb3 collate = utf8mb3_general_ci;
"""

# geolocale rows of production, 2026-11-11 (the coordinator's list); boxes made up for the test.
DATA = """
insert into geolocale (id, name, georank, parent, region, subregion, country, is_island, is_valid, valid_name, is_live, bioregion, bounding_box) values
  (97, 'United States', 'country', 'North America', 'Americas', 'North America', null, null, 1, 'none', 1, 'Nearctic', '-179.2, 18.9, -66.9, 71.4'),
  (103, 'Ecuador', 'country', 'South America', 'Americas', 'South America', null, null, 1, null, 1, 'Neotropical', '-92.1, -5.0, -75.2, 1.7'),
  (9, 'Mauritius', 'country', 'Eastern Africa', 'Africa', 'Eastern Africa', null, null, 1, null, 1, 'Malagasy', null),
  (620, 'Galapagos', 'adm1', 'Ecuador', null, null, 'Ecuador', 0, 0, 'Galapagos Islands', 0, 'Neotropical', null),
  (1732, 'Galapagos', 'country', 'South America', null, null, 'Galapagos', 1, 0, 'Galapagos Islands', 0, 'Neotropical', null),
  (1721, 'Galapagos Islands', 'country', 'South America', 'Americas', 'South America', 'Ecuador', 1, 1, null, 1, 'Neotropical', '-92.1, -1.5, -89.2, 1.7'),
  (2394, 'Galápagos', 'adm1', 'Ecuador', null, null, 'Ecuador', 1, 0, 'Galapagos Islands', null, 'Neotropical', null),
  (8864, 'Galápagos', 'country', 'South America', null, null, null, 1, 0, 'Galapagos Islands', 0, 'Neotropical', null),
  (423, 'Hawaii', 'country', 'Polynesia', 'Oceania', 'Polynesia', 'United States', 1, 1, 'none', 1, 'Oceania', null),
  (1017, 'Oahu', 'adm1', 'United States', null, null, 'United States', null, 0, 'Hawaii', null, 'Nearctic', null),
  (445, 'Massachusetts', 'adm1', 'United States', null, null, 'United States', null, 1, null, 1, 'Nearctic', null),
  (1696, 'MA', 'adm1', 'United States', null, null, 'United States', null, 0, 'Massachusetts', null, 'Nearctic', null),
  (10, 'Mayotte', 'country', 'Eastern Africa', 'Africa', 'Eastern Africa', 'Mayotte', null, 1, null, 1, 'Malagasy', null),
  (12, 'Reunion', 'country', 'Eastern Africa', 'Africa', 'Eastern Africa', 'Reunion', null, 1, null, 1, 'Malagasy', null),
  (8737, 'Réunion', 'country', 'Eastern Africa', null, null, 'Réunion', null, 0, 'Reunion', null, 'Malagasy', null),
  (691, 'Rodrigues', 'adm1', 'Mauritius', null, null, 'Mauritius', 0, 0, null, 0, 'Malagasy', null),
  (8865, 'Rodrigues', 'country', 'Eastern Africa', 'Africa', 'Eastern Africa', null, 0, 1, null, 1, 'Malagasy', null);
update geolocale set bounding_box_fixed = '-160.5, 18.9, -154.8, 22.3' where id = 423;
insert into ant_group values (1, 'California Academy of Sciences'), (21, 'University of Utah');
insert into specimen (code, taxon_name, status, country, adm1, island_country, region, subregion, bioregion, access_group, decimal_latitude, decimal_longitude) values
  ('hi1', 'sp only hawaii', 'valid', 'United States', null, 'Hawaii', 'Americas', 'North America', 'Oceania', 1, 21.3, -157.8),
  ('hi2', 'sp both', 'valid', 'United States', null, 'Hawaii', 'Americas', 'North America', 'Oceania', 1, 21.3, -157.8),
  ('hi3', 'sp only hawaii literature', 'valid', 'United States', null, 'Hawaii', 'Americas', 'North America', 'Oceania', 21, 37.7, -122.4),
  ('hi4', 'sp both', 'valid', 'Hawaii', 'Maui', 'Hawaii', 'Oceania', 'Polynesia', 'Oceania', 21, null, null),
  ('ga1', 'sp only galapagos', 'valid', 'Ecuador', null, 'Galapagos Islands', 'Americas', 'South America', 'Neotropical', 1, -0.74, -90.3),
  ('ga2', 'sp both', 'valid', 'Ecuador', null, 'Galapagos Islands', 'Americas', 'South America', 'Neotropical', 21, -999.9, -999.9),
  ('us1', 'sp both', 'valid', 'United States', 'California', null, 'Americas', 'North America', 'Nearctic', 1, 37.7, -122.4),
  ('us2', 'sp mainland', 'valid', 'United States', 'MA', null, 'Americas', 'North America', 'Nearctic', 21, 42.3, -71.0),
  ('us3', 'sp both', 'valid', 'United States', 'Oahu', null, 'Americas', 'North America', 'Nearctic', 21, 21.4, -157.9),
  ('ec1', 'sp both', 'valid', 'Ecuador', 'Pichincha', null, 'Americas', 'South America', 'Neotropical', 1, -0.2, -78.5),
  ('re1', 'sp both', 'valid', 'Reunion', 'Saint-Paul', null, 'Africa', 'Eastern Africa', 'Malagasy', 1, -21.0, 55.3),
  ('ro1', 'sp both', 'valid', 'Rodrigues', null, null, 'Africa', 'Eastern Africa', 'Malagasy', 1, -19.7, 63.4),
  ('xx1', 'sp both', 'valid', 'Ecuador', null, 'Galapagos', 'Americas', 'South America', 'Neotropical', 21, null, null),
  ('xx2', 'sp both', 'valid', null, null, 'Hawaii', null, null, 'Oceania', 21, null, null);
insert into taxon values ('sp only hawaii', 'species', 'valid'), ('sp both', 'species', 'valid'), ('sp only hawaii literature', 'species', 'valid'),
  ('sp only galapagos', 'species', 'valid'), ('sp mainland', 'species', 'valid'), ('genus only hawaii', 'genus', 'valid');
insert into geolocale_taxon values (97, 'sp only hawaii', 'specimen'), (97, 'sp both', 'specimen'), (97, 'sp only hawaii literature', 'antcat'),
  (97, 'sp mainland', 'specimen'), (97, 'genus only hawaii', 'curator'), (423, 'sp only hawaii', 'specimen'), (423, 'sp both', 'specimen'),
  (103, 'sp only galapagos', 'curator'), (103, 'sp both', 'specimen'), (1721, 'sp only galapagos', 'specimen');
"""

TABLES = ["geolocale", "specimen", "geolocale_taxon", "taxon", "ant_group"]


def snapshot():
    out = {}
    for t in TABLES:
        cols = [c[0] for c in mysql("select column_name from information_schema.columns where table_schema = database() and table_name = '"
                                    + t + "' order by ordinal_position")]
        out[t] = sorted(tuple(r) for r in mysql("select " + ", ".join("ifnull(hex(" + c + "), 'NULL')" for c in cols) + " from " + t))
    return out


def specimens():
    return {r[0]: tuple(r[1:]) for r in mysql("select code, ifnull(country, '-'), ifnull(island_country, '-'), ifnull(region, '-'), "
                                              "ifnull(subregion, '-'), ifnull(adm1, '-'), ifnull(bioregion, '-') from specimen")}


def tool(project, *args, app="http://127.0.0.1:9"):
    return subprocess.run([sys.executable, TOOL, "--project", project, "--container", CONTAINER, "--database", DATABASE,
                           "--out", os.path.join(project, "out"), "--app", app] + list(args), capture_output=True, text=True, timeout=300)


def run_tables():
    return sorted(r[0] for r in mysql("show tables like 'island_countries_backup_%'"))


def drop_run_tables():
    names = run_tables()
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
        print("MySQL part skipped (ISLAND_COUNTRIES_DB_CONTAINER not set).")
        return
    mysql("drop database if exists " + DATABASE + "; create database " + DATABASE, database="mysql")
    mysql(SCHEMA + DATA)
    before, sp_before = snapshot(), specimens()
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=root\npassword=" + PASSWORD + "\n")

        # 1. the report: what would move, by island, country and group; nothing is changed
        out = tool(d)
        assert out.returncode == 0, out.stderr + out.stdout
        for line in ("Islands (valid places flagged as islands): Galapagos Islands, Hawaii",
                     "Hawaii (id 423; page under Polynesia; political parent United States; bioregion Oceania)",
                     # xx2 (no country) is an island's specimen too: it gets the island as its country
                     "  specimens with island_country Hawaii: 5; already with country Hawaii: 1; to move: 4",
                     "          2  now country United States, group California Academy of Sciences (1)",
                     "          1  now country United States, group University of Utah (21)",
                     "          1  now country (none), group University of Utah (21)",
                     "  specimens with island_country Galapagos Islands: 2; already with country Galapagos Islands: 0; to move: 2",
                     "  region and subregion they get: Oceania / Polynesia",
                     # the fixed box wins; hi3 lies in San Francisco: inside the parent's box, outside the island's
                     "  bounding box used at upload: -160.5, 18.9, -154.8, 22.3; specimens with coordinates outside it: 1 <--",
                     # ga2 has -999.9 (not entered): not checked, as at upload
                     "  bounding box used at upload: -92.1, -1.5, -89.2, 1.7; specimens with coordinates outside it: 0",
                     "Specimens to move in all: 6",
                     "          1  United States / Oahu  ->  Hawaii",          # us3; us2 (MA: an old name of a state) is not listed
                     "  Rodrigues of Mauritius -> the country Rodrigues: its valid name will be set (row id 691)",
                     "          1  on the list of Ecuador, island Galapagos Islands, source curator",
                     "          1  on the list of United States, island Hawaii, source antcat",
                     "          1  on the list of United States, island Hawaii",          # sp only hawaii, source specimen: leaves by itself
                     "Report only: nothing was changed."):
            assert line in out.stdout, (line, out.stdout)
        assert snapshot() == before and not run_tables()
        with open(os.path.join(d, "out", "parent_list_rows.csv"), newline="", encoding="utf-8") as f:
            rows = list(csv.reader(f))
        # "sp both" has a specimen in the parent (us1, ec1): not listed; a genus row is not a species
        assert sorted(tuple(r[:5]) + (r[6],) for r in rows[1:]) == [
            ("Ecuador", "Galapagos Islands", "sp only galapagos", "valid", "curator", "yes: for a curator"),
            ("United States", "Hawaii", "sp only hawaii", "valid", "specimen", "no: built again from specimens"),
            ("United States", "Hawaii", "sp only hawaii literature", "valid", "antcat", "yes: for a curator")], rows
        with open(os.path.join(d, "out", "outside_box.csv"), newline="", encoding="utf-8") as f:
            assert [r[0] for r in list(csv.reader(f))[1:]] == ["Hawaii"]

        # 2. AntWeb says an upload is running: --apply stops before anything is written
        server = http.server.HTTPServer(("127.0.0.1", 0), Busy)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        out = tool(d, "--apply", app="http://127.0.0.1:" + str(server.server_address[1]))
        server.shutdown()
        assert out.returncode != 0 and "AntWeb says \"busy: upload\"" in out.stderr, out.stderr + out.stdout
        assert snapshot() == before and not run_tables()

        # 3. the database refuses a statement in the middle: the whole run is undone, no table is left
        mysql("create trigger island_countries_test before update on geolocale for each row signal sqlstate '45000' set message_text = 'refused for the test'")
        out = tool(d, "--apply")
        mysql("drop trigger island_countries_test")
        assert out.returncode != 0 and "Nothing changed (the transaction was undone and the backup tables dropped)" in out.stderr, (out.stderr + out.stdout)[-500:]
        assert snapshot() == before and not run_tables()

        # 4. --apply
        out = tool(d, "--apply")
        assert out.returncode == 0, out.stderr + out.stdout
        assert "Specimens changed: 6." in out.stdout and "Old-name rows changed: 1." in out.stdout, out.stdout
        assert "specimens still to move or old-name rows still to set: 0 (as expected)" in out.stdout and "NOW: if an old-name row changed" in out.stdout
        sp = specimens()
        # country, island_country, region, subregion, adm1, bioregion
        assert sp["hi1"] == ("Hawaii", "Hawaii", "Oceania", "Polynesia", "-", "Oceania"), sp["hi1"]
        assert sp["hi3"][:4] == ("Hawaii", "Hawaii", "Oceania", "Polynesia") and sp["xx2"][:4] == ("Hawaii", "Hawaii", "Oceania", "Polynesia")
        assert sp["ga1"] == ("Galapagos Islands", "Galapagos Islands", "Americas", "South America", "-", "Neotropical"), sp["ga1"]
        assert sp["ga2"][:2] == ("Galapagos Islands", "Galapagos Islands")
        # everything else as it was: the parents' own, an island specimen already moved (its adm1 kept), Reunion and
        # Rodrigues (plain countries), an adm1 that is an old name (the next upload's work), an island_country that is
        # an old name of the island (not a valid island: not guessed)
        for code in ("hi4", "us1", "us2", "us3", "ec1", "re1", "ro1", "xx1"):
            assert sp[code] == sp_before[code], (code, sp[code])
        assert mysql("select valid_name, is_valid from geolocale where id = 691") == [["Rodrigues", "0"]]
        table = run_tables()[0]
        assert ic.TABLE_NAME.match(table) and run_tables() == [table, table + "_geolocale"]
        assert mysql("select code, old_country, new_country, old_region, new_region, applied from " + table + " where code in ('hi1', 'xx2') order by code") == [
            ["hi1", "United States", "Hawaii", "Americas", "Oceania", "1"], ["xx2", "NULL", "Hawaii", "NULL", "Oceania", "1"]]
        assert mysql("select id, name, parent, ifnull(valid_name, '-') from " + table + "_geolocale") == [["691", "Rodrigues", "Mauritius", "-"]]
        with open(os.path.join(d, "out", "specimens.csv"), newline="", encoding="utf-8") as f:
            assert len(list(csv.reader(f))) == 7
        geo_after = [r for r in snapshot()["geolocale"]]
        assert len(set(geo_after) ^ set(before["geolocale"])) == 2, "one place row changed: 691"
        after = snapshot()

        # 5. a second run: nothing to move, no new table
        out = tool(d, "--apply")
        assert out.returncode == 0 and "Nothing to change." in out.stdout and "Specimens to move in all: 0" in out.stdout, out.stdout
        assert "Rodrigues of Mauritius -> the country Rodrigues: it already names Rodrigues" in out.stdout
        assert snapshot() == after and run_tables() == [table, table + "_geolocale"]

        # 6. --rollback: all as before, except a specimen changed since (a later upload: left as it is)
        assert tool(d, "--rollback", "specimen").returncode != 0
        mysql("update specimen set country = 'Japan' where code = 'hi2'")
        out = tool(d, "--rollback", table)
        assert out.returncode == 0 and "Rolled back: 5 of the 6 specimens" in out.stdout and "a later upload; left as they are" in out.stdout, out.stderr + out.stdout
        assert specimens()["hi2"][0] == "Japan"
        mysql("update specimen set country = 'United States', region = 'Americas', subregion = 'North America' where code = 'hi2'")
        assert snapshot() == before
        assert drop_run_tables()

        # 7. an upload loads a specimen again between the backup and the update (the tool's own statements, with a
        #    change in between): the specimen is left as it is and not marked applied; a rollback does not touch it
        mysql(";\n".join(ic.backup_sql(T, [])))
        mysql("update specimen set island_country = null, country = 'United States', adm1 = 'Texas' where code = 'hi3'")   # re-uploaded as a mainland record
        mysql(";\n".join(ic.apply_sql(T, [])))
        assert specimens()["hi3"][:2] == ("United States", "-") and specimens()["hi1"][0] == "Hawaii"
        assert mysql("select applied, count(*) from " + T + " group by applied order by applied") == [["0", "1"], ["1", "5"]]
        mysql("update specimen set country = 'Mexico' where code = 'hi3'")
        assert mysql(";\n".join(ic.rollback_sql(T, True))) == [["5"]]
        assert specimens()["hi3"][0] == "Mexico" and specimens()["hi1"][0] == "United States"
        assert drop_run_tables()
    mysql("drop database " + DATABASE, database="mysql")
    print("With a database: report, busy AntWeb, failed statement undone, apply, second run, rollback, a specimen changed in between.")


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

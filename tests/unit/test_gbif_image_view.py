"""The image view of the GBIF archive, darwin_core_multimedia (2026-11-11).

The Academy's IPT publishes AntWeb to GBIF from two views of this database, read from a copy of it; the image view is
changed by db/upgrade/9.4.0/2026-11-11-darwin-core-multimedia-medium.sql (Brian Fisher, 2026-11-11): medium images
from static.antweb.org; nothing else (the rights holder stays the image's copyright line). The IPT maps the columns
by position, so their number, order and names must not change.

Without a database: the texts of the upgrade file, its rollback and the data model document.
With a database (MySQL 8.4 in CI), reachable as `docker exec <container> mysql`:
  GBIF_VIEW_TEST_CONTAINER   the container (CI: pfmysql, the one of test_place_fix_db.py); without it these are skipped
  GBIF_VIEW_TEST_USER        default root (it creates and drops the database gbif_view_test)
  GBIF_VIEW_TEST_PASSWORD    its password
the upgrade file itself is run against tables like AntWeb's and the rows of the view are read; then the rollback; then
the upgrade twice. The test database has no user antweb@localhost, so the definer clause is replaced for the run (its
text is checked above).

Run: python3 tests/unit/test_gbif_image_view.py
"""
import os
import re
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
UPGRADE = os.path.join(ROOT, "db", "upgrade", "9.4.0", "2026-11-11-darwin-core-multimedia-medium.sql")
ROLLBACK = os.path.join(ROOT, "db", "upgrade", "9.4.0", "2026-11-11-darwin-core-multimedia-medium-rollback.sql")
COLUMNS = ["occurrenceid", "catalognumber", "imageurl", "shot_type", "shot_number", "references", "title", "license",
           "rightsHolder", "creator"]
DEFINER = "DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER"

CONTAINER = os.environ.get("GBIF_VIEW_TEST_CONTAINER", "")
USER = os.environ.get("GBIF_VIEW_TEST_USER", "root")
PASSWORD = os.environ.get("GBIF_VIEW_TEST_PASSWORD", "")
DATABASE = "gbif_view_test"


def statement(path):
    """The file without its comment lines."""
    with open(path, encoding="utf-8") as f:
        return "\n".join(line for line in f.read().split("\n") if not line.lstrip().startswith("--"))


def test_the_columns_keep_their_number_order_and_names():
    for path in (UPGRADE, ROLLBACK):
        sql = statement(path)
        assert sql.count("CREATE OR REPLACE") == 1 and "VIEW `darwin_core_multimedia` AS" in sql, path
        assert re.findall(r" AS `(\w+)`(?=,|\s+from\b)", sql) == COLUMNS, (path, re.findall(r" AS `(\w+)`(?=,|\s+from\b)", sql))
        assert DEFINER in sql, path + ": the definer of the view as it stood on production"


def test_the_archive_gets_medium_images_from_the_static_address():
    sql = statement(UPGRADE)
    address = "concat('https://static.antweb.org/images/',`im`.`image_of_id`,'/',`im`.`image_of_id`,'_',`im`.`shot_type`,'_',`im`.`shot_number`,'_med.jpg')"
    assert sql.count(address) == 2, "the address, and the order by"
    assert "www.antweb.org/images" not in sql and "_high" not in sql, "no address behind the shield, no large image"
    assert "https://www.antweb.org/specimenImages.do?name=" in sql, "the link to the specimen's page stays"
    with open(os.path.join(ROOT, "src", "org", "calacademy", "antweb", "SpecimenImage.java"), encoding="utf-8") as f:
        java = f.read()
    assert 'code + "/" + code + "_" + shot +  "_" + number + "_med.jpg"' in java, "the site's own medium address (SpecimenImage)"


def test_only_the_address_differs_from_the_definition_of_2026_11_11():
    """Brian Fisher, 2026-11-11: the address only; the rights holder and everything else as they were."""
    before = statement(ROLLBACK).strip()
    after = statement(UPGRADE).strip()
    assert before.replace("'https://www.antweb.org/images/'", "'https://static.antweb.org/images/'").replace("'_high.jpg'", "'_med.jpg'") == after
    assert "nullif(`cr`.`copyright`,'') AS `rightsHolder`" in after and "group_image" not in after


def test_the_rollback_is_the_definition_of_2026_11_11():
    sql = statement(ROLLBACK)
    assert "'https://www.antweb.org/images/'" in sql and "'_high.jpg'" in sql
    assert "nullif(`cr`.`copyright`,'') AS `rightsHolder`" in sql and "left join `copyright` `cr`" in sql


def test_the_data_model_names_the_reader():
    with open(os.path.join(ROOT, "docs", "data-model.md"), encoding="utf-8") as f:
        text = f.read()
    for view in ("darwin_core_2", "darwin_core_multimedia"):
        section = text.split("### `" + view + "`", 1)[1].split("\n### ", 1)[0]
        assert "IPT" in section and "GBIF" in section, view + ": read by the Academy's IPT for GBIF"
        assert "No reader" not in section and "candidate to drop" not in section, view


# ---- with a database -------------------------------------------------------------------------------------------------

def mysql(sql, database=None):
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-u", USER] + ([database] if database else []) + [
        "--default-character-set=utf8mb4", "--batch", "-N"]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=300)
    assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def run_file(path):
    sql = statement(path)
    assert DEFINER in sql
    mysql(sql.replace(DEFINER, "SQL SECURITY INVOKER"), DATABASE)


def make_database():
    mysql("drop database if exists " + DATABASE + "; create database " + DATABASE + " default character set utf8mb4")
    mysql("""
        create table image (id int not null primary key, image_of_id varchar(128), shot_type varchar(8), shot_number int,
                            license int, copyright int, artist int);
        create table license (id int not null primary key, license text);
        create table copyright (id int not null primary key, copyright text);
        create table artist (id int not null primary key, name text);
        create table group_image (group_id int, image_id int);
        insert into license values (1, 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/');
        insert into copyright values (1, 'California Academy of Sciences, 2000-2019'), (2, '');
        insert into artist values (1, 'April Nobile'), (2, '');
        insert into image values
            (1, 'casent0000001', 'h', 1, 1, 1, 1),      -- uploaded by the Academy
            (2, '08costa-0161',  'p', 2, 1, 1, 2),      -- uploaded by another group, no photographer
            (3, 'nogroup0001',   'd', 1, 1, 2, 1),      -- an empty copyright line
            (4, 'casent0000004', 'l', 1, 1, 1, 1),
            (5, 'shared0001',    'v', 1, 1, 1, 1);
        insert into group_image values (1, 1), (2, 2), (1, 4), (1, 4), (1, 5), (2, 5), (0, 99);
        """, DATABASE)


def rows():
    return {r[0]: r[1:] for r in mysql(
        "select catalognumber, occurrenceid, imageurl, `references`, ifnull(title, '<null>'), license, ifnull(rightsHolder, '<null>'), "
        "ifnull(creator, '<null>'), 'end' from darwin_core_multimedia", DATABASE)}


def db_test_the_view_as_the_upgrade_file_makes_it():
    make_database()
    run_file(UPGRADE)
    assert [r[0] for r in mysql("show columns from darwin_core_multimedia", DATABASE)] == COLUMNS
    got = rows()
    assert len(got) == 5 and mysql("select count(*) from darwin_core_multimedia", DATABASE) == [["5"]], "one row per image"
    occurrence, url, references, title, license_, holder, creator, _end = got["casent0000001"]
    assert occurrence == "CAS:ANTWEB:casent0000001"
    assert url == "https://static.antweb.org/images/casent0000001/casent0000001_h_1_med.jpg"
    assert references == "https://www.antweb.org/specimenImages.do?name=casent0000001"
    assert title == "casent0000001 head view 1" and license_.startswith("CC BY 4.0") and creator == "April Nobile"
    assert holder == "California Academy of Sciences, 2000-2019", "the rights holder as before: the image's copyright line"
    assert got["08costa-0161"][1] == "https://static.antweb.org/images/08costa-0161/08costa-0161_p_2_med.jpg"
    assert got["08costa-0161"][5] == "California Academy of Sciences, 2000-2019" and got["08costa-0161"][6] == "<null>"
    assert got["nogroup0001"][5] == "<null>" and got["nogroup0001"][6] == "April Nobile", "an empty copyright line: no rights holder"
    ordered = [r[0] for r in mysql("select imageurl from darwin_core_multimedia", DATABASE)]
    assert ordered == sorted(ordered), "in the order of the address"


def db_test_the_rollback_and_running_the_upgrade_twice():
    make_database()
    run_file(UPGRADE)
    run_file(ROLLBACK)
    got = rows()
    assert got["08costa-0161"][1] == "https://www.antweb.org/images/08costa-0161/08costa-0161_p_2_high.jpg"
    assert got["08costa-0161"][5] == "California Academy of Sciences, 2000-2019" and got["nogroup0001"][5] == "<null>"
    assert [r[0] for r in mysql("show columns from darwin_core_multimedia", DATABASE)] == COLUMNS
    run_file(ROLLBACK)
    run_file(UPGRADE)
    run_file(UPGRADE)
    assert rows()["08costa-0161"][1].endswith("_med.jpg") and len(rows()) == 5
    mysql("drop database if exists " + DATABASE)


if __name__ == "__main__":
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("test_") and callable(f)]
    for name, fn in tests:
        fn()
    print(str(len(tests)) + " tests passed (texts)")
    if not CONTAINER:
        print("database part skipped: no database (set GBIF_VIEW_TEST_CONTAINER to a MySQL container; see the top of this file)")
        sys.exit(0)
    db_tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith("db_test_") and callable(f)]
    for name, fn in db_tests:
        fn()
    print(str(len(db_tests)) + " database tests passed (MySQL " + mysql("select version()")[0][0] + ")")

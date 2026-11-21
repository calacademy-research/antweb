"""tools/place/empty_states.py against a real MySQL (8.4 in CI): plan, apply, the header's rollback (2026-11-21).

Latvia's states: an empty former district (retired), one with specimens written otherwise, one an old name leads to,
one with a curator species list, one with page text (all kept; the specimen's country written LATVIA), and Riga (an ISO subdivision, never a candidate);
Madagascar's empty region (its ISO list is older: never touched). --apply refuses without a reviewed plan; the change
sets is_valid = 0 on the one state with a backup row; the rollback in the module's header puts it back exactly; a
second apply the same day is refused.

  EMPTY_STATES_TEST_CONTAINER   the MySQL container (CI: pfmysql); without it the test is skipped
  EMPTY_STATES_TEST_USER        default root (it creates and drops the database empty_states_test)
  EMPTY_STATES_TEST_PASSWORD    its password
"""
import contextlib
import datetime
import io
import os
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "place"))
import empty_states as es  # noqa: E402

CONTAINER = os.environ.get("EMPTY_STATES_TEST_CONTAINER", "")
USER = os.environ.get("EMPTY_STATES_TEST_USER", "root")
PASSWORD = os.environ.get("EMPTY_STATES_TEST_PASSWORD", "")
DB = "empty_states_test"
TAG = datetime.date.today().strftime("%Y%m%d")

PLACES = [  # id, name, parent, is_valid, valid_name
    (1, "Aglonas Old District", "Latvia", 1, None),        # empty, not ISO: retired
    (2, "Balvu Old District", "Latvia", 1, None),          # a specimen writes it "balvu old district"
    (3, "Cesu Old District", "Latvia", 1, None),           # the old name "Cesis Old" leads to it
    (4, "Cesis Old", "Latvia", 0, "Cesu Old District"),
    (5, "Daugavpils Old District", "Latvia", 1, None),     # a curator species list
    (6, "Jelgavas Old District", "Latvia", 1, None),       # page text
    (7, "Riga", "Latvia", 1, None),                        # ISO LV-RIX
    (8, "Analamanga", "Madagascar", 1, None),              # ISO lists the provinces: never touched
]


def mysql(statement, database=DB):
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-u", USER] + ([database] if database else []) + [
        "--default-character-set=utf8mb4", "--batch", "-N"]
    r = subprocess.run(cmd, input=statement.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=300)
    assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def lit(v):
    return "null" if v is None else "'" + str(v).replace("'", "''") + "'" if isinstance(v, str) else str(v)


def fresh():
    mysql("drop database if exists %s; create database %s default character set utf8mb4" % (DB, DB), None)
    mysql("""create table geolocale (id int not null auto_increment primary key,
               name varchar(250), georank varchar(30), parent varchar(128), source varchar(30), is_valid tinyint,
               valid_name varchar(250), admin_notes text,
               unique key name_parent_georank (name, parent(50), georank)) default charset=utf8mb3 collate utf8mb3_bin;
             create table specimen (code varchar(128) not null primary key, country varchar(128), adm1 varchar(128))
               default charset=utf8mb3 collate utf8mb3_general_ci;
             create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), source varchar(30)) default charset=utf8mb4;
             create table geolocale_taxon_dispute (geolocale_id int, taxon_name varchar(128)) default charset=utf8mb4;
             create table object_edit (object_key varchar(250), title varchar(100), content text) default charset=utf8mb3;""")
    mysql("insert into geolocale (id, name, georank, parent, source, is_valid, valid_name) values "
          + ",".join("(%d, %s, 'adm1', %s, 'antweb', %d, %s)" % (i, lit(n), lit(p), v, lit(vn)) for i, n, p, v, vn in PLACES)
          + "; insert into specimen values ('s1', 'LATVIA', 'balvu old district'), ('s2', 'Latvia', 'Riga')"
          + "; insert into geolocale_taxon values (5, 'Formicinaelasius niger', 'curator'), (1, 'Formicinaelasius flavus', 'specimen')"
          + "; insert into object_edit values ('Jelgavas Old District', 'overview', 'text')")


def snapshot():
    return mysql("select id, name, is_valid, ifnull(valid_name, '<null>'), ifnull(admin_notes, '<null>') from geolocale order by id")


def run(project, folder, *extra):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        code = es.main(["--project", project, "--container", CONTAINER, "--database", DB, "--folder", folder] + list(extra))
    return code, out.getvalue()


def check_plan_apply_rollback(project):
    fresh()
    before = snapshot()
    with tempfile.TemporaryDirectory() as folder:
        try:
            run(project, folder, "--apply")
            raise AssertionError("--apply ran without a reviewed plan")
        except SystemExit as e:
            assert "No plan" in str(e), e
        code, out = run(project, folder)
        assert "retire 1, keep 4" in out, out
        # review of #407: a change that fails (a trigger refuses it) changes nothing and drops its empty backup
        mysql("create trigger no_update before update on geolocale for each row signal sqlstate '45000' set message_text = 'refused'")
        try:
            run(project, folder, "--apply")
            raise AssertionError("the change should have failed")
        except es.pf.SqlFailed:
            pass
        mysql("drop trigger no_update")
        assert snapshot() == before and not mysql("select table_name from information_schema.tables where table_schema = database()"
                                                  " and table_name like 'empty\\_states\\_bak%'"), "a failed apply left something behind"
        plan = open(os.path.join(folder, "plan.tsv"), encoding="utf-8").read()
        assert "retire\tLatvia\tAglonas Old District\t1" in plan and "Riga" not in plan and "Analamanga" not in plan, plan
        assert snapshot() == before, "the plan changed the database"
        code, out = run(project, folder, "--apply")
        assert "after: states of the plan still valid 0" in out, out
        rows = {r[0]: r for r in snapshot()}
        assert rows["1"][2] == "0" and rows["1"][3] == "<null>" and rows["1"][4].startswith("empty_states %s: retired" % TAG), rows["1"]
        assert all(rows[str(i)][2] == "1" for i in (2, 3, 5, 6, 7, 8)), rows
        try:
            run(project, folder, "--apply")
            raise AssertionError("applied twice")
        except SystemExit as e:
            assert "differs" in str(e) or "exists already" in str(e), e
    rollback = es.__doc__.split("Rollback")[1].split("then reload")[0].strip().split("\n")[1:]
    mysql("\n".join(l.strip().replace("<date>", TAG) for l in rollback if l.strip()))
    assert snapshot() == before, "the rollback did not put the rows back"
    # a row edited after the run is left alone by the rollback
    mysql("update geolocale set is_valid = 0, admin_notes = 'empty_states %s: retired' where id = 1" % TAG)
    mysql("update geolocale set admin_notes = 'edited by a curator' where id = 1")
    mysql("\n".join(l.strip().replace("<date>", TAG) for l in rollback if l.strip()))
    assert mysql("select is_valid, admin_notes from geolocale where id = 1") == [["0", "edited by a curator"]]


if __name__ == "__main__":
    if not CONTAINER:
        print("skipped: EMPTY_STATES_TEST_CONTAINER not set")
        sys.exit(0)
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w", encoding="utf-8") as f:
            f.write("user=%s\npassword=%s\n" % (USER, PASSWORD))
        check_plan_apply_rollback(d)
    mysql("drop database %s" % DB, None)
    print("1 test passed (MySQL %s)" % mysql("select version()", None)[0][0])

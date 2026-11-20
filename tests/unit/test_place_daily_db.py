"""tools/place/place_daily.py against a real MySQL: apply, then rollback (review of #389, 2026-11-20).

The rollback is one multi-table UPDATE that sets specimen.adm1 and place_update.applied while its own join reads both
(BINARY s.adm1 = BINARY p.proposed, p.applied = 1). The unit tests only compare its text; here it runs on MySQL 8.4,
through the same `docker exec ... mysql` the tool uses on the servers, before apply mode is switched on:
  1. an apply run writes three states, keeps the old ones in adm1_verbatim and its backup table, rows applied = 1;
  2. an upload then changes one of the three specimens;
  3. the counts before the rollback say 3 applied, 2 to put back;
  4. the rollback puts back two (adm1 and adm1_verbatim from the backup), keeps the one changed since, marks only the
     two rows not applied, and itself reports 2 put back, 1 kept;
  5. a second rollback puts back nothing (0 put back, 1 kept).

Needs a MySQL (8.4 in CI) reachable as `docker exec <container> mysql`:
  PLACE_DAILY_TEST_CONTAINER   the container (CI: pfmysql); without it the test is skipped
  PLACE_DAILY_TEST_USER        default root (it creates and drops the database place_daily_test)
  PLACE_DAILY_TEST_PASSWORD    its password (from the environment, written to a db.properties in a temporary folder)
"""
import importlib.util
import os
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "place"))
spec = importlib.util.spec_from_file_location("place_daily", os.path.join(ROOT, "tools", "place", "place_daily.py"))
pd = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pd)

CONTAINER = os.environ.get("PLACE_DAILY_TEST_CONTAINER", "")
USER = os.environ.get("PLACE_DAILY_TEST_USER", "root")
PASSWORD = os.environ.get("PLACE_DAILY_TEST_PASSWORD", "")
DB = "place_daily_test"
DAY = "2026-11-23"
UPGRADE = os.path.join(ROOT, "db", "upgrade", "9.4.0", "2026-11-16-place-update.sql")


def mysql(statement, database=DB):
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-u", USER] + ([database] if database else []) + [
        "--default-character-set=utf8mb4", "--batch", "-N"]
    r = subprocess.run(cmd, input=statement.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=PASSWORD), timeout=300)
    assert r.returncode == 0, r.stderr.decode("utf-8", "replace")
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def proposal(code, stored, proposed):
    return {"run_date": DAY, "mode": "apply", "code": code, "upload_group": "test", "field": "adm1", "stored_value": stored,
            "proposed": proposed, "basis": "coordinates", "flag": None, "note": None}


def state():
    return {r[0]: (r[1], r[2]) for r in mysql("select code, ifnull(adm1, '<null>'), ifnull(adm1_verbatim, '<null>') from specimen")}


def applied():
    return {r[0]: int(r[1]) for r in mysql("select code, applied from place_update where run_date = '%s' and mode = 'apply'" % DAY)}


def test_apply_then_rollback(project):
    mysql("drop database if exists %s; create database %s default character set utf8mb4" % (DB, DB), None)
    mysql("create table specimen (code varchar(128) not null primary key, country varchar(128), adm1 varchar(128)) default charset=utf8mb4;"
          "insert into specimen values ('s1', 'Madagascar', 'Toliara'), ('s2', 'Madagascar', 'Toliara'), ('s3', 'Madagascar', 'Fianarantsoa'),"
          " ('s4', 'Madagascar', 'Diana')")
    mysql(open(UPGRADE, encoding="utf-8").read())
    rows = [proposal("s1", "Toliara", "Menabe"), proposal("s2", "Toliara", "Atsimo-Andrefana"), proposal("s3", "Fianarantsoa", "Ihorombe")]
    pd.write_sql(project, CONTAINER, pd.apply_statements(rows, DAY), DB)
    assert state() == {"s1": ("Menabe", "Toliara"), "s2": ("Atsimo-Andrefana", "Toliara"), "s3": ("Ihorombe", "Fianarantsoa"),
                       "s4": ("Diana", "<null>")}, state()
    assert applied() == {"s1": 1, "s2": 1, "s3": 1}, applied()
    mysql("update specimen set adm1 = 'Haute Matsiatra', adm1_verbatim = null where code = 's3'")     # an upload since
    before = [int(mysql(q)[0][0]) for q in pd.rollback_counts(DAY)]
    assert before == [3, 2], before
    assert int(mysql(pd.backup_exists(DAY))[0][0]) == 1
    back, kept = pd.rollback_result(pd.write_sql(project, CONTAINER, pd.rollback_statements(DAY), DB))
    assert (back, kept) == (2, 1), (back, kept)
    assert state() == {"s1": ("Toliara", "<null>"), "s2": ("Toliara", "<null>"), "s3": ("Haute Matsiatra", "<null>"),
                       "s4": ("Diana", "<null>")}, state()
    assert applied() == {"s1": 0, "s2": 0, "s3": 1}, applied()      # the kept row stays applied
    assert pd.rollback_result(pd.write_sql(project, CONTAINER, pd.rollback_statements(DAY), DB)) == (0, 1)
    assert state()["s3"] == ("Haute Matsiatra", "<null>")
    mysql("drop database %s" % DB, None)


if __name__ == "__main__":
    if not CONTAINER:
        print("skipped: PLACE_DAILY_TEST_CONTAINER not set")
        sys.exit(0)
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w", encoding="utf-8") as f:
            f.write("user=%s\npassword=%s\n" % (USER, PASSWORD))
        test_apply_then_rollback(d)
    version = mysql("select version()", None)[0][0]
    print("1 test passed (MySQL %s)" % version)

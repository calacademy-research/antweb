"""tools/place/iso_states.py against a real MySQL: plan, apply, the header's rollback, a failed apply (2026-11-20).

Review of 4054e85: the backups and the change must be one transaction, the specimens moved only from the backup, the
rollback able to run in its order (rows added deleted first), and a failed apply must leave the day open for a rerun.
Here the tool runs on a database of its own through the same `docker exec ... mysql` it uses on the servers, with the
real iso3166_2.tsv and Slovakia's states:
  1. plan: Bratislava (3 specimens) and Bratislavsky kraj (1) are one state; Kosice has an old-name row already named
     as ISO writes it; Trnava and Trnavsky kraj are one, but Trnavsky kraj has a curator species list;
  2. --apply refuses without a reviewed plan, then applies it: Bratislava is renamed Bratislavský kraj (its old name
     added, the old name Pressburg pointed at it), Bratislavsky kraj made an old name of it, the four specimens under
     the ISO name; Kosice renamed Košický kraj with the old-name row swapped; Trnava and Trnavsky kraj left alone;
  2b. Egypt (another script) keeps its English names: Al Qāhirah merged into Cairo, Al Jīzah added as an old name of Giza;
  3. the rollback in the module's header puts every place row and specimen back as it was;
  4. an apply whose transaction fails changes nothing and drops its empty backup tables; the rerun then works;
  5. a geolocale.name that is not binary is refused before anything is made.

Needs a MySQL (8.4 in CI) reachable as `docker exec <container> mysql`:
  ISO_STATES_TEST_CONTAINER   the container (CI: pfmysql); without it the test is skipped
  ISO_STATES_TEST_USER        default root (it creates and drops the database iso_states_test)
  ISO_STATES_TEST_PASSWORD    its password (from the environment, written to a db.properties in a temporary folder)
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
import iso_states as iso  # noqa: E402

CONTAINER = os.environ.get("ISO_STATES_TEST_CONTAINER", "")
USER = os.environ.get("ISO_STATES_TEST_USER", "root")
PASSWORD = os.environ.get("ISO_STATES_TEST_PASSWORD", "")
DB = "iso_states_test"
TAG = datetime.date.today().strftime("%Y%m%d")

PLACES = [  # id, name, parent, is_valid, valid_name
    (1, "Bratislava", "Slovakia", 1, None),
    (2, "Bratislavsky kraj", "Slovakia", 1, None),
    (3, "Kosice", "Slovakia", 1, None),
    (4, "Košický kraj", "Slovakia", 0, "Kosice"),
    (5, "Trnava", "Slovakia", 1, None),
    (6, "Trnavsky kraj", "Slovakia", 1, None),
    (7, "Pressburg", "Slovakia", 0, "Bratislava"),
    (8, "Nitriansky kraj", "Slovakia", 1, None),
    (9, "Cairo", "Egypt", 1, None),
    (10, "Al Qāhirah", "Egypt", 1, None),
    (11, "Giza", "Egypt", 1, None),
]
SPECIMENS = [("a1", "Bratislava"), ("a2", "Bratislava"), ("a3", "Bratislava"), ("b1", "Bratislavsky kraj"), ("k1", "Kosice"),
             ("t1", "Trnava"), ("t2", "Trnavsky kraj"), ("n1", "Nitriansky kraj"), ("o1", "Pressburg"), ("x1", "Bratislavský kraj"),
             ("e1", "Cairo"), ("e2", "Cairo"), ("e3", "Al Qāhirah"), ("g1", "Giza")]
COUNTRY = {"e1": "Egypt", "e2": "Egypt", "e3": "Egypt", "g1": "Egypt"}


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
    # as production: geolocale in utf8 with utf8_bin (db/upgrade/6.10/2016-09-12.sql), specimen in another collation
    mysql("""create table geolocale (id int not null auto_increment primary key,
               name varchar(250), georank varchar(30), parent varchar(128), source varchar(30), is_valid tinyint,
               valid_name varchar(250), admin_notes text,
               unique key name_parent_georank (name, parent(50), georank)) default charset=utf8mb3 collate utf8mb3_bin;
             create table specimen (code varchar(128) not null primary key, country varchar(128), adm1 varchar(128))
               default charset=utf8mb3 collate utf8mb3_general_ci;
             create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), source varchar(30)) default charset=utf8mb4;
             create table object_edit (object_key varchar(250), title varchar(100), content text,
               created timestamp not null default current_timestamp, unique key (object_key, title)) default charset=utf8mb3;
             create table object_hist (object_key varchar(250), title varchar(100), content text,
               created timestamp not null default current_timestamp on update current_timestamp) default charset=utf8mb3;""")
    mysql("insert into geolocale (id, name, georank, parent, source, is_valid, valid_name) values "
          + ",".join("(%d, %s, 'adm1', %s, 'antweb', %d, %s)" % (i, lit(n), lit(p), v, lit(vn)) for i, n, p, v, vn in PLACES)
          + "; insert into specimen values " + ",".join("(%s, %s, %s)" % (lit(c), lit(COUNTRY.get(c, "Slovakia")), lit(a)) for c, a in SPECIMENS)
          + "; insert into geolocale_taxon values (6, 'Formicinaecamponotus herculeanus', 'curator')"
          # page text: Bratislava's moves with its rename, Kosice's to Košický kraj (dates kept)
          + "; insert into object_edit (object_key, title, content, created) values ('Bratislava', 'overview', 'Capital region', '2020-01-02 03:04:05'),"
          " ('Kosice', 'overview', 'East', '2021-01-02 03:04:05')"
          "; insert into object_hist (object_key, title, content, created) values ('Bratislava', 'overview', 'Older', '2019-01-02 03:04:05'),"
          " ('bratislava', 'overview', 'Oldest, its key written in small letters', '2018-01-02 03:04:05')")


def snapshot():
    return (mysql("select id, name, parent, is_valid, ifnull(valid_name, '<null>'), ifnull(admin_notes, '<null>') from geolocale order by id"),
            mysql("select code, adm1 from specimen order by code"),
            mysql("select object_key, title, created from object_edit order by object_key, title"),
            mysql("select object_key, title, created from object_hist order by object_key, title"))


def run(project, folder, *extra):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        code = iso.main(["--project", project, "--container", CONTAINER, "--database", DB, "--folder", folder] + list(extra))
    return code, out.getvalue()


def rollback_sql():
    lines = iso.__doc__.split("Rollback")[1].split("then reload")[0].strip().split("\n")[1:]
    return "\n".join(l.strip().replace("<date>", TAG) for l in lines if l.strip())


def check_plan_apply_rollback(project):
    fresh()
    before = snapshot()
    folder = tempfile.mkdtemp()
    try:
        run(project, folder, "--apply")
    except SystemExit as e:
        assert "No plan" in str(e), e
    else:
        raise AssertionError("--apply without a reviewed plan must be refused")
    code, out = run(project, folder)
    plan = open(os.path.join(folder, "plan.tsv"), encoding="utf-8").read()
    assert "rename\tSlovakia\tBratislava\t3\tBratislavský kraj" in plan and "merge\tSlovakia\tBratislavsky kraj\t1" in plan, plan
    assert "curator list\tSlovakia\tTrnavsky kraj" in plan, plan
    assert snapshot() == before                                    # the plan changes nothing
    code, out = run(project, folder, "--apply")
    assert "Trnava, a state to be merged into it has a curator species list" in out and "not merged into" in out, out
    places = {r[0]: r for r in mysql("select name, is_valid, ifnull(valid_name, ''), id from geolocale")}
    assert places["Bratislavský kraj"][1:] == ["1", "", "1"], places
    assert places["Bratislava"][1:3] == ["0", "Bratislavský kraj"] and places["Bratislavsky kraj"][1:] == ["0", "Bratislavský kraj", "2"], places
    # the page text moved with the renames, dates kept
    text = {(r[0], r[1]): r[2] for r in mysql("select object_key, title, created from object_edit")}
    assert text == {("Bratislavský kraj", "overview"): "2020-01-02 03:04:05", ("Košický kraj", "overview"): "2021-01-02 03:04:05"}, text
    assert sorted(mysql("select object_key, created from object_hist")) == [["Bratislavský kraj", "2018-01-02 03:04:05"],
                                                                           ["Bratislavský kraj", "2019-01-02 03:04:05"]]
    assert places["Pressburg"][2] == "Bratislavský kraj", places
    assert places["Košický kraj"][1:] == ["1", "", "3"] and places["Kosice"][1:] == ["0", "Košický kraj", "4"], places
    assert places["Trnava"][1] == "1" and places["Trnavsky kraj"][1] == "1", places
    spec = dict(mysql("select code, adm1 from specimen"))
    assert [spec[c] for c in ("a1", "a2", "a3", "b1")] == ["Bratislavský kraj"] * 4 and spec["k1"] == "Košický kraj", spec
    assert spec["t2"] == "Trnavsky kraj" and spec["o1"] == "Pressburg", spec
    # Egypt keeps its English names: Al Qāhirah merged into Cairo, Al Jīzah added as an old name of Giza
    assert places["Cairo"][1:3] == ["1", ""] and places["Al Qāhirah"][1:3] == ["0", "Cairo"], places
    assert places["Giza"][1:3] == ["1", ""] and places["Al Jīzah"][1:3] == ["0", "Giza"], places
    assert spec["e3"] == "Cairo" and spec["g1"] == "Giza", spec
    assert "specimens under a former name 0 (expect 0)" in out, out
    assert spec["x1"] == "Bratislavský kraj"          # already under the new name before the apply
    # an upload after the apply stores the new name (the old one is an old name of it): the rollback puts it back too,
    # but not x1, which had that name before
    mysql("insert into specimen values ('u1', 'Slovakia', 'Bratislavský kraj')")
    mysql(rollback_sql())
    after = dict(mysql("select code, adm1 from specimen"))
    assert after["u1"] == "Bratislava" and after["x1"] == "Bratislavský kraj", after
    mysql("delete from specimen where code = 'u1'")
    assert snapshot() == before, (snapshot(), before)
    mysql("drop table iso_states_bak_%s; drop table iso_states_spec_%s; drop table iso_states_text_%s" % (TAG, TAG, TAG))


def check_a_failed_apply_leaves_the_day_open(project):
    fresh()
    before = snapshot()
    folder = tempfile.mkdtemp()
    run(project, folder)
    was = iso.moved_specimens
    iso.moved_specimens = lambda spec: "update no_such_table set x = 1"
    try:
        run(project, folder, "--apply")
    except iso.pf.SqlFailed:
        pass
    else:
        raise AssertionError("the apply must fail")
    finally:
        iso.moved_specimens = was
    assert snapshot() == before
    assert not mysql("select table_name from information_schema.tables where table_schema = database() and table_name like 'iso\\_states\\_%'")
    code, out = run(project, folder, "--apply")
    assert code == 0 and dict(mysql("select code, adm1 from specimen"))["a1"] == "Bratislavský kraj"


def check_a_collation_that_ignores_accents_is_refused(project):
    fresh()
    mysql("alter table geolocale modify name varchar(250) character set utf8mb3 collate utf8mb3_general_ci")
    folder = tempfile.mkdtemp()
    run(project, folder)
    try:
        run(project, folder, "--apply")
    except SystemExit as e:
        assert "not a binary one" in str(e), e
    else:
        raise AssertionError("a geolocale.name that ignores accents must be refused")
    assert not mysql("select table_name from information_schema.tables where table_schema = database() and table_name like 'iso\\_states\\_%'")


if __name__ == "__main__":
    if not CONTAINER:
        print("skipped: ISO_STATES_TEST_CONTAINER not set")
        sys.exit(0)
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "etc"))
        with open(os.path.join(d, "etc", "db.properties"), "w", encoding="utf-8") as f:
            f.write("user=%s\npassword=%s\n" % (USER, PASSWORD))
        check_plan_apply_rollback(d)
        check_a_failed_apply_leaves_the_day_open(d)
        check_a_collation_that_ignores_accents_is_refused(d)
    mysql("drop database %s" % DB, None)
    print("3 tests passed (MySQL %s)" % mysql("select version()", None)[0][0])

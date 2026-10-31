"""The database steps of the weekly staging refresh, against a real MySQL 8.4 (CI starts one):
save the staging test logins, "load production" (tables replaced, ids that collide with the test
group and a test login), run tools/staging/scrub.sql, put the test logins back, and check that
  - no real email address and no production password is left, sessions and reset links are gone;
  - the test logins have exactly their saved hashes (so the GitHub secrets still work), in the
    test group, which is created again with a new id when production uses its old id;
  - a kept login that exists in production (the ANTWEB_TEST_USER curator) gets its staging hash
    and projects back, under production's id;
  - production's own rows with the colliding ids are untouched;
  - the administrator's "Upload as" list gets the test uploader's new login number, once.

Run: STAGING_REFRESH_DB_CONTAINER=<mysql container> STAGING_REFRESH_DB_PASSWORD=<root password> \
     python3 tests/unit/test_staging_refresh_db.py      (the container needs a database `ant`)
"""
import base64
import importlib.util
import json
import os
import subprocess
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
STAGING = os.path.join(ROOT, "tools", "staging")
spec = importlib.util.spec_from_file_location("accounts", os.path.join(STAGING, "create-test-accounts.py"))
accounts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(accounts)

CONTAINER = os.environ.get("STAGING_REFRESH_DB_CONTAINER", "refreshmysql")
PASSWORD = os.environ.get("STAGING_REFRESH_DB_PASSWORD", "ci-root")
db = accounts.Db("root", PASSWORD, CONTAINER)

SCHEMA = """
drop table if exists login, ant_group, login_project, login_country, remember_token, password_reset, login_deleted_2026_10_16;
create table login (id int NOT NULL, name varchar(32) NOT NULL, first_name varchar(64) DEFAULT NULL,
  last_name varchar(64) NOT NULL DEFAULT '', email varchar(128) NOT NULL DEFAULT '', password varchar(255) NOT NULL DEFAULT '',
  group_id int DEFAULT NULL, is_admin tinyint DEFAULT 0, is_upload_specimens tinyint DEFAULT 0, is_upload_images tinyint DEFAULT 0,
  upload_as varchar(100) DEFAULT NULL, created timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  last_login timestamp NULL DEFAULT NULL, must_reset tinyint NOT NULL DEFAULT 0, session_version int NOT NULL DEFAULT 0,
  inactive tinyint NOT NULL DEFAULT 0, inactive_since datetime NULL DEFAULT NULL, PRIMARY KEY (id));
create table ant_group (id int NOT NULL, name varchar(100) DEFAULT NULL, admin_login_id int DEFAULT NULL,
  abbrev varchar(20) DEFAULT NULL, PRIMARY KEY (id));
create table login_project (login_id int NOT NULL, project_name varchar(64) NOT NULL);
create table login_country (login_id int NOT NULL, country varchar(64) NOT NULL);
create table remember_token (id int NOT NULL AUTO_INCREMENT, login_id int NOT NULL, selector char(16) NOT NULL,
  token_hash char(64) NOT NULL, prev_hash char(64) NULL, rotated datetime NULL, created datetime NOT NULL,
  expires datetime NOT NULL, last_used datetime NULL, user_agent varchar(255) DEFAULT NULL,
  session_version int NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY selector (selector));
create table password_reset (id int NOT NULL AUTO_INCREMENT, login_id int NOT NULL, token_hash char(64) NOT NULL,
  created datetime NOT NULL, expires datetime NOT NULL, used_at datetime NULL, requested_ip varchar(64) DEFAULT NULL,
  PRIMARY KEY (id), UNIQUE KEY token_hash (token_hash));
create table login_deleted_2026_10_16 (id int NOT NULL, name varchar(32) NOT NULL, email varchar(128) NOT NULL DEFAULT '',
  password varchar(255) NOT NULL DEFAULT '', PRIMARY KEY (id));
"""

H_UPLOADER = accounts.hash_password("uploader-secret")
H_NOUPLOAD = accounts.hash_password("noupload-secret")
H_ADMIN = accounts.hash_password("admin-secret")
H_CURATOR_STAGING = accounts.hash_password("curator-staging-secret")
H_CURATOR_PROD = accounts.hash_password("curator-production-password")
H_BRIAN_PROD = accounts.hash_password("brian-production-password")

STAGING_ROWS = f"""
insert into ant_group values (1, 'California Academy of Sciences', 2, 'CAS'), (75, '{accounts.GROUP_NAME}', 0, 'zztest');
insert into login (id, name, first_name, last_name, email, password, group_id, is_admin, is_upload_specimens, is_upload_images, upload_as) values
 (2, 'bfisher', 'Brian', 'Fisher', 'old@example.org', 'old-staging-hash', 1, 1, 1, 1, NULL),
 (12, 'curator1', 'Jane', 'O''Neil', 'curator1-staging@example.org', '{H_CURATOR_STAGING}', 1, 0, 1, 0, '5,9'),
 (900, 'zztest_uploader', 'ZZ Test', 'zztest_uploader', 'zztest-uploader@example.invalid', '{H_UPLOADER}', 75, 0, 1, 1, NULL),
 (901, 'zztest_noupload', 'ZZ Test', 'zztest_noupload', 'zztest-noupload@example.invalid', '{H_NOUPLOAD}', 75, 0, 0, 0, NULL),
 (902, 'zztest_admin', 'ZZ Test', 'zztest_admin', 'zztest-admin@example.invalid', '{H_ADMIN}', 75, 1, 1, 1, NULL);
insert into login_project values (12, 'madagascarants'), (12, 'fijiants'), (2, 'allantwebants');
insert into login_country values (12, 'Kenya');
"""

# Production's tables: group 75 is a real museum there, login 900 a real person, curator1 has
# its production hash and email, one legacy plain-text password, one unaccepted invitation.
PROD_ROWS = f"""
insert into ant_group values (1, 'California Academy of Sciences', 2, 'CAS'), (75, 'Real Museum', 900, 'RM'), (76, 'Other', 0, 'O');
insert into login (id, name, first_name, last_name, email, password, group_id, is_admin, is_upload_specimens, is_upload_images, session_version, must_reset) values
 (2, 'bfisher', 'Brian', 'Fisher', 'bfisher@calacademy.org', '{H_BRIAN_PROD}', 1, 1, 1, 1, 3, 0),
 (12, 'curator1', 'Jane', 'O''Neil', 'jane@museum.org', '{H_CURATOR_PROD}', 1, 0, 1, 0, 0, 1),
 (900, 'jdoe', 'John', 'Doe', 'john@realmuseum.org', '{accounts.hash_password("x")}', 75, 0, 1, 1, 0, 0),
 (950, 'legacy', 'Old', 'Account', 'old@uni.edu', 'secret', 76, 0, 0, 0, 0, 0),
 (951, 'invited', 'New', 'Person', 'new@uni.edu', '', 76, 0, 0, 0, 0, 0);
insert into login_project values (12, 'californiaants'), (900, 'madagascarants');
insert into remember_token (login_id, selector, token_hash, created, expires) values (2, 'abcdefghijklmnop', repeat('a', 64), now(), now() + interval 30 day);
insert into password_reset (login_id, token_hash, created, expires) values (950, repeat('b', 64), now(), now() + interval 1 hour);
insert into login_deleted_2026_10_16 values (960, 'gone', 'gone@uni.edu', '{H_BRIAN_PROD}');
"""


def rows(sql):
    return db.run(sql)


def value(sql):
    return db.value(sql)


def run_scrub():
    with open(os.path.join(STAGING, "scrub.sql"), encoding="utf-8") as f:
        sql = f.read()
    r = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", CONTAINER, "mysql", "-u", "root", "ant"],
                       input=sql, env=dict(os.environ, MYSQL_PWD=PASSWORD), capture_output=True, text=True)
    assert r.returncode == 0, r.stderr


def check_scrubbed():
    for lid, email, password, sv in rows("select id, email, password, session_version from login where id in (2, 12, 900, 950, 951)"):
        if lid == "951":
            assert email == "login951@example.invalid" and password == "", (email, password)
            continue
        assert email == f"login{lid}@example.invalid", email
        parts = password.split("$")
        assert parts[0] == "pbkdf2_sha256" and parts[1] == "600000", password
        assert len(base64.b64decode(parts[2])) == 16 and len(base64.b64decode(parts[3])) == 32, password
    assert value("select session_version from login where id = 2") == "4"
    assert value("select count(*) from login where password in ('secret', %s, %s)"
                 % (accounts.sql_str(H_BRIAN_PROD), accounts.sql_str(H_CURATOR_PROD))) == "0"
    assert not accounts.password_matches(value("select password from login where id = 2"), "brian-production-password")
    assert value("select count(*) from remember_token") == "0"
    assert value("select count(*) from password_reset") == "0"
    assert value("select count(distinct password) from login where password <> ''") == "4", "a random hash per login"
    # A dated copy of the login table (left by a clean-up) is scrubbed too (2026-10-30).
    email, password = rows("select email, password from login_deleted_2026_10_16 where id = 960")[0]
    assert email.startswith("copy-") and email.endswith("@example.invalid"), email
    assert password.startswith("pbkdf2_sha256$600000$") and password != H_BRIAN_PROD, password
    assert value("select count(*) from information_schema.routines where routine_schema = 'ant'") == "0"


def check_restored(saved):
    gid = int(value(f"select id from ant_group where name = {accounts.q(accounts.GROUP_NAME)}"))
    assert gid == 77, gid
    assert rows("select id, name from ant_group order by id") == [
        ["1", "California Academy of Sciences"], ["75", "Real Museum"], ["76", "Other"], ["77", accounts.GROUP_NAME]]
    want = {"zztest_uploader": (H_UPLOADER, "0", "1", "1"), "zztest_noupload": (H_NOUPLOAD, "0", "0", "0"),
            "zztest_admin": (H_ADMIN, "1", "1", "1")}
    for name, (h, admin, spec_up, img_up) in want.items():
        lid, password, group, a, s, i, email = rows(
            f"select id, password, group_id, is_admin, is_upload_specimens, is_upload_images, email from login where name = '{name}'")[0]
        assert int(lid) > 951, (name, lid)
        assert password == h and (group, a, s, i) == (str(gid), admin, spec_up, img_up), name
        assert email.endswith("@example.invalid")
    assert accounts.password_matches(value("select password from login where name = 'zztest_uploader'"), "uploader-secret")
    # The kept curator: production's id, staging's password, email and projects.
    lid, password, email, upload_as, must_reset = rows(
        "select id, password, email, upload_as, must_reset from login where name = 'curator1'")[0]
    assert (lid, password, email, upload_as, must_reset) == ("12", H_CURATOR_STAGING, "curator1-staging@example.org", "5,9", "0")
    assert [r[0] for r in rows("select project_name from login_project where login_id = 12 order by 1")] == ["fijiants", "madagascarants"]
    assert rows("select country from login_country where login_id = 12") == [["Kenya"]]
    # Production's rows with colliding ids are untouched (but scrubbed).
    assert rows("select name, group_id, email from login where id = 900") == [["jdoe", "75", "login900@example.invalid"]]
    assert rows("select project_name from login_project where login_id = 900") == [["madagascarants"]]
    assert rows("select count(*) from login where name like 'zztest\\_%'") == [["3"]]
    assert value("select count(*) - count(distinct email) from login where email <> ''") == "0", "emails unique"
    assert not any(r[0] == "bfisher" for r in rows("select name from login where password = 'old-staging-hash'"))


def main():
    db.run(SCHEMA + STAGING_ROWS)
    with tempfile.TemporaryDirectory() as d:
        keep = os.path.join(d, "keep-logins.txt")
        with open(keep, "w") as f:
            f.write("curator1  # the e2e curator\nnobody_here\n")
        saved_path = os.path.join(d, "keep-accounts.json")
        saved = accounts.save_accounts(db, accounts.keep_names(accounts.read_keep_logins(keep)))
        accounts.write_saved(saved_path, saved)
        assert oct(os.stat(saved_path).st_mode & 0o777) == "0o600"
        with open(saved_path, encoding="utf-8") as f:
            saved = json.load(f)
        assert [x["name"] for x in saved["logins"]] == ["curator1", "zztest_uploader", "zztest_noupload", "zztest_admin"]
        assert saved["group"]["id"] == 75
        assert [x["in_test_group"] for x in saved["logins"]] == [False, True, True, True]
        assert saved["logins"][0]["projects"] == ["fijiants", "madagascarants"]
        print("ok saved", len(saved["logins"]), "logins")

        db.run(SCHEMA + PROD_ROWS)   # "load production"
        run_scrub()
        check_scrubbed()
        print("ok scrubbed")

        assert accounts.restore_accounts(db, saved) == []
        check_restored(saved)
        print("ok restored")
        assert accounts.restore_accounts(db, saved) == [], "a second restore changes nothing"
        check_restored(saved)
        print("ok restored again (idempotent)")

        # The admin's "Upload as" gets the test uploader's new number, once; curators are left alone.
        uid = value("select id from login where name = 'zztest_uploader'")
        assert accounts.add_upload_as(db, ["bfisher", "curator1", "nobody_here"]) == (1, 1)
        assert value("select upload_as from login where name = 'bfisher'") == uid
        assert value("select upload_as from login where name = 'curator1'") == "5,9"
        assert accounts.add_upload_as(db, ["bfisher"]) == (0, 1), "already listed"
        assert value("select upload_as from login where name = 'bfisher'") == uid
        # A list that is not empty (the format LoginEdit.uploadAsIds reads: numbers, commas).
        db.run("update login set upload_as = '23, 45' where name = 'bfisher'")
        assert accounts.add_upload_as(db, ["bfisher"]) == (1, 1)
        assert value("select upload_as from login where name = 'bfisher'") == "23,45," + uid
        print("ok upload as")


if __name__ == "__main__":
    main()

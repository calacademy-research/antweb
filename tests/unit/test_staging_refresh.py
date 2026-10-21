"""tools/staging/refresh-from-prod.sh, cleanup-disk.sh, staging-refresh.cron and the save/restore
part of create-test-accounts.py: the staging-only guards, dump choice and checks, disk estimate,
the cleanup safe list, and the SQL that puts the test logins back. No server, no database
(the database part is tests/unit/test_staging_refresh_db.py).

Run: python3 tests/unit/test_staging_refresh.py   (also in .github/workflows/tests.yaml)
"""
import gzip
import importlib.util
import os
import re
import subprocess
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
STAGING = os.path.join(ROOT, "tools", "staging")
REFRESH = os.path.join(STAGING, "refresh-from-prod.sh")
CLEANUP = os.path.join(STAGING, "cleanup-disk.sh")

spec = importlib.util.spec_from_file_location("accounts", os.path.join(STAGING, "create-test-accounts.py"))
accounts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(accounts)


def sh(script, *args, source=REFRESH):
    """Run bash with the script sourced (functions only); returns (exit code, stdout)."""
    r = subprocess.run(["bash", "-c", f'source "$SRC"; {script}', "unit-test", *args],
                       env=dict(os.environ, SRC=source), capture_output=True, text=True)
    return r.returncode, r.stdout


GOOD = ("staging", "staging", "1", "/root/antweb-next", "02:30", "165.232.136.170 10.48.0.5")


def guard(*a):
    return sh('guard_problems "$@"', *a)[1].strip().splitlines()


def test_guard_allows_staging_only():
    assert guard(*GOOD) == []
    host, role, ok, cdir, now, ips = GOOD
    assert any("not \"staging\"" in p for p in guard("antweb-prod", role, ok, cdir, now, ips))
    assert any("antweb-role" in p for p in guard(host, "", ok, cdir, now, ips))
    assert any("antweb-role" in p for p in guard(host, "production", ok, cdir, now, ips))
    assert any("db.properties" in p for p in guard(host, role, "0", cdir, now, ips))
    assert any("started from" in p for p in guard(host, role, ok, "/root/antweb", now, ips))
    assert any("started from" in p for p in guard(host, role, ok, "", now, ips))
    assert any("production's address" in p for p in guard(host, role, ok, cdir, now, "165.232.137.86 10.0.0.2"))
    assert any("production's address" in p for p in guard(host, role, ok, cdir, now, "64.225.91.165"))
    # Production as it really is: every check fails.
    assert len(guard("antweb-prod", "", "0", "/root/antweb", "02:30", "165.232.137.86")) == 5


def test_guard_nightly_window():
    host, role, ok, cdir, _now, ips = GOOD
    for t in ("04:45", "05:00", "08:14"):
        assert any("nightly" in p for p in guard(host, role, ok, cdir, t, ips)), t
    for t in ("04:44", "08:15", "02:30", "13:17", "23:59", "00:00"):
        assert guard(host, role, ok, cdir, t, ips) == [], t


def test_newest_dump_and_age():
    names = "2026-10-19.sql.gz\nant-currentDump.sql.gz\n2026-10-21.sql.gz\nerror.log\n2026-10-20.sql.gz\n2026-10-21.sql.gz.partial\n"
    r = subprocess.run(["bash", "-c", f'source "{REFRESH}"; newest_dump'], input=names, capture_output=True, text=True)
    assert r.stdout.strip() == "2026-10-21.sql.gz"
    r = subprocess.run(["bash", "-c", f'source "{REFRESH}"; newest_dump'], input="error.log\n", capture_output=True, text=True)
    assert r.returncode == 0 and r.stdout.strip() == ""
    assert sh('dump_age_days "$1" "$2"', "2026-10-18.sql.gz", "2026-10-21")[1].strip() == "3"
    assert sh('dump_age_days "$1" "$2"', "2026-10-21.sql.gz", "2026-10-21")[1].strip() == "0"
    assert sh('dump_age_days "$1" "$2"', "2026-02-28.sql.gz", "2026-03-01")[1].strip() == "1"


def test_needed_disk():
    mb = 1048576
    assert sh('needed_mb "$1" "$2"', str(120 * mb), "1024")[1].strip() == str(360 + 1024)
    assert sh('needed_mb "$1" "$2"', "1", "0")[1].strip() == "1"


def test_strip_definers_keeps_data():
    text = ("/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER t1 */;\n"
            "CREATE DEFINER=`antweb`@`%` PROCEDURE `p`()\n"
            "INSERT INTO `description_edit` VALUES ('DEFINER=`a`@`b` stays in data');\n")
    r = subprocess.run(["bash", "-c", f'source "{REFRESH}"; strip_definers'], input=text, capture_output=True, text=True)
    out = r.stdout.splitlines()
    assert out[0] == "/*!50003 CREATE*/ /*!50017 */ /*!50003 TRIGGER t1 */;"
    assert out[1] == "CREATE  PROCEDURE `p`()"
    assert out[2] == text.splitlines()[2]


def write_gz(path, text):
    with gzip.open(path, "wt", encoding="utf-8") as f:
        f.write(text)


def test_check_dump():
    body = os.urandom(3000).hex() + "\n"   # does not compress below the 1 KB minimum
    good = "-- Host: localhost\nCREATE DATABASE /*!32312 IF NOT EXISTS*/ `ant`;\n\nUSE `ant`;\n" + body + "-- Dump completed on 2026-10-21  1:02:03\n"
    cases = {
        "good.sql.gz": (good, 0),
        "cut.sql.gz": (good.rsplit("--", 1)[0], 1),
        "other.sql.gz": (good.replace("USE `ant`;", "USE `mysql`;"), 1),
        "two.sql.gz": (good.replace(body, body + "USE `mysql`;\n"), 1),
        "tiny.sql.gz": ("USE `ant`;\n-- Dump completed\n", 1),
    }
    with tempfile.TemporaryDirectory() as d:
        for name, (text, want) in cases.items():
            write_gz(os.path.join(d, name), text)
            rc, _ = sh('check_dump "$1"', os.path.join(d, name))
            assert rc == want, (name, rc)
        broken = os.path.join(d, "broken.sql.gz")
        with open(os.path.join(d, "good.sql.gz"), "rb") as f:
            data = f.read()
        with open(broken, "wb") as f:
            f.write(data[: len(data) - 20])
        assert sh('check_dump "$1"', broken)[0] == 1
        assert sh('check_dump "$1"', os.path.join(d, "missing.sql.gz"))[0] == 1


def test_options():
    r = subprocess.run(["bash", REFRESH, "--bogus"], capture_output=True, text=True)
    assert r.returncode == 64
    r = subprocess.run(["bash", REFRESH, "--not-after", "3:15"], capture_output=True, text=True)
    assert r.returncode == 64
    r = subprocess.run(["bash", REFRESH, "--help"], capture_output=True, text=True)
    assert r.returncode == 0 and "--dry-run" in r.stdout and "--rollback" in r.stdout


def test_read_only_on_production():
    """The only rclone calls list the bucket or copy FROM it; nothing writes to production."""
    src = open(REFRESH, encoding="utf-8").read()
    code = "\n".join(line for line in src.splitlines() if not line.lstrip().startswith("#"))
    calls = re.findall(r"(?:^|\$\(|[;&|]\s)\s*rclone\s+(\w+)", code, re.M)
    assert sorted(set(calls)) == ["copyto", "lsf"], calls
    for line in re.findall(r"rclone copyto[^\n]*", src):
        assert re.search(r'"\$REMOTE/\$DUMP_NAME" "\$local_dump"', line), line
    code = code.replace('PROD_ADDRESSES="165.232.137.86 64.225.91.165"', "")
    for word in ("ssh ", "scp ", "165.232.137.86", "rclone sync", "rclone delete", "rclone purge", "rclone move",
                 "rclone mount", "rclone copy ", "mount "):
        assert word not in code, word


def test_safe_list():
    yes = ["/root/ant-currentDump.sql.gz", "/root/2025-01-01.sql.gz", "/root/restore/2026-10-18.sql.gz",
           "/root/antweb-next/logs/web/compute.log.1.gz", "/root/antweb-next/logbak/emails.log.2.gz",
           "/root/antweb-watchdog-dumps/dump-1.txt"]
    no = ["/root/antweb-next/etc/db.properties", "/var/lib/docker/volumes/x", "/mnt/backup/db/daily/2026-10-18.sql.gz",
          "/mnt/antweb/images/x.jpg", "/root/staging-refresh/staging-before-refresh.sql.gz", "/root/antweb-next/logs/catalina.out",
          "/root/restore/../antweb-next/etc/x.sql.gz", "/root/antweb-watchdog-dumps/sub/x", "/root/.ssh/id_rsa", "/etc/passwd"]
    for p in yes:
        assert sh('on_safe_list "$1"', p, source=CLEANUP)[0] == 0, p
    for p in no:
        assert sh('on_safe_list "$1"', p, source=CLEANUP)[0] != 0, p


def test_cleanup_never_touches_volumes_or_spaces():
    src = open(CLEANUP, encoding="utf-8").read()
    code = "\n".join(line for line in src.splitlines() if not line.lstrip().startswith("#"))
    assert "volume prune" not in code and "system prune" not in code and "image prune -a" not in code
    assert not re.search(r"rm [^\n]*/mnt/", code)
    assert "APPLY=0" in src and "--dry-run|\"\") APPLY=0" in src, "report-only is the default"


def test_cron_line():
    lines = [line for line in open(os.path.join(STAGING, "staging-refresh.cron"), encoding="utf-8")
             if line.strip() and not line.startswith("#") and "=" not in line.split()[0]]
    assert len(lines) == 1
    f = lines[0].split()
    minute, hour, dom, month, dow, user, cmd = f[:7]
    assert (minute, hour, dom, month, dow, user) == ("30", "2", "*", "*", "0", "root")
    assert cmd == "/root/antweb-next/tools/staging/refresh-from-prod.sh"
    assert "--not-after" in f and f[f.index("--not-after") + 1] == "03:15"
    # After production's 01:00 dump; the latest stop point is well before the 04:00 reboot.
    assert 60 < int(hour) * 60 + int(minute) < 3 * 60 + 15 < 4 * 60


def test_scripts_executable():
    out = subprocess.run(["git", "-C", ROOT, "ls-files", "-s", "tools/staging"], capture_output=True, text=True).stdout
    modes = {line.split("\t")[1]: line.split()[0] for line in out.splitlines()}
    for name in ("refresh-from-prod.sh", "cleanup-disk.sh"):
        path = "tools/staging/" + name
        if path in modes:   # in a git checkout
            assert modes[path] == "100755", (path, modes[path])


def test_scrub_sql_covers_personal_data():
    sql = open(os.path.join(STAGING, "scrub.sql"), encoding="utf-8").read().lower()
    assert "@example.invalid" in sql and "set password" in sql and "random_bytes" in sql
    assert "delete from remember_token" in sql and "delete from password_reset" in sql
    assert "session_version = session_version + 1" in sql
    # Never a fixed password (it would be a known plain-text login).
    assert not re.search(r"set password\s*=\s*'", sql)


# ---------- create-test-accounts.py --save-to / --restore-from (pure parts) ----------

SAVED = {
    "version": 1, "saved": "2026-10-25T02:31:00-07:00",
    "group": {"name": accounts.GROUP_NAME, "abbrev": "zztest", "id": 75},
    "logins": [
        {"id": 900, "name": "zztest_uploader", "first_name": "ZZ Test", "last_name": "zztest_uploader",
         "email": "zztest-uploader@example.invalid", "password": "pbkdf2_sha256$600000$c2FsdA==$aGFzaA==",
         "group_id": 75, "is_admin": 0, "is_upload_specimens": 1, "is_upload_images": 1, "upload_as": None,
         "in_test_group": True, "projects": [], "countries": []},
        {"id": 12, "name": "curator1", "first_name": "O'Brien", "last_name": "Test\\Name",
         "email": "c1@example.org", "password": "pbkdf2_sha256$600000$eA==$eQ==", "group_id": 1, "is_admin": 0,
         "is_upload_specimens": 1, "is_upload_images": 0, "upload_as": "5,9", "in_test_group": False,
         "projects": ["madagascarants"], "countries": ["Kenya"]},
    ],
}


def test_sql_str():
    assert accounts.sql_str(None) == "NULL"
    assert accounts.sql_str("O'Brien") == "'O''Brien'"
    assert accounts.sql_str("a\\b") == "'a\\\\b'"
    try:
        accounts.sql_str("a\x00b")
        raise AssertionError("NUL accepted")
    except ValueError:
        pass


def test_restore_statements():
    cols = {"id", "name", "password", "email", "group_id", "inactive", "inactive_since", "must_reset", "created"}
    sql = accounts.restore_statements(SAVED, 301, {"curator1": 12}, cols, True, True)
    lines = sql.strip().splitlines()
    ins = [x for x in lines if x.startswith("insert into login ")]
    assert len(ins) == 1 and "'zztest_uploader'" in ins[0] and "coalesce(max(id), 0) + 1" in ins[0]
    assert "pbkdf2_sha256$600000$c2FsdA==$aGFzaA==" in ins[0]
    assert ", 301," in ins[0] or " 301," in ins[0], "the test login joins the test group's new id"
    upd = [x for x in lines if x.startswith("update login ")]
    assert len(upd) == 1 and upd[0].endswith("where id = 12;") and "group_id = 1," in upd[0]
    assert "inactive = 0" in upd[0] and "must_reset = 0" in upd[0] and "created = created" in upd[0]
    assert "upload_as = '5,9'" in upd[0]
    assert "insert into login_project (login_id, project_name) values (12, 'madagascarants');" in lines
    assert "insert into login_country (login_id, country) values (12, 'Kenya');" in lines
    # Without the optional columns and tables nothing refers to them.
    sql2 = accounts.restore_statements(SAVED, 301, {}, {"id", "name"}, False, False)
    assert "inactive" not in sql2 and "login_project" not in sql2 and "created = created" not in sql2
    assert sql2.count("insert into login ") == 2


def test_keep_logins_file():
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "keep.txt")
        with open(p, "w") as f:
            f.write("# the e2e test curator\ncurator1\n\n  curator2   # second\ncurator1\n")
        assert accounts.read_keep_logins(p) == ["curator1", "curator2"]
        assert accounts.read_keep_logins(os.path.join(d, "none.txt")) == []
        assert accounts.keep_names(["curator1", "zztest_admin"])[:3] == ["zztest_uploader", "zztest_noupload", "zztest_admin"]
        assert accounts.keep_names(["curator1", "zztest_admin"]).count("zztest_admin") == 1
        with open(p, "w") as f:
            f.write("bad name'; drop table login\n")
        try:
            accounts.read_keep_logins(p)
            raise AssertionError("bad name accepted")
        except SystemExit:
            pass


def main():
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)


if __name__ == "__main__":
    main()

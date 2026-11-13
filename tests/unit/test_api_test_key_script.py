#!/usr/bin/env python3
"""tools/api/create-test-key.py, the API key of the end-to-end tests on staging (2026-11-09): pure parts.

- staging only (host name and project folder);
- the key fits the pattern the API accepts; only its SHA-256 and first 7 characters go into SQL or the saved file;
- one admin, active key with its own limits, replacing any earlier test key;
- the database password is never on a command line, the SQL goes in on stdin;
- the weekly refresh puts the key back after the scrub and before AntWeb starts, and a failure there is no rollback.

Run: python3 tests/unit/test_api_test_key_script.py
"""
import hashlib
import importlib.util
import json
import os
import re
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
SCRIPT = os.path.join(ROOT, "tools", "api", "create-test-key.py")
spec = importlib.util.spec_from_file_location("create_test_key", SCRIPT)
tk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tk)
SRC = open(SCRIPT, encoding="utf-8").read()


def test_staging_only():
    assert tk.guard_problems("staging", "/root/antweb-next") == []
    assert tk.guard_problems("antweb-prod", "/root/antweb-next"), "production is refused"
    assert tk.guard_problems("staging", "/root/antweb"), "production's folder is refused"
    assert tk.guard_problems("staging", "/root/antweb-next", properties_exist=False)
    assert "guard_problems(socket.gethostname()" in SRC and SRC.index("guard_problems(socket.gethostname()") < SRC.index("have_table():\n        sys.exit")


def test_key_fits_the_api_and_only_its_hash_is_kept():
    api_keys = open(os.path.join(ROOT, "api", "v3.1", "api_keys.py"), encoding="utf-8").read()
    pattern = re.search(r"KEY_RE = re\.compile\(r?'([^']+)'\)", api_keys)
    assert pattern, "KEY_RE in api_keys.py"
    key = tk.new_key()
    assert re.fullmatch(pattern.group(1).rstrip("$").lstrip("^"), key) and len(key) == 43, len(key)
    assert tk.new_key() != key
    hashed = tk.key_hash(key)
    assert hashed == hashlib.sha256(key.encode()).hexdigest()
    sql = ";".join(tk.insert_statements(key[:7], hashed))
    assert key not in sql and hashed in sql and key[:7] in sql
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "x", "api-test-key.json")
        tk.write_saved(key[:7], hashed, path)
        assert oct(os.stat(path).st_mode & 0o777) == "0o600"
        text = open(path).read()
        assert key not in text and json.loads(text) == {"version": 1, "prefix": key[:7], "key_hash": hashed}
        assert tk.read_saved(path) == (key[:7], hashed)
        open(path, "w").write('{"prefix": "aw_abcd", "key_hash": "x\' or 1=1"}')
        assert tk.read_saved(path) is None, "a damaged file is not used"
        assert tk.read_saved(os.path.join(d, "none.json")) is None
    # the key itself is printed once and written nowhere
    assert SRC.count("print(key)") == 1 and "write_saved(prefix, hashed)" in SRC
    assert not re.search(r"(write|dump|log)\w*\([^)]*\bkey\b[,)]", SRC.replace("print(key)", "")), "the key goes nowhere else"


def test_only_the_test_key_is_ever_deleted():
    """Review of pull request 348 (2026-11-12): the clean-up also deleted any key with the same first characters
    (`or prefix = ...`), so a registrant's key that happened to start like the test key would have been removed
    without a word. The clean-up names the test key by its name or its own hash, nothing else; the script makes
    sure beforehand that no other key has the prefix (it is unique in the table), and changes nothing if one has."""
    delete, insert = tk.insert_statements("aw_abcd", "a" * 64)
    assert delete == ("delete from api_key where name = 'ZZ e2e test key (staging only)' or key_hash = '" + "a" * 64 + "'"), delete
    assert "prefix" not in delete and "aw_abcd" not in delete
    assert "'aw_abcd'" in insert, "the prefix is still stored with the new row"
    for line in SRC.splitlines():
        if "delete from api_key" in line:
            assert "prefix" not in line, line
    assert SRC.count("delete from api_key") == 2, "the clean-up before the insert, and --remove (by name)"
    # is the prefix free? Asked about the other keys only, before anything is changed
    taken = tk.prefix_taken_sql("aw_abcd")
    assert taken == "select 1 from api_key where prefix = 'aw_abcd' and name <> 'ZZ e2e test key (staging only)' limit 1", taken
    try:
        tk.prefix_taken_sql("aw_ab'd")
        raise AssertionError("a prefix that is not one was put into SQL")
    except ValueError:
        pass
    new = SRC[SRC.index("    for _attempt in range(20):"):SRC.index("    put_in_place(prefix, hashed)")]
    assert "if not prefix_taken(key[:7]):" in new and "nothing was changed" in new
    restore = SRC[SRC.index("        if args.restore:"):SRC.index("        row = state(saved[1])")]
    assert restore.index("prefix_taken(saved[0])") < restore.index("put_in_place(*saved)") and "return 1" in restore
    # one transaction: a failed insert does not leave staging without the test key that was there
    calls = []
    original = tk.mysql
    tk.mysql = lambda sql: calls.append(sql) or []
    try:
        tk.put_in_place("aw_abcd", "a" * 64)
    finally:
        tk.mysql = original
    assert len(calls) == 1 and calls[0].startswith("start transaction;\ndelete from api_key") and calls[0].endswith(";\ncommit;"), calls
    assert "insert_statements(" not in SRC.split("def main(")[1], "main() changes the key only through put_in_place()"


def test_one_admin_key_with_its_own_limits():
    delete, insert = tk.insert_statements("aw_abcd", "a" * 64)
    assert delete.startswith("delete from api_key where name = 'ZZ e2e test key (staging only)'")
    assert "'admin', 'active', utc_timestamp()" in insert
    assert "per_day, per_minute, records_per_day" in insert and insert.rstrip(")").endswith("200000, 600, 10000000")
    assert "max_records" not in insert, "records an answer stay at the default 500 (the tests pin the cap)"
    assert "@example.invalid" in insert
    for bad in (("aw_abcd", "zz"), ("x", "a" * 64), ("aw_ab'd", "a" * 64)):
        try:
            tk.insert_statements(*bad)
            raise AssertionError(bad)
        except ValueError:
            pass


def test_password_never_on_a_command_line():
    cmd = SRC[SRC.index("def mysql(sql):"):SRC.index("def write_saved")]
    assert 'env = dict(os.environ, MYSQL_PWD=password)' in cmd and '"-e", "MYSQL_PWD", DB_CONTAINER' in cmd
    assert "input=sql" in cmd and "-p" not in re.findall(r'"(-[a-zA-Z])"', cmd) and "password" not in cmd.split("cmd = ")[1].split("]")[0]


def test_refresh_puts_the_key_back():
    src = open(os.path.join(ROOT, "tools", "staging", "refresh-from-prod.sh"), encoding="utf-8").read()
    scrub = src.index('mysql_ant < "$PROJECT_DIR/tools/staging/scrub.sql"')
    key = src.index('create-test-key.py" --restore 2>&1')
    start = src.index('docker start "$APP_CONTAINER" >/dev/null\n  APP_STOPPED=0\n  IN_RESTORE=0')
    assert scrub < key < start
    assert '|| log "WARNING: the API test key was not put back' in src, "a failure there is no rollback"


def test_script_is_executable_in_git():
    import subprocess
    r = subprocess.run(["git", "ls-files", "-s", "tools/api/create-test-key.py"], cwd=ROOT, capture_output=True, text=True)
    if r.returncode == 0 and r.stdout.strip():
        assert r.stdout.split()[0] == "100755", r.stdout


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok  ", name)
            except Exception as e:  # noqa: BLE001
                failed += 1
                import traceback
                traceback.print_exc()
                print("FAIL", name, repr(e))
    sys.exit(1 if failed else 0)

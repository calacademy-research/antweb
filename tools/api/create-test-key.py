#!/usr/bin/env python3
"""The API key of the end-to-end tests, on AntWeb STAGING only.

Once v4 is open, v3.1 has ended and the admin switch up=1 does nothing (decided by Brian Fisher 2026-11-09), so the
end-to-end tests reach the API through v4 with an admin key (tests/e2e/antweb.py). This script makes that key in
staging's database and prints it ONCE, for the GitHub Actions secret ANTWEB_TEST_API_KEY. Nothing but its SHA-256
is stored: in the table api_key (as for every key) and in /root/staging-refresh/api-test-key.json (root only), from
which the weekly refresh from production puts the same key back (tools/staging/refresh-from-prod.sh, --restore),
so the secret keeps working.

Run on the staging droplet (ssh root@165.232.136.170), from the deployed copy of the repository:

    python3 /root/antweb-next/tools/api/create-test-key.py             # make a new key (replaces the old test key)
    python3 /root/antweb-next/tools/api/create-test-key.py --restore   # put the saved key back (after a refresh)
    python3 /root/antweb-next/tools/api/create-test-key.py --check     # is the saved key in the database and active?
    python3 /root/antweb-next/tools/api/create-test-key.py --remove    # delete the test key and the saved file

The key: level admin, status active, with its own limits so a full test run fits (600 requests a minute, 200,000
requests a day, 10,000,000 records a day; records an answer stay at the default 500, which the tests pin). It shows
on the admin page apiKeys.do as "ZZ e2e test key (staging only)". The API reads keys from the database on every
request: no restart is needed.

The database login is read from /root/antweb-next/etc/db.properties and handed to the mysql client in its
environment (never on a command line); the SQL goes in on stdin. Refuses to run unless the host name is "staging"
and the repository is /root/antweb-next. The key is never written to disk or to a log by this script.
"""
import argparse
import hashlib
import json
import os
import re
import secrets
import socket
import subprocess
import sys

PROJECT_DIR = "/root/antweb-next"
EXPECTED_HOSTNAME = "staging"
DB_PROPERTIES = PROJECT_DIR + "/etc/db.properties"
DB_CONTAINER = "antweb_mysql_1"
DATABASE = "ant"
SAVED = "/root/staging-refresh/api-test-key.json"

NAME = "ZZ e2e test key (staging only)"
EMAIL = "e2e-tests@example.invalid"
PROJECT = "End-to-end tests of AntWeb (GitHub Actions secret ANTWEB_TEST_API_KEY)"
NOTE = "Made by tools/api/create-test-key.py. Staging only."
LIMITS = {"per_minute": 600, "per_day": 200000, "records_per_day": 10000000}   # max_records: NULL (default 500)
KEY_RE = re.compile(r"aw_[A-Za-z0-9_-]{20,80}")    # api/v3.1/api_keys.py KEY_RE
HASH_RE = re.compile(r"[0-9a-f]{64}")
PREFIX_RE = re.compile(r"aw_[A-Za-z0-9_-]{4}")


def new_key():
    """A key as api_keys.new_key(): aw_ and 40 random URL-safe characters (240 bits)."""
    return "aw_" + secrets.token_urlsafe(30)


def key_hash(key):
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


def guard_problems(hostname, repo_dir, properties_exist=True):
    """Reasons not to run (empty list: go ahead). Pure, unit-tested."""
    problems = []
    if hostname != EXPECTED_HOSTNAME:
        problems.append(f'this host is "{hostname}", not "{EXPECTED_HOSTNAME}": staging only')
    if os.path.normpath(repo_dir) != PROJECT_DIR:
        problems.append(f"this script is in {repo_dir}, not {PROJECT_DIR}: run the staging copy")
    if not properties_exist:
        problems.append(f"{DB_PROPERTIES} not found")
    return problems


def q(value):
    """SQL string literal for the fixed values used here (no quotes or backslashes in them)."""
    s = str(value)
    if re.search(r"['\\\x00\n\r]", s):
        raise ValueError("unexpected character in a value")
    return "'" + s + "'"


def insert_statements(prefix, hashed):
    """The SQL that puts the test key in place: any earlier test key (by its name, or the same hash) is removed
    first, so there is always exactly one. Never by the prefix: the first characters of a key are not its owner, and
    another person's key that happens to start the same would be deleted (review of pull request 348, 2026-11-12).
    The prefix is unique in the table, so main() makes sure it is free first (prefix_taken). Pure, unit-tested."""
    if not HASH_RE.fullmatch(hashed) or not PREFIX_RE.fullmatch(prefix):
        raise ValueError("not a key hash and prefix")
    cols = ["prefix", "key_hash", "name", "email", "project", "level", "status", "confirmed", "note"] + sorted(LIMITS)
    values = [q(prefix), q(hashed), q(NAME), q(EMAIL), q(PROJECT), "'admin'", "'active'", "utc_timestamp()", q(NOTE)]
    values += [str(int(LIMITS[k])) for k in sorted(LIMITS)]
    return [
        f"delete from api_key where name = {q(NAME)} or key_hash = {q(hashed)}",
        f"insert into api_key ({', '.join(cols)}) values ({', '.join(values)})",
    ]


def read_db_properties(path=DB_PROPERTIES):
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    if not props.get("user") or "password" not in props:
        sys.exit(f"{path} has no user= or password= line")
    return props["user"], props["password"]


def mysql(sql):
    user, password = read_db_properties()
    env = dict(os.environ, MYSQL_PWD=password)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", DB_CONTAINER, "mysql", "-N", "-B", "-u", user, DATABASE]
    r = subprocess.run(cmd, input=sql, env=env, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("database command failed: " + r.stderr.strip())
    return [line.split("\t") for line in r.stdout.splitlines() if line != ""]


def write_saved(prefix, hashed, path=SAVED):
    os.makedirs(os.path.dirname(path), mode=0o700, exist_ok=True)
    fd = os.open(path + ".tmp", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "prefix": prefix, "key_hash": hashed}, f)
    os.replace(path + ".tmp", path)


def read_saved(path=SAVED):
    """(prefix, hash) of the saved test key, or None when there is no (usable) file."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        prefix, hashed = data["prefix"], data["key_hash"]
    except (OSError, ValueError, KeyError, TypeError):
        return None
    if not isinstance(prefix, str) or not isinstance(hashed, str):
        return None
    if not HASH_RE.fullmatch(hashed) or not PREFIX_RE.fullmatch(prefix):
        return None
    return prefix, hashed


def have_table():
    return bool(mysql("select 1 from information_schema.tables where table_schema = database() and table_name = 'api_key'"))


def prefix_taken_sql(prefix):
    """Is the prefix in use by a key that is not the test key? (api_key.prefix is unique.)"""
    if not PREFIX_RE.fullmatch(prefix):
        raise ValueError("not a key prefix")
    return f"select 1 from api_key where prefix = {q(prefix)} and name <> {q(NAME)} limit 1"


def prefix_taken(prefix):
    return bool(mysql(prefix_taken_sql(prefix)))


def put_in_place(prefix, hashed):
    """The delete and the insert as one transaction: if the insert fails, the test key that was there stays."""
    mysql("start transaction;\n" + ";\n".join(insert_statements(prefix, hashed)) + ";\ncommit;")


def state(hashed):
    rows = mysql(f"select id, level, status from api_key where key_hash = {q(hashed)}")
    return rows[0] if rows else None


def main(argv=None):
    ap = argparse.ArgumentParser(description="The API key of the end-to-end tests (staging only).")
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--restore", action="store_true", help="put the saved key back (after the weekly refresh)")
    g.add_argument("--check", action="store_true", help="say whether the saved key is in the database and active")
    g.add_argument("--remove", action="store_true", help="delete the test key and the saved file")
    args = ap.parse_args(argv)

    problems = guard_problems(socket.gethostname(), os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                              os.path.exists(DB_PROPERTIES))
    if problems:
        sys.exit("Not run: " + "; ".join(problems))
    if not have_table():
        sys.exit("the table api_key does not exist: run tools/api/setup-usage-login.sh first")

    if args.remove:
        mysql(f"delete from api_key where name = {q(NAME)}")
        if os.path.exists(SAVED):
            os.remove(SAVED)
        print("The test key is deleted. Remove the GitHub secret ANTWEB_TEST_API_KEY as well.")
        return 0

    if args.restore or args.check:
        saved = read_saved()
        if saved is None:
            print(f"No saved test key ({SAVED}): nothing to put back. Make one with this script without options.")
            return 0 if args.restore else 1
        if args.restore:
            if prefix_taken(saved[0]):
                print(f"Not put back: another key starts with {saved[0]} too. Make a new test key (this script without"
                      " options) and put it in the GitHub secret ANTWEB_TEST_API_KEY. No key was changed.")
                return 1
            put_in_place(*saved)
        row = state(saved[1])
        ok = row is not None and row[1] == "admin" and row[2] == "active"
        print(f"Test key {saved[0]}...: " + ("in the database, admin, active" if ok else "NOT in the database as an active admin key"))
        return 0 if ok else 1

    for _attempt in range(20):       # a key whose first characters no other key has (they are unique in the table)
        key = new_key()
        if not KEY_RE.fullmatch(key):
            sys.exit("key generation failed")
        if not prefix_taken(key[:7]):
            break
    else:
        sys.exit("no key with unused first characters after 20 tries; nothing was changed")
    prefix, hashed = key[:7], key_hash(key)
    put_in_place(prefix, hashed)
    if state(hashed) is None:
        sys.exit("the key was not saved in api_key")
    write_saved(prefix, hashed)
    print("The end-to-end test key is made (admin, active; only its SHA-256 is stored).")
    print("Copy the next line into the GitHub Actions secret ANTWEB_TEST_API_KEY. It is shown only now:")
    print()
    print(key)
    print()
    print("GitHub: repository Settings > Secrets and variables > Actions > New repository secret.")
    print("Do not paste it anywhere else. Then clear the terminal (clear).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

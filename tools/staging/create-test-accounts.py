#!/usr/bin/env python3
"""Test accounts for the end-to-end tests, on AntWeb STAGING only.

Creates (or, with --remove, deletes) on staging:
  - the group "ZZ Test Group (staging only)" (abbreviation zztest); its specimens use codes that
    start with "zztest" (for example zztest0001), a prefix no real collection uses;
  - zztest_uploader  curator in that group, may upload specimens and images;
  - zztest_noupload  curator in that group, may upload nothing;
  - zztest_admin     administrator (its own group is the test group, so an upload it makes
                     without "Upload as" also lands in the test group).

Run on the staging droplet (ssh root@165.232.136.170), from the deployed copy of the repository:

    python3 /root/antweb-next/tools/staging/create-test-accounts.py            # create
    python3 /root/antweb-next/tools/staging/create-test-accounts.py --remove   # delete again

Creating is idempotent: an existing group or login is kept as it is (its password is not
changed; --reset-passwords gives the three logins new passwords). Each new password is made
here with a cryptographically random generator, only its hash goes into the database (the same
PBKDF2-HMAC-SHA256 format as src/org/calacademy/antweb/util/PasswordHash.java), and it is
printed once, for the GitHub secrets. Nothing is written to disk.

The database login is read from /root/antweb-next/etc/db.properties and handed to the mysql
client in its environment (never on a command line). AntWeb keeps logins and groups in memory,
so the script restarts the staging AntWeb container (2 to 4 minutes) and then checks each new
password by logging in through the local port 127.0.0.1:8081.

Refuses to run unless the host name is "staging" and the repository is /root/antweb-next, and
between 04:45 and 08:15 Pacific (the nightly run).

Weekly refresh from production (tools/staging/refresh-from-prod.sh) uses two more options:
    --save-to FILE [--keep-logins FILE]   before the load: save the test group and the logins to
                                          keep, with their password hashes (JSON, chmod 600)
    --restore-from FILE                   after the load: put them back with the same hashes, so
                                          the passwords in the GitHub secrets keep working
    --add-upload-as [FILE]                after the restore: add zztest_uploader's login number to
                                          the "Upload as" list of the administrator login(s) named
                                          in FILE (default /root/staging-refresh/upload-as.txt,
                                          lines adminLogin=NAME; no file: the login antweb), only
                                          where it is missing
"""
import argparse
import base64
import datetime
import hashlib
import http.cookiejar
import json
import os
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.parse
import urllib.request

PROJECT_DIR = "/root/antweb-next"
EXPECTED_HOSTNAME = "staging"
DB_PROPERTIES = PROJECT_DIR + "/etc/db.properties"
DB_CONTAINER = "antweb_mysql_1"
APP_CONTAINER = "antweb_antweb_1"
APP_LOCAL_URL = "http://127.0.0.1:8081"
DATABASE = "ant"

GROUP_NAME = "ZZ Test Group (staging only)"
GROUP_ABBREV = "zztest"
CODE_PREFIX = "zztest"   # specimen codes of the test group (lower case, as AntWeb stores them)

# (login name, GitHub secret prefix, is_admin, upload specimens, upload images)
ACCOUNTS = [
    ("zztest_uploader", "ANTWEB_TEST_UPLOADER", False, True, True),
    ("zztest_noupload", "ANTWEB_TEST_NOUPLOAD", False, False, False),
    ("zztest_admin", "ANTWEB_TEST_ADMIN", True, True, True),
]

# Same parameters as PasswordHash.java: PBKDF2WithHmacSHA256, 600,000 iterations, 16-byte salt,
# 256-bit hash, stored as pbkdf2_sha256$iterations$salt$hash (standard base64 with padding).
HASH_PREFIX = "pbkdf2_sha256"
HASH_ITERATIONS = 600_000
SALT_BYTES = 16
HASH_BYTES = 32


# ---------- password hashing (pure functions, unit-tested in tests/unit/test_staging_accounts.py) ----------

def hash_password(plain, salt=None, iterations=HASH_ITERATIONS):
    """The value PasswordHash.hash(plain) would store (Java encodes the password as UTF-8)."""
    if salt is None:
        salt = secrets.token_bytes(SALT_BYTES)
    digest = hashlib.pbkdf2_hmac("sha256", plain.encode("utf-8"), salt, iterations, HASH_BYTES)
    b64 = lambda b: base64.b64encode(b).decode("ascii")  # noqa: E731
    return f"{HASH_PREFIX}${iterations}${b64(salt)}${b64(digest)}"


def password_matches(stored, plain):
    """PasswordHash.matches() for hashed values."""
    parts = (stored or "").split("$")
    if len(parts) != 4 or parts[0] != HASH_PREFIX:
        return False
    iterations = int(parts[1])
    salt = base64.b64decode(parts[2])
    expected = base64.b64decode(parts[3])
    actual = hashlib.pbkdf2_hmac("sha256", plain.encode("utf-8"), salt, iterations, len(expected))
    return secrets.compare_digest(actual, expected)


def new_password():
    """32 random characters, letters and digits only (easy to paste, no shell quoting issues)."""
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    return "".join(secrets.choice(alphabet) for _ in range(32))


# ---------- guards ----------

def guard_problems(hostname, repo_dir, now_pacific, project_dir_exists=True, container_dir=None):
    """Reasons not to run (empty list: go ahead). Pure, unit-tested."""
    problems = []
    if hostname != EXPECTED_HOSTNAME:
        problems.append(f'this host is "{hostname}", not "{EXPECTED_HOSTNAME}": staging only')
    if os.path.normpath(repo_dir) != PROJECT_DIR:
        problems.append(f"this script is in {repo_dir}, not {PROJECT_DIR}: run the staging copy")
    if not project_dir_exists:
        problems.append(f"{DB_PROPERTIES} not found")
    if container_dir is not None and os.path.normpath(container_dir) != PROJECT_DIR:
        problems.append(f"{APP_CONTAINER} was started from {container_dir}, not {PROJECT_DIR}")
    minutes = now_pacific.hour * 60 + now_pacific.minute
    if 4 * 60 + 45 <= minutes < 8 * 60 + 15:
        problems.append("it is between 04:45 and 08:15 Pacific (nightly run): try later")
    return problems


def pacific_now():
    try:
        from zoneinfo import ZoneInfo
        return datetime.datetime.now(ZoneInfo("America/Los_Angeles"))
    except Exception:  # noqa: BLE001
        return datetime.datetime.now()


def container_project_dir():
    r = subprocess.run(["docker", "inspect", "-f", '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}',
                        APP_CONTAINER], capture_output=True, text=True)
    return (r.stdout.strip() or None) if r.returncode == 0 else None


# ---------- database ----------

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


class Db:
    def __init__(self, user=None, password=None, container=DB_CONTAINER):
        if user is None:
            user, password = read_db_properties()
        self.user, self._password, self.container = user, password, container

    def _mysql(self, sql, raw=False):
        env = dict(os.environ, MYSQL_PWD=self._password)
        cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", self.container,
               "mysql", "-N", "-B", "--default-character-set=utf8mb4", "-u", self.user, DATABASE]
        if raw:
            cmd.insert(cmd.index("-B") + 1, "-r")
        r = subprocess.run(cmd, input=sql, env=env, capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit("database command failed: " + r.stderr.strip())
        return r.stdout

    def run(self, sql):
        """Run SQL (read from stdin by the mysql client); rows as lists of strings."""
        return [line.split("\t") for line in self._mysql(sql).splitlines() if line != ""]

    def json_rows(self, sql):
        """Rows of a query that selects one JSON_OBJECT(...) column, as dicts."""
        return [json.loads(line) for line in self._mysql(sql, raw=True).splitlines() if line.strip()]

    def value(self, sql):
        rows = self.run(sql)
        return rows[0][0] if rows and rows[0] and rows[0][0] != "NULL" else None


def q(value):
    """SQL string literal for the fixed values used here (names, hashes: no quotes in them)."""
    s = str(value)
    if re.search(r"['\\\x00\n\r]", s):
        raise ValueError("unexpected character in " + repr(s))
    return "'" + s + "'"


def group_id(db):
    v = db.value(f"select id from ant_group where name = {q(GROUP_NAME)}")
    return int(v) if v else None


def login_id(db, name):
    v = db.value(f"select id from login where name = {q(name)}")
    return int(v) if v else None


def create_group(db):
    gid = group_id(db)
    if gid:
        print(f"Group exists: {GROUP_NAME} (id {gid}), kept.")
        return gid
    # As GroupDb.saveGroup(): id = max + 1.
    db.run(f"insert into ant_group (id, name, admin_login_id, abbrev) "
           f"select coalesce(max(id), 0) + 1, {q(GROUP_NAME)}, 0, {q(GROUP_ABBREV)} from ant_group")
    gid = group_id(db)
    print(f"Group created: {GROUP_NAME} (id {gid}).")
    return gid


def create_or_reset_login(db, gid, name, is_admin, specimens, images, reset):
    """Returns the new plain password, or None if the login existed and was kept."""
    lid = login_id(db, name)
    if lid and not reset:
        db.run(f"update login set group_id = {gid}, is_admin = {int(is_admin)}, is_upload_specimens = {int(specimens)}, "
               f"is_upload_images = {int(images)} where id = {lid}")
        print(f"Login exists: {name} (id {lid}), password unchanged (rights checked).")
        return None
    password = new_password()
    stored = hash_password(password)
    assert password_matches(stored, password)
    if lid:
        db.run(f"update login set password = {q(stored)}, group_id = {gid}, is_admin = {int(is_admin)}, "
               f"is_upload_specimens = {int(specimens)}, is_upload_images = {int(images)} where id = {lid}")
        print(f"Login {name} (id {lid}): new password set.")
        return password
    # As LoginDb.saveLogin(): id = max + 1, the same columns.
    email = name.replace("_", "-") + "@example.invalid"
    db.run("insert into login (id, name, first_name, last_name, email, password, group_id, is_admin, "
           "is_upload_specimens, is_upload_images, upload_as) "
           f"select coalesce(max(id), 0) + 1, {q(name)}, 'ZZ Test', {q(name)}, {q(email)}, {q(stored)}, {gid}, "
           f"{int(is_admin)}, {int(specimens)}, {int(images)}, NULL from login")
    print(f"Login created: {name} (id {login_id(db, name)}).")
    return password


# ---------- the app ----------

def restart_app():
    print(f"Restarting {APP_CONTAINER} so AntWeb loads the new group and logins (2 to 4 minutes) ...")
    subprocess.run(["docker", "restart", APP_CONTAINER], check=True, capture_output=True)


def local_login_ok(name, password):
    """Log in through Tomcat's local port; True if editSession.do then says loggedIn."""
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),
                                         urllib.request.ProxyHandler({}))
    data = urllib.parse.urlencode({"userName": name, "password": password, "isSubmit": "true"}).encode()
    opener.open(urllib.request.Request(APP_LOCAL_URL + "/login.do", data=data), timeout=60).read()
    body = opener.open(APP_LOCAL_URL + "/editSession.do", timeout=60).read().decode("utf-8", "replace")
    return bool(json.loads(body).get("loggedIn"))


def wait_and_check(passwords, minutes=8):
    deadline = time.time() + minutes * 60
    pending = dict(passwords)
    while pending and time.time() < deadline:
        for name, pw in list(pending.items()):
            try:
                if local_login_ok(name, pw):
                    print(f"Login check: {name} OK")
                    del pending[name]
            except Exception:  # noqa: BLE001  (still starting)
                pass
        if pending:
            time.sleep(20)
    for name in pending:
        print(f"Login check: {name} FAILED (AntWeb did not accept the new password within {minutes} minutes)")
    return not pending


def bucket_note():
    """Which storage /mnt/antweb is (staging must not share production's images bucket)."""
    r = subprocess.run(["findmnt", "-n", "-o", "SOURCE,FSTYPE", "/mnt/antweb"], capture_output=True, text=True)
    src = r.stdout.strip() or "(not a mount point)"
    fstab = ""
    try:
        for line in open("/etc/fstab", encoding="utf-8"):
            f = line.split()
            if len(f) >= 2 and f[1] == "/mnt/antweb" and not line.lstrip().startswith("#"):
                fstab = f[0]
    except OSError:
        pass
    return f"/mnt/antweb on this server: {src}" + (f" (fstab: {fstab})" if fstab else "")


# ---------- remove ----------

REMOVE_TABLES = [
    # (table, where clause) in delete order; {gid} = test group, {logins} = test login ids
    ("group_image", "image_id in (select id from image where image_of_id like '" + CODE_PREFIX + "%')"),
    ("image", "image_of_id like '" + CODE_PREFIX + "%'"),
    ("image_uploaded", "image_upload_id in (select id from image_upload where group_id = {gid} or curator_id in ({logins}))"),
    ("image_upload_refused", "image_upload_id in (select id from image_upload where group_id = {gid} or curator_id in ({logins}))"),
    ("image_upload", "group_id = {gid} or curator_id in ({logins})"),
    ("specimen", "access_group = {gid}"),
    ("taxon", "source like '%specimen{gid}.txt' and taxarank in ('species', 'subspecies') "
              "and (antcat is null or antcat = 0) and taxon_name not in (select taxon_name from specimen)"),
    ("upload_line", "group_id = {gid}"),
    ("upload", "group_id = {gid}"),
    ("login_project", "login_id in ({logins})"),
    ("login_country", "login_id in ({logins})"),
    ("login", "id in ({logins})"),
    ("ant_group", "id = {gid}"),
]


def existing_tables(db):
    return {r[0] for r in db.run("show tables")}


def remove(db, assume_yes):
    gid = group_id(db)
    logins = [lid for lid in (login_id(db, a[0]) for a in ACCOUNTS) if lid]
    if not gid and not logins:
        print("Nothing to remove: no test group and no test logins.")
        return False
    gid = gid or -999999
    login_list = ",".join(str(i) for i in logins) or "-999999"
    tables = existing_tables(db)
    counts = []
    for table, where in REMOVE_TABLES:
        if table not in tables:
            continue
        w = where.format(gid=gid, logins=login_list)
        try:
            n = int(db.value(f"select count(*) from {table} where {w}") or 0)
        except SystemExit:
            print(f"  (skipping {table}: its columns differ)")
            continue
        counts.append((table, w, n))
    print("Will delete (test data only):")
    for table, _w, n in counts:
        print(f"  {table}: {n} rows")
    if not assume_yes and input('Type "yes" to delete: ').strip() != "yes":
        print("Nothing deleted.")
        return False
    stamp = pacific_now().strftime("%Y%m%d")
    for table, w, n in counts:
        if n == 0:
            continue
        bak = f"zztest_bak_{stamp}_{table}"
        db.run(f"create table if not exists {bak} as select * from {table} where 1 = 0;\n"
               f"insert into {bak} select * from {table} where {w};")
        db.run(f"delete from {table} where {w}")
        print(f"  {table}: {n} rows deleted (copy in {bak})")
    # Image files of test specimens only (folders named zztest...).
    r = subprocess.run(["docker", "exec", APP_CONTAINER, "find", "/usr/local/antweb/images/", "-mindepth", "1",
                        "-maxdepth", "1", "-type", "d", "-name", CODE_PREFIX + "*"], capture_output=True, text=True)
    for path in r.stdout.split():
        if re.fullmatch(r"/usr/local/antweb/images/" + CODE_PREFIX + r"[a-z0-9._-]+", path):
            subprocess.run(["docker", "exec", APP_CONTAINER, "rm", "-rf", "--", path], check=False)
            print(f"  removed image folder {path}")
    return True


# ---------- keep across a refresh from production (tools/staging/refresh-from-prod.sh) ----------
#
# --save-to FILE writes the test group and the logins to keep (the three test logins plus any
# names listed in --keep-logins FILE) with their password HASHES, rights and project/country rows,
# as JSON (chmod 600). --restore-from FILE puts them back after production's database has been
# loaded: the test group is created again if missing (new id), each login gets back its saved
# hash, so the passwords in the GitHub secrets keep working. Logins that also exist in
# production keep production's id; logins that do not are inserted with a new id.

SAVE_VERSION = 1
LOGIN_COLUMNS = ["id", "name", "first_name", "last_name", "email", "password", "group_id", "is_admin",
                 "is_upload_specimens", "is_upload_images", "upload_as"]
LOGIN_NAME_RE = re.compile(r"[A-Za-z0-9_.@+-]{1,64}")


def read_keep_logins(path):
    """Login names from a file, one per line; blank lines and # comments ignored."""
    if not path or not os.path.isfile(path):
        return []
    names = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            name = line.split("#", 1)[0].strip()
            if not name:
                continue
            if not LOGIN_NAME_RE.fullmatch(name):
                sys.exit(f"{path}: not a login name: {name!r}")
            if name not in names:
                names.append(name)
    return names


def keep_names(extra):
    names = [a[0] for a in ACCOUNTS]
    return names + [n for n in extra if n not in names]


def sql_str(value):
    """SQL literal for any text (quotes and backslashes escaped), or NULL."""
    if value is None:
        return "NULL"
    s = str(value)
    if "\x00" in s:
        raise ValueError("NUL character in " + repr(s))
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_int(value):
    return "NULL" if value is None else str(int(value))


def table_columns(db, table):
    return {r[0] for r in db.run(
        f"select column_name from information_schema.columns where table_schema = database() and table_name = {q(table)}")}


def save_accounts(db, names):
    """The test group and the named logins, as a dict (see SAVE_VERSION)."""
    gid = group_id(db)
    name_list = ",".join(sql_str(n) for n in names)
    obj = ", ".join(f"{sql_str(c)}, {c}" for c in LOGIN_COLUMNS)
    logins = db.json_rows(f"select json_object({obj}) from login where name in ({name_list}) order by id")
    tables = existing_tables(db)
    for login in logins:
        login["in_test_group"] = gid is not None and login.get("group_id") == gid
        login["projects"] = [r[0] for r in db.run(
            f"select project_name from login_project where login_id = {int(login['id'])} order by project_name")] \
            if "login_project" in tables else []
        login["countries"] = [r[0] for r in db.run(
            f"select country from login_country where login_id = {int(login['id'])} order by country")] \
            if "login_country" in tables else []
    return {"version": SAVE_VERSION, "saved": pacific_now().isoformat(timespec="seconds"),
            "group": {"name": GROUP_NAME, "abbrev": GROUP_ABBREV, "id": gid} if gid else None,
            "logins": logins}


def write_saved(path, data):
    fd = os.open(path + ".tmp", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    os.chmod(path + ".tmp", 0o600)
    os.replace(path + ".tmp", path)


def restore_statements(saved, gid, existing_ids, login_cols, has_project, has_country):
    """SQL (one string) that puts the saved logins back. Pure, unit-tested.

    gid: the test group's id now (after create_group); existing_ids: {name: id} of saved names
    that exist in the loaded database; login_cols: the login table's columns."""
    out = []
    for login in saved["logins"]:
        name = login["name"]
        group = gid if login.get("in_test_group") else login.get("group_id")
        sets = {"password": sql_str(login["password"]), "email": sql_str(login.get("email") or ""),
                "group_id": sql_int(group), "is_admin": sql_int(login.get("is_admin") or 0),
                "is_upload_specimens": sql_int(login.get("is_upload_specimens") or 0),
                "is_upload_images": sql_int(login.get("is_upload_images") or 0),
                "upload_as": sql_str(login.get("upload_as"))}
        for col, val in (("inactive", "0"), ("inactive_since", "NULL"), ("must_reset", "0")):
            if col in login_cols:
                sets[col] = val
        if name in existing_ids:
            lid = int(existing_ids[name])
            out.append("update login set " + ", ".join(f"{k} = {v}" for k, v in sets.items())
                       + (", created = created" if "created" in login_cols else "") + f" where id = {lid};")
            id_expr = str(lid)
        else:
            cols = ["id", "name", "first_name", "last_name"] + list(sets)
            vals = ["coalesce(max(id), 0) + 1", sql_str(name), sql_str(login.get("first_name")),
                    sql_str(login.get("last_name") or "")] + list(sets.values())
            out.append(f"insert into login ({', '.join(cols)}) select {', '.join(vals)} from login;")
            id_expr = f"(select id from (select id from login where name = {sql_str(name)}) as t)"
        if has_project:
            out.append(f"delete from login_project where login_id = {id_expr};")
            for p in login.get("projects") or []:
                out.append(f"insert into login_project (login_id, project_name) values ({id_expr}, {sql_str(p)});")
        if has_country:
            out.append(f"delete from login_country where login_id = {id_expr};")
            for c in login.get("countries") or []:
                out.append(f"insert into login_country (login_id, country) values ({id_expr}, {sql_str(c)});")
    return "\n".join(out) + "\n"


def restore_accounts(db, saved):
    """Puts the saved group and logins back; returns the names whose hash is not as saved."""
    if saved.get("version") != SAVE_VERSION:
        sys.exit(f"saved file has version {saved.get('version')}, expected {SAVE_VERSION}")
    gid = None
    if saved.get("group") or any(login.get("in_test_group") for login in saved["logins"]):
        gid = create_group(db)
    names = [login["name"] for login in saved["logins"]]
    existing = {}
    if names:
        existing = {r[0]: int(r[1]) for r in db.run(
            "select name, id from login where name in (" + ",".join(sql_str(n) for n in names) + ")")}
    tables = existing_tables(db)
    sql = restore_statements(saved, gid, existing, table_columns(db, "login"),
                             "login_project" in tables, "login_country" in tables)
    db.run(sql)
    wrong = []
    for login in saved["logins"]:
        now = db.value(f"select password from login where name = {sql_str(login['name'])}")
        state = "restored" if now == login["password"] else "NOT RESTORED"
        if now != login["password"]:
            wrong.append(login["name"])
        print(f"Login {login['name']}: {state} (id {login_id(db, login['name'])}, "
              f"{'existed in production' if login['name'] in existing else 'new'})")
    return wrong


# ---------- "Upload as" of the administrator after a refresh (tools/staging/refresh-from-prod.sh) ----------
#
# Production's database has no zztest_uploader, so after a refresh the test uploader gets a new
# login number and is missing from the administrator's "Upload as" menu on staging (Brian Fisher,
# 2026-10-21). --add-upload-as appends its number to login.upload_as of the administrator
# login(s) named in the config file, only if it is not there already; nothing else is changed.

UPLOAD_AS_CONFIG = "/root/staging-refresh/upload-as.txt"
DEFAULT_ADMIN_LOGINS = ["antweb"]
UPLOAD_AS_UPLOADER = "zztest_uploader"
assert UPLOAD_AS_UPLOADER in [a[0] for a in ACCOUNTS]


def read_upload_as_config(path):
    """Admin login names from lines "adminLogin=NAME" (several allowed; # comments, blank lines
    ignored). No file: DEFAULT_ADMIN_LOGINS. A file without adminLogin lines: none (switched off)."""
    if not path or not os.path.isfile(path):
        return list(DEFAULT_ADMIN_LOGINS)
    names = []
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            key, sep, value = line.partition("=")
            key, value = key.strip(), value.strip()
            if not sep or key != "adminLogin":
                sys.exit(f"{path} line {n}: expected adminLogin=NAME, found {line!r}")
            if not LOGIN_NAME_RE.fullmatch(value):
                sys.exit(f"{path} line {n}: not a login name: {value!r}")
            if value not in names:
                names.append(value)
    return names


def upload_as_with(current, login_id_to_add):
    """The upload_as value with login_id_to_add appended, or None if it is already there. Pure."""
    parts = [p.strip() for p in (current or "").split(",") if p.strip()]
    if str(int(login_id_to_add)) in parts:
        return None
    return ",".join(parts + [str(int(login_id_to_add))])


def add_upload_as(db, admin_names, uploader=UPLOAD_AS_UPLOADER):
    """Adds the uploader's login number to each named administrator's upload_as. Returns
    (changed, usable): the logins changed and the named administrator logins found (changed or
    already listing it). Missing logins and non-administrators are reported and left alone."""
    uid = login_id(db, uploader)
    if not uid:
        print(f"WARNING: Upload as: no login {uploader} on this server; nothing added.")
        return 0, 0
    if not admin_names:
        print("Upload as: no adminLogin in the config file (switched off); nothing added.")
        return 0, 0
    keep_created = ", created = created" if "created" in table_columns(db, "login") else ""
    changed = usable = 0
    for name in admin_names:
        found = db.run(f"select id, is_admin, coalesce(upload_as, '') from login where name = {sql_str(name)}")
        if not found:
            print(f"WARNING: Upload as: no login {name} on this server; skipped.")
            continue
        lid, is_admin, current = int(found[0][0]), found[0][1], found[0][2] if len(found[0]) > 2 else ""
        if is_admin != "1":
            print(f"WARNING: Upload as: {name} (login {lid}) is not an administrator; skipped.")
            continue
        usable += 1
        new = upload_as_with(current, uid)
        if new is None:
            print(f"Upload as: {name} (login {lid}) already lists {uploader} ({uid}).")
            continue
        db.run(f"update login set upload_as = {sql_str(new)}{keep_created} where id = {lid}")
        print(f"Upload as: added {uploader} ({uid}) to {name} (login {lid}): {new}")
        changed += 1
    return changed, usable


# ---------- main ----------

def main(argv=None):
    ap = argparse.ArgumentParser(description="Staging test accounts for the end-to-end tests.")
    ap.add_argument("--remove", action="store_true", help="delete the test group's data and the test logins")
    ap.add_argument("--reset-passwords", action="store_true", help="give the three test logins new passwords")
    ap.add_argument("--yes", action="store_true", help="with --remove: do not ask")
    ap.add_argument("--no-restart", action="store_true", help="do not restart AntWeb (logins work after the next restart)")
    ap.add_argument("--save-to", metavar="FILE",
                    help="save the test group and logins to keep (hashes, rights) to FILE, change nothing")
    ap.add_argument("--restore-from", metavar="FILE",
                    help="put the logins saved with --save-to back (same passwords); no restart")
    ap.add_argument("--add-upload-as", metavar="FILE", nargs="?", const=UPLOAD_AS_CONFIG,
                    help="add zztest_uploader to the Upload as list of the admin login(s) in FILE "
                         f"(default {UPLOAD_AS_CONFIG}; lines adminLogin=NAME; no file: antweb), if missing")
    ap.add_argument("--keep-logins", metavar="FILE",
                    help="with --save-to: more login names to keep, one per line (e.g. the ANTWEB_TEST_USER login)")
    args = ap.parse_args(argv)

    repo_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.realpath(__file__))))
    problems = guard_problems(socket.gethostname(), repo_dir, pacific_now(),
                              os.path.isfile(DB_PROPERTIES), container_project_dir())
    if problems:
        print("Refused:\n  - " + "\n  - ".join(problems))
        return 2

    db = Db()
    if args.save_to:
        names = keep_names(read_keep_logins(args.keep_logins))
        data = save_accounts(db, names)
        write_saved(args.save_to, data)
        found = [login["name"] for login in data["logins"]]
        print(f"Saved {len(found)} login(s) to {args.save_to}: {', '.join(found) or 'none'}"
              + (f"; test group id {data['group']['id']}" if data["group"] else "; no test group"))
        missing = [n for n in names if n not in found]
        if missing:
            print("Not on this server (not saved): " + ", ".join(missing))
        return 0
    if args.restore_from:
        with open(args.restore_from, encoding="utf-8") as f:
            saved = json.load(f)
        if not saved["logins"] and not saved.get("group"):
            print("Nothing saved: no test group and no logins to put back.")
            return 0
        wrong = restore_accounts(db, saved)
        if wrong:
            print("FAILED for: " + ", ".join(wrong))
            return 1
        print("All saved logins are back with their saved passwords. AntWeb loads them when it starts.")
        return 0
    if args.add_upload_as:
        names = read_upload_as_config(args.add_upload_as)
        if names and not login_id(db, UPLOAD_AS_UPLOADER):
            print(f"WARNING: no login {UPLOAD_AS_UPLOADER} on this server (were the test logins put back?); "
                  "nothing added")
            return 1
        _changed, usable = add_upload_as(db, names)
        if names and not usable:
            print(f"WARNING: none of {', '.join(names)} is an administrator login here; set adminLogin=NAME in "
                  f"{args.add_upload_as}")
            return 1
        return 0
    if args.remove:
        if remove(db, args.yes) and not args.no_restart:
            restart_app()
            print("Done. AntWeb is restarting (2 to 4 minutes). Delete the six ANTWEB_TEST_UPLOADER_*, "
                  "ANTWEB_TEST_NOUPLOAD_* and ANTWEB_TEST_ADMIN_* secrets in GitHub; the tests then skip.")
        return 0

    gid = create_group(db)
    passwords = {}
    for name, _secret, is_admin, specimens, images in ACCOUNTS:
        pw = create_or_reset_login(db, gid, name, is_admin, specimens, images, args.reset_passwords)
        if pw:
            passwords[name] = pw
    print(bucket_note())

    if not passwords:
        print("No new passwords (all logins existed). Use --reset-passwords to make new ones.")
        print("Rights were set again; if they had changed, they apply after the next AntWeb restart.")
        return 0

    if not args.no_restart:
        restart_app()
        if not wait_and_check(passwords):
            print("Some logins could not be checked; the passwords below are stored all the same.")

    print()
    print("=" * 78)
    print("Paste these into GitHub: calacademy-research/antweb > Settings > Secrets and variables >")
    print("Actions > New repository secret (one secret per line, name on the left). They are shown")
    print("only now; nothing is saved on this server. Use them for staging only.")
    print("=" * 78)
    for name, secret, *_ in ACCOUNTS:
        if name in passwords:
            print(f"{secret}_USER      {name}")
            print(f"{secret}_PASSWORD  {passwords[name]}")
    print("=" * 78)
    print("Then clear the terminal (Cmd+K in Terminal on a Mac).")
    return 0


if __name__ == "__main__":
    sys.exit(main())

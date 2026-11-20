#!/usr/bin/env python3
"""The daily place step: every morning, what the coordinates of AntWeb's specimens say their country and state should
be, kept in the table place_update (Places from coordinates, step 1; decided by Brian Fisher 2026-11-16 18:43,
Claude Doc "Places from coordinates: proposal": Decision 1 = A, Decision 2 = report first).

Why: a curator's re-upload writes the file's place again (Madagascar 2026-11-16: 902 specimens moved from the old
provinces to the regions of their coordinates; the next upload of the same files would put the provinces back). From
the apply mode on, this step puts the coordinates' value back the next morning. Until then (one week) it only reports.

  python3 place_daily.py                      report mode: run place_corrections.py for every group, keep its
                                              proposals in place_update (run_date today, mode report)
  python3 place_daily.py --mode apply         as report mode, then writes the proposals that are safe to write (step 2,
                                              2026-11-19; see "Apply mode" below); switched on by the cron line
  python3 place_daily.py --rollback DATE      puts back the states the apply run of DATE (YYYY-MM-DD) wrote
  python3 place_daily.py --dry-run            run and summarise, write nothing to the database

What it does, in report mode:
  1. Takes a lock (/run/antweb-place-daily.lock): two runs never overlap; a second one stops at once.
  2. Runs place_corrections.py (next to it; read-only, every statement a single select) for every upload group,
     into /root/antweb-place-fix/daily/<date> (the last 14 folders are kept).
  3. Loads its corrections.csv into place_update: one row per specimen and field. Today's rows of an earlier run the
     same day are replaced (a rerun is safe). Runs older than 60 days are deleted. These are the only writes, and only
     to place_update.
  4. Prints one summary line (and logs it with logger -t place-daily): proposals by field, by upload group (top 5),
     how long it took. A failed run logs "place-daily FAILED: <why>" there too and exits 1 (run()).
In report mode it never writes to specimen. Rollback: none needed (report rows only); to stop it, remove
/etc/cron.d/antweb-place-daily.

Apply mode (step 2, built 2026-11-19, switched on by Brian after the week of reports): after the report rows are
loaded, the proposals that are safe are written to specimen.adm1 (Decision 1 = A: the state the coordinates give goes
into adm1, which every page reads; the state the curator uploaded is kept in adm1_verbatim). A proposal is written
only when all of these hold (eligible(), unit-tested), otherwise it stays a report row and the summary counts why:
  - it is a state (adm1), never a country: a point that disagrees with its country is more likely wrong than the
    country, so country proposals are for a person (R5 of the place rules);
  - no other state lies within the stated error of the point (the flag column);
  - the country is not in place_daily_skip.txt (countries whose boundary shapes are at another level than AntWeb's
    states, 2026-11-19 state_sets.py: the United Kingdom, Slovenia, Uganda, ...);
  - the proposed name is a valid state of that country now;
  - the specimen still has the country and the state the report read (nothing changed since).
At most MAX_APPLY changes a run (more: nothing is applied, the run logs it, someone looks). Once a day: a second apply
run the same day applies nothing. Before writing, the specimens' adm1 and adm1_verbatim go to the backup table
place_apply_bak_<date>; the written rows are recorded in place_update (mode apply, applied 1).
Rollback of one day: python3 place_daily.py --rollback <date> (from the backup table). To stop applying: the cron line
back to report mode (the --mode apply removed).

The database login is read from <project>/etc/db.properties and goes to docker through the environment (as in
coordinate_country.py). Tests: tests/unit/test_place_daily.py.
"""
import argparse
import collections
import contextlib
import csv
import datetime
import fcntl
import os
import re
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import coordinate_country as cc  # noqa: E402

LOCK = "/run/antweb-place-daily.lock"
OUT_ROOT = "/root/antweb-place-fix/daily"
KEEP_FOLDERS = 14
KEEP_DAYS = 60
BATCH = 500
MAX_APPLY = 5000
SKIP_FILE = os.path.join(HERE, "place_daily_skip.txt")
COLUMNS = ("run_date", "mode", "code", "upload_group", "field", "stored_value", "proposed", "basis", "flag", "note")
WIDTH = {"code": 128, "upload_group": 100, "field": 16, "stored_value": 128, "proposed": 128, "basis": 255, "flag": 255, "note": 255}


def quote(value):
    """A value as a MySQL string literal (utf8mb4; backslashes and quotes escaped), or NULL for None."""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''").replace("\0", "") + "'"


def rows_of(corrections_csv, run_date, mode):
    """The rows for place_update from place_corrections.py's corrections.csv, cut to the columns' widths."""
    out = []
    with open(corrections_csv, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            if not r.get("specimen_code") or not r.get("proposed_value"):
                continue
            row = {"run_date": run_date, "mode": mode, "code": r["specimen_code"], "upload_group": r.get("upload_group") or None,
                   "field": r.get("field") or "", "stored_value": r.get("current_value") or None, "proposed": r["proposed_value"],
                   "basis": r.get("basis") or None, "flag": r.get("other_state_flag") or None, "note": r.get("note") or None,
                   "country": r.get("country") or ""}   # not a column of place_update: for the apply mode
            if len(row["code"]) > WIDTH["code"]:   # a cut code would name another specimen: skipped, never cut
                continue
            for k, w in WIDTH.items():
                if row[k] is not None and len(row[k]) > w:
                    row[k] = row[k][:w]
            out.append(row)
    return out


def statements(rows, run_date, mode):
    """The SQL of one load: today's rows of the same mode replaced, then the rows in batches, then old runs removed;
    one transaction."""
    out = ["START TRANSACTION",
           "DELETE FROM place_update WHERE run_date = " + quote(run_date) + " AND mode = " + quote(mode)]
    for i in range(0, len(rows), BATCH):
        values = ",".join("(" + ",".join(quote(r[c]) for c in COLUMNS) + ")" for r in rows[i:i + BATCH])
        out.append("INSERT INTO place_update (" + ",".join(COLUMNS) + ") VALUES " + values)
    out.append("DELETE FROM place_update WHERE run_date < " + quote(run_date) + " - INTERVAL " + str(KEEP_DAYS) + " DAY")
    out.append("COMMIT")
    return out


def skipped_countries(path=None):
    """The countries the apply mode leaves out (SKIP_FILE): one per line; # starts a note. A file that cannot be read
    stops the apply (review of #384: without it the countries at another level would be written)."""
    try:
        with open(path or SKIP_FILE, encoding="utf-8") as f:
            return {l.split("#", 1)[0].strip() for l in f if l.split("#", 1)[0].strip()}
    except OSError as e:
        raise SystemExit("apply refused: the skip file " + (path or SKIP_FILE) + " cannot be read (" + e.__class__.__name__ + ")")


LEFT_OUT = {
    "country": "a country proposal (for a person)",
    "flag": "another state within the stated error",
    "skip": "country left out (its shapes are at another level)",
    "invalid": "the proposed state is not a valid state of the country",
    "gone": "the specimen is gone",
    "moved": "the specimen's country is not the proposal's",
    "changed": "the specimen's state changed since the report",
}


def eligible(rows, valid_states, current, skip):
    """The report rows the apply mode writes, and why the others are left out (Counter of LEFT_OUT keys).
    valid_states: {(country, state)} of the valid states now; current: {code: (country, adm1)} as stored now
    ("" for none); skip: countries left out."""
    out, why = [], collections.Counter()
    for r in rows:
        stored_now = current.get(r["code"])
        if r["field"] != "adm1":
            why["country"] += 1
        elif r.get("flag"):
            why["flag"] += 1
        elif r["country"] in skip:
            why["skip"] += 1
        elif (r["country"], r["proposed"]) not in valid_states:
            why["invalid"] += 1
        elif stored_now is None:
            why["gone"] += 1
        elif stored_now[0] != r["country"]:
            why["moved"] += 1
        elif stored_now[1] != (r["stored_value"] or ""):
            why["changed"] += 1
        else:
            out.append(r)
    return out, why


def backup_table(run_date):
    return "place_apply_bak_" + run_date.replace("-", "")


def apply_statements(rows, run_date):
    """The SQL of one apply run: the backup table made first (CREATE TABLE commits, so it stands before the
    transaction: review of #384), then one transaction: the specimens' adm1 and adm1_verbatim to the backup table,
    each state written (only if the specimen still has the state the report read), and the rows recorded in
    place_update. A failure inside the transaction writes nothing; the backup table then stays, empty or partly
    filled, and the next run the same day stops at its CREATE TABLE."""
    bak = backup_table(run_date)
    codes = [r["code"] for r in rows]
    out = ["SET SESSION innodb_lock_wait_timeout = 600",
           "CREATE TABLE " + bak + " (code varchar(128) NOT NULL PRIMARY KEY, adm1 varchar(128) NULL, adm1_verbatim varchar(128) NULL)"
           " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
           "START TRANSACTION"]
    for i in range(0, len(codes), BATCH):
        out.append("INSERT INTO " + bak + " (code, adm1, adm1_verbatim) SELECT code, adm1, adm1_verbatim FROM specimen WHERE code IN ("
                   + ",".join(quote(c) for c in codes[i:i + BATCH]) + ")")
    for r in rows:
        out.append("UPDATE specimen SET adm1_verbatim = IFNULL(adm1_verbatim, adm1), adm1 = " + quote(r["proposed"])
                   + " WHERE code = " + quote(r["code"]) + " AND BINARY IFNULL(adm1, '') = BINARY " + quote(r["stored_value"] or ""))
    applied = [dict(r, mode="apply") for r in rows]
    for i in range(0, len(applied), BATCH):
        values = ",".join("(" + ",".join(quote(r[c]) for c in COLUMNS) + ",1)" for r in applied[i:i + BATCH])
        out.append("INSERT INTO place_update (" + ",".join(COLUMNS) + ",applied) VALUES " + values)
    out.append("COMMIT")
    return out


def rollback_statements(run_date):
    """Puts back adm1 and adm1_verbatim of the specimens the apply run of run_date wrote (from its backup table), only
    where the specimen still has the state that run wrote: a later upload or edit is kept (review of #384)."""
    bak = backup_table(run_date)
    return ["SET SESSION innodb_lock_wait_timeout = 600", "START TRANSACTION",
            "UPDATE specimen s JOIN " + bak + " b ON b.code = s.code JOIN place_update p ON p.code = s.code AND p.run_date = " + quote(run_date)
            + " AND p.mode = 'apply' AND p.applied = 1 AND p.field = 'adm1' SET s.adm1 = b.adm1, s.adm1_verbatim = b.adm1_verbatim"
            " WHERE BINARY s.adm1 = BINARY p.proposed",
            "UPDATE place_update SET applied = 0 WHERE run_date = " + quote(run_date) + " AND mode = 'apply'",
            "COMMIT"]


def read_state(project, container, codes):
    """(valid states, current values, applied today) from the database: {(country, state)}, {code: (country, adm1)}."""
    valid = {(cc.unhex(r[1]), cc.unhex(r[0])) for r in cc.run_sql(
        project, "select hex(name), hex(parent) from geolocale where georank = 'adm1' and is_valid = 1 and parent is not null", container)}
    current = {}
    codes = sorted(set(codes))
    for i in range(0, len(codes), BATCH):
        for r in cc.run_sql(project, "select hex(code), hex(ifnull(country, '')), hex(ifnull(adm1, '')) from specimen where code in ("
                            + ",".join(quote(c) for c in codes[i:i + BATCH]) + ")", container):
            current[cc.unhex(r[0])] = (cc.unhex(r[1]), cc.unhex(r[2]))
    return valid, current


def applied_today(project, container, run_date):
    rows = cc.run_sql(project, "select count(*) from place_update where run_date = " + quote(run_date) + " and mode = 'apply'", container)
    return int(rows[0][0]) if rows else 0


def write_sql(project, container, sql_statements):
    """Runs the statements in one mysql session (the login from db.properties, through the environment)."""
    user, password = cc.db_login(project)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant", "--default-character-set=utf8mb4"]
    text = ";\n".join(sql_statements) + ";\n"
    p = subprocess.run(cmd, input=text.encode("utf-8"), stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=dict(os.environ, MYSQL_PWD=password))
    if p.returncode != 0:
        raise RuntimeError("mysql failed: " + p.stderr.decode("utf-8", "replace").strip()[:500])


def summary(rows, seconds, mode):
    by_field = collections.Counter(r["field"] for r in rows)
    by_group = collections.Counter(r["upload_group"] or "(no group)" for r in rows)
    top = ", ".join(g + " " + cc.n(c) for g, c in by_group.most_common(5))
    return ("place-daily " + mode + ": " + cc.n(len(rows)) + " proposals (adm1 " + cc.n(by_field.get("adm1", 0)) + ", country " + cc.n(by_field.get("country", 0)) +
            ") in " + cc.n(len(by_group)) + " groups" + (" (" + top + ")" if top else "") + "; " + str(int(seconds)) + " s")


def prune(root, keep):
    folders = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
    for d in folders[:-keep] if keep else folders:
        shutil.rmtree(os.path.join(root, d), ignore_errors=True)


@contextlib.contextmanager
def locked(path):
    with open(path, "w") as f:
        try:
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit("place-daily: another run holds " + path + "; nothing done")
        yield


def log(line):
    print(line)
    if shutil.which("logger"):
        subprocess.run(["logger", "-t", "place-daily", line], check=False)


def main(argv=None, corrections=None, reader=None):
    """reader (the tests): a function (project, container, codes) -> (valid states, current values, applied today)."""
    a = argparse.ArgumentParser(description="The daily place step: proposals from coordinates into place_update; in apply mode the safe ones written.")
    a.add_argument("--mode", choices=["report", "apply"], default="report")
    a.add_argument("--rollback", metavar="DATE", default=None, help="put back what the apply run of DATE (YYYY-MM-DD) wrote")
    a.add_argument("--dry-run", action="store_true", help="run and summarise; write nothing to the database")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out-root", default=OUT_ROOT)
    a.add_argument("--lock", default=LOCK)
    args = a.parse_args(argv)
    project = args.project or cc.default_project()
    if args.rollback:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.rollback):   # YYYY-MM-DD only (fromisoformat takes 20261123 and week forms too)
            raise ValueError("--rollback takes a date YYYY-MM-DD: " + args.rollback)
        datetime.date.fromisoformat(args.rollback)   # and a real date
        if args.dry_run:
            log("place-daily rollback " + args.rollback + " (dry run): " + "; ".join(rollback_statements(args.rollback)))
            return 0
        write_sql(project, args.container, rollback_statements(args.rollback))
        log("place-daily rollback " + args.rollback + ": the states of that day's apply run put back from " + backup_table(args.rollback))
        return 0
    run_date = datetime.date.today().isoformat()
    started = time.time()
    with locked(args.lock):
        os.makedirs(args.out_root, exist_ok=True)
        folder = os.path.join(args.out_root, run_date)
        if corrections is None:   # the tests hand in a corrections.csv instead of running place_corrections.py
            import place_corrections
            shutil.rmtree(folder, ignore_errors=True)
            if place_corrections.main(["--out", folder, "--project", project, "--container", args.container]) not in (0, None):
                sys.exit("place-daily: place_corrections.py failed; nothing written")
            corrections = os.path.join(folder, "corrections.csv")
        rows = rows_of(corrections, run_date, "report")
        if not args.dry_run:
            write_sql(project, args.container, statements(rows, run_date, "report"))
        prune(args.out_root, KEEP_FOLDERS)
        line = summary(rows, time.time() - started, args.mode)
        if args.mode == "apply":
            if reader is None:
                valid, current = read_state(project, args.container, [r["code"] for r in rows])
                done = applied_today(project, args.container, run_date)
            else:
                valid, current, done = reader(project, args.container, [r["code"] for r in rows])
            todo, why = eligible(rows, valid, current, skipped_countries())
            left = ", ".join(LEFT_OUT[k] + " " + cc.n(v) for k, v in why.most_common())
            if done:
                line += "; apply: none, today's apply run already wrote " + cc.n(done)
            elif len(todo) > MAX_APPLY:   # one journal line: run() logs it as FAILED
                raise SystemExit(line + "; apply REFUSED: " + cc.n(len(todo)) + " changes, over the limit of " + cc.n(MAX_APPLY) + "; nothing applied")
            elif todo and not args.dry_run:
                write_sql(project, args.container, apply_statements(todo, run_date))
                line += "; applied " + cc.n(len(todo)) + " states (backup " + backup_table(run_date) + ")"
            else:
                line += "; would apply " + cc.n(len(todo)) + " states"
            if left:
                line += "; left out: " + left
        log(line + (" (dry run, nothing written)" if args.dry_run else ""))
    return 0


def run(argv=None):
    """main, with every failure logged too (cron sends the output nowhere): the journal shows a line for every run."""
    try:
        return main(argv)
    except SystemExit as e:
        if e.code not in (0, None):
            log("place-daily FAILED: " + str(e.code))
            return 1
        return 0
    except Exception as e:   # noqa: BLE001 - any failure must reach the journal
        log("place-daily FAILED: " + type(e).__name__ + ": " + str(e)[:500])
        return 1


if __name__ == "__main__":
    sys.exit(run())

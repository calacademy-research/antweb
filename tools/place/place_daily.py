#!/usr/bin/env python3
"""The daily place step: every morning, what the coordinates of AntWeb's specimens say their country and state should
be, kept in the table place_update (Places from coordinates, step 1; decided by Brian Fisher 2026-11-16 18:43,
Claude Doc "Places from coordinates: proposal": Decision 1 = A, Decision 2 = report first).

Why: a curator's re-upload writes the file's place again (Madagascar 2026-11-16: 902 specimens moved from the old
provinces to the regions of their coordinates; the next upload of the same files would put the provinces back). From
the apply mode on, this step puts the coordinates' value back the next morning. Until then (one week) it only reports.

  python3 place_daily.py                      report mode: run place_corrections.py for every group, keep its
                                              proposals in place_update (run_date today, mode report)
  python3 place_daily.py --mode apply         refused until the apply mode is built (step 2)
  python3 place_daily.py --dry-run            run and summarise, write nothing to the database

What it does, in report mode:
  1. Takes a lock (/run/antweb-place-daily.lock): two runs never overlap; a second one stops at once.
  2. Runs place_corrections.py (next to it; read-only, every statement a single select) for every upload group,
     into /root/antweb-place-fix/daily/<date> (the last 14 folders are kept).
  3. Loads its corrections.csv into place_update: one row per specimen and field. Today's rows of an earlier run the
     same day are replaced (a rerun is safe). Runs older than 60 days are deleted. These are the only writes, and only
     to place_update.
  4. Prints one summary line (and logs it with logger -t place-daily): proposals by field, by upload group (top 5),
     how long it took.
It never writes to specimen. Rollback: none needed (report rows only); to stop it, remove /etc/cron.d/antweb-place-daily.

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
                   "basis": r.get("basis") or None, "flag": r.get("other_state_flag") or None, "note": r.get("note") or None}
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


def main(argv=None, corrections=None):
    a = argparse.ArgumentParser(description="The daily place step (report mode): proposals from coordinates into place_update.")
    a.add_argument("--mode", choices=["report", "apply"], default="report")
    a.add_argument("--dry-run", action="store_true", help="run and summarise; write nothing to the database")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out-root", default=OUT_ROOT)
    a.add_argument("--lock", default=LOCK)
    args = a.parse_args(argv)
    if args.mode == "apply":
        sys.exit("place-daily: the apply mode is not built yet (step 2, after a week of reports); nothing done")
    project = args.project or cc.default_project()
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
        rows = rows_of(corrections, run_date, args.mode)
        if not args.dry_run:
            write_sql(project, args.container, statements(rows, run_date, args.mode))
        prune(args.out_root, KEEP_FOLDERS)
        log(summary(rows, time.time() - started, args.mode) + (" (dry run, nothing written)" if args.dry_run else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Locality codes stored URL-encoded: preview and repair (one-off, 2026-10-22).

Until 2026-10-22 the specimen upload stored every locality code URL-encoded (HttpUtil.encodePath,
ISO-8859-1): "Ahi_<5m" as "Ahi_%3C5m", "MontagnedesFrancais" with a cedilla as "MontagnedesFran%E7ais",
'KM55""805' as "KM55%22%22805". Production held 1,117 specimens in 67 such codes. The upload now stores
codes as written. Old rows stay encoded until the group uploads again (CAS: every night, the TaxonWorks
upload) or until this repair. UploadUtil.cleanCode removes every % from an uploaded code, so a % followed
by two hex digits in a stored code is always this encoding.

Steps (docs/runbook.md, "Locality codes stored URL-encoded"):

  preview   read-only. Lists every stored code that holds %XX, with its decoded value, its group, its
            specimen count and locality names, and whether the decoded value is already the code of other
            specimens (MySQL compares codes ignoring case and accents, so "Francais" matches the decoded
            value too). Writes the list as a TSV file for the next step.
              status ok         the decoded code is used nowhere else
              status merge      the decoded code is used by specimens of the same group with the same
                                locality name: after the repair they share one locality page (wanted)
              status collision  the decoded code is used by another group or for another locality name:
                                left out of the repair; decide by hand
  sql       writes the repair SQL from the preview TSV (no database access): a backup table
            specimen_localitycode_bak_20261022 (code, access_group, localitycode), one UPDATE per stored
            code and group, row counts, and the rollback statement (also printed).

Decoding: each run of %XX is turned into bytes and read as UTF-8 when it is valid UTF-8, otherwise as
ISO-8859-1 (the upload's charset): %E7 -> c with cedilla, %22%22 -> "", %3C -> <. A character outside
ISO-8859-1 (a curly apostrophe) had been stored as %3F and decodes to "?": the original is lost.

Usage on a server (repository checkout in /root/antweb on production, /root/antweb-next on staging):
  python3 tools/data/locality_codes.py preview --project /root/antweb --out /root/loccode-preview-20261022.tsv
  python3 tools/data/locality_codes.py sql --preview /root/loccode-preview-20261022.tsv --skip-group 1 \
      --out /root/loccode-repair-20261022.sql
preview runs mysql in the container antweb_mysql_1 with the login of <project>/etc/db.properties (the
password goes to docker through the environment, never on a command line).

Tests: tests/unit/test_locality_codes.py.
"""
import argparse
import os
import re
import subprocess
import sys

BACKUP_TABLE = "specimen_localitycode_bak_20261022"
ENCODED = re.compile(r"(?:%[0-9A-Fa-f]{2})+")
ENCODED_SQL = "'%[0-9A-Fa-f]{2}'"
COLUMNS = ["stored_code", "decoded_code", "group_id", "group_name", "specimens", "locality_names", "status", "detail"]


# ------------------------------------------------------------------ decoding

def decode_run(run):
    """'%E7' -> 'ç' (ISO-8859-1), '%C3%A7' -> 'ç' (valid UTF-8), '%22%22' -> '""'."""
    data = bytes(int(run[i + 1:i + 3], 16) for i in range(0, len(run), 3))
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return data.decode("iso-8859-1")


def decode(code):
    """The stored code with every run of %XX decoded; other characters as they are."""
    return ENCODED.sub(lambda m: decode_run(m.group(0)), code)


# ------------------------------------------------------------------ SQL text

def sql_string(value):
    """A single-quoted MySQL literal (backslashes and apostrophes escaped), as DBUtil.sqlLiteral."""
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def preview_sql():
    return ("select s.localitycode, s.access_group, coalesce(g.name, ''), count(*),"
            " coalesce(group_concat(distinct s.localityname order by s.localityname separator ' | '), '')"
            " from specimen s left join ant_group g on g.id = s.access_group"
            " where s.localitycode regexp " + ENCODED_SQL +
            " group by s.localitycode, s.access_group, g.name order by s.access_group, s.localitycode")


def existing_sql(decoded_values):
    """The specimens already using one of the decoded codes (MySQL's comparison: case and accents ignored)."""
    values = ", ".join(sql_string(v) for v in sorted(set(decoded_values)))
    return ("select s.localitycode, s.access_group, count(*),"
            " coalesce(group_concat(distinct s.localityname order by s.localityname separator ' | '), '')"
            " from specimen s where s.localitycode in (" + values + ")"
            " and not s.localitycode regexp " + ENCODED_SQL +
            " group by s.localitycode, s.access_group")


# ------------------------------------------------------------------ preview

def fold(value):
    """Roughly MySQL's utf8_general_ci: case and accents ignored, trailing spaces ignored."""
    import unicodedata
    v = unicodedata.normalize("NFKD", value.rstrip(" "))
    return "".join(c for c in v if not unicodedata.combining(c)).lower()


def classify(encoded_rows, existing_rows):
    """encoded_rows: (stored, group_id, group_name, specimens, names); existing_rows: (code, group_id,
    specimens, names) of specimens whose code equals a decoded value. Returns preview rows (dicts)."""
    by_fold = {}
    for code, gid, n, names in existing_rows:
        by_fold.setdefault(fold(code), []).append((code, str(gid), int(n), names))
    # Two encoded codes that decode to the same value are a merge or a collision between themselves too.
    decoded_by_fold = {}
    for stored, gid, gname, n, names in encoded_rows:
        decoded_by_fold.setdefault(fold(decode(stored)), []).append((stored, str(gid), names))
    out = []
    for stored, gid, gname, n, names in encoded_rows:
        decoded = decode(stored)
        others = [(c, g, k, nm) for c, g, k, nm in by_fold.get(fold(decoded), [])]
        siblings = [(s, g, nm) for s, g, nm in decoded_by_fold[fold(decoded)] if (s, g) != (stored, str(gid))]
        status, detail = "ok", ""
        notes = []
        for c, g, k, nm in others:
            same = g == str(gid) and nm == names and c == decoded
            notes.append(f"{'same' if same else 'other'}: code {c!r}, group {g}, {k} specimens, names {nm!r}")
            if not same:
                status = "collision"
            elif status == "ok":
                status = "merge"
        for s, g, nm in siblings:
            same = g == str(gid) and nm == names and decode(s) == decoded
            notes.append(f"{'same' if same else 'other'}: also encoded as {s!r}, group {g}, names {nm!r}")
            if not same:
                status = "collision"
            elif status == "ok":
                status = "merge"
        detail = "; ".join(notes)
        out.append({"stored_code": stored, "decoded_code": decoded, "group_id": str(gid), "group_name": gname,
                    "specimens": str(n), "locality_names": names, "status": status, "detail": detail})
    return out


def tsv_field(value):
    return str(value).replace("\t", " ").replace("\n", " ").replace("\r", " ")


def write_preview(rows, path):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\t".join(COLUMNS) + "\n")
        for r in rows:
            f.write("\t".join(tsv_field(r[c]) for c in COLUMNS) + "\n")


def read_preview(path):
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    head = lines[0].split("\t")
    assert head == COLUMNS, f"{path} is not a preview file (first line: {lines[0][:80]!r})"
    return [dict(zip(COLUMNS, line.split("\t"))) for line in lines[1:] if line.strip()]


def print_preview(rows, out=sys.stdout):
    total = sum(int(r["specimens"]) for r in rows)
    out.write(f"{len(rows)} stored codes (per group), {total} specimens\n")
    for status in ("ok", "merge", "collision"):
        part = [r for r in rows if r["status"] == status]
        out.write(f"  {status}: {len(part)} codes, {sum(int(r['specimens']) for r in part)} specimens\n")
    groups = {}
    for r in rows:
        g = groups.setdefault((r["group_id"], r["group_name"]), [0, 0])
        g[0] += 1
        g[1] += int(r["specimens"])
    for (gid, gname), (codes, n) in sorted(groups.items(), key=lambda x: int(x[0][0] or 0)):
        out.write(f"  group {gid} {gname}: {codes} codes, {n} specimens\n")
    out.write("\nstatus\tgroup\tspecimens\tstored code\t-> decoded code\tlocality names\n")
    for r in rows:
        out.write(f"{r['status']}\t{r['group_id']}\t{r['specimens']}\t{r['stored_code']}\t-> {r['decoded_code']}"
                  f"\t{r['locality_names'][:80]}\n")
        if r["detail"]:
            out.write(f"\t\t\t{r['detail']}\n")


# ------------------------------------------------------------------ database (preview only, read-only)

def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def run_query(project, sql, container="antweb_mysql_1"):
    """Rows of a read-only query, through mysql in the container (tab-separated, no escaping)."""
    user, password = db_login(project)
    env = dict(os.environ, MYSQL_PWD=password)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4", "-B", "-N", "--raw"]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=env, timeout=600)
    if r.returncode != 0:
        sys.exit("mysql failed: " + r.stderr.decode("utf-8", "replace").strip())
    text = r.stdout.decode("utf-8")
    return [line.split("\t") for line in text.split("\n") if line]


def cmd_preview(args):
    q = lambda sql: run_query(args.project, sql + ";", args.container)
    encoded = [(c, g, gn, int(n), nm) for c, g, gn, n, nm in q(preview_sql())]
    existing = []
    if encoded:
        existing = [(c, g, int(n), nm) for c, g, n, nm in q(existing_sql([decode(r[0]) for r in encoded]))]
    rows = classify(encoded, existing)
    write_preview(rows, args.out)
    print_preview(rows)
    print(f"\nPreview written to {args.out}")


# ------------------------------------------------------------------ repair SQL

def repair_sql(rows, skip_groups=(), include_collisions=False):
    """The repair script: backup, updates in one transaction, counts. Returns (sql, rollback, chosen rows)."""
    chosen = [r for r in rows if r["group_id"] not in skip_groups
              and (include_collisions or r["status"] != "collision")]
    expected = sum(int(r["specimens"]) for r in chosen)
    lines = [
        "-- Locality codes stored URL-encoded: repair (tools/data/locality_codes.py sql; docs/runbook.md).",
        f"-- {len(chosen)} stored codes, {expected} specimens. Skipped groups: {', '.join(skip_groups) or 'none'}."
        f" Collisions {'included' if include_collisions else 'left out'}.",
        "-- Fails at the first statement if the backup table exists already (the repair has run before).",
        "set names utf8mb4;",
    ]
    if not chosen:
        lines = lines[:2] + ["select 'nothing to repair' as result;"]
        return "\n".join(lines) + "\n", "", chosen
    pairs = ",\n  ".join(f"({sql_string(r['stored_code'])}, {int(r['group_id'])})" for r in chosen)
    lines += [
        f"create table {BACKUP_TABLE} as select code, access_group, localitycode from specimen",
        f" where (localitycode, access_group) in (\n  {pairs});",
        f"select count(*) as backed_up, {expected} as expected from {BACKUP_TABLE};",
        "start transaction;",
    ]
    for r in chosen:
        s = sql_string(r["stored_code"])
        lines.append(f"update specimen set localitycode = {sql_string(r['decoded_code'])}"
                     f" where localitycode = {s} and cast(localitycode as binary) = cast({s} as binary)"
                     f" and access_group = {int(r['group_id'])};")
    lines += [
        f"select count(*) as changed, {expected} as expected from specimen s join {BACKUP_TABLE} b"
        " on b.code = s.code and b.access_group = s.access_group"
        " where cast(s.localitycode as binary) <> cast(b.localitycode as binary);",
        "commit;",
        "select count(*) as still_encoded_in_repaired_groups from specimen where localitycode regexp " + ENCODED_SQL
        + f" and access_group in (select distinct access_group from {BACKUP_TABLE});",
    ]
    rollback = (f"update specimen s join {BACKUP_TABLE} b on b.code = s.code and b.access_group = s.access_group"
                " set s.localitycode = b.localitycode;")
    lines.append("-- Rollback: " + rollback)
    return "\n".join(lines) + "\n", rollback, chosen


def cmd_sql(args):
    rows = read_preview(args.preview)
    skip = tuple(str(g) for g in (args.skip_group or []))
    sql, rollback, chosen = repair_sql(rows, skip, args.include_collisions)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(sql)
    left = [r for r in rows if r not in chosen]
    print(f"Repair SQL written to {args.out}: {len(chosen)} stored codes, "
          f"{sum(int(r['specimens']) for r in chosen)} specimens.")
    if left:
        print(f"Left out: {len(left)} codes, {sum(int(r['specimens']) for r in left)} specimens "
              f"(skipped groups or collisions):")
        for r in left:
            print(f"  group {r['group_id']} {r['status']}: {r['stored_code']} -> {r['decoded_code']}")
    if rollback:
        print("Rollback: " + rollback)


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("preview", help="read-only: list the encoded codes, write the preview TSV")
    a.add_argument("--project", default="/root/antweb", help="AntWeb checkout with etc/db.properties")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", required=True)
    a.set_defaults(func=cmd_preview)
    b = sub.add_parser("sql", help="write the repair SQL from a preview TSV")
    b.add_argument("--preview", required=True)
    b.add_argument("--out", required=True)
    b.add_argument("--skip-group", action="append", help="group id to leave out (repeatable), e.g. 1 for CAS")
    b.add_argument("--include-collisions", action="store_true")
    b.set_defaults(func=cmd_sql)
    args = p.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()

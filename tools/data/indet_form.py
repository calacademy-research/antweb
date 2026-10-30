#!/usr/bin/env python3
"""Indet form: rename existing names "<genus> indet" to the one form "<genus> (indet)".

Decision of Brian Fisher, 2026-10-28: a specimen identified only to genus has ONE name, "Pheidole (indet)" (the
species epithet in parentheses, so it sorts first and is never read as a species epithet "indet"). Uploads file a
blank species, "sp.", and since 2026-10-28 also "indet", "Indet", "indet.", "undet" as "(indet)"
(UploadUtil.normalizeIndetEpithet). Older uploads left names such as "myrmicinaepheidole indet" next to
"myrmicinaepheidole (indet)": two taxa for the same thing. This tool renames the old form in every table that keys on
the name.

What is renamed (a specimen counts at each rank it is identified to, and no further; the same rules as the upload,
UploadUtil.indetColumns, Brian Fisher 2026-10-28/29). "indet" here is indet, undet, (indet) or sp., any case, with or
without a trailing period:
  "<g> indet"        -> "<g> (indet)"  "myrmicinaepheidole indet" -> "myrmicinaepheidole (indet)"
  "<g> indet <x>"    -> "<g> (indet)"  species indet: identified only to genus, the subspecies value dropped
                                       ("myrmicinaeocymyrmex indet karooicus" -> "myrmicinaeocymyrmex (indet)")
  "<g> <sp> indet"   -> "<g> <sp>"     subspecies indet: identified to species ("ectatomminaegnamptogenys
                                       striatula-complex indet" -> "ectatomminaegnamptogenys striatula-complex")
Other names with an indet word (four words or more) are counted and listed as "not handled" (look at them by hand).
A new name that has no taxon row yet gets the old taxon row, renamed; one that exists already takes the old rows
(merged, as below).

Tables (only those present in the database are used):
  merge   rows are renamed; a row whose unique key (with the new name) exists already is a duplicate of a row of the
          "(indet)" name and is deleted (counts and lists are recomputed at the next nightly run):
          taxon (the old taxon row goes when "(indet)" exists: specimens join the "(indet)" taxon), proj_taxon,
          geolocale_taxon, bioregion_taxon, museum_taxon, taxon_country, taxon_set_parked, object_map
  rename  rows are renamed; a clash on a unique key refuses the run (preview lists it; a curator decides):
          specimen, description, description_edit, description_hist, taxon_prop, favorite_images, similar_species,
          geolocale_taxon_dispute, proj_taxon_dispute
  history counted, never changed (they record what happened): specimen_name_update, proj_taxon_log_detail,
          geolocale_taxon_log_detail
  Renamed taxon and specimen rows get the columns an upload gives them: "<g> (indet)": species "(indet)", status
  "indetermined" (three-word names also subspecies null, taxon rank species); "<g> <sp>": species <sp>, subspecies
  null, taxon rank species, and the status of species_status(): morphotaxon for a morphospecies name (a digit, - _ ( )
  or ., as striatula-complex), else the old status, except indetermined (given only for the word indet), which becomes
  unrecognized. Preview shows each name's status old -> new.
  Other name columns that point at a renamed name are renamed too: taxon.current_valid_name, taxon.parent_taxon_name,
  similar_species.similar_species. specimen.original_taxon_name is not changed: it records the name as uploaded.

Steps (docs/runbook.md, "Indet form"):
  preview   read-only (select statements only). Per table: rows with an old name, rows that clash with the "(indet)"
            name, rows changed; the names old -> new with their taxon and specimen counts (--all for every name).
  run       backs up every row with an old or a new name to tables indet_form_bak_<date>_<table> (and the names to
            indet_form_bak_<date>_names), checks the copies, then renames in one transaction. A count that differs
            stops it before the commit. Asks for "yes" first (unless --yes).
  rollback  puts the rows of the backup back (one transaction): deletes the rows with an old or new name and inserts
            the backup. Refuses when rows with those names changed since the run (an upload): --force to go on.

Production: every step refuses on production (/root/antweb, or a host named antweb-prod) without --production.
Run it on staging first.

After run or rollback: restart the app (docker restart antweb_antweb_1; 2 to 4 minutes) so its name cache sees the
change; species, place and project counts follow at the next nightly run.

Database: mysql in the container antweb_mysql_1 with the login of <project>/etc/db.properties (the password goes to
docker through the environment, never on a command line or in a prompt).

Usage (checkout in /root/antweb-next on staging, /root/antweb on production):
  python3 tools/data/indet_form.py preview  --project /root/antweb-next
  python3 tools/data/indet_form.py run      --project /root/antweb-next
  python3 tools/data/indet_form.py rollback --project /root/antweb-next --date YYYYMMDD

Tests: tests/unit/test_indet_form.py.
"""
import argparse
import datetime
import os
import re
import socket
import subprocess
import sys

INDET = "(indet)"
# The old form: two words, the second indet or undet, with or without a period (any case).
OLD_FORM = re.compile(r"^(\S+) (indet|undet)\.?$", re.IGNORECASE)
# An epithet that says "not identified at this rank": indet, undet, (indet), sp. (any case, trailing period).
_IND = r"(?:(?:indet|undet|sp)\.?|\(indet\))"
# Three words, the species indet: "<g> indet <x>" (Ocymyrmex indet karooicus). Identified only to genus.
SPECIES_INDET_3 = re.compile(r"^(\S+) " + _IND + r" (\S+)$", re.IGNORECASE)
# Three words, the subspecies indet: "<g> <sp> indet" (Gnamptogenys striatula-complex indet). Identified to species.
SUBSPECIES_INDET_3 = re.compile(r"^(\S+) (\S+) " + _IND + r"$", re.IGNORECASE)
# Any name with indet or undet as a word after the first, or "(indet)" followed by a word or as the third word (to
# count what the tool does not handle).
ANY_INDET_WORD = re.compile(r" (indet|undet)\.?( |$)| \(indet\) |^\S+ \S+ \(indet\)$", re.IGNORECASE)
SQL_OLD_FORM = "'^[^ ]+ (indet|undet)[.]?$'"
SQL_ANY_INDET_WORD = "' (indet|undet)[.]?( |$)| [(]indet[)] |^[^ ]+ [^ ]+ [(]indet[)]$'"
# What a name is (kind_of): the two-word old form, three words with the species indet, three words with the
# subspecies indet.
TWO_WORD, SPECIES_INDET, SUBSPECIES_INDET = "two-word", "species indet", "subspecies indet"

MERGE = ("taxon", "proj_taxon", "geolocale_taxon", "bioregion_taxon", "museum_taxon", "taxon_country",
         "taxon_set_parked", "object_map")
RENAME = ("specimen", "description", "description_edit", "description_hist", "taxon_prop", "favorite_images",
          "similar_species", "geolocale_taxon_dispute", "proj_taxon_dispute")
HISTORY = {"specimen_name_update": ("uploaded_name", "old_name", "new_name"),
           "proj_taxon_log_detail": ("taxon_name",), "geolocale_taxon_log_detail": ("taxon_name",)}
# Name columns besides taxon_name (renamed where they hold an old name).
# specimen.original_taxon_name is left as it is: it records the name as uploaded (history).
EXTRA_COLUMNS = {"taxon": ("current_valid_name", "parent_taxon_name"), "similar_species": ("similar_species",)}
# Tables whose renamed rows get species "(indet)" and status "indetermined".
SET_INDET = ("taxon", "specimen")
PRODUCTION_PROJECT = "/root/antweb"
# The server's role (refresh-from-prod.sh reads the same file): anything but "staging" counts as production.
ROLE_FILE = "/etc/antweb-role"


# ------------------------------------------------------------------ names (no database)

def kind_of(old):
    """TWO_WORD, SPECIES_INDET or SUBSPECIES_INDET (see new_name), or None for a name the tool does not handle."""
    old = old or ""
    if OLD_FORM.match(old):
        return TWO_WORD
    if SPECIES_INDET_3.match(old):
        return SPECIES_INDET
    if SUBSPECIES_INDET_3.match(old):
        return SUBSPECIES_INDET
    return None


def new_name(old):
    """The name an old name is filed under, or None when the tool does not handle it. A specimen counts at each rank it
    is identified to, and no further (Brian Fisher, 2026-10-28/29):
      "<g> indet"        -> "<g> (indet)"   (two words; also Indet, indet., undet)
      "<g> indet <x>"    -> "<g> (indet)"   (species indet: identified only to genus; the subspecies value is dropped)
      "<g> <sp> indet"   -> "<g> <sp>"      (subspecies indet: identified to species)
    indet here is indet, undet, (indet) or sp., any case, with or without a period. The same rules as the upload
    (UploadUtil.indetColumns)."""
    kind = kind_of(old)
    if kind == TWO_WORD:
        return f"{OLD_FORM.match(old).group(1)} {INDET}"
    if kind == SPECIES_INDET:
        return f"{SPECIES_INDET_3.match(old).group(1)} {INDET}"
    if kind == SUBSPECIES_INDET:
        m = SUBSPECIES_INDET_3.match(old)
        return f"{m.group(1)} {m.group(2)}"
    return None


def is_morpho(taxon_name):
    """Taxon.isMorpho(): a morphospecies name (status morphotaxon): a one-letter last word, or a digit, - _ ( ) or ."""
    if not taxon_name or INDET in taxon_name:
        return False
    if len(taxon_name.split(" ")[-1]) == 1 and " " in taxon_name:
        return True
    return bool(re.search(r"[1-9\-_().]", taxon_name))


def species_status(new, old_status):
    """The status a "<g> <sp>" name gets (SUBSPECIES_INDET), as an upload of that name gives it: morphotaxon for a
    morphospecies name (striatula-complex); otherwise the old row's status, except indetermined (the old name counted
    as indet only because of the word indet), which becomes unrecognized (not in AntCat; the next upload of the group
    checks AntCat again)."""
    if is_morpho(new):
        return "morphotaxon"
    if not old_status or old_status == "indetermined":
        return "unrecognized"
    return old_status


def name_map(names):
    """{old: new} of the names in the old form (others left out). MySQL compares names ignoring case, so names that
    differ only in case are one name (the first in sorted order is kept)."""
    out, seen = {}, set()
    for n in sorted(set(names)):
        nn = new_name(n)
        if nn and n.lower() not in seen:
            seen.add(n.lower())
            out[n] = nn
    return out


def not_handled(names):
    """Names with an indet word that the tool does not rename (four words or more, or the like)."""
    return sorted({n for n in names if ANY_INDET_WORD.search(n or "") and not new_name(n)})


def columns_of(table):
    return ("taxon_name",) + EXTRA_COLUMNS.get(table, ())


# ------------------------------------------------------------------ SQL text

def sql_string(value):
    """A single-quoted MySQL literal (backslashes and apostrophes escaped)."""
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def in_list(values):
    """"('a', 'b')"; '(NULL)' when empty (valid SQL that matches nothing)."""
    return "(" + ", ".join(sql_string(v) for v in values) + ")" if values else "(NULL)"


def check(name, actual_sql, expected):
    """A check that stops mysql (an error ends the batch before the commit, so the transaction is rolled back)."""
    return (f"set @n = {actual_sql};\n"
            f"select @n as {name}, {int(expected)} as expected, if(@n = {int(expected)}, 'ok', "
            f"(select 'mismatch' union all select 'stop')) as check_{name};")


def map_sql(mapping):
    """A derived table m(seq, old_name, new_name) of the mapping in the order run renames them (no temporary table:
    preview stays read-only)."""
    if not mapping:
        return ("(select 0 as seq, cast(null as char) as old_name, cast(null as char) as new_name"
                " from dual where 1 = 0)")
    rows = [f"select {i} as seq, {sql_string(o)} as old_name, {sql_string(n)} as new_name" if i == 0
            else f"select {i}, {sql_string(o)}, {sql_string(n)}" for i, (o, n) in enumerate(sorted(mapping.items()))]
    return "(" + " union all ".join(rows) + ")"


def name_predicate(table, names, alias=""):
    """Rows of the table with one of the names in any of its name columns."""
    a = alias + "." if alias else ""
    return "(" + " or ".join(f"{a}{c} in {in_list(names)}" for c in columns_of(table)) + ")"


def clash_join(key, alias_old="a", alias_new="b"):
    """The join condition of a unique key: the same values in the other columns of the key."""
    return " and ".join(f"{alias_new}.{c} <=> {alias_old}.{c}" for c in key if c != "taxon_name")


def candidates_sql(tables):
    """The read-only queries that find the names: per table and name column, distinct names with an indet word."""
    out = []
    for t in tables:
        for c in columns_of(t):
            out.append(f"select distinct {c} from {t} where {c} regexp {SQL_ANY_INDET_WORD}")
    return out


def clash_sql(table, mapping, keys):
    """Rows with an old name that clash when renamed, in the order run renames them: on one of the unique keys, a row
    of the new name exists already, or a row of an earlier old name with the same new name (renamed before it). In a
    merge table run deletes exactly these rows; in a rename table they refuse the run. None without a unique key."""
    if not keys:
        return None
    m = map_sql(mapping)
    parts = []
    for key in keys:
        cond = clash_join(key)
        cond = f" and {cond}" if cond else ""
        parts.append(f"exists (select 1 from {table} b where b.taxon_name = m.new_name{cond})")
        parts.append(f"exists (select 1 from {table} b join {m} m2 on b.taxon_name = m2.old_name"
                     f" where m2.new_name = m.new_name and m2.seq < m.seq{cond})")
    return (f"select count(*) from {table} a join {m} m on a.taxon_name = m.old_name where "
            + " or ".join(parts))


def preview_sql(table, column, mapping, keys):
    """Counts of one table and column: rows with an old name, and (taxon_name only) the rows that clash."""
    q = {"rows": f"select count(*) from {table} where {column} in {in_list(sorted(mapping))}"}
    if column == "taxon_name":
        c = clash_sql(table, mapping, keys)
        if c:
            q["clash"] = c
    return q


def bak_tables(date, tables):
    return {t: f"indet_form_bak_{date}_{t}" for t in list(tables) + ["names", "after"]}


def backup_sql(date, tables, mapping, expected):
    """Copies every row with an old or new name to dated tables; checks each copy against the preview count."""
    b = bak_tables(date, tables)
    names = sorted(set(mapping) | set(mapping.values()))
    lines = ["-- Indet form: backup (tools/data/indet_form.py run). Stops if the backup tables exist already.",
             "set names utf8mb4;",
             f"create table {b['names']} (old_name varchar(128) not null, new_name varchar(128) not null);"]
    for o, n in sorted(mapping.items()):
        lines.append(f"insert into {b['names']} values ({sql_string(o)}, {sql_string(n)});")
    lines.append(check("names", f"(select count(*) from {b['names']})", len(mapping)))
    for t in tables:
        lines.append(f"create table {b[t]} as select * from {t} where {name_predicate(t, names)};")
        lines.append(check(f"bak_{t}", f"(select count(*) from {b[t]})", expected[t]))
    lines.append(f"create table {b['after']} (table_name varchar(64) not null, row_count int not null);")
    lines.append("select 'backup tables written' as result;")
    return "\n".join(lines) + "\n"


def set_columns(table, old, new, statuses=None):
    """The columns a renamed taxon or specimen row gets besides taxon_name (as an upload gives them)."""
    kind = kind_of(old)
    if kind == TWO_WORD:
        return f", species = {sql_string(INDET)}, status = 'indetermined'"
    rank = ", taxarank = 'species'" if table == "taxon" else ""
    if kind == SPECIES_INDET:
        return f", species = {sql_string(INDET)}, subspecies = null, status = 'indetermined'{rank}"
    status = (statuses or {}).get(old) or species_status(new, None)
    return f", species = {sql_string(new.split(' ', 1)[1])}, subspecies = null, status = {sql_string(status)}{rank}"


def run_sql(date, tables, mapping, keys, expected_after, statuses=None):
    """The renames in one transaction, name by name; the commit only if no old name is left and the counts match.
    statuses: {old name: status} of the "<g> <sp>" names (species_status), else species_status(new, None)."""
    b = bak_tables(date, tables)
    olds = sorted(mapping)
    names = sorted(set(mapping) | set(mapping.values()))
    lines = [f"-- Indet form: {len(mapping)} names to the (indet) form (tools/data/indet_form.py run).",
             "set names utf8mb4;", "start transaction;"]
    for old, new in sorted(mapping.items()):
        o, n = sql_string(old), sql_string(new)
        lines.append(f"-- {old} -> {new}")
        for t in tables:
            if t in MERGE:
                for key in keys.get(t, []):
                    cond = clash_join(key)
                    lines.append(f"delete a from {t} a join {t} b on b.taxon_name = {n}"
                                 + (f" and {cond}" if cond else "") + f" where a.taxon_name = {o};")
            sets = f"taxon_name = {n}"
            if t in SET_INDET:
                sets += set_columns(t, old, new, statuses)
            lines.append(f"update {t} set {sets} where taxon_name = {o};")
            for c in EXTRA_COLUMNS.get(t, ()):
                lines.append(f"update {t} set {c} = {n} where {c} = {o};")
    for t in tables:
        for c in columns_of(t):
            lines.append(check(f"left_{t}_{c}", f"(select count(*) from {t} where {c} in {in_list(olds)})", 0))
        lines.append(check(f"after_{t}", f"(select count(*) from {t} where {name_predicate(t, names)})",
                           expected_after[t]))
    # the row counts after the run, for rollback to see whether an upload changed them since (the table is made by
    # the backup: a create table here would commit the transaction early)
    for t in tables:
        lines.append(f"insert into {b['after']} select {sql_string(t)}, count(*) from {t} where {name_predicate(t, names)};")
    lines += ["commit;", "select 'committed' as result;"]
    return "\n".join(lines) + "\n"


def rollback_sql(date, tables, names):
    """Deletes the rows with an old or new name and puts the backup back, in one transaction."""
    b = bak_tables(date, tables)
    lines = ["-- Indet form: rollback from the backup tables (tools/data/indet_form.py rollback).",
             "set names utf8mb4;", "start transaction;"]
    for t in tables:
        lines.append(f"delete from {t} where {name_predicate(t, names)};")
        lines.append(f"insert into {t} select * from {b[t]};")
        lines.append(f"set @n = row_count();")
        lines.append(f"select @n as restored_{t}, (select count(*) from {b[t]}) as expected,"
                     f" if(@n = (select count(*) from {b[t]}), 'ok', (select 'mismatch' union all select 'stop'))"
                     f" as check_restored_{t};")
    lines += ["commit;", "select 'committed' as result;"]
    return "\n".join(lines) + "\n"


def expected_counts(tables, plan):
    """Rows per table before (old or new name) and after the run (minus the merge duplicates that go)."""
    before, after = {}, {}
    for t in tables:
        before[t] = plan[t]["rows_any"]
        after[t] = plan[t]["rows_any"] - plan[t]["drop"]
    return before, after


# ------------------------------------------------------------------ report

def print_preview(plan, mapping, samples, unhandled, history, out=None, show_all=False):
    out = out or sys.stdout
    w = out.write
    kinds = {k: sum(1 for o in mapping if kind_of(o) == k) for k in (TWO_WORD, SPECIES_INDET, SUBSPECIES_INDET)}
    w(f"Names in the old form: {len(mapping)} ({kinds[TWO_WORD]} '<genus> indet' -> '<genus> {INDET}';"
      f" {kinds[SPECIES_INDET]} '<genus> indet <x>' -> '<genus> {INDET}'; {kinds[SUBSPECIES_INDET]}"
      f" '<genus> <species> indet' -> '<genus> <species>').\n")
    w("\nTable                         old-name rows  clash  -> deleted (merge) / refused   rows changed\n")
    total_refused = 0
    for t, p in plan.items():
        kind = "merge" if t in MERGE else "rename"
        refused = p["clash"] if t not in MERGE else 0
        total_refused += refused
        extra = "".join(f", {c} {p['extra'][c]}" for c in EXTRA_COLUMNS.get(t, ()) if p["extra"].get(c))
        w(f"  {t:<28}{p['rows']:>12}  {p['clash']:>5}  {kind:>6}: "
          + (f"{p['drop']} deleted" if t in MERGE else (f"{refused} REFUSED" if refused else "none"))
          + f"   {p['rows'] - (p['drop'] if t in MERGE else 0)}{extra}\n")
    for t, n in history.items():
        w(f"  {t:<28}{n:>12}  (history, not changed)\n")
    w("\nNames (old -> new: taxon rows, specimens; '+' when the new taxon exists already, merged; the status the"
      " renamed rows get):\n")
    # every three-word name is listed (few, each worth a look); two-word names up to 25 unless --all
    shown = samples if show_all else ([x for x in samples if kind_of(x["old"]) != TWO_WORD]
                                      + [x for x in samples if kind_of(x["old"]) == TWO_WORD][:25])
    for s in shown:
        w(f"  {s['old']} -> {s['new']}: {s['taxa']} taxon, {s['specimens']} specimens"
          + (" +" if s["target_exists"] else "")
          + (f"; status {s.get('old_status') or '-'} -> {s['status']}" if s.get("status") else "") + "\n")
    if len(samples) > len(shown):
        w(f"  ... {len(samples) - len(shown)} more (--all lists them)\n")
    if unhandled:
        w(f"\nNot handled (not '<genus> indet', '<genus> indet <x>' or '<genus> <species> indet'; look by hand):"
          f" {len(unhandled)}\n")
        for n in unhandled[:25]:
            w(f"  {n}\n")
    if total_refused:
        w(f"\nREFUSED: {total_refused} rows would clash with a row of the (indet) name in a table that is not merged."
          " run stops until they are resolved.\n")
    return total_refused


# ------------------------------------------------------------------ database

def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def mysql(args, sql, batch=True):
    """Runs sql through mysql in the container; returns stdout. Exits with mysql's message on an error."""
    user, password = db_login(args.project)
    env = dict(os.environ, MYSQL_PWD=password)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", args.container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4"] + (["-B", "-N", "--raw"] if batch else ["--table"])
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=env, timeout=3600)
    text = r.stdout.decode("utf-8", "replace")
    if r.returncode != 0:
        if not batch:
            print(text)
        sys.exit("mysql stopped: " + r.stderr.decode("utf-8", "replace").strip()
                 + ("\nNothing was committed." if not batch else ""))
    return text


def rows_of(text):
    return [line.split("\t") for line in text.split("\n") if line]


def one(args, sql):
    r = rows_of(mysql(args, sql + ";"))
    return int(r[0][0]) if r else 0


def tables_present(args):
    """{table: [unique keys containing taxon_name, each a tuple of columns]} of the tables in the database."""
    wanted = list(MERGE) + list(RENAME)
    q = ("select table_name from information_schema.tables where table_schema = 'ant' and table_type = 'BASE TABLE'"
         f" and table_name in {in_list(wanted + list(HISTORY))};")
    found = {r[0] for r in rows_of(mysql(args, q))}
    q = ("select table_name, index_name, group_concat(column_name order by seq_in_index separator ',')"
         " from information_schema.statistics where table_schema = 'ant' and non_unique = 0"
         f" and table_name in {in_list(wanted)} group by table_name, index_name;")
    keys = {}
    for t, _, cols in rows_of(mysql(args, q)):
        cols = tuple(cols.split(","))
        if "taxon_name" in cols:
            keys.setdefault(t, []).append(cols)
    tables = [t for t in wanted if t in found]
    history = [t for t in HISTORY if t in found]
    return tables, {t: keys.get(t, []) for t in tables}, history


def current_plan(args):
    tables, keys, history_tables = tables_present(args)
    if "taxon" not in tables or "specimen" not in tables:
        sys.exit("Tables taxon and specimen not found in database ant: is this the AntWeb database?")
    found = set()
    for q in candidates_sql(tables):
        found |= {r[0] for r in rows_of(mysql(args, q + ";")) if r and r[0] != "NULL"}
    mapping = name_map(found)
    unhandled = not_handled(found)
    names = sorted(set(mapping) | set(mapping.values()))
    plan = {}
    for t in tables:
        p = dict(rows=0, clash=0, drop=0, extra={}, rows_any=0)
        if mapping:
            for c in columns_of(t):
                qs = preview_sql(t, c, mapping, keys[t])
                n = one(args, qs["rows"])
                if c == "taxon_name":
                    p["rows"] = n
                    p["clash"] = one(args, qs["clash"]) if "clash" in qs else 0
                    p["drop"] = p["clash"] if t in MERGE else 0
                else:
                    p["extra"][c] = n
            p["rows_any"] = one(args, f"select count(*) from {t} where {name_predicate(t, names)}")
        plan[t] = p
    history = {}
    for t in history_tables:
        cols = HISTORY[t]
        history[t] = one(args, f"select count(*) from {t} where " + " or ".join(
            f"{c} in {in_list(sorted(mapping))}" for c in cols)) if mapping else 0
    samples = []
    if mapping:
        taxa = {r[0].lower(): int(r[1]) for r in rows_of(mysql(args,
                f"select taxon_name, count(*) from taxon where taxon_name in {in_list(names)} group by taxon_name;"))}
        specs = {r[0].lower(): int(r[1]) for r in rows_of(mysql(args,
                 f"select taxon_name, count(*) from specimen where taxon_name in {in_list(sorted(mapping))} group by taxon_name;"))}
        old_status = {r[0].lower(): r[1] for r in rows_of(mysql(args,
                      f"select taxon_name, status from taxon where taxon_name in {in_list(sorted(mapping))};"))}
        for o, n in sorted(mapping.items()):
            st = old_status.get(o.lower())
            status = species_status(n, st) if kind_of(o) == SUBSPECIES_INDET else "indetermined"
            samples.append(dict(old=o, new=n, taxa=taxa.get(o.lower(), 0), specimens=specs.get(o.lower(), 0),
                                target_exists=n.lower() in taxa, old_status=st, status=status))
    return tables, keys, mapping, plan, samples, unhandled, history


def statuses_of(samples):
    """{old name: status} of the "<g> <sp>" names, for run_sql."""
    return {s["old"]: s["status"] for s in samples if kind_of(s["old"]) == SUBSPECIES_INDET}


# ------------------------------------------------------------------ commands

def server_role():
    try:
        with open(ROLE_FILE) as f:
            return f.read().strip().lower()
    except OSError:
        return None


def is_production(project):
    """Both paths resolved (/root/antweb may be a link to a dated folder); the host name; /etc/antweb-role."""
    role = server_role()
    return (os.path.realpath(project) == os.path.realpath(PRODUCTION_PROJECT)
            or socket.gethostname().lower().startswith("antweb-prod")
            or (role is not None and role != "staging"))


def start(args):
    if not os.path.isfile(os.path.join(args.project, "etc", "db.properties")):
        sys.exit(f"No {os.path.join(args.project, 'etc', 'db.properties')}: is --project the AntWeb checkout "
                 "(/root/antweb-next on staging, /root/antweb on production)?")
    if is_production(args.project) and not args.production:
        sys.exit("This is production. Run on staging first; on production add --production.")
    user = db_login(args.project)[0]
    print(f"Database: docker exec -i -e MYSQL_PWD {args.container} mysql -u {user} ant"
          f"   (password from {os.path.join(args.project, 'etc', 'db.properties')})")


def cmd_preview(args):
    start(args)
    tables, keys, mapping, plan, samples, unhandled, history = current_plan(args)
    print()
    print_preview(plan, mapping, samples, unhandled, history, show_all=args.all)
    print("\nPreview only: nothing was changed.")


def confirm(args, question):
    if args.yes:
        return
    if input(question + " Type yes to go on: ").strip().lower() != "yes":
        sys.exit("Stopped: nothing was changed.")


def cmd_run(args):
    start(args)
    tables, keys, mapping, plan, samples, unhandled, history = current_plan(args)
    print()
    refused = print_preview(plan, mapping, samples, unhandled, history)
    if not mapping:
        print("\nNothing to do: no name in the old form.")
        return
    if refused:
        sys.exit("Stopped: rows clash in a table that is not merged (REFUSED above). Nothing was changed.")
    before, after = expected_counts(tables, plan)
    bsql = backup_sql(args.date, tables, mapping, before)
    rsql = run_sql(args.date, tables, mapping, keys, after, statuses_of(samples))
    print(f"\nBackup to tables indet_form_bak_{args.date}_*, then {len(mapping)} names renamed in one transaction.")
    if args.show_sql:
        print(bsql + "\n" + rsql)
    confirm(args, "Back up and rename?")
    print(mysql(args, bsql, batch=False))
    out = mysql(args, rsql, batch=False)
    print(out)
    if "committed" not in out:
        sys.exit("No commit seen: check the output above.")
    print(f"\nDone: {len(mapping)} names renamed. Backup: tables indet_form_bak_{args.date}_*.\n"
          "Restart the app (docker restart antweb_antweb_1; 2 to 4 minutes) so its name cache sees the change; "
          "counts follow at the next nightly run.\n"
          f"Rollback: python3 tools/data/indet_form.py rollback --project {args.project} --date {args.date}")


def cmd_rollback(args):
    start(args)
    tables, _, _ = tables_present(args)
    b = bak_tables(args.date, tables)
    q = ("select table_name from information_schema.tables where table_schema = 'ant'"
         f" and table_name like {sql_string('indet_form_bak_' + args.date + '_%')};")
    present = {r[0] for r in rows_of(mysql(args, q))}
    if b["names"] not in present:
        sys.exit(f"No backup {b['names']}: give the --date of the run (YYYYMMDD).")
    tables = [t for t in tables if b[t] in present]
    names = set()
    for o, n in rows_of(mysql(args, f"select old_name, new_name from {b['names']};")):
        names |= {o, n}
    names = sorted(names)
    if b["after"] in present:
        recorded = {t: int(n) for t, n in rows_of(mysql(args, f"select table_name, row_count from {b['after']};"))}
        changed = []
        for t in tables:
            now = one(args, f"select count(*) from {t} where {name_predicate(t, names)}")
            if t in recorded and now != recorded[t]:
                changed.append(f"{t} ({recorded[t]} rows after the run, {now} now)")
        if changed and not args.force:
            sys.exit("Rows with these names changed since the run (an upload?): " + "; ".join(changed)
                     + ". Rollback would undo that too. Add --force to go on anyway.")
    sql = rollback_sql(args.date, tables, names)
    if args.show_sql:
        print(sql)
    confirm(args, f"Put back {len(tables)} tables from indet_form_bak_{args.date}_*?")
    out = mysql(args, sql, batch=False)
    print(out)
    if "committed" not in out:
        sys.exit("No commit seen: check the output above.")
    print("Rollback done. Restart the app (docker restart antweb_antweb_1); counts follow at the next nightly run.")


def main(argv=None):
    here = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)
    for name, func, text in (("preview", cmd_preview, "read-only: per-table counts and the names"),
                             ("run", cmd_run, "back up the rows, then rename (one transaction)"),
                             ("rollback", cmd_rollback, "put the rows back from the backup tables")):
        a = sub.add_parser(name, help=text)
        a.add_argument("--project", default=here, help="AntWeb checkout with etc/db.properties (default: this checkout)")
        a.add_argument("--container", default="antweb_mysql_1")
        a.add_argument("--date", default="", help="backup date YYYYMMDD, or YYYYMMDDb for a second run the same day (run: today; rollback: required)")
        a.add_argument("--production", action="store_true", help="allow production (run on staging first)")
        a.add_argument("--yes", action="store_true", help="do not ask before changing anything")
        a.add_argument("--force", action="store_true", help="rollback even if rows changed since the run")
        a.add_argument("--all", action="store_true", help="preview: list every name")
        a.add_argument("--show-sql", action="store_true", help="print the SQL before running it")
        a.set_defaults(func=func)
    args = p.parse_args(argv)
    # A letter after the date names a second run on the same day (20261029b): backup table names never repeat.
    if args.date and not re.fullmatch(r"\d{8}[a-z]?", args.date):
        sys.exit("--date must be YYYYMMDD, or YYYYMMDD plus one letter for another run the same day")
    if args.cmd == "rollback" and not args.date:
        sys.exit("rollback needs --date (the date of the run, YYYYMMDD)")
    if not args.date:
        args.date = datetime.date.today().strftime("%Y%m%d")
    args.func(args)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Country rename: give a country its current name everywhere AntWeb keeps the name (2026-11-10).

Why (Brian Fisher, 2026-11-10: "we should update antweb to the most current names: we have not updated them for
many many years ... it takes me too much to update antweb names"): a country's name is not only on its own row of
the place table (geolocale). Its states, the specimens collected there, the curators assigned to it, its page text
and two count tables carry the name too. Edit Geolocale renames the row and some of these; it leaves the specimens
(the country page then shows none) and does not keep the old name, so the next upload that writes "Ivory Coast"
is no longer understood. This tool does the whole rename in one transaction, with a backup and a way back.

  python3 country_rename.py                         report only (the default; nothing is changed)
  python3 country_rename.py --only "Swaziland"      only these of the renames (old names, comma separated)
  python3 country_rename.py --rename "Old=New"      a rename that is not in the list below (may be repeated)
  python3 country_rename.py --apply                 make the changes (backup tables are written first)
  python3 country_rename.py --rollback country_rename_backup_20261110_1615     put the old names back

The renames (AntWeb's valid name: the current name, ISO 3166-1), RENAMES below: Swaziland: Eswatini; Macedonia:
North Macedonia; Cape Verde: Cabo Verde; Czech Republic: Czechia; Ivory Coast: Côte d'Ivoire; Democratic Republic
of Congo: Democratic Republic of the Congo; Turkey: Türkiye. AntWeb's own island places are never renamed.

What a rename does (names are compared exactly: capitals and accents count):

  the country   its row keeps its id (species list, counts, images and maps are stored by the id and follow) and
                takes the new name. When AntWeb already has the new name as a not-valid row that points to the old
                one (Czechia, Cabo Verde, Côte d'Ivoire, Democratic Republic of the Congo), the two rows swap
                names. Otherwise a row is added for the old name.
  the old name  stays as a not-valid row whose valid name is the new one, so an upload, a TaxonWorks record or an
                AntCat record that writes the old name is stored under the new name, as for every old name.
  by name       other old names of the country (their valid name), its states (their parent and country), the
                specimens (country, island_country), the curators assigned to it (login_country), its page text
                and the text's history (object_edit, object_hist), the counts kept by country name
                (place_genus_curation, place_specimen_name) and the name shown on the imaging progress page.
  not changed   the state of a specimen; the country text of AntCat names (taxon.country, homonym.country: the
                daily update writes AntCat's own text again); the old unused tables (project.country, un_country,
                country, taxon_country, antwiki_taxon_country, country_bioregion); uploaded files and upload
                reports; specimens written with other capitals or accents than AntWeb's old name (the place fix
                tool, tools/place/place_fix.py, handles those).

Nothing is changed, and the report says why, when: AntWeb has no valid country of the old name; both names are
valid countries; the old or the new name is an island place; a state of the same name exists under both names;
the new name already has page text. A rename already made is found as such: the tool then only carries what is
still under the old name (specimens of an upload that ran before AntWeb's memory was reloaded).

--apply: (1) asks AntWeb whether an upload or the daily update is running and stops if so; (2) writes the record
table country_rename_backup_<date>_<time> and one backup table per table it will change (..._geolocale, ..._specimen,
..._login_country, ..._object_edit, ..._object_hist, ..._place_genus_curation, ..._place_specimen_name); (3) stops,
before changing anything, when the backups hold other numbers than the report counted (an upload ran in between:
run it again); (4) makes every change in one transaction: all of it or nothing; (5) reads the places again and
expects every rename done and nothing left under an old name.

After --apply or --rollback AntWeb still has the old names in memory: reload them at once (Technical page, "Reload
the in-memory lists": "countries and adm1s (geolocales)", then "logins"), or restart AntWeb. Until then a country
page counts no specimens and an upload stores the name AntWeb has in memory. The daily update then counts again:
the country's totals, its genus lists' Not curated column, the imaging progress row and the maps.

Rollback: --rollback <table> puts back the names of every row the run changed (a specimen only when it still has
the new name) and removes the rows it added. The tables stay until an administrator drops them.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. How to run it: docs/runbook.md, "Renaming a country". Tests: tests/unit/test_country_rename.py.
"""
import argparse
import collections
import csv
import datetime
import os
import re
import subprocess
import sys
import urllib.request

VERSION = "1"
TABLE_NAME = re.compile(r"^country_rename_backup_\d{8}_\d{4}$")
TEMP_NAME = "~country_rename~"

RENAMES = [
    ("Swaziland", "Eswatini"),
    ("Macedonia", "North Macedonia"),
    ("Cape Verde", "Cabo Verde"),
    ("Czech Republic", "Czechia"),
    ("Ivory Coast", "Côte d'Ivoire"),
    ("Democratic Republic of Congo", "Democratic Republic of the Congo"),
    ("Turkey", "Türkiye"),
]

# What is found for a pair of names.
RENAME = "rename"            # the old name is the valid country, AntWeb has no row of the new name: rename, add the old name
SWAP = "swap"                # the new name is a not-valid row pointing to the old one: the two rows swap names
DONE = "done"                # already renamed: only what is still under the old name is carried
ADD_OLD_NAME = "add old"     # the new name is the valid country and the old name has no row: the old name is added
STATE_TEXT = {
    RENAME: "the country's row takes the new name; a row is added for the old name",
    SWAP: "the country's row and the row AntWeb has for the new name swap names",
    DONE: "already renamed; only what is still under the old name is carried",
    ADD_OLD_NAME: "already renamed, but AntWeb has no row for the old name: it is added",
}


# ---------------------------------------------------------------- the decision (no database)

class Row:
    """A row of the place table of rank country."""
    def __init__(self, id, name, is_valid, valid_name, is_island, author_image=""):
        self.id = int(id)
        self.name = name
        self.is_valid = str(is_valid) == "1"
        v = (valid_name or "").strip()
        self.valid_name = "" if v.lower() in ("", "none", "null") else v      # the text "none" is stored here too
        self.is_island = str(is_island) == "1"
        self.author_image = author_image or ""


Decision = collections.namedtuple("Decision", "state valid_id alias_id problem")


def decide(rows, old, new):
    """What to do for one rename, from the country rows named exactly old or new. problem: why nothing is done."""
    def stop(why):
        return Decision(None, None, None, why)
    if old == new or not old.strip() or not new.strip():
        return stop("the old and the new name must be two names")
    olds = [r for r in rows if r.name == old]
    news = [r for r in rows if r.name == new]
    if len(olds) > 1 or len(news) > 1:
        return stop("AntWeb has two country rows named " + (old if len(olds) > 1 else new))
    x = olds[0] if olds else None
    y = news[0] if news else None
    if (x is not None and x.is_island) or (y is not None and y.is_island):
        return stop("an island place of AntWeb: this tool renames countries only")
    if x is not None and x.is_valid:
        if y is None:
            return Decision(RENAME, x.id, None, None)
        if y.is_valid:
            return stop("both names are valid countries in AntWeb: an administrator must first decide which one stays")
        if y.valid_name not in ("", old):
            return stop(new + " is an old name of " + y.valid_name + " in AntWeb, not of " + old)
        return Decision(SWAP, x.id, y.id, None)
    if y is not None and y.is_valid:
        if x is None:
            return Decision(ADD_OLD_NAME, y.id, None, None)
        if x.valid_name == new:
            return Decision(DONE, y.id, x.id, None)
        return stop(old + " is a not-valid row that points to " + (x.valid_name or "nothing") + ", not to " + new
                    + ": set its valid name in the Geolocale Manager first")
    return stop("AntWeb has no valid country named " + old)


# ---------------------------------------------------------------- SQL text (no database)

def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def hexed(value):
    return (value or "").encode("utf-8").hex().upper()


def unhex(value):
    return bytes.fromhex(value).decode("utf-8", "replace") if value and value != "NULL" else ""


def exact(column, name):
    """The column holds exactly this name. The tables compare text without capitals and accents (utf8mb3_general_ci),
    so the first part finds the rows by the index and the second keeps those written exactly so."""
    return "(" + column + " = " + sql_string(name) + " and hex(convert(" + column + " using utf8mb4)) = '" + hexed(name) + "')"


class Plan:
    """One rename with what was found for it."""
    def __init__(self, old, new, decision, counts=None, author_image=""):
        self.old, self.new, self.decision = old, new, decision
        self.counts = counts or {}
        self.author_image = author_image

    @property
    def state(self):
        return self.decision.state

    def n(self, key):
        return int(self.counts.get(key, 0))

    def ids(self):
        return [str(i) for i in (self.decision.valid_id, self.decision.alias_id) if i is not None]

    def problem(self):
        """Why nothing is changed for this rename, or None."""
        if self.decision.problem:
            return self.decision.problem
        if self.n("adm1_same_name"):
            return (str(self.n("adm1_same_name")) + " states of the same name exist under " + self.old + " and under " + self.new
                    + ": merge them in the Geolocale Manager first")
        if self.moves_text() is None:
            return ("the name " + self.new + " already has page text (" + str(self.n("text_new")) + " fields), so the page text of "
                    + self.old + " cannot move there: move or clear it first")
        return None

    def work(self):
        """Whether --apply has something to change for this rename."""
        if self.problem():
            return False
        if self.state != DONE:
            return True
        keys = ["old_names", "adm1_parent", "geolocale_country", "specimen_rows", "login_country", "imaging_progress"] + BY_KEY
        return any(self.n(k) for k in keys) or bool(self.moves_text())

    def moves_text(self):
        """True: the page text moves to the new name. False: there is none, or it stays. None: it cannot move (a problem)."""
        if not self.n("object_edit") and not self.n("object_hist"):
            return False
        if self.n("same_name_other"):
            return False      # another place has the old name: the text under that name stays with it (as Edit Geolocale)
        if self.n("text_new"):
            return None if self.state in (RENAME, SWAP) else False
        return True


# Tables the tool changes when they exist (older or test databases may lack the last ones), and the columns it needs.
REQUIRED = {"geolocale": ["id", "name", "georank", "is_valid", "valid_name", "parent", "country", "is_island", "region", "bioregion",
                         "alt_bioregion", "source"],
            "specimen": ["code", "country"]}
OPTIONAL = ["login_country", "object_edit", "object_hist", "place_genus_curation", "place_specimen_name", "imaging_progress"]
BY_KEY = ["place_genus_curation", "place_specimen_name"]
# Tables that hold a country name and are left as they are: (table, column, why).
LEFT = [("taxon", "country", "AntCat's text, written again by the daily update"),
        ("homonym", "country", "AntCat's text, written again by the daily update"),
        ("project", "country", "not read by AntWeb"),
        ("un_country", "name", "an old list, read by one administrator query"),
        ("country", "name", "an old list, not read by AntWeb"),
        ("taxon_country", "country", "an old table, not read by AntWeb"),
        ("antwiki_taxon_country", "country", "an old table, not read by AntWeb"),
        ("country_bioregion", "country", "an old table, not read by AntWeb")]

SCHEMA_SQL = ("select table_name, ifnull(engine, ''), '' from information_schema.tables where table_schema = database() "
              "union all select table_name, '', column_name from information_schema.columns where table_schema = database() "
              "and table_name in ('geolocale', 'specimen')")


class Schema:
    def __init__(self, rows):
        self.engines = {}
        self.columns = collections.defaultdict(set)
        for table, engine, column in rows:
            if column:
                self.columns[table.lower()].add(column.lower())
            else:
                self.engines[table.lower()] = engine

    def has(self, table, column=None):
        return table in self.engines and (column is None or column.lower() in self.columns[table])

    def missing(self):
        return [t + "." + c for t, cols in REQUIRED.items() for c in cols if not self.has(t, c)]

    def not_transactional(self):
        return sorted(t for t in list(REQUIRED) + OPTIONAL if self.has(t) and self.engines[t].lower() != "innodb")


def rows_sql(renames, schema):
    """The country rows named exactly as an old or a new name."""
    names = sorted({n for pair in renames for n in pair})
    author = "hex(ifnull(authorimage, ''))" if schema.has("geolocale", "authorimage") else "''"
    return ("select id, hex(name), is_valid, hex(ifnull(valid_name, '')), ifnull(is_island, 0), " + author
            + " from geolocale where georank = 'country' and (" + " or ".join(exact("name", n) for n in names) + ")")


def count_selects(plan, schema):
    """(key, a select that gives one number) for one rename: what would change, and what is only shown."""
    o, n = plan.old, plan.new
    ids = ", ".join(plan.ids()) or "0"
    out = [
        ("old_names", "select count(*) from geolocale where georank = 'country' and " + exact("valid_name", o) + " and id not in (" + ids + ")"),
        ("adm1_parent", "select count(*) from geolocale where georank = 'adm1' and " + exact("parent", o)),
        ("geolocale_country", "select count(*) from geolocale where " + exact("country", o)),
        ("geolocale_rows", "select count(*) from geolocale where " + geolocale_condition(plan)),
        ("adm1_under_new", "select count(*) from geolocale where georank = 'adm1' and " + exact("parent", n)),
        ("adm1_same_name", "select count(*) from geolocale a join geolocale b on b.name = a.name where a.georank = 'adm1' and b.georank = 'adm1' and "
         + exact("a.parent", o) + " and " + exact("b.parent", n)),
        ("same_name_other", "select count(*) from geolocale where name = " + sql_string(o) + " and id not in (" + ids + ")"),
        ("specimen_country", "select count(*) from specimen where " + exact("country", o)),
        ("specimen_rows", "select count(*) from specimen where " + specimen_condition(plan, schema)),
        ("specimen_new", "select count(*) from specimen where " + exact("country", n)),
        ("specimen_other_spelling", "select count(*) from specimen where country = " + sql_string(o) + " and not " + exact("country", o)),
    ]
    if schema.has("specimen", "island_country"):
        out.append(("specimen_island", "select count(*) from specimen where " + exact("island_country", o)))
    if schema.has("login_country"):
        out.append(("login_country", "select count(*) from login_country where " + exact("country", o)))
    for t in ("object_edit", "object_hist"):
        if schema.has(t):
            out.append((t, "select count(*) from " + t + " where " + exact("object_key", o)))
    if schema.has("object_edit"):
        out.append(("text_new", "select count(*) from object_edit where object_key = " + sql_string(n) + " and not " + exact("object_key", o)))
    for t in BY_KEY:
        if schema.has(t):
            out.append((t, "select count(*) from " + t + " where list_type = 'country' and " + exact("list_key", o)))
    if schema.has("imaging_progress") and plan.decision.valid_id is not None:
        out.append(("imaging_progress", "select count(*) from imaging_progress where place_type = 'country' and place_key = '"
                    + str(plan.decision.valid_id) + "' and not " + exact("place_name", n)))
    for table, column, _ in LEFT:
        if schema.has(table):
            out.append(("left_" + table, "select count(*) from " + table + " where " + exact(column, o)))
    if schema.has("geolocale_taxon") and plan.state == SWAP:
        out.append(("other_row_list", "select count(*) from geolocale_taxon where geolocale_id = " + str(plan.decision.alias_id)))
    return out


def counts_sql(plan, schema):
    return " union all ".join("select '" + key + "', (" + select + ")" for key, select in count_selects(plan, schema))


def geolocale_condition(plan):
    """The place rows a rename changes: the country's row, the row of the other name, old names, states."""
    parts = ["(georank = 'country' and " + exact("valid_name", plan.old) + ")",
             "(georank = 'adm1' and " + exact("parent", plan.old) + ")", exact("country", plan.old)]
    if plan.state in (RENAME, SWAP):
        parts.insert(0, "id in (" + ", ".join(plan.ids()) + ")")
    return "(" + " or ".join(parts) + ")"


def specimen_condition(plan, schema):
    parts = [exact("country", plan.old)]
    if schema.has("specimen", "island_country"):
        parts.append(exact("island_country", plan.old))
    return "(" + " or ".join(parts) + ")"


def any_of(conditions):
    return "(" + " or ".join(conditions) + ")" if conditions else "(1 = 0)"


def backup_sql(table, plans, schema, stamp):
    """The record table and one backup table per table a run changes. Statements, in order."""
    out = ["create table " + table + " (old_name varchar(250) not null, new_name varchar(250) not null, state varchar(20) not null, "
           "valid_id int not null, alias_id int null, alias_added tinyint not null default 0, text_moved tinyint not null default 0, "
           "specimens int not null default 0, made varchar(20) not null) default charset = utf8mb4"]
    for p in plans:
        out.append("insert into " + table + " (old_name, new_name, state, valid_id, alias_id, specimens, made) values ("
                   + sql_string(p.old) + ", " + sql_string(p.new) + ", '" + p.state + "', " + str(p.decision.valid_id) + ", "
                   + ("null" if p.decision.alias_id is None else str(p.decision.alias_id)) + ", " + str(p.n("specimen_rows")) + ", '" + stamp + "')")
    out.append("create table " + table + "_geolocale as select id, name, valid_name, parent, country, is_valid from geolocale where "
               + any_of([geolocale_condition(p) for p in plans]))
    island = ", island_country" if schema.has("specimen", "island_country") else ""
    out.append("create table " + table + "_specimen as select code, country" + island + " from specimen where "
               + any_of([specimen_condition(p, schema) for p in plans]))
    if schema.has("login_country"):
        out.append("create table " + table + "_login_country as select * from login_country where "
                   + any_of([exact("country", name) for p in plans for name in (p.old, p.new)]))
    for t in ("object_edit", "object_hist"):
        if schema.has(t):
            out.append("create table " + table + "_" + t + " as select * from " + t + " where "
                       + any_of([exact("object_key", p.old) for p in plans if p.moves_text()]))
    for t in BY_KEY:
        if schema.has(t):
            out.append("create table " + table + "_" + t + " as select * from " + t + " where list_type = 'country' and "
                       + any_of([exact("list_key", name) for p in plans for name in (p.old, p.new)]))
    return out


def backup_tables(table, schema):
    return [table] + [table + "_" + t for t in ["geolocale", "specimen"] + [o for o in OPTIONAL[:5] if schema.has(o)]]


def record_where(table, plan):
    return " where hex(old_name) = '" + hexed(plan.old) + "'"


def apply_sql(table, plans, schema):
    """Every change of a run, in one transaction: statements, in order."""
    out = ["start transaction"]
    for p in plans:
        o, n = sql_string(p.old), sql_string(p.new)
        vid = str(p.decision.valid_id)
        aid = None if p.decision.alias_id is None else str(p.decision.alias_id)
        # the place rows; the names are unique per parent and rank, so the row of the new name steps aside first
        if p.state == SWAP:
            out.append("update geolocale set name = concat('" + TEMP_NAME + "', id) where id = " + aid)
        if p.state in (RENAME, SWAP):
            out.append("update geolocale set name = " + n + " where id = " + vid)
        if p.state == SWAP:
            out.append("update geolocale set name = " + o + ", valid_name = " + n + ", is_valid = 0 where id = " + aid)
        if p.state in (RENAME, ADD_OLD_NAME):
            out.append("insert into geolocale (name, georank, is_valid, valid_name, parent, region, bioregion, alt_bioregion, source) "
                       "select " + o + ", 'country', 0, " + n + ", parent, region, bioregion, alt_bioregion, 'country_rename' from geolocale where id = " + vid)
            out.append("update " + table + " set alias_id = last_insert_id(), alias_added = 1" + record_where(table, p))
        out.append("update geolocale set valid_name = " + n + " where georank = 'country' and " + exact("valid_name", p.old) + " and id <> " + vid)
        out.append("update geolocale set parent = " + n + " where georank = 'adm1' and " + exact("parent", p.old))
        out.append("update geolocale set country = " + n + " where " + exact("country", p.old))
        # the specimens
        out.append("update specimen set country = " + n + " where " + exact("country", p.old))
        if schema.has("specimen", "island_country"):
            out.append("update specimen set island_country = " + n + " where " + exact("island_country", p.old))
        # the curators assigned to the country; one who has both names keeps one row
        if schema.has("login_country"):
            out.append("delete a from login_country a join login_country b on b.login_id = a.login_id where "
                       + exact("a.country", p.old) + " and " + exact("b.country", p.new))
            out.append("update login_country set country = " + n + " where " + exact("country", p.old))
        # the page text and its history; created = created keeps the dates of the texts
        if p.moves_text():
            for t in ("object_edit", "object_hist"):
                if schema.has(t):
                    out.append("update " + t + " set object_key = " + n + ", created = created where " + exact("object_key", p.old))
            out.append("update " + table + " set text_moved = 1" + record_where(table, p))
        # counts kept by country name: a row the new name already has stays, the daily update counts both again
        for t in BY_KEY:
            if schema.has(t):
                out.append("update ignore " + t + " set list_key = " + n + " where list_type = 'country' and " + exact("list_key", p.old))
                out.append("delete from " + t + " where list_type = 'country' and " + exact("list_key", p.old))
        if schema.has("imaging_progress"):
            out.append("update imaging_progress set place_name = " + n + " where place_type = 'country' and place_key = '" + vid + "'")
    out.append("commit")
    return out


RECORD_SQL = "select hex(old_name), hex(new_name), state, valid_id, ifnull(alias_id, 0), alias_added, text_moved from "


def read_record(rows):
    """The renames of a run, from its record table: dicts."""
    return [dict(old=unhex(r[0]), new=unhex(r[1]), state=r[2], valid_id=int(r[3]), alias_id=int(r[4]), alias_added=r[5] == "1",
                 text_moved=r[6] == "1") for r in rows]


def rollback_sql(table, record, schema):
    """Put back what a run changed: statements, in one transaction."""
    restore = ("update geolocale g join " + table + "_geolocale b on b.id = g.id set g.name = b.name, g.valid_name = b.valid_name, "
               "g.parent = b.parent, g.country = b.country, g.is_valid = b.is_valid")
    added = [str(r["alias_id"]) for r in record if r["alias_added"] and r["alias_id"]]
    swapped = [str(r["alias_id"]) for r in record if r["state"] == SWAP and r["alias_id"]]
    out = ["start transaction"]
    if added:
        out.append("delete from geolocale where id in (" + ", ".join(added) + ") and is_valid = 0 and georank = 'country'")
    if swapped:      # the row that took the old name steps aside, the country takes its old name, then the row takes its own
        out.append("update geolocale set name = concat('" + TEMP_NAME + "', id) where id in (" + ", ".join(swapped) + ")")
        out.append(restore + " where g.id not in (" + ", ".join(swapped) + ")")
        out.append(restore + " where g.id in (" + ", ".join(swapped) + ")")
    else:
        out.append(restore)
    island = schema.has("specimen", "island_country")
    for r in record:
        o, n = sql_string(r["old"]), sql_string(r["new"])
        out.append("update specimen s join " + table + "_specimen b on b.code = s.code set s.country = b.country where "
                   + exact("b.country", r["old"]) + " and " + exact("s.country", r["new"]))
        if island:
            out.append("update specimen s join " + table + "_specimen b on b.code = s.code set s.island_country = b.island_country where "
                       + exact("b.island_country", r["old"]) + " and " + exact("s.island_country", r["new"]))
        if schema.has("login_country") and schema.has(table + "_login_country"):
            out.append("delete from login_country where " + exact("country", r["old"]) + " or " + exact("country", r["new"]))
        if r["text_moved"]:
            for t in ("object_edit", "object_hist"):
                if schema.has(t):
                    out.append("update " + t + " set object_key = " + o + ", created = created where " + exact("object_key", r["new"]))
        for t in BY_KEY:
            if schema.has(t) and schema.has(table + "_" + t):
                out.append("delete from " + t + " where list_type = 'country' and (" + exact("list_key", r["old"]) + " or " + exact("list_key", r["new"]) + ")")
        if schema.has("imaging_progress") and r["state"] in (RENAME, SWAP):
            out.append("update imaging_progress set place_name = " + o + " where place_type = 'country' and place_key = '" + str(r["valid_id"]) + "'")
    for t in ["login_country"] + BY_KEY:
        if schema.has(t) and schema.has(table + "_" + t):
            out.append("insert into " + t + " select * from " + table + "_" + t)
    out.append("commit")
    return out


# ---------------------------------------------------------------- the report (no database)

CHANGE_LINES = [   # (count key, table, what changes)
    ("old_names", "geolocale", "other old names of the country: their valid name"),
    ("adm1_parent", "geolocale", "its states: their parent"),
    ("geolocale_country", "geolocale", "places with the name in their country column (its states)"),
    ("specimen_country", "specimen", "specimens: country"),
    ("specimen_island", "specimen", "specimens: island_country"),
    ("login_country", "login_country", "curators assigned to the country"),
    ("object_edit", "object_edit", "page text fields"),
    ("object_hist", "object_hist", "earlier versions of the page text"),
    ("place_genus_curation", "place_genus_curation", "genus rows of the Not curated counts (by country name)"),
    ("place_specimen_name", "place_specimen_name", "specimen names counted for the country (by country name)"),
    ("imaging_progress", "imaging_progress", "the name on the imaging progress page"),
]


def place_root(name):
    """Geolocale.getRoot: the folder of a place's author image, web/speciesList/<root>/."""
    return name.replace(" ", "").lower()


def rename_lines(p):
    """The lines of one rename in the report, and its rows for counts.csv: (table, what, rows)."""
    lines, rows = [p.old + "  ->  " + p.new], []
    problem = p.problem()
    if problem:
        lines += ["  NOT RENAMED: " + problem, ""]
        return lines, rows
    lines.append("  " + STATE_TEXT[p.state] + " (the country's row: id " + str(p.decision.valid_id)
                 + ("; the other row: id " + str(p.decision.alias_id) if p.decision.alias_id else "") + ")")

    def add(table, what, n):
        rows.append((table, what, n))
        lines.append("  " + format(n, ",").rjust(9) + "  " + table.ljust(22) + what)
    if p.state in (RENAME, SWAP):
        add("geolocale", "the country's row: its name", 1)
    if p.state == SWAP:
        add("geolocale", "the row AntWeb has for the new name: it takes the old name, not valid, valid name the new one", 1)
    if p.state in (RENAME, ADD_OLD_NAME):
        add("geolocale", "a row added for the old name: not valid, valid name the new one", 1)
    for key, table, what in CHANGE_LINES:
        if key not in p.counts:
            continue
        n = p.n(key)
        if key in ("object_edit", "object_hist") and n and not p.moves_text():
            what += " (NOT moved: " + ("another place is also named " + p.old if p.n("same_name_other") else "the new name has page text") + ")"
        add(table, what, n)
    notes = []
    if p.n("specimen_new"):
        notes.append(format(p.n("specimen_new"), ",") + " specimens are stored under " + p.new + " already; they are the country's from now on")
    if p.n("specimen_other_spelling"):
        notes.append(format(p.n("specimen_other_spelling"), ",") + " specimens write " + p.old
                     + " with other capitals or accents: not changed here (tools/place/place_fix.py)")
    if p.n("adm1_under_new"):
        notes.append(format(p.n("adm1_under_new"), ",") + " states are filed under " + p.new + " already; they stay")
    if p.n("other_row_list"):
        notes.append("the other row (id " + str(p.decision.alias_id) + ") has " + format(p.n("other_row_list"), ",")
                     + " species list rows of its own: they stay with that row, which no page shows as a country")
    if p.author_image and p.state in (RENAME, SWAP):
        notes.append("the country page has an author image (" + p.author_image + "): copy the folder web/speciesList/" + place_root(p.old)
                     + "/ to web/speciesList/" + place_root(p.new) + "/ on the image storage")
    left = [(t, why, p.n("left_" + t)) for t, _, why in LEFT if p.n("left_" + t)]
    for t, why, n in left:
        notes.append(format(n, ",") + " rows of " + t + " name " + p.old + ": not changed (" + why + ")")
    lines += ["     note: " + t for t in notes] + [""]
    return lines, rows


def report_text(when, commit, plans, table=None, left_after=None):
    ok = [p for p in plans if not p.problem()]
    lines = ["AntWeb country rename, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")", "",
             "Renames: " + str(len(plans)) + "; possible: " + str(len(ok)) + "; specimens that change: "
             + format(sum(p.n("specimen_rows") for p in ok), ","), ""]
    for p in plans:
        lines += rename_lines(p)[0]
    if table:
        lines += ["Applied. Record and backup tables: " + table + " and " + table + "_...",
                  "Put the old names back with:  python3 country_rename.py --rollback " + table, ""]
    if left_after is not None:
        lines += ["Check after the change: renames not done or rows still under an old name: " + str(left_after)
                  + (" (as expected)" if left_after == 0 else "  <-- NOT ZERO: run the report again and read it"), ""]
    return "\n".join(lines)


RELOAD_TEXT = ("NOW reload the names AntWeb keeps in memory: sign in as an administrator, open the Technical page (technical.do),\n"
               "\"Reload the in-memory lists\": \"countries and adm1s (geolocales)\", then \"logins\". Or restart AntWeb.\n"
               "Until then the country pages count no specimens and an upload stores the name AntWeb has in memory.\n"
               "The daily update then counts the countries again.\n")


# ---------------------------------------------------------------- the database

class SqlError(Exception):
    pass


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def run_sql(project, sql, container):
    """Rows of one statement or of the last of several (tab separated, no header). The first statement that fails
    stops the rest; an open transaction is then undone when the connection closes."""
    user, password = db_login(project)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4", "--batch", "--quick", "-N"]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=password), timeout=3600)
    if r.returncode != 0:
        raise SqlError(r.stderr.decode("utf-8", "replace").strip())
    return [line.split("\t") for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def read_first_line(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.readline().strip()
    except OSError:
        return ""


def default_project():
    for p in ("/root/antweb", "/root/antweb-next"):
        if os.path.exists(os.path.join(p, "etc", "db.properties")) and not os.path.islink(p):
            return p
    return "/root/antweb"


def busy_answer(url):
    """AntWeb's answer to "is an upload or a data job running?": "idle", "busy: ..." or "" when it does not answer."""
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.read(200).decode("utf-8", "replace").strip()
    except (OSError, ValueError):
        return ""


def load(project, container, renames):
    """(schema, plans): what is found for each rename now."""
    schema = Schema(run_sql(project, SCHEMA_SQL, container))
    missing = schema.missing()
    if missing:
        sys.exit("This database is not AntWeb's: missing " + ", ".join(missing))
    rows = run_sql(project, rows_sql(renames, schema), container)
    places = [Row(r[0], unhex(r[1]), r[2], unhex(r[3]), r[4], unhex(r[5])) for r in rows]
    plans = []
    for old, new in renames:
        d = decide(places, old, new)
        p = Plan(old, new, d)
        if not d.problem:
            p.counts = {r[0]: int(r[1]) for r in run_sql(project, counts_sql(p, schema), container)}
            p.author_image = next((r.author_image for r in places if r.id == d.valid_id), "")
        plans.append(p)
    return schema, plans


def left_over(plans):
    """After a run: renames that are not done, or rows still under an old name."""
    n = 0
    for p in plans:
        if p.problem() or p.state != DONE:
            n += 1
        else:
            n += sum(p.n(k) for k in ("old_names", "adm1_parent", "geolocale_country", "specimen_rows", "login_country") + tuple(BY_KEY))
            n += p.n("object_edit") + p.n("object_hist") if p.moves_text() else 0
    return n


def chosen_renames(args):
    renames = list(RENAMES)
    for text in args.rename or []:
        if "=" not in text:
            sys.exit("--rename: write it as \"Old name=New name\": " + repr(text))
        old, new = (t.strip() for t in text.split("=", 1))
        renames = [r for r in renames if r[0] != old] + [(old, new)]
    if args.only:
        wanted = [n.strip() for n in args.only.split(",") if n.strip()]
        unknown = [n for n in wanted if n not in [r[0] for r in renames]]
        if unknown:
            sys.exit("--only: not an old name of the list: " + ", ".join(unknown) + ". The list: " + ", ".join(r[0] for r in renames))
        renames = [r for r in renames if r[0] in wanted]
    return renames


def main(argv=None):
    a = argparse.ArgumentParser(description="Give a country its current name everywhere AntWeb keeps the name.")
    a.add_argument("--apply", action="store_true", help="make the changes (default: report only)")
    a.add_argument("--rollback", metavar="TABLE", help="put back the names kept in this run's backup tables")
    a.add_argument("--only", default=None, help="only these renames: old names, comma separated (default: all of the list)")
    a.add_argument("--rename", action="append", metavar="OLD=NEW", help="a rename that is not in the list (may be repeated)")
    a.add_argument("--even-if-busy", action="store_true", help="--apply although AntWeb says an upload or a data job is running")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--app", default="http://127.0.0.1:8081", help="AntWeb's local address, asked whether it is busy")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-country-rename/<date>-<time>)")
    args = a.parse_args(argv)
    project = args.project or default_project()
    now = datetime.datetime.now()
    stamp = now.strftime("%Y%m%d_%H%M")

    try:
        if args.rollback:
            return rollback(args, project)
        return report_or_apply(args, project, now, stamp)
    except SqlError as e:
        sys.exit("mysql failed: " + str(e))


def rollback(args, project):
    table = args.rollback
    if not TABLE_NAME.match(table):
        sys.exit("--rollback: not a record table name: " + repr(table))
    schema = Schema(run_sql(project, SCHEMA_SQL, args.container))
    if not schema.has(table):
        sys.exit("--rollback: no table " + table + " in this database")
    record = read_record(run_sql(project, RECORD_SQL + table, args.container))
    run_sql(project, ";\n".join(rollback_sql(table, record, schema)), args.container)
    print("Rolled back: " + "; ".join(r["new"] + " is " + r["old"] + " again" for r in record) + ". The tables " + table + "... are kept.\n")
    print(RELOAD_TEXT)
    return 0


def report_or_apply(args, project, now, stamp):
    renames = chosen_renames(args)
    folder = args.out or os.path.join("/root/antweb-country-rename", now.strftime("%Y-%m-%d-%H%M"))
    os.makedirs(folder, exist_ok=True)
    commit = read_first_line(os.path.join(project, "etc", "git-commit"))
    schema, plans = load(project, args.container, renames)
    todo = [p for p in plans if p.work()]
    table, left_after, note = None, None, ""

    if args.apply and todo:
        loose = schema.not_transactional()
        if loose:
            sys.exit("Nothing changed: these tables cannot take part in a transaction (not InnoDB): " + ", ".join(loose))
        busy = busy_answer(args.app + "/util.do?action=isBusy")
        if busy.startswith("busy") and not args.even_if_busy:
            sys.exit("Nothing changed: AntWeb says \"" + busy + "\". Run it again when the upload or the daily update has ended.")
        if not busy:
            note = "AntWeb did not answer at " + args.app + " (not running?): it was not asked whether an upload is running.\n"
        table = "country_rename_backup_" + stamp
        if run_sql(project, "show tables like '" + table + "%'", args.container):
            sys.exit("Table " + table + " exists already: wait a minute and run it again.")
        tables = backup_tables(table, schema)
        drop = "drop table if exists " + ", ".join(tables)
        try:
            run_sql(project, ";\n".join(backup_sql(table, todo, schema, now.strftime("%Y-%m-%d %H:%M"))), args.container)
            kept = run_sql(project, "select (select count(*) from " + table + "_specimen), (select count(*) from " + table + "_geolocale)", args.container)[0]
            expected = [sum(p.n("specimen_rows") for p in todo), sum(p.n("geolocale_rows") for p in todo)]
            if [int(kept[0]), int(kept[1])] != expected:
                run_sql(project, drop, args.container)
                sys.exit("Nothing changed: the backup holds " + kept[0] + " specimens and " + kept[1] + " places, the report counted "
                         + str(expected[0]) + " and " + str(expected[1]) + " (an upload ran in between?). Run it again.")
            run_sql(project, "alter table " + table + "_specimen add primary key (code); alter table " + table + "_geolocale add primary key (id)",
                    args.container)
            run_sql(project, ";\n".join(apply_sql(table, todo, schema)), args.container)
        except SqlError as e:
            run_sql(project, drop, args.container)
            sys.exit("Nothing changed (the transaction was undone and the backup tables dropped). mysql said: " + str(e))
        _, after = load(project, args.container, [(p.old, p.new) for p in todo])
        left_after = left_over(after)

    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, plans, table=table, left_after=left_after) + note
    if not args.apply:
        text += "Report only: nothing was changed. Run with --apply to make the changes.\n"
    elif not todo:
        text += "Nothing to change.\n"
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    with open(os.path.join(folder, "counts.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["old_name", "new_name", "state", "table", "what", "rows"])
        for p in plans:
            if p.problem():
                w.writerow([p.old, p.new, "not renamed: " + p.problem(), "", "", ""])
            for t, what, n in rename_lines(p)[1]:
                w.writerow([p.old, p.new, p.state, t, what, n])
    print(text)
    if table:
        print(RELOAD_TEXT)
    print("Files: " + folder)
    return 1 if left_after else 0


if __name__ == "__main__":
    sys.exit(main())

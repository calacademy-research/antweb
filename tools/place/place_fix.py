#!/usr/bin/env python3
"""Place fix: bring the country and bioregion stored with each specimen up to today's upload rule (2026-11-10).

Why (Brian Fisher, 2026-11-10: "we need to address these data deficiencies ... the data I send will be worked on
for a year - it has to be reproducible"): a specimen's country and bioregion are set once, when its file is
uploaded, and never looked at again. Records uploaded when a state still had the text "none" as its second
bioregion, or before a state was linked to its valid name, kept a blank bioregion for good (production
2026-11-10: about 5,840 specimens with no bioregion, 2,508 of them from one upload of 2024-09-25), and a country
spelled "México" stays outside every search for Mexico.

What it does: it reads every (country, state, bioregion) combination of the specimen table and the place table
(geolocale), works out for each combination what an upload TODAY would store, and lists the differences.

  python3 place_fix.py                    report only (the default; nothing is changed)
  python3 place_fix.py --apply            make the changes (a backup table is written first)
  python3 place_fix.py --countries "United States,Canada,Mexico" [--apply]    only specimens of these countries
  python3 place_fix.py --rollback place_fix_20261110_0915     put the old values back, where nothing newer is there

The rule, the same as the upload's (SpecimenUploadParse.parseLine, GeolocaleMgr.getGeolocaleBioregion):

  country    a name AntWeb lists as an old name of a valid country becomes the valid name (USA: United States).
             New here, and to go into the upload as well: a name that differs from exactly one valid country
             only by capitals, accents or blanks becomes that country (México: Mexico).
  bioregion  the bioregion of the valid state (the state itself, or the valid name an old name points to) when
             that state has exactly one; otherwise the country's when the country has exactly one; otherwise the
             stored value stays as it is (United States or Mexico with no state: the tool cannot know).
             "none", "null" and blank count as no bioregion, as in Geolocale.noBioregion. The valid countries
             and states are AntWeb's definition (bioregionCountryList.do); a row for an old state name that
             points to no valid state defines nothing, in the upload (GeolocaleMgr.getAdm1 returns valid rows
             only) and, since version 3, here.
  never      the state is not changed (the upload stores the state as the curator wrote it); a specimen filed
             under an island country (island_country) is not touched; nothing is guessed from locality text
             or coordinates.

It writes one folder (default /root/antweb-place-fix/<date>-<time>/):

  report.txt        totals by rule and by country, before and (after --apply) after.
  changes.csv       one line per combination that changes: country, state, stored bioregion, specimens, the new
                    country, the new bioregion, the reason.
  not_fixed.csv     combinations the rule cannot settle, with the reason (no country; country not known to
                    AntWeb; no state or unknown state in a country with two bioregions; a bioregion that is not
                    one of the country's). These are for the curators.
  specimens.csv     with --apply: one line per specimen changed (code, old and new country, old and new
                    bioregion, reason), read back from the backup table.

--apply: (1) writes the combinations to table place_fix_<date>_<time>_map; (2) writes every specimen that will
change, with its old values, to table place_fix_<date>_<time> (the backup, primary key code); (3) stops, before
changing anything, when the number of specimens in the backup differs from the number the report counted (an
upload ran in between: run it again); (4) updates the specimens from the backup table; (5) runs the report
again and expects no change left. Do not run it while an upload or the daily update is running. The counts on
place and bioregion pages follow with the next daily update.
  Nothing half-made: when a step before the update fails (the tables cannot be made or filled, the counts differ),
  the tables of this run are dropped and no specimen has been touched.
  Only rows that are still as the backup has them (since version 4): the update changes a specimen only when its
  country, state and bioregion are still, byte for byte, what the backup table recorded. A specimen that an upload
  loaded again between the backup and the update (the CAS TaxonWorks upload does every day) is left as it is,
  marked applied = 0 in the backup table and counted in the report ("N changed since the backup, left as they
  are"); specimens.csv says which.

Rollback: --rollback <table> sets country and bioregion back to the old values for the specimens that still hold
the values this run gave them. A specimen that has other values by now (a later upload, a curator) is left alone:
newer data is never overwritten. The run counts the specimens restored and those left, and lists the latter in
rollback_skipped.csv (code, the old values, the values the run applied, the values there now, why) next to its
report.txt, in a folder of its own. The values the run applied are read from the backup table (its columns
set_country, new_country, set_bioregion, new_bioregion: every version of this tool wrote them); a backup table
without those columns is joined to its _map table by the old country, state and bioregion. The tables of
versions 1 to 3 have no column applied: every row of them was updated. The tables stay until an administrator
drops them.

Bytes: stored place text is compared and carried as hex of its UTF-8 form, hex(convert(column using utf8mb4)),
whatever the column's character set and collation ("México" is not "Mexico"; a latin1 column gives the same bytes
as a utf8mb4 one), as tools/export/specimen_extract.py does.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. Tests: tests/unit/test_place_fix.py.
"""
import argparse
import collections
import csv
import datetime
import os
import re
import subprocess
import sys
import unicodedata

VERSION = "4"
TABLE_NAME = re.compile(r"^place_fix_\d{8}_\d{4}$")
Db = collections.namedtuple("Db", "project container database")


class SqlFailed(Exception):
    """mysql refused a statement; the message is its error text."""


# ---------------------------------------------------------------- the rule (no database)

def clean_bioregion(value):
    """Geolocale.noBioregion: blank, "none" and "null" mean no bioregion."""
    v = (value or "").strip()
    return None if v == "" or v.lower() == "none" or v == "null" else v


def normal(name):
    """A name without capitals, accents or extra blanks: México and  mexico  are the same."""
    decomposed = unicodedata.normalize("NFKD", name or "")
    return " ".join("".join(c for c in decomposed if not unicodedata.combining(c)).casefold().split())


class Place:
    def __init__(self, name, georank, parent, is_valid, valid_name, bioregion, alt_bioregion, is_island):
        self.name = name
        self.georank = georank
        self.parent = parent
        self.is_valid = str(is_valid) == "1"
        self.valid_name = clean_bioregion(valid_name)   # the text "none" is stored here too
        self.bioregion = clean_bioregion(bioregion)
        self.alt_bioregion = clean_bioregion(alt_bioregion)
        self.is_island = str(is_island) == "1"

    def one_bioregion(self):
        """Geolocale.getTheOneBioregion: the bioregion, unless the place has a second one."""
        return None if self.alt_bioregion else self.bioregion


class Places:
    def __init__(self, rows):
        self.countries = {}
        self.adm1s = {}
        self.adm1_names = collections.defaultdict(list)
        self.twice = set()
        for p in rows:
            if p.georank != "adm1" and (p.georank == "country" or p.is_island):
                if p.name in self.countries:
                    self.twice.add(("country", p.name, ""))
                self.countries[p.name] = p
            elif p.georank == "adm1":
                if (p.name, p.parent) in self.adm1s:
                    self.twice.add(("adm1", p.name, p.parent))
                self.adm1s[(p.name, p.parent)] = p
                self.adm1_names[p.name].append(p)
        self.by_normal = collections.defaultdict(set)
        for p in self.countries.values():
            if p.is_valid and not p.is_island:
                self.by_normal[normal(p.name)].add(p.name)

    def valid_country(self, name):
        """GeolocaleMgr.getValidCountry: the row itself when valid, else the row its valid name points to."""
        p = self.countries.get(name)
        if p is None:
            return None
        if p.is_valid:
            return p
        target = self.countries.get(p.valid_name) if p.valid_name else None
        return target if target is not None and target.is_valid else None

    def spelled_like(self, name):
        """The one valid country that differs only by capitals, accents or blanks; None when none or several."""
        names = self.by_normal.get(normal(name), set())
        return self.countries[next(iter(names))] if len(names) == 1 else None

    def valid_island(self, name):
        p = self.countries.get(name) if name else None
        return p if p is not None and p.is_island and p.is_valid else None

    def valid_adm1(self, adm1, country):
        """GeolocaleMgr.getValidAdm1."""
        p = self.adm1s.get((adm1, country))
        if p is None:
            return None
        if p.is_valid:
            return p
        target = self.adm1s.get((p.valid_name, country)) if p.valid_name else None
        return target if target is not None and target.is_valid else None


Decision = collections.namedtuple("Decision", "new_country new_bioregion reasons problem")
KEEP = object()


def decide(places, country, adm1, bioregion, island):
    """What an upload today would store for a specimen with these stored values.

    new_country / new_bioregion are KEEP when the stored value stays. problem is a text for the curators' list
    when the rule cannot settle the place, else None.
    """
    country = country or None
    adm1 = adm1 or None
    stored = clean_bioregion(bioregion)
    if island:
        return Decision(KEEP, KEEP, [], None)
    if country is None:
        return Decision(KEEP, KEEP, [], "no country")
    if places.valid_island(adm1) or places.valid_island(country):
        return Decision(KEEP, KEEP, [], "an island AntWeb treats as a country: upload the file again")

    reasons = []
    new_country = KEEP
    row = places.valid_country(country)
    if row is not None and row.name != country:
        new_country = row.name
        reasons.append("country: old name of " + row.name)
    elif row is None:
        row = places.spelled_like(country)
        if row is not None:
            new_country = row.name
            reasons.append("country: spelling of " + row.name)
    if row is None:
        return Decision(KEEP, KEEP, [], "country not known to AntWeb")

    # Only a valid state defines a bioregion (the list on bioregionCountryList.do). A row for an old name that
    # points to no valid state is not a definition: such rows were filled with the country's first bioregion when
    # they were made (production 2026-11-10: Irian Jaya, Sulawesi and Moluccas say Indomalaya, AntWeb's map says
    # Australasia), and some hold stray values (a Lithuanian old name says Oceania).
    state = places.valid_adm1(adm1, row.name) if adm1 else None
    old_name = adm1 is not None and state is None and (adm1, row.name) in places.adm1s
    derived = state.one_bioregion() if state is not None else None
    source = "state" if derived else None
    if derived is None:
        derived = row.one_bioregion()
        source = "country" if derived else None

    if derived is not None:
        if derived != stored:
            reasons.append("bioregion: the " + source + "'s (" + ("was " + stored if stored else "was blank") + ")")
            return Decision(new_country, derived, reasons, None)
        return Decision(new_country, KEEP, reasons, None)

    # the country (and the state, if any) has two bioregions: the stored value stays
    problem = None
    if stored is None:
        if adm1 is None:
            problem = "no bioregion: no state, and the country has two bioregions"
        elif old_name:
            problem = "no bioregion: an old state name with no valid name in AntWeb, and the country has two bioregions"
        elif state is None:
            problem = "no bioregion: state not known to AntWeb, and the country has two bioregions"
        else:
            problem = "no bioregion: the state has two bioregions"
    else:
        allowed = {row.bioregion, row.alt_bioregion}
        if state is not None:
            allowed |= {state.bioregion, state.alt_bioregion}
        if stored not in allowed:
            problem = "bioregion " + stored + " is not one of the " + ("state's or " if state is not None else "") + "country's"
    return Decision(new_country, KEEP, reasons, problem)


class Combination:
    def __init__(self, country, adm1, bioregion, island, specimens):
        self.country, self.adm1, self.bioregion, self.island, self.specimens = country, adm1, bioregion, island, specimens


def plan(places, combinations, only=None):
    """(changes, not_fixed): lists of (combination, decision). only: a set of valid country names; a change is
    then kept only when the specimen's country (after the change) is one of them. not_fixed is never limited."""
    changes, not_fixed = [], []
    for c in combinations:
        d = decide(places, c.country, c.adm1, c.bioregion, c.island)
        if d.new_country is not KEEP or d.new_bioregion is not KEEP:
            if only is None or (c.country if d.new_country is KEEP else d.new_country) in only:
                changes.append((c, d))
        if d.problem:
            not_fixed.append((c, d))
    return changes, not_fixed


def totals(changes, not_fixed):
    by_reason = collections.Counter()
    by_country = collections.Counter()
    for c, d in changes:
        for r in d.reasons:
            by_reason[re.sub(r" \(was .*\)$", "", r) if r.startswith("bioregion") else r] += c.specimens
        by_country[(c.country if d.new_country is KEEP else d.new_country, "" if d.new_bioregion is KEEP else d.new_bioregion)] += c.specimens
    by_problem = collections.Counter()
    for c, d in not_fixed:
        by_problem[d.problem] += c.specimens
    return by_reason, by_country, by_problem


def report_text(when, commit, changes, not_fixed, after=None, table=None, only=None, left=None):
    by_reason, by_country, by_problem = totals(changes, not_fixed)
    changed = sum(c.specimens for c, _ in changes)
    lines = ["AntWeb place fix, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")", ""]
    if only is not None:
        lines += ["Limited to these countries (--countries): " + ", ".join(sorted(only)) + ". Other countries are not changed.", ""]
    lines += [
             "Specimens whose country or bioregion differs from what an upload today would store: " + format(changed, ","),
             "  in " + format(len(changes), ",") + " combinations of country, state and bioregion (changes.csv)", "",
             "By reason (a specimen with a new country and a new bioregion is counted in both):"]
    lines += ["  " + format(n, ",").rjust(9) + "  " + r for r, n in by_reason.most_common()] or ["  none"]
    lines += ["", "By country and new bioregion:"]
    lines += ["  " + format(n, ",").rjust(9) + "  " + c + (" -> " + b if b else " (country only)") for (c, b), n in by_country.most_common(60)] or ["  none"]
    lines += ["", "Not settled by the rule, for the curators (not_fixed.csv): " + format(sum(c.specimens for c, _ in not_fixed), ",")]
    lines += ["  " + format(n, ",").rjust(9) + "  " + p for p, n in by_problem.most_common()] or ["  none"]
    if table:
        lines += ["", "Applied. Backup table: " + table + " (every changed specimen with its old values; specimens.csv).",
                  "Put the old values back with:  python3 place_fix.py --rollback " + table]
        if left is not None:
            lines += ["Specimens changed: " + format(changed - left, ",") + ". " + format(left, ",") + " changed since the backup, left as they are"
                      + ("." if not left else " (an upload loaded them again between the backup and the update; specimens.csv says which, column applied). "
                         "Run the tool again for them.")]
    if after is not None:
        lines += ["", "Check after the change: specimens that still differ from the rule: " + format(after, ",")
                  + (" (as expected)" if after == 0 else "  <-- NOT ZERO: read changes_after.csv")]
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------- the database

def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def hexed(value):
    return (value or "").encode("utf-8").hex().upper()


def unhex(value):
    return bytes.fromhex(value).decode("utf-8", "replace") if value and value != "NULL" else ""


def hx(column):
    """The bytes of a text column as hex of its UTF-8 form, whatever the column's character set and collation; null as ''."""
    return "hex(convert(ifnull(" + column + ", '') using utf8mb4))"


PLACES_SQL = ("select " + hx("name") + ", georank, " + hx("parent") + ", is_valid, " + hx("valid_name") + ", "
              + hx("bioregion") + ", " + hx("alt_bioregion") + ", ifnull(is_island, 0) "
              "from geolocale where georank in ('country', 'adm1') or is_island = 1")

ISLAND = "(s.island_country is not null and s.island_country <> '')"
COMBINATIONS_SQL = ("select " + hx("s.country") + ", " + hx("s.adm1") + ", " + hx("s.bioregion") + ", " + ISLAND + ", count(*) "
                    "from specimen s group by 1, 2, 3, 4")


def read_places(rows):
    return Places([Place(unhex(r[0]), r[1], unhex(r[2]), r[3], unhex(r[4]), unhex(r[5]), unhex(r[6]), r[7]) for r in rows])


def read_combinations(rows):
    return [Combination(unhex(r[0]), unhex(r[1]), unhex(r[2]), r[3] == "1", int(r[4])) for r in rows]


def map_sql(table, changes):
    """The work table of combinations: the stored values as hex of their UTF-8 bytes (exact, accents and capitals
    included). The hex columns are text: a country of 64 letters is up to 512 hex digits, a state of 128 up to
    1,024, and a column too short made --apply fail half way through the inserts. The key is on their beginnings."""
    out = ["create table " + table + "_map (hc text not null, ha text not null, hb text not null, "
           "set_country tinyint not null, new_country varchar(250), set_bioregion tinyint not null, new_bioregion varchar(250), "
           "reason text, specimens int not null, key (hc(100), ha(100))) default charset = utf8mb4"]
    for c, d in changes:
        out.append("insert into " + table + "_map values ('" + hexed(c.country) + "', '" + hexed(c.adm1) + "', '" + hexed(c.bioregion) + "', "
                   + ("0, null" if d.new_country is KEEP else "1, " + sql_string(d.new_country)) + ", "
                   + ("0, null" if d.new_bioregion is KEEP else "1, " + sql_string(d.new_bioregion)) + ", "
                   + sql_string("; ".join(d.reasons)) + ", " + str(c.specimens) + ")")
    return out


def backup_sql(table):
    return ("create table " + table + " as select s.code, s.country as old_country, s.adm1, s.bioregion as old_bioregion, "
            "m.set_country, m.new_country, m.set_bioregion, m.new_bioregion, m.reason "
            "from specimen s join " + table + "_map m on m.hc = " + hx("s.country") + " and m.ha = " + hx("s.adm1") + " "
            "and m.hb = " + hx("s.bioregion") + " where not " + ISLAND)


def backup_key_sql(table):
    """The backup's key, and applied: 1 once the update has changed the specimen, 0 for a row it left alone."""
    return "alter table " + table + " add primary key (code), add column applied tinyint not null default 0"


def same(column, value):
    """The specimen's column holds, byte for byte, this value (a column of the backup table)."""
    return hx(column) + " = " + hx(value)


def update_sql(table):
    """The update: only the specimens that are still as the backup recorded them (country, state, bioregion, and not
    filed under an island since). A row that an upload loaded again in between keeps what it has; applied stays 0.
    Both sides of each if() are converted to utf8mb4: the new value comes from the work table and the old one from
    the specimen table, whose collations may differ."""
    return ("update specimen s join " + table + " f on f.code = s.code "
            "set s.country = if(f.set_country = 1, convert(f.new_country using utf8mb4), convert(s.country using utf8mb4)), "
            "s.bioregion = if(f.set_bioregion = 1, convert(f.new_bioregion using utf8mb4), convert(s.bioregion using utf8mb4)), f.applied = 1 "
            "where " + same("s.country", "f.old_country") + " and " + same("s.adm1", "f.adm1") + " and "
            + same("s.bioregion", "f.old_bioregion") + " and not " + ISLAND)


NEW_COLUMNS = ("set_country", "new_country", "set_bioregion", "new_bioregion")


def applied_values(table, columns):
    """(more of the from clause, the country the run gave a row, the bioregion it gave: both as hex) for a backup
    table with these columns. The backup table has the new values itself (every version so far); one that has not
    is joined to its _map table by the old country, state and bioregion as hex. The choice between the new and the
    old value is made between their hex forms: the two columns can have different collations (the new values come
    from the work table, the old ones from the specimen table), and hex text has none to disagree about."""
    if all(c in columns for c in NEW_COLUMNS):
        join, m = "", "f"
    else:
        join = (" join " + table + "_map m on m.hc = " + hx("f.old_country") + " and m.ha = " + hx("f.adm1")
                + " and m.hb = " + hx("f.old_bioregion"))
        m = "m"
    return (join, "if(" + m + ".set_country = 1, " + hx(m + ".new_country") + ", " + hx("f.old_country") + ")",
            "if(" + m + ".set_bioregion = 1, " + hx(m + ".new_bioregion") + ", " + hx("f.old_bioregion") + ")")


def still_applied(table, columns):
    """(from clause after the specimen, condition): the specimen still holds the country and bioregion the run gave it
    (and the run did change it: applied = 1, where the table has that column)."""
    join, country, bioregion = applied_values(table, columns)
    condition = hx("s.country") + " = " + country + " and " + hx("s.bioregion") + " = " + bioregion
    return join, condition + (" and f.applied = 1" if "applied" in columns else "")


def rollback_sql(table, columns=NEW_COLUMNS):
    """The rollback: the old values back, only where the specimen still holds what the run gave it. Then the number
    of specimens restored."""
    join, condition = still_applied(table, columns)
    return ("update specimen s join " + table + " f on f.code = s.code" + join + " "
            "set s.country = f.old_country, s.bioregion = f.old_bioregion where " + condition + "; select row_count()")


def rollback_skipped_sql(table, columns=NEW_COLUMNS):
    """The rows a rollback leaves alone, read before it: code, the old values, the values the run applied, whether the
    specimen is gone, the values there now, whether the run ever changed the row."""
    join, country, bioregion = applied_values(table, columns)
    _, condition = still_applied(table, columns)
    return ("select f.code, " + hx("f.old_country") + ", " + hx("f.old_bioregion") + ", " + country + ", " + bioregion + ", "
            "s.code is null, " + hx("s.country") + ", " + hx("s.bioregion") + ", " + ("f.applied" if "applied" in columns else "1") + " "
            "from " + table + " f" + join + " left join specimen s on s.code = f.code "
            "where s.code is null or not (" + condition + ") order by f.code")


def skipped_reason(row):
    """Why a rollback leaves a row of rollback_skipped_sql alone."""
    code, old_country, old_bioregion, new_country, new_bioregion, gone, country, bioregion, applied = row
    if gone == "1":
        return "the specimen is no longer in AntWeb"
    if applied != "1":
        return "never changed by this run (it had changed between the backup and the update)"
    if (country, bioregion) == (old_country, old_bioregion):
        return "holds the old values already"
    return "has other values now than the run gave it: a later upload or a curator; left as it is"


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def run_sql(db, sql):
    """Rows of one statement or of the last of several (tab separated, no header). Raises SqlFailed."""
    user, password = db_login(db.project)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", db.container, "mysql", "-u", user, db.database,
           "--default-character-set=utf8mb4", "--batch", "--quick", "-N"]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=password), timeout=3600)
    if r.returncode != 0:
        raise SqlFailed(r.stderr.decode("utf-8", "replace").strip())
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


def show(value):
    return "(keep)" if value is KEEP else value


def write_lists(folder, changes, not_fixed, name="changes.csv"):
    with open(os.path.join(folder, name), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["country", "state", "stored_bioregion", "specimens", "new_country", "new_bioregion", "reason"])
        for c, d in sorted(changes, key=lambda x: (-x[0].specimens, x[0].country, x[0].adm1)):
            w.writerow([c.country, c.adm1, c.bioregion, c.specimens, show(d.new_country), show(d.new_bioregion), "; ".join(d.reasons)])
    if not_fixed is None:
        return
    with open(os.path.join(folder, "not_fixed.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["country", "state", "stored_bioregion", "specimens", "why"])
        for c, d in sorted(not_fixed, key=lambda x: (-x[0].specimens, x[0].country, x[0].adm1)):
            w.writerow([c.country, c.adm1, c.bioregion, c.specimens, d.problem])


def load(db):
    places = read_places(run_sql(db, PLACES_SQL))
    combinations = read_combinations(run_sql(db, COMBINATIONS_SQL))
    return places, combinations


def drop_tables(db, table):
    """The tables of a run that did not get as far as the update: dropped, whatever state they are in."""
    try:
        run_sql(db, "drop table if exists " + table + "; drop table if exists " + table + "_map")
        return "The tables of this run were dropped."
    except SqlFailed as e:
        return "The tables of this run could NOT be dropped (" + str(e) + "): drop " + table + " and " + table + "_map by hand."


def rollback(db, table, folder, when):
    """Puts the old values back where the specimen still holds what the run gave it; the report text."""
    columns = {r[0] for r in run_sql(db, "show columns from " + table)}
    if not all(c in columns for c in NEW_COLUMNS) and not run_sql(db, "show tables like '" + table + "\\_map'"):
        sys.exit("--rollback: table " + table + " does not say what the run applied (no columns " + ", ".join(NEW_COLUMNS)
                 + ") and there is no table " + table + "_map: nothing was changed.")
    total = int(run_sql(db, "select count(*) from " + table)[0][0])
    skipped = run_sql(db, rollback_skipped_sql(table, columns))
    restored = int(run_sql(db, rollback_sql(table, columns))[0][0])
    reasons = collections.Counter(skipped_reason(r) for r in skipped)
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "rollback_skipped.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["code", "old_country", "old_bioregion", "applied_country", "applied_bioregion", "country_now", "bioregion_now", "why"])
        for r in skipped:
            w.writerow([r[0]] + [unhex(x) for x in r[1:5]] + ["" if r[5] == "1" else unhex(r[6]), "" if r[5] == "1" else unhex(r[7]), skipped_reason(r)])
    lines = ["AntWeb place fix, rollback of " + table + ", " + when + " (tool version " + VERSION + ")", "",
             "Specimens in the backup table: " + format(total, ","),
             "Restored (country and bioregion as they were before the fix): " + format(restored, ","),
             "Left as they are, because they no longer hold the values the fix gave them (rollback_skipped.csv): " + format(len(skipped), ",")]
    lines += ["  " + format(n, ",").rjust(9) + "  " + reason for reason, n in reasons.most_common()]
    if restored + len(skipped) != total:
        lines += ["", "NOTE: restored and left do not add up to the table (" + format(restored + len(skipped), ",") + " of " + format(total, ",")
                  + "): specimens changed while the rollback ran. Run it again; it restores only what still holds the fix's values."]
    lines += ["", "Table " + table + " kept."]
    text = "\n".join(lines) + "\n"
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    return text


def main(argv=None):
    try:
        return run(argv)
    except SqlFailed as e:
        sys.exit("mysql failed: " + str(e))


def run(argv=None):
    a = argparse.ArgumentParser(description="Bring each specimen's country and bioregion up to today's upload rule.")
    a.add_argument("--apply", action="store_true", help="make the changes (default: report only)")
    a.add_argument("--rollback", metavar="TABLE", help="put back the old values kept in this backup table, where the specimen "
                   "still holds what the run gave it")
    a.add_argument("--countries", default=None, help="change only specimens of these valid countries, comma separated "
                   "(for example \"United States,Canada,Mexico\"); default: all countries")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--database", default="ant", help="the database (default ant; the tests use their own)")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-place-fix/<date>-<time>, for a rollback ...-rollback)")
    args = a.parse_args(argv)
    if not re.match(r"^[A-Za-z0-9_]+$", args.database):
        sys.exit("--database: not a database name: " + repr(args.database))
    db = Db(args.project or default_project(), args.container, args.database)
    now = datetime.datetime.now()
    stamp = now.strftime("%Y%m%d_%H%M")

    if args.rollback:
        if not TABLE_NAME.match(args.rollback):
            sys.exit("--rollback: not a backup table name: " + repr(args.rollback))
        folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M") + "-rollback")
        print(rollback(db, args.rollback, folder, now.strftime("%Y-%m-%d %H:%M")))
        print("Files: " + folder)
        return 0

    folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M"))
    os.makedirs(folder, exist_ok=True)
    commit = read_first_line(os.path.join(db.project, "etc", "git-commit"))
    places, combinations = load(db)
    only = None
    if args.countries:
        only = {n.strip() for n in args.countries.split(",") if n.strip()}
        unknown = sorted(n for n in only if n not in places.countries or not places.countries[n].is_valid)
        if unknown:
            sys.exit("--countries: not a valid AntWeb country: " + ", ".join(unknown))
    changes, not_fixed = plan(places, combinations, only)
    write_lists(folder, changes, not_fixed)
    expected = sum(c.specimens for c, _ in changes)
    table, after, left = None, None, None

    if args.apply and changes:
        table = "place_fix_" + stamp
        if run_sql(db, "show tables like '" + table + "%'"):
            sys.exit("Table " + table + " exists already: wait a minute and run it again.")
        # Up to the update nothing is changed in the specimens: a step that fails here leaves no table behind.
        try:
            run_sql(db, ";\n".join(map_sql(table, changes)))
            run_sql(db, backup_sql(table))
            kept = int(run_sql(db, "select count(*) from " + table)[0][0])
            if kept == expected:
                run_sql(db, backup_key_sql(table))
        except SqlFailed as e:
            sys.exit("Nothing changed: the backup could not be made (" + str(e) + "). " + drop_tables(db, table))
        if kept != expected:
            sys.exit("Nothing changed: the backup holds " + format(kept, ",") + " specimens, the report counted " + format(expected, ",")
                     + " (an upload ran in between?). Run it again. " + drop_tables(db, table))
        run_sql(db, update_sql(table))
        left = int(run_sql(db, "select count(*) from " + table + " where applied = 0")[0][0])
        rows = run_sql(db, "select code, " + hx("old_country") + ", " + hx("new_country") + ", set_country, " + hx("old_bioregion") + ", "
                       + hx("new_bioregion") + ", set_bioregion, " + hx("reason") + ", applied from " + table + " order by code")
        with open(os.path.join(folder, "specimens.csv"), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["code", "old_country", "new_country", "old_bioregion", "new_bioregion", "reason", "applied"])
            for r in rows:
                w.writerow([r[0], unhex(r[1]), unhex(r[2]) if r[3] == "1" else unhex(r[1]), unhex(r[4]),
                            unhex(r[5]) if r[6] == "1" else unhex(r[4]), unhex(r[7]),
                            "yes" if r[8] == "1" else "no: changed since the backup, left as it is"])
        places2, combinations2 = load(db)
        changes2, _ = plan(places2, combinations2, only)
        after = sum(c.specimens for c, _ in changes2)
        if after:
            write_lists(folder, changes2, None, name="changes_after.csv")

    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, changes, not_fixed, after=after, table=table, only=only, left=left)
    if not args.apply:
        text += "\nReport only: nothing was changed. Run with --apply to make the changes.\n"
    if places.twice:
        text += "\nPlace rows that exist twice (the later one is used): " + "; ".join(sorted(" ".join(t).strip() for t in places.twice)) + "\n"
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    print("Files: " + folder)
    return 1 if after else 0


if __name__ == "__main__":
    sys.exit(main())

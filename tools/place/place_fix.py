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
  python3 place_fix.py --rollback place_fix_20261110_0915     put the old values back

The rule, the same as the upload's (SpecimenUploadParse.parseLine, GeolocaleMgr.getGeolocaleBioregion):

  country    a name AntWeb lists as an old name of a valid country becomes the valid name (USA: United States).
             New here, and to go into the upload as well: a name that differs from exactly one valid country
             only by capitals, accents or blanks becomes that country (México: Mexico).
  bioregion  the bioregion of the state (of its valid name when the state is filed under an old name) when the
             state has exactly one; otherwise the country's when the country has exactly one; otherwise the
             stored value stays as it is (United States or Mexico with no state: the tool cannot know).
             "none", "null" and blank count as no bioregion, as in Geolocale.noBioregion.
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

Rollback: --rollback <table> sets country and bioregion of every specimen in that backup table back to the old
values. The tables stay until an administrator drops them.

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

VERSION = "1"
TABLE_NAME = re.compile(r"^place_fix_\d{8}_\d{4}$")


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

    state = places.valid_adm1(adm1, row.name) if adm1 else None
    if state is None and adm1:
        state = places.adm1s.get((adm1, row.name))     # an old name with no valid name: its own row
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


def plan(places, combinations):
    """(changes, not_fixed): lists of (combination, decision)."""
    changes, not_fixed = [], []
    for c in combinations:
        d = decide(places, c.country, c.adm1, c.bioregion, c.island)
        if d.new_country is not KEEP or d.new_bioregion is not KEEP:
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


def report_text(when, commit, changes, not_fixed, after=None, table=None):
    by_reason, by_country, by_problem = totals(changes, not_fixed)
    changed = sum(c.specimens for c, _ in changes)
    lines = ["AntWeb place fix, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")", "",
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


PLACES_SQL = ("select hex(name), georank, hex(ifnull(parent, '')), is_valid, hex(ifnull(valid_name, '')), "
              "hex(ifnull(bioregion, '')), hex(ifnull(alt_bioregion, '')), ifnull(is_island, 0) "
              "from geolocale where georank in ('country', 'adm1') or is_island = 1")

ISLAND = "(s.island_country is not null and s.island_country <> '')"
COMBINATIONS_SQL = ("select hex(ifnull(s.country, '')), hex(ifnull(s.adm1, '')), hex(ifnull(s.bioregion, '')), " + ISLAND + ", count(*) "
                    "from specimen s group by 1, 2, 3, 4")


def read_places(rows):
    return Places([Place(unhex(r[0]), r[1], unhex(r[2]), r[3], unhex(r[4]), unhex(r[5]), unhex(r[6]), r[7]) for r in rows])


def read_combinations(rows):
    return [Combination(unhex(r[0]), unhex(r[1]), unhex(r[2]), r[3] == "1", int(r[4])) for r in rows]


def map_sql(table, changes):
    """The work table of combinations: the stored values as hex (exact, accents and capitals included)."""
    out = ["create table " + table + "_map (hc varchar(400) not null, ha varchar(800) not null, hb varchar(300) not null, "
           "set_country tinyint not null, new_country varchar(128), set_bioregion tinyint not null, new_bioregion varchar(128), "
           "reason varchar(300), specimens int not null, key (hc(100), ha(100))) default charset = utf8mb4"]
    for c, d in changes:
        out.append("insert into " + table + "_map values ('" + hexed(c.country) + "', '" + hexed(c.adm1) + "', '" + hexed(c.bioregion) + "', "
                   + ("0, null" if d.new_country is KEEP else "1, " + sql_string(d.new_country)) + ", "
                   + ("0, null" if d.new_bioregion is KEEP else "1, " + sql_string(d.new_bioregion)) + ", "
                   + sql_string("; ".join(d.reasons)) + ", " + str(c.specimens) + ")")
    return out


def backup_sql(table):
    return ("create table " + table + " as select s.code, s.country as old_country, s.adm1, s.bioregion as old_bioregion, "
            "m.set_country, m.new_country, m.set_bioregion, m.new_bioregion, m.reason "
            "from specimen s join " + table + "_map m on m.hc = hex(ifnull(s.country, '')) and m.ha = hex(ifnull(s.adm1, '')) "
            "and m.hb = hex(ifnull(s.bioregion, '')) where not " + ISLAND)


def update_sql(table):
    return ("update specimen s join " + table + " f on f.code = s.code "
            "set s.country = if(f.set_country = 1, f.new_country, s.country), "
            "s.bioregion = if(f.set_bioregion = 1, f.new_bioregion, s.bioregion)")


def rollback_sql(table):
    return ("update specimen s join " + table + " f on f.code = s.code "
            "set s.country = f.old_country, s.bioregion = f.old_bioregion")


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def run_sql(project, sql, container):
    """Rows of one statement or of the last of several (tab separated, no header)."""
    user, password = db_login(project)
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4", "--batch", "--quick", "-N"]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True, env=dict(os.environ, MYSQL_PWD=password), timeout=3600)
    if r.returncode != 0:
        sys.exit("mysql failed: " + r.stderr.decode("utf-8", "replace").strip())
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


def load(project, container):
    places = read_places(run_sql(project, PLACES_SQL, container))
    combinations = read_combinations(run_sql(project, COMBINATIONS_SQL, container))
    return places, combinations


def main(argv=None):
    a = argparse.ArgumentParser(description="Bring each specimen's country and bioregion up to today's upload rule.")
    a.add_argument("--apply", action="store_true", help="make the changes (default: report only)")
    a.add_argument("--rollback", metavar="TABLE", help="put back the old values kept in this backup table")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-place-fix/<date>-<time>)")
    args = a.parse_args(argv)
    project = args.project or default_project()
    now = datetime.datetime.now()
    stamp = now.strftime("%Y%m%d_%H%M")

    if args.rollback:
        if not TABLE_NAME.match(args.rollback):
            sys.exit("--rollback: not a backup table name: " + repr(args.rollback))
        n = run_sql(project, "select count(*) from " + args.rollback, args.container)[0][0]
        run_sql(project, rollback_sql(args.rollback), args.container)
        print("Rolled back: country and bioregion of " + format(int(n), ",") + " specimens are as they were (table " + args.rollback + " kept).")
        return 0

    folder = args.out or os.path.join("/root/antweb-place-fix", now.strftime("%Y-%m-%d-%H%M"))
    os.makedirs(folder, exist_ok=True)
    commit = read_first_line(os.path.join(project, "etc", "git-commit"))
    places, combinations = load(project, args.container)
    changes, not_fixed = plan(places, combinations)
    write_lists(folder, changes, not_fixed)
    expected = sum(c.specimens for c, _ in changes)
    table, after = None, None

    if args.apply and changes:
        table = "place_fix_" + stamp
        if run_sql(project, "show tables like '" + table + "%'", args.container):
            sys.exit("Table " + table + " exists already: wait a minute and run it again.")
        run_sql(project, ";\n".join(map_sql(table, changes)), args.container)
        run_sql(project, backup_sql(table), args.container)
        kept = int(run_sql(project, "select count(*) from " + table, args.container)[0][0])
        if kept != expected:
            run_sql(project, "drop table " + table + "; drop table " + table + "_map", args.container)
            sys.exit("Nothing changed: the backup holds " + format(kept, ",") + " specimens, the report counted " + format(expected, ",")
                     + " (an upload ran in between?). Run it again.")
        run_sql(project, "alter table " + table + " add primary key (code)", args.container)
        run_sql(project, update_sql(table), args.container)
        rows = run_sql(project, "select code, hex(ifnull(old_country, '')), hex(ifnull(new_country, '')), set_country, hex(ifnull(old_bioregion, '')), "
                       "hex(ifnull(new_bioregion, '')), set_bioregion, hex(ifnull(reason, '')) from " + table + " order by code", args.container)
        with open(os.path.join(folder, "specimens.csv"), "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["code", "old_country", "new_country", "old_bioregion", "new_bioregion", "reason"])
            for r in rows:
                w.writerow([r[0], unhex(r[1]), unhex(r[2]) if r[3] == "1" else unhex(r[1]), unhex(r[4]),
                            unhex(r[5]) if r[6] == "1" else unhex(r[4]), unhex(r[7])])
        places2, combinations2 = load(project, args.container)
        changes2, _ = plan(places2, combinations2)
        after = sum(c.specimens for c, _ in changes2)
        if after:
            write_lists(folder, changes2, None, name="changes_after.csv")

    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, changes, not_fixed, after=after, table=table)
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

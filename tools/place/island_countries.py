#!/usr/bin/env python3
"""Island countries: file the specimens of an island that is its own country under the island (2026-11-11).

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent. Test: would you put it in the same key as the parent?" (The
Nearctic project exported all ants of the United States, and Hawaii had to be removed by hand.) This round: the places
flagged as islands, Hawaii and Galapagos Islands. They are stored as Reunion, Mayotte and Rodrigues already are:
specimen.country = the island's own name.

Until 2026-11-11 the upload stored an island's specimen with the political parent as its country (United States,
Ecuador) and the island only in specimen.island_country. The upload now stores the island's name in both columns
(SpecimenUploadParse, RecordPlace); this tool brings the specimens already stored to the same form, once:

  python3 island_countries.py                 report only (the default; nothing is changed)
  python3 island_countries.py --apply         make the changes (backup tables are written first)
  python3 island_countries.py --rollback island_countries_backup_20261111_1700     put the old values back

What changes (--apply):

  specimens   country = island_country where island_country is the name of a valid place flagged as an island
              (geolocale: georank country, is_island 1, is_valid 1) and the country is another; region and
              subregion become the island row's (where that row has them), as the upload stores them. Nothing
              else: island_country stays (it marks an island's specimen this round), adm1 and bioregion stay.
  old names   the place row "Rodrigues" of Mauritius (an adm1 row that is not valid and names no valid place)
              gets the valid name Rodrigues, so an upload that writes Mauritius + adm1 Rodrigues is filed under
              the country Rodrigues (ALIASES below). Only when the row is as described; otherwise it is reported.

What the report shows besides: the islands with their rows; the specimens to move by island, present country and
upload group; every old state name that files an upload's record under a country (the place rows the upload's
second rule reads: an adm1 row that is not valid and whose valid name is a valid country, not a state of its own
country), each marked when it is not one of the names this round expects (EXPECTED_OLD_NAMES): stop and ask then; specimens the tool does not change (a specimen under the parent whose adm1 is an old name of the
island and that has no island_country: its group's next upload files it); for each island the specimens whose
coordinates lie outside the island's bounding box (from now on the upload checks an island's specimen against the
island's box, not the parent's: these would be red-flagged at their next upload: correct the box first); and, for a
curator, the species on the political parent's list that have specimens on the island, none in the parent, and a
list row that does not come from specimens (curator, AntCat, ...): such rows stay when the lists are built again
(parent_list_rows.csv). Rows that do come from specimens leave the parent's list with the next daily update.

It writes one folder (default /root/antweb-island-countries/<date>-<time>/): report.txt, to_move.csv (one line per
island, present country and group), parent_list_rows.csv, outside_box.csv, and with --apply specimens.csv (every
specimen changed with its old and new values).

--apply: (1) asks AntWeb whether an upload or the daily update is running and stops if so, or if AntWeb does not
answer (--even-if-busy goes on in both cases: only when AntWeb is known to be stopped); (2) writes the backup
table island_countries_backup_<date>_<time> (every specimen that will change, with its old country, region and
subregion and the new ones) and ..._geolocale (the old-name rows it will change, with the valid name it gives them);
(3) stops, before changing
anything, when the backup holds another number of specimens than the report counted (an upload ran in between: run
it again); (4) changes the specimens and the rows in one transaction, a specimen only while it still has the country
the backup recorded; (5) reads again and expects nothing left. Safe to run twice: a second run finds nothing to
move and writes no table.

The AntWeb code that files islands under their own name must be live first: the CAS TaxonWorks upload loads every
CAS specimen again each morning, and with the old code it would put them back under the parent.

Afterwards: when an old-name row changed, reload the places AntWeb keeps in memory (as an administrator,
util.do?action=reloadAntwebMgr&name=geolocale). The species lists and every number of the islands and their parents
are built again by the next daily update (set 2: lists from specimens, counts, endemic and introduced; set 4: images,
views, imaging progress; set 5: maps, and it reloads the places); until then the pages show yesterday's numbers. When
the sets are run by hand instead, reload the places once more at the end: the pages read their numbers from the
places held in memory (staging, 2026-11-11: the Galapagos Islands page said Endemic 0 until that reload).

Rollback: --rollback <table> puts back country, region and subregion of every specimen of that table that still has
the values this tool gave it, and the old valid name of every row that still has the valid name this tool gave it
(a row an administrator changed since is left as it is and named). The tables stay until an administrator drops
them. Roll the code back first (or the next CAS upload moves the CAS specimens again).

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on a
command line. How to run it: docs/runbook.md, "Islands that are their own country". Tests:
tests/unit/test_island_countries.py.
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

VERSION = "2"
TABLE_NAME = re.compile(r"^island_countries_backup_\d{8}_\d{4}$")

# Old-name rows to give a valid name: (adm1 name, its country, the valid country it now names).
ALIASES = [("Rodrigues", "Mauritius", "Rodrigues")]


# ---------------------------------------------------------------- the rules (no database)

def blank(value):
    """The place table holds the text "none" (and "null") for no value."""
    v = (value or "").strip().lower()
    return v in ("", "none", "null")


class Island:
    """A valid place flagged as an island (geolocale: georank country, is_island 1, is_valid 1)."""
    def __init__(self, id, name, parent, region, subregion, country, bioregion, box):
        self.id, self.name = int(id), name
        self.subregion = "" if blank(subregion) else subregion       # as the upload stores it (geolocale.subregion)
        self.region = "" if blank(region) else region
        self.place_parent = "" if blank(parent) else parent          # the subregion its page hangs under
        self.political_parent = "" if blank(country) or country == name else country
        self.bioregion = "" if blank(bioregion) else bioregion
        self.box = "" if blank(box) else box


def numbers(text):
    """The numbers of a list "a, b, c, d" (parentheses ignored), as LocalityOverview.getDoubleListVal reads them."""
    out = []
    for part in (text or "").replace("(", "").replace(")", "").split(","):
        try:
            out.append(float(part.strip()))
        except ValueError:
            out.append(0.0)
    return (out + [0.0, 0.0, 0.0, 0.0])[:4]


def within_box(box, lat, lon):
    """LocalityOverview.isWithinBounds: the box is "left, bottom, right, top"; no box (four zeros) refuses nothing; 0.2
    degrees of grace; a box whose left is greater than its right goes around the 180th meridian."""
    left, bottom, right, top = numbers(box)
    if left == 0.0 and bottom == 0.0 and right == 0.0 and top == 0.0:
        return True
    left, bottom, right, top = left - 0.2, bottom - 0.2, right + 0.2, top + 0.2
    if not (bottom <= lat <= top):
        return False
    if left <= right:
        return left <= lon <= right
    return lon >= left or lon <= right


def alias_state(row, target):
    """What to do with an old-name row. row: (id, is_valid, valid_name) or None; target: (is_valid,) of the country it
    should name, or None. Returns (action, text): action "set", "done" or None (nothing to do, text says why)."""
    if row is None:
        return None, "AntWeb has no such row"
    if target is None or str(target[0]) != "1":
        return None, "AntWeb has no valid country of that name"
    if str(row[1]) == "1":
        return None, "the row is a valid state: an administrator decides (Geolocale Manager)"
    return ("set", "its valid name will be set") if blank(row[2]) else (None, "it already names " + row[2])


# ---------------------------------------------------------------- SQL text (no database)

def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def hx(column):
    """The bytes of a text column as hex of its UTF-8 form, whatever its character set and collation; null as ''."""
    return "hex(convert(ifnull(" + column + ", '') using utf8mb4))"


def unhex(value):
    return bytes.fromhex(value).decode("utf-8", "replace") if value and value != "NULL" else ""


ISLAND_ROW = "g.georank = 'country' and g.is_island = 1 and g.is_valid = 1"
# an island's specimen: island_country is the island's name, letter for letter
OF_ISLAND = "s.island_country = g.name and " + hx("s.island_country") + " = " + hx("g.name")
TO_MOVE = OF_ISLAND + " and not (s.country <=> g.name)"

ISLANDS_SQL = ("select g.id, " + hx("g.name") + ", " + hx("g.parent") + ", " + hx("g.region") + ", " + hx("g.subregion") + ", "
               + hx("g.country") + ", " + hx("g.bioregion") + ", "
               + hx("if(coalesce(g.bounding_box_fixed, '') <> '', g.bounding_box_fixed, g.bounding_box)")
               + " from geolocale g where " + ISLAND_ROW + " order by g.name")

TO_MOVE_SQL = ("select " + hx("g.name") + ", " + hx("s.country") + ", ifnull(s.access_group, 0), " + hx("ag.name") + ", count(*)"
               " from specimen s join geolocale g on " + TO_MOVE + " and " + ISLAND_ROW
               + " left join ant_group ag on ag.id = s.access_group group by 1, 2, 3, 4 order by 1, 5 desc")

FILED_SQL = ("select " + hx("g.name") + ", count(*), sum(s.country <=> g.name) from specimen s join geolocale g on " + OF_ISLAND
             + " and " + ISLAND_ROW + " group by 1")

# country = the island but no island_country: cannot come from an upload; only counted
NO_MARKER_SQL = ("select " + hx("g.name") + ", count(*) from specimen s join geolocale g on s.country = g.name and " + ISLAND_ROW
                 + " where coalesce(s.island_country, '') = '' group by 1")

# under another country with an adm1 that is the island's name or an old name of it, and no island_country
BY_ADM1_SQL = ("select " + hx("s.country") + ", " + hx("s.adm1") + ", " + hx("g.name") + ", count(*) from specimen s"
               " join geolocale a on a.georank = 'adm1' and a.name = s.adm1 and a.parent = s.country and coalesce(a.is_valid, 0) = 0"
               " join geolocale g on g.name = a.valid_name and g.georank = 'country' and g.is_valid = 1"
               " where coalesce(s.island_country, '') = '' and not (s.country <=> g.name)"
               # an old state name whose valid name is a state of the same country is an ordinary old name, not a country
               " and not exists (select 1 from geolocale v where v.georank = 'adm1' and v.name = a.valid_name and v.parent = a.parent and v.is_valid = 1)"
               " group by 1, 2, 3 order by 4 desc")

# Every old state name that files a record under a country (RecordPlace, rule 2): an adm1 row that is not valid, whose
# valid name is a valid country other than its own, and not a valid state of its own country.
OLD_NAME_ROWS_SQL = ("select a.id, " + hx("a.name") + ", " + hx("a.parent") + ", " + hx("a.valid_name") + " from geolocale a"
                     " join geolocale c on c.georank = 'country' and c.is_valid = 1 and c.name = a.valid_name"
                     " where a.georank = 'adm1' and coalesce(a.is_valid, 0) = 0 and a.valid_name <> a.parent"
                     " and not exists (select 1 from geolocale b where b.georank = 'adm1' and b.parent = a.parent and b.name = a.valid_name"
                     " and b.is_valid = 1) order by a.parent, a.name, a.id")

# (adm1, its country, the country the record is filed under) this round expects; any other row: stop and ask
# (production and staging, 2026-11-11: rows 620, 1017, 2394; Rodrigues once this tool has set its valid name).
EXPECTED_OLD_NAMES = {("Oahu", "United States", "Hawaii"), ("Galapagos", "Ecuador", "Galapagos Islands"),
                      ("Galápagos", "Ecuador", "Galapagos Islands"), ("Rodrigues", "Mauritius", "Rodrigues")}


def unexpected_old_names(rows):
    """The rows (id, adm1, country, valid name) that are not among EXPECTED_OLD_NAMES."""
    return [r for r in rows if (r[1], r[2], r[3]) not in EXPECTED_OLD_NAMES]


COORDINATES_SQL = ("select " + hx("g.name") + ", s.decimal_latitude, s.decimal_longitude, count(*) from specimen s join geolocale g on "
                   + OF_ISLAND + " and " + ISLAND_ROW + " where s.decimal_latitude is not null and s.decimal_longitude is not null"
                   " group by 1, 2, 3")


def parent_list_sql(island, parent):
    """Species and subspecies on the political parent's list with specimens on the island and none in the parent once
    the island's are moved: taxon, status, the list row's source, specimens on the island."""
    i, p = sql_string(island), sql_string(parent)
    return ("select " + hx("gt.taxon_name") + ", ifnull(t.status, ''), ifnull(gt.source, ''), "
            "(select count(*) from specimen s where s.taxon_name = gt.taxon_name and s.island_country = " + i + ") "
            "from geolocale pg join geolocale_taxon gt on gt.geolocale_id = pg.id join taxon t on t.taxon_name = gt.taxon_name "
            "where pg.georank = 'country' and pg.is_valid = 1 and pg.name = " + p + " and t.taxarank in ('species', 'subspecies') "
            "and exists (select 1 from specimen s where s.taxon_name = gt.taxon_name and s.island_country = " + i + ") "
            "and not exists (select 1 from specimen s where s.taxon_name = gt.taxon_name and s.country = " + p + " "
            "and coalesce(s.island_country, '') not in (select g.name from geolocale g where " + ISLAND_ROW + ")) "
            "order by gt.taxon_name")


def alias_sql(adm1, country, target):
    return ("select 'row', a.id, coalesce(a.is_valid, 0), " + hx("a.valid_name") + " from geolocale a where a.georank = 'adm1' and a.name = "
            + sql_string(adm1) + " and a.parent = " + sql_string(country)
            + " union all select 'target', c.id, coalesce(c.is_valid, 0), '' from geolocale c where c.georank = 'country' and c.name = "
            + sql_string(target))


def new_value(island_column, specimen_column):
    """The island row's value where it has one, else what the specimen has."""
    return "if(coalesce(" + island_column + ", '') in ('', 'none', 'null'), " + specimen_column + ", " + island_column + ")"


def backup_sql(table, aliases):
    """The backup tables. Statements, in order. aliases: (row id, valid name it will get)."""
    out = ["create table " + table + " as select s.code, s.country as old_country, s.region as old_region, s.subregion as old_subregion, "
           "s.island_country, s.access_group, g.name as new_country, " + new_value("g.region", "s.region") + " as new_region, "
           + new_value("g.subregion", "s.subregion") + " as new_subregion, 0 as applied "
           "from specimen s join geolocale g on " + TO_MOVE + " and " + ISLAND_ROW,
           "create table " + table + "_geolocale as select id, name, parent, valid_name, cast(null as char(250)) as new_valid_name"
           " from geolocale where id in (" + (", ".join(str(int(i)) for i, _ in aliases) or "0") + ")"]
    for row_id, valid_name in aliases:
        out.append("update " + table + "_geolocale set new_valid_name = " + sql_string(valid_name) + " where id = " + str(int(row_id)))
    return out


def apply_sql(table, aliases):
    """The change, in one transaction. A specimen only while it still has the country the backup recorded (an upload
    may have loaded it again since); aliases: (row id, valid name) to set."""
    out = ["start transaction",
           "update specimen s join " + table + " b on b.code = s.code set s.country = b.new_country, s.region = b.new_region, "
           "s.subregion = b.new_subregion, b.applied = 1 where " + hx("s.country") + " = " + hx("b.old_country")
           + " and " + hx("s.island_country") + " = " + hx("b.island_country")]
    for row_id, valid_name in aliases:
        out.append("update geolocale set valid_name = " + sql_string(valid_name) + " where id = " + str(int(row_id))
                   + " and coalesce(is_valid, 0) = 0 and coalesce(valid_name, '') in ('', 'none', 'null')")
    out.append("commit")
    return out


def given_valid_name_sql(has_column):
    """The valid name this tool gave a row of the ..._geolocale table b: its column new_valid_name; a table written by
    version 1 (staging, 2026-11-11) has no such column, and the name is the one ALIASES gives the row."""
    if has_column:
        return "b.new_valid_name"
    return ("case " + " ".join("when b.name = " + sql_string(adm1) + " and b.parent = " + sql_string(country) + " then " + sql_string(target)
                               for adm1, country, target in ALIASES) + " end")


def rollback_sql(table, has_geolocale, has_new_name=True):
    """The old values back: a specimen only while it still has the country this tool gave it, an old-name row only
    while it still has the valid name this tool gave it (an administrator may have changed it since). The last
    statement counts the specimens that have their old country again."""
    out = ["start transaction",
           "update specimen s join " + table + " b on b.code = s.code set s.country = b.old_country, s.region = b.old_region, "
           "s.subregion = b.old_subregion where b.applied = 1 and " + hx("s.country") + " = " + hx("b.new_country")
           + " and " + hx("s.island_country") + " = " + hx("b.island_country")]
    if has_geolocale:
        out.append("update geolocale g join " + table + "_geolocale b on b.id = g.id set g.valid_name = b.valid_name where "
                   + hx("g.valid_name") + " = " + hx(given_valid_name_sql(has_new_name)))
    out += ["commit", "select count(*) from specimen s join " + table + " b on b.code = s.code where b.applied = 1 and "
            + hx("s.country") + " = " + hx("b.old_country")]
    return out


# ---------------------------------------------------------------- the report (no database)

class Found:
    """Everything a run read."""
    def __init__(self):
        self.islands = []            # Island
        self.to_move = []            # (island, country, group id, group name, n)
        self.filed = {}              # island -> (specimens with its island_country, of those country = island)
        self.no_marker = {}          # island -> n
        self.by_adm1 = []            # (country, adm1, island, n)
        self.outside = []            # (island, lat, lon, n)
        self.parent_rows = []        # (parent, island, taxon, status, source, island specimens)
        self.aliases = []            # (adm1, country, target, row id or None, action, text)
        self.old_name_rows = []      # (id, adm1, country, valid name): the rows the upload's second rule reads

    def moving(self):
        return sum(r[4] for r in self.to_move)


def from_specimens(source):
    """GeolocaleTaxonDb.deleteSource("specimen"): the rows the daily update deletes and builds again from the specimens."""
    return "specimen" in (source or "")


def report_text(when, commit, f, table=None, changed=None, left=None, aliases_set=None):
    lines = ["AntWeb island countries, " + when + " (tool version " + VERSION + ", AntWeb commit " + (commit or "unknown") + ")", "",
             "Brian Fisher, 2026-11-11: an oceanic island with its own ant fauna is its own country; its specimens never",
             "count toward the political parent. An island's specimen is stored with country = the island's own name.", "",
             "Islands (valid places flagged as islands): " + (", ".join(i.name for i in f.islands) or "none")]
    for i in f.islands:
        all_n, done_n = f.filed.get(i.name, (0, 0))
        lines += ["", i.name + " (id " + str(i.id) + "; page under " + (i.place_parent or "nothing") + "; political parent "
                  + (i.political_parent or "none given") + "; bioregion " + (i.bioregion or "none") + ")",
                  "  specimens with island_country " + i.name + ": " + format(all_n, ",") + "; already with country " + i.name + ": "
                  + format(done_n, ",") + "; to move: " + format(sum(r[4] for r in f.to_move if r[0] == i.name), ",")]
        for r in f.to_move:
            if r[0] == i.name:
                lines.append("  " + format(r[4], ",").rjust(9) + "  now country " + (r[1] or "(none)") + ", group "
                             + (r[3] or "(no name)") + " (" + str(r[2]) + ")")
        lines.append("  region and subregion they get: " + (i.region or "(the row has none: unchanged)") + " / "
                     + (i.subregion or "(the row has none: unchanged)"))
        out = sum(r[3] for r in f.outside if r[0] == i.name)
        lines.append("  bounding box used at upload: " + (i.box or "none (nothing is refused)") + "; specimens with coordinates outside it: "
                     + format(out, ",") + (" <-- their next upload would red-flag them: correct the box (Edit Geolocale) first; outside_box.csv" if out else ""))
        if f.no_marker.get(i.name):
            lines.append("  note: " + format(f.no_marker[i.name], ",") + " specimens have country " + i.name
                         + " and no island_country: not changed (their next upload sets it)")
    lines += ["", "Specimens to move in all: " + format(f.moving(), ",")]
    if f.by_adm1:
        lines += ["", "Not changed by this tool: specimens under another country whose adm1 is an old name of a country,",
                  "with no island_country. Their group's next upload files them under that country:"]
        lines += ["  " + format(n, ",").rjust(9) + "  " + c + " / " + a + "  ->  " + i for c, a, i, n in f.by_adm1]
    lines += ["", "Old names:"]
    for adm1, country, target, row_id, action, text in f.aliases:
        lines.append("  " + adm1 + " of " + country + " -> the country " + target + ": " + text + (" (row id " + str(row_id) + ")" if row_id else ""))
    odd = unexpected_old_names(f.old_name_rows)
    lines += ["", "Old state names that file an upload's record under a country (every such row of the place table):"]
    lines += ["  " + adm1 + " of " + country + " -> the country " + valid + " (row id " + str(row_id) + ")"
              + ("" if (row_id, adm1, country, valid) not in odd else "  <-- NOT EXPECTED: stop and ask before the code is deployed")
              for row_id, adm1, country, valid in f.old_name_rows] or ["  none"]
    lines.append("  expected: Oahu -> Hawaii; Galapagos and Galápagos -> Galapagos Islands; Rodrigues -> Rodrigues once its valid name is set"
                 + ("" if not odd else "; " + str(len(odd)) + " NOT EXPECTED"))
    lines += ["", "For a curator: species on a political parent's list with specimens on the island, none in the parent,",
              "and a list row that does not come from specimens (it stays when the lists are built again):"]
    kept = [r for r in f.parent_rows if not from_specimens(r[4])]
    going = [r for r in f.parent_rows if from_specimens(r[4])]
    by = collections.Counter((r[0], r[1], r[4] or "(no source)") for r in kept)
    lines += ["  " + format(n, ",").rjust(9) + "  on the list of " + p + ", island " + i + ", source " + s for (p, i, s), n in sorted(by.items())] or ["  none"]
    lines.append("  (parent_list_rows.csv names them; nothing is deleted)")
    by = collections.Counter((r[0], r[1]) for r in going)
    lines += ["", "Rows of a parent's list that come from specimens only on the island (they leave the list with the next daily update):"]
    lines += ["  " + format(n, ",").rjust(9) + "  on the list of " + p + ", island " + i for (p, i), n in sorted(by.items())] or ["  none"]
    if table:
        lines += ["", "Applied. Backup tables: " + table + " and " + table + "_geolocale.",
                  "Specimens changed: " + format(changed or 0, ",") + ((". " + format(left, ",") + " had changed since the backup and were left as they are; run the tool again for them")
                                                                       if left else "") + ".",
                  "Old-name rows changed: " + str(aliases_set or 0) + ".",
                  "Put the old values back with:  python3 island_countries.py --rollback " + table]
    return "\n".join(lines) + "\n"


RELOAD = "util.do?action=reloadAntwebMgr&name=geolocale"
AFTER_TEXT = ("NOW: 1. Reload the places AntWeb keeps in memory, so the upload knows the changed old-name row: sign in as an\n"
              "        administrator and open " + RELOAD + "\n"
              "     2. The lists and numbers of the islands and their parents are built again by the next daily update (05:00),\n"
              "        which also reloads the places at its end (set 5). Until then the pages show yesterday's numbers and the\n"
              "        count check reports the parents and islands as different.\n"
              "     3. If the sets are run by hand instead (utilData.do?action=set2, set4, placeViewTotals&param=all), reload the\n"
              "        places AGAIN at the end (" + RELOAD + "): the pages read their\n"
              "        numbers from the places held in memory, and show the old ones (Endemic 0) until that reload.\n")


# ---------------------------------------------------------------- the database

class SqlFailed(Exception):
    pass


class Db:
    def __init__(self, project, container, database):
        self.project, self.container, self.database = project, container, database


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def run_sql(db, sql):
    """Rows of one statement or of the last of several (tab separated, no header). The first statement that fails stops
    the rest; an open transaction is then undone when the connection closes."""
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


def busy_answer(url):
    """AntWeb's answer to "is an upload or a data job running?": "idle", "busy: ..." or "" when it does not answer."""
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            return r.read(200).decode("utf-8", "replace").strip()
    except (OSError, ValueError):
        return ""


def load(db):
    f = Found()
    f.islands = [Island(r[0], unhex(r[1]), unhex(r[2]), unhex(r[3]), unhex(r[4]), unhex(r[5]), unhex(r[6]), unhex(r[7]))
                 for r in run_sql(db, ISLANDS_SQL)]
    f.to_move = [(unhex(r[0]), unhex(r[1]), int(r[2]), unhex(r[3]), int(r[4])) for r in run_sql(db, TO_MOVE_SQL)]
    f.filed = {unhex(r[0]): (int(r[1]), int(r[2] or 0)) for r in run_sql(db, FILED_SQL)}
    f.no_marker = {unhex(r[0]): int(r[1]) for r in run_sql(db, NO_MARKER_SQL)}
    f.by_adm1 = [(unhex(r[0]), unhex(r[1]), unhex(r[2]), int(r[3])) for r in run_sql(db, BY_ADM1_SQL)]
    f.old_name_rows = [(int(r[0]), unhex(r[1]), unhex(r[2]), unhex(r[3])) for r in run_sql(db, OLD_NAME_ROWS_SQL)]
    boxes = {i.name: i.box for i in f.islands}
    for r in run_sql(db, COORDINATES_SQL):
        name, lat, lon, n = unhex(r[0]), float(r[1]), float(r[2]), int(r[3])
        if lat == -999.9 or lon == -999.9:       # "not entered" in an upload file: the upload does not check it
            continue
        if not within_box(boxes.get(name, ""), lat, lon):
            f.outside.append((name, lat, lon, n))
    for i in f.islands:
        if i.political_parent:
            for r in run_sql(db, parent_list_sql(i.name, i.political_parent)):
                f.parent_rows.append((i.political_parent, i.name, unhex(r[0]), r[1], r[2], int(r[3])))
    for adm1, country, target in ALIASES:
        rows = run_sql(db, alias_sql(adm1, country, target))
        row = next(((r[1], r[2], unhex(r[3])) for r in rows if r[0] == "row"), None)
        tgt = next(((r[2],) for r in rows if r[0] == "target"), None)
        action, text = alias_state(row, tgt)
        f.aliases.append((adm1, country, target, int(row[0]) if row else None, action, text))
    return f


def write_lists(folder, f):
    with open(os.path.join(folder, "to_move.csv"), "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        w.writerow(["island", "country_now", "group_id", "group", "specimens"])
        w.writerows(f.to_move)
    with open(os.path.join(folder, "parent_list_rows.csv"), "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        w.writerow(["parent_list", "island", "taxon_name", "status", "source", "specimens_on_the_island", "row_stays"])
        for r in f.parent_rows:
            w.writerow(list(r) + ["no: built again from specimens" if from_specimens(r[4]) else "yes: for a curator"])
    with open(os.path.join(folder, "outside_box.csv"), "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        w.writerow(["island", "latitude", "longitude", "specimens"])
        w.writerows(f.outside)


def main(argv=None):
    a = argparse.ArgumentParser(description="File the specimens of an island that is its own country under the island.")
    a.add_argument("--apply", action="store_true", help="make the changes (default: report only)")
    a.add_argument("--rollback", metavar="TABLE", help="put back the old values kept in this backup table")
    a.add_argument("--even-if-busy", action="store_true",
                   help="--apply although AntWeb says an upload or a data job is running, or does not answer")
    a.add_argument("--project", default=None, help="AntWeb project folder (default /root/antweb or /root/antweb-next)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--database", default="ant")
    a.add_argument("--app", default="http://127.0.0.1:8081", help="AntWeb's local address, asked whether it is busy")
    a.add_argument("--out", default=None, help="output folder (default /root/antweb-island-countries/<date>-<time>)")
    args = a.parse_args(argv)
    db = Db(args.project or default_project(), args.container, args.database)
    now = datetime.datetime.now()
    try:
        if args.rollback:
            return rollback(args, db)
        return report_or_apply(args, db, now)
    except SqlFailed as e:
        sys.exit("mysql failed: " + str(e))


def rollback(args, db):
    table = args.rollback
    if not TABLE_NAME.match(table):
        sys.exit("--rollback: not a backup table name: " + repr(table))
    tables = {r[0] for r in run_sql(db, "show tables like '" + table + "%'")}
    if table not in tables:
        sys.exit("--rollback: no table " + table + " in this database")
    kept = int(run_sql(db, "select count(*) from " + table + " where applied = 1")[0][0])
    has_geolocale = table + "_geolocale" in tables
    has_new_name = has_geolocale and bool(run_sql(db, "show columns from " + table + "_geolocale like 'new_valid_name'"))
    back = int(run_sql(db, ";\n".join(rollback_sql(table, has_geolocale, has_new_name)))[0][0])
    rows = (run_sql(db, "select b.id, " + hx("b.name") + ", " + hx("b.parent") + ", " + hx("g.valid_name") + ", "
                    + hx("g.valid_name") + " = " + hx("b.valid_name") + " from " + table + "_geolocale b left join geolocale g on g.id = b.id order by b.id")
            if has_geolocale else [])
    print("Rolled back: " + format(back, ",") + " of the " + format(kept, ",") + " specimens of " + table + " have their old country again"
          + ("" if back >= kept else " (the others had other values than this tool gave them: a later upload; left as they are)") + ".")
    for r in rows:
        print("Old-name row " + unhex(r[1]) + " of " + unhex(r[2]) + " (id " + r[0] + "): "
              + ("its old valid name is back" if r[4] == "1" else "LEFT AS IT IS: its valid name is now " + (repr(unhex(r[3])) if unhex(r[3]) else "empty")
                 + ", not the one this tool gave it (changed since by an administrator)"))
    print("The tables are kept.\n")
    print(AFTER_TEXT)
    return 0


def report_or_apply(args, db, now):
    folder = args.out or os.path.join("/root/antweb-island-countries", now.strftime("%Y-%m-%d-%H%M"))
    os.makedirs(folder, exist_ok=True)
    commit = read_first_line(os.path.join(db.project, "etc", "git-commit"))
    f = load(db)
    write_lists(folder, f)
    to_set = [(r[3], r[2]) for r in f.aliases if r[4] == "set"]
    table, changed, left, note = None, None, None, ""

    if args.apply and (f.moving() or to_set):
        busy = busy_answer(args.app + "/util.do?action=isBusy")
        if busy.startswith("busy") and not args.even_if_busy:
            sys.exit("Nothing changed: AntWeb says \"" + busy + "\". Run it again when the upload or the daily update has ended.")
        answered = busy == "idle" or busy.startswith("busy")      # BusyStatus.current(): "idle" or "busy: ..."
        if not answered and not args.even_if_busy:
            sys.exit("Nothing changed: AntWeb did not say at " + args.app + " whether an upload or the daily update is running"
                     + (" (it answered: " + busy[:60] + ")" if busy else " (no answer)") + ". Run it again when AntWeb is up; "
                     "with --even-if-busy only when AntWeb is stopped on purpose.")
        if not answered:
            note = "AntWeb did not say at " + args.app + " whether an upload is running: --even-if-busy, so the change was made without knowing.\n"
        table = "island_countries_backup_" + now.strftime("%Y%m%d_%H%M")
        if run_sql(db, "show tables like '" + table + "%'"):
            sys.exit("Table " + table + " exists already: wait a minute and run it again.")
        drop = "drop table if exists " + table + ", " + table + "_geolocale"
        try:
            run_sql(db, ";\n".join(backup_sql(table, to_set)))
            kept = int(run_sql(db, "select count(*) from " + table)[0][0])
            if kept != f.moving():
                run_sql(db, drop)
                sys.exit("Nothing changed: the backup holds " + format(kept, ",") + " specimens, the report counted " + format(f.moving(), ",")
                         + " (an upload ran in between?). Run it again.")
            run_sql(db, "alter table " + table + " add primary key (code)")
            run_sql(db, ";\n".join(apply_sql(table, to_set)))
        except SqlFailed as e:
            run_sql(db, drop)
            sys.exit("Nothing changed (the transaction was undone and the backup tables dropped). mysql said: " + str(e))
        changed = int(run_sql(db, "select count(*) from " + table + " where applied = 1")[0][0])
        left = kept - changed
        rows = run_sql(db, "select code, " + hx("old_country") + ", " + hx("new_country") + ", " + hx("old_region") + ", " + hx("new_region") + ", "
                       + hx("old_subregion") + ", " + hx("new_subregion") + ", applied from " + table + " order by code")
        with open(os.path.join(folder, "specimens.csv"), "w", newline="", encoding="utf-8") as out:
            w = csv.writer(out)
            w.writerow(["code", "old_country", "new_country", "old_region", "new_region", "old_subregion", "new_subregion", "applied"])
            for r in rows:
                w.writerow([r[0]] + [unhex(v) for v in r[1:7]] + [r[7]])

    text = report_text(now.strftime("%Y-%m-%d %H:%M"), commit, f, table=table, changed=changed, left=left, aliases_set=len(to_set) if table else None)
    after = None
    if table:
        again = load(db)
        after = again.moving() + sum(1 for r in again.aliases if r[4] == "set")
        text += ("\nCheck after the change: specimens still to move or old-name rows still to set: " + str(after)
                 + (" (as expected)" if after == 0 else "  <-- NOT ZERO: run the report again and read it") + "\n")
    text += note
    if not args.apply:
        text += "\nReport only: nothing was changed. Run with --apply to make the changes.\n"
    elif not table:
        text += "\nNothing to change.\n"
    with open(os.path.join(folder, "report.txt"), "w", encoding="utf-8") as out:
        out.write(text)
    print(text)
    if table:
        print(AFTER_TEXT)
    print("Files: " + folder)
    return 1 if after else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Specimen extract for a collaborator: every specimen of a set of countries, with a manifest that proves
the file is complete (2026-11-10, Brian Fisher: the Ants of the Nearctic project's extract of March 2026
was short, Stenamma 14 of 42 species, and nothing in it said so).

Read-only. Run on an AntWeb server by an administrator:

  python3 specimen_extract.py --countries "United States,Canada,Mexico" --bioregion Nearctic

It writes one folder (default /root/antweb-extracts/<date>-<names>/):

  specimens.csv.gz        every specimen whose country (or island country, see below) is in the list: every
                          column of the specimen table, then taxon_status, taxon_fossil, taxon_rank,
                          taxon_current_valid_name, taxon_author_date, group_name and no_taxon_row. Specimens
                          WITHOUT coordinates are in the file (has_coordinates says which): a cut by a shape
                          file would lose them. Specimens whose name has no taxon row are in the file too
                          (no_taxon_row = 1; the API's specimen view leaves those out).
  genus_summary.csv       per genus: specimens, names (distinct taxon names), names with at least one
                          specimen with coordinates, and the same for the chosen bioregion.
  bioregion_list.csv      with --bioregion: AntWeb's species list of that bioregion (the list its page shows),
                          each name with its source and how many of its specimens are in the extract. A name
                          on the list with 0 specimens here comes from the literature or from a country
                          outside the extract.
  manifest.json / .txt    the filters, AntWeb's commit, the date, row counts by country and bioregion, how
                          many specimens have no coordinates, the sha256 of each file, and the check below.

The check ("verified"): the rows written per country and island country are compared with a separate
count(*) of the database taken after the export. They must be equal (an upload during the export is the
only honest reason for a difference; then run it again). The exit code is 1 when they differ.

Islands: AntWeb files an island that it treats as a country (is_island = 1) under specimen.island_country.
Islands whose geolocale row names one of the requested countries as parent or country are included and
listed in the manifest; leave them out with --no-islands.

The password is read from <project>/etc/db.properties and goes to docker through the environment, never on
a command line. Tests: tests/unit/test_specimen_extract.py.

The admin page dataExtract.do writes the same files from the website (src/.../util/DataExtract.java, a port of
this tool; docs/flows/data-extract.md). Keep the two in step: a change of the columns or the manifest goes into
both, with VERSION raised in both.
"""
import argparse
import collections
import csv
import datetime
import gzip
import hashlib
import json
import os
import re
import subprocess
import sys

VERSION = "1"
TAXON_COLUMNS = ("taxon_status", "taxon_fossil", "taxon_rank", "taxon_current_valid_name", "taxon_author_date",
                 "group_name", "no_taxon_row")
PLACE_NAME = re.compile(r"^[^\x00-\x1f;\\]{1,64}$")


def sql_string(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_list(values):
    return "(" + ", ".join(sql_string(v) for v in values) + ")"


def parse_countries(text):
    names = [n.strip() for n in text.split(",") if n.strip()]
    if not names:
        sys.exit("--countries: give at least one country name, as AntWeb writes it (for example United States)")
    for n in names:
        if not PLACE_NAME.match(n):
            sys.exit("--countries: not a place name: " + repr(n))
    return names


def unescape(field):
    """One field of mysql --batch output: \\n, \\t, \\0 and \\\\ are escapes; NULL is the empty value."""
    if field == "NULL":
        return ""
    if "\\" not in field:
        return field
    out, i, n = [], 0, len(field)
    while i < n:
        c = field[i]
        if c == "\\" and i + 1 < n:
            nxt = field[i + 1]
            out.append({"n": "\n", "t": "\t", "0": "\0", "\\": "\\"}.get(nxt, "\\" + nxt))
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def places_sql(countries):
    c = sql_list(countries)
    return ("select name, georank, is_valid, is_island, ifnull(parent, ''), ifnull(country, ''), ifnull(bioregion, ''), "
            "ifnull(alt_bioregion, '') from geolocale where (georank = 'country' and name in " + c + ") "
            "or (is_island = 1 and (parent in " + c + " or country in " + c + ")) order by georank, name")


def where_sql(countries, islands):
    w = "s.country in " + sql_list(countries)
    if islands:
        w += " or s.island_country in " + sql_list(islands)
    return "(" + w + ")"


def export_sql(countries, islands):
    return ("set session transaction isolation level read committed; "
            "select s.*, t.status as taxon_status, t.fossil as taxon_fossil, t.taxarank as taxon_rank, "
            "t.current_valid_name as taxon_current_valid_name, t.author_date as taxon_author_date, g.name as group_name, "
            "case when t.taxon_name is null then 1 else 0 end as no_taxon_row "
            "from specimen s left join taxon t on t.taxon_name = s.taxon_name "
            "left join ant_group g on g.id = s.access_group "
            "where " + where_sql(countries, islands) + " order by s.code")


def count_sql(countries, islands):
    return ("select ifnull(s.country, ''), ifnull(s.island_country, ''), count(*) from specimen s where "
            + where_sql(countries, islands) + " group by 1, 2 order by 1, 2")


def bioregion_list_sql(bioregion):
    return ("select bt.taxon_name, ifnull(t.subfamily, ''), ifnull(t.genus, ''), ifnull(t.species, ''), ifnull(t.subspecies, ''), "
            "ifnull(t.taxarank, ''), ifnull(t.status, ''), ifnull(t.fossil, ''), ifnull(bt.source, ''), ifnull(bt.is_endemic, ''), "
            "ifnull(bt.is_introduced, '') from bioregion_taxon bt left join taxon t on t.taxon_name = bt.taxon_name "
            "where bt.bioregion_name = " + sql_string(bioregion) + " and (t.taxarank in ('species', 'subspecies') or t.taxon_name is null) "
            "order by t.subfamily, t.genus, bt.taxon_name")


def db_login(project):
    props = {}
    with open(os.path.join(project, "etc", "db.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k.strip()] = v
    return props.get("user", ""), props.get("password", "")


def mysql_command(user, container, header):
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD", container, "mysql", "-u", user, "ant",
           "--default-character-set=utf8mb4", "--batch", "--quick"]
    if not header:
        cmd.append("-N")
    return cmd


def run_query(project, sql, container):
    """Rows of a small read-only query (fields unescaped)."""
    user, password = db_login(project)
    r = subprocess.run(mysql_command(user, container, header=False), input=sql.encode("utf-8"), capture_output=True,
                       env=dict(os.environ, MYSQL_PWD=password), timeout=1800)
    if r.returncode != 0:
        sys.exit("mysql failed: " + r.stderr.decode("utf-8", "replace").strip())
    return [[unescape(f) for f in line.split("\t")] for line in r.stdout.decode("utf-8", "replace").split("\n") if line]


def stream_query(project, sql, container):
    """Lines of a large query, one at a time (the first line is the header)."""
    user, password = db_login(project)
    p = subprocess.Popen(mysql_command(user, container, header=True), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.PIPE, env=dict(os.environ, MYSQL_PWD=password))
    p.stdin.write(sql.encode("utf-8"))
    p.stdin.close()
    for raw in p.stdout:
        yield raw.decode("utf-8", "replace").rstrip("\n")
    err = p.stderr.read().decode("utf-8", "replace").strip()
    if p.wait() != 0:
        sys.exit("mysql failed during the export: " + err)


def has_coordinates(lat, lon):
    try:
        la, lo = float(lat), float(lon)
    except (TypeError, ValueError):
        return False
    return not (la == 0 and lo == 0) and -90 <= la <= 90 and -180 <= lo <= 180


class Stats:
    def __init__(self, bioregion):
        self.bioregion = bioregion
        self.rows = 0
        self.by_place = collections.Counter()        # (country, island_country)
        self.by_bioregion = collections.Counter()
        self.by_status = collections.Counter()
        self.no_coordinates = 0
        self.no_taxon_row = 0
        self.flagged = 0
        self.not_ants = 0
        self.genus = collections.defaultdict(lambda: {"specimens": 0, "names": set(), "names_coords": set(),
                                                      "bio_specimens": 0, "bio_names": set()})
        self.name_specimens = collections.Counter()
        self.name_specimens_bio = collections.Counter()

    def add(self, row):
        self.rows += 1
        self.by_place[(row.get("country", ""), row.get("island_country", ""))] += 1
        bio = row.get("bioregion", "")
        self.by_bioregion[bio] += 1
        self.by_status[row.get("taxon_status", "") or "(no taxon row)"] += 1
        coords = has_coordinates(row.get("decimal_latitude"), row.get("decimal_longitude"))
        if not coords:
            self.no_coordinates += 1
        if row.get("no_taxon_row") == "1":
            self.no_taxon_row += 1
        if row.get("flag", ""):
            self.flagged += 1
        if (row.get("family", "") or "").lower() != "formicidae":
            self.not_ants += 1
        name = row.get("taxon_name", "")
        g = self.genus[((row.get("subfamily", "") or "").lower(), (row.get("genus", "") or "").lower())]
        g["specimens"] += 1
        g["names"].add(name)
        if coords:
            g["names_coords"].add(name)
        self.name_specimens[name] += 1
        if self.bioregion and bio == self.bioregion:
            g["bio_specimens"] += 1
            g["bio_names"].add(name)
            self.name_specimens_bio[name] += 1
        return coords


def write_specimens(lines, path, stats):
    """Writes specimens.csv.gz from the stream; returns the column names."""
    header = None
    with gzip.open(path, "wt", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        for line in lines:
            fields = line.split("\t")
            if header is None:
                header = fields
                w.writerow(header + ["has_coordinates"])
                continue
            if len(fields) != len(header):
                sys.exit("export row with %d fields, header has %d: %r" % (len(fields), len(header), line[:200]))
            values = [unescape(v) for v in fields]
            coords = stats.add(dict(zip(header, values)))
            w.writerow(values + ["1" if coords else "0"])
    if header is None:
        sys.exit("the export returned nothing, not even a header")
    return header


def write_genus_summary(path, stats):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        head = ["subfamily", "genus", "specimens", "names", "names_with_coordinates"]
        if stats.bioregion:
            head += ["specimens_in_" + stats.bioregion, "names_in_" + stats.bioregion]
        w.writerow(head)
        for (subfamily, genus), g in sorted(stats.genus.items()):
            row = [subfamily, genus, g["specimens"], len(g["names"]), len(g["names_coords"])]
            if stats.bioregion:
                row += [g["bio_specimens"], len(g["bio_names"])]
            w.writerow(row)


def write_bioregion_list(path, rows, stats):
    """AntWeb's list of the bioregion with the extract's specimen counts; returns (names, names with no specimen here)."""
    missing = 0
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["taxon_name", "subfamily", "genus", "species", "subspecies", "rank", "status", "fossil", "list_source",
                    "is_endemic", "is_introduced", "specimens_in_extract", "specimens_in_extract_in_" + stats.bioregion])
        for r in rows:
            n_all, n_bio = stats.name_specimens.get(r[0], 0), stats.name_specimens_bio.get(r[0], 0)
            if n_all == 0:
                missing += 1
            w.writerow(r + [n_all, n_bio])
    return len(rows), missing


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def compare_counts(stats, db_rows):
    """(verified, differences): the rows written per (country, island country) against the database's count."""
    db = {(r[0], r[1]): int(r[2]) for r in db_rows}
    diffs = []
    for key in sorted(set(db) | set(stats.by_place)):
        a, b = stats.by_place.get(key, 0), db.get(key, 0)
        if a != b:
            diffs.append({"country": key[0], "island_country": key[1], "in_file": a, "in_database": b})
    return not diffs, diffs


def slug(names):
    return re.sub(r"[^a-z0-9]+", "-", "-".join(names).lower()).strip("-")[:60]


def manifest_text(m):
    lines = ["AntWeb specimen extract", "",
             "Made: %s (UTC)   AntWeb commit: %s   tool version %s" % (m["made_utc"], m["antweb_commit"], m["tool_version"]),
             "Countries: " + ", ".join(m["countries"]),
             "Islands included: " + (", ".join(m["islands"]) or "none"),
             "", "Specimens in the file: {:,}".format(m["specimens"]),
             "Check against the database: " + ("VERIFIED, every count equal" if m["verified"] else "DIFFERENT, see differences; run again"),
             "", "By country (country / island country):"]
    for p in m["by_place"]:
        lines.append("  {:<40} {:>10,}".format(p["country"] + (" / " + p["island_country"] if p["island_country"] else ""), p["specimens"]))
    lines += ["", "By AntWeb bioregion:"]
    for name, n in m["by_bioregion"].items():
        lines.append("  {:<40} {:>10,}".format(name or "(none)", n))
    lines += ["", "Without usable coordinates: {:,} (in the file, has_coordinates = 0; a cut by a shape file loses them)".format(m["without_coordinates"]),
              "Name has no taxon row: {:,} (in the file, no_taxon_row = 1; the API's specimen view leaves these out)".format(m["no_taxon_row"]),
              "Red-flagged at upload: {:,} (column flag)".format(m["flagged"]),
              "Family not Formicidae: {:,}".format(m["not_ants"]),
              "Genera: {:,}   distinct names: {:,}".format(m["genera"], m["names"])]
    if m.get("bioregion"):
        b = m["bioregion_list"]
        lines += ["", "AntWeb's %s species list: %s names; %s of them have no specimen in this extract (literature, or other countries)"
                  % (m["bioregion"], "{:,}".format(b["names"]), "{:,}".format(b["names_without_specimen_in_extract"]))]
    lines += ["", "Files (sha256):"]
    for name, info in m["files"].items():
        lines.append("  %s  %s" % (info["sha256"], name))
    if m["differences"]:
        lines += ["", "Differences:"] + ["  " + json.dumps(d) for d in m["differences"]]
    lines += ["", "Cite: AntWeb. Version %s. California Academy of Sciences, online at https://www.antweb.org. Extract of %s."
              % (m.get("antweb_version") or "[version]", m["made_utc"][:10]),
              "Images and data on AntWeb are licensed CC BY 4.0."]
    return "\n".join(lines) + "\n"


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


def main(argv=None):
    a = argparse.ArgumentParser(description="Specimen extract for a set of countries, with a manifest (read-only).")
    a.add_argument("--countries", required=True, help='AntWeb country names, comma-separated: "United States,Canada,Mexico"')
    a.add_argument("--bioregion", default="", help="also write AntWeb's species list of this bioregion (for example Nearctic)")
    a.add_argument("--no-islands", action="store_true", help="leave out islands AntWeb treats as countries")
    a.add_argument("--project", default=None, help="AntWeb checkout with etc/db.properties (default: found)")
    a.add_argument("--container", default="antweb_mysql_1")
    a.add_argument("--out", default=None, help="folder to write (default /root/antweb-extracts/<date>-<names>)")
    args = a.parse_args(argv)

    project = args.project or default_project()
    countries = parse_countries(args.countries)
    if args.bioregion and not PLACE_NAME.match(args.bioregion):
        sys.exit("--bioregion: not a name: " + repr(args.bioregion))
    now = datetime.datetime.now(datetime.timezone.utc)
    out = args.out or os.path.join("/root/antweb-extracts", now.strftime("%Y-%m-%d") + "-" + slug(countries))
    os.makedirs(out, exist_ok=True)

    places = run_query(project, places_sql(countries), args.container)
    found = {p[0] for p in places if p[1] == "country"}
    unknown = [c for c in countries if c not in found]
    if unknown:
        sys.exit("no country row in AntWeb for: " + ", ".join(unknown) + " (names as on AntWeb's country pages)")
    islands = [] if args.no_islands else sorted({p[0] for p in places if p[3] == "1" and p[0] not in countries})
    print("Countries: " + ", ".join(countries))
    for p in places:
        print("  %-28s %-8s valid=%s island=%s parent=%s bioregion=%s alt=%s" % (p[0], p[1], p[2], p[3], p[4] or "-", p[6] or "-", p[7] or "-"))
    print("Islands included: " + (", ".join(islands) or "none"))

    stats = Stats(args.bioregion)
    spec_path = os.path.join(out, "specimens.csv.gz")
    print("Exporting (read-only) ...", flush=True)
    header = write_specimens(stream_query(project, export_sql(countries, islands), args.container), spec_path, stats)
    verified, diffs = compare_counts(stats, run_query(project, count_sql(countries, islands), args.container))

    genus_path = os.path.join(out, "genus_summary.csv")
    write_genus_summary(genus_path, stats)
    files = [spec_path, genus_path]
    bio = None
    if args.bioregion:
        list_path = os.path.join(out, "bioregion_list.csv")
        names, missing = write_bioregion_list(list_path, run_query(project, bioregion_list_sql(args.bioregion), args.container), stats)
        bio = {"names": names, "names_without_specimen_in_extract": missing}
        files.append(list_path)

    manifest = {
        "tool_version": VERSION, "made_utc": now.strftime("%Y-%m-%d %H:%M:%S"),
        "antweb_commit": read_first_line(os.path.join(project, "etc", "git-commit")) or "unknown",
        "antweb_version": read_first_line(os.path.join(project, ".version")),
        "countries": countries, "islands": islands, "bioregion": args.bioregion,
        "columns": header + ["has_coordinates"], "specimens": stats.rows, "verified": verified, "differences": diffs,
        "by_place": [{"country": k[0], "island_country": k[1], "specimens": n} for k, n in sorted(stats.by_place.items())],
        "by_bioregion": dict(sorted(stats.by_bioregion.items())), "by_taxon_status": dict(sorted(stats.by_status.items())),
        "without_coordinates": stats.no_coordinates, "no_taxon_row": stats.no_taxon_row, "flagged": stats.flagged,
        "not_ants": stats.not_ants, "genera": len(stats.genus), "names": len(stats.name_specimens),
        "bioregion_list": bio,
        "files": {os.path.basename(p): {"sha256": sha256(p), "bytes": os.path.getsize(p)} for p in files},
    }
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=1, ensure_ascii=False)
    text = manifest_text(manifest)
    with open(os.path.join(out, "manifest.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print()
    print(text)
    print("Folder: " + out)
    return 0 if verified else 1


if __name__ == "__main__":
    sys.exit(main())

"""tools/export/specimen_extract.py: a collaborator's specimen extract that proves it is complete (2026-11-10).

The Ants of the Nearctic list built from a March 2026 extract was short (Stenamma 14 of 42 species) and nothing
said so. The tool writes every specimen of the chosen countries (with or without coordinates, with or without a
taxon row) and a manifest whose counts are checked against a separate count of the database.

No database here: a stand-in `docker` answers the tool's four queries with mysql --batch output (escaped tabs,
newlines, backslashes, NULL), records its arguments, and can make the database count differ.

Run: python3 tests/unit/test_specimen_extract.py   (also in .github/workflows/tests.yaml)
"""
import csv
import gzip
import importlib.util
import json
import os
import stat
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
TOOL = os.path.join(ROOT, "tools", "export", "specimen_extract.py")
spec = importlib.util.spec_from_file_location("specimen_extract", TOOL)
se = importlib.util.module_from_spec(spec)
spec.loader.exec_module(se)

HEADER = ["code", "taxon_name", "subfamily", "genus", "country", "localityname", "decimal_latitude", "decimal_longitude",
          "family", "bioregion", "flag", "island_country", "taxon_status", "taxon_fossil", "taxon_rank",
          "taxon_current_valid_name", "taxon_author_date", "group_name", "no_taxon_row"]
ROWS = [
    ["casent1", "myrmicinaestenamma diecki", "myrmicinae", "stenamma", "United States", "Mt. Tam\\tridge\\nline2 \\\\ back",
     "37.9", "-122.6", "formicidae", "Nearctic", "NULL", "NULL", "valid", "0", "species", "NULL", "Emery, 1895", "CAS", "0"],
    ["casent2", "myrmicinaestenamma ca01", "myrmicinae", "stenamma", "United States", "no coords",
     "NULL", "NULL", "formicidae", "Nearctic", "NULL", "NULL", "morphotaxon", "0", "species", "NULL", "NULL", "CAS", "0"],
    ["casent3", "myrmicinaeveromessor andrei", "myrmicinae", "veromessor", "Mexico", "Ensenada, Bahía",
     "31.8", "-116.6", "formicidae", "Nearctic", "red", "NULL", "valid", "0", "species", "NULL", "(Mayr, 1886)", "Utah", "0"],
    ["casent4", "formicinaenotintaxon x", "formicinae", "notintaxon", "Canada", "zero coords",
     "0", "0", "formicidae", "Nearctic", "NULL", "NULL", "NULL", "NULL", "NULL", "NULL", "NULL", "Utah", "1"],
    ["casent5", "myrmicinaestenamma diecki", "myrmicinae", "stenamma", "Hawaii", "Oahu",
     "21.3", "-157.8", "formicidae", "Oceania", "NULL", "Hawaii", "valid", "0", "species", "NULL", "Emery, 1895", "CAS", "0"],
]
PLACES = [["Canada", "country", "1", "0", "", "", "Nearctic", "", ""],
          ["Hawaii", "country", "1", "1", "United States", "United States", "Oceania", "", ""],
          ["Mexico", "country", "1", "0", "", "", "Neotropical", "Nearctic", ""],
          ["United States", "country", "1", "0", "", "", "Nearctic", "Neotropical", ""],
          ["USA", "country", "0", "0", "", "", "", "", "United States"]]
LIST = [["myrmicinaestenamma diecki", "myrmicinae", "stenamma", "diecki", "", "species", "valid", "0", "specimen", "0", "0"],
        ["myrmicinaestenamma lit", "myrmicinae", "stenamma", "lit", "", "species", "valid", "0", "antcat", "0", "0"]]

SHIM = r'''#!/usr/bin/env python3
import json, os, sys
d = os.path.dirname(os.path.abspath(__file__))
sql = sys.stdin.read()
with open(os.path.join(d, "calls.jsonl"), "a") as f:
    f.write(json.dumps({"argv": sys.argv[1:], "has_pwd": bool(os.environ.get("MYSQL_PWD")), "sql": sql}) + "\n")
data = json.load(open(os.path.join(d, "answers.json")))
def out(rows): sys.stdout.write("".join("\t".join(r) + "\n" for r in rows))
if "s.country is null" in sql and "select s.*" in sql: out([data["header"]] + data["no_country_rows"])
elif "s.country is null" in sql: out([[str(data["no_country_count"])]])
elif "/* outdated names */" in sql: out(data["outdated"])
elif "/* per country */" in sql: out(data["per_country"])
elif "from geolocale" in sql: out(data["places"])
elif "select s.*" in sql: out([data["header"]] + data["rows"])
elif "count(*)" in sql: out(data["counts"])
elif "from bioregion_taxon" in sql: out(data["list"])
else: sys.exit("unexpected sql: " + sql)
'''


NO_COUNTRY_ROWS = [
    ["utah1", "myrmicinaestenamma utah01", "myrmicinae", "stenamma", "NULL", "somewhere in Utah",
     "NULL", "NULL", "formicidae", "NULL", "NULL", "NULL", "morphotaxon", "0", "species", "NULL", "NULL", "University of Utah", "0"],
    ["utep1", "myrmicinaestenamma diecki", "myrmicinae", "stenamma", "", "El Paso",
     "31.7", "-106.4", "formicidae", "NULL", "NULL", "NULL", "valid", "0", "species", "NULL", "Emery, 1895", " UTEP", "0"],
]


def setup(counts=None, places=None, rows=None, outdated=None, per_country=None, no_country_count=2):
    d = tempfile.mkdtemp(prefix="extract-test-")
    os.makedirs(os.path.join(d, "proj", "etc"))
    os.makedirs(os.path.join(d, "bin"))
    with open(os.path.join(d, "proj", "etc", "db.properties"), "w") as f:
        f.write("user=tester\npassword=not-a-real-password\n")
    with open(os.path.join(d, "proj", "etc", "git-commit"), "w") as f:
        f.write("abc1234\n")
    shim = os.path.join(d, "bin", "docker")
    with open(shim, "w") as f:
        f.write(SHIM)
    os.chmod(shim, os.stat(shim).st_mode | stat.S_IXUSR)
    if counts is None:
        counts = [["Canada", "", "1"], ["Hawaii", "Hawaii", "1"], ["Mexico", "", "1"], ["United States", "", "2"]]
    counts = [[c[0].encode("utf-8").hex(), c[1].encode("utf-8").hex(), c[2]] for c in counts]   # the count query answers hex(name)
    with open(os.path.join(d, "bin", "answers.json"), "w") as f:
        json.dump({"places": places or PLACES, "header": HEADER, "rows": ROWS if rows is None else rows, "counts": counts, "list": LIST,
                   # index of the requested country, then the old name / the number of its specimens
                   "outdated": [["0", "USA"]] if outdated is None else outdated,
                   "per_country": [["0", "2"], ["1", "1"], ["2", "1"]] if per_country is None else per_country,
                   "no_country_rows": NO_COUNTRY_ROWS, "no_country_count": no_country_count}, f)
    return d


def run(d, *extra):
    env = dict(os.environ, PATH=os.path.join(d, "bin") + os.pathsep + os.environ["PATH"])
    return subprocess.run([sys.executable, TOOL, "--project", os.path.join(d, "proj"), "--out", os.path.join(d, "out")] + list(extra),
                          capture_output=True, text=True, env=env)


def calls(d):
    with open(os.path.join(d, "bin", "calls.jsonl")) as f:
        return [json.loads(line) for line in f]


def test_the_extract_is_complete_and_verified():
    d = setup()
    r = run(d, "--countries", "United States,Canada,Mexico", "--bioregion", "Nearctic")
    assert r.returncode == 0, r.stdout + r.stderr
    assert "VERIFIED, every count equal" in r.stdout
    with gzip.open(os.path.join(d, "out", "specimens.csv.gz"), "rt", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    assert [x["code"] for x in rows] == ["casent1", "casent2", "casent3", "casent4", "casent5"]
    # escapes of mysql --batch are undone; NULL is empty; accents kept
    assert rows[0]["localityname"] == "Mt. Tam\tridge\nline2 \\ back" and rows[0]["flag"] == ""
    assert rows[2]["localityname"] == "Ensenada, Bahía"
    # specimens without coordinates (none, or 0/0) and without a taxon row stay in the file, marked
    assert [x["has_coordinates"] for x in rows] == ["1", "0", "1", "0", "1"]
    assert rows[3]["no_taxon_row"] == "1"
    m = json.load(open(os.path.join(d, "out", "manifest.json")))
    assert m["specimens"] == 5 and m["verified"] is True and m["differences"] == []
    assert m["without_coordinates"] == 2 and m["no_taxon_row"] == 1 and m["flagged"] == 1
    assert m["islands"] == ["Hawaii"] and m["antweb_commit"] == "abc1234"
    assert m["by_bioregion"] == {"Nearctic": 4, "Oceania": 1}
    assert m["bioregion_list"] == {"names": 2, "names_without_specimen_in_extract": 1}
    assert set(m["files"]) == {"specimens.csv.gz", "genus_summary.csv", "bioregion_list.csv"}
    assert m["files"]["specimens.csv.gz"]["sha256"] == se.sha256(os.path.join(d, "out", "specimens.csv.gz"))
    genus = {(g["subfamily"], g["genus"]): g for g in csv.DictReader(open(os.path.join(d, "out", "genus_summary.csv")))}
    st = genus[("myrmicinae", "stenamma")]
    assert (st["specimens"], st["names"], st["names_with_coordinates"], st["names_in_Nearctic"]) == ("3", "2", "1", "2")
    print("ok   test_the_extract_is_complete_and_verified")


def test_a_different_database_count_fails_the_run():
    d = setup(counts=[["Canada", "", "1"], ["Hawaii", "Hawaii", "1"], ["Mexico", "", "1"], ["United States", "", "3"]])
    r = run(d, "--countries", "United States,Canada,Mexico")
    assert r.returncode == 1, r.stdout + r.stderr
    assert "DIFFERENT" in r.stdout
    m = json.load(open(os.path.join(d, "out", "manifest.json")))
    assert m["verified"] is False
    assert m["differences"] == [{"country": "United States", "island_country": "", "in_file": 2, "in_database": 3}]
    print("ok   test_a_different_database_count_fails_the_run")


def test_an_unknown_country_name_stops_before_any_export():
    d = setup()
    r = run(d, "--countries", "USA,Canada")
    # USA is a row AntWeb keeps as an old name (is_valid = 0, valid_name United States): refused, with the valid name
    assert r.returncode != 0 and "no valid country in AntWeb named: USA (use United States)" in r.stderr, r.stderr
    assert not any("select s.*" in c["sql"] for c in calls(d))
    print("ok   test_an_unknown_country_name_stops_before_any_export")


def test_the_password_never_reaches_a_command_line_and_the_queries_only_read():
    d = setup()
    run(d, "--countries", "United States,Canada,Mexico", "--bioregion", "Nearctic")
    cs = calls(d)
    assert len(cs) == 6     # places, old names, the export, the count, the count per country, the bioregion list
    for c in cs:
        assert c["has_pwd"] and "not-a-real-password" not in " ".join(c["argv"]), c["argv"]
        assert c["argv"][:5] == ["exec", "-i", "-e", "MYSQL_PWD", "antweb_mysql_1"]
        low = c["sql"].lower()
        for word in ("insert ", "update ", "delete ", "drop ", "alter ", "create ", "truncate ", "replace "):
            assert word not in low, (word, c["sql"])
    print("ok   test_the_password_never_reaches_a_command_line_and_the_queries_only_read")


def test_the_database_count_is_grouped_by_the_exact_spelling():
    """Staging 2026-11-10: three specimens say "México"; MySQL's `country in ('Mexico')` finds them and a plain
    GROUP BY country adds them to Mexico, so the check reported a difference for a complete file."""
    sql = se.count_sql(["Mexico"], [])
    assert "hex(convert(ifnull(s.country, '') using utf8mb4))" in sql and "hex(convert(ifnull(s.island_country, '') using utf8mb4))" in sql
    stats = se.Stats("")
    for country in ("Mexico", "M\u00e9xico", "M\u00e9xico"):
        stats.add({"country": country, "island_country": ""})
    hx = lambda v: v.encode("utf-8").hex().upper()
    assert se.compare_counts(stats, [[hx("Mexico"), "", "1"], [hx("M\u00e9xico"), "", "2"]]) == (True, [])
    assert se.compare_counts(stats, [[hx("Mexico"), "", "3"]])[0] is False
    text = se.manifest_text({"made_utc": "2026-11-10 15:49:20", "antweb_commit": "x", "tool_version": "2", "countries": ["Mexico"],
                             "islands": [], "specimens": 4, "verified": True, "differences": [],
                             "by_place": [{"country": "Mexico", "island_country": "", "specimens": 1},
                                          {"country": "M\u00e9xico", "island_country": "", "specimens": 3}],
                             "by_bioregion": {}, "without_coordinates": 0, "no_taxon_row": 0, "flagged": 0, "not_ants": 0,
                             "genera": 1, "names": 1, "files": {}})
    assert "Country spellings in the file besides the names asked for" in text and "M\u00e9xico" in text
    print("ok   test_the_database_count_is_grouped_by_the_exact_spelling")


def test_specimens_under_an_outdated_name_are_in_the_file_and_counted():
    """Review M3 (2026-11-10): VERIFIED proved the file equals the filter, not that the filter is complete. Specimens
    still filed under an old name of a requested country (USA) are now in the extract and named in the manifest."""
    usa = ["casent6", "myrmicinaestenamma diecki", "myrmicinae", "stenamma", "USA", "old name",
           "40.1", "-100.2", "formicidae", "Nearctic", "NULL", "NULL", "valid", "0", "species", "NULL", "Emery, 1895", "CAS", "0"]
    d = setup(rows=ROWS + [usa], counts=[["Canada", "", "1"], ["Hawaii", "Hawaii", "1"], ["Mexico", "", "1"], ["USA", "", "1"],
                                         ["United States", "", "2"]])
    r = run(d, "--countries", "United States,Canada,Mexico")
    assert r.returncode == 0, r.stdout + r.stderr
    export = [c["sql"] for c in calls(d) if "select s.*" in c["sql"]][0]
    assert "s.country in ('United States', 'Canada', 'Mexico', 'USA')" in export
    count = [c["sql"] for c in calls(d) if "group by 1, 2" in c["sql"]][0]
    assert "'USA'" in count
    m = json.load(open(os.path.join(d, "out", "manifest.json")))
    assert m["tool_version"] == "4"
    assert m["outdated_names"] == [{"name": "USA", "valid_name": "United States"}]
    assert m["filed_under_outdated_name"] == 1 and m["specimens"] == 6 and m["verified"] is True
    assert m["countries_without_specimens"] == []
    assert {"country": "USA", "island_country": "", "specimens": 1} in m["by_place"]
    text = open(os.path.join(d, "out", "manifest.txt"), encoding="utf-8").read()
    assert "Outdated country names included: USA (now United States)\n" in text
    assert "Filed under an outdated country name: 1\n" in text
    assert "Country spellings in the file besides" not in text      # USA is listed as an outdated name, not as a spelling
    assert "WARNING" not in text
    assert "Spreadsheets: import the columns of specimens.csv as text." in text
    # the per-country count asks for the country and its old names together
    per = [c["sql"] for c in calls(d) if "/* per country */" in c["sql"]][0]
    assert "select 0, count(*) from specimen s where s.country in ('United States', 'USA')" in per
    print("ok   test_specimens_under_an_outdated_name_are_in_the_file_and_counted")


def test_a_country_without_specimens_is_a_warning():
    d = setup(outdated=[], per_country=[["0", "2"], ["1", "0"], ["2", "1"]])
    r = run(d, "--countries", "United States,Canada,Mexico")
    m = json.load(open(os.path.join(d, "out", "manifest.json")))
    assert m["countries_without_specimens"] == ["Canada"] and m["outdated_names"] == []
    text = open(os.path.join(d, "out", "manifest.txt"), encoding="utf-8").read()
    assert "WARNING: AntWeb has no specimen for: Canada (check that these are the countries meant)\n" in text
    assert "Outdated country names included: none\n" in text and "Filed under an outdated country name: 0\n" in text
    assert "WARNING: AntWeb has no specimen for: Canada" in r.stdout
    print("ok   test_a_country_without_specimens_is_a_warning")


def test_a_second_run_and_a_failed_run_leave_no_stale_or_partial_file():
    """Review L11: a second run in the same folder left the first run's bioregion_list.csv; a failed export left a
    partial specimens.csv.gz that looked finished."""
    d = setup()
    out = os.path.join(d, "out")
    assert run(d, "--countries", "United States,Canada,Mexico", "--bioregion", "Nearctic", "--with-no-country").returncode == 0
    assert os.path.exists(os.path.join(out, "bioregion_list.csv")) and os.path.exists(os.path.join(out, "specimens_no_country.csv.gz"))
    assert run(d, "--countries", "United States,Canada,Mexico").returncode == 0
    assert sorted(os.listdir(out)) == ["genus_summary.csv", "manifest.json", "manifest.txt", "specimens.csv.gz"]
    # an export that breaks off (a row with a field too few): no specimens.csv.gz, no manifest, nothing half-written
    d = setup(rows=ROWS + [["casent6", "broken row"]])
    r = run(d, "--countries", "United States,Canada,Mexico")
    assert r.returncode != 0 and "export row with 2 fields" in r.stderr, r.stderr
    assert os.listdir(os.path.join(d, "out")) == [], os.listdir(os.path.join(d, "out"))
    # and a failed run after a good one leaves nothing of the good one that could be taken for the new result
    d = setup()
    assert run(d, "--countries", "United States,Canada,Mexico").returncode == 0
    with open(os.path.join(d, "bin", "answers.json")) as f:
        answers = json.load(f)
    answers["rows"].append(["casent6", "broken row"])
    with open(os.path.join(d, "bin", "answers.json"), "w") as f:
        json.dump(answers, f)
    assert run(d, "--countries", "United States,Canada,Mexico").returncode != 0
    assert os.listdir(os.path.join(d, "out")) == []
    print("ok   test_a_second_run_and_a_failed_run_leave_no_stale_or_partial_file")
def test_specimens_with_no_country_go_in_their_own_file():
    """Production 2026-11-10: 1,623 specimens have no country (Utah 303, UTEP 167, ...); no country filter finds them."""
    d = setup()
    r = run(d, "--countries", "United States,Canada,Mexico", "--with-no-country")
    assert r.returncode == 0, r.stdout + r.stderr
    with gzip.open(os.path.join(d, "out", "specimens_no_country.csv.gz"), "rt", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    assert [x["code"] for x in rows] == ["utah1", "utep1"] and rows[0]["country"] == ""
    m = json.load(open(os.path.join(d, "out", "manifest.json")))
    assert m["no_country"] == {"specimens": 2, "in_database": 2, "with_coordinates": 1, "names": 2,
                               "names_not_in_the_countries_file": 1,
                               "by_group": {"University of Utah": 1, " UTEP": 1}}
    assert "specimens_no_country.csv.gz" in m["files"] and m["specimens"] == 5 and m["verified"] is True
    assert "Specimens with no country, in specimens_no_country.csv.gz" in r.stdout
    # a different database count fails the run
    d = setup(no_country_count=3)
    r = run(d, "--countries", "United States,Canada,Mexico", "--with-no-country")
    assert r.returncode == 1 and "DIFFERENT" in r.stdout
    # without the option: no file, no query
    d = setup()
    run(d, "--countries", "United States,Canada,Mexico")
    assert not os.path.exists(os.path.join(d, "out", "specimens_no_country.csv.gz"))
    assert not any("s.country is null" in c["sql"] for c in calls(d))
    print("ok   test_specimens_with_no_country_go_in_their_own_file")


def test_islands_can_be_left_out():
    d = setup(counts=[["Canada", "", "1"], ["Mexico", "", "1"], ["United States", "", "2"]])
    run(d, "--countries", "United States,Canada,Mexico", "--no-islands")
    export = [c["sql"] for c in calls(d) if "select s.*" in c["sql"]][0]
    assert "island_country in" not in export
    print("ok   test_islands_can_be_left_out")


def test_helpers():
    assert se.unescape("NULL") == "" and se.unescape("a\\tb\\nc\\\\d\\0") == "a\tb\nc\\d\0" and se.unescape("x\\y") == "x\\y"
    assert se.has_coordinates("37.9", "-122.6") and not se.has_coordinates("0", "0")
    assert not se.has_coordinates("", "") and not se.has_coordinates("95", "10") and not se.has_coordinates("abc", "1")
    assert se.sql_string("Cote d'Ivoire") == "'Cote d''Ivoire'" and se.sql_string("a\\b") == "'a\\\\b'"
    assert se.parse_countries(" United States , Canada ,") == ["United States", "Canada"]
    for bad in ("x; drop table specimen", "a\\b", ""):
        try:
            se.parse_countries(bad)
        except SystemExit:
            continue
        raise AssertionError("accepted " + repr(bad))
    assert "s.country in ('Canada')" in se.where_sql(["Canada"], []) and "island_country in ('Hawaii')" in se.where_sql(["Canada"], ["Hawaii"])
    print("ok   test_helpers")


if __name__ == "__main__":
    test_helpers()
    test_the_extract_is_complete_and_verified()
    test_a_different_database_count_fails_the_run()
    test_an_unknown_country_name_stops_before_any_export()
    test_the_password_never_reaches_a_command_line_and_the_queries_only_read()
    test_islands_can_be_left_out()
    test_the_database_count_is_grouped_by_the_exact_spelling()
    test_specimens_under_an_outdated_name_are_in_the_file_and_counted()
    test_a_country_without_specimens_is_a_warning()
    test_a_second_run_and_a_failed_run_leave_no_stale_or_partial_file()
    test_specimens_with_no_country_go_in_their_own_file()
    print("ok")

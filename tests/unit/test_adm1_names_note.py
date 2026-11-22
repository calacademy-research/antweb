"""The countries whose adm1s are written in English: Country.ENGLISH_NAMED (the line on the country page, Java) equals
ENGLISH in tools/place/iso_states.py (the renames). Run: python3 tests/unit/test_adm1_names_note.py"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "place"))
import iso_states  # noqa: E402


def test_the_two_lists_are_the_same():
    java = open(os.path.join(ROOT, "src/org/calacademy/antweb/geolocale/Country.java"), encoding="utf-8").read()
    block = re.search(r"ENGLISH_NAMED = .*?\)\)\);", java, re.S).group(0)
    codes = re.findall(r'"([A-Z]{2})"', block)
    assert len(codes) == len(set(codes)) and set(codes) == set(iso_states.ENGLISH), sorted(set(codes) ^ set(iso_states.ENGLISH))


def test_no_line_where_iso_has_no_subdivisions():
    # review of #408: Country.NO_ISO_SUBDIVISIONS is the codes of iso3166_countries.tsv with no row in iso3166_2.tsv,
    # plus Kosovo's XK
    java = open(os.path.join(ROOT, "src/org/calacademy/antweb/geolocale/Country.java"), encoding="utf-8").read()
    block = re.search(r"NO_ISO_SUBDIVISIONS = .*?\)\)\);", java, re.S).group(0)
    codes = re.findall(r'"([A-Z]{2})"', block)
    subs, countries = set(), []
    with open(os.path.join(ROOT, "tools/place/iso3166_2.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.startswith("#") and "-" in line.split("\t")[0]:
                subs.add(line.split("-")[0])
    with open(os.path.join(ROOT, "tools/place/iso3166_countries.tsv"), encoding="utf-8") as f:
        countries = [line.split("\t")[0] for line in f if not line.startswith("#") and line.strip()]
    want = {c for c in countries if c not in subs} | {"XK"}
    assert len(codes) == len(set(codes)) and set(codes) == want, sorted(set(codes) ^ want)


if __name__ == "__main__":
    test_the_two_lists_are_the_same()
    test_no_line_where_iso_has_no_subdivisions()
    print("2 tests passed")

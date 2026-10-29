#!/usr/bin/env python3
"""Place pages label the Species row "Species / subspecies / morphospecies" (Brian Fisher, 2026-10-29).

Every kind of place page (country, adm1, region, subregion, bioregion, museum, project) shows its "Taxonomic diversity"
table from one file, web/overview-body.jsp: the row data-stat="species" is labelled "Species / subspecies /
morphospecies" (the number counts valid names and morphospecies, the counting rules of 2026-10-28), the row
data-stat="valid" keeps "Valid species / subspecies", and every place page uses that file. The admin check number
under a genus list names the same label (web/taxonReportBody.jsp).

Run: python3 tests/unit/test_place_species_label.py   (also in .github/workflows/tests.yaml)
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
WEB = os.path.join(ROOT, "web")
PLACE_PAGES = ["country.jsp", "adm1.jsp", "region.jsp", "subregion.jsp", "bioregion.jsp", "museum.jsp", "project.jsp"]


def read(name):
    with open(os.path.join(WEB, name), encoding="utf-8") as f:
        return f.read()


def main():
    body = read("overview-body.jsp")
    rows = dict(re.findall(r'<tr data-stat="(\w+)"[^\n]*?><td class="ps_name">([^<]*)</td>', body))
    assert rows.get("species") == "Species / subspecies / morphospecies", rows.get("species")
    assert rows.get("valid") == "Valid species / subspecies", rows.get("valid")
    assert ">Species / subspecies<" not in body, "the old label is still on the place pages"
    for page in PLACE_PAGES:
        assert 'value="/overview-body.jsp"' in read(page), page + " does not use overview-body.jsp"
    assert "it should equal Species / subspecies / morphospecies on the" in read("taxonReportBody.jsp")
    print(f"Species / subspecies / morphospecies on {len(PLACE_PAGES)} kinds of place page; Valid species / subspecies kept.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

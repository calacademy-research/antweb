#!/usr/bin/env python3
"""Place pages label the Species row "Species / subspecies / morphospecies" (Brian Fisher, 2026-10-29).

Every kind of place page (country, adm1, region, subregion, bioregion, museum, project) shows its "Taxonomic diversity"
table from one file, web/overview-body.jsp: the row data-stat="species" is labelled "Species / subspecies /
morphospecies" (the number counts valid names and morphospecies, the counting rules of 2026-10-28), the row
data-stat="valid" keeps "Valid species / subspecies", and every place page uses that file. The admin check number
under a genus list names the same label (web/taxonReportBody.jsp).

2026-11-11 (Brian Fisher, the Fossil Ants page): the row data-stat="valid" counts valid living species. On the AntCat
list and Fossil Ants, whose lists have their own views, it is named "Valid living species / subspecies", opens their
view Valid extant (StatusSet.placePageValidView) and stands after the Species row; the three list numbers name the
list's own default view (StatusSet.defaultChoice: Curated, there Valid (with fossils)) and open it.

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
    # the Valid row is built once (validRow) and placed before the Species row, on the lists with their own views after it
    assert 'String validName = ownViews ? "Valid living species / subspecies" : "Valid species / subspecies";' in body
    assert '"<tr data-stat=\\"valid\\" data-n=\\"" + overview.getValidSpeciesCount() + "\\"><td class=\\"ps_name\\">" + validName + "</td>' in body
    before, after = body.split('<tr data-stat="species" data-n="<%= speciesShown %>">', 1)
    assert before.rstrip().endswith('<%= ownViews ? "" : validRow %>'), before[-120:]
    assert after.split("\n", 2)[1].strip() == '<%= ownViews ? validRow : "" %>', after.split("\n", 2)[1]
    # the views the numbers open and name: the list's default view, and the view of valid living species
    assert 'boolean ownViews = org.calacademy.antweb.StatusSet.hasOwnStatusSets(overview.getName());' in body
    assert 'String curatedView = "&statusSet=" + Html.url(defaultView);' in body
    assert 'String defaultView = org.calacademy.antweb.StatusSet.defaultChoice(overview);' in body
    assert 'String validView = org.calacademy.antweb.StatusSet.placePageValidView(overview.getName());' in body
    assert '"species&statusSet=" + Html.url(validView)' in body
    assert "in its default view, Curated" not in body, "a line still says Curated whatever the list's default view is"
    assert 'in its default view, " + Html.esc(defaultViewName) + " (the number opens it): "' in body
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

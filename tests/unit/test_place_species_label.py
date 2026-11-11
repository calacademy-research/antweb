#!/usr/bin/env python3
"""Place pages label the Species row "Species / subspecies / morphospecies" (Brian Fisher, 2026-10-29).

Every kind of place page (country, adm1, region, subregion, bioregion, museum, project) shows its "Taxonomic diversity"
table from one file, web/overview-body.jsp: the row data-stat="species" is labelled "Species / subspecies /
morphospecies" (the number counts valid names and morphospecies, the counting rules of 2026-10-28), the row
data-stat="valid" keeps "Valid species / subspecies", and every place page uses that file. The admin check number
under a genus list names the same label (web/taxonReportBody.jsp).

The rule of the list numbers (Brian Fisher, 2026-11-11, after the Fossil Ants page said "Valid species / subspecies 3"
beside a list of 868 and named the view Curated on a list that has none):
 1. Every number on a place page is the header of the list its link opens.
 2. The species lines are in the same order on every place page: first "Valid species / subspecies", then
    "Species / subspecies / morphospecies".
 3. "Valid species / subspecies" = the rows of the list's Valid view. On the two lists with their own views (Fossil
    Ants, AntCat list) the Valid view is Valid (with fossils) (StatusSet.ownChoice("valid")), so the number is the
    species total of that view, the link opens that view, and the text names it.
 4. "Species / subspecies / morphospecies" = the rows of the list's default view (Curated; Valid (with fossils) on the
    two own-view lists, so there both lines show the same number, and the text says so).
The texts of the Fossil Ants and AntCat list lines say nothing of living, extant or non-fossil names; every other
place page's texts are as they were.

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


def list_rule(body):
    # rule 2: one Valid row, before the Species row, on every page (no second place for it)
    assert body.count('<tr data-stat="valid"') == 1 and body.count('<tr data-stat="species"') == 1
    assert body.index('<tr data-stat="valid"') < body.index('<tr data-stat="species"'), "the Valid line is not before the Species line"
    # rules 3 and 4: the views come from the list (its default view; its Valid view), never a fixed name
    assert 'boolean ownViews = org.calacademy.antweb.StatusSet.hasOwnStatusSets(overview.getName());' in body
    assert 'String defaultView = org.calacademy.antweb.StatusSet.defaultChoice(overview);' in body
    assert ('String validView = ownViews ? org.calacademy.antweb.StatusSet.ownChoice(org.calacademy.antweb.StatusSet.LIST_VALID)'
            ' : org.calacademy.antweb.StatusSet.LIST_VALID;') in body
    assert 'String curatedView = "&statusSet=" + Html.url(defaultView);' in body
    assert '"species&statusSet=" + Html.url(validView)' in body
    # rule 3: on the lists with their own views the Valid number is the species total of that view, on the row and its link
    assert 'int validShown = ownViews ? speciesShown : overview.getValidSpeciesCount();' in body
    assert '<% if (validShown > 0) { %>\n<tr data-stat="valid" data-n="<%= validShown %>">' in body
    assert 'Formatter.commaFormat(validShown)' in body
    assert body.count("overview.getValidSpeciesCount()") == 1, "valid_species_count is shown somewhere besides the Valid line of the other lists"
    # the texts: the own-view lists' four lines say nothing of living, extant or non-fossil names; rule 4 in plain words
    texts = re.findall(r'String (subfamiliesDef|generaDef|validDef|speciesDef) = (.*?);\n', body, re.S)
    assert [name for name, _ in texts] == ["subfamiliesDef", "generaDef", "validDef", "speciesDef"], texts
    for name, code in texts:
        own = code.split("\n      ? ", 1)[1].split("\n      : ", 1)[0]
        other = code.split("\n      : ", 1)[1]
        for word in ("living", "extant", "non-fossil", "extinct", "Curated", "curated"):
            assert word not in own, f"{name}: the text of the lists with their own views says {word!r}: {own}"
        if name == "validDef":
            assert '"Currently valid, formally named species and subspecies on " + listName + ": its view " + Html.esc(validViewName)' in own, own
            assert "Currently valid, formally named, living (non-fossil) species and subspecies on" in other and "its view Valid (the number opens it)" in other
        if name == "speciesDef":
            assert "so this number equals Valid species / subspecies." in own, own
    assert 'String inDefaultView = " on " + listName + " in its default view, " + Html.esc(defaultViewName) + " (the number opens it)";' in body
    for old in ("placePageValidView", "Valid living species"):
        assert old not in body, old


def main():
    body = read("overview-body.jsp")
    rows = dict(re.findall(r'<tr data-stat="(\w+)"[^\n]*?><td class="ps_name">([^<]*)</td>', body))
    assert rows.get("species") == "Species / subspecies / morphospecies", rows.get("species")
    assert rows.get("valid") == "Valid species / subspecies", rows.get("valid")
    assert ">Species / subspecies<" not in body, "the old label is still on the place pages"
    list_rule(body)
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

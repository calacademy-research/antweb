#!/usr/bin/env python3
"""Place pages label the Species row "Species / subspecies / morphospecies" (Brian Fisher, 2026-10-29).

Every kind of place page (country, adm1, region, subregion, bioregion, museum, project) shows its "Taxonomic diversity"
table from one file, web/overview-body.jsp: the row data-stat="species" is labelled "Species / subspecies /
morphospecies" (the number counts valid names and morphospecies, the counting rules of 2026-10-28), the row
data-stat="valid" keeps "Valid species / subspecies", and every place page uses that file. The admin check number
under a genus list names the same label (web/taxonReportBody.jsp).

The rule of the list numbers (Brian Fisher, 2026-11-11, after the Fossil Ants page said "Valid species / subspecies 3"
beside a list of 868):
 1. Every number on a place page is the header of the list its link opens.
 2. The species lines are in the same order on every place page: first "Valid species / subspecies", then
    "Species / subspecies / morphospecies".
 3. "Valid species / subspecies" = the rows of the list's view Valid (statusSet=valid).
 4. "Species / subspecies / morphospecies" = the rows of the list's default view, Curated (statusSet=curated).
The same lines and links on every place page. The AntCat list and Fossil Ants have the same two views, counted with
their fossil names (StatusSet.OWN_SET_CHOICES; Fossil Ants 856 valid, 868 with its 12 morphospecies), so only texts
differ there: no "living (non-fossil)", no mention of the view All specimen names (they have none); their Valid number
is the stored list total of their view Valid, never valid_species_count. Every other place page's texts are as they
were.

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


OTHER_PAGES = {
    "subfamiliesDef": '"Subfamilies" + inDefaultView + ": valid subfamilies only. Specimens identified only to family and subfamily names not in AntCat are not counted."',
    "generaDef": ': valid genera, and morphogenera with a curated morphospecies here. Genera known here only from specimens identified to genus are listed under All specimen names and not counted.")',
    "validDef": '": its view Valid (the number opens it). Excludes morphospecies and unidentified taxa."',
    "speciesDef": ': valid names and curated morphospecies (morphospecies a curator has added to a list). Other morphospecies, names identified only to genus and names not in AntCat are listed under All specimen names and not counted here; their specimens are counted under Specimens.")',
}


def list_rule(body):
    # rule 2: one Valid row, before the Species row, on every page (no second place for it)
    assert body.count('<tr data-stat="valid"') == 1 and body.count('<tr data-stat="species"') == 1
    assert body.index('<tr data-stat="valid"') < body.index('<tr data-stat="species"'), "the Valid line is not before the Species line"
    # rules 3 and 4: the same links on every page (the two lists have the views valid and curated too)
    assert 'String curatedView = "&statusSet=" + org.calacademy.antweb.StatusSet.LIST_VETTED;' in body
    assert '"species&statusSet=" + org.calacademy.antweb.StatusSet.LIST_VALID' in body
    for cell in ('listBase + "subfamily" + curatedView', 'listBase + "genus" + curatedView', 'listBase + "species" + curatedView'):
        assert cell in body, cell
    # rule 3 on the two lists: the stored rows of their view Valid (the fourth list total); no line until it is counted;
    # valid_species_count (valid names that are not fossils) only on the other pages
    assert 'boolean ownViews = org.calacademy.antweb.StatusSet.hasOwnStatusSets(overview.getName());' in body
    assert ('int validShown = !ownViews ? overview.getValidSpeciesCount()\n'
            '      : viewTotals != null && viewTotals.length > 3 ? viewTotals[3] : -1;') in body
    assert '<% if (validShown > 0) { %>\n<tr data-stat="valid" data-n="<%= validShown %>">' in body
    assert 'Formatter.commaFormat(validShown)' in body
    assert body.count("overview.getValidSpeciesCount()") == 1, "valid_species_count is shown somewhere besides the Valid line of the other lists"
    # the texts: one per line; every other page's text as it was; on the two lists nothing of living, extant or
    # non-fossil names and no view they do not have
    assert 'String inDefaultView = " on " + listName + " in its default view, Curated (the number opens it)";' in body
    texts = dict(re.findall(r'String (subfamiliesDef|generaDef|validDef|speciesDef) = (.*?);\n', body, re.S))
    assert list(texts) == ["subfamiliesDef", "generaDef", "validDef", "speciesDef"], list(texts)
    for name, code in texts.items():
        assert OTHER_PAGES[name] in code, f"{name}: the text of the other place pages changed: {code}"
    assert "ownViews" not in texts["subfamiliesDef"], "the Subfamilies text is the same on every page"
    assert texts["validDef"].startswith('"Currently valid, formally named" + (ownViews ? "" : ", living (non-fossil)") + " species and subspecies on " + listName'), texts["validDef"]
    for name in ("generaDef", "speciesDef"):
        own = texts[name].split("(ownViews\n      ? ", 1)[1].split("\n      : ", 1)[0]
        for word in ("living", "extant", "non-fossil", "extinct", "All specimen names", "equals"):
            assert word not in own, f"{name}: the text of the AntCat list and Fossil Ants says {word!r}: {own}"
    assert '? ": valid genera, and the morphogenera on the list."' in texts["generaDef"]
    assert '? ": valid names and the morphospecies on the list."' in texts["speciesDef"]
    for old in ("placePageValidView", "Valid living species", "defaultViewName", "validViewName", "Valid (with fossils)\"",
                "so this number equals"):
        assert old not in body, old
    # the two lists' views (StatusSet): Valid, Curated, Valid extant, Valid fossil; Curated the default; Valid = status valid
    java = open(os.path.join(ROOT, "src/org/calacademy/antweb/StatusSet.java"), encoding="utf-8", newline="").read().replace("\r\n", "\n")
    assert "public final static String[] OWN_SET_CHOICES = {LIST_VALID, LIST_VETTED, VALID_EXTANT, VALID_FOSSIL};" in java
    own_choice = java.split("public static String ownChoice(String value) {", 1)[1].split("\n    }\n", 1)[0]
    assert own_choice.rstrip().endswith("return LIST_VETTED;"), own_choice
    own_set = java.split("static StatusSet ownStatusSet(String ownChoice) {", 1)[1].split("\n    }\n", 1)[0]
    assert "if (LIST_VALID.equals(ownChoice)) return new StatusSet(Status.VALID);" in own_set
    assert "if (LIST_VETTED.equals(ownChoice)) return new StatusSet(VALID_WITH_FOSSIL);" in own_set
    default = java.split("public static String defaultChoice(String placeType, String overviewName) {", 1)[1].split("\n    }\n", 1)[0]
    assert default.strip() == "return LIST_VETTED;", default
    # the fourth list total: counted and stored for the two lists only
    db = open(os.path.join(ROOT, "src/org/calacademy/antweb/home/PlaceViewTotalDb.java"), encoding="utf-8").read()
    assert 'public static final String VALID_SPECIES = "validspecies";' in db
    assert "storesValidSpecies(place) ? listRows(place, Rank.SPECIES, StatusSet.LIST_VALID) : -1);" in db
    assert "return storesValidSpecies(overview) ? 4 : 3;" in db


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

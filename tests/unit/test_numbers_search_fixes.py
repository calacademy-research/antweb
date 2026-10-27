"""Source checks of the 2026-10-27 fixes of search, collection and locality numbers (docs/numbers.md rows 65, 66, 69;
findings B50 to B53). They need no server or database; the end-to-end tests are in tests/e2e/test_numbers_search.py.

B50  After Compare or Map of a search by species, the header counts the species listed (the preparation's count),
     not the search's species (advancedSearchResultsByTaxon-body.jsp).
B51  The locality page lists one row per collection code (LocalityDb: group by the code, no "select distinct" over
     the habitat, method, coordinates and dates; specimens without a code are no collection).
B52  The collection page's "of M species" leaves out names not identified to genus (PlaceTotals.isIdentifiedToGenus).
B53  Map preparation reads coordinates as decimals (getDouble), not whole degrees (getInt).
B54  The species list of a search by species (AdvancedSearchResults.getSpeciesList; also the list of its Compare, Map
     and field guide) leaves out names not identified to genus; the page says how many specimens that is and links to
     the specimen view of the same search (the filtering itself: test/.../search/SearchSpeciesListTest.java).

Run: python3 tests/unit/test_numbers_search_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace") as f:
        return f.read().replace("\r\n", "\n")


def test_by_taxon_header_after_preparation():
    jsp = read("web/search/advancedSearchResultsByTaxon-body.jsp")
    assert 'request.getAttribute("resultSetModifier")' in jsp, "the by-taxon page ignores the preparation's count (B50)"
    i = jsp.index('request.getAttribute("resultSetModifier")')
    assert jsp.index('<h2 class="display_count"><%= resultSetModifier %>') > i, "the header is written before the prepared count is read"
    print("B50: the by-taxon header uses the count of Compare or Map preparation.")


def test_locality_lists_each_collection_once():
    src = read("src/org/calacademy/antweb/home/LocalityDb.java")
    body = src[src.index("String collectionQuery"):]
    body = body[:body.index("executeQuery(collectionQuery)")]
    assert "select distinct" not in body, "the collection query is a select distinct over several columns (B51)"
    assert "group by sp.collectioncode" in body, body[:600]
    assert "sp.collectioncode is not null" in body and "sp.collectioncode != ''" in body, body[:600]
    for column in ("habitat", "method", "decimal_longitude", "decimal_latitude", "collectedby", "microhabitat",
                   "datecollectedstart", "datecollectedend"):
        assert re.search(r"as " + column + r"\b", body), f"{column}: not an aggregate with its own name (Collection.makeCollectionFromRset reads it)"
    print("B51: one row per collection code on the locality page.")


def test_collection_species_are_identified_to_genus():
    jsp = read("web/collection-body.jsp")
    loop = jsp[jsp.index("HashSet speciesSet"):jsp.index("speciesCount = speciesSet.size()")]
    assert "isIdentifiedToGenus(resultItem.getGenus())" in loop, loop
    print("B52: the collection's species count leaves out names not identified to genus.")


def test_map_preparation_reads_decimal_coordinates():
    src = read("src/org/calacademy/antweb/search/PrepareMapResultsAction.java")
    body = src[src.index("private boolean hasGeoRefInfo"):]
    assert "getInt(" not in body, "hasGeoRefInfo reads coordinates as whole degrees (B53)"
    assert "getDouble(1)" in body and "getDouble(2)" in body and "wasNull()" in body, body[:1500]
    print("B53: map preparation reads coordinates as decimals.")


def test_search_species_list_leaves_out_names_not_identified_to_genus():
    src = read("src/org/calacademy/antweb/search/AdvancedSearchResults.java")
    body = src[src.index("public ArrayList<ResultItem> getSpeciesList()"):]
    body = body[:body.index("return specList;")]
    assert "if (!countsAsSpecies(thisItem)) continue;" in body, "getSpeciesList counts names not identified to genus (B54)"
    assert "PlaceTotals.isIdentifiedToGenus(item.getGenus())" in src, "countsAsSpecies is not the place pages' rule"
    # Every consumer takes the same list: the search page, Compare and Map preparations (field guide, Compare and Map
    # choose from the session's taxonList by index).
    for rel in ("src/org/calacademy/antweb/search/SearchAction.java",
                "src/org/calacademy/antweb/search/PrepareCompareResultsAction.java",
                "src/org/calacademy/antweb/search/PrepareMapResultsAction.java"):
        assert "getSpeciesList()" in read(rel), rel
    for rel in ("src/org/calacademy/antweb/search/FieldGuideResultsAction.java",
                "src/org/calacademy/antweb/search/CompareResultsAction.java"):
        assert 'session.getAttribute("taxonList")' in read(rel), rel
    action = read("src/org/calacademy/antweb/search/SearchAction.java")
    assert 'request.setAttribute("specimensNotIdentifiedToGenus", notIdentified)' in action
    assert "AdvancedSearchResults.specimenViewQuery(request.getParameterMap())" in action
    jsp = read("web/search/advancedSearchResultsByTaxon-body.jsp")
    note = jsp[jsp.index('id="not_identified_to_genus"'):]
    note = note[:note.index("</div>")]
    assert "not identified to genus (not counted as species), listed in the" in note, note
    assert "/advancedSearch.do?<%= org.calacademy.antweb.util.Html.escAttr(specimenViewQuery) %>" in note, note
    assert jsp.index("/aw-list, /aw-list-scroll") < jsp.index('id="not_identified_to_genus"'), "the note is not under the list"
    assert "preparedModifier == null" in jsp[jsp.index('getAttribute("specimensNotIdentifiedToGenus")'):jsp.index('id="not_identified_to_genus"')], \
        "the note shows on the Compare or Map preparation too"
    print("B54: the search's species list leaves out names not identified to genus and says how many specimens they have.")


def main():
    test_by_taxon_header_after_preparation()
    test_locality_lists_each_collection_once()
    test_collection_species_are_identified_to_genus()
    test_map_preparation_reads_decimal_coordinates()
    test_search_species_list_leaves_out_names_not_identified_to_genus()


if __name__ == "__main__":
    main()

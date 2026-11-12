"""The AntCat import files a name described from an island that is its own country under the island only (2026-11-12).

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent." Production, 2026-11-12: Ecuador's list still held 19 subspecies
described from the Galapagos, with no specimen, source AntCat: AntCat writes their type locality "Ecuador (Galapagos
Is)" and the import (SpeciesListUpload.importSpeciesList) filed a name under the text before the parenthesis.

AntcatLocalityTest (Java) tests the rule and AntcatIslandRowsDbTest the rows; neither runs the import itself (it needs
the whole export file and the taxa in memory). This test reads the source and fails when:
  1. the import stops asking the locality rule (GeolocaleMgr.getAntcatFiling) for the country of a name's list, or
     stops resolving the country before the parentheses the way it always did (GeolocaleMgr.getValidCountry: review of
     #347), or stops listing a place it does not know in its report;
  2. the rule gets a place rule of its own instead of asking the upload's (RecordPlace.of);
  3. the import's first step stops deleting every AntCat list row (that delete is how the rows of a political parent
     leave: no one-time removal was made).
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_bytes().decode("utf-8").replace("\r\n", "\n")


def code(src):
    """Java source without comments (strings here hold no comment marks that matter)."""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return "\n".join(line.split("//")[0] for line in src.split("\n"))


def test_the_import_asks_the_locality_rule_for_the_country():
    src = code(read("src/org/calacademy/antweb/upload/SpeciesListUpload.java"))
    start = src.index("if (thisCountry != null && !\"\".equals(thisCountry) && !isFossil) {")
    block = src[start:src.index("getMessageMgr().addToMessages(MessageMgr.countryNotFound", start)]
    # the country before the parentheses: the same call as before 2026-11-12, on the same text (review of #347)
    assert re.search(r"String tempCountry = AntcatLocality\.countryPart\(thisCountry\);\s*Country country = GeolocaleMgr\.getValidCountry\(tempCountry\);", block)
    assert block.count("GeolocaleMgr.getValidCountry(") == 1
    # then the place in the parentheses, for a country that was found
    asked = "AntcatLocality.Filing filing = GeolocaleMgr.getAntcatFiling(ownCountryParents, country, thisCountry);"
    assert asked in block and block.index("GeolocaleMgr.getValidCountry(tempCountry)") < block.index(asked)
    assert "country = filing.country;" in block
    assert "new GeolocaleTaxonDb(getConnection()).setTaxonSet(country, currentValidName, Source.ANTCAT);" in block
    assert block.index("country = filing.country;") < block.index("setTaxonSet(")
    assert block.count("setTaxonSet(") == 1, "a name is filed on one country's list"
    # a place the rule does not know, in a country with island countries: listed once in the report and counted
    assert re.search(r"if \(filing\.note != null && placesNotKnown\.add\(filing\.note\)\) \{\s*"
                     r"getMessageMgr\(\)\.addToMessages\(MessageMgr\.antcatPlaceNotKnown, Html\.esc\(filing\.note\)\);", block)
    assert "s_lastPlacesNotKnown = placesNotKnown.size();" in src
    messages = code(read("src/org/calacademy/antweb/upload/MessageMgr.java"))
    assert 'testList.add(new Test(antcatPlaceNotKnown, SET, "<b>" + ANTCAT_PLACE_NOT_KNOWN_HEADING + "</b>"));' in messages
    mgr = code(read("src/org/calacademy/antweb/util/GeolocaleMgr.java"))
    assert re.search(r"getAntcatFiling\(Set<String> parents, Country before, String locality\) \{\s*return org\.calacademy\.antweb\.upload\."
                     r"AntcatLocality\.filing\(countryNameMap, adm1CountryMap, adm1List, parents, before, locality\);", mgr)
    # getValidCountry is the lookup AntcatLocality.listCountry makes on a given map: the same function, the same map
    assert re.search(r"public static @Nullable Country getValidCountry\(String country\) \{\s*return validCountry\(countryNameMap, country\);", mgr)


def test_the_manager_file_keeps_its_line_endings():
    """GeolocaleMgr.java has CRLF line ends throughout; new lines must have them too (review of #347 saw CR characters
    in the diff: they are the file's own). No line with a bare LF or a bare CR."""
    data = (ROOT / "src/org/calacademy/antweb/util/GeolocaleMgr.java").read_bytes()
    assert data.count(b"\r\n") == data.count(b"\n") == data.count(b"\r") > 1000


def test_the_rule_is_the_uploads():
    src = code(read("src/org/calacademy/antweb/upload/AntcatLocality.java"))
    filing = src[src.index("public static Filing filing("):src.index("public static Country listCountry(")]
    assert "RecordPlace own = RecordPlace.of(countryNames, adm1s, adm1Rows, before.getName(), place);" in filing
    assert "if (own != null) return new Filing(before, own.place, null);" in filing
    # where a name is filed: no island test of its own (the note's question, which countries have island countries,
    # reads the flag in parentsOfOwnCountries and files nothing)
    for word in ("isIsland", "getIsland", "validIsland", "getCountry()"):
        assert word not in filing, word
    # the lookup on a given map (the tests) is the import's: validCountry of the text before the parentheses
    assert "GeolocaleMgr.validCountry(countryNames, countryPart(locality))" in src
    # no place names anywhere in the code
    for word in ("Galapagos", "Hawaii", "Ecuador", "Mauritius", "Rodrigues"):
        assert word not in src, word


def test_the_import_still_deletes_every_antcat_list_row_first():
    db = code(read("src/org/calacademy/antweb/home/SpeciesListUploadDb.java"))
    assert "query = \"delete from geolocale_taxon where source like '%\" + Source.ANTCAT + \"'\";" in db
    upload = code(read("src/org/calacademy/antweb/upload/SpeciesListUpload.java"))
    prepare = upload.index("getSpeciesListUploadDb().prepareDatabase(project);")
    load = upload.index("uploadDetails = importSpeciesList(project, uploadFile, accessGroupId);")
    assert prepare < load, "the AntCat rows are deleted before they are filed again"


def test_the_daily_import_says_so_in_its_log_line():
    """tests/e2e/test_island_countries.py waits for this line before it checks Ecuador's list."""
    java = re.search(r'ISLAND_LOG = "([^"]+)";', read("src/org/calacademy/antweb/upload/AntcatLocality.java")).group(1)
    py = re.search(r'^ANTCAT_ISLAND_LOG = "([^"]+)"$', read("tests/e2e/numbers_sample.py"), flags=re.M).group(1)
    assert java == py == "names of an island country filed under the island only (2026-11-12)"
    nightly = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    # the marker the e2e test reads, then the number of localities with a place that is not known
    assert ('"  AntCat import done (" + org.calacademy.antweb.upload.SpeciesListUpload.BIOREGION_VALID_LOG + ")"\n'
            '                        + " (" + org.calacademy.antweb.upload.AntcatLocality.ISLAND_LOG + "; "\n'
            '                        + org.calacademy.antweb.upload.AntcatLocality.placesNotKnownLog('
            'org.calacademy.antweb.upload.SpeciesListUpload.lastPlacesNotKnown()) + ")", true);') in nightly
    e2e = read("tests/e2e/test_island_countries.py")
    assert "if ns.antcat_island_names_filed() is not True:" in e2e and e2e.count('"formicinaecamponotus ') == 2
    # the skip says how to see the test run: the line comes from set 1 of the daily update, not from a hand-run import
    skip = e2e[e2e.index("if ns.antcat_island_names_filed() is not True:"):]
    skip = skip[:skip.index("ecuador = list_names(")]
    assert "after the next daily update" in skip and "worldantsFetchAndReload" in skip


def test_docs_say_where_a_name_from_antcat_is_filed():
    flow = read("docs/flows/antcat-to-antweb.md")
    assert "`upload/AntcatLocality`" in flow and "Ecuador (Galapagos Is)" in flow and "source like '%antcat'" in flow
    places = read("docs/flows/places.md")
    assert "**Names from AntCat** (2026-11-12)" in places and "AntcatIslandRowsDbTest" in places


if __name__ == "__main__":
    test_the_import_asks_the_locality_rule_for_the_country()
    test_the_manager_file_keeps_its_line_endings()
    test_the_rule_is_the_uploads()
    test_the_import_still_deletes_every_antcat_list_row_first()
    test_the_daily_import_says_so_in_its_log_line()
    test_docs_say_where_a_name_from_antcat_is_filed()
    print("ok")

"""The AntCat import files a name described from an island that is its own country under the island only (2026-11-12).

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent." Production, 2026-11-12: Ecuador's list still held 19 subspecies
described from the Galapagos, with no specimen, source AntCat: AntCat writes their type locality "Ecuador (Galapagos
Is)" and the import (SpeciesListUpload.importSpeciesList) filed a name under the text before the parenthesis.

AntcatLocalityTest (Java) tests the rule and AntcatIslandRowsDbTest the rows; neither runs the import itself (it needs
the whole export file and the taxa in memory). This test reads the source and fails when:
  1. the import stops asking GeolocaleMgr.getAntcatListCountry for the country of a name's list, or asks the old
     way (the valid country of the text before the parenthesis) again;
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
    assert "Country country = GeolocaleMgr.getAntcatListCountry(thisCountry);" in block
    assert "GeolocaleMgr.getValidCountry(" not in block, "the country of the text before the parenthesis is asked again"
    assert "new GeolocaleTaxonDb(getConnection()).setTaxonSet(country, currentValidName, Source.ANTCAT);" in block
    assert block.count("setTaxonSet(") == 1, "a name is filed on one country's list"
    # the report's "Country not found" still names the text before the parenthesis
    assert "String tempCountry = AntcatLocality.countryPart(thisCountry);" in block
    mgr = code(read("src/org/calacademy/antweb/util/GeolocaleMgr.java"))
    assert re.search(r"public static @Nullable Country getAntcatListCountry\(String locality\) \{\s*return org\.calacademy\.antweb\.upload\."
                     r"AntcatLocality\.listCountry\(countryNameMap, adm1CountryMap, adm1List, locality\);", mgr)


def test_the_rule_is_the_uploads():
    src = code(read("src/org/calacademy/antweb/upload/AntcatLocality.java"))
    assert "RecordPlace own = RecordPlace.of(countryNames, adm1s, adm1Rows, country.getName(), place);" in src
    assert "GeolocaleMgr.validCountry(countryNames, countryPart)" in src
    # no place names and no island test of its own
    for word in ("Galapagos", "Hawaii", "Ecuador", "isIsland", "getIsland", "validIsland"):
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
    assert ('"  AntCat import done (" + org.calacademy.antweb.upload.SpeciesListUpload.BIOREGION_VALID_LOG + ")"\n'
            '                        + " (" + org.calacademy.antweb.upload.AntcatLocality.ISLAND_LOG + ")", true);') in nightly
    e2e = read("tests/e2e/test_island_countries.py")
    assert "if ns.antcat_island_names_filed() is not True:" in e2e and e2e.count('"formicinaecamponotus ') == 2


def test_docs_say_where_a_name_from_antcat_is_filed():
    flow = read("docs/flows/antcat-to-antweb.md")
    assert "`upload/AntcatLocality`" in flow and "Ecuador (Galapagos Is)" in flow and "source like '%antcat'" in flow
    places = read("docs/flows/places.md")
    assert "**Names from AntCat** (2026-11-12)" in places and "AntcatIslandRowsDbTest" in places


if __name__ == "__main__":
    test_the_import_asks_the_locality_rule_for_the_country()
    test_the_rule_is_the_uploads()
    test_the_import_still_deletes_every_antcat_list_row_first()
    test_the_daily_import_says_so_in_its_log_line()
    test_docs_say_where_a_name_from_antcat_is_filed()
    print("ok")

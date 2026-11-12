"""An oceanic island is its own country in AntWeb (2026-11-11; RecordPlace, tools/place/island_countries.py,
docs/flows/places.md "Islands that are their own country").

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent." This round: Hawaii (political parent United States) and Galapagos
Islands (Ecuador). Until then a specimen of Hawaii was stored with country United States and island Hawaii, counted
for both, and a species found only on the Galapagos was on Ecuador's list too (so the Galapagos Islands page said
"Endemic 0").

Stages on a server, and each test skips until its stage is reached (the last test, 2026-11-12, waits for the first
daily AntCat import with the island rule: the names AntCat files in the Galapagos are then off Ecuador's list):
  1. the code is deployed: the parent's page names the island ("Hawaii has its own page and is not counted here.");
  2. the stored specimens are moved (tools/place/island_countries.py --apply): the API returns the island's specimens
     for country=<island>, each with the island as its country;
  3. the lists are rebuilt (the daily update, or the set 2 jobs of the runbook followed by a reload of the places in
     memory, which the pages read their numbers from): the island has endemic species. Staging, 2026-11-11: 10.

The forms a file can write (United States + adm1 Hawaii or Oahu, Ecuador + adm1 Galapagos or Galápagos, Mauritius +
adm1 Rodrigues) are tested at the upload's own level: test/org/calacademy/antweb/upload/IslandCountryTest.java.

Read-only GETs without a login; the last test also reads compute.log (staging, the admin test login).
"""
import re
from urllib.parse import quote_plus

import pytest

import numbers_sample as ns
from antweb import BASE_URL, TIMEOUT, new_session

ISLANDS = [("Hawaii", "United States"), ("Galapagos Islands", "Ecuador")]
NOTE = re.compile(r'<p id="own-islands-note">(.*?)</p>', re.S)


@pytest.fixture(scope="module")
def session():
    return new_session()


def page(session, path):
    r = session.get(BASE_URL + path, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, (path, r.status_code, r.text[:200])
    return r.text


def note_of(session, country):
    """The text of the line about the country's islands on its page, or None."""
    found = NOTE.search(page(session, "/country.do?name=" + quote_plus(country)))
    return found.group(1) if found else None


@pytest.fixture(scope="module")
def deployed(session):
    if note_of(session, "United States") is None:
        pytest.skip("islands as their own countries: not deployed on this host yet")


def island_specimens(session, island):
    """The API's specimens of country=<island> (up to 5), skipping while the stored specimens are not moved."""
    body = session.get(BASE_URL + "/v3.1/specimens?country=" + quote_plus(island) + "&limit=5&up=1", timeout=max(TIMEOUT, 120))
    assert body.status_code == 200, (island, body.status_code, body.text[:200])
    specimens = body.json().get("specimens") or []
    if not specimens:
        pytest.skip(f"{island}: the stored specimens are not moved yet (tools/place/island_countries.py --apply)")
    return specimens


@pytest.mark.parametrize("island,parent", ISLANDS)
def test_the_parents_page_names_the_island(session, deployed, island, parent):
    note = note_of(session, parent)
    assert note is not None, f"{parent}: no line about {island}"
    assert re.search(r'<a href="[^"]*/island\.do\?name=' + re.escape(quote_plus(island)) + r'"[^>]*>' + re.escape(island) + "</a>", note), note
    assert note.rstrip().endswith("has its own page and is not counted here."), note


def test_a_country_without_such_islands_has_no_line(session, deployed):
    """Madagascar has no flagged island; France's Reunion and Mayotte are plain countries (not flagged this round)."""
    assert note_of(session, "Madagascar") is None
    assert note_of(session, "France") is None


@pytest.mark.parametrize("island,parent", ISLANDS)
def test_the_islands_own_page_has_no_line(session, deployed, island, parent):
    text = page(session, "/island.do?name=" + quote_plus(island))
    assert island in text and "own-islands-note" not in text


@pytest.mark.parametrize("island,parent", ISLANDS)
def test_the_api_gives_the_islands_specimens_under_its_own_name(session, deployed, island, parent):
    """country=<island> returns its specimens (before the move: none), each with the island as its country."""
    for specimen in island_specimens(session, island):
        assert specimen["country"] == island, (specimen.get("specimenCode") or specimen.get("code"), specimen["country"])


@pytest.mark.parametrize("island,parent", ISLANDS)
def test_an_island_specimens_page_names_the_island_and_not_the_parent(session, deployed, island, parent):
    specimen = island_specimens(session, island)[0]
    code = specimen.get("specimenCode") or specimen.get("code")
    assert code, specimen
    text = page(session, "/specimen.do?code=" + quote_plus(code))
    place_lines = re.findall(r"<ul><li><b>(Country|Island):</b></li><li><a href='[^']*'>([^<]*)</a></li></ul>", text)
    assert place_lines == [("Island", island)], (code, place_lines)


def test_galapagos_islands_has_endemic_species(session, deployed):
    """A species found only on the Galapagos is on the island's list alone once the lists are rebuilt, so the page no
    longer says Endemic 0. (0 after the move and a daily update means: every such species is also on another country's
    list from another source; parent_list_rows.csv of the tool's report names them.)"""
    island_specimens(session, "Galapagos Islands")
    text = page(session, "/island.do?name=Galapagos+Islands")
    found = re.search(r'<tr data-stat="endemic" data-n="(\d+)"', text)
    assert found, "no Endemic row on the Galapagos Islands page"
    assert int(found.group(1)) > 0, "Galapagos Islands: Endemic 0"


# Production, 2026-11-12 (after the specimens were moved): these 19 subspecies, described from the Galapagos and with no
# specimen, were still on Ecuador's list, source AntCat, and on the Galapagos Islands list. AntCat writes their type
# locality "Ecuador (Galapagos Is)"; the import filed a name under the text before the parenthesis.
GALAPAGOS_SUBSPECIES = (
    ["formicinaecamponotus macilentus " + e for e in (
        "albemarlensis", "altinotus", "barringtonensis", "bindloensis", "castellanus", "duncanensis", "hoodensis", "jacobensis",
        "narboroensis", "pervicus", "sapphirinus", "vulcanalis", "wollebaeki")]
    + ["formicinaecamponotus planus " + e for e in (
        "fernandinensis", "fidelis", "hephaestus", "isabelensis", "pinzonensis", "sansalvadorensis")])


def list_names(session, country):
    """The taxon names of a country's list of valid species and subspecies."""
    html = page(session, "/taxonomicPage.do?rank=species&countryName=" + quote_plus(country) + "&statusSet=valid")
    return {row["name"] for row in ns.list_rows(html) if row["name"]}


def test_ecuadors_list_has_no_name_antcat_files_in_the_galapagos(session, deployed):
    """Brian Fisher's acceptance check: "Ecuador list has no Galapagos-only species". Since 2026-11-12 the AntCat import
    files a name whose type locality is the Galapagos under Galapagos Islands only; the rows it had put on Ecuador's
    list leave with the first daily import that runs the new code (it deletes every AntCat list row and files them
    again). Skips until compute.log has that import's line (staging; unreadable elsewhere). The line is written by the
    daily update's set 1 only: after an import run by hand (staging, 2026-11-12) the lists were right and this test
    still skipped; it runs after the next daily update."""
    if ns.antcat_island_names_filed() is not True:
        pytest.skip("compute.log has no line 'AntCat import done ... (" + ns.ANTCAT_ISLAND_LOG + ")', or cannot be read here (it is read "
                    "on staging only). Set 1 of the daily update (05:00) writes the line; an import run by hand "
                    "(utilData.do?action=worldantsFetchAndReload) does not. To see this test run: staging, after the next daily update.")
    ecuador = list_names(session, "Ecuador")
    assert len(ecuador) > 100, "Ecuador's list could not be read (" + str(len(ecuador)) + " names)"
    still = sorted(name for name in GALAPAGOS_SUBSPECIES if name in ecuador)
    assert not still, "on Ecuador's list although AntCat files them in the Galapagos: " + ", ".join(still)
    # they have not left AntWeb's lists: the island still has them
    galapagos = list_names(session, "Galapagos Islands")
    missing = sorted(name for name in GALAPAGOS_SUBSPECIES if name not in galapagos)
    assert not missing, "no longer on the Galapagos Islands list: " + ", ".join(missing)

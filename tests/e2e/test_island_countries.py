"""An oceanic island is its own country in AntWeb (2026-11-11; RecordPlace, tools/place/island_countries.py,
docs/flows/places.md "Islands that are their own country").

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its
specimens never count toward the political parent." This round: Hawaii (political parent United States) and Galapagos
Islands (Ecuador). Until then a specimen of Hawaii was stored with country United States and island Hawaii, counted
for both, and a species found only on the Galapagos was on Ecuador's list too (so the Galapagos Islands page said
"Endemic 0").

Three stages on a server, and each test skips until its stage is reached:
  1. the code is deployed: the parent's page names the island ("Hawaii has its own page and is not counted here.");
  2. the stored specimens are moved (tools/place/island_countries.py --apply): the API returns the island's specimens
     for country=<island>, each with the island as its country;
  3. the lists are rebuilt (the daily update, or the set 2 jobs of the runbook): the island has endemic species.

The forms a file can write (United States + adm1 Hawaii or Oahu, Ecuador + adm1 Galapagos or Galápagos, Mauritius +
adm1 Rodrigues) are tested at the upload's own level: test/org/calacademy/antweb/upload/IslandCountryTest.java.

Read-only GETs without a login.
"""
import re
from urllib.parse import quote_plus

import pytest

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

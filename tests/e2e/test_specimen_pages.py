"""Specimen and image pages: specimen.do, specimenImages.do, bigPicture.do, locality.do, collection.do and
the map page bigMap.do (road map step 2; docs/flows/specimen-and-image-pages.md).

Anonymous and read-only (GET only). The specimen is a georeferenced Camponotus specimen with images found
through the API; its images, locality, collection and map are reached by the links on its page. Pins the
answers to unknown codes: AntWeb's message page with HTTP 200. The Name History section is tested by
test_specimen_name_history.py, previous determinations by test_previous_determinations.py.
"""
import re

import pytest

from public_pages import (assert_message, assert_ok, fetch, h1, imaged_specimen, links, message, params,
                          specimen_page, title)
from antweb import BASE_URL, new_session

BIG_PICTURE = re.compile(r"bigPicture\.do\?name=([^&'\"]+)&(?:amp;)?shot=(\w+)&(?:amp;)?number=(\d+)")
MAP_CALL = "drawGoogleMap"
COLLECTIONS = re.compile(r"<h3>\s*(\d+)&nbsp;collections?\s*</h3>")


def _specimen():
    code = imaged_specimen()
    r, page = specimen_page(code)
    assert_ok(r, f"specimen.do?code={code}")
    return code, page


def test_specimen_page_sections():
    """Title "AntWeb Specimen: <Genus species> - <CODE>", the three data sections, and links to the
    specimen's images, its locality, its collection and its map."""
    code, page = _specimen()
    r, _ = specimen_page(code)
    t = title(r)
    assert t.startswith("AntWeb Specimen: ") and t.endswith(code.upper()), t
    for section in ("Locality Information", "Collection Information", "Specimen Information"):
        assert f"<h3>{section}: " in page, f"{code}: no {section} section"
    for label in ("Located at: ", "Owned by: ", "Collection code: "):
        assert f"<b>{label}</b>" in page, f"{code}: no {label!r}"
    assert any(params(p).get("name", "").lower() == code for p in links(page, "specimenImages")), f"{code}: no Images link"
    assert links(page, "locality"), f"{code}: no locality link"
    assert links(page, "collection"), f"{code}: no collection link"
    assert any(params(p).get("specimen", "").lower() == code for p in links(page, "bigMap")), f"{code}: no Map link"


def test_specimen_by_name_parameter():
    """specimen.do?name= is the same page as ?code= (older links), case does not matter."""
    code, _ = _specimen()
    r = fetch(f"/specimen.do?name={code.upper()}")
    assert_ok(r)
    assert title(r).endswith(code.upper()), title(r)


@pytest.mark.parametrize("path, expected", [
    ("/specimen.do?code=zznotaspecimen0001", "Specimen:zznotaspecimen0001 is not in the AntWeb database."),
    ("/specimenImages.do?code=zznotaspecimen0001", "Specimen:zznotaspecimen0001 is not in the AntWeb database."),
    ("/bigPicture.do?code=zznotaspecimen0001&shot=h&number=1", "Image not found for code:zznotaspecimen0001 shot:h number:1"),
    ("/bigPicture.do?code=casent0103190", "Must specify code and shot, or imageId."),
    ("/locality.do?code=zzNoSuchLocality0001", "Locality not found for name:"),
    ("/collection.do?name=zzNoSuchCollection0001", "Collection not found - name:zzNoSuchCollection0001."),
    ("/collection.do?name=zz%20No%20Such", "Unsatisfactory collection code name. Perhaps looking for"),
    ("/bigMap.do?taxonName=zznotataxon", "No map found for target:"),
])
def test_unknown_code_message(path, expected):
    assert_message(fetch(path), expected, path)


@pytest.mark.parametrize("path", ["/locality.do?code=zzNoSuchLocality0001", "/collection.do?name=zzNoSuchCollection0001",
                                  "/description.do?genus=camponotus&rank=zzbadrank"])
def test_not_found_message_has_no_request_details(path):
    """Finding F12 (fixed 2026-10-26): the request details (requestInfo, isBot) go to the log, not the page.
    The locality message added them when the request had a Referer."""
    s = new_session()
    s.headers["Referer"] = BASE_URL + "/"
    r = fetch(path, session=s)
    got = message(r) or ""
    assert got, f"{path}: not a message page"
    for debug in ("requestInfo", "RequestInfo", "isBot:", "execute()"):
        assert debug not in got, f"{path}: {debug!r} in {got[:300]!r}"


def test_big_picture_without_parameters_is_a_message():
    """Finding F21 (fixed 2026-10-26): was the home page (Struts form validation sent it to the input page)."""
    assert_message(fetch("/bigPicture.do"), "Must specify code and shot, or imageId.")


def test_specimen_images_page_and_image_page():
    """specimenImages.do lists the images (N Images); each opens bigPicture.do, "Closeup of the <shot> of
    Specimen <code>"."""
    code, _ = _specimen()
    r = fetch(f"/specimenImages.do?code={code}")
    assert_ok(r)
    assert title(r).startswith("Specimen: ") and title(r).endswith(f"{code.upper()} - AntWeb"), title(r)
    assert re.search(r'<h2 class="display_count">\d+ Images</h2>', r.text), "no 'N Images' heading"
    shots = BIG_PICTURE.findall(r.text)
    assert shots, f"{code}: no image links"
    name, shot, number = shots[0]
    assert name.lower() == code, shots[0]
    big = fetch(f"/bigPicture.do?name={name}&shot={shot}&number={number}")
    assert_ok(big)
    assert title(big).startswith("Closeup of the ") and code.upper() in title(big).upper(), title(big)
    assert links(big.text, "specimen") or f"specimen.do?name={code}" in big.text.lower(), "no link back to the specimen"


def test_locality_page_lists_the_specimens_collection():
    """locality.do: "Locality: <name>", "N collection(s)" (numbers.md #66) and a link to each collection,
    the specimen's own among them."""
    code, page = _specimen()
    loc = links(page, "locality")[0]
    coll = params(links(page, "collection")[0])["name"].lower()
    r = fetch(loc)
    assert_ok(r, loc)
    assert title(r) == "Locality - AntWeb", title(r)
    assert any(h.startswith("Locality: ") for h in h1(r.text)), h1(r.text)
    m = COLLECTIONS.search(r.text)
    assert m, f"{loc}: no 'N collections' heading"
    shown = {params(p).get("name", "").lower() for p in links(r.text, "collection")}
    assert 1 <= len(shown) <= int(m.group(1)), (m.group(1), len(shown))
    assert coll in shown, f"{loc}: collection {coll} of {code} not listed"


def test_collection_page_lists_the_specimen():
    """collection.do: "Collection: <code>" and the collection's specimens, the specimen among them."""
    code, page = _specimen()
    path = links(page, "collection")[0]
    r = fetch(path)
    assert_ok(r, path)
    assert title(r) == "Collection Search - AntWeb", title(r)
    coll = params(path)["name"]
    assert any(h.upper().startswith("COLLECTION: " + coll.upper()) for h in h1(r.text)), h1(r.text)
    codes = {(params(p).get("name") or params(p).get("code") or "").lower() for p in links(r.text, "specimen")}
    assert code in codes, f"{path}: {code} not listed ({len(codes)} specimens)"


def test_specimen_map():
    """bigMap.do?specimen=<code>: "Map of ..." with a drawn map."""
    code, page = _specimen()
    path = next(p for p in links(page, "bigMap") if params(p).get("specimen"))
    r = fetch(path)
    assert_ok(r, path)
    assert title(r).startswith("Map of "), title(r)
    assert MAP_CALL in r.text, f"{path}: no map drawn"


def test_specimen_map_is_the_same_when_reloaded():
    """Finding F9 (fixed 2026-10-26): the first request of a session mapped the specimen's whole species."""
    code, _ = _specimen()
    s = new_session()
    first = fetch(f"/bigMap.do?specimen={code}", session=s)
    second = fetch(f"/bigMap.do?specimen={code}", session=s)
    assert title(first) == title(second), (title(first), title(second))


@pytest.mark.parametrize("path", [
    "/bigMap.do?taxonName=formicinae&project=allantwebants",
    "/bigMap.do?countryName=Madagascar",
])
def test_taxon_and_place_maps(path):
    r = fetch(path)
    assert_ok(r, path)
    assert title(r).startswith("Map of "), title(r)
    assert MAP_CALL in r.text, f"{path}: no map drawn"


def test_map_needs_parameters():
    assert_message(fetch("/bigMap.do"), "Enter parameters for a map")


@pytest.mark.parametrize("path, expected", [
    ("/bigpicture.do?code=zznotaspecimen0001&shot=h&number=1", "Image not found for code:zznotaspecimen0001"),
    ("/specimenimages.do?code=zznotaspecimen0001", "Specimen:zznotaspecimen0001 is not in the AntWeb database."),
])
def test_lower_case_aliases(path, expected):
    """Old lower-case addresses forward to the same actions."""
    assert_message(fetch(path), expected, path)


def test_lower_case_map_alias():
    r = fetch("/bigmap.do?taxonName=formicinae&project=allantwebants")
    assert_ok(r)
    assert title(r).startswith("Map of "), title(r)

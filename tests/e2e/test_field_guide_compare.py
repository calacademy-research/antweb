"""Field guides, the image comparison and the map comparison of a taxon's children (road map step 2;
docs/flows/places.md, "Field guides" and "Compare tool").

These pages need a login (any login). Anonymous visitors get the explained log-in page (test_login_needed.py;
the two comparisons since 2026-10-26, finding F15, before that a message asking to log in). Logged in as the
staging test curator without upload rights (ANTWEB_TEST_NOUPLOAD_USER / _PASSWORD; skipped without them):
the field guide of a small genus and of a place, the image comparison and the map comparison of the genus
render. Each link is the one the site shows (the genus page's tools, the place list's Field Guides menu).
Read-only.
"""
import pytest

from public_pages import assert_ok, fetch, h1, links, params, small_genus, title
from staging_accounts import login, needs



@pytest.fixture(scope="module")
def curator():
    s, _, _ = login("noupload")
    return s


@pytest.fixture(scope="module")
def genus_page(curator):
    found = small_genus()
    if not found:
        pytest.skip("no genus found in the small subfamilies")
    subfamily, genus = found
    path = f"/description.do?genus={genus}&rank=genus"
    r = fetch(path, session=curator)
    assert_ok(r, path)
    return genus, r.text


def _tool_link(page, action):
    found = links(page, action)
    assert found, f"no {action}.do link on the genus page"
    return found[0]


@needs("noupload")
def test_field_guide_of_a_genus(curator, genus_page):
    genus, page = genus_page
    path = _tool_link(page, "fieldGuide")
    assert path.startswith("/fieldGuide.do?"), path
    assert params(path).get("genus", "").lower() == genus and params(path).get("rank") == "genus", path
    r = fetch(path, session=curator)
    assert_ok(r, path)
    assert title(r) == "Field Guide - AntWeb", title(r)
    assert "<h2>Field Guide " in r.text or any(h.startswith("Field Guide") for h in h1(r.text)), h1(r.text)
    assert genus.lower() in r.text.lower(), f"{path}: the genus is not on its field guide"


@needs("noupload")
def test_field_guide_of_a_place(curator):
    """The Field Guides menu of a country's list: the genera of Comoros."""
    lst = fetch("/taxonomicPage.do?rank=genus&countryName=Comoros", session=curator)
    assert_ok(lst)
    guides = [p for p in links(lst.text, "fieldGuide") if params(p).get("rank") == "genus"]
    path = guides[0] if guides else "/fieldGuide.do?countryName=Comoros&rank=genus"
    r = fetch(path, session=curator)
    assert_ok(r, path)
    assert title(r) == "Field Guide - AntWeb", title(r)
    assert "Field Guide of Genera in Comoros" in r.text, h1(r.text)


@needs("noupload")
def test_image_comparison_of_a_genus(curator, genus_page):
    genus, page = genus_page
    path = _tool_link(page, "getComparison")
    assert path.startswith("/getComparison.do?"), path
    r = fetch(path, session=curator)
    assert_ok(r, path)
    assert title(r) == "Comparison Tool - AntWeb", title(r)
    assert 'class="display_count">Comparison within' in r.text, f"{path}: no 'Comparison within' heading"


@needs("noupload")
def test_map_comparison_of_a_genus(curator, genus_page):
    genus, page = genus_page
    path = _tool_link(page, "mapComparison")
    assert path.startswith("/mapComparison.do?"), path
    r = fetch(path, session=curator)
    assert_ok(r, path)
    assert title(r) == "Map Comparison - AntWeb", title(r)
    assert "<h2>Map Comparison for " in r.text, f"{path}: no 'Map Comparison for' heading"

"""Edit Geolocale page (editGeolocale.do): the dead geodata tools are gone.

Flickr, Geonames, Geonet and Google APIs geodata fetching were removed (cleanup/geolocale). The page
now has two plain fields, "Centroid (lat, lon)" and "Bounding box (west lon, south lat, east lon,
north lat)", filled with the values the site uses and saved into the *Fixed columns.

Read-only: page loads only, as the test curator (no form is submitted), so it may run anywhere.
Target: /editGeolocale.do?id=19, or else the first geolocale linked from the Geolocale Manager.
A static check makes sure the removed utilData.do and editGeolocale.do sub-actions stay removed.
"""
import os
import re

import pytest

from antweb import REPO_ROOT, BASE_URL, TIMEOUT, classify, new_session, post_login

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

DEAD_WORDS = ("Flickr", "Geonames", "Google Apis", "Woe")

# Sub-actions removed with the importers (utilData.do?action=..., editGeolocale.do?action=...).
REMOVED_SUB_ACTIONS = (
    "fetchFlickrCountryData", "fetchFlickrAdm1Data", "fetchFlickrData", "fullGeodataFetch",
    "fetchGeonamesData", "fetchGeonamesCountryData", "fetchGeonamesAdm1Data", "fetchGeonetData",
    "fetchGeonetCentroidData", "fetchGoogleApisData", "geodataTestFetch", "updateAdm1FromCountryData",
    "removeFlickrData", "fetchGeoData", "removeGeoData",
)


def test_removed_geodata_sub_actions_stay_removed():
    """No source file or page still handles or links the removed geodata sub-actions."""
    found = []
    for top in ("src", "web", "WEB-INF"):
        for path in (REPO_ROOT / top).rglob("*"):
            if path.suffix not in (".java", ".jsp", ".xml") or not path.is_file():
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for name in REMOVED_SUB_ACTIONS:
                if re.search(r"""["'=]""" + name + r"""\b""", text):
                    found.append(f"{path.relative_to(REPO_ROOT)}: {name}")
    assert not found, found


@pytest.fixture(scope="module")
def session():
    if not USER or not PASSWORD:
        pytest.skip("needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")
    s = new_session()
    post_login(s, USER, PASSWORD)
    return s


def _edit_page(session):
    r = session.get(BASE_URL + "/editGeolocale.do?id=19", timeout=TIMEOUT)
    if classify(r).category == "ok" and "Edit Geolocale" in r.text:
        return r
    mgr = session.get(BASE_URL + "/geolocaleMgr.do", timeout=TIMEOUT)
    m = re.search(r"editGeolocale\.do\?id=(\d+)", mgr.text)
    if not m:
        pytest.skip(f"the test login can open no Edit Geolocale page: {classify(r)}")
    r = session.get(BASE_URL + f"/editGeolocale.do?id={m.group(1)}", timeout=TIMEOUT)
    if "Edit Geolocale" not in r.text:
        pytest.skip(f"the test login can open no Edit Geolocale page: {classify(r)}")
    return r


def test_edit_geolocale_page_has_coordinate_fields_and_no_dead_tools(session):
    r = _edit_page(session)
    assert r.status_code == 200, r.status_code
    page = r.text
    if 'name="centroidFixed"' not in page or "Centroid (lat, lon)" not in page:
        if "Flickr" in page:
            pytest.skip("this server still has the old Edit Geolocale page (change not deployed)")
    assert "Centroid (lat, lon)" in page
    assert "Bounding box (west lon, south lat, east lon, north lat)" in page
    assert re.search(r'<input[^>]*name="centroidFixed"', page), "no centroid field"
    assert re.search(r'<input[^>]*name="boundingBoxFixed"', page), "no bounding box field"
    for field in ("centroidFixed", "boundingBoxFixed"):
        tag = re.search(r'<input[^>]*name="' + field + r'"[^>]*>', page).group(0)
        assert "disabled" not in tag, f"{field} is disabled"
    # Whole words in the page without hidden inputs: the random CSRF token can contain
    # any three letters (a token with "Woe" in it failed this test once, 10-18).
    visible = re.sub(r"<input[^>]*type=['\"]hidden['\"][^>]*>", "", page, flags=re.I)
    for word in DEAD_WORDS:
        assert not re.search(r"\b" + re.escape(word) + r"\b", visible), \
            f'"{word}" is still on the Edit Geolocale page'
    assert "mapdevelopers.com" not in page

"""The public pages the e2e suite did not open yet (road map step 5; catalog section 5, public pages), anonymous and
read-only: each answers 200 with its own heading or content.

- World Ant Collections pages (basel, berlin, copenhagen, geneva, genoa, oxford): their heading names the city.
- The bioregion and country adm1 reports and their two text downloads (one "adm1<TAB>country" line each).
- API v3.1 documentation, the caste help popup, the liked pictures list.
- list.do?action=recentDescEdits, an old address of the Recent Description Edits page, gave a 500 (2026-11-03): it
  now redirects to /recentDescEdits.do.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, new_session

COLLECTIONS = ["/basel.do", "/berlin.do", "/copenhagen.do", "/geneva.do", "/genoa.do", "/oxford.do"]


def _get(path, allow_redirects=True):
    r = new_session().get(BASE_URL + path, timeout=max(TIMEOUT, 120), allow_redirects=allow_redirects)
    return r


@pytest.mark.parametrize("path", COLLECTIONS)
def test_world_ant_collection_page(path):
    r = _get(path)
    assert r.status_code == 200, (path, r.status_code)
    city = path.strip("/").split(".")[0].capitalize()
    assert "World Ants Collections" in r.text and city in r.text, f"{path}: no heading naming {city}"


@pytest.mark.parametrize("path, words", [
    ("/bioregionCountryList.do", ["Bioregion"]),
    ("/countryAdm1List.do", ["Country", "Adm1"]),
    ("/apiV3.1.do", ["AntWeb API", "/v3.1/"]),
    ("/casteDisplayPage.do", ["Caste"]),
    ("/list.do?action=pictureLikes", ["Liked Pictures"]),
])
def test_report_pages(path, words):
    r = _get(path)
    assert r.status_code == 200, (path, r.status_code)
    for w in words:
        assert w in r.text, f"{path}: '{w}' not on the page"


@pytest.mark.parametrize("path", [
    "/bioregionCountryList.do?action=acceptedAdm1s", "/bioregionCountryList.do?action=antwebAdm1s",
    "/countryAdm1List.do?action=acceptedAdm1s", "/countryAdm1List.do?action=antwebAdm1s",
])
def test_adm1_text_downloads(path):
    r = _get(path)
    assert r.status_code == 200, (path, r.status_code)
    lines = [ln for ln in r.text.splitlines() if ln.strip()]
    assert len(lines) > 1000, f"{path}: {len(lines)} lines"
    tabbed = [ln for ln in lines if ln.count("\t") >= 1]
    assert len(tabbed) > 0.9 * len(lines), f"{path}: lines are not adm1<TAB>country: {lines[:3]}"


def test_old_recent_desc_edits_address_redirects():
    r = _get("/list.do?action=recentDescEdits", allow_redirects=False)
    assert r.status_code in (301, 302, 303), f"/list.do?action=recentDescEdits answered {r.status_code}"
    assert "/recentDescEdits.do" in r.headers.get("Location", ""), r.headers.get("Location")
    page = _get("/list.do?action=recentDescEdits")
    assert page.status_code == 200 and "Recent Description Edits" in page.text, page.status_code

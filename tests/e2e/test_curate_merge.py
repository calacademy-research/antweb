"""Curate page cleanup, part 2 (2026-10): merged tools are reachable from their new place, the
Technical page is for administrators, renamed tools keep working, and Recalculate Your Museums is
open to curators for the museums of their own last upload.

Anonymous, read-only, any host: the Technical page asks for a login; the documentation page links
the taxon status help. As the test curator, staging only, changing nothing: the Technical page is
refused; Recalculate Your Museums without an upload in the session is refused before anything runs;
the Curate page shows the new names and "Your past upload files" opens the group's files.
Static: the Curate page links each merged tool once, from its new home only.
Java checks in CI: test/org/calacademy/antweb/security/CurateCleanupTest.java.
"""
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, REPO_ROOT, TIMEOUT, classify, get, new_session
import test_page_editor as pe

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


def _text(path):
    return re.sub(r"<!--.*?-->", "", (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace"), flags=re.S)


# Merged tools: (link text on the old Curate page, the page that now links it).
MOVED = [
    ("/geolocaleMgr.do?name=displayGeoregions", "web/curate/geolocale/geolocaleMgr-body.jsp"),
    ("/geolocaleMgr.do?name=displayBioregions", "web/curate/geolocale/geolocaleMgr-body.jsp"),
    ("/list.do?action=usrAdm\"", "web/curate/viewLogins-body.jsp"),
    ("/list.do?action=usrAdmLastLogin", "web/curate/viewLogins-body.jsp"),
    ("/dbStatus.do", "web/util/serverStatus-body.jsp"),
    ("/util.do?action=isRestart", "web/util/serverStatus-body.jsp"),
    ("name=lastSpecimenUpload", "web/util/listSpecimenUploads-body.jsp"),
    ("/list.do?action=pictureLikes", "web/imageLikes-body.jsp"),
    ("/statusDisplayPage.do", "web/web/homepage/documentation.jsp"),
    ("name=adminAlerts", "web/util/adminAlert.jsp"),
    ("/serverDebug.do", "web/curate/technical-body.jsp"),
    ("/sessionProps.do", "web/curate/technical-body.jsp"),
    ("/util.do?action=reloadAntwebMgr", "web/curate/technical-body.jsp"),
    ("/utilData.do?action=genRecentContent", "web/curate/technical-body.jsp"),
    ("/upload.do?action=runStatistics", "web/curate/technical-body.jsp"),
    ("/util.do?action=logQueryStats", "web/curate/technical-body.jsp"),
    ("/utilData.do?action=regenerateAllAntweb", "web/curate/technical-body.jsp"),
    ("/schedule.do?action=run", "web/curate/technical-body.jsp"),
]
CURATE = ("web/curate/curate-body.jsp", "web/curate/curatorLinks.jsp")
# Gone from the Curate page (merged elsewhere, or the same tool linked twice). The World (AntCat) upload
# reports link (name=worldantsUploads) came back for administrators on 2026-10-20 at Brian's request.
NOT_ON_CURATE = [m for m, _ in MOVED] + [
    "/pageTracker.do", "logMgr.do?action=get", "action=worldantsReload", "action=worldantsFetchAndReload",
    "/common/statusDisplayPage.jsp", "Manager:", "Artist Manager",
    "Unimaged Bolton Ants", "Casent -Dxx", ">Events<", "Validate Species List", "Archived Specimen List Files",
]


def test_merged_tools_have_a_new_home():
    missing = [f"{link} not on {page}" for link, page in MOVED if link not in _text(page)]
    assert not missing, missing


def test_curate_page_no_longer_links_merged_tools():
    found = [f"{p}: {x}" for p in CURATE for x in NOT_ON_CURATE if x in _text(p)]
    assert not found, found


def test_curate_page_new_names():
    text = _text("web/curate/curate-body.jsp") + _text("web/curate/curatorLinks.jsp")
    for name in ("Check names against AntCat", "Your past upload files", "Import AntCat names now",
                 "Images you uploaded for taxon page text", "Species List Tool", "Photographers",
                 "Curator activity log", "Species without images", "Specimen parts (-D codes) with different names",
                 "Taxa outside their native bioregions", "/technical.do", "Clear the warning"):
        assert name in text, name


def test_technical_page_needs_login():
    assert get(new_session(), "/technical.do").category == "login"


def test_documentation_links_taxon_status_help():
    r = new_session().get(BASE_URL + "/documentation.do", timeout=TIMEOUT)
    assert r.status_code == 200 and "statusDisplayPage.do" in r.text, r.status_code
    assert get(new_session(), "/statusDisplayPage.do").category == "ok"


@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s, info


@staging_only
def test_technical_page_refused_for_curator(curator):
    r = curator[0].get(BASE_URL + "/technical.do", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category in ("denied", "login"), classify(r)
    assert "Reload the in-memory lists" not in r.text


@staging_only
def test_museum_recount_needs_own_upload(curator):
    """No upload in this session: refused with an explanation, nothing is recounted."""
    s = curator[0]
    token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    r = s.post(f"{BASE_URL}/upload.do?_csrf={token}",
               files={"action": (None, "museumCalc"), "_csrf": (None, token)}, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert "Upload a specimen file first" in r.text, r.text[:300]
    assert "Only administrators" not in r.text and "Museums recounted" not in r.text


@staging_only
def test_curate_page_as_curator_new_names(curator):
    s, info = curator
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    html = r.text
    assert "Check names against AntCat" in html
    assert "Images you uploaded for taxon page text" in html
    m = re.search(r'href="([^"]*uploadHistory\.do\?editSpeciesList=specimen\d+)"', html)
    if m:  # shown to logins with the upload-specimens permission
        page = s.get(m.group(1) if m.group(1).startswith("http") else BASE_URL + m.group(1), timeout=TIMEOUT)
        assert page.status_code == 200 and classify(page).category not in ("login", "denied"), classify(page)

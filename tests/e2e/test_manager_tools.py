"""Manager Tools of the Curate page (review of 2026-10-21; docs/guides/manager-tools.md).

Museum Manager, Geolocale Manager (with Edit Geolocale and the Adm1 Manager), Project Manager (with the
project settings), Photographers, Admin Alert Manager, Bioregion Map Manager, Introduced Map Manager,
Query Manager and Log Manager. Every test only reads, or sends a request that is refused before anything
is done; no test changes data, also not on staging:

- anonymous (any host): each tool asks for a login (Photographers is public);
- test admin (staging; ANTWEB_TEST_ADMIN_USER): each page loads and explains itself (class mgr_help).
  The Introduced Map Manager is not loaded as admin: opening it adds empty rows for listed species
  that have none. The Admin Alert Manager is loaded without an action (removing alerts is a POST);
- test curator without admin rights (staging; ANTWEB_TEST_NOUPLOAD_USER): the admin tools are refused;
  the Geolocale and Bioregion Map Managers stay readable, and a save there with a valid form token is
  refused (the targets do not exist either: id 999999, genus "zz-e2e");
- test admin: a POST without the form token is refused (HTTP 403) on every tool that changes data
  (again with targets that do not exist).
Unit and database tests: test/org/calacademy/antweb/curate/ManagerToolsTest.java,
test/org/calacademy/antweb/home/ManagerToolsDbTest.java.
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import IS_STAGING, has, login, needs

# (url, what the page says) for the admin: every page has its explanation block.
ADMIN_PAGES = [
    ("/manageMuseum.do", "Museum Manager"),
    ("/viewMuseum.do", "Add a museum"),
    ("/geolocaleMgr.do", "Geolocale Manager"),
    ("/geolocaleMgr.do?georank=adm1&parent=Madagascar", "Geolocale Manager"),
    ("/projectMgr.do", "Project Manager"),
    ("/editProject.do?projectName=fossilants", "project's settings"),
    ("/artists.do", "Photographers credited on images"),
    ("/adminAlert.do", "Admin Alert Manager"),
    ("/bioregionMapMgr.do", "Genera Native Distribution Tool"),
    ("/query.do", "Query Manager"),
    ("/logMgr.do?action=list", "compute.log"),
]

# Admin tools: anonymous visitors get the log-in page, curators are refused.
ADMIN_ONLY = [
    "/manageMuseum.do", "/viewMuseum.do?code=CASC", "/projectMgr.do", "/editProject.do?projectName=fossilants",
    "/adminAlert.do", "/introducedMapMgr.do", "/query.do", "/logMgr.do?action=list",
]
# Curator tools (read-only for curators; saving needs an admin).
CURATOR_READABLE = ["/geolocaleMgr.do", "/bioregionMapMgr.do"]

# Changes, each with a target that does not exist. Without the form token: HTTP 403 from the filter.
CHANGES = [
    ("/viewMuseum.do", {"code": "ZZE2ENOSUCHMUSEUM", "name": "zz-e2e", "title": "zz-e2e"}),
    ("/viewMuseum.do", {"code": "ZZE2ENOSUCHMUSEUM", "step": "delete", "confirmDelete": "yes"}),
    ("/geolocaleMgr.do", {"id": "999999", "georank": "country"}),
    ("/editGeolocale.do", {"id": "999999", "isSubmit": "true", "name": "zz-e2e"}),
    ("/editGeolocale.do?id=999999&isDelete=true", {}),
    ("/editProject.do?projectName=zze2enosuchants", {"title": "zz-e2e"}),
    ("/editProject.do?action=delete&projectName=zze2enosuchants", {}),
    ("/artist.do", {"isEdit": "true", "id": "999999", "name": "zz-e2e"}),
    ("/artist.do", {"isRemove": "true", "id": "999999"}),
    ("/adminAlert.do?action=remove&id=0", {}),
    ("/bioregionMapMgr.do", {"taxonName": "zz-e2e", "isMalagasy": "on"}),
    ("/introducedMapMgr.do", {"taxonName": "zz-e2e", "isMalagasy": "on"}),
]


# ---------- anonymous ----------

@pytest.mark.parametrize("url", ADMIN_ONLY + CURATOR_READABLE + ["/editGeolocale.do?id=19"])
def test_anonymous_asked_to_log_in(url):
    assert get(new_session(), url).category == "login", url


def test_photographers_public():
    r = new_session().get(BASE_URL + "/artists.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category in ("ok", "error_page"), classify(r)
    assert 'class="mgr_help"' not in r.text, "the explanation is for curators"


# ---------- admin ----------

@pytest.fixture(scope="module")
def admin():
    if not (IS_STAGING and has("admin")):
        pytest.skip("needs ANTWEB_TEST_ADMIN_USER / _PASSWORD (staging only)")
    s, token, _id = login("admin")
    return s, token


@needs("admin")
@pytest.mark.parametrize("url,text", ADMIN_PAGES)
def test_admin_page_loads_and_explains_itself(admin, url, text):
    s, _token = admin
    r = s.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category in ("ok", "message"), f"{url}: {classify(r)}"
    if "currently in the process of an Upload" in r.text:
        pytest.skip(f"{url}: an upload is running on staging")
    assert 'class="mgr_help"' in r.text, f"{url}: no explanation block: {classify(r)} {r.text[:300]!r}"
    assert text in r.text, f"{url}: '{text}' missing"


@needs("admin")
def test_edit_geolocale_explains_itself(admin):
    s, _token = admin
    mgr = s.get(BASE_URL + "/geolocaleMgr.do?georank=country", timeout=TIMEOUT).text
    m = re.search(r"editGeolocale\.do\?id=(\d+)", mgr)
    assert m, "the Geolocale Manager links no place"
    r = s.get(BASE_URL + f"/editGeolocale.do?id={m.group(1)}", timeout=TIMEOUT)
    assert 'class="mgr_help"' in r.text and "Edit Geolocale" in r.text
    assert 'value="null"' not in r.text, "a missing value is shown as the text null"


@needs("admin")
def test_curate_page_explains_manager_tools(admin):
    s, _token = admin
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert "where each introduced species is native" in r.text
    assert "the bioregions where each genus is native" in r.text


@needs("admin")
def test_new_project_key_is_checked(admin):
    s, _token = admin
    r = s.get(BASE_URL + "/editProject.do?projectName=Not%20A%20Key", timeout=TIMEOUT)
    assert "No project" in r.text and "ending with" in r.text, classify(r)


@needs("admin")
@pytest.mark.parametrize("url,data", CHANGES)
def test_post_without_token_is_refused(admin, url, data):
    s, _token = admin
    r = s.post(BASE_URL + url, data=data, timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 403, f"{url}: {r.status_code}"


# ---------- curator without admin rights ----------

@pytest.fixture(scope="module")
def curator():
    if not (IS_STAGING and has("noupload")):
        pytest.skip("needs ANTWEB_TEST_NOUPLOAD_USER / _PASSWORD (staging only)")
    s, token, _id = login("noupload")
    return s, token


@needs("noupload")
@pytest.mark.parametrize("url", ADMIN_ONLY)
def test_admin_tools_refused_for_curator(curator, url):
    s, _token = curator
    outcome = get(s, url)
    assert outcome.category in ("login", "denied", "message"), f"{url}: {outcome}"


@needs("noupload")
@pytest.mark.parametrize("url", CURATOR_READABLE)
def test_curator_may_look(curator, url):
    s, _token = curator
    assert get(s, url).category == "ok", url


@needs("noupload")
def test_curator_save_refused_with_token(curator):
    s, token = curator
    r = s.post(BASE_URL + "/geolocaleMgr.do", timeout=TIMEOUT,
               data={"_csrf": token, "id": "999999", "georank": "country", "isValid": "on"})
    assert "Only an AntWeb administrator" in r.text, classify(r)
    r = s.post(BASE_URL + "/bioregionMapMgr.do", timeout=TIMEOUT,
               data={"_csrf": token, "taxonName": "zz-e2e", "isMalagasy": "on"})
    assert "Only an administrator can change the native bioregions" in r.text, classify(r)
    r = s.post(BASE_URL + "/editProject.do?projectName=zze2enosuchants", timeout=TIMEOUT,
               data={"_csrf": token, "title": "zz-e2e"})
    assert "Only an AntWeb administrator may change project settings" in r.text, classify(r)

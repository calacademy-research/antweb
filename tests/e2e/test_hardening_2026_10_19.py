"""Hardening and small fixes of 2026-10-19.

getSpeciesList.do, teamMember.do and the AntWiki date check were removed on 2026-10-19 (the
removed URLs are checked in test_check57_removals.py).

Anonymous (any host, read-only):
- page.do?name=x redirects to project.do?name=xants; a name that is not a plain project name
  answers "Page not found.".
Test curator (staging; ANTWEB_TEST_NOUPLOAD_USER, or ANTWEB_TEST_USER as fallback):
- sessionProps.do is refused.
- pictureLike.do for an unknown image answers "BigPicture not found." (no server error, nothing
  stored: the like is only written for an existing image).
Test admin (staging; ANTWEB_TEST_ADMIN_USER):
- sessionProps.do loads; its values are escaped.
Static checks of the source (any host): species list history has no delete-all branch, sessionProps
escapes its values, calcCaste runs one of group, specimen or all, the parentage repair checks for
null before use.
Unit tests: test/.../security/HardeningSmallFixesTest.java (page.do names).
"""
import os

import pytest

import test_page_editor as pe
from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, new_session
from staging_accounts import IS_STAGING, has, login

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def _src(path):
    return open(os.path.join(ROOT, path), encoding="utf-8").read()


# ---------- anonymous ----------

@pytest.mark.parametrize("name", ["california", "floridakeys"])
def test_page_redirects_to_project(name):
    r = new_session().get(f"{BASE_URL}/page.do?name={name}", timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code in (301, 302, 303), (r.status_code, r.text[:200])
    assert r.headers.get("Location", "").endswith(f"/project.do?name={name}ants"), r.headers.get("Location")


@pytest.mark.parametrize("name", ["x%26y%3D1", "..%2Fx", "a%20b", "%3Cb%3E"])
def test_page_refuses_other_names(name):
    r = new_session().get(f"{BASE_URL}/page.do?name={name}", timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 200, (r.status_code, r.headers.get("Location"))
    assert "Page not found." in r.text, r.text[:300]


# ---------- test curator ----------

def _curator_session():
    if IS_STAGING and has("noupload"):
        return login("noupload")[0]
    if not IS_PRODUCTION and pe.HOST in pe.ALLOWED_HOSTS and pe.USER and pe.PASSWORD:
        s = pe.login()
        if s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json().get("loggedIn"):
            return s
    pytest.skip("needs a staging test curator (ANTWEB_TEST_NOUPLOAD_USER or ANTWEB_TEST_USER)")


@pytest.fixture(scope="module")
def curator():
    return _curator_session()


@pytest.mark.parametrize("url", ["/sessionProps.do"])
def test_admin_pages_refuse_curator(curator, url):
    r = curator.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert classify(r).category in ("login", "denied"), (url, classify(r))
    assert "Session Attributes" not in r.text


def test_picture_like_unknown_image(curator):
    token = curator.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    r = curator.post(BASE_URL + "/pictureLike.do", timeout=TIMEOUT,
                     data={"code": "zzznosuchimage0000", "shot": "h", "number": "1", "_csrf": token})
    assert r.status_code < 500, (r.status_code, r.text[:300])
    assert "BigPicture not found." in r.text, r.text[:300]


# ---------- test admin ----------

@pytest.fixture(scope="module")
def admin():
    if not (IS_STAGING and has("admin")):
        pytest.skip("needs ANTWEB_TEST_ADMIN_USER / _PASSWORD (staging only)")
    return login("admin")[0]


def test_session_props_escaped(admin):
    marker = "<b>zz19esc</b>"
    admin.get(BASE_URL + "/search.do", params={"searchMethod": "quickSearch", "name": marker}, timeout=TIMEOUT)
    r = admin.get(BASE_URL + "/sessionProps.do", timeout=TIMEOUT)
    assert r.status_code == 200 and "Session Attributes" in r.text, r.text[:300]
    assert marker not in r.text


# ---------- source checks ----------

def test_species_list_history_has_no_delete_all():
    src = _src("src/org/calacademy/antweb/curate/speciesList/SpeciesListHistoryAction.java")
    assert "deleteAllLogs" not in src and "== -1" not in src
    for f in ("ProjTaxonLogDb.java", "GeolocaleTaxonLogDb.java"):
        assert "deleteAllLogs" not in _src("src/org/calacademy/antweb/home/" + f)


def test_session_props_escape():
    assert "Html.esc(session.getAttribute(name))" in _src("web/util/sessionProps-body.jsp")


def test_calc_caste_runs_one_scope():
    src = _src("src/org/calacademy/antweb/util/UtilDataAction.java")
    block = src[src.index('if (action.equals("calcCaste"))'):]
    block = block[:block.index("siteWarning")]
    # 2026-10-28 (audit triage th): a blank code no longer runs one specimen
    assert "} else if (code != null" in block


def test_parentage_repair_checks_null_first():
    src = _src("src/org/calacademy/antweb/home/GeolocaleTaxonDb.java")
    body = src[src.index("public String checkGeolocaleParentage("):src.index("public String checkTaxonParentage(")]
    assert "geolocale == null ||" in body
    assert "parentGeolocale != null && \"Chile\"" in body

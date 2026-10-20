"""Hardening and small fixes of 2026-10-19.

Anonymous (any host, read-only):
- getSpeciesList.do and teamMember.do are for administrators (log-in page, no file link, no email).
- page.do?name=x redirects to project.do?name=xants; a name that is not a plain project name
  answers "Page not found.".
Test curator (staging; ANTWEB_TEST_NOUPLOAD_USER, or ANTWEB_TEST_USER as fallback):
- getSpeciesList.do, teamMember.do and sessionProps.do are refused.
- pictureLike.do for an unknown image answers "BigPicture not found." (no server error, nothing
  stored: the like is only written for an existing image).
Test admin (staging; ANTWEB_TEST_ADMIN_USER):
- getSpeciesList.do shows the existing file without writing it; rebuild=true by GET only shows the
  confirm button (the slow rebuild itself is not run by the tests).
- teamMember.do and sessionProps.do load; values on sessionProps.do are escaped.
Static checks of the source (any host): species list history has no delete-all branch, the lookup
table statement uses bound parameters, sessionProps and the team member JSPs escape their values,
calcCaste runs one of group, specimen or all, the parentage repair checks for null before use.
Unit tests: test/.../security/HardeningSmallFixesTest.java (AntWiki date check, page.do names).
"""
import os
import re

import pytest

import test_page_editor as pe
from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, new_session
from staging_accounts import IS_STAGING, has, login

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def _src(path):
    return open(os.path.join(ROOT, path), encoding="utf-8").read()


# ---------- anonymous ----------

def test_species_list_file_admin_only_anonymous():
    r = new_session().get(BASE_URL + "/getSpeciesList.do?name=allantwebants", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (r.status_code, classify(r))
    assert "Save Link As" not in r.text and "Write the file again" not in r.text


def test_team_member_admin_only_anonymous():
    r = new_session().get(BASE_URL + "/teamMember.do", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (r.status_code, classify(r))
    assert "Curators</h1>" not in r.text


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


@pytest.mark.parametrize("url", ["/getSpeciesList.do?name=allantwebants", "/teamMember.do", "/sessionProps.do"])
def test_admin_pages_refuse_curator(curator, url):
    r = curator.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert classify(r).category in ("login", "denied"), (url, classify(r))
    assert "Save Link As" not in r.text and "Session Attributes" not in r.text and "Curators</h1>" not in r.text


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


def test_species_list_admin_sees_file_without_rebuild(admin):
    r = admin.get(BASE_URL + "/getSpeciesList.do?name=allantwebants", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert "Write the file again" in r.text, r.text[:500]
    r = admin.get(BASE_URL + "/getSpeciesList.do?name=allantwebants&rebuild=true", timeout=TIMEOUT)
    assert r.status_code == 200 and 'method="post"' in r.text and "Run it" in r.text, r.text[:500]


def test_team_member_admin(admin):
    r = admin.get(BASE_URL + "/teamMember.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    assert "Curators</h1>" in r.text


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


def test_lookup_statement_binds_values():
    src = _src("src/org/calacademy/antweb/home/AntwebDb.java")
    body = src[src.index("public boolean isCurrentInLookup("):]
    body = body[:body.index("return false;\n    }") if "return false;\n    }" in body else body.index("return false;\r\n    }")]
    assert "prepareStatement" in body and "+ value" not in body and "+ key" not in body


def test_session_props_and_team_member_escape():
    assert "Html.esc(session.getAttribute(name))" in _src("web/util/sessionProps-body.jsp")
    tm = _src("web/curate/teamMember.jsp")
    assert re.search(r"Html\.esc\(teamMemberA\.getText\(\)\)", tm) and "isAdmin(request)" in tm
    assert not re.search(r"<%= teamMemberA\.get(?!Id\(\))", tm)
    assert not re.search(r"<%= teamMemberEdit\.get(?!Id\(\))", _src("web/curate/teamMemberForm.jsp"))


def test_calc_caste_runs_one_scope():
    src = _src("src/org/calacademy/antweb/util/UtilDataAction.java")
    block = src[src.index('if (action.equals("calcCaste"))'):]
    block = block[:block.index("siteWarning")]
    assert "} else if (code != null) {" in block


def test_parentage_repair_checks_null_first():
    src = _src("src/org/calacademy/antweb/home/GeolocaleTaxonDb.java")
    body = src[src.index("public String checkGeolocaleParentage("):src.index("public String checkTaxonParentage(")]
    assert "geolocale == null ||" in body
    assert "parentGeolocale != null && \"Chile\"" in body

"""Hardening batch of 2026-10-18.

- API v3.1: ndjson replies are complete JSON lines; the record limit is 10,000 per request for
  JSON and ndjson (larger sets: page with offset); 500 since 2026-10-21 (test_api.py).
  Anonymous, read-only, any host.
- editLogin.do: changing the email of your own login needs your current password (as a new
  password does). As the test curator, staging only; every request is refused and the stored
  email is checked to be unchanged. Skipped where the change is not deployed (form label).
- The request-error page escapes the request URL (static check of SessionRequestFilter).
- upload.do?action=genAll needs a login (any host, anonymous, refused before anything runs);
  allSpecimenFiles is for administrators (test curator, staging, only where the change is
  deployed); getSearchList.do needs a login; introducedMapMgr.do is for administrators;
  the removed upload.do sub-actions (reloadSpecimenList, removeSpecimenList, uploadSpeciesList)
  answer "Action not found." (anonymous: log-in page; test curator: only where the change is
  deployed, nothing runs). Removed mappings (speciesListMove, speciesListToolAdd): test_actions.py.
Other parts of the batch are checked in CI (.github/workflows/tests.yaml): ImageMagick policy,
API container, clearDisk.py, Java unit tests (test/.../security/SecurityBatch7Test.java); the
image upload ownership rule in test_image_upload.py.
"""
import html
import json
import os
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, new_session
import test_page_editor as pe

API_TIMEOUT = max(TIMEOUT, 120)
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


# ---------- API ----------

def test_api_ndjson_lines_are_json():
    r = new_session().get(BASE_URL + "/v3.1/specimens?genus=camponotus&limit=3&ndjson=true&up=1", timeout=API_TIMEOUT)
    assert r.status_code == 200, r.status_code
    lines = [x for x in r.text.split("\n") if x]
    assert 0 < len(lines) <= 6 and len(lines) % 2 == 0, len(lines)
    for i, line in enumerate(lines):
        obj = json.loads(line)
        if i % 2 == 0:
            assert obj["index"]["_id"] == i // 2, obj
        else:
            assert obj.get("specimenCode"), obj


def test_api_limit_is_capped():
    r = new_session().get(BASE_URL + "/v3.1/specimens?genus=zzznotagenus&limit=50000&up=1", timeout=API_TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert r.json()["metaData"]["limit"] in (500, 10000)   # 500 where the 2026-10-21 cap is deployed
    r = new_session().get(BASE_URL + "/v3.1/specimens?genus=zzznotagenus&limit=1000000&ndjson=true&up=1",
                          timeout=API_TIMEOUT)
    assert r.status_code == 200 and r.text.strip() == "", (r.status_code, r.text[:200])


# ---------- own email change needs the current password ----------

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

FIELD = re.compile(r'<input[^>]*\bname="(name|email|firstName|lastName)"[^>]*\bvalue="([^"]*)"')


def _form(session, login_id):
    r = session.get(f"{BASE_URL}/editLogin.do?id={login_id}", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    return r.text, {k: html.unescape(v) for k, v in FIELD.findall(r.text)}


@pytest.fixture(scope="module")
def own_login():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    page, fields = _form(s, info["loginId"])
    if "needed to change your password or email" not in page:
        pytest.skip("the email check is not deployed on this server")
    assert fields.get("name"), fields   # the test login may have no email address
    return s, info["loginId"], fields


@staging_only
@pytest.mark.parametrize("current", [None, "wrong-current-password"])
def test_own_email_change_needs_current_password(own_login, current):
    s, login_id, fields = own_login
    token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    data = dict(fields, isSubmit="true", _csrf=token, email="e2e-email-change-check@calacademy.org",
                password="", retypePassword="")
    if current is not None:
        data["currentPassword"] = current
    r = s.post(f"{BASE_URL}/editLogin.do?id={login_id}", data=data, timeout=TIMEOUT)
    try:
        assert r.status_code == 200, r.status_code
        assert "To change your email, please enter your current password." in r.text, r.text[:500]
    finally:
        _page, now = _form(s, login_id)
        if now.get("email") != fields["email"]:
            # Must not happen; put the address back (needs the real current password now).
            token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
            s.post(f"{BASE_URL}/editLogin.do?id={login_id}", timeout=TIMEOUT,
                   data=dict(fields, isSubmit="true", _csrf=token, currentPassword=pe.PASSWORD,
                             password="", retypePassword=""))
            pytest.fail("the email was changed without the current password")


@staging_only
def test_own_unchanged_email_needs_no_password(own_login):
    """Saving the form without changing the email (or password) works as before."""
    s, login_id, fields = own_login
    token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    data = dict(fields, isSubmit="true", _csrf=token, password="", retypePassword="")
    r = s.post(f"{BASE_URL}/editLogin.do?id={login_id}", data=data, timeout=TIMEOUT)
    assert r.status_code == 200 and "User Account Saved." in r.text, r.text[:500]
    assert _form(s, login_id)[1] == fields


# ---------- error page ----------

def test_error_page_escapes_request():
    src = open(os.path.join(ROOT, "src/org/calacademy/antweb/util/SessionRequestFilter.java"), encoding="utf-8").read()
    for line in src.splitlines():
        if "<" in line and "target" in line and "+ target" in line.replace("+target", "+ target"):
            assert "Html.esc(" in line, line.strip()


# ---------- upload.do jobs, search download, introduced map ----------

def _gen_all_needs_login():
    r = new_session().get(BASE_URL + "/upload.do?action=genAll", timeout=TIMEOUT)
    return r, classify(r).category == "login"


def test_upload_gen_all_needs_login():
    r, refused = _gen_all_needs_login()
    assert r.status_code < 500 and refused, (r.status_code, classify(r))


def test_search_list_needs_login():
    r = new_session().get(BASE_URL + "/getSearchList.do", timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert "search.txt" not in r.text
    assert "Please log in to download search results." in r.text, r.text[:300]


@staging_only
def test_all_specimen_files_admin_only(own_login):
    """Refused to curators; removed altogether in the Curate page cleanup ("Action not found.")."""
    if not _gen_all_needs_login()[1]:
        pytest.skip("the upload.do checks are not deployed on this server")
    s = own_login[0]
    r = s.get(BASE_URL + "/upload.do?action=allSpecimenFiles", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert ("Only administrators can run this upload action." in r.text or "Action not found." in r.text), r.text[:300]


@staging_only
def test_introduced_map_admin_only(own_login):
    r = own_login[0].get(BASE_URL + "/introducedMapMgr.do", timeout=TIMEOUT)
    assert classify(r).category == "login", classify(r)


REMOVED_UPLOAD_ACTIONS = ["reloadSpecimenList", "removeSpecimenList", "uploadSpeciesList"]


@pytest.mark.parametrize("action", REMOVED_UPLOAD_ACTIONS)
def test_removed_upload_actions_anonymous(action):
    r = new_session().get(f"{BASE_URL}/upload.do?action={action}&groupId=1", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (r.status_code, classify(r))


@staging_only
@pytest.mark.parametrize("action", REMOVED_UPLOAD_ACTIONS)
def test_removed_upload_actions_not_found(own_login, action):
    """Removed sub-actions (unused tools): "Action not found." for a logged-in curator, GET or POST."""
    if not _gen_all_needs_login()[1]:
        pytest.skip("the upload.do checks are not deployed on this server")
    s = own_login[0]
    r = s.get(f"{BASE_URL}/upload.do?action={action}&groupId=1", timeout=TIMEOUT)
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]
    token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    r = s.post(f"{BASE_URL}/upload.do?_csrf={token}", files={"action": (None, action),
               "groupId": (None, "1"), "_csrf": (None, token)}, timeout=TIMEOUT)
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]

"""The login form (Login Manager, open a login: viewLogin.do, saved by saveLogin.do), reworked 2026-10-20.

Anonymous (any host, read-only):
- viewLogin.do asks for a login.
Test admin (staging; ANTWEB_TEST_ADMIN_USER), read-only:
- another login's page has the sections Account, What this person can do, Upload group, Places
  and projects this curator can edit, Other settings; the administrator warning; the upload group
  list with "No upload group" and the test upload group labelled "name (short name) - group N";
  the add boxes with their lists; the form token; none of the old fields.
- the admin's own page does not offer to remove their own administrator rights.
- a POST without the form token is refused (HTTP 403); a GET of saveLogin.do changes nothing.
  (The POST is a password change with two different passwords, so even if it got through, it
  would be refused before anything is stored.)
Test curator without admin rights (staging; ANTWEB_TEST_NOUPLOAD_USER), read-only:
- another login's page is refused; their own page shows account and password, and their rights
  only as text.
No test here saves a login. Unit and database tests: test/org/calacademy/antweb/curate/LoginEdit*Test.java.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import GROUP_ABBREV, GROUP_NAME, IS_STAGING, has, login, needs


def test_anonymous_gets_login_page():
    assert get(new_session(), "/viewLogin.do?id=1").category == "login"


@pytest.fixture(scope="module")
def admin():
    if not (IS_STAGING and has("admin")):
        pytest.skip("needs ANTWEB_TEST_ADMIN_USER / _PASSWORD (staging only)")
    return login("admin")


@pytest.fixture(scope="module")
def curator():
    if not (IS_STAGING and has("noupload")):
        pytest.skip("needs ANTWEB_TEST_NOUPLOAD_USER / _PASSWORD (staging only)")
    return login("noupload")


@needs("admin")
@needs("noupload")
def test_admin_sees_sections(admin, curator):
    s, _token, _admin_id = admin
    _c, _ct, curator_id = curator
    r = s.get(f"{BASE_URL}/viewLogin.do?id={curator_id}", timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    for text in ("<h2>Account</h2>", "<h2>What this person can do</h2>", "<h2>Upload group</h2>",
                 "<h2>Places and projects this curator can edit</h2>", "<h2>Other settings</h2>",
                 "Upload specimen data", "Upload specimen images", "Give only to AntWeb staff",
                 'id="adminWarning"', '<option value="none"', "No upload group",
                 f"{GROUP_NAME} ({GROUP_ABBREV}) — group", 'list="projectOptions"', '<datalist id="countryOptions">',
                 'name="_csrf"', 'method="POST"', 'name="editForm" value="2"', "Save changes",
                 "/addUploader.do\">Add an uploader</a>", "Login - AntWeb"):
        assert text in r.text, f"'{text}' not on the login page"
    for old in ("Is Super Admin", "Upload As (comma separated", "multiple size", "hold the Ctrl key", "orange_done.gif"):
        assert old not in r.text, f"old field '{old}' still on the login page"


@needs("admin")
def test_own_admin_rights_locked(admin):
    s, _token, admin_id = admin
    r = s.get(f"{BASE_URL}/viewLogin.do?id={admin_id}", timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    assert "Administrator: yes" in r.text and "cannot remove your own administrator rights" in r.text
    assert 'type="checkbox" name="isAdmin"' not in r.text
    assert 'name="delete"' not in r.text


@needs("admin")
@needs("noupload")
def test_post_without_token_refused(admin, curator):
    s, _token, _id = admin
    _c, _ct, curator_id = curator
    r = s.post(BASE_URL + "/saveLogin.do", timeout=TIMEOUT, allow_redirects=False,
               data={"id": str(curator_id), "changePassword": "Change Password",
                     "password": "never-stored-1", "retypePassword": "never-stored-2"})
    assert r.status_code == 403, r.status_code


@needs("admin")
@needs("noupload")
def test_get_does_not_save(admin, curator):
    s, _token, _id = admin
    _c, _ct, curator_id = curator
    r = s.get(f"{BASE_URL}/saveLogin.do?id={curator_id}", timeout=TIMEOUT)
    assert "Please use the form to change a login." in r.text, classify(r)


@needs("noupload")
@needs("admin")
def test_curator_refused_other_login(curator, admin):
    c, _token, _id = curator
    _a, _at, admin_id = admin
    outcome = get(c, f"/viewLogin.do?id={admin_id}")
    assert outcome.category in ("message", "denied", "login"), outcome


@needs("noupload")
def test_curator_own_page(curator):
    c, _token, curator_id = curator
    r = c.get(f"{BASE_URL}/viewLogin.do?id={curator_id}", timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    assert "<h1>Your login</h1>" in r.text and 'name="currentPassword"' in r.text
    assert "Upload specimen data: no" in r.text
    for control in ('name="isAdmin"', 'name="isUploadSpecimens"', 'name="groups"', 'name="uploadAs"', 'name="addProjects"'):
        assert control not in r.text, f"{control} on a curator's own page"

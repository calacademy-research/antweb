"""Add an uploader (addUploader.do) and the "Upload group" wording of the group pages (2026-10-20).

Anonymous (any host, read-only):
- addUploader.do asks for a login.
Test admin (staging; ANTWEB_TEST_ADMIN_USER):
- the page loads with its labels (Upload group, existing or new, AntCat institution hint) and
  lists the test upload group; the Curate page links "Add an uploader" and "Upload groups";
  the upload group pages say "Upload group".
- a POST with incomplete input changes nothing and says so; a POST without the form token is
  refused (HTTP 403). No test here creates a login or a group.
Test curator without admin rights (staging; ANTWEB_TEST_NOUPLOAD_USER):
- the page is refused, also a POST with a valid form token.
Unit and database tests: test/org/calacademy/antweb/curate/UploaderOnboarding*Test.java.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import GROUP_NAME, IS_STAGING, has, login, needs


def test_anonymous_gets_login_page():
    assert get(new_session(), "/addUploader.do").category == "login"


# ---------- admin ----------

@pytest.fixture(scope="module")
def admin():
    if not (IS_STAGING and has("admin")):
        pytest.skip("needs ANTWEB_TEST_ADMIN_USER / _PASSWORD (staging only)")
    s, token, _id = login("admin")
    return s, token


@needs("admin")
def test_page_loads_with_labels(admin):
    s, _token = admin
    r = s.get(BASE_URL + "/addUploader.do", timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    for text in ("Add an uploader", "Their upload group", "Existing upload group", "New upload group",
                 "AntCat's list of institutions", "an upload replaces all specimens of the uploader's",
                 'name="_csrf"', GROUP_NAME):
        assert text in r.text, f"'{text}' not on addUploader.do"
    assert 'method="POST"' in r.text


@needs("admin")
def test_curate_page_links(admin):
    s, _token = admin
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert "/addUploader.do\">Add an uploader</a>" in r.text
    assert "/viewGroups.do\">Upload groups</a>" in r.text
    assert "Group Manager" not in r.text


@needs("admin")
def test_group_pages_say_upload_group(admin):
    s, _token = admin
    r = s.get(BASE_URL + "/viewGroups.do", timeout=TIMEOUT)
    assert "<h1>Upload groups</h1>" in r.text and "Add an uploader" in r.text
    assert "<title>Upload groups" in r.text or "Upload groups - AntWeb" in r.text
    r = s.get(BASE_URL + "/viewGroup.do", timeout=TIMEOUT)
    assert classify(r).category == "ok" and "<h1>Upload groups</h1>" in r.text, "viewGroup.do without id: the list"
    r = s.get(BASE_URL + "/viewGroup.do?id=999999", timeout=TIMEOUT)
    assert "not found" in r.text and classify(r).category not in ("error_page", "server_error", "stack_trace")


@needs("admin")
def test_incomplete_input_changes_nothing(admin):
    s, token = admin
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": token, "step": "save", "email": "not-an-address@nowhere", "groupId": "new",
                     "newGroupName": "", "newGroupAbbrev": ""})
    assert classify(r).category == "ok", classify(r)
    assert "Nothing was changed" in r.text
    assert "is not a valid email address" in r.text
    assert "Add an uploader: done" not in r.text


@needs("admin")
def test_post_without_token_is_refused(admin):
    s, _token = admin
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT, allow_redirects=False,
               data={"step": "save", "email": "x@example.org", "groupId": "new", "newGroupName": "X", "newGroupAbbrev": "X"})
    assert r.status_code == 403, r.status_code


# ---------- curator without admin rights ----------

@needs("noupload")
def test_refused_for_curator():
    s, token, _id = login("noupload")
    outcome = get(s, "/addUploader.do")
    assert outcome.category in ("login", "denied"), outcome
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": token, "step": "save", "email": "x@example.org", "groupId": "new",
                     "newGroupName": "ZZ never made", "newGroupAbbrev": "zzNever"})
    assert classify(r).category in ("login", "denied"), classify(r)
    assert "Add an uploader: done" not in r.text and "Nothing was changed" not in r.text

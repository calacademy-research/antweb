"""Page text of project, geolocale (region, country, adm1), museum and bioregion pages: one editor.

Since October 2026 every page text on AntWeb is edited with the same TinyMCE editor and saved through
the same safety checks (DescriptionAction.saveDescriptionEdit, DescEditSafety): taxon, specimen and
these "overview" pages. The old duplicate editors (the "Page Copy" box on editProject.do that was
never saved, saveProject.do, the simple content editor, the home page editor) were removed; the
project and geolocale settings forms now link to the page editor instead.

What is checked (none of these writes anything):
- anonymous: the settings forms ask for a login, the pages show no editor, and an anonymous save
  of page text is refused with the text given back;
- logged in (test curator): the settings forms link to the page editor and have no page-text box;
  saves the editor did not mark ready, or with a stale loadedHash, are refused and change nothing;
- in a real browser: test_browser_overview_editor.py.

No write test: there is no approved test-safe project or geolocale field (the approved write
targets are the taxon and specimen fields in test_page_editor.py). Staging or a local server only,
never production. Targets can be changed with ANTWEB_EDIT_TEST_PROJECT / ANTWEB_EDIT_TEST_COUNTRY.
"""
import html
import os
import re
from urllib.parse import quote

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, get, new_session
import test_page_editor as pe

TEST_PROJECT = os.environ.get("ANTWEB_EDIT_TEST_PROJECT", "fossilants")
TEST_COUNTRY = os.environ.get("ANTWEB_EDIT_TEST_COUNTRY", "Comoros")
PROJECT_URL = "/project.do?name=" + quote(TEST_PROJECT)
COUNTRY_URL = "/country.do?name=" + quote(TEST_COUNTRY)
FIELD = "contents"

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS,
                       reason=f"overview editor checks run only on staging, not on {pe.HOST}"),
]

editor_safety_deployed = pe.editor_safety_deployed


def _page_or_skip(session, url):
    r = session.get(BASE_URL + url, timeout=TIMEOUT)
    if r.status_code != 200 or classify(r).category != "ok":
        pytest.skip(f"test page {url} not available on this server: {classify(r)}")
    return r


# ---------- anonymous ----------

@pytest.mark.parametrize("url", ["/editProject.do?projectName=" + quote(TEST_PROJECT),
                                 "/editGeolocale.do?name=" + quote(TEST_COUNTRY) + "&georank=country"])
def test_settings_forms_require_login(url):
    assert get(new_session(), url).category == "login", url


@pytest.mark.parametrize("url", [PROJECT_URL, COUNTRY_URL])
def test_anonymous_sees_no_editor(url):
    r = _page_or_skip(new_session(), url + "&editField=" + FIELD)
    assert 'id="editor1"' not in r.text
    assert "tinymce.min.js" not in r.text and "editSafety.js" not in r.text


@pytest.mark.parametrize("url", [PROJECT_URL, COUNTRY_URL])
def test_anonymous_save_is_refused_and_text_kept(url):
    _page_or_skip(new_session(), url)
    text = "E2E anonymous overview <b>text</b> & more"
    r = new_session().post(BASE_URL + url,
                           data={"editField": FIELD, "isSaveEditField": "true", "contents": text,
                                 "editorReady": "1", "loadedHash": pe.ABSENT},
                           timeout=TIMEOUT, allow_redirects=True)
    assert r.status_code == 200, r.status_code
    assert "not logged in any more" in r.text
    assert "were saved" not in r.text
    m = re.search(r'<textarea id="descEditKeptText"[^>]*>(.*?)</textarea>', r.text, re.S)
    assert m and html.unescape(m.group(1)) == text


# ---------- logged in, HTTP only (no writes) ----------

@pytest.fixture(scope="module")
def curator(editor_safety_deployed):
    if not pe.USER or not pe.PASSWORD:
        pytest.skip("needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("canEdit"):
        pytest.skip("the test account cannot edit page text (not a curator)")
    return s


def test_project_settings_form_links_to_page_editor(curator):
    r = curator.get(BASE_URL + "/editProject.do?projectName=" + quote(TEST_PROJECT), timeout=TIMEOUT)
    if classify(r).category == "message":
        pytest.skip("the test account is not assigned this project")
    assert r.status_code == 200
    assert 'name="contents"' not in r.text, "the old Page Copy box is back"
    assert 'name="authorBio"' not in r.text
    assert r.text.count('name="map"') == 1, "duplicate map field"
    assert "editField=contents" in r.text, "no link to the page-text editor"


def test_geolocale_settings_form_links_to_page_editor(curator):
    r = curator.get(BASE_URL + "/editGeolocale.do?name=" + quote(TEST_COUNTRY) + "&georank=country",
                    timeout=TIMEOUT)
    if classify(r).category != "ok":
        pytest.skip(f"editGeolocale for {TEST_COUNTRY}: {classify(r)}")
    assert "editField=contents" in r.text, "no link to the page-text editor"
    assert 'id="editor1"' not in r.text, "the admin notes box must not look like a page-text editor"


@pytest.mark.parametrize("url", [PROJECT_URL, COUNTRY_URL])
def test_overview_save_refused_when_not_ready_or_stale(curator, url):
    """The overview pages use the same safety checks as taxon pages. Both saves are refused before
    anything is written; the stored text (its fingerprint) is unchanged."""
    _page_or_skip(curator, url)
    form, _ = pe.open_editor(curator, url, FIELD)
    before = form.loaded_hash
    assert before, "the editor form has no loadedHash"
    text = "E2E overview text that must never be stored"
    r = pe.post_save(curator, form, text, ready="0")
    assert "were saved" not in r.text and "had not finished loading" in r.text
    r = pe.post_save(curator, form, text, loaded_hash="0" * 64)
    assert "were saved" not in r.text and "someone else saved" in r.text
    after, _ = pe.open_editor(curator, url, FIELD)
    assert after.loaded_hash == before, "the stored text changed"

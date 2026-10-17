"""Page-text editor safety (page editor plan, Phase 1). Staging only.

What is checked, over HTTP like a browser (no JavaScript is run here):
- editSession.do answers JSON (logged in or not, and the session's form token);
- an anonymous save (a curator whose login ended) gets a message and the text back, never "saved";
- a save the editor did not mark ready, a stale save (someone else saved in between), an empty
  save and a save of the old "Add your content here" placeholder are refused, the text is shown
  again, and the stored text is unchanged;
- a save works, the stored HTML comes back exactly (escaped into the box), script is removed;
- "Clear this field" (clearField=1) removes the text;
- a save with a wrong CSRF token is refused with the text shown again.

Writes go ONLY to these two staging records (approved by Brian, 2026-10-16):
- taxon page Polyrhachis amana, field "comments" (TEST_TAXON_URL, TEST_TAXON_FIELD)
- specimen CASENT0922067, field "notes" (TEST_SPECIMEN, TEST_SPECIMEN_FIELD)
They can be changed with ANTWEB_EDIT_TEST_TAXON_URL / ANTWEB_EDIT_TEST_SPECIMEN. A write test runs
only if its field is empty when it starts, and it leaves the field empty again (also on failure).
History rows (description_hist) are added on staging; that is expected.

Never against production: the module is skipped unless the host is staging.antweb.org or a local
server, and skipped where the editor safety code is not deployed yet (no editSession.do).
"""
import hashlib
import html
import json
import os
import re
from urllib.parse import urlparse

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session

ALLOWED_HOSTS = {"staging.antweb.org", "localhost", "127.0.0.1"}
HOST = urlparse(BASE_URL).hostname or ""

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

TEST_TAXON_URL = os.environ.get("ANTWEB_EDIT_TEST_TAXON_URL",
                                "/description.do?genus=polyrhachis&species=amana&rank=species")
TEST_TAXON_FIELD = "comments"
TEST_SPECIMEN = os.environ.get("ANTWEB_EDIT_TEST_SPECIMEN", "casent0922067")
TEST_SPECIMEN_URL = "/specimen.do?name=" + TEST_SPECIMEN
TEST_SPECIMEN_FIELD = "notes"

ABSENT = "absent"


def _deployed():
    try:
        r = new_session().get(BASE_URL + "/editSession.do", timeout=TIMEOUT)
        return r.status_code == 200 and "loggedIn" in r.json()
    except Exception:
        return False


pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or HOST not in ALLOWED_HOSTS,
                       reason=f"editor write tests run only on staging, not on {HOST}"),
]

_DEPLOYED = None


@pytest.fixture(scope="module", autouse=True)
def editor_safety_deployed():
    global _DEPLOYED
    if _DEPLOYED is None:
        _DEPLOYED = _deployed()
    if not _DEPLOYED:
        pytest.skip("editor safety (editSession.do) is not deployed on this server yet")


# ---------- helpers ----------

INPUT = re.compile(r"<input\b([^>]*)>", re.I)
ATTR = re.compile(r'(\w+)\s*=\s*"([^"]*)"')
EDITOR_BOX = re.compile(r'<textarea id="editor1" name="contents"[^>]*>(.*?)</textarea>', re.S)


class EditForm:
    """The editor form on a page opened with editField=<field>."""

    def __init__(self, page_html):
        at = page_html.find('id="editor1"')
        start = page_html.rfind("<form", 0, at)
        end = page_html.find("</form>", at)
        assert at >= 0 and start >= 0 and end >= 0, "no editor form on the page"
        tag_end = page_html.find(">", start)
        attrs, body = page_html[start:tag_end], page_html[tag_end + 1:end]
        self.action = html.unescape(dict(ATTR.findall(attrs)).get("action", ""))
        self.fields = {}
        for a in INPUT.findall(body):
            d = dict(ATTR.findall(a))
            if d.get("type") == "hidden" and "name" in d:
                self.fields[d["name"]] = html.unescape(d.get("value", ""))
        m = EDITOR_BOX.search(body)
        self.text = html.unescape(m.group(1)) if m else None
        self.has_clear = "descEditClear" in body
        self.body = body

    @property
    def loaded_hash(self):
        return self.fields.get("loadedHash")


def open_editor(session, page_url, field):
    r = session.get(BASE_URL + page_url + "&editField=" + field, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    return EditForm(r.text), r.text


def post_save(session, form, contents, ready="1", loaded_hash=None, clear=False, csrf=None):
    data = dict(form.fields)
    data["contents"] = contents
    data["editorReady"] = ready
    data["loadedHash"] = form.loaded_hash if loaded_hash is None else loaded_hash
    data["clearField"] = "1" if clear else "0"
    if csrf is not None:
        data["_csrf"] = csrf
    url = form.action if form.action.startswith("http") else BASE_URL + form.action
    return session.post(url, data=data, timeout=TIMEOUT, allow_redirects=True)


def sha256(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def login():
    s = new_session()
    s.post(BASE_URL + "/login.do", data={"userName": USER, "password": PASSWORD, "isSubmit": "true"},
           timeout=TIMEOUT, allow_redirects=True)
    return s


needs_curator = pytest.mark.skipif(not USER or not PASSWORD, reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


@pytest.fixture(scope="module")
def curator():
    s = login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    if not info.get("canEdit"):
        pytest.skip("the test account cannot edit page text (not a curator)")
    return s


MISSING = ("Taxon not found", "is not in the AntWeb database")


def _page_missing(text):
    return any(m in text for m in MISSING)


def _empty_field_or_skip(session, page_url, field):
    r = session.get(BASE_URL + page_url, timeout=TIMEOUT)
    if r.status_code != 200 or _page_missing(r.text):
        pytest.skip(f"test page {page_url} not available on this server")
    form, _ = open_editor(session, page_url, field)
    if form.loaded_hash != ABSENT:
        pytest.skip(f"test field {field} on {page_url} is not empty; it is never overwritten by tests")
    return form


def _leave_empty(session, page_url, field):
    """Restore the original state (empty). Runs after the write tests, also when they fail."""
    try:
        form, _ = open_editor(session, page_url, field)
        if form.loaded_hash and form.loaded_hash != ABSENT:
            post_save(session, form, "", clear=True)
    except Exception:
        pass


# ---------- anonymous ----------

def test_edit_session_anonymous():
    r = new_session().get(BASE_URL + "/editSession.do", timeout=TIMEOUT)
    assert r.status_code == 200
    assert "no-store" in r.headers.get("Cache-Control", "")
    data = r.json()
    assert data["loggedIn"] is False and data["canEdit"] is False and "csrf" not in data


def test_anonymous_save_is_refused_and_text_kept():
    """A save after the login ended (restart, 4 hours idle) must say so and give the text back."""
    text = "E2E anonymous <b>text</b> & more"
    s = new_session()
    r = s.post(BASE_URL + TEST_TAXON_URL,
               data={"editField": TEST_TAXON_FIELD, "isSaveEditField": "true", "contents": text,
                     "editorReady": "1", "loadedHash": ABSENT},
               timeout=TIMEOUT, allow_redirects=True)
    assert r.status_code == 200, r.status_code
    if _page_missing(r.text):
        pytest.skip(f"test page {TEST_TAXON_URL} not available on this server")
    assert "not logged in any more" in r.text
    assert "were saved" not in r.text
    m = re.search(r'<textarea id="descEditKeptText"[^>]*>(.*?)</textarea>', r.text, re.S)
    assert m and html.unescape(m.group(1)) == text


# ---------- curator ----------

@needs_curator
def test_edit_session_logged_in(curator):
    data = curator.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    assert data["loggedIn"] is True and data["canEdit"] is True
    assert re.fullmatch(r"[A-Za-z0-9_-]{43}", data["csrf"])
    page = curator.get(BASE_URL + "/curate.do", timeout=TIMEOUT).text
    assert f'name="_csrf" value="{data["csrf"]}"' in page, "editSession.do token differs from the session's form token"


@needs_curator
def test_taxon_editor_safety(curator):
    url, field = TEST_TAXON_URL, TEST_TAXON_FIELD
    form = _empty_field_or_skip(curator, url, field)
    try:
        # The editor form carries the fingerprint and a not-ready flag; no placeholder text.
        assert form.loaded_hash == ABSENT
        assert form.fields.get("editorReady") == "0"
        assert form.fields.get("clearField") == "0"
        assert form.text == ""
        assert "Add your content here" not in form.body
        assert "editSafety.js" in form.body
        assert not form.has_clear, "Clear button shown for an empty field"

        # Not marked ready by the editor: refused, text shown again, nothing stored.
        r = post_save(curator, form, "E2E not ready", ready="0")
        assert "had not finished loading" in r.text and "were saved" not in r.text
        assert EditForm(r.text).text == "E2E not ready"
        assert open_editor(curator, url, field)[0].loaded_hash == ABSENT

        # Empty box on an empty field: nothing to do.
        r = post_save(curator, form, "<p>&nbsp;</p>")
        assert "Nothing to save" in r.text
        assert open_editor(curator, url, field)[0].loaded_hash == ABSENT

        # A real save. Script is removed by the sanitizer; the stored HTML comes back exactly.
        r = post_save(curator, form, "<p>E2E editor test <b>one</b></p><script>alert(1)</script>")
        assert "were saved" in r.text, r.text[:500]
        form2, page = open_editor(curator, url, field)
        assert form2.loaded_hash != ABSENT
        assert "<b>one</b>" in form2.text and "<script" not in form2.text
        assert sha256(form2.text) == form2.loaded_hash, "editor box does not hold exactly the stored text"
        assert form2.has_clear

        # Stale save (the editor was opened before the save above): refused as a conflict,
        # both texts shown, stored text unchanged.
        r = post_save(curator, form, "<p>E2E stale text</p>")
        assert "someone else saved" in r.text and "were saved" not in r.text
        assert EditForm(r.text).text == "<p>E2E stale text</p>"
        assert 'id="descEditStoredText"' in r.text and "<b>one</b>" in r.text
        assert open_editor(curator, url, field)[0].loaded_hash == form2.loaded_hash

        # Empty box over text: refused, offers "Clear this field", stored text unchanged.
        for empty in ("", "<p>&nbsp;</p>", "Add your content here.", "<p>Add your content here</p>"):
            r = post_save(curator, form2, empty)
            assert "box is empty" in r.text and "were saved" not in r.text, empty
            assert open_editor(curator, url, field)[0].loaded_hash == form2.loaded_hash, empty

        # A literal "<" (stored as &lt;) survives opening and saving unchanged.
        r = post_save(curator, form2, "<p>a &lt;b&gt; literal &amp; more</p>")
        assert "were saved" in r.text
        form3, _ = open_editor(curator, url, field)
        assert "&lt;b&gt;" in form3.text and "<b>" not in form3.text
        assert sha256(form3.text) == form3.loaded_hash

        # Clear this field.
        r = post_save(curator, form3, "", clear=True)
        assert "was cleared" in r.text, r.text[:500]
        assert open_editor(curator, url, field)[0].loaded_hash == ABSENT
    finally:
        _leave_empty(curator, url, field)


@needs_curator
def test_specimen_editor_save_and_clear(curator):
    url, field = TEST_SPECIMEN_URL, TEST_SPECIMEN_FIELD
    form = _empty_field_or_skip(curator, url, field)
    try:
        r = post_save(curator, form, "<p>E2E specimen note</p>")
        assert "were saved" in r.text, r.text[:500]
        form2, _ = open_editor(curator, url, field)
        assert "E2E specimen note" in form2.text
        assert sha256(form2.text) == form2.loaded_hash
        r = post_save(curator, form, "<p>E2E stale</p>")
        assert "someone else saved" in r.text
        r = post_save(curator, form2, "", clear=True)
        assert "was cleared" in r.text
        assert open_editor(curator, url, field)[0].loaded_hash == ABSENT
    finally:
        _leave_empty(curator, url, field)


@needs_curator
def test_wrong_csrf_token_keeps_text(curator):
    form, _ = open_editor(curator, TEST_TAXON_URL, TEST_TAXON_FIELD)
    text = "<p>E2E csrf & text</p>"
    r = post_save(curator, form, text, csrf="x" * 43, loaded_hash="0" * 64)
    assert r.status_code == 403
    m = re.search(r'<textarea id="keptText"[^>]*>(.*?)</textarea>', r.text, re.S)
    assert m and html.unescape(m.group(1)) == text


# ---------- editor choice (TinyMCE 8, CKEditor 3 fallback; util/PageEditor.java) ----------

@needs_curator
def test_edit_form_uses_tinymce_with_versioned_scripts(curator):
    """Default editor is TinyMCE 8 with the shared setup; every script carries ?v= (util/Asset)."""
    form, page = open_editor(curator, TEST_TAXON_URL, TEST_TAXON_FIELD)
    assert re.search(r'/common/editors/tinymce/[0-9.]+/tinymce\.min\.js\?v=[0-9a-f]{8}"', page), "tinymce.min.js not loaded (or not versioned)"
    assert re.search(r'/common/editors/antwebEditor\.js\?v=[0-9a-f]{8}"', page), "antwebEditor.js not loaded (or not versioned)"
    assert re.search(r'/common/descEdit/editSafety\.js\?v=[0-9a-f]{8}"', page)
    assert "editor: 'tinymce'" in page
    assert "cacheSuffix: '?v=" in page
    assert "/ckeditor/ckeditor.js" not in page
    assert "TinyMCE" in page and "github.com/tinymce/tinymce" in page, "licence/source notice missing"


@needs_curator
def test_served_setup_sets_gpl_license_key(curator):
    """TinyMCE 8 opens read only without license_key 'gpl' (see antwebEditor.js)."""
    form, page = open_editor(curator, TEST_TAXON_URL, TEST_TAXON_FIELD)
    m = re.search(r'src="([^"]*/common/editors/antwebEditor\.js\?v=[0-9a-f]{8})"', page)
    assert m
    url = html.unescape(m.group(1))
    js = curator.get(url if url.startswith("http") else BASE_URL + url, timeout=TIMEOUT)
    assert js.status_code == 200
    assert "var LICENSE_KEY = 'gpl';" in js.text and "c.license_key = LICENSE_KEY;" in js.text
    lic = re.search(r'href="([^"]*/common/editors/tinymce/[0-9.]+/LICENSE\.txt)"', page)
    assert lic, "no link to TinyMCE's licence (LICENSE.txt; the site does not serve .md)"
    url = html.unescape(lic.group(1))
    assert curator.get(url if url.startswith("http") else BASE_URL + url, timeout=TIMEOUT).status_code == 200


@needs_curator
def test_edit_form_ckeditor_fallback_switch(curator):
    """pageEditor=ckeditor on the address gives the old editor (the same switch as etc/editor.properties)."""
    r = curator.get(BASE_URL + TEST_TAXON_URL + "&editField=" + TEST_TAXON_FIELD + "&pageEditor=ckeditor", timeout=TIMEOUT)
    assert r.status_code == 200
    assert "/ckeditor/ckeditor.js" in r.text
    assert "tinymce.min.js" not in r.text
    assert "editor: 'ckeditor'" in r.text
    form = EditForm(r.text)
    assert form.fields.get("pageEditor") == "ckeditor", "the save form does not keep the chosen editor"

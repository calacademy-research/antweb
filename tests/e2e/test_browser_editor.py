"""Curator edit form in a real browser (staging only, never writes).

Opens the edit box of the approved test field as the test curator and checks the buttons are
wired: the "Clear this field" button must ask for confirmation (it did nothing on 2026-10-17
because the script started before the button existed). The dialog is dismissed, so nothing is
saved. Skipped without Playwright, without the test login, or on production.
"""
from urllib.parse import urlsplit

import pytest

sync_api = pytest.importorskip("playwright.sync_api")

from antweb import BASE_URL, IS_PRODUCTION  # noqa: E402
import test_page_editor as pe  # noqa: E402
from test_browser import playwright, browser, _context  # noqa: E402,F401  (fixtures)

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or (urlsplit(BASE_URL).hostname or "") not in pe.ALLOWED_HOSTS,
                       reason="browser editor checks run on staging or a local server only"),
    pe.needs_curator,
]


@pytest.fixture(scope="module")
def curator_page(browser, editor_safety_deployed):  # noqa: F811
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=pe.TIMEOUT).json()
    if not info.get("canEdit"):
        pytest.skip("test login cannot edit page text")
    ctx = _context(browser, viewport={"width": 1280, "height": 900})
    host = urlsplit(BASE_URL).hostname
    ctx.add_cookies([{"name": c.name, "value": c.value, "domain": host, "path": c.path or "/",
                      "secure": True, "httpOnly": True} for c in s.cookies])
    page = ctx.new_page()
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(BASE_URL + pe.TEST_TAXON_URL + "&editField=comments")
    yield page, errors
    ctx.close()


editor_safety_deployed = pe.editor_safety_deployed


def test_editor_becomes_ready(curator_page):
    page, errors = curator_page
    page.wait_for_function("document.querySelector('input[name=editorReady]') && "
                           "document.querySelector('input[name=editorReady]').value === '1'", timeout=30000)
    assert not errors, errors


def test_clear_button_asks_for_confirmation(curator_page):
    page, errors = curator_page
    if page.locator("button.descEditClear").count() == 0:
        pytest.skip("test field is empty, so there is no Clear button")
    seen = []

    def on_dialog(d):
        seen.append(d.message)
        d.dismiss()   # never clear
    page.on("dialog", on_dialog)
    url = page.url
    page.locator("button.descEditClear").click()
    page.wait_for_timeout(1000)
    page.remove_listener("dialog", on_dialog)
    assert seen and "Remove all text" in seen[0], "Clear this field did not ask for confirmation"
    assert page.url == url
    assert not errors, errors


# ---------- TinyMCE 8 edit form (page editor plan phase 3) ----------
#
# Write tests: only the approved staging test field (Polyrhachis amana, Comments), only when it is
# empty at the start, and always left empty again (test_page_editor._leave_empty), as in
# test_page_editor.py. The browser's "Leave site?" dialog (beforeunload, shown while there is
# unsaved text) is accepted, as a tester would click "Leave": the draft stays in the browser.

import re  # noqa: E402
import time  # noqa: E402

READY_JS = ("document.querySelector('input[name=editorReady]') && "
            "document.querySelector('input[name=editorReady]').value === '1'")
BAR_SHOWN_JS = "document.getElementById('descEditSafetyBar').style.display === 'block'"
SAVE = "form:has(#editor1) input[type=submit][value=Save]"
EDIT_URL = pe.TEST_TAXON_URL + "&editField=" + pe.TEST_TAXON_FIELD


def _curator_context(browser, session, **kw):  # noqa: F811
    kw.setdefault("viewport", {"width": 1280, "height": 900})
    ctx = _context(browser, **kw)
    host = urlsplit(BASE_URL).hostname
    secure = BASE_URL.startswith("https")
    ctx.add_cookies([{"name": c.name, "value": c.value, "domain": host, "path": c.path or "/",
                      "secure": secure, "httpOnly": True} for c in session.cookies])
    return ctx


def _open(ctx, url=EDIT_URL):
    page = ctx.new_page()
    page.errors = []
    page.on("pageerror", lambda e: page.errors.append(str(e)))
    page.on("dialog", lambda d: d.accept())   # "Leave site?" and "Remove all text?": accept
    page.goto(BASE_URL + url)
    page.wait_for_function(READY_JS, timeout=60000)
    return page


def _text(page):
    return page.evaluate("tinymce.get('editor1').getContent()")


def _append(page, words):
    page.evaluate("""(w) => { const ed = tinymce.get('editor1'); ed.focus();
        ed.selection.select(ed.getBody(), true); ed.selection.collapse(false); ed.insertContent(w); }""", words)


def _save(page):
    with page.expect_navigation(timeout=120000):
        page.click(SAVE)


@pytest.fixture(scope="module")
def curator_session(editor_safety_deployed):
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=pe.TIMEOUT).json()
    if not info.get("canEdit"):
        pytest.skip("test login cannot edit page text")
    return s


def test_tinymce_opens_editable_with_no_js_errors(browser, curator_session):  # noqa: F811
    ctx = _curator_context(browser, curator_session)
    try:
        page = _open(ctx)
        state = page.evaluate("""() => { const ed = window.tinymce && tinymce.get('editor1');
            return ed ? {mode: ed.mode.get(), version: tinymce.majorVersion + '.' + tinymce.minorVersion,
                         ckeditor: !!window.CKEDITOR} : null; }""")
        assert state, "TinyMCE did not start on the edit form"
        assert state["mode"] == "design", f"TinyMCE is {state['mode']} (license_key 'gpl' missing?)"
        assert state["version"].startswith("8."), state
        assert not state["ckeditor"]
        assert not page.errors, page.errors
    finally:
        ctx.close()


def test_tinymce_edit_save_render_and_safety(browser, curator_session):  # noqa: F811
    """Existing text shown, empty save blocked, edit and save, result on the page, conflict between
    two tabs, draft offered after leaving the page. Writes only the test field; leaves it empty."""
    url, field = pe.TEST_TAXON_URL, pe.TEST_TAXON_FIELD
    form = pe._empty_field_or_skip(curator_session, url, field)
    stamp = str(int(time.time()))
    seed = f"<p>E2E browser seed <b>bold</b> {stamp}</p>"
    ctx = _curator_context(browser, curator_session)
    try:
        r = pe.post_save(curator_session, form, seed)
        assert "were saved" in r.text, r.text[:300]

        # 1. The editor shows the stored text and is ready.
        page = _open(ctx)
        assert f"E2E browser seed <b>bold</b> {stamp}" in _text(page)

        # 2. An empty box is never saved over the text.
        page.evaluate("tinymce.get('editor1').setContent('')")
        page.click(SAVE)
        page.wait_for_function(BAR_SHOWN_JS)
        assert "box is empty" in page.inner_text("#descEditSafetyBar")
        assert "editField=" in page.url
        page.close()

        # 3. Edit and save: saved message, and the text is on the page.
        page = _open(ctx)
        _append(page, f" E2E-EDIT-{stamp}")
        _save(page)
        body = page.content()
        assert "were saved" in body, body[:500]
        assert f"E2E-EDIT-{stamp}" in page.inner_text("body")
        assert page.locator("#editor1").count() == 0
        assert not page.errors, page.errors
        page.close()

        # 4. Two tabs: the second save must not overwrite the first.
        a, b = _open(ctx), _open(ctx)
        _append(b, f" TAB-B-{stamp}")
        _save(b)
        assert "were saved" in b.content()
        _append(a, f" TAB-A-{stamp}")
        _save(a)
        assert "someone else saved" in a.content()
        a.wait_for_function(READY_JS, timeout=60000)
        assert f"TAB-A-{stamp}" in _text(a), "the refused text is not back in the editor"
        assert f"TAB-B-{stamp}" in a.inner_text("#descEditStoredText")
        a.close()
        b.close()

        # 5. Draft: type, leave the page ("Leave site?" accepted), come back, restore.
        page = _open(ctx)
        _append(page, f" DRAFT-{stamp}")
        page.wait_for_timeout(3500)
        page.goto(BASE_URL + url)
        page.goto(BASE_URL + EDIT_URL)
        page.wait_for_function(READY_JS, timeout=60000)
        page.wait_for_function(BAR_SHOWN_JS, timeout=10000)
        assert "unsaved text" in page.inner_text("#descEditSafetyBar")
        page.click("#descEditSafetyBar button:has-text('Restore my unsaved text')")
        assert f"DRAFT-{stamp}" in _text(page)
        page.evaluate("() => { for (const k of Object.keys(localStorage)) if (k.startsWith('antweb.draft.')) localStorage.removeItem(k); }")
        page.close()
    finally:
        ctx.close()
        pe._leave_empty(curator_session, url, field)


def test_tinymce_edit_form_at_phone_width(playwright, browser, curator_session):  # noqa: F811
    device = dict(playwright.devices["iPhone 13"])
    device.pop("default_browser_type", None)
    device["viewport"] = {"width": 390, "height": 844}
    ctx = _curator_context(browser, curator_session, **device)
    try:
        page = _open(ctx)
        box = page.locator(".tox-tinymce").bounding_box()
        # AntWeb pages have no mobile viewport yet, so a phone shows them zoomed out on a ~980 px
        # wide layout. The editor must fit that layout (no sideways scrolling inside the page).
        # When the pages become responsive (to-do: AntWeb on phones), tighten this to 390 px.
        layout = page.evaluate("document.documentElement.clientWidth")
        assert box and box["x"] >= 0 and box["x"] + box["width"] <= layout + 1, \
            f"editor wider than the page layout ({layout} px): {box}"
        assert page.locator(".tox-toolbar, .tox-toolbar__primary, .tox-toolbar-overlord").count() > 0, "no editor toolbar at phone width"
        assert not page.errors, page.errors
    finally:
        ctx.close()


def test_ckeditor_fallback_still_loads(browser, curator_session):  # noqa: F811
    """The rollback switch (pageEditor=ckeditor) gives a usable form: CKEditor 3 if its core files are
    on the server (web/ckeditor/ in git holds only its loader), otherwise the plain HTML box. Either
    way the safety script marks it ready and there is no JavaScript error."""
    ctx = _curator_context(browser, curator_session)
    try:
        page = _open(ctx, EDIT_URL + "&pageEditor=ckeditor")
        assert page.evaluate("!window.tinymce")
        assert not page.errors, page.errors
    finally:
        ctx.close()


# ---------- History and Restore (page editor plan phase 2) ----------

def test_history_restore_in_browser(browser, curator_session):  # noqa: F811
    """[History] next to the field opens its versions; Restore asks for confirmation and restores.
    Writes only the test field, only when it is empty at the start; leaves it empty."""
    url, field = pe.TEST_TAXON_URL, pe.TEST_TAXON_FIELD
    form = pe._empty_field_or_skip(curator_session, url, field)
    stamp = str(int(time.time()))
    one, two = f"E2E browser history one {stamp}", f"E2E browser history two {stamp}"
    ctx = _curator_context(browser, curator_session)
    try:
        assert "were saved" in pe.post_save(curator_session, form, f"<p>{one}</p>").text
        form2, _ = pe.open_editor(curator_session, url, field)
        assert "were saved" in pe.post_save(curator_session, form2, f"<p>{two}</p>").text

        page = ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        dialogs = []
        page.on("dialog", lambda d: (dialogs.append(d.message), d.accept()))
        page.goto(BASE_URL + url)
        link = page.locator(f"a.descEditHistoryLink[href*='field={field}&']")
        assert link.count() == 1, "no [History] link next to the field"
        with page.expect_navigation():
            link.click()
        assert "History of" in page.inner_text("h1")
        block = page.locator(".fh-version", has=page.locator("textarea.fh-copy", has_text=one))
        assert block.count() >= 1, "the earlier version is not listed"
        with page.expect_navigation(timeout=120000):
            block.first.locator("button.fh-restore-btn").click()
        assert dialogs and "Replace the current text" in dialogs[0], dialogs
        assert "was restored to the version of" in page.content()
        assert one in page.inner_text("body")
        assert not errors, errors
        assert one in pe.open_editor(curator_session, url, field)[0].text
    finally:
        ctx.close()
        pe._leave_empty(curator_session, url, field)

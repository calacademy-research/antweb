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

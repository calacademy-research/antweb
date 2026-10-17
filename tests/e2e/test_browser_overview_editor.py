"""Project and geolocale page-text editor in a real browser (staging only, never writes).

As the test curator, opens the Contents editor of a project page and a country page and checks that
TinyMCE 8 starts editable, the safety script marks the form ready (editorReady=1) and there are no
JavaScript errors. Nothing is typed or saved. HTTP-level checks: test_overview_editor.py.
Skipped without Playwright, without the test login, or on production.
"""
from urllib.parse import urlsplit

import pytest

sync_api = pytest.importorskip("playwright.sync_api")

from antweb import BASE_URL, IS_PRODUCTION  # noqa: E402
import test_page_editor as pe  # noqa: E402
import test_overview_editor as oe  # noqa: E402
from test_browser import playwright, browser  # noqa: E402,F401  (fixtures)
from test_browser_editor import READY_JS, _curator_context  # noqa: E402

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or (urlsplit(BASE_URL).hostname or "") not in pe.ALLOWED_HOSTS,
                       reason="browser editor checks run on staging or a local server only"),
    pe.needs_curator,
]

editor_safety_deployed = pe.editor_safety_deployed


@pytest.fixture(scope="module")
def curator_session(editor_safety_deployed):
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=pe.TIMEOUT).json()
    if not info.get("canEdit"):
        pytest.skip("test login cannot edit page text")
    return s


@pytest.mark.parametrize("url", [oe.PROJECT_URL, oe.COUNTRY_URL], ids=["project", "country"])
def test_overview_editor_loads_tinymce(browser, curator_session, url):  # noqa: F811
    ctx = _curator_context(browser, curator_session)
    try:
        page = ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.goto(BASE_URL + url + "&editField=" + oe.FIELD)
        if page.locator("#editor1").count() == 0:
            pytest.skip(f"no editor on {url} (page missing on this server?)")
        page.wait_for_function(READY_JS, timeout=60000)
        state = page.evaluate("""() => { const ed = window.tinymce && tinymce.get('editor1');
            return ed ? {mode: ed.mode.get(), version: tinymce.majorVersion + '.' + tinymce.minorVersion,
                         ckeditor: !!window.CKEDITOR} : null; }""")
        assert state, "TinyMCE did not start on the edit form"
        assert state["mode"] == "design", f"TinyMCE is {state['mode']} (license_key 'gpl' missing?)"
        assert state["version"].startswith("8."), state
        assert not state["ckeditor"], "CKEditor loaded on a TinyMCE page"
        assert not errors, errors
    finally:
        ctx.close()

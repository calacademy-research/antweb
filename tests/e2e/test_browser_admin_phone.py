"""Admin pages at phone width (2026-10-21): the column of links (Login Manager, Upload groups, Add an
uploader ...; div.right) stacks above or below the page text (div.admin_left) instead of floating
over its first paragraph (web/common/responsive.css). At 1280 px it still floats to the right.

As the staging admin test login, read-only (page loads only). Skipped without Playwright, without
ANTWEB_TEST_ADMIN_USER / _PASSWORD, or outside staging.
"""
from urllib.parse import urlsplit

import pytest

sync_api = pytest.importorskip("playwright.sync_api")

from antweb import BASE_URL  # noqa: E402
from staging_accounts import IS_STAGING, login, needs  # noqa: E402
from test_browser import playwright, browser, _context  # noqa: E402,F401  (fixtures)

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only"), needs("admin")]

# Pages whose div.right comes before div.admin_left (it floated over the text) and after it.
PAGES = ["/viewLogin.do?id={id}", "/viewGroups.do", "/addUploader.do"]

MEASURE_JS = """() => {
  const left = document.querySelector('.admin_left');
  const right = [...document.querySelectorAll('div.right')].find(d => d.parentElement === (left && left.parentElement));
  if (!left || !right) return null;
  const a = left.getBoundingClientRect(), b = right.getBoundingClientRect();
  return {float: getComputedStyle(right).cssFloat, overlap: !(b.bottom <= a.top + 1 || a.bottom <= b.top + 1),
          rightText: right.innerText.trim().slice(0, 80), scrollX: document.documentElement.scrollWidth > window.innerWidth + 1};
}"""


@pytest.fixture(scope="module")
def admin():
    s, _token, login_id = login("admin")
    return s, login_id


def _admin_context(browser, session, **kw):  # noqa: F811
    ctx = _context(browser, **kw)
    host = urlsplit(BASE_URL).hostname
    secure = BASE_URL.startswith("https")
    ctx.add_cookies([{"name": c.name, "value": c.value, "domain": host, "path": c.path or "/",
                      "secure": secure, "httpOnly": True} for c in session.cookies])
    return ctx


@pytest.mark.parametrize("path", PAGES)
def test_admin_links_column_stacks_at_phone_width(browser, admin, path):  # noqa: F811
    session, login_id = admin
    ctx = _admin_context(browser, session, viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
    try:
        page = ctx.new_page()
        page.goto(BASE_URL + path.format(id=login_id))
        m = page.evaluate(MEASURE_JS)
        assert m, f"{path}: no div.right next to div.admin_left (not logged in as admin?)"
        assert m["float"] == "none", (path, m)
        assert not m["overlap"], f"{path}: the link column overlaps the page text: {m}"
    finally:
        ctx.close()


def test_admin_links_column_floats_right_on_desktop(browser, admin):  # noqa: F811
    session, login_id = admin
    ctx = _admin_context(browser, session, viewport={"width": 1280, "height": 900})
    try:
        page = ctx.new_page()
        page.goto(BASE_URL + PAGES[0].format(id=login_id))
        m = page.evaluate(MEASURE_JS)
        assert m and m["float"] == "right", m
    finally:
        ctx.close()

"""Real-browser checks (Playwright + headless Chromium).

The other tests read HTML with requests and never run its JavaScript, so a script that
breaks in the browser (as the header autocomplete did after the jQuery 3 upgrade, when
browsers kept an old autocomplete.js) passed them. These tests open pages in Chromium and:

  a. type into the header Taxa and Places boxes and wait for the suggestion list;
  b. fail on any uncaught JavaScript error on key pages;
  c. check every local script and stylesheet on those pages loads (200) and carries the
     ?v=<hash> cache-busting token (util/Asset.java), so old copies can't linger;
  d. open the main page types at phone width (390x844, iPhone user agent) and check that they fit the
     screen (no sideways page scroll), that the header search box works and the menu button opens
     the menu (responsive.css / responsive.js), with no JS errors; at 1280 px the menu button stays
     hidden and nothing scrolls sideways;
  e. at phone width, wide lists (a country species list, a specimen list) scroll sideways inside
     their own box (div.aw-list-scroll), not the page: the row shading spans all columns, an edge
     shadow shows more columns to the right, and the name column stays in view after scrolling.
     At 1280 px the lists keep their fixed 1000 px desktop layout.

Read-only: pages are opened and the search boxes typed into, nothing is submitted.
Skipped when Playwright is not installed (pip install -r requirements.txt;
python -m playwright install chromium).

Environment (set by the workflow's staging tunnel step):
  ANTWEB_BROWSER_HOST_RULES          Chromium --host-resolver-rules, e.g. "MAP staging.antweb.org 127.0.0.1"
  ANTWEB_BROWSER_IGNORE_HTTPS_ERRORS 1 = accept the staging origin certificate (Chromium does not read
                                     REQUESTS_CA_BUNDLE); never honoured for production.
"""
import os
import re
from dataclasses import dataclass, field
from urllib.parse import parse_qs, urlsplit

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT

sync_api = pytest.importorskip("playwright.sync_api", reason="Playwright not installed")

PAGE_TIMEOUT_MS = int(max(TIMEOUT, 120) * 1000)
SETTLE_MS = 1500          # after "load", time for late scripts (document ready, timers) to fail
SUGGEST_TIMEOUT_MS = 15000

# (b), (c): one URL per page type; all already used elsewhere in the suite.
KEY_PAGES = {
    "home": "/",
    "genus": "/description.do?genus=camponotus&rank=genus",
    "species": "/description.do?genus=nesomyrmex&species=clavipilis&rank=species",
    "specimen": "/specimen.do?name=casent0103190",
    "images": "/images.do?genus=camponotus&rank=genus",
    "bigMap": "/bigMap.do?taxonName=formicinae&project=allantwebants",
    "advancedSearch": "/advSearch.do",
    "login": "/login.do",
    "country": "/country.do?name=Madagascar",
}
PHONE_PAGES = {k: KEY_PAGES[k] for k in ("home", "genus", "species", "specimen", "images")}

# Uncaught errors known and accepted (substring of the message). Keep empty; add only with an issue link.
KNOWN_PAGE_ERRORS: list[str] = []

VERSION_TOKEN = re.compile(r"^[0-9a-f]{8}$")
BASE_HOST = urlsplit(BASE_URL).hostname


def _ignore_https_errors() -> bool:
    return os.environ.get("ANTWEB_BROWSER_IGNORE_HTTPS_ERRORS") == "1" and not IS_PRODUCTION


@pytest.fixture(scope="module")
def playwright():
    with sync_api.sync_playwright() as p:
        yield p


@pytest.fixture(scope="module")
def browser(playwright):
    args = []
    rules = os.environ.get("ANTWEB_BROWSER_HOST_RULES")
    if rules:
        args.append(f"--host-resolver-rules={rules}")
    try:
        b = playwright.chromium.launch(args=args)
    except Exception as e:  # browser binary not installed
        if os.environ.get("GITHUB_ACTIONS"):
            raise
        pytest.skip(f"Chromium not available: {str(e).splitlines()[0]}")
    yield b
    b.close()


def _context(browser, **kw):
    ctx = browser.new_context(ignore_https_errors=_ignore_https_errors(), **kw)
    ctx.set_default_timeout(PAGE_TIMEOUT_MS)
    ctx.set_default_navigation_timeout(PAGE_TIMEOUT_MS)
    return ctx


@pytest.fixture(scope="module")
def desktop(browser):
    ctx = _context(browser, viewport={"width": 1280, "height": 900})
    yield ctx
    ctx.close()


@dataclass
class Visit:
    url: str
    status: int | None = None
    errors: list[str] = field(default_factory=list)
    assets: dict[str, int | None] = field(default_factory=dict)       # local js/css url -> status
    cache_control: dict[str, str] = field(default_factory=dict)


def _is_local_asset(url: str) -> bool:
    parts = urlsplit(url)
    return parts.hostname == BASE_HOST and parts.path.lower().endswith((".js", ".css"))


def visit(ctx, path: str) -> Visit:
    v = Visit(path)
    page = ctx.new_page()
    loaded: dict[str, int] = {}
    headers: dict[str, str] = {}

    def on_response(resp):
        if resp.request.resource_type in ("script", "stylesheet") and _is_local_asset(resp.url):
            loaded[resp.url] = resp.status
            headers[resp.url] = resp.headers.get("cache-control", "")

    page.on("pageerror", lambda err: v.errors.append(str(err)))
    page.on("response", on_response)
    try:
        resp = page.goto(BASE_URL + path, wait_until="load")
        v.status = resp.status if resp else None
        page.wait_for_timeout(SETTLE_MS)
        referenced = page.eval_on_selector_all(
            "script[src], link[rel~='stylesheet'][href]",
            "els => els.map(e => e.src || e.href)")
        for url in referenced:
            if not _is_local_asset(url):
                continue
            if url not in loaded:  # e.g. served from memory cache without a response event
                r = ctx.request.get(url)
                loaded[url] = r.status
                headers[url] = r.headers.get("cache-control", "")
            v.assets[url] = loaded[url]
            v.cache_control[url] = headers.get(url, "")
    finally:
        page.close()
    return v


def _unexpected(errors: list[str]) -> list[str]:
    return [e for e in errors if not any(k in e for k in KNOWN_PAGE_ERRORS)]


_visits: dict[str, Visit] = {}


@pytest.fixture(scope="module")
def visited(desktop):
    def get(path):
        if path not in _visits:
            _visits[path] = visit(desktop, path)
        return _visits[path]
    return get


# a. header autocomplete -------------------------------------------------------------

@pytest.mark.parametrize("box,typed,expected", [
    ("#taxonName", "campon", "Camponotus"),
    ("#placeName", "madag", "Madagascar"),
])
def test_header_autocomplete(desktop, box, typed, expected):
    page = desktop.new_page()
    errors = []
    page.on("pageerror", lambda err: errors.append(str(err)))
    try:
        page.goto(BASE_URL + "/", wait_until="load")
        field_ = page.locator(box).first
        field_.click()
        field_.press_sequentially(typed, delay=80)   # real key events: the plugin listens to keydown
        suggestion = page.locator(".ac_results li", has_text=expected).first
        try:
            suggestion.wait_for(state="visible", timeout=SUGGEST_TIMEOUT_MS)
        except sync_api.TimeoutError:
            shown = page.locator(".ac_results li").all_inner_texts()
            pytest.fail(f"{box}: no '{expected}' suggestion {SUGGEST_TIMEOUT_MS // 1000}s after typing "
                        f"'{typed}'; list shows {shown[:10]}; JS errors: {errors}")
        assert not _unexpected(errors), f"uncaught JS errors on /: {errors}"
    finally:
        page.close()


# b. no uncaught JavaScript errors -------------------------------------------------------

@pytest.mark.parametrize("name", list(KEY_PAGES))
def test_no_uncaught_js_errors(visited, name):
    v = visited(KEY_PAGES[name])
    assert v.status is not None and v.status < 500, f"{v.url}: HTTP {v.status}"
    assert not _unexpected(v.errors), f"{v.url}: uncaught JS errors: {v.errors}"


# c. local scripts and stylesheets load and are versioned -------------------------------

@pytest.mark.parametrize("name", list(KEY_PAGES))
def test_local_assets_load_and_are_versioned(visited, name):
    v = visited(KEY_PAGES[name])
    assert v.assets, f"{v.url}: no local scripts or stylesheets found (page did not render?)"
    problems = []
    for url, status in sorted(v.assets.items()):
        short = url.replace(BASE_URL, "")
        if status != 200:
            problems.append(f"{short}: HTTP {status}")
        token = parse_qs(urlsplit(url).query).get("v", [""])[0]
        if not VERSION_TOKEN.match(token):
            problems.append(f"{short}: no ?v=<8 hex> token (Asset.url)")
        elif "max-age" not in v.cache_control.get(url, ""):
            problems.append(f"{short}: no Cache-Control max-age (AssetCacheFilter)")
    assert not problems, f"{v.url}:\n  " + "\n  ".join(problems)


# d. phone width ---------------------------------------------------------------------

@pytest.fixture(scope="module")
def phone(playwright, browser):
    device = dict(playwright.devices["iPhone 13"])   # 390x844, touch, mobile user agent
    device.pop("default_browser_type", None)
    device["viewport"] = {"width": 390, "height": 844}
    ctx = _context(browser, **device)
    yield ctx
    ctx.close()


@pytest.mark.parametrize("name", list(PHONE_PAGES))
def test_phone_width_no_js_errors(phone, name):
    v = visit(phone, PHONE_PAGES[name])
    assert v.status is not None and v.status < 500, f"{v.url}: HTTP {v.status}"
    assert not _unexpected(v.errors), f"{v.url} at 390x844: uncaught JS errors: {v.errors}"


# Page wider than the screen? antweb.css sets overflow-x on html and body, which can make body its own
# sideways-scrolling box, so both are measured.
OVERFLOW_JS = """() => { const d = document.documentElement, b = document.body;
    const w = Math.max(d.scrollWidth, b ? b.scrollWidth : 0);
    const wide = [...document.querySelectorAll('body *')].filter(e => e.getBoundingClientRect().right > d.clientWidth + 1)
        .slice(0, 5).map(e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (typeof e.className === 'string' && e.className ? '.' + e.className.trim().split(/\\s+/).join('.') : ''));
    return {scrollWidth: w, clientWidth: d.clientWidth, wide}; }"""


def _open_settled(ctx, path):
    page = ctx.new_page()
    page.errors = []
    page.on("pageerror", lambda err: page.errors.append(str(err)))
    page.goto(BASE_URL + path, wait_until="load")
    page.wait_for_timeout(SETTLE_MS)
    return page


def _assert_fits(page, path, where):
    o = page.evaluate(OVERFLOW_JS)
    assert o["scrollWidth"] <= o["clientWidth"] + 1, \
        f"{path} {where}: page scrolls sideways ({o['scrollWidth']} px content in {o['clientWidth']} px); wide: {o['wide']}"


@pytest.mark.parametrize("name", list(PHONE_PAGES))
def test_phone_page_fits_screen(phone, name):
    page = _open_settled(phone, PHONE_PAGES[name])
    try:
        _assert_fits(page, PHONE_PAGES[name], "at 390 px")
        assert not _unexpected(page.errors), page.errors
    finally:
        page.close()


@pytest.mark.parametrize("name", list(PHONE_PAGES))
def test_phone_header_search_and_menu(phone, name):
    path = PHONE_PAGES[name]
    page = _open_settled(phone, path)
    try:
        box = page.locator("#taxonName").first
        assert box.is_visible(), f"{path}: header Taxa box not visible at 390 px"
        bb = box.bounding_box()
        assert bb and bb["x"] >= 0 and bb["x"] + bb["width"] <= 391 and bb["width"] >= 150, \
            f"{path}: Taxa box not usable at 390 px: {bb}"
        box.click()
        box.press_sequentially("campon", delay=80)
        suggestion = page.locator(".ac_results li", has_text="Camponotus").first
        try:
            suggestion.wait_for(state="visible", timeout=SUGGEST_TIMEOUT_MS)
        except sync_api.TimeoutError:
            pytest.fail(f"{path}: no 'Camponotus' suggestion at 390 px; JS errors: {page.errors}")
        _assert_fits(page, path, "with the suggestion list open")
        page.keyboard.press("Escape")

        toggle = page.locator("#aw_menu_toggle")
        assert toggle.is_visible(), f"{path}: no menu button at 390 px"
        assert not page.locator("#site_links").is_visible(), f"{path}: site links should start folded away"
        toggle.click()
        page.locator("#site_links").wait_for(state="visible", timeout=5000)
        assert page.locator("#browseby").is_visible(), f"{path}: Browse menu not shown after the menu button"
        assert toggle.get_attribute("aria-expanded") == "true"
        _assert_fits(page, path, "with the menu open")
        toggle.click()
        page.locator("#site_links").wait_for(state="hidden", timeout=5000)
        assert not _unexpected(page.errors), page.errors
    finally:
        page.close()


@pytest.mark.parametrize("name", list(PHONE_PAGES))
def test_desktop_layout_unchanged(desktop, name):
    """At 1280 px the responsive rules must not apply: no menu button, full header, no sideways scroll."""
    path = PHONE_PAGES[name]
    page = _open_settled(desktop, path)
    try:
        assert not page.locator("#aw_menu_toggle").is_visible(), f"{path}: phone menu button shown at 1280 px"
        assert page.locator("#site_links").is_visible(), f"{path}: site links hidden at 1280 px"
        assert page.locator("#browseby").is_visible(), f"{path}: Browse menu hidden at 1280 px"
        header = page.locator("#masthead .header").bounding_box()
        assert header and abs(header["width"] - 1000) <= 1, f"{path}: header is no longer 1000 px wide: {header}"
        _assert_fits(page, path, "at 1280 px")
    finally:
        page.close()


# e. wide lists at phone width -----------------------------------------------------------

SPECIES_LIST = "/taxonomicPage.do?rank=species&countryName=Reunion"

# Measures the first div.aw-list-scroll box: its size, the frame classes (responsive.js), the first
# stripe row and the first-column cell of the first (or first striped) row.
LIST_JS = """(firstCol) => { const box = document.querySelector('.aw-list-scroll');
    if (!box) return null;
    const cs = getComputedStyle(box);
    const rows = [...box.querySelectorAll('.specimen_layout')];
    const c0 = rows.length && rows[0].querySelector(firstCol);
    if (c0) c0.scrollIntoView({block: 'center', inline: 'nearest'});
    const br = box.getBoundingClientRect();
    const alt = box.querySelector('.specimen_layout.specimen_layout_alt');
    const cell = (row) => row && row.querySelector(firstCol);
    const at = (el) => { if (!el) return null;
        const r = el.getBoundingClientRect();
        const hit = document.elementFromPoint(Math.min(r.left + 8, br.right - 1), r.top + Math.min(8, r.height / 2));
        return {left: r.left, right: r.right, width: r.width, visible: !!hit && (hit === el || el.contains(hit)),
                bg: getComputedStyle(el).backgroundColor}; };
    return {overflowX: cs.overflowX, scrollWidth: box.scrollWidth, clientWidth: box.clientWidth,
        scrollLeft: box.scrollLeft, boxLeft: br.left, boxRight: br.right,
        frame: box.parentNode.className || '', rows: rows.length,
        rowWidth: rows.length ? rows[0].getBoundingClientRect().width : 0,
        first: at(cell(rows[0])), alt: at(cell(alt)),
        altRowBg: alt ? getComputedStyle(alt).backgroundColor : null}; }"""


def _specimen_list_path(ctx) -> str:
    """A specimen list known to have rows: the first Reunion species' Reunion specimens link."""
    page = _open_settled(ctx, SPECIES_LIST)
    try:
        hrefs = page.eval_on_selector_all(
            ".specimen_layout .list_extras.specimens a[href*='browse.do']",
            "els => els.map(a => a.getAttribute('href')).filter(h => !/global=true/.test(h))")
    finally:
        page.close()
    assert hrefs, f"{SPECIES_LIST}: no Reunion specimen links in the species list"
    parts = urlsplit(hrefs[0] if "://" in hrefs[0] else BASE_URL + "/" + hrefs[0].lstrip("/"))
    return parts.path + ("?" + parts.query if parts.query else "")


def _check_wide_list(page, path, first_col):
    _assert_fits(page, path, "at 390 px")
    m = page.evaluate(LIST_JS, first_col)
    assert m, f"{path}: no div.aw-list-scroll around the list"
    assert m["rows"] > 0, f"{path}: no list rows"
    assert m["overflowX"] in ("auto", "scroll"), f"{path}: list box does not scroll sideways: {m}"
    assert m["scrollWidth"] > m["clientWidth"] + 20, f"{path}: list is not wider than its box at 390 px: {m}"
    assert m["rowWidth"] >= m["scrollWidth"] - 1, \
        f"{path}: row shading stops at {m['rowWidth']} px of a {m['scrollWidth']} px wide list"
    assert "aw-more-right" in m["frame"], f"{path}: no edge shadow for the hidden columns: {m['frame']!r}"
    assert m["first"] and m["first"]["visible"], f"{path}: first column not visible before scrolling: {m}"

    page.evaluate("() => { const b = document.querySelector('.aw-list-scroll'); b.scrollLeft = b.scrollWidth; "
                  "b.dispatchEvent(new Event('scroll')); }")
    page.wait_for_timeout(300)
    m = page.evaluate(LIST_JS, first_col)
    assert m["scrollLeft"] > 20, f"{path}: list box did not scroll: {m}"
    f = m["first"]
    assert f and f["left"] >= m["boxLeft"] - 1 and f["right"] <= m["boxRight"] + 1 and f["visible"], \
        f"{path}: first column ({first_col}) scrolled out of view: {m}"
    if m["alt"]:
        assert m["alt"]["bg"] == m["altRowBg"], \
            f"{path}: sticky column background {m['alt']['bg']} does not match the row shading {m['altRowBg']}"
    assert "aw-scrolled" in m["frame"] and "aw-more-right" not in m["frame"], \
        f"{path}: frame classes not updated after scrolling to the end: {m['frame']!r}"
    _assert_fits(page, path, "after scrolling the list")


@pytest.mark.parametrize("kind", ["species list", "specimen list"])
def test_phone_wide_list_scrolls_in_its_own_box(phone, kind):
    path = SPECIES_LIST if kind == "species list" else _specimen_list_path(phone)
    first_col = ".sd_name" if kind == "species list" else ".sd_specimen_code"
    page = _open_settled(phone, path)
    try:
        _check_wide_list(page, path, first_col)
        assert not _unexpected(page.errors), page.errors
    finally:
        page.close()


def test_desktop_species_list_unchanged(desktop):
    """At 1280 px the list keeps its 1000 px rows and nothing scrolls sideways inside or outside it."""
    page = _open_settled(desktop, SPECIES_LIST)
    try:
        _assert_fits(page, SPECIES_LIST, "at 1280 px")
        m = page.evaluate("""() => { const box = document.querySelector('.aw-list-scroll');
            const d = box && box.querySelector('.specimen_layout .sd_data');
            return box && {frame: box.parentNode.className || '', overflowX: getComputedStyle(box).overflowX,
                sdData: d ? d.getBoundingClientRect().width : 0,
                sticky: d ? getComputedStyle(d.querySelector('.sd_name')).position : ''}; }""")
        assert m, f"{SPECIES_LIST}: no div.aw-list-scroll around the list"
        assert m["overflowX"] == "visible", f"{SPECIES_LIST}: list box scrolls at 1280 px: {m}"
        assert abs(m["sdData"] - 1000) <= 1, f"{SPECIES_LIST}: list rows are no longer 1000 px wide: {m}"
        assert m["sticky"] != "sticky" and "aw-scroll-frame" not in m["frame"], \
            f"{SPECIES_LIST}: phone list rules applied at 1280 px: {m}"
    finally:
        page.close()

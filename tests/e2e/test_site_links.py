"""Every kind of public page: its AntWeb links work, including the links in map popups.

Why: on group.do?id=80 the map popups linked locality.do?code=loc22.8%252C88.3 ("Locality not
found") and showed "West+Bengal%2C+India" (2026-10-22). No test followed links from most page types,
and none looked inside maps, whose popups are built by JavaScript. Brian: "we need to improve the
checks".

What it does (anonymous, read-only, GET only):
 1. Page types: every public page of the function catalog (docs/catalog/actions.csv: access public or
    partial, answers anonymously), taxon pages at each rank, plus the group page of the bug report.
    A real example of each is found by following links from the home page and the lists (countries,
    museums, groups ...), falling back to the pages recorded from staging (expectations.json).
 2. Each example page must itself load. Its same-site links are collected: <a href> and page addresses
    written in its JavaScript; up to ANTWEB_LINKS_PER_PAGE (default 10) per page, spread over the page.
 3. Map popups: pages with a map are opened in Chromium (Playwright); every popup the map script makes
    is recorded (Leaflet's bindPopup) and its links are added (up to ANTWEB_POPUP_LINKS, default 15 a map).
 4. Every link is requested once (4 at a time). Broken: an HTTP error, AntWeb's error page or a stack
    trace, a not-found page or an AntWeb "... not found" / "Invalid request" message, and any link
    encoded twice (%25 followed by two hex digits) that does not open. A %25 link that opens is listed, not
    failed: the stored code itself holds a "%" (e.g. "Praia_das_Conchas_%3C50"), and encoding it once gives %253C.
    Login pages are fine (some pages need a login).
 Never requested: logout, login, uploads, saves, deletes, data jobs, downloads, the API, links with a
 form token (linkcheck.skip_reason).

The report lists each broken link with the page it was found on. About 3 minutes on staging.
"""
import os
import re
import threading
import time
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urljoin, urlsplit

import pytest

import linkcheck as lc
from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session

LINKS_PER_PAGE = int(os.environ.get("ANTWEB_LINKS_PER_PAGE", "10"))
POPUP_LINKS = int(os.environ.get("ANTWEB_POPUP_LINKS", "15"))
MAX_MAP_PAGES = int(os.environ.get("ANTWEB_MAP_PAGES", "14"))
DISCOVERY_BUDGET = int(os.environ.get("ANTWEB_DISCOVERY_PAGES", "60"))
WORKERS = 4

# Starting points for finding examples (lists that link to one page of each kind).
SEEDS = [
    "/",
    "/browse.do?name=formicidae&rank=family&project=allantwebants",
    "/browse.do?genus=camponotus&rank=genus&project=allantwebants",
    "/countries.do", "/regions.do", "/subregions.do", "/bioregions.do", "/museums.do", "/projects.do",
    "/groups.do", "/curators.do", "/artists.do", "/statsPage.do", "/adm1s.do",
]
# Pages that must be checked whatever the crawl finds: the bug report's page and the map pages.
ALWAYS = {
    "list (favourite images)": "/list.do?action=likes",
    "page (project by short name)": "/page.do?name=california",
    "place (place name search)": "/place.do?placeName=Madagascar",
    "group (bug report 2026-10-22)": "/group.do?id=80",
    "bigMap (taxon)": "/bigMap.do?taxonName=formicinae&project=allantwebants",
    "advanced search results": "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae"
                               "&country=Burundi&resultRank=specimen",
}
# Public page types with no example here, and why (printed in the report).
NOT_SAMPLED = {
    "/uploadReport": "needs the id of an upload, linked only from curator pages; tested by test_background_jobs.py "
                     "and test_specimen_upload_roundtrip.py",
}
# Page types that must have an example (the main pages people use).
REQUIRED = ["/", "/description:subfamily", "/description:genus", "/description:species", "/specimen",
            "/locality", "/collection", "/bigPicture", "/country", "/adm1", "/region", "/subregion",
            "/bioregion", "/project", "/museum", "/group", "/curator", "/browse:genus", "/images:genus"]
EXTRA_TYPES = ["/description:subfamily", "/description:genus", "/description:species", "/description:subspecies",
               "/browse:subfamily", "/browse:genus", "/browse:species", "/images:genus", "/images:species"]

# A map call with at least one point: drawGoogleMapX('small', 'map123', <lat> or new Array(<lat>, ...
# (Map.java writes no call when there are no points; an empty call would not be a link fault.)
MAP_CALL = re.compile(r"drawGoogleMap\w*\s*\(\s*'[^']*'\s*,\s*'[^']*'\s*,\s*(?:new Array\(\s*)?-?\d")

# Records the HTML of every Leaflet popup the page's map scripts make (L.Layer.bindPopup), before
# any page script runs: Leaflet 1.x sets window.L once it has loaded, and the hook goes in then.
# A Leaflet that set window.L before filling it would not be hooked: every map page would then fail
# loudly with "has a map but no marker popup was drawn" (check this hook when upgrading Leaflet).
POPUP_HOOK = """
(() => {
  window.__awPopups = [];
  let real;
  function hook(L) {
    try {
      if (L && L.Layer && !L.__awHooked) {
        const orig = L.Layer.prototype.bindPopup;
        L.Layer.prototype.bindPopup = function (content, options) {
          if (typeof content === "string") window.__awPopups.push(content);
          return orig.call(this, content, options);
        };
        L.__awHooked = true;
      }
    } catch (e) {}
    return L;
  }
  Object.defineProperty(window, "L", { configurable: true, get() { return real; }, set(v) { real = hook(v); } });
})();
"""


class Crawl:
    def __init__(self):
        self.session = new_session()
        self.pages = {}          # url -> (problem or None, html)
        self.examples = OrderedDict()   # page type -> url
        self.types = lc.catalog_page_types()

    def fetch(self, url, session=None):
        if url not in self.pages:
            session = session or self.session
            try:
                r = session.get(url, timeout=TIMEOUT)
                bad = lc.problem(r)
                if bad == "AntWeb error page":  # AntWeb shows it on some first requests of a session
                    time.sleep(2)
                    r = session.get(url, timeout=TIMEOUT)
                    bad = lc.problem(r)
                self.pages[url] = (bad, r.text if r.status_code == 200 else "")
            except Exception as e:  # noqa: BLE001
                self.pages[url] = (f"no answer ({type(e).__name__})", "")
        return self.pages[url]

    def bare_ok(self, t):
        """True when the page type answers without parameters (catalog anon_result "ok")."""
        return self.types.get(t, ("", "", "message"))[2] == "ok"

    def discover(self, wanted):
        """Find an example URL for each wanted page type by following links from the seeds."""
        queue = [BASE_URL + s for s in SEEDS]
        for s in SEEDS:
            self.examples.setdefault(lc.page_type(BASE_URL + s), BASE_URL + s)
        self.seed_types = set(self.examples)
        fetched = 0
        while queue and fetched < DISCOVERY_BUDGET and not (wanted <= set(self.examples) and all(
                urlsplit(u).query or self.bare_ok(t) or t in self.seed_types for t, u in self.examples.items())):
            url = queue.pop(0)
            if url in self.pages:
                continue
            fetched += 1
            bad, html = self.fetch(url)
            if bad or not html:
                continue
            for link, _ in lc.links_in(html, url):
                t = lc.page_type(link)
                if t not in wanted or lc.skip_reason(link) or lc.double_encoded(link):
                    continue
                has_query = bool(urlsplit(link).query)
                current = self.examples.get(t)
                # Prefer a page with parameters (region.do?name=Africa, not the bare region.do).
                if current is None and (has_query or t == "/" or self.bare_ok(t)):
                    self.examples[t] = link
                    queue.append(link)
                elif current is not None and has_query and not urlsplit(current).query and t not in self.seed_types:
                    self.examples[t] = link
                    queue.append(link)
        # Not found by following links: an example recorded from staging (expectations.json).
        for p in lc.recorded_pages():
            t = lc.page_type(BASE_URL + p)
            if (t in wanted and t not in self.examples and not lc.skip_reason(BASE_URL + p)
                    and (urlsplit(p).query or self.bare_ok(t))):
                self.examples[t] = BASE_URL + p


def _check(session, url):
    try:
        r = session.get(url, timeout=TIMEOUT)
    except Exception as e:  # noqa: BLE001
        return f"no answer ({type(e).__name__})"
    bad = lc.problem(r)
    if bad == "AntWeb error page":
        time.sleep(2)
        try:
            bad = lc.problem(session.get(url, timeout=TIMEOUT))
        except Exception as e:  # noqa: BLE001
            return f"no answer ({type(e).__name__})"
    return bad


def _popup_links(pages):
    """{page url: [popup link urls]} for pages with a map, from the real map scripts in Chromium.
    {} with a reason when Playwright or Chromium is missing."""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        return {}, "Playwright not installed: map popups not checked"
    out, notes = {}, []
    args = []
    if os.environ.get("ANTWEB_BROWSER_HOST_RULES"):
        args.append(f"--host-resolver-rules={os.environ['ANTWEB_BROWSER_HOST_RULES']}")
    ignore = os.environ.get("ANTWEB_BROWSER_IGNORE_HTTPS_ERRORS") == "1" and not IS_PRODUCTION
    with sync_playwright() as p:
        try:
            browser = p.chromium.launch(args=args)
        except Exception as e:  # noqa: BLE001
            if os.environ.get("GITHUB_ACTIONS"):
                raise
            return {}, f"Chromium not available ({str(e).splitlines()[0]}): map popups not checked"
        ctx = browser.new_context(ignore_https_errors=ignore, user_agent=new_session().headers["User-Agent"])
        ctx.add_init_script(POPUP_HOOK)
        ctx.set_default_timeout(max(TIMEOUT, 60) * 1000)
        for url in pages:
            page = ctx.new_page()
            try:
                page.goto(url, wait_until="load")
                # googleMapInclude.jsp draws the map 400 ms after DOMContentLoaded.
                try:
                    page.wait_for_function("window.__awPopups && window.__awPopups.length > 0", timeout=15000)
                except Exception:  # noqa: BLE001
                    pass
                popups = page.evaluate("window.__awPopups || []")
            except Exception as e:  # noqa: BLE001
                notes.append(f"{lc.short(url)}: could not open in Chromium ({type(e).__name__})")
                popups = None
            finally:
                page.close()
            if popups is None:
                continue
            if not popups:
                out[url] = None   # a map call in the page but no marker popup drawn
                continue
            hrefs = []
            for html in popups:
                for _, href in lc.ANCHOR.findall(html):
                    u = lc.normalise(href, url)
                    if u and u not in hrefs:
                        hrefs.append(u)
            out[url] = hrefs
        ctx.close()
        browser.close()
    return out, "; ".join(notes)


@pytest.mark.skipif(IS_PRODUCTION, reason="link check runs against staging, not production")
def test_links_on_every_page_type():
    started = time.time()
    types = lc.catalog_page_types()
    wanted = set(types) | set(EXTRA_TYPES)
    wanted -= {"/description", "/browse", "/images", "/index"}  # by rank (description:genus ...); /index is home
    wanted -= {"/list", "/page", "/place"} | set(NOT_SAMPLED)    # examples in ALWAYS, or not sampled
    crawl = Crawl()
    crawl.discover(wanted)

    examples = OrderedDict((t, crawl.examples[t]) for t in sorted(crawl.examples) if t in wanted)
    for name, path in ALWAYS.items():
        examples[name] = BASE_URL + path
    missing_required = [t for t in REQUIRED if t not in examples]
    no_example = sorted(t for t in wanted - set(examples) if types.get(t, ("", "", "message"))[2] == "message")
    # Types that answer without parameters: the bare page is the example.
    for t in sorted(wanted - set(examples)):
        if t in types and types[t][2] == "ok":
            examples[t] = urljoin(BASE_URL + "/", types[t][0].lstrip("/"))

    local = threading.local()

    def session():
        if not hasattr(local, "session"):
            local.session = new_session()
        return local.session

    with ThreadPoolExecutor(WORKERS) as pool:   # load the example pages, 4 at a time
        list(pool.map(lambda u: crawl.fetch(u, session()), list(examples.values())))

    broken = []          # (page, link, why)
    to_check = OrderedDict()   # link -> page it was first found on
    map_pages = []
    for t, url in examples.items():
        bad, html = crawl.fetch(url)
        if bad:
            broken.append((f"(example of {t})", url, bad))
            continue
        links = lc.links_in(html, url)
        if MAP_CALL.search(html) and len(map_pages) < MAX_MAP_PAGES and url not in map_pages:
            map_pages.append(url)
        # Links with %25xx are always requested: broken if they do not open ("encoded twice").
        for link, _ in links:
            if lc.double_encoded(link) and not lc.skip_reason(link):
                to_check.setdefault(link, url)
        candidates = [link for link, _ in links if not lc.skip_reason(link) and not lc.double_encoded(link)]
        # Links written in JavaScript are always taken (they are the ones no other test follows).
        script = [link for link, where in links if where == "script" and link in candidates]
        rest = [link for link in candidates if link not in script]
        for link in script[:LINKS_PER_PAGE] + lc.spread(rest, LINKS_PER_PAGE):
            to_check.setdefault(link, url)

    popups, popup_note = _popup_links(map_pages)
    for page_url, hrefs in popups.items():
        if hrefs is None:
            broken.append((page_url, "(map)", "the page has a map but no marker popup was drawn"))
            continue
        for link in hrefs:
            if lc.double_encoded(link) and not lc.skip_reason(link):
                to_check.setdefault(link, page_url + " [map popup]")
        ok = [h for h in hrefs if not lc.double_encoded(h) and not lc.skip_reason(h)]
        for link in lc.spread(ok, POPUP_LINKS):
            to_check.setdefault(link, page_url + " [map popup]")

    def check(link):
        if link in crawl.pages:          # already loaded as an example or while looking for examples
            return crawl.pages[link][0]
        return _check(session(), link)

    items = list(to_check.items())
    percent_ok = []      # links with %25xx that open: the stored value itself holds a "%" (not a fault here)
    with ThreadPoolExecutor(WORKERS) as pool:
        for (link, page), why in zip(items, pool.map(check, [link for link, _ in items])):
            if why:
                broken.append((page, link, ("encoded twice (%25..): " + why) if lc.double_encoded(link) else why))
            elif lc.double_encoded(link):
                percent_ok.append((page, link))

    lines = [f"Link check of {BASE_URL}: {len(examples)} example pages, {len(map_pages)} with a map, "
             f"{len(to_check)} links requested, {len(broken)} broken, {time.time() - started:.0f} s."]
    # Broken links first: the annotation shown on GitHub keeps only the start of this text.
    if broken:
        lines.append("Broken links (page -> link -> why):")
        for page, link, why in broken:
            lines.append(f"  {lc.short(page) if page.startswith('http') else page} -> {lc.short(link)} -> {why}")
    if popup_note:
        lines.append("Note: " + popup_note)
    if percent_ok:
        lines.append("Links with %25 that open (the stored code or name contains a %; check the data): "
                     + "; ".join(f"{lc.short(p) if p.startswith('http') else p} -> {lc.short(l)}" for p, l in percent_ok))
    if no_example:
        lines.append("No example found (not checked): " + ", ".join(no_example))
    lines.append("Not sampled: " + "; ".join(f"{t} ({why})" for t, why in NOT_SAMPLED.items()))
    lines.append("Example pages: " + ", ".join(f"{t} {lc.short(u)}" for t, u in examples.items()))
    print("\n".join(lines))
    assert not missing_required, f"no example page found for {missing_required}\n" + "\n".join(lines)
    assert not broken, "\n".join(lines)

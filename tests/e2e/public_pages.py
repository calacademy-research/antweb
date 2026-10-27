"""Helpers for the public page flow tests (road map step 2; docs/flows/taxon-pages.md and the other
public flow documents). Not a test file.

The examples are found on the site where that is cheap (the genus, species and subspecies of a taxon
page, a specimen with images, its locality and collection, the place pages' species lists), with the
pages recorded from staging (fingerprint.py, expectations.json) as fallbacks. Every request is a GET.
"""
import html
import re
import time
from functools import lru_cache
from urllib.parse import parse_qs, urlsplit

from antweb import BASE_URL, BROKEN, STACK_TRACE, TIMEOUT, classify, new_session

SLOW_TIMEOUT = max(TIMEOUT, 120)

# Answers that mean "busy, try again", not a fault of the page: AntWeb limits simultaneous maps (3),
# image and map comparisons (2 each) and field guides site-wide, and refuses bots when the server is busy.
BUSY_MESSAGES = ("Simultaneous map limit exceeded", "Simultaneous map comparison limit exceeded",
                 "Simultaneous get comparison limit exceeded", "simultaneous field guide creation requests",
                 "Too busy for bots", "Server Initializing", "is initializing the", "One moment please")

MESSAGE_BODY = re.compile(r'<div class="wide_left">\s*<br>\s*<br>\s*<b>(.*?)</b>\s*</div>', re.S)
TAG = re.compile(r"<[^>]+>")
H1 = re.compile(r"<h1[^>]*>(.*?)</h1>", re.S | re.I)
HREF = re.compile(r"""href\s*=\s*(["'])(.*?)\1""", re.I | re.S)


def fetch(path, session=None, allow_redirects=True, tries=4):
    """GET a site path (or full URL); retries a few times while AntWeb answers with a busy message."""
    s = session or new_session()
    url = path if path.startswith("http") else BASE_URL + path
    r = None
    for attempt in range(tries):
        r = s.get(url, timeout=SLOW_TIMEOUT, allow_redirects=allow_redirects)
        if attempt == tries - 1 or not any(m in r.text for m in BUSY_MESSAGES):
            return r
        time.sleep(5 * (attempt + 1))
    return r


def text(fragment):
    return re.sub(r"\s+", " ", html.unescape(TAG.sub(" ", fragment or ""))).strip()


def title(r):
    return classify(r).title


def h1(page):
    return [text(m) for m in H1.findall(page)]


def message(r):
    """The text of AntWeb's message page (common/message.jsp: title "message...", HTTP 200), or None."""
    if classify(r).category != "message":
        return None
    m = MESSAGE_BODY.search(r.text)
    return text(m.group(1)) if m else text(r.text)


def assert_ok(r, what=""):
    """A normal page: HTTP 200, not AntWeb's error or message page, no stack trace."""
    out = classify(r)
    assert out.category == "ok", f"{what or r.url}: {out} {message(r) or ''}"[:600]
    assert r.status_code == 200, f"{what or r.url}: HTTP {r.status_code}"
    assert not STACK_TRACE.search(r.text), f"{what or r.url}: stack trace in the page"


def assert_message(r, expected, what=""):
    """AntWeb's message page (HTTP 200) whose text contains `expected`."""
    out = classify(r)
    assert out.category not in BROKEN, f"{what or r.url}: {out}"
    assert r.status_code == 200, f"{what or r.url}: HTTP {r.status_code}"
    got = message(r)
    assert got is not None, f"{what or r.url}: not a message page: {out}"
    assert expected in got, f"{what or r.url}: message {got[:300]!r} does not contain {expected!r}"


def site_path(link):
    """'https://host/x.do?a=b' or '/x.do?a=b' or 'x.do?a=b' -> '/x.do?a=b' (HTML entities decoded)."""
    link = html.unescape(link.strip())
    parts = urlsplit(link)
    path = parts.path if parts.path.startswith("/") else "/" + parts.path
    return path + ("?" + parts.query if parts.query else "")


def links(page, action):
    """Same-site links to <action>.do in the page, as site paths, in page order, without repeats."""
    out = []
    for _, href in HREF.findall(page):
        if re.search(r"(^|/)" + re.escape(action) + r"\.do(\?|$)", href) and not href.startswith(("mailto:", "javascript:")):
            host = urlsplit(html.unescape(href)).hostname
            if host and host != urlsplit(BASE_URL).hostname and not host.endswith("antweb.org"):
                continue
            p = site_path(href)
            if p not in out:
                out.append(p)
    return out


def params(path):
    """Query parameters of a site path, first value of each."""
    return {k: v[0] for k, v in parse_qs(urlsplit(path).query, keep_blank_values=True).items()}


def api_json(path):
    r = new_session().get(BASE_URL + path, timeout=SLOW_TIMEOUT)
    if r.status_code != 200:
        return None
    try:
        return r.json()
    except ValueError:
        return None


# ---------------------------------------------------------------- examples found on the site

# Subfamilies whose browse page is small (a handful of genera): quick to load, and their genera are
# small enough for a field guide or comparison in a few seconds.
SMALL_SUBFAMILIES = ("martialinae", "aneuretinae", "paraponerinae", "agroecomyrmecinae")


@lru_cache(maxsize=None)
def genus_example():
    """A genus page path found on the subfamily browse page of Formicinae (fallback: Camponotus)."""
    r = fetch("/browse.do?subfamily=formicinae&rank=subfamily")
    for p in links(r.text, "description"):
        q = params(p)
        if q.get("rank") == "genus" and q.get("genus"):
            return f"/description.do?genus={q['genus']}&rank=genus"
    return "/description.do?genus=camponotus&rank=genus"


@lru_cache(maxsize=None)
def small_genus():
    """(subfamily, genus) of a genus in a small subfamily, found on its browse page; None if none."""
    for sub in SMALL_SUBFAMILIES:
        r = fetch(f"/browse.do?subfamily={sub}&rank=subfamily")
        if r.status_code != 200:
            continue
        for p in links(r.text, "description") + links(r.text, "browse"):
            q = params(p)
            if q.get("rank") == "genus" and q.get("genus"):
                return sub, q["genus"].lower()
    return None


@lru_cache(maxsize=None)
def species_example():
    """A valid species with imaged, georeferenced AntWeb specimens: (genus, species), from the API
    (fallback: a fingerprinted page)."""
    body = api_json("/v3.1/specimens?genus=camponotus&status=valid&hasImage=true&georeferenced=true&limit=1&up=1")
    try:
        sp = body["specimens"][0]
        return sp["genus"].lower(), sp["species"].lower()
    except (TypeError, KeyError, IndexError, AttributeError):
        return "nesomyrmex", "clavipilis"


@lru_cache(maxsize=None)
def subspecies_example():
    """A valid subspecies (genus, species, subspecies) from the API's taxon list, or None."""
    for genus in ("camponotus", "pheidole", "polyrhachis", "crematogaster"):
        body = api_json(f"/v3.1/taxa?genus={genus}&taxarank=subspecies&status=valid&limit=5&up=1")
        for t in (body or {}).get("taxa", []):
            if t.get("species") and t.get("subspecies"):
                return t["genus"].lower(), t["species"].lower(), t["subspecies"].lower()
    return None


@lru_cache(maxsize=None)
def imaged_specimen():
    """Code of a georeferenced specimen with images, from the API (fallback: casent0103190)."""
    body = api_json("/v3.1/specimens?genus=camponotus&hasImage=true&georeferenced=true&limit=1&up=1")
    try:
        return body["specimens"][0]["specimenCode"].lower()
    except (TypeError, KeyError, IndexError, AttributeError):
        return "casent0103190"


@lru_cache(maxsize=None)
def specimen_page(code):
    """(response, page text) of specimen.do for a code, fetched once per test process."""
    r = fetch(f"/specimen.do?code={code}")
    return r, r.text

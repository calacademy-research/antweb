"""Helpers for test_site_links.py: which pages to sample, which links to take from them, and
what counts as a broken link. No requests here except through the session passed in."""
import csv
import json
import re
from pathlib import Path
from urllib.parse import parse_qsl, urljoin, urlsplit, urlunsplit

from antweb import BASE_URL, ERROR_PAGE_TEXT, STACK_TRACE, TITLE
from policy import NEVER, WRITE_ACTIONS

HERE = Path(__file__).parent
CATALOG = HERE.parents[1] / "docs" / "catalog" / "actions.csv"
EXPECTATIONS = HERE / "expectations.json"
HOST = urlsplit(BASE_URL).hostname or ""

# A link whose query or path holds "%25" followed by two hex digits was encoded twice
# (locality.do?code=loc22.8%252C88.3, 2026-10-22).
DOUBLE_ENCODED = re.compile(r"%25[0-9A-Fa-f]{2}")

# Links never requested: they end the session, change data, start jobs, or download whole files.
SKIP_ACTIONS = set(NEVER) | set(WRITE_ACTIONS) | {
    "/login", "/logout", "/forgotPassword", "/resetPassword", "/uptime", "/notFound",
    "/speciesListDownload", "/getSpecimenList", "/specimenList", "/searchList", "/uploadFile",
    "/fieldHistory", "/descEdit", "/description-edit", "/editSession", "/imageUpload",
}
# deleteImages.do, saveGroup.do, genGroupMap.do ...: the word, then nothing or a capital (not "genera.do").
SKIP_WORDS = re.compile(r"(^|/)([Dd]elete|[Rr]emove|[Ss]ave|[Uu]pload|[Ll]ogout|[Ee]dit|[Rr]ecalc|[Gg]en|[Dd]ownload)([A-Z]\w*)?\.do$")
SKIP_PARAMS = {"csrf", "token", "_csrf", "org.apache.struts.taglib.html.TOKEN", "isSubmit", "action=delete"}
SKIP_PATH_PARTS = ("/v3.1/", "/api/", "/web/upload/", "/curate", "/util.do", "/utilData.do")

# Pages that answer 200 but say the thing was not found (texts from the Action classes and JSPs).
NOT_FOUND_TEXT = re.compile(
    r"Locality not found|Collection not found|Specimen not found|Taxon not found|Image not found|"
    r"BigPicture not found|Museum not found|Project not found|Country not found|Adm1 not found|"
    r"Region not found|Subregion not found|Bioregion not found|Geolocale not found|Place not found|"
    r"Page not found|No map found|Upload Report not found|Species list not found|Login not found|"
    r"is not in the AntWeb database|Action not found\.|"
    r"Enter a locality name or code|Log not available|Unable to find the file",
)
# Message pages that are a normal answer for a visitor, not a broken link.
LOGIN_MESSAGE = re.compile(r"Please log in|Log In</a> to|must be logged in|log in to", re.I)

ANCHOR = re.compile(r"""<a\s[^>]*?href\s*=\s*(["'])(.*?)\1""", re.I | re.S)
SCRIPT = re.compile(r"<script\b[^>]*>(.*?)</script>", re.I | re.S)
# A quoted same-site page address inside JavaScript: '/locality.do?code=...' or "https://host/x.do?..."
JS_URL = re.compile(r"""(["'`])((?:https?://[^/"'`\s]+)?/[A-Za-z][\w./-]*\.do(?:\?[^"'`\s<>]*)?)\1""")


def catalog_page_types():
    """Public page types from the function catalog: {action path: (url, purpose, anon_result)}."""
    out = {}
    with open(CATALOG, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["kind"] != "action" or r["status"] != "keep":
                continue
            if r["access"] not in ("public", "partial", "no check found"):
                continue
            if r["anon_result"] not in ("ok", "message"):
                continue
            purpose = r["purpose"]
            if purpose.startswith(("Lower-case alias", "Alias of")) or r["jsps"].strip().endswith(".do"):
                continue
            if r["key"] in SKIP_ACTIONS:
                continue
            out[r["key"]] = (r["url"], purpose, r["anon_result"])
    return out


def recorded_pages():
    """Example pages recorded from staging by survey.py (expectations.json), those that answered ok."""
    try:
        pages = json.loads(EXPECTATIONS.read_text())["pages"]
    except (OSError, ValueError, KeyError):
        return []
    return [p for p, outcome in pages.items() if outcome == "ok" and p.startswith("/") and not p.startswith("//")]


def page_type(url):
    """The page type of a same-site URL: the action path ("/" for home), with the taxon rank for
    taxon pages ("/description:genus"), or None."""
    parts = urlsplit(url)
    path = parts.path or "/"
    if path in ("/", "/index.do"):
        return "/"
    if not path.endswith(".do"):
        return None
    action = path[:-3]
    if action in ("/description", "/browse", "/images"):
        q = dict(parse_qsl(parts.query))
        rank = q.get("rank", "").lower()
        if not rank:
            rank = ("subspecies" if q.get("subspecies") else "species" if q.get("species") else
                    "genus" if q.get("genus") else "subfamily" if q.get("subfamily") else "")
        return f"{action}:{rank}" if rank else action
    return action


def same_site(url):
    return (urlsplit(url).hostname or "") == HOST


def normalise(href, page_url):
    """Absolute same-site URL without fragment, or None for links not to request."""
    href = (href or "").strip().replace("&amp;", "&")
    if not href or href.startswith(("mailto:", "javascript:", "#", "tel:", "data:")):
        return None
    url = urljoin(page_url, href)
    parts = urlsplit(url)
    if parts.scheme not in ("http", "https") or not same_site(url):
        return None
    scheme = urlsplit(BASE_URL).scheme
    return urlunsplit((scheme, parts.netloc, parts.path or "/", parts.query, ""))


def skip_reason(url):
    """Why a link is not requested (it changes something or downloads a whole file), or None."""
    parts = urlsplit(url)
    path = parts.path
    action = path[:-3] if path.endswith(".do") else None
    if action in SKIP_ACTIONS:
        return f"{action}.do is never requested by the tests"
    if SKIP_WORDS.search(path):
        return "changes data or downloads"
    if any(p in path for p in SKIP_PATH_PARTS):
        return "API, curator or admin tool"
    q = parts.query
    for name, value in parse_qsl(q, keep_blank_values=True):
        if name in SKIP_PARAMS or "token" in name.lower() or "csrf" in name.lower():
            return "carries a form token"
        if name == "action" and re.match(r"(delete|remove|save|gen|recalc|upload)", value, re.I):
            return "changes data"
    if path.lower().endswith((".jpg", ".jpeg", ".png", ".gif", ".tif", ".tiff", ".zip", ".pdf", ".xlsx", ".txt", ".csv")):
        return "file, not a page"
    return None


def links_in(html, page_url):
    """Every same-site link on a page: <a href> and page addresses written in its JavaScript.
    Returns [(url, where)], in page order, without duplicates; where is "a" or "script"."""
    found = {}
    for _, href in ANCHOR.findall(html):
        u = normalise(href, page_url)
        if u:
            found.setdefault(u, "a")
    for script in SCRIPT.findall(html):
        for _, href in JS_URL.findall(script):
            u = normalise(href, page_url)
            if u:
                found.setdefault(u, "script")
    return list(found.items())


def double_encoded(url):
    return bool(DOUBLE_ENCODED.search(urlsplit(url).query) or DOUBLE_ENCODED.search(urlsplit(url).path))


def problem(resp):
    """What is wrong with a page answer, or None. Login pages and "please log in" messages are fine."""
    body = resp.text or ""
    m = TITLE.search(body)
    title = re.sub(r"\s+", " ", m.group(1)) if m else ""
    final = resp.url
    if resp.status_code >= 400:
        return f"HTTP {resp.status_code}" + (f" ({title})" if title else "")
    if "error.jsp" in final or ERROR_PAGE_TEXT in body:
        return "AntWeb error page"
    st = STACK_TRACE.search(body)
    if st:
        return "stack trace in the page"
    if "login.do" in urlsplit(final).path or "Please Log In" in title or "notLoggedIn.jsp" in final:
        return None
    if re.search(r"<title>[^<]*(Not Found|Index of)", body, re.I):
        return f"not-found page ({title})"
    # The page's text only (no scripts, styles, comments, tags or attributes such as alt="...").
    visible = re.sub(r"<script\b.*?</script>|<style\b.*?</style>|<!--.*?-->", " ", body, flags=re.S | re.I)
    visible = re.sub(r"\s+", " ", re.sub(r"<[^>]*>", " ", visible))
    nf = NOT_FOUND_TEXT.search(visible)
    if nf:
        return f'"{nf.group(0)}"' + (f" ({_message(body)})" if title.startswith("message") else "")
    if title.startswith("message..."):
        msg = _message(body)
        if re.search(r"invalid request", msg, re.I):   # only on a message page (page text may say it)
            return f'"Invalid request" ({msg})'
        if LOGIN_MESSAGE.search(msg):
            return None
        return f'message page: "{msg}"'
    return None


def _message(body):
    """The text of AntWeb's message page (common/message-body.jsp), shortened."""
    text = re.sub(r"<script\b.*?</script>|<style\b.*?</style>", " ", body, flags=re.S | re.I)
    m = re.search(r'class="wide_left">(.*)', text, re.S) or re.search(r"<body\b(.*)", text, re.S | re.I)
    text = re.sub(r"<[^>]+>", " ", m.group(1) if m else text)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:160]


def spread(items, n):
    """At most n items, spread over the list (first, last and evenly between)."""
    if n <= 0 or len(items) <= n:
        return list(items)
    step = (len(items) - 1) / (n - 1) if n > 1 else 0
    return [items[round(i * step)] for i in range(n)]


def short(url):
    """The URL as written (still encoded), without the site address."""
    p = urlsplit(url)
    return (p.path or "/") + ("?" + p.query if p.query else "") if same_site(url) else url

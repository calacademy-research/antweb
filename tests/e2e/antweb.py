"""Shared helpers for the AntWeb end-to-end tests.

The tests drive a running AntWeb over HTTP, the way a visitor or curator does.
They do not depend on Struts internals, so they stay valid while the web layer
is upgraded or replaced.

Target: ANTWEB_URL (default https://staging.antweb.org). Never point the
data-changing tests at production; see ALLOW_WRITES below.
"""
import os
import re
import time as _time
import urllib.parse
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

import requests

BASE_URL = os.environ.get("ANTWEB_URL", "https://staging.antweb.org").rstrip("/")
IS_PRODUCTION = "www.antweb.org" in BASE_URL or BASE_URL.endswith("//antweb.org")
REPO_ROOT = Path(__file__).resolve().parents[2]
STRUTS_CONFIG = REPO_ROOT / "WEB-INF" / "struts-config.xml"

USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AntWebTestSuite/1.0 (+https://github.com/calacademy-research/antweb)"
TIMEOUT = float(os.environ.get("ANTWEB_TIMEOUT", "60"))

LOGIN_TITLE = "Please Log In"
ERROR_PAGE_TEXT = "We'll be right back!"
# A Java stack trace printed into a page: "...Exception..." followed by "at org.x.y(".
STACK_TRACE = re.compile(r"(Exception|Error)\b.{0,300}?\bat (org\.calacademy|org\.apache|java\.|javax\.)[\w.$]+\(", re.S)
TITLE = re.compile(r"<title>\s*(.*?)\s*</title>", re.I | re.S)


# ---------- the API: v3.1 with the admin switch while v4 is closed, v4 with the test key once it is open ----------
#
# The tests ask the API as "/v3.1/<route>?...&up=1" (the admin switch; the only way in while the API is closed).
# When v4 opens, v3.1 ends (every data route 410, up=1 does nothing; decided by Brian Fisher 2026-11-09). So that the
# suite passes in both states, every session from new_session() sends such a request to /v4/<route> instead, without
# up, with the test key in the X-API-Key header, whenever the server says v4 is open (/v3.1/ -> "v4": {"status":
# "open"}). A request to /v3.1/ without up=1 is never changed (tests of v3.1's own answers). The replies differ only in
# the v4 field names: collectingEventCode (v3.1 collectionCode), specimenCode in /taxaImages (v3.1 code), localityCode.
# v4's specimen routes answer for ants only unless asked otherwise (2026-11-12); v3.1 answers for every family. So a
# /specimens or /geoSpecimens request goes to v4 with allFamilies=true: the tests compare the API with the site's
# numbers, which count every specimen, and must mean the same in both states.
#
# The key: ANTWEB_TEST_API_KEY in the environment (GitHub Actions secret of that name, staging only), an admin key
# made on staging by tools/api/create-test-key.py. Never in the repository, never printed. Without it (or when the
# server refuses it) the tests that need the API are skipped, and test_api.py::test_the_tests_can_reach_the_api fails
# with what to do.
API_KEY_ENV = "ANTWEB_TEST_API_KEY"
API_KEY_HEADER = "X-API-Key"
_API_STATE = {}


def api_test_key() -> str:
    return os.environ.get(API_KEY_ENV, "").strip()


def v4_open() -> bool:
    """Does the server say v4 is open? Asked once per process (ANTWEB_API_V4=open or closed skips the question). An
    unreadable answer counts as closed and is asked again next time."""
    if "open" in _API_STATE:
        return _API_STATE["open"]
    forced = os.environ.get("ANTWEB_API_V4", "").strip().lower()
    if forced in ("open", "closed"):
        _API_STATE["open"] = forced == "open"
        return _API_STATE["open"]
    try:
        r = requests.get(BASE_URL + "/v3.1/", timeout=TIMEOUT, headers={"User-Agent": USER_AGENT})
        body = r.json()
        is_open = (body.get("v4") or {}).get("status") == "open"
    except (requests.RequestException, ValueError, AttributeError):
        return False
    _API_STATE["open"] = is_open
    return is_open


V4_ANTS_ONLY_ROUTES = ("specimens", "geoSpecimens")   # api_v4.FAMILY_ROUTES: ants only unless allFamilies=true


def api_v4_url(url: str):
    """The /v4/ address of a "/v3.1/<route>?...up=1" request (up removed), or None when the address is not one.
    The specimen routes get allFamilies=true, so the answer is of every family as v3.1's is."""
    parts = urllib.parse.urlsplit(url)
    if not parts.path.startswith("/v3.1/") or parts.path == "/v3.1/":
        return None
    query = urllib.parse.parse_qsl(parts.query, keep_blank_values=True)
    if ("up", "1") not in query:
        return None
    query = [(k, v) for k, v in query if k != "up"]
    if parts.path[len("/v3.1/"):] in V4_ANTS_ONLY_ROUTES and "allFamilies" not in dict(query):
        query.append(("allFamilies", "true"))
    return urllib.parse.urlunsplit((parts.scheme, parts.netloc, "/v4/" + parts.path[len("/v3.1/"):],
                                    urllib.parse.urlencode(query), ""))


def _skip(reason):
    import pytest
    pytest.skip(reason, allow_module_level=True)


class ApiSession(requests.Session):
    """A requests session that reaches the API in both states (see above). Everything else is unchanged."""

    def request(self, method, url, **kwargs):
        target = api_v4_url(str(url)) if str(method).upper() == "GET" and str(url).startswith(BASE_URL + "/v3.1/") else None
        if target is None or not v4_open():
            return super().request(method, url, **kwargs)
        key = api_test_key()
        if not key:
            _skip(f"v4 is open on {BASE_URL} and {API_KEY_ENV} is not set: the test cannot reach the API")
        headers = dict(kwargs.pop("headers", None) or {})
        headers[API_KEY_HEADER] = key
        kwargs.setdefault("allow_redirects", False)   # the key never follows a redirect to another address
        r = None
        for _attempt in range(3):
            r = super().request(method, target, headers=headers, **kwargs)
            if r.status_code != 429:
                break
            try:
                wait = int(r.headers.get("Retry-After", "60"))
            except ValueError:
                wait = 60
            if wait > 65:   # the day's limit: waiting does not help
                break
            _time.sleep(wait + 1)
        if r.status_code == 401:
            _skip(f"the server refused the key in {API_KEY_ENV} (HTTP 401): make a new one with"
                  " tools/api/create-test-key.py on staging and update the secret")
        return r


def new_session() -> requests.Session:
    s = ApiSession()
    s.headers["User-Agent"] = USER_AGENT
    return s


@dataclass
class Action:
    path: str          # "/description"
    type: str | None   # Java class, or None for a plain forward
    forward: str | None

    @property
    def url(self) -> str:
        return f"{self.path}.do" if self.path != "/" else "/"


def struts_actions() -> list[Action]:
    """Every action mapping in struts-config.xml (duplicates by path removed)."""
    root = ET.parse(STRUTS_CONFIG).getroot()
    seen, out = set(), []
    for a in root.find("action-mappings").findall("action"):
        path = a.get("path")
        if path in seen:
            continue
        seen.add(path)
        out.append(Action(path, a.get("type"), a.get("forward")))
    return out


@dataclass
class Outcome:
    category: str      # ok | login | denied | message | error_page | stack_trace | server_error | not_found | other
    status: int
    final_url: str
    title: str
    detail: str = ""


def classify(resp: requests.Response) -> Outcome:
    body = resp.text or ""
    final = resp.url.replace(BASE_URL, "")
    m = TITLE.search(body)
    title = re.sub(r"\s+", " ", m.group(1)) if m else ""
    if resp.status_code >= 500:
        return Outcome("server_error", resp.status_code, final, title, body[:200])
    if "error.jsp" in final or ERROR_PAGE_TEXT in body:
        return Outcome("error_page", resp.status_code, final, title)
    st = STACK_TRACE.search(body)
    if st:
        return Outcome("stack_trace", resp.status_code, final, title, st.group(0)[:200])
    if resp.status_code == 404:
        return Outcome("not_found", 404, final, title)
    if LOGIN_TITLE in title or "notLoggedIn.jsp" in final or final.startswith("/login.do"):
        return Outcome("login", resp.status_code, final, title)
    if "permissionDenied.jsp" in final:
        return Outcome("denied", resp.status_code, final, title)
    if title.startswith("message..."):
        return Outcome("message", resp.status_code, final, title)
    if 200 <= resp.status_code < 400:
        return Outcome("ok", resp.status_code, final, title)
    return Outcome("other", resp.status_code, final, title)


def get(session: requests.Session, url: str) -> Outcome:
    full = url if url.startswith("http") else BASE_URL + url
    return classify(session.get(full, timeout=TIMEOUT, allow_redirects=True))


# Categories that always mean something is broken, whatever the action.
BROKEN = {"server_error", "error_page", "stack_trace"}


# The site allows 10 POSTs to /login.do per minute per visitor (Caddy rate_limit). The suite logs in
# from many modules, so every test login goes through here: at most 7 per rolling minute, and a refused
# (429) login waits and retries.
_LOGIN_TIMES: list = []


def login_slot():
    """Wait until another login POST fits under the site's limit, and count it."""
    now = _time.time()
    while _LOGIN_TIMES and now - _LOGIN_TIMES[0] > 60:
        _LOGIN_TIMES.pop(0)
    if len(_LOGIN_TIMES) >= 7:
        _time.sleep(61 - (now - _LOGIN_TIMES[0]))
    _LOGIN_TIMES.append(_time.time())


# Set by conftest.py while a test of the parallel set runs in a parallel run (pytest -n): those
# tests share the site with other workers at the same moment, so they must never log in.
PARALLEL_TEST = None


def post_login(session, user, password, timeout=60, extra=None, allow_redirects=True):
    if PARALLEL_TEST:
        raise RuntimeError(f"{PARALLEL_TEST} logs in but is listed in [parallel] in tests/e2e/areas.toml: "
                           "remove it from that list (logged-in tests run one at a time)")
    data = {"userName": user, "password": password, "isSubmit": "true", **(extra or {})}
    for attempt in range(4):
        login_slot()
        r = session.post(BASE_URL + "/login.do", data=data, timeout=timeout, allow_redirects=allow_redirects)
        if r.status_code != 429:
            return r
        _time.sleep(30)
    return r

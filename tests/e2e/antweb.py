"""Shared helpers for the AntWeb end-to-end tests.

The tests drive a running AntWeb over HTTP, the way a visitor or curator does.
They do not depend on Struts internals, so they stay valid while the web layer
is upgraded or replaced.

Target: ANTWEB_URL (default https://staging.antweb.org). Never point the
data-changing tests at production; see ALLOW_WRITES below.
"""
import os
import re
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


def new_session() -> requests.Session:
    s = requests.Session()
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
import time as _time
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

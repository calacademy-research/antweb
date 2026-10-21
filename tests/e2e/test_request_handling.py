"""Request handling checks (input with quote characters, form posts, error pages).

Anonymous checks (any host, read-only GETs; safe on production):
- quote characters in taxonName on curation.do / curator.do give a normal page;
- error pages show no exception text in the page source;
- the server-busy report is not public.

Curator checks (staging only, ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, never production). They change
nothing: every request is refused or only reads.
- defaultSpecimen.do: redirects within the site; changes only by POST with the form token, only for
  known names;
- bioregionMapMgr.do: "refresh" only by POST and only for administrators; unknown names refused;
  introducedMapMgr.do the same (administrators only since 2026-10-18: a curator gets the log-in page);
- fieldHistory.do: Restore forms post to fieldHistory.do itself, for page-text pages only;
- the form token is read from the form body of a normal POST (the URL is used only for uploads).

Requests that would change data on a server without this code (a GET with command=post, a refresh
GET) run only where it is deployed. Deployment is detected by a harmless request: defaultSpecimen.do
without parameters redirects to the site, not to the Referer.
"""
import html
import re
from urllib.parse import parse_qs, urlencode, urlsplit, urlunsplit

import pytest

from antweb import BASE_URL, BROKEN, IS_PRODUCTION, STACK_TRACE, TIMEOUT, classify, get, new_session
import test_page_editor as pe

SQL_ERROR = re.compile(r"SQLSyntaxError|SQLException|You have an error in your SQL syntax|com\.mysql|"
                       r"MySQLSyntax|check the manual that corresponds", re.I)
QUOTES = ["x'", "x' or '1'='1", "x\\'", 'x"y', "x')) -- "]

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

TEST_TAXON_NAME = "formicinaepolyrhachis amana"   # the approved test taxon of test_page_editor.py
TEST_SPECIMEN = pe.TEST_SPECIMEN


def _clean(resp):
    out = classify(resp)
    assert out.category not in BROKEN, (resp.url, out)
    assert not SQL_ERROR.search(resp.text), (resp.url, SQL_ERROR.search(resp.text).group(0))
    return out


# ---------- anonymous ----------

@pytest.mark.parametrize("action", ["/curation.do", "/curator.do"])
@pytest.mark.parametrize("taxon", QUOTES)
def test_quote_in_taxon_name_anonymous(action, taxon):
    s = new_session()
    r = s.get(BASE_URL + action, params={"geolocaleId": "1", "taxonName": taxon}, timeout=TIMEOUT)
    _clean(r)


ERROR_PROBES = ["/error.jsp", "/specimen.jsp", "/descEditReport.jsp", "/search/fieldGuide.jsp",
                "/search/basicResults.jsp", "/search/recentSearchResults.jsp"]


@pytest.mark.parametrize("url", ERROR_PROBES)
def test_error_pages_show_no_exception_text(url):
    r = new_session().get(BASE_URL + url, timeout=TIMEOUT)
    body = r.text or ""
    assert "error.jsp exception" not in body, url
    assert "error.jsp logMessage" not in body, url
    assert not STACK_TRACE.search(body), url
    assert not re.search(r"\bjava\.(lang|io|util|sql)\.\w+(Exception|Error)\b", body), url
    assert not SQL_ERROR.search(body), url


def test_server_busy_report_not_public():
    r = new_session().get(BASE_URL + "/web/log/serverBusy.html", timeout=TIMEOUT, allow_redirects=True)
    assert r.status_code != 200 or "reportServerBusy" not in r.text
    for admin_page in ("/serverStatus.do", "/dbStatus.do"):
        assert get(new_session(), admin_page).category == "login", admin_page


# ---------- curator (staging only) ----------

@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


def _token(session):
    return session.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json().get("csrf")


def _redirect_target(session, referer):
    r = session.get(BASE_URL + "/defaultSpecimen.do", headers={"Referer": referer}, timeout=TIMEOUT,
                    allow_redirects=False)
    return r.status_code, r.headers.get("Location", "")


@pytest.fixture(scope="module")
def deployed(curator):
    status, location = _redirect_target(curator, BASE_URL + "/about.do")
    if status not in (301, 302, 303) or "about.do" in location:
        pytest.skip("batch 6 fixes are not deployed on this server; probes that could change data are skipped")
    return True


@staging_only
def test_default_specimen_never_redirects_to_referer(curator):
    status, location = _redirect_target(curator, "https://other.example/landing")
    assert status in (301, 302, 303), status
    assert "other.example" not in location, location


@staging_only
@pytest.mark.parametrize("command", ["post", "unpost", "delete"])
def test_default_specimen_get_changes_nothing(curator, deployed, command):
    r = curator.get(BASE_URL + "/defaultSpecimen.do", params={
        "command": command, "caste": "worker", "taxonName": TEST_TAXON_NAME, "specimenCode": TEST_SPECIMEN},
        timeout=TIMEOUT, allow_redirects=True)
    _clean(r)
    assert "Nothing was changed" in r.text, r.text[:300]


@staging_only
def test_default_specimen_post_without_token_is_refused(curator):
    r = curator.post(BASE_URL + "/defaultSpecimen.do", data={
        "command": "post", "caste": "worker", "taxonName": TEST_TAXON_NAME, "specimenCode": TEST_SPECIMEN},
        timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 403, r.status_code


@staging_only
@pytest.mark.parametrize("taxon", QUOTES)
def test_default_specimen_quotes_are_refused(curator, deployed, taxon):
    r = curator.post(BASE_URL + "/defaultSpecimen.do", data={
        "_csrf": _token(curator), "command": "post", "caste": "worker", "taxonName": taxon, "specimenCode": taxon},
        timeout=TIMEOUT, allow_redirects=True)
    _clean(r)
    assert "Nothing was changed" in r.text, r.text[:300]


@staging_only
def test_bioregion_map_refresh_get_is_refused(curator, deployed):
    r = curator.get(BASE_URL + "/bioregionMapMgr.do", params={"isRefresh": "true"}, timeout=TIMEOUT)
    _clean(r)
    assert "Only an administrator" in r.text or "Nothing was changed" in r.text, r.text[:300]


@staging_only
def test_introduced_map_refresh_get_does_nothing(curator, deployed):
    r = curator.get(BASE_URL + "/introducedMapMgr.do", params={"isRefresh": "true"}, timeout=TIMEOUT)
    _clean(r)


@staging_only
@pytest.mark.parametrize("action,word", [("/bioregionMapMgr.do", "genus"), ("/introducedMapMgr.do", "species")])
@pytest.mark.parametrize("taxon", QUOTES)
def test_map_manager_quotes_are_refused(curator, deployed, action, word, taxon):
    r = curator.post(BASE_URL + action, data={"_csrf": _token(curator), "taxonName": taxon},
                     timeout=TIMEOUT, allow_redirects=True)
    out = _clean(r)
    if action == "/introducedMapMgr.do" and out.category == "login":
        return   # administrators only since 2026-10-18: refused before the name is read
    if action == "/bioregionMapMgr.do" and "Only an administrator can change the native bioregions" in r.text:
        return   # saving a genus is for administrators since 2026-10-21: refused before the name is read
    assert f"unknown {word}" in r.text, r.text[:300]


@staging_only
@pytest.mark.parametrize("action", ["/curation.do", "/curator.do"])
def test_quote_in_taxon_name_curator(curator, action):
    for taxon in QUOTES:
        r = curator.get(BASE_URL + action, params={"geolocaleId": "1", "taxonName": taxon}, timeout=TIMEOUT)
        _clean(r)


FORM_TAG = re.compile(r"<form\b[^>]*>", re.I)
ACTION = re.compile(r'action\s*=\s*"([^"]*)"', re.I)
PAGE_INPUT = re.compile(r'<input type="hidden" name="page" value="([^"]*)"')


def _history_with_page(session, page):
    import test_field_history as fh
    href = fh.history_url(session, pe.TEST_TAXON_URL, pe.TEST_TAXON_FIELD)
    parts = urlsplit(href if href.startswith("http") else BASE_URL + href)
    q = {k: v[0] for k, v in parse_qs(parts.query).items()}
    q["page"] = page
    url = urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(q), ""))
    r = session.get(url, timeout=TIMEOUT)
    _clean(r)
    return r.text


@staging_only
@pytest.mark.parametrize("page", ["saveGroup.do?step=delete&id=5", "util.do?action=reloadAntwebMgr",
                                  "/defaultSpecimen.do?command=post&taxonName=x", "https://other.example/otherpage.do?a=1"])
def test_field_history_forms_post_to_field_history(curator, page):
    text = _history_with_page(curator, page)
    action_name = page.lstrip("/").split("?")[0].split("/")[-1]
    for tag in FORM_TAG.findall(text):
        m = ACTION.search(tag)
        target = html.unescape(m.group(1)) if m else ""
        assert action_name not in target, tag
        if "fh-restore" in tag:
            assert urlsplit(target).path.endswith("/fieldHistory.do"), tag
    for value in PAGE_INPUT.findall(text):
        assert action_name not in html.unescape(value), value


@staging_only
def test_field_history_page_keeps_only_naming_parameters(curator, deployed):
    text = _history_with_page(curator, pe.TEST_TAXON_URL.lstrip("/") + "&action=recalc&step=delete")
    for value in PAGE_INPUT.findall(text):
        v = html.unescape(value)
        assert v.startswith("description.do?"), v
        assert "action=" not in v and "step=" not in v, v


@staging_only
@pytest.mark.parametrize("page", ["saveGroup.do?step=delete&id=5", "editLogin.do?id=1"])
def test_restore_only_for_page_text_pages(curator, deployed, page):
    r = curator.post(BASE_URL + "/fieldHistory.do", data={
        "_csrf": _token(curator), "page": page, "editField": pe.TEST_TAXON_FIELD, "isSaveEditField": "true",
        "restoreFrom": "1", "loadedHash": "0" * 64, "editorReady": "1", "clearField": "0"},
        timeout=TIMEOUT, allow_redirects=True)
    _clean(r)
    assert "Nothing was changed" in r.text, r.text[:300]


@staging_only
def test_form_token_in_url_is_not_accepted(curator, deployed):
    token = _token(curator)
    r = curator.post(BASE_URL + "/fieldHistory.do?_csrf=" + token, data={"page": "x"},
                     timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 403, r.status_code
    # The same request with the token in the form body passes the check (and changes nothing).
    r = curator.post(BASE_URL + "/fieldHistory.do", data={"_csrf": token, "page": "x"},
                     timeout=TIMEOUT, allow_redirects=True)
    _clean(r)
    assert r.status_code == 200 and "Nothing was changed" in r.text, r.text[:300]

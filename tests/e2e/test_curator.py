"""Tier 3: a logged-in curator (staging test account, never production).

Uses the GitHub secrets ANTWEB_TEST_USER and ANTWEB_TEST_PASSWORD: an ordinary
curator ("Default Group", can upload specimens and images, not an admin).
These tests only read pages; they do not upload or change data.
"""
import os
import re
from urllib.parse import urlparse

import pytest

from antweb import BASE_URL, IS_PRODUCTION, get, new_session

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

pytestmark = pytest.mark.skipif(
    not USER or not PASSWORD or IS_PRODUCTION,
    reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, and never runs against production",
)

# Pages a logged-in curator uses.
CURATOR_PAGES = [
    "/curate.do",
    "/uploadHistory.do",
    "/listSpecimenUploads.do",
    "/listImageUploads.do",
    "/speciesListTool.do",
    "/validateSpeciesList.do",
    "/specimenListLookup.do",
    "/curators.do",
    "/editorTrial.do",
    "/curatorFiles.do",
]

# Admin-only pages: an ordinary curator must be refused (authorization, not just login).
ADMIN_ONLY = [
    "/viewLogins.do",
    "/manageMuseum.do",
    "/saveGroup.do",
    "/editCredit.do",
    "/worldAuthoritySave.do",
    "/newBioRegion.do",
    "/serverDebug.do",
    "/dbStatus.do",
    "/loginAs.do?id=1",
    "/sanitizeDescriptions.do",
]


def login(session):
    r = session.post(BASE_URL + "/login.do",
                     data={"userName": USER, "password": PASSWORD, "isSubmit": "true"},
                     timeout=60, allow_redirects=True)
    return r


@pytest.fixture(scope="module")
def curator():
    s = new_session()
    login(s)
    return s


def test_wrong_password_is_refused():
    s = new_session()
    s.post(BASE_URL + "/login.do", data={"userName": USER, "password": PASSWORD + "-wrong", "isSubmit": "true"}, timeout=60)
    assert get(s, "/curate.do").category == "login"


def test_login_works(curator):
    assert get(curator, "/curate.do").category != "login"


@pytest.mark.parametrize("url", CURATOR_PAGES)
def test_curator_page(curator, url):
    outcome = get(curator, url)
    assert outcome.category in ("ok", "message"), f"{url}: {outcome}"


@pytest.mark.parametrize("url", ADMIN_ONLY)
def test_admin_page_refused_for_curator(curator, url):
    outcome = get(curator, url)
    assert outcome.category in ("login", "denied"), f"{url} was not refused for an ordinary curator: {outcome}"


def test_other_account_refused_for_curator(curator):
    """viewLogin.do: a non-admin may open only their own account (login 1 is an administrator)."""
    outcome = get(curator, "/viewLogin.do?id=1")
    assert outcome.category in ("message", "denied", "login"), f"/viewLogin.do?id=1: {outcome}"


def test_curator_files_page(curator):
    """The curator's own folder is shown by curatorFiles.do (Tomcat does not list folders)."""
    r = curator.get(BASE_URL + "/curatorFiles.do", timeout=60)
    assert r.status_code == 200 and "Your Curator Files" in r.text
    assert "/web/curator/" in r.text
    assert "curatorFiles.do" in curator.get(BASE_URL + "/curate.do", timeout=60).text


def test_password_not_in_pages(curator):
    """The curator's own password must never be echoed into a page they open themselves.
    Passwords are stored hashed and never pre-filled into forms."""
    for url in ("/curate.do", "/editLogin.do", "/curator.do"):
        r = curator.get(BASE_URL + url, timeout=60)
        assert PASSWORD not in r.text, f"{url} contains the password"


def test_logout():
    s = new_session()
    login(s)
    s.get(BASE_URL + "/logout.do", timeout=60)
    assert get(s, "/curate.do").category == "login"


# Specimen List Lookup (GitHub #64): a read-only tool, so safe to exercise here.
KNOWN_CODES = ["casent0872013", "casent0922067"]  # CAS specimens (TaxonWorks upload 2026-10-14)
MISSING_CODES = ["zz-e2e-missing-001", "ZZ-E2E-MISSING-002"]


def _lookup(curator, lines):
    body = ("\n".join(lines) + "\n").encode()
    # A multipart POST carries the CSRF token in the URL (the filter does not read the body).
    return curator.post(BASE_URL + "/specimenListLookup.do?_csrf=" + csrf_token(curator),
                        files={"file": ("e2e_specimens.txt", body, "text/plain")},
                        timeout=120, allow_redirects=True)


CSRF_FIELD = re.compile(r'name="_csrf" value="([A-Za-z0-9_-]+)"')


def csrf_token(session, url="/specimenListLookup.do"):
    """The session's CSRF token, read from the hidden _csrf field of a page with a POST form."""
    r = session.get(BASE_URL + url, timeout=60)
    m = CSRF_FIELD.search(r.text)
    assert m, f"no _csrf field on {url}"
    return m.group(1)


def test_specimen_lookup_all_found_downloads_tsv(curator):
    r = _lookup(curator, KNOWN_CODES)
    assert r.status_code == 200
    assert "tab-separated-values" in r.headers.get("Content-Type", ""), r.text[:300]
    rows = r.text.strip().split("\n")
    assert len(rows) == 1 + len(KNOWN_CODES)


def test_specimen_lookup_lists_missing_codes_and_keeps_found_data(curator):
    r = _lookup(curator, [KNOWN_CODES[0], MISSING_CODES[0], "casent 0872013", MISSING_CODES[1], MISSING_CODES[0].upper()])
    assert r.status_code == 200
    assert "text/html" in r.headers.get("Content-Type", "")
    assert "Not found in AntWeb" in r.text
    # Each missing code once, as typed, in file order, inside the copy box.
    box = r.text.split('id="notFoundCodes"', 1)[1].split("</textarea>", 1)[0]
    assert box.split(">", 1)[1].strip().split("\n") == MISSING_CODES
    assert "specimenListLookup.do?download=tsv" in r.text
    spaced = r.text.split('id="invalidCodes"', 1)[1].split("</textarea>", 1)[0]
    assert spaced.split(">", 1)[1].strip() == "casent 0872013"

    d = curator.get(BASE_URL + "/specimenListLookup.do?download=tsv", timeout=60)
    assert "tab-separated-values" in d.headers.get("Content-Type", ""), d.text[:300]
    rows = d.text.strip().split("\n")
    assert len(rows) == 2 and rows[1].lower().startswith(KNOWN_CODES[0])


# "Keep me logged in" (AW_REMEMBER cookie).
def _remember_set_cookie(response):
    return [v for v in response.raw.headers.getlist("Set-Cookie") if v.startswith("AW_REMEMBER=")]


def _browser_with_remember(value):
    s = new_session()
    s.cookies.set("AW_REMEMBER", value, domain=urlparse(BASE_URL).hostname, path="/")
    return s


def test_remember_me():
    s = new_session()
    r = s.post(BASE_URL + "/login.do",
               data={"userName": USER, "password": PASSWORD, "isSubmit": "true", "rememberMe": "on"},
               timeout=60, allow_redirects=False)
    headers = [h for h in _remember_set_cookie(r) if not h.startswith("AW_REMEMBER=;")]
    assert headers, f"login with rememberMe set no AW_REMEMBER cookie: {r.raw.headers.getlist('Set-Cookie')}"
    h = headers[-1].lower()
    for flag in ("secure", "httponly", "samesite=lax", "path=/", "max-age="):
        assert flag in h, f"AW_REMEMBER lacks {flag}: {headers[-1]}"
    value = headers[-1].split(";", 1)[0].split("=", 1)[1].strip('"')

    # A new browser holding only that cookie is logged in, and gets a new (rotated) cookie.
    b = _browser_with_remember(value)
    assert get(b, "/curate.do").category != "login", "AW_REMEMBER alone did not log in"
    rotated = b.cookies.get("AW_REMEMBER")
    assert rotated and rotated != value, "AW_REMEMBER was not rotated after use"

    # Logout forgets it: the cookie no longer logs anyone in.
    b.get(BASE_URL + "/logout.do", timeout=60)
    assert get(_browser_with_remember(rotated), "/curate.do").category == "login", "AW_REMEMBER still works after logout"


def test_login_without_remember_me_sets_no_cookie():
    s = new_session()
    r = s.post(BASE_URL + "/login.do", data={"userName": USER, "password": PASSWORD, "isSubmit": "true"},
               timeout=60, allow_redirects=False)
    assert not [h for h in _remember_set_cookie(r) if not h.startswith("AW_REMEMBER=;")]
    s.get(BASE_URL + "/logout.do", timeout=60)


def test_forged_remember_cookie_is_refused():
    b = _browser_with_remember("A" * 16 + ":" + "B" * 43)
    assert get(b, "/curate.do").category == "login"


# CSRF protection: a logged-in POST must carry the session's token. deleteImages is used
# because, with nothing chosen, it changes nothing even if a request got through.
def test_post_without_csrf_token_is_refused(curator):
    r = curator.post(BASE_URL + "/deleteImages.do", data={"group": "0"}, timeout=60, allow_redirects=False)
    assert r.status_code == 403, f"POST without _csrf answered HTTP {r.status_code}"
    r = curator.post(BASE_URL + "/deleteImages.do", data={"_csrf": "x" * 43}, timeout=60, allow_redirects=False)
    assert r.status_code == 403, f"POST with a wrong _csrf answered HTTP {r.status_code}"


def test_post_with_csrf_token_is_accepted(curator):
    r = curator.post(BASE_URL + "/deleteImages.do", data={"_csrf": csrf_token(curator)}, timeout=60, allow_redirects=False)
    assert r.status_code != 403, "POST with the session's _csrf was refused"


def test_post_from_another_site_is_refused(curator):
    r = curator.post(BASE_URL + "/deleteImages.do", data={"_csrf": csrf_token(curator)},
                     headers={"Origin": "https://example.com"}, timeout=60, allow_redirects=False)
    assert r.status_code == 403, f"cross-site POST answered HTTP {r.status_code}"


def test_curate_page_forms_carry_csrf_token(curator):
    html = curator.get(BASE_URL + "/curate.do", timeout=60).text
    forms = re.findall(r"<form\b([^>]*)>(.*?)</form>", html, re.S | re.I)
    posts = [(attrs, body) for attrs, body in forms if re.search(r"method\s*=\s*[\"']?post", attrs, re.I)]
    assert posts, "no POST forms found on /curate.do"
    for attrs, body in posts:
        assert 'name="_csrf"' in body, f"form without _csrf field: {attrs[:120]}"
        if "multipart" in attrs.lower():
            assert "_csrf=" in attrs, f"upload form without _csrf in its action: {attrs[:120]}"


# Editor trial page (read only): the sanitize endpoint answers with JSON and needs the token.
def test_editor_trial_sanitize(curator):
    html = curator.get(BASE_URL + "/editorTrial.do", timeout=60).text
    m = re.search(r'data-csrf="([^"]+)"', html)
    assert m, "no data-csrf on /editorTrial.do"
    data = {"op": "sanitize", "field": "overview", "html": '<p onclick="x()">a<script>b</script></p>'}
    r = curator.post(BASE_URL + "/editorTrial.do", data=data, headers={"X-CSRF-Token": m.group(1)},
                     timeout=60, allow_redirects=False)
    assert r.status_code == 200 and r.json().get("sanitized") == "<p>a</p>", f"{r.status_code} {r.text[:200]}"
    r = curator.post(BASE_URL + "/editorTrial.do", data=data, timeout=60, allow_redirects=False)
    assert r.status_code == 403, f"sanitize POST without a token answered HTTP {r.status_code}"

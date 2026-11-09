"""Free API keys, step 6 PRs 4 and 5 (Brian Fisher, 2026-11-08): the registration page apiKey.do, the confirmation
page apiKeyConfirm.do and the admin page apiKeys.do.

Read-only and safe on staging: no request here registers, sends an email or changes a key. The form is only posted
without its token and fields (refused before anything is saved or counted); the confirmation page is only opened
with tokens that cannot exist. The rules (key format, hashes, 24-hour links, one use, throttles, CSV) are unit
tested in test/org/calacademy/antweb/security/ApiKeysTest.java and, against MySQL, home/ApiKeyDbTest.java.

Until etc/api-keys.properties on the server says apiKeys.open=true, apiKey.do says "Registration opens soon"
(so the page can be deployed before the API checks keys); the tests accept either state.
"""
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, new_session
from public_pages import assert_ok, title
from staging_accounts import login, needs

OPENS_SOON = "Registration opens soon."
TERMS = "https://www.antweb.org/apiV3.1.do#terms"
COLLAB = "Working on a larger project? Brian L. Fisher welcomes collaborations; write to"


def _src(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8")


# ---------- static ----------

def test_source_form_fields_and_checks():
    body = _src("web/apiKey-body.jsp")
    for field in ('name="name"', 'name="email"', 'name="institution"', 'name="project"', 'name="agree"',
                  "Csrf.field(request)", "Turnstile.isEnabled()", "ApiKeys.TERMS_URL", OPENS_SOON, COLLAB):
        assert field in body, field
    action = _src("src/org/calacademy/antweb/ApiKeyAction.java")
    # Checked in this order: open, CSRF, fields, human check, per-IP limit, then saved.
    order = [action.index(s) for s in ("ApiKeys.isOpen()", "Csrf.check(request)", "r.problem()", "Turnstile.check(request)",
                                       "ApiKeys.allowIp(", "register(request, r)")]
    assert order == sorted(order), order


def test_source_key_never_logged_or_emailed():
    mgr = _src("src/org/calacademy/antweb/util/ApiKeyMgr.java")
    for line in mgr.splitlines():
        if "logEvent(" in line or "s_log." in line or "Emailer." in line:
            assert not re.search(r"\b(key|token)\b\s*[+)]", line), line
    assert "sendTransactional(" in mgr  # the link email: its body is never written to a log
    assert "ApiKeys.COPY_TO" in mgr and '"bfisher@calacademy.org"' in _src("src/org/calacademy/antweb/util/ApiKeys.java")


def test_source_api_page_links_registration():
    body = _src("web/documentation/api/apiV3_1-body.jsp")
    terms = body.index('id="terms"')
    link = body.index("/apiKey.do\"><b>Register for a free API key</b></a>")
    assert terms < link < body.index("<h3>Purpose and responsible data access</h3>")


def test_source_admin_page_linked_from_curate():
    assert "/apiKeys.do\">API keys</a>" in _src("web/curate/curatorLinks.jsp")


# ---------- the public pages ----------

def test_register_page():
    r = new_session().get(BASE_URL + "/apiKey.do", timeout=TIMEOUT)
    assert_ok(r)
    assert title(r) == "AntWeb - Free API key", title(r)
    assert "<h1>Free AntWeb API key</h1>" in r.text
    assert "no-store" in r.headers.get("Cache-Control", ""), r.headers.get("Cache-Control")
    assert COLLAB in r.text
    if OPENS_SOON in r.text:
        assert 'name="email"' not in r.text, "the form is shown while registration is not open"
    else:
        for field in ('name="name"', 'name="email"', 'name="institution"', 'name="project"', 'name="agree"', 'name="_csrf"'):
            assert field in r.text, field
        assert TERMS in r.text and "/apiKey.do" in r.text


def test_register_post_without_token_saves_nothing():
    """A post with no CSRF token and no fields is refused with the form again (or "opens soon"); nothing is sent."""
    r = new_session().post(BASE_URL + "/apiKey.do", data={"name": "", "email": ""}, timeout=TIMEOUT)
    assert_ok(r)
    assert "Please check your email" not in r.text
    assert OPENS_SOON in r.text or "Please send it again." in r.text, r.text[:500]


@pytest.mark.parametrize("query", ["", "?token=short", "?token=" + "A" * 43])
def test_confirm_page_with_an_unusable_token(query):
    """No token, a malformed one, or one no registration has: "This link is not valid.", no key, no button.
    The page is never cached and sends no Referer (its address holds the token)."""
    r = new_session().get(BASE_URL + "/apiKeyConfirm.do" + query, timeout=TIMEOUT)
    assert_ok(r)
    assert title(r) == "AntWeb - Your API key", title(r)
    assert "This link is not valid." in r.text
    assert 'name="token"' not in r.text and "aw_" not in r.text
    assert "/apiKey.do" in r.text
    assert "no-store" in r.headers.get("Cache-Control", ""), r.headers.get("Cache-Control")
    assert r.headers.get("Referrer-Policy") == "no-referrer", r.headers.get("Referrer-Policy")


def test_api_page_links_registration():
    r = new_session().get(BASE_URL + "/apiV3.1.do", timeout=TIMEOUT)
    assert_ok(r)
    assert "Register for a free API key" in r.text and "/apiKey.do" in r.text


# ---------- the admin page ----------

def test_admin_page_needs_login():
    r = new_session().get(BASE_URL + "/apiKeys.do", timeout=TIMEOUT)
    assert "API keys</h1>" not in r.text and "api-keys-table" not in r.text
    r = new_session().get(BASE_URL + "/apiKeys.do?format=csv", timeout=TIMEOUT)
    assert "text/csv" not in r.headers.get("Content-Type", ""), r.headers.get("Content-Type")


@pytest.fixture(scope="module")
def admin_login():
    return login("admin")  # (session, csrf token, login id)


@pytest.fixture(scope="module")
def admin(admin_login):
    return admin_login[0]


@needs("admin")
def test_admin_page_lists_keys(admin):
    r = admin.get(BASE_URL + "/apiKeys.do", timeout=TIMEOUT)
    assert_ok(r)
    assert '<div class="aw-tools" id="api-keys-page">' in r.text
    assert 'id="api-keys-help"' in r.text and "5 requests a minute, 50 a day and 25 records an answer" in r.text
    assert 'id="api-keys-table"' in r.text and 'id="api-keys-no-key"' in r.text
    assert "/apiKeys.do?format=csv" in r.text
    r = admin.get(BASE_URL + "/apiKeys.do?status=revoked&level=admin&q=%25", timeout=TIMEOUT)
    assert_ok(r)
    assert 'id="api-keys-table"' in r.text


@needs("admin")
def test_admin_csv(admin):
    r = admin.get(BASE_URL + "/apiKeys.do?format=csv", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert r.headers.get("Content-Type", "").startswith("text/csv"), r.headers.get("Content-Type")
    assert "attachment" in r.headers.get("Content-Disposition", "")
    lines = r.content.decode("utf-8-sig").split("\r\n")
    assert lines[0].startswith("number,name,email,institution,project,level,status,"), lines[0]
    assert lines[1].startswith("0,All requests without a key,"), lines[1]


@needs("admin")
def test_admin_change_of_an_unknown_key_changes_nothing(admin_login):
    """A revoke for a key that does not exist changes nothing (no staging key is touched)."""
    session, token, _id = admin_login
    r = session.post(BASE_URL + "/apiKeys.do", data={"_csrf": token, "id": "999999999", "op": "revoke", "confirm": "yes"},
                     timeout=TIMEOUT)
    assert_ok(r)
    assert "Nothing changed: no such key." in r.text, r.text[:500]
    assert "No API key registration number 999999999." in r.text

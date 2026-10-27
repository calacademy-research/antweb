"""Forgot password and the reset link page (road map step 2; docs/flows/email-and-password-reset.md).

Anonymous and read-only: no request here sends an email or changes a login. The forgot-password form is
only posted empty (refused before any lookup, before the per-address limit is counted); the reset page is
only opened with tokens that cannot exist. The rules themselves (token hashing, 24 hours, one use, the
limits, sessions ended) are unit-tested in test/org/calacademy/antweb/security/PasswordResetTest.java and
the email texts in tests/unit/test_email_texts.py.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from public_pages import assert_ok, title

INVALID = "This link is not valid."


def test_forgot_password_form():
    r = new_session().get(BASE_URL + "/forgotPassword.do", timeout=TIMEOUT)
    assert_ok(r)
    assert title(r) == "AntWeb - Password Retrieval", title(r)
    assert "<h1>Forgot Your Password?</h1>" in r.text
    assert 'name="identifier"' in r.text and 'action="' in r.text and "/forgotPassword.do" in r.text
    assert "no-store" in r.headers.get("Cache-Control", ""), r.headers.get("Cache-Control")


def test_forgot_password_empty_post_is_refused():
    """An empty username/email is refused with the form again; nothing is looked up or sent."""
    r = new_session().post(BASE_URL + "/forgotPassword.do", data={"identifier": "  "}, timeout=TIMEOUT)
    assert_ok(r)
    assert "Please enter your username or email address." in r.text
    assert "we've sent a link" not in r.text
    assert 'name="identifier"' in r.text


@pytest.mark.parametrize("query", ["", "?token=short", "?token=" + "A" * 43])
def test_reset_page_with_an_unusable_token(query):
    """No token, a malformed one, or a well-formed one that is not in password_reset: "This link is not
    valid." with a link to ask for a new one; no form. The page is never cached and sends no Referer."""
    r = new_session().get(BASE_URL + "/resetPassword.do" + query, timeout=TIMEOUT)
    assert_ok(r)
    assert title(r) == "AntWeb - Set a New Password", title(r)
    assert INVALID in r.text
    assert 'name="password"' not in r.text and 'name="token"' not in r.text
    assert "/forgotPassword.do" in r.text
    assert "no-store" in r.headers.get("Cache-Control", ""), r.headers.get("Cache-Control")
    assert r.headers.get("Referrer-Policy") == "no-referrer", r.headers.get("Referrer-Policy")


def test_login_page_links_forgot_password():
    r = new_session().get(BASE_URL + "/login.do", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert "/forgotPassword.do" in r.text, "the log-in page has no 'Forgot your password?' link"

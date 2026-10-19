"""Tomcat request limits that real browsers can reach (2026-10-19).

A curator got AntWeb's "Request Error" page on login.do: the browser (or an email-security proxy)
sent an Accept-Language header with more than 10 languages, and Tomcat 11 refuses more than
10 by default (maxLocaleCount). docker/antweb/tomcat/conf/server.xml now allows 100.

Anonymous, read-only GETs (any host, safe on production): login.do, the home page and public
pages answer normally with 30 and 60 languages. Staging only, as the test curator: logging in
with such a header works (one login).

Reset links opened by email link scanners: test/.../security/EmailLinkTest.java (CI).
"""
import pytest

from antweb import BASE_URL, BROKEN, IS_PRODUCTION, TIMEOUT, classify, new_session, post_login
import test_page_editor as pe

# 60 real language tags; real headers look like "en-US,en;q=0.9,fr;q=0.8,...".
LANGS = ["en-US", "en", "fr-BE", "fr", "nl-BE", "nl", "de-DE", "de", "es-ES", "es", "it-IT", "it",
         "pt-BR", "pt", "pl", "cs", "sk", "hu", "ro", "bg", "el", "tr", "ru", "uk", "sv", "da", "nb",
         "fi", "et", "lv", "lt", "sl", "hr", "sr", "ja", "ko", "zh-CN", "zh-TW", "zh", "vi", "th",
         "id", "ms", "hi", "bn", "ta", "ar", "he", "fa", "ur", "sw", "am", "zu", "af", "ca", "eu",
         "gl", "is", "ga", "cy"]


def accept_language(n):
    tags = LANGS[:n]
    parts = [tags[0]] + [f"{t};q={max(0.1, round(0.9 - i * 0.01, 2))}" for i, t in enumerate(tags[1:])]
    return ",".join(parts)


def _assert_normal(resp, what):
    out = classify(resp)
    assert out.category not in BROKEN, (what, out)
    assert resp.status_code == 200, (what, resp.status_code)
    assert "Request Error" not in resp.text, (what, "AntWeb's Request Error page")
    assert "maximum allowed number of locales" not in resp.text, what


@pytest.mark.parametrize("n", [30, 60])
@pytest.mark.parametrize("path", ["/login.do", "/", "/browse.do?subfamily=formicinae&rank=subfamily",
                                  "/description.do?genus=camponotus&rank=genus"])
def test_many_accept_languages(path, n):
    # A new session each time: Struts reads the request's locales on a session's first request.
    s = new_session()
    s.headers["Accept-Language"] = accept_language(n)
    assert s.headers["Accept-Language"].count(",") == n - 1
    r = s.get(BASE_URL + path, timeout=TIMEOUT)
    _assert_normal(r, f"{path} with {n} languages")


staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="the login check runs only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


@staging_only
def test_login_with_many_accept_languages():
    s = new_session()
    s.headers["Accept-Language"] = accept_language(30)
    r = s.get(BASE_URL + "/login.do", timeout=TIMEOUT)
    _assert_normal(r, "login.do (GET) with 30 languages")
    r = post_login(s, pe.USER, pe.PASSWORD)
    assert r.status_code < 500, r.status_code
    assert "Request Error" not in r.text
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    assert info.get("loggedIn"), "log-in with a 30-language Accept-Language header failed"

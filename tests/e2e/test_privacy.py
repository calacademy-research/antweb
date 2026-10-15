"""Files that must not be readable by the public."""
import pytest

from antweb import BASE_URL, new_session


@pytest.mark.parametrize("path", ["/web/log/logins.txt"])
def test_private_file_not_public(path):
    r = new_session().get(BASE_URL + path, timeout=60)
    assert r.status_code in (403, 404), f"{path} is public (HTTP {r.status_code})"


# Removed servlets that must stay gone.
@pytest.mark.parametrize("path", ["/fileUploader", "/fileUploader/x"])
def test_removed_servlet_gone(path):
    r = new_session().post(BASE_URL + path, timeout=60)
    assert r.status_code == 404, f"{path} answered HTTP {r.status_code}"


def test_session_cookie_flags():
    """The session cookie is Secure, HttpOnly and SameSite=Lax."""
    r = new_session().get(BASE_URL + "/login.do", timeout=60)
    cookies = [v for k, v in r.raw.headers.items() if k.lower() == "set-cookie" and "JSESSIONID" in v]
    if not cookies:
        pytest.skip("no session cookie set on /login.do")
    c = cookies[0].lower()
    for flag in ("secure", "httponly", "samesite=lax"):
        assert flag in c, f"session cookie lacks {flag}: {cookies[0]}"


def test_login_page_has_no_error_before_submit():
    r = new_session().get(BASE_URL + "/login.do", timeout=60)
    assert "Please provide a username" not in r.text

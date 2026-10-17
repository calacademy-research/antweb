"""Files that must not be readable by the public."""
import pytest

from antweb import BASE_URL, new_session


@pytest.mark.parametrize("path", ["/web/log/logins.txt", "/web/log/serverBusy.html", "/data/index.jsp",
                                  "/web/log/", "/web/curator/", "/web/bak/taxonSets/"])
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


def test_data_jobs_need_admin():
    """utilData.do runs data jobs; "param=allow" must not let anonymous visitors in."""
    from antweb import get
    outcome = get(new_session(), "/utilData.do?action=taxonWorksNightly&param=allow")
    assert outcome.category == "login", outcome


def test_specimen_short_link_does_not_echo_input():
    """/specimen/<code> redirects to the specimen page; anything that is not a code is a 404
    and is never echoed back (it used to print the URL unescaped on an error)."""
    r = new_session().get(BASE_URL + "/specimen/%3Cscript%3Eaw5%3C%2Fscript%3E", timeout=60, allow_redirects=False)
    assert "<script>aw5" not in r.text
    assert r.status_code == 404, f"HTTP {r.status_code}"
    r = new_session().get(BASE_URL + "/specimen/casent0106322", timeout=60, allow_redirects=False)
    assert r.status_code in (301, 302) and "/specimen.do?name=casent0106322" in r.headers.get("Location", ""), r.status_code


def test_query_not_found_message_is_escaped():
    """query.do echoes an unknown query name; it must be escaped."""
    r = new_session().get(BASE_URL + "/query.do?name=%3Cb%3Eaw5x%3C%2Fb%3E&param=%3Ci%3Ey%3C%2Fi%3E", timeout=60)
    assert "<b>aw5x</b>" not in r.text and "<i>y</i>" not in r.text

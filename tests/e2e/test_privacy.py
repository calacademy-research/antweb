"""Files that must not be readable by the public."""
import pytest

from antweb import BASE_URL, IS_PRODUCTION, new_session


@pytest.mark.parametrize("path", ["/web/log/logins.txt", "/web/log/serverBusy.html", "/data/index.jsp",
                                  "/web/log/", "/web/curator/", "/web/bak/taxonSets/"])
def test_private_file_not_public(path):
    r = new_session().get(BASE_URL + path, timeout=60)
    assert r.status_code in (403, 404), f"{path} is public (HTTP {r.status_code})"


# Folders are never listed, at any depth (Tomcat listings are off; Caddy also refuses them).
@pytest.mark.parametrize("path", ["/web/log/upload/", "/web/log/upload/specimen/", "/web/curator/1/",
                                  "/web/curator/23/", "/web/bak/", "/images/", "/images/casent0106322/"])
def test_no_directory_listing(path):
    r = new_session().get(BASE_URL + path, timeout=60)
    assert "Directory Listing For" not in r.text, f"{path} lists its files"
    assert r.status_code in (403, 404), f"{path} answered HTTP {r.status_code}"


# Developer and test pages that were open to anyone; removed.
@pytest.mark.parametrize("path", ["/memory.jsp", "/util/test.jsp", "/util/jspFetch.jsp", "/util/dummyPage.jsp",
                                  "/util/toUpload.jsp", "/common/testMessage.jsp", "/common/mobile.jsp",
                                  "/util/userAgents.jsp", "/techDoc.jsp", "/test.do", "/mobile.do",
                                  "/testMessage.do", "/testMobile.do", "/userAgents.do"])
def test_removed_test_page_gone(path):
    if IS_PRODUCTION and path in ("/test.do", "/mobile.do"):
        pytest.skip("used to update counts; checked on staging")
    r = new_session().get(BASE_URL + path, timeout=60)
    assert r.status_code in (400, 404), f"{path} answered HTTP {r.status_code}"
    assert "freeMemory" not in r.text and "Test.jsp" not in r.text


# Removed servlets that must stay gone.
@pytest.mark.parametrize("path", ["/fileUploader", "/fileUploader/x", "/queryFile", "/apiV3/x", "/antweb_test/x"])
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
    # 400 (refused before it reaches the app) or 404; never a page that shows it.
    assert r.status_code in (400, 404), f"HTTP {r.status_code}"
    r = new_session().get(BASE_URL + "/specimen/casent0106322", timeout=60, allow_redirects=False)
    assert r.status_code in (301, 302) and "/specimen.do?name=casent0106322" in r.headers.get("Location", ""), r.status_code


def test_query_not_found_message_is_escaped():
    """query.do echoes an unknown query name; it must be escaped."""
    r = new_session().get(BASE_URL + "/query.do?name=%3Cb%3Eaw5x%3C%2Fb%3E&param=%3Ci%3Ey%3C%2Fi%3E", timeout=60)
    assert "<b>aw5x</b>" not in r.text and "<i>y</i>" not in r.text

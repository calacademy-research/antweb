"""editGeolocale.do: changes only by POST with the form token. Staging only; nothing is changed.

- anonymous GET and POST with save parameters get the log-in page;
- as the test curator, the form is a POST form carrying the session's _csrf token;
- a GET with save parameters is refused ("Nothing was changed") and the stored values are the same
  afterwards;
- a POST without the token is refused (403) and the stored values are the same afterwards.

Safety: the curator checks first confirm the server has this code (the edit form is a POST form with
a token). On a server without it, a GET with save parameters would save, so they are skipped there.
The requests carry only id and isSubmit, so even then nothing but the same values would be written.
Target: ANTWEB_EDIT_TEST_COUNTRY (default Comoros).
"""
import html
import re
from urllib.parse import quote

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, get, new_session
import test_page_editor as pe
from test_overview_editor import TEST_COUNTRY

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS,
                       reason=f"editGeolocale checks run only on staging, not on {pe.HOST}"),
]

FORM_URL = "/editGeolocale.do?name=" + quote(TEST_COUNTRY) + "&georank=country"
FIELD = re.compile(r'<(input|textarea|select)\b([^>]*)>', re.I)
ATTR = re.compile(r'(\w+)\s*=\s*"([^"]*)"')


def _values(page_html):
    """The stored values shown in the edit form (inputs, the admin notes box, selected options)."""
    out = {}
    for m in FIELD.finditer(page_html):
        a = dict(ATTR.findall(m.group(2)))
        name = a.get("name")
        if not name or name == "_csrf" or "disabled" in m.group(2):
            continue
        if m.group(1).lower() == "textarea":
            end = page_html.find("</textarea>", m.end())
            out[name] = html.unescape(page_html[m.end():end])
        elif m.group(1).lower() == "input":
            if a.get("type") == "checkbox":
                out[name] = "checked" in m.group(2)
            else:
                out[name] = html.unescape(a.get("value", ""))
    out["_selected"] = sorted(re.findall(r'<option[^>]*value="([^"]*)"[^>]*selected', page_html))
    return out


def test_anonymous_get_with_save_parameters_asks_for_login():
    assert get(new_session(), "/editGeolocale.do?id=1&isSubmit=true").category == "login"


def test_anonymous_post_asks_for_login():
    r = new_session().post(BASE_URL + "/editGeolocale.do", data={"id": "1", "isSubmit": "true"},
                           timeout=TIMEOUT, allow_redirects=True)
    assert classify(r).category == "login", classify(r)


@pytest.fixture(scope="module")
def curator_form():
    if not pe.USER or not pe.PASSWORD:
        pytest.skip("needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")
    s = pe.login()
    r = s.get(BASE_URL + FORM_URL, timeout=TIMEOUT)
    if classify(r).category != "ok" or 'name="isSubmit"' not in r.text:
        pytest.skip(f"editGeolocale for {TEST_COUNTRY} not available to the test login: {classify(r)}")
    m = re.search(r'<form\b[^>]*action="[^"]*editGeolocale[^"]*"[^>]*>', r.text, re.I)
    if not m or 'method="post"' not in m.group(0).lower():
        pytest.skip("this server still has the GET edit form (fix not deployed); change probes skipped")
    gid = re.search(r'<input type="hidden"[^>]*name="id" value="(\d+)"', r.text)
    assert gid, "no geolocale id in the form"
    return s, r.text, gid.group(1)


def test_edit_form_posts_with_token(curator_form):
    s, page, _ = curator_form
    form = page[re.search(r'<form\b[^>]*action="[^"]*editGeolocale', page, re.I).start():]
    form = form[:form.find("</form>")]
    token = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json().get("csrf")
    assert f'name="_csrf" value="{token}"' in form, "the edit form does not carry the form token"


def test_get_with_save_parameters_changes_nothing(curator_form):
    s, page, gid = curator_form
    before = _values(page)
    r = s.get(BASE_URL + f"/editGeolocale.do?id={gid}&isSubmit=true", timeout=TIMEOUT)
    assert "Nothing was changed" in r.text, classify(r)
    after = _values(s.get(BASE_URL + FORM_URL, timeout=TIMEOUT).text)
    assert after == before


def test_post_without_token_is_refused(curator_form):
    s, page, gid = curator_form
    before = _values(page)
    r = s.post(BASE_URL + "/editGeolocale.do", data={"id": gid, "isSubmit": "true"},
               timeout=TIMEOUT, allow_redirects=True)
    assert r.status_code == 403, r.status_code
    after = _values(s.get(BASE_URL + FORM_URL, timeout=TIMEOUT).text)
    assert after == before

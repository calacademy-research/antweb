"""Museum Manager save and delete, round trip on staging (road map step 5; catalog gaps: manageMuseum.do, saveMuseum.do
and viewMuseum.do, action save and delete).

As the test administrator (ANTWEB_TEST_ADMIN_*; staging only): save a test-only museum (code ZZTST, no specimen
carries it), find it in the Museum Manager list and on its form, change its name, then delete it: the first delete
asks for a confirmation and deletes nothing; the confirmed delete removes it. A finally block deletes it whatever
fails. ManageMuseumsAction decides the action from the request (a POST saves; step=delete deletes), so the action=
parameters sent here are only the catalog's names for the same requests.
"""
import re

from antweb import BASE_URL, TIMEOUT
import numbers_sample as ns
from staging_accounts import login, needs

CODE = "ZZTST"   # short: museum codes are institution codes (CASC, MCZC)
NAME = "ZZ Test Museum (staging only)"


def _post(session, token, path, **fields):
    r = session.post(BASE_URL + path, data=dict(fields, _csrf=token), timeout=TIMEOUT)
    assert r.status_code == 200, (path, r.status_code)
    return r.text


def _listed(session):
    return CODE in session.get(f"{BASE_URL}/manageMuseum.do", timeout=TIMEOUT).text


def _delete(session, token):
    return _post(session, token, "/viewMuseum.do?action=delete", code=CODE, step="delete", confirmDelete="yes")


@needs("admin")
def test_admin_saves_renames_and_deletes_a_museum():
    s, token, _lid = login("admin")
    if _listed(s):
        _delete(s, token)   # left by an earlier run that stopped half way
    try:
        _post(s, token, "/saveMuseum.do?action=save", code=CODE, name=NAME, title="", isActive="true")
        assert _listed(s), f"{CODE} saved but not in the Museum Manager list"
        form = s.get(f"{BASE_URL}/viewMuseum.do?code={CODE}", timeout=TIMEOUT).text
        assert NAME in form, f"/viewMuseum.do?code={CODE} does not show the saved name"

        _post(s, token, "/manageMuseum.do?action=save", code=CODE, name=NAME + " renamed", title="", isActive="true")
        form = s.get(f"{BASE_URL}/viewMuseum.do?code={CODE}", timeout=TIMEOUT).text
        assert NAME + " renamed" in form, "the second save did not change the name"

        ask = _post(s, token, "/manageMuseum.do?action=delete", code=CODE, step="delete")
        text = ns.text_of(ask)
        assert f"Delete museum {CODE}?" in text and "cannot be undone" in text, text[:300]
        assert "specimen records carry this museum code" not in text, "a test museum code is used by specimens"
        assert _listed(s), "the delete was not confirmed but the museum is gone"

        _delete(s, token)
        assert not _listed(s), f"{CODE} still listed after the confirmed delete"
    finally:
        if _listed(s):
            _delete(s, token)
    assert not re.search(CODE, s.get(f"{BASE_URL}/manageMuseum.do", timeout=TIMEOUT).text)

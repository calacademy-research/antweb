"""Administrator switches on staging (road map step 5; catalog gaps: serverStatus.do action toggleDownTime,
adminAlert.do action refresh and removeAll, passwordResetCampaign.do action preview, mark and send).

As the test administrator (ANTWEB_TEST_ADMIN_*; staging only):
- Upload down time: the Server Status page's Toggle Down Time button takes uploads off line (the page says so and
  ServerDb.DOWN is shown) and a second press brings them back; a finally block makes sure it ends on line.
  A GET of the toggle only asks for confirmation (PostOnly) and changes nothing.
- Admin alerts: refresh and remove all ask for confirmation on a GET (nothing removed); a POST of refresh reloads.
- Password reset campaign: the preview lists the accounts it would mark; mark and send without the confirm box do
  nothing ("Nothing done"). The confirmed steps are never run by a test (they end every account's session).
"""
import re

import numbers_sample as ns
from antweb import BASE_URL, TIMEOUT
from staging_accounts import login, needs

CONFIRM = "This changes data, so it runs only from a button on AntWeb."


def _get(session, path):
    r = session.get(BASE_URL + path, timeout=TIMEOUT)
    assert r.status_code == 200, (path, r.status_code)
    return r.text


def _post(session, token, path, **fields):
    r = session.post(BASE_URL + path, data=dict(fields, _csrf=token), timeout=TIMEOUT)
    assert r.status_code == 200, (path, r.status_code)
    return r.text


def _down_message(html):
    """The text under the Toggle Down Time button ('' when uploads are on line)."""
    m = re.search(r"Toggle Down Time</button></form>\s*<br>(.*?)</td>", html, re.S)
    assert m, "serverStatus.do has no Toggle Down Time row"
    return ns.text_of(m.group(1))


@needs("admin")
def test_upload_down_time_toggles_off_and_on():
    s, token, _lid = login("admin")
    toggle = "/serverStatus.do?action=toggleDownTime"
    assert CONFIRM in ns.text_of(_get(s, toggle)), "a GET of toggleDownTime did not ask for confirmation"
    before = _down_message(_get(s, "/serverStatus.do"))
    assert before == "", f"staging uploads were already off line: {before}"
    try:
        _post(s, token, toggle)
        down = _down_message(_get(s, "/serverStatus.do"))
        assert down, "Toggle Down Time did not take uploads off line"
    finally:
        if _down_message(_get(s, "/serverStatus.do")):
            _post(s, token, toggle)
    assert _down_message(_get(s, "/serverStatus.do")) == "", "uploads still off line after the second toggle"


@needs("admin")
def test_admin_alerts_confirm_before_removing_and_refresh_on_post():
    s, token, _lid = login("admin")
    for path in ("/adminAlert.do?action=removeAll", "/adminAlert.do?action=refresh"):
        assert CONFIRM in ns.text_of(_get(s, path)), f"a GET of {path} did not ask for confirmation"
    text = ns.text_of(_post(s, token, "/adminAlert.do?action=refresh"))
    assert CONFIRM not in text, "a POST of refresh asked for confirmation again: " + text[:300]


@needs("admin")
def test_password_reset_campaign_preview_and_unconfirmed_steps_do_nothing():
    s, token, _lid = login("admin")
    preview = ns.text_of(_get(s, "/passwordResetCampaign.do?action=preview"))
    assert "Database error" not in preview, preview[:300]
    for step in ("/passwordResetCampaign.do?action=mark", "/passwordResetCampaign.do?action=send"):
        text = ns.text_of(_post(s, token, step))   # no confirm=yes
        assert "Nothing done" in text, f"{step} without the confirm box: {text[:300]}"


def _server_debug(session):
    """The server debug option set now, as the Server Debug page shows it ('' when none)."""
    m = re.search(r"<b>Set Server Debug:</b>\s*([^<\n]*)", _get(session, "/serverDebug.do"))
    assert m, "serverDebug.do does not show the option set now"
    return m.group(1).strip()


@needs("admin")
def test_server_debug_set_and_restored():
    """Server Debug (catalog gap serverDebug.do action setDebug, 2026-11-04): a GET asks for confirmation and changes
    nothing; a POST sets the option (logGetConns, which only adds log lines) and the page shows it; the option set
    before is put back whatever fails."""
    s, token, _lid = login("admin")
    before = _server_debug(s)
    assert CONFIRM in ns.text_of(_get(s, "/serverDebug.do?action=setDebug&param=logGetConns")), \
        "a GET of setDebug did not ask for confirmation"
    assert _server_debug(s) == before, "a GET of setDebug changed the option"
    try:
        _post(s, token, "/serverDebug.do?action=setDebug", param="logGetConns")
        assert _server_debug(s) == "logGetConns", "setDebug did not set the option"
    finally:
        _post(s, token, "/serverDebug.do?action=setDebug", param=before)
    assert _server_debug(s) == before, f"the option was not put back to {before!r}"

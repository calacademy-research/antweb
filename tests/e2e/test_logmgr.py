"""Log viewer (logMgr.do): who may open which view, and that log text is shown as text.

Why (2026-10-17 audit): curators may open three views (uploadLog, specimenDetails, worldants);
everything else, including the old uploadLine view and the log files, is admin only. Log lines
contain text visitors control (URLs, user agents), so the viewer must never print them as HTML.

Read-only, anonymous.
"""
import pytest

from antweb import BASE_URL, TIMEOUT, new_session

URL = BASE_URL + "/logMgr.do"


def _refused(text):
    return any(s in text for s in ("Please Log In", "AntWeb - Please Log In", "not authorized", "Admin only",
                                   "must be an administrator", "Access denied", "logged in"))


@pytest.mark.parametrize("query", [
    "action=uploadLine&file=x&line=1",
    "action=uploadLog&file=x.txt&line=1",
    "action=specimenDetails&code=casent0922067",
    "action=worldants&line=2",
    "action=get&fileName=compute&ext=log",
    "action=list",
])
def test_anonymous_refused(query):
    r = new_session().get(f"{URL}?{query}", timeout=TIMEOUT)
    assert _refused(r.text) or r.status_code in (401, 403), (query, r.status_code, r.text[:200])
    assert "<table border=1>" not in r.text and "Line:<b>" not in r.text, query

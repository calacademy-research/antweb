"""Log viewer (logMgr.do): who may open which view, and that log text is shown as text.

Why (2026-10-17 audit): curators may open three views (uploadLog, specimenDetails, worldants);
everything else, including the old uploadLine view and the log files, is admin only. Log lines
contain text visitors control (URLs, user agents), so the viewer must never print them as HTML.

Read-only: anonymous, plus a few views as the test curator (staging only).
"""
import os

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session, post_login

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


def test_no_action_is_not_a_server_error():
    """logMgr.do with no action used to throw (HTTP 500) for admins; it now shows the log list.
    Anonymous visitors are still refused."""
    r = new_session().get(URL, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert _refused(r.text) or r.status_code in (401, 403), r.text[:200]


# As the test curator (an ordinary curator, not an admin; staging only).
USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")
needs_curator = pytest.mark.skipif(not USER or not PASSWORD or IS_PRODUCTION,
                                   reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, staging only")


@pytest.fixture(scope="module")
def curator():
    s = new_session()
    post_login(s, USER, PASSWORD)
    return s


@needs_curator
def test_no_action_as_curator(curator):
    """The log list is admin only: a curator is refused, and never gets a 500."""
    r = curator.get(URL, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert "Primary Logs" not in r.text


@needs_curator
def test_upload_line_of_other_files_refused(curator):
    """A curator sees lines of their own group's upload files only; Worldants and species list
    backups are admin material."""
    r = curator.get(URL + "?action=uploadLog&file=20240101-05:00:00-worldants_speciesList.txt&line=2",
                    timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert "Not available." in r.text, r.text[:300]
    assert "Line:<b>" not in r.text


@needs_curator
def test_upload_file_of_other_files_refused(curator):
    r = curator.get(BASE_URL + "/uploadFile.do?file=20240101-05:00:00-worldants_speciesList.txt", timeout=TIMEOUT)
    assert r.status_code == 404, r.status_code

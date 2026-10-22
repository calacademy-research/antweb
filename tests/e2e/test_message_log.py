"""Message pages write a short record to messages.txt; the visitor still sees the whole message (2026-10-22).

Why: every message page wrote its whole message to the data log messages.txt. Admin reports shown
as a message (named queries such as imageData) wrote lines of ~13 MB: 11 GB in 11 days on staging.
Now the log line is: date, the first 300 characters of the message as plain text with
"[cut, N characters]", and the page address cut at 500 characters (no _csrf or password values).

1. Anonymous: an unknown query.do action with a long name shows "action not found:<the whole name>"
   (only the log is shortened).
2. Admin (staging test login, read-only): the log line of that page, read through logMgr.do (tail of
   messages.txt), is short and carries the cut marker, never the whole name.
3. The public image data query shows at most 5,000 rows.
"""
import re
import uuid
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import IS_STAGING, login, needs

LONG = 700  # longer than the 300 characters of the message record and the 500 of the address


def _long_action():
    marker = "msglog" + uuid.uuid4().hex[:12]
    return marker, marker + "x" * LONG


def _show_message(session, action):
    return session.get(f"{BASE_URL}/query.do?action={action}", timeout=TIMEOUT)


def test_page_shows_the_whole_message():
    _marker, action = _long_action()
    r = _show_message(new_session(), action)
    assert r.status_code == 404, r.status_code
    assert "action not found:" + action in r.text, r.text[:500]


def test_image_data_query_shows_at_most_5000_rows():
    r = new_session().get(BASE_URL + "/query.do?name=imageData", timeout=300)
    assert r.status_code == 200, r.status_code
    rows = r.text.count("<tr><td>")
    assert rows <= 5000, rows
    m = re.search(r"rowCount:</b>(\d+)", r.text)
    assert m, r.text[-500:]
    assert int(m.group(1)) <= 5000, m.group(1)  # "limit 5000" in the query (#233)


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_log_line_is_short():
    s, _token, _id = login("admin")
    marker, action = _long_action()
    r = _show_message(s, action)
    assert "action not found:" + action in r.text
    # The end of messages.txt, through the log viewer (admin only, read-only).
    log = s.get(BASE_URL + "/logMgr.do?action=get&loc=data&fileName=messages&ext=txt&lines=300", timeout=120)
    assert r.status_code < 500 and log.status_code == 200, log.status_code
    text = unescape(log.text)
    lines = [l for l in text.splitlines() if marker in l]
    assert lines, f"no messages.txt line with {marker} in the last 300 lines"
    line = lines[-1]
    assert "action not found:" + marker in line, line[:400]
    assert "[cut, " in line, line[:400]
    assert action not in line, "the whole message (or address) is in the log"
    assert len(line) < 1200, len(line)

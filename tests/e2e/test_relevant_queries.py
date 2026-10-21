"""The "Relevant Queries" boxes, as the staging admin test login (read-only; Brian Fisher, 2026-10-21).

Upload groups (viewGroups.do): specimenGroups (the "groups" query was removed on 2026-10-21). Login Manager (viewLogins.do): uploadingCurators
and curatorEmails. Each link has a one-line plain explanation next to it (what it lists, its columns,
when to use it; Queries.QUERY_LIST_HELP), and each query opens with as many column headings as columns
(uploadingCurators had no "Group ID" heading). Java checks in CI: test/org/calacademy/antweb/util/RelevantQueriesTest.java.
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify
from staging_accounts import IS_STAGING, login, needs

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only"), needs("admin")]

PAGES = [
    ("/viewGroups.do", {"specimenGroups": "How many specimen records each upload group has"}),
    ("/viewLogins.do", {"uploadingCurators": "Every login allowed to upload specimens or images",
                        "curatorEmails": "These are not the uploaders"}),
]


@pytest.fixture(scope="module")
def admin():
    s, _token, _id = login("admin")
    return s


@pytest.mark.parametrize("path,queries", PAGES)
def test_each_query_is_explained(admin, path, queries):
    r = admin.get(BASE_URL + path, timeout=TIMEOUT)
    assert classify(r).category == "ok", (path, classify(r))
    for name, words in queries.items():
        m = re.search(r"name=" + name + r"'>" + name + r"</a> <span class=\"query-help\">([^<]+)</span>", r.text)
        assert m, f"{path}: no explanation next to {name}"
        assert words in m.group(1), (path, name, m.group(1))
    assert "name=groups'" not in r.text, f"{path}: the removed groups query is still linked"


@pytest.mark.parametrize("name", ["specimenGroups", "uploadingCurators", "curatorEmails"])
def test_query_headings_match_columns(admin, name):
    r = admin.get(f"{BASE_URL}/query.do?action=curiousQuery&name={name}", timeout=TIMEOUT)
    assert classify(r).category == "ok", (name, classify(r))
    m = re.search(r"<table><tr>(<th>.*?)</tr>(.*?)</table>", r.text, re.S)
    assert m, f"{name}: no result table"
    headings = m.group(1).count("<th>")
    rows = re.findall(r"<tr><td>(.*?)</tr>", m.group(2), re.S)
    if not rows:
        pytest.skip(f"{name}: no rows on this server")
    cells = ("<td>" + rows[0]).count("<td>")
    assert cells == headings, f"{name}: {headings} headings, {cells} columns"

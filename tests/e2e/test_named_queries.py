"""Every named query of the Query Manager (query.do) runs, answers in time and has one heading per column.
As the staging admin test login; read-only (named queries only read data). Brian Fisher, 2026-10-22: "part
of your checking of all queries is to make sure they are working and explained".

The queries are taken from src/org/calacademy/antweb/util/Queries.java (each `new NamedQuery("name"`), plus
the four species-list queries with a place (QueriesWithParams.java) run for Madagascar and the Malagasy
bioregion. For each query, query.do?name=... must
- answer within QUERY_LIMIT seconds (the server stops a named query after 90 s; Cloudflare gives up at 100 s),
- show its explanation ("What it shows:"),
- show no SQL error (the page says "could not run" with the error, class query-error),
- have as many headings as columns (the page writes both counts in <span class="query-check">).
The Query Manager page lists every query once, each with its one-line summary.
Static checks of the same (explanation, heading count, read-only, lists) run in CI without a server:
test/org/calacademy/antweb/util/QueriesReviewTest.java.
"""
import re
import time
from html import unescape
from pathlib import Path

import pytest

from antweb import BASE_URL, classify
from staging_accounts import IS_STAGING, login, needs

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only"), needs("admin")]

ROOT = Path(__file__).resolve().parents[2]
QUERIES_JAVA = ROOT / "src/org/calacademy/antweb/util/Queries.java"

QUERY_LIMIT = 60          # seconds a query may take on staging (a production-size copy of the database)
REQUEST_TIMEOUT = 150     # the request itself waits longer, so a slow query is reported as slow, not as an error

PARAM_QUERIES = [
    ("geolocaleSpeciesListWithRangeData", "Madagascar"),
    ("geolocaleSpeciesListRangeSummary", "Madagascar"),
    ("bioregionSpeciesListWithRangeData", "Malagasy"),
    ("bioregionSpeciesListRangeSummary", "Malagasy"),
]


def query_names():
    text = QUERIES_JAVA.read_text(encoding="utf-8")
    # Commented-out queries (/* */ blocks and lines starting with //) are not queries. A // inside a string
    # (https://...) is not a comment, so only whole comment lines are dropped.
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"(?m)^\s*//.*$", "", text)
    return re.findall(r'new NamedQuery\(\s*"(\w+)"', text)


NAMES = query_names()


@pytest.fixture(scope="module")
def admin():
    s, _token, _id = login("admin")
    return s


@pytest.fixture(scope="module")
def manager_page(admin):
    r = admin.get(BASE_URL + "/query.do", timeout=60)
    assert classify(r).category == "ok", classify(r)
    if "none may run longer than" not in r.text:
        pytest.skip("this server does not have the reviewed queries yet")
    return r.text


def test_names_are_unique():
    assert len(NAMES) > 50, NAMES
    dupes = sorted({n for n in NAMES if NAMES.count(n) > 1})
    assert not dupes, f"named queries defined twice: {dupes}"


def test_manager_lists_every_query_once(manager_page):
    listed = re.findall(r"/query\.do\?name=(\w+)'>\1</a> - ([^<]*)<br>", manager_page)
    names = [n for n, _ in listed]
    assert sorted(names) == sorted(NAMES), (set(NAMES) - set(names), set(names) - set(NAMES))
    short = [n for n, summary in listed if len(unescape(summary).strip()) < 20]
    assert not short, f"queries without a summary line: {short}"


def _run(admin, path):
    start = time.monotonic()
    r = admin.get(BASE_URL + path, timeout=REQUEST_TIMEOUT)
    return r, time.monotonic() - start


def _check(r, secs, label):
    outcome = classify(r)
    assert outcome.category == "ok", (label, outcome)
    text = r.text
    error = re.search(r'class="query-error">([^<]*)</b>([^<]*)', text)
    assert not error, f"{label}: {unescape(error.group(1) + error.group(2)).strip()}"
    assert "What it shows:" in text, f"{label}: no explanation"
    m = re.search(r'class="query-check" data-columns="(\d+)" data-headings="(\d+)"', text)
    assert m, f"{label}: no result table (query not run?)"
    assert m.group(1) == m.group(2), f"{label}: {m.group(2)} headings for {m.group(1)} columns"
    assert secs < QUERY_LIMIT, f"{label}: took {secs:.0f} s (limit {QUERY_LIMIT} s)"


@pytest.mark.parametrize("name", NAMES)
def test_query_runs(admin, manager_page, name):
    r, secs = _run(admin, f"/query.do?name={name}")
    _check(r, secs, name)


@pytest.mark.parametrize("name,param", PARAM_QUERIES)
def test_place_query_runs(admin, manager_page, name, param):
    r, secs = _run(admin, f"/query.do?name={name}&param={param}")
    _check(r, secs, f"{name} {param}")
    # The permalink on the page opens the same query (it used another name, which no page could open).
    assert f"/query.do?name={name}&amp;param={param}" in r.text, f"{name}: permalink"

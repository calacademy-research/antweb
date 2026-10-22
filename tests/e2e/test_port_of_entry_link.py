"""Port of Entry interceptions on the introduced species project page (Brian Fisher, 2026-10-21).

Specimens intercepted at ports (quarantine inspections) have the country "Port of Entry", a pseudo-country
that will be hidden as a place (geolocale is_valid = 0). The introducedants project page links to the
advanced search of these specimens. The search filters on the specimen's country text
(AdvancedSearch: sp.country = '...'), not on valid places, so it keeps working once the place is hidden.

Checked: the link and its one line are on project.do?name=introducedants (rel="nofollow"), not on another
project page; following the link answers with the paged specimen list. Read-only.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session

PAGE = "/project.do?name=introducedants"
OTHER = "/project.do?name=fossilants"
LINK = re.compile(r'<p id="port-of-entry"><a rel="nofollow" href="([^"]+)">Port of Entry interceptions</a>')
LINE = "Ants intercepted at ports of entry (quarantine inspections), listed from the specimen records."
SHOWING = re.compile(r"Showing\s+([\d,]+)(?:&ndash;|–)([\d,]+)\s+of\s+([\d,]+)")


def _get(path_or_url):
    url = path_or_url if path_or_url.startswith("http") else BASE_URL + path_or_url
    r = new_session().get(url, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{url}: HTTP {r.status_code}"
    return r.text


@pytest.fixture(scope="module")
def link():
    html = _get(PAGE)
    if 'id="port-of-entry"' not in html:
        pytest.skip("the Port of Entry link of 2026-10-21 is not deployed on this server yet")
    m = LINK.search(html)
    assert m, "Port of Entry link missing or without rel=\"nofollow\""
    assert LINE in html
    return unescape(m.group(1))


def test_link_on_introduced_project_only(link):
    assert "/advancedSearch.do?" in link
    assert "country=Port+of+Entry" in link and "family=Formicidae" in link and "resultRank=specimen" in link
    assert 'id="port-of-entry"' not in _get(OTHER), "the link belongs only on the introduced species project"


def test_search_answers(link):
    html = _get(link)
    m = SHOWING.search(html)
    assert m, "the Port of Entry search did not show the paged specimen list"
    assert int(m.group(3).replace(",", "")) > 0, "the Port of Entry search found no specimens"

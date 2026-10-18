"""Small display fixes (2026-10-18).

1. Taxon pages showed a stray "// Distribution" line under Overview (a JSP comment written as page text).
2. Curator project lists must not offer projects that have no public page. The project table holds only
   live projects; login_project still has 21 old per-country/state assignments (e.g. madagascarants)
   whose project rows were removed in 2016-2017 when those projects became geolocales. They must not
   appear in the curator's dropdowns.
Read-only.
"""
import os
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session, post_login

TAXON_PAGES = [
    "/description.do?genus=camponotus&rank=genus",
    "/description.do?genus=nesomyrmex&species=clavipilis&rank=species",
]

# login_project names with no project row (production, 2026-10-18): project.do says "not found".
DEAD_PROJECTS = [
    "bajaants", "belgiumants", "britishcolumbiaants", "californiaants", "comorosants", "costaricaants",
    "czechrepublicants", "fijiants", "frenchpolynesiaants", "illinoisants", "italyants", "madagascarants",
    "mauritiusants", "mayotteants", "ohioants", "paraguayants", "queenslandants", "reunionants",
    "seychellesants", "texasants", "virginiaants",
]

CURATOR_PAGES = ["/curate.do", "/speciesListTool.do", "/speciesListHistory.do"]

SELECT = re.compile(r"<select\b.*?</select>", re.S | re.I)
OPTION_VALUE = re.compile(r"<option\b[^>]*\bvalue=[\"']([^\"']*)[\"']", re.I)


@pytest.mark.parametrize("url", TAXON_PAGES)
def test_taxon_page_has_no_stray_distribution_comment(url):
    r = new_session().get(BASE_URL + url, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{url}: HTTP {r.status_code}"
    assert "// Distribution" not in r.text, f"{url} shows the stray '// Distribution' text"


USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")
needs_curator = pytest.mark.skipif(
    not USER or not PASSWORD or IS_PRODUCTION,
    reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, and never runs against production",
)


@pytest.fixture(scope="module")
def curator():
    s = new_session()
    post_login(s, USER, PASSWORD)
    return s


def _dropdown_values(html):
    values = []
    for select in SELECT.findall(html):
        values += OPTION_VALUE.findall(select)
    return values


@needs_curator
@pytest.mark.parametrize("url", CURATOR_PAGES)
def test_curator_dropdowns_have_no_dead_projects(curator, url):
    r = curator.get(BASE_URL + url, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{url}: HTTP {r.status_code}"
    dead = sorted(set(_dropdown_values(r.text)) & set(DEAD_PROJECTS))
    assert not dead, f"{url} offers projects with no public page: {dead}"


@needs_curator
def test_curator_dropdown_projects_have_public_pages(curator):
    """Every project offered on curate.do opens a real project page (not 'Project:x not found.')."""
    r = curator.get(BASE_URL + "/curate.do", timeout=max(TIMEOUT, 120))
    projects = sorted({v for v in _dropdown_values(r.text)
                       if re.fullmatch(r"[a-z]+ants", v) and v not in ("worldants", "allantwebants")})
    visitor = new_session()
    for name in projects:
        p = visitor.get(BASE_URL + "/project.do?name=" + name, timeout=max(TIMEOUT, 120))
        assert p.status_code == 200 and f"Project:{name} not found" not in p.text, f"project {name} has no public page"

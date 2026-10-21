"""Download guards (Brian Fisher, 2026-10-21): no free bulk downloads of specimen data; bulk access will go
through the API with registered tokens.

- Advanced search specimen list: 200 a page, "Showing 1–200 of N", Previous / Next (rel="nofollow"), no
  "See all" link; the Madagascar specimen search answers within 5 seconds; sorting applies before paging.
- getSpecimenList.do (Download Data of a taxon page): a genus or species only; a family is refused with a
  plain message, also for a logged-in curator (zztest uploader); a genus download of a country holds only
  specimens collected in that country.
- Show Specimen Taxa of Madagascar lists no non-ant names (Orthoptera uploaded with ant data).
- robots.txt disallows the search and download actions.

Read-only. Database behaviour: test/org/calacademy/antweb/home/DownloadGuardsDbTest.java (CI).
"""
import re
import time
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import login, needs

MADAGASCAR = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&country=Madagascar&resultRank=specimen"
SHOWING = re.compile(r"Showing\s+([\d,]+)(?:&ndash;|–)([\d,]+)\s+of\s+([\d,]+)")
CODE = re.compile(r'/specimen\.do\?code=([^"\'&]+)')
REFUSED = "Downloads are available for a genus or species. For larger data sets, contact antweb@antweb.org about API access."


def _deployed():
    r = new_session().get(BASE_URL + "/robots.txt", timeout=TIMEOUT)
    if "Disallow: /getSpecimenList.do" not in r.text:
        pytest.skip("the download guards of 2026-10-21 are not deployed on this server yet")


def _num(s):
    return int(s.replace(",", ""))


def test_robots_disallows_search_and_downloads():
    _deployed()
    lines = new_session().get(BASE_URL + "/robots.txt", timeout=TIMEOUT).text.splitlines()
    for rule in ("Disallow: /advancedSearch.do", "Disallow: /getSpecimenList.do", "Disallow: /getSearchList.do",
                 "Disallow: /speciesListDownload.do", "Disallow: /web/"):
        assert rule in lines, rule


def test_search_list_is_paged_and_fast():
    _deployed()
    s = new_session()
    start = time.time()
    r = s.get(BASE_URL + MADAGASCAR, timeout=max(TIMEOUT, 120))
    elapsed = time.time() - start
    assert r.status_code == 200, r.status_code
    m = SHOWING.search(r.text)
    assert m, "no 'Showing 1–200 of N' on the search page"
    first, last, total = (_num(g) for g in m.groups())
    assert first == 1 and last == min(200, total), m.group(0)
    assert "See all" not in r.text and "limit=10000" not in r.text
    codes = CODE.findall(r.text)
    assert 0 < len(set(codes)) <= 200, len(set(codes))
    if total > 200:
        nxt = re.search(r'<a class="search_pager_next" rel="nofollow" href="([^"]+)"', r.text)
        assert nxt, "no Next link"
        assert "page=2" in unescape(nxt.group(1))
        r2 = s.get(unescape(nxt.group(1)), timeout=max(TIMEOUT, 120))
        m2 = SHOWING.search(r2.text)
        assert m2 and _num(m2.group(1)) == 201, m2 and m2.group(0)
        assert not set(CODE.findall(r2.text)) & set(codes), "page 2 repeats specimens of page 1"
        assert 'class="search_pager_prev"' in r2.text
    assert elapsed < 5, f"Madagascar specimen search took {elapsed:.1f} s"


def test_sort_applies_before_paging():
    """Descending and ascending first pages share no specimen when there are more than 400: the order
    is applied to the whole result, then cut into pages (a sort within one page would give the same 200)."""
    _deployed()
    s = new_session()
    up = s.get(BASE_URL + MADAGASCAR + "&sortBy=code", timeout=max(TIMEOUT, 120))
    m = SHOWING.search(up.text)
    if not m or _num(m.group(3)) <= 400:
        pytest.skip("400 specimens or fewer")
    down = s.get(BASE_URL + MADAGASCAR + "&sortBy=code&sortOrder=down", timeout=max(TIMEOUT, 120))
    p_up, p_down = set(CODE.findall(up.text)), set(CODE.findall(down.text))
    assert p_up and p_down
    assert not p_up & p_down, sorted(p_up & p_down)[:5]


@needs("uploader")
def test_specimen_list_refuses_a_family_for_a_curator():
    _deployed()
    s, _token, _id = login("uploader")
    r = s.get(BASE_URL + "/getSpecimenList.do?countryName=Madagascar&taxonName=formicidae", timeout=max(TIMEOUT, 120))
    assert REFUSED in unescape(r.text), r.text[:500]
    assert "Tab-delimited data" not in r.text
    r = s.get(BASE_URL + "/getSpecimenList.do?taxonName=myrmicinae", timeout=max(TIMEOUT, 120))
    assert REFUSED in unescape(r.text), r.text[:500]


@needs("uploader")
def test_genus_download_of_a_country_holds_only_its_specimens():
    _deployed()
    s, _token, _id = login("uploader")
    r = s.get(BASE_URL + "/getSpecimenList.do?countryName=Comoros&taxonName=formicinaecamponotus", timeout=max(TIMEOUT, 300))
    assert r.status_code == 200
    link = re.search(r'href="([^"]+\.txt)"', r.text)
    assert link, r.text[:500]
    data = s.get(unescape(link.group(1)), timeout=max(TIMEOUT, 120)).text.splitlines()
    header = data[0].split("\t")
    country = header.index("country")
    rows = [line.split("\t") for line in data[1:] if line.strip()]
    assert rows, "no Camponotus specimens from the Comoros on this server"
    assert len(rows) <= 5000
    others = {row[country] for row in rows if len(row) > country and row[country] != "Comoros"}
    assert not others, f"specimens from other countries in the Comoros file: {sorted(others)[:5]}"


def test_madagascar_specimen_taxa_has_no_non_ants():
    _deployed()
    r = new_session().get(BASE_URL + "/taxonomicPage.do?rank=species&countryName=Madagascar&specimen=true",
                          timeout=max(TIMEOUT, 180))
    assert r.status_code == 200
    text = unescape(r.text).lower()
    for name in ("conocephalinae", "cyrtacanthacridinae", "tettigoniidae", "acrididae"):
        assert name not in text, name

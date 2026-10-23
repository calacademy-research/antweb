"""Search: the advanced search form and results (specimen list and by taxon), recent images, the header
Places box (road map step 2; docs/flows/search.md).

Anonymous and read-only (GET only). Paging, sorting, the 200-a-page list and the download guards are
tested by test_download_guards.py; the header Taxa box by test_taxon_pages.py and test_browser.py.
"""
import re

import pytest

from public_pages import assert_message, assert_ok, fetch, h1, links, params, title

SEARCH = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&country=Comoros"
SHOWING = re.compile(r"Showing\s+([\d,]+)(?:&ndash;|–)([\d,]+)\s+of\s+([\d,]+)")
BY_TAXON = re.compile(r'<h2 class="display_count">\s*([\d,]+) (\w+) \(from ([\d,]+) specimens\)', re.I)


def _num(s):
    return int(s.replace(",", ""))


def test_advanced_search_form():
    r = fetch("/advSearch.do")
    assert_ok(r)
    assert title(r) == "Advanced Search - AntWeb", title(r)
    assert 'action="' in r.text and "advancedSearch" in r.text, "no search form"
    for field in ('name="genus"', 'name="resultRank"', 'name="searchMethod"'):
        assert field in r.text, f"advSearch.do: no {field} field"


def test_search_by_genus_rank_lists_genera():
    """resultRank=genus: "Advanced Search Genus Results", "N genera (from M specimens)", each genus linked."""
    r = fetch(SEARCH + "&resultRank=genus")
    assert_ok(r)
    assert title(r) == "Advanced Specimen Search - AntWeb", title(r)
    assert "Advanced Search Genus Results" in h1(r.text), h1(r.text)
    m = BY_TAXON.search(r.text)
    assert m, "no 'N genera (from M specimens)' line"
    n, word, specimens = _num(m.group(1)), m.group(2), _num(m.group(3))
    assert word.lower() == "genera" and 0 < n <= specimens, m.group(0)
    genera = {params(p).get("genus") or params(p).get("name") for p in links(r.text, "description")
              if params(p).get("rank") == "genus"}
    assert genera, "no genus links"


def test_genus_filter_narrows_the_specimen_list():
    """A genus found in the country's genus results, searched in the same country, gives fewer or as many
    specimens as the whole country, and every specimen shown is linked to its page."""
    by_genus = fetch(SEARCH + "&resultRank=genus")
    genera = [params(p).get("genus") or params(p).get("name") for p in links(by_genus.text, "description")
              if params(p).get("rank") == "genus"]
    if not genera:
        pytest.skip("no genus in the Comoros results")
    country = fetch(SEARCH + "&resultRank=specimen")
    one = fetch(SEARCH + f"&genusSearchType=equals&genus={genera[0]}&resultRank=specimen")
    for r in (country, one):
        assert_ok(r)
        assert "Advanced Search Results" in h1(r.text), h1(r.text)
    total_country = _num(SHOWING.search(country.text).group(3))
    m = SHOWING.search(one.text)
    assert m, f"no 'Showing' line for genus {genera[0]}"
    assert 0 < _num(m.group(3)) <= total_country, (m.group(0), total_country)
    assert links(one.text, "specimen"), "no specimen links"


def _search_then_reshow():
    from antweb import new_session
    s = new_session()
    first = fetch(SEARCH + "&resultRank=specimen", session=s)
    assert_ok(first)
    again = fetch("/advancedSearchResults.do", session=s)
    assert_ok(again)
    return first, again


def test_results_shown_again_from_the_session():
    """advancedSearchResults.do shows the specimens of the session's last search page again."""
    first, again = _search_then_reshow()
    assert "Advanced Search Results" in h1(again.text), h1(again.text)
    assert SHOWING.search(again.text), "no 'Showing' line"
    codes = lambda page: {(params(p).get("code") or params(p).get("name") or "").lower() for p in links(page, "specimen")}
    assert codes(first.text) and codes(again.text) == codes(first.text)


@pytest.mark.xfail(strict=False, reason="finding F25: shown again, the page counts the 200 rows kept in the session, "
                                        "not the search's total; docs/flows/public-pages-findings.md")
def test_results_shown_again_keep_the_total():
    first, again = _search_then_reshow()
    m1, m2 = SHOWING.search(first.text), SHOWING.search(again.text)
    assert m1 and m2 and m1.group(3) == m2.group(3), (m1 and m1.group(0), m2 and m2.group(0))


def test_search_without_a_method():
    """advancedSearch.do without searchMethod answers with a message (case number to report), not an error."""
    assert_message(fetch("/advancedSearch.do?genus=camponotus"), "Invalid searchType:")


def test_recent_images():
    r = fetch("/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30")
    assert_ok(r)
    assert title(r) == "Recently Added Specimen Images - AntWeb", title(r)


def test_recent_images_needs_a_following_parameter():
    """Pinned: searchMethod=recentImageSearch must be followed by another parameter (SearchAction looks for
    the next '&')."""
    assert_message(fetch("/recentSearchResults.do?searchMethod=recentImageSearch"),
                   "RecentImageSearch was expecting ampersand parameter")


# ---------- the header Places box ----------

@pytest.mark.parametrize("typed, page", [
    ("madagascar", "/country.do?name=Madagascar"),
    ("Antananarivo, Madagascar", "/adm1.do?name=Antananarivo"),
])
def test_place_name_opens_the_place_page(typed, page):
    """place.do?placeName= redirects to the place's page (lower case is capitalised; 'Adm1, Country' finds an adm1)."""
    r = fetch(f"/place.do?placeName={typed}")
    assert_ok(r)
    assert r.history, "no redirect"
    assert page.split("?")[0] in r.url and params(page)["name"] == params(r.url)["name"], r.url


@pytest.mark.parametrize("path, expected", [
    ("/place.do?placeName=Atlantis", "Place not found:Atlantis"),
    ("/place.do", "Must enter a place name for the search."),
])
def test_place_not_found(path, expected):
    assert_message(fetch(path), expected, path)


def test_place_autocomplete():
    from antweb import BASE_URL, new_session
    r = new_session().get(BASE_URL + "/search/autoCompletePlaceKeys.jsp?q=madag", timeout=60)
    assert r.status_code == 200, r.status_code
    assert "Madagascar" in r.text, r.text[:300]

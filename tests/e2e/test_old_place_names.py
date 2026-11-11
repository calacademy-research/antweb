"""An old place name in a link goes to the same address with the current name (2026-11-10; OldPlaceNames,
SessionRequestFilter, docs/flows/places.md).

AntWeb keeps an old name of a place as a not-valid place whose valid name is the current one (USA: United States;
MA: Massachusetts in United States; Curacao: Curaçao; after the renames of 2026-11, Ivory Coast: Côte d'Ivoire). Until
2026-11-10 country.do?name=USA opened the empty page of the not-valid place ("This location is Invalid"), a list with
countryName=USA was empty, and adm1.do?name=MA&country=USA said the adm1 was not found.

Now a page request (GET) whose country (countryName=, country=, name= on country.do) or adm1 (adm1Name=, name= on
adm1.do) is such an old name is answered with a permanent redirect (301) to the same address with the current name,
URL-encoded, every other parameter as it was. A valid name, an unknown name and an old name without a valid name are
answered as before.

Read-only GETs without a login. The rows are production's (staging holds a copy): USA (not valid, valid name United
States), MA in United States (valid name Massachusetts), Curacao (valid name Curaçao), Bouvet Island (not valid, no
valid name).
"""
from urllib.parse import urlsplit

import pytest

from antweb import BASE_URL, TIMEOUT, new_session


@pytest.fixture(scope="module")
def session():
    return new_session()


def answer(session, url):
    return session.get(BASE_URL + url, timeout=TIMEOUT, allow_redirects=False)


def moved(session, url):
    """(path, query) of the Location of a 301 answer."""
    r = answer(session, url)
    assert r.status_code == 301, (url, r.status_code, r.headers.get("Location"))
    to = urlsplit(r.headers["Location"])
    assert to.netloc in ("", urlsplit(BASE_URL).netloc), r.headers["Location"]
    return to.path, to.query


def test_old_country_name_moves_to_the_country_page(session):
    assert moved(session, "/country.do?name=USA") == ("/country.do", "name=United+States")
    page = session.get(BASE_URL + "/country.do?name=USA", timeout=TIMEOUT)
    assert page.status_code == 200 and page.url.endswith("/country.do?name=United+States"), (page.status_code, page.url)
    assert "United States" in page.text and "This location is Invalid" not in page.text


def test_old_country_name_in_list_and_search_links(session):
    assert moved(session, "/taxonomicPage.do?rank=genus&countryName=USA") == ("/taxonomicPage.do", "rank=genus&countryName=United+States")
    assert moved(session, "/taxonomicPage.do?countryName=USA&rank=species&images=true") == (
        "/taxonomicPage.do", "countryName=United+States&rank=species&images=true")
    assert moved(session, "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&country=USA&resultRank=specimen") == (
        "/advancedSearch.do", "searchMethod=advancedSearch&advanced=true&family=formicidae&country=United+States&resultRank=specimen")


def test_the_new_name_is_url_encoded(session):
    """Curacao: Curaçao. The accent goes into the address as UTF-8 percent-encoding, and the page opens."""
    assert moved(session, "/country.do?name=Curacao") == ("/country.do", "name=Cura%C3%A7ao")
    page = session.get(BASE_URL + "/country.do?name=Curacao", timeout=TIMEOUT)
    assert page.status_code == 200 and page.url.endswith("/country.do?name=Cura%C3%A7ao"), (page.status_code, page.url)
    assert "This location is Invalid" not in page.text


def test_old_adm1_name_and_old_country_name_move_together(session):
    assert moved(session, "/adm1.do?name=MA&country=USA") == ("/adm1.do", "name=Massachusetts&country=United+States")
    assert moved(session, "/adm1.do?name=MA&country=United+States") == ("/adm1.do", "name=Massachusetts&country=United+States")
    assert moved(session, "/taxonomicPage.do?rank=genus&adm1Name=MA&countryName=United+States") == (
        "/taxonomicPage.do", "rank=genus&adm1Name=Massachusetts&countryName=United+States")
    page = session.get(BASE_URL + "/adm1.do?name=MA&country=USA", timeout=TIMEOUT)
    assert page.status_code == 200 and "Massachusetts" in page.text, (page.status_code, page.url)


@pytest.mark.parametrize("url", [
    "/country.do?name=United+States",                 # a valid name
    "/country.do?name=Madagascar",
    "/taxonomicPage.do?rank=genus&countryName=Madagascar",
    "/adm1.do?name=Massachusetts&country=United+States",
    "/country.do?name=Bouvet+Island",                 # an old name without a valid name: the page of before
    "/country.do?name=Atlantis",                      # not a place
    "/adm1.do?name=MA",                               # no country given
    "/description.do?name=USA&rank=genus",            # name is a country only on country.do
])
def test_other_names_are_answered_as_before(session, url):
    r = answer(session, url)
    assert r.status_code != 301, (url, r.status_code, r.headers.get("Location"))


def test_the_place_tools_keep_the_old_name(session):
    """Edit Geolocale works on the old-name row itself: it is not sent to the valid place (an anonymous visitor is
    sent to the log-in page or refused, as before)."""
    r = answer(session, "/editGeolocale.do?name=USA&georank=country")
    assert "United" not in r.headers.get("Location", ""), r.headers.get("Location")

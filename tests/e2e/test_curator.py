"""Tier 3: a logged-in curator (staging test account, never production).

Uses the GitHub secrets ANTWEB_TEST_USER and ANTWEB_TEST_PASSWORD: an ordinary
curator ("Default Group", can upload specimens and images, not an admin).
These tests only read pages; they do not upload or change data.
"""
import os

import pytest

from antweb import BASE_URL, IS_PRODUCTION, get, new_session

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

pytestmark = pytest.mark.skipif(
    not USER or not PASSWORD or IS_PRODUCTION,
    reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD, and never runs against production",
)

# Pages a logged-in curator uses.
CURATOR_PAGES = [
    "/curate.do",
    "/uploadHistory.do",
    "/listSpecimenUploads.do",
    "/listImageUploads.do",
    "/speciesListTool.do",
    "/validateSpeciesList.do",
    "/specimenListLookup.do",
    "/curators.do",
]

# Admin-only pages: an ordinary curator must be refused (authorization, not just login).
ADMIN_ONLY = [
    "/viewLogins.do",
    "/manageMuseum.do",
    "/saveGroup.do",
    "/editCredit.do",
    "/worldAuthoritySave.do",
    "/newBioRegion.do",
    "/serverDebug.do",
    "/dbStatus.do",
    "/loginAs.do?id=1",
]


def login(session):
    r = session.post(BASE_URL + "/login.do",
                     data={"userName": USER, "password": PASSWORD, "isSubmit": "true"},
                     timeout=60, allow_redirects=True)
    return r


@pytest.fixture(scope="module")
def curator():
    s = new_session()
    login(s)
    return s


def test_wrong_password_is_refused():
    s = new_session()
    s.post(BASE_URL + "/login.do", data={"userName": USER, "password": PASSWORD + "-wrong", "isSubmit": "true"}, timeout=60)
    assert get(s, "/curate.do").category == "login"


def test_login_works(curator):
    assert get(curator, "/curate.do").category != "login"


@pytest.mark.parametrize("url", CURATOR_PAGES)
def test_curator_page(curator, url):
    outcome = get(curator, url)
    assert outcome.category in ("ok", "message"), f"{url}: {outcome}"


@pytest.mark.parametrize("url", ADMIN_ONLY)
def test_admin_page_refused_for_curator(curator, url):
    outcome = get(curator, url)
    assert outcome.category in ("login", "denied"), f"{url} was not refused for an ordinary curator: {outcome}"


def test_password_not_in_pages(curator):
    """The curator's own password must never be echoed into a page they open themselves.
    Passwords are stored hashed and never pre-filled into forms."""
    for url in ("/curate.do", "/editLogin.do", "/curator.do"):
        r = curator.get(BASE_URL + url, timeout=60)
        assert PASSWORD not in r.text, f"{url} contains the password"


def test_logout():
    s = new_session()
    login(s)
    s.get(BASE_URL + "/logout.do", timeout=60)
    assert get(s, "/curate.do").category == "login"

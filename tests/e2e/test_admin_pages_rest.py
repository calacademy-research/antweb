"""The curator and administrator pages no test opened (road map step 5; catalog section 6), read only, as the test
administrator on staging: each answers 200 with its own heading (and, for the access check, an anonymous visitor is
asked to log in). Nothing here changes data. util.do?action=emailTest is left out (it sends an email).
"""
import pytest

from antweb import BASE_URL, TIMEOUT, get, new_session
from staging_accounts import login, needs

PAGES = [
    ("/list.do?action=introducedSpecimen&groupId=1", "Introduced Specimen"),
    ("/list.do?action=multiBioregionTaxaList&groupId=1", "in multiple bioregions"),
    ("/adm1Mgr.do?countryName=Madagascar", "Madagascar"),
    ("/countryDoc.do", "Country Documentation"),
    ("/logMgr.do?action=antwebInfoLog", "Log Manager"),
    ("/logMgr.do?action=antwebLog", "Log Manager"),
    ("/logMgr.do?action=queryStatsLog", "Log Manager"),
    ("/logMgr.do?action=tomcatLog", "Log Manager"),
    ("/logMgr.do?action=worldants", "Log Manager"),
    ("/logMgr.do?action=specimenDetails", "Specimen Description"),
    ("/newGroup.do", "New upload group"),
    ("/genusConceptRules.do", "Old genus concepts"),
    ("/query.do?action=curiousQueries", "Curious queries"),
    ("/query.do?action=devQueries", "Dev"),
    ("/query.do?action=events", "Events Report"),
    ("/manageMuseum.do?action=view&code=CASC", "Museum Manager"),
    ("/viewMuseum.do?action=view&code=CASC", "CASC"),
    ("/saveMuseum.do?action=view&code=CASC", "CASC"),
    ("/speciesListToolSearch.do", "Species List Tool"),
    ("/list.do", "not found"),   # no action: "action:null not found." (gave a 500 logged in, 2026-11-03)
    ("/speciesListTool.do?action=changeRefSpeciesList&refSpeciesListType=antcat", "Species List Tool"),
]


@pytest.fixture(scope="module")
def admin():
    return login("admin")[0]


@needs("admin")
@pytest.mark.parametrize("path, words", PAGES)
def test_admin_page_opens(admin, path, words):
    r = admin.get(BASE_URL + path, timeout=max(TIMEOUT, 180))
    assert r.status_code == 200, (path, r.status_code)
    assert words in r.text, f"{path}: '{words}' not on the page"
    assert "Perhaps your session has gone stale" not in r.text, f"{path}: the error page"


@pytest.mark.parametrize("path", ["/newGroup.do", "/countryDoc.do", "/query.do?action=devQueries"])
def test_anonymous_visitor_is_asked_to_log_in(path):
    outcome = get(new_session(), path)
    assert outcome.category == "login", (path, outcome)

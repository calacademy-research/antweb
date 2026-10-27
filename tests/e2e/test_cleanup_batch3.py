"""Cleanup batch 3: the removed util.do and utilData.do sub-actions stay removed.

util.do and utilData.do now answer any sub-action they do not handle with 404, before the login
check, so a removed job can be tested without logging in and nothing runs. The list of handled
sub-actions is the ACTIONS set in UtilAction.java and UtilDataAction.java.

Static checks (any host): no source file handles or links a removed sub-action; ACTIONS matches what
the code handles; the Curate page links (curatorLinks.jsp, curate-body.jsp) and the Country
Documentation page link only to handled sub-actions; the removed field guide and Move To Valid files
are gone. Live checks (read-only, anonymous): removed sub-actions answer 404, kept ones do not;
query.do?action=homonyms needs a login. As the test curator: the Curate page links no removed action.
"""
import os
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, get, new_session, post_login

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

REMOVED_UTIL = (
    "unlockImageUpload", "testMessage", "unboldMessage", "bareMessage", "zonageekTest", "gson",
    "checkIntegrity", "curiousQueries", "curiousQuery", "adminAlerts", "descEdit", "homonyms", "events",
    "geolocaleIntroduced", "archiveLogs", "cpuCheck", "diskFree", "isDiskLow",
)
REMOVED_UTIL_DATA = (
    "allSets", "set1to3", "updateFormicidaeProjects", "testWorldantsLog", "adminAlertTest", "parseDates",
    "setSpecimenSource", "populateFinish", "worldantsCount", "geolocaleTaxonImageCountCrawl", "calcEndemic",
    "calcIntroduced", "getCurations", "gson", "testProjTaxon", "archiveLogs", "updateNullLocalityCodes",
    "deleteOrphans", "specimenPostProcess", "deleteOldSpecimenUploadTaxa", "updateSpecimenStatus",
    "fixHtmlAuthorDates", "exifData", "imageUtil", "callPostInitialize", "moveImages", "changeOwner",
    "changeOwnerAndPerms",
    # Asked and decided: the AntWiki Regional Taxon List import, and the duplicate of antWikiData.do.
    "populateFromAntwikiData", "checkAntwikiForUpdates",
)
# Links that pointed at sub-actions that never existed (curate page and Country Documentation page).
DEAD_LINKED = (
    "deleteTaxonProp", "updateAdm1FromParentData", "countryData", "finishCountryUpload", "allCountCrawls",
    "populateOneGeolocaleTaxon", "populateGeolocaleCountryTaxon", "populateOneProjTaxon",
    "updateTaxonSetTaxonNames", "reCrawl", "countCrawls", "parentCrawl", "projTaxonImageCountCrawl",
    "updateTaxonCountryProject", "populateFromCountrySpecimenData", "undoPopulateFromSpecimenData",
    "populateFromAdm1SpecimenData", "undoPopulateFromAdm1SpecimenData", "undoPopulateFromAntwikiData",
    "introducedSpecimen", "pushCountryData", "pushCountriesToProjects", "undoPushCountriesToProjects",
    "countCrawl", "genGroupObjectMap",
)
REMOVED_FILES = (
    "src/org/calacademy/antweb/search/PrepareFieldGuideResultsAction.java", "web/fieldGuide.xsl",
    "web/fieldGuide.complicated.xsl", "web/image/fieldguide_title.png", "web/common/list_tools.jsp",
    "web/curate/moveToValid.jsp",
)
UTIL = REPO_ROOT / "src/org/calacademy/antweb/util/UtilAction.java"
UTIL_DATA = REPO_ROOT / "src/org/calacademy/antweb/util/UtilDataAction.java"
CURATE_PAGES = ("web/curate/curatorLinks.jsp", "web/curate/curate-body.jsp", "web/curate/technical-body.jsp",
                "web/curate/geolocale/countryDoc-body.jsp")
LINK = re.compile(r"/(util|utilData)\.do\?action=(\w+)")


def _code(path):
    """Source without comments."""
    text = path.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def _actions_set(path):
    m = re.search(r"Set<String> ACTIONS = (?:java\.util\.)?Set\.of\((.*?)\);", path.read_text(), re.S)
    assert m, f"no ACTIONS set in {path.name}"
    return set(re.findall(r'"(\w+)"', m.group(1)))


def _handled(path):
    found = re.findall(r'action\.(?:equals|contains)\("(\w+)"\)|"(\w+)"\.equals\(action\)', _code(path))
    return {a or b for a, b in found}


def test_actions_sets_match_the_code():
    for path in (UTIL, UTIL_DATA):
        assert _actions_set(path) == _handled(path), path.name
    assert not set(REMOVED_UTIL) & _actions_set(UTIL)
    assert not set(REMOVED_UTIL_DATA) & _actions_set(UTIL_DATA)


def test_removed_sub_actions_stay_removed_in_source():
    """No page or source file links a removed or never-existing util.do / utilData.do sub-action."""
    removed = {"util": set(REMOVED_UTIL) | set(DEAD_LINKED), "utilData": set(REMOVED_UTIL_DATA) | set(DEAD_LINKED)}
    found = []
    for top in ("src", "web", "WEB-INF"):
        for path in (REPO_ROOT / top).rglob("*"):
            if path.suffix not in (".java", ".jsp", ".xml", ".js", ".html") or not path.is_file():
                continue
            text = _code(path) if path.suffix == ".java" else re.sub(r"<!--.*?-->", "", path.read_text(
                encoding="utf-8", errors="replace"), flags=re.S)
            for servlet, action in LINK.findall(text):
                if action in removed[servlet]:
                    found.append(f"{path.relative_to(REPO_ROOT)}: {servlet}.do?action={action}")
    assert not found, found


def test_curate_pages_link_only_handled_actions():
    """Every util.do / utilData.do link on the Curate and Country Documentation pages is handled."""
    handled = {"util": _actions_set(UTIL), "utilData": _actions_set(UTIL_DATA)}
    bad = []
    for page in CURATE_PAGES:
        text = re.sub(r"<!--.*?-->", "", (REPO_ROOT / page).read_text(), flags=re.S)
        bad += [f"{page}: {s}.do?action={a}" for s, a in LINK.findall(text) if a not in handled[s]]
    assert not bad, bad
    assert "moveToValid" not in (REPO_ROOT / "WEB-INF/struts-config.xml").read_text()


def test_removed_files_are_gone():
    assert not [f for f in REMOVED_FILES if (REPO_ROOT / f).exists()]
    assert "prepareFieldGuideResults" not in (REPO_ROOT / "WEB-INF/struts-config.xml").read_text()
    for page in ("web/search/advancedSearchResults-body.jsp", "web/search/advancedSearchResultsByTaxon-body.jsp"):
        assert "fieldGuide" not in (REPO_ROOT / page).read_text(), page


def _deployed():
    return get(new_session(), "/util.do?action=noSuchUtilAction").status == 404


@pytest.mark.parametrize("url", [f"/util.do?action={a}" for a in REMOVED_UTIL]
                         + [f"/utilData.do?action={a}" for a in REMOVED_UTIL_DATA])
def test_removed_sub_action_is_not_found(url):
    """Answered with 404 before any login check; nothing runs."""
    if not _deployed():
        pytest.skip("this server does not have cleanup batch 3 yet")
    outcome = get(new_session(), url)
    assert outcome.status == 404 and outcome.category == "not_found", f"{url}: {outcome}"


def test_kept_sub_actions_still_answer():
    if not _deployed():
        pytest.skip("this server does not have cleanup batch 3 yet")
    r = new_session().get(BASE_URL + "/util.do?action=isRestart", timeout=TIMEOUT)
    assert r.status_code == 200 and re.search(r"\b(true|false)\b", r.text), r.status_code
    # A kept data job still asks an anonymous visitor to log in (it is not a 404).
    assert get(new_session(), "/utilData.do?action=countReport").category == "login"


def test_is_busy_answers_idle_or_busy():
    """util.do?action=isBusy (2026-10-27): anonymous, read-only, plain text "idle" or "busy: <kinds>".
    The deploy script asks it before a restart and waits while an upload runs; no login names in it."""
    r = new_session().get(BASE_URL + "/util.do?action=isBusy", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert r.headers.get("Content-Type", "").startswith("text/plain"), r.headers.get("Content-Type")
    text = r.text.strip()
    assert text == "idle" or text.startswith("busy:"), text[:200]
    if text.startswith("busy:"):
        kinds = {"specimen upload", "image upload", "TaxonWorks nightly upload", "nightly name update",
                 "background job", "data job", "nightly run"}
        assert {k.strip() for k in text[len("busy:"):].split(",")} <= kinds, text


def test_homonym_report_needs_login():
    """query.do?action=homonyms (moved from util.do, admin only there) is not public."""
    if not _deployed():
        pytest.skip("this server does not have cleanup batch 3 yet")
    assert get(new_session(), "/query.do?action=homonyms").category == "login"


def test_curate_page_as_curator_links_no_removed_action():
    if not USER or not PASSWORD:
        pytest.skip("needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")
    s = new_session()
    post_login(s, USER, PASSWORD)
    html = re.sub(r"<!--.*?-->", "", s.get(BASE_URL + "/curate.do", timeout=TIMEOUT).text, flags=re.S)
    removed = {"util": set(REMOVED_UTIL) | set(DEAD_LINKED), "utilData": set(REMOVED_UTIL_DATA) | set(DEAD_LINKED)}
    found = [f"{a}.do?action={b}" for a, b in LINK.findall(html) if b in removed[a]]
    assert not found, found
    assert "/moveToValid.do" not in html and "prepareFieldGuideResults" not in html

"""Merges and fixes after the check of the 57 unclear catalog rows (2026-10-19, Brian's decisions).

Anonymous, read-only, any host:
- /search.do redirects (301) to /advSearch.do;
- /recentDescEdits.do serves only the Recent Description Edits page (no action or
  action=recentDescEdits); every other action answers 404 there (the reports stay on /list.do);
- utilData.do: the three taxon count crawls (now part of each page's Recalculate), the parentage
  test (now the preview step of fixGeolocaleTaxonParentage) and taxaOutsideOfNativeBioregion (now a
  report on query.do) answer 404 before any login check;
- query.do?action=taxaOutsideOfNativeBioregion and logMgr.do?action=uploadLine ask for a login.
As the test curator (staging, read-only): logMgr.do?action=uploadLine is no longer refused; a file of
another group answers "Not available.". upload.do?action=genAll: test_curate_cleanup.py.
Static (any host): the count crawl links are gone, the project Recalculate runs the crawl, the Curate
page and Geolocale Manager link the report on query.do, the report starts with its explanation box,
the parentage repair previews before it applies.
"""
import os

import pytest

from antweb import BASE_URL, TIMEOUT, classify, get, new_session
from staging_accounts import IS_STAGING, has, login

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def _src(path):
    return open(os.path.join(ROOT, path), encoding="utf-8", errors="replace").read()


# ---------- anonymous ----------

def test_search_redirects_to_advanced_search():
    r = new_session().get(BASE_URL + "/search.do", timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 301, (r.status_code, r.text[:200])
    assert r.headers.get("Location", "").endswith("/advSearch.do"), r.headers.get("Location")


@pytest.mark.parametrize("url", ["/recentDescEdits.do", "/recentDescEdits.do?action=recentDescEdits"])
def test_recent_desc_edits_page(url):
    r = new_session().get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code == 200 and "Recent Description Edits" in r.text, (r.status_code, classify(r))


@pytest.mark.parametrize("action", ["casentDAnomalies", "likes", "pictureLikes", "usrAdm", "usrAdmLastLogin",
                                    "introducedSpecimen", "multiBioregionTaxaList", "countries", "bioregions"])
def test_recent_desc_edits_other_actions_not_found(action):
    outcome = get(new_session(), f"/recentDescEdits.do?action={action}")
    assert outcome.status == 404 and outcome.category == "not_found", f"{action}: {outcome}"


@pytest.mark.parametrize("action", ["geolocaleTaxonCountCrawl", "museumTaxonCountCrawl", "projTaxonChildCountCrawl",
                                    "testGeolocaleTaxonParentage", "taxaOutsideOfNativeBioregion"])
def test_merged_data_jobs_not_found(action):
    outcome = get(new_session(), f"/utilData.do?action={action}")
    assert outcome.status == 404 and outcome.category == "not_found", f"{action}: {outcome}"


@pytest.mark.parametrize("url", ["/query.do?action=taxaOutsideOfNativeBioregion",
                                 "/logMgr.do?action=uploadLine&file=x-specimen1.txt&line=2"])
def test_reports_need_login(url):
    outcome = get(new_session(), url)
    assert outcome.category == "login", f"{url}: {outcome}"


# ---------- test curator ----------

@pytest.fixture(scope="module")
def curator():
    if not (IS_STAGING and has("noupload")):
        pytest.skip("needs ANTWEB_TEST_NOUPLOAD_USER / _PASSWORD (staging only)")
    return login("noupload")[0]


def test_upload_line_open_to_curator_own_group_only(curator):
    # Group 1 is CAS; the staging test curator is not in it, so the line is not shown.
    r = curator.get(BASE_URL + "/logMgr.do?action=uploadLine&file=2026-01-01-specimen1.txt&line=2", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert classify(r).category not in ("login", "denied"), classify(r)
    assert "Not available." in r.text, r.text[:300]


# ---------- source checks ----------

def test_count_crawl_links_gone():
    assert "getCountCrawlLink" not in _src("web/taxonPage-body.jsp")
    assert "projTaxonChildCountCrawl" not in _src("web/overview-body.jsp")
    for f in ("src/org/calacademy/antweb/Museum.java", "src/org/calacademy/antweb/geolocale/Geolocale.java",
              "src/org/calacademy/antweb/Overview.java"):
        assert "getCountCrawlLink" not in _src(f), f


def test_project_recalculate_runs_the_crawl():
    src = _src("src/org/calacademy/antweb/OverviewAction.java")
    block = src[src.index("if (isRecalc) {"):]
    block = block[:block.index("}")]
    assert "new ProjTaxonCountDb(connection).childrenCountCrawl(projectName);" in block
    assert block.index("childrenCountCrawl") < block.index("projectDb.updateCounts(projectName)")


def test_taxa_outside_report_links_and_box():
    for f in ("web/curate/curatorLinks.jsp", "web/curate/geolocale/geolocaleMgr-body.jsp"):
        text = _src(f)
        assert "/query.do?action=taxaOutsideOfNativeBioregion" in text and "utilData.do?action=taxaOutside" not in text, f
    assert "ReportHelp.TAXA_OUTSIDE" in _src("src/org/calacademy/antweb/util/QueryAction.java")


def test_parentage_repair_previews_first():
    src = _src("src/org/calacademy/antweb/util/UtilDataAction.java")
    block = src[src.index('if (action.equals("fixGeolocaleTaxonParentage")) {'):]
    block = block[:block.index("geolocaleCountCrawl")]
    assert '"true".equals(request.getParameter("apply"))' in block
    assert "testGeolocaleTaxonParentage()" in block and "parentagePreviewHtml" in block


def test_status_links_use_the_action():
    """Taxon and specimen pages link the status help page through /statusDisplayPage.do (the JSP
    address stays on the allow-list for old links)."""
    for f in ("web/common/statusesDisplay.jsp", "web/common/taxonTitle.jsp", "web/common/statusDisplayCore.jsp"):
        text = _src(f)
        assert "/statusDisplayPage.do" in text and "%>/common/statusDisplayPage.jsp" not in text, f

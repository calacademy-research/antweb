"""Curate page cleanup (2026-10): removed tools stay removed, the jump boxes are gone, admin tools
refuse everyone else, and the taxon page by AntCat id still works.

Anonymous, read-only, any host:
- removed pages answer 404 (the Orphan, Type Status and Cache managers are in test_actions.py:
  test_removed_actions_stay_removed); removed sub-actions of list.do and query.do answer 404;
  the developer links page is not served;
- named queries are for administrators except the ones public pages link to; the two named
  queries that deleted rows are gone;
- admin tools (museum delete, statistics, site warning) ask for a login;
- description.do?antcatId=N opens a taxon page (the address stays; the box that built it is gone).
As the test curator, staging only, changing nothing: the same admin tools are refused; the removed
upload tools answer "Action not found."; the Curate page has no jump boxes, no Curious Query box and
no Refresh link. Static: the Curate page sources link none of the removed tools; developer rights
are the admin role (no login ids in the code).
Java checks in CI: test/org/calacademy/antweb/security/CurateCleanupTest.java.
"""
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, REPO_ROOT, TIMEOUT, classify, get, new_session
import test_page_editor as pe

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

# A taxon AntCat id that the uptime monitor also uses (AntCat taxon 429011).
ANTCAT_ID = 429011

REMOVED_URLS = [
    "/list.do?action=countries",
    "/list.do?action=bioregions",
    "/query.do?action=descEdit&field=notes",
]
# Named queries that are now for administrators (they read logins or are maintenance checks),
# and the two that deleted rows (removed; an unknown name is admin-only too).
ADMIN_QUERIES = [
    "/query.do?name=lastSpecimenUpload",
    "/query.do?action=curiousQuery&name=lastSpecimenUpload",
    "/query.do?action=curiousQuery&name=adminAlerts",
    "/query.do?name=geolocaleListMorphotaxa",
    "/query.do?name=deleteGeolocaleListMorphotaxa",
    "/query.do?name=deleteGeolocaleTaxaWithoutTaxon",
    "/query.do?action=queryBattery&name=x",
]
# Admin tools that change data: refused to anonymous visitors and to curators. None of them
# can act here: the museum code does not exist, and every one needs an administrator.
ADMIN_TOOLS = [
    "/manageMuseum.do?step=delete&code=E2ENOSUCHMUSEUM",
    "/viewMuseum.do?step=delete&code=E2ENOSUCHMUSEUM",
    "/utilData.do?action=siteWarning&text=e2e",
]
REMOVED_UPLOAD_ACTIONS = ["allSpecimenFiles", "GBIFUpload", "reloadSpeciesLists", "toggleDownTime", "genAll"]

CURATE_SOURCES = ("web/curate/curate-body.jsp", "web/curate/curatorLinks.jsp")
REMOVED_LINKS = (
    "/orphanMgr.do", "/typeStatus.do", "/cache.do", "list.do?action=countries", "list.do?action=bioregions",
    "action=materializeImages", "action=updateAdvancedSearch", "action=updateSpecimenStatus",
    "action=populateAll", "action=allSpecimenFiles", "GBIFUpload", "ancFileDirectory", "downloadSpeciesList",
    "action=curiousQuery\"", "Curious Query", "Access direct by", "action=descEdit", "/documentation/links.jsp",
    "/uptime.do", "adm1.do?%20(Terr.%20Amazonas)", "action=crawlForType", "action=populateMuseum",
    "action=museumTaxonCountCrawl", "action=populateBioregion", "action=geolocaleCountCrawl",
    "action=geolocaleTaxonCountCrawl", "action=updateTaxonSets", "action=genObjectMaps",
    "action=updateGroupCounts", "action=genGroupObjectMaps", "action=taxonFinish", "action=imageCountCrawl",
    "action=projTaxonChildCountCrawl", "action=projectCountCrawl", "action=runCountCrawls",
    "Create a New Ancillary Page", "Warning: Live Site", "For Developers", ">Refresh<",
)


# ---------- anonymous ----------

@pytest.mark.parametrize("url", REMOVED_URLS)
def test_removed_sub_action_is_not_found(url):
    outcome = get(new_session(), url)
    assert outcome.status == 404 and outcome.category == "not_found", f"{url}: {outcome}"


def test_developer_links_page_is_gone():
    r = new_session().get(BASE_URL + "/documentation/links.jsp", timeout=TIMEOUT)
    assert r.status_code == 404, r.status_code


@pytest.mark.parametrize("url", ADMIN_QUERIES)
def test_admin_named_query_anonymous(url):
    outcome = get(new_session(), url)
    assert outcome.category == "login", f"{url}: {outcome}"


def test_public_named_query_still_public():
    """The AntCat import history, linked from the public upload reports, stays public."""
    r = new_session().get(BASE_URL + "/query.do?name=worldantsUploads", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category not in ("login", "not_found"), classify(r)
    assert "worldantsUploads" in r.text, r.text[:300]


@pytest.mark.parametrize("url", ADMIN_TOOLS + ["/upload.do?action=runStatistics", "/upload.do?action=genRecentDescEdits"])
def test_admin_tool_anonymous(url):
    outcome = get(new_session(), url)
    assert outcome.category == "login", f"{url}: {outcome}"


@pytest.mark.parametrize("action", REMOVED_UPLOAD_ACTIONS)
def test_removed_upload_action_anonymous(action):
    outcome = get(new_session(), f"/upload.do?action={action}")
    assert outcome.category == "login", f"{action}: {outcome}"


def test_taxon_page_by_antcat_id():
    """description.do?antcatId=N opens the taxon page (documented in docs/guides/curate-page.md)."""
    r = new_session().get(f"{BASE_URL}/description.do?antcatId={ANTCAT_ID}", timeout=TIMEOUT)
    outcome = classify(r)
    assert r.status_code == 200 and outcome.category == "ok", outcome
    assert "Taxon name not found for antcatId" not in r.text, r.text[:300]
    assert "not in the AntWeb database" not in r.text, r.text[:300]


# ---------- static ----------

def _text(path):
    return re.sub(r"<!--.*?-->", "", (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace"), flags=re.S)


def test_curate_sources_link_no_removed_tool():
    found = [f"{p}: {x}" for p in CURATE_SOURCES for x in REMOVED_LINKS if x in _text(p)]
    assert not found, found


def test_developer_rights_are_the_admin_role():
    """No developer right is tied to a login id (Mark 22, the test login 50)."""
    bad = []
    for top in ("src", "web"):
        for path in (REPO_ROOT / top).rglob("*"):
            if path.suffix not in (".java", ".jsp") or not path.is_file():
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for pat in (r"\bisMark\(", r"Login\.MARK\b", r"Login\.TEST_LOGIN\b", r"getId\(\)\s*==\s*(22|50)\b"):
                for line in text.splitlines():
                    if re.search(pat, line) and not line.strip().startswith("//"):
                        bad.append(f"{path.relative_to(REPO_ROOT)}: {line.strip()}")
    assert not bad, bad


def test_upload_action_runs_nothing_on_get():
    """upload.do: a GET runs nothing; the two admin jobs linked from the Curate page get a confirmation page."""
    src = (REPO_ROOT / "src/org/calacademy/antweb/upload/UploadAction.java").read_text()
    assert 'CONFIRM_ON_GET = java.util.Set.of("runStatistics", "genRecentDescEdits")' in src
    assert "PostOnly.confirm(request, mapping" in src
    assert '"runStatistics".equals(action)\n' not in src.replace("\r\n", "\n")


def test_named_queries_do_not_write():
    src = (REPO_ROOT / "src/org/calacademy/antweb/util/QueryManager.java").read_text()
    assert "executeUpdate" not in src


def test_site_warning_is_sanitized():
    panel = (REPO_ROOT / "web/common/loginPanel.jsp").read_text()
    assert "HtmlSanitizer.sanitize(siteWarning)" in panel
    assert "<%= siteWarning %>" not in panel
    links = _text("web/curate/curatorLinks.jsp")
    assert "text=<%= content %>" not in links


# ---------- as the test curator (staging) ----------

@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


@staging_only
@pytest.mark.parametrize("url", ADMIN_QUERIES + ADMIN_TOOLS)
def test_admin_tools_refused_for_curator(curator, url):
    r = curator.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (url, classify(r))


@staging_only
def test_upload_admin_jobs_refused_for_curator(curator):
    for action in ("runStatistics", "genRecentDescEdits"):
        r = curator.get(f"{BASE_URL}/upload.do?action={action}", timeout=TIMEOUT)
        assert r.status_code == 200 and "Only administrators can run this upload action." in r.text, (action, r.text[:300])


@staging_only
@pytest.mark.parametrize("action", REMOVED_UPLOAD_ACTIONS)
def test_removed_upload_action_not_found(curator, action):
    """GET and POST (with the form token): "Action not found.", nothing runs."""
    r = curator.get(f"{BASE_URL}/upload.do?action={action}", timeout=TIMEOUT)
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]
    token = curator.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]
    r = curator.post(f"{BASE_URL}/upload.do?_csrf={token}",
                     files={"action": (None, action), "_csrf": (None, token)}, timeout=TIMEOUT)
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]


@staging_only
def test_curate_page_has_no_boxes(curator):
    r = curator.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    html = re.sub(r"<!--.*?-->", "", r.text, flags=re.S)
    for gone in ("Access direct by", "Curious Query", 'name="antcatId"', 'name="taxonName"',
                 "action=curiousQuery", ">Refresh</a>"):
        assert gone not in html, f"{gone} still on curate.do"
    # The page itself still renders (link renamed "Specimen upload reports" on 2026-10-20).
    assert "listSpecimenUploads.do" in html and "Specimen upload reports" in html

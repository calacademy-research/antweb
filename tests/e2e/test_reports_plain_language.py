"""Admin report pages explained in plain language (2026-10-19).

- Specimen parts (-D codes) with different names (list.do?action=casentDAnomalies): one query, a code
  counts as a part only when it ends in -d and digits, each base specimen listed once with its parts.
  The suffix rule is unit-tested in test/.../security/ReportsTest.java (CI).
- Homonym Report (query.do?action=homonyms): links carry the author and year; AntCat links https.
- Curate AntCat Queries (query.do?action=curateAntcat) removed: its three useful checks are in the
  Integrity Check (query.do?action=checkIntegrity); the AntCat import history keeps its page with the
  last 60 runs.
- Each page starts with "What this shows / Why it matters / What to do with a hit".

Anonymous (any host, read-only): admin pages ask for a login, curateAntcat is gone, the AntCat import
history is public, explained and at most 60 rows. Test curator (staging): the admin pages are refused.
Administrator (only when ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD are set; staging only):
each page loads with its explanation. Static checks of the code.
"""
import os
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, REPO_ROOT, TIMEOUT, classify, get, new_session, post_login
import test_page_editor as pe

ADMIN_PAGES = ["/list.do?action=casentDAnomalies", "/query.do?action=homonyms", "/query.do?action=checkIntegrity"]
HELP = "What this shows:"

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")
ADMIN_USER = os.environ.get("ANTWEB_TEST_ADMIN_USER")
ADMIN_PASSWORD = os.environ.get("ANTWEB_TEST_ADMIN_PASSWORD")
admin_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not ADMIN_USER or not ADMIN_PASSWORD,
    reason="needs ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD (staging only)")


def _text(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


# ---------- static ----------

def test_curate_antcat_removed_and_checks_moved():
    queries = _text("src/org/calacademy/antweb/util/Queries.java")
    integrity = queries[queries.index("getIntegrityNames()"):queries.index("getIntegrityQueries()")]
    for name in ("badParentTaxonName", "unrecognizedWithSynonymParent", "geolocaleTaxaNotUsingCurrentValidName"):
        assert f'"{name}"' in integrity, name
    assert '"unrecognizedTaxaWithoutSpecimen"' not in integrity
    assert "getCurateAntcatNames" not in queries
    assert "curateAntcat" not in re.sub(r"//.*", "", _text("src/org/calacademy/antweb/util/QueryAction.java"))
    # web/util/queries-body.jsp (the older query list) was removed on 2026-10-21.
    for page in ("web/curate/curatorLinks.jsp", "web/curate/curate-body.jsp"):
        assert "curateAntcat" not in _text(page), page
    assert "order by created desc limit 60" in queries


def test_integrity_sections_say_none_and_catch_errors():
    qm = _text("src/org/calacademy/antweb/util/QueryManager.java")
    assert "checkSection(namedQuery, connection)" in qm and "None." in qm and "This check could not run" in qm


def test_parts_query_is_one_bound_query():
    db = _text("src/org/calacademy/antweb/home/SpecimenDb.java")
    assert "getCasentDAnamalies" not in db and "isDuplicatedTaxonName" not in db
    assert "regexp ?" in db and "prepareStatement(query)" in db


def test_homonym_links():
    jsp = _text("web/util/homonyms.jsp")
    assert "&authorDate=" in jsp and "https://www.antcat.org/catalog/" in jsp and "http://www.antcat.org" not in jsp
    assert "ReportHelp.HOMONYMS" in jsp and "No homonyms" in jsp


# ---------- anonymous ----------

@pytest.mark.parametrize("url", ADMIN_PAGES)
def test_admin_report_needs_login(url):
    assert get(new_session(), url).category == "login", url


def test_curate_antcat_action_is_gone():
    r = new_session().get(BASE_URL + "/query.do?action=curateAntcat", timeout=TIMEOUT)
    assert r.status_code == 404, r.status_code


def test_antcat_import_history_explained_and_short():
    r = new_session().get(BASE_URL + "/query.do?name=worldantsUploads", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    assert HELP in r.text
    assert r.text.count("Log file</a>") <= 60


# ---------- test curator ----------

@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


@staging_only
@pytest.mark.parametrize("url", ADMIN_PAGES)
def test_admin_report_refused_for_curator(curator, url):
    r = curator.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (url, classify(r))
    assert HELP not in r.text


# ---------- administrator ----------

@pytest.fixture(scope="module")
def admin():
    s = new_session()
    post_login(s, ADMIN_USER, ADMIN_PASSWORD)
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("admin test login failed")
    return s


@admin_only
@pytest.mark.parametrize("url", ADMIN_PAGES)
def test_admin_report_loads_with_explanation(admin, url):
    r = admin.get(BASE_URL + url, timeout=max(TIMEOUT, 300))
    assert r.status_code == 200 and classify(r).category == "ok", (url, classify(r))
    assert HELP in r.text and "What to do with a hit:" in r.text, url

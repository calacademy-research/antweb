"""Specimen parts (-D codes) with different names, per group (2026-10-19).

list.do?action=casentDAnomalies shows, for every record, the group that uploaded it, the uploaded name, the
determination and the museum code. ?groupId=N limits the page to the specimens with a record from that
group (a specimen whose records come from several groups is listed under each, rows of other groups marked);
&format=csv downloads the current list. Grouping and CSV are unit-tested in
test/.../security/ReportsTest.java (CI).

Anonymous (any host, read-only): the filtered page and the CSV ask for a login. Test curator (staging): both
are refused. Administrator (only with ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD; staging only): the
CAS (group 1) page loads with the filter and the download link; the CSV is text/csv with the header line.
"""
import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, get, new_session
from test_reports_plain_language import admin, admin_only, curator, staging_only  # noqa: F401 (fixtures)

PAGE = "/list.do?action=casentDAnomalies&groupId=1"
CSV = "/list.do?action=casentDAnomalies&groupId=1&format=csv"
CSV_HEADER = ("group,specimen code,part code,taxon name,determined by,date determined,"
              "uploaded name,uploaded by,this group can fix,museum,specimen page")


def _text(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


# ---------- static ----------

def test_query_carries_group_and_determination():
    db = _text("src/org/calacademy/antweb/home/SpecimenDb.java")
    q = db[db.index("getPartsWithDifferentNames()"):]
    q = q[:q.index("return new ArrayList<>(groups.values());")]
    for col in ("access_group", "original_taxon_name", "determinedby", "datedetermined", "museum", "left join ant_group g"):
        assert col in q, col
    assert "prepareStatement(query)" in q and "regexp ?" in q


def test_csv_served_only_after_admin_check():
    la = _text("src/org/calacademy/antweb/util/ListAction.java")
    assert la.index("Check.admin(request, mapping)") < la.index('"text/csv; charset=UTF-8"')
    assert "PartsReport.parseGroupId(request.getParameter(\"groupId\"))" in la
    assert _text("src/org/calacademy/antweb/util/PartsReport.java").count("CSV_HEADER = ") == 1
    assert CSV_HEADER.split(",")[0] in _text("src/org/calacademy/antweb/util/PartsReport.java")
    assert "Use the group filter to get the list for one institution" in _text("src/org/calacademy/antweb/util/ReportHelp.java")


# ---------- anonymous ----------

@pytest.mark.parametrize("url", [PAGE, CSV])
def test_group_page_and_csv_need_login(url):
    r = get(new_session(), url)
    assert r.category == "login", (url, r)


# ---------- test curator ----------

@staging_only
@pytest.mark.parametrize("url", [PAGE, CSV])
def test_group_page_and_csv_refused_for_curator(curator, url):  # noqa: F811
    r = curator.get(BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (url, classify(r))
    assert "text/csv" not in r.headers.get("Content-Type", "")
    assert "Download this list (CSV)" not in r.text


# ---------- administrator ----------

@admin_only
def test_group_page_loads_for_admin(admin):  # noqa: F811
    r = admin.get(BASE_URL + PAGE, timeout=max(TIMEOUT, 300))
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    assert "What this shows:" in r.text and "Use the group filter" in r.text
    assert 'name="groupId"' in r.text
    if "None: every specimen" not in r.text:
        assert "Download this list (CSV)" in r.text
        assert 'value="1" selected' in r.text or "No specimens for this group." in r.text


@admin_only
def test_csv_download_for_admin(admin):  # noqa: F811
    r = admin.get(BASE_URL + CSV, timeout=max(TIMEOUT, 300))
    assert r.status_code == 200, r.status_code
    assert r.headers.get("Content-Type", "").startswith("text/csv"), r.headers.get("Content-Type")
    assert "attachment" in r.headers.get("Content-Disposition", "")
    assert r.text.splitlines()[0] == CSV_HEADER

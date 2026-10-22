"""Tool pages in the shared modern look (Brian Fisher, 2026-10-22: "a good style to use for all the 'report'
and 'form' pages ... I look forward to see all the pages like the Technical page and the data job pages in
the new modern look").

A tool page includes web/common/toolsHead.jsp (common/tools.css and common/tools.js) and wraps its markup in
<div class="aw-tools">; every rule of tools.css is scoped to .aw-tools (tests/e2e/test_curate_page_layout.py
checks that). Public layouts and public pages never load it. The shared message bodies take the look only
inside the admin layout. Style guide: docs/guides/page-style.md.

Also (same day, Brian's screenshot of data job 186): a nightly set started by hand stored the whole page
utilData.do answered with as its job result, and the job page showed a second AntWeb page (header, search,
footer) inside itself. The job now keeps a plain sentence, and the page never embeds a whole page
(src/org/calacademy/antweb/util/JobSummary.java; unit test test/.../util/JobSummaryTest.java).

Static checks run anywhere; the page checks are read-only GETs with the staging admin test login (paced
post_login helper).
"""
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, new_session
from staging_accounts import login, needs

PAGE_TIMEOUT = 120
CENTERED = re.compile(r"""align\s*=\s*["']?center|text-align\s*:\s*center""", re.I)
OLD_LAYOUT = ('class="admin_left"', "class=left>", 'class="left"', 'class="wide_left"', "cellpadding=10")

# Converted body JSPs: (file, wrapper id).
CONVERTED = [
    ("web/curate/technical-body.jsp", "technical-page"),
    ("web/curate/jobStatus-body.jsp", "job-status-page"),
    ("web/util/serverStatus-body.jsp", "server-status-page"),
    ("web/util/countCheck-body.jsp", "count-check-page"),
    ("web/util/logMgr-body.jsp", "log-manager-page"),
    ("web/curate/nameUpdates-body.jsp", "name-updates-page"),
    ("web/util/listSpecimenUploads-body.jsp", "specimen-uploads-page"),
    ("web/imageUploader/listImageUploads-body.jsp", "image-uploads-page"),
    ("web/curate/adminHtmlMessage-body.jsp", "admin-result-page"),
]
# Bodies shared with the public layout: tool look only inside the admin layout.
SHARED_BODIES = ["web/common/message-body.jsp", "web/common/unboldMessage-body.jsp"]
INCLUDE = '<%@include file="/common/toolsHead.jsp" %>'


def _src(path):
    return re.sub(r"<%--.*?--%>|<!--.*?-->", "", (REPO_ROOT / path).read_text(encoding="utf-8"), flags=re.S)


# ---------- static ----------

def test_source_converted_pages_opt_in():
    for path, wrapper in CONVERTED:
        src = _src(path)
        assert INCLUDE in src, f"{path}: does not include toolsHead.jsp"
        assert f'<div class="aw-tools" id="{wrapper}">' in src, f"{path}: no div.aw-tools#{wrapper}"
        found = [x for x in OLD_LAYOUT if x in src]
        assert not found, f"{path}: old layout markup {found}"
        assert not CENTERED.search(src), f"{path}: {CENTERED.search(src).group(0)}"


def test_source_only_tool_pages_load_the_stylesheet():
    """No public layout or public page includes tools.css or toolsHead.jsp; the set of pages that do is known."""
    users = sorted(str(p.relative_to(REPO_ROOT)) for p in (REPO_ROOT / "web").rglob("*.jsp")
                   if p.name != "toolsHead.jsp" and re.search(r"toolsHead\.jsp|/common/tools\.(css|js)",
                                                            _src(str(p.relative_to(REPO_ROOT)))))
    expected = sorted([p for p, _ in CONVERTED] + SHARED_BODIES + ["web/curate/curate-body.jsp"])
    assert users == expected, users
    for layout in ("web/layouts/layout.jsp", "web/layouts/basicLayout.jsp", "web/layouts/secureLayout.jsp",
                   "web/layouts/admLayout.jsp", "web/common/antweb-defs.jsp", "web/common/academyHeader.jsp",
                   "web/common/siteNav.jsp", "web/common/copyright.jsp"):
        if (REPO_ROOT / layout).exists():
            assert "tools.css" not in _src(layout) and "toolsHead" not in _src(layout), layout


def test_source_shared_message_bodies_only_in_admin_layout():
    assert 'request.setAttribute("awAdminLayout", Boolean.TRUE)' in _src("web/layouts/admLayout.jsp")
    for path in SHARED_BODIES:
        src = _src(path)
        cond = src.index('Boolean.TRUE.equals(request.getAttribute("awAdminLayout"))')
        assert cond < src.index(INCLUDE) < src.index("<% } else { %>"), path
        # The public branch keeps the old markup.
        assert '<div class="wide_left">' in src.split("<% } else { %>", 1)[1], path


def test_source_admin_html_message_keeps_public_view():
    src = _src("web/curate/adminHtmlMessage.jsp")
    assert "LoginMgr.getAccessLogin(request) != null" in src
    assert '<tiles:put name="body-content" value="/curate/adminHtmlMessage-body.jsp" />' in src
    assert "<br><b><%= message %></b>" in src.split("} else {", 1)[1]


def test_source_tools_js_and_css():
    js = (REPO_ROOT / "web/common/tools.js").read_text(encoding="utf-8")
    assert 'querySelectorAll(".aw-tools table")' in js and "aw-table-wrap" in js and "aw-num" in js
    css = (REPO_ROOT / "web/common/tools.css").read_text(encoding="utf-8")
    for rule in (".aw-tools .aw-card", ".aw-tools .aw-table-wrap", ".aw-tools th.aw-num", ".aw-tools table.aw-kv",
                 ".aw-tools .aw-crumbs", ".aw-tools .aw-intro"):
        assert rule in css, rule
    assert re.search(r"\.aw-tools \.aw-card \{[^}]*overflow-x:\s*auto", css), "a wide table must scroll inside its card"


def test_source_job_result_is_plain_text():
    sched = _src("src/org/calacademy/antweb/util/Scheduler.java")
    assert "Outcome.ok(Html.esc(JobSummary.scheduler(message)))" in sched
    assert "JobSummary.display(result)" in _src("src/org/calacademy/antweb/util/JobStatusAction.java")
    assert "JobSummary.dataJob(operationDetails.getMessage())" in _src("src/org/calacademy/antweb/util/UtilDataAction.java")


# ---------- staging, admin ----------

@pytest.fixture(scope="module")
def admin():
    s, _token, _id = login("admin")
    return s


def _tool_part(html, wrapper):
    marker = f'<div class="aw-tools" id="{wrapper}">'
    assert marker in html, f"no {marker}"
    assert "/common/tools.css?v=" in html, "tools.css not loaded"
    return html.split(marker, 1)[1]


PAGES = [
    ("/technical.do", "technical-page",
     ["/schedule.do?action=run&num=1", "/util.do?action=reloadAntwebMgr&name=group", "/serverDebug.do", "<h1>Technical page</h1>"]),
    ("/jobStatus.do", "job-status-page", ["<h1>Upload status</h1>", 'class="aw-crumbs"']),
    ("/serverStatus.do", "server-status-page",
     ["<h1>Server Status</h1>", "toggleDownTime", 'name="_csrf"', "/dbStatus.do", "Page Tracker"]),
    ("/countCheck.do?job=0", "count-check-page", ["<h1>Check the counts</h1>", 'id="count-check-run"']),
    ("/logMgr.do?action=list", "log-manager-page",
     ["<h1>Log Manager</h1>", "Primary Logs", "fileName=compute&ext=log", "fileName=taxonworksNightly&ext=log"]),
    ("/nameUpdates.do", "name-updates-page", ["What this shows:", 'name="from"', "Preview now (report only)"]),
    ("/listSpecimenUploads.do", "specimen-uploads-page", ["<th>Group</th>", 'class="aw-card"']),
    ("/listImageUploads.do", "image-uploads-page", ["Total image uploads:", "<th>Created</th>"]),
    ("/query.do?name=worldantsUploads", "admin-result-page", ["<h1>Query: worldantsUploads</h1>", 'class="query-check"']),
]


@needs("admin")
@pytest.mark.parametrize("path,wrapper,keep", PAGES, ids=[p[0] for p in PAGES])
def test_tool_page_loads_in_new_look(admin, path, wrapper, keep):
    r = admin.get(BASE_URL + path, timeout=PAGE_TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", (path, classify(r))
    part = _tool_part(r.text, wrapper)
    # The page's own markup is not centred (the server's own messages excepted).
    own = re.sub(r"<pre>.*?</pre>", "", part, flags=re.S)
    assert not re.search(r'<div align="?center', own, re.I), path
    for k in keep:
        assert k in r.text, f"{path}: '{k}' missing"


@needs("admin")
def test_finished_data_job_has_no_second_site_page(admin):
    """A finished data job's page shows its result as text, never a whole AntWeb page (header, footer)."""
    listing = admin.get(BASE_URL + "/jobStatus.do", timeout=PAGE_TIMEOUT).text
    ids = []
    for row in listing.split("<tr>"):
        m = re.search(r'jobStatus\.do\?id=(\d+)">', row)
        if m and "Nightly" in row and "aw-status-finished" in row:
            ids.append(m.group(1))
    if not ids:
        pytest.skip("no finished nightly set started by hand on the Upload status list")
    page = admin.get(BASE_URL + f"/jobStatus.do?id={ids[0]}", timeout=PAGE_TIMEOUT).text
    assert 'data-status="finished"' in page
    assert page.count("Cite AntWeb") == 0, "the site footer is inside the job page"
    assert page.lower().count("<html") <= 1 and page.lower().count("<body") <= 1, "a second page inside the job page"
    assert "{operation:" not in page, "operations not in plain words"
    result = re.search(r'<div id="job-result"[^>]*>(.*?)</div>', page, re.S)
    if result:
        assert "<form" not in result.group(1) and "<script" not in result.group(1), result.group(1)[:300]


@needs("admin")
def test_public_message_page_unchanged(admin):
    """A message in the public layout (jobStatus.do of a job that does not exist) keeps its old look."""
    r = admin.get(BASE_URL + "/jobStatus.do?id=999999999", timeout=PAGE_TIMEOUT)
    assert "Not available." in r.text
    assert 'class="aw-tools"' not in r.text and "/common/tools.css" not in r.text


def test_public_pages_do_not_load_tools_css():
    s = new_session()
    for path in ("/", "/about.do", "/country.do?name=Madagascar"):
        r = s.get(BASE_URL + path, timeout=PAGE_TIMEOUT)
        if r.status_code != 200:
            continue
        assert "/common/tools.css" not in r.text and 'class="aw-tools"' not in r.text, path

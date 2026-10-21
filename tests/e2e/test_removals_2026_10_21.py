"""Manager Tools removals and the Add an uploader draft email (2026-10-21, Brian's decisions).

Removed: the older Query Manager list (queries.do; its 404 is in test_actions.py::
test_removed_actions_stay_removed), upload.do's createProject step, Edit Geolocale's "Fast" option, the
Bioregion Map Manager's "Refresh" (its request check is test_request_handling.py::
test_bioregion_map_refresh_is_gone) and the "groups" named query of the Upload groups page
(test_relevant_queries.py). The museum Title was kept: it is the heading of the public museum pages.

Add an uploader no longer sends the welcome email: the summary page shows a draft (To, Subject, the
text in a read-only box, a Copy button and a mailto: link) for the administrator to send from their own
email, signed "[Your name]".

Static (any host): the removed files, fields and links are gone; Add an uploader has no send path.
Test uploader (staging, changing nothing): a POST to upload.do with createProject ends as "Action not found.".
Test admin (staging): Add an uploader for the test uploader in its own test upload group (nothing to
change: already in the group with its rights; written to admin.log) shows the draft box and no Send
button; a POST with step=send sends nothing.
Java checks in CI: test/org/calacademy/antweb/curate/UploaderOnboardingTest.java (text and draft box),
test/org/calacademy/antweb/util/RelevantQueriesTest.java.
"""
import os
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify
from staging_accounts import ACCOUNTS, IS_STAGING, has, login, needs, require_test_group

REMOVED_FILES = ["web/util/queries.jsp", "web/util/queries-body.jsp"]

# Text that must not appear in the sources any more (file, text).
GONE = [
    ("WEB-INF/struts-config.xml", 'path="/queries"'),
    ("src/org/calacademy/antweb/util/Queries.java", "/queries.do"),
    ("src/org/calacademy/antweb/util/Queries.java", '"groups"'),
    ("src/org/calacademy/antweb/upload/UploadAction.java", "getCreateProject"),
    ("src/org/calacademy/antweb/upload/UploadForm.java", "createProject"),
    ("src/org/calacademy/antweb/curate/geolocale/EditGeolocaleForm.java", "isFast"),
    ("src/org/calacademy/antweb/curate/geolocale/EditGeolocaleAction.java", "getIsFast"),
    ("web/curate/geolocale/editGeolocale-body.jsp", 'name="isFast"'),
    ("src/org/calacademy/antweb/home/TaxonPropDb.java", "public void refreshBioregionMap()"),
    ("src/org/calacademy/antweb/curate/BioregionMapMgrAction.java", "refreshBioregionMap"),
    ("web/curate/bioregionMapMgr-body.jsp", "isRefresh=true"),
    ("src/org/calacademy/antweb/curate/group/AddUploaderAction.java", "Emailer"),
    ("src/org/calacademy/antweb/curate/group/AddUploaderAction.java", '"send".equals(step)'),
    ("src/org/calacademy/antweb/curate/group/AddUploaderAction.java", "Send this email to"),
    ("src/org/calacademy/antweb/curate/group/UploaderOnboarding.java", "reply to this email"),
]


def _text(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


# ---------- static ----------

@pytest.mark.parametrize("path", REMOVED_FILES)
def test_removed_file_is_gone(path):
    assert not (REPO_ROOT / path).exists(), path


@pytest.mark.parametrize("path,text", GONE)
def test_removed_code_is_gone(path, text):
    assert text not in _text(path), f"{path}: {text}"


def test_no_page_links_queries_do():
    hits = []
    for top in ("web", "src", "WEB-INF"):
        for d, _, files in os.walk(REPO_ROOT / top):
            if os.sep + "editors" + os.sep in d + os.sep:
                continue
            for f in files:
                if f.endswith((".jsp", ".java", ".xml", ".js", ".html")):
                    p = os.path.join(d, f)
                    if re.search(r"/queries\.do\b", open(p, encoding="utf-8", errors="replace").read()):
                        hits.append(os.path.relpath(p, REPO_ROOT))
    assert not hits, hits


def test_welcome_text_is_a_draft_signed_by_the_admin():
    src = _text("src/org/calacademy/antweb/curate/group/UploaderOnboarding.java")
    assert 'SIGNATURE_PLACEHOLDER = "[Your name]"' in src
    assert "This link is for you only" in src
    page = _text("src/org/calacademy/antweb/curate/group/AddUploaderAction.java")
    for text in ("<h2>Draft email</h2>", "navigator.clipboard", "Open in my email program", "mailtoUrl("):
        assert text in page, text


# ---------- test curator (staging) ----------

@needs("uploader")
def test_create_project_step_is_gone():
    """A POST with the form token, as the old step needed (upload.do runs nothing on a GET)."""
    s, token, _id = login("uploader")
    r = s.post(f"{BASE_URL}/upload.do?_csrf={token}", timeout=TIMEOUT,
               files={"createProject": (None, "zze2etestants"), "_csrf": (None, token)})
    assert r.status_code == 200 and classify(r).category not in ("error_page", "server_error", "stack_trace"), classify(r)
    assert "To create project" not in r.text, r.text[:300]
    if "currently in an Upload Process" in r.text:
        pytest.skip("an upload is running on this server")
    assert "Action not found." in r.text, r.text[:300]


# ---------- test admin (staging) ----------

@pytest.fixture(scope="module")
def summary():
    """The Add an uploader summary page for the test uploader, in its own test upload group."""
    if not (IS_STAGING and has("admin") and has("uploader")):
        pytest.skip("needs the admin and uploader test logins (staging only)")
    up, _t, _id = login("uploader")
    gid = require_test_group(up)   # refuses anything but the test group
    s, token, _aid = login("admin")
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": token, "step": "save", "email": ACCOUNTS["uploader"][0],
                     "groupId": "existing", "existingGroupId": str(gid)})
    assert classify(r).category == "ok", classify(r)
    assert "Add an uploader: done" in r.text, r.text[:500]
    return s, token, r.text


def test_summary_shows_the_draft_box(summary):
    _s, _token, html = summary
    assert "was already in this upload group" in html
    for text in ("<h2>Draft email</h2>", 'id="draft-to" readonly', 'id="draft-subject" readonly',
                 'value="Your AntWeb upload account"', '<textarea id="draft-body" readonly',
                 'onclick="awCopyDraft(this)">Copy</button>', "AntWeb does not send this email", "[Your name]"):
        assert text in html, f"'{text}' not on the summary page"
    if "This login has no email address." not in html:
        assert 'id="draft-mailto" href="mailto:' in html and ">Open in my email program</a>" in html
    assert "Send this email to" not in html and 'name="step" value="send"' not in html
    assert "reply to this email" not in html.lower()


def test_send_step_sends_nothing(summary):
    s, token, _html = summary
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT, data={"_csrf": token, "step": "send"})
    assert classify(r).category == "ok", classify(r)
    assert "Sent to " not in r.text and "Not sent:" not in r.text

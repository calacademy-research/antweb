"""Manager Tools removals and the Add an uploader draft email (2026-10-21, Brian's decisions).

Removed: the older Query Manager list (queries.do; its 404 is in test_actions.py::
test_removed_actions_stay_removed), upload.do's createProject step, Edit Geolocale's "Fast" option, the
Bioregion Map Manager's "Refresh" (its request check is test_request_handling.py::
test_bioregion_map_refresh_is_gone) and the "groups" named query of the Upload groups page
(test_relevant_queries.py). The museum Title was kept: it is the heading of the public museum pages.

Add an uploader's welcome email: from 2026-10-21 a draft to copy; since 2026-10-29 (Brian) To, Subject
and text are editable with a Send button (from antweb@antweb.org, Reply-To and a copy to the
administrator, only to the address of the login just added) and Copy, signed with the administrator's
name. The mailto: link ("Open in my email program") was dropped: it did nothing with the long text.

Static (any host): the removed files, fields and links are gone, the mailto: link too.
Test uploader (staging, changing nothing): a POST to upload.do with createProject ends as "Action not found.".
Test admin (staging): Add an uploader for the test uploader in its own test upload group (nothing to
change: already in the group with its rights; written to admin.log) shows the editable welcome email
with Send and Copy; Sends that must be refused (another login number, another To address, a stale
form token) send nothing. No test presses a Send that would go out.
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
    ("src/org/calacademy/antweb/curate/group/AddUploaderAction.java", "mailtoUrl("),
    ("src/org/calacademy/antweb/curate/group/AddUploaderAction.java", "Open in my email program"),
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


def test_welcome_text_is_signed_by_the_admin():
    src = _text("src/org/calacademy/antweb/curate/group/UploaderOnboarding.java")
    assert 'SIGNATURE_PLACEHOLDER = "[Your name]"' in src
    assert "This link is for you only" in src
    page = _text("src/org/calacademy/antweb/curate/group/AddUploaderAction.java")
    for text in ("<h2>Welcome email</h2>", "navigator.clipboard", "Emailer.sendPersonal(",
                 "UploaderOnboarding.signature(admin.getFirstName(), admin.getLastName())"):
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


LOGIN_ID = re.compile(r'name="loginId" value="(\d+)"')
DRAFT_TO = re.compile(r'id="draft-to" name="to" size="50" maxlength="128" value="([^"]*)"')


def test_summary_shows_the_welcome_email(summary):
    _s, _token, html = summary
    assert "was already in this upload group" in html
    if "This login has no email address." in html:
        pytest.skip("the test uploader has no email address on this server")
    for text in ("<h2>Welcome email</h2>", 'id="draft-to" name="to" size=', 'id="draft-subject" name="subject" size=',
                 'value="Your AntWeb upload account"', '<textarea id="draft-body" name="body" rows=',
                 '<input type="hidden" name="step" value="send">', 'name="_csrf"',
                 '<input type="submit" id="draft-send" value="Send">', 'onclick="awCopyDraft(this)">Copy</button>',
                 "replies go to you, and you get a copy", "Best regards,"):
        assert text in html, f"'{text}' not on the summary page"
    assert "mailto:" not in html and "Open in my email program" not in html
    assert "reply to this email" not in html.lower()


def _draft(summary):
    s, token, html = summary
    m, t = LOGIN_ID.search(html), DRAFT_TO.search(html)
    if not (m and t):
        pytest.skip("no sendable welcome email on the summary page")
    return s, token, int(m.group(1)), t.group(1)


def test_send_for_another_login_is_refused(summary):
    s, token, login_id, to = _draft(summary)
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": token, "step": "send", "loginId": str(login_id + 1), "to": to,
                     "subject": "Your AntWeb upload account", "body": "Hello"})
    assert classify(r).category == "ok", classify(r)
    assert "Not sent: this welcome email belongs to an earlier Add an uploader" in r.text
    assert "Sent to " not in r.text


def test_send_to_another_address_is_refused(summary):
    s, token, login_id, _to = _draft(summary)
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": token, "step": "send", "loginId": str(login_id), "to": "zz-not-the-uploader@example.org",
                     "subject": "Your AntWeb upload account", "body": "Hello"})
    assert classify(r).category == "ok", classify(r)
    assert "Not sent: the To address must be the email address of the login just added" in r.text
    assert "Sent to " not in r.text
    assert 'value="zz-not-the-uploader@example.org"' in r.text, "what was typed is shown again"


def test_send_with_stale_token_sends_nothing(summary):
    s, _token, login_id, to = _draft(summary)
    r = s.post(BASE_URL + "/addUploader.do", timeout=TIMEOUT,
               data={"_csrf": "A" * 43, "step": "send", "loginId": str(login_id), "to": to,
                     "subject": "Your AntWeb upload account", "body": "Hello zz-stale"})
    assert r.status_code == 200, (r.status_code, r.text[:300])
    assert "Nothing was sent" in r.text and "Sent to " not in r.text
    assert "Hello zz-stale" in r.text and '<input type="submit" id="draft-send"' not in r.text

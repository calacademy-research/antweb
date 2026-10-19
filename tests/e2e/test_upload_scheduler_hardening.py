"""Upload and scheduler hardening (2026-10-18, second batch).

As the test curator (not an administrator), staging only:
- upload.do deleteProject (species list deletion) is for administrators; the probe names a
  list that does not exist, so nothing could be deleted even where the change is missing.
- The removed upload.do tools answer "Action not found.": the developer sub-actions
  specimenTest and speciesTest, "Upload a Data File" (testFile) and the world authority file
  generation (successKey=worldAuthorityFiles).
- downloadSpeciesList is escaped in the message page.
- utilData.do data jobs refuse a scheduler code sent from outside, in the query string or in
  the request header (only AntWeb's own calls from inside the container are accepted).

Every probe that would do something on a server without the change is skipped until the
species list check answers (the deployed-change marker). Zip extraction, the scheduler check
and the image report rule are unit-tested in CI (test/.../security/UploadSchedulerHardeningTest.java);
the image report rule also in test_image_upload.py (test_report_of_other_group_refused).
"""
import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, classify, new_session
import test_page_editor as pe

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

ADMIN_ONLY = "Only an AntWeb administrator may delete a species list."
NO_SUCH_LIST = "e2enosuchlistants"


@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


def _token(s):
    return s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()["csrf"]


def _post_upload(s, fields, files=None):
    token = _token(s)
    parts = {k: (None, v) for k, v in fields.items()}
    parts["_csrf"] = (None, token)
    if files:
        parts.update(files)
    return s.post(f"{BASE_URL}/upload.do?_csrf={token}", files=parts, timeout=TIMEOUT)


@pytest.fixture(scope="module")
def deployed(curator):
    """The species list check is live (the marker for this batch); skip otherwise."""
    r = _post_upload(curator, {"deleteProject": NO_SUCH_LIST})
    assert r.status_code == 200, r.status_code
    if ADMIN_ONLY not in r.text:
        pytest.skip("the upload.do changes of this batch are not deployed on this server")
    return curator


@staging_only
def test_delete_species_list_admin_only(curator):
    r = _post_upload(curator, {"deleteProject": NO_SUCH_LIST})
    assert r.status_code == 200, r.status_code
    if "Species List deleted:" in r.text:
        pytest.skip("the change is not deployed on this server (a list that does not exist was named)")
    assert ADMIN_ONLY in r.text, r.text[:300]


@staging_only
@pytest.mark.parametrize("action", ["specimenTest", "speciesTest"])
def test_developer_upload_actions_removed(deployed, action):
    r = deployed.get(f"{BASE_URL}/upload.do?action={action}", timeout=TIMEOUT)
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]


@pytest.mark.parametrize("action", ["specimenTest", "speciesTest"])
def test_developer_upload_actions_anonymous(action):
    r = new_session().get(f"{BASE_URL}/upload.do?action={action}", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", (r.status_code, classify(r))


@staging_only
def test_upload_data_file_removed(deployed):
    r = _post_upload(deployed, {}, files={"testFile": ("e2e_probe.txt", b"e2e\n", "text/plain")})
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]


@staging_only
def test_world_authority_generation_removed(deployed):
    r = _post_upload(deployed, {"successKey": "worldAuthorityFiles", "homePageDirectory": "worldAuthorityFiles"})
    assert r.status_code == 200 and "Action not found." in r.text, r.text[:300]
    assert "Problems with extinct taxa" not in r.text


@staging_only
def test_download_species_list_escaped(deployed):
    r = _post_upload(deployed, {"downloadSpeciesList": 'e2e"><i id=e2e>x'})
    assert r.status_code == 200, r.status_code
    assert '<i id=e2e>' not in r.text, "downloadSpeciesList reached the page unescaped"
    assert "speciesListDownload.do?projectName=" in r.text, r.text[:300]


# ---------- utilData.do: the scheduler code is accepted only from inside the container ----------

@staging_only
@pytest.mark.parametrize("how", ["query", "header"])
def test_scheduler_code_from_outside_refused(how):
    """A code in the URL or in the header, sent from outside, does not start a data job
    (anonymous; nothing runs: the code is not the current one either)."""
    url = f"{BASE_URL}/utilData.do?action=siteWarning&param=allow"
    headers = {}
    if how == "query":
        url += "&secureCode=00000000000000000000000000000000"
    else:
        headers["X-AntWeb-Scheduler-Code"] = "00000000000000000000000000000000"
    r = new_session().get(url, headers=headers, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    assert classify(r).category in ("login", "denied", "message"), classify(r)

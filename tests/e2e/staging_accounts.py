"""The staging-only test accounts (tools/staging/create-test-accounts.py) for the e2e tests.

GitHub secrets (Settings > Secrets and variables > Actions), passed by .github/workflows/e2e.yaml
only when the target is staging:
  ANTWEB_TEST_UPLOADER_USER / _PASSWORD   curator of the test group, may upload specimens and images
  ANTWEB_TEST_NOUPLOAD_USER / _PASSWORD   curator of the test group, may upload nothing
  ANTWEB_TEST_ADMIN_USER / _PASSWORD      administrator (staging only)
Tests that need one skip when its secret is absent.

Safety: these credentials are sent only to staging.antweb.org or a local server (login() refuses
anything else, also when the workflow is run by hand against production). A specimen upload
replaces ALL specimens of the uploader's group, so before any upload the tests check that the
login's group is the test group (group_of()).
"""
import os
import re
import zipfile
from io import BytesIO
from urllib.parse import urlparse

import pytest

from antweb import BASE_URL, IS_PRODUCTION, TIMEOUT, new_session, post_login

STAGING_HOSTS = {"staging.antweb.org", "localhost", "127.0.0.1"}
HOST = urlparse(BASE_URL).hostname or ""
IS_STAGING = HOST in STAGING_HOSTS and not IS_PRODUCTION

GROUP_NAME = "ZZ Test Group (staging only)"
GROUP_ABBREV = "zztest"
CODE_PREFIX = "zztest"

ACCOUNTS = {
    "uploader": (os.environ.get("ANTWEB_TEST_UPLOADER_USER"), os.environ.get("ANTWEB_TEST_UPLOADER_PASSWORD")),
    "noupload": (os.environ.get("ANTWEB_TEST_NOUPLOAD_USER"), os.environ.get("ANTWEB_TEST_NOUPLOAD_PASSWORD")),
    "admin": (os.environ.get("ANTWEB_TEST_ADMIN_USER"), os.environ.get("ANTWEB_TEST_ADMIN_PASSWORD")),
}
SECRET = {"uploader": "ANTWEB_TEST_UPLOADER", "noupload": "ANTWEB_TEST_NOUPLOAD", "admin": "ANTWEB_TEST_ADMIN"}


def has(kind):
    user, password = ACCOUNTS[kind]
    return bool(user and password)


def needs(kind):
    """Skip unless the account's secrets are set and the target is staging."""
    return pytest.mark.skipif(
        not (IS_STAGING and has(kind)),
        reason=f"needs {SECRET[kind]}_USER / {SECRET[kind]}_PASSWORD (staging only; tools/staging/create-test-accounts.py)")


def assert_staging():
    if not IS_STAGING:
        raise RuntimeError(f"refusing to send test credentials to {HOST}: staging only")


def login(kind):
    """(session, csrf token, login id) for a test account. Never sends credentials outside staging."""
    assert_staging()
    user, password = ACCOUNTS[kind]
    s = new_session()
    post_login(s, user, password)
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    assert info.get("loggedIn"), f"the {kind} test login failed on {HOST}"
    return s, info.get("csrf"), info.get("loginId")


def group_of(session):
    """(group id, group name) of the logged-in curator, from curate.do and group.do."""
    page = session.get(BASE_URL + "/curate.do", timeout=TIMEOUT).text
    m = re.search(r"listSpecimenUploads\.do\?groupId=(\d+)", page)
    assert m, "curate.do shows no group for this login"
    gid = int(m.group(1))
    name = ""
    for url in (f"/group.do?id={gid}", f"/listSpecimenUploads.do?groupId={gid}"):
        text = session.get(BASE_URL + url, timeout=TIMEOUT).text
        if GROUP_NAME in text:
            name = GROUP_NAME
            break
    return gid, name


def require_test_group(session):
    """The group id of this login, after checking it is the test group (or fail: never upload elsewhere)."""
    gid, name = group_of(session)
    if name != GROUP_NAME:
        pytest.fail(f"the test login's group {gid} is not '{GROUP_NAME}': refusing to upload "
                    f"(an upload replaces all specimens of the group)")
    return gid


def code(n):
    return f"{CODE_PREFIX}{n:04d}"


def zip_of(name, text):
    buf = BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(name, text)
    return buf.getvalue()


BUSY = ("currently in an Upload Process", "currently in the process of an Upload", "re-computation process",
        "AntwebMgr not yet initialized", "down time", "Another data job is running")


def upload_specimens(session, token, text, file_name="zztest_specimens.txt", timeout=900):
    """POST a specimen file (zipped, as the Curate page sends it) to upload.do. Returns the response.
    Skips the test when the server is busy (another upload, the nightly run, a restart)."""
    assert_staging()
    files = {"theFile": (file_name.replace(".txt", ".zip"), zip_of(file_name, text), "application/zip")}
    r = session.post(BASE_URL + "/upload.do", headers={"X-CSRF-Token": token},
                     data={"action": "specimenUpload"}, files=files, timeout=timeout)
    if any(b in r.text for b in BUSY):
        pytest.skip("staging is busy: " + next(b for b in BUSY if b in r.text))
    return r


def report_url(upload_response):
    """The upload report linked from the "Successful Upload." page."""
    m = re.search(r"""href\s*=\s*["']([^"']*/web/log/specimen/[^"']+\.html)["']""", upload_response.text)
    if not m:
        m = re.search(r"""href\s*=\s*["']([^"']*/web/log/[^"']+\.html)["']""", upload_response.text)
    assert m, "no upload report link on the result page: " + re.sub(r"<[^>]+>", " ", upload_response.text)[:600]
    url = m.group(1)
    return url if url.startswith("http") else BASE_URL + url


def specimen_page(session, c):
    """(exists, page text) for specimen.do?code=c."""
    r = session.get(BASE_URL + "/specimen.do?code=" + c, timeout=TIMEOUT)
    assert r.status_code < 500, (c, r.status_code)
    return ("is not in the AntWeb database" not in r.text and "Specimen code not found" not in r.text), r.text


BASELINE_HEADER = "SpecimenCode\tSubfamily\t[Species]Genus\tSpeciesName\tCountry\tLocLatitude\tLocLongitude\tCollectedBy"


def baseline_text(subfamily, genus, species):
    """The test group's resting state: two specimens of one valid name (zztest0001, zztest0002)."""
    lines = [BASELINE_HEADER]
    for n in (1, 2):
        lines.append("\t".join([code(n), subfamily, genus, species, "Madagascar", "-18.91", "47.52", "AntWeb e2e test"]))
    return "\n".join(lines) + "\n"

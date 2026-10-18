"""Image Uploader, one file per request (imageUploadBatch.do). Staging only.

Why: through Cloudflare a request may carry at most 100 MB and must be answered within 100 s.
The old uploader sent every selected image in one request and made all image sizes before
answering, so curators could send only 4 or 5 TIFFs at a time. The page now sends one file per
request: step=start (check all names, create one upload record), step=add (one file),
step=finish (mark the upload complete). See ImageUploadBatchAction.

What is checked, over HTTP (no JavaScript):
- anonymous POSTs to start, add and finish are refused (401 JSON), and a GET is refused;
- as the test curator, start, add and finish without the form token, or with a wrong one, are
  refused (403 from SessionRequestFilter);
- naming rules on start (dryRun=1, nothing created): bad case, bad shot, bad file type, a
  folder path, odd characters and an unknown specimen are rejected with a reason; a good name
  for the test specimen is accepted;
- add (dryRun=1, nothing saved): a good small JPEG is accepted ("checked"); a bad name gets 422;
  two files in one request and a request without a file get 400;
- add and finish refuse an upload record that does not exist (404) and one started by another
  login (403), before anything is read or written.

- not saved files (image_upload_refused): a dry-run start counts the names it rejects and the
  page's own "refused" list, cleaned and capped (nothing created); test_not_saved_round_trip
  makes one upload record with no images, records refused files three ways (start, add, finish)
  and checks the report's "Not saved" list, with the page's text escaped.

Writes: test_not_saved_round_trip leaves one finished upload record with no images (and its
"Not saved" rows) on staging per run; no image file or image row. Otherwise none by default.
dryRun=1 makes the server check names and specimens and read the file,
and save nothing (no upload record, no image file, no image row).
The real upload test (test_real_upload_round_trip) adds images to a specimen and the images
folder, so it runs only when ANTWEB_IMAGE_UPLOAD_WRITE_TEST=1 and ANTWEB_IMAGE_UPLOAD_TEST_SPECIMEN
names a specimen agreed for this (there is no default). Before enabling it, confirm that
staging's /mnt/antweb is NOT the production images bucket: the files go to images/<code>/.
It uses shot numbers 91 to 93 so it never replaces a real image, and leaves the test images and
one upload report on staging.

Never against production: skipped unless the host is staging.antweb.org or a local server.
"""
import base64
import os
import re
from urllib.parse import urlparse

import pytest

from antweb import post_login, BASE_URL, IS_PRODUCTION, TIMEOUT, new_session

ALLOWED_HOSTS = {"staging.antweb.org", "localhost", "127.0.0.1"}
HOST = urlparse(BASE_URL).hostname or ""
URL = BASE_URL + "/imageUploadBatch.do"

USER = os.environ.get("ANTWEB_TEST_USER")
PASSWORD = os.environ.get("ANTWEB_TEST_PASSWORD")

# A specimen that exists on staging; only its existence is checked (dry run). Same record the
# page-editor tests use.
TEST_SPECIMEN = os.environ.get("ANTWEB_EDIT_TEST_SPECIMEN", "casent0922067").upper()
NO_SPECIMEN = "CASENT9999999X"

WRITE_TEST = os.environ.get("ANTWEB_IMAGE_UPLOAD_WRITE_TEST") == "1"
WRITE_SPECIMEN = (os.environ.get("ANTWEB_IMAGE_UPLOAD_TEST_SPECIMEN") or "").upper()

# A real 40x30 JPEG (652 bytes), so ImageMagick can make the sizes in the write test.
JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAoHBwgHBgoICAgLCgoLDhgQDg0NDh0VFhEYIx8lJCIfIiEmKzcvJik0KSEiMEExNDk7Pj4+JS5ESUM8SDc9Pjv/"
    "2wBDAQoLCw4NDhwQEBw7KCIoOzs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozs7Ozv/wAARCAAeACgDASIAAhEBAxEB/8QA"
    "HwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkK"
    "FhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXG"
    "x8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC"
    "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOE"
    "hYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwB1FFFfPnuB"
    "RRRQAUUUUAFFFFABRRRQAUUUUAf/2Q==")

pytestmark = [
    pytest.mark.skipif(IS_PRODUCTION or HOST not in ALLOWED_HOSTS,
                       reason=f"image upload tests run only on staging, not on {HOST}"),
]

needs_curator = pytest.mark.skipif(not USER or not PASSWORD, reason="needs ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


@pytest.fixture(scope="module", autouse=True)
def deployed():
    r = new_session().get(URL, timeout=TIMEOUT, allow_redirects=False)
    if r.status_code != 405:
        pytest.skip("imageUploadBatch.do is not deployed on this server yet")


def _login():
    s = new_session()
    post_login(s, USER, PASSWORD)
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    assert info.get("loggedIn"), "test login failed"
    return s, info["csrf"], info["loginId"]


@pytest.fixture(scope="module")
def curator():
    s, token, login_id = _login()
    # Can this login upload images at all? (A dry-run start creates nothing.)
    r = s.post(URL + "?step=start&dryRun=1", data={"artist": _artist(s), "names": TEST_SPECIMEN + "_D_1.JPG"},
               headers={"X-CSRF-Token": token}, timeout=TIMEOUT)
    if r.status_code == 403 and "may not upload images" in r.text:
        pytest.skip("the test login may not upload images")
    return s, token, login_id


def _artist(session):
    """The first artist offered on the uploader page."""
    r = session.get(BASE_URL + "/imageUploader.do", timeout=TIMEOUT)
    m = re.search(r'<select name="artist">\s*<option value="(\d+)"', r.text)
    return m.group(1) if m else "1"


def _post(session, step, token=None, params="", **kw):
    headers = {"X-CSRF-Token": token} if token else {}
    return session.post(f"{URL}?step={step}{params}", headers=headers, timeout=TIMEOUT, allow_redirects=False, **kw)


def _file(name, data=JPEG):
    return {"file": (name, data, "image/jpeg")}


def _upload_list(session):
    """listImageUploads.do, which must not fail part way (then it lists nothing and the tests
    that read it would only skip)."""
    r = session.get(BASE_URL + "/listImageUploads.do", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert "an error occurred" not in r.text and "NullPointerException" not in r.text, \
        "listImageUploads.do failed: " + re.sub(r"<[^>]+>", " ", r.text)[-600:]
    return r.text


# ---------- anonymous ----------

@pytest.mark.parametrize("step", ["start", "add", "finish"])
def test_anonymous_post_refused(step):
    kw = {"files": _file(TEST_SPECIMEN + "_D_1.JPG")} if step == "add" else {"data": {"artist": "1", "names": "X_D_1.JPG", "batch": "1"}}
    r = _post(new_session(), step, params="&batch=1", **kw)
    if "application/json" not in r.headers.get("Content-Type", ""):
        # AntWeb's bot block ("supporting only logged in users") answers before the action.
        assert "logged in" in r.text and '"ok":true' not in r.text, (r.status_code, r.text[:200])
        return
    assert r.status_code == 401, (r.status_code, r.text[:200])
    j = r.json()
    assert j["ok"] is False and j["login"] is True


def test_get_refused():
    r = new_session().get(URL + "?step=start", timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 405, r.status_code


# ---------- form token ----------

@needs_curator
@pytest.mark.parametrize("step", ["start", "add", "finish"])
@pytest.mark.parametrize("token", [None, "wrong-token"])
def test_post_without_valid_token_refused(curator, step, token):
    s, _good, _ = curator
    kw = {"files": _file(TEST_SPECIMEN + "_D_1.JPG")} if step == "add" else {"data": {"artist": "1", "names": TEST_SPECIMEN + "_D_1.JPG"}}
    r = _post(s, step, token=token, params="&dryRun=1&batch=0", **kw)
    assert r.status_code == 403, (r.status_code, r.text[:200])
    assert "application/json" not in r.headers.get("Content-Type", "")


# ---------- naming rules (dry run: nothing is created) ----------

@needs_curator
def test_start_checks_names(curator):
    s, token, _ = curator
    good = [TEST_SPECIMEN + "_D_1.JPG", TEST_SPECIMEN + "_P_2.TIF", TEST_SPECIMEN + "_H.PNG"]
    bad = {
        TEST_SPECIMEN.lower() + "_d_1.jpg": "uppercase",
        TEST_SPECIMEN + "_D_1.GIF": "Unsupported file type",
        TEST_SPECIMEN + "_Q.JPG": "Unsupported shot type",
        TEST_SPECIMEN + "D1.JPG": "",
        NO_SPECIMEN + "_D_1.JPG": "not in AntWeb",
        TEST_SPECIMEN + "_D_1 (2).JPG": "may only contain",
    }
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": "\n".join(good + list(bad))})
    assert r.status_code == 200, r.text[:300]
    j = r.json()
    assert j["ok"] and j["dryRun"] and j["batchId"] == 0
    rejected = {x["name"]: x["reason"] for x in j["rejected"]}
    assert set(rejected) == set(bad), rejected
    for name, words in bad.items():
        assert words in rejected[name], (name, rejected[name])
    assert j["accepted"] == len(good)


# A CAS specimen with images (CAS group); the test curator is in "Default Group".
OTHER_GROUP_SPECIMEN = os.environ.get("ANTWEB_OTHER_GROUP_IMAGE_SPECIMEN", "casent0005904")


@needs_curator
def test_start_refuses_replacing_other_groups_image(curator):
    """An existing image may be replaced only by its own group (admins: any), like deleting."""
    s, token, _ = curator
    r = new_session().get(f"{BASE_URL}/v3.1/images?specimenCode={OTHER_GROUP_SPECIMEN}&up=1", timeout=max(TIMEOUT, 120))
    images = [i for rec in (r.json().get("images") or []) if isinstance(rec, dict) for i in rec.get("images") or []] \
        if r.status_code == 200 else []
    if not images:
        pytest.skip(f"{OTHER_GROUP_SPECIMEN} has no images on this server")
    img = images[0]
    name = f"{OTHER_GROUP_SPECIMEN.upper()}_{str(img['shotType']).upper()}_{img['shotNumber']}.JPG"
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": name})
    assert r.status_code == 200, r.text[:300]
    j = r.json()
    rejected = {x["name"]: x["reason"] for x in j["rejected"]}
    assert name in rejected and "another group" in rejected[name], j
    assert j["accepted"] == 0
    # The same file sent on its own (dry run) is refused too.
    r = _post(s, "add", token, "&dryRun=1", files=_file(name))
    assert r.status_code == 422 and "another group" in r.text, (r.status_code, r.text[:300])


@needs_curator
def test_start_needs_artist_and_names(curator):
    s, token, _ = curator
    assert _post(s, "start", token, "&dryRun=1", data={"names": TEST_SPECIMEN + "_D_1.JPG"}).status_code == 400
    assert _post(s, "start", token, "&dryRun=1", data={"artist": "-5", "names": TEST_SPECIMEN + "_D_1.JPG"}).status_code == 400
    assert _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": ""}).status_code == 400


# ---------- one file per request (dry run: nothing is saved) ----------

@needs_curator
def test_add_dry_run_accepts_good_file(curator):
    s, token, _ = curator
    r = _post(s, "add", token, "&dryRun=1", files=_file(TEST_SPECIMEN + "_D_1.JPG"))
    assert r.status_code == 200, r.text[:300]
    j = r.json()
    assert j["ok"] and j["status"] == "checked" and j["bytes"] == len(JPEG)


@needs_curator
def test_add_refuses_bad_name_and_wrong_shape(curator):
    s, token, _ = curator
    r = _post(s, "add", token, "&dryRun=1", files=_file(TEST_SPECIMEN.lower() + "_d_1.jpg"))
    assert r.status_code == 422 and r.json()["retry"] is False, r.text[:200]
    r = _post(s, "add", token, "&dryRun=1", files=_file(NO_SPECIMEN + "_D_1.JPG"))
    assert r.status_code == 422, r.text[:200]
    two = [("file", (TEST_SPECIMEN + "_D_1.JPG", JPEG, "image/jpeg")), ("file", (TEST_SPECIMEN + "_P_1.JPG", JPEG, "image/jpeg"))]
    assert _post(s, "add", token, "&dryRun=1", files=two).status_code == 400
    assert _post(s, "add", token, "&dryRun=1", data={"x": "1"}).status_code == 400


@needs_curator
def test_add_and_finish_refuse_unknown_upload(curator):
    s, token, _ = curator
    r = _post(s, "add", token, "&batch=0", files=_file(TEST_SPECIMEN + "_D_1.JPG"))
    assert r.status_code == 404, r.text[:200]
    r = _post(s, "finish", token, data={"batch": "0"})
    assert r.status_code == 404, r.text[:200]


@needs_curator
def test_other_logins_upload_refused(curator):
    """An upload record started by someone else can be neither added to nor finished."""
    s, token, login_id = curator
    page = _upload_list(s)
    other = None
    for row in page.split("<tr>"):
        c = re.search(r"curator\.do\?id=(\d+)", row)
        u = re.search(r"imageUploadReport\.do\?id=(\d+)", row)
        if c and u and int(c.group(1)) != int(login_id):
            other = u.group(1)
            break
    if not other:
        pytest.skip("no upload by another login listed on listImageUploads.do")
    r = _post(s, "add", token, f"&batch={other}", files=_file(TEST_SPECIMEN + "_D_91.JPG"))
    assert r.status_code == 403 and "another login" in r.json()["error"], r.text[:200]
    r = _post(s, "finish", token, data={"batch": other})
    assert r.status_code == 403 and "another login" in r.json()["error"], r.text[:200]


@needs_curator
def test_report_specimen_links_open_specimen_pages(curator):
    """The report's "Uploaded Specimen Set" links open specimen pages, not image folders.

    They used to point at /images/<code>/, a folder listing, which is turned off (Brian's
    50-image test, 10-17). Read-only: uses the newest report listed on listImageUploads.do.
    """
    s = curator[0]
    page = _upload_list(s)
    # Reports with images only: test_not_saved_round_trip leaves uploads with none.
    ids = re.findall(r"<td>([1-9]\d*)</td>\s*<td><a href='[^']*imageUploadReport\.do\?id=(\d+)'", page)
    ids = [upload_id for _count, upload_id in ids]
    if not ids:
        pytest.skip("no upload reports with images listed on listImageUploads.do")
    report = None
    for upload_id in sorted({int(i) for i in ids}, reverse=True)[:10]:
        text = s.get(BASE_URL + f"/imageUploadReport.do?id={upload_id}", timeout=TIMEOUT).text
        if "Uploaded Specimen Set" in text:
            report = text
            break
    if not report:
        pytest.skip("none of the newest upload reports has a specimen set")
    set_part = report.split("Uploaded Specimen Set", 1)[1]
    assert not re.search(r"href=['\"][^'\"]*/images/[^/'\"]+/['\"]", set_part), \
        "a specimen-set link still points at an images folder"
    codes = re.findall(r"specimen\.do\?code=([A-Za-z0-9_.-]+)", set_part)
    assert codes, "no specimen links in the Uploaded Specimen Set"
    r = s.get(BASE_URL + "/specimen.do?code=" + codes[0], timeout=TIMEOUT)
    assert r.status_code == 200 and "is not in the AntWeb database" not in r.text, (codes[0], r.status_code)


@needs_curator
def test_upload_list_loads(curator):
    """The list of all image uploads shows every row, also an upload by a login that is not a
    listed curator. The test login's uploads (test_not_saved_round_trip) made the page fail with
    a NullPointerException on 10-18, so the two tests above only skipped."""
    page = _upload_list(curator[0])
    assert "Total image uploads:" in page
    assert re.search(r"imageUploadReport\.do\?id=\d+", page) or "Total image uploads: 0" in page


# ---------- files not saved (image_upload_refused) ----------

def _supports_not_saved(start_json):
    if "notSaved" not in start_json:
        pytest.skip("the server does not record files not saved yet")


@needs_curator
def test_start_counts_not_saved_dry_run(curator):
    """Rejected names and the page's own refused list are counted once each; nothing is created."""
    s, token, _ = curator
    names = [TEST_SPECIMEN + "_D_1.JPG", NO_SPECIMEN + "_D_1.JPG"]
    refused = "\n".join([
        TEST_SPECIMEN + "_P_1.JPG\tlarger than 95 MB",
        TEST_SPECIMEN + "_P_1.JPG\tsame name twice counts once",
        NO_SPECIMEN + "_D_1.JPG\talso rejected by the server: counts once",
        "\tno name",
        "",
    ])
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": "\n".join(names), "refused": refused})
    assert r.status_code == 200, r.text[:300]
    j = r.json()
    _supports_not_saved(j)
    assert j["batchId"] == 0 and j["accepted"] == 1
    assert j["notSaved"] == 2, j
    # The page's list is capped (2000 per upload).
    many = "\n".join(f"F{i}.JPG\tx" for i in range(2100))
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": names[0], "refused": many})
    assert r.status_code == 200 and r.json()["notSaved"] == 2000, r.text[:300]


@needs_curator
def test_not_saved_round_trip(curator):
    """One upload with no image saved: files refused at start (server and page), at add (server)
    and at finish (page) are listed under "Not saved" in the report, with the reasons escaped.
    Writes one upload record (no images) on staging."""
    s, token, _ = curator
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": TEST_SPECIMEN + "_D_1.JPG"})
    _supports_not_saved(r.json())

    good = TEST_SPECIMEN + "_D_94.JPG"            # accepted at start, never sent
    bad_start = NO_SPECIMEN + "_D_1.JPG"          # the server rejects it at start
    page_big = TEST_SPECIMEN + "_P_94.JPG"        # the page refused it (too large)
    bad_add = TEST_SPECIMEN.lower() + "_h_94.jpg" # the server refuses it at add
    failed = TEST_SPECIMEN + "_L_94.JPG"          # failed after retries (page, at finish)
    script = "<script>alert(1)</script>"

    r = _post(s, "start", token, data={"artist": _artist(s), "names": good + "\n" + bad_start,
                                       "refused": page_big + "\tlarger than 95 MB"})
    j = r.json()
    assert r.status_code == 200 and j["batchId"] > 0 and j["notSaved"] == 2, r.text[:300]
    batch = j["batchId"]

    r = _post(s, "add", token, f"&batch={batch}", files=_file(bad_add))
    assert r.status_code == 422, r.text[:200]

    r = _post(s, "finish", token, data={"batch": str(batch), "refused": "\n".join([
        failed + "\tfailed: network error " + script,
        bad_start + "\tthe server's reason is kept",
    ])})
    j = r.json()
    assert r.status_code == 200 and j["images"] == 0 and j["notSaved"] == 4, r.text[:300]

    report = s.get(BASE_URL + f"/imageUploadReport.do?id={batch}", timeout=TIMEOUT).text
    assert "Not saved (4)" in report, report[:500]
    part = report.split('id="notSaved"', 1)[1]
    for name in (bad_start, page_big, bad_add, failed):
        assert name in part, name
    assert "not in AntWeb" in part and "larger than 95 MB" in part
    assert "the server's reason is kept" not in part
    assert script not in report and "&lt;script&gt;" in part
    assert good not in part


# ---------- the old one-request form (removed 2026-10) ----------

NEEDS_JS = "The Image Uploader needs JavaScript. Please enable it in your browser."


@needs_curator
def test_old_form_post_is_refused(curator):
    """imageUploader.do no longer takes files: a form post (as the page sent without JavaScript)
    gets the "needs JavaScript" line and saves nothing. The file names a specimen that does not
    exist, so even a server still running the old code saves nothing."""
    s, token, _ = curator
    page = s.get(BASE_URL + "/imageUploader.do", timeout=TIMEOUT).text
    if NEEDS_JS not in page:
        pytest.skip("the server still has the old no-JavaScript form")
    assert "<noscript>" in page and "imagesUploaded" not in page
    r = s.post(BASE_URL + "/imageUploader.do", headers={"X-CSRF-Token": token},
               data={"artist": _artist(s)}, files=_file(NO_SPECIMEN + "_D_1.JPG"),
               timeout=TIMEOUT, allow_redirects=False)
    assert r.status_code == 200, r.status_code
    assert NEEDS_JS in r.text, r.text[:300]


# ---------- real upload (opt-in, writes to staging) ----------

@needs_curator
@pytest.mark.skipif(not (WRITE_TEST and WRITE_SPECIMEN),
                    reason="writes images: set ANTWEB_IMAGE_UPLOAD_WRITE_TEST=1 and ANTWEB_IMAGE_UPLOAD_TEST_SPECIMEN (staging only)")
def test_real_upload_round_trip(curator):
    s, token, _ = curator
    names = [f"{WRITE_SPECIMEN}_D_91.JPG", f"{WRITE_SPECIMEN}_P_92.JPG", f"{WRITE_SPECIMEN}_H_93.JPG"]
    r = _post(s, "start", token, data={"artist": _artist(s), "names": "\n".join(names)})
    j = r.json()
    assert r.status_code == 200 and j["ok"] and not j["rejected"] and j["batchId"] > 0, r.text[:300]
    batch = j["batchId"]
    for n in names:
        r = _post(s, "add", token, f"&batch={batch}", files=_file(n))
        assert r.status_code == 200 and r.json()["ok"], (n, r.text[:300])
    # A retry of a file already saved in this upload is accepted and not saved twice.
    r = _post(s, "add", token, f"&batch={batch}", files=_file(names[0]))
    assert r.status_code == 200 and r.json()["status"] == "alreadySaved", r.text[:300]
    r = _post(s, "finish", token, data={"batch": str(batch)})
    j = r.json()
    assert r.status_code == 200 and j["images"] == len(names), r.text[:300]
    # Finished: no more files.
    r = _post(s, "add", token, f"&batch={batch}", files=_file(names[0]))
    assert r.status_code == 409, r.text[:200]
    report = s.get(BASE_URL + f"/imageUploadReport.do?id={batch}", timeout=TIMEOUT).text
    for n in names:
        assert n in report, n
    assert "<li>true</li>" in report.replace(" ", ""), "report does not say the upload completed"

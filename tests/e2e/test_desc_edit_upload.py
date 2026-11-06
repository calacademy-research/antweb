"""The page-text editor's file upload (/descEditImageUpload.do, DescEditImageUploadAction): the last nightly-or-tool
gap of the function catalog (docs/catalog/coverage-gaps.md, 2026-11-06).

A curator uploads a file from the page-text editor (common/descEdit/taxonImageEditField.jsp) into their own folder
web/curator/<login id>/: the answer links the file (and gives an img tag for an image), and the file is served at that
link. A JSP or other refused type is not saved, and a curator cannot choose another folder. Staging only, with the test
uploader account; the files keep fixed names, so each run replaces the last run's files (one .bak each), and nothing
in the database changes (the upload writes files only; no editField save is asked for).
"""
import re
import time
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import IS_STAGING, login, needs

pytestmark = [pytest.mark.skipif(not IS_STAGING, reason="writes files: staging only"), needs("uploader")]

# A valid 1x1 PNG.
PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082")
TARGET = "description.do?taxonName=myrmicinaepheidole"


@pytest.fixture(scope="module")
def curator():
    return login("uploader")


def upload(curator, filename, content, output_name="", directory="curator"):
    s, token, _id = curator
    fields = {"homePageDirectory": (None, directory), "editField": (None, "images"), "target": (None, TARGET),
              "outputFileName": (None, output_name), "_csrf": (None, token),
              "theFile2": (filename, content, "application/octet-stream")}
    r = s.post(f"{BASE_URL}/descEditImageUpload.do?_csrf={token}", files=fields, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    return r.text


def link_in(html):
    m = re.search(r'Your uploaded file is here: <a href="([^"]+)"', html)
    return unescape(m.group(1)) if m else None


def served(url):
    path = re.sub(r"^https?://[^/]+", "", url)
    return new_session().get(BASE_URL + path, timeout=TIMEOUT)


def test_image_upload_lands_in_own_folder_with_img_tag(curator):
    html = upload(curator, "e2e-desc-edit-upload.png", PNG)
    url = link_in(html)
    assert url, "no link to the uploaded file in the answer: " + re.sub(r"\s+", " ", html)[:300]
    assert url.endswith(f"/web/curator/{curator[2]}/e2e-desc-edit-upload.png"), url
    assert "&lt;img src=" in html, "an image upload gives an img tag to paste"
    r = served(url)
    assert r.status_code == 200 and r.content == PNG, (r.status_code, len(r.content))


def test_output_name_used_and_file_replaced(curator):
    """The output file name names the saved file, and an upload replaces the file of that name (this run's text)."""
    text = f"AntWeb e2e upload {time.time():.0f}\n".encode()
    url = link_in(upload(curator, "anything.txt", text, output_name="e2e-desc-edit-upload.txt"))
    assert url and url.endswith(f"/web/curator/{curator[2]}/e2e-desc-edit-upload.txt"), url
    r = served(url)
    assert r.status_code == 200 and r.content == text, (r.status_code, r.content[:80])


def test_jsp_refused_and_not_saved(curator):
    html = upload(curator, "e2e-desc-edit.jsp", b"<% out.print(1); %>")
    assert link_in(html) is None and "not allowed" in html, re.sub(r"\s+", " ", html)[:300]
    assert served(f"/web/curator/{curator[2]}/e2e-desc-edit.jsp").status_code == 404


def test_curator_cannot_choose_another_folder(curator):
    """homePageDirectory is for admins only: a curator's file goes to their own folder whatever the form says."""
    url = link_in(upload(curator, "e2e-desc-edit-dir.txt", b"folder check\n", directory="web"))
    assert url and f"/web/curator/{curator[2]}/" in url, url
    assert served("/web/e2e-desc-edit-dir.txt").status_code == 404


def test_anonymous_refused():
    s = new_session()
    r = s.post(f"{BASE_URL}/descEditImageUpload.do", files={"theFile2": ("x.png", PNG)}, timeout=TIMEOUT,
               allow_redirects=False)
    assert r.status_code in (302, 303, 401, 403) or "login" in r.text.lower(), r.status_code
    assert link_in(r.text) is None

"""EXIF on the image page (bigPicture.do), 2026-10-19.

The admin section of the image page lists the EXIF data read from the high resolution
file. It used to print the raw field map: text decoded with the platform charset (Latin-1
names became replacement characters), the embedded thumbnail and maker notes as binary
garbage, nothing escaped. Now: labelled rows, text decoded (UTF-8, else Windows-1252),
binary fields summarized, every value escaped once, a short explanation of EXIF.

Static checks; anonymous and test-curator page loads (no EXIF section, no replacement
characters); the admin view itself only when ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD
are set (the staging admin test login, tools/staging/create-test-accounts.py), on staging only.
"""
import os

import pytest

from antweb import BASE_URL, IS_PRODUCTION, REPO_ROOT, TIMEOUT, classify, new_session, post_login
import test_page_editor as pe

# casent0104828: EXIF with artist, software, dates. lacment145067: the image noted in
# doc/exif.txt as "Bad UTF8".
PAGES = [
    "/bigPicture.do?name=casent0104828&shot=d&number=1",
    "/bigPicture.do?name=lacment145067&shot=p&number=1",
]

# U+FFFD, and UTF-8 read as Latin-1 ("Ã©" for "é").
GARBLED = ["�", "&#65533;", "Ã©", "Ã±"]

ADMIN_USER = os.environ.get("ANTWEB_TEST_ADMIN_USER")
ADMIN_PASSWORD = os.environ.get("ANTWEB_TEST_ADMIN_PASSWORD")


def _text(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


# ---------- static ----------

def test_jsp_escapes_exif_rows_and_explains():
    body = _text("web/bigPicture-body.jsp")
    assert "exif.getFields()" not in body and "<%= exifData %>" not in body
    assert "<%= Html.esc(row.label) %>" in body and "<%= Html.esc(row.value) %>" in body
    assert "EXIF is information the camera and imaging software store inside the image file" in body
    # Still only in the admin section.
    admin = body.index("if (LoginMgr.isAdmin(request)) {")
    assert body.index("exif.getRows()") > admin
    assert "Photo by <%= photographer %>" not in body


def test_jpeg_text_is_read_byte_for_byte():
    block = _text("src/com/zonageek/jpeg/JpegBlock.java")
    assert "new String(this.data, pos, length, java.nio.charset.StandardCharsets.ISO_8859_1)" in block


# ---------- anonymous and curator ----------

@pytest.mark.parametrize("path", PAGES)
def test_image_page_anonymous(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    if classify(r).category != "ok":
        pytest.skip(f"image page not available here: {classify(r)}")
    assert "Admin Information" not in r.text
    assert "EXIF data" not in r.text
    for g in GARBLED:
        assert g not in r.text, g


staging_curator = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")


@staging_curator
def test_image_page_curator_has_no_exif_section():
    s = pe.login()
    r = s.get(BASE_URL + PAGES[0], timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    if classify(r).category != "ok":
        pytest.skip(f"image page not available here: {classify(r)}")
    assert "EXIF data" not in r.text
    for g in GARBLED:
        assert g not in r.text, g


# ---------- admin (optional account) ----------

@pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not ADMIN_USER or not ADMIN_PASSWORD,
    reason="admin view checked only on staging with ANTWEB_TEST_ADMIN_USER / ANTWEB_TEST_ADMIN_PASSWORD")
@pytest.mark.parametrize("path", PAGES)
def test_image_page_admin_exif_is_legible(path):
    s = new_session()
    post_login(s, ADMIN_USER, ADMIN_PASSWORD)
    r = s.get(BASE_URL + path, timeout=TIMEOUT)
    assert r.status_code < 500, r.status_code
    if classify(r).category != "ok":
        pytest.skip(f"image page not available here: {classify(r)}")
    assert "Admin Information" in r.text, "not logged in as an administrator"
    assert "EXIF data" in r.text
    assert "EXIF is information the camera" in r.text
    section = r.text[r.text.index("EXIF data"):]
    end = section.find("High Res Path")
    section = section[:end if end > 0 else 20000]
    for g in GARBLED:
        assert g not in section, g
    assert not any(ord(c) < 0x20 and c not in "\t\r\n" for c in section)
    assert "&amp;amp;" not in section and "&amp;#44;" not in section

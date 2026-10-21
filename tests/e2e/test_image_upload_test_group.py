"""Real image upload to a test specimen, as the staging test uploader (test-gaps test 10, first part).

One tiny JPEG (40x30) goes to ZZTEST0001_D_1.JPG through imageUploadBatch.do (start, add, finish),
the way the Image Uploader page sends it; the upload report lists it as saved, and the image is
then listed for the specimen. A second run replaces it (the test group's own image).

Files go to images/zztest0001/ in staging's images folder (/mnt/antweb), a folder no real specimen
uses; tools/staging/create-test-accounts.py --remove deletes it. The specimen comes from the test
group's baseline (test_specimen_upload_roundtrip.py); if it is missing, this test uploads the
baseline first.

The curator without upload rights is refused at the start step (dry run, nothing created).
Staging only; skips without ANTWEB_TEST_UPLOADER_* / ANTWEB_TEST_NOUPLOAD_*.
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT
from staging_accounts import (IS_STAGING, assert_upload_complete, baseline_text, code, login, needs, require_test_group,
                              specimen_page, upload_specimens)
from test_image_upload import JPEG, _artist

pytestmark = pytest.mark.skipif(not IS_STAGING, reason="image uploads run only on staging")

URL = BASE_URL + "/imageUploadBatch.do"
SPECIMEN = code(1)                       # zztest0001
NAME = SPECIMEN.upper() + "_D_1.JPG"     # file names use the code in upper case


def _post(s, step, token, params="", **kw):
    return s.post(f"{URL}?step={step}{params}", headers={"X-CSRF-Token": token}, timeout=max(TIMEOUT, 180),
                  allow_redirects=False, **kw)


@needs("uploader")
def test_image_upload_to_test_specimen():
    s, token, _ = login("uploader")
    require_test_group(s)
    if not specimen_page(s, SPECIMEN)[0]:
        from test_specimen_upload_roundtrip import find_names
        _syn, valid = find_names()
        r = upload_specimens(s, token, baseline_text((valid.get("subfamily") or "").capitalize(),
                                                     valid["genus"].capitalize(), valid["species"]))
        assert_upload_complete(r)
        assert specimen_page(s, SPECIMEN)[0], f"{SPECIMEN} missing after the baseline upload"

    r = _post(s, "start", token, data={"artist": _artist(s), "names": NAME})
    if r.status_code == 403 and "may not upload images" in r.text:
        pytest.fail("the test uploader may not upload images: check its rights (create-test-accounts.py)")
    j = r.json()
    assert r.status_code == 200 and j["ok"] and not j["rejected"] and j["batchId"] > 0, r.text[:300]
    batch = j["batchId"]
    r = _post(s, "add", token, f"&batch={batch}", files={"file": (NAME, JPEG, "image/jpeg")})
    assert r.status_code == 200 and r.json()["ok"], r.text[:300]
    r = _post(s, "finish", token, data={"batch": str(batch)})
    j = r.json()
    assert r.status_code == 200 and j["images"] == 1, r.text[:300]

    report = s.get(BASE_URL + f"/imageUploadReport.do?id={batch}", timeout=TIMEOUT).text
    assert NAME in report, "the image is not in its upload report"
    assert "<li>true</li>" in report.replace(" ", ""), "the report does not say the upload completed"
    assert not re.search(r"Not saved \([1-9]", report), "the report lists a file as not saved"
    # The API lists the image for the specimen (an image row was written).
    r = s.get(f"{BASE_URL}/v3.1/images?specimenCode={SPECIMEN}&up=1", timeout=max(TIMEOUT, 120))
    if r.status_code == 200:
        images = [i for rec in (r.json().get("images") or []) if isinstance(rec, dict) for i in rec.get("images") or []]
        assert any(str(i.get("shotType")).lower() == "d" and str(i.get("shotNumber")) == "1" for i in images), \
            f"no d/1 image listed for {SPECIMEN} by the API: {images[:3]}"


@needs("noupload")
def test_curator_without_image_right_is_refused():
    s, token, _ = login("noupload")
    require_test_group(s)
    r = _post(s, "start", token, "&dryRun=1", data={"artist": _artist(s), "names": NAME})
    assert r.status_code == 403 and "may not upload images" in r.text, (r.status_code, r.text[:300])

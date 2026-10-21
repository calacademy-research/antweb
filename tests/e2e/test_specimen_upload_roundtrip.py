"""Specimen upload round trip in the staging test group (docs/flows/test-gaps-uploads-nightly.md,
tests 2, 3 and 12). Staging only, test group only.

As the test uploader (ANTWEB_TEST_UPLOADER_*, a curator of "ZZ Test Group (staging only)"):
1. upload a 5-line file (codes zztest0001 to zztest0005):
     zztest0001  a valid AntCat name                 -> loaded under that name
     zztest0002  a synonym of it                     -> filed under the valid name, report
                                                       "Recognized invalid species" with the reason
                                                       ("synonym: junior synonym of <valid>", as on the
                                                       Name updates page), specimen page
                                                       "Uploaded As: <synonym>"
     zztest0003  a name AntCat does not know         -> kept, report "Unrecognized Invalid species"
     zztest0004! a code with a character not allowed -> refused, report "(not uploaded)"
     zztest0005  coordinates outside the country     -> loaded, report "Lat/Long not within ..."
   then check the report, the specimen pages and the group's upload list;
2. upload the 2-line baseline (zztest0001, zztest0002): the upload replaces the group's data, so
   zztest0003 and zztest0005 are gone. The group is left at this baseline (an upload cannot leave
   a group empty except through bug 1 below; tools/staging/create-test-accounts.py --remove
   deletes everything).

"All lines refused must not empty the group" (bug 1, docs/flows/specimen-upload.md 7.1) is an
xfail: it fails today and passes once the upload rolls back a file that loads nothing. It then
uploads the baseline again.

As the curator without upload rights (ANTWEB_TEST_NOUPLOAD_*): a specimen upload is refused with
"does not have permission" and the group is unchanged.

The names are found on the server through the API (a species-rank synonym whose current valid
name is a valid species), so the test does not depend on one AntCat name staying the same.
Every upload first checks that the login's group is the test group: an upload replaces all of a
group's specimens. Skips without the secrets, and on any host but staging.
"""
import re
from urllib.parse import quote, unquote_plus

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import (BASELINE_HEADER, assert_upload_complete, baseline_text, code, login, needs,
                              require_test_group, report_url, specimen_page, upload_specimens, IS_STAGING)

pytestmark = pytest.mark.skipif(not IS_STAGING, reason="specimen uploads run only on staging")

API_TIMEOUT = max(TIMEOUT, 120)
UNKNOWN_SPECIES = "zztestunknown"


def _api(path):
    r = new_session().get(BASE_URL + "/v3.1/" + path + ("&" if "?" in path else "?") + "up=1", timeout=API_TIMEOUT)
    if r.status_code != 200 or "application/json" not in r.headers.get("Content-Type", ""):
        pytest.skip(f"API not available for {path}: HTTP {r.status_code}")
    return r.json()


def find_names():
    """A species synonym and its valid species, both on this server: dicts with taxonName,
    subfamily, genus, species."""
    synonyms = _api("taxa?status=synonym&taxarank=species&limit=300").get("taxa") or []
    tried = 0
    for syn in synonyms:
        cvn = syn.get("currentValidName") or ""
        if not cvn or not re.fullmatch(r"[a-z]+", syn.get("species") or "") or not syn.get("genus"):
            continue
        if (syn.get("genus") or "").lower() not in cvn:
            continue  # keep the genus: avoids the synonymised-genus path (tested elsewhere)
        if cvn.rsplit(" ", 1)[-1][:3] == syn["species"][:3]:
            continue  # another epithet, not another ending or spelling: the reason reads "synonym: junior synonym of ..."
        tried += 1
        if tried > 25:
            break
        valid = _api("taxa?taxonName=" + quote(cvn)).get("taxa") or []
        if valid and valid[0].get("status") == "valid" and valid[0].get("taxarank") == "species" \
                and re.fullmatch(r"[a-z]+", valid[0].get("species") or ""):
            return syn, valid[0]
    pytest.skip("no species synonym with a valid species name found through the API")


def _row(n, taxon, species=None, lat="-18.91", lon="47.52", code_text=None):
    return "\t".join([code_text or code(n), (taxon.get("subfamily") or "").capitalize(), taxon["genus"].capitalize(),
                      species or taxon["species"], "Madagascar", lat, lon, "AntWeb e2e test"])


def _text(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html))


def _section(report, heading_words):
    """The text of the report part that starts with a heading containing heading_words."""
    i = report.find(heading_words)
    if i < 0:
        return ""
    rest = report[i:]
    end = re.search(r"<b>", rest[len(heading_words):])
    return rest[: len(heading_words) + (end.start() if end else 4000)]


@pytest.fixture(scope="module")
def uploader():
    s, token, login_id = login("uploader")
    gid = require_test_group(s)
    return s, token, gid


@pytest.fixture(scope="module")
def names():
    return find_names()


def _upload_baseline(s, token, valid):
    r = upload_specimens(s, token, baseline_text((valid.get("subfamily") or "").capitalize(),
                                                 valid["genus"].capitalize(), valid["species"]))
    assert_upload_complete(r)
    return r


@needs("uploader")
def test_specimen_upload_round_trip(uploader, names):
    s, token, gid = uploader
    syn, valid = names
    lines = [
        BASELINE_HEADER,
        _row(1, valid),
        _row(2, syn),
        _row(3, valid, species=UNKNOWN_SPECIES),
        _row(4, valid, code_text=code(4) + "!"),
        _row(5, valid, lat="40.1", lon="10.2"),
    ]
    r = upload_specimens(s, token, "\n".join(lines) + "\n")
    assert_upload_complete(r)
    # The upload runs in the background: the page answers at once (Cloudflare cuts at 100 s).
    assert r.post_seconds < 20, f"the upload POST took {r.post_seconds:.1f} s"
    rep_url = report_url(r)
    rep = s.get(rep_url, timeout=TIMEOUT)
    assert rep.status_code == 200, (rep_url, rep.status_code)
    report = rep.text

    # The report: synonym replaced, unknown name kept, bad code refused, out-of-bounds flagged.
    recognized = _section(report, "Recognized invalid species")
    assert recognized, "no 'Recognized invalid species' section in " + rep_url
    assert syn["species"] in recognized and valid["species"] in recognized, _text(recognized)[:400]
    # The line gives the reason, worded as on the Name updates page (UploadNameReasons, 2026-10-20):
    # "<synonym> -> <valid>: synonym: junior synonym of <Valid name> (Author year)".
    valid_pretty = valid["genus"].capitalize() + " " + valid["species"]
    assert "synonym: junior synonym of " + valid_pretty in _text(recognized), _text(recognized)[:600]
    assert 'class="name-update-reason"' in recognized, _text(recognized)[:400]
    unrecognized = _section(report, "Unrecognized Invalid species")
    assert UNKNOWN_SPECIES in unrecognized, _text(unrecognized or report)[:400]
    refused = _section(report, "Specimen code with characters other than")
    assert code(4) in refused and "not uploaded" in refused, _text(report)[:600]
    assert ("Lat/Long not within country" in report or "Lat/Long not within adm1" in report), \
        "zztest0005 (Italy coordinates, country Madagascar) not reported as out of bounds"

    # The specimens.
    for n in (1, 2, 3, 5):
        exists, _page = specimen_page(s, code(n))
        assert exists, f"{code(n)} was not loaded"
    exists, _page = specimen_page(s, code(4))
    assert not exists, "the refused line zztest0004! was loaded"
    exists, page = specimen_page(s, code(2))
    assert "Uploaded As:" in page, f"{code(2)}: no 'Uploaded As' on the specimen page"
    uploaded_as = page.split("Uploaded As:", 1)[1][:1500]
    m = re.search(r"description\.do\?taxonName=([^\"'&]+)", uploaded_as)
    assert m and unquote_plus(m.group(1)) == syn["taxonName"], (m and unquote_plus(m.group(1)), syn["taxonName"])
    api = new_session().get(f"{BASE_URL}/v3.1/specimens?specimenCode={code(2)}&up=1", timeout=API_TIMEOUT)
    if api.status_code == 200:
        recs = api.json().get("specimens") or []
        if recs:
            assert recs[0].get("antwebTaxonName") == valid["taxonName"], recs[0].get("antwebTaxonName")

    # The group's upload list links this report.
    listing = s.get(f"{BASE_URL}/listSpecimenUploads.do?groupId={gid}", timeout=TIMEOUT).text
    listed = {unquote_plus(h.rsplit("/", 1)[-1]) for h in re.findall(r"/web/log/specimen/[^\"'<>]+", listing)}
    assert unquote_plus(rep_url.rsplit("/", 1)[-1]) in listed, \
        "the new report is not listed on listSpecimenUploads.do for the test group"

    # Replacement: the baseline file leaves only zztest0001 and zztest0002.
    _upload_baseline(s, token, valid)
    for n, expected in ((1, True), (2, True), (3, False), (5, False)):
        exists, _page = specimen_page(s, code(n))
        assert exists == expected, f"after the baseline upload {code(n)} {'is missing' if expected else 'is still there'}"


@needs("uploader")
@pytest.mark.xfail(reason="bug 1 (docs/flows/specimen-upload.md 7.1): a file whose lines are all refused "
                          "empties the group; the upload should roll back", strict=False)
def test_all_lines_refused_keeps_the_group(uploader, names):
    s, token, _gid = uploader
    _syn, valid = names
    _upload_baseline(s, token, valid)
    try:
        bad = BASELINE_HEADER + "\n" + _row(1, valid, code_text=code(1) + "!") + "\n" + _row(2, valid, code_text=code(2) + "!") + "\n"
        upload_specimens(s, token, bad)
        kept = [specimen_page(s, code(n))[0] for n in (1, 2)]
        upload_specimens(s, token, BASELINE_HEADER + "\n")
        kept_after_header_only = [specimen_page(s, code(n))[0] for n in (1, 2)]
        assert all(kept), "a file with every line refused emptied the test group"
        assert all(kept_after_header_only), "a header-only file emptied the test group"
    finally:
        _upload_baseline(s, token, valid)


@needs("noupload")
def test_curator_without_upload_right_is_refused():
    s, token, _ = login("noupload")
    gid = require_test_group(s)
    before = s.get(f"{BASE_URL}/listSpecimenUploads.do?groupId={gid}", timeout=TIMEOUT).text.count("/web/log/")
    r = upload_specimens(s, token, BASELINE_HEADER + "\n")
    assert "does not have permission to upload specimens" in r.text, _text(r.text)[:400]
    after = s.get(f"{BASE_URL}/listSpecimenUploads.do?groupId={gid}", timeout=TIMEOUT).text.count("/web/log/")
    assert after == before, "an upload was recorded for a curator without the upload right"


@needs("uploader")
def test_upload_needs_post(uploader):
    s, _token, _gid = uploader
    r = s.get(BASE_URL + "/upload.do?action=specimenUpload", timeout=TIMEOUT)
    assert r.status_code < 500
    assert "must be submitted through an http post" in r.text, _text(r.text)[:300]

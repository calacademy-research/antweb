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
     zztest0006  Pachycondyla sauteri, a name of two -> kept under the uploaded name, report "Needs the
                 AntCat taxa (when this server has it)    curator's decision" with both AntCat taxa linked,
                                                       the same line as on nameUpdates.do (SharedNameReport)
     zztest0007  locality code zzloc<5m_Français"?    -> stored as written (it was stored URL-encoded until
                                                       2026-10-22): the specimen page links locality.do with
                                                       the code encoded once, the locality page shows it, and
                                                       its "Show All Specimens" search finds zztest0007
   then check the report, the specimen pages and the group's upload list. Every report line ends with
   "N specimens" and the collapsed list of its codes linked to the specimen pages (SpecimenCodes, the
   Name updates page's markup): zztest0002 on its "Recognized invalid species" line, zztest0003 on its
   "Unrecognized" line;
2. upload the 2-line baseline (zztest0001, zztest0002): the upload replaces the group's data, so
   zztest0003, zztest0005 and zztest0006 are gone. The group is left at this baseline (an upload cannot leave
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
from html import unescape
from urllib.parse import quote, quote_plus, unquote_plus

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import (BASELINE_HEADER, assert_upload_complete, assert_upload_recorded, baseline_text, code,
                              group_upload_count, login, needs, require_test_group, report_url, specimen_page,
                              upload_specimens, IS_STAGING)

pytestmark = pytest.mark.skipif(not IS_STAGING, reason="specimen uploads run only on staging")

API_TIMEOUT = max(TIMEOUT, 120)
UNKNOWN_SPECIES = "zztestunknown"
# A locality code with characters the upload used to URL-encode (<, ", ?, and a cedilla). Codes keep no
# spaces (UploadUtil.cleanCode) and lose a leading or trailing quote (the parser trims them).
LOCALITY_CODE = 'zzloc<5m_Fran\u00e7ais"?'
HEADER = BASELINE_HEADER + "\tLocalityCode"


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


SHARED_NAME = {"taxonName": "ponerinaepachycondyla sauteri", "subfamily": "ponerinae", "genus": "pachycondyla",
               "species": "sauteri"}


def shared_name_on_server():
    """True when this server holds Pachycondyla sauteri as a name AntCat moves elsewhere (obsolete combination
    of Cryptopone sauteri, Wheeler 1906; the other, Forel 1912, is Ectomomyrmex sauteri)."""
    taxa = _api("taxa?taxonName=" + quote(SHARED_NAME["taxonName"])).get("taxa") or []
    return bool(taxa) and taxa[0].get("status") not in (None, "valid", "unrecognized")


def _row(n, taxon, species=None, lat="-18.91", lon="47.52", code_text=None, locality_code=""):
    return "\t".join([code_text or code(n), (taxon.get("subfamily") or "").capitalize(), taxon["genus"].capitalize(),
                      species or taxon["species"], "Madagascar", lat, lon, "AntWeb e2e test", locality_code])


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
        HEADER,
        _row(1, valid),
        _row(2, syn),
        _row(3, valid, species=UNKNOWN_SPECIES),
        _row(4, valid, code_text=code(4) + "!"),
        _row(5, valid, lat="40.1", lon="10.2"),
        _row(7, valid, locality_code=LOCALITY_CODE),
    ]
    with_shared = shared_name_on_server()
    if with_shared:
        lines.append(_row(6, SHARED_NAME))
    uploads_before = group_upload_count(s, gid)
    r = upload_specimens(s, token, "\n".join(lines) + "\n")
    assert_upload_complete(r)
    # The upload's row in the upload table (missing for background uploads on production, 2026-10-21):
    # real upload id, uploadReport.do, the group's upload list, groups.do. The listing check further down
    # passed then: it checked the report link only, and the test group's short plain name never made the
    # row's insert fail.
    assert_upload_recorded(s, r, gid, uploads_before=uploads_before, check_count=False)
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
    # Each line ends with "N specimens" and the collapsed list of its codes, linked to the specimen pages,
    # as on the Name updates page (SpecimenCodes, 2026-10-21).
    for sec, n in ((recognized, 2), (unrecognized, 3)):
        assert '<details class="specimen-codes"' in sec, _text(sec)[:400]
        assert f'/specimen.do?name={code(n)}">{code(n)}</a>' in sec, (code(n), sec[:1500])
        assert re.search(r"<summary>\d[\d,]* specimens?</summary>", sec), sec[:1500]
    refused = _section(report, "Specimen code with characters other than")
    assert code(4) in refused and "not uploaded" in refused, _text(report)[:600]
    assert ("Lat/Long not within country" in report or "Lat/Long not within adm1" in report), \
        "zztest0005 (Italy coordinates, country Madagascar) not reported as out of bounds"

    # A name of two AntCat taxa: kept under the uploaded name, both taxa listed with their AntCat links,
    # worded as on the Name updates page (SharedNameReport, 2026-10-20; the old homonym section never fired).
    if with_shared:
        kept = _section(report, "Needs the curator&#39;s decision") or _section(report, "Needs the curator's decision")
        assert kept, "no 'Needs the curator's decision' section in " + rep_url
        text = _text(kept)
        assert "kept under the uploaded name" in text and "Pachycondyla sauteri" in text, text[:600]
        assert "name is shared by two taxa" in text and "Cryptopone sauteri" in text and "Ectomomyrmex sauteri" in text, text[:600]
        assert "https://www.antcat.org/catalog/" in kept and 'class="shared-name-taxa"' in kept, kept[:600]
        assert "Taxon names recognized as Homonyms" not in report
        api6 = new_session().get(f"{BASE_URL}/v3.1/specimens?specimenCode={code(6)}&up=1", timeout=API_TIMEOUT)
        if api6.status_code == 200 and (api6.json().get("specimens") or []):
            assert api6.json()["specimens"][0].get("antwebTaxonName") == SHARED_NAME["taxonName"], \
                "zztest0006 was moved to one of the two taxa"

    # The specimens.
    for n in (1, 2, 3, 5, 7) + ((6,) if with_shared else ()):
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

    # The locality code is stored as written and linked encoded once (2026-10-22).
    check_locality_code(s)

    # The group's upload list links this report.
    listing = s.get(f"{BASE_URL}/listSpecimenUploads.do?groupId={gid}", timeout=TIMEOUT).text
    listed = {unquote_plus(h.rsplit("/", 1)[-1]) for h in re.findall(r"/web/log/specimen/[^\"'<>]+", listing)}
    assert unquote_plus(rep_url.rsplit("/", 1)[-1]) in listed, \
        "the new report is not listed on listSpecimenUploads.do for the test group"

    # Replacement: the baseline file leaves only zztest0001 and zztest0002.
    base = _upload_baseline(s, token, valid)
    assert_upload_recorded(s, base, gid)
    for n, expected in ((1, True), (2, True), (3, False), (5, False), (6, False), (7, False)):
        exists, _page = specimen_page(s, code(n))
        assert exists == expected, f"after the baseline upload {code(n)} {'is missing' if expected else 'is still there'}"


def check_locality_code(s):
    """zztest0007's locality code: the specimen page links locality.do?code= with the plain code encoded once
    (Html.url, UTF-8), the locality page opens and shows the code as written, and its "Show All Specimens" link
    (advancedSearch.do, localityCode="<code>") finds the specimen."""
    exists, page = specimen_page(s, code(7))
    assert exists, f"{code(7)} was not loaded"
    expected = "/locality.do?code=" + quote_plus(LOCALITY_CODE)
    hrefs = [unescape(h) for h in re.findall(r"""href=["']([^"']*locality\.do\?code=[^"']*)["']""", page)]
    assert any(h.endswith(expected) for h in hrefs), (expected, hrefs)
    assert not any(re.search(r"%25[0-9A-Fa-f]{2}", h) for h in hrefs), hrefs
    # Shown as text, never as markup: the raw "<" of the code must not reach the HTML (it was hidden by the
    # URL-encoding before 2026-10-22).
    raw = LOCALITY_CODE.split("_")[0]   # zzloc<5m
    assert raw not in page, "the specimen page prints the locality code unescaped"
    loc = s.get(BASE_URL + expected, timeout=TIMEOUT)
    assert loc.status_code == 200, (expected, loc.status_code)
    assert "Locality not found" not in loc.text, _text(loc.text)[:400]
    shown = re.search(r"<li>Code:</li>\s*<li><b>(.*?)</b></li>", loc.text, re.S)
    assert LOCALITY_CODE in unescape(loc.text), ("the locality page does not show the code as written",
                                                 shown.group(1) if shown else _text(loc.text)[:600])
    assert raw not in loc.text, "the locality page prints the locality code unescaped"
    m = re.search(r"""href=['"]([^'"]*advancedSearch\.do\?[^'"]*localityCode=[^'"]*)['"]>Show All Specimens""", loc.text)
    assert m, "no Show All Specimens link on the locality page"
    found = s.get(unescape(m.group(1)), timeout=API_TIMEOUT)
    assert found.status_code == 200 and code(7) in found.text, (m.group(1), _text(found.text)[:400])


@needs("uploader")
@pytest.mark.xfail(reason="bug 1 (docs/flows/specimen-upload.md 7.1): a file whose lines are all refused "
                          "empties the group; the upload should roll back", strict=False)
def test_all_lines_refused_keeps_the_group(uploader, names):
    s, token, _gid = uploader
    _syn, valid = names
    _upload_baseline(s, token, valid)
    try:
        bad = HEADER + "\n" + _row(1, valid, code_text=code(1) + "!") + "\n" + _row(2, valid, code_text=code(2) + "!") + "\n"
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

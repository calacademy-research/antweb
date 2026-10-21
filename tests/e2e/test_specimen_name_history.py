"""The specimen page's "Name History" section (read only).

SpecimenNameHistory (2026-10-21) shows, newest first: the name the specimen is
filed under and the name it was uploaded as (with the upload report's reason),
a hold for the curator when its name belongs to two or more AntCat taxa, each
move the nightly name update applied (specimen_name_update, mode 'changed'),
and the previous determinations from TaxonWorks. No heading when there is none
of these; report-only rows (would change, Preview) are never shown.

No specimen is known to have an applied nightly move yet (the step runs in
report mode until Brian switches it to apply), so the checks are: the page
renders without errors, the section is either absent or well formed, never
shows report-only wording, and holds the previous determinations when a
specimen has them. ANTWEB_NAME_HISTORY_CODES (comma separated) adds specimens
that must show the section, for example after the first apply run.
"""
import html
import os
import re

import pytest

from antweb import BASE_URL, STACK_TRACE, TIMEOUT, new_session

HEADING = "<h3>Name History: </h3>"
SECTION = re.compile(r'<div class="name_history" id="name_history">(.*?)</div>', re.S)
ROW = re.compile(r'<ul class="(name_history_[a-z]+|previous_determinations)">\s*<li><b>([^<]*?): </b></li>\s*<li>(.*?)</li>\s*</ul>', re.S)

# A specimen page that must always render (also a fingerprinted key page), and the
# CAS specimens with previous determinations of test_previous_determinations.py.
PLAIN_SPECIMEN = "casent0103190"
WITH_PREVIOUS = ["casent0005428", "casent0005808", "casent0100108"]
KNOWN = [c.strip().lower() for c in os.environ.get("ANTWEB_NAME_HISTORY_CODES", "").split(",") if c.strip()]

ERROR_MARKERS = ("HTTP Status 500", "SQLSyntaxErrorException", "Unknown column")
REPORT_ONLY = ("Would change", "would_change", "report only", "kept_valid")


def _get(code):
    r = new_session().get(f"{BASE_URL}/specimen.do?name={code}", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{code}: HTTP {r.status_code}"
    for marker in ERROR_MARKERS:
        assert marker not in r.text, f"{code}: page shows {marker!r}"
    assert not STACK_TRACE.search(r.text), f"{code}: page shows a stack trace"
    return r.text


def _check_section(code, page):
    """The section, if present, is well formed. Returns its rows [(class, label, value)] or None."""
    if HEADING not in page:
        assert 'id="name_history"' not in page, f"{code}: section without its heading"
        return None
    assert page.count(HEADING) == 1, f"{code}: heading shown twice"
    m = SECTION.search(page)
    assert m, f"{code}: heading outside the name_history block"
    rows = ROW.findall(m.group(1))
    assert rows, f"{code}: empty Name History (heading without rows)"
    for cls, label, value in rows:
        assert value.strip(), f"{code}: empty value for {label!r}"
        assert "<script" not in value.lower(), f"{code}: script in {label!r}"
        for word in REPORT_ONLY:
            assert word not in value, f"{code}: report-only wording {word!r} in {label!r}"
        if cls == "name_history_update":
            assert re.fullmatch(r"Name update \d{4}-\d{2}-\d{2}", html.unescape(label)), f"{code}: label {label!r}"
            assert value.count("/description.do?taxonName=") >= 2, f"{code}: names not linked: {value!r}"
    return rows


def test_plain_specimen_page():
    page = _get(PLAIN_SPECIMEN)
    assert "Specimen Information" in page, "specimen data missing"
    _check_section(PLAIN_SPECIMEN, page)


@pytest.mark.parametrize("code", WITH_PREVIOUS)
def test_previous_determinations_are_in_the_section(code):
    page = _get(code)
    if "Specimen Information" not in page:
        pytest.skip(f"{code} is not on this server")
    if "Previous determination:" not in page:
        pytest.skip(f"{code} has no previous determinations here yet")
    rows = _check_section(code, page)
    if rows is None:
        pytest.skip("Name History not deployed here yet (previous determinations still under Specimen Information)")
    assert any(cls == "previous_determinations" for cls, _, _ in rows), f"{code}: previous determinations outside Name History"
    assert page.count("Previous determination:") == 1, f"{code}: previous determinations shown twice"


@pytest.mark.parametrize("code", KNOWN or [pytest.param(None, marks=pytest.mark.skip(reason="set ANTWEB_NAME_HISTORY_CODES"))])
def test_known_history_is_shown(code):
    page = _get(code)
    rows = _check_section(code, page)
    assert rows, f"{code}: no Name History"

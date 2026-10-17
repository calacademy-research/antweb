"""Previous determinations on the specimen page (read only).

specimen.previous_determinations is filled by the TaxonWorks upload (Darwin Core
previousIdentifications) and by the optional specimen-file column
previousDeterminations. The specimen page shows it as "Previous determination:",
one entry per line, only when present.
"""
import html
import re

import pytest

from antweb import BASE_URL, STACK_TRACE, TIMEOUT, new_session

LABEL = "Previous determination:"

# A specimen page that must always render (also a fingerprinted key page).
PLAIN_SPECIMEN = "casent0103190"

# CAS specimens whose TaxonWorks record has two previous determinations (export of
# 2026-10-16). They show the label once a TaxonWorks upload with this feature has run.
WITH_PREVIOUS = ["casent0005428", "casent0005808", "casent0100108"]

ERROR_MARKERS = ("HTTP Status 500", "SQLSyntaxErrorException", "Unknown column")


def _get(code):
    r = new_session().get(f"{BASE_URL}/specimen.do?name={code}", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{code}: HTTP {r.status_code}"
    for marker in ERROR_MARKERS:
        assert marker not in r.text, f"{code}: page shows {marker!r}"
    assert not STACK_TRACE.search(r.text), f"{code}: page shows a stack trace"
    return r.text


def _block(page):
    """The value <li> that follows the label, or None."""
    m = re.search(r"<b>Previous determination: </b></li>\s*<li>(.*?)</li>", page, re.S)
    return m.group(1) if m else None


def test_specimen_page_renders():
    page = _get(PLAIN_SPECIMEN)
    # A missing previous_determinations column makes the specimen query fail quietly
    # and the page lose its specimen data, so check the data is there.
    assert "Specimen Information" in page, "specimen data missing (database column added?)"
    assert "Date determined:" in page
    if LABEL in page:
        assert _block(page) is not None, "label without its value"


@pytest.mark.parametrize("code", WITH_PREVIOUS)
def test_previous_determinations_shown(code):
    page = _get(code)
    if "Specimen Information" not in page:
        pytest.skip(f"{code} is not on this server")
    if LABEL not in page:
        pytest.skip(f"{code} has no previous determinations here yet (needs a TaxonWorks upload with this feature)")
    value = _block(page)
    assert value is not None, "label without its value"
    entries = [html.unescape(e).strip() for e in value.split("<br>")]
    assert len(entries) >= 2, f"expected one line per entry, got {value!r}"
    assert all(entries), f"empty entry in {value!r}"
    assert not any(" | " in e for e in entries), f"entries not split: {entries}"
    # Shown as text: no markup inside an entry other than the line breaks.
    assert "<" not in value.replace("<br>", ""), f"unescaped markup: {value!r}"

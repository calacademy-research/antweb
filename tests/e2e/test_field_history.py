"""Page-text History and Restore (page editor plan, phase 2). Staging only.

What is checked, over HTTP like a browser (no JavaScript is run here):
- the History page (fieldHistory.do) needs a curator login; anonymous visitors see no [History] link;
- it lists the current text and the earlier versions, with who and when;
- "Restore this version" works through the normal save: the restored text is stored, and the text
  it replaced appears in the history;
- a restore with a stale fingerprint (the field changed after History was opened) is refused and
  changes nothing;
- an anonymous restore POST is refused and changes nothing;
- a version of another page/field, and the entry that records a clear, cannot be restored;
- a cleared field: its previous text is listed and can be restored.

Writes go ONLY to the approved staging test fields of test_page_editor.py (taxon Polyrhachis amana,
field "comments"; specimen CASENT0922067, field "notes"), only when the field is empty at the
start, and the field is left empty again (also on failure). History rows are added on staging.
"""
import html
import re
import time
from urllib.parse import parse_qs, urlsplit

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session
import test_page_editor as pe

pytestmark = pe.pytestmark
editor_safety_deployed = pe.editor_safety_deployed
curator = pe.curator
needs_curator = pe.needs_curator

ATTR = re.compile(r'([\w-]+)\s*=\s*"([^"]*)"')
HISTORY_LINK = re.compile(r'<a class="descEditHistoryLink" href="([^"]+)"')
VERSION_START = re.compile(r'<div class="(fh-version[^"]*)" id="([^"]+)">')
COPY = re.compile(r'<textarea class="fh-copy"[^>]*>(.*?)</textarea>', re.S)
FORM = re.compile(r'<form method="POST" action="([^"]+)" class="fh-restore"(.*?)</form>', re.S)
INPUT = re.compile(r"<input\b([^>]*)>", re.I)


class Version:
    def __init__(self, classes, anchor, block):
        self.classes = classes
        self.anchor = anchor
        self.block = block
        m = COPY.search(block)
        self.text = html.unescape(m.group(1)) if m else None
        self.action, self.fields = None, {}
        f = FORM.search(block)
        if f:
            self.action = html.unescape(f.group(1))
            for a in INPUT.findall(f.group(2)):
                d = dict(ATTR.findall(a))
                if "name" in d:
                    self.fields[d["name"]] = html.unescape(d.get("value", ""))

    @property
    def cleared(self):
        return "fh-cleared" in self.classes

    @property
    def current(self):
        return "fh-current" in self.classes

    @property
    def hist_id(self):
        return self.anchor[1:] if self.anchor.startswith("v") else None


class History:
    def __init__(self, page_html):
        self.html = page_html
        starts = list(VERSION_START.finditer(page_html))
        self.versions = []
        for i, m in enumerate(starts):
            end = starts[i + 1].start() if i + 1 < len(starts) else len(page_html)
            self.versions.append(Version(m.group(1), m.group(2), page_html[m.start():end]))

    @property
    def current(self):
        return next((v for v in self.versions if v.current), None)

    def with_text(self, needle):
        return [v for v in self.versions if not v.current and v.text and needle in v.text]

    @property
    def cleared(self):
        return [v for v in self.versions if v.cleared]


def history_url(session, page_url, field):
    r = session.get(BASE_URL + page_url, timeout=TIMEOUT)
    assert r.status_code == 200
    for href in HISTORY_LINK.findall(r.text):
        href = html.unescape(href)
        if parse_qs(urlsplit(href).query).get("field") == [field]:
            return href
    raise AssertionError(f"no [History] link for {field} on {page_url}")


def open_history(session, url):
    r = session.get(url if url.startswith("http") else BASE_URL + url, timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    assert "History of" in r.text, r.text[:500]
    assert "no-store" in r.headers.get("Cache-Control", "")
    return History(r.text)


def post_restore(session, version, **override):
    data = dict(version.fields)
    data.update(override)
    url = version.action if version.action.startswith("http") else BASE_URL + version.action
    return session.post(url, data=data, timeout=TIMEOUT, allow_redirects=True)


def stored_hash(session, page_url, field):
    return pe.open_editor(session, page_url, field)[0].loaded_hash


# ---------- anonymous ----------

def test_history_page_requires_login():
    key = "taxon:formicinaepolyrhachis amana"
    r = new_session().get(BASE_URL + "/fieldHistory.do", params={"field": "comments", "key": key,
                                                               "page": pe.TEST_TAXON_URL.lstrip("/")}, timeout=TIMEOUT)
    assert classify(r).category == "login", classify(r)
    assert "Restore this version" not in r.text and "fh-version" not in r.text


def test_no_history_link_for_visitors():
    r = new_session().get(BASE_URL + pe.TEST_TAXON_URL, timeout=TIMEOUT)
    if r.status_code != 200 or pe._page_missing(r.text):
        pytest.skip("test page not available on this server")
    assert "fieldHistory.do" not in r.text


# ---------- curator ----------

@needs_curator
def test_history_page_refuses_unknown_field(curator):
    r = curator.get(BASE_URL + "/fieldHistory.do", params={"field": "nosuchfield", "key": "taxon:x"}, timeout=TIMEOUT)
    assert r.status_code == 200 and "Unknown page or field" in r.text


@needs_curator
def test_taxon_history_and_restore(curator):
    url, field = pe.TEST_TAXON_URL, pe.TEST_TAXON_FIELD
    form = pe._empty_field_or_skip(curator, url, field)
    stamp = str(int(time.time()))
    one, two = f"E2E history one {stamp}", f"E2E history two {stamp}"
    try:
        # Two saves: version one goes to the history, two is current.
        assert "were saved" in pe.post_save(curator, form, f"<p>{one}</p>").text
        form2, _ = pe.open_editor(curator, url, field)
        assert "were saved" in pe.post_save(curator, form2, f"<p>{two} <b>bold</b></p>").text

        hist_url = history_url(curator, url, field)
        h = open_history(curator, hist_url)
        assert h.current and two in h.current.block, "current text not shown"
        assert "characters" in h.current.block and " by " in h.current.block
        olds = h.with_text(one)
        assert olds, "the earlier version is not listed"
        old = olds[0]
        assert old.action and old.fields.get("restoreFrom") == old.hist_id
        assert old.fields.get("loadedHash") == stored_hash(curator, url, field)
        assert old.fields.get("_csrf"), "restore form without the form token"
        assert 'class="fh-del"' in old.block or 'class="fh-ins"' in old.block, "no differences shown"

        # Stale fingerprint: someone changed the field after History was opened. Nothing changes.
        before = stored_hash(curator, url, field)
        r = post_restore(curator, old, loadedHash="0" * 64)
        assert "was changed after you opened its History" in r.text and "was restored" not in r.text
        assert stored_hash(curator, url, field) == before

        # Anonymous POST (login ended): refused, nothing changes.
        anon = {k: v for k, v in old.fields.items() if k != "_csrf"}
        target = old.action if old.action.startswith("http") else BASE_URL + old.action
        r = new_session().post(target, data=anon, timeout=TIMEOUT, allow_redirects=True)
        assert "not logged in any more" in r.text and "was restored" not in r.text
        assert stored_hash(curator, url, field) == before

        # A row that is not a version of this field, and a bad id: refused.
        for bad in ("999999999", "x"):
            r = post_restore(curator, old, restoreFrom=bad)
            assert "not in the history" in r.text and "was restored" not in r.text, bad
        assert stored_hash(curator, url, field) == before

        # Restore: the stored text is version one again; version two is now in the history.
        r = post_restore(curator, old)
        assert "was restored to the version of" in r.text, r.text[:500]
        form3, _ = pe.open_editor(curator, url, field)
        assert one in form3.text and two not in form3.text
        h = open_history(curator, hist_url)
        assert one in h.current.block
        assert h.with_text(two), "the replaced text is not in the history after a restore"

        # Clear the field: the cleared text is listed and can be restored (2026-10-17 testing case).
        assert "was cleared" in pe.post_save(curator, form3, "", clear=True).text
        h = open_history(curator, hist_url)
        assert "This field is empty now" in h.html
        assert h.cleared, "the clear is not shown in the history"
        assert not h.cleared[0].action, "a clear entry offers Restore"
        # The clear entry itself cannot be restored (it has no text).
        restorable = h.with_text(one)
        assert restorable and restorable[0].action, "the text before the clear cannot be restored"
        r = post_restore(curator, restorable[0], restoreFrom=h.cleared[0].hist_id)
        assert "being cleared" in r.text and "was restored" not in r.text
        assert stored_hash(curator, url, field) == pe.ABSENT
        r = post_restore(curator, restorable[0])
        assert "was restored to the version of" in r.text, r.text[:500]
        assert one in pe.open_editor(curator, url, field)[0].text
    finally:
        pe._leave_empty(curator, url, field)


@needs_curator
def test_specimen_history_and_restore(curator):
    url, field = pe.TEST_SPECIMEN_URL, pe.TEST_SPECIMEN_FIELD
    form = pe._empty_field_or_skip(curator, url, field)
    stamp = str(int(time.time()))
    one, two = f"E2E specimen history one {stamp}", f"E2E specimen history two {stamp}"
    try:
        assert "were saved" in pe.post_save(curator, form, f"<p>{one}</p>").text
        form2, _ = pe.open_editor(curator, url, field)
        assert "were saved" in pe.post_save(curator, form2, f"<p>{two}</p>").text
        h = open_history(curator, history_url(curator, url, field))
        old = h.with_text(one)[0]
        r = post_restore(curator, old)
        assert "was restored to the version of" in r.text, r.text[:500]
        assert one in pe.open_editor(curator, url, field)[0].text
    finally:
        pe._leave_empty(curator, url, field)

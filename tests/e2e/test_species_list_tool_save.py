"""Species List Tool save, round trip on staging (road map step 5; catalog gaps 6, 7 and 16: speciesListTool.do
action=save, the dispute it records and the add that clears it).

As the test curator (ANTWEB_TEST_UPLOADER_*; staging only, never production):
1. open a small place's list in the Species List Tool and untick one valid name that has specimens collected there;
   save with a reason;
2. the place's All specimen names list still shows the name, marked "removed from the list by <curator> (<date>):
   <reason>" (Brian Fisher, 2026-11-03: transparency), and the Curated list leaves it out;
3. tick it again and save: the mark is gone and Curated lists it again.
Step 3 runs in a finally block, so a failure in step 2 still puts the name back.

The same for a project list (catalog gaps 7 and 16: disputeMgr.do and its action=remove): a name removed from a
project's list is listed in the Dispute Manager (proj_taxon_dispute); its remove button deletes the dispute; the
name is then ticked again in the Species List Tool.
"""
import re
import time
from html.parser import HTMLParser
from html import unescape
from urllib.parse import parse_qsl, quote, urlparse

import pytest

import numbers_sample as ns
from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import login, needs

# Small places whose lists have valid names with specimens collected there (staging, 2026-11-03). The first that has
# one is used.
PLACES = ["Seychelles", "Mayotte", "Comoros", "Mauritius", "Fiji", "Reunion"]
REMOVED = "removed from the list"


class _SaveForm(HTMLParser):
    """The Species List Tool's save form: its action, hidden fields and the names ticked in list 1."""

    def __init__(self):
        super().__init__()
        self.action, self.hidden, self.chosen1, self.boxes1, self._in = None, [], [], [], False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "form" and (a.get("method") or "").lower() == "post" and "speciesListTool" in (a.get("action") or ""):
            self.action, self._in = a["action"], True
        elif tag == "input" and self._in:
            if a.get("type") == "hidden" and a.get("name"):
                self.hidden.append((a["name"], a.get("value") or ""))
            elif a.get("type") == "checkbox" and a.get("name") == "chosen1":
                self.boxes1.append(a.get("value"))
                if "checked" in a:
                    self.chosen1.append(a.get("value"))

    def handle_endtag(self, tag):
        if tag == "form":
            self._in = False


def _rows(html):
    """{name: row text} of a species list page."""
    out = {}
    for chunk in re.split(r'<div class="sd_data"', html)[1:]:
        names = re.findall(r'<a [^>]*href="[^"]*description\.do[^"]*"[^>]*>([^<]+)</a>', chunk)
        if names:
            out[names[-1].strip()] = chunk
    return out


def _candidate(place):
    """A valid name on the place's list with specimens collected there (not a row added from specimens), or None."""
    html = ns.get(f"/taxonomicPage.do?rank=species&countryName={quote(place)}&statusSet=all", new_session())
    for name, chunk in _rows(html).items():
        if re.search(r"[\d_()]", name) or REMOVED in ns.text_of(chunk):
            continue
        cells = re.findall(r'<div class="list_extras specimens[^"]*"([^>]*)>(.*?)</div>', chunk, re.S)
        if len(cells) > 1 and "Not on the list" not in cells[1][0] and re.search(r"\d", ns.text_of(cells[1][1])):
            return name
    return None


def _geolocale_id(session, place):
    page = session.get(f"{BASE_URL}/country.do?name={quote(place)}", timeout=TIMEOUT).text
    ids = set(re.findall(r"geolocaleId=(\d+)", page))
    assert len(ids) == 1, f"{place}: geolocale ids on its page: {ids}"
    return ids.pop()


def _open(session, gid):
    """The tool with one list (a geolocale id or a project name) in column 1."""
    r = session.get(f"{BASE_URL}/speciesListTool.do?mapSpeciesList1Name={quote(str(gid))}&mapSpeciesList2Name=none"
                    f"&mapSpeciesList3Name=none", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    form = _SaveForm()
    form.feed(r.text)
    assert form.action, "the Species List Tool shows no save form: " + ns.text_of(r.text)[:300]
    return form


def _save(session, form, chosen, reason=""):
    fields = [(k, v) for k, v in form.hidden if k != "removeReason"]
    fields += [("chosen1", c) for c in chosen] + [("removeReason", reason), ("save", "Save Changes")]
    url = form.action if form.action.startswith("http") else BASE_URL + form.action
    # the form is multipart/form-data
    r = session.post(url, files=[(k, (None, v)) for k, v in fields], timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, r.status_code
    return r.text


def _place_rows(place, view):
    return _rows(ns.get(f"/taxonomicPage.do?rank=species&countryName={quote(place)}&statusSet={view}", new_session()))


@needs("uploader")
def test_species_list_tool_remove_with_reason_and_add_back():
    place = name = None
    for p in PLACES:
        name = _candidate(p)
        if name:
            place = p
            break
    if not name:
        pytest.skip(f"none of {PLACES} has a valid list name with specimens collected there today")
    s, _token, _lid = login("uploader")
    gid = _geolocale_id(s, place)
    form = _open(s, gid)
    assert ("action", "save") in form.hidden, "/speciesListTool.do?action=save: the save form has no action=save"
    # a checkbox's value is the taxon name (subfamily joined to "genus species", lower case): find the one shown as name
    key = next((v for v in form.boxes1 if v and v.endswith(name.lower()) and v[:-len(name)].isalpha()), None)
    assert key in form.chosen1, f"{name} is on the {place} list page but not ticked in the Species List Tool ({key})"
    reason = f"e2e test {time.strftime('%Y-%m-%d %H:%M')}"
    try:
        _save(s, form, [c for c in form.chosen1 if c != key], reason)
        all_rows = _place_rows(place, "all")
        assert name in all_rows, f"{name} removed from {place}: no longer in All specimen names"
        mark = ns.text_of(all_rows[name])
        assert REMOVED in mark and reason in mark, f"{place} {name}: row says {mark[:300]}"
        assert name not in _place_rows(place, "curated"), f"{name} removed from {place} but still in Curated"
    finally:
        back = _open(s, gid)
        if key not in back.chosen1:
            _save(s, back, back.chosen1 + [key])
    assert key in _open(s, gid).chosen1, f"{name} was not put back on the {place} list"
    all_rows = _place_rows(place, "all")
    assert name in all_rows and REMOVED not in ns.text_of(all_rows[name]), f"{place} {name} still marked removed"
    assert name in _place_rows(place, "curated"), f"{name} put back on {place} but not in Curated"


PROJECT = "edibleants"   # a small project list (47 names on staging, 2026-11-03)
DISPUTE_REMOVE = re.compile(r'<form method="post" action="([^"]*disputeMgr\.do\?action=remove[^"]*)"[^>]*>(.*?)</form>', re.S)


def _dispute_buttons(session):
    """{(project, taxon name): (url, hidden fields)} of the Dispute Manager's remove buttons."""
    html = session.get(f"{BASE_URL}/disputeMgr.do", timeout=TIMEOUT).text
    out = {}
    for action, body in DISPUTE_REMOVE.findall(html):
        url = unescape(action)
        q = dict(parse_qsl(urlparse(url).query))
        fields = dict(re.findall(r'<input type="hidden" name="([^"]+)" value="([^"]*)"', body))
        out[(q.get("projectName"), q.get("taxonName"))] = (url if url.startswith("http") else BASE_URL + url, fields)
    return out


@needs("uploader")
def test_project_removal_is_a_dispute_the_dispute_manager_removes():
    s, _token, _lid = login("uploader")
    form = _open(s, PROJECT)
    valid = [c for c in form.chosen1 if c and not re.search(r"[\d_()]", c)]
    if not valid:
        pytest.skip(f"no valid name on the {PROJECT} list today")
    key = valid[0]
    try:
        _save(s, form, [c for c in form.chosen1 if c != key], f"e2e test {time.strftime('%Y-%m-%d %H:%M')}")
        assert key not in _open(s, PROJECT).chosen1, f"{key} still on the {PROJECT} list after the save"
        buttons = _dispute_buttons(s)
        assert (PROJECT, key) in buttons, f"/disputeMgr.do does not list the removal of {key} from {PROJECT}"
        url, fields = buttons[(PROJECT, key)]
        assert "/disputeMgr.do?action=remove" in url, url
        r = s.post(url, data=fields, timeout=TIMEOUT)
        assert r.status_code == 200, r.status_code
        assert (PROJECT, key) not in _dispute_buttons(s), f"the dispute {PROJECT} {key} is still listed after remove"
    finally:
        back = _open(s, PROJECT)
        if key not in back.chosen1:
            _save(s, back, back.chosen1 + [key])
    assert key in _open(s, PROJECT).chosen1, f"{key} was not put back on the {PROJECT} list"
    assert (PROJECT, key) not in _dispute_buttons(s), f"{key} is back on {PROJECT} but still disputed"

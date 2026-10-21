"""Curate page clarity (Brian Fisher, 2026-10-20).

1. The top section ("Curator Information") is three short groups, one link per line with a plain
   explanation: Your account, Your uploads, Names and checks. Every link and who sees it is unchanged
   (World (AntCat) upload reports and the count check: administrators only).
2. "Name updates" is called "Name update report" wherever users see it (the URL stays nameUpdates.do).
3. The count check is labelled "Check the counts" and its page says in plain words what it checks, what
   a difference means and what to do (usually nothing; persistent ones to antweb@antweb.org).
4. "Past upload files of: Select..." (administrators) is "Specimen files uploaded before": choose a
   group to list the specimen files it sent (AntWeb, TaxonWorks and GBIF files), newest first.
5. Specimen List Lookup, opened from the Curate page as the staging test curator: a short list of
   known CAS codes and one unknown code; the known codes are found (their data downloads) and the
   unknown one is listed as not found. Read-only.

Static checks run anywhere; the page checks run on staging with the zztest accounts
(tools/staging/create-test-accounts.py), read-only: only GET requests, the lookup (which changes
nothing) and the upload-file listing.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify
from staging_accounts import login, needs

CURATE = REPO_ROOT / "web" / "curate" / "curate-body.jsp"
GROUPS = {
    "curate-account": ("Your account", ["/curator.do?id=", "/viewLogin.do?id=", "/curatorFiles.do"]),
    "curate-uploads": ("Your uploads", ["/listSpecimenUploads.do?groupId=", "/listImageUploads.do?groupId=", "/jobStatus.do"]),
    "curate-names": ("Names and checks", ["/nameUpdates.do"]),
}
ADMIN_ONLY = ["/query.do?name=worldantsUploads", "/countCheck.do"]

# CAS specimens on staging (TaxonWorks upload; also used by test_curator.py) and a code that is not in AntWeb.
KNOWN_CODES = ["casent0872013", "casent0922067"]
UNKNOWN_CODE = "zz-e2e-clarity-missing-001"


def _src(path):
    return re.sub(r"<%--.*?--%>|<!--.*?-->", "", (REPO_ROOT / path).read_text(encoding="utf-8"), flags=re.S)


def _visible(html):
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html)))


def _group(html, gid):
    m = re.search(r'id="%s">(.*?)</ul>' % gid, html, re.S)
    assert m, f"no {gid} group on the Curate page"
    return m.group(1)


def _absolute(href):
    href = unescape(href)
    return href if href.startswith("http") else BASE_URL + href


def _assert_opens(session, href):
    r = session.get(_absolute(href), timeout=120)
    outcome = classify(r)
    assert r.status_code < 500 and outcome.category == "ok", f"{href}: {outcome}"
    return r


# Static: the source says what the pages will show.

def test_source_three_groups_one_link_per_line():
    src = _src("web/curate/curate-body.jsp")
    for gid, (title, links) in GROUPS.items():
        block = src.split(f'id="{gid}"', 1)[1].split("</ul>", 1)[0]
        assert f">{title}</h3>" in block, title
        for link in links:
            assert link in block, f"{link} not in {title}"
        items = re.findall(r"<li>(.*?)</li>", block, re.S)
        assert all(i.count("<a ") == 1 for i in items), f"{title}: one link per line"
        assert all(re.search(r"</a>: \S", i) for i in items), f"{title}: each link has an explanation"
    names = src.split('id="curate-names"', 1)[1].split("</ul>", 1)[0]
    # Administrators only, as before.
    assert re.search(r"isAdmin\(\)\) \{ %>\s*<li><a [^\n]*worldantsUploads", names)
    assert re.search(r"LoginMgr\.isAdmin\(accessLogin\)\) \{ %>\s*<li><a [^\n]*countCheck\.do", names)
    for old in ("View Your <a", "Manage Your <a", ">Name updates<", ">Count check<", "Past upload files of"):
        assert old not in src, old


def test_source_name_update_report_label():
    for path in ("web/curate/nameUpdates-body.jsp", "web/curate/nameUpdates.jsp", "web/curate/curate-body.jsp",
                 "docs/guides/curate-page.md"):
        text = _src(path)
        assert "Name update report" in text, path
        assert "Name updates" not in text.replace("(formerly Name updates)", ""), path


def test_source_count_check_wording():
    help_text = _src("src/org/calacademy/antweb/util/CountCheckAction.java")
    for words in ("counts again", "pending", "antweb@antweb.org", "Usually nothing"):
        assert words in help_text, words
    assert "<h1>Check the counts</h1>" in _src("web/util/countCheck-body.jsp")


# Staging: the pages as the test curator and the test administrator.

@pytest.fixture(scope="module")
def curator():
    s, _token, _id = login("uploader")
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    return s, r.text


@needs("uploader")
def test_curator_sees_three_groups_and_links_open(curator):
    s, html = curator
    for gid, (title, links) in GROUPS.items():
        block = _group(html, gid)
        assert title in block, title
        hrefs = re.findall(r'href="([^"]+)"', block)
        for link in links:
            assert any(link in h for h in hrefs), f"{link} missing from {title}"
        for href in hrefs:
            _assert_opens(s, href)
    assert "Name update report" in _visible(_group(html, "curate-names"))
    for link in ADMIN_ONLY:
        assert link not in html, f"{link} is for administrators"


@needs("uploader")
def test_name_update_report_title(curator):
    s, _html = curator
    r = _assert_opens(s, "/nameUpdates.do")
    assert "<h1>Name update report</h1>" in r.text
    assert "Name updates" not in _visible(r.text)


@needs("uploader")
def test_curator_past_upload_files(curator):
    s, html = curator
    m = re.search(r'href="([^"]*uploadHistory\.do\?editSpeciesList=specimen\d+)">Your past upload files</a>: (.*?)</div>', html, re.S)
    assert m, "no 'Your past upload files' link for the uploader"
    assert "download" in m.group(2)
    r = _assert_opens(s, m.group(1))
    assert "Specimen files uploaded before" in r.text and 'id="upload-history-help"' in r.text
    files = re.findall(r'uploadFile\.do\?file=([^"]+)"', r.text)
    stamps = [re.match(r"(\d{8}-\d\d(?:%3A|:)\d\d(?:%3A|:)\d\d)", f) for f in files]
    stamps = [x.group(1) for x in stamps if x]
    assert stamps == sorted(stamps, reverse=True), "newest first"


@needs("uploader")
def test_specimen_list_lookup_from_the_curate_page(curator):
    """Open Specimen List Lookup from its Curate page link, send two known codes and an unknown one,
    and check the result: 2 found (their data downloads), the unknown code listed as not found."""
    s, html = curator
    m = re.search(r'href="([^"]*/specimenListLookup\.do)">Specimen List Lookup</a>: ([^<]+)', html)
    assert m, "no Specimen List Lookup link with an explanation on the Curate page"
    form = _assert_opens(s, m.group(1))
    assert "<h1>Specimen List Lookup</h1>" in form.text
    token = re.search(r'name="_csrf" value="([A-Za-z0-9_-]+)"', form.text)
    assert token, "no CSRF field on the lookup form"
    body = ("\n".join(KNOWN_CODES + [UNKNOWN_CODE]) + "\n").encode()
    r = s.post(BASE_URL + "/specimenListLookup.do?_csrf=" + token.group(1),
               files={"file": ("clarity_specimens.txt", body, "text/plain")}, timeout=120)
    assert r.status_code == 200 and "text/html" in r.headers.get("Content-Type", ""), (r.status_code, r.text[:300])
    text = _visible(r.text)
    assert "Specimen codes submitted: 3" in text, text[:600]
    assert "Found in AntWeb: 2" in text, text[:600]
    assert "Not found in AntWeb: 1" in text, text[:600]
    box = r.text.split('id="notFoundCodes"', 1)[1].split("</textarea>", 1)[0]
    assert box.split(">", 1)[1].strip().split("\n") == [UNKNOWN_CODE]
    d = s.get(BASE_URL + "/specimenListLookup.do?download=tsv", timeout=60)
    assert "tab-separated-values" in d.headers.get("Content-Type", ""), d.text[:300]
    found = sorted(line.split("\t", 1)[0].lower() for line in d.text.strip().split("\n")[1:])
    assert found == sorted(KNOWN_CODES), found


@needs("admin")
def test_admin_sees_all_links_and_upload_files_menu():
    s, _token, _id = login("admin")
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    html = r.text
    names = _group(html, "curate-names")
    for link in ADMIN_ONLY:
        assert link in names, f"{link} missing for an administrator"
    assert "World (AntCat) upload reports" in names and ">Check the counts</a>" in names
    _assert_opens(s, "/query.do?name=worldantsUploads")
    for gid in GROUPS:
        for href in re.findall(r'href="([^"]+)"', _group(html, gid)):
            if "/countCheck.do" not in href:  # slow; loaded by test_count_check.py
                _assert_opens(s, href)
    # Specimen files uploaded before: a group menu, sorted by name, then the group's list.
    assert 'id="past-upload-files"' in html and "Past upload files of" not in html
    menu = html.split('id="past-upload-files"', 1)[1].split("</select>", 1)[0]
    options = re.findall(r'<option value="(specimen\d+)">([^<]*)</option>', menu)
    assert options, "no groups in the Specimen files uploaded before menu"
    labels = [unescape(label) for _v, label in options]
    assert labels == sorted(labels, key=str.lower), "groups sorted by name"
    token = re.search(r'name="_csrf" value="([A-Za-z0-9_-]+)"', html).group(1)
    value = next((v for v, label in options if "ZZ Test Group" in label), options[0][0])
    page = s.post(f"{BASE_URL}/uploadHistory.do?_csrf={token}",
                  files={"editSpeciesList": (None, value), "_csrf": (None, token)}, timeout=TIMEOUT)
    assert page.status_code == 200 and classify(page).category == "ok", classify(page)
    assert "Specimen files uploaded before" in page.text

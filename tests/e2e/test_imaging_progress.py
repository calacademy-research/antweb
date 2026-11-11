"""The imaging progress page (imagingProgress.do; Brian Fisher, 2026-11-08; docs/numbers.md rows 124 to 128).

Public, anonymous and read-only. The page shows valid living ant species and subspecies imaged (a photo of the ant,
anywhere in AntWeb; label photos never count), the species-only number of the home page and the statistics page, and
tables by museum, bioregion, country and genus, each row linked to its unimaged names; a CSV lists all unimaged names.
The numbers come from the table imaging_progress, filled by the daily update (set 4); the count check recounts them
every morning ("imaging.progress", ImagingProgressDb, NumberChecks.imagingProgress). Static checks need no server.
"""
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, new_session

PAGE = "/imagingProgress.do"


def _get(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT)
    if r.status_code == 404:
        pytest.skip(f"{path}: not deployed on this server yet")
    return r


def _page(path=PAGE):
    r = _get(path)
    if classify(r).category != "ok":
        pytest.skip(f"{path}: {classify(r).category}")
    return r.text


def _counted():
    html = _page()
    if 'id="ip-not-counted"' in html:
        pytest.skip("the imaging progress is not counted yet (the next daily update counts it)")
    return html


def _n(html, key):
    # the number may be followed by words inside the same element (statsPage: "12,065 species imaged")
    m = re.search(r'data-stat="' + re.escape(key) + r'">([\d,]+)(?=[<\s])', html)
    assert m, f"no number {key!r}"
    return int(m.group(1).replace(",", ""))


def _rows(html, kind):
    """The rows of one table: key, the data-v values of its number cells, the unimaged link (or None)."""
    m = re.search(r'<table class="ip-table ip-sortable" id="ip-table-' + kind + r'".*?</table>', html, re.S)
    if not m:
        return []
    out = []
    for key, body in re.findall(r'<tr data-key="([^"]*)">(.*?)</tr>', m.group(0), re.S):
        values = [float(v) for v in re.findall(r'data-v="(-?[\d.]+)"', body)]
        link = re.search(r'class="ip-unimaged" href="([^"]+)"', body)
        out.append((key, values, link.group(1).replace("&amp;", "&") if link else None))
    return out


# ---------- static ----------

def test_source_page_words():
    """Plain language, every number explained, no em-dashes, "daily update" (never "nightly") on the page."""
    body = (REPO_ROOT / "web/util/imagingProgress-body.jsp").read_text(encoding="utf-8")
    text = re.sub(r"<%--.*?--%>", "", body, flags=re.S)
    assert "\u2014" not in text and "&mdash;" not in text
    assert "nightly" not in text.lower()
    for words in ("Label photos never count", "What the columns mean", "Imaged from here",
                  "Unimaged but with specimens in AntWeb", "when the rules of these numbers last changed",
                  # Brian Fisher, 2026-11-09: photos of red-flagged specimens are not counted, and the page says so
                  "Photos of red-flagged specimens are not counted", "photos of red-flagged specimens are not counted"):
        assert words in text, words
    assert "toolsHead.jsp" not in text, "a public page: no tools.css"


# ---------- live ----------

def test_page_loads_with_the_headline():
    html = _counted()
    assert "<h1>Imaging progress</h1>" in html
    assert 'id="ip-label-note"' in html and "Label photos never count" in html
    imaged, total = _n(html, "imaged"), _n(html, "total")
    assert 0 < imaged <= total, (imaged, total)
    assert _n(html, "unimaged") == total - imaged
    assert _n(html, "unimaged-casc") <= _n(html, "unimaged-with-specimens") <= total - imaged
    assert _n(html, "species-imaged") <= _n(html, "living-species") <= total
    assert _n(html, "species-imaged") <= imaged
    assert "\u2014" not in html


def test_headline_species_equals_the_statistics_page():
    """The species-only line (valid living species imaged) = statsPage's "species imaged" (statistics.living_species_imaged);
    both are counted by the daily update from the same data (set 4)."""
    html = _counted()
    stats = _page("/statsPage.do")
    if 'data-stat="species-imaged"' not in stats or 'data-stat="pending"' in stats:
        pytest.skip("statsPage.do: the species imaged number is not counted yet")
    # The statistics row is rewritten after every specimen upload (the tests upload on staging), the imaging table once a
    # day: images added since the daily update may show on the statistics page first (review 2026-11-08). The exact
    # equality is checked by the count check (imaging.progress) right after set 4; here a small lag is allowed.
    for name in ("species-imaged", "living-species"):
        page, shown = _n(html, name), _n(stats, name)
        assert abs(page - shown) <= max(5, shown // 200), f"{name}: imaging progress {page}, statistics page {shown}"
    assert "imagingProgress.do" in stats, "statsPage links to the imaging progress page"


def test_csv_downloads_every_unimaged_name():
    html = _counted()
    r = _get(PAGE + "?format=csv")
    assert r.status_code == 200, r.status_code
    assert r.headers.get("Content-Type", "").startswith("text/csv"), r.headers.get("Content-Type")
    assert "attachment" in r.headers.get("Content-Disposition", "")
    lines = r.content.decode("utf-8").split("\r\n")
    assert lines[0] == "taxon_name,name,rank,subfamily,genus,specimens,casc_specimens,museums"
    rows = [l for l in lines[1:] if l]
    assert len(rows) == _n(html, "unimaged"), f"CSV {len(rows)} names, page {_n(html, 'unimaged')} still to image"


def test_rows_nest_and_a_bioregion_opens_its_unimaged_list():
    """Every row: imaged from here <= imaged <= total; the smallest bioregion's link opens taxonomicPage.do?isImaged=false
    with "U Unimaged Species (out of N)", U = its unimaged, N = its total (the Valid view)."""
    html = _counted()
    for kind in ("museum", "bioregion", "country", "genus"):
        rows = _rows(html, kind)
        assert rows, f"no {kind} table"
        for key, v, _link in rows:
            total, imaged = v[0], v[1]
            assert imaged <= total, (kind, key, v)
            if kind != "genus":
                assert v[4] <= imaged, (kind, key, "imaged from here", v)
    bio = [r for r in _rows(html, "bioregion") if r[2] and r[1][0] > 0]
    if not bio:
        pytest.skip("no bioregion with unimaged names")
    key, v, link = min(bio, key=lambda r: r[1][0])
    assert "isImaged=false" in link and "statusSet=valid" in link
    lst = new_session().get(link if link.startswith("http") else BASE_URL + link, timeout=300)
    if classify(lst).category != "ok":
        pytest.skip(f"{link}: {classify(lst).category}")
    m = re.search(r'<span class="numbers">([\d,]+)</span> Unimaged Species \(out of <span class="numbers">([\d,]+)</span>\)', lst.text)
    assert m, f"{link}: no 'U Unimaged Species (out of N)'"
    assert int(m.group(2).replace(",", "")) == int(v[0]), f"{key}: out of {m.group(2)}, imaging progress total {int(v[0])}"
    assert int(m.group(1).replace(",", "")) == int(v[0] - v[1]), f"{key}: {m.group(1)} unimaged, imaging progress {int(v[0] - v[1])}"


def test_genus_view_lists_its_unimaged_names():
    html = _counted()
    genera = [r for r in _rows(html, "genus") if r[2] and r[1][0] - r[1][1] > 0]
    if not genera:
        pytest.skip("no genus with unimaged names")
    key, v, link = min(genera, key=lambda r: r[1][0] - r[1][1])
    page = _page(link.replace(BASE_URL, "") if link.startswith(BASE_URL) else link[link.index("/imagingProgress.do"):])
    assert 'id="ip-genus-title"' in page
    assert _n(page, "genus-unimaged") == int(v[0] - v[1])
    names = re.findall(r"<tr><td><a href=", page[page.index('id="ip-genus-list"'):])
    assert len(names) == int(v[0] - v[1])
    r = _get(PAGE + "?genus=" + key + "&format=csv")
    assert r.headers.get("Content-Type", "").startswith("text/csv")
    assert len([l for l in r.content.decode("utf-8").split("\r\n")[1:] if l]) == int(v[0] - v[1])


def test_unknown_genus_is_a_message():
    r = _get(PAGE + "?genus=nosuchgenus")
    assert classify(r).category == "message", classify(r)


def test_home_box_links_to_the_page():
    html = _page("/")
    m = re.search(r'<div id="stats">(.*?)<span class="right">', html, re.S)
    if not m or 'data-stat="species-imaged"' not in m.group(1):
        pytest.skip("no home page box of 2026-11-06")
    if 'href="/imagingProgress.do"' not in m.group(1):
        pytest.skip("the box is written by the daily update or an upload; not yet since this change")
    assert re.search(r'<a class="stat-link" href="/imagingProgress.do"[^>]*><div class="stat" data-stat="species-imaged">', m.group(1))

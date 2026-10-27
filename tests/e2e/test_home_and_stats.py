"""The home page and the statistics page (road map step 2; docs/flows/home-and-statistics.md).

Anonymous and read-only. The home page's links are tested by test_home_page_links.py; its numbers and those
of statsPage.do by the count invariants (docs/numbers.md #1 to #19) and test_numbers_pages.py. Here: the
pages render their sections, the home page's generated parts (statistics, recent images, recent edits)
are filled in, and the statistics page has every section.
"""
import re

from public_pages import assert_ok, fetch, links, title

HOME_NUMBERS = ["valid species + ssp.", "specimen records", "species + ssp. imaged", "specimens imaged",
                "total specimen images"]
STATS_SECTIONS = ["Current Statistics:", "Global Data:", "Images:", "Imaged Species:", "Bioregion Data:",
                  "Museum Data:"]


def test_home_page_sections():
    r = fetch("/")
    assert_ok(r, "/")
    assert title(r) == "AntWeb", title(r)
    stats = re.search(r'<div id="stats">(.*?)</div>', r.text, re.S)
    assert stats, "no statistics box"
    for label in HOME_NUMBERS:
        assert re.search(r'<span class="numbers"[^>]*>[\d,]+</span>\s*' + re.escape(label), stats.group(1)), \
            f"home page statistics: no number for {label!r} (web/genInc/statistics.jsp not written?)"
    assert links(stats.group(1), "statsPage"), "no link to statsPage.do"
    for box in ('id="recent_activity"', 'id="contributors"', 'id="promo_container"'):
        assert box in r.text, f"home page: no {box}"
    recent = r.text.split('id="recent_activity"', 1)[1]
    assert links(recent, "recentSearchResults"), "no link to the recent images page"
    for page in ("groups", "curators", "museums", "artists", "worldants"):
        assert links(r.text, page), f"home page: no link to {page}.do"


def test_old_home_address():
    r = fetch("/index.do")
    assert_ok(r)
    assert title(r) == "AntWeb" and 'id="stats"' in r.text, title(r)


def test_statistics_page_sections():
    """statsPage.do: its sections and the live valid species number. Right after a restart the live numbers are
    not loaded yet and the page says so (data-stat="starting", never 0): wait for them, up to 5 minutes."""
    import time
    for _ in range(10):
        r = fetch("/statsPage.do")
        assert_ok(r)
        if 'data-stat="starting"' not in r.text:
            break
        time.sleep(30)
    assert title(r) == "Antweb Statistics", title(r)
    for section in STATS_SECTIONS:
        assert f"<h2>{section}</h2>" in r.text, f"statsPage.do: no {section!r} section"
    m = re.search(r'<b data-stat="valid-species">([\d,]+) valid species \+ ssp\.</b>', r.text)
    assert m and int(m.group(1).replace(",", "")) > 10000, "no valid species number"


def test_taxa_list_of_a_project():
    """taxaList.do (linked from the statistics tables): plain list of the names of a rank in a place."""
    r = fetch("/taxaList.do?rank=subfamily&status=valid&project=allantwebants")
    assert_ok(r)
    assert "formicinae" in r.text.lower() and "myrmicinae" in r.text.lower(), r.text[:300]


def test_statistics_page_institutions_are_the_museums_listed():
    """statsPage.do "Specimen records from AntWeb's N collaborating institutions": N = the museums listed on
    museums.do (MuseumMgr.getMuseumCount; Brian Fisher 2026-10-27, docs/numbers.md row 7 and B73: was the fixed
    text "21"). Right after a restart the list is not loaded yet and the page says so instead of a number."""
    r = fetch("/statsPage.do")
    assert_ok(r)
    m = re.search(r'<span data-stat="institutions">([\d,]+)</span> collaborating institutions', r.text)
    if not m:
        assert 'data-stat="starting"' in r.text, "statsPage.do: no number of collaborating institutions"
        return
    shown = int(m.group(1).replace(",", ""))
    museums = fetch("/museums.do")
    assert_ok(museums)
    listed = len(re.findall(r'<tr>\s*<td><a href="[^"]*/museum\.do\?', museums.text))
    assert shown == listed, f"statsPage.do: {shown} collaborating institutions, museums.do lists {listed} museums"

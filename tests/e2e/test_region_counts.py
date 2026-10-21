"""Region and subregion pages count the specimens collected in their countries (2026-10-20).

Brian Fisher, 2026-10-20: "this is why no one believes our numbers: they never made sense; we need to
make sure that we say what a number means." Until then the Specimens number of a region or subregion
(and its Imaged specimens and Images) counted every specimen, anywhere in the world, of the names on its
list. Now they count the specimens that the pages of its countries count (collected there, identified to
a name on that country's list), each once (Geolocale.regionSpecimensSql).

Checked on the Eastern Africa subregion page (the subregion of Madagascar; staging data differs from
production, so no fixed numbers):
- the Specimens row of the statistics table (data-stat="specimens") has the new definition;
- Images is not smaller than Imaged specimens (when the image numbers are computed);
- staging, admin login: once a nightly run has counted the subregions by the new rule (compute.log line
  "Subregions populated (specimens collected in their countries)"), Specimens is not more than the sum of
  the Specimens of its countries (the country pages listed on the subregion page for curators, valid
  countries, islands included). Before that run the stored number is still the old one: skipped.
Country pages (Comoros): the Specimens row says "identified to ... on this country's AntWeb list" and,
under "List all specimens collected in ...", points to Show Specimen Taxa of the species list.
Read-only. Database behaviour: test/org/calacademy/antweb/home/RegionCountDbTest.java (CI).
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import IS_STAGING, login, needs

PAGE = "/subregion.do?name=Eastern+Africa"
DEFINITION = "Ant specimens collected in this subregion's countries and identified to a name on their lists; each specimen counted once."
RULE_LOG = "specimens collected in their countries"   # GeolocaleDb.REGION_RULE_LOG

# Rows of the statistics table carry data-stat and data-n (the number shown).
ROW = re.compile(r'<tr data-stat="([a-z-]+)" data-n="(\d+)"[^>]*>(.*?)</tr>', re.S)


def _rows(html):
    """data-stat -> (data-n, row HTML) of the statistics table of a place page."""
    if 'id="place-stats"' not in html:
        return {}
    block = html[html.index('id="place-stats"'):]
    block = block[:block.index("</table>")]
    return {m.group(1): (int(m.group(2)), m.group(3)) for m in ROW.finditer(block)}


def _specimens(html):
    rows = _rows(html)
    return rows["specimens"][0] if "specimens" in rows else None


def _text(html):
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html))).replace("\xa0", " ")


@pytest.fixture(scope="module")
def page():
    r = new_session().get(BASE_URL + PAGE, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{PAGE}: HTTP {r.status_code}"
    if 'id="place-stats"' not in r.text:
        pytest.skip("the statistics with definitions are not deployed on this server yet")
    if "collected anywhere, not only here" in r.text:
        pytest.skip("the region rule of 2026-10-20 is not deployed on this server yet")
    return r.text


def test_specimens_definition(page):
    rows = _rows(page)
    assert "specimens" in rows, f"no Specimens row in the statistics table; rows: {list(rows)}"
    text = _text(rows["specimens"][1])
    assert DEFINITION in text, f"Specimens definition {DEFINITION!r} not in {text!r}"


def test_images_not_fewer_than_imaged_specimens(page):
    rows = _rows(page)
    if "images" not in rows or "imaged" not in rows:
        pytest.skip("place image numbers not computed yet (nightly set 4 after the deploy)")
    assert rows["images"][0] >= rows["imaged"][0], {k: v[0] for k, v in rows.items()}


COUNTRY_PAGE = "/country.do?name=Comoros"
COUNTRY_DEFINITION = ("Ant specimens collected in this country and identified to a species, morphospecies, or unidentified taxon"
                      " on this country's AntWeb list.")


def test_country_specimens_row_points_to_specimen_taxa():
    """A country's Specimens row: its definition (names on its list) and, under the link to all its specimen
    records, Show Specimen Taxa of the species list for the names of every specimen collected there."""
    r = new_session().get(BASE_URL + COUNTRY_PAGE, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{COUNTRY_PAGE}: HTTP {r.status_code}"
    rows = _rows(r.text)
    if "specimens" not in rows or "on this country's AntWeb list" not in _text(rows["specimens"][1]):
        pytest.skip("the country definition of 2026-10-20 is not deployed on this server yet")
    row = rows["specimens"][1]
    assert COUNTRY_DEFINITION in _text(row), _text(row)
    assert "List all specimens collected in Comoros" in _text(row), _text(row)
    m = re.search(r'id="specimen-taxa-link" href="([^"]+)"', row)
    assert m, f"no Show Specimen Taxa link in the Specimens row: {row}"
    href = unescape(m.group(1))
    assert "taxonomicPage.do?rank=species" in href and "countryName=Comoros" in href and "specimen=true" in href, href


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_specimens_not_more_than_its_countries():
    s, _token, _id = login("admin")
    r = s.get(f"{BASE_URL}/logMgr.do?action=get&fileName=compute&ext=log&lines=3000", timeout=120)
    assert r.status_code == 200, r.status_code
    m = re.search(r"<pre>(.*?)</pre>", r.text, re.S)
    lines = unescape(m.group(1)).splitlines() if m else []
    populated = [l for l in lines if "Subregions populated" in l]
    if not populated or RULE_LOG not in populated[-1]:
        pytest.skip("no nightly run has counted the subregions by the 2026-10-20 rule yet (compute.log)")

    r = s.get(BASE_URL + PAGE, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, r.status_code
    subregion = _specimens(r.text)
    assert subregion is not None, "no Specimens row on the subregion page"
    # Curators see the valid countries (the ones the count uses), live or not.
    heading = r.text.index("Countries:</b>") if "Countries:</b>" in r.text else -1
    assert heading >= 0, "no list of countries on the subregion page"
    block = r.text[heading:r.text.index("<br>", heading) if "<br>" in r.text[heading:] else heading + 20000]
    links = re.findall(r"href='([^']+)'>([^<]+)</a>", block)
    assert links, "no country links on the subregion page"
    total = 0
    for href, name in links:
        url = href if href.startswith("http") else BASE_URL + href
        c = s.get(url, timeout=max(TIMEOUT, 120))
        assert c.status_code == 200, (name, c.status_code)
        n = _specimens(c.text)
        assert n is not None, f"no Specimens row on the page of {name}"
        total += n
    assert subregion <= total, (f"Eastern Africa shows {subregion} specimens, more than its countries together ({total}): "
                                f"{', '.join(n for _h, n in links)}")

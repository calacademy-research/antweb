"""Region and subregion pages count the specimens collected in their countries (2026-10-20).

Brian Fisher, 2026-10-20: "this is why no one believes our numbers: they never made sense; we need to
make sure that we say what a number means." Until then the Specimens number of a region or subregion
(and its Imaged specimens and Images) counted every specimen, anywhere in the world, of the names on its
list. Now they count the specimens that the pages of its countries count (collected there, identified to
a name on that country's list), each once (Geolocale.regionSpecimensSql).

Checked on the Eastern Africa subregion page (the subregion of Madagascar; staging data differs from
production, so no fixed numbers):
- Specimens is followed by the new definition;
- Images is not smaller than Imaged specimens (when the image numbers are computed);
- staging, admin login: once a nightly run has counted the subregions by the new rule (compute.log line
  "Subregions populated (specimens collected in their countries)"), Specimens is not more than the sum of
  the Specimens of its countries (the country pages listed on the subregion page for curators, valid
  countries, islands included). Before that run the stored number is still the old one: skipped.
Read-only. Database behaviour: test/org/calacademy/antweb/home/RegionCountDbTest.java (CI).
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from staging_accounts import IS_STAGING, login, needs

PAGE = "/subregion.do?name=Eastern+Africa"
DEFINITION = "(ant specimens collected in this subregion's countries and identified to a name on their lists"
RULE_LOG = "specimens collected in their countries"   # GeolocaleDb.REGION_RULE_LOG

SPECIMENS = re.compile(r"<b>Specimens:</b>&nbsp;([\d,]+)")


def _num(s):
    return int(s.replace(",", ""))


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
    text = _text(page[page.index('id="place-stats"'):])
    m = re.search(r"Specimens:\s*([\d,]+)\s*" + re.escape(DEFINITION), text)
    assert m, f"Specimens is not followed by its number and {DEFINITION!r}: {text[:600]}"


def test_images_not_fewer_than_imaged_specimens(page):
    if "data-images=" not in page:
        pytest.skip("place image numbers not computed yet (nightly set 4 after the deploy)")
    d = dict(re.findall(r'data-([a-z-]+)="(\d+)"', page[page.index('id="place-stats"'):][:400]))
    assert int(d["images"]) >= int(d["imaged"]), d


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
    m = SPECIMENS.search(r.text)
    assert m, "no Specimens number on the subregion page"
    subregion = _num(m.group(1))
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
        cm = SPECIMENS.search(c.text)
        assert cm, f"no Specimens number on the page of {name}"
        total += _num(cm.group(1))
    assert subregion <= total, (f"Eastern Africa shows {subregion} specimens, more than its countries together ({total}): "
                                f"{', '.join(n for _h, n in links)}")

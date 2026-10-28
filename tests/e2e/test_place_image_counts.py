"""Place pages show their statistics in four sections, each with its definition, and the image numbers add up.

Brian Fisher, 2026-10-20: the Madagascar country page showed Images 3,435 although the database has
about 29,700 image files of Madagascar specimens (the number was the sum of per-taxon image counts that
nightly set 2 read before set 4 had filled them in). Images now come straight from the image table
(PlaceImageCountDb, nightly set 4). Brian's layout ("AntWeb Country Pages", 2026-10-20): a table in four
sections, Taxonomic diversity (Subfamilies, Genera, Species / subspecies, Valid species / subspecies),
Endemism and introduced species (Endemic, Introduced), Specimen holdings (Specimens, Imaged specimens) and
Image documentation (Images, with Ant images and Label images under it), each row with its definition.

Checked on the Madagascar country page (staging data differs from production, so no fixed numbers):
the four sections in that order; each row has its definition; Introduced links to the introduced-species
list; Ant images plus Label images equal Images; the views add up to Ant images; Images is not smaller
than Imaged specimens. Before the first image count after the deploy the image rows are not there: that
part skips. Read-only. Database behaviour: test/.../home/PlaceImageCountDbTest.java.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session

PAGE = "/country.do?name=Madagascar"

SECTIONS = ["Taxonomic diversity", "Endemism and introduced species", "Specimen holdings", "Image documentation"]

# data-stat of each row and the start of its definition.
DEFINITIONS = [
    ("subfamilies", "Subfamilies recorded on this country's AntWeb list"),
    ("genera", "Genera recorded on this country's AntWeb list"),
    ("valid", "Currently valid, formally named, living (non-fossil) species and subspecies"),
    ("species", "All species and subspecies recorded on this country's AntWeb list, from any source"),
    ("endemic", "Named species and subspecies recorded for this country and no other country in AntWeb"),
    ("introduced", "Species introduced by humans that are not native to this country's bioregion"),
    ("specimens", "Ant specimens collected in this country and identified to a species, morphospecies, or unidentified taxon"),
]


def _text(html):
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html))).replace("\xa0", " ").strip()


@pytest.fixture(scope="module")
def page():
    r = new_session().get(BASE_URL + PAGE, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{PAGE}: HTTP {r.status_code}"
    if 'class="place_stats"' not in r.text:
        pytest.skip("the statistics table is not deployed on this server yet")
    block = r.text[r.text.index('id="place-stats"'):]
    block = block[:block.index("</table>")]
    rows = {m.group(1): (int(m.group(2)), m.group(3))
            for m in re.finditer(r'<tr data-stat="([a-z-]+)" data-n="(\d+)"[^>]*>(.*?)</tr>', block, re.S)}
    return r.text, block, rows


def test_sections_in_order(page):
    _html, block, _rows = page
    heads = [_text(h) for h in re.findall(r"<th[^>]*>(.*?)</th>", block, re.S)]
    assert heads == SECTIONS, f"section headings {heads}"


def test_diversity_rows_in_order(page):
    """Brian 2026-10-27: the Taxonomic diversity rows grow downward: subfamilies, genera, valid species, all species."""
    _html, block, _rows = page
    order = [m for m in re.findall(r'<tr data-stat="([a-z-]+)"', block) if m in ("subfamilies", "genera", "valid", "species")]
    assert order == ["subfamilies", "genera", "valid", "species"], f"Taxonomic diversity row order {order}"


def test_each_statistic_has_its_definition(page):
    _html, _block, rows = page
    for stat, definition in DEFINITIONS:
        assert stat in rows, f"row {stat} missing; rows: {list(rows)}"
        assert definition in _text(rows[stat][1]), f"{stat}: definition {definition!r} not in {_text(rows[stat][1])!r}"
    assert "project=introducedants" in rows["introduced"][1], "Introduced does not link to the introduced-species list"


def test_images_add_up(page):
    html, _block, rows = page
    if "ant-images" not in rows:
        pytest.skip("place image numbers not computed yet (nightly set 4 or Run after the deploy)")
    images, ant, labels, imaged = rows["images"][0], rows["ant-images"][0], rows["label-images"][0], rows["imaged"][0]
    assert ant + labels == images, f"{ant} ant images + {labels} label images != {images} images"
    views = _text(rows["ant-images"][1])
    view_sum = sum(int(n.replace(",", "")) for n in re.findall(r"([\d,]+) (?:head|profile|dorsal|ventral|field images?|other)\b", views))
    assert view_sum == ant, f"views {views!r} add up to {view_sum}, not {ant}"
    assert images >= imaged, f"Images {images} smaller than Imaged specimens {imaged}"
    d = dict(re.findall(r'data-([a-z-]+)="(\d+)"', html[html.index('id="place-stats"'):][:400]))
    assert int(d["images"]) == images and int(d["ant"]) == ant and int(d["labels"]) == labels and int(d["imaged"]) == imaged
    shown = re.search(r'data-label-only-shown="(\d+)"', rows["imaged"][1])
    if int(d["label-only"]) > 0:
        assert shown and int(shown.group(1)) == int(d["label-only"]), "label-only specimens not shown"
    else:
        assert not shown

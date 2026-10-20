"""Place pages say what each number counts, and the image numbers add up (2026-10-20).

Brian Fisher, 2026-10-20: the Madagascar country page showed Images 3,435 although the database has
about 29,700 image files of Madagascar specimens (the number was the sum of per-taxon image counts that
nightly set 2 read before set 4 had filled them in). Images now come straight from the image table
(PlaceImageCountDb, nightly set 4), and each statistic is followed by its meaning, e.g.

    Specimens: 116,697 (ant specimens collected here, identified to a species, morphospecies or indet name)
    Imaged specimens: 7,623 (specimens with at least one photo of the ant; label-only photos not counted)
    Images: 29,700: 24,264 of the ant (7,849 head, 9,332 profile, 6,806 dorsal, 276 ventral, 1 field photo) and 5,436 of labels

Checked on the Madagascar country page (staging data differs from production, so no fixed numbers):
every statistic is followed by its definition; the views plus the labels add up to Images; Images is
not smaller than Imaged specimens. Before the first nightly run after the deploy the image breakdown is
not computed yet: that part skips. Read-only. Database behaviour: test/.../home/PlaceImageCountDbTest.java.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, new_session

PAGE = "/country.do?name=Madagascar"

# Each statistic and the start of the definition that must follow it.
DEFINITIONS = [
    ("Specimens:", "(ant specimens collected here"),
    ("Subfamilies:", "(subfamilies on this"),
    ("Genera:", "(genera on this"),
    ("Species/Subspecies:", "(species and subspecies on this"),
    ("Valid Species/Subspecies:", "(currently valid, living (non-fossil)"),
    ("Endemic:", "(named species and subspecies, not morphospecies or fossils"),
    ("Introduced:", "(species known to have been introduced by people"),
]

NUMBER = r"([\d,]+)"


def _num(s):
    return int(s.replace(",", ""))


@pytest.fixture(scope="module")
def page():
    r = new_session().get(BASE_URL + PAGE, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{PAGE}: HTTP {r.status_code}"
    if 'id="place-stats"' not in r.text:
        pytest.skip("the statistics with definitions are not deployed on this server yet")
    block = r.text[r.text.index('id="place-stats"'):]
    block = block[:block.index("</div>")]
    text = re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", block))).replace("\xa0", " ")
    return r.text, text


def test_each_statistic_is_followed_by_its_definition(page):
    _html, text = page
    for label, definition in DEFINITIONS:
        m = re.search(re.escape(label) + r"\s*" + NUMBER + r"\s*" + re.escape(definition), text)
        assert m, f"{label} is not followed by its number and {definition!r}: {text[:600]}"


def test_images_breakdown_adds_up(page):
    html, text = page
    if "data-images=" not in html:
        pytest.skip("place image numbers not computed yet (nightly set 4 after the deploy)")
    m = re.search(r"Imaged specimens:\s*" + NUMBER + r"\s*\(specimens with at least one photo of the ant; label-only photos not counted\)", text)
    assert m, f"Imaged specimens and its definition not found: {text[:800]}"
    imaged = _num(m.group(1))
    m = re.search(r"Images:\s*" + NUMBER + r":\s*" + NUMBER + r" of the ant(?: \(([^)]*)\))? and " + NUMBER + " of labels", text)
    assert m, f"Images and its breakdown not found: {text[:800]}"
    images, ant, views, labels = _num(m.group(1)), _num(m.group(2)), m.group(3) or "", _num(m.group(4))
    assert ant + labels == images, f"{ant} of the ant + {labels} of labels != {images} images"
    view_sum = sum(_num(n) for n in re.findall(r"([\d,]+) (?:head|profile|dorsal|ventral|field photos?|other)\b", views))
    assert view_sum == ant, f"views {views!r} add up to {view_sum}, not {ant}"
    assert images >= imaged, f"Images {images} smaller than Imaged specimens {imaged}"
    # The same numbers in the data attributes (what the page was given).
    d = dict(re.findall(r'data-([a-z-]+)="(\d+)"', html[html.index('id="place-stats"'):][:400]))
    assert int(d["images"]) == images and int(d["ant"]) == ant and int(d["labels"]) == labels and int(d["imaged"]) == imaged
    lo = re.search(r"\(" + NUMBER + r" more have only a label photo\)", text)
    if int(d["label-only"]) > 0:
        assert lo and _num(lo.group(1)) == int(d["label-only"]), "label-only specimens not shown"
    else:
        assert not lo

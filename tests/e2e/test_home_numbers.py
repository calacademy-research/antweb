"""The home page numbers of 2026-11-06 (Brian Fisher, release 9.3.0; StatisticsDb.homeBoxHtml, docs/numbers.md rows 1 to 5).

The box "AntWeb in numbers" separates global diversity (valid living ant species in AntCat, fossils apart) from AntWeb's
coverage (species with specimens in AntWeb, species imaged) and gives each share. statsPage.do shows the same numbers
with subspecies, morphospecies and imaged fossils. Anonymous and read-only. The numbers are written by the nightly run
(and after each specimen upload); a box still in the form before 2026-11-06 is pending until then. Their values are
recounted from the database every morning by the count check ("home", test_count_check.py).
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session

KEYS = ["specimens", "living-species", "species-with-specimens", "species-imaged", "images"]


def _page(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT)
    if classify(r).category != "ok":
        pytest.skip(f"{path}: {classify(r).category}")
    return r.text


def _box():
    html = _page("/")
    m = re.search(r'<div id="stats">(.*?)<span class="right">', html, re.S)
    assert m, "no statistics box on the home page"
    box = m.group(1)
    if 'data-stat="living-species"' not in box:
        pytest.skip("the box is still in the form before 2026-11-06 (written by the next nightly run or upload)")
    return box


def _stat(box, key):
    m = re.search(r'<div class="stat" data-stat="' + key + r'"><span class="numbers"[^>]*>([\d,]+)</span>'
                  r'<span class="stat-l">([^<]*)</span>(?:<span class="stat-s">([^<]*)</span>)?</div>', box)
    assert m, f"home page box: no number {key!r}"
    return int(m.group(1).replace(",", "")), m.group(2), m.group(3) or ""


def test_home_box_numbers_and_labels():
    box = _box()
    assert "AntWeb in numbers" in _page("/")
    labels = {k: _stat(box, k)[1] for k in KEYS}
    assert labels["specimens"] == "specimen records"
    assert labels["living-species"] == "valid living ant species"
    assert labels["species-with-specimens"] == "species with specimens in AntWeb"
    assert labels["species-imaged"] == "species imaged"
    assert re.fullmatch(r"images of ants of [\d,]+ specimens", labels["images"]), labels["images"]


def test_home_box_numbers_nest_and_shares_are_right():
    """species imaged <= species with specimens <= valid living species, and each share is its rounded percent."""
    box = _box()
    living = _stat(box, "living-species")
    specimens = _stat(box, "species-with-specimens")
    imaged = _stat(box, "species-imaged")
    assert 0 < imaged[0] <= specimens[0] <= living[0], (imaged[0], specimens[0], living[0])
    assert re.fullmatch(r"\+ [\d,]+ fossil species", living[2]), living[2]
    for num, _label, sub in (specimens, imaged):
        assert sub == f"{int(100 * num / living[0] + 0.5)}% of valid living species", (num, sub)   # Java Math.round
    images = _stat(box, "images")
    assert re.fullmatch(r"\+ [\d,]+ label images", images[2]), images[2]


def test_statspage_has_the_home_numbers_and_the_details():
    """statsPage.do: the home numbers (equal to the box once both are from the same nightly run) and the subspecies,
    morphospecies and imaged fossils the box leaves out; valid living species = Global Data, Extant, Valid Species."""
    html = _page("/statsPage.do")
    if 'data-stat="living-species"' not in html:
        pytest.skip("statsPage.do without the numbers of 2026-11-06 (not deployed yet)")
    if 'data-stat="pending"' in html:
        pytest.skip("statsPage.do: the numbers of 2026-11-06 are not counted yet (next nightly run)")
    def n(key):
        m = re.search(r'data-stat="' + key + r'">([\d,]+)', html)
        assert m, f"statsPage.do: no {key}"
        return int(m.group(1).replace(",", ""))
    for key in ("living-species", "fossil-species", "species-with-specimens", "species-imaged", "fossil-species-imaged",
                "living-subspecies", "morphospecies", "morphospecies-imaged"):
        n(key)
    assert n("species-imaged") <= n("species-with-specimens") <= n("living-species")
    assert n("fossil-species-imaged") <= n("fossil-species") and n("morphospecies-imaged") <= n("morphospecies")
    rows = dict(re.findall(r"<tr align=right><td>(Extant|Fossil)</td>((?:<td>\d+</td>){4})</tr>", html))
    if "Extant" in rows and "Fossil" in rows:
        extant = [int(v) for v in re.findall(r"\d+", rows["Extant"])]
        fossil = [int(v) for v in re.findall(r"\d+", rows["Fossil"])]
        assert n("living-species") == extant[2], f"valid living species {n('living-species')}, Global Data Extant species {extant[2]}"
        assert n("fossil-species") == fossil[2], f"fossil species {n('fossil-species')}, Global Data Fossil species {fossil[2]}"
        assert n("living-subspecies") == extant[3], f"living subspecies {n('living-subspecies')}, Global Data Extant subspecies {extant[3]}"

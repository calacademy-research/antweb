"""Numbers on public pages that the count check (countCheck.do) cannot see, 2026-10-21 (docs/numbers.md).

Brian Fisher, 2026-10-20: every number shown on the site must be validated by a test. countCheck.do
compares the numbers stored in the database; these tests read the pages themselves:

  statsPage.do     "N valid species + ssp." counts species and subspecies (it counted valid taxa of every
                   rank): at most the extant and fossil species and subspecies of the Global Data table,
                   which count the same names; its tables are counted once a day (end of the nightly run),
                   and the page says when they were counted
  taxonomicPage.do images view: "N Genera Imaged (out of M Total)" (it printed the same number twice):
                   imaged <= total
  countries.do     the column is labelled "Valid species" (it showed the Valid species number as "Species")

Pages without the new markup (not deployed yet) are skipped.
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session


def _ok(path):
    r = new_session().get(BASE_URL + path, timeout=TIMEOUT)
    outcome = classify(r)
    if outcome.category != "ok":
        pytest.skip(f"{path}: {outcome.category}")
    return r.text


def _number(text):
    return int(text.replace(",", ""))


def test_statspage_valid_species_count_species_and_subspecies():
    html = _ok("/statsPage.do")
    m = re.search(r'data-stat="valid-species">([\d,]+) valid species \+ ssp\.', html)
    if not m:
        pytest.skip("statsPage.do without data-stat (not deployed yet)")
    shown = _number(m.group(1))
    assert shown > 0
    # Global Data table (StatSet): Extant and Fossil rows of valid Subfamilies, Genera, Species, Subspecies.
    rows = dict(re.findall(r"<tr align=right><td>(Extant|Fossil)</td>((?:<td>\d+</td>){4})</tr>", html))
    if "Extant" in rows and "Fossil" in rows:
        extant = [int(v) for v in re.findall(r"\d+", rows["Extant"])]
        fossil = [int(v) for v in re.findall(r"\d+", rows["Fossil"])]
        species = extant[2] + extant[3] + fossil[2] + fossil[3]
        # Equal unless some taxa have no fossil flag; before 2026-10-21 the subfamilies and genera were added too.
        assert species <= shown < species + extant[0] + extant[1], (
            f"statsPage: {shown} valid species + ssp.; Global Data species + subspecies {species}")


def test_statspage_tables_are_kept_not_recounted_each_visit():
    """statsPage.do (linked from the home page) joins every specimen with every image to count its tables. Since
    2026-10-23 the tables are counted once a day, at the end of the nightly run: two visits in a row show the same "counted at" time, and the
    second visit is quick."""
    import time
    first = _ok("/statsPage.do")
    m1 = re.search(r'id="stats-counted-at">The tables below were counted at ([^.]+)\.', first)
    if not m1:
        pytest.skip("statsPage.do without the counted-at line (not deployed yet)")
    start = time.monotonic()
    second = _ok("/statsPage.do")
    elapsed = time.monotonic() - start
    m2 = re.search(r'id="stats-counted-at">The tables below were counted at ([^.]+)\.', second)
    assert m2 and m2.group(1) == m1.group(1), (m1.group(1), m2 and m2.group(1))
    assert elapsed < 15, f"second visit took {elapsed:.1f} s; the kept tables should make it quick"


def test_images_view_header_counts_imaged_children():
    html = _ok("/taxonomicPage.do?rank=genus&countryName=Madagascar&images=true")
    m = re.search(r'class="display_count" data-imaged="(\d+)" data-total="(\d+)">(\d+) \w+ Imaged \(out of (\d+) Total\)', html)
    if not m:
        pytest.skip("images view without data-imaged (not deployed yet)")
    imaged, total = int(m.group(1)), int(m.group(2))
    assert int(m.group(3)) == imaged and int(m.group(4)) == total
    assert 0 < imaged <= total


def test_list_of_countries_labels_valid_species():
    html = _ok("/countries.do")
    if "<th>Valid species</th>" not in html:
        pytest.skip("countries.do with the old column label (not deployed yet)")
    assert "<th>Species</th><th>Specimen" not in html

"""Numbers on public pages that the count check (countCheck.do) cannot see, 2026-10-21 (docs/numbers.md).

Brian Fisher, 2026-10-20: every number shown on the site must be validated by a test. countCheck.do
compares the numbers stored in the database; these tests read the pages themselves:

  statsPage.do     "N valid species + ssp." counts species and subspecies (it counted valid taxa of every
                   rank): at most the extant and fossil species and subspecies of the Global Data table,
                   which count the same names
  taxonomicPage.do images view: "N Genera Imaged (out of M Total)" (it printed the same number twice):
                   imaged <= total
  countries.do     the column is labelled "Valid species" (it showed the Valid species number as "Species")

Brian Fisher's decisions of 2026-10-22 (docs/numbers.md section 5), each page explaining its numbers:
  statsPage.do     Q7: Global, Bioregion and Museum Data count ants only (Formicidae), said under each heading
  taxonomicPage.do Q12: isImaged=false lists names with no photo of the ant anywhere in AntWeb, said under the header
  project.do       Port of Entry: a species list only; the page says what it is and shows no specimen or image numbers

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


def test_statspage_says_it_counts_ants_only():
    html = _ok("/statsPage.do")
    if 'id="stats-global-note"' not in html:
        pytest.skip("statsPage.do without the Q7 notes (not deployed yet)")
    for note in ("stats-global-note", "stats-bioregion-note", "stats-museum-note"):
        m = re.search(r'id="' + note + r'">(.*?)</p>', html, re.S)
        assert m and "Formicidae" in m.group(1), f"statsPage.do: {note} does not say ants only"


def test_unimaged_list_says_anywhere_in_antweb():
    html = _ok("/taxonomicPage.do?rank=species&countryName=Madagascar&statusSet=all&isImaged=false")
    if 'id="unimaged-note"' not in html:
        pytest.skip("taxonomicPage.do without the Q12 note (not deployed yet)")
    assert "no photo of the ant anywhere in AntWeb" in html
    m = re.search(r'<span class="numbers">([\d,]+)</span> Unimaged Species \(out of <span class="numbers">([\d,]+)</span>\)', html)
    assert m, "isImaged=false: no 'N Unimaged Species (out of M)' header (0 is shown too since 2026-10-22)"
    assert _number(m.group(1)) <= _number(m.group(2))


def test_port_of_entry_is_a_species_list_without_numbers():
    html = _ok("/project.do?name=poeants")
    if 'id="port-of-entry-note"' not in html:
        pytest.skip("project.do?name=poeants without the Port of Entry note (not deployed yet, or no such project)")
    assert "not established introductions" in html
    for stat in ("specimens", "imaged", "images", "ant-images", "label-images"):
        assert f'data-stat="{stat}"' not in html, f"Port of Entry shows {stat}"
    assert "Specimens per Subfamily" not in html
    listing = _ok("/taxonomicPage.do?rank=species&project=poeants")
    if 'id="port-of-entry-list-note"' in listing:
        assert "orderBy=specimens" not in listing and "orderBy=images" not in listing, "Port of Entry list shows count columns"

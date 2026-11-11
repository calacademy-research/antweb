"""The Fossil Ants page: every number of its Taxonomic diversity table is the header of the list it opens.

Brian Fisher, 2026-11-11 (production): project.do?name=fossilants said Genera 259 and opened a list headed "258 Valid
(with fossils) Genera"; "Valid species / subspecies 3" opened a list headed "868 Valid (with fossils) Species"; every
line said "in its default view, Curated", a view Fossil Ants does not have.

Fossil Ants and the AntCat list (worldants) keep their own views, Valid (with fossils) (the default), Valid extant and
Valid fossil (StatusSet.OWN_SET_CHOICES). Since 2026-11-11:
  - Subfamilies, Genera and Species are the rows of the lists in that default view, counted by the daily update with
    the list's own code (PlaceViewTotalDb counts the site-wide lists too) and again when set 5's list check takes a
    genus without species off the list; their lines name that view and open it;
  - the line of valid living species is named "Valid living species / subspecies", stands after the Species line and
    opens the view Valid extant, whose header is its number.

Read-only, anonymous. The numbers are those of the last daily update: until one has run with this code the page shows
the project's stored counts (data-view-counted="false") and the three list numbers are skipped, not failed.
"""
import re
from urllib.parse import parse_qs, urlparse

import pytest

import numbers_sample as ns
from public_pages import attr_unescape

ROW = re.compile(r'<tr data-stat="(subfamilies|genera|species|valid)" data-n="(\d+)"><td class="ps_name">([^<]*)</td>'
                 r'<td class="ps_num"><a href="([^"]+)"[^>]*>[\d,]+</a></td><td class="ps_def">(.*?)</td></tr>', re.S)
DEFAULT_VIEW = "Valid (with fossils)"
LIVING_VIEW = "valid extant"
RANK = {"subfamilies": "subfamily", "genera": "genus", "species": "species", "valid": "species"}


def _page(project):
    html = ns.fetch(f"/project.do?name={project}")
    if html is None or 'id="place-stats"' not in html:
        pytest.skip(f"project.do?name={project} is not a page with the statistics table on this server")
    rows = {stat: {"n": int(n), "name": name, "href": attr_unescape(href), "def": ns.text_of(text)}
            for stat, n, name, href, text in ROW.findall(html)}
    if "species" not in rows:
        pytest.skip(f"project.do?name={project} has no Species line (an empty list on this server)")
    if "Valid (with fossils)" not in html:
        pytest.skip(f"project.do?name={project}: the page does not name its own views yet (this code is not deployed here)")
    return html, rows


def _view(row):
    query = parse_qs(urlparse(row["href"]).query)
    return (query.get("rank") or [""])[0], (query.get("statusSet") or [""])[0]


@pytest.mark.parametrize("project", ["fossilants", "worldants"])
def test_lines_name_and_open_the_lists_own_views(project):
    """Each line opens the list of its rank in the view it names: the three list numbers the default view Valid (with
    fossils), the valid living species the view Valid extant. No line names Curated, a view these lists do not have."""
    html, rows = _page(project)
    for stat in ("subfamilies", "genera", "species"):
        assert stat in rows, f"{project}: no {stat} line with a link"
        rank, view = _view(rows[stat])
        assert (rank, view) == (RANK[stat], DEFAULT_VIEW), f"{project} {stat}: opens rank={rank} statusSet={view!r}"
        assert f"in its default view, {DEFAULT_VIEW} (the number opens it)" in rows[stat]["def"], rows[stat]["def"]
        assert "Curated" not in rows[stat]["def"] and "curated" not in rows[stat]["def"], rows[stat]["def"]
    assert "living and fossil" in rows["species"]["def"], rows["species"]["def"]
    if "valid" in rows:
        rank, view = _view(rows["valid"])
        assert (rank, view) == ("species", LIVING_VIEW), f"{project} valid: opens rank={rank} statusSet={view!r}"
        assert rows["valid"]["name"] == "Valid living species / subspecies", rows["valid"]["name"]
        assert "its view Valid extant (the number opens it)" in rows["valid"]["def"], rows["valid"]["def"]
        # after the Species line: the living species are a part of it
        assert html.index('<tr data-stat="species"') < html.index('<tr data-stat="valid"'), "the living species stand before the Species line"


def test_fossil_ants_numbers_are_the_headers_of_their_lists():
    """Subfamilies, Genera, Species and Valid living species of the Fossil Ants page = the header of the list each
    number opens = the rows that list shows."""
    html, rows = _page("fossilants")
    counted = 'data-view-counted="true"' in html
    wrong, pending = [], []
    for stat in ("subfamilies", "genera", "species", "valid"):
        if stat not in rows:
            continue                      # no valid living species on the list: the line is not shown
        link = urlparse(rows[stat]["href"])
        page = ns.fetch("/taxonomicPage.do?" + link.query)
        assert page is not None, f"the {stat} list ({rows[stat]['href']}) could not be read"
        n, text = ns.header(page)
        assert n is not None, f"the {stat} list has no header ({text!r})"
        listed = len(ns.list_rows(page))
        if listed != n:
            wrong.append(f"{stat}: header {text!r}, {listed} rows listed")
        expected_words = "Valid extant" if stat == "valid" else DEFAULT_VIEW
        if expected_words not in text:
            wrong.append(f"{stat}: the list's header {text!r} is not of the view {expected_words}")
        if n != rows[stat]["n"]:
            line = f"{stat}: {rows[stat]['n']} on the page, its list's header {text!r}"
            (pending if stat != "valid" and not counted else wrong).append(line)
    assert not wrong, ("Fossil Ants: the page's numbers are not the headers of the lists they open (the numbers are those of "
                       "the last daily update; set 5's list check counts a project again when it takes a row off its list): "
                       + "; ".join(wrong))
    if pending:
        pytest.skip("Fossil Ants' list totals are not counted yet (the first daily update with this code counts them): "
                    + "; ".join(pending))

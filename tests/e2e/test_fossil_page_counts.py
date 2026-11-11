"""The Fossil Ants and AntCat list pages: the lines of their Taxonomic diversity table follow the one rule of every
place page, in the words of their own views.

Brian Fisher, 2026-11-11 (production): project.do?name=fossilants said Genera 259 and opened a list headed "258 Valid
(with fossils) Genera"; "Valid species / subspecies 3" opened a list headed "868 Valid (with fossils) Species"; every
line said "in its default view, Curated", a view Fossil Ants does not have. And of a first correction: "fossil ants
are fossil: how can you mention living in the same phrase"; "we want valid species first - that is the rule for all
lists, after that is with morpho - like in all the lists".

The rule (web/overview-body.jsp, docs/numbers.md):
 1. Every number on a place page is the header of the list its link opens.
 2. The species lines are in the same order on every place page: first "Valid species / subspecies", then
    "Species / subspecies / morphospecies".
 3. "Valid species / subspecies" = the rows of the list's Valid view. On the two lists with their own views (Fossil
    Ants, AntCat list) the Valid view is Valid (with fossils), so the number is the species total of that view, the
    link opens that view, and the text names it.
 4. "Species / subspecies / morphospecies" = the rows of the list's default view (Curated; Valid (with fossils) on the
    two own-view lists, so there both lines show the same number, and the text says so).

This file checks the lines of the two pages: order, names, links, texts (no word of living, extant or non-fossil names),
and that both species lines show one number. That each number is the header of the list it opens (rule 1) is checked
for these two pages on every run by test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind.
Read-only, anonymous.
"""
import re
from urllib.parse import parse_qs, urlparse

import pytest

import numbers_sample as ns
from public_pages import attr_unescape

ROW = re.compile(r'<tr data-stat="(subfamilies|genera|species|valid)" data-n="(\d+)"><td class="ps_name">([^<]*)</td>'
                 r'<td class="ps_num"><a href="([^"]+)"[^>]*>[\d,]+</a></td><td class="ps_def">(.*?)</td></tr>', re.S)
OWN_VIEW = "Valid (with fossils)"
RANK = {"subfamilies": "subfamily", "genera": "genus", "species": "species", "valid": "species"}
NAMES = {"subfamilies": "Subfamilies", "genera": "Genera", "valid": "Valid species / subspecies",
         "species": "Species / subspecies / morphospecies"}
NOT_SAID = ("living", "extant", "non-fossil", "extinct", "curated")


def lines(project):
    """(page HTML, {stat: number, name, link, text}) of a project page's Taxonomic diversity lines that have a link."""
    html = ns.fetch(f"/project.do?name={project}")
    if html is None or 'id="place-stats"' not in html:
        pytest.skip(f"project.do?name={project} is not a page with the statistics table on this server")
    rows = {stat: {"n": int(n), "name": name, "href": attr_unescape(href), "def": ns.text_of(text)}
            for stat, n, name, href, text in ROW.findall(html)}
    if "species" not in rows:
        pytest.skip(f"project.do?name={project} has no Species line (an empty list on this server)")
    return html, rows


def view_of(row):
    query = parse_qs(urlparse(row["href"]).query)
    return (query.get("rank") or [""])[0], (query.get("statusSet") or [""])[0]


@pytest.mark.parametrize("project", ["fossilants", "worldants"])
def test_lines_follow_the_rule_in_the_words_of_the_lists_own_view(project):
    html, rows = lines(project)
    assert "valid" in rows, f"{project}: no Valid species / subspecies line (the list has species: {rows['species']['n']})"
    # rule 2: the same four lines in the same order as on every place page, Valid before Species
    order = [m.group(1) for m in re.finditer(r'<tr data-stat="(subfamilies|genera|valid|species)"', html)]
    assert order == ["subfamilies", "genera", "valid", "species"], f"{project}: the lines are in the order {order}"
    for stat, name in NAMES.items():
        assert rows[stat]["name"] == name, f"{project}: the {stat} line is named {rows[stat]['name']!r}"
    # rules 3 and 4: every line opens the list's own view and names it; both species lines show that view's species total
    for stat in ("subfamilies", "genera", "valid", "species"):
        rank, view = view_of(rows[stat])
        assert (rank, view) == (RANK[stat], OWN_VIEW), f"{project} {stat}: the number opens rank={rank} statusSet={view!r}"
        assert OWN_VIEW + " (the number opens it)" in rows[stat]["def"], f"{project} {stat}: {rows[stat]['def']!r}"
        for word in NOT_SAID:
            assert word not in rows[stat]["def"].lower(), f"{project} {stat}: the text says {word!r}: {rows[stat]['def']!r}"
    assert rows["valid"]["n"] == rows["species"]["n"], (
        f"{project}: Valid species / subspecies {rows['valid']['n']}, Species / subspecies / morphospecies {rows['species']['n']}: "
        "on a list with its own views both are the species total of Valid (with fossils)")
    assert rows["valid"]["def"].startswith("Currently valid, formally named species and subspecies on this project's AntWeb list: "
                                           "its view Valid (with fossils) (the number opens it)."), rows["valid"]["def"]
    assert "so this number equals Valid species / subspecies." in rows["species"]["def"], rows["species"]["def"]


def test_other_project_pages_read_as_before():
    """A project with the four views (the day's project of the sample): its lines name the view Curated, the Valid line
    the view Valid and valid living names, as before 2026-11-11."""
    projects = ns.pick(ns.projects(), 1, "fossil-page-other")
    if not projects:
        pytest.skip("no project found on projects.do " + ns.day_note())
    p = projects[0]
    html = ns.fetch(p.page)
    if html is None:
        pytest.skip(f"{p}: the page could not be read")
    rows = {stat: {"n": int(n), "name": name, "href": attr_unescape(href), "def": ns.text_of(text)}
            for stat, n, name, href, text in ROW.findall(html)}
    if "species" not in rows:
        pytest.skip(f"{p}: no Species line")
    for stat in ("subfamilies", "genera", "species"):
        assert view_of(rows[stat]) == (RANK[stat], "curated"), f"{p} {stat}: {view_of(rows[stat])}"
        assert "in its default view, Curated (the number opens it): " in rows[stat]["def"], rows[stat]["def"]
    if "valid" in rows:
        assert view_of(rows["valid"]) == ("species", "valid"), view_of(rows["valid"])
        assert rows["valid"]["def"].startswith("Currently valid, formally named, living (non-fossil) species and subspecies on"), rows["valid"]["def"]
        assert html.index('<tr data-stat="valid"') < html.index('<tr data-stat="species"')

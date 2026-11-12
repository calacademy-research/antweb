"""The Fossil Ants and AntCat list pages: the lines of their Taxonomic diversity table are those of every place page,
and their lists have the views Valid and Curated.

Brian Fisher, 2026-11-11 (production): project.do?name=fossilants said Genera 259 and opened a list of 258; "Valid
species / subspecies 3" opened a list of 868 rows, 856 of them valid names and 12 morphospecies. "fossil ants are fossil:
how can you mention living in the same phrase"; "we want valid species first - that is the rule for all lists, after
that is with morpho - like in all the lists".

The rule (web/overview-body.jsp, docs/numbers.md):
 1. Every number on a place page is the header of the list its link opens.
 2. The species lines are in the same order on every place page: first "Valid species / subspecies", then
    "Species / subspecies / morphospecies".
 3. "Valid species / subspecies" = the rows of the list's view Valid (statusSet=valid).
 4. "Species / subspecies / morphospecies" = the rows of the list's default view, Curated (statusSet=curated).
The two lists have the same two views as every list, counted with their fossil names (StatusSet.OWN_SET_CHOICES):
Valid = the names of status valid, Curated = those and the morphotaxa on the list; and Valid extant, Valid fossil. The
old address value "Valid (with fossils)" opens Curated.

This file checks, on the two pages: the lines (order, names, links, texts: no word of living, extant or non-fossil
names), each of the four numbers = the header of the list its own link opens = the rows listed, valid <= curated, the
selector of their lists, and the old address value. test_numbers_lists.py checks the numbers on every run too.
Read-only, anonymous.
"""
import re
from urllib.parse import parse_qs, urlparse

import pytest

import numbers_sample as ns
from public_pages import attr_unescape

ROW = re.compile(r'<tr data-stat="(subfamilies|genera|species|valid)" data-n="(\d+)"><td class="ps_name">([^<]*)</td>'
                 r'<td class="ps_num"><a href="([^"]+)"[^>]*>[\d,]+</a></td><td class="ps_def">(.*?)</td></tr>', re.S)
OLD_VIEW = "Valid (with fossils)"
RANK = {"subfamilies": "subfamily", "genera": "genus", "species": "species", "valid": "species"}
VIEW = {"subfamilies": "curated", "genera": "curated", "species": "curated", "valid": "valid"}
NAMES = {"subfamilies": "Subfamilies", "genera": "Genera", "valid": "Valid species / subspecies",
         "species": "Species / subspecies / morphospecies"}
NOT_SAID = ("living", "extant", "non-fossil", "extinct", "all specimen names")
SELECTOR = ["Valid", "Curated", "Valid extant", "Valid fossil"]
CHOICE = re.compile(r'<a class="view-btn[^"]*" data-status-choice="([^"]*)"[^>]*>(.*?)</a>', re.S)


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
def test_lines_are_those_of_every_place_page(project):
    html, rows = lines(project)
    if 'data-view-counted="true"' not in html:
        pytest.fail(f"{project}: its list totals are not counted (data-view-counted is not true); the daily update counts "
                    "them, on the day of the change an administrator runs /utilData.do?action=placeViewTotals once")
    assert "valid" in rows, f"{project}: no Valid species / subspecies line (the list has species: {rows['species']['n']})"
    # rule 2: the same four lines in the same order as on every place page, Valid before Species
    order = [m.group(1) for m in re.finditer(r'<tr data-stat="(subfamilies|genera|valid|species)"', html)]
    assert order == ["subfamilies", "genera", "valid", "species"], f"{project}: the lines are in the order {order}"
    for stat, name in NAMES.items():
        assert rows[stat]["name"] == name, f"{project}: the {stat} line is named {rows[stat]['name']!r}"
    # the same links as on every place page; no word of living, extant or non-fossil names in the four texts
    for stat in ("subfamilies", "genera", "valid", "species"):
        assert view_of(rows[stat]) == (RANK[stat], VIEW[stat]), f"{project} {stat}: the number opens {view_of(rows[stat])}"
        for word in NOT_SAID:
            assert word not in rows[stat]["def"].lower(), f"{project} {stat}: the text says {word!r}: {rows[stat]['def']!r}"
        if stat != "valid":
            assert "in its default view, Curated (the number opens it): " in rows[stat]["def"], rows[stat]["def"]
    assert rows["valid"]["def"] == ("Currently valid, formally named species and subspecies on this project's AntWeb list: its view "
                                    "Valid (the number opens it). Excludes morphospecies and unidentified taxa."), rows["valid"]["def"]
    # rule 1: each of the four numbers = the header of the list its own link opens = the rows that list shows
    # (the AntCat list's species lists are long: a list too slow to read is not compared, and said at the end)
    headers, slow = {}, []
    for stat in ("subfamilies", "genera", "valid", "species"):
        page = ns.fetch(rows[stat]["href"])
        if page is None:
            slow.append(stat)
            continue
        n, text = ns.header(page)
        listed = len(ns.list_rows(page))
        assert n is not None, f"{project} {stat}: the list it opens has no header ({text!r})"
        assert listed == n, f"{project} {stat}: header {text!r}, {listed} rows listed"
        assert n == rows[stat]["n"], f"{project} {stat}: {rows[stat]['n']} on the page, the list it opens says {text!r}"
        headers[stat] = text
    # the headers in the words of every list
    if "valid" in headers:
        assert re.match(r"[\d,]+ Valid species \(of [\d,]+ species, valid and curated\)$", headers["valid"]), headers["valid"]
    if "species" in headers:
        assert re.match(r"[\d,]+ Species \(valid and curated\)$", headers["species"]), headers["species"]
    for stat in ("genera", "subfamilies"):
        assert stat not in headers or headers[stat].endswith("(valid and curated)"), headers[stat]
    # every valid name is in Curated; Fossil Ants has morphospecies (12 on 2026-11-11: 856 and 868), the AntCat list none
    assert rows["valid"]["n"] <= rows["species"]["n"], f"{project}: Valid {rows['valid']['n']} > Species {rows['species']['n']}"
    if slow:
        pytest.skip(f"{project}: lines, links and texts checked; the lists of {slow} could not be read (slow or an error page)")


@pytest.mark.parametrize("project", ["fossilants", "worldants"])
def test_the_lists_have_the_views_valid_and_curated(project):
    """The selector of the two lists: Valid, Curated, Valid extant, Valid fossil; Curated opens first; the old address
    value "Valid (with fossils)" opens Curated (no broken links); the views nest."""
    base = f"/taxonomicPage.do?rank=genus&project={project}"   # the genus lists: the AntCat list's species list is long
    default = ns.fetch(base)
    if default is None:
        pytest.skip(f"{base} could not be read (slow or an error page)")
    labels = [ns.text_of(label) for _value, label in CHOICE.findall(default)]
    assert labels == SELECTOR, f"{project}: the selector shows {labels}"
    assert [value for value, _label in CHOICE.findall(default)] == ["valid", "curated", "valid extant", "valid fossil"]
    current = re.search(r'id="change_status"[^>]*data-status-set="([^"]*)"', default)
    assert current and current.group(1) == "curated", f"{project}: the list opens in {current.group(1) if current else None!r}"
    n_default, text_default = ns.header(default)
    assert OLD_VIEW not in text_default and text_default.endswith("(valid and curated)"), f"{project}: the list is headed {text_default!r}"
    counts = {}
    for value in ("valid", "curated", "Valid (with fossils)", "valid extant", "valid fossil"):
        page = ns.fetch(base + "&statusSet=" + value.replace(" ", "%20").replace("(", "%28").replace(")", "%29"))
        if page is None:
            pytest.skip(f"{base} statusSet={value} could not be read (slow or an error page)")
        counts[value], text = ns.header(page)
        assert counts[value] is not None, f"{project} {value}: no header ({text!r})"
        assert counts[value] == len(ns.list_rows(page)), f"{project} {value}: header {text!r}, {len(ns.list_rows(page))} rows listed"
        if value == "Valid (with fossils)":
            assert text == text_default, f"{project}: the old address value shows {text!r}, the default view {text_default!r}"
    assert counts["curated"] == n_default == counts["Valid (with fossils)"], counts
    assert counts["valid"] <= counts["curated"], counts
    # Each of the two is part of Valid. They are not disjoint at genus rank: the view Valid fossil also lists the living
    # genera with valid fossil species on the list (Brian Fisher, 2026-11-06, TaxaPage.genusAwareCriteria), and Valid
    # extant lists the living genera (staging 2026-11-11, AntCat list: 348 extant + 251 fossil of 525 valid genera).
    assert counts["valid extant"] <= counts["valid"] and counts["valid fossil"] <= counts["valid"], f"{project}: extant and fossil are valid names: {counts}"


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

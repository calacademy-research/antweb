"""The genus and species lists in the four views (Brian Fisher, 2026-11-02; docs/numbers.md "Views of lists",
docs/flows/places.md "Where the names come from"; the design of 2026-10-30 it replaces in brackets).

  1. Views. Four buttons, Valid | Curated | All specimen names | Fossil (Curated the default) replace the status menu and
     the "Names from" selector (and Show Specimen Taxa before it); old links (specimen=true, statusSet=validmorpho)
     open All specimen names. A genus known in a place only from specimens identified to genus ("Lividopone (indet)"
     in Mozambique) is on the genus list in All specimen names only.
  2. Genus rows, one line each: Taxon name | Author date | Species (in the view, or "-") | Other specimens (the
     specimens collected here under names not in the view, linked to them; empty when 0) | Images | Map | Source (an
     icon: specimen, curated, literature). Acropyga in Mozambique (morphospecies collected there, no species on the
     list): in Valid with no species and its Other specimens, the link listing Acropyga specimens. ("No species on the
     list · N specimens collected here" and "+ N specimens of other names" are gone.)
  3. Genus page in a place, Valid and Curated: "Specimens collected here not on the species list: N (names: ...)",
     above the notes on specimen and image counts. Source icons: specimen, curated, literature (Bolton's catalogue)
     and AntCat (the AntCat import, title "From the AntCat import"). The view Curated was first called Vetted:
     statusSet=vetted still opens it. Fossil is shown only when the list has fossil names.
  4. Header: line 1 the header and the buttons, line 2 the small links, then a closed "About this list" that explains
     the views, the columns and the source icons.

Read-only and anonymous. The data on staging is a copy of production; a test skips with a reason when its sample has
no data. Only staging can confirm these (the pages are rendered by the server); the rules are in the JUnit tests
ListViewTest and home.PlaceTaxonNotesDbTest.
"""
import re
from html import unescape

import pytest

import numbers_sample as ns
from antweb import new_session

MOZ_GENERA = "/taxonomicPage.do?rank=genus&countryName=Mozambique"
MOZ_SPECIES = "/taxonomicPage.do?rank=species&countryName=Mozambique"
OTHER_LINK = re.compile(r'<a rel="nofollow" class="other-names" data-other-specimens="(\d+)" title="[^"]*" href="([^"]+)">([^<]+)</a>')
GONE = ("No species on the list", "specimens of other names", 'id="names_from_toggle"', "Show Specimen Taxa</a>",
        "Hide Specimen Taxa", "click on Show Specimen Taxa")


def genus_row(html, genus):
    for row in ns.list_rows(html):
        h = unescape(row["html"]).lower()
        if f"genus={genus}&" in h or f"genus={genus}\"" in h:
            return row["html"]
    return None


def specimens_listed(html):
    """The specimen links of an advanced search result page."""
    return re.findall(r"specimen\.do\?(?:name|code)=([^\"'&>]+)", html)


def current_view(html):
    m = re.search(r'<span id="change_status" class="view-buttons" data-status-set="([^"]*)"', html or "")
    return m.group(1) if m else None


def test_mozambique_acropyga_row_links_its_specimens():
    html = ns.get(MOZ_GENERA + "&statusSet=valid", new_session())
    row = genus_row(html, "acropyga")
    if row is None:
        pytest.skip("Acropyga is not on the Mozambique genus list (Valid) on this server")
    m = OTHER_LINK.search(row)
    assert m, f"the Acropyga row has no Other specimens link: {ns.text_of(row)[:200]}"
    n, href, text = int(m.group(1)), unescape(m.group(2)), m.group(3)
    assert n > 0 and ns.n(text) == n, text
    assert "advancedSearch.do?" in href and "country=Mozambique" in href and "genus=acropyga" in href, href
    result = ns.get(href.replace(ns.BASE_URL, "") if href.startswith(ns.BASE_URL) else href)
    assert specimens_listed(result), f"the Acropyga link lists no specimen: {href}"
    assert "acropyga" in ns.text_of(result).lower(), href


def test_genus_rows_have_the_columns_of_the_views():
    """The Mozambique genus list (Curated): the Other specimens column, a Species cell that is a number or a dash, a
    source icon with its word as title; none of the texts of 2026-10-30."""
    html = ns.get(MOZ_GENERA, new_session())
    assert current_view(html) == "curated", current_view(html)
    assert '<div class="list_extras other">' in html, "no Other specimens column"
    for gone in GONE:
        assert gone not in html, gone
    rows = ns.list_rows(html)
    if not rows:
        pytest.skip("no genus on the Mozambique list on this server")
    for r in rows:
        cells = re.findall(r'<div class="list_extras specimens">(.*?)</div>', r["html"], re.S)
        assert cells, ns.text_of(r["html"])[:120]
        species = ns.text_of(cells[-1])
        assert re.fullmatch(r"[\d,]+|–", species), f"Species cell {species!r}: {ns.text_of(r['html'])[:120]}"
        src = re.search(r'<span class="src-icon src-(\w+)" data-source="(\w+)" title="([^"]+)"', r["html"])
        titles = {"specimen": "Specimen", "curated": "Curated", "literature": "Literature", "antcat": "From the AntCat import"}
        assert src and src.group(1) == src.group(2) and titles.get(src.group(1)) == src.group(3), ns.text_of(r["html"])[:120]


def test_genus_known_only_from_specimens_identified_to_genus_is_in_all_only():
    """Lividopone in Mozambique, known there only from Lividopone (indet): not on the Valid and Curated genus lists, on
    All specimen names (when its specimens there are not red-flagged: red flags never count)."""
    all_html = ns.get(MOZ_GENERA + "&statusSet=all", new_session())
    if genus_row(all_html, "lividopone") is None:
        pytest.skip("Lividopone is not on the Mozambique genus list in All specimen names (none collected there, or red-flagged)")
    species = ns.get(MOZ_SPECIES + "&statusSet=all", new_session())
    names = [r["name"] for r in ns.list_rows(species) if r["name"] and r["name"].startswith("dorylinaelividopone ")]
    if any(not n.endswith("(indet)") for n in names):
        pytest.skip(f"Lividopone has other names in Mozambique on this server: {names}")
    for view in ("valid", "curated"):
        assert genus_row(ns.get(MOZ_GENERA + "&statusSet=" + view, new_session()), "lividopone") is None, view


def test_genus_page_names_specimens_not_on_the_list():
    html = ns.get("/browse.do?subfamily=formicinae&genus=acropyga&rank=genus&countryName=Mozambique&statusSet=valid", new_session())
    m = re.search(r'<div id="genus_other_names"[^>]*data-other-specimens="(\d+)"[^>]*>(.*?)</div>', html, re.S)
    if m is None:
        listed = genus_row(ns.get(MOZ_GENERA + "&statusSet=valid", new_session()), "acropyga")
        if listed is None or "other-names" not in listed:
            pytest.skip("no Acropyga specimens of other names in Mozambique on this server")
    assert m, "the Acropyga page in Mozambique has no line for its specimens not on the species list"
    line = m.group(2)
    assert "Specimens collected here not on the species list:" in ns.text_of(line), line
    assert re.search(r'genus=acropyga&amp;species=[^"]+&amp;rank=(?:sub)?species&amp;countryName=Mozambique"', line), line
    # above the notes on specimen and image counts (Brian Fisher, 2026-11-02)
    if 'id="list-notes"' in html:
        assert html.index('id="genus_other_names"') < html.index('id="list-notes"'), "the line is under the notes"
    # All specimen names: those names are on the list itself, no line
    every = ns.get("/browse.do?subfamily=formicinae&genus=acropyga&rank=genus&countryName=Mozambique&statusSet=all", new_session())
    assert 'id="genus_other_names"' not in every


def test_views_header_and_about_this_list():
    s = new_session()
    html = ns.get(MOZ_SPECIES, s)
    for gone in GONE:
        assert gone not in html, gone
    assert current_view(html) == "curated"
    # line 1: the header and the buttons, together; line 2: the links; then About this list, closed
    head = html[html.index('id="list-head"'):html.index('id="list-head"') + 6000]
    assert '<h2 class="display_count">' in head and 'id="status_toggle"' in head
    assert re.search(r'<details id="about-list" class="about-list">\s*<summary>About this list</summary>', html)
    about = html[html.index('id="about-list"'):]
    about = about[:about.index("</details>")]
    for words in ("Views.", "Valid</b>", "Curated</b>", "All specimen names</b>", "Fossil</b>", "Red-flagged specimens never count",
                  "Source.", 'data-source="specimen"', 'data-source="curated"', 'data-source="literature"',
                  'data-source="antcat" title="From the AntCat import"'):
        assert words in about, words
    assert html.index('id="list-links"') < html.index('id="about-list"')
    assert "List all specimens collected in Mozambique" in ns.text_of(html[html.index('id="list-links"'):html.index('id="about-list"')])
    assert "Edit Species List" not in html, "Edit Species List is for curators and administrators"
    # old links keep working: specimen=true and validmorpho open All specimen names
    for old in ("&specimen=true", "&statusSet=validmorpho"):
        page = ns.get(MOZ_SPECIES + old, s)
        assert current_view(page) == "all", old
        count, text = ns.header(page)
        assert text.endswith("(all specimen names)"), text
    # the views nest; All specimen names adds the names of every specimen collected here
    numbers = {v: ns.header(ns.get(MOZ_SPECIES + "&statusSet=" + v, s))[0] for v in ("valid", "curated", "all")}
    assert None not in numbers.values() and numbers["valid"] <= numbers["curated"] <= numbers["all"], numbers
    # nothing kept in the session
    assert current_view(ns.get(MOZ_SPECIES, s)) == "curated"


def test_every_kind_of_list_has_the_four_views():
    """Bioregions, museums, curated projects and All AntWeb have the same four buttons (Curated current), and no
    Names from selector."""
    for path in ("/taxonomicPage.do?rank=species&bioregionName=Afrotropical",
                 "/taxonomicPage.do?rank=genus&museumCode=CASC",
                 "/taxonomicPage.do?rank=genus&project=allantwebants",
                 "/taxonomicPage.do?rank=species&project=madants"):
        html = ns.fetch(path, new_session())
        if html is None:
            continue
        assert current_view(html) == "curated", path
        labels = re.findall(r'<a class="view-btn[^"]*" data-status-choice="[^"]+"[^>]*>([^<]*)</a>', html)
        # Fossil only when that list has fossil names (2026-11-02)
        fossil = ns.fetch(path + "&statusSet=fossil", new_session())
        n = ns.header(fossil or "")[0]
        want = ["Valid", "Curated", "All specimen names"] + (["Fossil"] if n else [])
        if fossil is not None:
            assert labels == want, (path, labels, n)
        else:
            assert labels[:3] == want[:3], (path, labels)
        for gone in GONE:
            assert gone not in html, (path, gone)

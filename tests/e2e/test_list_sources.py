"""The genus and species lists in the four views (Brian Fisher, 2026-11-02; docs/numbers.md "Views of lists",
docs/flows/places.md "Where the names come from"; the design of 2026-10-30 it replaces in brackets).

  1. Views. Four buttons, Valid | Curated | All specimen names | Fossil (Curated the default) replace the status menu and
     the "Names from" selector (and Show Specimen Taxa before it); old links (specimen=true, statusSet=validmorpho)
     open All specimen names. A genus known in a place only from specimens identified to genus ("Lividopone (indet)"
     in Mozambique) is on the genus list in All specimen names only.
  2. Genus rows, one line each: Taxon name | Author date | Species (in the view, or "-") | Not curated (views Valid and
     Curated: the specimens of the genus collected here whose name is not curated, counted nightly, hover "N specimens
     not curated", linked to the genus page in the view Not curated; empty when 0; it replaced Other specimens on
     2026-11-02) | Images ("N species imaged": the species of the view with an image of the ant, linked to the genus's
     images page) | Map | Source (an icon: specimen, curated, literature). Acropyga in Mozambique (morphospecies
     collected there, no species on the list): in Valid with no species and its Not curated number; the click opens
     Acropyga in Mozambique in the view Not curated, whose header gives the same number and its parts. ("No species on
     the list · N specimens collected here", "+ N specimens of other names" and the Other specimens search link are gone.)
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
from public_pages import site_path

MOZ_GENERA = "/taxonomicPage.do?rank=genus&countryName=Mozambique"
MOZ_SPECIES = "/taxonomicPage.do?rank=species&countryName=Mozambique"
NOT_CURATED_LINK = re.compile(r'<a class="not-curated" data-not-curated="(\d+)" title="([^"]*)" href="([^"]+)">([^<]+)</a>')
GONE = ("No species on the list", "specimens of other names", 'id="names_from_toggle"', "Show Specimen Taxa</a>",
        "Hide Specimen Taxa", "click on Show Specimen Taxa")


def genus_row(html, genus):
    for row in ns.list_rows(html):
        h = unescape(row["html"]).lower()
        if f"genus={genus}&" in h or f"genus={genus}\"" in h:
            return row["html"]
    return None


def current_view(html):
    m = re.search(r'<span id="change_status" class="view-buttons" data-status-set="([^"]*)"', html or "")
    return m.group(1) if m else None


def test_mozambique_acropyga_row_links_its_names_not_curated():
    """Acropyga in Mozambique (Valid): its Not curated number, the same in Curated, links to Acropyga in Mozambique in
    the view Not curated, whose header starts with the same number ("N specimens not curated: ...") and lists Acropyga
    names only, none of them valid."""
    html = ns.get(MOZ_GENERA + "&statusSet=valid", new_session())
    row = genus_row(html, "acropyga")
    if row is None:
        pytest.skip("Acropyga is not on the Mozambique genus list (Valid) on this server")
    m = NOT_CURATED_LINK.search(row)
    if m is None and re.search(r'<div class="list_extras other[^"]*"></div>', row):
        pytest.skip("Acropyga has no Not curated number in Mozambique on this server (not counted yet, or none)")
    assert m, f"the Acropyga row has no Not curated link: {ns.text_of(row)[:200]}"
    n, title, href, text = int(m.group(1)), unescape(m.group(2)), unescape(m.group(3)), m.group(4)
    assert n > 0 and text == f"{n:,} Specimen{'' if n == 1 else 's'}", text   # "N Specimens" since 2026-11-03
    assert title == f"{n:,} specimen{'' if n == 1 else 's'} not curated", title
    assert "browse.do?" in href and "genus=acropyga" in href and "countryName=Mozambique" in href and "statusSet=notcurated" in href, href
    curated = NOT_CURATED_LINK.search(genus_row(ns.get(MOZ_GENERA, new_session()), "acropyga") or "")
    assert curated and int(curated.group(1)) == n, "Valid and Curated show the same Not curated number"
    page = ns.get(href.replace(ns.BASE_URL, "") if href.startswith(ns.BASE_URL) else href, new_session())
    assert current_view(page) == "notcurated", current_view(page)
    head = re.search(r'<h2 class="display_count">(.*?)</h2>', page, re.S)
    assert head and ns.text_of(head.group(1)).startswith(f"{n:,} specimen"), head and ns.text_of(head.group(1))
    assert "not curated" in ns.text_of(head.group(1)), ns.text_of(head.group(1))
    names = [r["name"] for r in ns.list_rows(page) if r["name"]]
    assert names, "the view Not curated lists no name"
    assert all(nm.startswith("formicinaeacropyga ") for nm in names), names


def test_genus_rows_have_the_columns_of_the_views():
    """The Mozambique genus list (Curated): the Not curated column (not in All specimen names), a Species cell that is
    a number or a dash, an Images cell "N species imaged" with N at most the row's Species, linked to the genus's
    images page in the view, a source icon with its word as title; none of the texts of 2026-10-30."""
    html = ns.get(MOZ_GENERA, new_session())
    assert current_view(html) == "curated", current_view(html)
    assert re.search(r'<div class="list_extras other[^"]*">', html), "no Not curated column"
    assert re.search(r">Not curated</(span|a)>", html), "no Not curated heading"   # a sort link since 2026-11-02 21:30
    assert "Other specimens" not in html and 'class="other-names"' not in html, "Other specimens is gone (2026-11-02)"
    assert not re.search(r'<div class="list_extras other[^"]*">', ns.get(MOZ_GENERA + "&statusSet=all", new_session())), \
        "All specimen names has no Not curated column"
    for gone in GONE:
        assert gone not in html, gone
    rows = ns.list_rows(html)
    if not rows:
        pytest.skip("no genus on the Mozambique list on this server")
    for r in rows:
        cells = re.findall(r'<div class="list_extras specimens[^"]*">(.*?)</div>', r["html"], re.S)
        assert cells, ns.text_of(r["html"])[:120]
        species = ns.text_of(cells[-1])
        assert re.fullmatch(r"[\d,]+|–", species), f"Species cell {species!r}: {ns.text_of(r['html'])[:120]}"
        img = re.search(r'<div class="list_extras images[^"]*">(.*?)</div>', r["html"], re.S)
        if img and ns.on_list(r["html"]):
            t = ns.text_of(img.group(1))
            m = re.fullmatch(r"([\d,]+)|–", t)   # "Sp. imaged" column: the number alone, a dash when none (2026-11-02, 21:12)
            assert m, f"Images cell {t!r}: {ns.text_of(r['html'])[:120]}"
            if m.group(1):
                assert ns.n(m.group(1)) <= (0 if species == "–" else ns.n(species)), f"more species imaged than species: {ns.text_of(r['html'])[:120]}"
                assert "images.do?" in img.group(1) and "statusSet=curated" in img.group(1), img.group(1)
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
        if listed is None or 'class="not-curated"' not in listed:
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


@pytest.mark.parametrize("country", ["Madagascar", "Mozambique"])
def test_every_species_number_on_genus_lists_is_a_link(country):
    """Every number in the Species column of a country's genus list links to the genus page in the same view, rows
    added from specimens too (2026-11-03: Phyracaces in Madagascar, known there from one specimen, showed a bare 1)."""
    for view in ("valid", "curated", "all"):
        html = ns.get(f"/taxonomicPage.do?rank=genus&countryName={country}&statusSet={view}", new_session())
        for r in ns.list_rows(html):
            cells = re.findall(r'<div class="list_extras specimens[^"]*"[^>]*>(.*?)</div>', r["html"], re.S)
            if not cells:
                continue
            cell = cells[-1]
            if re.fullmatch(r"[\d,]+", ns.text_of(cell)):
                assert "<a " in cell and "browse.do?" in unescape(cell) and f"statusSet={view}" in unescape(cell), \
                    f"{view}: bare Species number: {ns.text_of(r['html'])[:120]}"


@pytest.mark.parametrize("country", ["Madagascar", "Mozambique"])
def test_every_species_row_with_antweb_specimens_has_a_map(country):
    """Brian Fisher, 2026-11-07: a curated name with no specimens collected here still has AntWeb specimens elsewhere,
    and its Map link opens their map; before, Map was shown only when the place had specimens of the name. Species
    list, view Curated: a row whose Global column counts specimens has a Map link."""
    html = ns.get(f"/taxonomicPage.do?rank=species&countryName={country}&statusSet=curated", new_session())
    checked, wrong = 0, []
    for r in ns.list_rows(html):
        cells = re.findall(r'<div class="list_extras specimens[^"]*"[^>]*>(.*?)</div>', r["html"], re.S)
        if len(cells) < 2:
            continue
        m = re.match(r"([\d,]+) Specimens?", ns.text_of(cells[0]).strip())
        if not m or int(m.group(1).replace(",", "")) == 0:
            continue
        checked += 1
        if "bigMap.do?" not in r["html"]:
            wrong.append(ns.text_of(r["html"])[:100])
    if not checked:
        pytest.skip(f"no species row with a Global count on {country}'s list")
    assert not wrong, f"{len(wrong)} of {checked} rows with AntWeb specimens have no Map link: " + "; ".join(wrong[:5])


def test_rows_added_from_specimens_say_specimens():
    """A row added from specimens collected here says "N Specimens" like the list's own rows (2026-11-03: it said
    "N here"). Only the added rows (title "Not on the list: from specimens collected here") are checked."""
    html = ns.get("/taxonomicPage.do?rank=species&countryName=Madagascar&statusSet=all", new_session())
    added = [ns.text_of(c) for c in re.findall(
        r'<div class="list_extras specimens[^"]*" title="Not on the list: from specimens collected here">(.*?)</div>', html, re.S)]
    if not added:
        pytest.skip("no rows added from specimens on the Madagascar list (All specimen names) today")
    wrong = [c for c in added if not re.fullmatch(r"[\d,]+ Specimens?", c.strip())]
    assert not wrong, wrong[:5]


def test_rows_not_on_the_list_open_their_specimens():
    """A row added from specimens links its name and its "N Specimens" to the name in the place in the view All
    specimen names, which shows the specimens. In the default view an indetermined name's page said "0 Specimens"
    (Temnothorax (indet) in Mozambique, CASENT0849587; Brian Fisher, 2026-11-04)."""
    html = ns.get("/taxonomicPage.do?rank=species&countryName=Madagascar&statusSet=all", new_session())
    rows = [c for c in re.split(r'<div class="sd_data"', html)[1:] if 'title="Not on the list: from specimens collected here"' in c]
    if not rows:
        pytest.skip("no rows added from specimens on the Madagascar list today")
    for row in rows[:20]:
        for href in re.findall(r"""href=['"]([^'"]*(?:description|browse)\.do\?[^'"]*)['"]""", row):
            assert "statusSet=all" in unescape(href), f"a row not on the list links without the view: {href}"
    href = re.search(r"""href=['"]([^'"]*browse\.do\?[^'"]*)['"]""", rows[0])
    assert href, "the added row's specimen number is not linked"
    page = ns.text_of(ns.get(site_path(href.group(1)), new_session()))
    assert not re.search(r"\b0 Specimens", page), f"{href.group(1)}: the name's page shows 0 specimens"


def test_placeholder_genus_rows_open_their_specimens():
    """Genus list, All specimen names (Brian Fisher, 2026-11-04): a placeholder row ("(dorylinae)") shows in its Species
    column "N Specimens", its specimens collected here identified only to subfamily, linked to them; the page it opens
    lists the same number. Real genera keep their species number."""
    html = ns.get("/taxonomicPage.do?rank=genus&countryName=Madagascar&statusSet=all", new_session())
    cells = re.findall(r"""<a class='subfamily-only' title='([^']*)' href='([^']*)'><span class='numbers'>([\d,]+)</span> Specimens?</a>""", html)
    if not cells:
        pytest.skip("no placeholder genus rows with specimens on the Madagascar genus list today")
    for title, href, n in cells[:4]:
        href = unescape(href)
        assert "statusSet=all" in href and "species=%28indet%29" in href and "Madagascar" in href, href
        assert "identified only to subfamily" in title, title
        page = ns.text_of(ns.get(site_path(href), new_session()))
        m = re.search(r"\b([\d,]+) Specimens?\b", page)
        assert m and m.group(1) == n, f"{href}: the list says {n} specimens, the page {m.group(0) if m else 'none'}"


def test_fossil_only_genera_only_in_the_fossil_view():
    """A genus known in a place only from fossils is listed in the view Fossil only (Brian Fisher, 2026-11-05): Ukraine's
    Oecophylla (Rovno amber) was on the Curated genus list with no species."""
    rows = {}
    for view in ("curated", "valid", "all", "fossil"):
        html = ns.get(f"/taxonomicPage.do?rank=genus&countryName=Ukraine&statusSet={view}", new_session())
        rows[view] = any("oecophylla" in (r["name"] or "").lower() for r in ns.list_rows(html))
    if not rows["fossil"]:
        pytest.skip("Oecophylla is not on Ukraine's Fossil genus list on this server")
    assert not (rows["curated"] or rows["valid"] or rows["all"]), f"Oecophylla in Ukraine, by view: {rows}"


def test_rows_added_from_specimens_marks_and_images():
    """Rows added from specimens (not on the list), Brian Fisher 2026-11-05: no Introduced or Endemic mark (they are on
    no introduced or endemic list; New Caledonia's Brachymyrmex obscurior_cf was marked Introduced), and their Images
    cell is "No Images" or "N Images" like every other row (it was "-": Comoros Hypoponera amd_01)."""
    checked = 0
    for country in ("New%20Caledonia", "Comoros"):
        html = ns.get(f"/taxonomicPage.do?rank=species&countryName={country}&statusSet=all", new_session())
        for row in [c for c in re.split(r'<div class="sd_data"', html)[1:] if 'title="Not on the list: from specimens collected here"' in c][:40]:
            name = ns.text_of(row)[:60]
            assert 'title="Introduced"' not in row and 'title="Endemic"' not in row, f"{country}: {name}: marked although not on the list"
            images = re.search(r'<div class="list_extras images[^"]*">(.*?)</div>', row, re.S)
            if images:
                cell = ns.text_of(images.group(1))
                assert re.fullmatch(r"No Images|[\d,]+ Images?", cell), f"{country}: {name}: Images cell {cell!r}"
            checked += 1
    if not checked:
        pytest.skip("no rows added from specimens on these lists today")

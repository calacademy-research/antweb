"""Where a place list's names come from (Brian Fisher, 2026-10-30; docs/flows/places.md "Where the names come from").

  1. Genus rows. Mozambique's genus list had Acropyga and Form_gen-afrc-za01 with "No Species" and nothing led to the
     specimens that put them there (morphospecies and unrecognized names collected in Mozambique, not on its species
     list). A genus with no species on the list now says "No species on the list · N specimens collected here", the
     number linked to the place's specimen search for the genus; a genus with species and such specimens adds
     "+ N specimens of other names". The Acropyga row's link lists at least one Acropyga specimen.
  2. Genus page in a place: "Specimens collected here not on the species list: N (names: ...)" (Acropyga, Mozambique).
  3. Names from. One selector, "Names from: Place list | Specimens collected here", replaces the Show Specimen Taxa
     link and the sentence that pointed to it; specimen=true still works (old links) and the header then says the
     source ("... on the list or collected in Mozambique").
  4. Header. Line 1 the header with the Species Status and Names from selectors, line 2 the small links; the notes on
     the list's sources are in a closed "About this list" (details element).

Read-only and anonymous. The data on staging is a copy of production; a test skips with a reason when its sample has
no data. specimen=true is remembered in the session (TaxaPageAction), so those requests use their own session.
"""
import re
from html import unescape

import pytest

import numbers_sample as ns
from antweb import new_session

MOZ_GENERA = "/taxonomicPage.do?rank=genus&countryName=Mozambique&specimen=false"
MOZ_SPECIES = "/taxonomicPage.do?rank=species&countryName=Mozambique"
OLD_SENTENCE = "click on Show Specimen Taxa"
OTHER_LINK = re.compile(r'<a rel="nofollow" class="other-names" data-other-specimens="(\d+)" href="([^"]+)">([^<]+)</a>')


def genus_row(html, genus):
    for row in ns.list_rows(html):
        if f"genus={genus}&" in unescape(row["html"]).lower() or f"genus={genus}\"" in unescape(row["html"]).lower():
            return row["html"]
    return None


def specimens_listed(html):
    """The specimen links of an advanced search result page."""
    return re.findall(r"specimen\.do\?(?:name|code)=([^\"'&>]+)", html)


def test_mozambique_acropyga_row_links_its_specimens():
    html = ns.get(MOZ_GENERA, new_session())
    row = genus_row(html, "acropyga")
    if row is None:
        pytest.skip("Acropyga is not on the Mozambique genus list on this server")
    m = OTHER_LINK.search(row)
    assert m, f"the Acropyga row has no link to its specimens: {ns.text_of(row)[:200]}"
    n, href, text = int(m.group(1)), unescape(m.group(2)), m.group(3)
    assert n > 0 and (text.startswith(f"{n} specimen") or text.startswith(f"+ {n} specimen")), text
    if "No species on the list" in ns.text_of(row):
        assert text == f"{n} specimen{'s' if n != 1 else ''} collected here", text
    assert "advancedSearch.do?" in href and "country=Mozambique" in href and "genus=acropyga" in href, href
    result = ns.get(href.replace(ns.BASE_URL, "") if href.startswith(ns.BASE_URL) else href)
    codes = specimens_listed(result)
    assert codes, f"the Acropyga link lists no specimen: {href}"
    assert "acropyga" in ns.text_of(result).lower(), href


def test_genus_rows_with_no_species_say_where_they_come_from():
    """Every "No species on the list" row of the Mozambique genus list names a number of specimens collected here."""
    html = ns.get(MOZ_GENERA, new_session())
    rows = [r for r in ns.list_rows(html) if "No species on the list" in r["html"]]
    for r in rows:
        m = re.search(r'class="other-names" data-other-specimens="(\d+)"', r["html"])
        assert m and int(m.group(1)) > 0, ns.text_of(r["html"])[:200]
    if not rows:
        pytest.skip("no genus of the Mozambique list is there only from specimens of other names")


def test_genus_page_names_specimens_not_on_the_list():
    html = ns.get("/browse.do?subfamily=formicinae&genus=acropyga&rank=genus&countryName=Mozambique", new_session())
    m = re.search(r'<div id="genus_other_names"[^>]*data-other-specimens="(\d+)"[^>]*>(.*?)</div>', html, re.S)
    if m is None:
        listed = genus_row(ns.get(MOZ_GENERA, new_session()), "acropyga")
        if listed is None or "other-names" not in listed:
            pytest.skip("no Acropyga specimens of other names in Mozambique on this server")
    assert m, "the Acropyga page in Mozambique has no line for its specimens not on the species list"
    line = m.group(2)
    assert "Specimens collected here not on the species list:" in ns.text_of(line), line
    assert re.search(r'genus=acropyga&amp;species=[^"]+&amp;rank=(?:sub)?species&amp;countryName=Mozambique"', line), line


def test_names_from_selector_and_header():
    s = new_session()
    html = ns.get(MOZ_SPECIES + "&specimen=false", s)
    assert 'id="names_from_toggle"' in html, "no Names from selector on the Mozambique species list"
    assert 'data-names-from="list"' in html
    assert 'data-names-choice="specimens"' in html and "specimen=true" in html
    assert "Show Specimen Taxa</a>" not in html and "Hide Specimen Taxa" not in html
    assert OLD_SENTENCE not in html
    # line 1: the header and the two selectors, together; line 2: the links; then About this list, closed
    head = html[html.index('id="list-head"'):html.index('id="list-head"') + 6000]
    assert '<h2 class="display_count">' in head and 'id="status_toggle"' in head and 'id="names_from_toggle"' in head
    assert head.index('id="status_toggle"') < head.index('id="names_from_toggle"')
    assert re.search(r'<details id="about-list" class="about-list">\s*<summary>About this list</summary>', html)
    assert html.index('id="list-links"') < html.index('id="about-list"')
    assert "List all specimens collected in Mozambique" in ns.text_of(html[html.index('id="list-links"'):html.index('id="about-list"')])
    assert "Edit Species List" not in html, "Edit Species List is for curators and administrators"
    # old links keep working: specimen=true lists the specimens' names too and the header says so
    with_specimens = ns.get(MOZ_SPECIES + "&specimen=true", s)
    assert 'data-names-from="specimens"' in with_specimens
    count, text = ns.header(with_specimens)
    assert text.endswith("on the list or collected in Mozambique"), text
    listed, _ = ns.header(html)
    assert count is not None and listed is not None and count >= listed, (count, listed)
    # and the choice is remembered until Place list is chosen
    back = ns.get(MOZ_SPECIES + "&specimen=false", s)
    assert 'data-names-from="list"' in back


def test_places_without_specimen_names_have_no_selector():
    """Bioregions, museums and curated projects never had Show Specimen Taxa: no Names from selector."""
    for path in ("/taxonomicPage.do?rank=species&bioregionName=Afrotropical",
                 "/taxonomicPage.do?rank=genus&museumCode=CASC",
                 "/taxonomicPage.do?rank=species&project=madants"):
        html = ns.fetch(path, new_session())
        if html is None:
            continue
        assert 'id="names_from_toggle"' not in html, path
        assert OLD_SENTENCE not in html, path

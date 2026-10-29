"""Specimens identified only to genus, and taxa not recorded from a place (Brian Fisher, 2026-10-29; docs/numbers.md
row 121 and section 5, docs/flows/taxon-pages.md).

On 2026-10-29 19,213 specimens were renamed onto 182 names "<Genus> (indet)". Lividopone (indet): 45 specimens (4
imaged), most from Madagascar, some from Mozambique.

  1. Indet line. Every genus page (description.do, browse.do, images.do; any place or all of AntWeb) says under its
     species "Not identified to species: Lividopone (indet), N specimens (M imaged)", linked to the indet name's page of
     the same kind and place; N and M are what that page lists. Checked on Lividopone (all of AntWeb and Mozambique) and
     on the day's genus of the API's indet names.
  2. Not recorded. A genus or species page opened for a place whose list does not have it and where it was not
     collected says "<Taxon> is not recorded from <Place>.", "Recorded from: <the bioregions whose list has it>" and
     "See <Taxon> in all of AntWeb". Never on a page whose place has the taxon. Checked on Lividopone (Neotropical, not
     Malagasy, not Madagascar, not Mozambique) and on the day's genus against the API's bioregion lists.
  3. Lists. The genus of a specimen identified only to genus is on the country's and adm1's list (nightly set 2, from
     2026-10-29); the indet name is not a species row. Lividopone on the Mozambique and Afrotropical genus lists;
     pending until compute.log has the set 2 line (numbers_sample.GENUS_ROWS_LOG), so this test is not in [parallel]
     (it logs in to read compute.log).

Read-only. The data on staging is a copy of production; a test skips with a reason when its sample has no data.
"""
import re
from html import unescape
from urllib.parse import quote_plus

import pytest

import numbers_sample as ns

LIVIDOPONE = "subfamily=dorylinae&genus=lividopone&rank=genus"
LINE = re.compile(r'Not identified to species: <a href="([^"]+)">([^<]+)</a>, (\d[\d,]*) specimens? \((\d[\d,]*) imaged\)')
NOTICE = "is not recorded from"


def indet_line(html):
    """(url, name, N, M) of the genus page's indet line, or None."""
    m = LINE.search(html)
    if not m:
        return None
    return unescape(m.group(1)), unescape(m.group(2)), ns.n(m.group(3)), ns.n(m.group(4))


def browse_header(html):
    """(specimens, imaged) of a species browse page's header ("45 Specimens (4 imaged)"; "No Specimens")."""
    m = re.search(r'<h2 class="display_count">(.*?)</h2>', html, re.S)
    assert m, "no header on the species page"
    text = ns.text_of(m.group(1))
    if text.startswith("No "):
        return 0, 0
    n = re.match(r"([\d,]+)", text)
    imaged = re.search(r"\(\s*([\d,]+)\s*imaged\)", text)
    return ns.n(n.group(1)), ns.n(imaged.group(1)) if imaged else 0


def indet_genera():
    """Genera with an indet name in AntWeb: [(subfamily, genus lower case, genus taxon name)] from the API."""
    rows = ns.api_all("taxa?species=" + quote_plus("(indet)") + "&taxarank=species", max_pages=4) or []
    out = []
    for r in rows:
        name, parent = r.get("taxonName") or "", r.get("parentTaxonName") or ""
        if name.endswith(" (indet)") and parent and r.get("subfamily") and r.get("genus") and not parent.startswith("("):
            out.append((r["subfamily"].lower(), r["genus"].lower(), parent))
    return sorted(set(out))


def check_line(page, params, where):
    """The genus page's indet line agrees with the indet page it links to; returns N (0 when no line)."""
    html = ns.get(f"/{page}.do?{params}")
    line = indet_line(html)
    if line is None:
        return 0
    url, name, n, m = line
    assert name.endswith(" (indet)"), (where, name)
    assert f"/{page}.do?" in url and "species=%28indet%29" in url, (where, "links to the indet page of the same kind", url)
    browse = url.replace(f"/{page}.do?", "/browse.do?") + "&statusSet=all"
    shown = browse_header(ns.get(browse))
    assert (n, m) == shown, f"{where} {page}: the line says {n} specimens ({m} imaged), the indet page lists {shown}: {browse}"
    return n


def test_genus_page_names_its_specimens_identified_only_to_genus():
    """Brian Fisher, 2026-10-29: the indet page could be reached from no genus page. Lividopone in all of AntWeb and in
    Mozambique, on the three page kinds; N and M = the indet page's own header."""
    n_all = [check_line(p, LIVIDOPONE + "&project=allantwebants", "Lividopone, all of AntWeb") for p in ("description", "browse", "images")]
    if n_all == [0, 0, 0]:
        pytest.skip("no Lividopone (indet) specimens on this server")
    assert len(set(n_all)) == 1 and n_all[0] > 0, ("one number on the three pages", n_all)
    n_moz = [check_line(p, LIVIDOPONE + "&countryName=Mozambique", "Lividopone, Mozambique") for p in ("description", "browse", "images")]
    assert len(set(n_moz)) == 1, ("one number on the three pages", n_moz)
    assert n_moz[0] < n_all[0], ("Mozambique has some of them, not all", n_moz, n_all)
    # Not a species anywhere: the indet name is no row of the Mozambique list.
    rows = ns.api_rows(ns.api("geolocaleTaxa?country=Mozambique&taxonName=" + quote_plus("dorylinaelividopone (indet)")))
    assert not rows, "Lividopone (indet) is a row of the Mozambique list"


def test_day_genus_indet_line():
    """The day's genus with an indet name: its line in all of AntWeb agrees with its indet page."""
    genera = indet_genera()
    if not genera:
        pytest.skip("the API lists no (indet) names")
    for subfamily, genus, _parent in ns.pick(genera, 2, "indet-line"):
        n = check_line("browse", f"subfamily={subfamily}&genus={genus}&rank=genus&project=allantwebants", f"{genus} {ns.day_note()}")
        assert n >= 0


def test_not_recorded_notice():
    """Brian Fisher, 2026-10-29: Lividopone in Afrotropical said "0 Species Imaged (0 total)" with no explanation and no
    way out. A place without the taxon says so, names the bioregions that have it and links all of AntWeb."""
    html = ns.get(f"/images.do?{LIVIDOPONE}&bioregionName=Neotropical")
    text = ns.text_of(html)
    assert "Lividopone is not recorded from Neotropical." in text, text[:400]
    m = re.search(r'id="not_recorded".*?</div>', html, re.S)
    assert m, "no notice block"
    block = m.group(0)
    assert "Recorded from:" in block and "bioregionName=Malagasy" in block, block
    assert "Neotropical" not in re.sub(r"is not recorded from Neotropical", "", ns.text_of(block)), block
    assert "project=allantwebants" in block and "See Lividopone in all of AntWeb" in ns.text_of(block), block
    # Never where the taxon is recorded: on the list (Malagasy, Madagascar) or collected there (Mozambique: indet only).
    for params in ("bioregionName=Malagasy", "countryName=Madagascar", "countryName=Mozambique", "project=allantwebants"):
        for page in ("description", "browse", "images"):
            assert NOTICE not in ns.text_of(ns.get(f"/{page}.do?{LIVIDOPONE}&{params}")), (page, params)


def test_day_genus_not_recorded_notice():
    """The day's genus with an indet name, in a bioregion whose list does not have it: the notice names exactly the
    bioregions whose list has it (API /bioregionTaxa); in one of those, no notice."""
    genera = indet_genera()
    bioregions = [b.name for b in ns.bioregions()]
    if not genera or not bioregions:
        pytest.skip("no indet names or bioregions from the API")
    for subfamily, genus, parent in ns.pick(genera, 2, "not-recorded"):
        listed = sorted({r.get("bioregionName") for r in ns.api_rows(ns.api("bioregionTaxa?taxonName=" + quote_plus(parent)))} - {None})
        params = f"subfamily={subfamily}&genus={genus}&rank=genus"
        others = [b for b in bioregions if b not in listed]
        if others:
            place = ns.pick(others, 1, "nr" + genus)[0]
            html = ns.get(f"/browse.do?{params}&bioregionName={quote_plus(place)}")
            m = re.search(r'id="not_recorded".*?</div>', html, re.S)
            assert m, f"{genus} in {place}: no notice {ns.day_note()}"
            named = sorted(set(re.findall(r"bioregionName=([A-Za-z]+)", m.group(0))))
            assert named == listed, (genus, place, named, listed, ns.day_note())
        if listed:
            html = ns.get(f"/browse.do?{params}&bioregionName={quote_plus(listed[0])}")
            assert NOTICE not in ns.text_of(html), (genus, listed[0], ns.day_note())


def test_genus_rows_of_specimens_identified_only_to_genus():
    """Brian Fisher, 2026-10-29 (a specimen counts at each rank it is identified to): Lividopone, known in Mozambique only
    from Lividopone (indet), is on the Mozambique and Afrotropical genus lists, and the day's indet genus is on the list
    of the country with most of its indet specimens. Pending until nightly set 2 has run with this rule."""
    if ns.genus_rows_from_specimens_built() is False:
        pytest.skip("pending until nightly set 2 builds the genus rows (compute.log line 'Set 2 done (... "
                    + ns.GENUS_ROWS_LOG + ")')")
    moz = ns.api_rows(ns.api("specimens?genus=lividopone&species=" + quote_plus("(indet)") + "&country=Mozambique&limit=1"))
    if not moz:
        pytest.skip("no Lividopone (indet) specimen from Mozambique on this server")
    for url in ("/taxonomicPage.do?rank=genus&countryName=Mozambique&statusSet=all",
                "/taxonomicPage.do?rank=genus&bioregionName=Afrotropical&statusSet=all"):
        assert "genus=lividopone" in ns.get(url).lower(), f"Lividopone is not on {url}"
    assert NOTICE not in ns.text_of(ns.get(f"/images.do?{LIVIDOPONE}&bioregionName=Afrotropical"))
    valid_countries = {r.get("geolocaleName") for r in ns.geolocales("country")}
    for subfamily, genus, parent in ns.pick(indet_genera(), 2, "genus-rows"):
        rows = ns.api_all(f"specimens?genus={quote_plus(genus)}&species=" + quote_plus("(indet)"), max_pages=4) or []
        by_country = {}
        for r in rows:
            c = r.get("country")
            if c in valid_countries:
                by_country[c] = by_country.get(c, 0) + 1
        if not by_country:
            continue
        country = max(sorted(by_country), key=lambda c: by_country[c])
        listed = ns.api_rows(ns.api(f"geolocaleTaxa?country={quote_plus(country)}&taxonName={quote_plus(parent)}"))
        assert listed, f"{parent} is not on the list of {country} ({by_country[country]} indet specimens) {ns.day_note()}"

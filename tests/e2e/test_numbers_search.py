"""The numbers of the search results, the compare and map preparations, the comparison page, the collection and
locality pages, the admin Subtaxon total and the statsPage Valid Fossil museum table (docs/numbers.md rows 19, 45,
65 to 70), 2026-10-27.

test_numbers_live.py checks some of these on Madagascar and Pheidole with consistency rules only. Here each number is
compared with an independent count, on a sample chosen from the day (numbers_sample.py): the day's country (one with
specimens that the API finds by its country) and a genus of its list with at most ANTWEB_SAMPLE_MAX_SEARCH specimens.

  row 67  advancedSearch.do "Showing a-b of N Specimens (M imaged on this page)": N = the API's specimen records of
          that country and genus with a counted status (AdvancedSearch: sp.country, sp.genus, sp.family, the statuses
          of StatusSet "all"); the page lists min(N, 200) rows, every one of them in the API's list; M = the rows
          without the no-photos mark = the rows whose API imageCount > 0.
  row 68  resultRank=species "S Species (from N specimens).": N = the specimen search's N, S = the rows listed = the
          distinct genus, species (and subspecies) of the API's records (AdvancedSearchResults.getSpeciesList).
          B54 (xfail): names not identified to genus are counted as species.
  row 69  prepareCompareResults.do and prepareMapResults.do (log in needed: curator test account): with resultRank
          specimen the page shows the specimens of the search page that have an image (compare) or coordinates (map):
          "Showing 1-k of k Specimens", k = the rows and checkboxes listed = the search page's rows with an image /
          with API coordinates; with resultRank species "N imaged species" and "N georeferenced species (from M
          specimens)": N = the rows listed (B50, fixed), N <= S, M <= the search's N. B53 (fixed): coordinates
          within one degree of the equator or prime meridian count.
  row 70  compareResults.do "N specimens to compare." = the specimens chosen, and the specimens it shows are those.
  row 65  collection.do "N specimens of M species": N = the search by that collection code (the same specimens),
          and its codes; M = the distinct genus + species of the API's records of those specimens, names not
          identified to genus left out (B52, fixed).
  row 66  locality.do "N collections": N = the collections listed = the distinct collection codes of the locality's
          specimens (search by locality code, read from the rows' "Collection:" links) (B51, fixed).
  row 45  taxonomicPage.do and browse.do, admin "Subtaxon total" (admin test account): = the sum of the numbers in the
          rows; for every kind of place: rank genus = the place's Species, rank subfamily = Genera, rank species <=
          Specimens; browse.do of a genus: = the sum of its rows, and its rows = the genus row's "N Species".
  row 19  statsPage.do Museum Data, Valid Fossil: for the day's museums, subfamilies, genera, species + subspecies =
          the headers of the museum's lists with statusSet "valid fossil" (TaxaPage, a different code path).

Read-only (the compare form POST only shows the chosen specimens). Logged-in tests are staging only and skip without
the test account; they are not in [parallel] in areas.toml. Image counts and coordinates of the API are those of the
specimen table: after an upload they can be pending until the nightly run.
"""
import os
import re
from html import unescape
from urllib.parse import quote_plus, unquote

import pytest

import numbers_sample as ns
from antweb import BASE_URL, new_session
from staging_accounts import IS_STAGING, login, needs

MAX_SEARCH = int(os.environ.get("ANTWEB_SAMPLE_MAX_SEARCH", "1500"))
COUNTED = {"valid", "unrecognized", "morphotaxon", "indetermined", "unidentifiable"}   # StatusSet "all"
UNIDENTIFIED_GENERA = {"indet", "undet"}                                                 # PlaceTotals
SEARCH = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true"
SHOWING = re.compile(r"Showing\s+([\d,]+)(?:&ndash;|–)([\d,]+)\s+of\s+([\d,]+)")
BY_TAXON = re.compile(r"([\d,]+) Species \(from ([\d,]+) specimens\)")
IMAGED_ON_PAGE = re.compile(r'\(<span class="numbers">(\d+)</span> imaged on this page\)')
PENDING = "image counts and coordinates of the API are the specimen table's; after an upload they can be pending"


def identified_to_genus(genus):
    g = (genus or "").strip().lower()
    return not g.startswith("(") and g not in UNIDENTIFIED_GENERA


def code_of(chunk, pattern=r"specimen\.do\?(?:code|name)=([^\"'&<> ]+)"):
    m = re.search(pattern, chunk)
    return unquote(unescape(m.group(1))).strip().lower() if m else None


def showing(html):
    m = SHOWING.search(html or "")
    return tuple(ns.n(g) for g in m.groups()) if m else None


def search_total(html):
    """N of "Showing a-b of N", 0 for "Sorry, nothing matches", None when neither is on the page."""
    s = showing(html)
    if s:
        return s[2]
    return 0 if html and "nothing matches your request" in html else None


def collection_codes_of_rows(html):
    """The collection codes of a specimen list's rows (specimenReport.jsp "Collection: <a ...collection.do?name=X">)."""
    return [unquote(unescape(c)).strip().lower()
            for c in re.findall(r'Collection: <a href="[^"]*/collection\.do\?name=([^"]+)"', html or "")]


def chosen_values(html):
    return re.findall(r'<input type="checkbox"[^>]*name="chosen" value="([^"]*)"', html or "")


# ----------------------------------------------------------------------------------------------- the day's search

_SAMPLE = {}


def _genera_of(country):
    html = ns.fetch(f"/taxonomicPage.do?rank=genus&countryName={quote_plus(country)}&statusSet=all") or ""
    out = []
    for r in ns.list_rows(html):
        m = re.search(r'description\.do\?[^"\'>]*?\bgenus=([^&"\'>]+)', r["html"])
        if m:
            g = unquote(unescape(m.group(1))).strip().lower()
            if identified_to_genus(g) and g not in out:
                out.append(g)
    return out


def search_url(country, genus, rank="specimen"):
    return (SEARCH + f"&familySearchType=equals&family=formicidae&genusSearchType=equals&genus={quote_plus(genus)}"
            f"&country={quote_plus(country)}&statusSet=all&resultRank={rank}")


def sample():
    """The day's search: (country, genus, specimen search HTML, N, the API's records of it by code)."""
    if "s" in _SAMPLE:
        if _SAMPLE["s"] is None:
            pytest.skip("no country and genus found for the search sample " + ns.day_note())
        return _SAMPLE["s"]
    _SAMPLE["s"] = None
    countries = [r.get("geolocaleName") for r in ns.geolocales("country")]
    for country in ns.pick(countries, 4, "search"):
        for genus in ns.pick(_genera_of(country), 3, "search" + country):
            html = ns.fetch(search_url(country, genus), new_session())
            total = search_total(html)
            if not total or total > MAX_SEARCH:
                continue
            rows = ns.api_all(f"specimens?country={quote_plus(country)}&genus={quote_plus(genus)}&family=formicidae",
                              max_pages=max(2, MAX_SEARCH // 500 + 2))
            if rows is None:
                continue
            mine = {(r.get("specimenCode") or "").lower(): r for r in rows
                    if r.get("country") == country and (r.get("status") or "") in COUNTED}
            if not mine:
                continue   # an island country: the search reads island_country, the API country
            _SAMPLE["s"] = (country, genus, html, total, mine)
            return _SAMPLE["s"]
    pytest.skip("no country and genus found for the search sample " + ns.day_note())


def page_rows(html):
    """(code, has photo, row HTML) of a specimen list's rows."""
    return [(code_of(r["html"]), not r["no_photos"], r["html"]) for r in ns.list_rows(html or "")]


# ----------------------------------------------------------------------------------------------- row 67, 68

def test_search_total_and_imaged_count_equal_the_api():
    """Row 67: "Showing a-b of N Specimens (M imaged on this page)": N = the API's records of the same country, genus,
    family and statuses; the page lists min(N, 200) of them; M = its rows with a photo = its rows whose API
    imageCount > 0."""
    country, genus, html, total, api = sample()
    where = f"{genus} of {country} {ns.day_note()}"
    wrong = []
    if total != len(api):
        wrong.append(f"search N {total}, API records {len(api)}")
    first, last, n = showing(html)
    rows = page_rows(html)
    if (first, last) != (1, min(n, 200)) or len(rows) != last - first + 1:
        wrong.append(f"'Showing {first}-{last} of {n}' with {len(rows)} rows listed")
    m = IMAGED_ON_PAGE.search(html)
    if not m:
        wrong.append("no '(M imaged on this page)'")
    else:
        imaged = int(m.group(1))
        with_photo = sum(1 for _c, photo, _h in rows if photo)
        by_api = sum(1 for c, _p, _h in rows if c in api and int(api[c].get("imageCount") or 0) > 0)
        if imaged != with_photo:
            wrong.append(f"{imaged} imaged on this page, {with_photo} rows with a photo")
        if imaged != by_api:
            wrong.append(f"{imaged} imaged on this page, {by_api} rows with an API imageCount > 0 ({PENDING})")
    unknown = [c for c, _p, _h in rows if c not in api]
    if unknown:
        wrong.append(f"rows not in the API's records: {unknown[:5]}")
    assert not wrong, where + ":\n" + "\n".join(wrong)


def _species_key(r):
    sub = (r.get("subspecies") or "").strip().lower()
    return ((r.get("genus") or "").lower(), (r.get("species") or "").lower() or None, sub or None)


def by_species(country, genus, s=None):
    html = ns.fetch(search_url(country, genus, "species"), s or new_session())
    m = BY_TAXON.search(ns.text_of(html or ""))
    if not m:
        pytest.skip(f"search by species of {genus} of {country}: no 'S Species (from N specimens)' {ns.day_note()}")
    return html, ns.n(m.group(1)), ns.n(m.group(2))


def test_search_by_species_counts_the_names_of_its_specimens():
    """Row 68: "S Species (from N specimens).": N = the specimen search's N, S = the rows listed = the distinct genus,
    species and subspecies of the API's records."""
    country, genus, _html, total, api = sample()
    html, species, specimens = by_species(country, genus)
    wrong = []
    if "truncated" not in html and specimens != total:
        wrong.append(f"from {specimens} specimens, specimen search N {total}")
    listed = len(ns.list_rows(html))
    if listed != species:
        wrong.append(f"{species} Species, {listed} rows listed")
    expected = len({_species_key(r) for r in api.values()})
    if species != expected:
        wrong.append(f"{species} Species, {expected} distinct names in the API's records")
    assert not wrong, f"{genus} of {country} {ns.day_note()}:\n" + "\n".join(wrong)


@pytest.mark.xfail(strict=False, reason="B54: the search by species counts names not identified to genus as species")
def test_search_by_species_leaves_out_names_not_identified_to_genus():
    """Row 68 (B54): a search by species of names whose genus is not identified ((indet), indet) finds no species
    (Brian Fisher, 2026-10-22: such names are not species anywhere)."""
    checked = []
    for country in ["Madagascar"] + ns.pick([r.get("geolocaleName") for r in ns.geolocales("country")], 1, "indet"):
        url = (SEARCH + "&familySearchType=equals&family=formicidae&genusSearchType=contains&genus=indet"
               f"&country={quote_plus(country)}&statusSet=all&resultRank=species")
        html = ns.fetch(url, new_session())
        m = BY_TAXON.search(ns.text_of(html or ""))
        if not m:
            continue
        checked.append(country)
        genera = []
        for r in ns.list_rows(html):
            g = re.search(r'description\.do\?[^"\'>]*?\bgenus=([^&"\'>]+)', r["html"])
            genera.append(unquote(unescape(g.group(1))).strip().lower() if g else None)
        identified = sum(1 for g in genera if g and identified_to_genus(g))
        assert ns.n(m.group(1)) == identified, (
            f"{country}, genus contains 'indet': {m.group(0)}; {identified} of the {len(genera)} names listed are "
            f"identified to genus ({sorted(set(g for g in genera if g))[:5]})")
    if not checked:
        pytest.skip("no specimens with an unidentified genus found " + ns.day_note())


# ----------------------------------------------------------------------------------------------- rows 69, 70 (log in)

def _logged_in():
    s, _token, _id = login("noupload")
    return s


@pytest.mark.skipif(not IS_STAGING, reason="curator test login: staging only")
@needs("noupload")
def test_compare_and_map_preparations_count_what_they_list():
    """Row 69: Compare and Map of the day's search. Specimens: the page lists the search page's specimens with an image
    (compare) or with coordinates (map; API decimalLatitude and decimalLongitude set and not 0, B53): "Showing 1-k of
    k" = rows = checkboxes. Species: "N imaged species" and "N georeferenced species (from M specimens)": N = the rows
    listed (B50), N <= S of the search, M <= its specimens."""
    country, genus, _html, total, api = sample()
    s = _logged_in()
    wrong = []
    for tool, keep in (("Compare", lambda c, photo: photo),
                       ("Map", lambda c, photo: c in api and _georeferenced(api[c]))):
        search = ns.fetch(search_url(country, genus), s)
        rows = page_rows(search)
        expected = sum(1 for c, photo, _h in rows if keep(c, photo))
        html = ns.fetch(f"/prepare{tool}Results.do?resultRank=specimen", s)
        if html is None:
            wrong.append(f"prepare{tool}Results.do: not a normal page")
            continue
        listed = len(ns.list_rows(html))
        boxes = len(chosen_values(html))
        shown = showing(html)
        if expected == 0:
            if listed:
                wrong.append(f"{tool} specimens: {listed} rows, none expected")
            continue
        if shown != (1, listed, listed) or boxes != listed:
            wrong.append(f"{tool} specimens: 'Showing {shown}', {listed} rows, {boxes} checkboxes")
        if listed != expected:
            wrong.append(f"{tool} specimens: {listed} rows; the search page has {expected} "
                         + ("with a photo" if tool == "Compare" else f"with API coordinates ({PENDING})"))
    _html, species, specimens = by_species(country, genus, s)
    for tool, pattern in (("Compare", r"([\d,]+) imaged species"),
                          ("Map", r"([\d,]+) georeferenced species \(from ([\d,]+) specimens\)")):
        by_species(country, genus, s)   # the preparation changes the session's results: search again
        html = ns.fetch(f"/prepare{tool}Results.do?resultRank=species", s)
        if html is None:
            wrong.append(f"prepare{tool}Results.do?resultRank=species: not a normal page")
            continue
        h = re.search(r'<h2 class="display_count">(.*?)</h2>', html, re.S)
        m = re.search(pattern, ns.text_of(h.group(1)) if h else "")
        listed = len(ns.list_rows(html))
        if not m:
            wrong.append(f"{tool} species: header {ns.text_of(h.group(1)) if h else None!r}, {listed} rows listed (B50)")
            continue
        if ns.n(m.group(1)) != listed:
            wrong.append(f"{tool} species: {m.group(0)}, {listed} rows listed")
        if listed > species:
            wrong.append(f"{tool} species: {listed} rows, the search has {species} species")
        if tool == "Map" and ns.n(m.group(2)) > specimens:
            wrong.append(f"{m.group(0)}; the search has {specimens} specimens")
    assert not wrong, f"{genus} of {country} {ns.day_note()}:\n" + "\n".join(wrong)


def _georeferenced(r):
    try:
        lat, lon = float(r.get("decimalLatitude")), float(r.get("decimalLongitude"))
    except (TypeError, ValueError):
        return False
    return lat != 0 and lon != 0


@pytest.mark.skipif(not IS_STAGING, reason="curator test login: staging only")
@needs("noupload")
def test_comparison_counts_the_chosen_specimens():
    """Row 70: compareResults.do "N specimens to compare." = the specimens chosen on the compare page (up to 3), and
    the specimens it shows are those."""
    country, genus, _html, _total, _api = sample()
    s = _logged_in()
    ns.fetch(search_url(country, genus), s)
    prep = ns.fetch("/prepareCompareResults.do?resultRank=specimen", s) or ""
    values = chosen_values(prep)
    codes = [c for c, _p, _h in page_rows(prep)]
    csrf = re.search(r'name="_csrf" value="([^"]+)"', prep)
    if len(values) < 2 or not csrf or len(codes) != len(values):
        pytest.skip(f"compare page of {genus} of {country} with fewer than 2 imaged specimens {ns.day_note()}")
    pick = list(range(min(3, len(values))))
    r = s.post(BASE_URL + "/compareResults.do", timeout=ns.SLOW,
               data={"_csrf": csrf.group(1), "resultRank": "specimen", "chosen": [values[i] for i in pick]})
    m = re.search(r"<h2>(\d+) specimens to compare\.</h2>", r.text)
    assert m, f"compareResults.do: no 'N specimens to compare.' (HTTP {r.status_code})"
    assert int(m.group(1)) == len(pick), f"{m.group(0)} for {len(pick)} chosen {ns.day_note()}"
    shown = [unquote(unescape(c)).strip().lower() for c in re.findall(
        r'<div class="ratio_name">\s*<a href="[^"]*/specimen\.do\?name=([^"]+)"', r.text)]
    chosen = sorted(codes[i] for i in pick)
    assert sorted(shown) == chosen, f"compareResults.do shows {sorted(shown)}, chosen {chosen} {ns.day_note()}"


# ----------------------------------------------------------------------------------------------- rows 65, 66

def _collections_of_sample():
    country, genus, html, _total, _api = sample()
    codes = sorted(set(collection_codes_of_rows(html)))
    if not codes:
        pytest.skip(f"no specimen of {genus} of {country} with a collection code {ns.day_note()}")
    return country, genus, ns.pick(codes, 2, "collection")


def _search_pages(query, max_pages=5):
    """The rows of a paged specimen search (up to max_pages pages of 200), its N; None when larger."""
    s = new_session()
    html = ns.fetch(SEARCH + query + "&resultRank=specimen", s)
    total = search_total(html)
    if total is None or total > 200 * max_pages:
        return None, total
    pages = [html]
    for page in range(2, (total + 199) // 200 + 1):
        pages.append(ns.fetch(SEARCH + query + f"&resultRank=specimen&page={page}", s) or "")
    return pages, total


def test_collection_counts_equal_a_search_and_the_api():
    """Row 65: collection.do "N specimens of M species": N = the specimens listed = the search by that collection code
    (the same specimens); M = the distinct genus + species of their API records, names not identified to genus left
    out (B52)."""
    country, genus, codes = _collections_of_sample()
    wrong, checked = [], []
    for code in codes:
        html = ns.fetch("/collection.do?name=" + quote_plus(code))
        m = re.search(r"<h3>([\d,]+) specimens(?: of ([\d,]+) species)?</h3>", html or "")
        if not m:
            continue
        n, species = ns.n(m.group(1)), ns.n(m.group(2) or "0")
        listed = sorted({code_of(chunk) for chunk in re.findall(r'specimen\.do\?name=[^"\'>]+', html)} - {None})
        pages, total = _search_pages(f"&collectionCodeSearchType=equals&collectionCode={quote_plus(chr(34) + code + chr(34))}")
        checked.append(code)
        if len(listed) != n:
            wrong.append(f"collection {code}: {n} specimens, {len(listed)} listed")
        if total != n:
            wrong.append(f"collection {code}: {n} specimens, search by its code {total}")
        if pages is not None:
            found = sorted({c for p in pages for c, _ph, _h in page_rows(p)} - {None})
            if found != listed:
                wrong.append(f"collection {code}: codes listed {listed[:5]}..., search {found[:5]}...")
        if n <= 80:
            names = set()
            for c in listed:
                rows = ns.api_rows(ns.api("specimens?specimenCode=" + quote_plus(c)))
                if not rows:
                    names = None
                    break
                g = (rows[0].get("genus") or "").lower()
                if identified_to_genus(g):
                    names.add((g, (rows[0].get("species") or "").lower()))
            if names is not None and len(names) != species:
                wrong.append(f"collection {code}: {species} species, {len(names)} distinct names identified to genus in the API")
    if not checked:
        pytest.skip(f"no collection page of {genus} of {country} with 'N specimens' {ns.day_note()}")
    assert not wrong, f"{genus} of {country} {ns.day_note()}:\n" + "\n".join(wrong)


def test_locality_collections_equal_the_codes_of_its_specimens():
    """Row 66: locality.do "N collections" = the collections listed = the distinct collection codes of the locality's
    specimens (the search by its locality code, as the page's "Show All Specimens" link; B51)."""
    country, genus, codes = _collections_of_sample()
    wrong, checked = [], []
    for code in codes:
        coll = ns.fetch("/collection.do?name=" + quote_plus(code)) or ""
        loc = re.search(r"locality\.do\?code=([^\"'&<> ]+)", coll)
        if not loc:
            continue
        lcode = unquote(unescape(loc.group(1)))
        html = ns.fetch("/locality.do?code=" + quote_plus(lcode))
        m = re.search(r"<h3>([\d,]+)&nbsp;collections?\s*</h3>", html or "")
        if not m:
            continue
        n = ns.n(m.group(1))
        data = html[m.end():]
        rows = len(re.findall(r'<div class="browse_col_one">', data)) - 1   # minus the column header
        listed = sorted({unquote(unescape(c)).strip().lower() for c in re.findall(r'collection\.do\?name=([^"\'&<> ]+)', data)})
        pages, total = _search_pages(f"&localityCodeSearchType=equals&localityCode={quote_plus(chr(34) + lcode + chr(34))}")
        if pages is None:
            continue
        checked.append(lcode)
        expected = sorted({c for p in pages for c in collection_codes_of_rows(p)})
        if rows != n:
            wrong.append(f"locality {lcode}: {n} collections, {rows} rows listed")
        if len(listed) != n:
            wrong.append(f"locality {lcode}: {n} collections, {len(listed)} different collection codes listed")
        if listed != expected:
            wrong.append(f"locality {lcode}: collections listed {listed[:6]}, its {total} specimens have {expected[:6]}")
    if not checked:
        pytest.skip(f"no locality page found from the collections of {genus} of {country} {ns.day_note()}")
    assert not wrong, f"{genus} of {country} {ns.day_note()}:\n" + "\n".join(wrong)


# ----------------------------------------------------------------------------------------------- row 45 (admin)

def _row_number(chunk):
    """The place's number in a taxon list row: the last "list_extras specimens" cell (the first is Global on lists
    that show it), 0 for "No ..."."""
    cells = re.findall(r'<div class="list_extras specimens">(.*?)</div>', chunk, re.S)
    if not cells:
        return 0
    m = re.search(r"<span class='numbers'>(\d+)</span>", cells[-1])
    return int(m.group(1)) if m else 0


def _subtaxon(html):
    m = re.search(r"Subtaxon total: (-?\d+)", html or "")
    return int(m.group(1)) if m else None


@pytest.mark.skipif(not IS_STAGING, reason="admin test login: staging only")
@needs("admin")
def test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place():
    """Row 45: the admin "Subtaxon total" of taxonomicPage.do = the sum of the rows' numbers, for the day's place of
    every kind; rank genus = the place's Species, rank subfamily = Genera (validated by the count check), rank species
    <= Specimens (names not identified to genus are in Specimens only); and on browse.do of a genus of the day's
    country = the sum of its rows, its rows = the genus row's "N Species"."""
    s, _token, _id = login("admin")
    wrong, checked = [], []
    for p in ns.one_place_of_each_kind("subtaxon"):
        stats = ns.place_stats(ns.fetch(p.page, s) or "")
        if "species" not in stats:
            continue
        for rank, stat, rule in (("subfamily", "genera", "="), ("genus", "species", "="), ("species", "specimens", "<=")):
            if rank == "species" and stats.get("species", 0) > 1500:
                continue   # a list too long for the time of a test
            html = ns.fetch(p.list_url(rank), s)
            total = _subtaxon(html)
            if total is None:
                continue
            rows = ns.list_rows(html)
            summed = sum(_row_number(r["html"]) for r in rows)
            if total != summed:
                wrong.append(f"{p} rank={rank}: Subtaxon total {total}, rows add up to {summed}")
            expected = stats.get(stat, 0)
            if (rule == "=" and total != expected) or (rule == "<=" and total > expected):
                wrong.append(f"{p} rank={rank}: Subtaxon total {total}, place {stat} {expected} (must be {rule})")
            checked.append(f"{p} {rank}")
            if p.kind == "country" and rank == "genus" and rows:
                r = ns.pick(rows, 1, "subtaxon-browse")[0]
                link = re.search(r"href='[^']*/browse\.do\?([^']+)'", r["html"])
                if link:
                    browse = ns.fetch("/browse.do?" + unescape(link.group(1)), s)
                    btotal = _subtaxon(browse)
                    brows = ns.list_rows(browse or "")
                    if btotal is not None:
                        bsum = sum(_row_number(b["html"]) for b in brows)
                        if btotal != bsum:
                            wrong.append(f"{p} browse.do?{link.group(1)}: Subtaxon total {btotal}, rows add up to {bsum}")
                        if len(brows) != _row_number(r["html"]):
                            wrong.append(f"{p} browse.do?{link.group(1)}: {len(brows)} rows, genus row {_row_number(r['html'])} Species")
    if not checked:
        pytest.skip("no list with a Subtaxon total " + ns.day_note())
    assert not wrong, f"{len(checked)} lists {ns.day_note()} (place numbers of the last nightly run):\n" + "\n".join(wrong)


# ----------------------------------------------------------------------------------------------- row 19

def _statset_rows(section):
    """StatSet rows of statsPage.do: name -> [4 numbers] (<tr align=right><td>name</td><td>n</td>...)."""
    rows = {}
    for name, cells in re.findall(r"<tr align=right><td>([^<]*)</td>((?:<td>[\d,]+</td>){4})</tr>", section):
        rows[name.strip()] = [ns.n(v) for v in re.findall(r"<td>([\d,]+)</td>", cells)]
    return rows


def test_statspage_valid_fossil_equals_the_museum_lists():
    """Row 19: statsPage.do Museum Data, Valid Fossil, for the day's museums (two with fossils, one other): valid
    fossil subfamilies, genera, species + subspecies = the headers of the museum's lists with statusSet "valid fossil"
    (species list: species and subspecies; its rows split them)."""
    html = ns.get("/statsPage.do")
    i = html.find("<h3>Valid Fossil:</h3>")
    if i < 0:
        pytest.skip("statsPage.do without the Valid Fossil table")
    j = html.find("</table>", i)
    fossil = _statset_rows(html[i:j if j > 0 else len(html)])
    if not fossil:
        pytest.skip("statsPage.do Valid Fossil table without rows")
    with_fossils = sorted(c for c, v in fossil.items() if any(v))
    others = sorted(c for c, v in fossil.items() if not any(v))
    sample_codes = ns.pick(with_fossils, 2, "fossil") + ns.pick(others, 1, "fossil-none")
    wrong, checked = [], []
    for code in sample_codes:
        sub, gen, spe, ssp = fossil[code]
        params = f"museumCode={quote_plus(code)}&statusSet={quote_plus('valid fossil')}"
        got = {}
        for rank in ("subfamily", "genus", "species"):
            page = ns.fetch(f"/taxonomicPage.do?rank={rank}&{params}")
            if page is None:
                break
            got[rank] = (ns.header(page)[0], ns.list_rows(page))
        if len(got) < 3 or None in [v[0] for v in got.values()]:
            continue
        checked.append(code)
        for rank, shown in (("subfamily", sub), ("genus", gen), ("species", spe + ssp)):
            if got[rank][0] != shown:
                wrong.append(f"{code} {rank}: statsPage {shown}, list header {got[rank][0]}")
        names = [r["name"] for r in got["species"][1]]
        if names and None not in names:
            three = sum(1 for nm in names if len(nm.split()) == 3)
            if (len(names) - three, three) != (spe, ssp):
                wrong.append(f"{code}: statsPage {spe} species and {ssp} subspecies, the list {len(names) - three} and {three}")
    if not checked:
        pytest.skip("no museum list readable " + ns.day_note())
    assert not wrong, f"{len(checked)} museums {ns.day_note()}:\n" + "\n".join(wrong)

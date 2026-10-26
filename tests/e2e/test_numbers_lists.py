"""The numbers of the taxonomic lists and browse pages of every kind of place (docs/numbers.md rows 38, 39, 46, 47,
49), 2026-10-26.

test_numbers_live.py checks these on Madagascar and the genus Pheidole. Here every kind of place (country, adm1,
region, subregion, bioregion, museum, project) is checked on every run, a different place of each kind each day
(numbers_sample.py), against numbers that the count check validates on every place:

  row 38  taxonomicPage.do header "N Species / Genera / Subfamilies" with every counted status (statusSet=all) = the
          place page's Species / Genera / Subfamilies, and with valid extant = Valid species (one rule:
          PlaceTotals.rankCondition and TaxaPage.fetchChildren); the header = the rows the page lists.
  row 39  isImaged=false: "U Unimaged Species (out of N)": U = the rows listed, N = the place's Species, and the
          names listed = the names of the list that the API's /unimagedGeolocaleTaxa returns for the place (the
          same rule written twice: TaxaPage.findUnimaged in Java, imagedTaxaSql in the API) (countries).
  row 46  browse.do header "N Species" of a genus = the genus row's "N Species" on the place's list (validated by
          inv:list.genus), "N Genera" of a subfamily = the subfamily row's "N Genera" (inv:list.subfamily), and the
          rows the browse page lists; "N Specimens" of a species = the species row's "N Specimens" (B15: the browse
          page and the count use different place rules; xfail until fixed).
  row 47  "(Only the first k of N displayed)" (species browse with limit=k): N = the header's N; the page shows k rows
          (B42: it shows k + 1; xfail until fixed).
  row 49  species browse "(M imaged)" and the hidden "M Imaged" = the specimens listed that have an image (rows
          without the no-photos mark, and the API's imageCount of those specimens); genus browse: M <= N only.

Read-only, anonymous. A number stored by the last nightly run can differ from the live list after an upload until
the next nightly run; the failure message says so.
"""
import os
import re

import pytest

import numbers_sample as ns

PENDING = "the place numbers and list rows are those of the last nightly run; after an upload they are pending"
MAX_SPECIES_BROWSE = int(os.environ.get("ANTWEB_SAMPLE_MAX_SPECIMENS", "300"))

_PLACES = {}


def places():
    if "all" not in _PLACES:
        _PLACES["all"] = ns.one_place_of_each_kind("lists")
    if not _PLACES["all"]:
        pytest.skip("no place found (API /geolocales, /bioregions, museums.do, projects.do) " + ns.day_note())
    return _PLACES["all"]


_STATS = {}


def stats_of(p):
    key = repr(p)
    if key not in _STATS:
        _STATS[key] = ns.place_stats(ns.fetch(p.page) or "")
    return _STATS[key]


def test_list_headers_equal_place_numbers_of_every_kind():
    """Row 38: for the day's place of every kind, the list header with every counted status = Subfamilies, Genera,
    Species of the place page; with valid extant = Valid species (0 when the page shows no Valid row); every header
    = the rows its page lists."""
    wrong, checked, slow = [], [], []
    for p in places():
        stats = stats_of(p)
        if "species" not in stats:
            continue
        for rank, stat, status in (("subfamily", "subfamilies", "all"), ("genus", "genera", "all"),
                                   ("species", "species", "all"), ("species", "valid", "valid extant")):
            html = ns.fetch(p.list_url(rank, status))
            if html is None:
                slow.append(f"{p} {rank} {status}")
                continue
            n, text = ns.header(html)
            if n is None:
                wrong.append(f"{p} rank={rank} {status}: no header ({text!r})")
                continue
            rows = len(ns.list_rows(html))
            if rows != n:
                wrong.append(f"{p} rank={rank} {status}: header {text!r}, {rows} rows listed")
            expected = stats.get(stat, 0)
            if n != expected:
                wrong.append(f"{p} rank={rank} {status}: header {n}, place page {stat} {expected}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place page with data-stat numbers " + ns.day_note())
    assert not wrong, (f"{len(checked)} places {ns.day_note()} ({PENDING}); not read: {slow}:\n" + "\n".join(wrong))


def _unimaged_from_api(p):
    """The names the API calls unimaged on the place's list, or None (also when paging is not reliable)."""
    rows, offset = [], 0
    for _ in range(20):
        body = ns.api(f"unimagedGeolocaleTaxa?geolocaleName={ns.quote_plus(p.name)}&taxarank=species&limit=750&offset={offset}")
        if body is None:
            return None
        page = [(r.get("taxonName") or "").lower() for r in ns.api_rows(body)]
        rows += page
        meta = body.get("metaData") or {}
        if not page or (len(page) < 750 and not meta.get("limitNotice")):
            # without an order, pages could overlap: then the set is smaller than the rows returned
            return set(rows) if len(set(rows)) == len(rows) else None
        offset += len(page)
    return None


def test_unimaged_names_equal_the_api():
    """Row 39: on Madagascar and the day's country, isImaged=false lists U names under "U Unimaged Species (out of N)":
    U = the rows listed, N = the place's Species, and the names = the names of the full list that the API's
    /unimagedGeolocaleTaxa returns (no photo of the ant anywhere in AntWeb)."""
    adm1_names = {r.get("geolocaleName") for r in ns.geolocales("adm1")}
    countries = [r for r in ns.geolocales("country") if r.get("geolocaleName") not in adm1_names]   # the API finds a place by name
    sample = [ns.madagascar()] + [ns.geolocale_place(r) for r in ns.pick(countries, 1, "unimaged")]
    wrong, checked = [], []
    for p in sample:
        stats = stats_of(p)
        html = ns.fetch(p.list_url("species", extra="&isImaged=false"))
        full = ns.fetch(p.list_url("species"))
        if not stats.get("species") or html is None or full is None:
            continue
        m = re.search(r'<span class="numbers">([\d,]+)</span> Unimaged Species \(out of <span class="numbers">([\d,]+)</span>\)', html)
        if not m:
            wrong.append(f"{p}: isImaged=false without 'U Unimaged Species (out of N)'")
            continue
        unimaged, total = ns.n(m.group(1)), ns.n(m.group(2))
        shown = {r["name"] for r in ns.list_rows(html) if r["name"]}
        names = {r["name"] for r in ns.list_rows(full) if r["name"]}
        if len(ns.list_rows(html)) != unimaged:
            wrong.append(f"{p}: {unimaged} Unimaged, {len(ns.list_rows(html))} rows listed")
        if total != stats["species"]:
            wrong.append(f"{p}: 'out of {total}', place Species {stats['species']}")
        api = _unimaged_from_api(p)
        if api is not None:
            expected = api & names
            if shown != expected:
                wrong.append(f"{p}: page {len(shown)} unimaged names, API {len(expected)}; only on the page"
                             f" {sorted(shown - expected)[:5]}, only in the API {sorted(expected - shown)[:5]}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no country list readable " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- browse pages

def _row_count_and_link(row_html):
    """A list row's own count ("N Species", "N Genera", "N Specimens" of this place, not the Global column) and the
    browse.do link it carries; (None, None) when the row has none."""
    for cell in re.findall(r'<div class="list_extras specimens">(.*?)</div>', row_html, re.S):
        if "global=true" in cell:
            continue
        link = re.search(r"href='([^']*browse\.do\?[^']*)'", cell)
        num = re.search(r"<span class='numbers'>(\d+)</span>", cell)
        if num:
            return int(num.group(1)), link and link.group(1)
        if "No " in cell:
            return 0, None
    return None, None


def _browse(link):
    path = re.sub(r"^https?://[^/]+", "", link)
    return path + ("&" if "?" in path else "?") + "statusSet=all"


def _browse_numbers(html):
    n, text = ns.header(html)
    imaged = re.search(r"\(<span class=\"numbers\">([\d,]+)</span> imaged\)", html.split('id="totalcount"')[0])
    hidden_n = re.search(r'<div id="totalcount"[^>]*><span class="numbers">([\d,]+)</span>', html)
    hidden_m = re.search(r'<div id="imagecount"[^>]*><span class="numbers">([\d,]+)</span>.*?out of <span class="numbers">([\d,]+)</span>', html, re.S)
    return {"n": n, "text": text, "m": ns.n(imaged.group(1)) if imaged else 0,
            "hidden_n": ns.n(hidden_n.group(1)) if hidden_n else None,
            "hidden_m": ns.n(hidden_m.group(1)) if hidden_m else None,
            "hidden_m_of": ns.n(hidden_m.group(2)) if hidden_m else None}


def _consistent(where, b, rows, wrong):
    """The header, the hidden totals and the rows listed of one browse page agree."""
    if b["n"] is None:
        wrong.append(f"{where}: no header")
        return
    if b["hidden_n"] is not None and b["hidden_n"] != b["n"]:
        wrong.append(f"{where}: hidden total {b['hidden_n']}, header {b['text']!r}")
    if b["hidden_m_of"] is not None and b["hidden_m_of"] != b["n"]:
        wrong.append(f"{where}: hidden 'out of {b['hidden_m_of']}', header {b['text']!r}")
    if b["hidden_m"] is not None and b["hidden_m"] != b["m"]:
        wrong.append(f"{where}: hidden {b['hidden_m']} imaged, header {b['m']} imaged")
    if b["m"] > b["n"]:
        wrong.append(f"{where}: {b['m']} imaged of {b['n']}")
    if rows is not None and b["n"] <= 2000 and rows != b["n"]:
        wrong.append(f"{where}: header {b['text']!r}, {rows} rows listed")


def _pick_row(p, rank, salt, max_count=None):
    """The day's row (with a count above 0 and a browse link) of the place's list of that rank: (row, count, link)."""
    html = ns.fetch(p.list_url(rank))
    if html is None:
        return None
    rows = []
    for r in ns.list_rows(html):
        count, link = _row_count_and_link(r["html"])
        if count and link and (max_count is None or count <= max_count):
            rows.append((r, count, link))
    got = ns.pick(rows, 1, salt + repr(p))
    return got[0] if got else None


def test_browse_headers_equal_the_list_rows():
    """Row 46 (Species, Genera) and 49 (consistency): for the day's place of every kind, the day's genus row of its
    list: browse.do of the genus says "N Species" = the row's "N Species" and lists N rows; the day's subfamily row:
    browse.do says "N Genera" = the row's "N Genera" and lists N rows; on both, the hidden totals say the same N and
    M, and M imaged <= N."""
    wrong, checked = [], []
    for p in places():
        for rank in ("genus", "subfamily"):
            got = _pick_row(p, rank, "browse" + rank)
            if not got:
                continue
            row, count, link = got
            html = ns.fetch(_browse(link))
            if html is None:
                continue
            where = f"{p} {rank} {row['name'] or link}"
            b = _browse_numbers(html)
            _consistent(where, b, len(ns.list_rows(html)), wrong)
            if b["n"] is not None and b["n"] != count:
                wrong.append(f"{where}: browse header {b['text']!r}, list row {count}")
            checked.append(where)
    if not checked:
        pytest.skip("no list row with a browse link found " + ns.day_note())
    assert not wrong, f"{len(checked)} browse pages {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


_SPECIES = {}


def _species_browse(p):
    """The day's species row (at most MAX_SPECIES_BROWSE specimens) of the place's list and its browse page."""
    key = repr(p)
    if key not in _SPECIES:
        got = _pick_row(p, "species", "browse-species", MAX_SPECIES_BROWSE)
        html = ns.fetch(_browse(got[2])) if got else None
        _SPECIES[key] = (got, html) if html else None
    return _SPECIES[key]


def _specimen_codes(html):
    return [m.lower() for m in re.findall(r'<div class="sd_specimen_code">.*?specimen\.do\?code=([^"&]+)"', html, re.S)]


def _api_image_counts(link):
    """specimen code -> the API's imageCount, for every specimen of the species of a species browse link."""
    p = dict(kv.split("=", 1) for kv in re.findall(r"(?:^|[?&])((?:genus|species)=[^&\"']+)", link))
    if not p.get("genus") or not p.get("species"):
        return None
    rows = ns.api_all(f"specimens?genus={p['genus']}&species={p['species']}", max_pages=4)
    return None if rows is None else {(r.get("specimenCode") or "").lower(): int(r.get("imageCount") or 0) for r in rows}


def test_species_browse_imaged_count_equals_the_specimens_with_images():
    """Row 49 (and 46: header = rows): for the day's place of every kind, the day's species row (at most
    ANTWEB_SAMPLE_MAX_SPECIMENS specimens): its browse page says "N specimens (M imaged)"; N = the specimens listed;
    M = the hidden "M Imaged" = the listed specimens without the no-photos mark = the listed specimens whose API
    imageCount is above 0 (specimen.image_count, validated by inv:taxon.images)."""
    wrong, checked = [], []
    for p in places():
        got = _species_browse(p)
        if not got:
            continue
        (row, count, link), html = got
        where = f"{p} {row['name'] or link}"
        b = _browse_numbers(html)
        rows = ns.list_rows(html)
        _consistent(where, b, len(rows), wrong)
        with_photos = sum(1 for r in rows if not r["no_photos"])
        if b["n"] is not None and len(rows) == b["n"] and with_photos != b["m"]:
            wrong.append(f"{where}: {b['m']} imaged, {with_photos} of the {len(rows)} specimens listed have photos")
        counts = _api_image_counts(link)
        codes = _specimen_codes(html)
        if counts is not None and codes and all(c in counts for c in codes) and len(codes) == b["n"]:
            api_imaged = sum(1 for c in codes if counts[c] > 0)
            if api_imaged != b["m"]:
                wrong.append(f"{where}: {b['m']} imaged, the API gives images to {api_imaged} of the {len(codes)} specimens listed")
        checked.append(where)
    if not checked:
        pytest.skip("no species row with a browse link found " + ns.day_note())
    assert not wrong, f"{len(checked)} species browse pages {ns.day_note()}:\n" + "\n".join(wrong)


@pytest.mark.xfail(reason="B15: the browse page counts a species' specimens with Overview.getOverviewCriteria, the list row"
                          " with the place rule of the nightly count", strict=False)
def test_species_browse_counts_the_specimens_of_its_list_row():
    """Row 46 (Specimens; B15): the species row's "N Specimens" on a place's list links to browse.do, whose header
    must say the same N. For the day's place of every kind."""
    wrong, checked = [], []
    for p in places():
        got = _species_browse(p)
        if not got:
            continue
        (row, count, link), html = got
        b = _browse_numbers(html)
        if b["n"] != count:
            wrong.append(f"{p} {row['name'] or link}: list row {count} Specimens, browse header {b['text']!r}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no species row with a browse link found " + ns.day_note())
    assert not wrong, f"{ns.day_note()}:\n" + "\n".join(wrong)


def _limited(p, k):
    got = _species_browse(p)
    if not got:
        return None
    (row, count, link), html = got
    b = _browse_numbers(html)
    if b["n"] is None or b["n"] <= k + 1:
        return None
    limited = ns.fetch(_browse(link) + f"&limit={k}")
    return None if limited is None else (row, link, b, limited)


def test_browse_limit_note_names_the_header_number():
    """Row 47: species browse with limit=k (k below N): "(Only the first k of N displayed ...)" with N = the header's N."""
    wrong, checked = [], []
    for p in places():
        got = _limited(p, 3)
        if not got:
            continue
        row, link, b, limited = got
        m = re.search(r"Only the first ([\d,]+) of ([\d,]+) displayed", limited)
        if not m:
            wrong.append(f"{p} {row['name'] or link} limit=3: no 'Only the first k of N displayed' ({b['text']!r})")
        elif ns.n(m.group(1)) != 3 or ns.n(m.group(2)) != b["n"]:
            wrong.append(f"{p} {row['name'] or link}: {m.group(0)!r}, header {b['text']!r}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no species with more than 4 specimens found " + ns.day_note())
    assert not wrong, f"{ns.day_note()}:\n" + "\n".join(wrong)


@pytest.mark.xfail(reason="B42: specimenReport.jsp shows rows index 0 to limit (limit + 1 rows) under 'Only the first limit'",
                   strict=False)
def test_browse_limit_shows_that_many_specimens():
    """Row 47 (B42): with "(Only the first k of N displayed)" the page lists k specimens."""
    wrong, checked = [], []
    for p in places():
        got = _limited(p, 3)
        if not got:
            continue
        row, link, b, limited = got
        shown = len(ns.list_rows(limited))
        if shown != 3:
            wrong.append(f"{p} {row['name'] or link}: 'Only the first 3', {shown} specimens listed")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no species with more than 4 specimens found " + ns.day_note())
    assert not wrong, f"{ns.day_note()}:\n" + "\n".join(wrong)

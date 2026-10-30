"""The numbers of the taxonomic lists and browse pages of every kind of place (docs/numbers.md rows 38, 39, 46, 47,
49), 2026-10-26.

test_numbers_live.py checks these on Madagascar and the genus Pheidole. Here every kind of place (country, adm1,
region, subregion, bioregion, museum, project) is checked on every run, a different place of each kind each day
(numbers_sample.py), against numbers that the count check validates on every place:

  row 38  taxonomicPage.do header: species list with "Valid species + morphospecies" (statusSet=validmorpho) = the
          place page's Species / subspecies / morphospecies, with "Valid species" (valid) = Valid species / subspecies;
          genus and subfamily lists with every counted status (all) = Genera / Subfamilies (one rule:
          PlaceTotals.rankCondition and TaxaPage.fetchChildren); the header = the rows the page lists. Genera and
          subfamilies rule (2026-10-30, every kind of place, curated projects too; it replaced B67): Subfamilies and
          Genera count only the listed rows that are valid subfamilies (no placeholders such as "(formicidae)", no
          unrecognized names) and genera (valid genera and morphogenera), both from the API /taxa, so they are compared
          with those rows; the
          header still counts every row listed. A subfamily browse page likewise (row 46).
          The status choices and defaults of 2026-10-30 (Valid species on geolocales and curated projects, Valid
          species + morphospecies on bioregions and museums) are checked in test_status_choice.py.
  row 39  isImaged=false: "U Unimaged Species (out of N)": U = the rows listed, N = the place's Species, and the
          names listed = the names of the list that the API's /unimagedGeolocaleTaxa returns for the place (the
          same rule written twice: TaxaPage.findUnimaged in Java, imagedTaxaSql in the API) (countries).
  row 46  browse.do header "N Species" of a genus = the genus row's "N Species" on the place's list (validated by
          inv:list.genus), "N Genera" of a subfamily = the subfamily row's "N Genera" (inv:list.subfamily), and the
          rows the browse page lists; "N Specimens" of a species = the species row's "N Specimens" (B15: the browse
          page and the count use different place rules; xfail until fixed).
  row 47  "(Only the first k of N displayed)" (species browse with limit=k): N = the header's N; the page shows k rows
          (B42: it shows k + 1; xfail until fixed).
  row 49  species browse "(M imaged)" and the hidden "M Imaged" = the specimens listed that have a photo of the ant
          (rows without the no-photos mark; label photos do not count, B71), at most the specimens the API's
          imageCount gives images to; genus browse: M <= N only.

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
    """Row 38: for the day's place of every kind, the list header with every counted status = Subfamilies, Genera of
    the place page; species with Valid species + morphospecies = Species; with Valid species = Valid species (0 when
    the page shows no Valid row); every header = the rows its page lists."""
    wrong, stored, stored_genera, checked, slow = [], [], [], [], []
    for p in places():
        stats = stats_of(p)
        if "species" not in stats:
            continue
        for rank, stat, status in (("subfamily", "subfamilies", "all"), ("genus", "genera", "all"),
                                   ("species", "species", "validmorpho"), ("species", "valid", "valid")):
            html = ns.fetch(p.list_url(rank, status))
            if html is None:
                slow.append(f"{p} {rank} {status}")
                continue
            n, text = ns.header(html)
            if n is None:
                wrong.append(f"{p} rank={rank} {status}: no header ({text!r})")
                continue
            listed = ns.list_rows(html)
            rows = len(listed)
            if rows != n:
                wrong.append(f"{p} rank={rank} {status}: header {text!r}, {rows} rows listed")
            expected = stats.get(stat, 0)
            # Genera and subfamilies rule (Brian Fisher, 2026-10-30): Subfamilies and Genera count the listed rows that
            # are valid subfamilies (not placeholders such as "(formicidae)", not unrecognized names) and genera (valid genera and morphogenera; not "(myrmicinae)" or
            # unrecognized genus names, which stay on the list): the header counts every row listed.
            if rank in ("subfamily", "genus"):
                counted = ns.counted_rows(listed, rank)
                if counted is None:
                    slow.append(f"{p} {rank} {status} (row names or API genera not read)")
                    continue
                n = counted
            # (B67, a curated project's Subfamilies of every status, was superseded on 2026-10-30: every kind of place,
            # curated projects too, counts valid subfamilies only)
            if n != expected:
                # Subfamilies and Genera apart (2026-10-30 review): the genera and subfamilies rule's pending covers them only
                (stored_genera if rank in ("subfamily", "genus") else stored).append(
                    f"{p} rank={rank} {status}: {'rows counted' if rank != 'species' else 'header'} {n}, place page {stat} {expected}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place page with data-stat numbers " + ns.day_note())
    # the place page's numbers are stored by the nightly run, the list header is counted live: until a nightly run has
    # counted by the counting rules of 2026-10-28 they differ (pending); the header against its own rows stays strict
    rules_pending = ns.counting_rules_counted() is False
    pending_species = bool(stored) and rules_pending
    # Subfamilies and Genera: also until a nightly run has counted by the genera and subfamilies rule of 2026-10-30
    # (that pending never hides a species difference: the lists are split, as in test_numbers_live.py)
    pending_genera = bool(stored_genera) and (rules_pending or ns.genera_rule_counted() is False)
    if not pending_species:
        wrong += stored
    if not pending_genera:
        wrong += stored_genera
    assert not wrong, (f"{len(checked)} places {ns.day_note()} ({PENDING}); not read: {slow}:\n" + "\n".join(wrong))
    if pending_species or pending_genera:
        pending = (stored if pending_species else []) + (stored_genera if pending_genera else [])
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28 and the genera and subfamilies rule of 2026-10-30") + ":\n" + "\n".join(pending))


def test_default_list_forgets_the_last_choice():
    """Brian Fisher, 2026-10-29 (kept 2026-10-30): a status chosen on one page is not carried to the next (it was kept
    in the session). The same session first opens a species list with Fossil species, then the list without a status:
    its header is that of the place's default choice (test_status_choice.py checks the defaults themselves)."""
    wrong, checked = [], []
    s = ns.new_session()
    for p in places():
        default = "valid" if p.kind in ("country", "adm1", "region", "subregion", "project") else "validmorpho"
        if ns.fetch(p.list_url("species", "fossil"), s) is None:
            continue
        html = ns.fetch(f"/taxonomicPage.do?rank=species&{p.params}", s)
        explicit = ns.fetch(p.list_url("species", default), s)
        if html is None or explicit is None:
            continue
        n, text = ns.header(html)
        n_default, text_default = ns.header(explicit)
        if n is None or n != n_default or text != text_default:
            wrong.append(f"{p}: the list without a status says {text!r}, with statusSet={default} {text_default!r}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


def _unimaged_from_api(p):
    """The names the API calls unimaged on the place's list, or None (also when paging is not reliable). Species and
    subspecies, like the list (the API's default ranks; until 2026-10-27 taxarank=species left out subspecies, so
    Czech Republic's three unimaged subspecies showed as "only on the page")."""
    rows, offset = [], 0
    for _ in range(20):
        body = ns.api(f"unimagedGeolocaleTaxa?geolocaleName={ns.quote_plus(p.name)}&limit=750&offset={offset}")
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


def _browse(link, status="validmorpho"):
    """The browse page of a list row's link with the status choice that its row count counts (2026-10-30): a genus
    row's Species counts valid names and morphospecies (validmorpho), a subfamily row's Genera every counted status
    (all); a species page lists its specimens whatever the choice."""
    path = re.sub(r"^https?://[^/]+", "", link)
    return path + ("&" if "?" in path else "?") + "statusSet=" + status


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
    wrong, stored, stored_genera, checked, known = [], [], [], [], []
    for p in places():
        for rank in ("genus", "subfamily"):
            got = _pick_row(p, rank, "browse" + rank)
            if not got:
                continue
            row, count, link = got
            html = ns.fetch(_browse(link, "validmorpho" if rank == "genus" else "all"))
            if html is None:
                continue
            where = f"{p} {rank} {row['name'] or link}"
            b = _browse_numbers(html)
            browse_rows = ns.list_rows(html)
            _consistent(where, b, len(browse_rows), wrong)
            n = b["n"]
            if rank == "subfamily" and n is not None:
                # Genera rule (Brian Fisher, 2026-10-30): the list row's "N Genera" counts the genus rows that are genera
                # (valid genera and morphogenera); the browse page lists every genus row (placeholders such as
                # "(myrmicinae)" and unrecognized genus names too) and its header counts them all.
                n = ns.counted_rows(browse_rows, "genus")
                if n is None:
                    continue
            if n is not None and n != count:
                line = f"{where}: browse header {b['text']!r}" + (f" ({n} of its rows are genera)" if n != b["n"] else "") + f", list row {count}"
                # B77 (found 2026-10-27, also on production): a bioregion's subfamily browse page counts and lists
                # every genus row of the bioregion (Palearctic Ponerinae: 18, including "ponerine"), the list row
                # only the counted statuses (16). Known until fixed; any other kind of place or rank still fails.
                # The list row is stored by the nightly run, the browse header counted live (counting rules of
                # 2026-10-28: pending until a nightly run has counted by them).
                # a subfamily row's N Genera apart from a genus row's N Species (2026-10-30 review): the genera and
                # subfamilies rule's pending covers the N Genera only
                if p.kind == "bioregion" and rank == "subfamily":
                    known.append(line)
                else:
                    (stored_genera if rank == "subfamily" else stored).append(line)
            checked.append(where)
    if not checked:
        pytest.skip("no list row with a browse link found " + ns.day_note())
    rules_pending = ns.counting_rules_counted() is False
    pending_species = bool(stored) and rules_pending
    # a subfamily row's N Genera: also until a nightly run has counted by the genera and subfamilies rule of 2026-10-30
    pending_genera = bool(stored_genera) and (rules_pending or ns.genera_rule_counted() is False)
    if not pending_species:
        wrong += stored
    if not pending_genera:
        wrong += stored_genera
    assert not wrong, f"{len(checked)} browse pages {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
    if pending_species or pending_genera:
        pending = (stored if pending_species else []) + (stored_genera if pending_genera else [])
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28 and the genera and subfamilies rule of 2026-10-30") + ":\n" + "\n".join(pending + known))
    if known:
        pytest.xfail("B77: bioregion subfamily browse header counts genus rows the list row leaves out: " + "; ".join(known))


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
    M = the hidden "M Imaged" = the listed specimens without the no-photos mark <= the listed specimens whose API
    imageCount is above 0 (specimen.image_count, validated by inv:taxon.images; it counts label photos too, which
    never make a specimen imaged, B71)."""
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
            # B71 (2026-10-27): a specimen with only label photos is not imaged, but the API's imageCount counts
            # every photo, so the API can give images to more specimens than M, never to fewer.
            if api_imaged < b["m"]:
                wrong.append(f"{where}: {b['m']} imaged, the API gives images to only {api_imaged} of the {len(codes)} specimens listed")
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


# ---------------------------------------------------------------- counting rules of 2026-10-28 (rows 22, 38, 41)

SPECIES_STATUSES = {"valid", "morphotaxon"}   # PlaceTotals.SPECIES_STATUSES
NAMES = int(os.environ.get("ANTWEB_SAMPLE_NAMES", "6"))
GENUS_ONLY_NOTE = re.compile(r'<span id="genus-only-note" data-genus-only="(\d+)">([^<]*)')


def _status(name):
    rows = ns.api_rows(ns.api("taxa?taxonName=" + ns.quote_plus(name)))
    t = next((r for r in rows if (r.get("taxonName") or "").lower() == name), None)
    return None if t is None else (t.get("status") or "").lower()


def test_species_lists_show_valid_names_and_morphospecies_only():
    """Brian Fisher, 2026-10-28: a specimen counts at each rank it is identified to, and no further. For the day's place
    of every kind: a sample of the names of its species list (every counted status) are valid names or morphospecies
    (statuses from the API /taxa); the line under the list, "N specimens identified only to genus are not listed here:
    Lioponera (1), Pheidole (2).", adds up to its N; on the genus list, a row that says "No species on the list" names
    the specimens collected here that put it there (2026-10-30; until then "N specimens identified to genus only")."""
    wrong, checked = [], []
    for p in places():
        html = ns.fetch(p.list_url("species"))
        if html is None:
            continue
        names = [r["name"] for r in ns.list_rows(html) if r["name"]]
        for name in ns.pick(names, NAMES, "species-status" + p.name):
            status = _status(name)
            if status is not None and status not in SPECIES_STATUSES:
                wrong.append(f"{p}: {name} ({status}) is listed as a species")
        m = GENUS_ONLY_NOTE.search(html)
        if m:
            parts = [int(x) for x in re.findall(r"\((\d+)\)", m.group(2))]
            lead = re.match(r"\s*([\d,]+)", m.group(2))
            if sum(parts) != int(m.group(1)) or not lead or ns.n(lead.group(1)) != int(m.group(1)):
                wrong.append(f"{p}: the line under the species list does not add up: {m.group(2)!r} ({m.group(1)})")
        genus_html = ns.fetch(p.list_url("genus"))
        for row in ns.list_rows(genus_html or ""):
            # 2026-10-30: a genus row with no species on the list names the specimens collected here (other names)
            if "No species on the list" in row["html"] and 'class="other-names"' not in row["html"]:
                wrong.append(f"{p}: a genus row with no species on the list names no specimens: {ns.text_of(row['html'])[:120]}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no species list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)

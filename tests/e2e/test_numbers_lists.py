"""The numbers of the taxonomic lists and browse pages of every kind of place (docs/numbers.md rows 38, 39, 46, 47,
49), 2026-10-26.

test_numbers_live.py checks these on Madagascar and the genus Pheidole. Here every kind of place (country, adm1,
region, subregion, bioregion, museum, project) is checked on every run, a different place of each kind each day
(numbers_sample.py), against numbers that the count check validates on every place:

  row 38  taxonomicPage.do header: species list in the view Valid (statusSet=valid) = Valid species / subspecies, and
          the views nest (Valid <= Curated <= All specimen names; 2026-11-02: no view lists exactly the rows of Species /
          subspecies / morphospecies, which counts every morphospecies of the list, curated or not);
          genus and subfamily lists with every counted status (all) = Genera / Subfamilies (one rule:
          PlaceTotals.rankCondition and TaxaPage.fetchChildren); the header = the rows the page lists. Genera and
          subfamilies rule (2026-10-30, every kind of place, curated projects too; it replaced B67): Subfamilies and
          Genera count only the listed rows that are valid subfamilies (no placeholders such as "(formicidae)", no
          unrecognized names) and genera (valid genera and morphogenera), both from the API /taxa, so they are compared
          with those rows; the
          header still counts every row listed. A subfamily browse page likewise (row 46).
          The views and their default (Curated everywhere, 2026-11-02) are checked in test_status_choice.py.
  row 39  isImaged=false: "U Unimaged Species (out of N)": U = the rows listed, N = the place's Valid species (view Valid), and the
          names listed = the names of the list that the API's /unimagedGeolocaleTaxa returns for the place (the
          same rule written twice: TaxaPage.findUnimaged in Java, imagedTaxaSql in the API) (countries).
  row 46  browse.do header "N Species" of a genus (same view) = the genus row's Species on the place's list (validated by
          inv:list.genus), "N Genera" of a subfamily = the subfamily row's "N Genera" in the same view, Valid, Curated
          and All specimen names (B77, fixed 2026-11-03: the row counts the genus rows of its browse page in the view),
          and the rows the browse page lists; "N Specimens" of a species = the species row's "N Specimens" (B15: the browse
          page and the count use different place rules; xfail until fixed).
  row 47  "(Only the first k of N displayed)" (species browse with limit=k): N = the header's N; the page shows k rows
          (B42: it showed k + 1 until 2026-11-02).
  row 49  species browse "(M imaged)" and the hidden "M Imaged" = the specimens listed that have a photo of the ant
          (rows without the no-photos mark; label photos do not count, B71), at most the specimens the API's
          imageCount gives images to; genus browse: M <= N only.

Read-only, anonymous. A number stored by the last nightly run can differ from the live list after an upload until
the next nightly run; the failure message says so.
"""
import os
import re
from html import unescape

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
        html = ns.fetch(p.page) or ""
        stats = ns.place_stats(html)
        # 2026-11-04: Genera and Species are the rows of the default list, counted nightly (PlaceViewTotalDb); until a
        # nightly has counted this place the page shows its old stored totals
        stats["_view_counted"] = 'data-view-counted="true"' in html
        _STATS[key] = stats
    return _STATS[key]


# The two lists whose views count the fossil names (StatusSet.hasOwnStatusSets): checked on every run, besides the day's
# places. Their Valid species is a stored list total too (PlaceViewTotalDb.VALID_SPECIES; Fossil Ants 2026-11-11: Valid
# 856, Species / subspecies / morphospecies 868).
OWN_VIEW_LISTS = ("fossilants", "worldants")
OWN_ROW = re.compile(r'<tr data-stat="(subfamilies|genera|species|valid)" data-n="(\d+)"><td class="ps_name">[^<]*</td>'
                     r'<td class="ps_num"><a href="([^"]+)"')


def _own_view_list(project, wrong, slow, checked):
    """Fossil Ants and the AntCat list (Brian Fisher, 2026-11-11: Fossil Ants said Genera 259 beside a list of 258 and
    "Valid species / subspecies 3" beside a list of 868, and no test looked: the sample leaves these two out, and a
    place whose list totals were never counted was "pending"). Each of the four numbers = the header of the list its own
    link opens = the rows that list shows. For these two an uncounted page is a failure, not pending: the daily update
    counts them, and the count check says so when it did not (inv:place.view.totals)."""
    from public_pages import attr_unescape
    from urllib.parse import urlparse
    page = ns.fetch(f"/project.do?name={project}")
    if page is None:
        wrong.append(f"project {project}: the page could not be read")
        return
    rows = {stat: (int(n), attr_unescape(href)) for stat, n, href in OWN_ROW.findall(page)}
    if 'data-view-counted="true"' not in page:
        wrong.append(f"project {project}: its list totals are not counted (data-view-counted is not true): the page shows the "
                     "project's stored counts, not the headers of its lists (the daily update counts the site-wide lists since "
                     "2026-11-11; on the day of the change an administrator runs /utilData.do?action=placeViewTotals once)")
    lists = {}
    for stat in ("subfamilies", "genera", "valid", "species"):
        if stat not in rows:
            wrong.append(f"project {project}: no {stat} line with a link to its list")
            continue
        shown, link = rows[stat]
        query = urlparse(link).query
        view = "statusSet=valid" if stat == "valid" else "statusSet=curated"
        if not query.endswith(view):
            wrong.append(f"project {project} {stat}: the number opens {query!r}, not the view {view}")
        if query not in lists:
            lists[query] = ns.fetch("/taxonomicPage.do?" + query)
        html = lists[query]
        if html is None:
            slow.append(f"project {project} {stat} ({query})")
            continue
        n, text = ns.header(html)
        if n is None:
            wrong.append(f"project {project} {stat}: the list it opens has no header ({text!r})")
            continue
        listed = len(ns.list_rows(html))
        if listed != n:
            wrong.append(f"project {project} {stat}: header {text!r}, {listed} rows listed")
        if n != shown:
            wrong.append(f"project {project} {stat}: {shown} on the page, the list it opens says {text!r}")
    # every valid name is in Curated (Fossil Ants has morphospecies besides)
    if "valid" in rows and "species" in rows and rows["valid"][0] > rows["species"][0]:
        wrong.append(f"project {project}: Valid species {rows['valid'][0]} > Species / subspecies / morphospecies {rows['species'][0]}")
    checked.append(f"project {project}")


def test_list_headers_equal_place_numbers_of_every_kind():
    """Row 38 (Brian Fisher, 2026-11-04: each place page number is the header of the list it opens): for the day's
    place of every kind, Subfamilies, Genera and Species / subspecies / morphospecies of the place page = the list
    headers in the default view (Curated; "/utilData.do?action=placeViewTotals" counts them nightly), Valid species = the
    header in the view Valid (0 when the page shows no Valid row); Valid <= Curated <= All specimen names; every header =
    the rows its page lists. And on every run the two lists with their own views, Fossil Ants and the AntCat list
    (2026-11-11): each of the four numbers against the header of the list its own link opens (Valid: statusSet=valid, the
    others their default view Curated), Valid <= Species; uncounted there is a failure."""
    wrong, pending, checked, slow = [], [], [], []
    for project in OWN_VIEW_LISTS:
        _own_view_list(project, wrong, slow, checked)
    for p in places():
        stats = stats_of(p)
        if "species" not in stats:
            continue
        headers = {}
        for rank, stat, status in (("subfamily", "subfamilies", "curated"), ("genus", "genera", "curated"),
                                   ("species", "valid", "valid"), ("species", "species", "curated"), ("species", None, "all")):
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
            if rank == "species":
                headers[status] = n
            if stat is None:
                continue
            expected = stats.get(stat, 0)
            if n != expected:
                line = f"{p} rank={rank} {status}: header {n}, place page {stat} {expected}"
                # Subfamilies, Genera and Species follow the list once the nightly has counted the place (PlaceViewTotalDb)
                (pending if stat in ("subfamilies", "genera", "species") and not stats["_view_counted"] else wrong).append(line)
        # the views nest: every Valid name is Curated, every Curated name in All specimen names
        if len(headers) == 3 and not (headers["valid"] <= headers["curated"] <= headers["all"]):
            wrong.append(f"{p}: species headers Valid {headers['valid']}, Curated {headers['curated']}, All {headers['all']}: not nested")
        checked.append(repr(p))
    if not checked and not wrong:
        pytest.skip("no place page with data-stat numbers " + ns.day_note())
    assert not wrong, (f"{len(checked)} places {ns.day_note()} ({PENDING}); not read: {slow}:\n" + "\n".join(wrong))
    if pending:
        pytest.skip("place list totals not counted yet by a nightly run (PlaceViewTotalDb, 2026-11-04):\n" + "\n".join(pending))


def test_default_list_forgets_the_last_choice():
    """Brian Fisher, 2026-10-29 (kept 2026-10-30 and 11-02): a view chosen on one page is not carried to the next (it
    was kept in the session). The same session first opens a species list with Fossil, then the list without a view:
    its header is that of the default view, Curated (test_status_choice.py checks the default itself)."""
    wrong, checked = [], []
    s = ns.new_session()
    for p in places():
        default = "curated"
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
    U = the rows listed, N = the place's Valid species (the view Valid; 2026-11-02: no view lists the rows of Species),
    and the names = the names of the full list that the API's /unimagedGeolocaleTaxa returns (no photo of the ant
    anywhere in AntWeb)."""
    adm1_names = {r.get("geolocaleName") for r in ns.geolocales("adm1")}
    countries = [r for r in ns.geolocales("country") if r.get("geolocaleName") not in adm1_names]   # the API finds a place by name
    sample = [ns.madagascar()] + [ns.geolocale_place(r) for r in ns.pick(countries, 1, "unimaged")]
    wrong, checked = [], []
    for p in sample:
        stats = stats_of(p)
        html = ns.fetch(p.list_url("species", "valid", extra="&isImaged=false"))
        full = ns.fetch(p.list_url("species", "valid"))
        if not stats.get("valid") or html is None or full is None:
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
        if total != stats["valid"]:
            wrong.append(f"{p}: 'out of {total}', place Valid species {stats['valid']}")
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
    for cell in re.findall(r'<div class="list_extras specimens[^"]*">(.*?)</div>', row_html, re.S):
        if "global=true" in cell:
            continue
        link = re.search(r"href='([^']*browse\.do\?[^']*)'", cell)
        num = re.search(r"<span class='numbers'>(\d+)</span>", cell)
        if num:
            return int(num.group(1)), link and link.group(1)
        if "No " in cell:
            return 0, None
    return None, None


def _browse(link, status="curated"):
    """The browse page of a list row's link with the view that its row count counts: a genus row's Species are those of
    the list's view (2026-11-02; the link carries it, so a statusSet already in the link is kept), a subfamily row's
    Genera every counted status (all); a species page lists its specimens whatever the view."""
    path = re.sub(r"^https?://[^/]+", "", unescape(link))
    if "statusSet=" in path:
        return path
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


def _pick_row(p, rank, salt, max_count=None, view="curated"):
    """The day's row (with a count above 0 and a browse link) of the place's list of that rank in a view (default
    Curated): (row, count, link)."""
    html = ns.fetch(p.list_url(rank, view))
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
    list (Curated): browse.do of the genus says "N Species" = the row's "N Species" and lists N rows; the day's subfamily
    row in each of the views Valid, Curated and All specimen names: browse.do of the subfamily in that view says
    "N Genera" = the row's "N Genera" and lists N rows (B77, fixed 2026-11-03: a subfamily row's Genera are the genus
    rows its browse page shows in the same view, ListView.SubfamilyRows; until then the row showed the stored count of
    the nightly run); on both, the hidden totals say the same N and M, and M imaged <= N."""
    wrong, stored, checked = [], [], []
    for p in places():
        for rank, views in (("genus", ("curated",)), ("subfamily", ("valid", "curated", "all"))):
            for view in views:
                got = _pick_row(p, rank, "browse" + rank + view, view=view)
                if not got:
                    continue
                row, count, link = got
                # the row's link carries its view (2026-11-02 genus rows, 2026-11-03 subfamily rows)
                if rank == "subfamily" and f"statusSet={view}" not in unescape(link):
                    wrong.append(f"{p} subfamily {row['name'] or link} ({view}): the row's link {link!r} does not keep the view")
                html = ns.fetch(_browse(link, view))
                if html is None:
                    continue
                where = f"{p} {rank} {row['name'] or link} ({view})"
                b = _browse_numbers(html)
                browse_rows = ns.list_rows(html)
                _consistent(where, b, len(browse_rows), wrong)
                n = b["n"]
                if n is not None and n != count:
                    line = f"{where}: browse header {b['text']!r}, list row {count}"
                    # a genus row's Species: pending until a nightly run has counted by the counting rules of 2026-10-28;
                    # a subfamily row's Genera are counted live from the same rows as the page (strict)
                    (stored if rank == "genus" else wrong).append(line)
                checked.append(where)
    if not checked:
        pytest.skip("no list row with a browse link found " + ns.day_note())
    pending_species = bool(stored) and ns.counting_rules_counted() is False
    if not pending_species:
        wrong += stored
    assert not wrong, f"{len(checked)} browse pages {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
    if pending_species:
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28") + ":\n" + "\n".join(stored))


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


def test_browse_limit_shows_that_many_specimens():
    """Row 47 (B42, fixed 2026-11-02): with "(Only the first k of N displayed)" the page lists k specimens."""
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
    Lioponera (1), Pheidole (2).", adds up to its N; on the genus list (view Curated), a row from specimens with no
    species in the view ("-") shows the specimens collected here that put it there in its Not curated number (2026-11-02:
    in Curated every valid extant name and curated morphospecies is a species of the view, so what keeps such a row is an
    uncurated morphospecies or an unrecognized name, which Not curated counts; until then the Other specimens of Valid,
    and before 2026-11-02 "No species on the list · N specimens collected here"). Not checked: fossil genus rows (a
    valid fossil species is curated but not in Curated) and places without the column (regions, subregions, projects)."""
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
        genus_html = ns.fetch(p.list_url("genus", "curated"))
        has_column = bool(re.search(r'class="list_extras other[^"]*"', genus_html or ""))
        counted = 'class="not-curated"' in (genus_html or "")   # the nightly count has run for this place
        for row in ns.list_rows(genus_html or ""):
            # 2026-11-02: a genus row from specimens with no species in the view shows its Not curated number
            # the Species cell's dash (taxonReportBody.jsp); the Images cell's dash is class='none-imaged' (until
            # 2026-11-06 it was 'no-species' too, so a row with species but none imaged, Palearctic Acropyga, was flagged)
            no_species = "class='no-species'" in row["html"]
            fossil = "&dagger;" in row["html"] or "\u2020" in row["html"]
            if has_column and counted and no_species and not fossil and 'class="not-curated"' not in row["html"] \
                    and ns.on_list(row["html"]) and 'data-source="specimen"' in row["html"]:
                wrong.append(f"{p}: a genus row with no species in the view has no Not curated number: {ns.text_of(row['html'])[:120]}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no species list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)

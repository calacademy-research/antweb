"""The counts of API v3.1 recounted from other replies and pages (docs/numbers.md rows 101, 102, 105, 106, 107, 110,
111, 112, 113, 116, 117, 118), 2026-10-27.

test_api_numbers.py checks that each meta count equals the rows of its own reply on fixed requests (Madagascar,
Pheidole). Here each count is recounted independently, on a sample that changes with the day (numbers_sample.py):

  row 101  /specimens count = the records of the filter (every row matches it; two pages of k = one page of 2k);
           /geoSpecimens count = the records inside the box of the radius, and distinct=genus = the genera of them
  row 102  /geoSpeciesStats: the ten caste and subcaste counts of a name = the specimens /specimens returns for the
           name and the place column (status valid), counted by the same rule (country, adm1, subregion of the day);
           a name shared by two geolocales counts each specimen once (B55)
  row 105  /taxa taxonCount = the taxa of the filter (the species of the day's genus = the species rows of all its
           taxa; pages of k add up to a page of 2k)
  row 106  /images count and specimenCount = the images and specimens of a multi-specimen reply, no specimen twice;
           the images of a specimen = /images of that specimen alone = /specimens imageCount
  row 107  /taxaImages taxonCount, specimenCount, imageCount of a name = its specimens with images (/specimens) and
           their images (/images)
  row 110  /geolocales endemicSpeciesCount, introducedSpeciesCount = Endemic and Introduced of the place page
  row 111  /geolocales geolocaleCount = the adm1s of a country, recounted from the list of all adm1s
  rows 112, 113  /geolocaleTaxa per row subfamily/genus/species, specimen and image counts = the place's list rows
           (taxonomicPage.do, validated by the count check); geolocaleCount = the rows of each page; the rank filter
  rows 116, 118  /bioregionTaxa the same for the day's bioregion; bioregionCount = the rows of each page
  row 117  /unimagedGeolocaleTaxa: names on the place's list; a name returned has no image of the ant (only labels or
           none, /taxaImages), a name of the list not returned has one; pages never repeat a name (B56)
  row 118  byCaste=1: unimagedGeolocaleTaxaCount = names, unimagedGeolocaleTaxaCasteCount = (name, caste) rows; a
           name without any image of the ant is unimaged for worker, queen and male; byCaste=true = byCaste=1 (B57)

The place numbers and list rows are those of the last nightly run; the API reads the same tables live, so a
difference right after an upload can be pending (the failure says so). Read-only, anonymous.
"""
import math
import re
from collections import Counter, defaultdict
from html import unescape
from urllib.parse import quote_plus, unquote_plus

import pytest

import numbers_sample as ns

PENDING = "list rows and place numbers are those of the last nightly run; after an upload they are pending"
PAGE = 500


def _meta(body):
    return (body or {}).get("metaData") or (body or {}).get("_metaData") or {}


def _api(path):
    body = ns.api(path)
    if body is None:
        pytest.skip(f"/v3.1/{path}: no JSON reply {ns.day_note()}")
    return body


def _pages(path, key, max_pages=8, size=PAGE):
    """Every row of a paged API list (limit=size, offset), checking on each page that metaData[key] = its rows.
    Returns (rows, wrong); rows is None when it needs more than max_pages pages or a request fails."""
    rows, wrong = [], []
    for i in range(max_pages):
        body = ns.api(f"{path}&limit={size}&offset={i * size}")
        if body is None:
            return None, wrong
        page = ns.api_rows(body)
        meta = _meta(body)
        if key in meta and meta[key] != len(page):
            wrong.append(f"/v3.1/{path} offset {i * size}: metaData {key} {meta[key]}, {len(page)} rows")
        rows += page
        if len(page) < size:
            return rows, wrong
    return None, wrong


def _names_by_geolocale():
    by = defaultdict(list)
    for rank in ("country", "adm1", "region", "subregion"):
        for r in ns.geolocales(rank):
            by[r["geolocaleName"]].append(r)
    return by


def _unique(rank, salt, n=1, with_specimens=True):
    """The day's geolocales of a rank whose name no other valid geolocale has (the API finds a place by name)."""
    by = _names_by_geolocale()
    rows = [r for r in ns.geolocales(rank) if len(by[r["geolocaleName"]]) == 1
            and (not with_specimens or (r.get("specimenCount") or 0) > 0)]
    return ns.pick(rows, n, salt)


def _int(v):
    try:
        return int(v or 0)
    except (TypeError, ValueError):
        return 0


# ----------------------------------------------------------------------------------- row 101: /specimens, /geoSpecimens

def _radius_box(lat, lon, km):
    d_lat = km / 111.32
    c = math.cos(math.radians(lat))
    d_lon = 180.0 if c < 1e-6 else min(180.0, km / (111.32 * c))
    return lat - d_lat, lat + d_lat, lon - d_lon, lon + d_lon


def test_specimen_counts_are_the_records_of_the_filter():
    """Row 101: /specimens?country=X: count = the rows, every row is of X, the first 20 and the next 20 = the first
    40; /geoSpecimens around one of them: count = the rows, every row inside the box, the specimen itself among them,
    and distinct=genus count = its genera = the genera of the rows."""
    sample = _unique("country", "api-specimens") or [r for r in ns.geolocales("country") if r["geolocaleName"] == "Madagascar"]
    if not sample:
        pytest.skip("no country from /geolocales " + ns.day_note())
    name = sample[0]["geolocaleName"]
    q = quote_plus(name)
    wrong = []
    body = _api(f"specimens?country={q}&limit=40")
    rows = ns.api_rows(body)
    if not rows:
        pytest.skip(f"no specimen of {name} " + ns.day_note())
    if _meta(body).get("count") != len(rows) or len(rows) > 40:
        wrong.append(f"/specimens {name}: count {_meta(body).get('count')}, {len(rows)} rows (limit 40)")
    wrong += [f"/specimens?country={name}: {r.get('specimenCode')} is of {r.get('country')!r}"
              for r in rows if name.lower() not in (r.get("country") or "").lower()]
    a = ns.api_rows(_api(f"specimens?country={q}&limit=20"))
    b = ns.api_rows(_api(f"specimens?country={q}&limit=20&offset=20"))
    codes = [r.get("specimenCode") for r in rows]
    if [r.get("specimenCode") for r in a + b] != codes:
        wrong.append(f"/specimens {name}: limit 20 + offset 20 is not limit 40 ({len(a)} + {len(b)}, {len(rows)})")

    located = [r for r in rows if str(r.get("decimalLatitude") or "").strip() and str(r.get("decimalLongitude") or "").strip()]
    if located:
        r = ns.pick(located, 1, "api-geo")[0]
        lat, lon = float(r["decimalLatitude"]), float(r["decimalLongitude"])
        body = _api(f"geoSpecimens?coords={lat},{lon}&radius=2&limit={PAGE}")
        geo = ns.api_rows(body)
        if _meta(body).get("count") != len(geo):
            wrong.append(f"/geoSpecimens {lat},{lon}: count {_meta(body).get('count')}, {len(geo)} rows")
        lo_lat, hi_lat, lo_lon, hi_lon = _radius_box(lat, lon, 2)
        eps = 1e-6
        outside = [g.get("specimenCode") for g in geo
                   if not (lo_lat - eps <= float(g["decimalLatitude"]) <= hi_lat + eps
                           and lo_lon - eps <= float(g["decimalLongitude"]) <= hi_lon + eps)]
        if outside:
            wrong.append(f"/geoSpecimens {lat},{lon} radius 2 km: outside the box {outside[:5]}")
        if len(geo) < PAGE:
            if r.get("specimenCode") not in {g.get("specimenCode") for g in geo}:
                wrong.append(f"/geoSpecimens {lat},{lon}: the specimen {r.get('specimenCode')} at that point is missing")
            body = _api(f"geoSpecimens?coords={lat},{lon}&radius=2&distinct=genus&limit={PAGE}")
            genera = ns.api_rows(body)
            if _meta(body).get("count") != len(genera):
                wrong.append(f"/geoSpecimens distinct=genus: count {_meta(body).get('count')}, {len(genera)} genera")
            expected = {(g.get("genus") or "").lower() for g in geo}
            got = {(g or "").lower() for g in genera}
            if got != expected:
                wrong.append(f"/geoSpecimens {lat},{lon} distinct=genus: {sorted(got)[:8]}, the rows' genera {sorted(expected)[:8]}")
    assert not wrong, f"{name} {ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------- row 102: /geoSpeciesStats

STAT_RULES = (
    ("queenCount", lambda c, s: c == "queen"),
    ("queenErgatoidCount", lambda c, s: c == "queen" and s == "ergatoid"),
    ("queenAlateDealateCount", lambda c, s: s == "alate/dealate"),
    ("queenBrachypterousCount", lambda c, s: s == "brachypterous"),
    ("workerCount", lambda c, s: c == "worker"),
    ("workerMajorSoldierCount", lambda c, s: s == "major/soldier"),
    ("workerNormalCount", lambda c, s: s == "normal"),
    ("maleCount", lambda c, s: c == "male"),
    ("maleErgatoidCount", lambda c, s: c == "male" and s == "ergatoid"),
    ("maleAlateCount", lambda c, s: s == "alate"),
)


def _same_place(a, b):
    """Place names equal as MySQL compares them: case, accents and trailing spaces ignored."""
    import unicodedata

    def fold(x):
        x = unicodedata.normalize("NFKD", (x or "").rstrip())
        return "".join(ch for ch in x if not unicodedata.combining(ch)).casefold()
    return fold(a) == fold(b)


def _recount_castes(stat, column, name):
    """The ten counts of a /geoSpeciesStats row recounted from /specimens (status valid, the place column = name),
    or None when there are too many records."""
    genus, species, taxon = stat.get("genus") or "", stat.get("species") or "", (stat.get("taxonName") or "").lower()
    param = {"country": "country", "adm1": "adm1", "subregion": "subregion", "region": "region"}[column]
    rows, _ = _pages(f"specimens?genus={quote_plus(genus)}&species={quote_plus(species)}&status=valid"
                     f"&{param}={quote_plus(name)}", "count", max_pages=4)
    if rows is None:
        return None
    # the API matches the place with MySQL's equality (utf8mb4 general/0900 collation: case, accents and trailing spaces
    # ignored), so the recount compares names the same way (staging 2026-10-28: a gredleri worker of "Dolnośląskie"
    # whose adm1 is spelled differently was counted by the API and dropped here, 8 vs 7)
    rows = [r for r in rows if _same_place(r.get(column), name) and (r.get("antwebTaxonName") or "").lower() == taxon]
    out = {}
    for key, rule in STAT_RULES:
        out[key] = sum(1 for r in rows if rule((r.get("caste") or "").lower(), (r.get("subcaste") or "").lower()))
    return out


def _stats_sample(name, salt, n=2):
    body = ns.api(f"geoSpeciesStats?geolocaleName={quote_plus(name)}&limit={PAGE}")
    rows = ns.api_rows(body)
    if _meta(body).get("count") not in (None, len(rows)):
        return rows, [], [f"{name}: count {_meta(body).get('count')}, {len(rows)} rows"]
    named = [r for r in rows if r.get("genus") and r.get("species") and not str(r["genus"]).startswith("(")]
    return rows, ns.pick(named, n, salt + name), []


def test_geo_species_stats_castes_equal_the_specimens():
    """Row 102: for the day's country, adm1 and subregion (names no other geolocale has), two names of
    /geoSpeciesStats: each of the ten counts = the specimens of the name in the place (status valid) that /specimens
    returns, counted by the same rule."""
    wrong, checked = [], []
    for rank in ("country", "adm1", "subregion"):
        for g in _unique(rank, "api-castes"):
            name = g["geolocaleName"]
            _, sample, w = _stats_sample(name, "castes")
            wrong += w
            for stat in sample:
                expected = _recount_castes(stat, rank, name)
                if expected is None:
                    continue
                diff = {k: (stat.get(k), v) for k, v in expected.items() if _int(stat.get(k)) != v}
                if diff:
                    wrong.append(f"{rank} {name} {stat.get('taxonName')}: (API, recount) {diff}")
                checked.append(f"{name} {stat.get('taxonName')}")
    if not checked:
        pytest.skip("no /geoSpeciesStats row to recount " + ns.day_note())
    assert not wrong, f"{len(checked)} names {ns.day_note()}:\n" + "\n".join(wrong)


def test_geo_species_stats_of_a_shared_name_count_each_specimen_once():
    """Row 102, B55 (fixed 2026-10-27): for a name that several valid geolocales have (a country and an adm1, or two
    adm1s), the counts of a name equal the recount of one place column; they joined every geolocale of the name, so a
    specimen counted once per geolocale whose list has the name."""
    shared = sorted(n for n, rows in _names_by_geolocale().items() if len(rows) > 1)
    if not shared:
        pytest.skip("no geolocale name shared by two valid geolocales")
    wrong, checked = [], []
    for name in ns.pick(shared, 2, "api-shared"):
        _, sample, w = _stats_sample(name, "shared")
        wrong += w
        for stat in sample:
            recounts = {}
            for column in ("country", "adm1", "subregion", "region"):
                rc = _recount_castes(stat, column, name)
                if rc is not None:
                    recounts[column] = rc
            if not recounts:
                continue
            got = {k: _int(stat.get(k)) for k, _ in STAT_RULES}
            if got not in recounts.values():
                wrong.append(f"{name} {stat.get('taxonName')}: API {got}; recounts by column {recounts}")
            checked.append(f"{name} {stat.get('taxonName')}")
    if not checked:
        pytest.skip("no /geoSpeciesStats row of a shared name " + ns.day_note())
    assert not wrong, f"{len(checked)} names {ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------------- row 105: /taxa

def test_taxa_count_is_the_taxa_of_the_filter():
    """Row 105: the day's valid genus: /taxa?genus=G&taxarank=species: taxonCount = the rows of every page = the
    species rows of /taxa?genus=G (every rank); the first k and the next k = the first 2k."""
    genera, _ = _pages("taxa?taxarank=genus&status=valid", "taxonCount", max_pages=4)
    genera = [g for g in (genera or []) if g.get("genus")]
    if not genera:
        pytest.skip("/taxa lists no valid genus")
    genus = str(ns.pick(genera, 1, "api-taxa")[0]["genus"]).lower()
    species, wrong = _pages(f"taxa?genus={quote_plus(genus)}&taxarank=species", "taxonCount")
    every, w2 = _pages(f"taxa?genus={quote_plus(genus)}", "taxonCount")
    wrong += w2
    if species is None or every is None:
        pytest.skip(f"genus {genus}: more than 8 pages of taxa")
    names = [t.get("taxonName") for t in species]
    expected = [t.get("taxonName") for t in every if t.get("taxarank") == "species"]
    if sorted(names) != sorted(expected):
        wrong.append(f"{genus}: taxarank=species {len(names)} taxa, species rows of the genus {len(expected)}")
    wrong += [f"{genus}: {t.get('taxonName')} is {t.get('genus')} {t.get('taxarank')}" for t in species
              if (t.get("genus") or "").lower() != genus or t.get("taxarank") != "species"]
    k = max(1, min(10, len(every) // 2))
    first = ns.api_rows(_api(f"taxa?genus={quote_plus(genus)}&limit={k}"))
    second = ns.api_rows(_api(f"taxa?genus={quote_plus(genus)}&limit={k}&offset={k}"))
    both = ns.api_rows(_api(f"taxa?genus={quote_plus(genus)}&limit={2 * k}"))
    if [t.get("taxonName") for t in first + second] != [t.get("taxonName") for t in both]:
        wrong.append(f"{genus}: limit {k} + offset {k} is not limit {2 * k}")
    assert not wrong, f"genus {genus} {ns.day_note()}:\n" + "\n".join(wrong)


# --------------------------------------------------------------------------------- rows 106, 107: /images, /taxaImages

def _images_of(code):
    body = _api(f"images?specimenCode={quote_plus(code)}")
    return sum(len(r.get("images") or []) for r in ns.api_rows(body)), body


def _recent_images():
    for path in ("images?since=90&limit=300", "images?since=730&limit=300", "images?limit=300"):
        body = ns.api(path)
        if body and ns.api_rows(body):
            return path, body
    pytest.skip("/images returned nothing")


def test_images_counts_of_many_specimens():
    """Row 106: a reply of 300 images of many specimens: count = its images, specimenCount = its specimens, no
    specimen twice; for three specimens fully in the reply, their images = /images of the specimen alone (count,
    specimenCount 1) = /specimens imageCount."""
    path, body = _recent_images()
    rows, meta, wrong = ns.api_rows(body), _meta(body), []
    images = sum(len(r.get("images") or []) for r in rows)
    codes = [r.get("specimenCode") for r in rows]
    if meta.get("count") != images:
        wrong.append(f"/{path}: count {meta.get('count')}, {images} images")
    if meta.get("specimenCount") != len(rows):
        wrong.append(f"/{path}: specimenCount {meta.get('specimenCount')}, {len(rows)} specimens")
    twice = [c for c, k in Counter(codes).items() if k > 1]
    if twice:
        wrong.append(f"/{path}: specimens listed more than once {twice[:5]}")
    whole = rows[:-1] if images >= 300 else rows   # the last specimen may go on in the next page
    for r in ns.pick(whole, 3, "api-images"):
        code, shown = r.get("specimenCode"), len(r.get("images") or [])
        alone, b = _images_of(code)
        if alone != shown or _meta(b).get("count") != alone or _meta(b).get("specimenCount") != 1:
            wrong.append(f"{code}: {shown} images in /{path}, /images of it {alone} (meta {_meta(b).get('count')},"
                         f" {_meta(b).get('specimenCount')} specimens)")
        spec = ns.api_rows(_api(f"specimens?specimenCode={quote_plus(code)}"))
        if spec and _int(spec[0].get("imageCount")) != alone:
            wrong.append(f"{code}: /specimens imageCount {spec[0].get('imageCount')}, /images {alone}")
    assert not wrong, ns.day_note() + "\n" + "\n".join(wrong)


def test_taxa_images_counts_equal_the_specimens_and_images_of_the_name():
    """Row 107: the name of a specimen with images (from a recent /images reply): /taxaImages?taxonName=X:
    taxonCount 1, specimenCount = its specimens = the specimens of the name with images (/specimens), imageCount =
    its images; three specimens' images = /images of the specimen."""
    path, body = _recent_images()
    wrong, checked = [], []
    for r in ns.pick(ns.api_rows(body), 2, "api-taxa-images"):
        spec = ns.api_rows(_api(f"specimens?specimenCode={quote_plus(r.get('specimenCode') or '')}"))
        if not spec or not spec[0].get("antwebTaxonName") or not spec[0].get("species"):
            continue
        s = spec[0]
        taxon = s["antwebTaxonName"]
        tb = _api(f"taxaImages?taxonName={quote_plus(taxon)}&limit={PAGE}")
        trows, meta = ns.api_rows(tb), _meta(tb)
        specimens = [sp for t in trows for sp in (t.get("specimen") or [])]
        images = sum(len(sp.get("images") or []) for sp in specimens)
        if meta.get("taxonCount") != len(trows) or len(trows) != 1:
            wrong.append(f"{taxon}: taxonCount {meta.get('taxonCount')}, {len(trows)} taxa (one asked for)")
        if meta.get("specimenCount") != len(specimens) or len({sp.get('code') for sp in specimens}) != len(specimens):
            wrong.append(f"{taxon}: specimenCount {meta.get('specimenCount')}, {len(specimens)} specimens"
                         f" ({len({sp.get('code') for sp in specimens})} codes)")
        if meta.get("imageCount") != images:
            wrong.append(f"{taxon}: imageCount {meta.get('imageCount')}, {images} images")
        if images < PAGE:
            recs, _ = _pages(f"specimens?genus={quote_plus(s.get('genus') or '')}&species={quote_plus(s['species'])}"
                             "&hasImage=true", "count", max_pages=4)
            if recs is not None:
                expected = {x.get("specimenCode") for x in recs if (x.get("antwebTaxonName") or "") == taxon}
                got = {sp.get("code") for sp in specimens}
                if got != expected:
                    wrong.append(f"{taxon}: /taxaImages {len(got)} specimens, /specimens with images {len(expected)};"
                                 f" only in taxaImages {sorted(got - expected)[:3]}, only in specimens {sorted(expected - got)[:3]}")
        for sp in ns.pick(specimens if images < PAGE else [], 3, "api-ti" + taxon):
            alone, _ = _images_of(sp.get("code"))
            if alone != len(sp.get("images") or []):
                wrong.append(f"{taxon} {sp.get('code')}: /taxaImages {len(sp.get('images') or [])} images, /images {alone}")
        checked.append(taxon)
    if not checked:
        pytest.skip(f"no named specimen in /{path}")
    assert not wrong, f"{checked} {ns.day_note()}:\n" + "\n".join(wrong)


# ----------------------------------------------------------------------------------------- rows 110, 111: /geolocales

def test_geolocale_endemic_and_introduced_equal_the_place_page():
    """Row 110: the day's country, adm1, region and subregion, and Madagascar: /geolocales endemicSpeciesCount and
    introducedSpeciesCount = Endemic and Introduced of the place page (validated on every place by the count check
    and endemic.do)."""
    sample = [ns.madagascar()] + [ns.geolocale_place(r) for rank in ("country", "adm1", "region", "subregion")
                                  for r in ns.pick(ns.geolocales(rank), 1, "api-endemic" + rank)]
    wrong, checked = [], []
    for p in sample:
        if p is None or not p.api_row:
            continue
        stats = ns.place_stats(ns.fetch(p.page) or "")
        for stat, key in (("endemic", "endemicSpeciesCount"), ("introduced", "introducedSpeciesCount")):
            if stat in stats:
                if _int(p.api_row.get(key)) != stats[stat]:
                    wrong.append(f"{p}: /geolocales {key} {p.api_row.get(key)}, place page {stat} {stats[stat]}")
                checked.append(f"{p} {stat}")
    if not checked:
        pytest.skip("no place page with Endemic or Introduced " + ns.day_note())
    assert not wrong, f"{len(checked)} numbers {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


def test_geolocales_count_is_the_geolocales_of_the_filter():
    """Row 111: the day's country with adm1s: /geolocales?georank=adm1&parent=C geolocaleCount = its rows = the adm1s
    of C in the list of every adm1; /geolocales?geolocaleName=N = the geolocales of every rank with that name."""
    adm1s = ns.geolocales("adm1")
    if not adm1s:
        pytest.skip("/geolocales lists no adm1")
    parents = Counter(r.get("parent") for r in adm1s if r.get("parent"))
    wrong = []
    for parent in ns.pick(sorted(parents), 2, "api-geolocales"):
        body = _api(f"geolocales?georank=adm1&parent={quote_plus(parent)}&limit={PAGE}")
        rows = ns.api_rows(body)
        expected = {str(r["geolocaleId"]) for r in adm1s if r.get("parent") == parent}
        got = {str(r.get("geolocaleId")) for r in rows}
        if _meta(body).get("geolocaleCount") != len(rows):
            wrong.append(f"{parent}: geolocaleCount {_meta(body).get('geolocaleCount')}, {len(rows)} rows")
        if got != expected or len(rows) != len(expected):
            wrong.append(f"{parent}: {len(rows)} adm1s, {len(expected)} in the list of every adm1")
    by = _names_by_geolocale()
    for name in ns.pick(sorted(by), 2, "api-geolocale-name"):
        body = _api(f"geolocales?geolocaleName={quote_plus(name)}&limit={PAGE}")
        rows = [r for r in ns.api_rows(body) if r.get("georank") in ("country", "adm1", "region", "subregion")]
        if _meta(body).get("geolocaleCount") != len(ns.api_rows(body)):
            wrong.append(f"{name}: geolocaleCount {_meta(body).get('geolocaleCount')}, {len(ns.api_rows(body))} rows")
        if len(rows) != len(by[name]):
            wrong.append(f"{name}: {len(rows)} geolocales of that name, {len(by[name])} in the lists by rank")
    assert not wrong, ns.day_note() + "\n" + "\n".join(wrong)


# ---------------------------------------------------------------- rows 112, 113, 116, 118: /geolocaleTaxa, /bioregionTaxa

OWN_COUNT = {"subfamily": "genusCount", "genus": "speciesCount", "species": "specimenCount"}


def _row_numbers(row_html):
    """The row's own count ("N Genera", "N Species", "N Specimens" of this place, not the Global one) and its
    Images, as taxonReportBody.jsp writes them (TaxonSet.getNextSubtaxon, getImageCountStr)."""
    own = None
    for cell in re.findall(r'<div class="list_extras specimens">(.*?)</div>', row_html, re.S):
        if "global=true" in cell:
            continue
        num = re.search(r"<span class='numbers'>(\d+)</span>", cell)
        own = int(num.group(1)) if num else (0 if "No " in cell else None)
        break
    img = re.search(r'<div class="list_extras images">(.*?)</div>', row_html, re.S)
    images = None
    if img:
        num = re.search(r"<span class='numbers'>(\d+)</span>", img.group(1))
        images = int(num.group(1)) if num else (0 if "No Images" in img.group(1) else None)
    return own, images


def _row_name(row, rank):
    """taxon_name of a list row: species rows by numbers_sample; subfamily rows are the subfamily, genus rows
    subfamily + genus (their description.do links carry no taxonName)."""
    if rank == "species":
        return row["name"]
    m = re.search(r'description\.do\?([^"\'>]+)', row["html"])
    if not m:
        return None
    params = dict(kv.split("=", 1) for kv in unescape(m.group(1)).split("&") if "=" in kv)
    name = (params.get("subfamily") or "") + (params.get("genus") or "" if rank == "genus" else "")
    return unquote_plus(name).strip().lower() or None


def _compare_list(p, rank, api_rows, wrong):
    """The place's list of that rank (every counted status) against the API rows: each name listed has an API row
    with the same own count, and Images at least the API's (place) imageCount. Returns the number of rows compared, or None when the page is not read."""
    html = ns.fetch(p.list_url(rank))
    if html is None:
        return None
    by_name = {(r.get("taxonName") or "").lower(): r for r in api_rows}
    n = 0
    for row in ns.list_rows(html):
        name = _row_name(row, rank)
        if not name:
            continue
        own, images = _row_numbers(row["html"])
        a = by_name.get(name)
        if a is None:
            wrong.append(f"{p} {rank} {name}: on the list, not in the API")
            continue
        if own is not None and _int(a.get(OWN_COUNT[rank])) != own:
            wrong.append(f"{p} {rank} {name}: list {own}, API {OWN_COUNT[rank]} {a.get(OWN_COUNT[rank])}")
        # Since 2026-10-28 the list's Images cell shows the AntWeb-wide number (images of the name from any
        # specimen, ImagesAnywhere.imagesColumn: max of the AntWeb-wide and the place's own count), while the API's
        # imageCount is the place's own: the cell can only be larger. The AntWeb-wide number itself is checked by
        # test_numbers_images.py::test_list_images_column_equals_the_api_recount.
        if images is not None and images < _int(a.get("imageCount")):
            wrong.append(f"{p} {rank} {name}: list {images} Images, fewer than the place's API imageCount {a.get('imageCount')}")
        n += 1
    return n


def _rank_ok(rank, row):
    return row.get("taxarank") in (("species", "subspecies") if rank == "species" else (rank,))


def test_geolocale_taxa_rows_equal_the_place_lists():
    """Rows 112, 113: the day's country, adm1, region and subregion: /geolocaleTaxa?geolocaleName=X&taxarank=R for
    subfamily, genus and (lists up to 1,500 names) species: geolocaleCount = the rows of each page, every row of the
    rank asked for, and each name of the place's list (taxonomicPage.do) has its row with the list's numbers."""
    sample = [ns.geolocale_place(r) for rank in ("country", "adm1", "region", "subregion")
              for r in _unique(rank, "api-geotaxa")]
    wrong, checked = [], []
    for p in [p for p in sample if p is not None and p.gid is not None]:
        for rank in ("subfamily", "genus", "species"):
            rows, w = _pages(f"geolocaleTaxa?geolocaleName={quote_plus(p.name)}&taxarank={rank}", "geolocaleCount",
                             max_pages=3)
            wrong += w
            if rows is None:
                continue
            wrong += [f"{p} taxarank={rank}: {r.get('taxonName')} is a {r.get('taxarank')}" for r in rows if not _rank_ok(rank, r)]
            rows = [r for r in rows if str(r.get("geolocaleId")) == str(p.gid)]
            n = _compare_list(p, rank, rows, wrong)
            if n:
                checked.append(f"{p} {rank} {n}")
    if not checked:
        pytest.skip("no place list read " + ns.day_note())
    assert not wrong, f"{checked} {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong[:60])


def test_bioregion_taxa_rows_equal_the_bioregion_lists():
    """Rows 116, 118: the day's bioregion: /bioregionTaxa?bioregionName=B&taxarank=R for subfamily, genus and
    (up to 1,500 names) species: bioregionCount = the rows of each page, every row of the rank asked for, and each
    name of the bioregion's list has its row with the list's numbers."""
    sample = ns.pick(ns.bioregions(), 1, "api-biotaxa")
    if not sample:
        pytest.skip("no bioregion found")
    wrong, checked = [], []
    for p in sample:
        for rank in ("subfamily", "genus", "species"):
            rows, w = _pages(f"bioregionTaxa?bioregionName={quote_plus(p.name)}&taxarank={rank}", "bioregionCount",
                             max_pages=3)
            wrong += w
            if rows is None:
                continue
            wrong += [f"{p} taxarank={rank}: {r.get('taxonName')} is a {r.get('taxarank')}" for r in rows if not _rank_ok(rank, r)]
            wrong += [f"{p}: a row of {r.get('bioregionName')}" for r in rows if r.get("bioregionName") != p.name]
            n = _compare_list(p, rank, rows, wrong)
            if n:
                checked.append(f"{p} {rank} {n}")
    if not checked:
        pytest.skip("no bioregion list read " + ns.day_note())
    assert not wrong, f"{checked} {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong[:60])


# ---------------------------------------------------------------------------- rows 117, 118: /unimagedGeolocaleTaxa

def _ant_images(taxon):
    """(images of the ant, label images) of the name's specimens (/taxaImages), or None when it is more than a page."""
    body = ns.api(f"taxaImages?taxonName={quote_plus(taxon)}&limit={PAGE}")
    if body is None:
        return None
    shots = [(i.get("shotType") or "").strip().lower() for t in ns.api_rows(body)
             for sp in (t.get("specimen") or []) for i in (sp.get("images") or [])]
    if len(shots) >= PAGE and not any(s != "l" for s in shots):
        return None
    return sum(1 for s in shots if s != "l"), sum(1 for s in shots if s == "l")


def _unimaged_place():
    sample = _unique("country", "api-unimaged") or []
    if not sample:
        pytest.skip("no country with a name of its own " + ns.day_note())
    return ns.geolocale_place(sample[0])


def test_unimaged_names_have_no_image_of_the_ant():
    """Row 117: the day's country: /unimagedGeolocaleTaxa (pages of 500, never a name twice, B56) returns names of
    the place's species list (/geolocaleTaxa); three names returned have no image of the ant (/taxaImages: none, or
    labels only); three names of the list not returned have one."""
    p = _unimaged_place()
    q = quote_plus(p.name)
    names, wrong = _pages(f"unimagedGeolocaleTaxa?geolocaleName={q}&taxarank=species", "unimagedGeolocaleTaxaCount")
    listed, w = _pages(f"geolocaleTaxa?geolocaleName={q}&taxarank=species", "geolocaleCount")
    wrong += w
    if names is None or listed is None:
        pytest.skip(f"{p}: more than 8 pages")
    unimaged = [(r.get("taxonName") or "") for r in names]
    twice = [n for n, k in Counter(unimaged).items() if k > 1]
    if twice:
        wrong.append(f"{p}: names on more than one page or twice {twice[:5]}")
    on_list = {(r.get("taxonName") or "") for r in listed if r.get("taxarank") == "species"}
    on_list_all = {(r.get("taxonName") or "") for r in listed}
    # taxarank=species on /unimagedGeolocaleTaxa means rank species only (t.taxarank = :taxarank)
    extra = set(unimaged) - on_list_all
    if extra:
        wrong.append(f"{p}: unimaged names not on its list {sorted(extra)[:5]}")
    for name in ns.pick(sorted(set(unimaged)), 3, "api-unimaged-yes"):
        got = _ant_images(name)
        if got is not None and got[0] > 0:
            wrong.append(f"{p} {name}: returned as unimaged, but /taxaImages has {got[0]} images of the ant")
    for name in ns.pick(sorted(on_list - set(unimaged)), 3, "api-unimaged-no"):
        got = _ant_images(name)
        if got is not None and got[0] == 0:
            wrong.append(f"{p} {name}: not returned as unimaged, but /taxaImages has no image of the ant ({got[1]} labels)")
    assert not wrong, f"{p} {ns.day_note()}:\n" + "\n".join(wrong)


def test_unimaged_by_caste_counters():
    """Row 118: the day's country with byCaste=1: unimagedGeolocaleTaxaCount = the names returned (each once),
    unimagedGeolocaleTaxaCasteCount = the (name, caste) pairs; byCaste=true returns the same (B57); a name with no
    image of the ant (returned without byCaste) is unimaged for worker, queen and male."""
    p = _unimaged_place()
    q = quote_plus(p.name)
    wrong = []
    replies = {}
    for flag in ("1", "true"):
        body = _api(f"unimagedGeolocaleTaxa?geolocaleName={q}&taxarank=species&byCaste={flag}&limit={PAGE}")
        rows, meta = ns.api_rows(body), _meta(body)
        castes = {(r.get("taxonName") or ""): [c.strip() for c in (r.get("castes") or "").split(",") if c.strip()] for r in rows}
        if meta.get("unimagedGeolocaleTaxaCount") != len(rows) or len(castes) != len(rows):
            wrong.append(f"byCaste={flag}: unimagedGeolocaleTaxaCount {meta.get('unimagedGeolocaleTaxaCount')},"
                         f" {len(rows)} rows, {len(castes)} names")
        pairs = sum(len(c) for c in castes.values())
        if meta.get("unimagedGeolocaleTaxaCasteCount") != pairs:
            wrong.append(f"byCaste={flag}: unimagedGeolocaleTaxaCasteCount {meta.get('unimagedGeolocaleTaxaCasteCount')},"
                         f" {pairs} castes listed")
        bad = {n: c for n, c in castes.items() if not set(c) <= {"worker", "queen", "male"} or len(set(c)) != len(c)}
        if bad:
            wrong.append(f"byCaste={flag}: castes {list(bad.items())[:3]}")
        replies[flag] = (castes, pairs)
    if replies["true"][0] != replies["1"][0]:
        wrong.append(f"byCaste=true {len(replies['true'][0])} names, byCaste=1 {len(replies['1'][0])} (B57)")
    castes, pairs = replies["1"]
    plain = ns.api_rows(_api(f"unimagedGeolocaleTaxa?geolocaleName={q}&taxarank=species&limit={PAGE}"))
    if pairs < PAGE and len(plain) < PAGE:
        for r in plain:
            name = r.get("taxonName") or ""
            if set(castes.get(name, [])) != {"worker", "queen", "male"}:
                wrong.append(f"{name}: no image of the ant, but byCaste=1 lists {castes.get(name)}")
    assert not wrong, f"{p} {ns.day_note()}:\n" + "\n".join(wrong[:40])

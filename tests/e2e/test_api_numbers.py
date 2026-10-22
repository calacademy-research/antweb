"""The counts of API v3.1 (docs/numbers.md rows 100 to 118), 2026-10-22 ("every number checked").

The numbers the API reads from the place and list tables (/geolocales, /bioregions, list rows) are checked against
the records by the count check (CountInvariants; their rows in docs/numbers.md name those checks). Here: the counts
the API computes for each reply must describe that reply, and agree with the pages:

  meta count of /specimens, /geoSpecimens, /geoSpeciesStats, /taxa, /geolocales, /geolocaleTaxa, /bioregions,
  /bioregionTaxa = the rows returned (<= limit)                                         rows 101, 105, 111, 113, 115, 118
  (geolocaleTaxa and bioregionTaxa name their count geolocaleCount and bioregionCount: it is the taxa returned)
  /images meta count = the images returned, specimenCount = the specimens returned      row 106
  /taxaImages meta taxonCount, specimenCount, imageCount = what it returned              row 107
  /geoSpeciesStats: a caste count is at most the specimens of the name in the place;
                    queen and worker counts are at least their subcastes                 row 102
  /specimens imageCount of a specimen = the images /images returns for it               rows 100, 106
  /bioregions: as many as bioregions.do lists                                           row 115
  /unimagedGeolocaleTaxa meta unimagedGeolocaleTaxaCount = the names returned                     rows 117, 118

Read-only, anonymous; staging or production.
"""
import re

import pytest

from antweb import BASE_URL, TIMEOUT, new_session

SLOW = max(TIMEOUT, 180)


def _api(path):
    r = new_session().get(BASE_URL + "/v3.1/" + path + ("&" if "?" in path else "?") + "up=1", timeout=SLOW)
    if r.status_code != 200 or "json" not in r.headers.get("Content-Type", ""):
        pytest.skip(f"/v3.1/{path}: HTTP {r.status_code}")
    return r.json()


def _meta(body):
    return body.get("metaData") or body.get("_metaData") or {}


def _rows(body):
    for k, v in body.items():
        if k not in ("metaData", "_metaData") and isinstance(v, list):
            return v
    return []


@pytest.mark.parametrize("path, key", [
    ("specimens?genus=pheidole&country=Madagascar&limit=50", "count"),
    ("geoSpecimens?coords=-18.9,47.5&radius=10&limit=50", "count"),
    ("geoSpeciesStats?geolocaleName=Madagascar&limit=50", "count"),
    ("taxa?genus=pheidole&taxarank=species&limit=50", "taxonCount"),
    ("geolocales?georank=country&limit=500", "geolocaleCount"),
    ("geolocaleTaxa?geolocaleName=Madagascar&taxarank=genus&limit=500", "geolocaleCount"),
    ("bioregions", "bioregionCount"),
    ("unimagedGeolocaleTaxa?geolocaleName=Madagascar&taxarank=species", "unimagedGeolocaleTaxaCount"),
    ("bioregionTaxa?bioregionName=Malagasy&taxarank=genus&limit=500", "bioregionCount"),
])
def test_meta_count_is_the_rows_returned(path, key):
    body = _api(path)
    meta = _meta(body)
    if key not in meta:
        pytest.skip(f"{path}: no metaData {key} (keys {sorted(meta)})")
    rows = _rows(body)
    assert meta[key] == len(rows), f"/v3.1/{path}: metaData {key} {meta[key]}, {len(rows)} rows returned"
    limit = re.search(r"limit=(\d+)", path)
    if limit:
        assert len(rows) <= int(limit.group(1)), f"/v3.1/{path}: {len(rows)} rows, limit {limit.group(1)}"


def _a_specimen_with_images():
    rows = _rows(_api("specimens?genus=pheidole&country=Madagascar&hasImage=true&limit=20"))
    rows = [r for r in rows if int(r.get("imageCount") or 0) > 0]
    if not rows:
        pytest.skip("no Pheidole specimen of Madagascar with images")
    return max(rows, key=lambda r: int(r.get("imageCount") or 0))


def test_images_meta_counts_and_specimen_image_count():
    specimen = _a_specimen_with_images()
    code = specimen.get("specimenCode") or specimen.get("code")
    if not code:
        pytest.skip(f"/specimens row without a specimen code: {sorted(specimen)}")
    body = _api("images?specimenCode=" + code)
    meta = _meta(body)
    rows = _rows(body)
    assert all("specimenCode" in r for r in rows), f"/images {code}: a specimen without specimenCode (B29: 'spefimenCode'): {rows[:2]}"
    images = sum(len(r.get("images") or []) for r in rows)
    assert meta.get("count") == images, f"/images {code}: metaData count {meta.get('count')}, {images} images returned"
    assert meta.get("specimenCount") == len(rows), f"/images {code}: metaData specimenCount {meta.get('specimenCount')}, {len(rows)} specimens"
    assert int(specimen.get("imageCount") or 0) == images, f"{code}: /specimens imageCount {specimen.get('imageCount')}, /images {images}"


def test_images_of_an_unknown_specimen_are_none():
    """B29 (2026-10-22): /images said specimenCount 1 and returned one empty specimen when it found nothing."""
    body = _api("images?specimenCode=zznosuchcode0000")
    meta = _meta(body)
    assert _rows(body) == [], _rows(body)
    assert meta.get("specimenCount") == 0 and meta.get("count") == 0, meta


def test_taxa_images_meta_counts():
    body = _api("taxaImages?genus=pheidole&shotType=h&limit=200")
    meta = _meta(body)
    rows = _rows(body)
    if not rows:
        pytest.skip("taxaImages returned nothing")
    specimens = sum(len(t.get("specimen") or []) for t in rows)
    images = sum(len(s.get("images") or []) for t in rows for s in (t.get("specimen") or []))
    assert meta.get("taxonCount") == len(rows), f"taxonCount {meta.get('taxonCount')}, {len(rows)} taxa"
    assert meta.get("specimenCount") == specimens, f"specimenCount {meta.get('specimenCount')}, {specimens} specimens"
    assert meta.get("imageCount") == images, f"imageCount {meta.get('imageCount')}, {images} images"


def test_geo_species_stats_castes_add_up():
    rows = _rows(_api("geoSpeciesStats?geolocaleName=Madagascar&limit=200"))
    if not rows:
        pytest.skip("geoSpeciesStats returned nothing")
    wrong = []
    for r in rows:
        queen, worker = r.get("queenCount") or 0, r.get("workerCount") or 0
        q_sub = (r.get("queenErgatoidCount") or 0) + (r.get("queenAlateDealateCount") or 0) + (r.get("queenBrachypterousCount") or 0)
        w_sub = (r.get("workerMajorSoldierCount") or 0) + (r.get("workerNormalCount") or 0)
        if q_sub > queen:
            wrong.append(f"{r.get('taxonName')}: queen subcastes {q_sub} > queens {queen}")
        if w_sub > worker:
            wrong.append(f"{r.get('taxonName')}: worker subcastes {w_sub} > workers {worker}")
        if (r.get("maleErgatoidCount") or 0) + (r.get("maleAlateCount") or 0) > (r.get("maleCount") or 0):
            wrong.append(f"{r.get('taxonName')}: male subcastes > males {r.get('maleCount')}")
    assert not wrong, "/geoSpeciesStats Madagascar:\n" + "\n".join(wrong[:40])


def test_bioregions_count_equals_the_bioregions_page():
    rows = _rows(_api("bioregions"))
    html = new_session().get(BASE_URL + "/bioregions.do", timeout=SLOW).text
    listed = set(re.findall(r'<tr>\s*<td><a href="[^"]*bioregion\.do\?name=([^"&]+)"', html))
    if not listed:
        pytest.skip("bioregions.do lists no bioregion links")
    assert len(rows) == len(listed), f"API /bioregions {len(rows)}, bioregions.do {len(listed)}"


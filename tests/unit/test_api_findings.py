"""API v3.1 (api/v3.1/api.py), public-pages findings fixed 2026-10-26 (docs/flows/public-pages-findings.md):

F6  /images: each record's url is the public /v3.1/specimens address with specimenCode (was localhost:5000 and
    the refused parameter code); no image is [] with specimenCount 0.
F7  /specimens validGenus=true / validSubfamily=true: compared in lower case, filtered in SQL before limit and offset.
F8  /taxa, /distinctTaxa, /images, /geolocaleTaxa, /bioregionTaxa, /taxaImages: every query has an ORDER BY.
F22 /geoSpecimens: radius in kilometres (radiusBox), the distinct lists keep the area, distinct=species works.
F20 /specimens prints its debug lines only with log=true (log defaulted to '*', which is true).
F23 /geoSpeciesStats: an unknown place is a JSON error with HTTP 404 (was plain text with 200).

The routes run against a small SQLite database built from the API's own table definitions (no MySQL needed).
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_findings.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_findings.py
"""
import contextlib
import io
import math
import os
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

from sqlalchemy import create_engine, event  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402

engine = create_engine("sqlite://")
api.Base.metadata.create_all(engine)
api.session = scoped_session(sessionmaker(bind=engine))
client = api.application.test_client()

STATEMENTS = []


@event.listens_for(engine, "before_cursor_execute")
def _record(conn, cursor, statement, parameters, context, executemany):
    STATEMENTS.append(statement)


def _specimen(code, genus, subfamily="myrmicinae", species="sp1", lat=0.0, lon=0.0):
    return api.Specimen(occurrenceId="o-" + code, code=code, genus=genus, subfamily=subfamily, species=species,
                        decimalLatitude=lat, decimalLongitude=lon, family="formicidae")


def setup():
    s = api.session()
    s.add_all([
        _specimen("a1", "pheidole", lat=37.77, lon=-122.47),                       # San Francisco
        _specimen("a2", "Strumigenys", species="rogeri", lat=37.80, lon=-122.40),  # 7 km away, capitalised
        _specimen("a3", "camponotus", "formicinae", lat=37.00, lon=-122.00),       # about 95 km away
        _specimen("a4", "notagenus", lat=37.77, lon=-122.47),                      # not a valid genus
        _specimen("a5", "pheidole", "notasubfamily", lat=-18.9, lon=47.5),          # Madagascar
        _specimen("a6", "aulacopone", "heteroponerinae", lat=-18.9, lon=47.5),
    ])
    s.add_all([api.Image(id="3", code="b", shotType="h", shotNumber="1"),
               api.Image(id="1", code="a", shotType="p", shotNumber="1"),
               api.Image(id="2", code="a", shotType="h", shotNumber="1")])
    s.commit()


def get(url):
    r = client.get(url)
    assert r.status_code == 200, (url, r.status_code, r.data[:300])
    return r.get_json()


# --- F6 ---

def test_images_url_is_public_with_specimen_code():
    body = get("/images?up=1")
    urls = [rec["url"] for rec in body["images"]]
    assert urls == ["https://www.antweb.org/v3.1/specimens?specimenCode=a",
                    "https://www.antweb.org/v3.1/specimens?specimenCode=b"], urls
    assert "specimenCode" in api.acceptedParams("getSpecimen")
    assert api.specimenApiUrl("a b&c") == "https://www.antweb.org/v3.1/specimens?specimenCode=a%20b%26c"


def test_images_of_nothing_is_an_empty_list():
    body = get("/images?up=1&specimenCode=nothing")
    assert body["images"] == [], body
    assert api.imagesBySpecimen([]) == ([], 0, 0)


# --- F7 ---

def test_valid_genus_compares_in_lower_case():
    codes = [s["specimenCode"] for s in get("/specimens?up=1&validGenus=true")["specimens"]]
    assert codes == ["a1", "a2", "a3", "a5", "a6"], codes
    for name in ("iridomyrmex", "iberoformica", "indomyrma", "ishakidris", "iroponera", "pheidole"):
        assert name in api.VALID_GENERA, name
    assert all(n == n.lower() for n in api.VALID_GENERA)


def test_valid_subfamily_and_paging_before_the_filter():
    codes = [s["specimenCode"] for s in get("/specimens?up=1&validSubfamily=true")["specimens"]]
    assert codes == ["a1", "a2", "a3", "a4", "a6"], codes
    # Filtered in SQL before limit/offset: a full page, and the second page follows on.
    page1 = [s["specimenCode"] for s in get("/specimens?up=1&validGenus=true&limit=2")["specimens"]]
    page2 = [s["specimenCode"] for s in get("/specimens?up=1&validGenus=true&limit=2&offset=2")["specimens"]]
    assert (page1, page2) == (["a1", "a2"], ["a3", "a5"]), (page1, page2)


# --- F8 ---

def _sql_of(url):
    del STATEMENTS[:]
    get(url)
    selects = [s for s in STATEMENTS if s.lstrip().upper().startswith("SELECT")]
    assert selects, url
    return selects[-1]


def test_paged_routes_have_a_fixed_order():
    for url in ("/taxa?up=1", "/distinctTaxa?up=1&taxarank=genus", "/distinctTaxa?up=1", "/images?up=1",
                "/geolocaleTaxa?up=1", "/bioregionTaxa?up=1", "/taxaImages?up=1"):
        assert "ORDER BY" in _sql_of(url), url


def test_images_ordered_by_specimen_then_shot():
    sql = _sql_of("/images?up=1")
    order = sql[sql.index("ORDER BY"):]
    assert order.index("image_of_id") < order.index("shot_type") < order.index("shot_number"), order
    shots = [[i["shotType"] for i in rec["images"]] for rec in get("/images?up=1")["images"]]
    assert shots == [["h", "p"], ["h"]], shots


# --- F22 ---

def test_radius_box_is_in_kilometres():
    minLat, maxLat, minLon, maxLon = api.radiusBox(0.0, 10.0, 111.32)
    assert math.isclose(maxLat - minLat, 2.0) and math.isclose(maxLon - minLon, 2.0)
    minLat, maxLat, minLon, maxLon = api.radiusBox(60.0, 0.0, 10)
    assert math.isclose(maxLat - 60.0, 10 / 111.32) and math.isclose(60.0 - minLat, 10 / 111.32)
    assert math.isclose(maxLon, 10 / (111.32 * 0.5), rel_tol=1e-9), maxLon
    assert api.radiusBox(90.0, 0.0, 5)[2:] == (-180.0, 180.0)


def test_geo_specimens_stay_within_the_radius():
    codes = [s["specimenCode"] for s in get("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10")["specimens"]]
    assert codes == ["a1", "a2", "a4"], codes
    codes = [s["specimenCode"] for s in get("/geoSpecimens?up=1&coords=37.77,-122.47&radius=150")["specimens"]]
    assert codes == ["a1", "a2", "a3", "a4"], codes


def test_geo_specimens_distinct_keeps_the_area():
    body = get("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10&distinct=subfamily")
    assert body["subfamilies"] == ["myrmicinae"], body
    body = get("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10&distinct=genus")
    assert sorted(g.lower() for g in body["genera"]) == ["notagenus", "pheidole", "strumigenys"], body
    body = get("/geoSpecimens?up=1&coords=-18.9,47.5&radius=1&distinct=species")
    assert body["species"] == ["Aulacopone sp1", "Pheidole sp1"], body
    assert client.get("/geoSpecimens?up=1&coords=0,0&distinct=tribe").status_code == 400


# --- F20 ---

def _printed(url):
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        get(url)
    return out.getvalue()


def test_specimens_debug_lines_only_with_log_true():
    quiet = _printed("/specimens?up=1&genus=pheidole")
    assert "hasImage:" not in quiet and "Query complete" not in quiet, quiet
    loud = _printed("/specimens?up=1&genus=pheidole&log=true")
    assert "hasImage:" in loud and "Query complete" in loud, loud


# --- F23 ---

def test_geo_species_stats_unknown_place_is_json_404():
    r = client.get("/geoSpeciesStats?up=1&geolocaleName=Atlantis")
    assert r.status_code == 404, r.status_code
    assert r.get_json() == {"error": "Geolocale not found: Atlantis"}, r.data[:200]


if __name__ == "__main__":
    setup()
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok:", name)
            except AssertionError as e:
                failed += 1
                print("FAIL:", name, e)
    print("API findings", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)

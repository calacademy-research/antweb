"""API v3.1 (api/v3.1/api.py), decisions by Brian Fisher 2026-10-27:

8   /geoSpeciesStats counts every subcaste only within its caste (as the ergatoids since B30): a queen
    marked alate does not count as an alate male, a male marked normal does not count as a normal worker.
9   minDate, maxDate, minElevation and maxElevation include their bounds, on every route that has them
    (/specimens, /distinctTaxa). A maxDate given as a day includes the whole day.
20  Without up=1 every data route answers HTTP 503 with a JSON message (was an empty page with 200);
    / (health check) stays 200.
21  With API_REQUIRE_READONLY_LOGIN=true (production and staging, docker-compose.yml) the API does not
    start without its read-only login file; without it (development) a warning and the main account.

The routes run against a small SQLite database (no MySQL needed). Run inside the API image (CI,
.github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_decisions.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_decisions.py
"""
import datetime
import os
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

from sqlalchemy import create_engine, text  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402

engine = create_engine("sqlite://")
api.Base.metadata.create_all(engine)
api.session = scoped_session(sessionmaker(bind=engine))
client = api.application.test_client()


def _specimen(code, elevation, collected):
    return api.Specimen(occurrenceId="o-" + code, code=code, genus="pheidole", subfamily="myrmicinae",
                        species="sp-" + code, family="formicidae", minimumElevationInMeters=elevation,
                        dateCollectedStart=collected)


def setup():
    s = api.session()
    s.add_all([
        _specimen("e1199", 1199, datetime.datetime(1969, 12, 31)),
        _specimen("e1200", 1200, datetime.datetime(1970, 1, 1)),
        _specimen("e1500", 1500, datetime.datetime(1979, 12, 31, 14, 30)),   # afternoon of the last day
        _specimen("e1501", 1501, datetime.datetime(1980, 1, 1)),
    ])
    s.add(api.Geolocale(id="1", name="Comoros", georank="country", isValid="1"))
    s.add(api.Taxon(taxonName="myrmicinaepheidole sp1"))
    s.add(api.GeolocaleTaxon(geolocaleId=1, taxonName="myrmicinaepheidole sp1"))
    s.commit()
    # /geoSpeciesStats reads the specimen table itself (plain SQL), not the api3_1_specimen view.
    with engine.begin() as c:
        c.execute(text("create table specimen (taxon_name text, genus text, species text, caste text, subcaste text,"
                       " status text, country text)"))
        rows = [("queen", "alate/dealate"), ("queen", "alate"), ("queen", "brachypterous"), ("queen", "ergatoid"),
                ("worker", "major/soldier"), ("worker", "normal"), ("worker", "alate/dealate"),
                ("male", "alate"), ("male", "normal"), ("male", "brachypterous"), ("male", "major/soldier")]
        for caste, subcaste in rows:
            c.execute(text("insert into specimen values ('myrmicinaepheidole sp1', 'pheidole', 'sp1', :c, :s,"
                           " 'valid', 'Comoros')"), {"c": caste, "s": subcaste})


def codes(url):
    r = client.get(url)
    assert r.status_code == 200, (url, r.status_code, r.get_data(as_text=True)[:300])
    return sorted(s["specimenCode"] for s in r.get_json()["specimens"])


def test_elevation_bounds_are_inclusive():
    assert codes("/specimens?up=1&minElevation=1200&maxElevation=1500") == ["e1200", "e1500"]


def test_date_bounds_are_inclusive_and_max_date_takes_the_whole_day():
    assert codes("/specimens?up=1&minDate=1970-01-01&maxDate=1979-12-31") == ["e1200", "e1500"]


def test_distinct_taxa_uses_the_same_bounds():
    r = client.get("/distinctTaxa?up=1&taxarank=species&minElevation=1200&maxElevation=1500")
    assert r.status_code == 200, r.status_code
    names = r.get_data(as_text=True)
    assert "e1200" in names and "e1500" in names and "e1199" not in names and "e1501" not in names, names
    r = client.get("/distinctTaxa?up=1&taxarank=species&minDate=1970-01-01&maxDate=1979-12-31")
    names = r.get_data(as_text=True)
    assert "e1200" in names and "e1500" in names and "e1199" not in names and "e1501" not in names, names


def test_geo_species_stats_counts_subcastes_within_their_caste():
    r = client.get("/geoSpeciesStats?up=1&geolocaleName=Comoros")
    assert r.status_code == 200, (r.status_code, r.get_data(as_text=True)[:300])
    row = r.get_json()["geoSpeciesStats"][0]
    expected = {"queenCount": 4, "queenErgatoidCount": 1, "queenAlateDealateCount": 1, "queenBrachypterousCount": 1,
                "workerCount": 3, "workerMajorSoldierCount": 1, "workerNormalCount": 1,
                "maleCount": 4, "maleErgatoidCount": 0, "maleAlateCount": 1}
    got = {k: row[k] for k in expected}
    assert got == expected, got


def test_without_up_every_data_route_is_503_json():
    routes = [rule.rule for rule in api.application.url_map.iter_rules()
              if rule.endpoint not in api.UNCHECKED_ENDPOINTS and "<" not in rule.rule]
    assert "/specimens" in routes and "/geoSpeciesStats" in routes, routes
    for route in routes:
        r = client.get(route)
        assert r.status_code == 503, (route, r.status_code)
        assert r.get_json()["error"] == api.API_DOWN_MESSAGE, (route, r.get_data(as_text=True)[:200])


def test_health_route_stays_open():
    r = client.get("/")
    assert r.status_code == 200 and "Antweb API" in r.get_data(as_text=True), r.status_code


def test_readonly_login_required_on_servers():
    assert api._readonlyLoginRequired({"API_REQUIRE_READONLY_LOGIN": "true"})
    assert not api._readonlyLoginRequired({"API_REQUIRE_READONLY_LOGIN": "false"})
    assert not api._readonlyLoginRequired({})
    try:
        api._db_credentials({}, required=True)
    except SystemExit as e:
        assert e.code == 1, e.code
    else:
        raise AssertionError("the API started without its read-only login")
    assert api._db_credentials({"user": "apiro", "password": "x"}, required=True) == ("apiro", "x")
    assert api._db_credentials({}, required=False) == ("antweb", "CHANGE_ME")


def test_compose_files_set_the_requirement():
    root = os.environ.get("REPO_DIR")
    if not root:
        return  # checked in CI by the container step (tests.yaml)
    base = open(os.path.join(root, "docker-compose.yml")).read()
    dev = open(os.path.join(root, "docker-compose.dev.yml")).read()
    assert 'API_REQUIRE_READONLY_LOGIN: "true"' in base, "docker-compose.yml"
    assert 'API_REQUIRE_READONLY_LOGIN: "false"' in dev, "docker-compose.dev.yml"


if __name__ == "__main__":
    setup()
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok  ", name)
            except Exception as e:  # noqa: BLE001
                failed += 1
                print("FAIL", name, repr(e))
    sys.exit(1 if failed else 0)

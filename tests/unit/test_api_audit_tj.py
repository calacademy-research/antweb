"""API v3.1 (api/v3.1/api.py) audit fixes of 2026-10-29:

/specimens?geolocaleName= uses the list of the one geolocale of that name (not every geolocale of the name), so a
specimen on two same-named places' lists comes back once, and the specimen side matches the place exactly: an adm1
only in its own country, "Western" not matching "Western Cape". /geoSpeciesStats counts an adm1's specimens in its
own country only.

The routes run against a small SQLite database built from the API's own table definitions (no MySQL needed).
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_audit_tj.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_audit_tj.py
"""
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
    STATEMENTS.append((statement, parameters))


def _specimen(code, country, adm1, taxon="myrmicinaepheidole sp1"):
    return api.Specimen(occurrenceId="o-" + code, code=code, taxonName=taxon, genus="pheidole",
                        subfamily="myrmicinae", species="sp1", family="formicidae", country=country, adm1=adm1)


def setup():
    s = api.session()
    s.add(api.Taxon(taxonName="myrmicinaepheidole sp1", subfamily="myrmicinae", genus="pheidole", species="sp1"))
    # Two adm1s named Western (Kenya, Zambia), a Western Cape, and a country and an adm1 both named Georgia.
    s.add_all([
        api.Geolocale(id="1", name="Western", georank="adm1", parent="Kenya", isValid="1"),
        api.Geolocale(id="2", name="Western", georank="adm1", parent="Zambia", isValid="1"),
        api.Geolocale(id="3", name="Western Cape", georank="adm1", parent="South Africa", isValid="1"),
        api.Geolocale(id="4", name="Georgia", georank="country", parent="Western Asia", isValid="1"),
        api.Geolocale(id="5", name="Georgia", georank="adm1", parent="United States", isValid="1"),
    ])
    s.add_all([api.GeolocaleTaxon(geolocaleId=i, taxonName="myrmicinaepheidole sp1") for i in (1, 2, 3, 4, 5)])
    s.add_all([
        _specimen("k1", "Kenya", "Western"),
        _specimen("z1", "Zambia", "Western"),
        _specimen("c1", "South Africa", "Western Cape"),
        _specimen("g1", "Georgia", None),
        _specimen("g2", "United States", "Georgia"),
    ])
    s.commit()


def codes(url):
    r = client.get(url)
    assert r.status_code == 200, (url, r.status_code, r.data[:300])
    data = r.get_json()
    return sorted(rec["specimenCode"] for rec in data["specimens"])


def test_adm1_only_in_its_own_country():
    # getGeolocale picks the lowest id of the name: Western of Kenya.
    assert codes("/specimens?geolocaleName=Western&up=1") == ["k1"], codes("/specimens?geolocaleName=Western&up=1")


def test_country_list_used_once():
    got = codes("/specimens?geolocaleName=Georgia&up=1")
    assert got == ["g1"], got   # once, and only the country's specimens


def test_unknown_place_gives_no_specimens():
    assert codes("/specimens?geolocaleName=Atlantis&up=1") == []


def test_geo_species_stats_adm1_clause():
    STATEMENTS.clear()
    client.get("/geoSpeciesStats?geolocaleName=Western&up=1")
    sql = [(st, p) for st, p in STATEMENTS if "geolocale_taxon gt" in st]
    assert sql, "the stats query did not run"
    assert "s.country = " in sql[-1][0], sql[-1][0]


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

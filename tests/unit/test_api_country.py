"""API v3.1 (api/v3.1/api.py), public-pages finding F24 fixed 2026-10-27 (docs/flows/public-pages-findings.md):

- /distinctTaxa?taxarank=species returns distinct "Genus species" names (as /geoSpecimens?distinct=species does);
  it returned the bare epithet, so maculatus came once for every genus. Names not identified to genus (genus
  "(...)", indet or undet) are left out of the species list (Brian Fisher's rule of 2026-10-22).
- country= matches the whole country name, in any case (countryIs()), on /specimens, /distinctTaxa, /measurements,
  /geolocaleTaxa and the geolocaleName filter of /specimens; it matched by "contains", so country=Niger also
  returned Nigeria.
- country= given as an old name of a country (2026-11-10; currentCountry()): AntWeb keeps an old name as a not-valid
  country whose valid name is the current one (USA: United States; after its rename, Ivory Coast: Côte d'Ivoire). The
  answer is the current country's records and metaData.countryName says which name was asked for and which was used.
  A valid name, an unknown name and an old name without a valid name are used as given.

The routes run against a small SQLite database built from the API's own table definitions (no MySQL needed).
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_country.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 API_DOCS=web/documentation/api python3 tests/unit/test_api_country.py
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
    STATEMENTS.append(statement)


def _specimen(code, genus, species, country):
    return api.Specimen(occurrenceId="o-" + code, code=code, genus=genus, subfamily="myrmicinae", species=species,
                        country=country, family="formicidae")


def setup():
    s = api.session()
    s.add_all([
        _specimen("n1", "pheidole", "maculatus", "Niger"),
        _specimen("n2", "camponotus", "maculatus", "Niger"),
        _specimen("n3", "camponotus", "maculatus", "Niger"),          # same name again: listed once
        _specimen("g1", "tetramorium", "sericeiventre", "Nigeria"),
        _specimen("m1", "pheidole", "megacephala", "Madagascar"),
        _specimen("m2", "pheidole", None, "Madagascar"),             # no species: not in the species list
        _specimen("u1", "(formicidae)", "(indet)", "Madagascar"),    # not identified to genus: not a species
        _specimen("u2", "indet", "sp1", "Madagascar"),               # the same
        _specimen("u3", "UNDET", "sp2", "Madagascar"),               # the same, in any case
    ])
    s.add_all([api.Measurement(id=1, code="n1", trait="WL"), api.Measurement(id=2, code="g1", trait="WL"),
               api.Measurement(id=3, code="us1", trait="WL")])
    # old names of countries (no species: the species lists of the tests above stay as they are)
    s.add_all([
        _specimen("us1", "pheidole", None, "United States"),
        _specimen("us2", "pheidole", None, "United States"),
        _specimen("us3", "pheidole", None, "USA"),                   # still stored under the old name: not the country's
        _specimen("ci1", "pheidole", None, "Côte d'Ivoire"),
        _specimen("zr1", "pheidole", None, "Zaire"),
        api.Geolocale(id="1", name="United States", georank="country", isValid="1", validName="none"),
        api.Geolocale(id="2", name="USA", georank="country", isValid="0", validName="United States"),
        api.Geolocale(id="3", name="Côte d'Ivoire", georank="country", isValid="1"),
        api.Geolocale(id="4", name="Ivory Coast", georank="country", isValid="0", validName="Côte d'Ivoire"),
        api.Geolocale(id="5", name="Zaire", georank="country", isValid="0", validName="none"),      # old, no valid name
        api.Geolocale(id="6", name="Old A", georank="country", isValid="0", validName="Zaire"),     # points to a not-valid one
        api.Geolocale(id="7", name="USA", georank="adm1", isValid="0", validName="Niger"),          # not a country row
    ])
    s.commit()


def get(url):
    r = client.get(url)
    assert r.status_code == 200, (url, r.status_code, r.data[:300])
    return r.get_json()


def test_distinct_species_are_full_names():
    names = get("/distinctTaxa?up=1&taxarank=species")["taxarank"]
    assert names == ["Camponotus maculatus", "Pheidole maculatus", "Pheidole megacephala",
                     "Tetramorium sericeiventre"], names


def test_distinct_genus_and_subfamily_unchanged():
    # the genus list is unchanged: it still names the placeholders (only the species list leaves them out)
    assert get("/distinctTaxa?up=1&taxarank=genus")["genera"] == ["(formicidae)", "UNDET", "camponotus", "indet",
                                                                  "pheidole", "tetramorium"]
    assert get("/distinctTaxa?up=1&taxarank=subfamily")["subfamilies"] == ["myrmicinae"]


def test_distinct_species_leave_out_names_not_identified_to_genus():
    """Brian Fisher, 2026-10-22: names not identified to genus ("(formicidae) (indet)", indet, undet) never count
    as species (the staging run listed '(Formicidae) (indet)' and the like)."""
    names = get("/distinctTaxa?up=1&taxarank=species&country=Madagascar")["taxarank"]
    assert names == ["Pheidole megacephala"], names


def test_distinct_species_by_country_is_the_whole_name():
    names = get("/distinctTaxa?up=1&taxarank=species&country=niger")["taxarank"]
    assert names == ["Camponotus maculatus", "Pheidole maculatus"], names


def test_specimens_country_is_the_whole_name_in_any_case():
    for value in ("Niger", "niger", "NIGER"):
        codes = sorted(sp["specimenCode"] for sp in get("/specimens?up=1&country=" + value)["specimens"])
        assert codes == ["n1", "n2", "n3"], (value, codes)
    assert get("/specimens?up=1&country=Nige")["specimens"] == []
    codes = [sp["specimenCode"] for sp in get("/specimens?up=1&country=Nigeria")["specimens"]]
    assert codes == ["g1"], codes


def test_measurements_country_is_the_whole_name():
    body = get("/measurements?up=1&country=niger")
    codes = sorted(m["specimenCode"] for m in body["measurements"])
    assert codes == ["n1"], body


def test_geolocale_taxa_and_geolocale_name_compare_in_lower_case():
    del STATEMENTS[:]
    get("/geolocaleTaxa?up=1&country=NIGER")
    sql = " ".join(STATEMENTS).lower()
    assert "lower(geolocale.name) = " in sql, sql[-600:]
    api.session().add(api.Geolocale(id="99", name="Niger", georank="country"))
    api.session().commit()
    del STATEMENTS[:]
    get("/specimens?up=1&geolocaleName=Niger")
    sql = " ".join(STATEMENTS).lower()
    assert "lower(specimen.country) = " in sql or "lower(api3_1_specimen.country) = " in sql, sql[-600:]
    assert "country like" not in sql, sql[-600:]


def codes(url):
    body = get(url)
    return sorted(sp["specimenCode"] for sp in body["specimens"]), body["metaData"].get("countryName")


def test_old_country_name_gives_the_current_countrys_records_and_says_so():
    assert codes("/specimens?up=1&country=USA") == (["us1", "us2"], {"requested": "USA", "used": "United States"})
    assert codes("/specimens?up=1&country=usa") == (["us1", "us2"], {"requested": "usa", "used": "United States"})
    assert codes("/specimens?up=1&country=Ivory%20Coast") == (["ci1"], {"requested": "Ivory Coast", "used": "Côte d'Ivoire"})
    body = get("/measurements?up=1&country=USA")
    assert [m["specimenCode"] for m in body["measurements"]] == ["us1"], body
    assert body["metaData"]["countryName"] == {"requested": "USA", "used": "United States"}
    body = get("/distinctTaxa?up=1&taxarank=genus&country=USA")
    assert body["genera"] == ["pheidole"] and body["metaData"]["countryName"]["used"] == "United States", body
    del STATEMENTS[:]
    body = get("/geolocaleTaxa?up=1&country=USA")
    assert body["metaData"]["countryName"] == {"requested": "USA", "used": "United States"}, body["metaData"]
    assert "lower(geolocale.name) = " in " ".join(STATEMENTS).lower()


def test_old_country_name_other_names_are_used_as_given():
    # a valid name: nothing is said
    assert codes("/specimens?up=1&country=United%20States") == (["us1", "us2"], None)
    assert codes("/specimens?up=1&country=C%C3%B4te%20d%27Ivoire") == (["ci1"], None)
    # an old name without a valid name, one that points to a place that is not valid, an unknown name: as before
    assert codes("/specimens?up=1&country=Zaire") == (["zr1"], None)
    assert codes("/specimens?up=1&country=Old%20A") == ([], None)
    assert codes("/specimens?up=1&country=Atlantis") == ([], None)
    # no country asked for: nothing is looked up
    del STATEMENTS[:]
    get("/specimens?up=1&limit=2")
    assert "valid_name" not in " ".join(STATEMENTS).lower()
    assert api.currentCountry("*") == "*" and api.currentCountry("") == "" and api.currentCountry(None) is None


def test_no_route_matches_country_by_contains():
    with open(api.__file__) as f:
        source = f.read()
    assert "country.contains(" not in source, "a route still matches country by contains"
    # every route that reads country= resolves an old name
    assert source.count("request.args.get('country'") == source.count("currentCountry(request.args.get('country'") == 4, \
        "a route reads country= without currentCountry()"


def test_documentation_says_whole_name():
    path = os.path.join(os.environ.get("API_DOCS", "/docs"), "apiV3_1-body.jsp")
    if not os.path.exists(path):
        print("skip: no documentation page at", path)
        return
    with open(path, encoding="utf-8") as f:
        page = f.read()
    assert "containing the word land" not in page, "the documentation still describes country as contains"
    assert page.count("The whole country name, in any case") >= 2
    assert page.count("An old name of a country") >= 3 and "metaData.countryName" in page, \
        "the documentation does not say what an old country name does"
    assert '"Genus species"' in page
    assert "not identified to genus" in page, "the documentation does not say species leave out unidentified genera"


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
    print("API country and distinct species (F24)", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)

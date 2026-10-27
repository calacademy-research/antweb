"""API v3.1 (api/v3.1/api.py), checks of 2026-10-27:

- /specimens ownedby, locatedat, collectedby and /geolocaleTaxa geolocaleId filter (they answered 500).
- /specimens bbox and /images since: a value that is not numbers is a 400 with a message, not a 500.
- /specimens hasImage=false also lists specimens whose image count is not set.
- /geoSpecimens distinct=species and /images, /taxaImages: a missing genus, code or shot type does not fail the page.
- /taxaImages: each image once per specimen, with the key "urls" as in /images (the old "urls:" is kept).
- /unimagedGeolocaleTaxa: an imaged specimen without a name does not hide every unimaged name; rows read by name.
- api-db.properties: spaces around "=" are allowed; a password with @ : / still makes a working database address.
- repr() of every table class works (debug printing).

The routes run against a small SQLite database built from the API's own table definitions (no MySQL needed).
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_inputs.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_inputs.py
"""
import os
import sys
import tempfile

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

from sqlalchemy import create_engine, text  # noqa: E402
from sqlalchemy.engine import make_url  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402

engine = create_engine("sqlite://", connect_args={"check_same_thread": False},
                       poolclass=__import__("sqlalchemy.pool", fromlist=["StaticPool"]).StaticPool)
api.Base.metadata.create_all(engine)
api.session = scoped_session(sessionmaker(bind=engine))
api.engine = engine
client = api.application.test_client()


def _specimen(code, genus, **kw):
    return api.Specimen(occurrenceId="o-" + code, code=code, genus=genus, species=kw.pop("species", "sp1"),
                        subfamily="myrmicinae", family="formicidae", **kw)


def setup():
    s = api.session()
    s.add_all([
        _specimen("c1", "pheidole", ownedBy="CAS", locatedAt="MCZ", collectedBy="B.L.Fisher", imageCount=2,
                  decimalLatitude=-18.9, decimalLongitude=47.5),
        _specimen("c2", "pheidole", ownedBy="MCZ", locatedAt="CAS", collectedBy="P.S.Ward", imageCount=0),
        _specimen("c3", None, species="nogenus", imageCount=None, decimalLatitude=-18.95, decimalLongitude=47.55),
    ])
    s.add_all([api.Geolocale(id="1", name="Madagascar", georank="country", isValid="1"),
               api.Geolocale(id="2", name="Comoros", georank="country", isValid="1"),
               api.Taxon(taxonName="myrmicinaepheidole a", taxarank="species", status="valid"),
               api.Taxon(taxonName="myrmicinaepheidole b", taxarank="species", status="valid"),
               api.Taxon(taxonName="myrmicinaepheidole c", taxarank="species", status="valid")])
    s.flush()
    s.add_all([api.GeolocaleTaxon(geolocaleId=1, taxonName="myrmicinaepheidole a"),
               api.GeolocaleTaxon(geolocaleId=1, taxonName="myrmicinaepheidole b"),
               api.GeolocaleTaxon(geolocaleId=2, taxonName="myrmicinaepheidole c")])
    s.add_all([api.TaxaImage(uid=1, taxonName="myrmicinaepheidole a", code="c1", shotType="h", shotNumber="1"),
               api.TaxaImage(uid=2, taxonName="myrmicinaepheidole a", code="c1", shotType="p", shotNumber="1"),
               api.TaxaImage(uid=3, taxonName="myrmicinaepheidole a", code="c9", shotType=None, shotNumber="1")])
    s.commit()
    with engine.begin() as c:
        c.execute(text("create table specimen (code text, taxon_name text, caste text)"))
        c.execute(text("insert into specimen values ('c1', 'myrmicinaepheidole a', 'worker'), ('x', null, 'worker')"))
        c.execute(text("insert into image (id, image_of_id, shot_type, shot_number) values (10, 'c1', 'h', 1),"
                       " (11, 'x', 'h', 1)"))


def get(url, status=200):
    r = client.get(url)
    assert r.status_code == status, (url, r.status_code, r.data[:300])
    return r.get_json()


def codes(body):
    return sorted(sp["specimenCode"] for sp in body["specimens"])


def test_owner_location_collector_filters():
    assert codes(get("/specimens?up=1&ownedby=CAS")) == ["c1"]
    assert codes(get("/specimens?up=1&locatedat=CAS")) == ["c2"]
    assert codes(get("/specimens?up=1&collectedby=P.S.Ward")) == ["c2"]


def test_geolocale_taxa_by_id():
    body = get("/geolocaleTaxa?up=1&geolocaleId=2")
    assert len(body["geolocaleTaxa"]) == 1, body


def test_bbox_must_be_four_numbers():
    assert "bbox" in get("/specimens?up=1&bbox=1,2", 400)["error"]
    assert "bbox" in get("/specimens?up=1&bbox=a,b,c,d", 400)["error"]
    assert codes(get("/specimens?up=1&bbox=-18,48,-19,47")) == ["c1", "c3"]
    assert codes(get("/specimens?up=1&bbox=-18,48,-18.92,47")) == ["c1"]


def test_since_must_be_days():
    assert "since" in get("/images?up=1&since=abc", 400)["error"]
    get("/images?up=1&since=7")


def test_has_image_false_includes_unset_counts():
    assert codes(get("/specimens?up=1&hasImage=false")) == ["c2", "c3"]


def test_distinct_species_without_genus():
    body = get("/geoSpecimens?up=1&coords=-18.9,47.5&radius=50&distinct=species")
    assert " nogenus" in str(body), body


def test_taxa_images_once_with_urls():
    body = get("/taxaImages?up=1")
    taxon = [t for t in body["taxaImages"] if t["taxonName"] == "myrmicinaepheidole a"][0]
    c1 = [sp for sp in taxon["specimen"] if sp["code"] == "c1"][0]
    assert sorted(i["imageId"] for i in c1["images"]) == [1, 2], c1
    assert all(i["urls"] == i["urls:"] and len(i["urls"]) == 4 for i in c1["images"]), c1


def test_unimaged_ignores_nameless_imaged_specimen():
    body = get("/unimagedGeolocaleTaxa?up=1&geolocaleName=Madagascar")
    names = [t["taxonName"] for t in body["unimagedGeolocaleTaxaDict"]]
    assert names == ["myrmicinaepheidole b"], body
    assert "taxon_name is not null" in api.imagedTaxaSql()


def test_properties_with_spaces_and_special_password():
    with tempfile.NamedTemporaryFile("w", suffix=".properties", delete=False) as f:
        f.write("# api login\nuser = apiro\n password= p@ss:w/rd \n")
    props = api._apiProperties(f.name)
    os.unlink(f.name)
    assert api._db_credentials(props) == ("apiro", "p@ss:w/rd"), props
    url = api.URL.create("mysql+pymysql", username="apiro", password="p@ss:w/rd", host="mysql", database="ant")
    back = make_url(url.render_as_string(hide_password=False))
    assert (back.username, back.password, back.host) == ("apiro", "p@ss:w/rd", "mysql"), back


def test_repr_of_every_table():
    for cls in api.Base.__subclasses__():
        repr(cls())


if __name__ == "__main__":
    setup()
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok:", name)
            except Exception as e:  # noqa: BLE001
                failed += 1
                print("FAIL:", name, type(e).__name__, e)
    print("API inputs", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)

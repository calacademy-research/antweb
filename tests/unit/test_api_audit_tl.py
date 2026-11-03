"""API v3.1 (api/v3.1/api.py), audit fixes of 2026-11-02:

- A filter value the API does not understand is a 400 with a message (true/false filters, whole-number elevations,
  yyyy-mm-dd dates). Before, it was ignored and every record came back as if filtered.
- georeferenced=false gives the specimens without coordinates (it gave every specimen).
- habitat= and lifeStageSex= treat % and _ as plain characters (habitat=% matched everything).
- The database connection stops a read after 90 seconds (max_execution_time).

The routes run against a small SQLite database built from the API's own table definitions (no MySQL needed).
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_audit_tl.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_audit_tl.py
"""
import os
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402

engine = create_engine("sqlite://", connect_args={"check_same_thread": False},
                       poolclass=__import__("sqlalchemy.pool", fromlist=["StaticPool"]).StaticPool)
api.Base.metadata.create_all(engine)
api.session = scoped_session(sessionmaker(bind=engine))
api.engine = engine
client = api.application.test_client()


def _specimen(code, **kw):
    return api.Specimen(occurrenceId="o-" + code, code=code, genus="pheidole", species="sp1",
                        subfamily="myrmicinae", family="formicidae", **kw)


def setup():
    s = api.session()
    s.add_all([
        _specimen("geo", decimalLatitude=-18.9, decimalLongitude=47.5, habitat="rainforest", lifeStageSex="1w"),
        _specimen("nolat", decimalLongitude=47.5, habitat="50% forest", lifeStageSex="1q"),
        _specimen("none", habitat="dry_forest"),
    ])
    s.commit()


def get(url, status=200):
    r = client.get(url)
    assert r.status_code == status, (url, r.status_code, r.data[:300])
    return r.get_json()


def codes(body):
    return sorted(sp["specimenCode"] for sp in body["specimens"])


def test_bad_values_are_refused():
    for url, word in [("/specimens?up=1&georeferenced=yes", "georeferenced"),
                      ("/specimens?up=1&fossil=True", "fossil"),
                      ("/specimens?up=1&hasImage=1", "hasImage"),
                      ("/specimens?up=1&validGenus=1", "validGenus"),
                      ("/specimens?up=1&minElevation=abc", "minElevation"),
                      ("/specimens?up=1&maxElevation=1.5", "maxElevation"),
                      ("/specimens?up=1&minDate=garbage", "minDate"),
                      ("/specimens?up=1&maxDate=1979-12-31:", "maxDate"),
                      ("/images?up=1&hasTiff=1", "hasTiff")]:
        body = get(url, 400)
        assert word in body["error"], (url, body)
    # Understood values still work.
    get("/specimens?up=1&fossil=0&hasImage=false&minElevation=-10&maxElevation=3000&minDate=1970-01-01&maxDate=2030-12-31")
    assert api.badParamValue({"fossil": "1", "georeferenced": "false", "minDate": "2010-02-28"}) is None


def test_georeferenced_false():
    assert codes(get("/specimens?up=1&georeferenced=true")) == ["geo"]
    assert codes(get("/specimens?up=1&georeferenced=false")) == ["nolat", "none"]


def test_like_wildcards_are_plain():
    assert codes(get("/specimens?up=1&habitat=%25")) == ["nolat"], "a % is a percent sign"
    assert codes(get("/specimens?up=1&habitat=a_n")) == [], "an underscore is an underscore (not any one character: rAINforest)"
    assert codes(get("/specimens?up=1&habitat=dry_f")) == ["none"]
    assert codes(get("/specimens?up=1&lifeStageSex=%25")) == []
    assert codes(get("/specimens?up=1&lifeStageSex=1q")) == ["nolat"]


def test_statement_time_limit():
    assert api.MAX_EXECUTION_MS == 90000
    src = open(api.__file__, encoding="utf-8").read()
    assert "SET SESSION max_execution_time=%d" in src and "init_command" in src


def main():
    setup()
    test_bad_values_are_refused()
    test_georeferenced_false()
    test_like_wildcards_are_plain()
    test_statement_time_limit()
    print("API audit tl: bad filter values refused, georeferenced=false, plain LIKE characters, time limit OK")


if __name__ == "__main__":
    main()

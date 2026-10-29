"""API v3.1 (api/v3.1/api.py): every reply carries the licence of AntWeb images and data, CC BY 4.0
(decision of Brian Fisher, 2026-10-29).

- Every reply, on every route (JSON, streamed ndjson, errors, 503 without up=1, plain-text messages, the / page), has the header
  Link: <https://creativecommons.org/licenses/by/4.0/>; rel="license".
- Every JSON data reply has a top-level "license" object: name, url, citation. Error replies stay {"error": ...}.
- The ndjson stream keeps its format (index line, record line); the licence is in the header only.
- The citation is the site's statement (src/org/calacademy/antweb/util/License.java), when that file is at hand.

The routes run against a small SQLite database (no MySQL needed). Run inside the API image (CI,
.github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_license.py
or locally with Flask and SQLAlchemy installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_license.py
"""
import datetime
import json
import os
import re
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402

engine = create_engine("sqlite://")
api.Base.metadata.create_all(engine)
api.session = scoped_session(sessionmaker(bind=engine))
client = api.application.test_client()

URL = "https://creativecommons.org/licenses/by/4.0/"
LINK = '<' + URL + '>; rel="license"'
LICENSE_JAVA = os.environ.get("LICENSE_JAVA", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                              "src", "org", "calacademy", "antweb", "util", "License.java"))


def setup():
    s = api.session()
    s.add(api.Specimen(occurrenceId="o-c1", code="c1", genus="pheidole", subfamily="myrmicinae", species="sp1",
                       family="formicidae", minimumElevationInMeters=10, dateCollectedStart=datetime.datetime(2000, 1, 1)))
    s.commit()


def routes():
    return sorted(rule.rule for rule in api.application.url_map.iter_rules()
                  if rule.endpoint not in api.UNCHECKED_ENDPOINTS and "<" not in rule.rule)


def has_link(r):
    return LINK in r.headers.get("Link", "")


def test_license_object():
    assert api.LICENSE["name"] == "CC BY 4.0", api.LICENSE
    assert api.LICENSE["url"] == URL, api.LICENSE
    assert api.LICENSE["citation"].startswith("Images and data on AntWeb are licensed under CC BY 4.0"), api.LICENSE


def test_citation_is_the_site_statement():
    if not os.path.exists(LICENSE_JAVA):
        return  # in CI the step mounts the file (tests.yaml)
    java = open(LICENSE_JAVA, encoding="utf-8").read()
    name = re.search(r'NAME = "([^"]+)"', java).group(1)
    full = re.search(r'FULL_NAME = "([^"]+)"', java).group(1)
    url = re.search(r'URL = "([^"]+)"', java).group(1)
    m = re.search(r'STATEMENT = "([^"]*)" \+ NAME \+ " \(" \+ FULL_NAME\s*\+ "([^"]*)";', java)
    assert m, "License.STATEMENT not found in License.java"
    assert api.LICENSE["citation"] == m.group(1) + name + " (" + full + m.group(2), api.LICENSE["citation"]
    assert (api.LICENSE["name"], api.LICENSE["url"]) == (name, url)


def test_every_route_has_the_header_and_data_replies_the_field():
    found = routes()
    assert "/specimens" in found and "/images" in found and "/taxa" in found, found
    data_replies = 0
    for route in found:
        for query in ("", "?up=1"):
            r = client.get(route + query)
            assert has_link(r), (route + query, r.status_code, r.headers.get("Link"))
            body = r.get_json(silent=True)
            if not isinstance(body, dict):
                # /geoSpecimens and /unimagedGeolocaleTaxa answer a missing parameter with a plain-text message
                # (older behaviour, not JSON): the header carries the licence.
                assert route in ("/geoSpecimens", "/unimagedGeolocaleTaxa"), (route + query, r.get_data(as_text=True)[:200])
                continue
            if "error" in body:
                assert "license" not in body, (route + query, body)
            else:
                assert body.get("license") == api.LICENSE, (route + query, sorted(body))
                data_replies += 1
    assert data_replies >= 8, f"only {data_replies} routes gave a data reply with no parameters"


def test_error_and_health_replies_have_the_header():
    r = client.get("/specimens?up=1&nosuchparameter=1")
    assert r.status_code == 400 and has_link(r), (r.status_code, r.headers.get("Link"))
    assert "license" not in r.get_json()
    r = client.get("/nosuchroute")
    assert r.status_code == 404 and has_link(r), r.status_code
    r = client.get("/")
    assert r.status_code == 200 and has_link(r), r.status_code


def test_ndjson_keeps_its_format_and_has_the_header():
    r = client.get("/specimens?up=1&ndjson=true")
    assert r.status_code == 200 and has_link(r), (r.status_code, r.headers.get("Link"))
    lines = [json.loads(line) for line in r.get_data(as_text=True).splitlines() if line.strip()]
    assert len(lines) == 2 and "index" in lines[0] and lines[1]["specimenCode"] == "c1", lines
    assert not any("license" in line for line in lines), lines


def test_an_existing_link_header_is_kept():
    with api.application.test_request_context("/"):
        resp = api.application.response_class("x")
        resp.headers["Link"] = '<https://example.org/next>; rel="next"'
        resp = api.addLicenseHeader(resp)
        assert resp.headers["Link"] == '<https://example.org/next>; rel="next", ' + LINK, resp.headers["Link"]
        assert api.addLicenseHeader(resp).headers["Link"].count('rel="license"') == 1


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
    if failed:
        sys.exit(1)
    print("API licence OK")

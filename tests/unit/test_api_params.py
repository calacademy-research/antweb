"""API v3.1 (api/v3.1/api.py): unknown parameters are refused, code and rank are aliases.

A misspelt filter used to be ignored: /specimens?up=1&code=casent0852727 returned the first
10,000 specimens instead of one. Now any endpoint answers HTTP 400 with the accepted names;
code (the API v3 name) means specimenCode, and rank (used on the documentation page) taxarank.

Runs without a database: the check happens before the endpoint runs; the alias test only looks
at the request after the check. Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" \
    -v "$PWD/web/documentation/api:/docs:ro" antweb/api:dev /t/test_api_params.py
or locally with Flask and SQLAlchemy installed:
  API_DIR=api/v3.1 API_DOCS=web/documentation/api python3 tests/unit/test_api_params.py
"""
import os
import re
import sys
from urllib.parse import parse_qsl

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

app = api.application
client = app.test_client()
DOCS = os.path.join(os.environ.get("API_DOCS", "/docs"), "apiV3_1-body.jsp")


def args_after_check(url):
    """The request parameters the endpoint sees, or the 400 reply."""
    with app.test_request_context(url):
        from flask import request
        reply = app.preprocess_request()
        if reply is not None:
            return reply
        return list(request.args.items(multi=True))


def test_unknown_parameter_is_refused():
    r = client.get("/specimens?up=1&specimenCod=casent0852727")
    assert r.status_code == 400, r.status_code
    body = r.get_json()
    assert body["unknownParameters"] == ["specimenCod"], body
    assert "specimenCode" in body["acceptedParameters"], body
    assert "limit" in body["acceptedParameters"] and "up" in body["acceptedParameters"], body
    assert body["aliases"] == {"code": "specimenCode"}, body
    assert "specimenCod" in body["error"], body


def test_every_endpoint_refuses_unknown_parameters():
    for rule in app.url_map.iter_rules():
        if rule.endpoint in api.UNCHECKED_ENDPOINTS:
            continue
        r = client.get(rule.rule + "?up=1&noSuchParameter=1")
        assert r.status_code == 400, (rule.rule, r.status_code)
        assert r.get_json()["unknownParameters"] == ["noSuchParameter"], rule.rule


def test_code_is_specimen_code():
    # The reported request: one specimen, not the first 10,000 of everything.
    assert args_after_check("/specimens?up=1&code=casent0852727") == [("up", "1"), ("specimenCode", "casent0852727")]
    assert args_after_check("/images?code=casent0852727&up=1") == [("specimenCode", "casent0852727"), ("up", "1")]
    reply = args_after_check("/specimens?up=1&code=a&specimenCode=b")
    assert reply[1] == 400, reply
    # Not an alias where the endpoint has no specimen code.
    assert args_after_check("/taxa?up=1&code=casent0852727")[1] == 400


def test_rank_is_taxarank():
    assert args_after_check("/taxa?genus=camponotus&rank=genus&up=1") == [("genus", "camponotus"), ("taxarank", "genus"), ("up", "1")]
    assert args_after_check("/specimens?up=1&rank=genus")[1] == 400


def test_known_parameters_pass():
    for url in ("/specimens?up=1&specimenCode=casent0852727&limit=5&offset=0&ndjson=true&log=true",
                "/geolocales?name=Madagascar&up=1",
                "/geoSpecimens?coords=37.77,-122.47&radius=10&distinct=genus&up=1",
                "/taxa?taxonName=myrmicinaecrematogaster%20modiglianii&taxarank=species&up=1",
                "/specimens?up=1&&genus=camponotus"):
        assert isinstance(args_after_check(url), list), url


def test_documentation_examples_use_accepted_names():
    if not os.path.exists(DOCS):
        print("  (documentation page not mounted, skipped)")
        return
    text = open(DOCS, encoding="utf-8").read()
    endpoints = {rule.rule: rule.endpoint for rule in app.url_map.iter_rules()}
    examples = re.findall(r'apiDomainApp %>(/\w+)\?([^"<\s]*)', text)
    assert len(examples) > 20, len(examples)
    for path, query in examples:
        assert path in endpoints, path
        url = path + "?" + query + "&up=1"
        assert isinstance(args_after_check(url), list), url


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok:", name)
            except AssertionError as e:
                failed += 1
                print("FAIL:", name, e)
    print("API parameters", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)

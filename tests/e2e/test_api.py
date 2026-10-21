"""API v3.1: every case answers, and data endpoints return JSON. Requests are capped at 500
records (2026-10-21, until registered API keys); asking for more gives 500 and a notice."""
import json

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from api_cases import API_CASES


@pytest.mark.parametrize("name", sorted(API_CASES))
def test_api(name):
    r = new_session().get(BASE_URL + API_CASES[name], timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{name}: HTTP {r.status_code}"
    if name == "index":
        assert "Antweb API" in r.text
        return
    assert "operational error" not in r.text, f"{name}: {r.text[:200]}"
    try:
        r.json()
    except ValueError:
        pytest.fail(f"{name}: not JSON: {r.text[:200]!r}")


def test_taxa_respects_limit():
    r = new_session().get(BASE_URL + "/v3.1/taxa?genus=camponotus&limit=3&up=1", timeout=120)
    assert len(r.json()["taxa"]) <= 3



def test_unknown_parameter_is_refused():
    # A misspelt filter used to be ignored and the reply was the first 10,000 specimens.
    r = new_session().get(BASE_URL + "/v3.1/specimens?up=1&code=casent0922626", timeout=120)
    assert r.status_code == 400, r.status_code
    body = r.json()
    assert body["unknownParameters"] == ["code"], body
    assert "specimenCode" in body["acceptedParameters"], body
    assert body["didYouMean"] == {"code": ["specimenCode"]}, body


def test_specimen_code_returns_one_specimen():
    r = new_session().get(BASE_URL + "/v3.1/specimens?up=1&specimenCode=casent0922626", timeout=120)
    assert r.status_code == 200, r.status_code
    specimens = r.json()["specimens"]
    assert [s["specimenCode"] for s in specimens] == ["casent0922626"], [s["specimenCode"] for s in specimens][:5]


LIMIT_NOTICE = ("Requests are limited to 500 records while AntWeb moves to registered API keys. "
                "For larger data needs contact antweb@antweb.org.")


def _get(path):
    r = new_session().get(BASE_URL + path, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, (path, r.status_code, r.text[:200])
    return r


def _skip_if_cap_not_deployed(meta):
    # The old code reported limit 10,000 (or, for /geolocales, no limit at all).
    if "limit" not in meta or meta["limit"] > 500:
        pytest.skip("500-record cap not deployed on this host yet")


def test_limit_1000_returns_500_and_the_notice():
    r = _get("/v3.1/specimens?genus=camponotus&limit=1000&up=1")
    body = r.json()
    _skip_if_cap_not_deployed(body["metaData"])
    assert body["metaData"]["limit"] == 500, body["metaData"]
    assert len(body["specimens"]) == 500, len(body["specimens"])
    assert body["metaData"]["limitNotice"] == LIMIT_NOTICE, body["metaData"]
    assert r.headers.get("X-AntWeb-Limit-Notice") == LIMIT_NOTICE, dict(r.headers)


def test_limit_within_the_cap_has_no_notice():
    r = _get("/v3.1/specimens?genus=camponotus&limit=5&up=1")
    body = r.json()
    assert len(body["specimens"]) == 5
    assert "limitNotice" not in body["metaData"], body["metaData"]
    assert "X-AntWeb-Limit-Notice" not in r.headers


def test_ndjson_is_capped_with_the_notice_header():
    r = _get("/v3.1/specimens?genus=camponotus&limit=1000&ndjson=true&up=1")
    lines = [x for x in r.text.split("\n") if x]
    if len(lines) > 1000:
        pytest.skip("500-record cap not deployed on this host yet")
    assert len(lines) == 1000, len(lines)   # an index line and a record line per specimen
    assert json.loads(lines[-1]).get("specimenCode"), lines[-1][:200]
    assert r.headers.get("X-AntWeb-Limit-Notice") == LIMIT_NOTICE, dict(r.headers)


def test_offset_pages_past_the_cap():
    first = _get("/v3.1/specimens?genus=camponotus&limit=1000&up=1").json()
    _skip_if_cap_not_deployed(first["metaData"])
    second = _get("/v3.1/specimens?genus=camponotus&limit=500&offset=500&up=1").json()
    # /specimens is sorted by specimen code (since the cap), so pages follow on without overlap.
    assert second["metaData"]["offset"] == 500, second["metaData"]
    assert "limitNotice" not in second["metaData"], second["metaData"]
    codes1 = [s["specimenCode"] for s in first["specimens"]]
    codes2 = [s["specimenCode"] for s in second["specimens"]]
    assert codes2 and not set(codes1) & set(codes2), len(set(codes1) & set(codes2))


def test_geolocales_are_capped():
    # geolocales used to ignore limit and return every geolocale.
    body = _get("/v3.1/geolocales?georank=adm1&limit=1000&up=1").json()
    _skip_if_cap_not_deployed(body["metaData"])
    assert 0 < len(body["geolocales"]) <= 500, len(body["geolocales"])
    assert body["metaData"]["limitNotice"] == LIMIT_NOTICE

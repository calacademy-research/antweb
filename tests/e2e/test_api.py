"""API v3.1: every case answers, and data endpoints return JSON."""
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

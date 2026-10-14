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

"""Tiers 2 and 5: key pages still show the same content (see fingerprint.py)."""
import json
from pathlib import Path

import pytest

from antweb import BASE_URL, TIMEOUT, new_session
from fingerprint import KEY_PAGES, differences, fingerprint

SNAPSHOTS = Path(__file__).parent / "snapshots.json"
RECORDED = json.loads(SNAPSHOTS.read_text()) if SNAPSHOTS.exists() else {}


def test_every_key_page_is_recorded():
    assert set(KEY_PAGES) <= set(RECORDED), f"not recorded: {sorted(set(KEY_PAGES) - set(RECORDED))}"


@pytest.mark.parametrize("url", KEY_PAGES)
def test_page_content(url):
    if url not in RECORDED:
        pytest.skip("not recorded yet")
    r = new_session().get(BASE_URL + url, timeout=max(TIMEOUT, 120))
    assert r.status_code == RECORDED[url]["status"], f"{url}: HTTP {r.status_code}"
    diff = differences(RECORDED[url], fingerprint(r.text))
    assert not diff, f"{url}: " + "; ".join(diff)

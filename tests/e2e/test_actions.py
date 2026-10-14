"""Characterization tests: every Struts action and every recorded public page
must still behave as recorded in expectations.json (made by survey.py).

A difference is either a regression to fix or an intended change; in the
second case re-record the baseline (see README.md).
"""
import json
from pathlib import Path

import pytest

from antweb import BROKEN, IS_PRODUCTION, get, new_session, struts_actions
from policy import FLAKY, NEVER, REFUSE_WITH_MESSAGE, WRITE_ACTIONS

import os

EXPECTED = json.loads((Path(__file__).parent / "expectations.json").read_text())
PROBE_WRITES = os.environ.get("ANTWEB_PROBE_WRITE_ACTIONS") == "1" and not IS_PRODUCTION

# Actions broken when the baseline was recorded. They must not get worse, and
# the list should shrink; remove an entry once it is fixed.
KNOWN_BROKEN = {
    "/taxonomicPage": "500 without parameters (works with rank/project)",
    "/taxonomicpage": "500 without parameters (works with rank/project)",
    "/chooseComparison": "error page without parameters",
    "/viewGroup": "error page without parameters",
}


def test_removed_actions_stay_removed():
    """Mappings removed on purpose (dead classes, retired home page editor)."""
    for path in ("/ancPageMgr", "/jspFetch", "/saveHomePage", "/previewHomePage", "/editHomePage"):
        outcome = get(new_session(), f"{path}.do")
        assert outcome.category not in BROKEN and outcome.status in (400, 404), f"{path}: {outcome}"


def test_every_action_has_a_decision():
    """A new action in struts-config.xml must be recorded or listed in policy.py."""
    missing = [a.path for a in struts_actions()
               if a.path not in EXPECTED["actions"] and a.path not in NEVER and a.path not in WRITE_ACTIONS]
    assert not missing, f"actions with no expectation: {missing}"


@pytest.mark.parametrize("path", sorted(EXPECTED["actions"]))
def test_action_anonymous(path):
    url = f"{path}.do" if path != "/" else "/"
    outcome = get(new_session(), url)
    expected = EXPECTED["actions"].get(path)
    if path in FLAKY:
        assert outcome.category in FLAKY[path], f"{path}: {outcome}"
        return
    if path in KNOWN_BROKEN:
        if outcome.category not in BROKEN:
            pytest.fail(f"{path} works now ({outcome.category}); remove it from KNOWN_BROKEN and re-record")
        return
    assert outcome.category not in BROKEN, f"{path}: {outcome}"
    assert outcome.category == expected, f"{path}: expected {expected}, got {outcome}"


@pytest.mark.skipif(not PROBE_WRITES, reason="set ANTWEB_PROBE_WRITE_ACTIONS=1 (staging only)")
@pytest.mark.parametrize("path", sorted(WRITE_ACTIONS))
def test_write_action_requires_login(path):
    """Security: data-changing actions must refuse anonymous requests (#75)."""
    if path in KNOWN_BROKEN:
        pytest.skip(KNOWN_BROKEN[path])
    outcome = get(new_session(), f"{path}.do")
    allowed = {"login", "not_found"} | ({"message"} if path in REFUSE_WITH_MESSAGE else set())
    assert outcome.category in allowed, f"{path} did not ask for a login: {outcome}"


@pytest.mark.parametrize("url", sorted(u for u, c in EXPECTED["pages"].items() if c in ("ok", "login")))
def test_public_page(url):
    outcome = get(new_session(), url)
    assert outcome.category not in BROKEN, f"{url}: {outcome}"
    assert outcome.category == EXPECTED["pages"][url], f"{url}: expected {EXPECTED['pages'][url]}, got {outcome}"

"""The Daily update page (dailyUpdate.do, 2026-11-07): where AntWeb's daily update (the Scheduler's run at 05:00
Pacific) is, step by step, and its JSON (dailyUpdate.do?format=json, no login) read by the production morning check.

Static checks (any host): the Scheduler and set 1 mark the steps in the order DailyUpdate.STEPS lists them; the
page's own words say "daily update", never "nightly" (Brian Fisher, 2026-11-07). Live checks (read-only, staging):
the JSON answers without a login with the agreed keys ({"runs":[{"started","ended","state","steps":[{"name","state",
"started","ended","usual_seconds","message",...}]}]}, newest run first, times ISO 8601 without zone, Pacific); an
anonymous visitor is asked to log in for the page; the admin test login sees the page in the tool-page look. Skipped
where the page is not deployed yet. Logic: test/org/calacademy/antweb/util/DailyUpdateTest.java; table:
DailyUpdateDbTest.java.
"""
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, get, new_session
from staging_accounts import IS_STAGING, login, needs

SRC = REPO_ROOT / "src/org/calacademy/antweb/util"
ISO = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d$")
RUN_STATES = {"running", "ok", "failed"}
STEP_STATES = {"done", "running", "waiting", "skipped", "failed"}
STEP_KEYS = {"name", "state", "started", "ended", "usual_seconds", "message"}


def _code(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def _step_keys():
    code = _code(SRC / "DailyUpdate.java")
    block = code[code.index("STEPS = "):code.index("private static final Map<String, Integer> INDEX")]
    consts = {"COUNT_CHECK": "countCheck", "END": "end"}
    return [key or consts[const] for key, const in re.findall(r'new Step\((?:"(\w+)"|(COUNT_CHECK|END))', block)]


def test_source_steps_marked_in_order():
    """Each step is marked (start or skip) where the run reaches it, in the order of DailyUpdate.STEPS."""
    keys = _step_keys()
    assert len(keys) == 16 and keys[0] == "antcatImport" and keys[-1] == "end", keys
    sched = _code(SRC / "Scheduler.java")
    util = _code(SRC / "UtilDataAction.java")
    set1 = util[util.index('if ("set1".equals(action))'):util.index('if ("set2".equals(action))')]
    # Set 1's own steps, in UtilDataAction
    order = [set1.index(f'DailyUpdate.start("{k}")') for k in ("taxonWorks", "nameUpdate", "set1Finish")]
    assert order == sorted(order), order
    assert set1.index('DailyUpdate.start("taxonWorks")') < set1.index("TaxonWorksNightly.run(")
    assert set1.index('DailyUpdate.start("nameUpdate")') < set1.index("nightlyNameUpdate(")
    # Set 4's own step (2026-11-08): the imaging progress, after the statistics row
    set4 = util[util.index('if ("set4".equals(action))'):util.index('if ("set5".equals(action))')]
    assert set4.index("populateStatistics(") < set4.index('DailyUpdate.start("imagingProgress")') < set4.index('doAction("imagingProgress"')
    # The Scheduler's steps
    marks = []
    for k in keys:
        if k in ("taxonWorks", "nameUpdate", "set1Finish", "imagingProgress", "end"):
            continue
        if k == "placeListTotals":   # marked in runPlaceListTotals(), called right after set 4
            marks.append((sched.index("runPlaceListTotals();"), k))
            assert 'DailyUpdate.start("placeListTotals")' in sched
            continue
        name = "DailyUpdate.COUNT_CHECK" if k == "countCheck" else f'"{k}"'
        found = [m.start() for m in re.finditer(r"DailyUpdate\.(?:start|skip)\(" + re.escape(name), sched)]
        assert found, f"step {k} is never marked"
        marks.append((min(found), k))
    assert [k for _, k in sorted(marks)] == [k for _, k in marks], sorted(marks)
    assert "DailyUpdate.beginRun()" in sched and "DailyUpdate.schedulerFinished(" in sched
    assert "DailyUpdate.failRun(" in sched


def test_source_page_says_daily_update():
    """User-facing words: "daily update", never "nightly" (internal class names may keep it)."""
    action = _code(SRC / "DailyUpdateAction.java")
    intro = action[action.index("INTRO = "):action.index("static String statusClass")]
    body = (REPO_ROOT / "web/util/dailyUpdate-body.jsp").read_text(encoding="utf-8")
    body = re.sub(r"<%--.*?--%>", "", body, flags=re.S)
    for text in (intro, body):
        assert "daily update" in text.lower() or "Daily update" in text
        assert "nightly" not in text.lower()
    mgr = _code(SRC / "AntwebMgr.java")
    assert "AntWeb is running its daily update" in mgr


def _json(session):
    r = session.get(BASE_URL + "/dailyUpdate.do?format=json", timeout=TIMEOUT)
    if r.status_code == 404 or "json" not in r.headers.get("Content-Type", ""):
        outcome = classify(r)
        if outcome.category in ("not_found", "message", "error_page", "other"):
            pytest.skip("dailyUpdate.do is not deployed on this server yet")
    assert r.status_code == 200, r.status_code
    assert r.headers.get("Content-Type", "").startswith("application/json"), r.headers.get("Content-Type")
    return r.json()


def test_json_without_login_has_the_agreed_keys():
    data = _json(new_session())
    assert isinstance(data.get("runs"), list)
    assert len(data["runs"]) <= 2
    starts = [run["started"] for run in data["runs"]]
    assert starts == sorted(starts, reverse=True), "newest run first"
    for run in data["runs"]:
        assert {"started", "ended", "state", "steps"} <= set(run), run.keys()
        assert ISO.match(run["started"]), run["started"]
        assert run["ended"] is None or ISO.match(run["ended"]), run["ended"]
        assert run["state"] in RUN_STATES, run["state"]
        if run["state"] == "running":
            assert run["ended"] is None
        assert len(run["steps"]) == len(_step_keys())   # 16 since 2026-11-08 (imagingProgress)
        assert run["steps"][0]["name"] == "antcatImport" and run["steps"][-1]["name"] == "end"
        for step in run["steps"]:
            assert STEP_KEYS <= set(step), step
            assert step["state"] in STEP_STATES, step
            for k in ("started", "ended"):
                assert step[k] is None or ISO.match(step[k]), step
            assert step["usual_seconds"] is None or isinstance(step["usual_seconds"], int), step
            assert isinstance(step["message"], str)
    text = str(data)
    assert "@" not in text, "no personal data (email addresses) in the JSON"


def test_page_needs_login():
    outcome = get(new_session(), "/dailyUpdate.do")
    if outcome.category == "not_found":
        pytest.skip("dailyUpdate.do is not deployed on this server yet")
    assert outcome.category == "login", outcome


@pytest.mark.skipif(not IS_STAGING, reason="test logins: staging only")
@needs("admin")
def test_admin_sees_the_page():
    s, _token, _id = login("admin")
    r = s.get(BASE_URL + "/dailyUpdate.do", timeout=120)
    outcome = classify(r)
    if outcome.category == "not_found":
        pytest.skip("dailyUpdate.do is not deployed on this server yet")
    assert outcome.category == "ok", outcome
    assert '<div class="aw-tools" id="daily-update-page">' in r.text
    assert "<h1>Daily update</h1>" in r.text
    assert "05:00 Pacific" in r.text and "Pacific" in r.text
    if 'id="daily-update-none"' not in r.text:
        assert 'class="daily-update-steps"' in r.text
        assert "<th>Usual</th>" in r.text and "Started (Pacific)" in r.text
        states = re.findall(r'<tr data-step="\w+" data-state="(\w+)"', r.text)
        assert states and set(states) <= STEP_STATES, states

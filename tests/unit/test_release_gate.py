"""The release gate's checks (tools/release/gate.py) and the known-failures list (tests/e2e/known-failures.toml)."""
import datetime as dt
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gate", ROOT / "tools/release/gate.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

UTC = dt.timezone.utc


def test_known_failures_list_is_valid_today():
    entries = gate.read_known_failures()
    assert gate.known_failure_problems(entries, dt.date.today()) == []


def test_known_failure_rules():
    d = dt.date(2026, 11, 6)
    ok = {"test": "t", "reason": "r", "decided_by": "Brian Fisher", "decided": d, "expires": d + dt.timedelta(days=7)}
    assert gate.known_failure_problems([ok], d) == []
    assert "expired" in gate.known_failure_problems([dict(ok, expires=d - dt.timedelta(days=1))], d)[0]
    assert "at most 7" in gate.known_failure_problems([dict(ok, expires=d + dt.timedelta(days=8))], d)[0]
    assert "missing reason" in gate.known_failure_problems([dict(ok, reason="")], d)[0]
    # review of #330: a future decision date would stretch the 7 days; a datetime is refused, not a crash
    assert "future" in gate.known_failure_problems([dict(ok, decided=d + dt.timedelta(days=30), expires=d + dt.timedelta(days=35))], d)[0]
    assert "must be dates" in gate.known_failure_problems([dict(ok, decided=dt.datetime(2026, 11, 6, 9, 0))], d)[0]


def test_window():
    # 2026-11-06 is PST (UTC-8)
    assert gate.window_closed(dt.datetime(2026, 11, 6, 12, 45, tzinfo=UTC))       # 04:45
    assert gate.window_closed(dt.datetime(2026, 11, 6, 16, 14, tzinfo=UTC))       # 08:14
    assert not gate.window_closed(dt.datetime(2026, 11, 6, 16, 15, tzinfo=UTC))   # 08:15
    assert not gate.window_closed(dt.datetime(2026, 11, 6, 12, 44, tzinfo=UTC))   # 04:44
    assert gate.window_closed(dt.datetime(2026, 7, 1, 12, 0, tzinfo=UTC))         # 05:00 PDT


def test_rollback_line():
    assert gate.has_rollback("text\n\nRollback: revert this PR (no database step).")
    assert gate.has_rollback("**Rollback:** revert")
    assert not gate.has_rollback("roll back if needed")
    assert not gate.has_rollback(None)


def test_nightly_files():
    files = ["src/org/calacademy/antweb/util/Scheduler.java", "src/org/calacademy/antweb/home/PlaceViewTotalDb.java",
             "src/org/calacademy/antweb/home/GroupCountDb.java", "web/overview-body.jsp", "tests/e2e/test_x.py"]
    assert gate.nightly_files(files) == files[:3]


def test_review_state():
    head = "d13a8863e8dd17bf09e9046ccb7d12b9175f6587"
    def c(text, sha="d13a886"):
        return {"body": f"{gate.REVIEW_MARKER}\n### Claude review\n\n{text}\n\n<sub>Automatic review of {sha} by `m`.</sub>",
                "user": {"login": gate.REVIEW_BOT}}
    assert gate.review_state([], head)[0] is False
    assert gate.review_state([c("No blocking issues. Should fix: ...")], head)[0] is True
    assert gate.review_state([c("No blocking issues.", sha="bbd866d")], head)[0] is False
    assert gate.review_state([c("No blocking issues."), c("**Blocking**: x")], head)[0] is False
    assert gate.review_state([{"body": "an ordinary comment"}, c("No blocking issues.")], head)[0] is True
    # review of #330: a comment in the review's form posted by someone else does not count
    forged = dict(c("No blocking issues."), user={"login": "someone"})
    assert gate.review_state([c("**Blocking**: x"), forged], head)[0] is False


def test_failed_tests_and_unlisted():
    notes = [{"title": "e2e failed tests", "message": "2 failed: test_a, test_b"},
             {"title": "e2e failure", "message": "test_c: AssertionError: x"},
             {"title": "e2e timing", "message": "full run: 40 min"}]
    failed = gate.failed_tests(notes)
    assert failed == {"test_a", "test_b", "test_c"}
    assert gate.unlisted(failed, [{"test": "test_x.py::test_b"}, {"test": "test_c"}]) == ["test_a"]
    # the e2e workflow separates the names with "; " (2026-11-06, run 42249265193)
    assert gate.failed_tests([{"title": "e2e failed tests", "message": "2 failed: test_a; test_b"}]) == {"test_a", "test_b"}


def _json(*runs):
    import json
    return 200, {"Content-Type": "application/json"}, json.dumps({"runs": list(runs)})


def test_daily_update_state_json():
    running = {"started": "2026-11-09T05:00:00", "ended": None, "state": "running", "steps": []}
    ok = {"started": "2026-11-08T05:00:00", "ended": "2026-11-08T07:41:00", "state": "ok", "steps": []}
    failed = {"started": "2026-11-08T05:00:00", "ended": None, "state": "failed", "steps": []}
    assert gate.daily_update_state(*_json(running, ok))[0] == "running"
    assert gate.daily_update_state(*_json(ok, failed))[0] == "not running"
    assert gate.daily_update_state(*_json(failed, ok))[0] == "not running"
    assert gate.daily_update_state(*_json())[0] == "not running"
    # conservative: an ok run with no end, a state the gate does not know, other JSON
    assert gate.daily_update_state(*_json(dict(ok, ended=None)))[0] == "unknown"
    assert gate.daily_update_state(*_json(dict(ok, state="waiting")))[0] == "unknown"
    assert gate.daily_update_state(200, {}, '{"status": "fine"}')[0] == "unknown"
    assert gate.daily_update_state(200, {}, "[]")[0] == "unknown"


def test_daily_update_state_unreachable():
    challenge = "<html><title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/x'></script>"
    assert gate.daily_update_state(403, {}, challenge)[0] == "unknown"
    assert gate.daily_update_state(200, {"cf-mitigated": "challenge"}, "")[0] == "unknown"
    assert gate.daily_update_state(404, {}, "not found")[0] == "unknown"
    assert gate.daily_update_state(502, {}, "")[0] == "unknown"
    # a login page (the HTML page needs a curator login)
    assert gate.daily_update_state(200, {}, "<html><form action='login.do'>")[0] == "unknown"


def test_daily_update_state_page():
    # markup of DailyUpdateAction.runCard (util/dailyUpdate-body.jsp)
    def page(state):
        return ('<div class="aw-card" id="daily-update-latest"><h2>Today\'s daily update (running)</h2>'
                f'<p class="aw-lead" id="daily-update-state" data-state="{state}">2026-11-09, started 05:00: x.</p>'
                '<table class="daily-update-steps"><tr data-step="end" data-state="done"></tr></table></div>')
    assert gate.daily_update_state(200, {}, page("running"))[0] == "running"
    assert gate.daily_update_state(200, {}, page("ok"))[0] == "not running"
    assert gate.daily_update_state(200, {}, page("failed"))[0] == "not running"
    assert gate.daily_update_state(200, {}, page("weird"))[0] == "unknown"
    assert gate.daily_update_state(200, {}, '<div class="aw-card" id="daily-update-none"><p>No daily update</p></div>')[0] == "not running"


def test_daily_check():
    running = {"started": "2026-11-09T05:00:00", "ended": None, "state": "running"}
    ok = {"started": "2026-11-08T05:00:00", "ended": "2026-11-08T07:41:00", "state": "ok"}

    def boom():
        raise OSError("Tunnel connection failed: 403 Forbidden")
    # running: closed, also with --daily-update-checked
    assert gate.daily_check(lambda: _json(running), False)[0] is False
    assert gate.daily_check(lambda: _json(running), True)[0] is False
    assert gate.daily_check(lambda: _json(ok), False)[0] is True
    # unknown fails closed unless it was read by hand
    ok_, text = gate.daily_check(boom, False)
    assert ok_ is False and gate.UNKNOWN_DAILY in text
    ok_, text = gate.daily_check(lambda: (403, {"cf-mitigated": "challenge"}, ""), False)
    assert ok_ is False and gate.UNKNOWN_DAILY in text
    ok_, text = gate.daily_check(boom, True)
    assert ok_ is True and gate.UNKNOWN_DAILY in text and "--daily-update-checked" in text
    # user wording: "daily update", never "nightly" in what check 8 says (the flag's name aside)
    for fetch, by_hand in ((lambda: _json(running), False), (lambda: _json(ok), False), (boom, False)):
        assert "nightly" not in gate.daily_check(fetch, by_hand)[1].replace("--daily-update-checked", "")


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")

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


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")

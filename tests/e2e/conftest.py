import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import os
import re

import pytest

import antweb
import areas

# ---------- quick / full runs and parallel workers (areas.toml, README "Quick and full runs") ----------


def pytest_configure(config):
    config.addinivalue_line("markers", "quick: in every quick run (areas.toml [quick])")
    config.addinivalue_line("markers", "parallel: anonymous read-only test, may run side by side with others")
    for name in areas.areas():
        config.addinivalue_line("markers", f"area_{name}: tests of the {name} area (areas.toml)")
    config._e2e_started = time.time()


def _in_parallel_run(config):
    return bool(getattr(config.option, "numprocesses", None)) or bool(os.environ.get("PYTEST_XDIST_WORKER"))


@pytest.hookimpl(tryfirst=True)
def pytest_collection_modifyitems(config, items):
    """Markers from areas.toml; in a parallel run every test not in [parallel] goes to one serial group."""
    cfg = areas.load()
    parallel_run = _in_parallel_run(config)
    for item in items:
        file_name = item.path.name
        test_name = getattr(item, "originalname", None) or item.name.split("[", 1)[0]
        marks = areas.markers_for(file_name, test_name, cfg)
        for m in marks:
            item.add_marker(m)
        item._e2e_parallel = "parallel" in marks
        if parallel_run and not item._e2e_parallel:
            # pytest-xdist --dist loadgroup: the whole group runs in one worker, in collection order.
            item.add_marker(pytest.mark.xdist_group("serial"))


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_protocol(item, nextitem):
    """In a parallel run, a test of the parallel set may not log in (antweb.post_login refuses)."""
    if os.environ.get("PYTEST_XDIST_WORKER") and getattr(item, "_e2e_parallel", False):
        antweb.PARALLEL_TEST = _plain_id(item.nodeid)
    try:
        yield
    finally:
        antweb.PARALLEL_TEST = None


def _plain_id(nodeid):
    return re.sub(r"@serial$", "", nodeid)


_TIMES = defaultdict(float)      # seconds per test file (setup, call and teardown)
_SET_TIMES = defaultdict(float)  # seconds of the parallel set and of the one-at-a-time set


def pytest_runtest_logreport(report):
    seconds = report.duration or 0.0
    _TIMES[report.nodeid.split("::", 1)[0].rsplit("/", 1)[-1]] += seconds
    _SET_TIMES["parallel" if "parallel" in (getattr(report, "keywords", None) or {}) else "serial"] += seconds


def _minutes(seconds):
    return f"{seconds / 60:.1f} min"


def _timing_lines(config):
    wall = time.time() - getattr(config, "_e2e_started", time.time())
    total = sum(_TIMES.values())
    run = os.environ.get("E2E_RUN", "")
    head = (f"{run + ' run: ' if run else ''}{_minutes(wall)} wall clock; tests' own time {_minutes(total)} "
            f"(parallel set {_minutes(_SET_TIMES['parallel'])}, one-at-a-time set {_minutes(_SET_TIMES['serial'])})")
    slow = sorted(_TIMES.items(), key=lambda kv: -kv[1])
    return head, slow


def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """List failures in the GitHub Actions job summary (easier to read than the log)."""
    if os.environ.get("PYTEST_XDIST_WORKER"):
        return  # the controller reports for all workers
    failed = terminalreporter.stats.get("failed", []) + terminalreporter.stats.get("error", [])
    if not _TIMES:
        return  # nothing ran (--collect-only)
    head, slow = _timing_lines(config)
    terminalreporter.write_line("Time: " + head)
    terminalreporter.write_line("Slowest files: " + ", ".join(f"{f} {s:.0f}s" for f, s in slow[:15]))
    if os.environ.get("GITHUB_ACTIONS") == "true":
        # Readable through the API (check-runs annotations) when the log cannot be downloaded.
        note = head + ". Slowest files: " + ", ".join(f"{f} {s:.0f}s" for f, s in slow[:12])
        terminalreporter.write_line("::notice title=e2e timing::" + note.replace("%", "%25"))
        # One annotation per failure (GitHub shows the first 10 of a step): readable through the
        # API (check-runs annotations) when the log and job summary cannot be downloaded.
        for rep in failed[:10]:
            msg = str(getattr(rep, "longrepr", ""))
            reason = next((l.strip() for l in msg.splitlines() if l.strip().startswith("E ")), msg.splitlines()[-1] if msg else "")
            text = (_plain_id(rep.nodeid).split("::", 1)[-1][:150] + ": " + reason[:350]).replace("%", "%25").replace("\r", "").replace("\n", " ")
            terminalreporter.write_line("::error title=e2e failure::" + text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    passed = len(terminalreporter.stats.get("passed", []))
    skipped = len(terminalreporter.stats.get("skipped", []))
    lines = [f"## Test results: {passed} passed, {len(failed)} failed, {skipped} skipped", "", "Time: " + head, ""]
    if failed:
        lines += ["| Test | Reason |", "|---|---|"]
        for rep in failed:
            msg = str(getattr(rep, "longrepr", ""))
            reason = next((l.strip() for l in reversed(msg.splitlines()) if l.strip().startswith(("E ", "AssertionError", "Failed"))), msg.splitlines()[-1] if msg else "")
            lines.append(f"| {_plain_id(rep.nodeid).split('::', 1)[-1][:120]} | {reason.replace('|', '/')[:200]} |")
        # The full failure text (the assert line and its "E" lines), since the table shows one line only.
        for rep in failed:
            msg = str(getattr(rep, "longrepr", ""))
            all_lines = [l for l in msg.splitlines() if l.strip()]
            # The failing source line (">") and the first "E" lines say what failed; page HTML follows.
            src = [l for l in all_lines if l.startswith(">")][-3:]
            errs = [l for l in all_lines if l.startswith("E ")][:15]
            tail = src + errs if (src or errs) else all_lines[-40:]
            lines += ["", "<details><summary>" + _plain_id(rep.nodeid).split("::", 1)[-1][:120] + "</summary>", "", "```",
                      *[l.replace("```", "~~~")[:300] for l in tail], "```", "</details>"]
    lines += ["", "<details><summary>Time per test file (slowest first)</summary>", "",
              "| File | Seconds |", "|---|---|", *[f"| {f} | {s:.0f} |" for f, s in slow], "</details>"]
    with open(path, "a") as f:
        f.write("\n".join(lines) + "\n")

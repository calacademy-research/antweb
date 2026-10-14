import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import os


def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """List failures in the GitHub Actions job summary (easier to read than the log)."""
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    failed = terminalreporter.stats.get("failed", []) + terminalreporter.stats.get("error", [])
    passed = len(terminalreporter.stats.get("passed", []))
    skipped = len(terminalreporter.stats.get("skipped", []))
    lines = [f"## Test results: {passed} passed, {len(failed)} failed, {skipped} skipped", ""]
    if failed:
        lines += ["| Test | Reason |", "|---|---|"]
        for rep in failed:
            msg = str(getattr(rep, "longrepr", ""))
            reason = next((l.strip() for l in reversed(msg.splitlines()) if l.strip().startswith(("E ", "AssertionError", "Failed"))), msg.splitlines()[-1] if msg else "")
            lines.append(f"| {rep.nodeid.split('::', 1)[-1][:120]} | {reason.replace('|', '/')[:200]} |")
    with open(path, "a") as f:
        f.write("\n".join(lines) + "\n")

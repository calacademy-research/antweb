import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import os


def pytest_terminal_summary(terminalreporter, exitstatus, config):
    """List failures in the GitHub Actions job summary (easier to read than the log)."""
    failed = terminalreporter.stats.get("failed", []) + terminalreporter.stats.get("error", [])
    if os.environ.get("GITHUB_ACTIONS") == "true":
        # One annotation per failure (GitHub shows the first 10 of a step): readable through the
        # API (check-runs annotations) when the log and job summary cannot be downloaded.
        for rep in failed[:10]:
            msg = str(getattr(rep, "longrepr", ""))
            reason = next((l.strip() for l in msg.splitlines() if l.strip().startswith("E ")), msg.splitlines()[-1] if msg else "")
            text = (rep.nodeid.split("::", 1)[-1][:150] + ": " + reason[:350]).replace("%", "%25").replace("\r", "").replace("\n", " ")
            terminalreporter.write_line("::error title=e2e failure::" + text)
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    passed = len(terminalreporter.stats.get("passed", []))
    skipped = len(terminalreporter.stats.get("skipped", []))
    lines = [f"## Test results: {passed} passed, {len(failed)} failed, {skipped} skipped", ""]
    if failed:
        lines += ["| Test | Reason |", "|---|---|"]
        for rep in failed:
            msg = str(getattr(rep, "longrepr", ""))
            reason = next((l.strip() for l in reversed(msg.splitlines()) if l.strip().startswith(("E ", "AssertionError", "Failed"))), msg.splitlines()[-1] if msg else "")
            lines.append(f"| {rep.nodeid.split('::', 1)[-1][:120]} | {reason.replace('|', '/')[:200]} |")
        # The full failure text (the assert line and its "E" lines), since the table shows one line only.
        for rep in failed:
            msg = str(getattr(rep, "longrepr", ""))
            all_lines = [l for l in msg.splitlines() if l.strip()]
            # The failing source line (">") and the first "E" lines say what failed; page HTML follows.
            src = [l for l in all_lines if l.startswith(">")][-3:]
            errs = [l for l in all_lines if l.startswith("E ")][:15]
            tail = src + errs if (src or errs) else all_lines[-40:]
            lines += ["", "<details><summary>" + rep.nodeid.split("::", 1)[-1][:120] + "</summary>", "", "```",
                      *[l.replace("```", "~~~")[:300] for l in tail], "```", "</details>"]
    with open(path, "a") as f:
        f.write("\n".join(lines) + "\n")

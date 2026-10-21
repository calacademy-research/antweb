"""Choose the tests of a quick run from the paths a pull request changes (areas.toml).

    python tests/e2e/select_tests.py --changed changed.txt      # one path per line
    python tests/e2e/select_tests.py --changed changed.txt --github   # also write GITHUB_ENV / summary

Prints the pytest arguments (test files or file::test ids) on one line. With --github it sets
E2E_TARGETS, E2E_AREAS and (when a changed path asks for it) E2E_FULL=true in $GITHUB_ENV, and
explains the choice in the job summary.
"""
import argparse
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import areas  # noqa: E402


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--changed", required=True, help="file with one changed path per line")
    ap.add_argument("--github", action="store_true", help="write GITHUB_ENV and the job summary")
    args = ap.parse_args(argv)
    changed = [l.strip() for l in Path(args.changed).read_text().splitlines() if l.strip()]
    sel = areas.select(changed)
    print(" ".join(sel["targets"]))
    if args.github:
        with open(os.environ["GITHUB_ENV"], "a") as f:
            f.write(f"E2E_TARGETS={' '.join(sel['targets'])}\n")
            f.write(f"E2E_AREAS={','.join(sel['areas'])}\n")
            if sel["full"]:
                f.write("E2E_FULL=true\n")
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            kind = "full run (a changed path affects every page)" if sel["full"] else "quick run"
            lines = [f"## Test selection: {kind}", "",
                     f"Areas: {', '.join(sel['areas']) or 'none (quick set only)'}", "",
                     f"{len(changed)} changed paths. Why:", ""]
            lines += [f"- {r}" for r in sel["reasons"][:60]]
            if not sel["full"]:
                lines += ["", "<details><summary>Tests run</summary>", "", *[f"- {t}" for t in sel["targets"]], "</details>"]
            with open(summary, "a") as f:
                f.write("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

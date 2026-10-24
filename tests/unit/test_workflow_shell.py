#!/usr/bin/env python3
"""Every run: block of the GitHub workflows is valid shell (bash -n), including the scripts passed to sh -c '...'.

2026-10-24: an apostrophe in a comment ("specimen's") inside a docker run ... sh -c '...' block ended the quoted script
early; the step failed with "syntax error near unexpected token" only on GitHub. This test finds that before a push.
Run: python3 tests/unit/test_workflow_shell.py
"""
import glob
import os
import re
import subprocess
import sys
import tempfile

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def run_blocks(path):
    with open(path) as f:
        wf = yaml.safe_load(f)
    for job_name, job in (wf.get("jobs") or {}).items():
        for i, step in enumerate(job.get("steps") or []):
            if "run" in step:
                yield f"{os.path.basename(path)} {job_name}: {step.get('name', 'step ' + str(i))}", step["run"]


def inner_scripts(script):
    """The text of each sh -c '...' / bash -c '...' argument (single-quoted, as the workflows write them)."""
    for m in re.finditer(r"\b(?:sh|bash) -c '", script):
        start = m.end()
        end = script.find("'", start)
        if end == -1:
            yield None
            continue
        yield script[start:end]


def syntax_error(text):
    with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False) as f:
        # ${{ ... }} expressions are filled in by GitHub before the shell runs
        f.write(re.sub(r"\$\{\{[^}]*\}\}", "X", text))
    try:
        r = subprocess.run(["bash", "-n", f.name], capture_output=True, text=True)
        return r.stderr.strip() if r.returncode else None
    finally:
        os.unlink(f.name)


def problems():
    out = []
    for path in sorted(glob.glob(os.path.join(ROOT, ".github/workflows/*.y*ml"))):
        for where, script in run_blocks(path):
            err = syntax_error(script)
            if err:
                out.append(f"{where}: {err}")
                continue
            for inner in inner_scripts(script):
                if inner is None:
                    out.append(f"{where}: sh -c ' is never closed")
                    continue
                err = syntax_error(inner)
                if err:
                    out.append(f"{where} (the script given to sh -c): {err}")
    return out


def self_test():
    good = "docker run x sh -c '\n  echo one\n  # the line of one specimen\n'\n"
    bad = "docker run x sh -c '\n  echo one\n  # one specimen's line (any curator)\n  echo two\n'\n"
    assert syntax_error(good) is None and all(syntax_error(s) is None for s in inner_scripts(good))
    errs = [syntax_error(bad)] + [syntax_error(s) for s in inner_scripts(bad) if s is not None]
    assert any(errs), "an apostrophe inside sh -c '...' must be caught"


if __name__ == "__main__":
    self_test()
    p = problems()
    for line in p:
        print("FAIL", line)
    print(f"{'FAILED' if p else 'OK'}: workflow run blocks checked")
    sys.exit(1 if p else 0)

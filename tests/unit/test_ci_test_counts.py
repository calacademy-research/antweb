#!/usr/bin/env python3
"""Every Java unit test step in tests.yaml that requires "OK (N tests)" names the number of tests its class has.

Twice on 2026-10-27 (PR #291) a class gained tests while its step still expected the old number, so the Tests check
failed (or, with a skipped step, hid a compile check). This compares each expected number with the class's
"public void test" methods before any Docker work.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
WORKFLOW = os.path.join(ROOT, ".github", "workflows", "tests.yaml")


def steps(text):
    """(class name, expected count) for every step: the last TestRunner class before each OK (N tests) check."""
    out, last = [], None
    for line in text.splitlines():
        for cls in re.findall(r"TestRunner\s+([\w.]+)", line):
            last = cls
        for n in re.findall(r"OK \((\d+) tests?\)", line):
            out.append((last, int(n)))
    return out


def count_tests(cls):
    path = os.path.join(ROOT, "test", *re.sub(r"^test\.", "", cls).split(".")) + ".java"
    with open(path, encoding="utf-8") as f:
        return len(re.findall(r"public\s+void\s+test\w*\s*\(", f.read()))


def main():
    with open(WORKFLOW, encoding="utf-8") as f:
        found = steps(f.read())
    assert len(found) >= 20, f"only {len(found)} counted steps found; did the workflow format change?"
    bad = []
    for cls, n in found:
        assert cls, "an OK (N tests) check with no TestRunner class before it"
        have = count_tests(cls)
        if have != n:
            bad.append(f"{cls}: the step expects OK ({n} tests), the class has {have}")
    assert not bad, "\n".join(bad)
    print(f"{len(found)} Java test steps expect the number of tests their class has.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

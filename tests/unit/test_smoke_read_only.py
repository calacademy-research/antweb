#!/usr/bin/env python3
"""The production smoke checks (tests/smoke/) only read: GET requests, no sign-in, no form data, no cookies.

Absolute rule (release gate, 2026-11-07): tests that write anything never run against production. Every file in
tests/smoke/ is read and this fails on any sign of a write or a sign-in: POST/PUT/DELETE, a request body, the
requests library or the e2e helpers (which can sign in), a cookie jar, curl or a subprocess. It also checks that the
two production workflows run only tests/smoke/ and never the e2e suite.
Run: python3 tests/unit/test_smoke_read_only.py   (also in .github/workflows/tests.yaml)
"""
import glob
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

FORBIDDEN = [
    (r"(?i)post\(", "a POST call"),
    (r"(?i)login|log_in|signin|sign_in", "a sign-in"),
    (r"(?i)password|passwd", "a password"),
    (r"(?i)\brequests\b", "the requests library (use urllib GET only)"),
    (r"(?i)method\s*=\s*['\"](?!GET['\"])", "an HTTP method other than GET"),
    (r"(?i)['\"](POST|PUT|DELETE|PATCH)['\"]", "a write method name"),
    (r"(?i)\.(put|delete|patch)\(", "a PUT/DELETE/PATCH call"),
    (r"urlopen\([^)\n]*\bdata\b", "a request body (urlopen data=)"),
    (r"Request\([^)\n]*\bdata\s*=", "a request body (Request data=)"),
    (r"(?i)urlencode", "form data"),
    (r"(?i)cookie", "cookies"),
    (r"(?i)http\.client|subprocess|os\.system|\bcurl\b", "a way around the GET-only helper"),
    (r"tests[/.]e2e|import antweb|from antweb", "the e2e helpers (they can sign in)"),
]


def problems():
    out = []
    files = sorted(glob.glob(os.path.join(ROOT, "tests", "smoke", "**", "*"), recursive=True))
    files = [f for f in files if os.path.isfile(f) and "__pycache__" not in f]
    if not files:
        return ["tests/smoke/ has no files"]
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding="utf-8").read()
        for pat, what in FORBIDDEN:
            for m in re.finditer(pat, text):
                line = text.count("\n", 0, m.start()) + 1
                out.append(f"{rel}:{line}: {what} ({m.group(0)!r})")
        for m in re.finditer(r"urllib\.request\.Request\(([^)]*)\)", text):
            if 'method="GET"' not in m.group(1):
                out.append(f"{rel}: a urllib Request without method=\"GET\"")
    for wf in (".github/workflows/prod-smoke.yaml", ".github/workflows/prod-morning.yaml"):
        p = os.path.join(ROOT, wf)
        if not os.path.exists(p):
            out.append(f"{wf} is missing")
            continue
        s = open(p, encoding="utf-8").read()
        if "tests/e2e" in s or "pytest" in s:
            out.append(f"{wf}: runs something other than tests/smoke/ against production")
        if "tests/smoke/prod_smoke.py" not in s:
            out.append(f"{wf}: does not run tests/smoke/prod_smoke.py")
    return out


def main():
    p = problems()
    for x in p:
        print("FAIL", x)
    if p:
        sys.exit(1)
    print("tests/smoke/ only reads (GET, no sign-in, no body, no cookies); production workflows run only tests/smoke/.")


if __name__ == "__main__":
    main()

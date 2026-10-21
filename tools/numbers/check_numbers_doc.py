#!/usr/bin/env python3
"""Checks docs/numbers.md, the list of every number AntWeb shows and the test that validates it.

Brian Fisher, 2026-10-20: every number shown on the site must be validated by a test, so new code can't undo
past progress. docs/numbers.md has one row per kind of number. This check (CI, .github/workflows/tests.yaml)
fails when:

  - a row's Status is not "yes", "partly" or "not yet";
  - a row marked yes or partly names no test, or a row marked "not yet" does not say "not yet" in its Test column;
  - a test it names does not exist:
      inv:<id>                CountInvariants check id (src/org/calacademy/antweb/util/CountInvariants.java, IDS)
      cc:<check>              CountCheck recount (a check name in src/org/calacademy/antweb/util/CountCheck.java)
      junit:<Class>#<method>  JUnit test method (test/**/<Class>.java)
      e2e:<file>::<function>  end-to-end test (tests/e2e/<file>)
      unit:<file>::<function> unit test (tests/unit/<file>)
  - a CountInvariants check id is named by no row (a check nobody knows what it covers);
  - the rows are not numbered 1, 2, 3, ... without gaps.

It prints the coverage (rows validated fully, partly, not yet). Usage: python3 tools/numbers/check_numbers_doc.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "numbers.md"
INVARIANTS = ROOT / "src/org/calacademy/antweb/util/CountInvariants.java"
COUNT_CHECK = ROOT / "src/org/calacademy/antweb/util/CountCheck.java"
STATUSES = {"yes", "partly", "not yet"}


def invariant_ids():
    text = INVARIANTS.read_text(encoding="utf-8")
    m = re.search(r"public static final String\[\] IDS = \{(.*?)\};", text, re.S)
    if not m:
        sys.exit("CountInvariants.IDS not found")
    return set(re.findall(r'"([^"]+)"', m.group(1)))


def count_check_names():
    text = COUNT_CHECK.read_text(encoding="utf-8")
    names = set(re.findall(r'new Mismatch\(place, "([^"]+)"', text))
    names |= set(re.findall(r'checkChildren\(place, "[^"]+", "[^"]+", "[^"]+", "([^"]+)"', text))
    return names


def test_exists(ref):
    kind, _, rest = ref.partition(":")
    if kind == "junit":
        cls, _, method = rest.partition("#")
        files = list((ROOT / "test").rglob(cls + ".java"))
        return bool(files) and any(re.search(r"void " + re.escape(method) + r"\(", f.read_text(encoding="utf-8")) for f in files)
    if kind in ("e2e", "unit"):
        name, _, func = rest.partition("::")
        f = ROOT / "tests" / kind / name
        return f.exists() and re.search(r"^def " + re.escape(func) + r"\(", f.read_text(encoding="utf-8"), re.M) is not None
    return False


def main():
    ids = invariant_ids()
    cc = count_check_names()
    errors = []
    rows = []
    used = set()
    for line in DOC.read_text(encoding="utf-8").splitlines():
        if not re.match(r"^\| \d+ \|", line):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != 6:
            errors.append(f"row with {len(cells)} columns (6 expected): {line[:80]}")
            continue
        number, _page, _what, _how, status, test = cells
        rows.append((int(number), status))
        if status not in STATUSES:
            errors.append(f"#{number}: status '{status}' (yes, partly or not yet)")
            continue
        if status == "not yet":
            if not test.startswith("not yet"):
                errors.append(f"#{number}: status not yet, but the Test column does not say 'not yet'")
            continue
        refs = [r.strip() for r in test.split(";") if r.strip()]
        if not refs:
            errors.append(f"#{number}: status {status} names no test")
        for ref in refs:
            if ref.startswith("inv:"):
                if ref[4:] not in ids:
                    errors.append(f"#{number}: {ref}: no such CountInvariants check")
                used.add(ref[4:])
            elif ref.startswith("cc:"):
                if ref[3:] not in cc:
                    errors.append(f"#{number}: {ref}: no such CountCheck check (known: {', '.join(sorted(cc))})")
            elif not test_exists(ref):
                errors.append(f"#{number}: {ref}: test not found")
    numbers = [n for n, _ in rows]
    if numbers != list(range(1, len(numbers) + 1)):
        errors.append("rows are not numbered 1, 2, 3, ... without gaps")
    for unused in sorted(ids - used):
        errors.append(f"CountInvariants check {unused} is named by no row of docs/numbers.md")
    yes = sum(1 for _, s in rows if s == "yes")
    partly = sum(1 for _, s in rows if s == "partly")
    print(f"docs/numbers.md: {len(rows)} kinds of numbers; validated {yes}, partly {partly}, not yet {len(rows) - yes - partly}")
    if errors:
        for e in errors:
            print("ERROR:", e)
        sys.exit(1)


if __name__ == "__main__":
    main()

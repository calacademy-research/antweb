"""The function catalog tool (tools/catalog/build_catalog.py), 2026-11-04:
- a page whose name has a version number (/apiV3.1.do) is matched in the tests;
- status manual (not tested end to end on purpose: sends email, restarts the app, production only, one-off admin
  repair) is a valid status; such rows are not counted as gaps and are listed with their reason.
Run: python3 tests/unit/test_catalog_tool.py
"""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools" / "catalog"))
import build_catalog as bc  # noqa: E402


def row(key, status, tests="", notes=""):
    return {"key": key, "kind": "sub-action", "url": key, "access": "admin", "hits": "<2", "e2e_tests": tests,
            "e2e_probe": "", "purpose": "p", "status": status, "notes": notes}


def test_key_with_version():
    m = bc.KEY_RE.search('r = _get("/apiV3.1.do")')
    assert m and m.group(1) == "apiV3.1", m and m.group(0)
    m = bc.KEY_RE.search('"/country.do?name=Madagascar"')
    assert m and m.group(1) == "country", m and m.group(0)


def test_manual_rows_listed_not_counted():
    assert "manual" in bc.STATUSES
    rows = [row("/utilData.do?action=set5", "manual", notes="production only"),
            row("/x.do?action=y", "keep"),
            row("/z.do?action=w", "keep", tests="test_z.py::test_w")]
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "gaps.md"
        bc.write_gaps(rows, p, "test")
        text = p.read_text()
    assert "1 of 3 rows have no functional e2e test." in text, text
    assert "## Not tested end to end on purpose (1)" in text and "production only" in text, text


if __name__ == "__main__":
    test_key_with_version()
    test_manual_rows_listed_not_counted()
    print("ok")

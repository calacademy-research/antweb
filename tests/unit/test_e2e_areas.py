"""The end-to-end tests' split (tests/e2e/areas.toml) covers every test file, names only real tests,
and chooses the expected tests for sample pull requests.

- every tests/e2e/test_*.py file is in [quick] or in at least one area, so no file is silently left
  out of quick runs (the full run is always the whole suite);
- every "file::test" entry names a test function that exists in that file (entries for files not
  in this branch yet are allowed: they are assigned ahead of the pull request that adds them);
- every area has paths and tests; path globs behave as documented;
- sample changes select the expected tests (upload code -> upload round trip, and so on).

Run: python3 tests/unit/test_e2e_areas.py   (also in .github/workflows/tests.yaml)
"""
import ast
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
E2E = os.path.join(ROOT, "tests", "e2e")
sys.path.insert(0, E2E)

import areas  # noqa: E402


def test_functions(file_name):
    tree = ast.parse(open(os.path.join(E2E, file_name), encoding="utf-8").read())
    return {n.name for n in ast.walk(tree) if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
            and n.name.startswith("test_")}


def all_entries(cfg):
    yield "quick", cfg.get("quick", {}).get("tests", [])
    yield "parallel", cfg.get("parallel", {}).get("tests", [])
    for name, area in areas.areas(cfg).items():
        yield "areas." + name, area.get("tests", [])


def main():
    cfg = areas.load()
    files = areas.test_files()
    assert len(files) > 40, files
    problems, notes = [], []

    # Every test file is assigned to the quick set or an area.
    assigned = set()
    for where, entries in all_entries(cfg):
        if where == "parallel":
            continue
        assigned |= {areas.split_entry(e)[0] for e in entries}
    for f in files:
        if f not in assigned:
            problems.append(f"{f} is in no area and not in [quick] (tests/e2e/areas.toml; see tests/e2e/README.md)")

    # Entries name real files and tests.
    for where, entries in all_entries(cfg):
        seen = set()
        for e in entries:
            if e in seen:
                problems.append(f"[{where}] lists {e} twice")
            seen.add(e)
            f, t = areas.split_entry(e)
            if not (f.startswith("test_") and f.endswith(".py")):
                problems.append(f"[{where}] {e}: not a test file name")
                continue
            if f not in files:
                notes.append(f"[{where}] {f} is not in this branch yet")
                continue
            if t is not None and t not in test_functions(f):
                problems.append(f"[{where}] {e}: no test function {t} in {f}")

    for name, area in areas.areas(cfg).items():
        if not area.get("paths") or not area.get("tests"):
            problems.append(f"[areas.{name}] needs paths and tests")
        for g in area.get("paths", []):
            if g.startswith("/") or "\\" in g:
                problems.append(f"[areas.{name}] {g}: paths are relative to the repository root, with /")

    # Globs.
    g = areas.glob_match
    assert g("src/org/calacademy/antweb/upload/SpecimenUpload.java", "src/org/calacademy/antweb/upload/**")
    assert g("web/curate/a/b/upload.jsp", "web/curate/**/upload*")
    assert g("web/curate/upload.jsp", "web/curate/**/upload*")
    assert g("web/index.jsp", "web/*.jsp")
    assert not g("web/curate/index.jsp", "web/*.jsp")
    assert not g("src/org/calacademy/antweb/util/X.java", "src/org/calacademy/antweb/*.java")

    # Sample pull requests.
    def sel(*paths):
        return areas.select(list(paths), cfg)

    quick = [t for t in sel("README.md")["targets"]]
    assert quick and not sel("README.md")["full"] and sel("README.md")["areas"] == [], sel("README.md")
    for t in quick:
        f = areas.split_entry(t.rsplit("/", 1)[-1])[0]
        assert f in files, t
    up = sel("src/org/calacademy/antweb/upload/SpecimenUpload.java")
    assert "upload" in up["areas"] and "tests/e2e/test_specimen_upload_roundtrip.py" in up["targets"], up
    assert "tests/e2e/test_specimen_upload_roundtrip.py" not in quick, "the upload round trip is not in every quick run"
    cur = sel("src/org/calacademy/antweb/curate/CurateAction.java")
    assert "curator" in cur["areas"] and "tests/e2e/test_login_form.py" in cur["targets"], cur
    cnt = sel("src/org/calacademy/antweb/util/CountCheck.java")
    assert "tests/e2e/test_count_check.py" in cnt["targets"], cnt
    own = sel("tests/e2e/test_name_updates.py")
    assert "tests/e2e/test_name_updates.py" in own["targets"], own
    assert sel("WEB-INF/lib/struts.jar")["full"]
    assert sel("WEB-INF/lib/struts.jar")["targets"] == ["tests/e2e"]
    assert len(set(up["targets"])) == len(up["targets"]), "duplicate targets"

    # The nightly health check is not in the quick set.
    assert not any("test_admin_staging.py" in t for t in quick)

    for n in sorted(set(notes)):
        print("note:", n)
    if problems:
        raise AssertionError("\n".join(problems))
    print(f"e2e areas OK: {len(files)} test files assigned; quick set {len(quick)} entries; "
          f"areas: {', '.join(areas.areas(cfg))}")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

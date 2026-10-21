"""How the end-to-end tests are split into a quick run and a full run (areas.toml).

Used by conftest.py (markers, the serial group for parallel runs), select_tests.py (which tests a
pull request's quick run includes) and tests/unit/test_e2e_areas.py (every test file is assigned).
Standard library only (Python 3.11+ tomllib).
"""
import re
import tomllib
from functools import lru_cache
from pathlib import Path

E2E_DIR = Path(__file__).resolve().parent
AREAS_FILE = E2E_DIR / "areas.toml"
E2E_REL = "tests/e2e"


@lru_cache(maxsize=None)
def load(path: str = str(AREAS_FILE)) -> dict:
    with open(path, "rb") as f:
        return tomllib.load(f)


@lru_cache(maxsize=None)
def _glob_regex(pattern: str) -> re.Pattern:
    """A path glob as a regex: ** spans folders, * and ? stay within one folder name."""
    out, i = [], 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("".join(out) + r"\Z")


def glob_match(path: str, pattern: str) -> bool:
    return bool(_glob_regex(pattern).match(path))


def split_entry(entry: str) -> tuple[str, str | None]:
    """'test_x.py' -> ('test_x.py', None); 'test_x.py::test_y' -> ('test_x.py', 'test_y')."""
    file, _, test = entry.partition("::")
    return file, (test or None)


def entry_matches(file_name: str, test_name: str, entries) -> bool:
    """Is the test (file name, function name without parameters) covered by one of the entries?"""
    for e in entries:
        f, t = split_entry(e)
        if f == file_name and (t is None or t == test_name):
            return True
    return False


def test_files() -> list[str]:
    return sorted(p.name for p in E2E_DIR.glob("test_*.py"))


def areas(cfg=None) -> dict:
    return (cfg or load()).get("areas", {})


def markers_for(file_name: str, test_name: str, cfg=None) -> list[str]:
    """quick, area_<name> and parallel markers of one test."""
    cfg = cfg or load()
    out = []
    if entry_matches(file_name, test_name, cfg.get("quick", {}).get("tests", [])):
        out.append("quick")
    for name, area in areas(cfg).items():
        if entry_matches(file_name, test_name, area.get("tests", [])):
            out.append("area_" + name)
    if entry_matches(file_name, test_name, cfg.get("parallel", {}).get("tests", [])):
        out.append("parallel")
    return out


def select(changed_paths, cfg=None) -> dict:
    """What a pull request's quick run includes, from the paths it changes.

    Returns {"full": bool, "targets": [pytest arguments], "areas": [...], "reasons": [...]}.
    targets are test files or file::test node ids under tests/e2e (no duplicates, file entries
    swallow node ids of the same file).
    """
    cfg = cfg or load()
    reasons, chosen_areas, entries = [], [], list(cfg.get("quick", {}).get("tests", []))
    full_paths = cfg.get("full_when", {}).get("paths", [])
    existing = set(test_files())
    for path in changed_paths:
        hit = next((g for g in full_paths if glob_match(path, g)), None)
        if hit:
            reasons.append(f"{path} matches {hit} (full run)")
            return {"full": True, "targets": [E2E_REL], "areas": [], "reasons": reasons}
    for path in changed_paths:
        # A changed or new test file runs itself.
        if path.startswith(E2E_REL + "/") and "/" not in path[len(E2E_REL) + 1:]:
            name = path[len(E2E_REL) + 1:]
            if name in existing:
                entries.append(name)
                reasons.append(f"{path}: the changed test file")
        for name, area in areas(cfg).items():
            g = next((g for g in area.get("paths", []) if glob_match(path, g)), None)
            if g:
                if name not in chosen_areas:
                    chosen_areas.append(name)
                    entries += area.get("tests", [])
                reasons.append(f"{path} matches {g} (area {name})")
    whole = {e for e in entries if "::" not in e}
    targets, seen = [], set()
    for e in entries:
        f, t = split_entry(e)
        if f not in existing:
            continue  # assigned ahead of the pull request that adds it
        if t is not None and f in whole:
            continue
        if e in seen:
            continue
        seen.add(e)
        targets.append(f"{E2E_REL}/{e}")
    if not chosen_areas:
        reasons.append("no area matched: the quick set only")
    return {"full": False, "targets": targets, "areas": chosen_areas, "reasons": reasons}

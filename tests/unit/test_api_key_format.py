"""The registration page's keys (ApiKeys.newKey(), Java) fit the pattern the API accepts (api_keys.KEY_RE, Python).
Review of #339, 2026-11-09: one side said 32 characters, the docs 40; a key the API calls malformed could never be
used, and its owner sees it only once."""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def test_a_registration_key_matches_the_api_pattern():
    java = (ROOT / "src/org/calacademy/antweb/util/ApiKeys.java").read_text()
    py = (ROOT / "api/v3.1/api_keys.py").read_text()
    n = int(re.search(r"KEY_RANDOM_CHARS\s*=\s*(\d+)", java).group(1))
    start = re.search(r'KEY_START\s*=\s*"([^"]+)"', java).group(1)
    pattern = re.search(r"KEY_RE = re\.compile\(r'([^']+)'\)", py).group(1)
    for ch in ("A", "z", "0", "-", "_"):
        assert re.match(pattern, start + ch * n), (pattern, n)
    assert not re.match(pattern, start + "A" * 5)


if __name__ == "__main__":
    test_a_registration_key_matches_the_api_pattern()
    print("ok")

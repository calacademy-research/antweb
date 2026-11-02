"""AntwebUtil.getReleaseNum() reads the first quoted text in web/documentation/releaseNum.jsp: it must be the release
number in .version (2026-11-02: the file gained a header comment, which must never contain a quote character)."""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def test_first_quoted_text_is_the_version():
    text = (ROOT / "web/documentation/releaseNum.jsp").read_text(encoding="utf-8")
    first = re.search(r'"([^"]*)"', text)
    assert first, "no quoted text in releaseNum.jsp"
    assert first.group(1) == (ROOT / ".version").read_text(encoding="utf-8").strip()


if __name__ == "__main__":
    test_first_quoted_text_is_the_version()
    print("OK")

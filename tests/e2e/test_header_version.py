"""The page header shows the version and, when known, the deployed commit.

Why: Brian asked (10-18) that both sites show the short git commit next to the version,
linked to the commit on GitHub, so it is clear exactly which code is live.

Read-only. The commit comes from etc/git-commit, which deploy.sh writes on the server; when
that file is missing only the version is shown, so the commit part is checked only when present.
"""
import re

from antweb import BASE_URL, TIMEOUT, new_session

VERSION_DIV = re.compile(r'<div id="version">(.*?)</div>', re.S)
COMMIT = re.compile(
    r'<span class="aw-commit">\s*&middot;\s*<a href="https://github\.com/calacademy-research/antweb/commit/([0-9a-f]{40})"[^>]*>([0-9a-f]{7})</a></span>'
)


def test_header_version_and_commit():
    r = new_session().get(BASE_URL + "/about.do", timeout=TIMEOUT)
    assert r.status_code == 200, r.status_code
    m = VERSION_DIV.search(r.text)
    assert m, "no version block in the page header"
    block = m.group(1)
    assert re.search(r">\s*(?:&nbsp;)*v\d+\.\d+(\.\d+)?</a>", block), block
    if "aw-commit" in block:
        c = COMMIT.search(block)
        assert c, "commit shown but not as '· <7-char hash>' linked to GitHub: " + block
        assert c.group(1).startswith(c.group(2)), c.groups()

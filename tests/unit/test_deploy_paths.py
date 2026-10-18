"""deploy.sh skips the AntWeb restart when only files the site never runs changed.
Checks that list (the grep -E pattern in .github/scripts/deploy.sh) with sample paths.

Run: python3 tests/unit/test_deploy_paths.py   (also in .github/workflows/tests.yaml)
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")

NO_RESTART = [".github/workflows/tests.yaml", "tests/e2e/test_api.py", "tools/traffic-by-action.sh",
              "doc/x.txt", "docs/a/b.md", "release-notes/9.2.0.md", "README.md", "CHANGELOG.md"]
RESTART = ["src/org/calacademy/antweb/Login.java", "web/index.jsp", "bin/clearDisk.py", "docker-compose.yml",
           "docker/antweb/Dockerfile", "api/v3.1/api.py", "WEB-INF/struts-config.xml", "etc/x.properties",
           "web/notes.md", "build.xml", "toolsx/a.sh"]


def main():
    src = open(os.path.join(ROOT, ".github", "scripts", "deploy.sh"), encoding="utf-8").read()
    m = re.search(r"grep -qvE '(\^\([^']*\))'", src)
    assert m, "no-restart pattern not found in deploy.sh"
    pattern = re.compile(m.group(1))
    for p in NO_RESTART:
        assert pattern.search(p), "should not restart for " + p
    for p in RESTART:
        assert not pattern.search(p), "should restart for " + p
    print("deploy.sh no-restart list OK:", m.group(1))


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

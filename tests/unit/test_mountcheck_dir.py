#!/usr/bin/env python3
"""The mount check (MountCheck.PROBE_DIR) writes into a folder the app already writes to on /mnt/antweb.

2026-10-24: the check wrote into web/log/upload, which does not exist on production, and sent a false "storage mount
problem" email every 6 hours. The folder must be one the image (docker/antweb/Dockerfile) links into the app for upload
reports and that every specimen upload writes to: web/log/specimen (checked on production and staging 2026-10-24:
present, user tomcat 2001 can write). Run: python3 tests/unit/test_mountcheck_dir.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main():
    java = open(os.path.join(ROOT, "src/org/calacademy/antweb/util/MountCheck.java"), encoding="utf-8").read()
    probe = re.search(r'PROBE_DIR\s*=\s*"([^"]+)"', java).group(1)
    docker = open(os.path.join(ROOT, "docker/antweb/Dockerfile"), encoding="utf-8").read()
    linked = re.findall(r"ln -s /mnt/antweb/(\S+) ", docker)
    problems = []
    if probe not in linked:
        problems.append(f"PROBE_DIR {probe} is not a folder the Dockerfile links from /mnt/antweb ({', '.join(linked)})")
    if probe in ("web/log/upload", "mountcheck"):
        problems.append(f"PROBE_DIR {probe} does not exist on production or staging (false alarms 2026-10-23/24)")
    if probe != "web/log/specimen":
        problems.append(f"PROBE_DIR {probe}: check it exists and user 2001 can write there on BOTH servers, then update this test")
    for p in problems:
        print("FAIL", p)
    print("FAILED" if problems else f"OK: mount check writes into /mnt/antweb/{probe}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

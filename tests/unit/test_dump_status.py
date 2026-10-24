"""bin/db-daily-dump.sh writes the status line the app's hourly mount check reads (MountCheck, 2026-10-24).

The app cannot read /mnt/backup (root-only rclone mount), so the host's daily dump writes
logs/web/db-dump-status.txt: "ok <time> <bytes> <file>" or "failed <time> <reason>". This runs the script in a
copy of the repository layout with a fake docker and no /mnt/backup, so it must write a "failed" line.

Run: python3 tests/unit/test_dump_status.py   (also in .github/workflows/tests.yaml)
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def main():
    if os.path.exists("/mnt/backup/db/daily"):
        print("skipped: /mnt/backup/db/daily exists here (a server), the test needs it absent")
        return
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "bin"))
        os.makedirs(os.path.join(d, "etc"))
        os.makedirs(os.path.join(d, "logs", "web"))
        os.makedirs(os.path.join(d, "fakebin"))
        shutil.copy(os.path.join(ROOT, "bin", "db-daily-dump.sh"), os.path.join(d, "bin"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=antweb\npassword=test\n")
        with open(os.path.join(d, "fakebin", "docker"), "w") as f:
            f.write("#!/bin/sh\nexit 1\n")
        os.chmod(os.path.join(d, "fakebin", "docker"), 0o755)
        env = dict(os.environ, PATH=os.path.join(d, "fakebin") + os.pathsep + os.environ["PATH"])
        subprocess.run(["bash", os.path.join(d, "bin", "db-daily-dump.sh")], env=env, capture_output=True, timeout=60)
        status = open(os.path.join(d, "logs", "web", "db-dump-status.txt")).read().strip()
        assert status.startswith("failed "), status
        assert "/mnt/backup/db/daily is missing" in status, status
        assert not os.path.exists(os.path.join(d, "logs", "web", "db-dump-status.txt.tmp"))
    print("db-daily-dump.sh status line OK:", status)


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAILED:", e)
        sys.exit(1)

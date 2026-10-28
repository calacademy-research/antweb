"""bin/db-daily-dump.sh writes the status line the app's hourly mount check reads (MountCheck, 2026-10-24).

The app cannot read /mnt/backup (root-only rclone mount), so the host's daily dump writes
logs/web/db-dump-status.txt: "ok <time> <bytes> <file>" or "failed <time> <reason>". This runs the script in a
copy of the repository layout with a fake docker and no /mnt/backup, so it must write a "failed" line.

2026-10-27: also with a backup folder (ANTWEB_DUMP_ROOT): a mysqldump that stops part way (exit 1, no "-- Dump
completed" line) must give "failed", write no dated file and delete no older dump; a complete dump gives "ok".

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
    cut_off_and_complete_dumps()


FAKE_DOCKER = """#!/bin/sh
echo "-- MySQL dump (first line, removed by the script)"
head -c 300000 /dev/urandom | base64
%s
exit %d
"""


def run_with_backup_folder(fake_docker):
    with tempfile.TemporaryDirectory() as d:
        for sub in ("bin", "etc", os.path.join("logs", "web"), "fakebin", "tmp", os.path.join("backup", "daily")):
            os.makedirs(os.path.join(d, sub))
        shutil.copy(os.path.join(ROOT, "bin", "db-daily-dump.sh"), os.path.join(d, "bin"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=antweb\npassword=test\n")
        with open(os.path.join(d, "fakebin", "docker"), "w") as f:
            f.write(fake_docker)
        os.chmod(os.path.join(d, "fakebin", "docker"), 0o755)
        old = os.path.join(d, "backup", "daily", "2000-01-01.sql.gz")
        with open(old, "w") as f:
            f.write("an old dump")
        os.utime(old, (946684800, 946684800))
        env = dict(os.environ, PATH=os.path.join(d, "fakebin") + os.pathsep + os.environ["PATH"],
                   ANTWEB_DUMP_ROOT=os.path.join(d, "backup"), ANTWEB_DUMP_TMP=os.path.join(d, "tmp"))
        subprocess.run(["bash", os.path.join(d, "bin", "db-daily-dump.sh")], env=env, capture_output=True, timeout=120)
        status = open(os.path.join(d, "logs", "web", "db-dump-status.txt")).read().strip()
        files = sorted(os.listdir(os.path.join(d, "backup", "daily")))
        current = os.path.exists(os.path.join(d, "backup", "ant-currentDump.sql.gz"))
        return status, files, current


def cut_off_and_complete_dumps():
    status, files, current = run_with_backup_folder(FAKE_DOCKER % ("", 1))
    assert status.startswith("failed "), status
    assert files == ["2000-01-01.sql.gz", "error.log"], files   # nothing new, the old dump kept
    assert not current, "a failed dump replaced ant-currentDump.sql.gz"
    status, files, current = run_with_backup_folder(FAKE_DOCKER % ("echo '-- Dump completed on 2026-10-27'", 0))
    assert status.startswith("ok "), status
    assert len([f for f in files if f.endswith(".sql.gz")]) == 1 and "2000-01-01.sql.gz" not in files, files
    assert current, "ant-currentDump.sql.gz not written"
    print("db-daily-dump.sh: a cut-off dump fails and deletes nothing; a complete one is kept: OK")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAILED:", e)
        sys.exit(1)

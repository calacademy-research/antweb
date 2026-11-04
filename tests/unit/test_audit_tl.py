#!/usr/bin/env python3
"""Audit fixes of 2026-11-02 (triage tl) in host scripts, the e2e workflow and source rules. No database, no Docker.

- bin/reboot_checker.sh (root on the host) never appends through a link planted in the tomcat-owned log folder.
- bin/db-daily-dump.sh replaces the status file instead of writing through a link there; the dump is written with
  umask 077 into a mktemp file (it was /tmp/<date>.sql.gz, world-readable).
- e2e.yaml: the checkout leaves no token in .git/config; the SSH key file is removed once the tunnel is up.
- DBUtil keeps no map that only grows; Subfamily's global query has its space before "where".
Run: python3 tests/unit/test_audit_tl.py   (also in .github/workflows/tests.yaml)
"""
import os
import shutil
import subprocess
import tempfile

import yaml

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def test_reboot_checker_never_writes_through_a_link():
    for link_the in ("file", "folder"):
        d = tempfile.mkdtemp()
        try:
            os.makedirs(os.path.join(d, "bin"))
            os.makedirs(os.path.join(d, "logs"))
            shutil.copy(os.path.join(ROOT, "bin", "reboot_checker.sh"), os.path.join(d, "bin"))
            sentinel = os.path.join(d, "sentinel")
            with open(sentinel, "w") as f:
                f.write("keep\n")
            if link_the == "file":
                os.makedirs(os.path.join(d, "logs", "detail"))
                os.symlink(sentinel, os.path.join(d, "logs", "detail", "rebootCheck.log"))
            else:
                other = os.path.join(d, "elsewhere")
                os.makedirs(other)
                os.symlink(other, os.path.join(d, "logs", "detail"))
            stubs = os.path.join(d, "stubs")
            os.makedirs(stubs)
            with open(os.path.join(stubs, "curl"), "w") as f:
                f.write('#!/bin/bash\ncase "$*" in *isBusy*) printf idle ;; *) printf "<html>odd</html>" ;; esac\n')
            with open(os.path.join(stubs, "docker"), "w") as f:
                f.write("#!/bin/bash\nexit 0\n")
            for n in ("curl", "docker"):
                os.chmod(os.path.join(stubs, n), 0o755)
            env = dict(os.environ, PATH=stubs + ":" + os.environ["PATH"])
            r = subprocess.run(["bash", os.path.join(d, "bin", "reboot_checker.sh")], capture_output=True, text=True,
                               env=env, timeout=30)
            assert open(sentinel).read() == "keep\n", "wrote through the linked log " + link_the
            if link_the == "folder":
                assert not os.listdir(os.path.join(d, "elsewhere")), "wrote into the linked folder"
            assert "is a link; not written" in r.stderr, r.stderr
        finally:
            shutil.rmtree(d)
    # A normal folder is still written.
    d = tempfile.mkdtemp()
    try:
        os.makedirs(os.path.join(d, "bin"))
        os.makedirs(os.path.join(d, "logs", "detail"))
        shutil.copy(os.path.join(ROOT, "bin", "reboot_checker.sh"), os.path.join(d, "bin"))
        stubs = os.path.join(d, "stubs")
        os.makedirs(stubs)
        with open(os.path.join(stubs, "curl"), "w") as f:
            f.write('#!/bin/bash\nprintf "<html>odd</html>"\n')
        os.chmod(os.path.join(stubs, "curl"), 0o755)
        subprocess.run(["bash", os.path.join(d, "bin", "reboot_checker.sh")], capture_output=True, text=True,
                       env=dict(os.environ, PATH=stubs + ":" + os.environ["PATH"]), timeout=30)
        assert "Unexpected response" in open(os.path.join(d, "logs", "detail", "rebootCheck.log")).read()
    finally:
        shutil.rmtree(d)
    print("reboot_checker.sh: no write through a linked log file or folder; a normal log is written")


def test_daily_dump_status_and_temp_file():
    if os.path.exists("/mnt/backup/db/daily"):
        print("skipped: /mnt/backup/db/daily exists here (a server)")
        return
    with tempfile.TemporaryDirectory() as d:
        for sub in ("bin", "etc", os.path.join("logs", "web"), "fakebin"):
            os.makedirs(os.path.join(d, sub))
        shutil.copy(os.path.join(ROOT, "bin", "db-daily-dump.sh"), os.path.join(d, "bin"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=antweb\npassword=test\n")
        with open(os.path.join(d, "fakebin", "docker"), "w") as f:
            f.write("#!/bin/sh\nexit 1\n")
        os.chmod(os.path.join(d, "fakebin", "docker"), 0o755)
        sentinel = os.path.join(d, "sentinel")
        with open(sentinel, "w") as f:
            f.write("keep\n")
        status = os.path.join(d, "logs", "web", "db-dump-status.txt")
        os.symlink(sentinel, status)
        env = dict(os.environ, PATH=os.path.join(d, "fakebin") + os.pathsep + os.environ["PATH"])
        subprocess.run(["bash", os.path.join(d, "bin", "db-daily-dump.sh")], env=env, capture_output=True, timeout=60)
        assert open(sentinel).read() == "keep\n", "wrote through the linked status file"
        assert not os.path.islink(status) and open(status).read().startswith("failed "), "status file replaced"
        assert oct(os.stat(status).st_mode & 0o777) == "0o644", oct(os.stat(status).st_mode)
        assert [n for n in os.listdir(os.path.join(d, "logs", "web")) if n.startswith(".db-dump-status")] == []
    src = read("bin/db-daily-dump.sh")
    assert "umask 077" in src and 'mktemp "${ANTWEB_DUMP_TMP:-/tmp}/daily-dump.XXXXXX"' in src
    assert '"$date".sql.gz' not in src.split("datedBackupFile=")[0].split("tempfile=")[-1]
    print("db-daily-dump.sh: status file replaced, not written through a link; private temporary dump")


def test_e2e_workflow_credentials():
    wf = yaml.safe_load(read(".github/workflows/e2e.yaml"))
    steps = wf["jobs"]["e2e"]["steps"]
    checkouts = [s for s in steps if str(s.get("uses", "")).startswith("actions/checkout")]
    assert checkouts and all(s.get("with", {}).get("persist-credentials") is False for s in checkouts), checkouts
    tunnel = next(s for s in steps if s.get("name") == "Tunnel to staging over SSH")["run"]
    assert tunnel.index("rm -f ~/.ssh/id_rsa") > tunnel.index("-f -N -L 443:127.0.0.1:443"), "key removed after the tunnel"
    record = next(s for s in steps if str(s.get("name", "")).startswith("Record baseline"))
    assert "x-access-token:${GH_TOKEN}" in record["run"] and record["env"]["GH_TOKEN"] == "${{ github.token }}"
    print("e2e.yaml: no token in .git/config, the key file removed after the tunnel, record step pushes with its own token")


def test_source_rules():
    dbutil = read("src/org/calacademy/antweb/util/DBUtil.java")
    assert "connectionMap.put" not in dbutil and "ConcurrentHashMap<NewProxyConnection, String>" not in dbutil
    assert 'String fetchChildrenClause = " where 1 = 1";' in read("src/org/calacademy/antweb/Subfamily.java")
    print("DBUtil keeps no ever-growing map; Subfamily's global query is valid SQL")


def main():
    test_reboot_checker_never_writes_through_a_link()
    test_daily_dump_status_and_temp_file()
    test_e2e_workflow_credentials()
    test_source_rules()
    print("OK")


if __name__ == "__main__":
    main()

"""bin/clearDisk.py (run as root by admin.sh every night): it deletes old Tomcat logs under
/usr/local/tomcat/logs, but must never walk logs/web (the app's web/log, which tomcat owns and
logrotate manages) and never follow or delete symbolic links.

Run: python3 tests/unit/test_clear_disk.py   (also in .github/workflows/tests.yaml)
"""
import datetime as DT
import importlib.util
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "..", "..", "bin", "clearDisk.py")


def load():
    spec = importlib.util.spec_from_file_location("clearDisk", SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)   # main() runs only as a script
    return mod


def touch(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write("x")


def main():
    cd = load()
    old = str(DT.date.today() - DT.timedelta(days=10))
    with tempfile.TemporaryDirectory() as root:
        logs = os.path.join(root, "logs")
        outside = os.path.join(root, "outside")
        delete = [
            os.path.join(logs, f"catalina.{old}.log"),
            os.path.join(logs, f"localhost_access_log.{old}.txt"),
            os.path.join(logs, "other", "app.log.5"),
        ]
        keep = [
            os.path.join(logs, "catalina.log"),
            os.path.join(logs, "web", f"srfExceptions{old} 10:00:00.txt"),
            os.path.join(logs, "web", "compute.log.3"),
            os.path.join(logs, "web", "bak", f"x.{old}.log"),
            os.path.join(outside, f"dump.{old}.log"),
            os.path.join(outside, "db.log.4"),
        ]
        for p in delete + keep:
            touch(p)
        # Links into another folder: neither followed nor deleted.
        os.symlink(outside, os.path.join(logs, "linkdir"))
        os.symlink(os.path.join(outside, "db.log.4"), os.path.join(logs, "link.log.7"))

        cd.findFiles(logs)
        cd.getSize(logs)

        for p in delete:
            assert not os.path.exists(p), "not deleted: " + p
        for p in keep:
            assert os.path.exists(p), "deleted: " + p
        assert os.path.islink(os.path.join(logs, "linkdir")), "link to a folder removed"
        assert os.path.islink(os.path.join(logs, "link.log.7")), "link to a file removed"
        walked = {os.path.relpath(os.path.join(d, n), logs) for d, n in cd.walkLogs(logs)}
        assert not any(w.startswith("web" + os.sep) for w in walked), walked
    print("clearDisk.py OK")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

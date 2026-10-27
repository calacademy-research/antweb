"""deploy.sh skips the AntWeb restart when only files the site never runs changed.
Checks that list (the grep -E pattern in .github/scripts/deploy.sh) with sample paths.

deploy.sh waits while AntWeb is busy (util.do?action=isBusy) before every step that restarts it
(2026-10-26: a deploy restarted AntWeb in the middle of a curator image upload). Checks the order
in the script and runs wait_until_idle with a stand-in curl. bin/reboot_checker.sh does not restart
during an upload either, but still restarts a stuck nightly run.

Run: python3 tests/unit/test_deploy_paths.py   (also in .github/workflows/tests.yaml)
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

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
    # One deploy at a time per server (two overlapping production deploys failed on 2026-10-23).
    for wf, group in (("deploy.yaml", "deploy-production"), ("deploy-staging.yaml", "deploy-staging")):
        text = open(os.path.join(ROOT, ".github", "workflows", wf), encoding="utf-8").read()
        assert re.search(r"(?m)^concurrency:\n  group: " + group + r"\n  cancel-in-progress: false$", text), \
            wf + ": needs a top-level concurrency group " + group + " that does not cancel a running deploy"
    print("deploy workflows: one deploy at a time OK")
    check_wait_order(src)
    check_wait_behaviour(src)
    check_reboot_checker()


def check_wait_order(src):
    assert "util.do?action=isBusy" in src and "127.0.0.1:8081" in src, "deploy.sh must ask isBusy on the local port"
    body = src.split("\nbusy_waited=0\n", 1)
    assert len(body) == 2, "wait_until_idle must be followed by busy_waited=0"
    main = body[1]
    no_restart_exit = main.index("antweb not restarted.")
    calls = [m.start() for m in re.finditer(r"(?m)^\s*wait_until_idle\b", main)]
    assert calls and min(calls) > no_restart_exit, "a deploy that does not restart AntWeb must not wait"
    # Every step that stops, reloads or restarts AntWeb comes after a wait.
    for step in ('migrate_web_log "$dc"', "$dc up -d --build api antweb", 'echo "Removing stale jars..."',
                 "compose restart antweb\n  sleep 5", 'echo "Restarting antweb container..."'):
        at = main.index(step)
        assert any(c < at for c in calls), "no wait_until_idle before: " + step
    # The final restart has a wait after ant deploy (an upload may have started during the build).
    ant = main.index("ant deploy < /dev/null\nfi")
    final = main.index('echo "Restarting antweb container..."')
    assert any(ant < c < final for c in calls), "ask again between ant deploy and the restart"
    print("deploy.sh waits on isBusy before every restart OK")


def run_wait(src, answers, max_seconds=3):
    """Runs wait_until_idle with a curl that prints the given answers in turn. Returns (rc, output)."""
    func = src[src.index("wait_until_idle() {"):src.index("\nbusy_waited=0\n")]
    d = tempfile.mkdtemp()
    try:
        with open(os.path.join(d, "answers"), "w") as f:
            f.write("\n".join(answers) + "\n")
        curl = os.path.join(d, "curl")
        with open(curl, "w") as f:
            f.write("#!/bin/bash\nn=$(cat %s/n 2>/dev/null || echo 0); n=$((n+1)); echo $n > %s/n\n"
                    "line=$(sed -n \"${n}p\" %s/answers); [ -z \"$line\" ] && line=$(tail -1 %s/answers)\n"
                    "[ \"$line\" = DOWN ] && exit 7\nprintf %%s \"$line\"\n" % (d, d, d, d))
        os.chmod(curl, 0o755)
        script = "set -euo pipefail\n" + func + "\nbusy_waited=0\nwait_until_idle\necho CALLS=$(cat %s/n)\n" % d
        env = dict(os.environ, PATH=d + ":" + os.environ["PATH"], BUSY_POLL_SECONDS="1",
                   BUSY_MAX_SECONDS=str(max_seconds))
        r = subprocess.run(["bash", "-c", script], capture_output=True, text=True, env=env, timeout=30)
        return r.returncode, r.stdout + r.stderr
    finally:
        shutil.rmtree(d)


def check_wait_behaviour(src):
    rc, out = run_wait(src, ["idle"])
    assert rc == 0 and "CALLS=1" in out and "waiting" not in out, out
    rc, out = run_wait(src, ["busy: image upload", "busy: image upload", "idle"])
    assert rc == 0 and "CALLS=3" in out and out.count("waiting before the restart") == 2 and "idle now" in out, out
    rc, out = run_wait(src, ["busy: specimen upload"], max_seconds=2)
    assert rc == 0 and "CALLS=3" in out and "::warning::" in out and "restarting anyway" in out, out
    rc, out = run_wait(src, ["DOWN"])
    assert rc == 0 and "CALLS=1" in out and "not waiting" in out, out
    rc, out = run_wait(src, ["<html>action not found:isBusy</html>"])
    assert rc == 0 and "CALLS=1" in out and "not waiting" in out, out
    print("wait_until_idle: waits while busy, gives up loudly after the limit, goes ahead when no answer OK")


def check_reboot_checker():
    """bin/reboot_checker.sh with a stand-in curl and docker: no restart during an upload."""
    cases = [  # isRestart answer, isBusy answer, restarted?
        ("<b>true</b>", "idle", True),
        ("<b>true</b>", "busy: image upload", False),
        ("case#123", "busy: specimen upload", False),
        ("case#123", "idle", True),
        ("<b>true</b>", "busy: data job, nightly run", True),   # a stuck set 1 is still restarted
        ("<b>true</b>", "", True),                             # older code without isBusy
        ("<b>false</b>", "busy: image upload", False),
    ]
    for restart, busy, expected in cases:
        d = tempfile.mkdtemp()
        try:
            os.makedirs(os.path.join(d, "bin"))
            os.makedirs(os.path.join(d, "logs", "detail"))
            shutil.copy(os.path.join(ROOT, "bin", "reboot_checker.sh"), os.path.join(d, "bin"))
            stubs = os.path.join(d, "stubs")
            os.makedirs(stubs)
            with open(os.path.join(stubs, "curl"), "w") as f:
                f.write('#!/bin/bash\ncase "$*" in *isBusy*) printf %%s %s ;; *) printf %%s %s ;; esac\n'
                        % (shlex_quote(busy), shlex_quote(restart)))
            with open(os.path.join(stubs, "docker"), "w") as f:
                f.write('#!/bin/bash\necho "$*" >> %s/docker-calls\n' % d)
            for n in ("curl", "docker"):
                os.chmod(os.path.join(stubs, n), 0o755)
            env = dict(os.environ, PATH=stubs + ":" + os.environ["PATH"])
            subprocess.run(["bash", os.path.join(d, "bin", "reboot_checker.sh")], capture_output=True,
                           text=True, env=env, timeout=30)
            restarted = os.path.exists(os.path.join(d, "docker-calls"))
            assert restarted == expected, "reboot_checker isRestart=%r isBusy=%r: restarted=%s" % (restart, busy, restarted)
        finally:
            shutil.rmtree(d)
    print("reboot_checker.sh: no restart during an upload, stuck nightly still restarted OK")


def shlex_quote(s):
    return "'" + s.replace("'", "'\\''") + "'"


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

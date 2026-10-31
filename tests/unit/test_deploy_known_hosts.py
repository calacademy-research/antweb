"""Deploys check the server's host key (2026-10-27, Brian Fisher: yes).

Both deploy workflows and the end-to-end tunnel to staging (2026-10-30) compare the key the server answers with against the public host keys pinned in
.github/known_hosts, and ssh runs with StrictHostKeyChecking=yes. Before, ssh-keyscan trusted any key that answered.
Run: python3 tests/unit/test_deploy_known_hosts.py
"""
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(p):
    with open(os.path.join(ROOT, p)) as f:
        return f.read()


def main():
    pinned = read(".github/known_hosts").split("\n")
    for ip in ("165.232.137.86", "64.225.91.165", "165.232.136.170"):
        assert any(l.startswith(ip + " ssh-ed25519 ") for l in pinned), "no ed25519 key pinned for " + ip
    for wf in (".github/workflows/deploy.yaml", ".github/workflows/deploy-staging.yaml", ".github/workflows/e2e.yaml"):
        s = read(wf)
        assert "ssh-keyscan -H" not in s, wf + ": still trusts any key"
        assert ".github/known_hosts" in s and "comm -12" in s, wf + ": does not compare with the pinned keys"
        assert "StrictHostKeyChecking=yes" in s, wf + ": ssh does not check the host key"
    # Every workflow: no ssh-keyscan output reaches known_hosts unless compared with the pinned keys.
    wfdir = os.path.join(ROOT, ".github", "workflows")
    for name in sorted(os.listdir(wfdir)):
        s = read(os.path.join(".github", "workflows", name))
        if "ssh-keyscan" in s:
            assert "comm -12" in s and ".github/known_hosts" in s, name + ": ssh-keyscan without the pinned-key check"
            assert ">> ~/.ssh/known_hosts" not in s, name + ": appends scanned keys to known_hosts"
    print("Deploys and the end-to-end tunnel check the pinned host keys.")


if __name__ == "__main__":
    main()

"""Log rotation covers the data logs, and nothing writes a whole message page to messages.txt (2026-10-22).

Why: messages.txt (data logs: host ./logbak, container /usr/local/antweb/log) reached 11 GB in 11
days on staging and 1.8 GB on production. logrotate ran, but it records a file it has not seen before
with the date of that run and rotates it a week later ("weekly"); a big file must rotate sooner.

Checks:
- docker/antweb/logrotate.conf has maxsize (at most 100M), compress without delaycompress, a bounded
  rotate count, and a glob for the folder messages.txt is written to (AntwebProps data root + "log/",
  the container side of the ./logbak volume in docker-compose.yml);
- the crontab runs logrotate daily with that conf, as tomcat;
- the four message pages call LogMgr.logMessagePage and none writes messages.txt directly.

Run: python3 tests/unit/test_log_rotation.py   (also in .github/workflows/tests.yaml)
"""
import fnmatch
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def parse_logrotate(text):
    """[(globs, {directive: value})] of a logrotate conf (blocks only)."""
    text = re.sub(r"#.*", "", text)
    blocks = []
    for m in re.finditer(r"((?:[^{}]+?))\{([^}]*)\}", text, re.S):
        globs = m.group(1).split()
        opts = {}
        for line in m.group(2).splitlines():
            parts = line.split()
            if parts:
                opts[parts[0]] = " ".join(parts[1:])
        blocks.append((globs, opts))
    return blocks


def size_bytes(v):
    m = re.fullmatch(r"(\d+)([kMG]?)", v)
    assert m, v
    return int(m.group(1)) * {"": 1, "k": 1024, "M": 1024 ** 2, "G": 1024 ** 3}[m.group(2)]


def volume_target(compose, host_dir):
    """The container path docker-compose.yml mounts host_dir (relative, e.g. ./logbak) on."""
    m = re.search(r"-\s*" + re.escape(host_dir) + r"/?:(\S+?)/?(?::r[ow])?\s*$", compose, re.M)
    assert m, f"{host_dir} not mounted in docker-compose.yml"
    return m.group(1).rstrip("/")


def main():
    problems = []

    blocks = parse_logrotate(read("docker/antweb/logrotate.conf"))
    assert blocks, "no block in logrotate.conf"

    # The data log folder: where LogMgr.appendDataLog writes (data root + "log/") and the ./logbak volume.
    compose = read("docker-compose.yml")
    data_log_dir = volume_target(compose, "./logbak")
    if data_log_dir != "/usr/local/antweb/log":
        problems.append(f"./logbak is mounted on {data_log_dir}, expected /usr/local/antweb/log")
    logmgr = read("src/org/calacademy/antweb/util/LogMgr.java")
    if 'AntwebProps.getDataRoot()+ "log/"' not in logmgr and 'AntwebProps.getDataRoot() + "log/"' not in logmgr:
        problems.append("LogMgr.appendDataLog no longer writes to the data root's log/ folder")
    # /data/antweb is a link to /usr/local/antweb (Dockerfile), the data root in the image.
    if "ln -s /usr/local/antweb /data/antweb" not in read("docker/antweb/Dockerfile"):
        problems.append("Dockerfile no longer links /data/antweb to /usr/local/antweb")

    for name in ["messages.txt", "longRequest.log", "cCheck.log", "emails.log", "logrotate.log"]:
        path = data_log_dir + "/" + name
        hits = [(g, o) for g, o in blocks if any(fnmatch.fnmatch(path, x) for x in g)]
        if not hits:
            problems.append(f"{path} is not rotated by logrotate.conf")
            continue
        opts = hits[0][1]
        if "maxsize" not in opts:
            problems.append(f"{path}: no maxsize (a big file waits for the weekly rotation)")
        elif size_bytes(opts["maxsize"]) > 100 * 1024 ** 2:
            problems.append(f"{path}: maxsize {opts['maxsize']} is over 100M")
        if "compress" not in opts:
            problems.append(f"{path}: rotated copies are not compressed")
        if "delaycompress" in opts:
            problems.append(f"{path}: delaycompress keeps the last (possibly huge) copy plain")
        if not opts.get("rotate", "").isdigit() or int(opts["rotate"]) > 52:
            problems.append(f"{path}: rotate must be a count of at most 52, is {opts.get('rotate')!r}")
        if "nocreate" not in opts:
            problems.append(f"{path}: nocreate missing (AntWeb creates its logs itself)")

    # web/log is rotated too.
    if not any(fnmatch.fnmatch("/usr/local/antweb/web/log/compute.log", x) for g, _ in blocks for x in g):
        problems.append("web/log/compute.log is not rotated")

    # Daily, as tomcat, with the state file in the data log folder (kept across rebuilds).
    cron = read("docker/antweb/crontab.txt")
    line = next((l for l in cron.splitlines() if "logrotate" in l and not l.startswith("#")), "")
    if not re.match(r"\d+ \d+ \* \* \* root setpriv --reuid=tomcat ", line):
        problems.append(f"logrotate is not run daily as tomcat: {line!r}")
    if "/etc/antweb-logrotate.conf" not in line or f"--state {data_log_dir}/.logrotate.state" not in line:
        problems.append(f"logrotate cron line does not use the conf and the state file in {data_log_dir}: {line!r}")
    if "COPY --chmod=0644 docker/antweb/logrotate.conf /etc/antweb-logrotate.conf" not in read("docker/antweb/Dockerfile"):
        problems.append("Dockerfile no longer installs logrotate.conf as /etc/antweb-logrotate.conf")

    # Message pages write a short record only.
    for jsp in ["web/common/message-body.jsp", "web/common/justMessage.jsp",
                "web/common/unboldMessage-body.jsp", "web/curate/adminHtmlMessage.jsp"]:
        text = read(jsp)
        if "LogMgr.logMessagePage(request, message)" not in text:
            problems.append(f"{jsp} does not use LogMgr.logMessagePage")
    for base, _, files in os.walk(ROOT):
        if "/.git" in base or "/node_modules" in base or base.startswith(os.path.join(ROOT, "test")):
            continue
        for f in files:
            if f.endswith((".java", ".jsp")):
                p = os.path.join(base, f)
                with open(p, encoding="utf-8", errors="replace") as fh:
                    if '"messages.txt"' in fh.read() and not p.endswith(("MessagePageLog.java", "LogMgr.java", "LogMgrAction.java")):
                        problems.append(f"{os.path.relpath(p, ROOT)} writes messages.txt directly")

    if problems:
        print("FAIL")
        for p in problems:
            print(" -", p)
        sys.exit(1)
    print("OK: logrotate covers", data_log_dir, "with maxsize; message pages write short records")


if __name__ == "__main__":
    main()

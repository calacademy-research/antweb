"""No PHP on AntWeb (2026-10-21, PHP2Shell CVE-2026-109618/109619): the server image installs no PHP
and the repository holds no PHP files. AntWeb is Java (Tomcat) and Python (API); nothing runs PHP."""
import pathlib, re, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[2]
problems = []
docker = (root / "docker/antweb/Dockerfile").read_text()
if re.search(r"(^|\s)php[\w.-]*(\s|\\|$)", docker, re.M):
    problems.append("docker/antweb/Dockerfile installs PHP")
files = subprocess.run(["git", "ls-files"], cwd=root, capture_output=True, text=True, check=True).stdout.split()
php = [f for f in files if re.search(r"\.(php\d?|phtml)$", f, re.I)]
if php:
    problems.append("PHP files in the repository: " + ", ".join(php[:10]))
if problems:
    print("\n".join(problems)); sys.exit(1)
print("no PHP: OK")

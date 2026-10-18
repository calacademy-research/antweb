"""Reads the direct-JSP allow-list from the Caddyfiles (no third-party imports, so CI can run it).

The allow-list is the `not path` lines of the @jsp_not_allowed matcher. Production
(docker/caddy/Caddyfile) and staging (docker/caddy/Caddyfile.common) each have a copy;
`python3 tests/e2e/caddy_lists.py` fails when the two differ.
"""
import re
import sys
from pathlib import Path

CADDY_DIR = Path(__file__).resolve().parents[2] / "docker" / "caddy"
PROD = CADDY_DIR / "Caddyfile"
STAGING = CADDY_DIR / "Caddyfile.common"


def jsp_allow_list(caddyfile: Path) -> list[str]:
    text = caddyfile.read_text()
    m = re.search(r"@jsp_not_allowed\s*\{(.*?)\n\s*\}", text, re.S)
    if not m:
        raise ValueError(f"{caddyfile}: no @jsp_not_allowed matcher")
    paths = []
    for line in m.group(1).splitlines():
        line = line.split("#", 1)[0].strip()
        if line.startswith("not path "):
            paths += line[len("not path "):].split()
    return paths


def main() -> int:
    prod, staging = jsp_allow_list(PROD), jsp_allow_list(STAGING)
    if sorted(prod) != sorted(staging):
        print("JSP allow-list differs between Caddyfile and Caddyfile.common:")
        print("  only in Caddyfile:", sorted(set(prod) - set(staging)))
        print("  only in Caddyfile.common:", sorted(set(staging) - set(prod)))
        return 1
    print(f"JSP allow-list OK ({len(prod)} entries, same in Caddyfile and Caddyfile.common)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

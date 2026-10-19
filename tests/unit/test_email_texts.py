"""No email AntWeb sends tells the reader to ignore it (2026-10-19).

Scans every Java, JSP and properties file under src/, web/ and WEB-INF/ for text such as
"ignore this email", "you can ignore" or "disregard this". The rendered email texts are also
checked by test/org/calacademy/antweb/security/EmailLinkTest.java.

Run: python3 tests/unit/test_email_texts.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
PATTERN = re.compile(r"ignore\s+(this|the)\s+(e-?mail|message)|you\s+can\s+(safely\s+)?ignore|"
                     r"disregard\s+(this|the)", re.I)
EXTENSIONS = (".java", ".jsp", ".jspf", ".properties", ".html", ".txt")


def main():
    found = []
    scanned = 0
    for top in ("src", "web", "WEB-INF", "etc"):
        for dirpath, _dirs, files in os.walk(os.path.join(ROOT, top)):
            for name in files:
                if not name.endswith(EXTENSIONS):
                    continue
                path = os.path.join(dirpath, name)
                scanned += 1
                with open(path, encoding="utf-8", errors="replace") as f:
                    for no, line in enumerate(f, 1):
                        if PATTERN.search(line):
                            found.append(f"{os.path.relpath(path, ROOT)}:{no}: {line.strip()}")
    assert scanned > 500, f"only {scanned} files scanned"
    assert not found, "Email text tells the reader to ignore it:\n" + "\n".join(found)
    print(f"No 'ignore this email' text in {scanned} files.")


if __name__ == "__main__":
    main()

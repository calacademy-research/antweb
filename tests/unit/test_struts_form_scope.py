"""Actions with the shared dummyForm keep it per request (2026-11-03). Struts keeps a form in the session unless the
mapping says scope="request", so an action= of one request carried over to the next: a plain serverStatus.do showed the
toggle's confirm step again, list.do with no action showed the previous list, and a later adminAlert.do POST without
an action could run an earlier "remove all". Runs without a server: python3 tests/unit/test_struts_form_scope.py
"""
import os
import re

CONFIG = os.path.join(os.path.dirname(__file__), "..", "..", "WEB-INF", "struts-config.xml")


def main():
    text = open(CONFIG, encoding="utf-8").read()
    wrong = []
    for tag in re.findall(r"<action\s[^>]*?>", text, re.S):
        if re.search(r'name\s*=\s*"dummyForm"', tag) and not re.search(r'scope\s*=\s*"request"', tag):
            wrong.append(re.search(r'path\s*=\s*"([^"]+)"', tag).group(1))
    assert not wrong, "dummyForm kept in the session (add scope=\"request\"): " + ", ".join(wrong)
    print("struts dummyForm scope OK")


if __name__ == "__main__":
    main()

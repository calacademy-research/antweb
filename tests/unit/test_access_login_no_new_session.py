#!/usr/bin/env python3
"""LoginMgr.getAccessLogin reads the session with getSession(false): it never creates one.

2026-10-27 (PR #291 staging runs): when a login's session had been ended (password reset or a newer login of the same
account), getAccessLogin invalidated it; the header's next call made a new session after part of the home page was
sent, Tomcat refused ("Cannot create a session after the response has been committed") and the page was cut off.
"""
import os
import re
import sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "org", "calacademy", "antweb",
                   "util", "LoginMgr.java")


def main():
    with open(SRC, encoding="utf-8") as f:
        java = f.read()
    m = re.search(r"public static Login getAccessLogin\(HttpServletRequest request\) \{(.*?)\n    \}", java, re.S)
    assert m, "getAccessLogin not found"
    body = re.sub(r"//[^\n]*", "", m.group(1))
    assert "request.getSession(false)" in body, "getAccessLogin must read the session with getSession(false)"
    assert "request.getSession()" not in body, "getAccessLogin must not create a session"
    assert "request.getSession(true)" in body.split("invalidate()", 1)[-1], \
        "after ending a stale session getAccessLogin starts the replacement at once"
    with open(os.path.join(os.path.dirname(SRC), "RememberMeFilter.java"), encoding="utf-8") as f:
        flt = f.read()
    assert flt.find("LoginMgr.getAccessLogin(request)") != -1 and flt.find("LoginMgr.getAccessLogin(request)") < flt.find("chain.doFilter"), \
        "RememberMeFilter (the home page and .jsp pages) checks the login before the page starts"
    print("getAccessLogin never creates a session on a plain read, replaces an ended one at once; the home page checks"
          " it before the page starts.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

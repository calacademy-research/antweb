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
    assert "invalidate()" not in body, "getAccessLogin logs an ended session out; it never invalidates it mid-request"
    assert 'session.removeAttribute("accessLogin")' in body, "getAccessLogin removes the login of an ended session"
    with open(os.path.join(os.path.dirname(SRC), "RememberMeFilter.java"), encoding="utf-8") as f:
        flt = f.read()
    assert -1 < flt.find("request.getSession(true)") < flt.find("chain.doFilter"), \
        "RememberMeFilter (the home page and .jsp pages) makes the session before the page starts"
    assert flt.find("LoginMgr.getAccessLogin(request)") != -1 and flt.find("LoginMgr.getAccessLogin(request)") < flt.find("chain.doFilter"), \
        "RememberMeFilter (the home page and .jsp pages) checks the login before the page starts"
    links = os.path.join(os.path.dirname(SRC), *[".."] * 5, "tests", "e2e", "test_upload_report_links.py")
    with open(links, encoding="utf-8") as f:
        assert '"/logout.do"' in f.read(), "the link checks must never follow Log out"
    print("getAccessLogin never creates or invalidates a session; the home page has its session before it starts;"
          " link checks never log out.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

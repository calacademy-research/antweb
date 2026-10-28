#!/usr/bin/env python3
"""Admin Alert Manager (Brian Fisher, 2026-10-28): the page had three red X pictures, one of them only an illustration in
the help line, so clicking it did nothing; and marking an alert as dealt with rewrote its date (admin_alerts.created
has ON UPDATE CURRENT_TIMESTAMP). Buttons now say what they do, and the date is kept."""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def main():
    with open(os.path.join(ROOT, "web", "util", "adminAlert.jsp"), encoding="utf-8") as f:
        jsp = re.sub(r"<%--.*?--%>", "", f.read(), flags=re.S)
    assert "redX.png" not in jsp, "a red X picture is back on the Admin Alert page"
    assert '"Dealt with"' in jsp and '"All dealt with"' in jsp, "the buttons must say what they do"
    with open(os.path.join(ROOT, "src", "org", "calacademy", "antweb", "util", "AdminAlertMgr.java"), encoding="utf-8") as f:
        java = f.read()
    for m in re.findall(r'"update admin_alerts set acknowledged = 1[^"]*"', java):
        assert "created = created" in m, f"acknowledging must keep the alert's date: {m}"
    print("Admin Alert page: labelled buttons, no picture-only X; dealt-with alerts keep their date.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

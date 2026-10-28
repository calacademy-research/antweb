#!/usr/bin/env python3
"""The admin check number and the * note under every list say what they are (Brian Fisher, 2026-10-27).

"Subtaxon total: 1778" became "Total of the Reunion column: 1,778 specimens (administrators only; ...)"; the tests read
the number from data-subtaxon-total. "Counts include specimens of all statuses" became "Numbers marked * count every
specimen filed under the name, whatever the name's status (...)".
"""
import os
import sys

JSP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "web", "taxonReportBody.jsp")


def main():
    with open(JSP, encoding="utf-8") as f:
        jsp = f.read()
    assert "<br>Subtaxon total:" not in jsp, "the old \"Subtaxon total\" line is still shown"
    assert 'data-subtaxon-total="<%= subtaxonTotal %>"' in jsp, "the tests' data-subtaxon-total attribute is missing"
    assert "Total of the " in jsp and "administrators only;" in jsp, "the plain wording of the column total is missing"
    assert "Numbers marked * count every " in jsp, "the plain wording of the * note is missing"
    assert '"Counts include " + nextPluralRank + " of all statuses";' not in jsp, "the old * note is still built"
    print("List notes: the column total and the * note are in plain words.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

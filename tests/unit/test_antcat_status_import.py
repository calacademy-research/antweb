#!/usr/bin/env python3
"""The daily AntCat import reads the status column through Status.fromAntcat before it stores or uses it (AntCat #1617,
2026-11-14: "unjustified emendation" is filed as a synonym; the mapping itself is tested by upload.AntcatStatusTest).
Review of #359: a refactor that reads the status without the mapping would store the new status again, with no test
failing. This reads SpeciesListUpload.java and checks the mapping comes before the status is kept."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main():
    src = open(os.path.join(ROOT, "src/org/calacademy/antweb/upload/SpeciesListUpload.java"), encoding="utf-8").read()
    m = re.search(r"if \(index == statusHeader\) \{(.*?)\}", src, re.S)
    assert m, "the import no longer has the block that reads the status column"
    block = m.group(1)
    assert "element = Status.fromAntcat(element);" in block, "the status column is not read through Status.fromAntcat"
    assert block.index("Status.fromAntcat") < block.index("status = element;"), "the status is kept before the mapping"
    assert len(re.findall(r"index == statusHeader", src)) == 1, "the status column is read in a second place"
    print("OK: the AntCat import reads the status through Status.fromAntcat")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

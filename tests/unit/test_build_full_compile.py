#!/usr/bin/env python3
"""ant compile (run by every deploy) deletes the compiled classes before javac, so every source is compiled again.

2026-10-27, PR #291: TaxonSet.globalImageCountColumn changed from static to an instance method. javac recompiled only
the changed files; GeolocaleTaxon, BioregionTaxon and the other callers kept classes compiled against the static
method, and every place species list on staging failed with IncompatibleClassChangeError.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def main():
    with open(os.path.join(ROOT, "build.xml"), encoding="utf-8") as f:
        xml = f.read()
    m = re.search(r'<target name="compile".*?</target>', xml, re.S)
    assert m, "no compile target in build.xml"
    body = re.sub(r"<!--.*?-->", "", m.group(0), flags=re.S)
    d = body.find('<delete dir="${build.home}/WEB-INF/classes"')
    j = body.find("<javac")
    assert d != -1, "the compile target does not delete ${build.home}/WEB-INF/classes"
    assert j != -1 and d < j, "the classes folder must be deleted before javac runs"
    m = re.search(r'<target name="deploy"[^>]*depends="([^"]*)"', xml)
    assert m and "compile" in m.group(1), "ant deploy must depend on compile"
    print("ant compile deletes the compiled classes first; every deploy compiles every source.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

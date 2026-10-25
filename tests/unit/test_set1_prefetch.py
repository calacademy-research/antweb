#!/usr/bin/env python3
"""The nightly's AntCat download runs before set 1 takes its database connection, and only for the scheduler's set 1.

2026-10-25: the nightly may try the download for up to an hour (FetchRetry). The long connection pool closes a connection
held over 60 minutes (unreturnedConnectionTimeout in WEB-INF/struts-configDb.xml) and set 1 already takes up to an hour,
so the retries must not run while set 1 holds its connection. Run: python3 tests/unit/test_set1_prefetch.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main():
    src = open(os.path.join(ROOT, "src/org/calacademy/antweb/util/UtilDataAction.java"), encoding="utf-8").read()
    problems = []
    call = 'if (isScheduler && "set1".equals(action)) SpeciesListUploader.prefetchWorldantsList();'
    if src.count("prefetchWorldantsList(") != 1:
        problems.append("prefetchWorldantsList must be called exactly once in UtilDataAction")
    if call not in src:
        problems.append("the prefetch must run only for the scheduler's set 1: " + call)
    else:
        start = src.index("public ActionForward execute(")
        pre = src.index(call)
        conn = src.index('getDataSource(request, "longConPool")', start)
        if not start < pre < conn:
            problems.append("the prefetch must come before set 1 takes its longConPool connection in execute()")
    uploader = open(os.path.join(ROOT, "src/org/calacademy/antweb/curate/speciesList/SpeciesListUploader.java"), encoding="utf-8").read()
    body = uploader[uploader.index("public UploadDetails fetchWorldantsList(boolean"):]
    body = body[:body.index("\n  public ")]
    if "FetchRetry.run(" in body:
        problems.append("set 1's own fetch (fetchWorldantsList) must not retry: it holds the connection")
    pool = open(os.path.join(ROOT, "WEB-INF/struts-configDb.xml"), encoding="utf-8").read()
    m = re.search(r'unreturnedConnectionTimeout"\s+value="(\d+)"', pool)
    if not m:
        problems.append("unreturnedConnectionTimeout not found in WEB-INF/struts-configDb.xml (update this test)")
    for p in problems:
        print("FAIL", p)
    print("FAILED" if problems else "OK: the nightly AntCat download runs before set 1 takes its connection")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())

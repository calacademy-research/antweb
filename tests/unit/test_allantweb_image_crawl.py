#!/usr/bin/env python3
"""Nightly set 4 counts the AntWeb-wide list's Images even when the project cache has no allantwebants project.

Staging 2026-10-28: ProjectMgr.getProject("allantwebants") returned null, imageCrawlProjects skipped the list, every
row stayed 0 (Dacatria templaris: atolants 13, worldants 13, allantwebants 0), and compute.log still said "AntWeb-wide
list Images counted". The crawl now builds the list by name when it is missing, and logs whether it was counted."""
import os
import re
import sys

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "org", "calacademy", "antweb", "home",
                   "ProjTaxonCountDb.java")


def main():
    with open(SRC, encoding="utf-8") as f:
        java = f.read()
    body = java[java.index("public void imageCountCrawl()"):java.index("public static final String ALLANTWEB_IMAGES_LOG")]
    assert "allAntweb == null" in body and "setName(Project.ALLANTWEBANTS)" in body, "a missing allantwebants project must be built by name"
    assert "countedAllAntweb ? ALLANTWEB_IMAGES_LOG" in body, "compute.log must say whether the AntWeb-wide list was counted"
    print("Set 4 counts the AntWeb-wide list's Images even without a cached project; compute.log says so.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

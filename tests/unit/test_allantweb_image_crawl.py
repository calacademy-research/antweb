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
    assert "allAntwebProject()" in body, "set 4 must count the allantwebants list"
    helper = java[java.index("static Project allAntwebProject()"):]
    assert "allAntweb == null" in helper and "setName(Project.ALLANTWEBANTS)" in helper, "a missing allantwebants project must be built by name"
    assert "countedAllAntweb ? ALLANTWEB_IMAGES_LOG" in body, "compute.log must say whether the AntWeb-wide list was counted"
    # 2026-10-28: every specimen upload rebuilds the allantwebants rows with image_count 0 (regenerateAllAntweb);
    # their Images must be counted again at once, not at the next nightly set 4.
    with open(os.path.join(os.path.dirname(SRC), "ProjTaxonDb.java"), encoding="utf-8") as f:
        ptdb = f.read()
    regen = ptdb[ptdb.index("public void regenerateAllAntweb()"):ptdb.index("public void finishRegenerateAllAntweb()")]
    assert regen.index("allAntwebImageCount()") > regen.index("insert into proj_taxon"), "regenerateAllAntweb must recount the AntWeb-wide Images after re-inserting the rows"
    print("Set 4 and every upload's rebuild count the AntWeb-wide list's Images; compute.log says so.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

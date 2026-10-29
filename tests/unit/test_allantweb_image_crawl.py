#!/usr/bin/env python3
"""Nightly set 4 counts the AntWeb-wide list's Images even when the project cache has no allantwebants project.

Staging 2026-10-28: ProjectMgr.getProject("allantwebants") returned null, imageCrawlProjects skipped the list, every
row stayed 0 (Dacatria templaris: atolants 13, worldants 13, allantwebants 0), and compute.log still said "AntWeb-wide
list Images counted". The crawl now builds the list by name when it is missing, and logs whether it was counted.
Decision 3 (2026-10-28): a synonym's specimens' images count under the valid name (PlaceTotals.allAntwebImagesSql)."""
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
    # Decision 3 (2026-10-28): the AntWeb-wide Images count a synonym's specimens under the valid name, the rule of the
    # museum, bioregion and curated project lists (staging run 40795139086: floridakeysants Camponotus inaequalis 32,
    # allantwebants 29). Set 4 and regenerateAllAntweb both go through Project.getTaxonImageCountQuery.
    with open(os.path.join(os.path.dirname(SRC), "..", "Project.java"), encoding="utf-8") as f:
        project = f.read()
    q = project[project.index("public String getTaxonImageCountQuery()"):project.index("public String getUpdateImageCountSQL(")]
    assert "ALLANTWEBANTS.equals(getName())" in q and "PlaceTotals.allAntwebImagesSql(" in q, "allantwebants Images must follow decision 3"
    assert q.index("allAntwebImagesSql") < q.index("synonymsToValid"), "the allantwebants branch must come before the very-name query"
    assert "imageCountCrawl(allAntwebProject())" in java[java.index("public void allAntwebImageCount()"):], "the upload rebuild must use the same query"
    with open(os.path.join(os.path.dirname(SRC), "PlaceTotals.java"), encoding="utf-8") as f:
        totals = f.read()
    sql = totals[totals.index("public static String allAntwebImagesSql("):]
    sql = sql[:sql.index("\n    }\n")]
    assert "renamedCondition(\"sx\")" in sql and "current_valid_name" in sql, "a renamed name's images go to its current valid name"
    assert "StatisticsDb.ANT_IMAGES_OF_SPECIMEN" in sql, "ant images only"
    assert "not exists" in sql, "each specimen once: not under the valid name when its own name has a row"
    print("Set 4 and every upload's rebuild count the AntWeb-wide list's Images (a synonym's under the valid name); compute.log says so.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)

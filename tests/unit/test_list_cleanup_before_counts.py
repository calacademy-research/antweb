"""The daily update takes rows off project lists before it counts them (2026-11-12).

Set 5, production's last set, removed project list rows (a genus row without species on the list, a proxy row of the
Species List Tool without children: ProjTaxonDb.verifyProjTaxon, cleanupSpeciesListProxyRecords) after set 3 had
stored the project totals and set 4 the statistics row. Production 2026-11-12: Genera of fossilants 259 stored, 258
listed; allantwebants 536 and 535; the statistics row's proj_taxa 70,419, a recount 70,413. Staging skips set 5.

Source checks (the behaviour is tested against a database by home.ListCleanupOrderDbTest):
  - set 2, which every run reaches on production and staging, calls ProjTaxonDb.cleanupLists right after the data
    clean-up and before the first count of a project list (the Global recount), and writes its line to compute.log;
  - no later step of the sets removes project list rows: sets 3, 4 and 5 call neither clean-up;
  - the clean-up is the two that set 5 ran, and the AntCat list is still left alone;
  - the rule of the genus rows (agreed with Brian Fisher, 2026-11-02: "A genus stays on a place's list only with a
    species row of that genus or a specimen of it there") is in one method, which the daily clean-up uses for every
    project and the rebuild of the AntWeb-wide list (each specimen upload, the name update) for its list, after the
    rows are in and before they are counted: the builder and the clean-up cannot read the rule differently again
    (until 2026-11-12 the rebuild listed every counted name and the clean-up removed a genus without species, so the
    list's Genera moved by one between an upload and the next daily update);
  - the line names the rows removed (Brian Fisher, 2026-11-03: no job deletes list entries without a record).
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8").replace("\r\n", "\n")


def code(text):
    """Java source without its comments (a call that is commented out is not a call)."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"(?m)^\s*//[^\n]*", "", text)


def set_block(src, n):
    start = src.index('if ("set%d".equals(action)) {' % n)
    end = src.index('if ("set%d".equals(action)) {' % (n + 1), start) if n < 5 else src.index("AntwebMgr.populate(connection, true);", start)
    return code(src[start:end])


CLEANUPS = ("cleanupLists", "verifyProjTaxon", "cleanupSpeciesListProxyRecords")


def test_set_2_cleans_the_project_lists_before_the_first_count():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    set2 = set_block(src, 2)
    call = "String listCleanup = new ProjTaxonDb(connection).cleanupLists();"
    log = 'LogMgr.appendLog("compute.log", "  " + listCleanup, true);'
    order = ['doAction("dataCleanup"', call, log, "recountGlobal(connection)", "recountAntCatList(connection)", 'doAction("updateTaxonSets"',
             'doAction("geolocaleCountCrawl"', "Set 2 done"]
    positions = [set2.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    assert set2.count("cleanupLists") == 1
    # the Global recount is the first count of a project list in the run after set 1
    assert "new ProjectDb(connection).updateCounts(Project.ALLANTWEBANTS);" in src[src.index("static String recountGlobal(Connection connection)"):]


def test_no_later_set_removes_project_list_rows():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    for n in (3, 4, 5):
        block = set_block(src, n)
        for name in CLEANUPS:
            assert name not in block, "set %d still calls %s after the lists were counted" % (n, name)
    # set 5 is the default images and the maps, then the caches
    set5 = set_block(src, 5)
    assert re.findall(r'doAction\("(\w+)"', set5) == ["deleteConflictedDefaultImages", "genObjectMaps"], set5
    # the sets where the stored numbers are written come after set 2
    assert 'doAction("projectCountCrawl"' in set_block(src, 3)
    assert "populateStatistics(" in set_block(src, 4)
    # the two jobs stay available by hand, with the list totals counted again
    for job in ("cleanupSpeciesListProxyRecords", "verifyProjTaxon"):
        assert 'if (action.equals("%s")) {' % job in src
    assert src.count("listTotalsAfterRowsRemoved(connection, projTaxonDb)") == 2


def test_the_cleanup_is_the_two_of_set_5():
    src = read("src/org/calacademy/antweb/home/ProjTaxonDb.java")
    assert 'public static final String CLEANUP_LOG = "Project lists cleaned";' in src
    start = src.index("public String cleanupLists() {")
    cleanup = code(src[start:src.index("\n    }\n", start)])
    assert cleanup.index("cleanupSpeciesListProxyRecords();") < cleanup.index("verifyProjTaxon();")
    assert '"; FAILED: "' in cleanup
    for word in ("commit(", "rollback(", "setAutoCommit("):
        assert word not in cleanup, word
    # the AntCat list keeps every genus row; rows removed are counted and named per project
    assert "from proj_taxon where project_name != 'worldants' order by project_name" in src
    assert "rowsRemoved.merge(projectName, c, Integer::sum);" in src
    assert "namesRemoved.computeIfAbsent(projectName, k -> new ArrayList<>()).add(taxonName);" in src
    assert 'String named = removedRowsText();' in cleanup


def method(src, signature):
    start = src.index(signature)
    return code(src[start:src.index("\n    }\n", start)])


def test_one_rule_for_the_cleanup_and_the_rebuild_of_the_antweb_wide_list():
    src = read("src/org/calacademy/antweb/home/ProjTaxonDb.java")
    rule = "removeGenusRowsWithoutSpeciesOrSpecimen"
    # the daily clean-up: every project but the AntCat list, by the method
    verify = method(src, "public String verifyProjTaxon() throws SQLException {")
    assert "for (String project : projects) removed += %s(project);" % rule in verify
    assert "hasNoChildren" not in verify
    # the method: no species row on the list; on the AntWeb-wide list also no specimen
    body = method(src, "public int %s(String projectName) throws SQLException {" % rule)
    assert "GENUS_ROWS_WITHOUT_SPECIES_SQL" in body
    assert "if (Project.ALLANTWEBANTS.equals(projectName) && antwebHasSpecimenOf(genus[1], genus[2])) continue;" in body
    assert "deleteProjTaxon(genus[0], projectName);" in body
    assert "ct.parent_taxon_name" in src[src.index("GENUS_ROWS_WITHOUT_SPECIES_SQL ="):][:700]
    # the rebuild: the rows in, the disputed ones out, then the rule, and only then the counts
    rebuild = method(src, "public void rebuildAllAntwebList() throws SQLException {")
    order = ["delete from proj_taxon where project_name = 'allantwebants'", "insert into proj_taxon (taxon_name, project_name, source)",
             "from proj_taxon_dispute", "%s(Project.ALLANTWEBANTS)" % rule]
    positions = [rebuild.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    assert "childrenCountCrawl" not in rebuild and "updateCounts" not in rebuild
    regenerate = method(src, "public void regenerateAllAntweb() throws SQLException {")
    order = ["rebuildAllAntwebList();", 'childrenCountCrawl("allantwebants")', "allAntwebImageCount();", 'updateCounts("allantwebants")']
    positions = [regenerate.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    # every caller of the rebuild goes through regenerateAllAntweb (uploads, the name update, the parent repair, the admin job)
    for f in ("src/org/calacademy/antweb/upload/SpecimenUpload.java", "src/org/calacademy/antweb/upload/SpeciesListUpload.java",
              "src/org/calacademy/antweb/util/UtilDataAction.java"):
        assert "regenerateAllAntweb()" in read(f), f


if __name__ == "__main__":
    test_set_2_cleans_the_project_lists_before_the_first_count()
    test_no_later_set_removes_project_list_rows()
    test_the_cleanup_is_the_two_of_set_5()
    test_one_rule_for_the_cleanup_and_the_rebuild_of_the_antweb_wide_list()
    print("ok")

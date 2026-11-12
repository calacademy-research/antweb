"""The daily update takes rows off project lists before it counts them (2026-11-12).

Set 5, production's last set, removed project list rows (a genus row without species on the list, a proxy row of the
Species List Tool without children: ProjTaxonDb.verifyProjTaxon, cleanupSpeciesListProxyRecords) after set 3 had
stored the project totals and set 4 the statistics row. Production 2026-11-12: Genera of fossilants 259 stored, 258
listed; allantwebants 536 and 535; the statistics row's proj_taxa 70,419, a recount 70,413. Staging skips set 5.

Source checks (the behaviour is tested against a database by home.ListCleanupOrderDbTest):
  - set 2, which every run reaches on production and staging, calls ProjTaxonDb.cleanupLists right after the data
    clean-up and before the first count of a project list (the Global recount), and writes its line to compute.log;
  - no later step of the sets removes project list rows: sets 3, 4 and 5 call neither clean-up;
  - the clean-up is the two that set 5 ran, and the AntCat list is still left alone.
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
    # the AntCat list keeps every genus row; rows removed are counted per project
    assert "\"   and pt.project_name != 'worldants'\"" in src
    assert "rowsRemoved.merge(projectName, c, Integer::sum);" in src


if __name__ == "__main__":
    test_set_2_cleans_the_project_lists_before_the_first_count()
    test_no_later_set_removes_project_list_rows()
    test_the_cleanup_is_the_two_of_set_5()
    print("ok")

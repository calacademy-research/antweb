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

Review of PR #346 (2026-11-12): set 2's step commits the data clean-up first and has its own rollback
(UtilDataAction.cleanProjectLists), and its line is escaped in the job's answer; every row removed is recorded in
list_removal_log; the specimen test leaves out red-flagged specimens by the lists' own condition.

Second review of PR #347 (2026-11-12): the record first, then the delete. The record of the clean-ups throws when it
cannot be written, and the row is then not deleted (until then the failure was logged and the row deleted: a removal
without a record); the rebuild records a genus with the source it had before the rebuild; the line counts names.
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
    # the line names taxa and may hold an error's text: escaped in the job's answer (an HTML page)
    call = 'message += " " + Html.esc(cleanProjectLists(connection));'
    order = ['doAction("dataCleanup"', call, "recountGlobal(connection)", "recountAntCatList(connection)", 'doAction("updateTaxonSets"',
             'doAction("geolocaleCountCrawl"', "Set 2 done"]
    positions = [set2.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    assert set2.count("cleanProjectLists(") == 1 and "cleanupLists" not in set2
    # the step: the data clean-up committed first, then the clean-up, committed, or rolled back after an error; its line
    start = src.index("public static String cleanProjectLists(Connection connection) {")
    step = code(src[start:src.index("\n    }\n", start)])
    order = ["connection.commit();", "line = lists.cleanupLists();", "if (lists.cleanupFailed()) {", "connection.rollback();", "NOT done, no row removed",
             "connection.commit();\n            }", 'LogMgr.appendLog("compute.log", "  " + line, true);']
    positions = [step.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
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
        assert 'action.equals("%s")' % job in src
    assert src.count("listTotalsAfterRowsRemoved(connection, projTaxonDb)") == 1   # in cleanListsByHand, for both jobs


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
    # one per name taken off a list, as the names shown (a name's row twice on a list is one name)
    assert "rowsRemoved.merge(projectName, 1, Integer::sum);" in src
    text = method(src, "public String removedRowsText() {")
    assert "names.size() + \": \"" in text and "names.size() > shown" in text and "rowsRemoved" not in text
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
    assert "return removeGenusRows(projectName, ListRemovalLog.JOB_GENUS_NO_SPECIES, null);" in method(
        src, "public int %s(String projectName) throws SQLException {" % rule)
    body = method(src, "private int removeGenusRows(String projectName, String job, java.util.Map<String, String> recordOnly) throws SQLException {")
    assert "GENUS_ROWS_WITHOUT_SPECIES_SQL" in body
    assert "if (Project.ALLANTWEBANTS.equals(projectName) && antwebHasSpecimenOf(genus[1], genus[2])) continue;" in body
    assert "deleteProjTaxon(genus[0], projectName, null, record ? job : null, ListRemovalLog.REASON_GENUS_NO_SPECIES, sourceBefore);" in body
    # the rebuild's record has the source the genus row had before the rebuild (recordOnly: name -> that source)
    assert "String sourceBefore = recordOnly == null ? null : DBUtil.sqlLiteral(recordOnly.get(genus[0]));" in body
    # every delete of the clean-ups goes through deleteProjTaxon, which records the row before it deletes it
    delete = method(src, "private String deleteProjTaxon(String taxonName, String projectName, String extraClause, String job, String reason, String sourceSql) throws SQLException {")
    # (recordWhereOnce since the review of #347: a row the import puts back every day is not recorded every day)
    assert delete.index("ListRemovalLog.recordWhereOnce(getConnection(), ListRemovalLog.PROJECT,") < delete.index("stmt.executeUpdate(dml)")
    assert src.count("delete from proj_taxon \" + where") == 1
    assert "ListRemovalLog.JOB_PROXY_NO_CHILDREN, ListRemovalLog.REASON_PROXY_NO_CHILDREN, null);" in src
    # a red-flagged specimen is no specimen of the genus: the lists' own condition
    sql = method(src, "public static String antwebSpecimenOfGenusSql(boolean withSubfamily) {")
    assert 'SpecimenDb.notRedFlagged("s")' in sql and "s.genus = ?" in sql
    assert "ct.parent_taxon_name" in src[src.index("GENUS_ROWS_WITHOUT_SPECIES_SQL ="):][:700]
    # the rebuild: the rows in, the disputed ones out, then the rule, and only then the counts
    rebuild = method(src, "public void rebuildAllAntwebList() throws SQLException {")
    order = ["delete from proj_taxon where project_name = 'allantwebants'", "insert into proj_taxon (taxon_name, project_name, source)",
             "from proj_taxon_dispute", "removeGenusRows(Project.ALLANTWEBANTS, ListRemovalLog.JOB_REBUILD_GENUS, generaBefore)"]
    positions = [rebuild.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    assert "childrenCountCrawl" not in rebuild and "updateCounts" not in rebuild
    assert rebuild.index("generaBefore.put(r.getString(1), r.getString(2));") < rebuild.index("delete from proj_taxon where project_name = 'allantwebants'")
    assert "select pt.taxon_name, pt.source from proj_taxon pt" in rebuild
    # no record without that read: its error is not swallowed (the first catch of the rebuild is the rule's)
    assert "catch" not in rebuild[:rebuild.index("delete from proj_taxon where project_name = 'allantwebants'")]
    # the method's javadoc sits on the method, the test hook above it
    assert re.search(r"\*/\n    public void rebuildAllAntwebList\(\) throws SQLException \{", src)
    assert src.index("private static volatile Runnable s_beforeGenusRuleForTests") < src.index("     * The rows of the AntWeb-wide list, built again:")
    assert "} catch (SQLException | RuntimeException e) {\n            s_log.error(\"rebuildAllAntwebList() genus rows e:\" + e);" in rebuild
    # review of #347: a deadlock (the database rolled the upload's transaction back) is passed on, by the rebuild and by
    # the record of removed rows; nothing else is
    assert re.search(r"if \(DBUtil\.transactionRolledBack\(e\)\) \{\s*throw e instanceof SQLException \? \(SQLException\) e", rebuild)
    log = read("src/org/calacademy/antweb/home/ListRemovalLog.java")
    assert re.search(r"if \(org\.calacademy\.antweb\.util\.DBUtil\.transactionRolledBack\(e\)\) \{\s*throw new IllegalStateException\(", log)
    util = read("src/org/calacademy/antweb/util/DBUtil.java")
    rolled = util[util.index("public static boolean transactionRolledBack(Throwable e) {"):]
    rolled = rolled[:rolled.index("\n    }\n")]
    assert "sql.getErrorCode() == 1213" in rolled and 'sql.getErrorCode() != 1205' in rolled and "t.getCause()" in rolled

    regenerate = method(src, "public void regenerateAllAntweb() throws SQLException {")
    order = ["rebuildAllAntwebList();", 'childrenCountCrawl("allantwebants")', "allAntwebImageCount();", 'updateCounts("allantwebants")']
    positions = [regenerate.index(x) for x in order]
    assert positions == sorted(positions), list(zip(order, positions))
    # every caller of the rebuild goes through regenerateAllAntweb (uploads, the name update, the parent repair, the admin job)
    for f in ("src/org/calacademy/antweb/upload/SpecimenUpload.java", "src/org/calacademy/antweb/upload/SpeciesListUpload.java",
              "src/org/calacademy/antweb/util/UtilDataAction.java"):
        assert "regenerateAllAntweb()" in read(f), f


def test_no_delete_without_its_record():
    """Brian Fisher, 2026-11-03: no job deletes list entries without a record a curator can see. The record of the
    project list clean-ups (ListRemovalLog.recordWhereOnce) throws when it cannot be written; deleteProjTaxon throws on
    before its delete, with the database's error number (a deadlock must still be told from a lock wait timeout). The
    daily clean-up keeps that as an error of its step, which rolls back; the rebuild at an upload logs it and leaves
    the list as built. Until 2026-11-12 the failure was logged and the delete ran."""
    log = read("src/org/calacademy/antweb/home/ListRemovalLog.java")
    once = [m for m in re.finditer(r"public static int recordWhereOnce\(Connection connection, [^)]*\) throws SQLException \{\n(.*?)\n    \}\n", log, re.S)]
    assert len(once) == 2, len(once)
    assert "return insert(connection, scriptSql(listType, job, reason, where, sourceSql) + \" and \" + notRecordedYet(listType, job, reason, sourceSql));" in once[1].group(1)
    assert "write(" not in once[0].group(1) + once[1].group(1)   # write() logs an error and returns 0
    start = log.index("private static int insert(Connection connection, String insert) throws SQLException {")
    assert "catch" not in log[start:log.index("\n    }\n", start)]
    # the source before the rebuild is the one written and the one compared
    start = log.index("static String notRecordedYet(String listType, String job, String reason, String sourceSql) {")
    cond = log[start:log.index("\n    }\n", start)]
    assert 'String rowSource = sourceSql == null ? "l.source" : sourceSql;' in cond
    assert 'coalesce(" + rowSource + ", \'\')' in cond and '"(" + rowSource + " in ("' in cond

    src = read("src/org/calacademy/antweb/home/ProjTaxonDb.java")
    delete = method(src, "private String deleteProjTaxon(String taxonName, String projectName, String extraClause, String job, String reason, String sourceSql) throws SQLException {")
    record, failed, thrown, dml = (delete.index(x) for x in (
        "ListRemovalLog.recordWhereOnce(", "} catch (SQLException | RuntimeException e) {", "throw e instanceof SQLException", "stmt.executeUpdate(dml)"))
    assert record < failed < thrown < dml
    assert "new SQLException(text, ((SQLException) e).getSQLState(), ((SQLException) e).getErrorCode(), e)" in delete
    assert "return" not in delete[failed:dml]   # nothing between the failed record and the delete but the throw
    # the two clean-ups keep the error for the line; set 2's step and the jobs by hand roll back when there is one
    for signature, label in (("public String verifyProjTaxon() throws SQLException {", "genus rows: "),
                             ("public String cleanupSpeciesListProxyRecords() throws SQLException {", "proxy rows: ")):
        body = method(src, signature)
        assert re.search(r'catch \(SQLException \| RuntimeException e\) \{\s*s_log\.error\([^;]*;\s*cleanupErrors\.add\("%s" \+ e\);' % label, body), signature
    util = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    start = util.index("public static String cleanProjectLists(Connection connection) {")
    step = code(util[start:util.index("\n    }\n", start)])
    assert step.index("cleanupFailed()") < step.index("connection.rollback();")


def test_the_jobs_by_hand_say_a_failure_and_roll_back():
    src = read("src/org/calacademy/antweb/util/UtilDataAction.java")
    assert re.search(r'if \(action\.equals\("cleanupSpeciesListProxyRecords"\) \|\| action\.equals\("verifyProjTaxon"\)\) \{\s*'
                     r"message = cleanListsByHand\(connection, action\);", src)
    start = src.index("public static String cleanListsByHand(Connection connection, String action) {")
    job = code(src[start:src.index("\n    }\n", start)])
    assert "if (projTaxonDb.cleanupFailed()) return head + rolledBack(connection, projTaxonDb.cleanupErrorsText());" in job
    assert job.index("cleanupFailed()") < job.index("listTotalsAfterRowsRemoved(connection, projTaxonDb)")
    start = src.index("private static String rolledBack(Connection connection, String errors) {")
    undo = code(src[start:src.index("\n    }\n", start)])
    assert "connection.rollback();" in undo and 'return "; FAILED: " + Html.esc(errors) + undone;' in undo


if __name__ == "__main__":
    test_set_2_cleans_the_project_lists_before_the_first_count()
    test_no_later_set_removes_project_list_rows()
    test_the_cleanup_is_the_two_of_set_5()
    test_one_rule_for_the_cleanup_and_the_rebuild_of_the_antweb_wide_list()
    test_no_delete_without_its_record()
    test_the_jobs_by_hand_say_a_failure_and_roll_back()
    print("ok")

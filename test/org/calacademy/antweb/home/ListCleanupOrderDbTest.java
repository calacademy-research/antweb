package test.org.calacademy.antweb.home;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Overview;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.home.ListRemovalLog;
import org.calacademy.antweb.home.PlaceGenusCurationDb;
import org.calacademy.antweb.home.PlaceViewTotalDb;
import org.calacademy.antweb.home.ProjTaxonDb;
import org.calacademy.antweb.home.ProjectDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.UtilDataAction;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The daily update takes the rows off project lists that go once their children are gone (a genus row without species
 * on the list, a proxy row of the Species List Tool without children; ProjTaxonDb.cleanupLists) BEFORE it counts the
 * lists. Until 2026-11-12 set 5, production's last set, removed them after set 3 had stored the project totals and set 4
 * the statistics row, and the count check reported those numbers every day: production 2026-11-12, "Genera of project
 * pages = genus rows on the list" fossilants 259 stored, 258 listed, allantwebants 536 and 535; statistics row
 * proj_taxa 70,419, a recount 70,413 (6 rows removed). Staging skips set 5 and never showed it.
 *
 * On the numbers fixture (NumbersTestDb: projects madants and allantwebants, genera Pheidole and Hypoponera) with a
 * valid genus that has no species, Crematogaster, on both lists and on the AntCat list (worldants, which the clean-up
 * leaves alone), and a proxy row without children, the subfamily Dorylinae, on madants. The stored numbers are written
 * by the code the daily update uses: the project totals (ProjectDb.finish: the Global recount of set 2, the project
 * counts of set 3), the statistics row (StatisticsDb.populateStatistics, set 4) and the list totals of the project page
 * (PlaceViewTotalDb.fill, after set 4).
 *
 *  - the order until 2026-11-12 (counts, then the clean-up): the three kinds of difference production showed;
 *  - the order now (the clean-up, then the counts): no row the clean-up removed is in any stored number, and the count
 *    check finds nothing; the AntCat list keeps its row; the line names the rows removed per project;
 *  - a second clean-up removes nothing; a database error is in the line and not thrown;
 *  - the line names every row removed, by project and name, up to ten names per project, then "and N more" (Brian
 *    Fisher, 2026-11-03: no job deletes list entries without a record; administrators read compute.log in the Log
 *    Manager, logMgr.do).
 *
 * The rule of the genus rows (agreed with Brian Fisher, 2026-11-02: "A genus stays on a place's list only with a
 * species row of that genus or a specimen of it there") holds at every moment: the rebuild of the AntWeb-wide list at
 * each specimen upload (ProjTaxonDb.rebuildAllAntwebList) applies the method the daily clean-up uses
 * (removeGenusRowsWithoutSpeciesOrSpecimen). Until 2026-11-12 the rebuild put every counted name on the list, a genus
 * without species among them, and the clean-up took it off: the list's Genera moved by one between an upload and the
 * next daily update (production: 536, 535).
 *  - after a rebuild the genus without species or specimens is not on the list, a genus whose only name is "<Genus>
 *    (indet)" (specimens identified only to genus) is, and so is a genus that has a specimen in AntWeb but no name on
 *    the list; the daily clean-up then removes nothing from that list and its Genera do not move;
 *  - on a project list, whose specimens are those of its listed names, a genus without a species row goes.
 *
 * Review of PR #346 (2026-11-12):
 *  - every row the clean-up removes is recorded in list_removal_log (ListRemovalLog; Brian Fisher, 2026-11-03: "no job
 *    deletes list entries without a record a curator can see"), every one, not only the ten the line names; the
 *    rebuild records a genus that was on the list before it, once, and not the genus it only put on and took off;
 *  - a red-flagged specimen keeps no genus on the AntWeb-wide list ("A red-flagged specimen never counts, in any view,
 *    on any list", claude/antweb-list-design-rules.md);
 *  - the specimen query of that rule can use the index genus_species_idx (EXPLAIN);
 *  - set 2's step commits the data clean-up's work first and rolls back only its own after an error.
 *
 * Review of PR #347 (2026-11-12):
 *  - a deadlock in the rule of the genus rows during the rebuild of the AntWeb-wide list is passed on, not swallowed:
 *    the database has rolled back the whole transaction, which is the upload's own, and the upload must fail instead
 *    of committing nothing and reporting success; a lock wait timeout, which undoes one statement only, is still
 *    swallowed and the list stays as built (DBUtil.transactionRolledBack);
 *  - the two admin jobs say "FAILED: ..." in their answer and roll back when the clean-up met an error;
 *  - a row the AntCat import puts back every day and the clean-up takes off every day is recorded once in
 *    list_removal_log, not every day; a curator's row, and a row removed again after a curator's own removal, are
 *    recorded again (ListRemovalLog.recordWhereOnce).
 *
 * Second review of PR #347 (2026-11-12): the record first, then the delete.
 *  - a row whose removal cannot be recorded (the record's insert meets a lock wait timeout) is not deleted: the daily
 *    clean-up says it as an error of its step ("FAILED: ... no record could be written ..."), which the step's caller
 *    rolls back; the rebuild at an upload logs it, leaves the genus row on the list and goes on. Until then the
 *    failure was logged and the row deleted: a removal without a record;
 *  - the rebuild's record has the source the genus row had before the rebuild, which is also what tells a curator's
 *    row and a removal recorded already;
 *  - the line counts one per name, also when a name's row is on a list twice.
 *
 * tests/unit/test_list_cleanup_before_counts.py checks in the source that set 2 calls the clean-up before its first
 * count and that no later set removes list rows. Runs in CI against MySQL 8.4; ANTWEB_TEST_DB_URL overrides.
 */
public class ListCleanupOrderDbTest extends TestCase {

    static final String CREMATOGASTER = "myrmicinaecrematogaster";
    static final String DORYLINAE = "dorylinae";
    static final String ALL = Project.ALLANTWEBANTS;

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        // the columns the list page's own code reads (TaxaPage.fetchChildren, Taxon.setDetails), as PlaceViewTotalDbTest
        exec(connection, "alter table taxon add column subspecies varchar(64), add column antcat_id int, add column type tinyint default 0,"
            + " add column author_date varchar(128)");
        exec(connection, "update taxon set genus = 'pheidole' where taxon_name like 'myrmicinaepheidole%'");
        exec(connection, "update taxon set genus = 'hypoponera' where taxon_name like 'ponerinaehypoponera%'");
        exec(connection, "update taxon set species = substring_index(taxon_name, ' ', -1) where taxarank in ('species', 'subspecies')");
        exec(connection, "alter table proj_taxon add column rev int default 0");
        exec(connection, "drop table if exists place_view_total");
        exec(connection, "drop table if exists place_view_total_state");
        exec(connection, "drop table if exists proj_taxon_gone");
        exec(connection, PlaceViewTotalDb.createTable(null, null));
        exec(connection, PlaceViewTotalDb.createStateTable(null, null));
        StatisticsDb.ensureHomeColumns(connection);
        exec(connection, "alter table taxon add column source varchar(30)");   // read by the rebuild of the AntWeb-wide list
        // the record of removed list rows, empty
        ListRemovalLog.forgetSchema();
        ListRemovalLog.ensureSchema(connection);
        exec(connection, "delete from " + ListRemovalLog.TABLE);
        // a valid genus without species, and a subfamily that is on madants only as a proxy row of the Species List Tool
        exec(connection, "insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values"
            + " ('" + CREMATOGASTER + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'crematogaster'),"
            + " ('" + DORYLINAE + "', 'subfamily', 'valid', '" + FAMILY + "', 'formicidae', '" + DORYLINAE + "', null)");
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('madants', '" + CREMATOGASTER + "', 'specimen'),"
            + " ('" + ALL + "', '" + CREMATOGASTER + "', 'worldants'), ('worldants', '" + CREMATOGASTER + "', 'worldants'),"
            + " ('madants', '" + DORYLINAE + "', 'proxyspeciesListTool')");
    }

    @Override
    protected void tearDown() throws Exception {
        ProjTaxonDb.setBeforeGenusRuleForTests(null);
        if (connection != null) {
            try { connection.rollback(); } catch (java.sql.SQLException e) { /* autocommit: nothing to roll back */ }
            connection.setAutoCommit(true);
            exec(connection, "drop table if exists proj_taxon_gone");
            connection.close();
        }
    }

    private int one(String sql) throws Exception {
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            return r.next() ? r.getInt(1) : -1;
        }
    }

    private static Project project(String name) {
        Project p = new Project();
        p.setName(name);
        return p;
    }

    /** The numbers the daily update stores about the project lists, in its order, by its own code. */
    private void counts() throws Exception {
        Method finish = ProjectDb.class.getDeclaredMethod("finish", Project.class);
        finish.setAccessible(true);
        finish.invoke(new ProjectDb(connection), project(ALL));        // set 2: the Global recount's project totals
        finish.invoke(new ProjectDb(connection), project("madants"));  // set 3: the project counts
        new StatisticsDb(connection).populateStatistics("nightly", 0, "set4", null);   // set 4: the statistics row
        new PlaceGenusCurationDb(connection).fillAll();
        new PlaceViewTotalDb(connection).fill(Arrays.<Overview>asList(project("madants")), true);   // after set 4: the page's list totals
    }

    /** {Genera of the project as stored, its genus rows that are valid genera on the list now}. */
    private int[] genera(String project) throws Exception {
        return new int[] {one("select genus_count from project where project_name = '" + project + "'"),
            one("select count(*) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name where l.project_name = '" + project
                + "' and t.taxarank = 'genus' and t.status = 'valid'")};
    }

    /** {proj_taxa of the latest statistics row, the rows of proj_taxon now}. */
    private int[] projTaxa() throws Exception {
        return new int[] {one("select proj_taxa from statistics order by id desc limit 1"), one("select count(*) from proj_taxon")};
    }

    private int viewGenera() throws Exception {
        return one("select n from place_view_total where list_type = 'project' and list_key = 'madants' and taxarank = 'genus'");
    }

    /** The count check's real differences of the kinds production showed on 2026-11-12. */
    private List<String> differences() throws Exception {
        CountCheck.Result r = new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, null);
        List<String> out = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) {
            String text = String.valueOf(m);
            if (!m.isReal()) continue;
            if ("place.totals.genus".equals(m.check) || "place.totals.subfamily".equals(m.check)
                    || ("admin.statistics".equals(m.check) && text.contains("proj_taxa"))) out.add(text);
        }
        return out;
    }

    private static String assertOnlyThese(List<String> differences, String... parts) {
        String all = String.join("\n  ", differences);
        for (String part : parts) assertTrue("no difference with '" + part + "' in:\n  " + all, all.contains(part));
        return all;
    }

    /** As set 5 did until 2026-11-12: the lists are counted, then the clean-up removes rows the numbers counted. */
    public void testCountsThenCleanupLeavesTheRemovedRowsInTheStoredNumbers() throws Exception {
        counts();
        assertEquals("nothing differs before the clean-up: " + differences(), 0, differences().size());
        String line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 names removed in "));
        // the three numbers of production 2026-11-12, here one or two rows too high each
        assertEquals("madants Genera stored, listed", "[3, 2]", Arrays.toString(genera("madants")));
        assertEquals(ALL + " Genera stored, listed", "[3, 2]", Arrays.toString(genera(ALL)));
        assertEquals("statistics row proj_taxa, the rows now", 3, projTaxa()[0] - projTaxa()[1]);
        // (the page's Genera of madants were 2 all along: its default view, Curated, shows no genus without species; on
        // Fossil Ants, which has its own views, they were one too high until 2026-11-11: PlaceViewTotalDbTest)
        assertEquals(2, viewGenera());
        List<String> differences = differences();
        String all = assertOnlyThese(differences, "madants", ALL, "proj_taxa");
        assertTrue("Genera of both projects, Subfamilies of madants, proj_taxa: " + all, differences.size() >= 3);
    }

    /** As the daily update does now: the clean-up first (set 2), then every count. */
    public void testCleanupThenCountsLeavesNoRemovedRowInAnyStoredNumber() throws Exception {
        int rows = one("select count(*) from proj_taxon");
        String line = new ProjTaxonDb(connection).cleanupLists();
        // the line names each row removed, by project (madants first: its proxy row went before the genus rows)
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 names removed in "));
        assertTrue(line, line.endsWith(" s; madants 2: " + DORYLINAE + ", " + CREMATOGASTER + "; " + ALL + " 1: " + CREMATOGASTER));
        assertFalse(line, line.contains("FAILED"));
        assertEquals(rows - 3, one("select count(*) from proj_taxon"));
        assertEquals("the genus without species and the proxy row are off the two lists", 0,
            one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "') and project_name <> 'worldants'"));
        assertEquals("the AntCat list keeps every genus", 1, one("select count(*) from proj_taxon where project_name = 'worldants' and taxon_name = '" + CREMATOGASTER + "'"));
        assertEquals("the rows with children stay", 2, one("select count(*) from proj_taxon where project_name = 'madants' and taxon_name in ('" + PHEIDOLE + "', '" + HYPO + "')"));
        // every removed row has its record, with the row's source, the job and the reason
        assertEquals(Arrays.asList(
            ALL + "|" + CREMATOGASTER + "|worldants|" + ListRemovalLog.JOB_GENUS_NO_SPECIES + "|" + ListRemovalLog.REASON_GENUS_NO_SPECIES,
            "madants|" + DORYLINAE + "|proxyspeciesListTool|" + ListRemovalLog.JOB_PROXY_NO_CHILDREN + "|" + ListRemovalLog.REASON_PROXY_NO_CHILDREN,
            "madants|" + CREMATOGASTER + "|specimen|" + ListRemovalLog.JOB_GENUS_NO_SPECIES + "|" + ListRemovalLog.REASON_GENUS_NO_SPECIES),
            records());

        counts();
        assertEquals("madants Genera stored = listed", "[2, 2]", Arrays.toString(genera("madants")));
        assertEquals(ALL + " Genera stored = listed", "[2, 2]", Arrays.toString(genera(ALL)));
        assertEquals("madants Subfamilies: the proxy row is not counted", 2, one("select subfamily_count from project where project_name = 'madants'"));
        assertEquals("statistics row proj_taxa = the rows", projTaxa()[1], projTaxa()[0]);
        assertEquals("the page's Genera", 2, viewGenera());
        assertEquals("the count check: " + differences(), 0, differences().size());

        // the next day: nothing to remove, and the numbers stand
        line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue(line, line.matches(ProjTaxonDb.CLEANUP_LOG + ": 0 names removed in \\d+ s"));
        counts();
        assertEquals("the count check: " + differences(), 0, differences().size());
    }

    /** More rows of one project than the line names: ten names, then "and N more"; the number is of all. */
    public void testTheLineNamesTheRowsRemovedUpToTenPerProject() throws Exception {
        StringBuilder taxa = new StringBuilder();
        StringBuilder rows = new StringBuilder();
        for (int i = 11; i <= 22; i++) {
            String name = MYRM + "genus" + i;
            taxa.append(i == 11 ? "" : ", ").append("('").append(name).append("', 'genus', 'valid', '").append(MYRM).append("', 'formicidae', '")
                .append(MYRM).append("', 'genus").append(i).append("')");
            rows.append(i == 11 ? "" : ", ").append("('madants', '").append(name).append("', 'specimen')");
        }
        exec(connection, "insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values " + taxa);
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values " + rows);
        ProjTaxonDb db = new ProjTaxonDb(connection);
        String line = db.cleanupLists();
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 15 names removed in "));
        // madants: the proxy row, Crematogaster and the twelve genera = 14 rows, ten named
        assertTrue(line, line.contains("; madants 14: " + DORYLINAE + ", " + CREMATOGASTER + ", " + MYRM + "genus11, "));
        assertTrue(line, line.contains(", " + MYRM + "genus18 and 4 more; " + ALL + " 1: " + CREMATOGASTER));
        assertFalse("the eleventh row is counted, not named: " + line, line.contains(MYRM + "genus19"));
        assertEquals(db.removedRowsText(), line.substring(line.indexOf(" s; ") + 4));
        // the record has every row, also the four the line does not name
        assertEquals(15, records().size());
        assertEquals(1, one("select count(*) from " + ListRemovalLog.TABLE + " where list_type = 'project' and list_key = 'madants' and taxon_name = '"
            + MYRM + "genus22' and removed_by_job = '" + ListRemovalLog.JOB_GENUS_NO_SPECIES + "' and removed_at is not null"));
    }

    /**
     * The line counts names, one per name taken off a list, as the names it shows and its "and N more" do. A name whose
     * row is on a list twice (the fixture's key on the list table rules that out; the repository's record of the
     * schema shows no such key) is one name: until 2026-11-12 the number was of rows deleted, so madants read "3:" with
     * two names. Each of its rows has its record.
     */
    public void testANameWhoseRowIsOnAListTwiceCountsOnce() throws Exception {
        exec(connection, "alter table proj_taxon drop primary key");
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('madants', '" + CREMATOGASTER + "', 'specimen')");
        ProjTaxonDb db = new ProjTaxonDb(connection);
        String line = db.cleanupLists();
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 names removed in "));
        assertTrue(line, line.endsWith(" s; madants 2: " + DORYLINAE + ", " + CREMATOGASTER + "; " + ALL + " 1: " + CREMATOGASTER));
        assertEquals("both rows are gone", 0, one("select count(*) from proj_taxon where project_name = 'madants' and taxon_name = '" + CREMATOGASTER + "'"));
        assertEquals("a record for each row: the proxy row, Crematogaster twice on madants and once on the AntWeb-wide list", 4, records().size());
    }

    // ------------------------------------------------------------------ the rule at every moment: the AntWeb-wide list's rebuild

    static final String LIOPONERA = "dorylinaelioponera";
    static final String OLDGENUS = "myrmicinaeoldgenus";

    /**
     * Three more genera in the taxon table, each of a counted status, as the rebuild finds them:
     *   Crematogaster  no name under it, no specimen                      neither half of the rule: not on the list
     *   Lioponera      only "Lioponera (indet)", the name of specimens identified only to genus, with a specimen:
     *                  a species row of the genus                           on the list
     *   Oldgenus       its only name is a synonym (not a counted status, so not on the list), with a specimen of
     *                  the genus in AntWeb                                  on the list: a specimen of it there
     */
    private void moreGenera() throws Exception {
        exec(connection, "insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('" + DORYLINAE + "x', 'subfamily', 'synonym', '" + FAMILY + "', 'formicidae', 'dorylinaex', null, null),"
            + " ('" + LIOPONERA + "', 'genus', 'valid', '" + DORYLINAE + "', 'formicidae', '" + DORYLINAE + "', 'lioponera', null),"
            + " ('" + LIOPONERA + " (indet)', 'species', 'indetermined', '" + LIOPONERA + "', 'formicidae', '" + DORYLINAE + "', 'lioponera', '(indet)'),"
            + " ('" + OLDGENUS + "', 'genus', 'valid', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'oldgenus', null),"
            + " ('" + OLDGENUS + " moved', 'species', 'synonym', '" + OLDGENUS + "', 'formicidae', '" + MYRM + "', 'oldgenus', 'moved')");
        exec(connection, "insert into specimen (code, taxon_name, status, country, family, subfamily, genus, access_group) values"
            + " ('lio1', '" + LIOPONERA + " (indet)', 'indetermined', 'Madagascar', 'formicidae', '" + DORYLINAE + "', 'lioponera', 1),"
            + " ('old1', '" + OLDGENUS + " moved', 'synonym', 'Madagascar', 'formicidae', '" + MYRM + "', 'oldgenus', 1)");
    }

    /** The records of removed rows: "list key|name|source|job|reason", by list and name (a name twice: twice). */
    private List<String> records() throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement("select concat_ws('|', list_key, taxon_name, coalesce(source, ''), removed_by_job, reason) from "
                + ListRemovalLog.TABLE + " where list_type = 'project' and removed_by_login is null order by list_key, taxon_name, id"); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getString(1));
        }
        return out;
    }

    private List<String> genusRows(String project) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement("select l.taxon_name from proj_taxon l join taxon t on t.taxon_name = l.taxon_name"
                + " where l.project_name = ? and t.taxarank = 'genus' order by l.taxon_name")) {
            s.setString(1, project);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) out.add(r.getString(1));
            }
        }
        return out;
    }

    /**
     * An upload rebuilds the AntWeb-wide list. The list it leaves follows the rule, so the daily clean-up finds nothing
     * to remove there and the list's Genera are the same after the upload and after the next daily update (production
     * 2026-11-12: 536 after an upload, 535 after the daily update).
     */
    public void testTheRebuiltAntWebWideListFollowsTheRuleOfTheGenusRows() throws Exception {
        moreGenera();
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals("genus rows after the rebuild: not the genus without species or specimens",
            Arrays.asList(LIOPONERA, OLDGENUS, PHEIDOLE, HYPO), genusRows(ALL));
        // Crematogaster was on the list before this rebuild (setUp) and is not after it: one record, with the source the
        // row had before the rebuild (setUp: worldants), not the one the rebuild gave the row it took off (none here,
        // the test's taxon rows have no source)
        String rebuilt = "|" + ListRemovalLog.JOB_REBUILD_GENUS + "|" + ListRemovalLog.REASON_GENUS_NO_SPECIES;
        String crematogaster = ALL + "|" + CREMATOGASTER + "|worldants" + rebuilt;
        assertEquals(Arrays.asList(crematogaster), records());
        // the next upload's rebuild puts it on the list and takes it off again: it was not on the list before, no second record
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals(Arrays.asList(LIOPONERA, OLDGENUS, PHEIDOLE, HYPO), genusRows(ALL));
        assertEquals("one record, not one per upload", Arrays.asList(crematogaster), records());
        // the row is back on the list as it was (source worldants) and the next rebuild takes it off: the same removal as
        // the newest record, by the source before the rebuild: not recorded again
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('" + ALL + "', '" + CREMATOGASTER + "', 'worldants')");
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals("the same removal again", Arrays.asList(crematogaster), records());
        // a curator put it on the list (Species List Tool): the rebuild gives every row its own source, so only the
        // source before the rebuild says that this is a curator's row, whose removal is always recorded
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('" + ALL + "', '" + CREMATOGASTER + "', 'speciesListTool')");
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals("a curator's row: recorded, with the curator's source",
            Arrays.asList(crematogaster, ALL + "|" + CREMATOGASTER + "|speciesListTool" + rebuilt), records());
        assertEquals(Arrays.asList(LIOPONERA, OLDGENUS, PHEIDOLE, HYPO), genusRows(ALL));
        assertEquals("every other counted name is on the list (the rule is about genus rows only)", 0,
            one("select count(*) from taxon t where t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')"
                + " and t.taxon_name <> '" + CREMATOGASTER + "' and t.taxon_name not in (select taxon_name from proj_taxon where project_name = '" + ALL + "')"));
        assertEquals("no name of another status", 0,
            one("select count(*) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name where l.project_name = '" + ALL + "' and t.status = 'synonym'"));
        counts();
        int afterUpload = genera(ALL)[0];
        assertEquals("Genera stored = listed after the upload", "[4, 4]", Arrays.toString(genera(ALL)));

        // the next daily update: the clean-up takes nothing off that list (madants loses its two rows as before)
        String line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 2 names removed in "));
        assertFalse("nothing removed from the AntWeb-wide list: " + line, line.contains(ALL));
        counts();
        assertEquals("Genera do not move between the upload and the daily update", afterUpload, genera(ALL)[0]);
        assertEquals(Arrays.asList(LIOPONERA, OLDGENUS, PHEIDOLE, HYPO), genusRows(ALL));

        // the specimen of Oldgenus goes (a correction): no species row, no specimen: the next rebuild or clean-up drops it
        exec(connection, "delete from specimen where code = 'old1'");
        line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue(line, line.endsWith(" s; " + ALL + " 1: " + OLDGENUS));
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals(Arrays.asList(LIOPONERA, PHEIDOLE, HYPO), genusRows(ALL));
        assertEquals("Oldgenus: recorded by the clean-up that took it off", 1, one("select count(*) from " + ListRemovalLog.TABLE
            + " where list_key = '" + ALL + "' and taxon_name = '" + OLDGENUS + "' and removed_by_job = '" + ListRemovalLog.JOB_GENUS_NO_SPECIES + "'"));
        // a name a curator removed from the list (a dispute) is not a species row: its genus goes with it
        exec(connection, "delete from specimen where code = 'lio1'");
        exec(connection, "insert into proj_taxon_dispute (project_name, taxon_name) values ('" + ALL + "', '" + LIOPONERA + " (indet)')");
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals(Arrays.asList(PHEIDOLE, HYPO), genusRows(ALL));
        assertEquals("Lioponera was on the list before that rebuild: recorded by it", 1, one("select count(*) from " + ListRemovalLog.TABLE
            + " where list_key = '" + ALL + "' and taxon_name = '" + LIOPONERA + "' and removed_by_job = '" + ListRemovalLog.JOB_REBUILD_GENUS + "'"));
    }

    /**
     * "A red-flagged specimen never counts, in any view, on any list" (claude/antweb-list-design-rules.md): a genus whose
     * only specimen in AntWeb is red-flagged, with no name on the list, has no specimen there and goes from the
     * AntWeb-wide list, by the rebuild and by the clean-up; unflagged, it stays.
     */
    public void testARedFlaggedSpecimenKeepsNoGenusOnTheList() throws Exception {
        moreGenera();
        exec(connection, "update specimen set flag = 'red' where code = 'old1'");
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals("Oldgenus has only a red-flagged specimen", Arrays.asList(LIOPONERA, PHEIDOLE, HYPO), genusRows(ALL));
        // the clean-up, on a list that still has the row
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('" + ALL + "', '" + OLDGENUS + "', 'specimen')");
        ProjTaxonDb db = new ProjTaxonDb(connection);
        assertEquals(1, db.removeGenusRowsWithoutSpeciesOrSpecimen(ALL));
        assertEquals(ALL + " 1: " + OLDGENUS, db.removedRowsText());
        // another flag, or the flag taken off: a specimen of the genus, and the genus stays
        for (String flag : new String[] {"'yellow'", "null"}) {
            exec(connection, "update specimen set flag = " + flag + " where code = 'old1'");
            new ProjTaxonDb(connection).rebuildAllAntwebList();
            assertEquals("flag " + flag, Arrays.asList(LIOPONERA, OLDGENUS, PHEIDOLE, HYPO), genusRows(ALL));
        }
        // the condition is the lists' own
        assertTrue(ProjTaxonDb.antwebSpecimenOfGenusSql(true).contains(org.calacademy.antweb.home.SpecimenDb.notRedFlagged("s")));
    }

    /**
     * The specimen query of the rule, asked once per genus row without a species row at each rebuild of the AntWeb-wide
     * list (every upload): with production's index genus_species_idx (genus, species) it reads by the genus, the
     * index's first column, with or without the subfamily (EXPLAIN, as PlaceViewTotalDbTest for the place queries).
     */
    public void testTheSpecimenQueryCanUseTheGenusIndex() throws Exception {
        // as on production; only when the table does not have it yet (a second run in a schema that kept the table)
        if (one("select count(*) from information_schema.statistics where table_schema = database() and table_name = 'specimen'"
                + " and index_name = 'genus_species_idx'") == 0) {
            exec(connection, "alter table specimen add index genus_species_idx (genus, species)");
        }
        for (boolean withSubfamily : new boolean[] {true, false}) {
            String sql = ProjTaxonDb.antwebSpecimenOfGenusSql(withSubfamily);
            try (PreparedStatement s = connection.prepareStatement("explain " + sql)) {
                s.setString(1, "pheidole");
                if (withSubfamily) s.setString(2, MYRM);
                try (ResultSet r = s.executeQuery()) {
                    assertTrue(r.next());
                    assertEquals("s", r.getString("table"));
                    assertTrue(sql + ": possible_keys " + r.getString("possible_keys"), String.valueOf(r.getString("possible_keys")).contains("genus_species_idx"));
                    // (which key is used on a table of a few rows is the optimizer's choice; that it can use this one is the point)
                }
            }
        }
    }

    // ------------------------------------------------------------------ a deadlock is passed on (review of #347)

    /** Which errors mean the transaction is gone: a deadlock (1213), not a lock wait timeout (1205), also as a cause. */
    public void testWhichErrorsMeanTheTransactionIsGone() {
        assertTrue(org.calacademy.antweb.util.DBUtil.transactionRolledBack(new java.sql.SQLException("Deadlock found", "40001", 1213)));
        assertTrue("as the cause of another error", org.calacademy.antweb.util.DBUtil.transactionRolledBack(
            new IllegalStateException("x", new RuntimeException(new java.sql.SQLException("Deadlock found", "40001", 1213)))));
        assertTrue("another serialization failure", org.calacademy.antweb.util.DBUtil.transactionRolledBack(new java.sql.SQLException("x", "40001", 0)));
        assertFalse("a lock wait timeout undoes one statement (the driver gives it the state 40001 too)",
            org.calacademy.antweb.util.DBUtil.transactionRolledBack(new java.sql.SQLException("Lock wait timeout exceeded", "40001", 1205)));
        assertFalse(org.calacademy.antweb.util.DBUtil.transactionRolledBack(new java.sql.SQLException("Unknown column", "42S22", 1054)));
        assertFalse(org.calacademy.antweb.util.DBUtil.transactionRolledBack(new RuntimeException("x")));
        assertFalse(org.calacademy.antweb.util.DBUtil.transactionRolledBack(null));
    }

    /**
     * A specimen upload is one transaction on one connection: its specimens, then the rebuild of the AntWeb-wide list,
     * one commit at the end (SpecimenUploadJob). Here the rule of the genus rows meets a real deadlock in the rebuild:
     * another transaction holds the record table and waits for a list row the rebuild holds; the rebuild then asks for
     * the record table, and the database rolls the upload's transaction back. The rebuild must pass that on. Swallowed
     * (until 2026-11-12), the upload went on, committed nothing of its specimens and reported success.
     *
     * Which of the two the database rolls back: the one of smaller weight (InnoDB: the rows a transaction changed plus
     * the locks it holds; information_schema.innodb_trx.trx_weight, "InnoDB selects the transaction with the smallest
     * weight as the victim"). The other transaction changes 2,000 rows and locks them all; the upload's so far is the
     * rebuild of the fixture's list, some dozens of rows. Where the test's user may read innodb_trx (not in CI: it
     * needs the PROCESS privilege) the test waits until the other transaction is seen waiting and checks the two
     * weights before the rebuild goes on. The order does not decide: if the rebuild asks first and the other
     * transaction's request closes the circle, the weights are the same. If the other transaction is rolled back all
     * the same, or the preparation itself fails, the test says that, not "the rebuild swallowed the deadlock".
     */
    public void testADeadlockInTheRebuildIsPassedOn() throws Exception {
        exec(connection, "set innodb_lock_wait_timeout = 5");   // no deadlock found: fail in seconds, not wait
        connection.setAutoCommit(false);
        Thread[] waiter = new Thread[1];
        Throwable[] preparationFailed = new Throwable[1];
        java.sql.SQLException[] otherRolledBack = new java.sql.SQLException[1];
        String[] weights = {"not read"};
        try (Connection other = connect(); Connection watch = connect()) {
            try {
                int uploadId = one("select connection_id()");
                exec(connection, "update project set specimen_count = 4321 where project_name = 'madants'");   // the upload's own writes
                ProjTaxonDb.setBeforeGenusRuleForTests(() -> {
                    try {
                        int otherId;
                        try (PreparedStatement s = other.prepareStatement("select connection_id()"); ResultSet r = s.executeQuery()) {
                            r.next();
                            otherId = r.getInt(1);
                        }
                        other.setAutoCommit(false);
                        // a much larger transaction than the upload's so far (the database rolls back the smaller of the two)
                        StringBuilder rows = new StringBuilder();
                        for (int i = 0; i < 2000; i++) rows.append(i == 0 ? "" : ", ").append("('project', 'weight', 'w").append(i).append("', now())");
                        exec(other, "insert into " + ListRemovalLog.TABLE + " (list_type, list_key, taxon_name, removed_at) values " + rows);
                        try (PreparedStatement s = other.prepareStatement("select id from " + ListRemovalLog.TABLE + " for update"); ResultSet r = s.executeQuery()) {
                            while (r.next()) { /* every row and the gap after them are locked */ }
                        }
                        // and it waits for a row the rebuild has just put on the list
                        java.util.concurrent.CountDownLatch asking = new java.util.concurrent.CountDownLatch(1);
                        waiter[0] = new Thread(() -> {
                            asking.countDown();
                            try {
                                exec(other, "update proj_taxon set source = 'x' where project_name = '" + ALL + "' and taxon_name = '" + CREMATOGASTER + "'");
                            } catch (java.sql.SQLException e) {
                                otherRolledBack[0] = e;   // the other side lost the deadlock: said below
                            }
                        });
                        waiter[0].start();
                        assertTrue("the other transaction's thread did not start", asking.await(10, java.util.concurrent.TimeUnit.SECONDS));
                        int[] w = weightsOnceTheOtherWaits(watch, uploadId, otherId);
                        if (w != null) {
                            weights[0] = "upload " + w[0] + ", other " + w[1];
                            assertTrue("the upload's transaction was not found in innodb_trx: " + weights[0], w[0] >= 0);
                            assertTrue("the other transaction must be the heavier one, or the database rolls it back instead: " + weights[0], w[1] > w[0]);
                        }
                    } catch (Throwable e) {
                        preparationFailed[0] = e;
                        throw new RuntimeException(e);
                    }
                });
                java.sql.SQLException thrown = null;
                try {
                    new ProjTaxonDb(connection).rebuildAllAntwebList();
                } catch (java.sql.SQLException e) {
                    thrown = e;
                }
                ProjTaxonDb.setBeforeGenusRuleForTests(null);
                if (waiter[0] != null) waiter[0].join(10000);
                if (preparationFailed[0] != null) {
                    throw new AssertionError("the test's own preparation failed, not the rebuild: " + preparationFailed[0], preparationFailed[0]);
                }
                assertNull("the database rolled back the other transaction, not the upload's (weights: " + weights[0] + "): " + otherRolledBack[0], otherRolledBack[0]);
                assertNotNull("the rebuild swallowed the deadlock: the upload would go on on an empty transaction", thrown);
                assertTrue("" + thrown, org.calacademy.antweb.util.DBUtil.transactionRolledBack(thrown));
                assertEquals(1213, thrown.getErrorCode());
                assertTrue("which row had no record: " + thrown, thrown.getMessage().startsWith("no record could be written of the removal of "
                    + CREMATOGASTER + " from the list of " + ALL + ", so the row is not deleted: "));
                // why it must be passed on: the upload's own writes are gone from its transaction
                assertEquals("the upload's write was rolled back by the database", 5, one("select specimen_count from project where project_name = 'madants'"));
                other.rollback();
                connection.rollback();
                assertEquals("the list is as before the rebuild", Arrays.asList(CREMATOGASTER, PHEIDOLE, HYPO), genusRows(ALL));
            } finally {
                ProjTaxonDb.setBeforeGenusRuleForTests(null);
                if (waiter[0] != null) waiter[0].join(10000);   // before its connection closes
                try { other.rollback(); } catch (java.sql.SQLException e) { /* closed or never opened a transaction */ }
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Waits (up to 5 s) until the other session's transaction waits for a lock, and returns the weights of the upload's
     * transaction and of the other one, as the database has them (information_schema.innodb_trx). Null when the test's
     * user may not read that table (the PROCESS privilege; CI's user has the test database only): then a pause.
     * Read every 250 ms, not faster: the database refreshes what that table shows only when the last read of it is
     * more than 100 ms old, so a faster reader keeps seeing its first answer.
     */
    private static int[] weightsOnceTheOtherWaits(Connection watch, int uploadId, int otherId) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (true) {
            Thread.sleep(250);
            int[] w = {-1, -1};
            boolean waits = false;
            try (PreparedStatement s = watch.prepareStatement("select trx_mysql_thread_id, trx_weight, trx_state from information_schema.innodb_trx"
                    + " where trx_mysql_thread_id in (?, ?)")) {
                s.setInt(1, uploadId);
                s.setInt(2, otherId);
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        boolean isOther = r.getInt(1) == otherId;
                        w[isOther ? 1 : 0] = r.getInt(2);
                        if (isOther && "LOCK WAIT".equals(r.getString(3))) waits = true;
                    }
                }
            } catch (java.sql.SQLException e) {
                Thread.sleep(700);
                return null;
            }
            if (waits || System.currentTimeMillis() > end) return w;
        }
    }

    /**
     * A lock wait timeout in the same place undoes the one statement that waited (the record), not the transaction:
     * the rebuild logs it and goes on, and the upload's writes are still in its transaction. The record was not
     * written, so the genus row is not taken off the list (Brian Fisher, 2026-11-03: no job deletes list entries
     * without a record a curator can see; until 2026-11-12 the row was deleted and its record was missing). The next
     * rebuild, with the record table free, takes it off and records it.
     */
    public void testALockWaitTimeoutInTheRebuildIsNotPassedOn() throws Exception {
        exec(connection, "set innodb_lock_wait_timeout = 1");
        connection.setAutoCommit(false);
        try (Connection other = connect()) {
            other.setAutoCommit(false);
            exec(other, "insert into " + ListRemovalLog.TABLE + " (list_type, list_key, taxon_name, removed_at) values ('project', 'held', 'h', now())");
            try (PreparedStatement s = other.prepareStatement("select id from " + ListRemovalLog.TABLE + " for update"); ResultSet r = s.executeQuery()) {
                while (r.next()) { /* locked */ }
            }
            exec(connection, "update project set specimen_count = 4321 where project_name = 'madants'");
            new ProjTaxonDb(connection).rebuildAllAntwebList();   // does not throw
            assertEquals("the upload's write is still in its transaction", 4321, one("select specimen_count from project where project_name = 'madants'"));
            assertEquals("no record could be written: the genus row is still on the list", Arrays.asList(CREMATOGASTER, PHEIDOLE, HYPO), genusRows(ALL));
            other.rollback();
            connection.commit();
            assertEquals("the upload's write stands", 4321, one("select specimen_count from project where project_name = 'madants'"));
            assertEquals("still on the list after the commit", Arrays.asList(CREMATOGASTER, PHEIDOLE, HYPO), genusRows(ALL));
            assertEquals("no record, and no removal without one", 0, records().size());
            // the next upload's rebuild, nothing in the way: off the list, with its record (the source the last rebuild gave it: none)
            new ProjTaxonDb(connection).rebuildAllAntwebList();
            connection.commit();
            assertEquals(Arrays.asList(PHEIDOLE, HYPO), genusRows(ALL));
            assertEquals(Arrays.asList(ALL + "|" + CREMATOGASTER + "||" + ListRemovalLog.JOB_REBUILD_GENUS + "|" + ListRemovalLog.REASON_GENUS_NO_SPECIES), records());
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * The daily clean-up, the same rule: the record's insert waits for the record table (a Species List Tool save
     * holds it, for example) until the lock wait timeout. No row whose record failed is deleted: the line says 0 rows
     * removed and names, for each of the two clean-ups, the row that has no record; the clean-up has failed
     * (cleanupFailed), which makes set 2's step and the jobs by hand roll back and say so
     * (testSet2sStepCommitsTheDataCleanupAndRollsBackOnlyItself, testTheJobsByHandSayAFailureAndRollBack). Until
     * 2026-11-12 the rows were deleted, the line named them as removed and no record was written. With the table free
     * the step removes and records them.
     */
    public void testARowWhoseRemovalCannotBeRecordedStaysOnItsList() throws Exception {
        connection.setAutoCommit(false);
        try (Connection other = connect()) {
            other.setAutoCommit(false);
            exec(other, "insert into " + ListRemovalLog.TABLE + " (list_type, list_key, taxon_name, removed_at) values ('project', 'held', 'h', now())");
            try (PreparedStatement s = other.prepareStatement("select id from " + ListRemovalLog.TABLE + " for update"); ResultSet r = s.executeQuery()) {
                while (r.next()) { /* locked */ }
            }
            exec(connection, "set innodb_lock_wait_timeout = 1");
            ProjTaxonDb db = new ProjTaxonDb(connection);
            String line = db.cleanupLists();
            String noRecord = ".*no record could be written of the removal of %s from the list of %s, so the row is not deleted: .*";
            assertTrue(line, line.matches("(?s)" + ProjTaxonDb.CLEANUP_LOG + ": 0 names removed in \\d+ s; FAILED: proxy rows: "
                + String.format(noRecord, DORYLINAE, "madants") + "; genus rows: " + String.format(noRecord, CREMATOGASTER, ALL)));
            assertTrue("a failed record is an error of the clean-up: its caller rolls the step back", db.cleanupFailed());
            assertEquals("no row is named as removed", "", db.removedRowsText());
            assertEquals("no project's totals to count again", 0, db.getProjectsWithRowsRemoved().size());
            assertEquals("every row is still on its list, in the clean-up's own transaction", 4,
                one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')"));
            connection.rollback();
            other.rollback();
            assertEquals(0, records().size());
            // the record table is free: set 2's step removes the three rows and records each
            line = UtilDataAction.cleanProjectLists(connection);
            assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 names removed in "));
            assertEquals(3, records().size());
            assertEquals("the AntCat list's row stays", 1, one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')"));
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------ the admin jobs say a failure (review of #347)

    /**
     * utilData.do?action=verifyProjTaxon and cleanupSpeciesListProxyRecords, by hand. The genus clean-up fails on its
     * second project (another transaction holds the row, until the lock timeout) after it removed and recorded a row
     * of the first: the job's answer says FAILED with the error, and what it removed and recorded is rolled back.
     * Until 2026-11-12 the answer was as after a clean-up that found nothing more. Without an error: the rows by name.
     */
    public void testTheJobsByHandSayAFailureAndRollBack() throws Exception {
        connection.setAutoCommit(false);
        try (Connection other = connect()) {
            other.setAutoCommit(false);
            try (PreparedStatement s = other.prepareStatement("select taxon_name from proj_taxon where project_name = 'madants' and taxon_name = '"
                    + CREMATOGASTER + "' for update"); ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
            }
            exec(connection, "set innodb_lock_wait_timeout = 1");
            String answer = UtilDataAction.cleanListsByHand(connection, "verifyProjTaxon");
            assertTrue(answer, answer.startsWith("verifyProjTaxon Records:; FAILED: genus rows: "));
            assertTrue(answer, answer.endsWith("; no row removed (rolled back)"));
            assertFalse("escaped for the job page: " + answer, answer.contains("<") || answer.contains("'"));
            other.rollback();
            connection.commit();
            assertEquals("the row removed from the first project is back", Arrays.asList(CREMATOGASTER, PHEIDOLE, HYPO), genusRows(ALL));
            assertEquals("and no record of it", 0, records().size());

            // nothing in the way: the rows by project and name, the list totals counted again
            answer = UtilDataAction.cleanListsByHand(connection, "verifyProjTaxon");
            assertTrue(answer, answer.startsWith("verifyProjTaxon Records:deleteProjTaxon:2; rows removed from the lists of 2 projects: "
                + ALL + " 1: " + CREMATOGASTER + "; madants 1: " + CREMATOGASTER + "; list totals counted again for "));
            assertFalse(answer, answer.contains("FAILED"));
            answer = UtilDataAction.cleanListsByHand(connection, "cleanupSpeciesListProxyRecords");
            assertTrue(answer, answer.startsWith("Cleanup Species List Proxy Records:deleteProjTaxon:1; rows removed from the lists of 1 project: madants 1: " + DORYLINAE + "; "));
            connection.commit();
            assertEquals(3, records().size());
            // nothing left to remove
            assertEquals("verifyProjTaxon Records:null", UtilDataAction.cleanListsByHand(connection, "verifyProjTaxon"));
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ------------------------------------------------------------------ a recurring removal is recorded once (review of #347)

    /** The import (or a curator) puts Crematogaster back on madants with this source; the clean-up takes it off; the records then. */
    private int putBackAndClean(String source) throws Exception {
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('madants', '" + CREMATOGASTER + "', '" + source + "')");
        String line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue("removed again: " + line, line.endsWith(" s; madants 1: " + CREMATOGASTER));
        return one("select count(*) from " + ListRemovalLog.TABLE + " where list_key = 'madants' and taxon_name = '" + CREMATOGASTER + "'");
    }

    /**
     * The AntCat import puts the same fossil genera without species on the Fossil Ants list every day, and set 2 takes
     * them off every day (staging 2026-11-12: dissimulodorylus, pseudosirex, formicites, myrmicites). The first removal
     * is recorded; the same removal on the days after is not recorded again. Recorded again: a row a curator put back
     * (its source says so), the import's row after that (the newest record is another one), and the import's row after
     * a curator's own removal.
     */
    public void testTheSameRemovalDayAfterDayIsRecordedOnce() throws Exception {
        new ProjTaxonDb(connection).cleanupLists();
        assertEquals("day 1: the three rows", 3, records().size());
        // days 2 and 3: the import puts the row back with the source it had, the clean-up takes it off: no new record
        assertEquals("day 2", 1, putBackAndClean("specimen"));
        assertEquals("day 3", 1, putBackAndClean("specimen"));
        assertEquals(3, records().size());
        // a curator puts it on the list (Species List Tool); without a species it goes again: recorded, with that source
        assertEquals("a curator's row", 2, putBackAndClean("speciesListTool"));
        assertEquals("a curator's row is recorded every time", 3, putBackAndClean("speciesListTool"));
        assertEquals("the parent row a curator's entry made, too", 4, putBackAndClean("proxyspeciesListTool"));
        // the import's row again: the newest record is the curator's row, another one: recorded; the day after, not
        assertEquals("the import's row after the curator's", 5, putBackAndClean("specimen"));
        assertEquals("and the day after", 5, putBackAndClean("specimen"));
        // a curator removes the name with the Species List Tool (a record with a login); the import's row after that: recorded
        exec(connection, "insert into " + ListRemovalLog.TABLE + " (list_type, list_key, taxon_name, source, removed_by_login, reason, removed_at)"
            + " values ('project', 'madants', '" + CREMATOGASTER + "', 'specimen', 7, 'not in Madagascar', now() + interval 1 second)");
        assertEquals("after a curator's own removal", 7, putBackAndClean("specimen"));
        // a row of another source than the recorded one is another removal
        assertEquals("another source", 8, putBackAndClean("antcat"));
        // the record itself: the first of the import's rows stands, with its date
        assertEquals(1, one("select count(*) from (select min(id) from " + ListRemovalLog.TABLE + " where list_key = 'madants' and taxon_name = '"
            + CREMATOGASTER + "' and source = 'specimen' and removed_by_login is null and removed_at is not null) x"));
    }

    // ------------------------------------------------------------------ set 2's step: its own commit and rollback

    /**
     * Set 2 runs in one transaction. Its step commits what the data clean-up did before it starts, so a deadlock in the
     * list clean-up (the database then rolls back the whole open transaction) cannot undo the data clean-up. Here the
     * genus clean-up fails (another transaction holds a row it must delete, until the lock timeout) after the proxy row
     * was removed: the step's own work is rolled back, the line says that no row was removed, no record is left of
     * rows that are still there, and the data clean-up's work is committed. Then, with nothing in the way, the step
     * removes, records and commits.
     */
    public void testSet2sStepCommitsTheDataCleanupAndRollsBackOnlyItself() throws Exception {
        connection.setAutoCommit(false);
        try (Connection other = connect(); Connection reader = connect()) {
            exec(connection, "update project set specimen_count = 4321 where project_name = 'madants'");   // the data clean-up's work, not committed
            other.setAutoCommit(false);
            try (PreparedStatement s = other.prepareStatement("select taxon_name from proj_taxon where project_name = 'madants' and taxon_name = '"
                    + CREMATOGASTER + "' for update"); ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
            }
            exec(connection, "set innodb_lock_wait_timeout = 1");
            String line = UtilDataAction.cleanProjectLists(connection);
            assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": NOT done, no row removed (rolled back; the next daily update cleans the lists): genus rows: "));
            connection.rollback();   // whatever set 2 would still roll back: nothing of the data clean-up
            other.rollback();
            try (PreparedStatement s = reader.prepareStatement("select (select specimen_count from project where project_name = 'madants'),"
                    + " (select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')),"
                    + " (select count(*) from " + ListRemovalLog.TABLE + ")"); ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals("the data clean-up's work was committed before the step", 4321, r.getInt(1));
                assertEquals("the proxy row removed before the error is back: no row removed", 4, r.getInt(2));
                assertEquals("no record of a row that is still on its list", 0, r.getInt(3));
            }
            // nothing in the way: removed, recorded, committed
            line = UtilDataAction.cleanProjectLists(connection);
            assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 names removed in "));
            connection.rollback();
            try (PreparedStatement s = reader.prepareStatement("select (select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE
                    + "') and project_name <> 'worldants'), (select count(*) from " + ListRemovalLog.TABLE + ")"); ResultSet r = s.executeQuery()) {
                assertTrue(r.next());
                assertEquals("committed by the step", 0, r.getInt(1));
                assertEquals(3, r.getInt(2));
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * A project list is no place: its specimens are those filed under its listed names (Project.getCountSpecimensQuery),
     * which are the genus's species rows. So a genus row without a species row goes from a project list, whatever
     * specimens AntWeb has of the genus; with its "(indet)" name listed it stays. The AntCat list is never cleaned.
     */
    public void testOnAProjectListAGenusNeedsASpeciesRow() throws Exception {
        moreGenera();
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('madants', '" + OLDGENUS + "', 'specimen'),"
            + " ('madants', '" + LIOPONERA + "', 'specimen'), ('madants', '" + LIOPONERA + " (indet)', 'specimen'),"
            + " ('worldants', '" + OLDGENUS + "', 'worldants')");
        ProjTaxonDb db = new ProjTaxonDb(connection);
        assertEquals("Crematogaster and Oldgenus", 2, db.removeGenusRowsWithoutSpeciesOrSpecimen("madants"));
        assertEquals(Arrays.asList(LIOPONERA, PHEIDOLE, HYPO), genusRows("madants"));
        assertEquals("madants 2: " + CREMATOGASTER + ", " + OLDGENUS, db.removedRowsText());
        new ProjTaxonDb(connection).verifyProjTaxon();
        assertEquals("the AntCat list keeps every genus", Arrays.asList(CREMATOGASTER, OLDGENUS), genusRows("worldants"));
        // on the AntWeb-wide list Oldgenus stays: AntWeb has a specimen of it
        exec(connection, "insert into proj_taxon (project_name, taxon_name, source) values ('" + ALL + "', '" + OLDGENUS + "', 'specimen')");
        assertEquals(0, new ProjTaxonDb(connection).removeGenusRowsWithoutSpeciesOrSpecimen(ALL));
    }

    /** A database error (here: no proj_taxon table) is in the line and not thrown: set 2 goes on. */
    public void testAnErrorIsSaidInTheLineAndNotThrown() throws Exception {
        exec(connection, "alter table proj_taxon rename to proj_taxon_gone");
        String line = new ProjTaxonDb(connection).cleanupLists();
        exec(connection, "alter table proj_taxon_gone rename to proj_taxon");
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 0 names removed in "));
        assertTrue(line, line.matches(ProjTaxonDb.CLEANUP_LOG + ": 0 names removed in \\d+ s; FAILED: proxy rows: .*; genus rows: .*"));
        assertEquals("no row was removed", 4, one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')"));
    }
}

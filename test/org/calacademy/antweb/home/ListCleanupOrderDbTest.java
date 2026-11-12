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
import org.calacademy.antweb.home.PlaceGenusCurationDb;
import org.calacademy.antweb.home.PlaceViewTotalDb;
import org.calacademy.antweb.home.ProjTaxonDb;
import org.calacademy.antweb.home.ProjectDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountCheck;

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
        if (connection != null) {
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 rows removed in "));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 rows removed in "));
        assertTrue(line, line.endsWith(" s; madants 2: " + DORYLINAE + ", " + CREMATOGASTER + "; " + ALL + " 1: " + CREMATOGASTER));
        assertFalse(line, line.contains("FAILED"));
        assertEquals(rows - 3, one("select count(*) from proj_taxon"));
        assertEquals("the genus without species and the proxy row are off the two lists", 0,
            one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "') and project_name <> 'worldants'"));
        assertEquals("the AntCat list keeps every genus", 1, one("select count(*) from proj_taxon where project_name = 'worldants' and taxon_name = '" + CREMATOGASTER + "'"));
        assertEquals("the rows with children stay", 2, one("select count(*) from proj_taxon where project_name = 'madants' and taxon_name in ('" + PHEIDOLE + "', '" + HYPO + "')"));

        counts();
        assertEquals("madants Genera stored = listed", "[2, 2]", Arrays.toString(genera("madants")));
        assertEquals(ALL + " Genera stored = listed", "[2, 2]", Arrays.toString(genera(ALL)));
        assertEquals("madants Subfamilies: the proxy row is not counted", 2, one("select subfamily_count from project where project_name = 'madants'"));
        assertEquals("statistics row proj_taxa = the rows", projTaxa()[1], projTaxa()[0]);
        assertEquals("the page's Genera", 2, viewGenera());
        assertEquals("the count check: " + differences(), 0, differences().size());

        // the next day: nothing to remove, and the numbers stand
        line = new ProjTaxonDb(connection).cleanupLists();
        assertTrue(line, line.matches(ProjTaxonDb.CLEANUP_LOG + ": 0 rows removed in \\d+ s"));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 15 rows removed in "));
        // madants: the proxy row, Crematogaster and the twelve genera = 14 rows, ten named
        assertTrue(line, line.contains("; madants 14: " + DORYLINAE + ", " + CREMATOGASTER + ", " + MYRM + "genus11, "));
        assertTrue(line, line.contains(", " + MYRM + "genus18 and 4 more; " + ALL + " 1: " + CREMATOGASTER));
        assertFalse("the eleventh row is counted, not named: " + line, line.contains(MYRM + "genus19"));
        assertEquals(db.removedRowsText(), line.substring(line.indexOf(" s; ") + 4));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 2 rows removed in "));
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
        // a name a curator removed from the list (a dispute) is not a species row: its genus goes with it
        exec(connection, "delete from specimen where code = 'lio1'");
        exec(connection, "insert into proj_taxon_dispute (project_name, taxon_name) values ('" + ALL + "', '" + LIOPONERA + " (indet)')");
        new ProjTaxonDb(connection).rebuildAllAntwebList();
        assertEquals(Arrays.asList(PHEIDOLE, HYPO), genusRows(ALL));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 0 rows removed in "));
        assertTrue(line, line.matches(ProjTaxonDb.CLEANUP_LOG + ": 0 rows removed in \\d+ s; FAILED: proxy rows: .*; genus rows: .*"));
        assertEquals("no row was removed", 4, one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')"));
    }
}

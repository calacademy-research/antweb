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
 *  - a second clean-up removes nothing; a database error is in the line and not thrown.
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 rows removed (madants 2, " + ALL + " 1) in "));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 3 rows removed (madants 2, " + ALL + " 1) in "));
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
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 0 rows removed in "));
        counts();
        assertEquals("the count check: " + differences(), 0, differences().size());
    }

    /** A database error (here: no proj_taxon table) is in the line and not thrown: set 2 goes on. */
    public void testAnErrorIsSaidInTheLineAndNotThrown() throws Exception {
        exec(connection, "alter table proj_taxon rename to proj_taxon_gone");
        String line = new ProjTaxonDb(connection).cleanupLists();
        exec(connection, "alter table proj_taxon_gone rename to proj_taxon");
        assertTrue(line, line.startsWith(ProjTaxonDb.CLEANUP_LOG + ": 0 rows removed in "));
        assertTrue(line, line.contains("; FAILED: proxy rows: ") && line.contains("; genus rows: "));
        assertEquals("no row was removed", 4, one("select count(*) from proj_taxon where taxon_name in ('" + CREMATOGASTER + "', '" + DORYLINAE + "')"));
    }
}

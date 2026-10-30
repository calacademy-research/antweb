package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.home.BioregionTaxonDb;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.MuseumDb;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.WorldantsUploadDb;
import org.calacademy.antweb.util.CountCheck;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * Brian Fisher's decisions of 2026-10-30 against the NumbersTestDb database (MySQL 8.4 in CI, host "mysql";
 * ANTWEB_TEST_DB_URL overrides):
 *
 *   Genera   valid genera and morphogenera (PlaceTotals.countsAsGenus). A country "Testland" lists a valid genus
 *            (Pheidole), a morphogenus, the placeholder "(myrmicinae)" (specimens identified only to subfamily), the
 *            unrecognized "(formicidae)polyrachis" and "Amblyoponinae)" (a stray parenthesis): Genera = 2, by the nightly
 *            totals, the subfamily row's "N Genera" written by the crawl, upload groups, and the count check (which
 *            finds a number counted by the old rule, 5, and calls it pending until a nightly run has counted by the rule).
 *            Subfamilies: the placeholder subfamily row "(formicidae)" (status morphotaxon) is not counted: 1.
 *   Subfamilies   valid subfamilies only (PlaceTotals.countsAsSubfamily; it replaced B67, a curated project's subfamily
 *            rows of every status). Testland and a curated project "testants" list Myrmicinae, "(formicidae)" (status
 *            morphotaxon) and a misspelled "myrmicicinae" (status unrecognized): Subfamilies = 1 each, by the nightly
 *            totals, the Formicidae row's "N Subfamilies" written by the crawl, and the count check.
 *   Worldants alert   WorldantsUploadDb.CHANGE_COUNT_SQL counts the imports of the last 7 days whose file size differs
 *            from the previous import's; equal backup sizes (production since 10-20) do not hide the changes.
 */
public class GeneraRuleDbTest extends TestCase {

    private Connection connection;

    static final int TESTLAND = 50;
    static final String FORM = "(formicidae)";
    static final String MORPH = "myrmicinaemyrm_gen-za01";
    static final String PLACEHOLDER = "myrmicinae(myrmicinae)";
    static final String POLYRACHIS = "(formicidae)polyrachis";
    static final String STRAY = "amblyoponinae)";
    static final String MISSPELLED = "myrmicicinae";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec("update taxon set genus = 'pheidole' where taxon_name = '" + PHEIDOLE + "'");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values"
            + " ('" + FORM + "', 'subfamily', 'morphotaxon', '" + FAMILY + "', 'formicidae', '" + FORM + "', null),"
            + " ('" + MORPH + "', 'genus', 'morphotaxon', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'myrm_gen-za01'),"
            + " ('" + PLACEHOLDER + "', 'genus', 'indetermined', '" + MYRM + "', 'formicidae', '" + MYRM + "', '(myrmicinae)'),"
            + " ('" + POLYRACHIS + "', 'genus', 'unrecognized', '" + FORM + "', 'formicidae', '" + FORM + "', 'polyrachis'),"
            + " ('" + STRAY + "', 'genus', 'morphotaxon', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'amblyoponinae)')");
        // Testland: no specimens; its list and its numbers as the nightly run writes them by the rule of 2026-10-30.
        exec("insert into geolocale (id, name, georank, parent, is_valid, is_live, subfamily_count, genus_count) values"
            + " (" + TESTLAND + ", 'Testland', 'country', 'Nowhere', 1, 1, 1, 2)");
        row(FAMILY, 1, 2);
        row(MYRM, 0, 2);
        row(FORM, 0, 0);
        for (String g : new String[] {PHEIDOLE, MORPH, PLACEHOLDER, POLYRACHIS, STRAY}) row(g, 0, 0);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private void row(String name, int subfamilies, int genera) throws Exception {
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, subfamily_count, genus_count, created) values (" + TESTLAND + ", '"
            + name + "', " + subfamilies + ", " + genera + ", '2020-01-01 10:00:00')");
    }

    /** Brian Fisher, 2026-10-30: Subfamilies count valid subfamilies only, on every kind of place, curated projects too. */
    public void testSubfamiliesCountValidSubfamiliesOnly() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('" + MISSPELLED + "', 'subfamily', 'unrecognized', '" + FAMILY + "', 'formicidae', '" + MISSPELLED + "')");
        row(MISSPELLED, 0, 0);
        // a curated project with the same three subfamily rows; its numbers as the nightly run writes them by the rule
        exec("insert into project (project_name, is_live, subfamily_count) values ('testants', 1, 1)");
        for (String n : new String[] {FAMILY, MYRM, FORM, MISSPELLED}) {
            exec("insert into proj_taxon (project_name, taxon_name, subfamily_count, created) values ('testants', '" + n + "', "
                + (FAMILY.equals(n) ? 1 : 0) + ", '2020-01-01 10:00:00')");
        }
        String sql = PlaceTotals.listedCountSql(PlaceTotals.GEOLOCALE, PlaceTotals.SUBFAMILY);
        assertEquals("the country: Myrmicinae only", 1, PlaceTotals.count(connection, sql, "" + TESTLAND));
        sql = PlaceTotals.listedCountSql(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY);
        assertEquals("the curated project: Myrmicinae only (B67 counted all 3)", 1, PlaceTotals.count(connection, sql, "testants"));
        assertEquals("madants of the fixture: Myrmicinae and Ponerinae", 2, PlaceTotals.count(connection, sql, "madants"));
        assertEquals(1, PlaceTotals.listedCounts(connection, PlaceTotals.PROJECT, "testants")[0]);
        // all places at once (the count check's inv:place.totals.subfamily)
        Map<String, Integer> byPlace = new TreeMap<>();
        try (PreparedStatement s = connection.prepareStatement(PlaceTotals.listedCountsByPlaceSql(PlaceTotals.PROJECT, PlaceTotals.SUBFAMILY));
             ResultSet r = s.executeQuery()) {
            while (r.next()) byPlace.put(r.getString(1), r.getInt(2));
        }
        assertEquals(byPlace.toString(), Integer.valueOf(1), byPlace.get("testants"));
        // the rows stay on the lists
        assertEquals(3, PlaceTotals.count(connection, "select count(*) from proj_taxon l join taxon t on t.taxon_name = l.taxon_name"
            + " where l.project_name = ? and t.taxarank = 'subfamily'", "testants"));
        // the crawl: the Formicidae row's Subfamilies
        Country testland = new Country();
        testland.setId(TESTLAND);
        testland.setName("Testland");
        assertEquals(Integer.valueOf(1), counts(testland.getCountChildrenQuery("subfamily")).get(FAMILY));
        Map<String, Integer> project = counts(new Project("testants").getCountChildrenQuery("subfamily"));
        assertEquals(project.toString(), Integer.valueOf(1), project.get(FAMILY));
        // the count check: right numbers pass; the number of the old rule (B67: 3) is a real difference
        CountCheck.Result r = check(true);
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("place.totals.subfamily".equals(m.check)) {
                assertFalse("Subfamilies are right by the rule: " + list(r), "Testland".equals(m.place.label) || "testants".equals(m.place.key));
            }
        }
        exec("update project set subfamily_count = 3 where project_name = 'testants'");
        r = check(true);
        boolean found = false;
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("place.totals.subfamily".equals(m.check) && "testants".equals(m.place.key)) {
                assertEquals(3, m.stored);
                assertEquals(1, m.recount);
                assertTrue(m.isReal());
                found = true;
            }
        }
        assertTrue(list(r), found);
    }

    private CountCheck.Result check(boolean rulesCounted) throws Exception {
        CountCheck check = new CountCheck(connection);
        check.curatorNumbers = curatorNumbers();
        check.artistNumbers = artistNumbers();
        return check.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, rulesCounted, true, null);
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m.check).append(": ").append(m);
        return b.toString();
    }

    public void testGeneraCountValidGeneraAndMorphogeneraOnly() throws Exception {
        String key = "" + TESTLAND;
        assertEquals("Pheidole and the morphogenus", 2, PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.GEOLOCALE, PlaceTotals.GENUS), key));
        assertEquals("Myrmicinae; not the placeholder (formicidae)", 1,
            PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.GEOLOCALE, PlaceTotals.SUBFAMILY), key));
        int[] n = PlaceTotals.listedCounts(connection, PlaceTotals.GEOLOCALE, key);
        assertEquals(1, n[0]);
        assertEquals(2, n[1]);
        // the rows stay on the list (listing is a separate change)
        assertEquals(5, PlaceTotals.count(connection, "select count(*) from geolocale_taxon l join taxon t on t.taxon_name = l.taxon_name"
            + " where l.geolocale_id = ? and t.taxarank = 'genus'", key));
        // Madagascar of the fixture: unchanged
        int[] mad = PlaceTotals.listedCounts(connection, PlaceTotals.GEOLOCALE, "" + MADAGASCAR);
        assertEquals(2, mad[0]);
        assertEquals(2, mad[1]);
    }

    public void testTheCrawlCountsTheGeneraOfASubfamilyRowByTheRule() throws Exception {
        Country testland = new Country();
        testland.setId(TESTLAND);
        testland.setName("Testland");
        Map<String, Integer> genera = counts(testland.getCountChildrenQuery("genus"));
        assertEquals(genera.toString(), Integer.valueOf(2), genera.get(MYRM));
        assertNull("the unrecognized genus under (formicidae) is not counted: " + genera, genera.get(FORM));
        Map<String, Integer> subfamilies = counts(testland.getCountChildrenQuery("subfamily"));
        assertEquals("the Formicidae row's Subfamilies: Myrmicinae only " + subfamilies, Integer.valueOf(1), subfamilies.get(FAMILY));
    }

    private Map<String, Integer> counts(String sql) throws Exception {
        Map<String, Integer> m = new TreeMap<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) m.put(r.getString("parentTaxonName"), r.getInt("count"));
        }
        return m;
    }

    public void testTheCountCheckUsesTheSameRule() throws Exception {
        CountCheck.Result r = check(true);
        for (CountCheck.Mismatch m : r.mismatches) {
            assertFalse("Testland's numbers are right by the rule: " + list(r), "Testland".equals(m.place.label));
        }
        // the numbers of the old rule (every counted status): Genera 5, Myrmicinae's N Genera 4, Subfamilies 2
        exec("update geolocale set genus_count = 5, subfamily_count = 2 where id = " + TESTLAND);
        exec("update geolocale_taxon set genus_count = 4 where geolocale_id = " + TESTLAND + " and taxon_name = '" + MYRM + "'");
        r = check(true);
        List<String> found = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) {
            if (!"Testland".equals(m.place.label)) continue;
            assertTrue("a real difference once a nightly run has counted by the rule: " + m, m.isReal());
            found.add(m.check + " " + m.stored + "/" + m.recount);
        }
        assertTrue(list(r), found.contains("place.totals.genus 5/2"));
        assertTrue(list(r), found.contains("place.totals.subfamily 2/1"));
        assertTrue(list(r), found.contains("list.subfamily 4/2"));
        // before the nightly run has written GENERA_RULE_LOG: pending
        r = check(false);
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("Testland".equals(m.place.label)) assertFalse("pending until the nightly run: " + m, m.isReal());
        }
        // the sample recount of one place (CountCheck.checkChildren): the subfamily row's Genera by the rule too
        CountCheck.Place testland = new CountCheck.Place(CountCheck.GEOLOCALE, "" + TESTLAND, "Testland", "country", "Testland");
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(testland);
        CountCheck c = new CountCheck(connection);
        r = c.run(places, new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, false, null);
        boolean genera = false;
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("genera".equals(m.check) && MYRM.equals(m.taxonName)) {
                assertEquals(4, m.stored);
                assertEquals(2, m.recount);
                genera = true;
            }
        }
        assertTrue(list(r), genera);
        exec("update geolocale_taxon set genus_count = 2 where geolocale_id = " + TESTLAND + " and taxon_name = '" + MYRM + "'");
        r = c.run(places, new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, false, null);
        for (CountCheck.Mismatch m : r.mismatches) assertFalse(list(r), "genera".equals(m.check));
    }

    public void testUploadGroupGeneraCountValidGeneraAndMorphogenera() throws Exception {
        // group 1 of the fixture: Pheidole and Hypoponera (2). Add specimens of the unrecognized genus, the morphogenus and
        // one identified only to subfamily.
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily, genus, access_group) values"
            + " ('g1', '(formicidae)polyrachis sp1', 'unrecognized', 'Madagascar', 'formicidae', '(formicidae)', 'polyrachis', 1),"
            + " ('g2', 'myrmicinaemyrm_gen-za01 za01', 'morphotaxon', 'Madagascar', 'formicidae', 'myrmicinae', 'myrm_gen-za01', 1),"
            + " ('g3', 'myrmicinae(myrmicinae) (indet)', 'indetermined', 'Madagascar', 'formicidae', 'myrmicinae', '(myrmicinae)', 1)");
        int genera = -1;
        try (PreparedStatement s = connection.prepareStatement(GroupCountDb.groupCountsSql())) {
            s.setInt(1, 1);
            try (ResultSet r = s.executeQuery()) {
                if (r.next()) genera = r.getInt(8);   // g, all, specimens, collections, localities, ungeoreferenced, subfamilies, genera
            }
        }
        assertEquals("Pheidole, Hypoponera and the morphogenus", 3, genera);
        String[] totals = GroupCountDb.totalsSqls("1");
        int total = -1;
        for (String sql : totals) {
            if (!sql.contains("s.genus")) continue;
            try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
                if (r.next()) total = r.getInt(1);
            }
        }
        assertEquals(3, total);
    }

    public void testWorldantsChangeCountComparesWithThePreviousImport() throws Exception {
        exec("drop table if exists worldants_upload");
        exec("create table worldants_upload (id int not null auto_increment primary key, file_size varchar(200), backup_file_size int,"
            + " backup_file_name varchar(200), orig_worldants_count int, validate_message varchar(1000),"
            + " created timestamp default current_timestamp, log_file_name varchar(100), exec_time varchar(100), operation varchar(100))");
        WorldantsUploadDb db = new WorldantsUploadDb(connection);
        assertEquals("no imports", 0, db.getWorldantsChangeCount());
        // 10 days ago (before the week), then daily; the backup size equals the file size on every row (production 10-20 on)
        String[][] rows = {
            {"10", "139370000"},   // the previous import of the first row in the week
            {"6", "139370000"},    // no change
            {"5", "139376773"},    // change
            {"4", "139379248"},    // change
            {"3", "139379248"},    // no change
            {"2", "0"},            // no file size: left out
            {"1", "139380000"},    // change (against 139379248, the row before the one without a size)
        };
        for (String[] r : rows) {
            exec("insert into worldants_upload (file_size, backup_file_size, created) values ('" + r[1] + "', " + r[1]
                + ", now() - interval " + r[0] + " day)");
        }
        assertEquals(3, db.getWorldantsChangeCount());
        // a week without changes: the alert's case
        exec("delete from worldants_upload");
        exec("insert into worldants_upload (file_size, backup_file_size, created) values ('139379248', 139379248, now() - interval 9 day),"
            + " ('139379248', 139379248, now() - interval 3 day), ('139379248', 139379248, now() - interval 1 day)");
        assertEquals(0, db.getWorldantsChangeCount());
        // the only import has no previous one: no change
        exec("delete from worldants_upload");
        exec("insert into worldants_upload (file_size, backup_file_size, created) values ('139379248', 0, now() - interval 1 day)");
        assertEquals(0, db.getWorldantsChangeCount());
        // the first import in the week changed against the one before the week
        exec("insert into worldants_upload (file_size, backup_file_size, created) values ('139000000', 0, now() - interval 9 day)");
        assertEquals(1, db.getWorldantsChangeCount());
        exec("drop table worldants_upload");
    }

    // ---- Review of #307 (2026-10-30) ----

    private List<String> column(String sql, String col) throws Exception {
        List<String> out = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(sql); ResultSet r = s.executeQuery()) {
            while (r.next()) out.add(r.getString(col));
        }
        return out;
    }

    /**
     * The choices Valid and Valid + morphotaxa list the rows the place page's number of the rank counts (StatusSet
     * .listRankCondition, TaxaPage.taxaQuery: the list's own query): Testland's genus list under Valid genera +
     * morphogenera has Pheidole and the morphogenus (= Genera 2), not "(myrmicinae)", "(formicidae)polyrachis" or the
     * morphotaxon "amblyoponinae)"; its subfamily list has Myrmicinae (= Subfamilies 1), not "(formicidae)" (morphotaxon).
     * All names lists every row.
     */
    public void testValidChoicesListTheRowsTheNumbersCount() throws Exception {
        Country testland = new Country();
        testland.setId(TESTLAND);
        testland.setName("Testland");
        String key = "" + TESTLAND;
        int genera = PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.GEOLOCALE, PlaceTotals.GENUS), key);
        int subfamilies = PlaceTotals.count(connection, PlaceTotals.listedCountSql(PlaceTotals.GEOLOCALE, PlaceTotals.SUBFAMILY), key);
        List<String> g = column(org.calacademy.antweb.TaxaPage.taxaQuery(testland, "genus", true, org.calacademy.antweb.StatusSet.listStatusSet("validmorpho")), "genus");
        assertEquals(g.toString(), genera, g.size());
        assertTrue(g.toString(), g.contains("pheidole") && g.contains("myrm_gen-za01"));
        g = column(org.calacademy.antweb.TaxaPage.taxaQuery(testland, "genus", true, org.calacademy.antweb.StatusSet.listStatusSet("valid")), "genus");
        assertEquals("valid genera: Pheidole " + g, java.util.Arrays.asList("pheidole"), g);
        g = column(org.calacademy.antweb.TaxaPage.taxaQuery(testland, "genus", true, org.calacademy.antweb.StatusSet.listStatusSet("all")), "genus");
        assertEquals("All names: every genus row " + g, 5, g.size());
        List<String> sf = column(org.calacademy.antweb.TaxaPage.taxaQuery(testland, "subfamily", true, org.calacademy.antweb.StatusSet.listStatusSet("validmorpho")), "subfamily");
        assertEquals(sf.toString(), subfamilies, sf.size());
        assertEquals(java.util.Arrays.asList(MYRM), sf);
        sf = column(org.calacademy.antweb.TaxaPage.taxaQuery(testland, "subfamily", true, org.calacademy.antweb.StatusSet.listStatusSet("all")), "subfamily");
        assertTrue("All names: (formicidae) too " + sf, sf.contains(FORM) && sf.contains(MYRM));
        // browse.do's Subfamily list (genus rows of a subfamily) by the same rule
        String browse = "select distinct taxon.genus from taxon" + testland.getFetchChildrenClause() + " and taxon.subfamily = '" + MYRM + "'"
            + " and taxon.genus != '' and taxarank = 'genus' " + org.calacademy.antweb.StatusSet.listStatusSet("validmorpho").getAndCriteria("taxon", "genus");
        assertEquals(column(browse, "genus").toString(), 2, column(browse, "genus").size());
    }

    /**
     * PlaceTotals.countsAsGenus (SQL) and isCountedGenus (Java) are one rule, and so are countsAsSubfamily and
     * isCountedSubfamily: the same fixture values, the same answer (2026-10-30: Java rejected a genus value with ")"
     * while SQL checked only a leading "(" on the genus).
     */
    public void testJavaAndSqlGenusRulesAgree() throws Exception {
        exec("drop table if exists rule_fixture");
        exec("create table rule_fixture (id int primary key, status varchar(40), taxon_name varchar(128), genus varchar(64))");
        String[][] values = {
            {"valid", "myrmicinaepheidole", "pheidole"},
            {"morphotaxon", "myrmicinaemyrm_gen-za01", "myrm_gen-za01"},
            {"unrecognized", "myrmicinaepolyrachis", "polyrachis"},
            {"indetermined", "myrmicinae(myrmicinae)", "(myrmicinae)"},
            {"morphotaxon", "amblyoponinae)", "amblyoponinae)"},
            {"morphotaxon", "amblyoponinaex", "x)"},
            {"morphotaxon", "amblyoponinaey", "y(z"},
            {"valid", "(formicidae)polyrachis", "polyrachis"},
            {"valid", "myrmicinaeindet", "indet"},
            {"valid", "myrmicinaeundet", " Undet "},
            {"valid", "myrmicinae(indet)", "(indet)"},
            {"morphotaxon", "(formicidae)", null},
            {"valid", "myrmicinae", null},
            {"valid", "myrmicinicinae", ""},
            {"synonym", "myrmicinaeoldname", "oldname"},
            {null, "myrmicinaenostatus", "nostatus"},
        };
        for (int i = 0; i < values.length; i++) {
            String[] v = values[i];
            exec("insert into rule_fixture values (" + i + ", " + q(v[0]) + ", " + q(v[1]) + ", " + q(v[2]) + ")");
        }
        for (int i = 0; i < values.length; i++) {
            String[] v = values[i];
            int sqlGenus = PlaceTotals.count(connection, "select count(*) from rule_fixture a where a.id = ? and " + PlaceTotals.countsAsGenus("a"), "" + i);
            assertEquals(java.util.Arrays.toString(v) + " genus", PlaceTotals.isCountedGenus(v[0], v[1], v[2]) ? 1 : 0, sqlGenus);
            int sqlSubfamily = PlaceTotals.count(connection, "select count(*) from rule_fixture a where a.id = ? and " + PlaceTotals.countsAsSubfamily("a"), "" + i);
            assertEquals(java.util.Arrays.toString(v) + " subfamily", PlaceTotals.isCountedSubfamily(v[0], v[1]) ? 1 : 0, sqlSubfamily);
        }
        // the cases of the review: a genus value with a parenthesis is never a genus, by either rule
        assertFalse(PlaceTotals.isCountedGenus("morphotaxon", "amblyoponinaex", "x)"));
        assertFalse(PlaceTotals.isCountedGenus("morphotaxon", "amblyoponinaey", "y(z"));
        assertTrue(PlaceTotals.isCountedGenus("morphotaxon", "myrmicinaemyrm_gen-za01", "myrm_gen-za01"));
        exec("drop table rule_fixture");
    }

    private static String q(String v) {
        return v == null ? "null" : "'" + v.replace("'", "''") + "'";
    }

    /**
     * Brian Fisher, 2026-10-30: one rule for red flags on every list. A red-flagged specimen makes no list row, on a
     * country or adm1 list (valid names, and the genus rows of specimens identified to genus), a bioregion list or a
     * museum list (SpecimenDb.notRedFlagged in each query).
     */
    public void testListsFromSpecimensLeaveOutRedFlags() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, species) values"
            + " ('myrmicinaepheidole (indet)', 'species', 'indetermined', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', '(indet)'),"
            + " ('myrmicinaepheidole c', 'species', 'valid', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 'c')");
        exec("insert into specimen (code, taxon_name, status, country, museum, bioregion, family, subfamily, genus, flag) values"
            + " ('rf1', 'myrmicinaepheidole (indet)', 'indetermined', 'Mozambique', 'TESTM', 'Afrotropical', 'formicidae', '" + MYRM + "', 'pheidole', 'red'),"
            + " ('rf2', 'myrmicinaepheidole c', 'valid', 'Mozambique', 'TESTM', 'Afrotropical', 'formicidae', '" + MYRM + "', 'pheidole', 'red'),"
            + " ('ok1', '" + PB + "', 'morphotaxon', 'Kenya', 'TESTM', 'Afrotropical', 'formicidae', '" + MYRM + "', 'pheidole', 'yellow'),"
            + " ('ok2', '" + HB + "', 'valid', 'Mozambique', 'TESTM', 'Afrotropical', 'formicidae', '" + PON + "', 'hypoponera', null)");
        List<String> bioregion = column(BioregionTaxonDb.speciesFromSpecimenSql("Afrotropical"), "taxon_name");
        assertEquals(bioregion.toString(), new java.util.TreeSet<>(java.util.Arrays.asList(PB, HB)), new java.util.TreeSet<>(bioregion));
        List<String> museum = column(MuseumDb.speciesFromSpecimenSql("TESTM"), "taxon_name");
        assertEquals(museum.toString(), new java.util.TreeSet<>(java.util.Arrays.asList(PB, HB)), new java.util.TreeSet<>(museum));
        // country lists: the genus rows of specimens identified to genus (the indet specimen is red-flagged: no row)
        String mozambique = "select taxon_name from (" + GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL + ") r where r.country = 'Mozambique'";
        List<String> genusRows = column(mozambique, "taxon_name");
        assertFalse(genusRows.toString(), genusRows.contains(PHEIDOLE));
        exec("update specimen set flag = null where code = 'rf1'");
        assertTrue(column(mozambique, "taxon_name").contains(PHEIDOLE));
        assertTrue(column(BioregionTaxonDb.speciesFromSpecimenSql("Afrotropical"), "taxon_name").contains("myrmicinaepheidole (indet)"));
        assertEquals(" (flag is null or flag != 'red') ", SpecimenDb.getFlagCriteria());
        assertEquals("(s.flag is null or s.flag != 'red')", SpecimenDb.notRedFlagged("s"));
    }

    /**
     * Specimen criteria of the sets valid extant, valid fossil and Valid (with fossils) (2026-10-30, review of #307):
     * specimen.status and the name's taxon.fossil (specimens have no fossil column); they were the five counted statuses.
     */
    public void testSpecimenCriteriaOfTheValidSets() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, fossil) values"
            + " ('myrmicinaepheidole fossilis', 'species', 'valid', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', 'pheidole', 1)");
        exec("insert into specimen (code, taxon_name, status, country, family, subfamily, genus) values"
            + " ('fo1', 'myrmicinaepheidole fossilis', 'valid', 'Madagascar', 'formicidae', '" + MYRM + "', 'pheidole')");
        for (String table : new String[] {"specimen", "sp"}) {
            String from = "select " + table + ".code from specimen " + table + " where ";
            List<String> extant = column(from + new org.calacademy.antweb.StatusSet(org.calacademy.antweb.StatusSet.VALID_EXTANT).getCriteria(table), "code");
            List<String> fossil = column(from + new org.calacademy.antweb.StatusSet(org.calacademy.antweb.StatusSet.VALID_FOSSIL).getCriteria(table), "code");
            List<String> withFossil = column(from + new org.calacademy.antweb.StatusSet(org.calacademy.antweb.StatusSet.VALID_WITH_FOSSIL).getCriteria(table), "code");
            assertEquals(table, java.util.Arrays.asList("fo1"), fossil);
            assertFalse(table + " " + extant, extant.contains("fo1") || extant.contains("casent3"));
            assertTrue(table + " " + extant, extant.contains("casent1") && extant.contains("casent5"));
            assertTrue(table + " " + withFossil, withFossil.contains("fo1") && withFossil.contains("casent1") && !withFossil.contains("casent3"));
        }
    }
}

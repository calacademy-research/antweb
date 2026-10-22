package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.NumberChecks;
import org.calacademy.antweb.util.StatSet;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The count check's checks of the numbers that are not place totals (NumberChecks, 2026-10-22): statsPage tables,
 * admin pages, API /taxa, endemic flags, and the suspects B25 (taxon parents) and B26 (adm1 by name). On the database
 * of NumbersTestDb every number agrees (CountInvariantsTest.testRightNumbersGiveNoDifference); here each check finds
 * one wrong number, as a real difference, and the statsPage queries the check uses for all places at once give the
 * same numbers as the page's own code for one place.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class NumberChecksTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(connection, sql);
    }

    private CountCheck.Result check() throws Exception {
        return check(curatorNumbers(), artistNumbers());
    }

    private CountCheck.Result check(Map<Integer, int[]> curators, Map<Integer, int[]> artists) throws Exception {
        CountCheck check = new CountCheck(connection);
        check.curatorNumbers = curators;
        check.artistNumbers = artists;
        return check.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m.check).append(": ").append(m);
        return b.toString();
    }

    private static int real(CountCheck.Result r, String check) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.isReal() && m.check.equals(check)) ++n;
        return n;
    }

    private static CountCheck.Mismatch first(CountCheck.Result r, String check) {
        for (CountCheck.Mismatch m : r.mismatches) if (m.check.equals(check)) return m;
        return null;
    }

    public void testRightNumbersGiveNoDifferenceAndTheExpectedValues() throws Exception {
        CountCheck.Result r = check();
        assertEquals(list(r), 0, r.mismatches.size());
        Map<String, Object> e = r.invariants.expected;
        assertTrue(e.toString(), e.containsKey("statsPage") && e.containsKey("groups") && e.containsKey("curators") && e.containsKey("artists"));
        String json = NumberChecks.json(e);
        assertTrue(json, json.contains("\"imagesSum\":[4,0,0,0,4]"));
        assertTrue(json, json.contains("\"extant\":[2,2,2,1]"));
    }

    public void testStatsPageGlobalDataAndHomeNumber() throws Exception {
        exec("update taxon set fossil = null where taxon_name = '" + PAX + "'");   // in neither Extant nor Fossil
        CountCheck.Result r = check();
        assertTrue(list(r), real(r, "stats.global") > 0);
        assertTrue(first(r, "stats.global").taxonName.contains("extant + fossil species and subspecies"));
    }

    public void testStatsPageImagesSumLeavesOutSpecimensWithoutStatus() throws Exception {
        exec("insert into specimen (code, taxon_name, status, country, upload_id, family) values ('nostatus1', '" + PA + "', null, 'Madagascar', 1, 'formicidae')");
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (50, 'nostatus1', 'h', '2020-01-01 10:00:00')");
        CountCheck.Result r = check();
        // the Sum plus the images in no row add up; the images in no row are the finding
        assertEquals(list(r), 1, real(r, "stats.images"));
        CountCheck.Mismatch m = first(r, "stats.images");
        assertTrue(m.taxonName, m.taxonName.contains("without a status") && m.taxonName.contains("not in the Sum"));
        assertEquals(1, m.stored);
        assertEquals(0, m.recount);
    }

    public void testStatsPageBioregionAndMuseumData() throws Exception {
        // a valid wasp on the Malagasy list: statsPage counts it, the bioregion page (ants only) does not
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, fossil) values ('vespidaepolistes a', 'species', 'valid', 'vespidaepolistes', 'vespidae', 0)");
        exec("insert into bioregion_taxon (bioregion_name, taxon_name, created) values ('Malagasy', 'vespidaepolistes a', '2020-01-01')");
        exec("update museum set valid_species_count = 1 where code = 'CASC'");
        CountCheck.Result r = check();
        assertTrue(list(r), real(r, "stats.bioregions") > 0);
        assertTrue(list(r), real(r, "stats.museums") > 0);
        CountCheck.Mismatch m = first(r, "stats.museums");
        assertEquals("museum CASC", m.place.label);
        assertEquals(2, m.stored);
        assertEquals(1, m.recount);
    }

    /** The grouped statsPage queries of the check give what the page's code gives for one bioregion or museum. */
    public void testStatsPagePlaceQueriesMatchThePageCode() throws Exception {
        StatisticsDb db = new StatisticsDb(connection);
        Bioregion b = new Bioregion();
        b.setName("Malagasy");
        StatSet page = db.getBioregionData(b);
        long[] grouped = grouped(NumberChecks.statsPlacesSql("bioregion"), "Malagasy", 0);
        for (int i = 0; i < 4; i++) assertEquals("bioregion value " + (i + 1), page.getValue(i + 1), grouped[i]);
        Museum m = new Museum();
        m.setCode("CASC");
        StatSet extant = db.getExtantMuseumData(m);
        StatSet fossil = db.getFossilMuseumData(m);
        long[] ge = grouped(NumberChecks.statsPlacesSql("museum"), "CASC", 0);
        long[] gf = grouped(NumberChecks.statsPlacesSql("museum"), "CASC", 1);
        for (int i = 0; i < 4; i++) {
            assertEquals("museum extant " + (i + 1), extant.getValue(i + 1), ge[i]);
            assertEquals("museum fossil " + (i + 1), fossil.getValue(i + 1), gf[i]);
        }
        assertEquals(1, extant.getValue(4));   // pheidole a x
    }

    private long[] grouped(String sql, String key, int fossil) throws Exception {
        long[] a = new long[4];
        String[] ranks = {"subfamily", "genus", "species", "subspecies"};
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                if (!key.equalsIgnoreCase(r.getString(1)) || r.getInt(2) != fossil) continue;
                a[java.util.Arrays.asList(ranks).indexOf(r.getString(3))] += r.getLong(4);
            }
        }
        return a;
    }

    public void testAdminNumbers() throws Exception {
        exec("update statistics set proj_taxa = 1");
        exec("update upload set specimens = 9");
        exec("update image_upload set image_count = 5");
        exec("update ant_group set upload_count = 3");
        Map<Integer, int[]> curators = curatorNumbers();
        curators.get(5)[0] = 7;
        Map<Integer, int[]> artists = artistNumbers();
        artists.get(1)[1] = 1;
        CountCheck.Result r = check(curators, artists);
        for (String id : new String[] {"admin.statistics", "admin.groups", "admin.imageUploads", "admin.curators", "admin.artists"}) {
            assertTrue(id + list(r), real(r, id) > 0);
        }
        CountCheck.Mismatch m = first(r, "admin.curators");
        assertEquals(7, m.stored);
        assertEquals(1, m.recount);
    }

    /** Another group's upload does not change this group's numbers; the nightly name update of its specimens may. */
    public void testGroupNumbersArePendingAfterItsSpecimensWereRenamed() throws Exception {
        exec("update upload set specimens = 9");
        exec("insert into upload (upload_id, created, group_id) values (2, now(), 2)");
        CountCheck.Result r = check();
        assertEquals(list(r), 1, real(r, "admin.groups"));
        exec("create table specimen_name_update (id int auto_increment primary key, run_date datetime, mode varchar(12), code varchar(128),"
            + " access_group int, old_name varchar(128), new_name varchar(128))");
        exec("insert into specimen_name_update (run_date, mode, code, access_group, old_name, new_name) values (now(), 'changed', 'casent3', 1, 'x', 'y')");
        r = check();
        assertEquals(list(r), 0, real(r, "admin.groups"));
    }

    public void testAGenusRowWithFewerImagesThanItsSpeciesIsFound() throws Exception {
        exec("update museum_taxon set image_count = 1 where code = 'CASC' and taxon_name = '" + PHEIDOLE + "'");
        CountCheck.Result r = check();
        CountCheck.Mismatch m = first(r, "list.images");
        assertNotNull(list(r), m);
        assertTrue(m.isReal());
        assertEquals(1, m.stored);
        assertEquals(3, m.recount);
    }

    public void testApiTaxaCountsAreTheGlobalRow() throws Exception {
        exec("update taxon set specimen_count = 0 where taxon_name = '" + PA + "'");
        CountCheck.Result r = check();
        assertEquals(list(r), 1, real(r, "api.taxa"));
    }

    public void testEndemicFlagsByTheRule() throws Exception {
        exec("update geolocale_taxon set is_endemic = 0 where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PAX + "'");
        CountCheck.Result r = check();
        assertTrue(list(r), real(r, "place.endemic.flags") > 0);
    }

    /** B25: a subspecies whose parent is its species. */
    public void testSubspeciesUnderItsSpeciesIsFound() throws Exception {
        exec("update taxon set parent_taxon_name = '" + PA + "' where taxon_name = '" + PAX + "'");
        CountCheck.Result r = check();
        assertTrue(list(r), real(r, "taxon.parents") > 0);
        boolean b25 = false;
        for (CountCheck.Mismatch m : r.mismatches) if ("taxon.parents".equals(m.check) && m.taxonName.contains("B25")) b25 = true;
        assertTrue(list(r), b25);
    }

    /** B26: a state of the same name in another country. */
    public void testAdm1OfTheSameNameInAnotherCountryIsFound() throws Exception {
        exec("update geolocale set specimen_count = 3 where id = " + ATSINANANA);
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, specimen_count, created) values (" + ATSINANANA + ", '" + PA + "', 3, '2020-01-01')");
        exec("update specimen set adm1 = 'Atsinanana' where code in ('casent1', 'casent2')");
        exec("insert into specimen (code, taxon_name, status, country, adm1, upload_id, family) values ('kenya1', '" + PA + "', 'valid', 'Kenya', 'Atsinanana', 1, 'formicidae')");
        CountCheck.Result r = check();
        CountCheck.Mismatch m = first(r, "geo.adm1.country");
        assertNotNull(list(r), m);
        assertTrue(m.isReal());
        assertEquals(3, m.stored);
        assertEquals(2, m.recount);
    }

    /** At most LIST_CAP differences of one check are listed; the others are counted (API /taxa can differ for every taxon). */
    public void testManyDifferencesOfOneCheckAreCountedNotListed() throws Exception {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 320; i++) {
            if (i > 0) values.append(", ");
            values.append("('myrmicinaepheidole x").append(i).append("', 'species', 'valid', '").append(PHEIDOLE).append("', 'formicidae', 0, 5)");
        }
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, fossil, specimen_count) values " + values);
        exec("insert into proj_taxon (project_name, taxon_name, created) select 'allantwebants', taxon_name, '2020-01-01' from taxon where taxon_name like 'myrmicinaepheidole x%'");
        CountCheck.Result r = check();
        assertTrue(list(r).length() > 0);
        assertTrue(r.unlisted.toString(), r.unlisted.containsKey("api.taxa"));
        assertTrue(r.real() >= 320);
    }
}

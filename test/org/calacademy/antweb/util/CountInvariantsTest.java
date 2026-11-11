package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.CountInvariants;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The invariants of the count check (CountInvariants, 2026-10-21): every place's numbers against the numbers
 * they must equal, on a database where all agree (no difference), then with one number wrong per check (each
 * check finds its number, as a real difference), and with the reasons a difference is only pending.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like CountCheckTest; ANTWEB_TEST_DB_URL overrides.
 */
public class CountInvariantsTest extends TestCase {

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

    /** The invariants only (no recount), with the last nightly run long after the test data was written. */
    private CountCheck.Result run(boolean rulesCounted) throws Exception {
        CountCheck check = new CountCheck(connection);
        check.curatorNumbers = curatorNumbers();
        check.artistNumbers = artistNumbers();
        return check.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, rulesCounted, true, null);
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

    public void testRulesLineInComputeLog() {
        List<String> log = new ArrayList<>();
        log.add("2026-10-21 05:00:00 Begin run");
        assertFalse(CountInvariants.rulesCounted(log));
        log.add("2026-10-21 07:40:00   Set 4 done (" + CountInvariants.RULES_LOG + ")");
        // the counting rules of 2026-10-28 too (the same line since then)
        assertFalse(CountInvariants.rulesCounted(log));
        log.add("2026-10-29 07:40:00   Set 4 done (" + CountInvariants.RULES_LOG + "; " + CountInvariants.COUNTING_RULES_LOG + ")");
        // the genera rule of 2026-10-30 too (the same line since then)
        assertFalse(CountInvariants.rulesCounted(log));
        log.add("2026-10-31 07:40:00   Set 4 done (" + CountInvariants.RULES_LOG + "; " + CountInvariants.COUNTING_RULES_LOG
            + "; " + CountInvariants.GENERA_RULE_LOG + ")");
        // the adm1 rule and the red-flag images rule of 2026-11-09 too (the same line since then)
        assertFalse(CountInvariants.rulesCounted(log));
        log.add("2026-11-10 07:40:00   Set 4 done (" + CountInvariants.RULES_LOG + "; " + CountInvariants.COUNTING_RULES_LOG
            + "; " + CountInvariants.GENERA_RULE_LOG + "; " + CountInvariants.ADM1_RULE_LOG
                    + "; " + org.calacademy.antweb.home.StatisticsDb.RED_FLAG_IMAGES_RULE_LOG + ")");
        assertTrue(CountInvariants.rulesCounted(log));
    }

    /**
     * Brian Fisher, 2026-11-09: images of red-flagged specimens are not counted anywhere. casent3 (pheidole b, one profile
     * photo) red-flagged: the numbers stored before (counted with it) differ from the recount; pending until a nightly
     * run has counted by the rule (rulesCounted), real after; once recounted as nightly set 4 does, every check agrees.
     */
    public void testImagesOfARedFlaggedSpecimenAreNotCounted() throws Exception {
        exec("update specimen set flag = 'red' where code = 'casent3'");
        // the group numbers as the nightly group recount writes them (red-flagged specimens apart, as before 2026-11-09)
        exec("update group_count set specimens = 4, flagged = 1, species = 3");
        CountCheck.Result r = run(false);
        int real = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.isReal()) ++real;
        assertTrue("the stored image numbers still count casent3", r.mismatches.size() > 0);
        assertEquals("before the nightly run: pending" + list(r), 0, real);
        r = run(true);
        String all = list(r);
        assertTrue(all, all.contains("total specimen images stored:2 recount:1"));
        assertTrue(all, all.contains("specimens imaged stored:2 recount:1"));
        assertTrue(all, all.contains("myrmicinaepheidole b: image_count stored:1 recount:0"));
        // recounted as nightly set 4 does: taxon images (ImageCountDb), the statistics row (StatisticsDb)
        exec(org.calacademy.antweb.home.ImageCountDb.SPECIES_IMAGES_SQL);
        new org.calacademy.antweb.home.ImageCountDb(connection).rollUpTaxonImageCounts();
        exec("update statistics set total_images = (" + org.calacademy.antweb.home.StatisticsDb.TOTAL_IMAGES_SQL + "),"
            + " specimens_imaged = (" + org.calacademy.antweb.home.StatisticsDb.IMAGED_SPECIMENS_SQL + "),"
            + " species_imaged = (" + org.calacademy.antweb.home.StatisticsDb.IMAGED_SPECIES_SQL + "),"
            + " valid_species_imaged = (" + org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_IMAGED_SQL + ")");
        assertEquals("pheidole a (casent1 h) only", 1, one("select total_images from statistics"));
        assertEquals(1, one("select specimens_imaged from statistics"));
        assertEquals(1, one("select species_imaged from statistics"));
        assertEquals("pheidole b's own row: no image", 0, one("select image_count from taxon where taxon_name = '" + PB + "'"));
        assertEquals("the genus: pheidole a's 2 (casent1 h, l)", 2, one("select image_count from taxon where taxon_name = '" + PHEIDOLE + "'"));
        assertEquals("casent3 keeps its own image_count: still imaged", 1, one("select image_count from specimen where code = 'casent3'"));
        r = run(true);
        assertEquals(list(r), 0, r.mismatches.size());
    }

    private long one(String sql) throws Exception {
        try (java.sql.Statement s = connection.createStatement(); java.sql.ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : -1;
        }
    }

    public void testRightNumbersGiveNoDifference() throws Exception {
        CountCheck.Result r = run(true);
        assertEquals(list(r), 0, r.mismatches.size());
        Map<String, int[]> summary = r.invariants.summary();
        assertEquals("every check id of CountInvariants.IDS ran, no other", new java.util.TreeSet<>(java.util.Arrays.asList(CountInvariants.IDS)),
            new java.util.TreeSet<>(summary.keySet()));
        for (String id : CountInvariants.IDS) {
            assertTrue("check " + id + " ran", summary.containsKey(id));
            assertTrue("check " + id + " compared numbers", summary.get(id)[0] > 0);
        }
        for (CountInvariants.Check c : r.invariants.checks) assertNull(c.id + " " + c.error, c.error);
    }

    public void testEveryWrongNumberIsFound() throws Exception {
        exec("update geolocale set genus_count = 5 where id = " + MADAGASCAR);                       // roll-up sum gone wrong
        exec("update geolocale set valid_species_count = 0 where id = " + MADAGASCAR);               // never recounted
        exec("update project set specimen_count = 4 where project_name = 'madants'");                 // a day behind its list
        exec("update museum set subfamily_count = 2 where code = 'CASC'");
        exec("update geolocale set endemic_species_count = 4 where id = " + MADAGASCAR);             // off by one
        exec("update bioregion set specimen_subfamily_dist_json = '{\"label\": \"Myrmicinae\",\"value\": 900,\"color\": \"#999999\"}'");
        exec("update museum set imaged_specimen_count = 9 where code = 'MCZC'");                     // more imaged than images
        exec("update place_image_count set image_count = 5 where place_key = '" + MADAGASCAR + "' and shot_type = '*all'");
        exec("update geolocale_taxon set species_count = 7 where geolocale_id = " + INDIAN_OCEAN + " and taxon_name = '" + PHEIDOLE + "'");
        exec("update museum_taxon set specimen_count = 8 where code = 'MCZC' and taxon_name = '" + FAMILY + "'");
        exec("update bioregion_taxon set specimen_count = 3 where taxon_name = '" + HB + "'");       // more than its Global
        exec("update proj_taxon set specimen_count = 6 where project_name = 'allantwebants' and taxon_name = '" + PB + "'");
        exec("update statistics set total_images = 3");
        exec("update taxon set image_count = 1 where taxon_name = '" + PHEIDOLE + "'");              // species not added to the genus
        exec("update geolocale set specimen_count = 50 where id = " + INDIAN_OCEAN);
        CountCheck.Result r = run(true);
        String all = list(r);
        assertEquals(all, 0, r.pending());
        for (String id : new String[] {"place.totals.genus", "place.totals.valid", "place.totals.specimens", "place.totals.subfamily",
                "place.totals.endemic", "place.pies", "place.images.order", "place.images", "list.genus", "list.family", "list.global",
                "global.specimens", "home", "taxon.images", "geo.regions"}) {
            assertTrue(id + " found its wrong number:" + all, real(r, id) > 0);
        }
        boolean found = false;
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("place.totals.genus".equals(m.check)) {
                assertEquals("Madagascar", m.place.label);
                assertEquals(5, m.stored);
                assertEquals(2, m.recount);
                found = true;
            }
        }
        assertTrue(found);
    }

    public void testANumberWhoseRuleChangedIsPendingUntilTheNightlyRun() throws Exception {
        exec("update geolocale set genus_count = 5 where id = " + MADAGASCAR);
        CountCheck.Result r = run(false);
        assertEquals(list(r), 0, r.real());
        assertEquals(list(r), 1, r.pending());
        assertEquals(1, run(true).real());
    }

    public void testAListRowAddedAfterTheNightlyRunIsPending() throws Exception {
        // A curator added a morphospecies to the Madagascar list today: Species and the genus row wait for the nightly run.
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('myrmicinaepheidole new1', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + MADAGASCAR + ", 'myrmicinaepheidole new1', now())");
        CountCheck.Result r = run(true);
        assertEquals(list(r), 0, r.real());
        assertTrue(list(r), r.pending() >= 2);
    }

    public void testNewImagesMakeImageNumbersPending() throws Exception {
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (9, 'casent2', 'h', now())");
        exec("update specimen set image_count = 1 where code = 'casent2'");
        CountCheck.Result r = run(true);
        assertEquals(list(r), 0, r.real());
        assertTrue(list(r), r.pending() > 0);
        assertTrue(r.imagesSinceNightly);
    }

    /**
     * Brian Fisher, 2026-11-03 (audit of 2026-11-02): an upload makes a place's numbers pending only when it touched that
     * place. A wrong number of the museum MCZC stays real after an upload of a specimen owned elsewhere (another country,
     * museum, bioregion, a name on no list); the same upload of an MCZC specimen makes it pending.
     */
    public void testAnUploadElsewhereLeavesAPlaceDifferenceReal() throws Exception {
        exec("update museum set imaged_specimen_count = 9 where code = 'MCZC'");   // more imaged than images: place.images.order
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('myrmicinaepheidole ke1', 'species', 'morphotaxon', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "')");
        exec("insert into upload (upload_id, created) values (2, now())");
        exec("insert into specimen (code, taxon_name, status, country, museum, bioregion, upload_id, family, subfamily, ownedBy) values"
            + " ('casent9', 'myrmicinaepheidole ke1', 'morphotaxon', 'Kenya', 'NMK', 'Afrotropical', 2, 'formicidae', '" + MYRM + "', 'NMK')");
        CountCheck.Result r = run(true);
        assertTrue(r.uploadSinceNightly);
        assertNotNull(r.changed);
        assertFalse(r.changed.specimens(CountCheck.MUSEUM, "MCZC"));
        assertTrue(r.changed.specimens(CountCheck.MUSEUM, "NMK"));
        assertTrue(r.changed.specimens(CountCheck.BIOREGION, "Afrotropical"));
        assertFalse(r.changed.specimens(CountCheck.GEOLOCALE, "" + MADAGASCAR));
        assertEquals(list(r), 2, real(r, "place.images.order"));   // Imaged <= Images, Imaged <= Specimens
        assertEquals(list(r), 1, real(r, "place.images"));

        // the specimen owned by MCZC (specimen.museum is set at night; ownedBy names the museum now)
        exec("update specimen set museum = null, ownedBy = 'MCZC, Harvard' where code = 'casent9'");
        r = run(true);
        assertTrue(r.changed.specimens(CountCheck.MUSEUM, "MCZC"));
        assertEquals(list(r), 0, real(r, "place.images.order"));
        assertEquals(list(r), 0, r.real());
    }

    public void testRotatingPlacesStopAtTheTimeLimit() throws Exception {
        List<CountCheck.Place> rotating = new ArrayList<>();
        rotating.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        rotating.add(new CountCheck.Place(CountCheck.MUSEUM, "MCZC", "museum MCZC", "museum", "MCZC"));
        CountCheck.Result none = new CountCheck(connection).run(new ArrayList<>(), rotating, -1, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, false, null);
        assertEquals(2, none.rotatingPlanned);
        assertEquals(0, none.rotatingChecked);
        assertEquals(2, none.rotatingSkipped.size());
        CountCheck.Result both = new CountCheck(connection).run(new ArrayList<>(), rotating, 60, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, null);
        assertEquals(2, both.rotatingChecked);
        assertEquals(list(both), 0, both.mismatches.size());
        assertTrue(both.rowsChecked.get(rotating.get(0)) > 0);
    }

    public void testRecountOfASampledPlaceChecksItsImagesColumn() throws Exception {
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.GEOLOCALE, "" + MADAGASCAR, "Madagascar", "country", "Madagascar"));
        places.add(new CountCheck.Place(CountCheck.PROJECT, "madants", "project madants", null, null));
        CountCheck.Result r = new CountCheck(connection).run(places, new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, false, null);
        assertEquals(list(r), 0, r.mismatches.size());
        exec("update geolocale_taxon set image_count = 9 where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PB + "'");
        exec("update project set specimen_count = 9 where project_name = 'madants'");
        r = new CountCheck(connection).run(places, new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, false, null);
        assertEquals(list(r), 2, r.real());
        assertEquals(1, real(r, "images"));
        assertEquals(1, real(r, "place specimens"));
    }

    /** Staging, 2026-10-21: an invariant could not run. Keys differ in case between tables; old rows have zero dates. */
    public void testStagingDataCaseAndZeroDates() throws Exception {
        exec("update image set image_of_id = 'CASENT1' where image_of_id = 'casent1'");   // as in image, the code in capitals
        exec("insert into image (id, image_of_id, shot_type, upload_date) values (30, 'CASENT9', 'h', " + "'2020-01-01 10:00:00')");   // an orphan image (no specimen)
        exec("update museum_taxon set code = 'casc' where code = 'CASC'");
        exec("update specimen set museum = 'casc ' where museum = 'CASC'");
        exec("set session sql_mode = ''");
        exec("update proj_taxon set created = '0000-00-00 00:00:00' where project_name = 'allantwebants'");
        exec("update statistics set created = '0000-00-00 00:00:00', total_images = 3");   // images of the ant: 1, 3 and 30
        exec("update geolocale_taxon set created = '0000-00-00 00:00:00' where geolocale_id = " + MADAGASCAR);
        CountCheck.Result r = run(true);
        for (CountInvariants.Check c : r.invariants.checks) assertNull(c.id + ": " + c.error, c.error);
        assertEquals(list(r), 0, r.mismatches.size());
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.GEOLOCALE, "" + MADAGASCAR, "Madagascar", "country", "Madagascar"));
        places.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        CountCheck.Result s = new CountCheck(connection).run(places, new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, false, null);
        int real = 0;
        for (CountCheck.Mismatch m : s.mismatches) if (m.isReal() && !m.check.startsWith("place images")) ++real;
        assertEquals(list(s), 0, real);
    }

    public void testACheckThatCannotRunNamesItsQuery() throws Exception {
        exec("alter table museum drop column taxon_subfamily_dist_json");
        CountCheck.Result r = run(true);
        String error = null;
        for (CountInvariants.Check c : r.invariants.checks) if (c.error != null) error = c.id + ": " + c.error;
        assertNotNull(error);
        assertTrue(error, error.startsWith("place.pies: "));
        assertTrue(error, error.contains("taxon_subfamily_dist_json") && error.contains("SQL: select") && error.contains("CountInvariants.java:"));
        boolean listed = false;
        for (CountCheck.Mismatch m : r.mismatches) if (m.taxonName.startsWith("the check could not run: ") && m.taxonName.contains("SQL: ")) listed = true;
        assertTrue(listed);
        // the other checks still ran
        assertTrue(r.invariants.summary().get("taxon.images")[0] > 0);
    }

    /** The background job keeps the last reports for countCheck.do?job=N; the report shows the time of each part. */
    public void testJobReportsAndPartTimes() throws Exception {
        for (int id = 101; id <= 107; id++) org.calacademy.antweb.util.CountCheckJob.keep(id, "report " + id);
        assertEquals(107, org.calacademy.antweb.util.CountCheckJob.latest());
        assertEquals("report 107", org.calacademy.antweb.util.CountCheckJob.report(107));
        assertNull("only the last 5 are kept", org.calacademy.antweb.util.CountCheckJob.report(102));
        List<CountCheck.Place> rotating = new ArrayList<>();
        rotating.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        CountCheck.Result r = new CountCheck(connection).run(new ArrayList<>(), rotating, 60, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, null);
        assertTrue(r.millis >= r.invariants.millis + r.coreMillis + r.rotatingMillis - 5);
        assertEquals(1, r.rotatingChecked);
        // with the invariants run, a rotating place skips the genus, subfamily and Global recounts
        for (CountCheck.Mismatch m : r.mismatches) assertFalse(m.check.equals("species") || m.check.equals("global"));
    }

    public void testStatsPageNumberInMemory() throws Exception {
        CountCheck.Result r = new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, 3);
        assertEquals(list(r), 0, r.mismatches.size());
        r = new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, 9);
        assertEquals(list(r), 1, real(r, "home"));
    }
}

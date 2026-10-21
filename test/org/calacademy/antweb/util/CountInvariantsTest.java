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
        return new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
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
        assertTrue(CountInvariants.rulesCounted(log));
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
        exec("update geolocale set endemic_species_count = 3 where id = " + MADAGASCAR);             // off by one
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
        exec("insert into image values (9, 'casent2', 'h', now())");
        exec("update specimen set image_count = 1 where code = 'casent2'");
        CountCheck.Result r = run(true);
        assertEquals(list(r), 0, r.real());
        assertTrue(list(r), r.pending() > 0);
        assertTrue(r.imagesSinceNightly);
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

    public void testStatsPageNumberInMemory() throws Exception {
        CountCheck.Result r = new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, 3);
        assertEquals(list(r), 0, r.mismatches.size());
        r = new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, 9);
        assertEquals(list(r), 1, real(r, "home"));
    }
}

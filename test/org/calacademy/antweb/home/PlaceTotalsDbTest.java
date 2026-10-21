package test.org.calacademy.antweb.home;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Project;
import org.calacademy.antweb.Taxon;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.home.BioregionDb;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.ImageCountDb;
import org.calacademy.antweb.home.MuseumDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.ProjectDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.util.CountCheck;
import org.calacademy.antweb.util.TaxonMgr;
import org.calacademy.antweb.util.UtilDataAction;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The fixes of 2026-10-21 to the numbers the nightly run writes (docs/numbers.md), each against a database
 * (NumbersTestDb): the writers produce the numbers the count check's invariants (CountInvariants) expect.
 *
 *   place totals   Genera, Species (all places) and Subfamilies (museums, bioregions) counted from the list, not
 *                  summed from row counts; geolocale Valid species written by the nightly run (it never was)
 *   pies           Species per Subfamily adds up to Species; Specimens per Subfamily to Specimens (museums and
 *                  bioregions: their own specimens, not every specimen of the names on their list)
 *   endemic        the number = the flagged rows (the loop gave the first place one too many, the last one too few)
 *   museums        specimens belong to a museum when its code is a whole word of ownedBy (not a substring)
 *   taxon images   species added to their genus (a genus counted only specimens filed under the genus name)
 *   home page      specimens and species imaged: images of the ant, labels not counted; species by name
 *   Global         recounted after the nightly name update moved specimens
 *   statsPage      "valid species + ssp." counts species and subspecies, not every rank
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class PlaceTotalsDbTest extends TestCase {

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

    private long one(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    private String text(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getString(1);
        }
    }

    /** The count check's invariants, every place, rules of 2026-10-21 counted. */
    private CountCheck.Result invariants() throws Exception {
        return new CountCheck(connection).run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"),
            true, true, true, null);
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m);
        return b.toString();
    }

    private static void callPrivate(Object target, String method, Class<?> type, Object arg) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, type);
        m.setAccessible(true);
        m.invoke(target, arg);
    }

    /** Clears the numbers the place's finish() writes; a stale genus row count that the old roll-up summed. */
    private void clearTotals() throws Exception {
        for (String t : new String[] {"geolocale", "project", "museum", "bioregion"}) {
            exec("update " + t + " set subfamily_count = 0, genus_count = 0, species_count = 0, valid_species_count = 0,"
                + " taxon_subfamily_dist_json = null, specimen_subfamily_dist_json = null");
        }
        exec("update geolocale_taxon set species_count = 9 where taxon_name = '" + PHEIDOLE + "'");
        exec("update museum_taxon set subfamily_count = 1 where taxon_name = '" + MYRM + "'");
    }

    public void testFinishWritesEveryTotalFromTheList() throws Exception {
        clearTotals();
        callPrivate(new GeolocaleDb(connection), "finish", int.class, MADAGASCAR);
        callPrivate(new GeolocaleDb(connection), "finish", int.class, INDIAN_OCEAN);
        Project p = new Project();
        p.setName("madants");
        callPrivate(new ProjectDb(connection), "finish", Project.class, p);
        p = new Project();
        p.setName("allantwebants");
        callPrivate(new ProjectDb(connection), "finish", Project.class, p);
        callPrivate(new MuseumDb(connection), "finish", String.class, "CASC");
        callPrivate(new MuseumDb(connection), "finish", String.class, "MCZC");
        Bioregion b = new Bioregion();
        b.setName("Malagasy");
        callPrivate(new BioregionDb(connection), "finish", Bioregion.class, b);
        // the stale row counts the old roll-up summed are still there; the place numbers are right anyway
        exec("update geolocale_taxon set species_count = 2 where taxon_name = '" + PHEIDOLE + "'");
        exec("update museum_taxon set subfamily_count = 0 where taxon_name = '" + MYRM + "'");

        assertEquals("Genera of Madagascar", 2, one("select genus_count from geolocale where id = " + MADAGASCAR));
        assertEquals("Species and subspecies of Madagascar", 4, one("select species_count from geolocale where id = " + MADAGASCAR));
        assertEquals("Valid species of Madagascar (was never written)", 3, one("select valid_species_count from geolocale where id = " + MADAGASCAR));
        assertEquals("Subfamilies of CASC (was a roll-up)", 1, one("select subfamily_count from museum where code = 'CASC'"));
        assertEquals(4, PlaceTotals.pieSum(text("select taxon_subfamily_dist_json from geolocale where id = " + MADAGASCAR)));
        assertEquals(5, PlaceTotals.pieSum(text("select specimen_subfamily_dist_json from geolocale where id = " + MADAGASCAR)));
        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, r.mismatches.size());
    }

    public void testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens() throws Exception {
        // Another museum's specimen of pheidole a, outside the Malagasy bioregion: not in CASC's or Malagasy's pie.
        exec("insert into specimen (code, taxon_name, status, country, museum, bioregion, upload_id, family, subfamily) values"
            + " ('other1', '" + PA + "', 'valid', 'Kenya', 'NHMUK', 'Afrotropical', 1, 'formicidae', '" + MYRM + "')");
        PlaceTotals.writePies(connection, PlaceTotals.MUSEUM, "CASC");
        PlaceTotals.writePies(connection, PlaceTotals.BIOREGION, "Malagasy");
        assertEquals("CASC: its 4 specimens", 4, PlaceTotals.pieSum(text("select specimen_subfamily_dist_json from museum where code = 'CASC'")));
        assertEquals("Malagasy: its 3 specimens", 3, PlaceTotals.pieSum(text("select specimen_subfamily_dist_json from bioregion where name = 'Malagasy'")));
        // Species per Subfamily: species and subspecies rows only (the project and bioregion pies counted every rank)
        PlaceTotals.writePies(connection, PlaceTotals.PROJECT, "madants");
        assertEquals(4, PlaceTotals.pieSum(text("select taxon_subfamily_dist_json from project where project_name = 'madants'")));
        assertTrue(text("select taxon_subfamily_dist_json from project where project_name = 'madants'").contains("\"Myrmicinae\""));
    }

    /** 2026-10-21: specimen uploads rolled back with "Data too long for column 'specimen_subfamily_dist_json'". */
    public void testALongDistributionFitsItsColumnAndNeverStopsTheWork() throws Exception {
        PlaceTotals.clearColumnLengths();
        // CASC holds specimens of 60 subfamilies (and of names that are no subfamily): far more than 1000 characters of JSON.
        StringBuilder taxa = new StringBuilder();
        StringBuilder specimens = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            String sf = "longsubfamilynumber" + i + "inae";
            taxa.append(i == 1 ? "" : ", ").append("('").append(sf).append("', 'subfamily', 'valid', 'formicidae', 'formicidae', '").append(sf).append("')");
            for (int k = 0; k < i % 3 + 1; k++) {
                specimens.append(specimens.length() == 0 ? "" : ", ").append("('x").append(i).append("_").append(k).append("', 'CASC', '").append(sf).append("', 'formicidae')");
            }
            specimens.append(", ('typo").append(i).append("', 'CASC', 'notasubfamily").append(i).append("', 'formicidae')");
        }
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values " + taxa);
        exec("insert into specimen (code, museum, subfamily, family) values " + specimens);
        long total = one("select count(*) from specimen where museum = 'CASC' and family = 'formicidae'");
        PlaceTotals.writePies(connection, PlaceTotals.MUSEUM, "CASC");
        String json = text("select specimen_subfamily_dist_json from museum where code = 'CASC'");
        assertNotNull(json);
        assertTrue(json.length() + " characters", json.length() <= 1000);
        assertEquals("the slices still add up to Specimens", total, PlaceTotals.pieSum(json));
        assertTrue(json.contains("\"label\": \"Other\""));
        // geolocales: varchar(5000), the whole distribution fits (one Other slice for the names that are no subfamily)
        String all = PlaceTotals.pieJson(connection, PlaceTotals.specimenPieSql(PlaceTotals.MUSEUM), "CASC");
        assertEquals(total, PlaceTotals.pieSum(all));
        assertEquals("60 subfamilies, myrmicinae, Other", 62, all.split("\"label\"").length - 1);

        // A chart that cannot be written (here: no such column) is logged; the work around it goes on.
        exec("alter table bioregion drop column specimen_subfamily_dist_json");
        PlaceTotals.clearColumnLengths();
        PlaceTotals.writePies(connection, PlaceTotals.BIOREGION, "Malagasy");
        Bioregion b = new Bioregion();
        b.setName("Malagasy");
        exec("update bioregion set valid_species_count = 0");
        callPrivate(new BioregionDb(connection), "finish", Bioregion.class, b);
        assertEquals(2, one("select valid_species_count from bioregion where name = 'Malagasy'"));
        PlaceTotals.clearColumnLengths();
    }

    /** Brian Fisher, 2026-10-21: specimens of other families stay in AntWeb but are not counted on place pages. */
    public void testOtherFamiliesAreNotCounted() throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values"
            + " ('apidaeapis mellifera', 'species', 'valid', 'apidaeapis', 'apidae', 'apinae')");
        exec("insert into specimen (code, taxon_name, status, country, museum, bioregion, upload_id, family, subfamily) values"
            + " ('bee1', 'apidaeapis mellifera', 'valid', 'Madagascar', 'CASC', 'Malagasy', 1, 'apidae', 'apinae')");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, specimen_count, created) values (" + MADAGASCAR + ", 'apidaeapis mellifera', 1, '2020-01-01')");
        exec("insert into image values (40, 'bee1', 'h', '2020-01-01')");
        exec("update specimen set image_count = 1 where code = 'bee1'");
        exec("update taxon set image_count = 1 where taxon_name = 'apidaeapis mellifera'");
        clearTotals();
        exec("update geolocale_taxon set species_count = 2 where taxon_name = '" + PHEIDOLE + "'");
        exec("update museum_taxon set subfamily_count = 0 where taxon_name = '" + MYRM + "'");
        exec("update geolocale set specimen_count = 0 where id = " + MADAGASCAR);
        GeolocaleDb g = new GeolocaleDb(connection);
        callPrivate(g, "finish", int.class, MADAGASCAR);
        callPrivate(g, "updateCountsFromSpecimenData", int.class, MADAGASCAR);
        callPrivate(new GeolocaleDb(connection), "finish", int.class, INDIAN_OCEAN);
        callPrivate(new MuseumDb(connection), "finish", String.class, "CASC");
        callPrivate(new MuseumDb(connection), "finish", String.class, "MCZC");
        for (String p : new String[] {"madants", "allantwebants"}) {
            Project pr = new Project();
            pr.setName(p);
            callPrivate(new ProjectDb(connection), "finish", Project.class, pr);
        }
        Bioregion b = new Bioregion();
        b.setName("Malagasy");
        callPrivate(new BioregionDb(connection), "finish", Bioregion.class, b);
        assertEquals("Madagascar: 5 ant specimens, the bee not counted", 5, one("select specimen_count from geolocale where id = " + MADAGASCAR));
        assertEquals(4, one("select species_count from geolocale where id = " + MADAGASCAR));
        assertEquals(2, one("select imaged_specimen_count from museum where code = 'CASC'"));
        assertEquals(4, PlaceTotals.pieSum(text("select specimen_subfamily_dist_json from museum where code = 'CASC'")));
        CountCheck.Result r = invariants();
        int places = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.check.startsWith("place.")) ++places;
        assertEquals(list(r), 0, places);
    }

    public void testEndemicNumbersAreTheFlaggedRows() throws Exception {
        // Three bioregions, each with species found only there: Afrotropical 2, Malagasy 1, Neotropical 1.
        for (String n : new String[] {"t1", "t2", "t3"}) {
            exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily) values ('myrmicinaepheidole " + n
                + "', 'species', 'valid', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "')");
        }
        exec("insert into bioregion (name) values ('Afrotropical'), ('Neotropical')");
        exec("insert into bioregion_taxon (bioregion_name, taxon_name) values ('Afrotropical', 'myrmicinaepheidole t1'),"
            + " ('Afrotropical', 'myrmicinaepheidole t2'), ('Neotropical', 'myrmicinaepheidole t3'), ('Afrotropical', '" + HB + "')");
        exec("update bioregion set endemic_species_count = 7");
        new BioregionDb(connection).calcEndemic();
        assertEquals(2, one("select endemic_species_count from bioregion where name = 'Afrotropical'"));
        assertEquals("pheidole a only (hypoponera b is also Afrotropical)", 1, one("select endemic_species_count from bioregion where name = 'Malagasy'"));
        assertEquals(1, one("select endemic_species_count from bioregion where name = 'Neotropical'"));
        for (String b : new String[] {"Afrotropical", "Malagasy", "Neotropical"}) {
            assertEquals(b, one("select count(*) from bioregion_taxon where is_endemic = 1 and bioregion_name = '" + b + "'"),
                one("select endemic_species_count from bioregion where name = '" + b + "'"));
        }
        // Geolocales: the number from the flags, also for a subregion (its flags were set, its number stayed 0).
        exec("update geolocale_taxon set is_endemic = 1 where geolocale_id = " + INDIAN_OCEAN + " and taxon_name = '" + PA + "'");
        PlaceTotals.writeCountsFromFlags(connection, PlaceTotals.GEOLOCALE, "endemic");
        assertEquals(2, one("select endemic_species_count from geolocale where id = " + MADAGASCAR));
        assertEquals(1, one("select endemic_species_count from geolocale where id = " + INDIAN_OCEAN));
    }

    public void testMuseumCodeIsAWholeWordOfOwnedBy() throws Exception {
        exec("insert into specimen (code, taxon_name, status, ownedBy) values ('o1', '" + PA + "', 'valid', 'CASC'),"
            + " ('o2', '" + PA + "', 'valid', 'CAS'), ('o3', '" + PA + "', 'valid', 'MCZC; CAS'), ('o4', '" + PA + "', 'valid', 'cas, San Francisco'),"
            + " ('o5', '" + PA + "', 'valid', 'CASENT collection'), ('o6', '" + PA + "', 'valid', 'MCZC')");
        try (PreparedStatement s = connection.prepareStatement(MuseumDb.specimenMuseumDml())) {
            s.setString(1, "CAS");
            s.setString(2, MuseumDb.ownedByPattern("CAS"));
            s.executeUpdate();
        }
        List<String> cas = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("select code from specimen where museum = 'CAS' order by code")) {
            while (r.next()) cas.add(r.getString(1));
        }
        assertEquals("[o2, o3, o4]", cas.toString());
        assertEquals("(^|[^[:alnum:]])ZMH[-]B($|[^[:alnum:]])", MuseumDb.ownedByPattern("ZMH-B"));
    }

    public void testTaxonImageCountsAddTheTaxaUnderThem() throws Exception {
        // As nightly set 4: every taxon from its own specimens, then the roll-up.
        exec("update taxon t set image_count = (select coalesce(sum(s.image_count), 0) from specimen s where s.taxon_name = t.taxon_name)");
        new ImageCountDb(connection).rollUpTaxonImageCounts();
        assertEquals("pheidole: a (2) + b (1)", 3, one("select image_count from taxon where taxon_name = '" + PHEIDOLE + "'"));
        assertEquals(4, one("select image_count from taxon where taxon_name = '" + FAMILY + "'"));
        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, r.mismatches.size());
    }

    public void testHomeNumbersCountImagesOfTheAnt() throws Exception {
        // a specimen filed under the genus name, with an image: a specimen imaged, not a species imaged
        exec("insert into specimen (code, taxon_name, status, upload_id, image_count) values ('g1', '" + PHEIDOLE + "', 'valid', 1, 1)");
        exec("insert into image values (20, 'g1', 'h', '2020-01-01 10:00:00')");
        assertEquals("casent1, casent3, g1 (casent5 has a label only)", 3, one(StatisticsDb.IMAGED_SPECIMENS_SQL));
        assertEquals("pheidole a, pheidole b", 2, one(StatisticsDb.IMAGED_SPECIES_SQL));
        assertEquals("pheidole a", 1, one(StatisticsDb.VALID_SPECIES_IMAGED_SQL));
        exec("update specimen set image_count = 1 where code = 'g1'");
        exec("update taxon set image_count = 4 where taxon_name in ('" + PHEIDOLE + "', '" + MYRM + "')");
        exec("update taxon set image_count = 5 where taxon_name = '" + FAMILY + "'");
        new StatisticsDb(connection).populateStatistics("nightly", 0, "set4", null);
        assertEquals(6, one("select specimens from statistics order by id desc limit 1"));
        assertEquals(3, one("select specimens_imaged from statistics order by id desc limit 1"));
        CountCheck.Result r = invariants();
        int home = 0;
        for (CountCheck.Mismatch m : r.mismatches) if ("home".equals(m.check)) ++home;
        assertEquals(list(r), 0, home);
    }

    public void testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens() {
        SpecimenNameUpdate.Summary s = new SpecimenNameUpdate.Summary();
        assertFalse(UtilDataAction.globalRecountNeeded(s));
        s.rowMode = SpecimenNameUpdate.WOULD_CHANGE;
        s.specimens = 5;
        assertFalse("report only", UtilDataAction.globalRecountNeeded(s));
        s.rowMode = SpecimenNameUpdate.CHANGED;
        assertTrue(UtilDataAction.globalRecountNeeded(s));
        s.capped = true;
        assertFalse("refused above maxChanges", UtilDataAction.globalRecountNeeded(s));
        s.capped = false;
        s.specimens = 0;
        assertFalse(UtilDataAction.globalRecountNeeded(s));
        assertFalse(UtilDataAction.globalRecountNeeded(null));
    }

    public void testStatsPageImageTableAddsUp() throws Exception {
        // casent1..5 have no caste (NULL): they were in no column; now Other.
        exec("update specimen set caste = 'worker' where code = 'casent1'");
        java.util.Map<String, int[]> stats = new org.calacademy.antweb.home.ImageDb(connection).getImageStats();
        int total = 0;
        for (int[] row : stats.values()) {
            assertEquals("Worker + Male + Queen + Other = Total", row[0], row[1] + row[2] + row[3] + row[4]);
            total += row[0];
        }
        assertEquals("every image of a specimen", 4, total);
    }

    public void testHabitatsAreOrderedByTheNumberShown() throws Exception {
        exec("alter table specimen add column habitat varchar(64), add column microhabitat varchar(64), add column method varchar(64),"
            + " add column collectioncode varchar(64)");
        // forest / leaf litter / Winkler: 3 specimens of one collection event (shown "1 times");
        // savanna / soil / pitfall: 2 events.
        exec("update specimen set habitat = 'forest', microhabitat = 'leaf litter', method = 'Winkler', collectioncode = 'c1'"
            + " where code in ('casent1', 'casent2')");
        exec("insert into specimen (code, taxon_name, status, habitat, microhabitat, method, collectioncode) values"
            + " ('h1', '" + PA + "', 'valid', 'forest', 'leaf litter', 'Winkler', 'c1'),"
            + " ('h2', '" + PA + "', 'valid', 'savanna', 'soil', 'pitfall', 'c2'), ('h3', '" + PA + "', 'valid', 'savanna', 'soil', 'pitfall', 'c3')");
        org.calacademy.antweb.Species s = new org.calacademy.antweb.Species();
        java.lang.reflect.Field f = Taxon.class.getDeclaredField("taxonName");
        f.setAccessible(true);
        f.set(s, PA);
        s.setHabitats(connection);
        assertEquals("[savanna:2, forest:1]", s.getHabitats().toString());
        s.setMicrohabitats(connection);
        assertEquals("[soil:2, leaf litter:1]", s.getMicrohabitats().toString());
        s.setMethods(connection);
        assertEquals("[pitfall:2, Winkler:1]", s.getMethods().toString());
    }

    public void testStatsPageCountsValidSpeciesAndSubspecies() {
        List<Taxon> taxa = new ArrayList<>();
        String[][] t = {{"genus", "valid"}, {"subfamily", "valid"}, {"species", "valid"}, {"subspecies", "valid"}, {"species", "morphotaxon"}};
        for (String[] a : t) {
            Taxon x = new Taxon();
            x.setRank(a[0]);
            x.setStatus(a[1]);
            taxa.add(x);
        }
        assertEquals(2, TaxonMgr.countValidSpecies(taxa));
    }
}

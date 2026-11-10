package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.ImagingProgressDb;
import org.calacademy.antweb.home.StatisticsDb;
import org.calacademy.antweb.util.CountCheck;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The imaging progress (ImagingProgressDb, imagingProgress.do; Brian Fisher, 2026-11-08) on the numbers fixture
 * (NumbersTestDb) plus a second country, Kenya (id 20), and bioregion Afrotropical, whose only specimen of pheidole a
 * (casent6) has no image:
 *
 *   pheidole a      valid species     imaged (casent1, Madagascar, Malagasy, CASC: head and label photo)
 *   pheidole a x    valid subspecies  not imaged; specimen casent4 at CASC
 *   hypoponera b    valid species     label photo only (casent5, MCZC): not imaged
 *   pheidole b      morphospecies     not counted (not a valid name)
 *
 * The rules: a label-only name is unimaged; a name imaged elsewhere counts as imaged in Kenya but not as imaged from
 * there; the rows of all of AntWeb equal the home page numbers; the unimaged list and its CSV; the count check finds a
 * wrong row; the trend reads the last statistics row of each day since 2026-10-28.
 *
 * Runs in CI against MySQL 8.4 (.github/workflows/tests.yaml); ANTWEB_TEST_DB_URL overrides.
 */
public class ImagingProgressDbTest extends TestCase {

    static final int KENYA = 20;

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec(connection, "alter table geolocale add column bioregion varchar(64)");   // as the live table
        exec(connection, "update geolocale set bioregion = 'Malagasy' where id = " + MADAGASCAR);
        exec(connection, "insert into geolocale (id, name, georank, parent, is_valid, is_live) values (" + KENYA + ", 'Kenya', 'country', 'Eastern Africa', 1, 1)");
        exec(connection, "insert into geolocale_taxon (geolocale_id, taxon_name) values (" + KENYA + ", '" + PA + "')");
        exec(connection, "insert into bioregion (name) values ('Afrotropical')");
        exec(connection, "insert into bioregion_taxon (bioregion_name, taxon_name) values ('Afrotropical', '" + PA + "')");
        exec(connection, "insert into specimen (code, taxon_name, status, country, museum, bioregion, family, subfamily) values"
            + " ('casent6', '" + PA + "', 'valid', 'Kenya', 'NMKE', 'Afrotropical', 'formicidae', '" + MYRM + "')");
        String line = new ImagingProgressDb(connection).fill();
        assertFalse(line, line.contains("FAILED"));
        assertTrue(line, line.startsWith(ImagingProgressDb.LOG + ": 1 of 3 valid living species and subspecies imaged; "));
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private ImagingProgressDb.Row row(String kind, String key) throws Exception {
        ImagingProgressDb.Row r = new ImagingProgressDb(connection).read(kind, key);
        assertNotNull(kind + " " + key + " has a row", r);
        return r;
    }

    private long one(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : -1;
        }
    }

    private static void assertRow(ImagingProgressDb.Row r, int total, int imaged, int here, int withSpecimens, int casc) {
        assertEquals(r + " total", total, r.total);
        assertEquals(r + " imaged", imaged, r.imaged);
        assertEquals(r + " imaged here", here, r.imagedHere);
        assertEquals(r + " unimaged with specimens", withSpecimens, r.unimagedWithSpecimens);
        assertEquals(r + " unimaged with a CASC specimen", casc, r.unimagedCasc);
    }

    public void testALabelOnlyNameIsUnimaged() throws Exception {
        // hypoponera b has one image, a label: its genus, museum and the headline count it as not imaged
        assertRow(row(ImagingProgressDb.GENUS, HYPO), 1, 0, -1, 1, 0);
        assertEquals("Hypoponera", row(ImagingProgressDb.GENUS, HYPO).label());
        assertEquals(PON, row(ImagingProgressDb.GENUS, HYPO).parent);
        assertRow(row(ImagingProgressDb.MUSEUM, "MCZC"), 1, 0, 0, 1, 0);
        // Madagascar: pheidole a, pheidole a x, hypoponera b (pheidole b is a morphospecies); only pheidole a imaged
        assertRow(row(ImagingProgressDb.COUNTRY, "" + MADAGASCAR), 3, 1, 1, 2, 1);
        assertEquals("Madagascar", row(ImagingProgressDb.COUNTRY, "" + MADAGASCAR).name);
        assertEquals("Malagasy", row(ImagingProgressDb.COUNTRY, "" + MADAGASCAR).parent);
        List<String> unimaged = new ArrayList<>();
        for (ImagingProgressDb.Unimaged u : new ImagingProgressDb(connection).readUnimaged()) unimaged.add(u.taxonName);
        assertTrue(unimaged.toString(), unimaged.contains(HB));
        assertFalse(unimaged.toString(), unimaged.contains(PA));
    }

    public void testImagedElsewhereIsImagedButNotImagedFromHere() throws Exception {
        // Kenya's only specimen of pheidole a has no image; the name is imaged from Madagascar
        assertRow(row(ImagingProgressDb.COUNTRY, "" + KENYA), 1, 1, 0, 0, 0);
        assertRow(row(ImagingProgressDb.BIOREGION, "Afrotropical"), 1, 1, 0, 0, 0);
        assertRow(row(ImagingProgressDb.MUSEUM, "NMKE"), 1, 1, 0, 0, 0);
        // where the photo was taken: imaged and imaged from here
        assertRow(row(ImagingProgressDb.BIOREGION, "Malagasy"), 2, 1, 1, 1, 0);
        assertRow(row(ImagingProgressDb.MUSEUM, "CASC"), 2, 1, 1, 1, 1);
        assertRow(row(ImagingProgressDb.GENUS, PHEIDOLE), 2, 1, -1, 1, 1);
    }

    public void testAllOfAntWebEqualsTheHomePageNumbers() throws Exception {
        assertRow(row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING), 3, 1, -1, 2, 1);
        ImagingProgressDb.Row species = row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING_SPECIES);
        assertEquals(one(StatisticsDb.LIVING_SPECIES_SQL), species.total);
        assertEquals(one(StatisticsDb.LIVING_SPECIES_IMAGED_SQL), species.imaged);
        assertEquals(2, species.total);
        assertEquals(1, species.imaged);
        assertNull("no fossils in the fixture: no fossil row", new ImagingProgressDb(connection).read(ImagingProgressDb.ALL, ImagingProgressDb.FOSSIL));
        exec(connection, "insert into taxon (taxon_name, taxarank, status, family, subfamily, genus, fossil) values"
            + " ('" + PHEIDOLE + " fos', 'species', 'valid', 'formicidae', '" + MYRM + "', 'pheidole', 1)");
        new ImagingProgressDb(connection).fill();
        assertRow(row(ImagingProgressDb.ALL, ImagingProgressDb.FOSSIL), 1, 0, -1, 0, 0);
        assertEquals("fossils are not in the headline", 3, row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING).total);
        // the stored home page number (statistics.living_species_imaged) equals the species-only row
        StatisticsDb.ensureHomeColumns(connection);
        new StatisticsDb(connection).populateStatistics("nightly", 0, "set4", null);
        assertEquals(species.imaged, one("select living_species_imaged from statistics order by id desc limit 1"));
        assertEquals(species.total, one("select living_species from statistics order by id desc limit 1"));
        // a second fill replaces the rows (no duplicates, every row written again)
        long rows = one("select count(*) from imaging_progress");
        new ImagingProgressDb(connection).fill();
        assertEquals(rows, one("select count(*) from imaging_progress"));
        assertEquals(0, one("select count(*) from imaging_progress where computed is null"));
    }

    public void testUnimagedListAndCsv() throws Exception {
        List<ImagingProgressDb.Unimaged> list = new ImagingProgressDb(connection).readUnimaged();
        ImagingProgressDb.Row living = row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING);
        assertEquals("the list has total minus imaged names", living.total - living.imaged, list.size());
        // CASC specimens first (the labs' queue): pheidole a x (casent4 at CASC), then hypoponera b (MCZC)
        assertEquals(PAX, list.get(0).taxonName);
        assertEquals(1, list.get(0).cascSpecimens);
        assertEquals("CASC", list.get(0).museums);
        assertEquals("Pheidole a x", list.get(0).displayName());
        assertEquals(HB, list.get(1).taxonName);
        assertEquals("MCZC", list.get(1).museums);
        assertEquals(HYPO, list.get(1).genusKey());
        String csv = ImagingProgressDb.csv(list);
        assertTrue(csv, csv.startsWith("taxon_name,name,rank,subfamily,genus,specimens,casc_specimens,museums\r\n"));
        assertTrue(csv, csv.contains(PAX + ",Pheidole a x,subspecies," + MYRM + ",pheidole,1,1,CASC\r\n"));
        assertEquals(3, csv.split("\r\n").length);
        // the unimaged() copy is the one of the current fill
        assertEquals(list.size(), new ImagingProgressDb(connection).unimaged().size());
        ImagingProgressDb.Unimaged odd = new ImagingProgressDb.Unimaged();
        odd.taxonName = "=cmd, \"x\"";
        String line = ImagingProgressDb.csv(java.util.Collections.singletonList(odd)).split("\r\n")[1];
        assertTrue(line, line.startsWith("\"'=cmd, \"\"x\"\"\","));
    }

    // Review 2026-11-08: one imaged specimen without a name emptied the whole list (NOT IN with a null).
    public void testAnImagedSpecimenWithoutANameDoesNotEmptyTheList() throws Exception {
        int before = new ImagingProgressDb(connection).readUnimaged().size();
        assertTrue("the fixture has unimaged names", before > 0);
        exec(connection, "insert into specimen (code, taxon_name, status, family) values ('noname1', null, 'valid', 'formicidae')");
        exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9901, 'noname1', 'h', '2026-11-01 10:00:00')");
        String line = new ImagingProgressDb(connection).fill();
        assertFalse(line, line.contains("FAILED"));
        assertEquals(before, new ImagingProgressDb(connection).readUnimaged().size());
    }

    public void testCountCheckAgreesAndFindsAWrongRow() throws Exception {
        CountCheck.Result r = checkRun();
        assertEquals(list(r), 0, count(r, true));
        assertTrue("the check compared numbers", compared(r) > 0);
        exec(connection, "update imaging_progress set imaged = 9 where place_type = 'all' and place_key = 'living'");
        exec(connection, "update imaging_progress set imaged_here = 2 where place_type = 'country' and place_key = '" + KENYA + "'");
        r = checkRun();
        String all = list(r);
        assertTrue(all, all.contains("imaging progress headline: imaged = a recount"));
        assertTrue(all, all.contains("imaged <= total"));
        assertTrue(all, all.contains("imaged from here <= imaged"));
        assertTrue(all, count(r, true) >= 3);
    }

    public void testTrendReadsTheLastRowOfEachDaySinceTheRuleChanged() throws Exception {
        exec(connection, "delete from statistics");
        exec(connection, "insert into statistics (action, extant_taxa, valid_species_imaged, created) values"
            + " ('nightly', 100, 10, '2026-10-27 07:00:00'), ('nightly', 100, 20, '2026-10-28 07:00:00'),"
            + " ('upload', 100, 21, '2026-10-28 15:00:00'), ('nightly', 101, 22, '2026-10-29 07:00:00')");
        List<ImagingProgressDb.Day> trend = new ImagingProgressDb(connection).trend();
        assertEquals(2, trend.size());
        assertEquals("2026-10-28", trend.get(0).date);
        assertEquals(21, trend.get(0).validImaged);
        assertEquals(22, trend.get(1).validImaged);
        assertEquals(101, trend.get(1).validTotal);
        assertEquals("21.8%", ImagingProgressDb.Row.percent(22, 101));
    }

    // ------------------------------------------------------------------ count check helpers (as CountInvariantsTest)

    private CountCheck.Result checkRun() throws Exception {
        CountCheck check = new CountCheck(connection);
        check.curatorNumbers = curatorNumbers();
        check.artistNumbers = artistNumbers();
        return check.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    private static int count(CountCheck.Result r, boolean real) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if ("imaging.progress".equals(m.check) && m.isReal() == real) ++n;
        return n;
    }

    private static int compared(CountCheck.Result r) {
        Integer[] n = {0};
        r.invariants.summary().forEach((id, v) -> { if ("imaging.progress".equals(id)) n[0] = v[0]; });
        return n[0];
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) if ("imaging.progress".equals(m.check)) b.append("\n  ").append(m);
        return b.toString();
    }
}

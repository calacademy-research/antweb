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
 * wrong row; photos of red-flagged specimens do not count (2026-11-09); the trend reads the last statistics row of each
 * day since ImagingProgressDb.RULE_SINCE (2026-11-12: the first daily update with that rule on production).
 *
 * The unimaged list is stored with the counts (table imaging_unimaged, 2026-11-11; staging 2026-11-10: the page said
 * "Still to image: 2,773" and the CSV, read live after a restart, had 2,780 names): the stored list always has the
 * headline's total minus imaged names, whatever changed since the fill and with nothing kept in memory; data that
 * changes while the fill counts is counted again; when it keeps changing, yesterday's counts and list stay, together;
 * counts stored before the list table existed have no list (the page says "not counted yet"); the count check finds a
 * stored list that is not the list of the counts.
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
        ImagingProgressDb.setBeforeListForTests(null);
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

    /**
     * Brian Fisher, 2026-11-09: photos of red-flagged specimens do not count. A head photo of a red-flagged specimen
     * leaves its name unimaged everywhere (the headline, its genus, country, museum and the home page's query); the
     * specimen still counts as a specimen of the name (unimaged, with specimens: it can be fixed and unflagged). The
     * count check recounts by the same rule and agrees.
     */
    public void testPhotosOfRedFlaggedSpecimensDoNotCount() throws Exception {
        exec(connection, "insert into specimen (code, taxon_name, status, country, museum, bioregion, family, subfamily, flag) values"
            + " ('casent7', '" + HB + "', 'valid', 'Madagascar', 'MCZC', 'Malagasy', 'formicidae', '" + PON + "', 'red')");
        exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9902, 'casent7', 'h', '2026-11-01 10:00:00')");
        new ImagingProgressDb(connection).fill();
        assertRow(row(ImagingProgressDb.GENUS, HYPO), 1, 0, -1, 1, 0);
        assertRow(row(ImagingProgressDb.MUSEUM, "MCZC"), 1, 0, 0, 1, 0);
        assertRow(row(ImagingProgressDb.COUNTRY, "" + MADAGASCAR), 3, 1, 1, 2, 1);
        assertRow(row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING), 3, 1, -1, 2, 1);
        ImagingProgressDb.Unimaged hb = null;
        for (ImagingProgressDb.Unimaged u : new ImagingProgressDb(connection).readUnimaged()) if (HB.equals(u.taxonName)) hb = u;
        assertNotNull("hypoponera b is still to image", hb);
        assertEquals("casent5 and the red-flagged casent7: still specimens", 2, hb.specimens);
        assertEquals(list(checkRun()), 0, count(checkRun(), true));

        // pheidole a's only photographed specimen (casent1) red-flagged: no valid living name is imaged
        exec(connection, "update specimen set flag = 'red' where code = 'casent1'");
        new ImagingProgressDb(connection).fill();
        assertRow(row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING), 3, 0, -1, 3, 2);
        assertRow(row(ImagingProgressDb.COUNTRY, "" + MADAGASCAR), 3, 0, 0, 3, 2);
        assertRow(row(ImagingProgressDb.MUSEUM, "CASC"), 2, 0, 0, 2, 2);
        assertEquals(0, one(StatisticsDb.LIVING_SPECIES_IMAGED_SQL));
        assertEquals(0, row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING_SPECIES).imaged);
        assertEquals(3, new ImagingProgressDb(connection).readUnimaged().size());
        assertEquals(list(checkRun()), 0, count(checkRun(), true));
    }

    public void testTrendReadsTheLastRowOfEachDaySinceTheRuleChanged() throws Exception {
        // The rule last changed when photos of red-flagged specimens stopped counting (decided 2026-11-09; before:
        // 2026-10-28). Production got it on 2026-11-11 after that day's daily update: its rows of 11-09, 11-10 and
        // 11-11 05:00 were counted the old way, a row written by an upload later on 11-11 the new way. The first day
        // counted by the rule on every server is 2026-11-12: the one date of the query and of the page's texts.
        assertEquals("2026-11-12", ImagingProgressDb.RULE_SINCE);
        exec(connection, "delete from statistics");
        exec(connection, "insert into statistics (action, extant_taxa, valid_species_imaged, created) values"
            + " ('nightly', 100, 8, '2026-11-09 05:40:00'), ('nightly', 100, 9, '2026-11-10 05:40:00'),"
            + " ('nightly', 100, 10, '2026-11-11 05:40:00'), ('upload', 100, 11, '2026-11-11 15:00:00'),"
            + " ('nightly', 100, 20, '2026-11-12 05:40:00'),"
            + " ('upload', 100, 21, '2026-11-12 15:00:00'), ('nightly', 101, 22, '2026-11-13 05:40:00')");
        List<ImagingProgressDb.Day> trend = new ImagingProgressDb(connection).trend();
        assertEquals("11-09, 11-10 and the mixed 11-11 are left out", 2, trend.size());
        assertEquals("2026-11-12", trend.get(0).date);
        assertEquals(21, trend.get(0).validImaged);
        assertEquals(22, trend.get(1).validImaged);
        assertEquals(101, trend.get(1).validTotal);
        assertEquals("21.8%", ImagingProgressDb.Row.percent(22, 101));
        // a server with rows before the date only (staging until its daily update of 11-12): no trend, no error
        exec(connection, "delete from statistics where created >= '2026-11-12'");
        assertTrue(new ImagingProgressDb(connection).trend().isEmpty());
    }

    // ------------------------------------------------------------------ the list stored with the counts (2026-11-11)

    /** The names of a list, in its order. */
    private static List<String> names(List<ImagingProgressDb.Unimaged> list) {
        List<String> out = new ArrayList<>();
        for (ImagingProgressDb.Unimaged u : list) out.add(u.taxonName);
        return out;
    }

    /** The headline's "Still to image": total minus imaged of the stored row all/living. */
    private int stillToImage() throws Exception {
        return row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING).unimaged();
    }

    /** What the page and its CSV show, read as after a restart (a new connection, nothing kept in memory): equal. */
    private void assertCsvIsThePagesNumber(String when, int expected) throws Exception {
        try (Connection other = connect()) {
            ImagingProgressDb db = new ImagingProgressDb(other);
            int page = db.read(ImagingProgressDb.ALL, ImagingProgressDb.LIVING).unimaged();
            List<ImagingProgressDb.Unimaged> list = db.unimaged();
            int csvRows = ImagingProgressDb.csv(list).split("\r\n").length - 1;
            assertEquals(when + ": Still to image", expected, page);
            assertEquals(when + ": CSV rows = the page's Still to image", page, csvRows);
            assertTrue(when + ": the stored list is the list of the stored counts", db.listStored());
            // the genus view: each genus row's total minus imaged = the names of the list in that genus
            for (ImagingProgressDb.Row genus : db.read(ImagingProgressDb.GENUS)) {
                int listed = 0;
                for (ImagingProgressDb.Unimaged u : list) if (u.genusKey().equalsIgnoreCase(genus.key)) ++listed;
                assertEquals(when + ": genus " + genus.key, genus.unimaged(), listed);
            }
        }
    }

    /**
     * Staging 2026-11-10: the page said "Still to image: 2,773" (stored by the daily update) and the CSV had 2,780 names
     * (read live at the first request after a restart, with what had changed since). The list is now stored by fill() in
     * the transaction of the counts: whatever changes afterwards, the CSV has the page's number of names, also when
     * nothing is kept in memory, and the next fill moves both.
     */
    public void testTheCsvAlwaysHasThePagesStillToImage() throws Exception {
        assertCsvIsThePagesNumber("after the fill", 2);
        assertEquals(java.util.Arrays.asList(PAX, HB), names(new ImagingProgressDb(connection).unimaged()));
        assertEquals("every stored row has its time", 0, one("select count(*) from imaging_unimaged where computed is null"));
        // since the fill: hypoponera b gets a head photo, and a new valid species arrives from AntCat
        exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9950, 'casent5', 'h', '2026-11-10 10:00:00')");
        exec(connection, "insert into taxon (taxon_name, taxarank, status, family, subfamily, genus, species) values"
            + " ('" + PHEIDOLE + " n1', 'species', 'valid', 'formicidae', '" + MYRM + "', 'pheidole', 'n1'),"
            + " ('" + PHEIDOLE + " n2', 'species', 'valid', 'formicidae', '" + MYRM + "', 'pheidole', 'n2')");
        assertEquals("counted now, the list is another one", java.util.Arrays.asList(PAX, PHEIDOLE + " n1", PHEIDOLE + " n2"),
            names(new ImagingProgressDb(connection).readUnimaged()));
        assertCsvIsThePagesNumber("data changed since the fill (as after a restart or a deploy)", 2);
        assertEquals("the stored list is the one of the fill", java.util.Arrays.asList(PAX, HB), names(new ImagingProgressDb(connection).unimaged()));
        // the next fill: the counts and the list move together
        String line = new ImagingProgressDb(connection).fill();
        assertFalse(line, line.contains("FAILED"));
        assertTrue(line, line.contains("; 3 unimaged names listed"));
        assertCsvIsThePagesNumber("after the next fill", 3);
        assertEquals(java.util.Arrays.asList(PAX, PHEIDOLE + " n1", PHEIDOLE + " n2"), names(new ImagingProgressDb(connection).unimaged()));
        // a second fill replaces the list (no rows left over, none twice)
        new ImagingProgressDb(connection).fill();
        assertEquals(3, one("select count(*) from imaging_unimaged"));
        assertEquals(3, one("select count(distinct taxon_name) from imaging_unimaged"));
    }

    /**
     * Data that changes while the fill counts (an image upload between the counts and the list): the counts and the list
     * are counted again and stored as one moment. Data that changes at every try: nothing of all of AntWeb, the genera
     * and the list is written, so yesterday's counts and yesterday's list stay, still equal; the line says FAILED and the
     * other kinds are written.
     */
    public void testDataChangingDuringTheFillNeverSeparatesTheCountsAndTheList() throws Exception {
        assertEquals(2, stillToImage());
        // once: hypoponera b gets its head photo after the counts and before the list is read
        int[] calls = {0};
        ImagingProgressDb.setBeforeListForTests(() -> {
            try {
                if (++calls[0] == 1) exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9951, 'casent5', 'h', '2026-11-10 10:00:00')");
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        });
        String line = new ImagingProgressDb(connection).fill();
        assertFalse(line, line.contains("FAILED"));
        assertEquals("counted a second time", 2, calls[0]);
        assertCsvIsThePagesNumber("an image arrived between the counts and the list", 1);
        assertEquals(java.util.Arrays.asList(PAX), names(new ImagingProgressDb(connection).unimaged()));

        // at every try: the photo comes and goes between the counts and the list
        calls[0] = 0;
        ImagingProgressDb.setBeforeListForTests(() -> {
            try {
                if (++calls[0] % 2 == 1) exec(connection, "delete from image where id = 9951");
                else exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9951, 'casent5', 'h', '2026-11-10 10:00:00')");
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        });
        exec(connection, "update imaging_progress set computed = '2026-11-01 05:40:00'");
        line = new ImagingProgressDb(connection).fill();
        assertEquals(3, calls[0]);
        assertTrue(line, line.contains("FAILED for all, genus and the unimaged list (the unimaged list has 2 names, total minus imaged is 1"));
        assertTrue(line, line.contains("3 of 3 tries"));
        assertFalse("no list was written: the line does not say one was", line.contains("unimaged names listed"));
        assertCsvIsThePagesNumber("the data changed at every try: yesterday's counts and list stay", 1);
        assertEquals("the rows of all of AntWeb are yesterday's", "2026-11-01 05:40:00",
            String.valueOf(row(ImagingProgressDb.ALL, ImagingProgressDb.LIVING).computed).substring(0, 19));
        assertEquals("the genus rows too", "2026-11-01 05:40:00", String.valueOf(row(ImagingProgressDb.GENUS, HYPO).computed).substring(0, 19));
        assertFalse("the other kinds were written", String.valueOf(row(ImagingProgressDb.MUSEUM, "MCZC").computed).startsWith("2026-11-01"));
        // a quiet fill afterwards: everything again (the photo is gone: hypoponera b is to image again)
        ImagingProgressDb.setBeforeListForTests(null);
        line = new ImagingProgressDb(connection).fill();
        assertFalse(line, line.contains("FAILED"));
        assertCsvIsThePagesNumber("the next quiet fill", 2);
    }

    /**
     * Counts stored before the list table existed (a server that had the page before 2026-11-11), or a list that is not
     * the list of the counts: listStored() is false, so the page says "not counted yet" and the CSV answers 503 instead
     * of a number and a list of two moments; nothing is read live. The count check reports it. The next fill stores both.
     */
    public void testCountsWithoutTheirListAreNotShown() throws Exception {
        ImagingProgressDb db = new ImagingProgressDb(connection);
        assertTrue(db.listStored());
        CountCheck.Result r = checkRun();
        assertEquals(list(r), 0, count(r, true));
        // the stored list lost a name: not the list of the counts, and a real difference for the count check
        exec(connection, "delete from imaging_unimaged where taxon_name = '" + HB + "'");
        assertFalse(db.listStored());
        r = checkRun();
        assertTrue(list(r), list(r).contains("names in the stored unimaged list (the CSV as downloaded) = total minus imaged"));
        assertEquals(list(r), 1, count(r, true));
        // no list at all (counts of before the list was stored): pending for the count check, not shown by the page
        exec(connection, "delete from imaging_unimaged");
        assertFalse(db.listStored());
        assertTrue("nothing is read live in its place", db.unimaged().isEmpty());
        r = checkRun();
        assertEquals(list(r), 0, count(r, true));
        assertEquals(list(r), 1, count(r, false));
        exec(connection, "drop table imaging_unimaged");
        assertFalse(db.listStored());
        assertTrue(db.unimaged().isEmpty());
        // nothing counted at all
        exec(connection, ImagingProgressDb.createListTable(null, null));
        exec(connection, "delete from imaging_progress");
        assertFalse(db.listStored());
        // the next fill stores both
        String line = db.fill();
        assertFalse(line, line.contains("FAILED"));
        assertCsvIsThePagesNumber("after the next fill", 2);
        // no unimaged name at all: an empty list is the list of the counts
        exec(connection, "insert into image (id, image_of_id, shot_type, upload_date) values (9952, 'casent5', 'h', '2026-11-10 10:00:00'),"
            + " (9953, 'casent4', 'h', '2026-11-10 10:00:00')");
        line = db.fill();
        assertFalse(line, line.contains("FAILED"));
        assertCsvIsThePagesNumber("every name imaged", 0);
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

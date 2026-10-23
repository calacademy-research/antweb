package test.org.calacademy.antweb.home;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.Curator;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.GeolocaleTaxonCountDb;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.GroupCountDb;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.home.MuseumDb;
import org.calacademy.antweb.home.MuseumTaxonCountDb;
import org.calacademy.antweb.home.MuseumTaxonDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.upload.Upload;
import org.calacademy.antweb.util.CountCheck;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The order and rules of the nightly counts found wrong on staging on 2026-10-22 (docs/numbers.md), against the
 * NumbersTestDb database:
 *
 *   genus rows       a name of a status that is not counted (unavailable) under a genus does not raise the genus row
 *                    (set 2's geolocaleTaxonFix rewrote it after the count with every row under the genus)
 *   museum lists     a specimen filed under a synonym: its list row is renamed to the valid name before the list is
 *                    counted and the totals are written, and the valid name's row counts its specimens (museum AFRC)
 *   curator edits    Description Edits = taxon, place and project page-text edits, every saved version
 *   upload groups    the group pages' numbers are the group's specimens today, recounted every night (Q13)
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class NightlyCountOrderDbTest extends TestCase {

    private Connection connection;

    static final String PC = "myrmicinaepheidole c";          // unavailable name under Pheidole
    static final String OLD_A = "myrmicinaepheidole olda";    // junior synonym of pheidole a
    static final String PD = "myrmicinaepheidole d";          // valid; its CASC specimen is filed under its synonym
    static final String OLD_D = "myrmicinaepheidole oldd";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        GroupCountDb.forgetTable();
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
            return r.next() ? r.getLong(1) : -1;
        }
    }

    private void taxon(String name, String status, String validName) throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, current_valid_name) values ('" + name
            + "', 'species', '" + status + "', '" + PHEIDOLE + "', 'formicidae', '" + MYRM + "', " + (validName == null ? "null" : "'" + validName + "'") + ")");
    }

    /** The protected crawl of a list (CountDb.childrenCountCrawl), as the nightly run calls it. */
    static final class GeoCrawl extends GeolocaleTaxonCountDb {
        GeoCrawl(Connection c) { super(c); }
        void crawl(Countable countable) throws SQLException { childrenCountCrawl(countable); }
    }

    static final class MuseumCrawl extends MuseumTaxonCountDb {
        MuseumCrawl(Connection c) { super(c); }
        void crawl(Countable countable) throws SQLException { childrenCountCrawl(countable); }
    }

    private static void callPrivate(Object target, String method, Class<?> type, Object arg) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, type);
        m.setAccessible(true);
        m.invoke(target, arg);
    }

    private CountCheck.Result invariants(Map<Integer, int[]> curators) throws Exception {
        CountCheck cc = new CountCheck(connection);
        cc.curatorNumbers = curators;
        return cc.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    private static int real(CountCheck.Result r, String check) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.isReal() && m.check.equals(check)) ++n;
        return n;
    }

    private static int realAt(CountCheck.Result r, String type, String key) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) {
            if (m.isReal() && m.place != null && type.equals(m.place.type) && key.equalsIgnoreCase(m.place.key)) ++n;
        }
        return n;
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m);
        return b.toString();
    }

    /** Staging 2026-10-22: US formicinaeformica 104 with an unavailable species on the list; 103 is right. */
    public void testAnUnavailableSpeciesDoesNotRaiseItsGenusRow() throws Exception {
        taxon(PC, "unavailable", null);
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + MADAGASCAR + ", '" + PC + "', '2020-01-01 10:00:00')");
        Geolocale madagascar = new Geolocale();
        madagascar.setId(MADAGASCAR);
        madagascar.setName("Madagascar");
        madagascar.setGeorank("country");
        new GeoCrawl(connection).crawl(madagascar);
        assertEquals("pheidole a, b and a x; not the unavailable c", 3,
            one("select species_count from geolocale_taxon where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + PHEIDOLE + "'"));
        // the admin job geolocaleTaxonFix (no longer in set 2) counts by the same rule
        assertEquals(3, new GeolocaleTaxonDb(connection).getGeolocaleTaxonCount(madagascar, PHEIDOLE));
        assertEquals(list(invariants(curatorNumbers())), 0, real(invariants(curatorNumbers()), "list.genus"));
    }

    /**
     * Staging 2026-10-22, museum AFRC: valid species rows whose specimens are all filed under an older name. The rows
     * are renamed before the count and the totals, the valid row counts the synonym's specimens, and a synonym whose
     * valid name is on the list already is merged into it (one row, specimens added).
     */
    public void testAMuseumListCountsTheSpecimensOfASynonymUnderTheValidName() throws Exception {
        taxon(OLD_A, "synonym", PA);
        taxon(PD, "valid", null);
        taxon(OLD_D, "synonym", PD);
        // specimens keep the status written at their upload (valid), as on staging
        exec("insert into specimen (code, taxon_name, status, museum, upload_id, family, subfamily, ownedBy) values"
            + " ('casent6', '" + OLD_A + "', 'valid', 'CASC', 1, 'formicidae', '" + MYRM + "', 'CASC'),"
            + " ('casent7', '" + OLD_D + "', 'valid', 'CASC', 1, 'formicidae', '" + MYRM + "', 'CASC')");
        // Global: every specimen under its own name
        exec("insert into proj_taxon (project_name, taxon_name, specimen_count, created) values ('allantwebants', '" + OLD_A + "', 1, '2020-01-01 10:00:00'),"
            + " ('allantwebants', '" + OLD_D + "', 1, '2020-01-01 10:00:00')");
        exec("update museum set specimen_count = 6 where code = 'CASC'");
        // the list as MuseumDb.populateSpecies builds it: one row per specimen name
        exec("insert into museum_taxon (code, taxon_name, specimen_count, created) values ('CASC', '" + OLD_A + "', 1, '2020-01-01 10:00:00'),"
            + " ('CASC', '" + OLD_D + "', 1, '2020-01-01 10:00:00')");

        assertEquals(2, new MuseumTaxonDb(connection).renameToValid(PlaceTotals.MUSEUM, "CASC"));
        Museum casc = new Museum();
        casc.setCode("CASC");
        new MuseumCrawl(connection).crawl(casc);
        callPrivate(new MuseumDb(connection), "finish", String.class, "CASC");

        assertEquals("no synonym rows left", 0, one("select count(*) from museum_taxon where code = 'CASC' and taxon_name in ('" + OLD_A + "', '" + OLD_D + "')"));
        assertEquals("merged, no duplicate", 1, one("select count(*) from museum_taxon where code = 'CASC' and taxon_name = '" + PA + "'"));
        assertEquals("casent1, 2 and the synonym's casent6", 3, one("select specimen_count from museum_taxon where code = 'CASC' and taxon_name = '" + PA + "'"));
        assertEquals("renamed row: its synonym's specimen", 1, one("select specimen_count from museum_taxon where code = 'CASC' and taxon_name = '" + PD + "'"));
        assertEquals("genus row: a, b, a x, d", 4, one("select species_count from museum_taxon where code = 'CASC' and taxon_name = '" + PHEIDOLE + "'"));
        assertEquals("genus row specimens", 6, one("select specimen_count from museum_taxon where code = 'CASC' and taxon_name = '" + PHEIDOLE + "'"));
        assertEquals("Species of the museum page counts the renamed row", 4, one("select species_count from museum where code = 'CASC'"));
        assertEquals("Valid species: a, a x, d", 3, one("select valid_species_count from museum where code = 'CASC'"));

        // the count check's recount of the museum's rows (rotating sample) and its invariants agree
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        CountCheck.Result sample = new CountCheck(connection).run(places, Timestamp.valueOf("2021-01-01 08:00:00"));
        int specimens = 0;
        for (CountCheck.Mismatch m : sample.mismatches) if ("specimens".equals(m.check)) ++specimens;
        assertEquals(list(sample), 0, specimens);
        CountCheck.Result r = invariants(curatorNumbers());
        assertEquals(list(r), 0, realAt(r, "museum", "CASC"));
        assertEquals(list(r), 0, real(r, "list.global"));
    }

    /** Brian Fisher 2026-10-22: Description Edits count taxon, place and project texts, every saved version. */
    public void testCuratorEditsCountPlaceAndProjectTexts() throws Exception {
        exec("insert into description_hist (access_login, created) values (5, '2020-01-01 10:00:00')");
        exec("insert into object_edit (object_key, access_login, created) values ('Madagascar', 5, '2020-01-01 10:00:00')");
        exec("insert into object_hist (object_key, access_login, created) values ('Madagascar', 5, '2020-01-01 10:00:00')");
        Curator curator = new Curator();
        curator.setId(5);
        new LoginDb(connection).setDescEditCounts(curator);
        assertEquals("description_edit, description_hist, object_edit, object_hist", 4, curator.getDescEditCount());

        Map<Integer, int[]> shown = curatorNumbers();
        shown.get(5)[1] = 4;
        assertEquals(list(invariants(shown)), 0, real(invariants(shown), "admin.curators"));
        shown.get(5)[1] = 1;   // the old rule: description_edit only
        assertEquals(1, real(invariants(shown), "admin.curators"));
    }

    /** Q13 (2026-10-22): the group pages show the group's specimens today, recounted every night. */
    public void testGroupNumbersAreRecountedEveryNight() throws Exception {
        exec("insert into ant_group values (2, 'Gone', 0, 0)");
        exec("update specimen set flag = 'red' where code = 'casent5'");
        String message = new GroupCountDb(connection).recountAll();
        assertTrue(message, message.startsWith("Group counts:"));
        assertEquals("flagged since the upload", 1, one("select flagged from group_count where group_id = 1"));
        assertEquals("a flagged specimen is not counted", 4, one("select specimens from group_count where group_id = 1"));
        assertEquals("a group without specimens shows 0", 0, one("select specimens from group_count where group_id = 2"));
        assertEquals("its upload row keeps the upload's numbers", 5, one("select specimens from upload where upload_id = 1"));

        Upload upload = new Upload();
        upload.setGroupId(1);
        upload.setSpecimens(5);
        new GroupCountDb(connection).apply(upload);
        assertEquals(4, upload.getSpecimens());
        assertEquals(1, upload.getFlagged());
        assertNotNull(upload.getCountedAt());

        CountCheck.Result r = invariants(curatorNumbers());
        assertEquals(list(r), 0, real(r, "admin.groups"));
        exec("update group_count set species = 9 where group_id = 1");
        assertEquals(1, real(invariants(curatorNumbers()), "admin.groups"));
    }
}

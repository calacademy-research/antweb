package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;

import junit.framework.TestCase;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.GeolocaleTaxonCountDb;
import org.calacademy.antweb.home.ProjTaxonCountDb;
import org.calacademy.antweb.home.TaxonParentDb;
import org.calacademy.antweb.util.CountCheck;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The taxon tree the lists count by (2026-10-22, docs/numbers.md B36 and B40), against the NumbersTestDb database:
 *
 *   B36  every ant species and subspecies of a counted status that is its own current valid name has its genus as parent,
 *        and the genus is a taxon row: nightly set 1 makes the missing genus rows and sets the parents
 *        (TaxonParentDb.repair; staging: '(indet)decamorium decem' with no parent and no genus row)
 *   B40  a list's genus, subfamily and family rows hold the images of every row under them by the taxon tree; a synonym
 *        filed under its old genus counts in the genus of its valid name (staging worldants: ectatomminaeholcoponera 596,
 *        its rows 604)
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class TaxonTreeCountDbTest extends TestCase {

    private Connection connection;

    static final String INDET = "(indet)decamorium decem";      // indetermined, no parent, its genus has no row
    static final String INDET_GENUS = "(indet)decamorium";
    static final String PE = "myrmicinaepheidole e";            // valid, no parent
    static final String PF = "myrmicinaepheidole f";            // valid, under another genus
    static final String SYN = "myrmicinaewasmannia aurea";      // synonym of pheidole a: the parent of its valid name
    static final String WASMANNIA = "myrmicinaewasmannia";

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        fill(connection);
        exec("alter table taxon add column source varchar(64), add column insert_method varchar(64)");
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

    private String text(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : "(no row)";
        }
    }

    private CountCheck.Result invariants() throws Exception {
        CountCheck cc = new CountCheck(connection);
        cc.curatorNumbers = curatorNumbers();
        return cc.run(new ArrayList<>(), new ArrayList<>(), 0, Timestamp.valueOf("2021-01-01 08:15:00"), true, true, true, null);
    }

    private static int real(CountCheck.Result r, String check) {
        int n = 0;
        for (CountCheck.Mismatch m : r.mismatches) if (m.isReal() && m.check.equals(check)) ++n;
        return n;
    }

    private static String list(CountCheck.Result r) {
        StringBuilder b = new StringBuilder();
        for (CountCheck.Mismatch m : r.mismatches) b.append("\n  ").append(m);
        return b.toString();
    }

    private void species(String name, String status, String parent, String subfamily, String genus, String validName) throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus, current_valid_name) values ('"
            + name + "', 'species', '" + status + "', " + (parent == null ? "null" : "'" + parent + "'") + ", 'formicidae', '" + subfamily + "', '"
            + genus + "', " + (validName == null ? "null" : "'" + validName + "'") + ")");
    }

    private int previewRows() throws Exception {
        int n = 0;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(TaxonParentDb.PREVIEW_SQL)) {
            while (r.next()) ++n;
        }
        return n;
    }

    /** Staging 2026-10-22 (taxon.parents 6): '(indet)decamorium decem' had no parent, and its genus no taxon row. */
    public void testRepairGivesEveryCountedNameItsGenus() throws Exception {
        species(INDET, "indetermined", null, "(indet)", INDET_GENUS, null);
        species(PE, "valid", null, MYRM, "pheidole", null);
        species(PF, "valid", HYPO, MYRM, "pheidole", null);
        species(SYN, "synonym", PHEIDOLE, MYRM, "wasmannia", PA);
        assertEquals(list(invariants()), 3, real(invariants(), "taxon.parents"));
        assertEquals(3, previewRows());

        TaxonParentDb.Result result = new TaxonParentDb(connection).repair();
        assertEquals("the genus row of the indet name", 1, result.genera);
        assertEquals(3, result.parents);
        assertEquals(INDET_GENUS, text("select parent_taxon_name from taxon where taxon_name = '" + INDET + "'"));
        assertEquals(PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + PE + "'"));
        assertEquals(PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + PF + "'"));
        assertEquals("a synonym keeps the parent of its valid name", PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + SYN + "'"));
        assertEquals("genus unrecognized parentFix", text("select concat(taxarank, ' ', status, ' ', insert_method) from taxon where taxon_name = '" + INDET_GENUS + "'"));
        assertEquals("no subfamily row (indet): no parent", 1, one("select count(*) from taxon where taxon_name = '" + INDET_GENUS + "' and parent_taxon_name is null"));

        assertEquals(0, previewRows());
        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, real(r, "taxon.parents"));
        TaxonParentDb.Result again = new TaxonParentDb(connection).repair();
        assertFalse("a second run changes nothing", again.changed());
    }

    /** A parent that names the genus while the genus has no taxon row: the lists have no genus row to count it under. */
    public void testAParentWithoutAGenusRowIsFoundAndMade() throws Exception {
        species("myrmicinaenovus a", "valid", "myrmicinaenovus", MYRM, "novus", null);
        assertEquals(list(invariants()), 1, real(invariants(), "taxon.parents"));
        TaxonParentDb.Result result = new TaxonParentDb(connection).repair();
        assertEquals(1, result.genera);
        assertEquals(0, result.parents);
        assertEquals("under its subfamily row", MYRM, text("select parent_taxon_name from taxon where taxon_name = 'myrmicinaenovus'"));
        assertEquals(0, real(invariants(), "taxon.parents"));
    }

    /** The protected image crawl of one list (CountDb.imageCountCrawl), as nightly set 4 calls it. */
    static final class ProjectImages extends ProjTaxonCountDb {
        ProjectImages(Connection c) { super(c); }
        void crawl(Countable countable) throws SQLException { imageCountCrawl(countable); }
    }

    static final class GeoImages extends GeolocaleTaxonCountDb {
        GeoImages(Connection c) { super(c); }
        void crawl(Countable countable) throws SQLException { imageCountCrawl(countable); }
    }

    private long images(String table, String keyColumn, String key, String name) throws Exception {
        return one("select image_count from " + table + " where " + keyColumn + " = " + key + " and taxon_name = '" + name + "'");
    }

    /**
     * Staging 2026-10-22 (list.images 9): worldants ectatomminaeholcoponera 596, its rows 604; myrmicinaeallomerus 394, its
     * rows 401. A synonym takes the parent of its valid name; the images of its specimens (filed under the old genus, as
     * specimen.genus says) went to the old genus row and not to the genus that holds the synonym's row.
     */
    public void testGenusRowHoldsTheImagesOfASynonymUnderIt() throws Exception {
        species(SYN, "synonym", PHEIDOLE, MYRM, "wasmannia", PA);
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values ('" + WASMANNIA
            + "', 'genus', 'synonym', '" + MYRM + "', 'formicidae', '" + MYRM + "', 'wasmannia')");
        exec("insert into specimen (code, taxon_name, status, country, museum, upload_id, image_count, family, subfamily, genus, species, access_group)"
            + " values ('casent6', '" + SYN + "', 'synonym', 'Madagascar', 'CASC', 1, 3, 'formicidae', '" + MYRM + "', 'wasmannia', 'aurea', 1)");
        exec("insert into proj_taxon (project_name, taxon_name, created) values ('madants', '" + SYN + "', '2020-01-01'), ('madants', '" + WASMANNIA + "', '2020-01-01')");
        exec("update proj_taxon set image_count = 99 where project_name = 'madants'");   // the crawl writes every row it counts

        new ProjectImages(connection).crawl(new Project("madants"));
        String m = "'madants'";
        assertEquals(2, images("proj_taxon", "project_name", m, PA));
        assertEquals(3, images("proj_taxon", "project_name", m, SYN));
        assertEquals("pheidole a 2, b 1, a x 0 and the synonym 3", 6, images("proj_taxon", "project_name", m, PHEIDOLE));
        assertEquals("the old genus row holds none of it", 99, images("proj_taxon", "project_name", m, WASMANNIA));
        assertEquals(6, images("proj_taxon", "project_name", m, MYRM));
        assertEquals(1, images("proj_taxon", "project_name", m, PON));
        assertEquals(7, images("proj_taxon", "project_name", m, FAMILY));

        exec("update proj_taxon set image_count = 0 where project_name = 'madants' and taxon_name = '" + WASMANNIA + "'");   // as the nightly reset leaves it
        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, real(r, "list.images"));
    }

    /** The same crawl on the fixture's country writes the numbers the fixture has (nothing else changes). */
    public void testCountryImagesAreUnchanged() throws Exception {
        exec("update geolocale_taxon set image_count = 0 where geolocale_id = " + MADAGASCAR);
        Geolocale madagascar = new Geolocale();
        madagascar.setId(MADAGASCAR);
        madagascar.setName("Madagascar");
        madagascar.setGeorank("country");
        new GeoImages(connection).crawl(madagascar);
        String g = "" + MADAGASCAR;
        assertEquals(4, images("geolocale_taxon", "geolocale_id", g, FAMILY));
        assertEquals(3, images("geolocale_taxon", "geolocale_id", g, MYRM));
        assertEquals(1, images("geolocale_taxon", "geolocale_id", g, PON));
        assertEquals(3, images("geolocale_taxon", "geolocale_id", g, PHEIDOLE));
        assertEquals(1, images("geolocale_taxon", "geolocale_id", g, HYPO));
        assertEquals(2, images("geolocale_taxon", "geolocale_id", g, PA));
        assertEquals(1, images("geolocale_taxon", "geolocale_id", g, PB));
        assertEquals(0, images("geolocale_taxon", "geolocale_id", g, PAX));
        assertEquals(1, images("geolocale_taxon", "geolocale_id", g, HB));
        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, real(r, "list.images"));
    }
}

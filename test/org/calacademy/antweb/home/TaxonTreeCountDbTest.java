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
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.home.ProjTaxonCountDb;
import org.calacademy.antweb.home.TaxonParentDb;
import org.calacademy.antweb.util.CountCheck;

import test.org.calacademy.antweb.util.NumbersTestDb;

import static test.org.calacademy.antweb.util.NumbersTestDb.*;

/**
 * The taxon tree the lists count by (2026-10-22, docs/numbers.md B36 and B40), against the NumbersTestDb database:
 *
 *   B36  every ant species and subspecies of a counted status that is its own current valid name and is identified to
 *        genus has its genus row as parent: nightly set 1 sets the parents where the genus row exists, never makes a genus
 *        row, and reports the names whose genus row is missing (TaxonParentDb.repair; staging: the Rennes morphotaxa
 *        repaired; '(indet)decamorium decem' and the valid 'formicinaewilsonia liaoningensis' reported)
 *   names not identified to genus (Brian Fisher, 2026-10-22) are not species: not in species totals, genus rows'
 *        Species, pies or endemism; their specimens still count in the Specimens; no genus rows are made for them
 *   B40  a list's genus, subfamily and family rows hold the images of every row under them by the taxon tree; a synonym
 *        filed under its old genus counts in the genus of its valid name (staging worldants: ectatomminaeholcoponera 596,
 *        its rows 604)
 *
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class TaxonTreeCountDbTest extends TestCase {

    private Connection connection;

    static final String INDET = "(indet)decamorium decem";      // indetermined, no parent, its genus has no row (the file lacked the subfamily)
    static final String INDET_GENUS = "(indet)decamorium";
    static final String DECAMORIUM = "myrmicinaedecamorium";     // the AntCat genus row of Decamorium
    static final String MORPHO = "amblyoponinaeamblyoponine_genus8 nz01";   // morphotaxon, no parent, its genus row exists (Rennes)
    static final String MORPHO_GENUS = "amblyoponinaeamblyoponine_genus8";
    static final String WILSONIA = "formicinaewilsonia liaoningensis";       // valid, its genus has no row (AntCat import problem)
    /** Not identified to genus (staging 2026-10-22), each with its genus column. */
    static final String[][] UNIDENTIFIED = {
        {"(indet)((indet)) (indet)", "((indet))"}, {"(indet)(formicinae) (indet)", "(formicinae)"},
        {"formicinaeindet (indet)", "indet"}, {"indetindet (indet)", "indet"}};
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

    private void genus(String name, String subfamily, String genus, String status) throws Exception {
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family, subfamily, genus) values ('" + name + "', 'genus', '"
            + status + "', '" + subfamily + "', 'formicidae', '" + subfamily + "', '" + genus + "')");
    }

    /**
     * Staging 2026-10-22 (preview 14 names): the repair sets parents only where the genus row exists (the Rennes
     * morphotaxa), never makes a genus row, leaves names not identified to genus alone, and reports the rest.
     */
    public void testRepairSetsParentsOnlyToExistingGenusRows() throws Exception {
        species(INDET, "indetermined", null, "(indet)", "decamorium", null);
        genus(DECAMORIUM, MYRM, "decamorium", "valid");
        species(PE, "valid", null, MYRM, "pheidole", null);
        species(PF, "valid", HYPO, MYRM, "pheidole", null);
        species(SYN, "synonym", PHEIDOLE, MYRM, "wasmannia", PA);
        genus(MORPHO_GENUS, "amblyoponinae", "amblyoponine_genus8", "morphotaxon");
        species(MORPHO, "morphotaxon", null, "amblyoponinae", "amblyoponine_genus8", null);
        species(WILSONIA, "valid", null, "formicinae", "wilsonia", null);
        for (String[] u : UNIDENTIFIED) species(u[0], "indetermined", u[0].startsWith("formicinaeindet") ? u[0] : null, u[0].substring(0, u[0].indexOf(u[1])), u[1], null);
        long taxa = one("select count(*) from taxon");

        CountCheck.Result before = invariants();
        assertEquals("INDET, PE, PF, MORPHO, WILSONIA (the unidentified names are not species)" + list(before), 5, real(before, "taxon.parents"));
        assertEquals(5, previewRows());

        TaxonParentDb.Result result = new TaxonParentDb(connection).repair();
        assertEquals("PE, PF and the morphotaxon", 3, result.parents);
        assertEquals("no genus row is ever made", taxa, one("select count(*) from taxon"));
        assertEquals(PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + PE + "'"));
        assertEquals(PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + PF + "'"));
        assertEquals(MORPHO_GENUS, text("select parent_taxon_name from taxon where taxon_name = '" + MORPHO + "'"));
        assertEquals("a synonym keeps the parent of its valid name", PHEIDOLE, text("select parent_taxon_name from taxon where taxon_name = '" + SYN + "'"));
        assertNull("not repaired: its genus has no row", text("select parent_taxon_name from taxon where taxon_name = '" + INDET + "'"));
        for (String[] u : UNIDENTIFIED) {
            assertEquals(u[0] + " left as it was", u[0].startsWith("formicinaeindet") ? u[0] : null,
                text("select parent_taxon_name from taxon where taxon_name = '" + u[0] + "'"));
        }
        assertEquals(0, one("select count(*) from taxon where taxarank = 'genus' and (genus like '(%' or genus in ('indet', 'undet'))"));

        assertEquals("reported: INDET and WILSONIA", 2, result.missing);
        assertEquals(1, result.missingValid);
        assertTrue(result.line(), result.line().contains(INDET + " (indetermined; genus row " + DECAMORIUM + ")"));
        assertTrue(result.alert(), result.alert().startsWith(TaxonParentDb.MISSING_VALID_ALERT) && result.alert().contains(WILSONIA));

        assertEquals("left: the two whose genus row is missing", 2, previewRows());
        CountCheck.Result r = invariants();
        assertEquals(list(r), 2, real(r, "taxon.parents"));
        TaxonParentDb.Result again = new TaxonParentDb(connection).repair();
        assertFalse("a second run changes nothing", again.changed());
        assertEquals(2, again.missing);
    }

    /** No valid name without a genus row: no alert. */
    public void testNoAlertWithoutAValidNameMissingItsGenus() throws Exception {
        species(INDET, "indetermined", null, "(indet)", "decamorium", null);
        TaxonParentDb.Result result = new TaxonParentDb(connection).repair();
        assertEquals(1, result.missing);
        assertEquals(0, result.missingValid);
        assertNull(result.alert());
    }

    /** The SQL rule and the Java rule of "identified to genus" agree. */
    public void testIdentifiedToGenusSqlAndJavaAgree() throws Exception {
        String[] genera = {"pheidole", "amblyoponine_genus8", "indet", "INDET", "undet", "(indet)", "((indet))", "(formicinae)", "(formicidae)", "", "decamorium"};
        exec("create temporary table g (genus varchar(64))");
        for (String g : genera) {
            exec("delete from g");
            exec("insert into g values ('" + g + "')");
            assertEquals(g, PlaceTotals.isIdentifiedToGenus(g) ? 1 : 0, one("select count(*) from g a where " + PlaceTotals.identifiedToGenus("a")));
        }
        exec("delete from g");
        exec("insert into g values (null)");
        assertEquals("no genus value: counted as before", 1, one("select count(*) from g a where " + PlaceTotals.identifiedToGenus("a")));
        assertTrue(PlaceTotals.isIdentifiedToGenus(null));
        assertFalse(PlaceTotals.isIdentifiedToGenus("(myrmicinae)"));
    }

    static final String U = "myrmicinae(myrmicinae) (indet)";   // not identified to genus

    /**
     * A name not identified to genus on a project list, under a genus row (a parent from an old upload): the nightly
     * count leaves it out of the genus row's, the subfamily's and the family's Species, and of the project's Species
     * and its pie; its specimen counts in the Specimens. The count check agrees.
     */
    public void testANameNotIdentifiedToGenusIsNotASpecies() throws Exception {
        species(U, "indetermined", PHEIDOLE, MYRM, "(myrmicinae)", null);
        exec("insert into specimen (code, taxon_name, status, country, museum, upload_id, image_count, family, subfamily, genus, species, access_group)"
            + " values ('casent6', '" + U + "', 'indetermined', 'Madagascar', null, 1, 0, 'formicidae', '" + MYRM + "', '(myrmicinae)', '(indet)', 1)");
        exec("insert into proj_taxon (project_name, taxon_name, created) values ('madants', '" + U + "', '2020-01-01')");

        new ProjTaxonCountDb(connection).recount(new Project("madants"));
        String m = "'madants'";
        assertEquals("pheidole a, b, a x", 3, one("select species_count from proj_taxon where project_name = " + m + " and taxon_name = '" + PHEIDOLE + "'"));
        assertEquals("its specimen counts", 5, one("select specimen_count from proj_taxon where project_name = " + m + " and taxon_name = '" + PHEIDOLE + "'"));
        assertEquals(3, one("select species_count from proj_taxon where project_name = " + m + " and taxon_name = '" + MYRM + "'"));
        assertEquals(5, one("select specimen_count from proj_taxon where project_name = " + m + " and taxon_name = '" + MYRM + "'"));
        assertEquals(4, one("select species_count from proj_taxon where project_name = " + m + " and taxon_name = '" + FAMILY + "'"));
        assertEquals(6, one("select specimen_count from proj_taxon where project_name = " + m + " and taxon_name = '" + FAMILY + "'"));

        int[] totals = PlaceTotals.writeListedCounts(connection, PlaceTotals.PROJECT, "madants");
        assertEquals("Species", 4, totals[2]);
        PlaceTotals.writePies(connection, PlaceTotals.PROJECT, "madants");
        assertEquals("Species per Subfamily", 4, PlaceTotals.pieSum(text("select taxon_subfamily_dist_json from project where project_name = " + m)));
        assertEquals("Specimens per Subfamily", 6, PlaceTotals.pieSum(text("select specimen_subfamily_dist_json from project where project_name = " + m)));
        exec("update project set specimen_count = 6 where project_name = " + m);

        CountCheck.Result r = invariants();
        for (String check : new String[] {"list.genus", "list.subfamily", "list.family", "place.totals.species", "place.totals.specimens", "place.pies", "species", "taxon.parents"}) {
            assertEquals(check + list(r), 0, real(r, check));
        }
    }

    /**
     * A name not identified to genus found in one country only is not endemic there (the check's recount of the flags),
     * and is not in the country's Species; its specimen is in the country's Specimens.
     */
    public void testANameNotIdentifiedToGenusIsNotEndemic() throws Exception {
        species(U, "indetermined", null, MYRM, "(myrmicinae)", null);
        exec("insert into specimen (code, taxon_name, status, country, museum, upload_id, image_count, family, subfamily, genus, species, access_group)"
            + " values ('casent6', '" + U + "', 'indetermined', 'Madagascar', null, 1, 0, 'formicidae', '" + MYRM + "', '(myrmicinae)', '(indet)', 1)");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, specimen_count, created) values (" + MADAGASCAR + ", '" + U + "', 1, '2020-01-01')");
        assertEquals(4, PlaceTotals.listedCounts(connection, PlaceTotals.GEOLOCALE, "" + MADAGASCAR)[2]);

        CountCheck.Result r = invariants();
        assertEquals(list(r), 0, real(r, "place.endemic.flags"));
        assertEquals(list(r), 0, real(r, "place.totals.species"));
        assertEquals("the stored Specimens (5) lack its specimen" + list(r), 1, real(r, "place.totals.specimens"));
        exec("update geolocale set specimen_count = 6 where id = " + MADAGASCAR);
        assertEquals(0, real(invariants(), "place.totals.specimens"));
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

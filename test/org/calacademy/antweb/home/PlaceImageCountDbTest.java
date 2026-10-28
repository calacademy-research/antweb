package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.util.CountCheck;

/**
 * Image numbers of places (PlaceImageCountDb, 2026-10-20) against a database: Images (all files,
 * labels too), images per view, Imaged specimens (an image that is not a label) and specimens with
 * only a label photo, for an island (Madagascar), a country, an adm1, a region, a museum, a bioregion
 * and a project; the per-taxon image counts of a place (Geolocale.getTaxonImageCountQuery) only count
 * the place's specimens; the count check (CountCheck "place images") finds stale numbers.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like CountCheckTest; ANTWEB_TEST_DB_URL overrides.
 */
public class PlaceImageCountDbTest extends TestCase {

    static final String SUBFAMILY = "myrmicinae";
    static final String GENUS = "myrmicinaepheidole";
    static final String SP_A = "myrmicinaepheidole a1";            // morphospecies
    static final String SP_B = "myrmicinaepheidole megacephala";
    static final String SP_C = "myrmicinaepheidole other";         // not on the Madagascar list

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"specimen", "image", "taxon", "geolocale", "geolocale_taxon", "proj_taxon", "project",
                                      "museum", "bioregion", "museum_taxon", "bioregion_taxon", "upload", "place_image_count"}) {
            exec("drop table if exists " + t);
        }
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128),"
            + " family varchar(64) default 'formicidae', current_valid_name varchar(128))");   // place numbers count ants only (2026-10-21)
        // the genus column of the live table (the species counts read it: PlaceTotals.identifiedToGenus); invisible, so
        // the inserts by position stay as they are
        exec("alter table taxon add column genus varchar(64) invisible");
        exec("create table specimen (code varchar(128) primary key, taxon_name varchar(128), status varchar(40), country varchar(64),"
            + " adm1 varchar(64), island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int,"
            + " family varchar(64) default 'formicidae', subfamily varchar(64), genus varchar(64), species varchar(64), image_count int default 0)");
        exec("create table image (id int primary key, image_of_id varchar(128), shot_type varchar(10), upload_date datetime)");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128), is_valid tinyint default 1,"
            + " is_island tinyint default 0, image_count int default 0, imaged_specimen_count int default 0)");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (geolocale_id, taxon_name))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (project_name, taxon_name))");
        exec("create table museum_taxon (code varchar(64), taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (code, taxon_name))");
        exec("create table bioregion_taxon (bioregion_name varchar(64), taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (bioregion_name, taxon_name))");
        exec("create table project (project_name varchar(64) primary key, image_count int default 0, imaged_specimen_count int default 0)");
        exec("create table museum (code varchar(64) primary key, image_count int default 0, imaged_specimen_count int default 0)");
        exec("create table bioregion (name varchar(64) primary key, image_count int default 0, imaged_specimen_count int default 0)");
        exec("create table upload (upload_id int primary key, created timestamp null)");
        exec("insert into upload values (1, '2020-01-01 10:00:00')");
        exec(PlaceImageCountDb.createTable(null, null));   // as the application does at startup

        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name) values ('" + SUBFAMILY + "', 'subfamily', 'valid', 'formicidae')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name) values ('" + GENUS + "', 'genus', 'valid', '" + SUBFAMILY + "')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name) values ('" + SP_A + "', 'species', 'morphotaxon', '" + GENUS + "')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name) values ('" + SP_B + "', 'species', 'valid', '" + GENUS + "')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name) values ('" + SP_C + "', 'species', 'valid', '" + GENUS + "')");

        exec("insert into geolocale values (7, 'Madagascar', 'country', 'Eastern Africa', 1, 1, 3435, 9999)");   // stale numbers, as before the fix
        exec("insert into geolocale values (8, 'Comoros', 'country', 'Eastern Africa', 1, 0, 0, 0)");
        exec("insert into geolocale values (9, 'Antananarivo', 'adm1', 'Madagascar', 1, 0, 0, 0)");
        exec("insert into geolocale values (1, 'Africa', 'region', null, 1, 0, 0, 0)");
        exec("insert into geolocale values (2, 'Eastern Africa', 'subregion', 'Africa', 1, 0, 0, 0)");
        String old = "'2020-01-01 10:00:00'";
        for (int g : new int[] {7, 1}) {
            for (String t : new String[] {SUBFAMILY, GENUS, SP_A, SP_B}) exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + g + ", '" + t + "', " + old + ")");
        }
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (8, '" + SP_B + "', " + old + ")");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (9, '" + SP_A + "', " + old + ")");
        exec("insert into project values ('madants', 0, 0)");
        for (String t : new String[] {GENUS, SP_A, SP_B}) exec("insert into proj_taxon (project_name, taxon_name, created) values ('madants', '" + t + "', " + old + ")");
        exec("insert into museum values ('CASC', 0, 0)");
        exec("insert into bioregion values ('Malagasy', 0, 0)");
        // their lists (2026-10-28: a museum's or bioregion's images are those of the specimens of its list rows)
        for (String t : new String[] {GENUS, SP_A, SP_B}) exec("insert into museum_taxon (code, taxon_name, created) values ('CASC', '" + t + "', " + old + ")");
        for (String t : new String[] {GENUS, SP_A, SP_B, SP_C}) exec("insert into bioregion_taxon (bioregion_name, taxon_name, created) values ('Malagasy', '" + t + "', " + old + ")");

        specimen("casent1", SP_A, "morphotaxon", "Madagascar", "Antananarivo", "Madagascar", "CASC", "Malagasy", "h", "p", "d", "l");
        specimen("casent2", SP_A, "morphotaxon", "Madagascar", null, "Madagascar", "CASC", "Malagasy", "l");          // label only
        specimen("casent3", SP_B, "valid", "Madagascar", null, "Madagascar", "CASC", "Malagasy", "H", "f", "");      // upper case, field, empty
        specimen("casent4", SP_B, "valid", "Comoros", null, null, "MCZC", "Malagasy", "h");
        specimen("casent5", SP_B, "synonym", "Madagascar", null, "Madagascar", "CASC", "Malagasy", "p");             // status not counted in geolocales
        specimen("casent6", SP_C, "valid", "Madagascar", null, "Madagascar", null, "Malagasy", "h");                 // not on the Madagascar list
    }

    private int nextImageId = 1;

    private void specimen(String code, String taxon, String status, String country, String adm1, String island,
                          String museum, String bioregion, String... shots) throws SQLException {
        exec("insert into specimen (code, taxon_name, status, country, adm1, island_country, museum, bioregion, upload_id,"
            + " family, subfamily, genus, species, image_count) values ('" + code + "', '" + taxon + "', '" + status + "', " + q(country) + ", " + q(adm1)
            + ", " + q(island) + ", " + q(museum) + ", " + q(bioregion) + ", 1, 'formicidae', '" + SUBFAMILY + "', 'pheidole', '"
            + taxon.substring(taxon.indexOf(' ') + 1) + "', " + shots.length + ")");
        for (String shot : shots) {
            exec("insert into image values (" + (nextImageId++) + ", '" + code + "', '" + shot + "', '2020-01-01 10:00:00')");
        }
    }

    private static String q(String v) { return v == null ? "null" : "'" + v + "'"; }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            // The other database tests (CountCheckTest) use the same database without these two tables.
            exec("drop table if exists image");
            exec("drop table if exists place_image_count");
            connection.close();
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private int[] placeRow(String table, String column, String key) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("select image_count, imaged_specimen_count from " + table + " where " + column + " = '" + key + "'")) {
            assertTrue(r.next());
            return new int[] {r.getInt(1), r.getInt(2)};
        }
    }

    public void testMadagascarImagesByViewAndImagedSpecimens() throws Exception {
        PlaceImageCountDb db = new PlaceImageCountDb(connection);
        String line = db.updateAll();
        assertTrue(line, line.startsWith("Place image counts:"));

        PlaceImageCountDb.PlaceImages p = db.read(PlaceImageCountDb.GEOLOCALE, "7");
        assertNotNull(p);
        // casent1 (h p d l), casent2 (l), casent3 (h f ''); not casent5 (synonym status) nor casent6 (not on the list).
        assertEquals(8, p.allImages);
        assertEquals(6, p.antImages);
        assertEquals(2, p.labelImages());
        assertEquals(2, (int) p.shotImages.get("h"));
        assertEquals(1, (int) p.shotImages.get("f"));
        assertEquals(1, (int) p.shotImages.get(""));
        assertEquals("casent1 and casent3", 2, p.imagedSpecimens);
        assertEquals("casent2", 1, p.labelOnlySpecimens);
        assertEquals(1, p.labelOnlyImages);
        assertEquals(3, p.specimensWithImages);
        assertEquals("6 of the ant (2 head, 1 profile, 1 dorsal, 1 field image, 1 other) and 2 of labels", PlaceImageCountDb.breakdown(p));
        assertEquals(": 2 head, 1 profile, 1 dorsal, 1 field image, and 1 other", PlaceImageCountDb.antViews(p));

        // The numbers on the page: the stale 3,435 and 9,999 are replaced.
        assertEquals(8, placeRow("geolocale", "id", "7")[0]);
        assertEquals(2, placeRow("geolocale", "id", "7")[1]);
        assertEquals("Comoros: casent4", 1, placeRow("geolocale", "id", "8")[0]);
        assertEquals("Antananarivo: casent1", 4, placeRow("geolocale", "id", "9")[0]);
        assertEquals(1, placeRow("geolocale", "id", "9")[1]);
        // Africa (since 2026-10-20): the specimens of its countries (Madagascar, Comoros), each counted by
        // its country's rule: casent1 to casent4 (more in RegionCountDbTest).
        assertEquals(9, placeRow("geolocale", "id", "1")[0]);
        assertEquals(3, placeRow("geolocale", "id", "1")[1]);
        // Museum and bioregion (Brian Fisher, 2026-10-28, as their Specimens): their specimens of a counted status filed
        // under a name of their list (before: all their specimens, any status: CASC 9 images, 3 imaged; Malagasy 11).
        assertEquals("casent1, 2, 3 (not casent5, status synonym)", 8, placeRow("museum", "code", "CASC")[0]);
        assertEquals("casent1, 3", 2, placeRow("museum", "code", "CASC")[1]);
        assertEquals("casent1 to casent4 and casent6", 10, placeRow("bioregion", "name", "Malagasy")[0]);
        assertEquals("casent1, 3, 4, 6", 4, placeRow("bioregion", "name", "Malagasy")[1]);
        // Project: specimens of the names on its list, anywhere, any status: casent1 to casent5.
        assertEquals(10, placeRow("project", "project_name", "madants")[0]);
        assertEquals(4, placeRow("project", "project_name", "madants")[1]);
        for (String[] place : new String[][] {{"geolocale", "id", "7"}, {"museum", "code", "CASC"}, {"bioregion", "name", "Malagasy"}}) {
            int[] row = placeRow(place[0], place[1], place[2]);
            assertTrue("Images >= Imaged specimens", row[0] >= row[1]);
        }
    }

    public void testOnePlaceAgainAndNoImages() throws Exception {
        PlaceImageCountDb db = new PlaceImageCountDb(connection);
        db.updateAll();
        exec("insert into image values (100, 'casent4', 'p', now())");
        exec("update geolocale set image_count = 77 where id = 7");
        PlaceImageCountDb.PlaceImages comoros = db.updatePlace(PlaceImageCountDb.GEOLOCALE, "8");
        assertEquals(2, comoros.allImages);
        assertEquals(2, placeRow("geolocale", "id", "8")[0]);
        assertEquals("other places are left alone", 77, placeRow("geolocale", "id", "7")[0]);
        exec("delete from image where image_of_id = 'casent4'");
        PlaceImageCountDb.PlaceImages none = db.updatePlace(PlaceImageCountDb.GEOLOCALE, "8");
        assertEquals(0, none.allImages);
        assertEquals(0, placeRow("geolocale", "id", "8")[0]);
        assertNull("no rows stored for a place without images", db.read(PlaceImageCountDb.GEOLOCALE, "8"));
    }

    /** The "N Images" of a taxon on a place's list count the images of that place's specimens only. */
    public void testTaxonImageCountsOfAPlaceAreOfItsSpecimens() throws Exception {
        Geolocale madagascar = new Geolocale();
        madagascar.setId(7);
        madagascar.setName("Madagascar");
        madagascar.setGeorank("country");
        madagascar.setIsIsland(true);
        String query = madagascar.getTaxonImageCountQuery();
        assertTrue(query, query.contains("s.island_country = 'Madagascar'"));
        assertTrue(query, query.contains("order by"));
        int spB = -1;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(query)) {
            while (r.next()) if (SP_B.equals(r.getString("taxonName"))) spB = r.getInt("imageSum");
        }
        assertEquals("casent3 only (not casent4 of Comoros, not the synonym-status casent5)", 3, spB);

        Geolocale cote = new Geolocale();
        cote.setId(10);
        cote.setName("Cote d'Ivoire");
        cote.setGeorank("country");
        assertTrue(cote.getSpecimenPlaceCriteria(), cote.getSpecimenPlaceCriteria().contains("'Cote d''Ivoire'"));
    }

    public void testCountCheckFindsStalePlaceImages() throws Exception {
        new PlaceImageCountDb(connection).updateAll();
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.GEOLOCALE, "7", "Madagascar", "island_country", "Madagascar"));
        places.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        places.add(new CountCheck.Place(CountCheck.BIOREGION, "Malagasy", "Malagasy bioregion", "bioregion", "Malagasy"));
        places.add(new CountCheck.Place(CountCheck.PROJECT, "madants", "project madants", null, null));
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        assertTrue(r.placeImagesComputed);
        assertEquals(placeImageMismatches(r).toString(), 0, placeImageMismatches(r).size());

        // A stale total, and Images smaller than Imaged specimens: both real.
        exec("update geolocale set image_count = 1, imaged_specimen_count = 5 where id = 7");
        r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        List<CountCheck.Mismatch> m = placeImageMismatches(r);
        assertEquals(m.toString(), 3, m.size());
        for (CountCheck.Mismatch x : m) assertTrue(x.toString(), x.isReal());

        // An image added after the last nightly run: its difference is pending, the smaller-than one stays real.
        exec("insert into image values (200, 'casent1', 'v', now())");
        r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        int real = 0;
        for (CountCheck.Mismatch x : placeImageMismatches(r)) if (x.isReal()) ++real;
        assertEquals(placeImageMismatches(r).toString(), 1, real);
    }

    /**
     * Brian Fisher, 2026-10-28: images follow the Specimens rule. A specimen filed under a synonym (Carebara polita, a
     * synonym of C. diversa) counts under the valid name on museum, bioregion and curated project lists: the place's
     * Images and Imaged specimens, and the list row's Images column; the count check agrees.
     */
    public void testImagesOfASynonymCountUnderTheValidName() throws Exception {
        String old = "myrmicinaepheidole oldname";
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, current_valid_name) values ('" + old
            + "', 'species', 'synonym', '" + GENUS + "', '" + SP_B + "')");
        specimen("casent7", old, "valid", "Madagascar", null, "Madagascar", "CASC", "Malagasy", "h", "p");
        PlaceImageCountDb db = new PlaceImageCountDb(connection);
        db.updateAll();
        assertEquals("CASC: 8 + casent7's 2", 10, placeRow("museum", "code", "CASC")[0]);
        assertEquals(3, placeRow("museum", "code", "CASC")[1]);
        assertEquals("Malagasy: 10 + 2", 12, placeRow("bioregion", "name", "Malagasy")[0]);
        assertEquals("madants: 10 + 2", 12, placeRow("project", "project_name", "madants")[0]);
        assertEquals("Madagascar: the synonym is not on its list (geolocale lists by the very name)", 8, placeRow("geolocale", "id", "7")[0]);

        // the Images column of the bioregion's list: SP_B gets casent3 (3), casent4 (1) and casent7 (2)
        org.calacademy.antweb.geolocale.Bioregion malagasy = new org.calacademy.antweb.geolocale.Bioregion();
        malagasy.setName("Malagasy");
        int spB = 0;
        boolean oldRow = false;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(malagasy.getTaxonImageCountQuery())) {
            while (r.next()) {
                if (SP_B.equals(r.getString("taxonName"))) spB += r.getInt("imageSum");   // one row per specimen name columns
                if (old.equals(r.getString("taxonName"))) oldRow = true;
            }
        }
        assertEquals("casent3 (H, f, empty), casent4 (h), casent7 (h, p); not casent5 (status synonym)", 6, spB);
        assertFalse("never under the old name", oldRow);

        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        places.add(new CountCheck.Place(CountCheck.BIOREGION, "Malagasy", "Malagasy bioregion", "bioregion", "Malagasy"));
        places.add(new CountCheck.Place(CountCheck.PROJECT, "madants", "project madants", null, null));
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        assertEquals(placeImageMismatches(r).toString(), 0, placeImageMismatches(r).size());
    }

    public void testCountCheckWaitsForTheFirstRun() throws Exception {
        // Deployed, the table created at startup, no nightly run yet: no rows.
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.GEOLOCALE, "7", "Madagascar", "island_country", "Madagascar"));
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        assertFalse(r.placeImagesComputed);
        assertEquals(0, placeImageMismatches(r).size());
        assertTrue(r.notes.toString(), r.notes.toString().contains("not been computed yet"));
    }

    private static List<CountCheck.Mismatch> placeImageMismatches(CountCheck.Result r) {
        List<CountCheck.Mismatch> list = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) if ("place images".equals(m.check)) list.add(m);
        return list;
    }
}

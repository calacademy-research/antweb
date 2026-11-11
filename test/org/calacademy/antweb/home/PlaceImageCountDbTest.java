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
 * the place's specimens; red-flagged specimens' images are not counted (2026-11-09); the count check (CountCheck
 * "place images") finds stale numbers.
 *
 * Since 2026-11-11 the daily update counts all places from a working set of the specimens that have images (session
 * tables) instead of the specimen table. The last tests here are the proof that no number changed: the new statements
 * are the old ones with another table name; on a fixture with every rule (moreRules) the old statements
 * (countReference) and the new give the same rows, and the tables written are identical row by row and column by
 * column; a run whose working set cannot be made uses the old statements; nothing is committed inside the caller's
 * transaction; the comparison job (compare) finds a difference when there is one and writes nothing.
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
            + " family varchar(64) default 'formicidae', subfamily varchar(64), genus varchar(64), species varchar(64), image_count int default 0,"
            + " flag varchar(10))");   // red flag (2026-11-09: images of red-flagged specimens are not counted)
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
        // 2026-11-10: the geolocale part says how long each of its sources took (production: 639 s, which one?)
        // 2026-11-11: the line names every part with its seconds: the working set of the imaged specimens (all six
        // specimens of the fixture have an image), then for each kind its counting and its writing.
        assertTrue(line, line.matches("Place image counts: imaged specimens 6 in [0-9.]+ s \\(read [0-9.]+ s, stored [0-9.]+ s, checked [0-9.]+ s,"
            + " copies [0-9.]+ s, statements prepared [0-9.]+ s\\); geolocale \\d+ places with images in [0-9.]+ s \\(islands [0-9.]+ s, countries [0-9.]+ s,"
            + " adm1s [0-9.]+ s, regions and subregions [0-9.]+ s, written [0-9.]+ s\\);"
            + " bioregion \\d+ places with images in [0-9.]+ s \\(counted [0-9.]+ s, written [0-9.]+ s\\);"
            + " museum \\d+ places with images in [0-9.]+ s \\(counted [0-9.]+ s, written [0-9.]+ s\\);"
            + " project \\d+ places with images in [0-9.]+ s \\(counted [0-9.]+ s, written [0-9.]+ s\\);"));
        assertFalse(line, line.contains(PlaceImageCountDb.WORKING_SET_NOT_USED));

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

    /**
     * Decision 3 (Brian Fisher, 2026-10-28) on the AntWeb-wide list (allantwebants, the Images cell of every list): a
     * specimen filed under a synonym counts under the valid name. allantwebants has rows of the counted statuses only,
     * so before, those images were counted nowhere and a curated project's row could exceed it (staging run
     * 40795139086: floridakeysants Camponotus inaequalis 32, allantwebants 29). Each specimen once; the genus row adds
     * them up once (the taxon tree).
     */
    public void testAllAntwebImagesCountASynonymUnderTheValidName() throws Exception {
        String old = "myrmicinaepheidole oldname";
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, current_valid_name) values ('" + old
            + "', 'species', 'synonym', '" + GENUS + "', '" + SP_B + "')");
        specimen("casent7", old, "valid", "Madagascar", null, "Madagascar", "CASC", "Malagasy", "h", "p");
        for (String t : new String[] {SUBFAMILY, GENUS, SP_A, SP_B, SP_C}) {   // regenerateAllAntweb: counted statuses only
            exec("insert into proj_taxon (project_name, taxon_name) values ('allantwebants', '" + t + "')");
        }
        org.calacademy.antweb.Project all = new org.calacademy.antweb.Project(org.calacademy.antweb.Project.ALLANTWEBANTS);
        java.util.Map<String, Integer> sums = new java.util.HashMap<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(all.getTaxonImageCountQuery())) {
            while (r.next()) sums.merge(r.getString("taxonName"), r.getInt("imageSum"), Integer::sum);
        }
        assertEquals("casent3 (3), casent4 (1), casent5 (1, the very name), casent7 (2, the synonym)", Integer.valueOf(7), sums.get(SP_B));
        assertNull("no row for the synonym", sums.get(old));

        new org.calacademy.antweb.home.ProjTaxonCountDb(connection).allAntwebImageCount();
        java.util.Map<String, Integer> rows = new java.util.HashMap<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(
                "select taxon_name, image_count from proj_taxon where project_name = 'allantwebants'")) {
            while (r.next()) rows.put(r.getString(1), r.getInt(2));
        }
        assertEquals(Integer.valueOf(7), rows.get(SP_B));
        assertEquals("a1 3 + megacephala 7 + other 1, casent7 once", Integer.valueOf(11), rows.get(GENUS));
        assertEquals(Integer.valueOf(11), rows.get(SUBFAMILY));
        assertNull(rows.get(old));

        // the count check recounts the allantwebants rows by the same rule
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.PROJECT, "allantwebants", "project allantwebants", null, null));
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        List<CountCheck.Mismatch> images = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) if ("images".equals(m.check)) images.add(m);
        assertEquals(images.toString(), 0, images.size());

        // worldants keeps the very name (it lists every name as AntCat has it)
        org.calacademy.antweb.Project world = new org.calacademy.antweb.Project("worldants");
        assertFalse(world.getTaxonImageCountQuery().contains("current_valid_name"));
    }

    /**
     * Brian Fisher, 2026-11-09: images of red-flagged specimens are not counted anywhere. casent3 (H, f, empty) red-flagged:
     * every place it was counted in loses its 3 images and 1 imaged specimen; the list rows' Images too; a green flag
     * changes nothing; the count check recounts by the same rule and agrees.
     */
    public void testRedFlaggedSpecimensImagesAreNotCounted() throws Exception {
        exec("update specimen set flag = 'green' where code = 'casent1'");   // a flag that is not red: still counted
        exec("update specimen set flag = 'red' where code = 'casent3'");
        PlaceImageCountDb db = new PlaceImageCountDb(connection);
        db.updateAll();
        PlaceImageCountDb.PlaceImages p = db.read(PlaceImageCountDb.GEOLOCALE, "7");
        assertEquals("8 without casent3's 3", 5, p.allImages);
        assertEquals("6 without casent3's 3", 3, p.antImages);
        assertEquals(2, p.labelImages());
        assertEquals("casent1 only", 1, p.imagedSpecimens);
        assertEquals("casent2", 1, p.labelOnlySpecimens);
        assertNull("casent3's field image is not counted", p.shotImages.get("f"));
        assertEquals(5, placeRow("geolocale", "id", "7")[0]);
        assertEquals(1, placeRow("geolocale", "id", "7")[1]);
        assertEquals("Africa: 9 - 3", 6, placeRow("geolocale", "id", "1")[0]);
        assertEquals(2, placeRow("geolocale", "id", "1")[1]);
        assertEquals("CASC: 8 - 3", 5, placeRow("museum", "code", "CASC")[0]);
        assertEquals(1, placeRow("museum", "code", "CASC")[1]);
        assertEquals("Malagasy: 10 - 3", 7, placeRow("bioregion", "name", "Malagasy")[0]);
        assertEquals(3, placeRow("bioregion", "name", "Malagasy")[1]);
        assertEquals("madants: 10 - 3", 7, placeRow("project", "project_name", "madants")[0]);
        assertEquals(3, placeRow("project", "project_name", "madants")[1]);

        // the Images column of the Madagascar list: megacephala had casent3's 3 images only
        Geolocale madagascar = new Geolocale();
        madagascar.setId(7);
        madagascar.setName("Madagascar");
        madagascar.setGeorank("country");
        madagascar.setIsIsland(true);
        int spB = -1;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(madagascar.getTaxonImageCountQuery())) {
            while (r.next()) if (SP_B.equals(r.getString("taxonName"))) spB = r.getInt("imageSum");
        }
        assertEquals("the row is kept, with no image", 0, spB);

        List<CountCheck.Place> places = new ArrayList<>();
        places.add(new CountCheck.Place(CountCheck.GEOLOCALE, "7", "Madagascar", "island_country", "Madagascar"));
        places.add(new CountCheck.Place(CountCheck.MUSEUM, "CASC", "museum CASC", "museum", "CASC"));
        places.add(new CountCheck.Place(CountCheck.BIOREGION, "Malagasy", "Malagasy bioregion", "bioregion", "Malagasy"));
        places.add(new CountCheck.Place(CountCheck.PROJECT, "madants", "project madants", null, null));
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2021-01-01 08:00:00"));
        assertEquals(placeImageMismatches(r).toString(), 0, placeImageMismatches(r).size());

        // unflagged again (curated): counted again by the next count
        exec("update specimen set flag = null where code = 'casent3'");
        db.updateAll();
        assertEquals(8, placeRow("geolocale", "id", "7")[0]);
        assertEquals(2, placeRow("geolocale", "id", "7")[1]);
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

    // ------------------------------------------------------------------ 2026-11-11: counting from the imaged specimens

    /**
     * More places and specimens, so that every rule of the sources decides at least one row: two states of the same
     * name in two countries, a country that is not an island, an island that is one, a second subregion, a region
     * without images, a name that is not on a place's list, a status that is not counted, a red-flagged specimen with
     * images, a specimen with a label photo only, specimens without any image, a specimen counted by a country and by an
     * island of the same subregion, a specimen identified to genus only, a specimen that is not an ant, a specimen filed
     * under a synonym, a second museum, bioregion and project, and the AntWeb-wide project.
     */
    private void moreRules() throws SQLException {
        String old = "'2020-01-01 10:00:00'";
        exec("insert into geolocale values (10, 'Kenya', 'country', 'Eastern Africa', 1, 0, 0, 0)");
        exec("insert into geolocale values (11, 'Central', 'adm1', 'Kenya', 1, 0, 0, 0)");
        exec("insert into geolocale values (12, 'Uganda', 'country', 'Eastern Africa', 1, 0, 0, 0)");
        exec("insert into geolocale values (13, 'Central', 'adm1', 'Uganda', 1, 0, 0, 0)");       // the same name, another country
        exec("insert into geolocale values (3, 'Western Africa', 'subregion', 'Africa', 1, 0, 0, 0)");
        exec("insert into geolocale values (14, 'Ghana', 'country', 'Western Africa', 1, 0, 0, 0)");
        exec("insert into geolocale values (4, 'Asia', 'region', null, 1, 0, 0, 0)");               // no images
        exec("insert into geolocale values (5, 'Southern Asia', 'subregion', 'Asia', 1, 0, 0, 0)");
        exec("insert into geolocale values (15, 'India', 'country', 'Southern Asia', 1, 0, 0, 0)");
        exec("insert into geolocale values (16, 'Reunion', 'country', 'Eastern Africa', 1, 1, 0, 0)");   // a second island
        exec("insert into geolocale values (17, 'Somalia', 'country', 'Eastern Africa', 0, 0, 0, 0)");   // not valid: not in its subregion

        String synonym = "myrmicinaepheidole oldname";
        String bee = "apinaeapis mellifera";
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, current_valid_name) values ('" + synonym
            + "', 'species', 'synonym', '" + GENUS + "', '" + SP_B + "')");
        exec("insert into taxon (taxon_name, taxarank, status, parent_taxon_name, family) values ('" + bee + "', 'species', 'valid', 'apinaeapis', 'apidae')");

        String[][] lists = {
            {"10", SP_A}, {"10", SP_B}, {"10", GENUS}, {"10", bee}, {"11", SP_A}, {"11", SP_B}, {"12", SP_B}, {"13", SP_B}, {"13", SP_A},
            {"14", SP_B}, {"15", SP_B}, {"16", SP_B}, {"17", SP_B},
            {"2", SP_B}, {"2", GENUS},          // the subregion's list has no SP_A: its SP_A specimens are not its images
            {"3", SP_B}, {"4", SP_B}, {"5", SP_B}, {"8", SP_A}, {"1", bee}};
        for (String[] l : lists) exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + l[0] + ", '" + l[1] + "', " + old + ")");

        exec("insert into museum values ('MCZC', 0, 0)");
        exec("insert into museum values ('EMPTY', 5, 5)");   // no list: its stale numbers go to 0
        for (String t : new String[] {SP_B, bee}) exec("insert into museum_taxon (code, taxon_name, created) values ('MCZC', '" + t + "', " + old + ")");
        exec("insert into museum_taxon (code, taxon_name, created) values ('CASC', '" + bee + "', " + old + ")");
        exec("insert into bioregion values ('Afrotropical', 0, 0)");
        for (String t : new String[] {SP_A, SP_B}) exec("insert into bioregion_taxon (bioregion_name, taxon_name, created) values ('Afrotropical', '" + t + "', " + old + ")");
        exec("insert into project values ('kenyaants', 0, 0)");
        exec("insert into project values ('allantwebants', 0, 0)");
        exec("insert into proj_taxon (project_name, taxon_name, created) values ('kenyaants', '" + SP_A + "', " + old + ")");
        for (String t : new String[] {SUBFAMILY, GENUS, SP_A, SP_B, SP_C}) {
            exec("insert into proj_taxon (project_name, taxon_name, created) values ('allantwebants', '" + t + "', " + old + ")");
        }

        specimen("ken1", SP_A, "morphotaxon", "Kenya", "Central", null, "CASC", "Afrotropical", "h", "p");
        specimen("ken2", SP_B, "valid", "Kenya", "Central", null, "MCZC", "Afrotropical", "l");                    // label only
        specimen("ken3", SP_B, "valid", "Kenya", "Central", null, "MCZC", "Afrotropical");                         // no image
        specimen("ken4", GENUS, "valid", "Kenya", "Central", null, "CASC", "Afrotropical", "h");                   // identified to genus only
        specimen("ken5", bee, "valid", "Kenya", "Central", null, "CASC", "Afrotropical", "h", "d");                // not an ant
        specimen("ken6", SP_B, "valid", "Kenya", null, null, "MCZC", "Afrotropical", " P ", "L");                  // no adm1; spaces, upper case
        specimen("uga1", SP_B, "valid", "Uganda", "Central", null, "MCZC", "Afrotropical", "h");
        specimen("uga2", SP_C, "valid", "Uganda", "Central", null, "MCZC", "Afrotropical", "h");                   // not on Uganda's list
        specimen("uga3", SP_B, "valid", "Uganda", "Central", null, "MCZC", "Afrotropical", "h", "p");              // red-flagged below
        specimen("uga4", SP_A, "morphotaxon", "Uganda", "Central", null, "CASC", "Afrotropical", "d");             // on Central's list, not on Uganda's
        specimen("gha1", SP_B, "valid", "Ghana", null, null, "CASC", "Afrotropical", "d", "l");
        specimen("gha2", SP_B, "unidentifiable", "Ghana", null, null, "CASC", "Afrotropical", "h");                // a counted status
        specimen("gha3", SP_B, "excluded", "Ghana", null, null, "CASC", "Afrotropical", "h");                      // not a counted status
        specimen("reu1", SP_B, "valid", "Reunion", null, "Reunion", "CASC", "Malagasy", "h", "v");
        specimen("mix1", SP_B, "valid", "Comoros", null, "Madagascar", "CASC", "Malagasy", "p");                   // a country and an island of one subregion
        specimen("som1", SP_B, "valid", "Somalia", null, null, "CASC", "Afrotropical", "h");                       // a country that is not valid
        specimen("syn1", synonym, "valid", "Kenya", "Central", null, "MCZC", "Afrotropical", "h", "l");            // filed under a synonym of SP_B
        specimen("nomus", SP_B, "valid", "Kenya", null, null, null, null, "f");                                    // no museum, no bioregion
        for (int i = 0; i < 12; i++) specimen("plain" + i, i % 2 == 0 ? SP_A : SP_B, "valid", "Madagascar", "Antananarivo", "Madagascar", "CASC", "Malagasy");
        exec("update specimen set flag = 'red' where code = 'uga3'");
        exec("update specimen set flag = 'green' where code = 'gha1'");
    }

    /** Every row the step writes, without the time it was written: place_image_count and the numbers of the place tables. */
    private String written() throws SQLException {
        StringBuilder b = new StringBuilder();
        String[] queries = {
            "select place_type, place_key, shot_type, image_count, specimen_count from place_image_count order by place_type, place_key, cast(shot_type as binary)",
            "select 'geolocale', id, image_count, imaged_specimen_count from geolocale order by id",
            "select 'project', project_name, image_count, imaged_specimen_count from project order by project_name",
            "select 'museum', code, image_count, imaged_specimen_count from museum order by code",
            "select 'bioregion', name, image_count, imaged_specimen_count from bioregion order by name"};
        for (String query : queries) {
            try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(query)) {
                int n = r.getMetaData().getColumnCount();
                while (r.next()) {
                    for (int i = 1; i <= n; i++) b.append(i > 1 ? "|" : "").append(r.getString(i));
                    b.append("\n");
                }
            }
        }
        return b.toString();
    }

    private static String rowsOf(java.util.Map<String, PlaceImageCountDb.PlaceImages> places) {
        StringBuilder b = new StringBuilder();
        for (String key : new java.util.TreeSet<>(places.keySet())) {
            b.append(key).append(":");
            for (java.util.Map.Entry<String, long[]> e : PlaceImageCountDb.rows(places.get(key)).entrySet()) {
                b.append(" [").append(e.getKey()).append("] ").append(e.getValue()[0]).append("/").append(e.getValue()[1]);
            }
            b.append("\n");
        }
        return b.toString();
    }

    private boolean tableThere(String table) {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("select count(*) from " + table)) {
            return r.next();
        } catch (SQLException e) {
            return false;
        }
    }

    /** A run whose working set cannot be made (for example a database user that may not create temporary tables). */
    private static class NoWorkingSet extends PlaceImageCountDb {
        NoWorkingSet(Connection connection) { super(connection); }
        @Override protected int openWorkingSet() throws SQLException {
            throw new SQLException("CREATE TEMPORARY TABLES command denied to user 'antweb'@'%' for table 'x'");
        }
    }

    /** A wrong working set (one imaged specimen missing), to see that the comparison finds it. */
    private static class WrongWorkingSet extends PlaceImageCountDb {
        private final Connection c;
        WrongWorkingSet(Connection connection) { super(connection); c = connection; }
        @Override protected int openWorkingSet() throws SQLException {
            int n = super.openWorkingSet();
            try (Statement s = c.createStatement()) {
                for (String table : PlaceImageCountDb.WORK_TABLES) s.executeUpdate("delete from " + table + " where code = 'casent1'");
            }
            return n - 1;
        }
    }

    /**
     * The new statements are the old ones with the working tables in the place of the specimen table, and nothing else:
     * no source reads the specimen table any more, none names a working table twice (MySQL cannot read a temporary
     * table twice in a statement), and every specimen column a source reads (alias s) is a column the working tables have.
     */
    public void testNewStatementsAreTheOldOnesOverTheWorkingSet() {
        int sources = 0;
        java.util.regex.Pattern column = java.util.regex.Pattern.compile("\\bs\\.(\\w+)");
        for (String type : PlaceImageCountDb.TYPES) {
            List<PlaceImageCountDb.Source> oldSources = PlaceImageCountDb.sources(type, false);
            List<PlaceImageCountDb.Source> newSources = PlaceImageCountDb.sources(type, false, PlaceImageCountDb.WORK_TABLES);
            assertEquals(oldSources.size(), newSources.size());
            for (int i = 0; i < oldSources.size(); i++) {
                String oldSql = oldSources.get(i).sql;
                String newSql = newSources.get(i).sql;
                assertEquals(oldSources.get(i).name, newSources.get(i).name);
                assertTrue(oldSql, oldSql.contains(" join specimen s on "));
                String back = newSql;
                for (String table : PlaceImageCountDb.WORK_TABLES) {
                    assertTrue(newSources.get(i).name + ": " + table + " more than once", newSql.split(table, -1).length <= 2);
                    back = back.replace(table, "specimen");
                }
                assertEquals(newSources.get(i).name, oldSql, back);
                assertFalse(newSql, newSql.matches(".*\\b(from|join) specimen\\b.*"));
                assertTrue(newSql, newSql.contains(PlaceImageCountDb.WORK_TABLES[0]));
                java.util.regex.Matcher m = column.matcher(oldSql);
                while (m.find()) {
                    assertTrue("specimen column " + m.group(1) + " of source " + oldSources.get(i).name + " is not in the working tables",
                        PlaceImageCountDb.SPECIMEN_COLUMNS.contains(m.group(1)));
                }
                ++sources;
            }
        }
        assertEquals("islands, countries, adm1s, regions and subregions, bioregion, museum, project", 7, sources);
        // the regions source reads the specimens three times: once from each working table
        String regions = PlaceImageCountDb.sources(PlaceImageCountDb.GEOLOCALE, false, PlaceImageCountDb.WORK_TABLES).get(3).sql;
        for (String table : PlaceImageCountDb.WORK_TABLES) assertTrue(regions, regions.contains(" join " + table + " s on "));
        // the one-place statements (Recalculate button, count check) are the old ones
        assertTrue(PlaceImageCountDb.listSource(PlaceImageCountDb.MUSEUM, true).contains(" join specimen s on "));
        // the working set: a plain SELECT (no INSERT ... SELECT from specimen, which would lock its rows), temporary tables only
        assertTrue(PlaceImageCountDb.IMAGED_SPECIMENS.startsWith("select s.code, s.taxon_name, s.status, s.country, s.adm1, s.island_country,"
            + " s.museum, s.bioregion, s.flag from specimen s where exists (select 1 from image i where i.image_of_id = s.code)"));
        for (String table : PlaceImageCountDb.WORK_TABLES) {
            String create = PlaceImageCountDb.createWorkTable(table);
            assertTrue(create, create.startsWith("create temporary table " + table + " (primary key (code)"));
            assertTrue(create, create.endsWith(" from specimen s where 1 = 0"));
            assertTrue(table, table.startsWith("tmp_"));
        }
    }

    /**
     * The proof on a fixture with every rule: the old statements and the new give the same rows for every place of
     * every kind, and the rows are what the rules say (so the fixture does decide them).
     */
    public void testOldAndNewStatementsGiveTheSameRowsForEveryRule() throws Exception {
        moreRules();
        class Open extends PlaceImageCountDb {
            Open(Connection c) { super(c); }
            int open() throws SQLException { return openWorkingSet(); }
            void close() { closeWorkingSet(); }
        }
        Open db = new Open(connection);
        int imaged = db.open();
        try {
            assertEquals("the 6 of the first fixture and 17 of moreRules (not ken3, not the 12 plain ones)", 23, imaged);
            int[] placesOfKind = {0, 0, 0, 0};
            for (int i = 0; i < PlaceImageCountDb.TYPES.length; i++) {
                String type = PlaceImageCountDb.TYPES[i];
                java.util.Map<String, PlaceImageCountDb.PlaceImages> oldPlaces = db.countReference(type);
                java.util.Map<String, PlaceImageCountDb.PlaceImages> newPlaces = db.countFromWorkingSet(type);
                assertEquals(type, rowsOf(oldPlaces), rowsOf(newPlaces));
                placesOfKind[i] = newPlaces.size();
                if (PlaceImageCountDb.GEOLOCALE.equals(type)) {
                    // two states named Central: each by its country. Kenya's: ken1 (h, p), ken2 (l); not ken4 (genus), ken5
                    // (a bee), syn1 (a synonym is not on a geolocale list). Uganda's: uga1 (h), uga4 (d); not uga2 (not on
                    // the list) nor uga3 (red flag).
                    assertEquals(3, newPlaces.get("11").allImages);
                    assertEquals(1, newPlaces.get("11").imagedSpecimens);
                    assertEquals(1, newPlaces.get("11").labelOnlySpecimens);
                    assertEquals(2, newPlaces.get("13").allImages);
                    assertEquals(2, newPlaces.get("13").imagedSpecimens);
                    // Uganda itself: uga1 only (SP_A is on Central's list, not on Uganda's)
                    assertEquals(1, newPlaces.get("12").allImages);
                    // Kenya: ken1 (2), ken2 (1), ken6 (2), nomus (1)
                    assertEquals(6, newPlaces.get("10").allImages);
                    assertEquals("ken1's p and ken6's ' P '", 2, (int) newPlaces.get("10").shotImages.get("p"));
                    assertEquals("ken2's l and ken6's 'L'", 2, (int) newPlaces.get("10").shotImages.get("l"));
                    // Comoros: casent4 and mix1; Madagascar (island): its 8 and mix1; Reunion (island): reu1
                    assertEquals(2, newPlaces.get("8").allImages);
                    assertEquals(9, newPlaces.get("7").allImages);
                    assertEquals(2, newPlaces.get("16").allImages);
                    // Ghana: gha1 (2, green flag), gha2 (1, unidentifiable); not gha3 (excluded)
                    assertEquals(3, newPlaces.get("14").allImages);
                    // Eastern Africa (its list: SP_B only): casent3 (3), casent4 (1), mix1 (1, once), reu1 (2), ken2 (1), ken6 (2),
                    // nomus (1), uga1 (1); not Somalia's som1 (the country is not valid)
                    assertEquals(12, newPlaces.get("2").allImages);
                    assertEquals("mix1 once, though Comoros and Madagascar both count it", 8, newPlaces.get("2").specimensWithImages);
                    assertEquals("Western Africa: Ghana's", 3, newPlaces.get("3").allImages);
                    assertNull("Asia has no images", newPlaces.get("4"));
                    assertNull("an adm1 or country without counted images has no rows", newPlaces.get("15"));
                    assertEquals("Somalia's own page counts som1", 1, newPlaces.get("17").allImages);
                }
                if (PlaceImageCountDb.MUSEUM.equals(type)) {
                    // MCZC (list: SP_B, the bee): casent4 (1), ken2 (1), ken6 (2), uga1 (1), syn1 (2, under the valid name);
                    // not uga2 (SP_C), uga3 (red), ken3 (no image)
                    assertEquals(7, newPlaces.get("MCZC").allImages);
                    assertNull(newPlaces.get("EMPTY"));
                }
                if (PlaceImageCountDb.PROJECT.equals(type)) {
                    assertEquals("kenyaants (SP_A anywhere): casent1 (4), casent2 (1), ken1 (2), uga4 (1)", 8, newPlaces.get("kenyaants").allImages);
                    assertNotNull(newPlaces.get("allantwebants"));
                }
            }
            assertEquals("Madagascar, Comoros, Antananarivo, Kenya, 2 x Central, Uganda, Ghana, Reunion, Somalia, Africa, 2 subregions", 13, placesOfKind[0]);
            assertEquals("Malagasy, Afrotropical", 2, placesOfKind[1]);
            assertEquals("CASC, MCZC", 2, placesOfKind[2]);
            assertEquals("madants, kenyaants, allantwebants", 3, placesOfKind[3]);
        } finally {
            db.close();
        }
        assertFalse("the working tables are dropped", tableThere(PlaceImageCountDb.WORK_TABLES[0]));
    }

    /**
     * Every row and column the step writes is the same by the new statements and by the old ones (a run without the
     * working set uses the old statements and says so), in either order, and the working tables do not stay.
     */
    public void testWrittenTablesAreIdenticalByOldAndNewStatements() throws Exception {
        moreRules();
        String line = new PlaceImageCountDb(connection).updateAll();
        assertTrue(line, line.startsWith("Place image counts: imaged specimens 23 in "));
        String byNew = written();
        assertTrue(byNew, byNew.contains("geolocale|11|*all|3|2\n"));
        assertTrue(byNew, byNew.contains("museum|EMPTY|0|0\n"));
        for (String table : PlaceImageCountDb.WORK_TABLES) assertFalse(table + " is dropped", tableThere(table));

        // stale numbers everywhere, then the old statements
        exec("delete from place_image_count");
        exec("update geolocale set image_count = 99, imaged_specimen_count = 98");
        exec("update museum set image_count = 99, imaged_specimen_count = 98");
        exec("update bioregion set image_count = 99, imaged_specimen_count = 98");
        exec("update project set image_count = 99, imaged_specimen_count = 98");
        String oldLine = new NoWorkingSet(connection).updateAll();
        assertTrue(oldLine, oldLine.startsWith("Place image counts: " + PlaceImageCountDb.WORKING_SET_NOT_USED
            + " (CREATE TEMPORARY TABLES command denied to user 'antweb'@'%' for table 'x'), the old statements ran; geolocale 13 places"));
        String byOld = written();
        assertEquals(byOld, byNew);

        // and the new statements again over the old ones' rows; one kind alone (utilData.do?action=placeImageCounts&name=museum)
        exec("update museum set image_count = 99, imaged_specimen_count = 98");
        exec("delete from place_image_count where place_type = 'museum'");
        assertEquals(2, new PlaceImageCountDb(connection).updateType(PlaceImageCountDb.MUSEUM));
        assertEquals(byOld, written());
        exec("delete from place_image_count where place_type = 'museum'");
        assertEquals(2, new NoWorkingSet(connection).updateType(PlaceImageCountDb.MUSEUM));
        assertEquals(byOld, written());
        new PlaceImageCountDb(connection).updateAll();
        assertEquals(byOld, written());
        for (String table : PlaceImageCountDb.WORK_TABLES) assertFalse(table + " is dropped", tableThere(table));
    }

    /**
     * Set 4 runs the step inside its transaction: making, filling and dropping the working tables must not commit it
     * (a CREATE TABLE, ALTER TABLE or CREATE INDEX would). What the caller did before and what the step wrote are both
     * undone by the caller's rollback.
     */
    public void testNothingIsCommittedInsideTheCallersTransaction() throws Exception {
        moreRules();
        new PlaceImageCountDb(connection).updateAll();
        String before = written();
        connection.setAutoCommit(false);
        try {
            exec("update project set image_count = 4321 where project_name = 'madants'");   // the caller's own, not yet committed
            exec("insert into image values (900, 'ken3', 'h', now())");                    // ken3 becomes an imaged specimen
            String line = new PlaceImageCountDb(connection).updateAll();
            assertTrue("the step sees the caller's uncommitted image: " + line, line.startsWith("Place image counts: imaged specimens 24 in "));
            assertFalse(before.equals(written()));
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }
        assertEquals("the rollback undid the caller's change and the step's writes: nothing was committed in between", before, written());
        for (String table : PlaceImageCountDb.WORK_TABLES) assertFalse(table + " is dropped", tableThere(table));
    }

    /**
     * The comparison job (utilData.do?action=placeImageCountCompare): no difference between the old and the new
     * statements; stored numbers that are stale are listed; a wrong working set is found, with the places it changes;
     * a working set that cannot be made is said; and the job writes nothing.
     */
    public void testComparisonFindsDifferencesAndWritesNothing() throws Exception {
        moreRules();
        new PlaceImageCountDb(connection).updateAll();
        String before = written();

        PlaceImageCountDb.Comparison same = new PlaceImageCountDb(connection).compare();
        assertEquals(same.lines.toString(), 0, same.differing);
        assertEquals(same.lines.toString(), 0, same.storedDiffering);
        assertEquals(4, same.typesCompared);
        assertEquals(PlaceImageCountDb.COMPARE_LOG + " the old and the new statements give the same rows for every place of 4 kinds;"
            + " stored numbers that differ from a fresh count: 0 places", same.summary());
        assertEquals(same.summary(), same.lines.get(0));
        assertTrue(same.lines.toString(), same.lines.get(1).matches("imaged specimens 23 in .*"));
        assertTrue(same.lines.toString(), same.lines.get(2).matches("geolocale: 13 places with images by the old statements in [0-9.]+ s \\(islands .*\\),"
            + " 13 by the new in [0-9.]+ s \\(islands .*\\); places that differ: 0\\."));
        assertEquals(before, written());

        // an image added since the last run: the stored numbers of its places are stale, the two counts still agree
        exec("insert into image values (901, 'gha1', 'h', now())");
        PlaceImageCountDb.Comparison stale = new PlaceImageCountDb(connection).compare();
        assertEquals(stale.lines.toString(), 0, stale.differing);
        assertEquals("Ghana, Western Africa, Africa; Afrotropical; CASC; madants, allantwebants: " + stale.lines, 7, stale.storedDiffering);
        assertTrue(stale.lines.toString(), stale.lines.contains("geolocale 14: counted now *all 4/2, *ant 3/2, *label_only 0/0, d 1/1, h 2/2, l 1/1;"
            + " stored *all 3/2, *ant 2/2, *label_only 0/0, d 1/1, h 1/1, l 1/1; on the page 3 images, 2 imaged specimens."));
        exec("delete from image where id = 901");

        // a working set without casent1: every place that counts casent1 differs, each named
        PlaceImageCountDb.Comparison wrong = new WrongWorkingSet(connection).compare();
        assertEquals("Madagascar, Antananarivo, Africa; Malagasy; CASC; madants, kenyaants, allantwebants: " + wrong.lines, 8, wrong.differing);
        assertTrue(wrong.summary(), wrong.summary().startsWith(PlaceImageCountDb.COMPARE_LOG + " the old and the new statements DIFFER for 8 places"));
        assertTrue(wrong.lines.toString(), wrong.lines.contains("geolocale 9: old statements *all 4/1, *ant 3/1, *label_only 0/0, d 1/1, h 1/1, l 1/1, p 1/1;"
            + " new statements no rows."));

        PlaceImageCountDb.Comparison none = new NoWorkingSet(connection).compare();
        assertEquals(0, none.typesCompared);
        assertTrue(none.summary(), none.summary().startsWith(PlaceImageCountDb.COMPARE_LOG + " NOT compared, " + PlaceImageCountDb.WORKING_SET_NOT_USED));

        assertEquals("the comparison wrote nothing", before, written());
        for (String table : PlaceImageCountDb.WORK_TABLES) assertFalse(table + " is dropped", tableThere(table));

        // before the first run there is nothing stored to compare with: said, not counted as 0 differences
        exec("drop table place_image_count");
        PlaceImageCountDb.Comparison first = new PlaceImageCountDb(connection).compare();
        assertEquals(0, first.differing);
        assertFalse(first.storedCompared);
        assertTrue(first.summary(), first.summary().endsWith("for every place of 4 kinds; nothing is stored yet, so no stored number was compared"));
    }

    private static List<CountCheck.Mismatch> placeImageMismatches(CountCheck.Result r) {
        List<CountCheck.Mismatch> list = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) if ("place images".equals(m.check)) list.add(m);
        return list;
    }
}

package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.GeolocaleTaxonCountDb;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.home.PlaceTotals;
import org.calacademy.antweb.util.CountCheck;

/**
 * Region and subregion numbers (Brian Fisher, 2026-10-20): Specimens, Imaged specimens, Images, the
 * "N specimens" and "N Images" of each name on their list and the "Specimens per Subfamily" chart count
 * only the specimens collected in their countries: the specimens that the pages of their countries count
 * (collected there, of a countable status, identified to a name on that country's list), each once.
 * Before, they counted every specimen of the names on the list, anywhere in the world.
 *
 * Places: Africa (region) > Eastern Africa (subregion) > Madagascar (island), Comoros, Kenya, Tanzania,
 * Zanzibar (island, specimens with country Tanzania), and an invalid country; Americas > South America > Brazil.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like PlaceImageCountDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class RegionCountDbTest extends TestCase {

    static final String SUBFAMILY = "myrmicinae";
    static final String GENUS = "myrmicinaepheidole";
    static final String SP_A = "myrmicinaepheidole a1";            // morphospecies, Madagascar
    static final String SP_B = "myrmicinaepheidole megacephala";   // everywhere
    static final String SP_X = "myrmicinaepheidole brasil";        // collected in Brazil only; on the Eastern Africa list by a curator

    static final int AFRICA = 1, EASTERN_AFRICA = 2, MADAGASCAR = 7, COMOROS = 8, KENYA = 9, TANZANIA = 10, ZANZIBAR = 11,
                     OLD_NAME = 12, AMERICAS = 20, SOUTH_AMERICA = 21, BRAZIL = 22;

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"specimen", "image", "taxon", "geolocale", "geolocale_taxon", "proj_taxon", "upload", "place_image_count"}) {
            exec("drop table if exists " + t);
        }
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128),"
            + " family varchar(64), subfamily varchar(64))");
        // the genus column of the live table (the species counts read it: PlaceTotals.identifiedToGenus); invisible, so
        // the inserts by position stay as they are
        exec("alter table taxon add column genus varchar(64) invisible");
        exec("create table specimen (code varchar(128) primary key, taxon_name varchar(128), status varchar(40), country varchar(64),"
            + " adm1 varchar(64), island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int,"
            + " family varchar(64), subfamily varchar(64), genus varchar(64), species varchar(64), image_count int default 0)");
        exec("create table image (id int primary key, image_of_id varchar(128), shot_type varchar(10), upload_date datetime)");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128), is_valid tinyint default 1,"
            + " is_island tinyint default 0, specimen_count int default 0, image_count int default 0, imaged_specimen_count int default 0,"
            + " subfamily_count int default 0)");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (geolocale_id, taxon_name))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, image_count int default 0, created timestamp null,"
            + " primary key (project_name, taxon_name))");
        exec("create table upload (upload_id int primary key, created timestamp null)");
        exec("insert into upload values (1, '2020-01-01 10:00:00')");
        exec(PlaceImageCountDb.createTable(null, null));

        exec("insert into taxon values ('" + SUBFAMILY + "', 'subfamily', 'valid', 'formicidae', 'formicidae', '" + SUBFAMILY + "')");
        exec("insert into taxon values ('" + GENUS + "', 'genus', 'valid', '" + SUBFAMILY + "', 'formicidae', '" + SUBFAMILY + "')");
        for (String[] sp : new String[][] {{SP_A, "morphotaxon"}, {SP_B, "valid"}, {SP_X, "valid"}}) {
            exec("insert into taxon values ('" + sp[0] + "', 'species', '" + sp[1] + "', '" + GENUS + "', 'formicidae', '" + SUBFAMILY + "')");
        }

        geolocale(AFRICA, "Africa", "region", null, 1, 0);
        geolocale(EASTERN_AFRICA, "Eastern Africa", "subregion", "Africa", 1, 0);
        geolocale(MADAGASCAR, "Madagascar", "country", "Eastern Africa", 1, 1);
        geolocale(COMOROS, "Comoros", "country", "Eastern Africa", 1, 0);
        geolocale(KENYA, "Kenya", "country", "Eastern Africa", 1, 0);
        geolocale(TANZANIA, "Tanzania", "country", "Eastern Africa", 1, 0);
        geolocale(ZANZIBAR, "Zanzibar", "country", "Eastern Africa", 1, 1);
        geolocale(OLD_NAME, "Malagasy Republic", "country", "Eastern Africa", 0, 0);   // not valid: no page counts its specimens
        geolocale(AMERICAS, "Americas", "region", null, 1, 0);
        geolocale(SOUTH_AMERICA, "South America", "subregion", "Americas", 1, 0);
        geolocale(BRAZIL, "Brazil", "country", "South America", 1, 0);

        // The lists, as the nightly run builds them from the specimens (and a curator row).
        list(MADAGASCAR, SP_A, SP_B);
        list(COMOROS, SP_B);
        list(TANZANIA, SP_B);
        list(ZANZIBAR, SP_B);
        list(KENYA);                          // SP_B in Kenya is disputed: not on the Kenya list
        list(BRAZIL, SP_B, SP_X);
        list(EASTERN_AFRICA, SP_A, SP_B, SP_X);
        list(AFRICA, SP_A, SP_B, SP_X);
        list(SOUTH_AMERICA, SP_B, SP_X);
        list(AMERICAS, SP_B, SP_X);
        // Stale numbers, as the old rule left them (every specimen of the name, anywhere).
        exec("update geolocale_taxon set specimen_count = 7 where geolocale_id in (" + EASTERN_AFRICA + ", " + AFRICA + ") and taxon_name = '" + SP_B + "'");
        exec("update geolocale_taxon set specimen_count = 1 where geolocale_id in (" + EASTERN_AFRICA + ", " + AFRICA + ") and taxon_name = '" + SP_X + "'");

        specimen("m1", SP_A, "morphotaxon", "Madagascar", "Madagascar", "h", "p", "l");
        specimen("m2", SP_B, "valid", "Madagascar", "Madagascar", "h");
        specimen("m3", SP_B, "synonym", "Madagascar", "Madagascar", "h");     // status not counted
        specimen("c1", SP_B, "valid", "Comoros", null);
        specimen("z1", SP_B, "valid", "Tanzania", "Zanzibar", "d");           // Tanzania and Zanzibar: counted once
        specimen("k1", SP_B, "valid", "Kenya", null, "h");                    // name not on the Kenya list
        specimen("r1", SP_B, "valid", "Malagasy Republic", null, "h");        // country name of no valid country
        specimen("b1", SP_B, "valid", "Brazil", null, "h", "p");
        specimen("b2", SP_X, "valid", "Brazil", null);
    }

    private void geolocale(int id, String name, String georank, String parent, int valid, int island) throws SQLException {
        exec("insert into geolocale (id, name, georank, parent, is_valid, is_island) values (" + id + ", '" + name + "', '" + georank
            + "', " + q(parent) + ", " + valid + ", " + island + ")");
    }

    private void list(int geolocaleId, String... species) throws SQLException {
        String old = "'2020-01-01 10:00:00'";
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + geolocaleId + ", '" + SUBFAMILY + "', " + old + ")");
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + geolocaleId + ", '" + GENUS + "', " + old + ")");
        for (String sp : species) {
            exec("insert into geolocale_taxon (geolocale_id, taxon_name, created) values (" + geolocaleId + ", '" + sp + "', " + old + ")");
        }
    }

    private int nextImageId = 1;

    private void specimen(String code, String taxon, String status, String country, String island, String... shots) throws SQLException {
        exec("insert into specimen (code, taxon_name, status, country, island_country, upload_id, family, subfamily, genus, species, image_count)"
            + " values ('" + code + "', '" + taxon + "', '" + status + "', " + q(country) + ", " + q(island) + ", 1, 'formicidae', '"
            + SUBFAMILY + "', 'pheidole', '" + taxon.substring(taxon.indexOf(' ') + 1) + "', " + shots.length + ")");
        for (String shot : shots) {
            exec("insert into image values (" + (nextImageId++) + ", '" + code + "', '" + shot + "', '2020-01-01 10:00:00')");
        }
    }

    private static String q(String v) { return v == null ? "null" : "'" + v + "'"; }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            // CountCheckTest uses the same database without these tables.
            exec("drop table if exists image");
            exec("drop table if exists place_image_count");
            exec("drop table if exists geolocale");
            connection.close();
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private int intQuery(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertTrue(sql, r.next());
            return r.getInt(1);
        }
    }

    private static Geolocale place(int id, String name, String georank, boolean island) {
        Geolocale g = new Geolocale();
        g.setId(id);
        g.setName(name);
        g.setGeorank(georank);
        g.setIsIsland(island);
        return g;
    }

    private Map<String, Integer> perTaxon(String query) throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(query)) {
            while (r.next()) m.put(r.getString("taxonName"), r.getInt(1));
        }
        return m;
    }

    /** CountDb.countSpecimens (protected) as the nightly geolocale count crawl calls it. */
    static final class Crawl extends GeolocaleTaxonCountDb {
        Crawl(Connection c) { super(c); }
        int specimens(Countable countable) throws SQLException { return countSpecimens(countable); }
    }

    private void countAll() throws SQLException {
        Crawl crawl = new Crawl(connection);
        GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
        for (Geolocale g : new Geolocale[] {
                place(MADAGASCAR, "Madagascar", "country", true), place(COMOROS, "Comoros", "country", false),
                place(KENYA, "Kenya", "country", false), place(TANZANIA, "Tanzania", "country", false),
                place(ZANZIBAR, "Zanzibar", "country", true), place(BRAZIL, "Brazil", "country", false),
                place(EASTERN_AFRICA, "Eastern Africa", "subregion", false), place(AFRICA, "Africa", "region", false),
                place(SOUTH_AMERICA, "South America", "subregion", false), place(AMERICAS, "Americas", "region", false)}) {
            crawl.specimens(g);
            geolocaleDb.getSpecimenCount(g);
            exec("update geolocale set specimen_count = " + g.getSpecimenCount() + " where id = " + g.getId());
        }
    }

    private int specimenCount(int geolocaleId, String taxon) throws SQLException {
        return intQuery("select specimen_count from geolocale_taxon where geolocale_id = " + geolocaleId + " and taxon_name = '" + taxon + "'");
    }

    private int placeSpecimens(int geolocaleId) throws SQLException {
        return intQuery("select specimen_count from geolocale where id = " + geolocaleId);
    }

    public void testSubregionCountsTheSpecimensOfItsCountries() throws Exception {
        Geolocale eastern = place(EASTERN_AFRICA, "Eastern Africa", "subregion", false);
        assertTrue(eastern.isCountedFromCountries());
        Map<String, Integer> m = perTaxon(eastern.getCountSpecimensQuery());
        assertEquals("m1", 1, (int) m.get(SP_A));
        // m2 (Madagascar), c1 (Comoros), z1 (Zanzibar, also Tanzania: once); not m3 (synonym status), not k1 (not
        // on the Kenya list), not r1 (no valid country), not b1 (Brazil).
        assertEquals(3, (int) m.get(SP_B));
        assertNull("collected in Brazil only", m.get(SP_X));
        assertEquals("a region: the countries of its subregions", m, perTaxon(place(AFRICA, "Africa", "region", false).getCountSpecimensQuery()));
        Map<String, Integer> sa = perTaxon(place(SOUTH_AMERICA, "South America", "subregion", false).getCountSpecimensQuery());
        assertEquals(1, (int) sa.get(SP_B));
        assertEquals(1, (int) sa.get(SP_X));
        // A country is unchanged: its own specimens.
        assertFalse(place(BRAZIL, "Brazil", "country", false).isCountedFromCountries());
        assertEquals("", place(BRAZIL, "Brazil", "country", false).getSpecimenPlaceJoin());
    }

    public void testNightlyCountsTotalsAndChart() throws Exception {
        countAll();
        assertEquals(3, specimenCount(EASTERN_AFRICA, SP_B));
        assertEquals("the stale count of a name without specimens here is set to 0", 0, specimenCount(EASTERN_AFRICA, SP_X));
        assertEquals(1, specimenCount(EASTERN_AFRICA, SP_A));
        assertEquals("Eastern Africa: m1, m2, c1, z1", 4, placeSpecimens(EASTERN_AFRICA));
        assertEquals(4, placeSpecimens(AFRICA));
        assertEquals(2, placeSpecimens(SOUTH_AMERICA));
        // Specimens of a subregion never more than the sum of its countries' (here equal, minus the
        // specimen counted on both Tanzania and Zanzibar).
        int countries = 0;
        for (int c : new int[] {MADAGASCAR, COMOROS, KENYA, TANZANIA, ZANZIBAR, OLD_NAME}) countries += placeSpecimens(c);
        assertEquals("Madagascar 2, Comoros 1, Tanzania 1, Zanzibar 1", 5, countries);
        assertTrue(placeSpecimens(EASTERN_AFRICA) <= countries);

        // "Specimens per Subfamily" chart: the same specimens.
        String chart = PlaceTotals.specimenPieSql(PlaceTotals.GEOLOCALE);
        try (java.sql.PreparedStatement s = connection.prepareStatement(chart)) {
          s.setString(1, "" + EASTERN_AFRICA);
          try (ResultSet r = s.executeQuery()) {
            assertTrue(r.next());
            assertEquals(SUBFAMILY, r.getString("subfamily"));
            assertEquals(4, r.getInt("count"));
            assertFalse(r.next());
          }
        }
    }

    public void testRegionImagesAreThoseOfItsCountries() throws Exception {
        PlaceImageCountDb db = new PlaceImageCountDb(connection);
        db.updateType(PlaceImageCountDb.GEOLOCALE);
        PlaceImageCountDb.PlaceImages p = db.read(PlaceImageCountDb.GEOLOCALE, "" + EASTERN_AFRICA);
        assertNotNull(p);
        // m1 (h p l), m2 (h), z1 (d); not m3, k1, r1, b1.
        assertEquals(5, p.allImages);
        assertEquals(1, p.labelImages());
        assertEquals("m1, m2, z1", 3, p.imagedSpecimens);
        assertEquals("z1 once", 1, (int) p.shotImages.get("d"));
        assertEquals(5, db.read(PlaceImageCountDb.GEOLOCALE, "" + AFRICA).allImages);
        assertEquals("b1", 2, db.read(PlaceImageCountDb.GEOLOCALE, "" + SOUTH_AMERICA).allImages);
        assertEquals(5, intQuery("select image_count from geolocale where id = " + EASTERN_AFRICA));
        assertEquals(3, intQuery("select imaged_specimen_count from geolocale where id = " + EASTERN_AFRICA));

        // One place again (the Recalculate button): the statement with the id as parameter.
        exec("update geolocale set image_count = 99 where id = " + EASTERN_AFRICA);
        assertEquals(5, db.updatePlace(PlaceImageCountDb.GEOLOCALE, "" + EASTERN_AFRICA).allImages);
        assertEquals(5, intQuery("select image_count from geolocale where id = " + EASTERN_AFRICA));

        // "N Images" of each name on the subregion's image pages.
        String query = place(EASTERN_AFRICA, "Eastern Africa", "subregion", false).getTaxonImageCountQuery();
        Map<String, Integer> images = new HashMap<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(query)) {
            while (r.next()) images.put(r.getString("taxonName"), r.getInt("imageSum"));
        }
        assertEquals("m2 and z1", 2, (int) images.get(SP_B));
        assertEquals(3, (int) images.get(SP_A));
    }

    public void testCountCheckOfASubregion() throws Exception {
        countAll();
        new PlaceImageCountDb(connection).updateType(PlaceImageCountDb.GEOLOCALE);
        List<CountCheck.Place> places = new ArrayList<>();
        CountCheck.Place eastern = CountCheck.Place.region("" + EASTERN_AFRICA, "Eastern Africa (subregion)");
        places.add(eastern);
        java.sql.Timestamp nightly = java.sql.Timestamp.valueOf("2021-01-01 08:00:00");
        // Subfamilies of the page (CountCheck.checkSubfamilyTotal, GeolocaleDb.getListedSubfamilyCount): the
        // subfamily rows of the subregion's list, as for any other place.
        exec("update geolocale set subfamily_count = 1 where id = " + EASTERN_AFRICA);
        CountCheck.Result r = new CountCheck(connection).run(places, nightly, true);
        assertEquals(checked(r).toString(), 0, checked(r).size());
        assertEquals(r.mismatches.toString(), 0, subfamilyTotals(r).size());
        exec("update geolocale set subfamily_count = 0 where id = " + EASTERN_AFRICA);
        List<CountCheck.Mismatch> sub = subfamilyTotals(new CountCheck(connection).run(places, nightly, true));
        assertEquals(sub.toString(), 1, sub.size());
        assertEquals(1, sub.get(0).recount);
        exec("update geolocale set subfamily_count = 1 where id = " + EASTERN_AFRICA);

        // The old numbers (every specimen of the name, anywhere): real differences once the nightly has used the rule.
        exec("update geolocale set specimen_count = 9, image_count = 8 where id = " + EASTERN_AFRICA);
        exec("update geolocale_taxon set specimen_count = 7 where geolocale_id = " + EASTERN_AFRICA + " and taxon_name = '" + SP_B + "'");
        r = new CountCheck(connection).run(places, nightly, true);
        List<CountCheck.Mismatch> m = checked(r);
        assertEquals(m.toString(), 3, m.size());
        for (CountCheck.Mismatch x : m) assertTrue(x.toString(), x.isReal());
        boolean total = false;
        for (CountCheck.Mismatch x : m) {
            if ("place specimens".equals(x.check)) {
                assertEquals(9, x.stored);
                assertEquals(4, x.recount);
                total = true;
            }
        }
        assertTrue(total);
        // Before the first nightly run with the rule: pending.
        r = new CountCheck(connection).run(places, nightly, false);
        for (CountCheck.Mismatch x : checked(r)) assertFalse(x.toString(), x.isReal());

        List<String> log = new ArrayList<>();
        log.add("2026-10-20 05:40:00     Subregions populated");
        assertFalse(CountCheck.regionRuleCounted(log));
        log.add("2026-10-21 05:40:00     Subregions populated (" + GeolocaleDb.REGION_RULE_LOG + ")");
        assertTrue(CountCheck.regionRuleCounted(log));
    }

    private static List<CountCheck.Mismatch> subfamilyTotals(CountCheck.Result r) {
        List<CountCheck.Mismatch> list = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) if ("place totals".equals(m.check) && "Subfamilies".equals(m.taxonName)) list.add(m);
        return list;
    }

    /** The differences of the checks this change is about (list species and genus counts are not set up here). */
    private static List<CountCheck.Mismatch> checked(CountCheck.Result r) {
        List<CountCheck.Mismatch> list = new ArrayList<>();
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("specimens".equals(m.check) || "place specimens".equals(m.check) || "place images".equals(m.check)) list.add(m);
        }
        return list;
    }
}

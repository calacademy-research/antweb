package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.calacademy.antweb.PlaceTaxonNotes;
import org.calacademy.antweb.geolocale.Country;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.geolocale.Subregion;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.PlaceGenusCurationDb;
import org.calacademy.antweb.home.PlaceImageCountDb;
import org.calacademy.antweb.home.SpecimenDb;

/**
 * Oceanic islands are their own countries (Brian Fisher, 2026-11-11: "Its specimens never count toward the political
 * parent."). A specimen of Hawaii is stored with country Hawaii and island_country Hawaii (the column stays as the
 * marker of a flagged island this round); until now it was country United States, island_country Hawaii, and every
 * count of United States included it.
 *
 * Against a database, with the statements the pages and the daily update run: with both columns equal an island's
 * specimen is the island's, once, and never its political parent's: the country and island pages' specimens and
 * downloads, the specimen names of a place and its Not curated counts, the place images' sources, the subregions and
 * regions (each specimen once), the genus rows the lists take from specimens. A specimen the one-time update has not
 * reached yet (country United States, island_country Hawaii) is still counted for both, as before, until it is moved.
 * Endemic: a species collected only on the island is on the island's list alone after the move, so it is endemic to
 * the island (before, the parent's list had it too: "Endemic 0" on the Galapagos Islands page).
 *
 * Places: Americas > North America > United States; Americas > South America > Ecuador, Galapagos Islands (island of
 * Ecuador); Oceania > Polynesia > Hawaii (island of United States). Runs in CI against MySQL 8.4 (host "mysql");
 * ANTWEB_TEST_DB_URL overrides.
 */
public class IslandCountryDbTest extends TestCase {

    static final String SUBFAMILY = "myrmicinae";
    static final String GENUS = "myrmicinaepheidole";
    static final String SP_W = "myrmicinaepheidole megacephala";   // everywhere
    static final String SP_H = "myrmicinaepheidole hawaiiensis";   // collected in Hawaii only
    static final String SP_G = "myrmicinaepheidole williamsi";     // collected in the Galapagos only
    static final String MORPHO = "myrmicinaepheidole hi01";        // a morphospecies of Hawaii

    static final int AMERICAS = 1, NORTH_AMERICA = 2, SOUTH_AMERICA = 3, OCEANIA = 4, POLYNESIA = 5,
                     US = 97, ECUADOR = 103, HAWAII = 423, GALAPAGOS = 1721;

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"specimen", "image", "taxon", "geolocale", "geolocale_taxon", "place_specimen_name"}) {
            exec("drop table if exists " + t);
        }
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128),"
            + " family varchar(64), subfamily varchar(64), genus varchar(64), species varchar(64), fossil tinyint default 0)");
        exec("create table specimen (code varchar(128) primary key, taxon_name varchar(128), status varchar(40), country varchar(64),"
            + " adm1 varchar(64), island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int,"
            + " family varchar(64), subfamily varchar(64), genus varchar(64), species varchar(64), image_count int default 0,"
            + " flag varchar(10), key country_adm1_idx (country, adm1), key island_country_idx (island_country))");
        exec("create table image (id int primary key, image_of_id varchar(128), shot_type varchar(10), upload_date datetime)");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128), country varchar(128),"
            + " is_valid tinyint default 1, is_island tinyint default 0)");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), source varchar(40), is_introduced tinyint default 0,"
            + " is_endemic tinyint default 0, specimen_count int default 0, image_count int default 0, primary key (geolocale_id, taxon_name))");
        exec("create table place_specimen_name (list_type varchar(16) not null, list_key varchar(128) not null, taxon_name varchar(128) not null,"
            + " specimens int not null default 0, counted datetime null, primary key (list_type, list_key, taxon_name))");

        exec("insert into taxon values ('" + SUBFAMILY + "', 'subfamily', 'valid', 'formicidae', 'formicidae', '" + SUBFAMILY + "', null, null, 0)");
        exec("insert into taxon values ('" + GENUS + "', 'genus', 'valid', '" + SUBFAMILY + "', 'formicidae', '" + SUBFAMILY + "', 'pheidole', null, 0)");
        for (String[] sp : new String[][] {{SP_W, "valid"}, {SP_H, "valid"}, {SP_G, "valid"}, {MORPHO, "morphotaxon"}}) {
            exec("insert into taxon values ('" + sp[0] + "', 'species', '" + sp[1] + "', '" + GENUS + "', 'formicidae', '" + SUBFAMILY
                + "', 'pheidole', '" + sp[0].substring(sp[0].indexOf(' ') + 1) + "', 0)");
        }

        geolocale(AMERICAS, "Americas", "region", null, null, 0);
        geolocale(NORTH_AMERICA, "North America", "subregion", "Americas", null, 0);
        geolocale(SOUTH_AMERICA, "South America", "subregion", "Americas", null, 0);
        geolocale(OCEANIA, "Oceania", "region", null, null, 0);
        geolocale(POLYNESIA, "Polynesia", "subregion", "Oceania", null, 0);
        geolocale(US, "United States", "country", "North America", null, 0);
        geolocale(ECUADOR, "Ecuador", "country", "South America", null, 0);
        geolocale(HAWAII, "Hawaii", "country", "Polynesia", "United States", 1);          // the political parent, for reference only
        geolocale(GALAPAGOS, "Galapagos Islands", "country", "South America", "Ecuador", 1);

        // The specimens as the upload stores them from 2026-11-11: an island's with country = the island's own name.
        specimen("us1", SP_W, "valid", "United States", "California", null, "h");
        specimen("hi1", SP_H, "valid", "Hawaii", null, "Hawaii", "h");
        specimen("hi2", SP_W, "valid", "Hawaii", "Maui", "Hawaii", "p");                 // an adm1 the file gave is kept
        specimen("hi3", MORPHO, "morphotaxon", "Hawaii", null, "Hawaii");
        specimen("old1", SP_H, "valid", "United States", null, "Hawaii", "d");           // not moved yet (the old way)
        specimen("ec1", SP_W, "valid", "Ecuador", null, null, "h");
        specimen("ga1", SP_G, "valid", "Galapagos Islands", null, "Galapagos Islands", "h");

        // The lists as the daily update builds them from these specimens (source specimen).
        list(US, SP_W, SP_H);                // SP_H only because old1 is still filed under United States
        list(HAWAII, SP_W, SP_H, MORPHO);
        list(ECUADOR, SP_W);
        list(GALAPAGOS, SP_G);
        list(NORTH_AMERICA, SP_W, SP_H);
        list(POLYNESIA, SP_W, SP_H, MORPHO);
        list(SOUTH_AMERICA, SP_W, SP_G);
        list(AMERICAS, SP_W, SP_H, SP_G);
        list(OCEANIA, SP_W, SP_H, MORPHO);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            for (String t : new String[] {"image", "geolocale", "place_specimen_name"}) exec("drop table if exists " + t);
            connection.close();
        }
    }

    private void geolocale(int id, String name, String georank, String parent, String country, int island) throws SQLException {
        exec("insert into geolocale (id, name, georank, parent, country, is_valid, is_island) values (" + id + ", '" + name + "', '" + georank
            + "', " + q(parent) + ", " + q(country) + ", 1, " + island + ")");
    }

    private void list(int geolocaleId, String... species) throws SQLException {
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + geolocaleId + ", '" + SUBFAMILY + "', 'specimen'), ("
            + geolocaleId + ", '" + GENUS + "', 'specimen')");
        for (String sp : species) exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + geolocaleId + ", '" + sp + "', 'specimen')");
    }

    private int nextImageId = 1;

    private void specimen(String code, String taxon, String status, String country, String adm1, String island, String... shots) throws SQLException {
        exec("insert into specimen (code, taxon_name, status, country, adm1, island_country, upload_id, family, subfamily, genus, species, image_count)"
            + " values ('" + code + "', '" + taxon + "', '" + status + "', " + q(country) + ", " + q(adm1) + ", " + q(island) + ", 1, 'formicidae', '"
            + SUBFAMILY + "', 'pheidole', '" + taxon.substring(taxon.indexOf(' ') + 1) + "', " + shots.length + ")");
        for (String shot : shots) exec("insert into image values (" + (nextImageId++) + ", '" + code + "', '" + shot + "', '2020-01-01 10:00:00')");
    }

    private static String q(String v) { return v == null ? "null" : "'" + v + "'"; }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    /** The values of one column, each row (so a specimen that comes twice shows twice), sorted. */
    private List<String> column(String sql, int col) throws SQLException {
        List<String> list = new ArrayList<>();
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) list.add(r.getString(col));
        }
        java.util.Collections.sort(list);
        return list;
    }

    private static List<String> l(String... values) {
        List<String> list = new ArrayList<>(java.util.Arrays.asList(values));
        java.util.Collections.sort(list);
        return list;
    }

    private static Country country(int id, String name, boolean island) {
        Country c = new Country();
        c.setId(id);
        c.setName(name);
        c.setGeorank("country");
        c.setIsIsland(island);
        return c;
    }

    private static Subregion subregion(int id, String name) {
        Subregion s = new Subregion();
        s.setId(id);
        s.setName(name);
        s.setGeorank("subregion");
        return s;
    }

    /** The one-time update (tools/place/island_countries.py): country = island_country for a flagged island's specimen. */
    private void move() throws SQLException {
        exec("update specimen s join geolocale g on g.name = s.island_country and g.georank = 'country' and g.is_island = 1 and g.is_valid = 1"
            + " set s.country = g.name where not (s.country <=> g.name)");
        // the lists are built again from the specimens (set 2): the parent's rows from specimens it no longer has go
        exec("delete from geolocale_taxon where geolocale_id in (" + US + ", " + NORTH_AMERICA + ") and taxon_name = '" + SP_H + "'");
        exec("delete from geolocale_taxon where geolocale_id = " + AMERICAS + " and taxon_name = '" + SP_H + "'");
    }

    private List<String> pageSpecimens(Geolocale place) throws SQLException {
        return column("select s.code from specimen s" + place.getSpecimenPlaceJoin() + " join geolocale_taxon gt on s.taxon_name = gt.taxon_name"
            + " where gt.geolocale_id = " + place.getId() + place.getSpecimenPlaceCriteria() + " and s.status in " + Geolocale.COUNTABLE, 1);
    }

    /** The country page and the island page (Geolocale.getSpecimenPlaceCriteria: their Specimens, their downloads). */
    public void testTheParentsPageHasNoIslandSpecimenAndTheIslandsHasEachOnce() throws Exception {
        Country us = country(US, "United States", false), hawaii = country(HAWAII, "Hawaii", true);
        Country ecuador = country(ECUADOR, "Ecuador", false), galapagos = country(GALAPAGOS, "Galapagos Islands", true);
        assertEquals("old1 is still filed under United States", l("old1", "us1"), pageSpecimens(us));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), pageSpecimens(hawaii));
        assertEquals(l("ec1"), pageSpecimens(ecuador));
        assertEquals(l("ga1"), pageSpecimens(galapagos));
        assertEquals("the country's download", l("old1", "us1"), column("select s.code" + SpecimenDb.getDownloadFromWhere(us), 1));
        assertEquals("the island's download", l("hi1", "hi2", "hi3", "old1"), column("select s.code" + SpecimenDb.getDownloadFromWhere(hawaii), 1));

        move();
        assertEquals("a United States export holds no Hawaii record", l("us1"), column("select s.code" + SpecimenDb.getDownloadFromWhere(us), 1));
        assertEquals(l("us1"), pageSpecimens(us));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), pageSpecimens(hawaii));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), column("select s.code" + SpecimenDb.getDownloadFromWhere(hawaii), 1));
    }

    /** The specimens of a place as the lists' views and names take them (Overview.getOverviewCriteria, placeSpecimenParts). */
    public void testListViewsTakeAnIslandSpecimenOnce() throws Exception {
        Country us = country(US, "United States", false), hawaii = country(HAWAII, "Hawaii", true);
        move();
        assertEquals(l("us1"), column("select specimen.code from specimen where 1 = 1" + us.getOverviewCriteria(), 1));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), column("select specimen.code from specimen where 1 = 1" + hawaii.getOverviewCriteria(), 1));
        List<String> parts = PlaceTaxonNotes.placeSpecimenParts(hawaii);
        assertEquals(2, parts.size());
        StringBuilder union = new StringBuilder();
        for (String part : parts) union.append(union.length() > 0 ? " union all " : "").append("select specimen.code from specimen where 1 = 1").append(part);
        assertEquals("both columns name the island: the specimen comes once", l("hi1", "hi2", "hi3", "old1"), column(union.toString(), 1));
        // a subregion: its countries by specimen.country, its islands by island_country
        assertEquals(l("hi1", "hi2", "hi3", "old1"),
            column("select specimen.code from specimen where 1 = 1" + PlaceTaxonNotes.placeSpecimenCriteria(subregion(POLYNESIA, "Polynesia")), 1));
        assertEquals(l("us1"),
            column("select specimen.code from specimen where 1 = 1" + PlaceTaxonNotes.placeSpecimenCriteria(subregion(NORTH_AMERICA, "North America")), 1));
    }

    /** Subregions and regions (Geolocale.regionSpecimensSql): the island's specimens in the island's subregion, once. */
    public void testRegionsCountAnIslandSpecimenOnceInTheIslandsRegion() throws Exception {
        assertEquals("before the move the parent's subregion has the specimen not moved yet",
            l("old1", "us1"), column(Geolocale.regionSpecimensSql(" and r.id = " + NORTH_AMERICA), 2));
        move();
        assertEquals(l("us1"), column(Geolocale.regionSpecimensSql(" and r.id = " + NORTH_AMERICA), 2));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), column(Geolocale.regionSpecimensSql(" and r.id = " + POLYNESIA), 2));
        assertEquals("Ecuador and its island are in one subregion: each specimen once", l("ec1", "ga1"),
            column(Geolocale.regionSpecimensSql(" and r.id = " + SOUTH_AMERICA), 2));
        assertEquals(l("ec1", "ga1", "us1"), column(Geolocale.regionSpecimensSql(" and r.id = " + AMERICAS), 2));
        assertEquals(l("hi1", "hi2", "hi3", "old1"), column(Geolocale.regionSpecimensSql(" and r.id = " + OCEANIA), 2));
    }

    /** The place images (PlaceImageCountDb): an island's by island_country, a country's by country where it is no island. */
    public void testPlaceImageSourcesTakeAnIslandsImagesForTheIslandOnly() throws Exception {
        move();
        Set<String> rows = new TreeSet<>();
        for (PlaceImageCountDb.Source source : PlaceImageCountDb.sources(PlaceImageCountDb.GEOLOCALE, false)) {
            try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(source.sql)) {
                while (r.next()) assertTrue("an image comes once per place: " + source.name + " " + r.getString(1) + " " + r.getString(2),
                    rows.add(source.name + ":" + r.getString(1) + ":" + r.getString(2)));
            }
        }
        Set<String> expected = new TreeSet<>(java.util.Arrays.asList(
            "islands:" + HAWAII + ":hi1", "islands:" + HAWAII + ":hi2", "islands:" + HAWAII + ":old1", "islands:" + GALAPAGOS + ":ga1",
            "countries:" + US + ":us1", "countries:" + ECUADOR + ":ec1",
            "regions and subregions:" + NORTH_AMERICA + ":us1", "regions and subregions:" + AMERICAS + ":us1",
            "regions and subregions:" + SOUTH_AMERICA + ":ec1", "regions and subregions:" + SOUTH_AMERICA + ":ga1",
            "regions and subregions:" + AMERICAS + ":ec1", "regions and subregions:" + AMERICAS + ":ga1",
            "regions and subregions:" + POLYNESIA + ":hi1", "regions and subregions:" + POLYNESIA + ":hi2", "regions and subregions:" + POLYNESIA + ":old1",
            "regions and subregions:" + OCEANIA + ":hi1", "regions and subregions:" + OCEANIA + ":hi2", "regions and subregions:" + OCEANIA + ":old1"));
        assertEquals(expected, rows);
    }

    /** The specimen names stored per country (place_specimen_name, the Not curated counts): the island's under the island. */
    public void testSpecimenNamesOfAPlace() throws Exception {
        exec(PlaceGenusCurationDb.fillNamesSql(PlaceGenusCurationDb.COUNTRY));
        assertEquals("before the move: old1 for both, as it always was",
            l("Ecuador megacephala 1", "Galapagos Islands williamsi 1", "Hawaii hawaiiensis 2", "Hawaii hi01 1", "Hawaii megacephala 1",
              "United States hawaiiensis 1", "United States megacephala 1"),
            column("select concat(list_key, ' ', substring_index(taxon_name, ' ', -1), ' ', specimens) from place_specimen_name", 1));
        exec("delete from place_specimen_name");
        move();
        exec(PlaceGenusCurationDb.fillNamesSql(PlaceGenusCurationDb.COUNTRY));
        assertEquals(l("Ecuador megacephala 1", "Galapagos Islands williamsi 1", "Hawaii hawaiiensis 2", "Hawaii hi01 1", "Hawaii megacephala 1",
              "United States megacephala 1"),
            column("select concat(list_key, ' ', substring_index(taxon_name, ' ', -1), ' ', specimens) from place_specimen_name", 1));
    }

    /** The genus rows the country lists take from specimens (set 2): a morphospecies of Hawaii gives Hawaii its genus, not United States. */
    public void testListRowsFromSpecimensGoToTheIsland() throws Exception {
        move();
        assertEquals(l("Hawaii"), column("select distinct x.country from (" + GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL + ") x", 1));
        // the species rows: country union island_country of the valid names, as insertGeolocaleTaxaFromSpecimens selects them
        assertEquals(l("Ecuador megacephala", "Galapagos Islands williamsi", "Hawaii hawaiiensis", "Hawaii megacephala", "United States megacephala"),
            column("select concat(x.c, ' ', substring_index(x.taxon_name, ' ', -1)) from (select distinct country c, taxon_name from specimen where status = 'valid'"
                + " and country is not null union select distinct island_country, taxon_name from specimen where status = 'valid' and island_country is not null) x", 1));
    }

    /**
     * Endemic (GeolocaleDb.COUNTRY_ENDEMIC_SQL: on the native rows of exactly one country's list). Filed the old way a
     * species collected only on the island is on the island's list and on its parent's: endemic to neither ("Endemic 0"
     * on the Galapagos Islands page). After the move and the rebuild of the lists it is endemic to the island.
     */
    public void testASpeciesOnlyOnTheIslandIsEndemicToItAfterTheMove() throws Exception {
        String endemic = "select concat(x.geolocale_id, ' ', substring_index(x.taxon_name, ' ', -1)) from (" + GeolocaleDb.COUNTRY_ENDEMIC_SQL + ") x";
        assertEquals("Hawaii's own species is on the United States list too: not endemic", l(GALAPAGOS + " williamsi"), column(endemic, 1));
        // the Galapagos filed the old way: its species on Ecuador's list as well
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + ECUADOR + ", '" + SP_G + "', 'specimen')");
        assertEquals("the double filing: no endemic on either page", l(), column(endemic, 1));
        exec("delete from geolocale_taxon where geolocale_id = " + ECUADOR + " and taxon_name = '" + SP_G + "'");
        move();
        assertEquals(l(GALAPAGOS + " williamsi", HAWAII + " hawaiiensis"), column(endemic, 1));
        // a row of the parent's list that does not come from specimens (a curator's, AntCat's) stays and keeps the name
        // from being endemic to the island: these rows are reported for a curator (island_countries.py, parent_list_rows.csv)
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + US + ", '" + SP_H + "', 'curator')");
        assertEquals(l(GALAPAGOS + " williamsi"), column(endemic, 1));
    }
}

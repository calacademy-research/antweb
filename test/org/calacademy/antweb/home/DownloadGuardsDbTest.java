package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Project;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.SpecimenDb;
import org.calacademy.antweb.search.AdvancedSearch;
import org.calacademy.antweb.search.ResultItem;
import org.calacademy.antweb.search.SearchPaging;

/**
 * Download guards against MySQL (Brian Fisher, 2026-10-21).
 *
 * The Download Data file of a place (getSpecimenList.do) holds only the specimens collected in that place.
 * Before, it held every specimen, worldwide, of the names on the place's list (Reunion, formicidae: 35,851
 * specimens, 1,778 of them from Reunion). A file holds at most 5,000 specimens (the limit is passed here).
 * The advanced search specimen list reads one page at a time (COUNT, then LIMIT/OFFSET). Place pages list
 * ant specimens only (SpecimenDb.getAntSpecimenCriteria).
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like RegionCountDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class DownloadGuardsDbTest extends TestCase {

    static final String SF = "myrmicinae", GENUS = "myrmicinaepheidole", SP = "myrmicinaepheidole megacephala";

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"specimen", "image", "taxon", "geolocale_taxon", "proj_taxon", "ant_group"}) {
            exec("drop table if exists " + t);
        }
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), family varchar(64), subfamily varchar(64))");
        exec("create table specimen (code varchar(128) primary key, taxon_name varchar(128), family varchar(64), subfamily varchar(64),"
            + " genus varchar(64), species varchar(64), subspecies varchar(64), status varchar(40), type_status varchar(64), toc int,"
            + " country varchar(64), adm1 varchar(64), adm2 varchar(64), island_country varchar(64), localityname varchar(128),"
            + " life_stage varchar(64), caste varchar(64), subcaste varchar(64), medium varchar(64), specimennotes varchar(255),"
            + " localitycode varchar(64), collectioncode varchar(64), created datetime null, habitat varchar(64), microhabitat varchar(64),"
            + " collectedby varchar(64), museum varchar(64), datecollectedstart date null, access_group int, determinedby varchar(64),"
            + " method varchar(64), dnaextractionnotes varchar(64), ownedby varchar(64), locatedat varchar(64), elevation varchar(20),"
            + " decimal_longitude float, decimal_latitude float, bioregion varchar(64), upload_id int, flag varchar(10), image_count int default 0)");
        exec("create table image (id int primary key, image_of_id varchar(128), shot_type varchar(10))");
        exec("create table ant_group (id int primary key, name varchar(128))");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), primary key (geolocale_id, taxon_name))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), primary key (project_name, taxon_name))");
        exec("insert into ant_group values (1, 'CAS')");

        exec("insert into taxon values ('" + SF + "', 'subfamily', 'valid', 'formicidae', '" + SF + "')");
        exec("insert into taxon values ('" + GENUS + "', 'genus', 'valid', 'formicidae', '" + SF + "')");
        exec("insert into taxon values ('" + SP + "', 'species', 'valid', 'formicidae', '" + SF + "')");
        // An orthopteran subfamily that an upload put under Formicidae: not an AntCat ant subfamily.
        exec("insert into taxon values ('cyrtacanthacridinae', 'subfamily', 'unrecognized', 'formicidae', 'cyrtacanthacridinae')");

        // code, country, adm1, island_country, museum, bioregion
        specimen("re1", "Reunion", null, null, "CASC", "Malagasy");
        specimen("mg1", "Madagascar", "Toamasina", null, "CASC", "Malagasy");
        specimen("mg2", "Madagascar", "Toliara", null, "MHNG", "Malagasy");
        specimen("mg3", "Madagascar", "Toamasina", null, "MHNG", "Malagasy");
        specimen("us1", "United States", "California", null, "CASC", "Nearctic");
        specimen("tz1", "Tanzania", null, "Zanzibar", "CASC", "Afrotropical");
        exec("update specimen set type_status = 'holotype' where code = 'mg2'");
        exec("insert into image values (1, 'mg3', 'h'), (2, 'mg3', 'p'), (3, 'mg1', 'h')");
        // The Reunion list has the species (as the nightly run builds it); so do the others.
        for (int id : new int[] {1, 2, 3, 4}) exec("insert into geolocale_taxon values (" + id + ", '" + SP + "')");
        exec("insert into proj_taxon values ('madants', '" + SP + "')");

        // Non-ants (uploaded, found by search, not listed on place pages).
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, species, status, country)"
            + " values ('or1', 'conocephalinae(indet)', 'tettigoniidae', 'conocephalinae', '(indet)', null, 'indetermined', 'Madagascar')");
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, species, status, country)"
            + " values ('or2', 'cyrtacanthacridinae(indet)', 'formicidae', 'cyrtacanthacridinae', '(indet)', null, 'indetermined', 'Madagascar')");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void specimen(String code, String country, String adm1, String island, String museum, String bioregion) throws SQLException {
        exec("insert into specimen (code, taxon_name, family, subfamily, genus, species, status, country, adm1, island_country, museum, bioregion, access_group, created)"
            + " values ('" + code + "', '" + SP + "', 'formicidae', '" + SF + "', '" + GENUS + "', 'megacephala', 'valid', "
            + q(country) + ", " + q(adm1) + ", " + q(island) + ", " + q(museum) + ", " + q(bioregion) + ", 1, '2020-01-01 10:00:00')");
    }

    private static String q(String s) { return s == null ? "null" : "'" + s + "'"; }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.execute(sql); }
    }

    private static Geolocale place(int id, String name, String georank, boolean island) {
        Geolocale g = new Geolocale();
        g.setId(id);
        g.setName(name);
        g.setGeorank(georank);
        g.setIsIsland(island);
        return g;
    }

    private List<String> codes(Object overview, int limit) throws SQLException {
        return new SpecimenDb(connection).getDownloadSpecimenCodes((org.calacademy.antweb.Overview) overview, "formicidae", SF, GENUS, "megacephala", null, limit);
    }

    private int count(Object overview) throws SQLException {
        return new SpecimenDb(connection).countDownloadSpecimens((org.calacademy.antweb.Overview) overview, "formicidae", SF, GENUS, null, null);
    }

    // --- place downloads ------------------------------------------------------------

    public void testCountryDownloadHasOnlyTheSpecimensCollectedThere() throws Exception {
        Geolocale reunion = place(1, "Reunion", "country", false);
        // The old query: every specimen of the names on the Reunion list, anywhere.
        ArrayList<String> old = new SpecimenDb(connection).getAntwebSpecimenCodes(reunion, "formicidae", SF, GENUS, "megacephala");
        assertEquals(6, old.size());
        // Now: the specimen collected in Reunion only.
        assertEquals(Arrays.asList("re1"), codes(reunion, 5000));
        assertEquals(1, count(reunion));
        assertEquals(Arrays.asList("mg1", "mg2", "mg3"), codes(place(2, "Madagascar", "country", false), 5000));
    }

    public void testIslandAdm1MuseumBioregionProject() throws Exception {
        assertEquals(Arrays.asList("tz1"), codes(place(3, "Zanzibar", "country", true), 5000));
        assertEquals(Arrays.asList("mg1", "mg3"), codes(place(4, "Toamasina", "adm1", false), 5000));
        Museum mhng = new Museum();
        mhng.setCode("MHNG");
        assertEquals(Arrays.asList("mg2", "mg3"), codes(mhng, 5000));
        org.calacademy.antweb.geolocale.Bioregion nearctic = new org.calacademy.antweb.geolocale.Bioregion();
        nearctic.setName("Nearctic");
        assertEquals(Arrays.asList("us1"), codes(nearctic, 5000));
        // Projects: the specimens of the names on the project's list (as the project counts them); allantwebants: all.
        assertEquals(6, codes(new Project("madants"), 5000).size());
        assertEquals(6, codes(new Project(Project.ALLANTWEBANTS), 5000).size());
        assertEquals(0, codes(new Project("otherproject"), 5000).size());
    }

    public void testCapAndNoDownloadForPlacesWithoutTheirOwnSpecimens() throws Exception {
        Geolocale madagascar = place(2, "Madagascar", "country", false);
        assertEquals(Arrays.asList("mg1", "mg2"), codes(madagascar, 2));     // the first ones, by code
        assertEquals(3, codes(madagascar, 0).size());                          // 0: no cap (administrators)
        assertEquals(3, count(madagascar));
        // A place rank without specimens of its own (adm2 here) gives no file, not a worldwide one.
        assertNull(SpecimenDb.getDownloadFromWhere(place(9, "Somewhere", "adm2", false)));
        assertEquals(-1, count(place(9, "Somewhere", "adm2", false)));
        assertTrue(codes(place(9, "Somewhere", "adm2", false), 5000).isEmpty());
        // Quotes in a name are SQL text, not SQL.
        assertEquals(0, count(place(5, "Cote d'Ivoire", "country", false)));
    }

    // --- ant specimens only on place pages ---------------------------------------------

    public void testAntSpecimenCriteriaLeavesOutNonAnts() throws Exception {
        List<String> found = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("select s.code from specimen s where" + SpecimenDb.getAntSpecimenCriteria("s")
                 + " and s.country = 'Madagascar' order by s.code")) {
            while (r.next()) found.add(r.getString(1));
        }
        assertEquals(Arrays.asList("mg1", "mg2", "mg3"), found);   // not or1 (Tettigoniidae) nor or2 (not an AntCat ant subfamily)
    }

    // --- paged advanced search ----------------------------------------------------------

    private AdvancedSearch madagascarSearch() {
        AdvancedSearch search = new AdvancedSearch();
        search.setConnection(connection);
        search.setCountry("Madagascar");
        search.setIsIgnoreInsufficientCriteria(true);
        return search;
    }

    private static List<String> codesOf(List<ResultItem> items) {
        List<String> l = new ArrayList<>();
        for (ResultItem i : items) l.add(i.getCode());
        return l;
    }

    public void testPagedSearchCountsAndReadsOnePage() throws Exception {
        AdvancedSearch search = madagascarSearch();
        assertEquals(5, search.countPaged());   // non-ants too: search finds them
        assertEquals(Arrays.asList("mg1", "mg2"), codesOf(search.getPage(SearchPaging.orderBy("code", null), 2, 0)));
        assertEquals(Arrays.asList("mg3", "or1"), codesOf(search.getPage(SearchPaging.orderBy("code", null), 2, 2)));
        assertEquals(Arrays.asList("or2"), codesOf(search.getPage(SearchPaging.orderBy("code", null), 2, 4)));
        // Sorting applies before paging: most images first.
        List<ResultItem> byImages = search.getPage(SearchPaging.orderBy("images", null), 2, 0);
        assertEquals(Arrays.asList("mg3", "mg1"), codesOf(byImages));
        assertEquals(2, byImages.get(0).getImageCount());
        // Descending code order.
        assertEquals(Arrays.asList("or2", "or1"), codesOf(search.getPage(SearchPaging.orderBy("code", "down"), 2, 0)));
    }

    public void testPagedSearchImagesAndTypesOnly() throws Exception {
        AdvancedSearch images = madagascarSearch();
        images.setImagesOnly("on");
        assertEquals(2, images.countPaged());
        assertEquals(Arrays.asList("mg1", "mg3"), codesOf(images.getPage("sp.code", 200, 0)));
        AdvancedSearch types = madagascarSearch();
        types.setTypes("on");
        assertEquals(1, types.countPaged());
        assertEquals(Arrays.asList("mg2"), codesOf(types.getPage("sp.code", 200, 0)));
    }
}

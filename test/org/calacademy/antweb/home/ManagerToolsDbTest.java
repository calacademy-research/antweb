package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.Museum;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.MuseumDb;

/**
 * Manager Tools review (2026-10-21), database steps of the Museum Manager and the Geolocale Manager:
 * - a museum name with an apostrophe saves as typed (the values were put into the SQL text before), saving
 *   an existing code updates it (no second row), a blank code saves nothing, and an odd code is only data;
 * - the delete confirmation counts the specimen records that carry the code;
 * - a place with places under it reports them (Edit Geolocale refuses to delete it), and deleting a place
 *   removes its species list rows (geolocale_taxon) too.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like RegionCountDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class ManagerToolsDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        for (String t : new String[] {"museum", "specimen", "object_edit", "geolocale", "geolocale_taxon"}) {
            exec("drop table if exists " + t);
        }
        exec("create table museum (code varchar(64) primary key, name varchar(256), title varchar(256), active tinyint default 1,"
            + " valid_species_count int default 0, specimen_count int default 0, image_count int default 0,"
            + " chart_color varchar(32), created timestamp null default current_timestamp)");
        exec("create table specimen (code varchar(128) primary key, museum varchar(64))");
        exec("create table object_edit (object_key varchar(128), title varchar(64), content text)");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), parent varchar(128))");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), primary key (geolocale_id, taxon_name))");
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private int count(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static Museum museum(String code, String name, boolean active) {
        Museum m = new Museum();
        m.setCode(code);
        m.setName(name);
        m.setTitle(name);
        m.setIsActive(active);
        return m;
    }

    public void testMuseumWithApostropheAddedThenUpdated() throws Exception {
        MuseumDb db = new MuseumDb(connection);
        String added = db.saveMuseum(museum("MHNG", "Musee d'histoire naturelle", true));
        assertTrue(added, added.contains("added"));
        Museum m = db.getMuseum("MHNG");
        assertEquals("Musee d'histoire naturelle", m.getName());
        assertTrue(m.getIsActive());

        String updated = db.saveMuseum(museum("MHNG", "Museum d'Histoire Naturelle, Geneve", false));
        assertTrue(updated, updated.contains("updated"));
        assertEquals(1, count("select count(*) from museum"));
        m = db.getMuseum("MHNG");
        assertEquals("Museum d'Histoire Naturelle, Geneve", m.getName());
        assertFalse(m.getIsActive());
    }

    public void testBlankCodeSavesNothingAndCodesAreData() throws Exception {
        MuseumDb db = new MuseumDb(connection);
        assertTrue(db.saveMuseum(museum("  ", "x", true)).startsWith("Nothing was saved"));
        assertTrue(db.saveMuseum(museum(null, "x", true)).startsWith("Nothing was saved"));
        assertEquals(0, count("select count(*) from museum"));
        assertNull("a code with a quote is looked up as a value", db.getMuseum("X' or '1'='1"));
        db.saveMuseum(museum(" CASC ", "California Academy of Sciences", true));
        assertNotNull("the code is stored trimmed", db.getMuseum("CASC"));
    }

    public void testSpecimenCountForDeleteConfirmation() throws Exception {
        exec("insert into specimen values ('casent0001', 'CASC'), ('casent0002', 'CASC'), ('casent0003', 'MCZC')");
        MuseumDb db = new MuseumDb(connection);
        assertEquals(2, db.getSpecimenCountOfCode("CASC"));
        assertEquals(0, db.getSpecimenCountOfCode("ZZZZ"));
        assertEquals(0, db.getSpecimenCountOfCode(null));
    }

    public void testPlaceWithChildrenAndDeleteRemovesSpeciesRows() throws Exception {
        exec("insert into geolocale values (1, 'Madagascar', 'country', 'Eastern Africa'),"
            + " (2, 'Antsiranana', 'adm1', 'Madagascar'), (3, 'Toliara', 'adm1', 'Madagascar'), (4, 'Typo Adm1', 'adm1', 'Comoros')");
        exec("insert into geolocale_taxon values (4, 'myrmicinaepheidole megacephala'), (4, 'formicinaecamponotus x'), (2, 'myrmicinaepheidole megacephala')");
        GeolocaleDb db = new GeolocaleDb(connection);

        Geolocale madagascar = new Geolocale();
        madagascar.setName("Madagascar");
        madagascar.setGeorank("country");
        assertEquals("two adm1s lie in Madagascar", 2, db.getChildCount(madagascar));
        Geolocale adm1 = new Geolocale();
        adm1.setName("Typo Adm1");
        adm1.setGeorank("adm1");
        assertEquals("an adm1 has no child places", 0, db.getChildCount(adm1));

        assertTrue(db.deleteGeolocale(4));
        assertEquals(0, count("select count(*) from geolocale where id = 4"));
        assertEquals("its species list rows are gone", 0, count("select count(*) from geolocale_taxon where geolocale_id = 4"));
        assertEquals("other places keep theirs", 1, count("select count(*) from geolocale_taxon where geolocale_id = 2"));
        assertFalse("nothing to delete", db.deleteGeolocale(99));
    }
}

package test.org.calacademy.antweb.home;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Source;
import org.calacademy.antweb.home.CuratorTaxonSetDb;
import org.calacademy.antweb.home.GeolocaleTaxonDb;
import org.calacademy.antweb.home.ProjTaxonDb;
import org.calacademy.antweb.home.TaxonSetDb;

/**
 * Curator rows of region (geolocale_taxon) and project (proj_taxon) species lists survive the
 * automated cleanups (2026-10-19).
 *
 * Before: the nightly dataCleanup (set 2) and the orphan step of a specimen upload deleted every
 * list row whose taxon row was missing at that moment. A curator's morphospecies whose specimens
 * were missing for one night (an upload re-run, a group's data removed and reloaded) left every
 * curated list for good. Now such rows are parked in taxon_set_parked and put back when the name
 * exists again; rows the system builds (source specimen, antcat, proxy parents) are deleted as
 * before.
 *
 * Runs in CI (.github/workflows/tests.yaml) in the antweb image against a MySQL 8.4 container
 * reachable as host "mysql" (database ant, user antweb). ANTWEB_TEST_DB_URL overrides the URL.
 * It creates its own small taxon, specimen, geolocale_taxon and proj_taxon tables there.
 */
public class CuratorTaxonSetTest extends TestCase {

    private static final String CHRYSAPACE = "dorylinaechrysapace mg01";
    private static final String TAPINOLEPIS = "formicinaetapinolepis mg01";
    private static final String VALID = "myrmicinaemalagidris belti";
    private static final int MADAGASCAR = 7;

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        String user = System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb");
        String password = System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME");
        connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            for (String t : new String[] {"geolocale_taxon", "proj_taxon", "specimen", "taxon"}) {
                s.executeUpdate("drop table if exists " + t);
            }
            s.executeUpdate("create table taxon (taxon_name varchar(128) not null primary key,"
                + " taxarank varchar(20), status varchar(30))");
            s.executeUpdate("create table specimen (code varchar(64) not null primary key, taxon_name varchar(128))");
            s.executeUpdate("create table geolocale_taxon (geolocale_id int not null, taxon_name varchar(128) not null,"
                + " source varchar(30), insert_method varchar(30), created timestamp null default current_timestamp,"
                + " primary key (geolocale_id, taxon_name))");
            s.executeUpdate("create table proj_taxon (project_name varchar(64) not null, taxon_name varchar(128) not null,"
                + " source varchar(30), created timestamp null default current_timestamp,"
                + " primary key (project_name, taxon_name))");
            // The application creates this table once per run (ensureSchema); here it is emptied.
            s.executeUpdate(CuratorTaxonSetDb.createTable(null, null));
            s.executeUpdate("delete from taxon_set_parked");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private int count(String sql) throws Exception {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private void geo(String taxonName, String source) throws Exception {
        exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + MADAGASCAR + ", '" + taxonName + "', '" + source + "')");
    }

    private void proj(String project, String taxonName, String source) throws Exception {
        exec("insert into proj_taxon (project_name, taxon_name, source) values ('" + project + "', '" + taxonName + "', '" + source + "')");
    }

    private boolean inGeo(String taxonName) throws Exception {
        return count("select count(*) from geolocale_taxon where geolocale_id = " + MADAGASCAR + " and taxon_name = '" + taxonName + "'") == 1;
    }

    private int waiting() throws Exception {
        return count("select count(*) from taxon_set_parked where restored is null");
    }

    /** Puts a parked row back into its table (the site uses the list insert, which needs the geolocale caches). */
    private CuratorTaxonSetDb.Restorer tableRestorer(List<String> done) {
        return p -> {
            if (CuratorTaxonSetDb.GEOLOCALE.equals(p.listType)) {
                exec("insert into geolocale_taxon (geolocale_id, taxon_name, source) values (" + p.listKey + ", '" + p.taxonName + "', '" + p.source + "')");
            } else {
                exec("insert into proj_taxon (project_name, taxon_name, source) values ('" + p.listKey + "', '" + p.taxonName + "', '" + p.source + "')");
            }
            done.add(p.toString());
            return true;
        };
    }

    // --- Sources ---

    public void testCuratorSourcesAreTheCuratorSourcesOfSource() {
        for (String s : CuratorTaxonSetDb.CURATOR_SOURCES) {
            assertEquals(s, Source.CURATOR, Source.getSourceCanonical(s));
            assertTrue(CuratorTaxonSetDb.isCuratorSource(s));
        }
        for (String s : new String[] {"specimen", "adm1Specimen", "proxyspecimen", "antcat", "proxyantcat",
                "antwiki", "proxyspeciesListTool", "proxycurator", "", null}) {
            assertFalse(String.valueOf(s), CuratorTaxonSetDb.isCuratorSource(s));
        }
        assertFalse(CuratorTaxonSetDb.parkGeolocaleSql().contains("proxy"));
    }

    // --- Park ---

    public void testCuratorRowsWithoutTaxonAreParkedOthersNot() throws Exception {
        exec("insert into taxon values ('" + VALID + "', 'species', 'valid')");
        geo(CHRYSAPACE, "speciesListTool");          // curator, taxon missing: parked
        geo(TAPINOLEPIS, "curator");                 // curator, taxon missing: parked
        geo("formicinaetapinolepis", "proxyspeciesListTool"); // proxy parent: rebuilt by the insert, not parked
        geo("ponerinaehypoponera mg999", "specimen");  // rebuilt from specimens, not parked
        geo(VALID, "speciesListTool");               // taxon present: nothing to park
        proj("madants", TAPINOLEPIS, "speciesListTool"); // curated project: parked
        proj("worldants", TAPINOLEPIS, "speciesListTool"); // system list: not parked

        CuratorTaxonSetDb db = new CuratorTaxonSetDb(connection);
        assertEquals(3, db.parkCuratorRowsWithoutTaxon());
        assertEquals(3, waiting());
        assertEquals(1, count("select count(*) from taxon_set_parked where list_type = 'geolocale' and list_key = '7'"
            + " and taxon_name = '" + CHRYSAPACE + "' and source = 'speciesListTool'"));
        assertEquals(1, count("select count(*) from taxon_set_parked where list_type = 'project' and list_key = 'madants'"));

        // Parking again (every night) keeps one row per list and name.
        db.parkCuratorRowsWithoutTaxon();
        assertEquals(3, count("select count(*) from taxon_set_parked"));
    }

    public void testParkOneName() throws Exception {
        exec("insert into taxon values ('" + CHRYSAPACE + "', 'species', 'morphotaxon')");
        geo(CHRYSAPACE, "speciesListTool");
        geo(TAPINOLEPIS, "speciesListTool");
        proj("madants", CHRYSAPACE, "curator");
        assertEquals(2, new CuratorTaxonSetDb(connection).parkCuratorRows(CHRYSAPACE));
        assertEquals(0, count("select count(*) from taxon_set_parked where taxon_name = '" + TAPINOLEPIS + "'"));
    }

    // --- The nightly cleanup ---

    public void testNightlyCleanupKeepsCuratorRows() throws Exception {
        // A curator's morphospecies whose specimens are missing tonight: its taxon row is gone.
        geo(CHRYSAPACE, "speciesListTool");
        proj("madants", CHRYSAPACE, "speciesListTool");
        // A system row without taxon, and a curator row of a name that still exists.
        geo("ponerinaehypoponera mg999", "specimen");
        exec("insert into taxon values ('" + VALID + "', 'species', 'valid')");
        geo(VALID, "speciesListTool");

        TaxonSetDb.dataCleanup(connection);

        assertFalse("rows without taxon leave the list", inGeo(CHRYSAPACE));
        assertFalse(inGeo("ponerinaehypoponera mg999"));
        assertTrue(inGeo(VALID));
        assertEquals("the curator's rows are parked", 2, waiting());

        // Next night the specimens are back, so is the name: the rows return.
        exec("insert into taxon values ('" + CHRYSAPACE + "', 'species', 'morphotaxon')");
        exec("insert into specimen values ('CASENT0906557', '" + CHRYSAPACE + "')");
        List<String> done = new ArrayList<>();
        String message = new CuratorTaxonSetDb(connection).restore(tableRestorer(done));
        assertTrue(message, message.contains("restored:2"));
        assertTrue(inGeo(CHRYSAPACE));
        assertEquals(1, count("select count(*) from proj_taxon where project_name = 'madants' and taxon_name = '" + CHRYSAPACE + "'"));
        assertEquals(0, waiting());
        assertEquals("restored rows are kept as the record for an undo", 2,
            count("select count(*) from taxon_set_parked where restored is not null"));

        // Nothing waiting: a second restore does nothing.
        done.clear();
        new CuratorTaxonSetDb(connection).restore(tableRestorer(done));
        assertTrue(done.isEmpty());
    }

    public void testRestoreWaitsForTheName() throws Exception {
        geo(TAPINOLEPIS, "speciesListTool");
        CuratorTaxonSetDb db = new CuratorTaxonSetDb(connection);
        db.parkCuratorRowsWithoutTaxon();
        exec("delete from geolocale_taxon");
        List<String> done = new ArrayList<>();
        db.restore(tableRestorer(done));
        assertTrue("the name does not exist yet", done.isEmpty());
        assertEquals(1, waiting());

        // A list that no longer exists: stays parked.
        exec("insert into taxon values ('" + TAPINOLEPIS + "', 'species', 'morphotaxon')");
        db.restore(p -> false);
        assertEquals(1, waiting());
        db.restore(tableRestorer(done));
        assertEquals(1, done.size());
        assertEquals(0, waiting());

        // Parked again later (the name is gone again): waiting again.
        exec("delete from taxon");
        db.parkCuratorRowsWithoutTaxon();
        assertEquals(1, waiting());
    }

    // --- Morphospecies without specimens ---

    public void testMorphoCleanupLeavesCuratorRows() throws Exception {
        exec("insert into taxon values ('" + TAPINOLEPIS + "', 'species', 'morphotaxon')");
        geo(TAPINOLEPIS, "speciesListTool");
        proj("madants", TAPINOLEPIS, "speciesListTool");
        exec("insert into taxon values ('ponerinaehypoponera mg999', 'species', 'morphotaxon')");
        geo("ponerinaehypoponera mg999", "specimen");
        proj("madants", "ponerinaehypoponera mg999", "specimen");

        call(new GeolocaleTaxonDb(connection), "deleteUncuratedMorphosWithoutSpecimen");
        call(new ProjTaxonDb(connection), "deleteUncuratedMorphosWithoutSpecimen");

        assertTrue(inGeo(TAPINOLEPIS));
        assertEquals(1, count("select count(*) from proj_taxon where taxon_name = '" + TAPINOLEPIS + "'"));
        assertFalse(inGeo("ponerinaehypoponera mg999"));
        assertEquals(0, count("select count(*) from proj_taxon where taxon_name = 'ponerinaehypoponera mg999'"));
    }

    private static void call(Object db, String method) throws Exception {
        Method m = db.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(db);
    }
}

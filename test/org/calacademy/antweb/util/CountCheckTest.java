package test.org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.SpecimenNameUpdateDb;
import org.calacademy.antweb.util.CountCheck;

/**
 * The count check (countCheck.do) against a database: stored list counts are compared with a
 * recount from the specimen and taxon tables; a difference is "pending" only when something changed
 * after the last nightly count run, otherwise "real".
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like CuratorTaxonSetTest; ANTWEB_TEST_DB_URL overrides.
 */
public class CountCheckTest extends TestCase {

    static final String GENUS = "myrmicinaepheidole";
    static final String SP_A = "myrmicinaepheidole a1";       // morphospecies: 2 specimens in Madagascar
    static final String SP_B = "myrmicinaepheidole megacephala";
    static final String SUBFAMILY = "myrmicinae";

    private Connection connection;
    private CountCheck.Place madagascar;
    private CountCheck.Place project;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        exec("drop table if exists specimen");
        exec("drop table if exists taxon");
        exec("drop table if exists geolocale_taxon");
        exec("drop table if exists proj_taxon");
        exec("drop table if exists upload");
        exec("create table taxon (taxon_name varchar(128) primary key, taxarank varchar(20), status varchar(40), parent_taxon_name varchar(128))");
        exec("create table specimen (code varchar(128) primary key, taxon_name varchar(128), status varchar(40), country varchar(64),"
            + " adm1 varchar(64), island_country varchar(64), museum varchar(64), bioregion varchar(64), upload_id int)");
        exec("create table geolocale_taxon (geolocale_id int, taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, created timestamp null, primary key (geolocale_id, taxon_name))");
        exec("create table proj_taxon (project_name varchar(64), taxon_name varchar(128), subfamily_count int default 0, genus_count int default 0,"
            + " species_count int default 0, specimen_count int default 0, created timestamp null, primary key (project_name, taxon_name))");
        exec("create table upload (upload_id int primary key, created timestamp null)");
        exec(SpecimenNameUpdateDb.createTable(null, null));
        exec("delete from specimen_name_update");

        exec("insert into taxon values ('" + SUBFAMILY + "', 'subfamily', 'valid', 'formicidae')");
        exec("insert into taxon values ('" + GENUS + "', 'genus', 'valid', '" + SUBFAMILY + "')");
        exec("insert into taxon values ('" + SP_A + "', 'species', 'morphotaxon', '" + GENUS + "')");
        exec("insert into taxon values ('" + SP_B + "', 'species', 'valid', '" + GENUS + "')");
        exec("insert into upload values (1, '2020-01-01 10:00:00')");
        exec("insert into specimen values ('casent1', '" + SP_A + "', 'morphotaxon', 'Madagascar', null, 'Madagascar', null, 'Malagasy', 1)");
        exec("insert into specimen values ('casent2', '" + SP_A + "', 'morphotaxon', 'Madagascar', null, 'Madagascar', null, 'Malagasy', 1)");
        exec("insert into specimen values ('casent3', '" + SP_B + "', 'valid', 'Madagascar', null, 'Madagascar', null, 'Malagasy', 1)");
        exec("insert into specimen values ('casent4', '" + SP_B + "', 'valid', 'Mauritius', null, 'Mauritius', null, 'Malagasy', 1)");
        exec("insert into specimen values ('casent5', '" + SP_B + "', 'synonym', 'Madagascar', null, 'Madagascar', null, 'Malagasy', 1)");

        // The Madagascar list, with the counts the nightly run would store (all right).
        String old = "'2020-01-01 10:00:00'";
        exec("insert into geolocale_taxon values (7, '" + SUBFAMILY + "', 0, 1, 2, 3, " + old + ")");
        exec("insert into geolocale_taxon values (7, '" + GENUS + "', 0, 0, 2, 3, " + old + ")");
        exec("insert into geolocale_taxon values (7, '" + SP_A + "', 0, 0, 0, 2, " + old + ")");
        exec("insert into geolocale_taxon values (7, '" + SP_B + "', 0, 0, 0, 1, " + old + ")");   // the synonym-status specimen does not count
        // Global (allantwebants): all specimens of the name, any status.
        exec("insert into proj_taxon values ('allantwebants', '" + SP_A + "', 0, 0, 0, 2, " + old + ")");
        exec("insert into proj_taxon values ('allantwebants', '" + SP_B + "', 0, 0, 0, 3, " + old + ")");
        // A curated project: all specimens of its names.
        exec("insert into proj_taxon values ('madants', '" + GENUS + "', 0, 0, 2, 5, " + old + ")");
        exec("insert into proj_taxon values ('madants', '" + SP_A + "', 0, 0, 0, 2, " + old + ")");
        exec("insert into proj_taxon values ('madants', '" + SP_B + "', 0, 0, 0, 3, " + old + ")");

        madagascar = new CountCheck.Place(CountCheck.GEOLOCALE, "7", "Madagascar", "island_country", "Madagascar");
        project = new CountCheck.Place(CountCheck.PROJECT, "madants", "project madants", null, null);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) { s.executeUpdate(sql); }
    }

    private CountCheck.Result check() throws SQLException {
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(madagascar);
        places.add(project);
        return new CountCheck(connection).run(places);
    }

    public void testLastCompletedRunFromComputeLog() {
        List<String> log = new ArrayList<>();
        assertNull(CountCheck.lastCompletedRun(log));
        log.add("2026-10-18 05:00:00 Begin run");
        log.add("2026-10-18 07:58:12 End run");
        log.add("2026-10-19 05:00:01 Begin run");
        log.add("2026-10-19 05:40:00 Scheduler FAILED (remaining sets skipped) url:x e:y");
        log.add("2026-10-19 05:40:01 End run");
        assertEquals("a failed run does not count", java.sql.Timestamp.valueOf("2026-10-18 07:58:12"), CountCheck.lastCompletedRun(log));
        log.add("2026-10-20 05:00:00 Begin run");
        log.add("2026-10-20 05:31:00   name update: FAILED, nothing changed (x)");
        log.add("2026-10-20 07:50:00 End run");
        assertEquals("a failed name update does not stop the counts", java.sql.Timestamp.valueOf("2026-10-20 07:50:00"), CountCheck.lastCompletedRun(log));
        log.add("2026-10-21 05:00:00 Begin run");
        assertEquals("a run still going does not count", java.sql.Timestamp.valueOf("2026-10-20 07:50:00"), CountCheck.lastCompletedRun(log));
    }

    public void testCountedUntilIsTheLastRun() throws Exception {
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(madagascar);
        // The last completed run was long ago: the upload of 2020-06-01 came after it.
        exec("update upload set created = '2020-06-01 10:00:00' where upload_id = 1");
        exec("update geolocale_taxon set specimen_count = 1 where taxon_name = '" + SP_A + "'");
        CountCheck.Result r = new CountCheck(connection).run(places, java.sql.Timestamp.valueOf("2020-05-01 08:00:00"));
        assertEquals(java.sql.Timestamp.valueOf("2020-05-01 08:00:00"), r.countedUntil);
        assertEquals(r.mismatches.toString(), 0, r.real());
        assertTrue(r.pending() >= 1);
    }

    public void testRightCountsGiveNoMismatch() throws Exception {
        CountCheck.Result r = check();
        assertEquals(r.mismatches.toString(), 0, r.mismatches.size());
        assertTrue(r.rowsChecked.get(madagascar) >= 5);
        assertNotNull(r.countedUntil);
    }

    public void testStaleCountsAreReal() throws Exception {
        // Specimens that left the name (e.g. moved to another name) while the stored count stayed.
        exec("update geolocale_taxon set specimen_count = 9 where taxon_name = '" + SP_A + "'");
        exec("update geolocale_taxon set species_count = 5 where taxon_name = '" + GENUS + "'");
        exec("update geolocale_taxon set genus_count = 0 where taxon_name = '" + SUBFAMILY + "'");
        exec("update proj_taxon set specimen_count = 7 where project_name = 'allantwebants' and taxon_name = '" + SP_B + "'");
        exec("update proj_taxon set specimen_count = 1 where project_name = 'madants' and taxon_name = '" + SP_B + "'");
        CountCheck.Result r = check();
        assertEquals(r.mismatches.toString(), 5, r.real());
        assertEquals(0, r.pending());
        boolean found = false;
        for (CountCheck.Mismatch m : r.mismatches) {
            if ("specimens".equals(m.check) && m.place == madagascar && SP_A.equals(m.taxonName)) {
                assertEquals(9, m.stored);
                assertEquals(2, m.recount);
                found = true;
            }
        }
        assertTrue(found);
    }

    /** The nightly recount of project lists (set 3) makes the stored counts match the recount, stale or missing. */
    public void testProjectRecountFixesStaleCounts() throws Exception {
        exec("update proj_taxon set specimen_count = 1, species_count = 9 where project_name = 'madants'");
        exec("insert into taxon values ('myrmicinaepheidole gone', 'species', 'valid', '" + GENUS + "')");
        exec("insert into proj_taxon values ('madants', 'myrmicinaepheidole gone', 0, 0, 0, 12, '2020-01-01 10:00:00')");
        List<CountCheck.Place> places = new ArrayList<>();
        places.add(project);
        assertTrue(new CountCheck(connection).run(places).real() >= 3);
        org.calacademy.antweb.Project p = new org.calacademy.antweb.Project();
        p.setName("madants");
        new org.calacademy.antweb.home.ProjTaxonCountDb(connection).recount(p);
        CountCheck.Result r = new CountCheck(connection).run(places);
        assertEquals(r.mismatches.toString(), 0, r.mismatches.size());
    }

    public void testChangesAfterTheNightlyArePending() throws Exception {
        exec("update geolocale_taxon set specimen_count = 1 where taxon_name = '" + SP_A + "'");
        // casent2 came with an upload after the last nightly count run.
        exec("insert into upload values (2, now())");
        exec("update specimen set upload_id = 2 where code = 'casent2'");
        CountCheck.Result r = check();
        assertTrue(r.uploadSinceNightly);
        assertEquals(r.mismatches.toString(), 0, r.real());
        assertTrue(r.pending() >= 1);
    }

    public void testListRowAddedAfterTheNightlyIsPending() throws Exception {
        // A curator added a species to the list today: its count and its genus's count wait for the nightly.
        exec("insert into taxon values ('myrmicinaepheidole new1', 'species', 'morphotaxon', '" + GENUS + "')");
        exec("insert into geolocale_taxon values (7, 'myrmicinaepheidole new1', 0, 0, 0, 0, now())");
        exec("insert into specimen values ('casent6', 'myrmicinaepheidole new1', 'morphotaxon', 'Madagascar', null, 'Madagascar', null, 'Malagasy', 1)");
        CountCheck.Result r = check();
        assertEquals(r.mismatches.toString(), 0, r.real());
        assertEquals(r.mismatches.toString(), 2, r.pending());
    }
}

package test.org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.Specimen;
import org.calacademy.antweb.upload.SpecimenNameHistory;
import org.calacademy.antweb.upload.SpecimenNameUpdate;
import org.calacademy.antweb.upload.SpecimenNameUpdateDb;

/**
 * The specimen page's Name History query (SpecimenNameHistory.read) against MySQL 8.4: one specimen's
 * applied moves and its shared-name hold, newest first; never the report-only rows (would_change, the
 * Preview) or the kept_valid list; the index on code; and an empty history when the table is missing.
 *
 * Runs in CI (.github/workflows/tests.yaml) like SpecimenNameUpdateDbTest: MySQL 8.4 as host "mysql"
 * (database ant, user antweb); ANTWEB_TEST_DB_URL overrides the URL.
 */
public class SpecimenNameHistoryDbTest extends TestCase {

    static final String CODE = "casent0000001";
    static final String WAS = "myrmicinaewasmannia auropunctata";
    static final String ALLO = "myrmicinaeallomerus auropunctatus";
    static final String OCH = "myrmicinaeochetomyrmex auropunctatus";

    private Connection connection;

    static Connection connect() throws SQLException {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        String user = System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb");
        String password = System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME");
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    protected void setUp() throws Exception {
        connection = connect();
        connection.setAutoCommit(true);
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("drop table if exists specimen_name_update_hold");
            s.executeUpdate(SpecimenNameUpdateDb.createTable(null, null));
            s.executeUpdate("delete from specimen_name_update");
            s.executeUpdate("insert into specimen_name_update (run_date, mode, code, access_group, uploaded_name, old_name, new_name,"
                + " antcat_status, reason, antcat_id) values"
                // applied moves of this specimen, inserted oldest last to check the order is by date
                + " ('2026-10-22 12:31:07', 'changed', '" + CODE + "', 1, '" + OCH + "', '" + WAS + "', '" + ALLO + "', 'obsolete combination',"
                + "   'combination in Allomerus (Longino & Branstetter 2026)', 451279),"
                + " ('2026-10-21 12:30:00', 'changed', '" + CODE + "', 1, '" + OCH + "', '" + OCH + "', '" + WAS + "', 'synonym', null, null),"
                // report-only and spot-check rows: never shown
                + " ('2026-10-23 12:30:00', 'would_change', '" + CODE + "', 1, '" + OCH + "', '" + ALLO + "', '" + OCH + "', 'synonym', 'x', 1),"
                + " ('2026-10-23 12:30:00', 'kept_valid', '" + CODE + "', 1, '" + OCH + "', '" + ALLO + "', '" + OCH + "', 'synonym', null, null),"
                // the latest run held it (shared name)
                + " ('2026-10-23 12:30:00', 'shared_name', '" + CODE + "', 1, '" + OCH + "', '" + ALLO + "', '', 'valid', 'name is shared by two taxa', null),"
                // another specimen
                + " ('2026-10-22 12:31:07', 'changed', 'casent0000002', 1, '" + WAS + "', '" + WAS + "', '" + ALLO + "', 'obsolete combination', 'y', 2)");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    public void testAppliedRowsOfThisSpecimenNewestFirst() {
        List<SpecimenNameHistory.Event> events = SpecimenNameHistory.read(connection, CODE);
        assertEquals(3, events.size());
        assertEquals(SpecimenNameUpdate.SHARED, events.get(0).mode);
        assertEquals(SpecimenNameUpdate.CHANGED, events.get(1).mode);
        assertEquals(WAS, events.get(1).oldName);
        assertEquals(ALLO, events.get(1).newName);
        assertEquals(OCH, events.get(1).uploadedName);
        assertEquals("combination in Allomerus (Longino & Branstetter 2026)", events.get(1).reason);
        assertEquals(Integer.valueOf(451279), events.get(1).antcatId);
        // The day (as the page shows it); the hour may shift with the JVM and server time zones in CI.
        assertEquals("2026-10-22", new java.text.SimpleDateFormat("yyyy-MM-dd").format(events.get(1).runDate));
        assertEquals(OCH, events.get(2).oldName);
        assertNull(events.get(2).antcatId);
        assertNull(events.get(2).reason);
        for (SpecimenNameHistory.Event e : events) {
            assertFalse(SpecimenNameUpdate.WOULD_CHANGE.equals(e.mode));
            assertFalse(SpecimenNameUpdate.KEPT_VALID.equals(e.mode));
        }
        assertTrue(SpecimenNameHistory.read(connection, "casent9999999").isEmpty());
        assertTrue(SpecimenNameHistory.read(connection, null).isEmpty());
    }

    public void testOnlyReportOnlyRowsGiveNoSection() throws Exception {
        exec("delete from specimen_name_update where mode in ('changed', 'shared_name')");
        assertTrue(SpecimenNameHistory.read(connection, CODE).isEmpty());
        Specimen s = specimen(ALLO, null);
        assertEquals("", SpecimenNameHistory.html(connection, s));
    }

    public void testQueryUsesTheCodeIndex() throws Exception {
        boolean indexed = false;
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("show index from specimen_name_update where Key_name = 'by_code'")) {
            while (r.next()) indexed |= "code".equals(r.getString("Column_name")) && r.getInt("Seq_in_index") == 1;
        }
        assertTrue("index by_code (code) in the table's DDL", indexed);
        String explain = SpecimenNameHistory.QUERY.replace("code = ?", "code = '" + CODE + "'");
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("explain " + explain)) {
            assertTrue(r.next());
            assertTrue("possible keys: " + r.getString("possible_keys"), String.valueOf(r.getString("possible_keys")).contains("by_code"));
        }
    }

    public void testPageSectionFromTheDatabase() throws Exception {
        exec("delete from specimen_name_update where mode = 'shared_name'");
        Specimen s = specimen(ALLO, OCH);
        s.setPreviousDeterminations("Wasmannia auropunctata: by Longino on 2010-01-01");
        String html = SpecimenNameHistory.html(connection, s);
        int filed = html.indexOf("Filed under: ");
        int newer = html.indexOf("Name update 2026-10-22: ");
        int older = html.indexOf("Name update 2026-10-21: ");
        int previous = html.indexOf("Previous determination: ");
        assertTrue(html, filed > 0 && newer > filed && older > newer && previous > older);
        assertTrue(html, html.contains("see the name update of 2026-10-22 below"));
        assertTrue(html, html.contains("https://www.antcat.org/catalog/451279"));
        assertFalse(html, html.contains("2026-10-23"));
    }

    public void testMissingTableGivesNoHistory() throws Exception {
        exec("rename table specimen_name_update to specimen_name_update_hold");
        try {
            assertTrue(SpecimenNameHistory.read(connection, CODE).isEmpty());
            assertEquals("", SpecimenNameHistory.html(connection, specimen(ALLO, null)));
        } finally {
            exec("rename table specimen_name_update_hold to specimen_name_update");
        }
    }

    private static Specimen specimen(String filed, String uploaded) {
        Specimen s = new Specimen();
        s.setCode(CODE);
        s.setParentTaxonName(filed);
        s.setOriginalTaxonName(uploaded);
        return s;
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate(sql);
        }
    }
}

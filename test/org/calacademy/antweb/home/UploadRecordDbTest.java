package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.home.SpecimenUploadDb;

/**
 * The upload table row of a specimen upload (SpecimenUploadDb.insertUploadRow, 2026-10-22).
 *
 * Production, 2026-10-21: a curator's background upload (group 57) loaded 9,279 specimens and said
 * "Upload complete", but wrote no upload row, so the upload was missing from the Specimen Upload
 * Reports, the group's last upload and the curator's uploads. The row was written by a statement
 * built from text (group name in quotes) whose SQLException was only logged; the upload's
 * transaction then committed without it. These tests use the production table definition and
 * sql_mode (STRICT_TRANS_TABLES, docker/mysql/mysql.cnf):
 *  - the old statement fails for a group name with a quote or longer than the column (the cause);
 *  - the new insert writes the row for any group name (cut to 100 characters, the width of
 *    upload.group_name since db/upgrade/8.115/2026-10-23, as ant_group.name), in the caller's
 *    transaction, refuses file names longer than the column (links), and throws when it cannot
 *    write the row (the upload then rolls back).
 * The test drops and recreates the table upload, and refuses to run on a database whose upload
 * table looks real.
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like BackgroundJobDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class UploadRecordDbTest extends TestCase {

    private Connection connection;

    // The production table (db/upgrade 4.64, 7.9, 8.56, 8.70, and group_name widened from 64 to 100 by
    // 8.115/2026-10-23-drop-old-tables-widen-group-name.sql), utf8mb3 as in production.
    static final String CREATE = "create table upload ("
        + " id int NOT NULL AUTO_INCREMENT, login_id int NOT NULL, group_name varchar(100) NOT NULL,"
        + " group_id int NOT NULL, log_file_name varchar(64) NOT NULL,"
        + " created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, upload_id int,"
        + " specimens int, collections int, subfamilies int, genera int, species int, ungeoreferenced int,"
        + " flagged int, localities int, backup_dir_file varchar(64), PRIMARY KEY (id)"
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb3";

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        exec("set session sql_mode = 'STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");
        refuseRealUploadTable();
        exec("drop table if exists upload");
        exec(CREATE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                exec("drop table if exists upload");
            } finally {
                connection.close();
            }
        }
    }

    /**
     * This test drops and recreates the table upload. Refuse when ANTWEB_TEST_DB_URL points at a
     * database whose upload table looks real (the application's columns and rows in it): the CI
     * database holds only the small tables the database tests create.
     */
    private void refuseRealUploadTable() throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("select count(*) from information_schema.columns where table_schema = database()"
                 + " and table_name = 'upload' and column_name = 'log_file_name'")) {
            r.next();
            if (r.getInt(1) == 0) return;   // no upload table, or one made by another test
        }
        if (System.getenv("ANTWEB_TEST_DB_URL") != null) {
            fail("refusing to run: ANTWEB_TEST_DB_URL points at a database with an application upload table (this test drops it)");
        }
        if (count("1 = 1") > 0) {
            fail("refusing to run: the database at ANTWEB_TEST_DB_URL has a real upload table with rows (this test drops it)");
        }
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute(sql);
        }
    }

    private int count(String where) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("select count(*) from upload where " + where)) {
            r.next();
            return r.getInt(1);
        }
    }

    private String groupNameOf(int uploadId) throws SQLException {
        try (Statement s = connection.createStatement();
             ResultSet r = s.executeQuery("select group_name from upload where upload_id = " + uploadId)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    /** The statement used until 2026-10-22 (SpecimenUploadDb.updateUpload), built from text. */
    private static String oldInsert(int uploadId, String groupName) {
        return "insert into upload(upload_id, login_id, group_name, group_id, log_file_name, backup_dir_file) "
            + "values (" + uploadId + ", 9, '" + groupName + "', 57, '20261021-11:28:00-specimenUpload.html', 'upload/20261021-11:31:20-specimen57.txt')";
    }

    static final String LONG_NAME = "Universidade Federal de Viçosa - Museu Regional de Entomologia (UFVB) d'Ouro";

    /** Longer than upload.group_name (100) and ant_group.name. */
    static final String TOO_LONG_NAME = LONG_NAME + " - Coleção Entomológica do Departamento de Biologia Animal";

    public void testTheOldStatementLostTheRow() throws Exception {
        // the table as it was until 2026-10-23 (group_name varchar(64))
        exec("drop table upload");
        exec(CREATE.replace("group_name varchar(100)", "group_name varchar(64)"));
        assertTrue(LONG_NAME.length() > 64);
        for (String name : new String[] {"Museu d'Entomologia", "Universidade Federal de Viçosa e Museu Regional de Entomologia UFVB"}) {
            try {
                exec(oldInsert(2254, name));
                fail("the old statement wrote a row for " + name);
            } catch (SQLException expected) {
                // quote: syntax error; too long: "Data too long for column 'group_name'" (strict mode)
            }
        }
        assertEquals(0, count("upload_id = 2254"));
    }

    public void testRowWrittenForAnyGroupName() throws Exception {
        SpecimenUploadDb.insertUploadRow(connection, 2254, 9, 57, "Universidade Federal de Viçosa",
            "20261021-11:28:00-specimenUpload.html", "upload/20261021-11:31:20-specimen57.txt");
        assertEquals(1, count("upload_id = 2254 and group_id = 57 and login_id = 9"
            + " and log_file_name = '20261021-11:28:00-specimenUpload.html'"
            + " and backup_dir_file = 'upload/20261021-11:31:20-specimen57.txt'"));
        assertEquals("Universidade Federal de Viçosa", groupNameOf(2254));

        try {
            SpecimenUploadDb.insertUploadRow(connection, 2257, 9, 58, "G", "2026-" + LONG_NAME + ".html", null);
            fail("a report file name longer than the column was cut instead of refused");
        } catch (SQLException expected) {
            // a cut name would make the report link point to a file that does not exist
        }
        assertEquals(0, count("upload_id = 2257"));

        // a group name longer than 64 (group 57's has 81 characters) is stored whole since the column is 100 wide
        SpecimenUploadDb.insertUploadRow(connection, 2255, 9, 58, LONG_NAME, "x.html", null);
        assertEquals(LONG_NAME, groupNameOf(2255));
        assertEquals(1, count("upload_id = 2255 and backup_dir_file is null"));

        SpecimenUploadDb.insertUploadRow(connection, 2258, 9, 58, TOO_LONG_NAME, "z.html", null);
        assertEquals(TOO_LONG_NAME.substring(0, SpecimenUploadDb.GROUP_NAME_WIDTH), groupNameOf(2258));

        SpecimenUploadDb.insertUploadRow(connection, 2256, 9, 59, "Ants 🐜 of Kenya", "y.html", "upload/y.txt");
        assertEquals("Ants ? of Kenya", groupNameOf(2256));
    }

    public void testRowIsPartOfTheCallersTransaction() throws Exception {
        connection.setAutoCommit(false);
        SpecimenUploadDb.insertUploadRow(connection, 3001, 9, 57, "G", "a.html", "upload/a.txt");
        connection.rollback();
        assertEquals("rolled back with the upload", 0, count("upload_id = 3001"));
        SpecimenUploadDb.insertUploadRow(connection, 3002, 9, 57, "G", "b.html", "upload/b.txt");
        connection.commit();
        assertEquals(1, count("upload_id = 3002"));
    }

    public void testFailureIsThrownNotLogged() throws Exception {
        exec("drop table upload");
        exec("create table upload (id int NOT NULL AUTO_INCREMENT, upload_id int, PRIMARY KEY (id))");
        try {
            SpecimenUploadDb.insertUploadRow(connection, 4001, 9, 57, "G", "c.html", "upload/c.txt");
            fail("no exception although the row could not be written");
        } catch (SQLException expected) {
            // the caller (SpecimenUploadJob, TaxonWorksNightly) rolls the upload back and says so
        }
        try {
            SpecimenUploadDb.insertUploadRow(connection, 0, 9, 57, "G", "c.html", "upload/c.txt");
            fail("an upload id of 0 was accepted");
        } catch (SQLException expected) {
        }
    }

    public void testFit() {
        assertNull(SpecimenUploadDb.fit(null));
        assertEquals("", SpecimenUploadDb.fit(""));
        assertEquals(100, SpecimenUploadDb.GROUP_NAME_WIDTH);
        assertEquals(LONG_NAME, SpecimenUploadDb.fit(LONG_NAME));
        assertTrue(TOO_LONG_NAME.length() > SpecimenUploadDb.GROUP_NAME_WIDTH);
        assertEquals(SpecimenUploadDb.GROUP_NAME_WIDTH, SpecimenUploadDb.fit(TOO_LONG_NAME).length());
        assertEquals("Viçosa", SpecimenUploadDb.fit("Viçosa"));
    }
}

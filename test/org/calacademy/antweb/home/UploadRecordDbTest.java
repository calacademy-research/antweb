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
 *  - the new insert writes the row for any group name (cut to 64 characters), in the caller's
 *    transaction, and throws when it cannot write it (the upload then rolls back).
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like BackgroundJobDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class UploadRecordDbTest extends TestCase {

    private Connection connection;

    // The production table (db/upgrade 4.64, 7.9, 8.56, 8.70), utf8mb3 as in production.
    static final String CREATE = "create table upload ("
        + " id int NOT NULL AUTO_INCREMENT, login_id int NOT NULL, group_name varchar(64) NOT NULL,"
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

    public void testTheOldStatementLostTheRow() throws Exception {
        assertTrue(LONG_NAME.length() > SpecimenUploadDb.UPLOAD_TEXT_WIDTH);
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

        SpecimenUploadDb.insertUploadRow(connection, 2255, 9, 58, LONG_NAME, "x.html", null);
        assertEquals(LONG_NAME.substring(0, SpecimenUploadDb.UPLOAD_TEXT_WIDTH), groupNameOf(2255));
        assertEquals(1, count("upload_id = 2255 and backup_dir_file is null"));

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
        assertEquals(SpecimenUploadDb.UPLOAD_TEXT_WIDTH, SpecimenUploadDb.fit(LONG_NAME).length());
        assertEquals("Viçosa", SpecimenUploadDb.fit("Viçosa"));
    }
}

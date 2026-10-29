/*
 * LicenseUpdateDbTest: the start-up update of stored licence texts to CC BY 4.0 (Brian Fisher, 2026-10-29), against
 * MySQL 8.4 in CI (host "mysql"; ANTWEB_TEST_DB_URL overrides).
 */
package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.home.LicenseUpdateDb;
import org.calacademy.antweb.util.License;

import test.org.calacademy.antweb.util.NumbersTestDb;

/**
 * The first run backs up the old texts and sets every stored text to License.LINE; a second run changes nothing and
 * leaves the backups as they were; a missing license table is skipped. Last in its CI step: it makes its own small
 * image_upload and license tables.
 */
public class LicenseUpdateDbTest extends TestCase {

    static final String OLD = "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free Documentation License (GFDL)";

    private Connection c;

    @Override
    protected void setUp() throws Exception {
        c = NumbersTestDb.connect();
        for (String t : new String[] {"image_upload", "license", LicenseUpdateDb.UPLOAD_BACKUP, LicenseUpdateDb.LICENSE_BACKUP}) {
            exec("drop table if exists " + t);
        }
        exec("create table image_upload (id int primary key, license mediumtext)");
        exec("create table license (id int primary key, license text)");
        exec("insert into image_upload values (1, '" + OLD + "'), (2, '" + OLD + "'), (3, null), (4, '" + License.LINE + "')");
        exec("insert into license values (1, 'old licence text')");
    }

    @Override
    protected void tearDown() throws Exception {
        if (c != null) c.close();
    }

    private void exec(String sql) throws Exception {
        NumbersTestDb.exec(c, sql);
    }

    private String one(String sql) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            assertTrue(sql, rs.next());
            return rs.getString(1);
        }
    }

    public void testFirstRunBacksUpThenUpdates() throws Exception {
        LicenseUpdateDb.Result r = LicenseUpdateDb.run(c);
        assertEquals(2, r.uploads);
        assertEquals(1, r.licenses);
        assertEquals("3", one("select count(*) from image_upload where license = '" + License.LINE + "'"));
        assertNull(one("select license from image_upload where id = 3"));
        assertEquals("1", one("select count(*) from license where license = '" + License.LINE + "'"));
        assertEquals("2", one("select count(*) from " + LicenseUpdateDb.UPLOAD_BACKUP));
        assertEquals(OLD, one("select license from " + LicenseUpdateDb.UPLOAD_BACKUP + " where id = 1"));
        assertEquals("old licence text", one("select license from " + LicenseUpdateDb.LICENSE_BACKUP + " where id = 1"));
    }

    public void testSecondRunChangesNothing() throws Exception {
        LicenseUpdateDb.run(c);
        LicenseUpdateDb.Result r = LicenseUpdateDb.run(c);
        assertEquals(0, r.uploads);
        assertEquals(0, r.licenses);
        assertEquals("2", one("select count(*) from " + LicenseUpdateDb.UPLOAD_BACKUP));
        assertEquals("1", one("select count(*) from " + LicenseUpdateDb.LICENSE_BACKUP));
        assertEquals("old licence text", one("select license from " + LicenseUpdateDb.LICENSE_BACKUP + " where id = 1"));
        // The rollback of db/upgrade/8.115/2026-10-29-license.sql restores the old texts.
        exec("update image_upload u join " + LicenseUpdateDb.UPLOAD_BACKUP + " b on b.id = u.id set u.license = b.license");
        assertEquals("2", one("select count(*) from image_upload where license = '" + OLD + "'"));
    }

    public void testMissingLicenseTableIsSkipped() throws Exception {
        exec("drop table license");
        LicenseUpdateDb.Result r = LicenseUpdateDb.run(c);
        assertEquals(2, r.uploads);
        assertEquals(0, r.licenses);
        assertEquals("0", one("select count(*) from information_schema.tables where table_schema = database()"
            + " and table_name = '" + LicenseUpdateDb.LICENSE_BACKUP + "'"));
    }
}

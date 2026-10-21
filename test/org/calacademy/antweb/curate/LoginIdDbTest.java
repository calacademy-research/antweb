package test.org.calacademy.antweb.curate;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.home.PasswordResetDb;

/**
 * Login numbers and login.created (2026-10-21), against MySQL 8.4.
 *
 * 1. A new login never gets a number used before (LoginDb.nextLoginId): not a deleted login's
 *    number (its curator folder web/curator/N/ may hold images linked from page text), not a
 *    number found in other tables (page text authors, uploads, likes, backups of login rows).
 * 2. Password reset steps (PasswordResetDb) keep login.created, which is ON UPDATE
 *    CURRENT_TIMESTAMP in production.
 * 3. db/upgrade/8.113/2026-10-21-login-created.sql removes the ON UPDATE, keeps NULL/NOT NULL,
 *    and can run twice.
 *
 * Runs in CI (host "mysql" unless ANTWEB_TEST_DB_URL is set). The upgrade script is read from
 * -Dantweb.db (default /d, the repository's db folder).
 */
public class LoginIdDbTest extends TestCase {

    private static final String[] EXTRA = {"login_inactive_backup_20261018", "description_edit", "image_like", "upload",
        "ant_group", "login_project", "login_country", "password_reset", "remember_token", "notes_login"};

    private Connection c;
    private final List<File> tmp = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        exec("drop table if exists login");
        for (String t : EXTRA) exec("drop table if exists " + t);
        // Tables of other tests in this database that hold login numbers (login_country, ...).
        List<String> others = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("select distinct table_name from information_schema.columns"
                + " where table_schema = database() and (column_name in ('login_id', 'access_login', 'curator_id', 'admin_login_id')"
                + " or (column_name = 'id' and table_name like '%login%'))")) {
            while (r.next()) others.add(r.getString(1));
        }
        for (String t : others) exec("drop table if exists `" + t + "`");
        exec("create table login (id int NOT NULL, name varchar(32) NOT NULL, first_name varchar(64) DEFAULT NULL,"
            + " last_name varchar(64) NOT NULL DEFAULT '', email varchar(128) NOT NULL DEFAULT '', password varchar(255) NOT NULL DEFAULT '',"
            + " group_id int DEFAULT NULL, is_admin tinyint DEFAULT 0, is_upload_specimens tinyint DEFAULT 0, is_upload_images tinyint DEFAULT 0,"
            + " upload_as varchar(100) DEFAULT NULL, created timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + " last_login timestamp NULL DEFAULT NULL, must_reset tinyint NOT NULL DEFAULT 0, session_version int NOT NULL DEFAULT 0,"
            + " inactive tinyint NOT NULL DEFAULT 0, inactive_since datetime NULL DEFAULT NULL, PRIMARY KEY (id))");
        exec("insert into login (id, name, email, password, group_id, created) values"
            + " (2, 'bfisher', 'b@example.org', 'x', 1, '2001-02-03 04:05:06'),"
            + " (5, 'jdoe', 'jane@example.org', 'x', 71, '2012-01-01 00:00:00'),"
            + " (9, 'ward', 'ward@example.org', 'x', 1, '2003-03-03 03:03:03')");
    }

    @Override
    protected void tearDown() throws Exception {
        // Leave only the login table, as the other database tests of this step expect.
        if (c != null) {
            for (String t : EXTRA) exec("drop table if exists " + t);
            c.close();
        }
        for (File f : tmp) deleteTree(f);
    }

    private void exec(String sql) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private String one(String q) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private File curatorDir(String... folders) throws Exception {
        File d = Files.createTempDirectory("curator").toFile();
        tmp.add(d);
        for (String f : folders) {
            File x = new File(d, f);
            if (f.endsWith(".jpg")) Files.write(x.toPath(), new byte[] {1});
            else assertTrue(x.mkdir());
        }
        return d;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    public void testOnlyLoginTable() throws Exception {
        assertEquals(10, LoginDb.nextLoginId(c, null));
        assertEquals(10, LoginDb.nextLoginId(c, new File("/nonexistent/curator")));
    }

    public void testDeletedLoginNumberIsNotReused() throws Exception {
        File dir = curatorDir("2", "5", "9");
        exec("create table login_project (login_id int NOT NULL, project_name varchar(64) NOT NULL)");
        exec("create table login_country (login_id int NOT NULL, country varchar(64) NOT NULL)");
        exec("insert into login_project values (9, 'fijiants')");
        new LoginDb(c).deleteById(9);
        assertNull(one("select id from login where id = 9"));
        // Before: max(id) + 1 = 6, the next login would have been given folder 9 later on.
        assertEquals(10, LoginDb.nextLoginId(c, dir));
        assertFalse(new File(dir, "10").exists());
    }

    public void testCuratorFoldersCount() throws Exception {
        File dir = curatorDir("3", "57", "notes", "123abc", "999.jpg");
        assertEquals(58, LoginDb.nextLoginId(c, dir));
        assertEquals(57, LoginDb.maxCuratorFolder(dir));
        assertEquals(0, LoginDb.maxCuratorFolder(null));
    }

    public void testLoginNumbersInOtherTables() throws Exception {
        exec("create table login_inactive_backup_20261018 (id int, name varchar(32))");
        exec("insert into login_inactive_backup_20261018 values (40, 'gone')");
        assertEquals(41, LoginDb.nextLoginId(c, null));

        exec("create table description_edit (taxon_name varchar(128), title varchar(32), access_login int)");
        exec("insert into description_edit values ('a', 'notes', 77)");
        exec("create table image_like (image_id int, login_id int)");
        exec("insert into image_like values (1, 12)");
        exec("create table upload (upload_id int, login_id int)");
        exec("insert into upload values (1, null)");
        exec("create table ant_group (id int, admin_login_id int)");
        exec("insert into ant_group values (500, 6)");
        // A text column with a login-like name and no number in it is not a login number.
        exec("create table notes_login (id varchar(20))");
        exec("insert into notes_login values ('abc')");
        assertEquals(78, LoginDb.nextLoginId(c, curatorDir("70")));
        // The group number (ant_group.id, 500) is not a login number.
        exec("delete from description_edit");
        assertEquals(71, LoginDb.nextLoginId(c, curatorDir("70")));
    }

    public void testGetNewLoginIdUsesTheSameRule() throws Exception {
        exec("create table login_inactive_backup_20261018 (id int)");
        exec("insert into login_inactive_backup_20261018 values (30)");
        int id = new LoginDb(c).getNewLoginId();
        assertTrue("" + id, id >= 31);
    }

    public void testPasswordResetKeepsCreated() throws Exception {
        PasswordResetDb db = new PasswordResetDb(c);
        db.ensureSchema();
        String created = one("select date_format(created, '%Y-%m-%d %H:%i:%s') from login where id = 5");
        assertEquals("2012-01-01 00:00:00", created);
        Thread.sleep(1100);   // a changed created would be at least a second later
        db.setNewPassword(5, "A-long-new-password-1");
        db.afterPasswordChange(5);
        db.bumpSessionVersion(5);
        db.markMustReset(5);
        assertEquals(created, one("select date_format(created, '%Y-%m-%d %H:%i:%s') from login where id = 5"));
        assertEquals("4", one("select session_version from login where id = 5"));
        assertEquals("1", one("select must_reset from login where id = 5"));
    }

    private void runUpgradeScript() throws Exception {
        File f = new File(System.getProperty("antweb.db", "/d"), "upgrade/8.113/2026-10-21-login-created.sql");
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(f.toPath())) {
            if (line.trim().startsWith("--")) continue;
            sql.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                String st = sql.toString().trim();
                exec(st.substring(0, st.length() - 1));
                sql.setLength(0);
            }
        }
        assertEquals("", sql.toString().trim());
    }

    private String createdColumn() throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("show create table login")) {
            r.next();
            for (String line : r.getString(2).split("\n")) if (line.contains("`created`")) return line.trim().toLowerCase();
        }
        return null;
    }

    public void testUpgradeScriptRemovesOnUpdate() throws Exception {
        assertTrue(createdColumn(), createdColumn().contains("on update"));
        runUpgradeScript();
        String col = createdColumn();
        assertFalse(col, col.contains("on update"));
        assertTrue(col, col.contains("default current_timestamp"));
        assertFalse(col, col.contains("not null"));
        runUpgradeScript();   // a second run changes nothing
        assertEquals(col, createdColumn());
        // An update that does not say "created = created" keeps the date now.
        exec("update login set first_name = 'Jane' where id = 5");
        assertEquals("2012-01-01 00:00:00", one("select date_format(created, '%Y-%m-%d %H:%i:%s') from login where id = 5"));
        // A new row still gets the time it was made.
        exec("insert into login (id, name) values (11, 'new')");
        assertNotNull(one("select created from login where id = 11"));
    }

    public void testUpgradeScriptKeepsNotNull() throws Exception {
        exec("alter table login modify created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        runUpgradeScript();
        String col = createdColumn();
        assertFalse(col, col.contains("on update"));
        assertTrue(col, col.contains("not null"));
    }
}

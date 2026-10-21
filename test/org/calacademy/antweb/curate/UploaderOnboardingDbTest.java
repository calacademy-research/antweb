package test.org.calacademy.antweb.curate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.group.UploaderOnboarding;
import org.calacademy.antweb.curate.group.UploaderOnboarding.Request;
import org.calacademy.antweb.curate.group.UploaderOnboarding.Result;
import org.calacademy.antweb.home.GroupDb;

/**
 * "Add an uploader" against MySQL 8.4 (UploaderOnboarding.apply): a new person and a new
 * upload group; an existing account found by email or username (case does not matter); a
 * login in another group is moved only when asked; duplicates, unknown usernames and two
 * matching logins change nothing; login.created is kept; inactive accounts get a link; a
 * group with logins or specimens is not deleted (GroupDb.whyNotDeletable).
 *
 * The tables are made here with the columns the code uses (login.created ON UPDATE
 * CURRENT_TIMESTAMP and login.name varchar(32), as in production).
 * Runs in CI against MySQL 8.4 (host "mysql"); ANTWEB_TEST_DB_URL overrides.
 */
public class UploaderOnboardingDbTest extends TestCase {

    private Connection c;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            s.executeUpdate("drop table if exists login");
            s.executeUpdate("drop table if exists ant_group");
            s.executeUpdate("drop table if exists specimen");
            s.executeUpdate("create table login (id int NOT NULL, name varchar(32) NOT NULL, first_name varchar(64) DEFAULT NULL,"
                + " last_name varchar(64) NOT NULL DEFAULT '', email varchar(128) NOT NULL DEFAULT '', password varchar(255) NOT NULL DEFAULT '',"
                + " group_id int DEFAULT NULL, is_admin tinyint DEFAULT 0, is_upload_specimens tinyint DEFAULT 0, is_upload_images tinyint DEFAULT 0,"
                + " upload_as varchar(100) DEFAULT NULL, created timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " last_login timestamp NULL DEFAULT NULL, inactive tinyint NOT NULL DEFAULT 0, PRIMARY KEY (id))");
            s.executeUpdate("create table ant_group (id int NOT NULL, name varchar(100) DEFAULT NULL, admin_login_id int DEFAULT NULL,"
                + " abbrev varchar(20) DEFAULT NULL, upload_specimens int DEFAULT NULL, PRIMARY KEY (id))");
            s.executeUpdate("create table specimen (code varchar(40) NOT NULL, access_group int, PRIMARY KEY (code))");
            s.executeUpdate("insert into ant_group (id, name, admin_login_id, abbrev) values (0, 'Default Group', 0, 'Default'),"
                + " (1, 'California Academy of Sciences', 2, 'CAS'), (62, 'Economo Ant Lab', 0, 'OIST')");
            s.executeUpdate("insert into login (id, name, first_name, last_name, email, password, group_id, is_upload_specimens, is_upload_images, created) values"
                + " (2, 'bfisher', 'Brian', 'Fisher', 'bfisher@example.org', 'pbkdf2_sha256$1$x$y', 1, 1, 1, '2001-02-03 04:05:06'),"
                + " (5, 'jdoe', 'Jane', 'Doe', 'Jane.Doe@Museum-X.org', 'pbkdf2_sha256$1$x$y', -1, 0, 0, '2012-01-01 00:00:00'),"
                + " (6, 'kiko@lab.org', '', '', '', 'pbkdf2_sha256$1$x$y', -1, 0, 0, '2015-05-05 05:05:05'),"
                + " (9, 'ward', 'Phil', 'Ward', 'ward@example.org', 'pbkdf2_sha256$1$x$y', 1, 1, 0, '2003-03-03 03:03:03')");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (c != null) c.close();
    }

    private static Request req(String email, int groupId, String name, String abbrev) {
        Request r = new Request();
        r.email = email;
        r.groupId = groupId;
        r.newGroupName = name;
        r.newGroupAbbrev = abbrev;
        return r;
    }

    private String one(String q) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private int count(String table) throws Exception {
        return Integer.parseInt(one("select count(*) from " + table));
    }

    public void testNewPersonNewGroup() throws Exception {
        Request r = req("new.person@museum-x.org", 0, "Museum X", "MX");
        r.firstName = "New";
        r.lastName = "Person";
        Result res = UploaderOnboarding.apply(c, r);
        assertTrue(res.problems.toString(), res.ok());
        assertTrue(res.newLogin);
        assertTrue(res.newGroup);
        assertTrue(res.needsPasswordLink);
        assertEquals(63, res.group.id);
        assertEquals(10, res.login.id);
        assertEquals("new.person@museum-x.org", res.login.name);
        assertEquals("Museum X|MX|10", one("select concat(name, '|', abbrev, '|', admin_login_id) from ant_group where id = 63"));
        assertEquals("new.person@museum-x.org|new.person@museum-x.org||63|1|0|0|New Person",
            one("select concat(name, '|', email, '|', password, '|', group_id, '|', is_upload_specimens, '|', is_upload_images, '|',"
                + " is_admin, '|', first_name, ' ', last_name) from login where id = 10"));
        assertEquals(1, res.group.uploaders);
        assertTrue(c.getAutoCommit());
    }

    public void testExistingLoginByEmailAnyCase() throws Exception {
        Request r = req("jane.doe@museum-x.ORG", 62, "", "");
        r.uploadImages = true;
        Result res = UploaderOnboarding.apply(c, r);
        assertTrue(res.problems.toString(), res.ok());
        assertFalse(res.newLogin);
        assertFalse(res.newGroup);
        assertFalse("has a password", res.needsPasswordLink);
        assertEquals("62|1|1|Jane.Doe@Museum-X.org|2012-01-01 00:00:00",
            one("select concat(group_id, '|', is_upload_specimens, '|', is_upload_images, '|', email, '|', created) from login where id = 5"));
        assertEquals("group had no admin curator", "5", one("select admin_login_id from ant_group where id = 62"));
        assertEquals(1, count("ant_group where id = 62"));
    }

    public void testGroupAdminKept() throws Exception {
        Result res = UploaderOnboarding.apply(c, req("jdoe", 1, "", ""));
        assertTrue(res.problems.toString(), res.ok());
        assertEquals("2", one("select admin_login_id from ant_group where id = 1"));
        assertEquals("0", one("select is_upload_images from login where id = 5"));
    }

    public void testSelfRegisteredAccountGetsEmail() throws Exception {
        Request r = req("Kiko@lab.org", 62, "", "");
        r.firstName = "Kiko";
        r.lastName = "Gomez";
        Result res = UploaderOnboarding.apply(c, r);
        assertTrue(res.problems.toString(), res.ok());
        assertEquals("Kiko@lab.org|62|Kiko Gomez|2015-05-05 05:05:05",
            one("select concat(email, '|', group_id, '|', first_name, ' ', last_name, '|', created) from login where id = 6"));
        assertTrue(String.join(" ", res.done).contains("Stored the email address"));
    }

    public void testOtherGroupOnlyWhenMoved() throws Exception {
        Result res = UploaderOnboarding.apply(c, req("ward@example.org", 62, "", ""));
        assertFalse(res.ok());
        assertTrue(res.problems.get(0).contains("California Academy of Sciences"));
        assertEquals("1", one("select group_id from login where id = 9"));

        Result asNew = UploaderOnboarding.apply(c, req("ward@example.org", 0, "Ward Lab", "Ward"));
        assertFalse(asNew.ok());
        assertEquals("no group made when refused", 3, count("ant_group"));

        Request move = req("ward@example.org", 62, "", "");
        move.move = true;
        res = UploaderOnboarding.apply(c, move);
        assertTrue(res.problems.toString(), res.ok());
        assertEquals("62", one("select group_id from login where id = 9"));
    }

    public void testSameGroupAgainIsFine() throws Exception {
        Result res = UploaderOnboarding.apply(c, req("bfisher", 1, "", ""));
        assertTrue(res.problems.toString(), res.ok());
        assertTrue(String.join(" ", res.done).contains("already in this upload group"));
    }

    public void testRefusalsChangeNothing() throws Exception {
        String before = one("select group_concat(concat(id, name, group_id, is_upload_specimens) order by id) from login");
        assertFalse("group name taken (any case)", UploaderOnboarding.apply(c, req("x@y.org", 0, "economo ant lab", "EAL")).ok());
        assertFalse("short name taken", UploaderOnboarding.apply(c, req("x@y.org", 0, "Another Lab", "cas")).ok());
        assertFalse("unknown username", UploaderOnboarding.apply(c, req("nobody", 62, "", "")).ok());
        assertFalse("no such group", UploaderOnboarding.apply(c, req("x@y.org", 999, "", "")).ok());
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into login (id, name, email) values (11, 'twin@x.org', ''), (12, 'twin2', 'TWIN@x.org')");
        }
        Result twins = UploaderOnboarding.apply(c, req("twin@x.org", 62, "", ""));
        assertFalse(twins.ok());
        assertTrue(twins.problems.get(0).contains("more than one login"));
        assertEquals(3, count("ant_group"));
        assertEquals(before, one("select group_concat(concat(id, name, group_id, is_upload_specimens) order by id) from login where id < 11"));
    }

    public void testLongEmailGetsShortUsername() throws Exception {
        Result res = UploaderOnboarding.apply(c, req("someone.with.a.very.long.address@museum-x.org", 62, "", ""));
        assertTrue(res.problems.toString(), res.ok());
        assertEquals("the part before @, cut to 29 characters", "someone.with.a.very.long.addr", res.login.name);
        assertEquals("someone.with.a.very.long.address@museum-x.org", one("select email from login where id = " + res.login.id));
    }

    public void testInactiveAccountNeedsLink() throws Exception {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("update login set inactive = 1 where id = 5");
        }
        Result res = UploaderOnboarding.apply(c, req("jdoe", 62, "", ""));
        assertTrue(res.problems.toString(), res.ok());
        assertTrue(res.needsPasswordLink);
    }

    public void testGroupWithLoginsOrSpecimensIsNotDeletable() throws Exception {
        assertNull(GroupDb.whyNotDeletable(c, 62));
        assertTrue(GroupDb.whyNotDeletable(c, 1).contains("2 logins and 0 specimens"));
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into specimen (code, access_group) values ('mx0001', 62)");
        }
        assertTrue(GroupDb.whyNotDeletable(c, 62).contains("0 logins and 1 specimen."));
    }
}

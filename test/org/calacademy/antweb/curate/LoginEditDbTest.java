package test.org.calacademy.antweb.curate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.login.LoginEdit;
import org.calacademy.antweb.curate.login.LoginEdit.Change;
import org.calacademy.antweb.curate.login.LoginEdit.Choice;
import org.calacademy.antweb.curate.login.LoginEdit.Result;
import org.calacademy.antweb.curate.login.LoginEdit.State;
import org.calacademy.antweb.curate.login.LoginEditPage;
import org.calacademy.antweb.home.LoginDb;

/**
 * An administrator saves the login form, against MySQL 8.4 (LoginEdit.apply): rights, upload
 * group, project and country rows and "Upload as" are saved in one step and summarised; any
 * refused value changes nothing; your own administrator rights cannot be removed; projects that
 * are no longer offered stay unless removed; login.created is kept; the page's dates and
 * "Upload as" names are read; deleting a login removes its assignment rows (LoginDb.deleteById).
 *
 * The tables are made here with the columns the code uses. Runs in CI (host "mysql" unless
 * ANTWEB_TEST_DB_URL is set).
 */
public class LoginEditDbTest extends TestCase {

    private static final List<Choice> PROJECTS = Arrays.asList(
        new Choice("madagascarants", "Madagascar (madagascarants)"), new Choice("fijiants", "Fiji (fijiants)"));
    private static final List<Choice> COUNTRIES = Arrays.asList(new Choice("Kenya", "Kenya"), new Choice("Madagascar", "Madagascar"));

    private Connection c;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        try (Statement s = c.createStatement()) {
            for (String t : new String[] {"login", "ant_group", "login_project", "login_country"}) s.executeUpdate("drop table if exists " + t);
            s.executeUpdate("create table login (id int NOT NULL, name varchar(32) NOT NULL, first_name varchar(64) DEFAULT NULL,"
                + " last_name varchar(64) NOT NULL DEFAULT '', email varchar(128) NOT NULL DEFAULT '', password varchar(255) NOT NULL DEFAULT '',"
                + " group_id int DEFAULT NULL, is_admin tinyint DEFAULT 0, is_upload_specimens tinyint DEFAULT 0, is_upload_images tinyint DEFAULT 0,"
                + " upload_as varchar(100) DEFAULT NULL, created timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + " last_login timestamp NULL DEFAULT NULL, inactive tinyint NOT NULL DEFAULT 0, inactive_since datetime NULL DEFAULT NULL, PRIMARY KEY (id))");
            s.executeUpdate("create table ant_group (id int NOT NULL, name varchar(100) DEFAULT NULL, admin_login_id int DEFAULT NULL,"
                + " abbrev varchar(20) DEFAULT NULL, PRIMARY KEY (id))");
            s.executeUpdate("create table login_project (login_id int NOT NULL, project_name varchar(64) NOT NULL)");
            s.executeUpdate("create table login_country (login_id int NOT NULL, country varchar(64) NOT NULL)");
            s.executeUpdate("insert into ant_group (id, name, admin_login_id, abbrev) values (1, 'California Academy of Sciences', 2, 'CAS'),"
                + " (71, 'Museum X', 5, 'MX')");
            s.executeUpdate("insert into login (id, name, first_name, last_name, email, password, group_id, is_admin, is_upload_specimens,"
                + " is_upload_images, upload_as, created, last_login, inactive, inactive_since) values"
                + " (2, 'bfisher', 'Brian', 'Fisher', 'bfisher@example.org', 'pbkdf2_sha256$1$x$y', 1, 1, 1, 1, '5,9', '2001-02-03 04:05:06', '2026-10-01 08:00:00', 0, null),"
                + " (5, 'jdoe', 'Jane', 'Doe', 'jane@museum-x.org', 'pbkdf2_sha256$1$x$y', 71, 0, 1, 0, null, '2012-01-01 00:00:00', null, 0, null),"
                + " (6, 'kiko', 'Kiko', 'Gomez', '', '', -1, 0, 0, 0, null, '2015-05-05 05:05:05', null, 1, '2026-10-18 10:00:00'),"
                + " (9, 'ward', 'Phil', 'Ward', 'ward@example.org', 'pbkdf2_sha256$1$x$y', 1, 0, 1, 0, null, '2003-03-03 03:03:03', null, 0, null)");
            s.executeUpdate("insert into login_project values (5, 'madagascarants'), (5, 'oldprojectants'), (9, 'fijiants')");
            s.executeUpdate("insert into login_country values (5, 'Kenya'), (9, 'Kenya')");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (c != null) c.close();
    }

    private String one(String q) throws Exception {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private String rows(String q) throws Exception {
        StringBuilder b = new StringBuilder();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(q)) {
            int n = r.getMetaData().getColumnCount();
            while (r.next()) {
                for (int i = 1; i <= n; i++) b.append(r.getString(i)).append(i < n ? "|" : "\n");
            }
        }
        return b.toString();
    }

    private String snapshot() throws Exception {
        return rows("select * from login order by id") + rows("select * from login_project order by login_id, project_name")
            + rows("select * from login_country order by login_id, country");
    }

    private Change unchanged(int id) throws Exception {
        State s = LoginEdit.read(c, id);
        Change ch = new Change();
        ch.id = id;
        ch.name = s.name;
        ch.email = s.email;
        ch.firstName = s.firstName;
        ch.lastName = s.lastName;
        ch.isAdmin = s.isAdmin;
        ch.uploadSpecimens = s.uploadSpecimens;
        ch.uploadImages = s.uploadImages;
        ch.group = s.groupId == LoginEdit.NO_GROUP ? "none" : "" + s.groupId;
        ch.uploadAs = s.uploadAs;
        return ch;
    }

    private Result apply(Change ch, int by) throws Exception {
        return LoginEdit.apply(c, ch, by, true, PROJECTS, COUNTRIES, LoginEdit.labels(PROJECTS));
    }

    public void testRead() throws Exception {
        State s = LoginEdit.read(c, 5);
        assertEquals("jdoe", s.name);
        assertEquals(71, s.groupId);
        assertTrue(s.uploadSpecimens && !s.uploadImages && !s.isAdmin && s.hasPassword);
        assertEquals("[madagascarants, oldprojectants]", s.projects.toString());
        assertEquals("[Kenya]", s.countries.toString());
        assertFalse(LoginEdit.read(c, 6).hasPassword);
        assertNull(LoginEdit.read(c, 404));
        assertEquals("California Academy of Sciences (CAS) — group 1", LoginEdit.groups(c).get(0).label());
    }

    public void testSaveAndSummary() throws Exception {
        Change ch = unchanged(5);
        ch.uploadImages = true;
        ch.group = "1";
        ch.removeProjects = new String[] {"madagascarants"};
        ch.addProjects = new String[] {"Fiji (fijiants)"};
        ch.addCountries = new String[] {"madagascar"};
        ch.email = "jane.doe@museum-x.org";
        ch.uploadAs = "9";
        Result r = apply(ch, 2);
        assertTrue(r.problems.toString(), r.problems.isEmpty());
        assertTrue(r.saved);
        assertEquals(Arrays.asList(
            "Email: \"jane@museum-x.org\" changed to \"jane.doe@museum-x.org\"",
            "Upload specimen images: added",
            "Upload group: California Academy of Sciences (CAS) — group 1 (was: Museum X (MX) — group 71)",
            "Project Fiji (fijiants): added",
            "Project Madagascar (madagascarants): removed",
            "Country Madagascar: added",
            "Upload as: (empty) changed to \"9\""), r.changes);
        assertEquals("1|1|1|jane.doe@museum-x.org|9", rows("select group_id, is_upload_specimens, is_upload_images, email, upload_as from login where id = 5").trim());
        assertEquals("fijiants\noldprojectants", rows("select project_name from login_project where login_id = 5 order by 1").trim());
        assertEquals("Kenya\nMadagascar", rows("select country from login_country where login_id = 5 order by 1").trim());
        assertEquals("created is kept", "2012-01-01 00:00:00", one("select date_format(created, '%Y-%m-%d %H:%i:%s') from login where id = 5"));
        assertEquals("other logins untouched", "fijiants", one("select group_concat(project_name) from login_project where login_id = 9"));

        // Saving the same values again changes nothing.
        r = apply(unchanged(5), 2);
        assertTrue(r.problems.isEmpty());
        assertTrue(r.changes.isEmpty());
        assertFalse(r.saved);
    }

    public void testRefusedChangesNothing() throws Exception {
        String before = snapshot();
        Change ch = unchanged(5);
        ch.uploadImages = true;
        ch.addProjects = new String[] {"Atlantis"};
        assertTrue(apply(ch, 2).problems.get(0).contains("No live project is called \"Atlantis\""));

        ch = unchanged(5);
        ch.group = "999";
        assertTrue(apply(ch, 2).problems.get(0).contains("Upload group 999 does not exist"));

        ch = unchanged(5);
        ch.name = "WARD";
        assertTrue(apply(ch, 2).problems.get(0).contains("Another login already has the username \"WARD\""));

        ch = unchanged(5);
        ch.email = "Ward@Example.org";
        assertTrue(apply(ch, 2).problems.get(0).contains("Another login already has the email address"));

        ch = unchanged(5);
        ch.uploadAs = "9,404";
        assertTrue(apply(ch, 2).problems.get(0).contains("there is no login number 404"));

        ch = unchanged(2);
        ch.isAdmin = false;
        ch.uploadImages = false;
        Result r = apply(ch, 2);
        assertTrue(r.problems.get(0).contains("cannot remove your own administrator rights"));

        r = LoginEdit.apply(c, unchanged(5), 9, false, PROJECTS, COUNTRIES, null);
        assertEquals(Arrays.asList("Only administrators can change logins."), r.problems);

        Change missing = unchanged(5);
        missing.id = 404;
        assertTrue(apply(missing, 2).problems.get(0).contains("does not exist"));
        assertEquals("nothing was written", before, snapshot());
    }

    public void testAdministratorsAndOldAssignments() throws Exception {
        // Another administrator may remove administrator rights; an admin may add their own projects.
        Change ch = unchanged(2);
        ch.addCountries = new String[] {"Kenya"};
        Result r = apply(ch, 2);
        assertEquals(Arrays.asList("Country Kenya: added"), r.changes);

        ch = unchanged(2);
        ch.isAdmin = false;
        r = apply(ch, 9);
        assertEquals(Arrays.asList("Administrator: removed"), r.changes);
        assertEquals("0", one("select is_admin from login where id = 2"));

        // "oldprojectants" is not offered any more: it stays until it is removed.
        ch = unchanged(5);
        ch.group = "none";
        r = apply(ch, 2);
        assertEquals(Arrays.asList("Upload group: No upload group (was: Museum X (MX) — group 71)"), r.changes);
        assertEquals("-1", one("select group_id from login where id = 5"));
        assertEquals("madagascarants\noldprojectants", rows("select project_name from login_project where login_id = 5 order by 1").trim());
        ch = unchanged(5);
        ch.removeProjects = new String[] {"oldprojectants"};
        assertEquals(Arrays.asList("Project oldprojectants: removed"), apply(ch, 2).changes);
    }

    public void testPageData() throws Exception {
        LoginEditPage.PageData d = LoginEditPage.read(c, 2, false, PROJECTS, COUNTRIES, LoginEdit.labels(PROJECTS));
        assertEquals("2001-02-03 04:05", d.created);
        assertEquals("2026-10-01 08:00", d.lastLogin);
        assertFalse(d.inactive);
        assertEquals("jdoe (Museum X)", d.uploadAsNames.get(5));
        assertEquals("ward (California Academy of Sciences)", d.uploadAsNames.get(9));
        d = LoginEditPage.read(c, 6, false, PROJECTS, COUNTRIES, null);
        assertTrue(d.inactive);
        assertEquals("2026-10-18 10:00", d.inactiveSince);
        String html = LoginEditPage.renderAdmin(d, 2, "", "", "");
        assertTrue(html.contains("Inactive</span> since 2026-10-18 10:00"));
        assertTrue(html.contains("not set yet"));
        d = LoginEditPage.read(c, 9001, true, PROJECTS, COUNTRIES, null);
        assertEquals(9001, d.state.id);
        assertEquals(2, d.groups.size());
    }

    public void testDeleteRemovesAssignments() throws Exception {
        new LoginDb(c).deleteById(5);
        assertNull(one("select id from login where id = 5"));
        assertEquals("0", one("select count(*) from login_project where login_id = 5"));
        assertEquals("0", one("select count(*) from login_country where login_id = 5"));
        assertEquals("1", one("select count(*) from login_project where login_id = 9"));
    }
}

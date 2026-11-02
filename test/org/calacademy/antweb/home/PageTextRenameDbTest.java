package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import junit.framework.TestCase;

import org.calacademy.antweb.curate.geolocale.EditGeolocaleForm;
import org.calacademy.antweb.home.GeolocaleDb;
import org.calacademy.antweb.home.PageTextKeys;

/**
 * Renaming a place keeps its page text (2026-10-21). Page text of countries, adm1, regions and
 * subregions is in object_edit, its history in object_hist, keyed by the place's name. Saving the
 * editGeolocale.do form with a new name (GeolocaleDb.updateGeolocale) moves both, in the same
 * transaction, with their dates; a rename onto a name that already has page text saves nothing;
 * when another place still has the old name, the text stays with it.
 * Since 2026-11-02 the rename also carries the new name to the child places (parent), the region and
 * subregion columns below a region or subregion, synonyms (valid_name) and a country's curator
 * assignments (login_country), in the same transaction (GeolocaleDb.carryRename).
 *
 * The tables are made here with the columns the code uses (object_hist.created is ON UPDATE
 * CURRENT_TIMESTAMP, as in production). Runs in CI against MySQL 8.4 (host "mysql");
 * ANTWEB_TEST_DB_URL overrides.
 */
public class PageTextRenameDbTest extends TestCase {

    static final int OLD_LAND = 1, TAKEN_LAND = 2, GEORGIA_ADM1 = 3, GEORGIA = 4, PLAIN = 5;

    /** created as text from the server (no time zone conversion in the driver). */
    static final String D = "date_format(created, '%Y-%m-%d %H:%i:%s')";

    private Connection c;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        c = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        c.setAutoCommit(true);
        for (String t : new String[] {"geolocale", "object_edit", "object_hist", "login_country"}) exec("drop table if exists " + t);
        exec("create table login_country (login_id int, country varchar(250))");
        exec("create table geolocale (id int primary key, name varchar(128), georank varchar(20), georank_type varchar(40),"
            + " is_valid tinyint default 1, valid_name varchar(128), isoCode varchar(10), iso3Code varchar(10), is_un tinyint default 0,"
            + " is_live tinyint default 0, source varchar(64), admin_notes varchar(255), parent varchar(128), region varchar(128),"
            + " subregion varchar(128), country varchar(128), bioregion varchar(64), alt_bioregion varchar(64), extent varchar(128),"
            + " coords varchar(128), map varchar(128), centroid varchar(64), centroid_fixed varchar(64), bounding_box varchar(128),"
            + " bounding_box_fixed varchar(128), is_use_children tinyint default 0, is_use_parent_region tinyint default 0,"
            + " is_island tinyint default 0, created timestamp null default current_timestamp, rev int default 0,"
            + " subfamily_count int default 0, genus_count int default 0, species_count int default 0, valid_species_count int default 0,"
            + " specimen_count int default 0, image_count int default 0, imaged_specimen_count int default 0,"
            + " endemic_species_count int default 0, introduced_species_count int default 0, chart_color varchar(255),"
            + " taxon_subfamily_dist_json text, specimen_subfamily_dist_json text)");
        // As in production (db/upgrade/6.1/2015-11-10.sql, utf8 general collation since 2016/2021).
        exec("create table object_edit (object_key varchar(128) NOT NULL, title varchar(32) NOT NULL, content longtext,"
            + " edit_id int NOT NULL DEFAULT 0, created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP, is_manual_entry tinyint NOT NULL,"
            + " access_group int DEFAULT NULL, access_login int DEFAULT NULL, UNIQUE KEY object_key (object_key, title))"
            + " DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci");
        exec("create table object_hist (object_key varchar(128) NOT NULL, title varchar(32) NOT NULL, content longtext,"
            + " edit_id int NOT NULL DEFAULT 0, created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + " hist_id int NOT NULL AUTO_INCREMENT, access_group varchar(32) DEFAULT NULL, access_login int DEFAULT NULL,"
            + " PRIMARY KEY (hist_id)) DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci");
        exec("insert into geolocale (id, name, georank, parent) values (1, 'Old Land', 'country', 'Eastern Africa'),"
            + " (2, 'Taken Land', 'country', 'Eastern Africa'), (3, 'Georgia', 'adm1', 'United States'),"
            + " (4, 'Georgia', 'country', 'Western Asia'), (5, 'Plain', 'country', 'Eastern Africa')");
        exec("insert into object_edit (object_key, title, content, created, is_manual_entry, access_login) values"
            + " ('Old Land', 'contents', '<p>Old Land text</p>', '2020-01-02 03:04:05', 1, 7),"
            + " ('Old Land', 'author', 'A. Author', '2020-01-02 03:04:06', 1, 7),"
            + " ('Taken Land', 'contents', '<p>Taken Land text</p>', '2021-01-01 00:00:00', 1, 8),"
            + " ('Georgia', 'contents', '<p>Georgia text</p>', '2022-01-01 00:00:00', 1, 9)");
        exec("insert into object_hist (object_key, title, content, created, access_login) values"
            + " ('Old Land', 'contents', '<p>first version</p>', '2019-05-05 05:05:05', 7),"
            + " ('Old Land', 'contents', '', '2019-06-06 06:06:06', 7),"
            + " ('Taken Land', 'contents', '<p>Taken old</p>', '2018-01-01 00:00:00', 8)");
    }

    @Override
    protected void tearDown() throws Exception {
        if (c != null) c.close();
    }

    private void exec(String sql) throws Exception {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql);
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
        return rows("select id, name from geolocale order by id")
            + rows("select cast(object_key as binary), title, content, " + D + " from object_edit order by object_key, title")
            + rows("select hist_id, cast(object_key as binary), title, content, " + D + " from object_hist order by hist_id");
    }

    private String save(int id, String name) throws Exception {
        EditGeolocaleForm form = new EditGeolocaleForm();
        form.setId(id);
        form.setName(name);
        form.setIsValid(true);
        form.setSource("test");
        return new GeolocaleDb(c).updateGeolocale(form);
    }

    public void testRenameMovesTextAndHistory() throws Exception {
        String msg = save(OLD_LAND, "New Land");
        assertFalse(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertTrue(msg, msg.contains("Renamed Old Land to New Land: its page text (2 fields, 2 earlier versions) moved with it."));
        assertEquals("New Land", rows("select name from geolocale where id = 1").trim());
        assertEquals("", rows("select title from object_edit where object_key = 'Old Land'"));
        assertEquals("", rows("select title from object_hist where object_key = 'Old Land'"));
        // Same rows and dates under the new name.
        assertEquals("author|A. Author|2020-01-02 03:04:06|7\ncontents|<p>Old Land text</p>|2020-01-02 03:04:05|7\n",
            rows("select title, content, " + D + ", access_login from object_edit where object_key = 'New Land' order by title"));
        assertEquals("<p>first version</p>|2019-05-05 05:05:05\n|2019-06-06 06:06:06\n",
            rows("select content, " + D + " from object_hist where object_key = 'New Land' order by hist_id"));
        // Nothing else moved.
        assertEquals("1\n", rows("select count(*) from object_edit where object_key = 'Taken Land'"));
        assertEquals("1\n", rows("select count(*) from object_hist where object_key = 'Taken Land'"));
        assertTrue(c.getAutoCommit());
    }

    public void testRenameOntoNameWithTextIsRefused() throws Exception {
        String before = snapshot();
        String msg = save(OLD_LAND, "Taken Land");
        assertTrue(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertTrue(msg, msg.contains("The name Taken Land already has page text (1 field in object_edit)"));
        assertEquals("nothing changed", before, snapshot());
        // Upper/lower case does not matter to the database: "taken land" is the same name.
        msg = save(OLD_LAND, "taken land");
        assertTrue(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertEquals("nothing changed", before, snapshot());
        assertTrue(c.getAutoCommit());
    }

    public void testRefusalIsEscaped() throws Exception {
        exec("insert into object_edit (object_key, title, content, is_manual_entry) values ('<b>x</b>', 'contents', 't', 1)");
        String r = PageTextKeys.refusal(c, "Old Land", "<b>x</b>");
        assertNotNull(r);
        assertFalse(r, r.contains("<b>"));
        assertTrue(r, r.contains("&lt;b&gt;x&lt;/b&gt;"));
        assertNull(PageTextKeys.refusal(c, "Old Land", "Nowhere"));
        assertNull(PageTextKeys.refusal(c, null, "Taken Land"));
    }

    public void testCaseOnlyRenameMovesTheKey() throws Exception {
        String msg = save(OLD_LAND, "OLD LAND");
        assertFalse(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertEquals("OLD LAND|author\nOLD LAND|contents\n",
            rows("select cast(object_key as char), title from object_edit where object_key = 'old land' order by title"));
        assertEquals("2020-01-02 03:04:05\n", rows("select " + D + " from object_edit where object_key = 'OLD LAND' and title = 'contents'"));
        assertEquals("2\n", rows("select count(*) from object_hist where binary object_key = 'OLD LAND'"));
    }

    public void testOldNameStillUsedByAnotherPlaceKeepsTheText() throws Exception {
        String msg = save(GEORGIA_ADM1, "Georgia (state)");
        assertFalse(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertTrue(msg, msg.contains("Another place is still named Georgia"));
        assertEquals("Georgia (state)", rows("select name from geolocale where id = 3").trim());
        assertEquals("<p>Georgia text</p>|2022-01-01 00:00:00\n", rows("select content, " + D + " from object_edit where object_key = 'Georgia'"));
        assertEquals("", rows("select title from object_edit where object_key = 'Georgia (state)'"));
    }

    public void testSaveWithoutRenameChangesNoText() throws Exception {
        String before = rows("select * from object_edit order by object_key, title") + rows("select * from object_hist order by hist_id");
        String msg = save(PLAIN, "Plain");
        assertEquals("Update successful.", msg);
        msg = save(OLD_LAND, "Old Land");
        assertEquals("Update successful.", msg);
        assertEquals(before, rows("select * from object_edit order by object_key, title") + rows("select * from object_hist order by hist_id"));
    }

    public void testMoveKeepsHistoryDates() throws Exception {
        int[] moved = PageTextKeys.move(c, "Taken Land", "Other Land");
        assertEquals(1, moved[0]);
        assertEquals(1, moved[1]);
        assertEquals("2018-01-01 00:00:00\n", rows("select " + D + " from object_hist where object_key = 'Other Land'"));
        assertEquals("2021-01-01 00:00:00\n", rows("select " + D + " from object_edit where object_key = 'Other Land'"));
    }

    public void testCountryRenameCarriesToAdm1sAndCurators() throws Exception {
        exec("insert into geolocale (id, name, georank, parent, country) values (10, 'Alpha', 'adm1', 'Old Land', 'Old Land'),"
            + " (11, 'Beta', 'adm1', 'Old Land', null), (12, 'Gamma', 'adm1', 'Plain', null),"
            + " (13, 'Old Land', 'adm1', 'Plain', null), (14, 'Old Lande', 'country', 'Eastern Africa', null)");
        exec("update geolocale set is_valid = 0, valid_name = 'Old Land' where id = 14");
        exec("insert into login_country values (7, 'Old Land'), (8, 'Old Land'), (8, 'New Land'), (9, 'Plain')");
        String msg = save(OLD_LAND, "New Land");
        assertFalse(msg, msg.startsWith(GeolocaleDb.NOT_SAVED));
        assertTrue(msg, msg.contains("The new name was carried to 2 adm1s (their parent), 1 place that name it as valid name, 2 curator assignments."));
        assertEquals("10|New Land|New Land\n11|New Land|null\n12|Plain|null\n13|Plain|null\n",
            rows("select id, parent, country from geolocale where georank = 'adm1' and id >= 10 order by id"));
        assertEquals("New Land\n", rows("select valid_name from geolocale where id = 14"));
        // Login 8 had both names: one row left. Other countries untouched.
        assertEquals("7|New Land\n8|New Land\n9|Plain\n", rows("select login_id, country from login_country order by login_id, country"));
        assertTrue(c.getAutoCommit());
    }

    public void testRegionAndSubregionRenameCarryDown() throws Exception {
        exec("insert into geolocale (id, name, georank, parent, region, subregion) values"
            + " (20, 'Africa', 'region', null, null, null), (21, 'Eastern Africa', 'subregion', 'Africa', 'Africa', null),"
            + " (22, 'Western Africa', 'subregion', 'Africa', 'Africa', null)");
        exec("update geolocale set region = 'Africa', subregion = 'Eastern Africa' where georank = 'country'");
        exec("insert into geolocale (id, name, georank, parent, region, subregion) values (23, 'Kiwi', 'adm1', 'Plain', 'Africa', 'Eastern Africa')");
        String msg = save(21, "East Africa");
        assertTrue(msg, msg.contains("The new name was carried to 3 countries (their parent), 5 places below it (their subregion)."));
        assertEquals("1|East Africa|East Africa\n2|East Africa|East Africa\n4|Western Asia|East Africa\n5|East Africa|East Africa\n",
            rows("select id, parent, subregion from geolocale where georank = 'country' order by id"));
        msg = save(20, "Afrika");
        assertTrue(msg, msg.contains("The new name was carried to 2 subregions (their parent), 7 places below it (their region)."));
        assertEquals("0\n", rows("select count(*) from geolocale where parent = 'Africa' or region = 'Africa'"));
        assertEquals("Afrika\n", rows("select region from geolocale where id = 23"));
    }

    public void testAmbiguousOrFailedRenameCarriesNothing() throws Exception {
        // Two adm1s named Georgia would be ambiguous only in the same country: the country Georgia's adm1s
        // keep their parent when the adm1 Georgia (United States) is renamed.
        exec("insert into geolocale (id, name, georank, parent) values (30, 'Tbilisi', 'adm1', 'Georgia')");
        exec("insert into login_country values (7, 'Georgia')");
        String msg = save(GEORGIA_ADM1, "Georgia (state)");
        assertFalse(msg, msg.contains("carried"));
        assertEquals("Georgia\n", rows("select parent from geolocale where id = 30"));
        assertEquals("Georgia\n", rows("select country from login_country"));
        // A second country with the same name: nothing is carried, and the message says so.
        exec("insert into geolocale (id, name, georank, parent) values (31, 'Plain', 'country', 'Western Asia'), (32, 'Delta', 'adm1', 'Plain')");
        msg = save(PLAIN, "Flat");
        assertTrue(msg, msg.contains("Another country is still named Plain"));
        assertEquals("Plain\n", rows("select parent from geolocale where id = 32"));
        // A failure inside the carry rolls the whole save back (one transaction).
        exec("drop table login_country");
        try {
            save(OLD_LAND, "New Land");
            fail("expected the missing login_country table to fail the save");
        } catch (java.sql.SQLException expected) { }
        assertEquals("Old Land\n", rows("select name from geolocale where id = 1"));
        assertEquals("2\n", rows("select count(*) from object_edit where object_key = 'Old Land'"));
        assertTrue(c.getAutoCommit());
    }
}

package test.org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import junit.framework.TestCase;

import org.calacademy.antweb.home.ApiKeyDb;
import org.calacademy.antweb.util.ApiKeyMgr;
import org.calacademy.antweb.util.ApiKeys;

/**
 * Free API keys, step 6 PRs 4 and 5 (Brian Fisher, 2026-11-08), database steps: a registration is saved as pending
 * with only the hash of its link; opening the link changes nothing, the button makes the key once (only its SHA-256
 * and first 7 characters stored), a second use or an expired link shows no key; the email limit; the admin list
 * (newest first, filters, usage today / 7 / 30 days, the no-key row) and its changes (revoke, reactivate, level,
 * limits, note).
 *
 * Runs in CI against MySQL 8.4 (host "mysql"), like RegionCountDbTest; ANTWEB_TEST_DB_URL overrides.
 */
public class ApiKeyDbTest extends TestCase {

    private Connection connection;

    @Override
    protected void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ANTWEB_TEST_DB_URL",
            "jdbc:mysql://mysql:3306/ant?useSSL=false&allowPublicKeyRetrieval=true");
        connection = DriverManager.getConnection(url, System.getenv().getOrDefault("ANTWEB_TEST_DB_USER", "antweb"),
            System.getenv().getOrDefault("ANTWEB_TEST_DB_PASSWORD", "CHANGE_ME"));
        connection.setAutoCommit(true);
        exec("drop table if exists api_key");
        exec("drop table if exists api_usage_daily");
        new ApiKeyDb(connection).ensureSchema();
        new ApiKeyDb(connection).ensureSchema();  // safe to run again
    }

    @Override
    protected void tearDown() throws Exception {
        if (connection != null) connection.close();
    }

    private void exec(String sql) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private String one(String sql) throws SQLException {
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        }
    }

    private static ApiKeys.Registration reg(String name, String email) {
        return new ApiKeys.Registration(name, email, "CAS", "Maps of " + name, true);
    }

    /** A pending registration and its link token (as register() makes them, without the email). */
    private String pending(ApiKeyDb db, String name, String email) throws SQLException {
        String token = ApiKeys.newConfirmToken();
        db.insertPending(reg(name, email), ApiKeys.sha256(token), ApiKeys.CONFIRM_HOURS);
        return token;
    }

    public void testLinkShowsTheKeyOnceAndOnlyItsHashIsStored() throws Exception {
        ApiKeyDb db = new ApiKeyDb(connection);
        String token = pending(db, "Ana", "ana@example.org");
        assertEquals("pending", one("select status from api_key"));
        assertEquals(ApiKeys.sha256(token), one("select confirm_token_hash from api_key"));
        assertTrue(one("select prefix from api_key").startsWith("p_"));
        assertEquals("1", one("select count(*) from api_key where confirm_expires > now() + interval 23 hour"));

        // Opening the link (GET) shows the button and changes nothing (email scanners open links first).
        ApiKeyMgr.ConfirmPage get = ApiKeyMgr.confirm(connection, token, false);
        assertEquals("confirm", get.state);
        assertNull(get.key);
        assertEquals("Ana", get.name);
        assertEquals("pending", one("select status from api_key"));

        ApiKeyMgr.ConfirmPage done = ApiKeyMgr.confirm(connection, token, true);
        assertEquals("done", done.state);
        assertTrue(ApiKeys.isWellFormedKey(done.key));
        assertEquals("active", one("select status from api_key"));
        assertEquals(ApiKeys.sha256(done.key), one("select key_hash from api_key"));
        assertEquals(done.key.substring(0, 7), one("select prefix from api_key"));
        assertNotNull(one("select confirmed from api_key"));
        String everything = one("select concat_ws('|', prefix, key_hash, name, email, institution, project, level, status,"
            + " confirm_token_hash, coalesce(note, '')) from api_key");
        assertFalse("the key itself is never stored", everything.contains(done.key));
        assertFalse(everything.contains(done.key.substring(3)));

        // Shown once: the same link again (GET or POST) shows no key.
        assertEquals("used", ApiKeyMgr.confirm(connection, token, false).state);
        ApiKeyMgr.ConfirmPage again = ApiKeyMgr.confirm(connection, token, true);
        assertEquals("used", again.state);
        assertNull(again.key);
        assertEquals(ApiKeys.sha256(done.key), one("select key_hash from api_key"));
    }

    public void testExpiredUnknownAndCancelledLinks() throws Exception {
        ApiKeyDb db = new ApiKeyDb(connection);
        String token = pending(db, "Bo", "bo@example.org");
        exec("update api_key set confirm_expires = now() - interval 1 minute");
        assertEquals("expired", ApiKeyMgr.confirm(connection, token, false).state);
        ApiKeyMgr.ConfirmPage post = ApiKeyMgr.confirm(connection, token, true);
        assertEquals("expired", post.state);
        assertNull(post.key);
        assertEquals("pending", one("select status from api_key"));

        assertEquals("invalid", ApiKeyMgr.confirm(connection, ApiKeys.newConfirmToken(), true).state);
        assertEquals("invalid", ApiKeyMgr.confirm(connection, "x' or 1=1 -- ", true).state);
        assertEquals("invalid", ApiKeyMgr.confirm(connection, null, false).state);

        String token2 = pending(db, "Cy", "cy@example.org");
        int id = Integer.parseInt(one("select id from api_key where name = 'Cy'"));
        assertTrue(db.revoke(id));
        assertEquals("cancelled", ApiKeyMgr.confirm(connection, token2, true).state);
        assertFalse("never confirmed: cannot be reactivated", db.reactivate(id));
    }

    public void testRegisterLimitsPerEmail() throws Exception {
        ApiKeyDb db = new ApiKeyDb(connection);
        // No etc/mail.properties here: saved as pending, the link is not emailed.
        assertEquals(ApiKeyMgr.MAIL_FAILED, ApiKeyMgr.register(connection, reg("Di", "di@example.org")));
        assertEquals(ApiKeyMgr.MAIL_FAILED, ApiKeyMgr.register(connection, reg("Di", "di@example.org")));
        assertEquals(ApiKeyMgr.MAIL_FAILED, ApiKeyMgr.register(connection, reg("Di", "di@example.org")));
        assertEquals(3, db.countByEmailLastDay("di@example.org"));
        assertEquals(ApiKeyMgr.EMAIL_LIMIT, ApiKeyMgr.register(connection, reg("Di", "di@example.org")));
        assertEquals("3", one("select count(*) from api_key"));
        assertEquals(ApiKeyMgr.MAIL_FAILED, ApiKeyMgr.register(connection, reg("Ed", "ed@example.org")));
        exec("update api_key set created = now() - interval 25 hour where email = 'di@example.org'");
        assertEquals(0, db.countByEmailLastDay("di@example.org"));
        assertEquals(1, db.countAllLastDay());
        assertEquals("pending", one("select distinct status from api_key"));
    }

    public void testAdminListUsageAndChanges() throws Exception {
        ApiKeyDb db = new ApiKeyDb(connection);
        String t1 = pending(db, "Ana Old", "ana@example.org");
        exec("update api_key set created = now() - interval 2 day");
        assertEquals("done", ApiKeyMgr.confirm(connection, t1, true).state);
        pending(db, "Bo 100%", "bo@museum.org");
        int ana = Integer.parseInt(one("select id from api_key where name = 'Ana Old'"));
        int bo = Integer.parseInt(one("select id from api_key where name like 'Bo%'"));

        exec("insert into api_usage_daily (day, key_id, requests, records, limited) values"
            + " (curdate(), " + ana + ", 10, 100, 1), (curdate() - interval 5 day, " + ana + ", 20, 200, 2),"
            + " (curdate() - interval 20 day, " + ana + ", 30, 300, 3), (curdate() - interval 40 day, " + ana + ", 999, 999, 9),"
            + " (curdate(), 0, 4, 40, 7), (curdate() - interval 29 day, 0, 5, 50, 0)");

        List<ApiKeyDb.Row> all = db.list(new ApiKeyDb.Filter());
        assertEquals(2, all.size());
        assertEquals("newest first", bo, all.get(0).id);
        ApiKeyDb.Row a = all.get(1);
        assertEquals(10, a.usage.today);
        assertEquals(30, a.usage.last7);
        assertEquals(60, a.usage.last30);
        assertEquals(600, a.usage.records30);
        assertEquals(6, a.usage.refused30);
        assertTrue(a.shownPrefix().startsWith("aw_"));
        assertEquals("", all.get(0).shownPrefix());
        assertEquals(0, all.get(0).usage.last30);

        ApiKeyDb.Usage noKey = db.noKeyUsage();
        assertEquals(4, noKey.today);
        assertEquals(9, noKey.last30);
        assertEquals(7, noKey.refused30);

        ApiKeyDb.Filter f = new ApiKeyDb.Filter();
        f.status = "active";
        assertEquals(1, db.list(f).size());
        f = new ApiKeyDb.Filter();
        f.search = "museum";
        assertEquals(bo, db.list(f).get(0).id);
        f.search = "100%";
        assertEquals(1, db.list(f).size());
        f.search = "%";  // a literal percent sign, not "everything"
        assertEquals(1, db.list(f).size());
        f = new ApiKeyDb.Filter();
        f.level = "collaborator";
        assertEquals(0, db.list(f).size());

        assertTrue(db.setLevel(ana, "collaborator"));
        assertEquals(1, db.list(f).size());
        try {
            db.setLevel(ana, "superuser");
            fail("unknown level saved");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        assertTrue(db.setLimits(ana, 120, null, 2000, 100000));
        ApiKeyDb.Row r = db.get(ana);
        assertEquals(Integer.valueOf(120), r.perMinute);
        assertNull(r.perDay);
        assertEquals(Integer.valueOf(2000), r.maxRecords);
        assertEquals(Integer.valueOf(100000), r.recordsPerDay);
        assertTrue(db.setNote(ana, "Agreed with Brian, 2026-11-08"));
        assertEquals("Agreed with Brian, 2026-11-08", db.get(ana).note);
        assertTrue(db.setNote(ana, ""));
        assertNull(db.get(ana).note);

        assertTrue(db.revoke(ana));
        assertFalse("already revoked", db.revoke(ana));
        assertEquals("revoked", db.get(ana).status);
        assertNotNull(db.get(ana).revoked);
        assertTrue(db.reactivate(ana));
        assertEquals("active", db.get(ana).status);
        assertNull(db.get(ana).revoked);
        assertNull(db.get(9999));
    }
}

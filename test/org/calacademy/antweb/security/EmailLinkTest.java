package test.org.calacademy.antweb.security;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.calacademy.antweb.upload.UploadNotice;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * Emails AntWeb sends (2026-10-19):
 * - no email tells the reader to ignore it (every email text is rendered here);
 * - a reset, reactivation or invitation link (all resetPassword.do?token=...) is not used up
 *   when it is opened: email security scanners (Safe Links, Mimecast, Proofpoint, Avanan)
 *   open links before the person does. Only the POST that sets a new password uses it up.
 *
 * The database is a small in-memory stand-in (a JDBC proxy) that records every statement.
 */
public class EmailLinkTest extends TestCase {

    private static final Pattern IGNORE = Pattern.compile(
        "ignore (this|the) (e-?mail|message)|you can (safely )?ignore|disregard (this|the)", Pattern.CASE_INSENSITIVE);

    private static void assertNoIgnore(String what, String text) {
        assertNotNull(what, text);
        assertFalse(what + " tells the reader to ignore it:\n" + text, IGNORE.matcher(text).find());
        assertFalse(what, text.toLowerCase(Locale.ROOT).contains("ignore this email"));
    }

    // ------------------------------------------------------------ email texts

    public void testResetEmailsDoNotSayIgnore() {
        String link = "https://www.antweb.org/resetPassword.do?token=T";
        String intro = "Someone, probably you, asked to reset the password of your AntWeb account (username: ann).";
        assertNoIgnore("reset (forgot password)", PasswordResetMgr.emailBody("Ann Lee", intro, link));
        assertNoIgnore("reset, no intro", PasswordResetMgr.emailBody("", null, link));
        assertNoIgnore("reset, 24 hours", PasswordResetMgr.emailBody("Ann Lee", intro, link, PasswordResetMgr.VALID_MINUTES, false));
        String campaign = PasswordResetMgr.emailBody("Ann Lee",
            "As a security precaution, AntWeb is asking all curators and administrators to set a new password.",
            link, PasswordResetMgr.CAMPAIGN_VALID_MINUTES, true);
        assertNoIgnore("reset (campaign, required)", campaign);
        assertTrue(campaign.contains("This is required"));
        assertNoIgnore("reactivation", PasswordResetMgr.reactivationEmailBody("Ann Lee", "ann", link));
        assertNoIgnore("reactivation, no name", PasswordResetMgr.reactivationEmailBody(null, null, link));
    }

    public void testUploadEmailsDoNotSayIgnore() {
        for (boolean images : new boolean[] {false, true}) {
            for (boolean complete : new boolean[] {false, true}) {
                for (boolean report : new boolean[] {false, true}) {
                    UploadNotice.Facts f = new UploadNotice.Facts();
                    f.images = images;
                    f.complete = complete;
                    f.fileName = "specimens.txt";
                    f.fileType = "AntWeb specimen file";
                    f.groupName = "Test group";
                    f.when = new Date(1_760_000_000_000L);
                    f.uploadId = 5;
                    f.specimensLoaded = images ? -1 : 10;
                    f.redFlagged = images ? -1 : 1;
                    f.imagesSaved = images ? 3 : -1;
                    f.imagesNotSaved = images ? 1 : -1;
                    f.reportUrl = report ? "https://www.antweb.org/web/log/upload/x.html" : null;
                    f.reportNeedsLogin = images;
                    f.failure = complete ? null : "The file has no header line.";
                    f.uploadPageUrl = "https://www.antweb.org/curate.do";
                    String what = "upload email images=" + images + " complete=" + complete + " report=" + report;
                    assertNoIgnore(what + " (subject)", UploadNotice.subject(f));
                    assertNoIgnore(what, UploadNotice.body(f));
                }
            }
        }
    }

    // --------------------------------------- reset links survive link scanners

    /** In-memory password_reset row and login row, and every statement run. */
    static final class FakeDb {
        final List<String> statements = new ArrayList<>();
        boolean tokenUsed = false;
        String tokenHash;
        final int loginId = 42;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "prepareStatement": return statement((String) a[0]);
                        case "getAutoCommit": return Boolean.TRUE;
                        case "isClosed": return Boolean.FALSE;
                        case "toString": return "FakeDb";
                        case "hashCode": return 1;
                        case "equals": return p == a[0];
                        default: return null;
                    }
                });
        }

        private PreparedStatement statement(String sql) {
            final String q = sql.toLowerCase(Locale.ROOT).trim();
            final List<Object> params = new ArrayList<>();
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class},
                (p, m, a) -> {
                    String n = m.getName();
                    if (n.startsWith("set") && a != null && a.length == 2) { params.add(a[1]); return null; }
                    if (n.equals("executeQuery")) { statements.add(q); return query(q, params); }
                    if (n.equals("executeUpdate")) { statements.add(q); return update(q, params); }
                    if (n.equals("close")) return null;
                    if (n.equals("toString")) return q;
                    if (n.equals("hashCode")) return q.hashCode();
                    if (n.equals("equals")) return p == a[0];
                    throw new UnsupportedOperationException("FakeDb statement: " + n);
                });
        }

        private ResultSet query(String q, List<Object> params) {
            List<Object[]> rows = new ArrayList<>();
            String[] cols;
            if (q.startsWith("select login_id from password_reset")) {
                cols = new String[] {"login_id"};
                if (!tokenUsed && params.size() > 0 && params.get(0).equals(tokenHash)) rows.add(new Object[] {loginId});
            } else if (q.startsWith("select id, name, first_name")) {
                cols = new String[] {"id", "name", "first_name", "last_name", "email", "is_admin", "must_reset"};
                rows.add(new Object[] {loginId, "anntest", "Ann", "Lee", "ann@example.org", 0, 0});
            } else if (q.startsWith("select session_version")) {
                cols = new String[] {"session_version"};
                rows.add(new Object[] {7});
            } else {
                cols = new String[0];
            }
            return resultSet(cols, rows);
        }

        private int update(String q, List<Object> params) {
            if (q.startsWith("update password_reset set used_at = now() where token_hash")) {
                if (tokenUsed || !params.get(0).equals(tokenHash)) return 0;
                tokenUsed = true;
                return 1;
            }
            if (q.startsWith("update password_reset")) return 0;
            return 1;
        }

        private ResultSet resultSet(final String[] cols, final List<Object[]> rows) {
            final int[] at = {-1};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
                (p, m, a) -> {
                    String n = m.getName();
                    if (n.equals("next")) return ++at[0] < rows.size();
                    if (n.equals("close")) return null;
                    if (n.equals("wasNull")) return Boolean.FALSE;
                    if (n.equals("toString")) return "FakeResultSet";
                    if (n.equals("hashCode")) return 2;
                    if (n.equals("equals")) return p == a[0];
                    Object[] row = rows.get(at[0]);
                    Object v = a[0] instanceof Integer ? row[(Integer) a[0] - 1] : row[java.util.Arrays.asList(cols).indexOf(a[0])];
                    switch (n) {
                        case "getInt": return ((Number) v).intValue();
                        case "getLong": return ((Number) v).longValue();
                        case "getBoolean": return ((Number) v).intValue() != 0;
                        case "getString": return v == null ? null : v.toString();
                        case "getObject": return v;
                        default: throw new UnsupportedOperationException("FakeDb result: " + n);
                    }
                });
        }

        boolean anyWrite() {
            for (String s : statements) {
                if (s.startsWith("update") || s.startsWith("insert") || s.startsWith("delete") || s.startsWith("replace")) return true;
            }
            return false;
        }
    }

    public void testOpeningTheLinkDoesNotUseItUp() throws Exception {
        FakeDb db = new FakeDb();
        String token = PasswordResetMgr.newToken();
        db.tokenHash = PasswordResetMgr.hashToken(token);
        Connection c = db.connection();

        // A link scanner opens the link, then the person opens it (twice: GET, GET).
        for (int i = 0; i < 2; i++) {
            PasswordResetMgr.ResetPage page = PasswordResetMgr.handleResetPage(c, token, false, null, null);
            assertEquals("GET " + (i + 1) + " shows the form", "form", page.state);
            assertFalse("GET used up the token", db.tokenUsed);
            assertFalse("GET wrote to the database: " + db.statements, db.anyWrite());
        }
        // A scanner that sends a POST without a password changes nothing either.
        PasswordResetMgr.ResetPage page = PasswordResetMgr.handleResetPage(c, token, true, null, null);
        assertEquals("form", page.state);
        assertNotNull(page.error);
        assertFalse(db.tokenUsed);
        assertFalse("POST without a password wrote to the database: " + db.statements, db.anyWrite());

        // Then the person sets a password: the token is used up now, and only now.
        page = PasswordResetMgr.handleResetPage(c, token, true, "correct horse battery", "correct horse battery");
        assertEquals("done", page.state);
        assertTrue(db.tokenUsed);
        assertTrue(db.anyWrite());

        // The same link afterwards: invalid, and nothing more is written.
        int before = db.statements.size();
        page = PasswordResetMgr.handleResetPage(c, token, false, null, null);
        assertEquals("invalid", page.state);
        page = PasswordResetMgr.handleResetPage(c, token, true, "another good passphrase", "another good passphrase");
        assertEquals("invalid", page.state);
        for (String s : db.statements.subList(before, db.statements.size())) assertTrue(s, s.startsWith("select"));
    }

    public void testMalformedTokenTouchesNothing() throws Exception {
        FakeDb db = new FakeDb();
        assertEquals("invalid", PasswordResetMgr.handleResetPage(db.connection(), "abc", false, null, null).state);
        assertEquals("invalid", PasswordResetMgr.handleResetPage(db.connection(), null, true, "x", "x").state);
        assertTrue(db.statements.isEmpty());
    }
}

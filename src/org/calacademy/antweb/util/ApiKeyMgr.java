package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.ApiKeyDb;

/**
 * Registration and email confirmation of free API keys (step 6 PR 4; Brian Fisher, 2026-11-08). Rules that need no
 * database are in ApiKeys; the tables in home/ApiKeyDb.
 *
 *   register()  apiKey.do after the form, the human check and the per-IP limit passed: at most 3 registrations a day
 *               per email address and 100 a day in all; saves the registration as 'pending' with the SHA-256 of a
 *               new confirmation token (24 hours), emails the registrant the link apiKeyConfirm.do?token=..., and
 *               emails a copy (no token, no key) to bfisher@calacademy.org with a link to the admin page.
 *   confirm()   apiKeyConfirm.do: opening the link (GET) only shows a button (email scanners open links first);
 *               the POST makes the key ("aw_" + 32 characters), stores only its SHA-256 and first 7 characters,
 *               sets the key active and returns it, once, for the page to show.
 *
 * Keys and tokens are never logged or emailed; admin.log gets the registration id and, once active, the prefix.
 * Tests: test/org/calacademy/antweb/security/ApiKeysTest.java (texts), home/ApiKeyDbTest.java (database).
 */
public final class ApiKeyMgr {

    private static final Log s_log = LogFactory.getLog(ApiKeyMgr.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final String SUBJECT_CONFIRM = "AntWeb: confirm your API key registration";
    public static final String SUBJECT_COPY = "AntWeb API key registration";

    // Outcomes of register().
    public static final String SENT = "sent";
    public static final String EMAIL_LIMIT = "email limit";
    public static final String DAILY_LIMIT = "daily limit";
    public static final String MAIL_FAILED = "mail not sent";
    public static final String ERROR = "error";

    private ApiKeyMgr() {}

    /** Save the registration and email the link. Returns one of the outcomes above. */
    public static String register(Connection connection, ApiKeys.Registration r) {
        try {
            ApiKeyDb db = new ApiKeyDb(connection);
            if (!ApiKeys.allowEmail(db.countByEmailLastDay(r.email))) {
                logEvent("registration refused: more than " + ApiKeys.MAX_PER_EMAIL_PER_DAY + " today for one email address");
                return EMAIL_LIMIT;
            }
            if (!ApiKeys.allowAll(db.countAllLastDay())) {
                logEvent("registration refused: more than " + ApiKeys.MAX_PER_DAY_ALL + " registrations today in all");
                return DAILY_LIMIT;
            }
            String token = ApiKeys.newConfirmToken();
            int id = db.insertPending(r, ApiKeys.sha256(token), ApiKeys.CONFIRM_HOURS);
            String link = AntwebProps.getSecureDomainApp() + "/apiKeyConfirm.do?token=" + token;
            boolean sent = Emailer.sendTransactional(r.email, SUBJECT_CONFIRM, confirmationEmailBody(r.name, link));
            String adminLink = AntwebProps.getSecureDomainApp() + "/apiKeys.do?id=" + id;
            Emailer.sendNotice(ApiKeys.COPY_TO, SUBJECT_COPY + ": " + r.name, copyText(r, id, LocalDateTime.now(), sent, adminLink), r.email);
            logEvent("registration id:" + id + " saved as pending; confirmation link " + (sent ? "emailed" : "NOT emailed"));
            return sent ? SENT : MAIL_FAILED;
        } catch (SQLException e) {
            s_log.error("register() e:" + e);
            return ERROR;
        }
    }

    /** What apiKeyConfirm.do shows: state "confirm" (the button), "done" (key set), or invalid, expired, used, cancelled, error. */
    public static final class ConfirmPage {
        public String state = "invalid";
        public String key;
        public String prefix;
        public String name;
    }

    public static ConfirmPage confirm(Connection connection, String token, boolean isPost) {
        ConfirmPage page = new ConfirmPage();
        if (!ApiKeys.isWellFormedToken(token)) return page;
        String tokenHash = ApiKeys.sha256(token);
        try {
            ApiKeyDb db = new ApiKeyDb(connection);
            ApiKeyDb.Row row = db.findByTokenHash(tokenHash);
            page.state = stateWord(row);
            if (row != null) page.name = row.name;
            if (!"confirm".equals(page.state) || !isPost) return page;
            for (int attempt = 0; attempt < 5; attempt++) {
                String key = ApiKeys.newKey();
                String prefix = ApiKeys.prefixOf(key);
                try {
                    if (db.activate(row.id, tokenHash, ApiKeys.sha256(key), prefix)) {
                        page.state = "done";
                        page.key = key;
                        page.prefix = prefix;
                        logEvent("registration id:" + row.id + " confirmed: key " + prefix + "... active");
                        return page;
                    }
                    // Someone else used the link a moment ago, or it just expired.
                    page.state = stateWord(db.findByTokenHash(tokenHash));
                    if ("confirm".equals(page.state)) page.state = "error";
                    return page;
                } catch (SQLIntegrityConstraintViolationException e) {
                    s_log.info("confirm() id:" + row.id + " key prefix taken, making another");  // 1 in millions
                }
            }
            page.state = "error";
            return page;
        } catch (SQLException e) {
            s_log.error("confirm() e:" + e);
            page.state = "error";
            return page;
        }
    }

    static String stateWord(ApiKeyDb.Row row) {
        ApiKeys.ConfirmState s = row == null ? ApiKeys.ConfirmState.UNKNOWN
            : ApiKeys.confirmState(row.status, row.confirmExpires, row.dbNow == null ? LocalDateTime.now() : row.dbNow);
        switch (s) {
            case VALID: return "confirm";
            case EXPIRED: return "expired";
            case USED: return "used";
            case CANCELLED: return "cancelled";
            default: return "invalid";
        }
    }

    // ---------------------------------------------------------------- email texts (unit tested)

    /** The email to the registrant (HTML, like the password reset email). The link holds the token, never a key. */
    public static String confirmationEmailBody(String name, String link) {
        String hello = name == null || name.trim().isEmpty() ? "Hello," : "Hello " + Html.esc(name.trim()) + ",";
        return hello + "<br><br>\n"
            + "Thank you for registering for a free AntWeb API key. To confirm your email address and see your key,"
            + " open this link (it works for " + ApiKeys.CONFIRM_HOURS + " hours, and only once):<br>\n"
            + "<a href=\"" + Html.escAttr(link) + "\">" + Html.esc(link) + "</a><br><br>\n"
            + "The key is shown only once, on that page. Copy it then and keep it private.<br><br>\n"
            + "Terms of use: <a href=\"" + ApiKeys.TERMS_URL + "\">" + ApiKeys.TERMS_URL + "</a><br><br>\n"
            + "If you did not register: without this link no key is made.<br><br>\n"
            + "Working on a larger project? Brian L. Fisher welcomes collaborations; write to antweb@antweb.org.<br><br>\n"
            + "AntWeb<br>California Academy of Sciences";
    }

    /** The copy to Brian Fisher (plain text): who registered and for what, with a link to the admin page. */
    public static String copyText(ApiKeys.Registration r, int id, LocalDateTime when, boolean linkSent, String adminLink) {
        return "A new AntWeb API key registration (number " + id + "):\n\n"
            + "Name: " + r.name + "\n"
            + "Email: " + r.email + "\n"
            + "Institution or affiliation: " + (r.institution == null || r.institution.isEmpty() ? "(none given)" : r.institution) + "\n"
            + "Time: " + when.format(TIME) + " (Pacific)\n"
            + "Project:\n" + r.project + "\n\n"
            + (linkSent ? "The confirmation link was emailed to the registrant. The key becomes active when they open it."
                        : "The confirmation link could NOT be emailed (mail not configured or a sending limit); the registration stays pending.")
            + "\n\nSee, revoke or change the key: " + adminLink + "\n"
            + "Reply to this email to write to the registrant.\n";
    }

    /** One line in admin.log. Never pass keys or tokens. */
    public static void logEvent(String text) {
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " api key: " + text);
    }
}

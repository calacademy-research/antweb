package org.calacademy.antweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.home.PasswordResetDb;

/**
 * Password reset by email link, and ending a login's other sessions.
 *
 * A reset link carries a random token (32 bytes, base64url). Only its SHA-256
 * is stored (password_reset.token_hash). A link is valid for 24 hours (14 days from the campaign) and works
 * once. At most 3 links per login per hour, and at most 10 requests per IP
 * address per hour from the "Forgot your password?" page.
 *
 * Sessions: login.session_version is copied into the session at login. When
 * the stored value changes (password reset, admin campaign), every session
 * holding the old value is treated as logged out (LoginMgr.getAccessLogin()).
 * The stored value is cached for a minute per login, so this costs at most one
 * small query per login per minute.
 *
 * Tokens and passwords are never logged.
 */
public final class PasswordResetMgr {

    private static final Log s_log = LogFactory.getLog(PasswordResetMgr.class);

    public static final int TOKEN_BYTES = 32;
    // Links someone asks for (forgot password, or logging in while a reset is required).
    public static final int VALID_MINUTES = 24 * 60;
    // Links sent by the admin campaign: people may not read the email for days.
    public static final int CAMPAIGN_VALID_MINUTES = 14 * 24 * 60;
    public static final int MAX_PER_LOGIN_PER_HOUR = 3;
    public static final int MAX_PER_IP_PER_HOUR = 10;

    public static final String SUBJECT_RESET = "AntWeb password reset";
    public static final String SUBJECT_CAMPAIGN = "AntWeb: please set a new password";

    public static final String SESSION_VERSION_ATTR = "sessionVersion";

    // Outcomes of sendResetLink().
    public static final String SENT = "sent";
    public static final String NO_EMAIL = "no email";
    public static final String RATE_LIMITED = "rate limited";
    public static final String MAIL_FAILED = "mail not sent";
    public static final String ERROR = "error";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetMgr() {}

    // ---------------------------------------------------------------- tokens

    /** A new random token, URL-safe, 43 characters. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lowercase hex SHA-256 of the token: what the database stores. */
    public static String hashToken(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** True if the value has the shape of a token made by newToken(). */
    public static boolean isWellFormed(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    // ------------------------------------------------------- per-IP limit

    private static final Map<String, Deque<Long>> s_ipRequests = new ConcurrentHashMap<>();

    /** Records a forgot-password request from this IP. False if over the hourly limit. */
    public static boolean allowIp(String ip) {
        return allowIp(ip, System.currentTimeMillis());
    }

    static boolean allowIp(String ip, long now) {
        if (ip == null) ip = "";
        long hourAgo = now - 60 * 60 * 1000L;
        if (s_ipRequests.size() > 10_000) {
            // Drop idle entries so the map cannot grow without bound.
            s_ipRequests.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long last = e.getValue().peekLast();
                    return last == null || last < hourAgo;
                }
            });
        }
        Deque<Long> times = s_ipRequests.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < hourAgo) times.pollFirst();
            if (times.size() >= MAX_PER_IP_PER_HOUR) return false;
            times.addLast(now);
            return true;
        }
    }

    /** Tests only. */
    static void resetIpLimits() {
        s_ipRequests.clear();
    }

    // ------------------------------------------------------------ sending

    /**
     * Create a reset token for the login and email the link.
     * intro is one plain-text sentence placed before the link (escaped here).
     * Returns SENT, NO_EMAIL, RATE_LIMITED, MAIL_FAILED or ERROR.
     */
    public static String sendResetLink(Connection connection, int loginId, String displayName, String email,
                                       String ip, String subject, String intro) {
        return sendResetLink(connection, loginId, displayName, email, ip, subject, intro, VALID_MINUTES, false);
    }

    /** required: the account must reset (campaign, or login refused), so the email must not say it can be ignored. */
    public static String sendResetLink(Connection connection, int loginId, String displayName, String email,
                                       String ip, String subject, String intro, int validMinutes, boolean required) {
        if (email == null || !email.contains("@")) return NO_EMAIL;
        try {
            PasswordResetDb db = new PasswordResetDb(connection);
            if (db.countRecentRequests(loginId) >= MAX_PER_LOGIN_PER_HOUR) {
                logEvent("reset link not sent (more than " + MAX_PER_LOGIN_PER_HOUR + " this hour) login:" + loginId);
                return RATE_LIMITED;
            }
            String token = newToken();
            db.insertToken(loginId, hashToken(token), validMinutes, ip);
            String link = AntwebProps.getSecureDomainApp() + "/resetPassword.do?token=" + token;
            boolean sent = Emailer.sendTransactional(email, subject, emailBody(displayName, intro, link, validMinutes, required));
            logEvent("reset link " + (sent ? "emailed" : "NOT emailed") + " login:" + loginId + " subject:" + subject);
            return sent ? SENT : MAIL_FAILED;
        } catch (SQLException e) {
            s_log.error("sendResetLink() login:" + loginId + " e:" + e);
            return ERROR;
        }
    }

    static String validFor(int minutes) {
        if (minutes % (24 * 60) == 0) { int d = minutes / (24 * 60); return d == 1 ? "24 hours" : d + " days"; }
        if (minutes % 60 == 0) { int h = minutes / 60; return h == 1 ? "1 hour" : h + " hours"; }
        return minutes + " minutes";
    }

    static String emailBody(String displayName, String intro, String link) {
        return emailBody(displayName, intro, link, VALID_MINUTES, false);
    }

    static String emailBody(String displayName, String intro, String link, int validMinutes, boolean required) {
        String login = AntwebProps.getSecureDomainApp() + "/login.do";
        String hello = displayName == null || displayName.trim().isEmpty() ? "Hello," : "Hello " + Html.esc(displayName.trim()) + ",";
        return hello + "<br><br>\n"
            + (intro == null ? "" : Html.esc(intro) + "<br><br>\n")
            + "To set a new password for your AntWeb account, open this link (it works for " + validFor(validMinutes) + "):<br>\n"
            + "<a href=\"" + Html.escAttr(link) + "\">" + Html.esc(link) + "</a><br><br>\n"
            + "The link works only once. Your new password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long.<br><br>\n"
            + "If the link has expired, go to <a href=\"" + Html.escAttr(login) + "\">" + Html.esc(login) + "</a>,"
            + " click \"Forgot your password?\" and enter your email address: a new link arrives within a minute.<br><br>\n"
            // Only a link someone asked for (forgot password) can be ignored; a required
            // reset (campaign, or login refused until reset) must not say so.
            + (!required ? "If you did not ask for this, you can ignore this email.<br><br>\n"
                : "This is required: your old AntWeb password no longer works, so you need to set a new one before you can log in.<br><br>\n")
            + "AntWeb<br>California Academy of Sciences";
    }

    // One background thread sends forgot-password mail, so the page answers at
    // once and takes the same time whether or not the account exists.
    private static final ThreadPoolExecutor s_mailExecutor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200), r -> {
            Thread t = new Thread(r, "password-reset-mail");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.DiscardPolicy());

    /**
     * Forgot-password request: if a login has this username or email address,
     * email it a reset link. Runs in the background; the caller always shows
     * the same answer. The identifier is not logged.
     */
    public static void requestResetAsync(final String identifier, final String ip) {
        s_mailExecutor.execute(() -> {
            try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
                PasswordResetDb.Account a = new PasswordResetDb(c).findAccount(identifier);
                if (a == null) {
                    logEvent("forgot-password request with no matching account");
                    return;
                }
                if (!a.hasEmail()) {
                    logEvent("forgot-password request for login:" + a.id + " which has no email address");
                    return;
                }
                sendResetLink(c, a.id, a.getDisplayName(), a.email, ip, SUBJECT_RESET,
                    "Someone, probably you, asked to reset the password of your AntWeb account (username: " + a.name + ").");
            } catch (Exception e) {
                s_log.error("requestResetAsync() e:" + e);
            }
        });
    }

    // ------------------------------------------------- admin campaign send

    private static final AtomicBoolean s_campaignRunning = new AtomicBoolean(false);
    private static volatile String s_campaignStatus = null;

    public static boolean isCampaignRunning() {
        return s_campaignRunning.get();
    }

    /** Progress or result of the last campaign send (since the last restart), or null. */
    public static String getCampaignStatus() {
        return s_campaignStatus;
    }

    /**
     * Email a reset link to each campaign account (PasswordResetDb.getCampaignAccounts()),
     * about 10 a second, in the background. False if a send is already running.
     */
    public static boolean startCampaignSend(final String adminName, final int adminId) {
        if (!s_campaignRunning.compareAndSet(false, true)) return false;
        s_campaignStatus = "Starting...";
        Thread t = new Thread(() -> {
            int sent = 0, noEmail = 0, limited = 0, failed = 0, total = 0;
            try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
                List<PasswordResetDb.Account> accounts = new PasswordResetDb(c).getCampaignAccounts();
                total = accounts.size();
                for (PasswordResetDb.Account a : accounts) {
                    if (!a.hasEmail()) {
                        noEmail++;
                    } else {
                        String r = sendResetLink(c, a.id, a.getDisplayName(), a.email, "campaign:" + adminId, SUBJECT_CAMPAIGN,
                            "As a security precaution, AntWeb is asking all curators and administrators to set a new password.",
                            CAMPAIGN_VALID_MINUTES, true);
                        if (SENT.equals(r)) sent++;
                        else if (RATE_LIMITED.equals(r)) limited++;
                        else failed++;
                        Thread.sleep(100);  // about 10 a second
                    }
                    s_campaignStatus = "Running: " + (sent + noEmail + limited + failed) + " of " + total
                        + " done (sent " + sent + ", no email " + noEmail + ", rate limited " + limited + ", failed " + failed + ")";
                }
            } catch (Exception e) {
                s_log.error("startCampaignSend() e:" + e);
                failed++;
            } finally {
                String summary = "campaign send by " + adminName + " (id " + adminId + ") finished " + DateUtil.getFormatDateTimeStr()
                    + ": accounts " + total + ", sent " + sent + ", skipped (no email) " + noEmail
                    + ", skipped (rate limited) " + limited + ", failed " + failed;
                s_campaignStatus = summary;
                logEvent(summary);
                s_campaignRunning.set(false);
            }
        }, "password-reset-campaign");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /** j***@d***.org style, for admin screens. */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        int at = email.indexOf('@');
        if (at <= 0) return email.charAt(0) + "***";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String domainHead = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        String head = domainHead.isEmpty() ? "" : domainHead.charAt(0) + "***";
        return local.charAt(0) + "***@" + head + tld;
    }

    /** One line in admin.log. Never pass tokens or passwords. */
    public static void logEvent(String text) {
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " password: " + text);
    }

    // ----------------------------------------------------- session version

    private static final long CACHE_MS = 60_000L;
    // loginId -> {version, fetchedAtMillis}; version Long.MIN_VALUE = unknown (column missing, db error).
    private static final Map<Integer, long[]> s_versions = new ConcurrentHashMap<>();
    private static final long UNKNOWN = Long.MIN_VALUE;

    /** Drop the cached version, so the next check reads the database. */
    public static void forgetVersion(int loginId) {
        s_versions.remove(loginId);
    }

    /** Remember a version just written, so the change takes effect at once. */
    public static void noteVersion(int loginId, int version) {
        s_versions.put(loginId, new long[] {version, System.currentTimeMillis()});
    }

    /** The stored session_version (cached for a minute), or null if it cannot be read. */
    public static Integer currentVersion(int loginId) {
        long now = System.currentTimeMillis();
        long[] cached = s_versions.get(loginId);
        if (cached == null || now - cached[1] > CACHE_MS) {
            long v = UNKNOWN;
            try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
                v = new PasswordResetDb(c).getSessionVersion(loginId);
            } catch (Exception e) {
                s_log.debug("currentVersion() login:" + loginId + " e:" + e);
            }
            cached = new long[] {v, now};
            s_versions.put(loginId, cached);
        }
        return cached[0] == UNKNOWN ? null : (int) cached[0];
    }

    /** Store the login's current session_version in the session (call at login). */
    public static void stampSession(HttpSession session, int loginId) {
        if (session == null || loginId <= 0) return;
        Integer v = currentVersion(loginId);
        if (v != null) session.setAttribute(SESSION_VERSION_ATTR, v);
    }

    /**
     * False if the session belongs to an older session_version of the login,
     * meaning it was ended by a password reset or by an administrator.
     * A session without a stamp is stamped now (sessions from before this feature).
     */
    public static boolean isSessionCurrent(HttpSession session, int loginId) {
        if (session == null || loginId <= 0) return true;
        Integer v = currentVersion(loginId);
        if (v == null) return true;  // cannot tell (e.g. column not created yet): do not log anyone out
        Object stamp = session.getAttribute(SESSION_VERSION_ATTR);
        if (!(stamp instanceof Integer)) {
            session.setAttribute(SESSION_VERSION_ATTR, v);
            return true;
        }
        return ((Integer) stamp).intValue() == v.intValue();
    }
}

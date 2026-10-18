package org.calacademy.antweb.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Base64;
import java.util.Date;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.home.PasswordResetDb;
import org.calacademy.antweb.home.RememberTokenDb;

/**
 * "Keep me logged in on this computer (30 days)".
 *
 * At login with the box ticked, the browser gets the cookie AW_REMEMBER = selector:validator
 * (Secure, HttpOnly, SameSite=Lax, 30 days). The database (remember_token) keeps the
 * selector and the SHA-256 of the validator, the login's session_version at that time,
 * and the expiry (30 days from login; not extended by use).
 *
 * When a page is requested with no login in the session, autoLogin() runs (SessionRequestFilter
 * for *.do, RememberMeFilter for "/" and *.jsp, so the home page counts too): the row is
 * found by selector, the validator hash compared in constant time,
 * and the login must still exist, not be must_reset, and have the same session_version
 * (so a password change, reset, admin "end sessions" or the campaign ends it). On success
 * the login is put in a new session exactly as at a normal login (LoginMgr.logIn()) and
 * the validator is replaced (new cookie). On any failure the row is deleted and the cookie
 * cleared. Session timeout is unchanged; the cookie only re-creates the session.
 *
 * Opening the login page (GET /login.do) with a valid cookie logs in the same way, and
 * LoginAction then goes to the target page (or home) instead of showing the form, which
 * would otherwise make a remembered user type the password again (and that login replaces
 * the token). A POST to /login.do (the form itself) and /logout.do never auto-login.
 *
 * Tokens are never logged.
 */
public final class RememberMeMgr {

    private static final Log s_log = LogFactory.getLog(RememberMeMgr.class);

    public static final String COOKIE = "AW_REMEMBER";
    public static final String PARAM = "rememberMe";
    public static final int VALID_DAYS = 30;
    public static final int SELECTOR_BYTES = 12;   // 16 base64url characters
    public static final int VALIDATOR_BYTES = 32;  // 43 base64url characters
    // After a rotation the previous validator still works this long (parallel requests).
    public static final int GRACE_SECONDS = 60;
    // Request attribute set when autoLogin() logged the user in on this request.
    public static final String AUTO_LOGGED_IN_ATTR = "rememberMeAutoLogin";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RememberMeMgr() {}

    // ------------------------------------------------------------ pure logic (unit tested)

    public static String newRandom(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String newSelector() { return newRandom(SELECTOR_BYTES); }
    public static String newValidator() { return newRandom(VALIDATOR_BYTES); }

    /** {selector, validator} from a cookie value, or null if it is not exactly that shape. */
    public static String[] parse(String value) {
        if (value == null || value.length() != 16 + 1 + 43) return null;
        if (!value.matches("[A-Za-z0-9_-]{16}:[A-Za-z0-9_-]{43}")) return null;
        return new String[] {value.substring(0, 16), value.substring(17)};
    }

    public static String format(String selector, String validator) {
        return selector + ":" + validator;
    }

    /** SHA-256 hex of the validator (same function as reset tokens). */
    public static String hash(String validator) {
        return PasswordResetMgr.hashToken(validator);
    }

    /** Constant-time comparison of two hex hashes; false if either is null. */
    public static boolean hashEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    public enum Verdict { OK, GRACE, EXPIRED, MISMATCH, NO_LOGIN, MUST_RESET, STALE }

    /**
     * Decide whether a presented validator logs in. OK: log in and rotate. GRACE: the
     * validator was just replaced by a parallel request; log in without rotating again.
     * Anything else: refuse (and delete the row).
     */
    public static Verdict check(String validator, String tokenHash, String prevHash, long remainingSecs,
                                long secsSinceRotated, boolean loginExists, boolean mustReset,
                                int tokenVersion, int loginVersion) {
        String h = hash(validator);
        boolean current = hashEquals(h, tokenHash);
        // Always compute both comparisons (no early exit on the secret).
        boolean previous = hashEquals(h, prevHash);
        if (!current && !(previous && secsSinceRotated >= 0 && secsSinceRotated <= GRACE_SECONDS)) return Verdict.MISMATCH;
        if (remainingSecs <= 0) return Verdict.EXPIRED;
        if (!loginExists) return Verdict.NO_LOGIN;
        if (mustReset) return Verdict.MUST_RESET;
        if (tokenVersion != loginVersion) return Verdict.STALE;
        return current ? Verdict.OK : Verdict.GRACE;
    }

    // ------------------------------------------------------------ cookies

    static Cookie cookie(String value, int maxAgeSecs) {
        Cookie c = new Cookie(COOKIE, value);
        c.setPath("/");
        c.setSecure(true);
        c.setHttpOnly(true);
        c.setMaxAge(maxAgeSecs);
        c.setAttribute("SameSite", "Lax");
        return c;
    }

    private static void setCookie(HttpServletResponse response, String value, long maxAgeSecs) {
        response.addCookie(cookie(value, (int) Math.max(0, Math.min(Integer.MAX_VALUE, maxAgeSecs))));
    }

    private static void clearCookie(HttpServletResponse response) {
        response.addCookie(cookie("", 0));
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** True if the login form asked to be remembered (checkbox rememberMe). */
    public static boolean isRequested(HttpServletRequest request) {
        String v = request.getParameter(PARAM);
        return v != null && !v.isEmpty() && !"false".equalsIgnoreCase(v) && !"0".equals(v);
    }

    // ------------------------------------------------------------ login / logout

    /**
     * After a successful password login with the box ticked: store a token and set the cookie.
     * Not for "log in as" (LoginAsAction does not call this).
     */
    public static void remember(HttpServletRequest request, HttpServletResponse response, Login login) {
        if (login == null || login.getId() <= 0) return;
        String selector = newSelector();
        String validator = newValidator();
        try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
            int version = new PasswordResetDb(c).getSessionVersion(login.getId());
            new RememberTokenDb(c).insert(login.getId(), selector, hash(validator), VALID_DAYS,
                request.getHeader("User-Agent"), version);
            setCookie(response, format(selector, validator), VALID_DAYS * 24L * 3600L);
        } catch (Exception e) {
            s_log.error("remember() login:" + login.getId() + " e:" + e);
        }
    }

    /** Logout (or a new login): delete this browser's token, if any, and clear the cookie. */
    public static void forget(HttpServletRequest request, HttpServletResponse response) {
        String value = cookieValue(request);
        if (value == null) return;
        String[] parts = parse(value);
        if (parts != null) {
            try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
                new RememberTokenDb(c).deleteBySelector(parts[0]);
            } catch (Exception e) {
                s_log.warn("forget() e:" + e);
            }
        }
        clearCookie(response);
    }

    /** True if autoLogin() should not run for this request (logout, or submitting the login form). */
    public static boolean isExcluded(String servletPath, String method) {
        if ("/logout.do".equals(servletPath)) return true;
        // The login form itself (POST) logs in with the password; opening the page (GET) may auto-login.
        return "/login.do".equals(servletPath) && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
    }

    /**
     * Per page request (SessionRequestFilter for *.do, RememberMeFilter for "/" and *.jsp):
     * if the session has no login and the request carries AW_REMEMBER, try to log in from it.
     * Costs nothing without the cookie. Returns true if it logged the user in on this request
     * (and sets the request attribute AUTO_LOGGED_IN_ATTR).
     */
    public static boolean autoLogin(HttpServletRequest request, HttpServletResponse response) {
        String value = cookieValue(request);
        if (value == null) return false;
        if (isExcluded(request.getServletPath(), request.getMethod())) return false;
        HttpSession existing = request.getSession(false);
        if (existing != null && LoginMgr.getAccessLogin(request) != null) return false;

        String[] parts = parse(value);
        if (parts == null) {
            clearCookie(response);
            return false;
        }
        String selector = parts[0], validator = parts[1];
        try (Connection c = DBUtilSimple.getDataSource().getConnection()) {
            RememberTokenDb db = new RememberTokenDb(c);
            RememberTokenDb.Token t = db.findBySelector(selector);
            if (t == null) {
                clearCookie(response);
                return false;
            }
            Verdict v = check(validator, t.tokenHash, t.prevHash, t.remainingSecs, t.secsSinceRotated,
                t.loginExists, t.mustReset, t.sessionVersion, t.loginSessionVersion);
            Login login = null;
            if (v == Verdict.OK || v == Verdict.GRACE) {
                // Fully loaded, as at a password login (getLoginByName): a curator's species lists too.
                LoginDb loginDb = new LoginDb(c);
                login = loginDb.getLogin(t.loginId);
                if (login != null) loginDb.postInstantiate(login);
                if (login == null) v = Verdict.NO_LOGIN;
            }
            if (login == null) {
                db.delete(t.id);
                clearCookie(response);
                if (v == Verdict.MISMATCH) {
                    // A wrong validator for a real selector: possibly a stolen, already-used cookie.
                    LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr()
                        + " remember-me: validator mismatch, token deleted for login:" + t.loginId);
                }
                s_log.info("autoLogin() refused (" + v + ") login:" + t.loginId);
                return false;
            }

            // Log in exactly as a password login does.
            PasswordResetMgr.noteVersion(login.getId(), t.loginSessionVersion);
            LoginMgr.logIn(request, login);
            request.setAttribute(AUTO_LOGGED_IN_ATTR, Boolean.TRUE);
            new LoginDb(c).updateLastLogin(login);
            LogMgr.appendLog("logins.txt", login.getName() + " - " + new Date() + " (remembered)");

            if (v == Verdict.OK) {
                String newValidator = newValidator();
                if (db.rotate(t.id, t.tokenHash, hash(newValidator), request.getHeader("User-Agent"))) {
                    setCookie(response, format(selector, newValidator), t.remainingSecs);
                }
                // else a parallel request rotated it first; its response carries the new cookie.
            } else {
                db.touch(t.id);
            }
            return true;
        } catch (Exception e) {
            s_log.error("autoLogin() e:" + e);
        }
        return LoginMgr.getAccessLogin(request) != null && request.getAttribute(AUTO_LOGGED_IN_ATTR) != null;
    }
}

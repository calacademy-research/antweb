package org.calacademy.antweb.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Cross-site request forgery protection for logged-in users.
 *
 * Each session gets one random token (32 bytes from SecureRandom, base64url). Every form that
 * POSTs carries it as the hidden field "_csrf" (Csrf.field(request) in a JSP). Multipart forms
 * (file uploads) also carry it in the form's action URL (Csrf.action(request, url)), because the
 * filter cannot read a multipart body without consuming it; only multipart POSTs may present it
 * there. SessionRequestFilter refuses a POST from a logged-in user whose token is missing or
 * wrong, or whose Origin/Referer is another site.
 */
public final class Csrf {

    public static final String PARAM = "_csrf";
    public static final String HEADER = "X-CSRF-Token";
    static final String SESSION_ATTR = "antweb.csrfToken";

    private static final SecureRandom RANDOM = new SecureRandom();

    // Hosts whose pages may POST to AntWeb besides the request's own host.
    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "www.antweb.org", "antweb.org", "staging.antweb.org", "localhost");

    // Pages that take a POST from a user who may not be logged in yet, or that are protected
    // by their own one-time token.
    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/login.do", "/forgotPassword.do", "/resetPassword.do");

    private Csrf() {}

    /** A new random token: 32 bytes, base64url without padding (43 characters). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The session's token, created on first use. */
    public static String token(HttpServletRequest request) {
        HttpSession session;
        try {
            session = request.getSession(true);
        } catch (IllegalStateException e) {
            // Response already committed and no session yet: nobody is logged in, nothing to protect.
            return "";
        }
        synchronized (session) {
            Object t = session.getAttribute(SESSION_ATTR);
            if (t instanceof String && !((String) t).isEmpty()) return (String) t;
            String token = newToken();
            session.setAttribute(SESSION_ATTR, token);
            return token;
        }
    }

    /** The hidden form field. Put it inside every form with method="post". */
    public static String field(HttpServletRequest request) {
        return "<input type=\"hidden\" name=\"" + PARAM + "\" value=\"" + token(request) + "\">";
    }

    /**
     * A small inline POST form that looks like a link, for actions that change data (they refuse
     * GET, so a cross-site link can not trigger them). url is the action with its query string;
     * labelHtml is trusted markup (text or an img tag). confirm, if not null, asks first.
     */
    public static String postButton(HttpServletRequest request, String url, String labelHtml, String confirm) {
        String onsubmit = confirm == null ? "" : " onsubmit=\"return confirm('" + Html.js(confirm) + "')\"";
        return "<form method=\"post\" action=\"" + Html.escAttr(url) + "\" style=\"display:inline;margin:0\"" + onsubmit + ">"
            + field(request)
            + "<button type=\"submit\" style=\"background:none;border:none;padding:0;margin:0;color:#0645ad;"
            + "font:inherit;cursor:pointer;text-decoration:underline\">" + labelHtml + "</button></form>";
    }

    public static String postButton(HttpServletRequest request, String url, String labelHtml) {
        return postButton(request, url, labelHtml, null);
    }

    /**
     * The url with the token added as a query parameter (any old _csrf parameter removed).
     * Use for the action of multipart (file upload) forms.
     */
    public static String action(HttpServletRequest request, String url) {
        if (url == null) url = "";
        String base = stripParam(url);
        String sep = base.contains("?") ? (base.endsWith("?") || base.endsWith("&") ? "" : "&") : "?";
        return base + sep + PARAM + "=" + token(request);
    }

    /** Remove any _csrf parameter from a url (keeps the fragment). */
    public static String stripParam(String url) {
        String fragment = "";
        int hash = url.indexOf('#');
        if (hash >= 0) {
            fragment = url.substring(hash);
            url = url.substring(0, hash);
        }
        int q = url.indexOf('?');
        if (q < 0) return url + fragment;
        StringBuilder kept = new StringBuilder();
        for (String pair : url.substring(q + 1).split("&")) {
            if (pair.isEmpty() || pair.equals(PARAM) || pair.startsWith(PARAM + "=")) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(pair);
        }
        String base = url.substring(0, q);
        return (kept.length() > 0 ? base + "?" + kept : base) + fragment;
    }

    /** Constant-time comparison. False if either is null or empty. */
    public static boolean matches(String expected, String given) {
        if (expected == null || given == null || expected.isEmpty() || given.isEmpty()) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** True for the paths that never need a token. */
    public static boolean isExempt(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null) return false;
        return EXEMPT_PATHS.contains(path) || path.startsWith("/v3.1/");
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String ct = request.getContentType();
        return ct != null && ct.toLowerCase().startsWith("multipart/");
    }

    /** The first _csrf value in the query string, read without touching the request body. */
    public static String queryParam(String queryString) {
        if (queryString == null) return null;
        for (String pair : queryString.split("&")) {
            if (pair.startsWith(PARAM + "=")) {
                try {
                    return URLDecoder.decode(pair.substring(PARAM.length() + 1), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * The token the request presents: the X-CSRF-Token header first; then, for a multipart (file
     * upload) form, the query string (Csrf.action puts it there because the body can not be read
     * here); for any other POST, the form body only: a token in the URL of a normal form is never
     * used, so a token can not be handed to another action through a link or a form action built
     * from request input.
     */
    static String presented(HttpServletRequest request) {
        String given = request.getHeader(HEADER);
        if (given != null && !given.isEmpty()) return given;
        if (isMultipart(request)) return queryParam(request.getQueryString());
        // Tomcat lists query-string values before body values: skip the ones from the URL.
        int inQuery = queryCount(request.getQueryString());
        if (inQuery == 0) return request.getParameter(PARAM);
        String[] all = request.getParameterValues(PARAM);
        return (all != null && all.length > inQuery) ? all[inQuery] : null;
    }

    /** How many _csrf parameters the query string has. */
    public static int queryCount(String queryString) {
        if (queryString == null) return 0;
        int n = 0;
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            try {
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);  // as the server reads it (%5Fcsrf is _csrf)
            } catch (IllegalArgumentException e) {
                // left as is
            }
            if (PARAM.equals(name)) n++;
        }
        return n;
    }

    /** Host of an Origin or Referer header value, lower case; null if it can not be read. */
    public static String hostOf(String headerValue) {
        if (headerValue == null || headerValue.isEmpty() || "null".equals(headerValue)) return null;
        try {
            String host = new URI(headerValue.trim()).getHost();
            return host == null ? null : host.toLowerCase();
        } catch (Exception e) {
            return "";  // present but unreadable: treated as another site
        }
    }

    /** True if host is this site (the request's own host, or a known AntWeb host). */
    static boolean isOwnHost(String host, HttpServletRequest request) {
        if (host == null) return true;
        if (ALLOWED_HOSTS.contains(host)) return true;
        String serverName = request.getServerName();
        if (serverName != null && host.equalsIgnoreCase(serverName)) return true;
        try {
            String domain = new URI(AntwebProps.getDomainApp()).getHost();
            if (domain != null && host.equalsIgnoreCase(domain)) return true;
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    /**
     * Check a POST. Returns null if it may go ahead, otherwise a short reason (no token values).
     * Called only for POSTs from logged-in users on paths that are not exempt.
     */
    public static String check(HttpServletRequest request) {
        // Same-origin check first: a browser sends Origin (and usually Referer) on a POST.
        String origin = request.getHeader("Origin");
        String originHost = hostOf(origin);
        if (originHost != null && !isOwnHost(originHost, request)) return "cross-site Origin";
        if (originHost == null) {
            String refererHost = hostOf(request.getHeader("Referer"));
            if (refererHost != null && !isOwnHost(refererHost, request)) return "cross-site Referer";
        }

        HttpSession session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute(SESSION_ATTR);
        String given = presented(request);
        if (given == null || given.isEmpty()) return "missing token";
        if (!(expected instanceof String) || !matches((String) expected, given)) return "token mismatch";
        return null;
    }

    /** True for a page-text editor save (urlencoded, so its fields can be read here). */
    public static boolean isEditorSave(HttpServletRequest request) {
        return !isMultipart(request) && "true".equals(request.getParameter("isSaveEditField"));
    }

    /**
     * The 403 answer for a page-text editor save: the curator's text is shown again so it is
     * never lost (it is also kept as a draft in their browser by editSafety.js).
     */
    public static void denyEditorSave(HttpServletResponse response, String text) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Not saved: request refused</title></head>"
            + "<body style=\"font-family:sans-serif;margin:2em;max-width:60em\"><h2>Your text was not saved</h2>"
            + "<p>This form could not be checked as coming from AntWeb (403). This can happen if the page was open"
            + " for a long time or you logged in again in another tab.</p>"
            + "<p><b>Your text is below</b>, and it is also kept as a draft in this browser: go back, reload the page,"
            + " open the field with [Edit] and choose Restore, then Save. Or copy the text from this box.</p>"
            + "<textarea id=\"keptText\" readonly style=\"width:100%;height:20em\">" + Html.esc(text) + "</textarea>"
            + "</body></html>");
    }

    /** The 403 answer. */
    public static void deny(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Request refused</title></head>"
            + "<body style=\"font-family:sans-serif;margin:2em\"><h2>Request refused (403)</h2>"
            + "<p>This form could not be checked as coming from AntWeb. This can happen if the page was open"
            + " for a long time or you logged in again in another tab.</p>"
            + "<p>Please go back, reload the page, and try again.</p></body></html>");
    }
}

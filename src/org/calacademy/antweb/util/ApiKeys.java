package org.calacademy.antweb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Free registered API keys (step 6, PRs 4 and 5; decided by Brian Fisher 2026-11-08): the rules that need no
 * database, so they can be unit tested (test/org/calacademy/antweb/security/ApiKeysTest.java).
 *
 *   - a key is "aw_" + 32 random URL-safe characters (SecureRandom); only its SHA-256 (api_key.key_hash) and its
 *     first 7 characters (api_key.prefix, for the admin page) are stored, never the key itself;
 *   - the email confirmation token is 32 random bytes (43 characters); only its SHA-256 is stored
 *     (api_key.confirm_token_hash), valid for 24 hours, used once;
 *   - registrations are throttled: 5 an hour per IP address (in memory), 3 a day per email address and 100 a day in
 *     all (counted in api_key);
 *   - the levels, statuses and default limits shown on the admin page;
 *   - CSV fields for the admin page's download;
 *   - etc/api-keys.properties on the server: apiKeys.open=true opens registration (apiKey.do says
 *     "Registration opens soon" without it), so this can be deployed before the API checks keys (PR 3).
 *
 * Keys and tokens are never logged or emailed (the key is shown once on the confirmation page).
 * Database side: home/ApiKeyDb. Pages: ApiKeyAction (apiKey.do), ApiKeyConfirmAction (apiKeyConfirm.do),
 * curate/ApiKeysAction (apiKeys.do, admins). Mail and the confirmation step: ApiKeyMgr.
 */
public final class ApiKeys {

    private static final Log s_log = LogFactory.getLog(ApiKeys.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {}

    public static final String KEY_START = "aw_";
    public static final int KEY_RANDOM_CHARS = 32;
    public static final int PREFIX_LENGTH = 7;
    public static final int CONFIRM_HOURS = 24;

    public static final int MAX_PER_IP_PER_HOUR = 5;
    public static final int MAX_PER_EMAIL_PER_DAY = 3;
    public static final int MAX_PER_DAY_ALL = 100;

    public static final String PENDING = "pending";
    public static final String ACTIVE = "active";
    public static final String REVOKED = "revoked";
    public static final List<String> STATUSES = List.of(PENDING, ACTIVE, REVOKED);

    public static final String REGISTERED = "registered";
    public static final String COLLABORATOR = "collaborator";
    public static final String ADMIN = "admin";
    public static final List<String> LEVELS = List.of(REGISTERED, COLLABORATOR, ADMIN);

    /** Null-safe (List.of(...).contains(null) throws). */
    public static boolean isStatus(String s) {
        return s != null && STATUSES.contains(s);
    }

    public static boolean isLevel(String s) {
        return s != null && LEVELS.contains(s);
    }

    // Default limits (Brian Fisher, 2026-11-08). The API (api/v3.1/api.py, PR 3) applies them; a limit set on a key
    // replaces the default for that key.
    public static final int NO_KEY_PER_MINUTE = 5;
    public static final int NO_KEY_PER_DAY = 50;
    public static final int NO_KEY_MAX_RECORDS = 25;
    public static final int REGISTERED_PER_MINUTE = 60;
    public static final int REGISTERED_PER_DAY = 5000;
    public static final int REGISTERED_MAX_RECORDS = 500;
    public static final int REGISTERED_RECORDS_PER_DAY = 50000;
    /** The largest limit an admin may set on a key (a typing error such as 50000000 is refused). */
    public static final int MAX_LIMIT_VALUE = 10_000_000;

    public static final int MAX_NAME = 200;
    public static final int MAX_EMAIL = 254;
    public static final int MAX_INSTITUTION = 300;
    public static final int MAX_PROJECT = 4000;
    public static final int MAX_NOTE = 4000;

    public static final String TERMS_URL = "https://www.antweb.org/apiV3.1.do#terms";
    public static final String DOCS_URL = "https://www.antweb.org/apiV3.1.do";
    public static final String HEADER = "X-API-Key";
    /** Who gets a copy of every registration (Brian Fisher, 2026-11-08: bfisher@calacademy.org only). */
    public static final String COPY_TO = "bfisher@calacademy.org";

    private static final Pattern KEY_RE = Pattern.compile("aw_[A-Za-z0-9_-]{" + KEY_RANDOM_CHARS + "}");
    private static final Pattern TOKEN_RE = Pattern.compile("[A-Za-z0-9_-]{43}");
    // A plain address: no spaces, quotes, angle brackets or line breaks (it goes into a mail header).
    private static final Pattern EMAIL_RE = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");

    // ---------------------------------------------------------------- keys and tokens

    /** A new key: "aw_" + 32 URL-safe random characters (24 random bytes). */
    public static String newKey() {
        byte[] bytes = new byte[KEY_RANDOM_CHARS * 3 / 4];
        RANDOM.nextBytes(bytes);
        return KEY_START + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isWellFormedKey(String key) {
        return key != null && KEY_RE.matcher(key).matches();
    }

    /** The part of a key stored and shown: its first 7 characters ("aw_" and 4 more). */
    public static String prefixOf(String key) {
        return key.substring(0, PREFIX_LENGTH);
    }

    /** A new email confirmation token: 32 random bytes, base64url, 43 characters. */
    public static String newConfirmToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isWellFormedToken(String token) {
        return token != null && TOKEN_RE.matcher(token).matches();
    }

    /**
     * Stand-ins for a registration not confirmed yet: key_hash and prefix are required and unique, but the key is made
     * only when the email link is followed. The prefix starts with "p_" (a key starts with "aw_"), so no key matches it.
     */
    public static String placeholderPrefix() {
        byte[] bytes = new byte[9];
        RANDOM.nextBytes(bytes);
        return "p_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String placeholderHash() {
        return sha256("pending:" + newConfirmToken());
    }

    /** Lowercase hex SHA-256: what the database stores for a key or a token. */
    public static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ---------------------------------------------------------------- the confirmation link

    /** What a confirmation link can do: VALID (show the button), or why not. */
    public enum ConfirmState { VALID, EXPIRED, USED, CANCELLED, UNKNOWN }

    /** The state of a registration found by its token (status null = no registration has this token). */
    public static ConfirmState confirmState(String status, LocalDateTime expires, LocalDateTime now) {
        if (status == null) return ConfirmState.UNKNOWN;
        if (ACTIVE.equals(status)) return ConfirmState.USED;
        if (REVOKED.equals(status)) return ConfirmState.CANCELLED;
        if (!PENDING.equals(status)) return ConfirmState.UNKNOWN;
        if (expires == null || !now.isBefore(expires)) return ConfirmState.EXPIRED;
        return ConfirmState.VALID;
    }

    // ---------------------------------------------------------------- throttles

    /** At most max events per key in a sliding window (in memory; tests pass the time). */
    public static final class SlidingWindow {
        private final int max;
        private final long windowMs;
        private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

        public SlidingWindow(int max, long windowMs) {
            this.max = max;
            this.windowMs = windowMs;
        }

        /** Counts the event and returns true, or returns false (and counts nothing) when over the limit. */
        public boolean allow(String key, long now) {
            if (key == null) key = "";
            long since = now - windowMs;
            if (events.size() > 10_000) {
                // Drop idle entries so the map cannot grow without bound.
                events.entrySet().removeIf(e -> {
                    synchronized (e.getValue()) {
                        Long last = e.getValue().peekLast();
                        return last == null || last <= since;
                    }
                });
            }
            Deque<Long> times = events.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (times) {
                while (!times.isEmpty() && times.peekFirst() <= since) times.pollFirst();
                if (times.size() >= max) return false;
                times.addLast(now);
                return true;
            }
        }

        public void clear() {
            events.clear();
        }
    }

    private static final SlidingWindow s_ip = new SlidingWindow(MAX_PER_IP_PER_HOUR, 60 * 60 * 1000L);

    /** Records a registration from this IP address; false if it already made 5 in the last hour. */
    public static boolean allowIp(String ip) {
        return s_ip.allow(ip, System.currentTimeMillis());
    }

    /** Tests only. */
    static void resetIpLimit() {
        s_ip.clear();
    }

    /** Registrations already made today with this email (last 24 hours) and in all: may another be made? */
    public static boolean allowEmail(int sameEmailLastDay) {
        return sameEmailLastDay < MAX_PER_EMAIL_PER_DAY;
    }

    public static boolean allowAll(int allLastDay) {
        return allLastDay < MAX_PER_DAY_ALL;
    }

    // ---------------------------------------------------------------- the form

    /** What a person typed on apiKey.do. */
    public static final class Registration {
        public String name = "";
        public String email = "";
        public String institution = "";
        public String project = "";
        public boolean agreed;

        public Registration() {}

        public Registration(String name, String email, String institution, String project, boolean agreed) {
            this.name = clean(name, false);
            this.email = clean(email, false).toLowerCase();
            this.institution = clean(institution, false);
            this.project = clean(project, true);
            this.agreed = agreed;
        }

        /** Null if the form can be saved, else what to correct (plain words, shown on the form). */
        public String problem() {
            if (name.isEmpty()) return "Please enter your name.";
            if (name.length() > MAX_NAME) return "Your name is too long (at most " + MAX_NAME + " characters).";
            if (email.isEmpty()) return "Please enter your email address.";
            if (!isValidEmail(email)) return "Please enter a valid email address.";
            if (institution.length() > MAX_INSTITUTION) return "The institution or affiliation is too long (at most " + MAX_INSTITUTION + " characters).";
            if (project.isEmpty()) return "Please describe your project: what you will use the API for.";
            if (project.length() > MAX_PROJECT) return "The project description is too long (at most " + MAX_PROJECT + " characters).";
            if (!agreed) return "Please read the terms of use and tick the box to agree.";
            return null;
        }
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= MAX_EMAIL && EMAIL_RE.matcher(email).matches();
    }

    /** Trim and remove control characters (line breaks and tabs are kept only where multiLine). Null becomes "". */
    public static String clean(String value, boolean multiLine) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r') continue;
            if (c == '\n' || c == '\t') {
                sb.append(multiLine ? c : ' ');
            } else if (!Character.isISOControl(c)) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- admin page

    /**
     * A limit typed on the admin page: blank = the level's default (null); otherwise a whole number from 0 to
     * MAX_LIMIT_VALUE (commas and spaces allowed, "5,000"). Anything else: IllegalArgumentException with the reason.
     */
    public static Integer parseLimit(String label, String value) {
        if (value == null) return null;
        String v = value.replace(",", "").replace(" ", "").trim();
        if (v.isEmpty()) return null;
        if (!v.matches("\\d{1,9}")) throw new IllegalArgumentException(label + ": \"" + value.trim() + "\" is not a whole number.");
        int n = Integer.parseInt(v);
        if (n > MAX_LIMIT_VALUE) throw new IllegalArgumentException(label + ": at most " + String.format("%,d", MAX_LIMIT_VALUE) + ".");
        return n;
    }

    /** One CSV field: quoted when it holds a comma, quote or line break; a leading = + - @ is made plain text for spreadsheets. */
    public static String csvField(Object value) {
        String s = value == null ? "" : value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** One CSV line (with \r\n, as RFC 4180 and spreadsheets expect). */
    public static String csvLine(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvField(values[i]));
        }
        return sb.append("\r\n").toString();
    }

    // ---------------------------------------------------------------- settings

    static final String CONFIG = "/antweb/deploy/etc/api-keys.properties";
    private static Properties s_props = new Properties();
    private static long s_loaded = 0;

    /** True when etc/api-keys.properties on the server has apiKeys.open=true (read again every minute). */
    public static boolean isOpen() {
        return isOpen(props());
    }

    public static boolean isOpen(Properties p) {
        return p != null && "true".equalsIgnoreCase(p.getProperty("apiKeys.open", "").trim());
    }

    private static synchronized Properties props() {
        long now = System.currentTimeMillis();
        if (now - s_loaded > 60_000) {
            s_loaded = now;
            Properties p = new Properties();
            File f = new File(CONFIG);
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                } catch (Exception e) {
                    s_log.error("props() could not read " + CONFIG + " e:" + e);
                }
            }
            s_props = p;
        }
        return s_props;
    }
}

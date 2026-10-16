package org.calacademy.antweb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Cloudflare Turnstile: a free, privacy-friendly bot check (usually just a
 * checkbox, often invisible) shown on the Create Account form.
 *
 * Configured in etc/turnstile.properties on the server (~/antweb/etc/, which
 * the container sees as /antweb/deploy/etc/; the file is in .gitignore and
 * never in the repository):
 *   siteKey=...   public key, put in the page
 *   secret=...    secret key, used to check the answer with Cloudflare
 * Changes are picked up within a minute, without a restart. With no file
 * (or empty values) the check is off and Create Account works as before.
 * When the file is present but the answer cannot be verified (Cloudflare
 * unreachable, timeout, unreadable file), signup is refused (fail closed).
 */
public final class Turnstile {

    private static final Log s_log = LogFactory.getLog(Turnstile.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    public static final String RESPONSE_PARAM = "cf-turnstile-response";

    private Turnstile() {}

    static final String CONFIG = "/antweb/deploy/etc/turnstile.properties";
    private static Properties s_props = new Properties();
    private static long s_loaded = 0;
    private static boolean s_fileExists = false;
    private static boolean s_loadFailed = false;

    /** Outcome of a check: PASSED (or check off), FAILED (bad/missing answer), UNAVAILABLE (could not verify). */
    public enum Result { PASSED, FAILED, UNAVAILABLE }

    private static synchronized Properties props() {
        long now = System.currentTimeMillis();
        if (now - s_loaded > 60_000) {
            s_loaded = now;
            Properties p = new Properties();
            File f = new File(CONFIG);
            boolean failed = false;
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                } catch (Exception e) {
                    failed = true;
                    s_log.error("props() could not read " + CONFIG + " e:" + e);
                }
            }
            s_fileExists = f.exists();
            s_loadFailed = failed;
            s_props = p;
        }
        return s_props;
    }

    public static String getSiteKey() {
        return props().getProperty("siteKey", "").trim();
    }

    private static String getSecret() {
        return props().getProperty("secret", "").trim();
    }

    public static boolean isEnabled() {
        return !getSiteKey().isEmpty() && !getSecret().isEmpty();
    }

    /** True if the check is off, or if Cloudflare confirms the visitor passed it. */
    public static boolean passed(HttpServletRequest request) {
        return check(request) == Result.PASSED;
    }

    public static Result check(HttpServletRequest request) {
        props();  // refresh settings
        synchronized (Turnstile.class) {
            // File present but unreadable (or a directory): treat as on, and fail closed.
            if (s_fileExists && (s_loadFailed || !new File(CONFIG).isFile())) return Result.UNAVAILABLE;
        }
        if (!isEnabled()) return Result.PASSED;
        String token = request.getParameter(RESPONSE_PARAM);
        if (token == null || token.isEmpty()) return Result.FAILED;
        String body = "secret=" + enc(getSecret()) + "&response=" + enc(token);
        String ip = request.getHeader("CF-Connecting-IP");  // the visitor, as Cloudflare saw them
        if (ip != null && !ip.isEmpty()) body += "&remoteip=" + enc(ip);
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) URI.create(VERIFY_URL).toURL().openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(5000);
            con.setReadTimeout(10000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = con.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream in = con.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject result = JsonParser.parseString(json).getAsJsonObject();
                boolean success = result.has("success") && result.get("success").getAsBoolean();
                if (!success) s_log.info("check() refused: " + result.get("error-codes"));
                return success ? Result.PASSED : Result.FAILED;
            }
        } catch (Exception e) {
            // Cloudflare unreachable or timed out: fail closed; the person is asked to try again.
            s_log.error("check() could not verify with Cloudflare e:" + e);
            return Result.UNAVAILABLE;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

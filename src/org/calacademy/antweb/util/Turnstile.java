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
 */
public final class Turnstile {

    private static final Log s_log = LogFactory.getLog(Turnstile.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    public static final String RESPONSE_PARAM = "cf-turnstile-response";

    private Turnstile() {}

    static final String CONFIG = "/antweb/deploy/etc/turnstile.properties";
    private static Properties s_props = new Properties();
    private static long s_loaded = 0;

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
        if (!isEnabled()) return true;
        String token = request.getParameter(RESPONSE_PARAM);
        if (token == null || token.isEmpty()) return false;
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
                if (!success) s_log.info("passed() refused: " + result.get("error-codes"));
                return success;
            }
        } catch (Exception e) {
            // Cloudflare unreachable: let the person through rather than lock everyone out.
            s_log.error("passed() could not reach Cloudflare e:" + e);
            return true;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

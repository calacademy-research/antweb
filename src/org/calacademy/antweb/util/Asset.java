package org.calacademy.antweb.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Cache-busting for local scripts and stylesheets.
 *
 * JSPs write  src="<%= domainApp %><%= org.calacademy.antweb.util.Asset.url(application, "/common/x.js") %>"
 * which gives  /common/x.js?v=1a2b3c4d , where the token is the first 8 hex digits of the
 * SHA-256 of the deployed file. When the file changes, the URL changes, so browsers and
 * Cloudflare fetch the new copy at once instead of keeping the old one for its max-age
 * (see AssetCacheFilter). The hash is computed once per file and recomputed only when the
 * file's modification time or size changes (one stat per call otherwise).
 *
 * If the file cannot be read (missing, packed WAR) the token is FALLBACK, so the page still renders.
 */
public final class Asset {

    private static final Log s_log = LogFactory.getLog(Asset.class);

    /** Used when the deployed file cannot be read. */
    public static final String FALLBACK = "0";

    private record Entry(long lastModified, long length, String token) { }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private Asset() { }

    /** The web path with ?v=token appended, e.g. "/common/base.css?v=1a2b3c4d". */
    public static String url(ServletContext context, String webPath) {
        String token = v(context, webPath);
        return webPath + (webPath.indexOf('?') >= 0 ? "&" : "?") + "v=" + token;
    }

    /** The version token for a web path such as "/common/base.css". Never null. */
    public static String v(ServletContext context, String webPath) {
        String path = normalize(webPath);
        if (path == null || context == null) return FALLBACK;
        String real;
        try {
            real = context.getRealPath(path);
        } catch (RuntimeException e) {
            real = null;
        }
        if (real == null) return FALLBACK;
        File file = new File(real);
        long modified = file.lastModified();   // 0 if missing or unreadable
        long length = file.length();
        if (modified == 0L || !file.isFile()) {
            CACHE.remove(path);
            return FALLBACK;
        }
        Entry cached = CACHE.get(path);
        if (cached != null && cached.lastModified() == modified && cached.length() == length) {
            return cached.token();
        }
        String token = hash(file);
        if (token == null) return FALLBACK;
        CACHE.put(path, new Entry(modified, length, token));
        return token;
    }

    /** "/common/x.js" style path, no query or fragment; null if not a plain local path. */
    static String normalize(String webPath) {
        if (webPath == null) return null;
        String p = webPath.trim();
        int cut = indexOfAny(p, '?', '#');
        if (cut >= 0) p = p.substring(0, cut);
        if (p.isEmpty() || p.contains("..") || p.contains("://") || p.startsWith("//") || p.indexOf('\0') >= 0) return null;
        return p.startsWith("/") ? p : "/" + p;
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a), j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }

    static String hash(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest()).substring(0, 8);
        } catch (IOException | NoSuchAlgorithmException e) {
            s_log.warn("Asset.hash() cannot read " + file + ": " + e);
            return null;
        }
    }
}

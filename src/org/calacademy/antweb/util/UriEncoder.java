package org.calacademy.antweb.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Percent-encoding for URL paths and queries.
 *
 * Replaces org.apache.commons.httpclient.util.URIUtil (commons-httpclient 3.1,
 * end of life since 2011). Same character sets (RFC 2396, as httpclient 3.1
 * used them) and the same output: characters outside the allowed set are
 * encoded as %XX (upper-case hex) of their bytes in the given charset.
 */
public final class UriEncoder {

    private UriEncoder() {}

    private static final BitSet UNRESERVED = new BitSet(128);
    private static final BitSet ABS_PATH = new BitSet(128);
    private static final BitSet QUERY = new BitSet(128);

    static {
        for (char c = 'a'; c <= 'z'; c++) UNRESERVED.set(c);
        for (char c = 'A'; c <= 'Z'; c++) UNRESERVED.set(c);
        for (char c = '0'; c <= '9'; c++) UNRESERVED.set(c);
        for (char c : "-_.!~*'()".toCharArray()) UNRESERVED.set(c);

        // abs_path = "/" path_segments; segment chars = pchar | ";" ; pchar as httpclient 3.1 used it: unreserved, ":@&=$," (not "+")
        ABS_PATH.or(UNRESERVED);
        for (char c : ":@&=$,;/".toCharArray()) ABS_PATH.set(c);

        // query = *uric ; uric = reserved | unreserved | escaped ; reserved = ";/?:@&=+$,"
        QUERY.or(UNRESERVED);
        for (char c : ";/?:@&=+$,".toCharArray()) QUERY.set(c);
    }

    /** Like URIUtil.encodePath(path, charset). */
    public static String encodePath(String path, String charset) {
        return encode(path, ABS_PATH, Charset.forName(charset));
    }

    /** Like URIUtil.encodeQuery(query): UTF-8, the httpclient default. */
    public static String encodeQuery(String query) {
        return encode(query, QUERY, StandardCharsets.UTF_8);
    }

    private static String encode(String s, BitSet allowed, Charset charset) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (byte b : s.getBytes(charset)) {
            int v = b & 0xff;
            if (v < 128 && allowed.get(v)) {
                out.append((char) v);
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit((v >> 4) & 0xf, 16)));
                out.append(Character.toUpperCase(Character.forDigit(v & 0xf, 16)));
            }
        }
        return out.toString();
    }
}

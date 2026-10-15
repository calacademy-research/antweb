package org.calacademy.antweb.util;

/**
 * HTML escaping helpers for values that come from requests or uploads.
 * Use esc() for element text and escAttr() for quoted attribute values.
 */
public final class Html {

    private Html() {}

    /** Escape for HTML text. Null becomes "". Escapes &amp; &lt; &gt; &quot; &#39;. */
    public static String esc(Object value) {
        if (value == null) return "";
        String s = value.toString();
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep;
            switch (c) {
                case '&': rep = "&amp;"; break;
                case '<': rep = "&lt;"; break;
                case '>': rep = "&gt;"; break;
                case '"': rep = "&quot;"; break;
                case '\'': rep = "&#39;"; break;
                default: rep = null;
            }
            if (rep != null) {
                if (out == null) {
                    out = new StringBuilder(s.length() + 16);
                    out.append(s, 0, i);
                }
                out.append(rep);
            } else if (out != null) {
                out.append(c);
            }
        }
        return out == null ? s : out.toString();
    }

    /** Escape for a quoted HTML attribute value. Same rules as esc(). */
    public static String escAttr(Object value) {
        return esc(value);
    }

    /**
     * Escape for use inside a quoted JavaScript string literal (single or double quotes),
     * including one inside an HTML attribute or a script block. Null becomes "".
     */
    public static String js(Object value) {
        if (value == null) return "";
        String s = value.toString();
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\'' || c == '"' || c == '<' || c == '>' || c == '&' || c == '/' || c < 0x20 || c == 0x2028 || c == 0x2029) {
                out.append(String.format("\\u%04x", (int) c));
            } else out.append(c);
        }
        return out.toString();
    }

    /** URL-encode a query-string component (UTF-8). Null becomes "". */
    public static String url(Object value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value.toString(), java.nio.charset.StandardCharsets.UTF_8);
    }
}

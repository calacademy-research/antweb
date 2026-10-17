package org.calacademy.antweb.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Allowlist HTML sanitizer for curator-edited page text (description.do edits), which is
 * stored as HTML and shown on public pages.
 *
 * No HTML sanitizer library is in WEB-INF/lib (no jsoup, no OWASP java-html-sanitizer), so
 * this is a small self-contained one. It does not strip a blacklist with regular expressions:
 * it tokenizes the input and rebuilds the output from scratch, so only what is listed below
 * can ever reach the page.
 *
 *  - Allowed tags are re-emitted with their name in lower case and only their allowed
 *    attributes, each value decoded, checked and re-escaped inside double quotes.
 *  - script, style, iframe, object, embed, form and similar are dropped with their content
 *    (iframe: see allowVideoEmbeds below).
 *  - Any other tag is dropped and its text kept. Comments, doctypes and processing
 *    instructions are dropped.
 *  - Text is escaped (a bare &lt; or &gt; or a stray &amp; cannot start markup); existing
 *    character references such as &amp;nbsp; are kept.
 *  - href: http, https, mailto or relative. src: http, https or relative. No on* attributes,
 *    no style with url(), expression(), @import or similar.
 *  - Unclosed tags are closed at the end; stray closing tags are dropped.
 *  - With allowVideoEmbeds (the taxon page "videos" field only), an iframe is kept when its
 *    src is a YouTube or Vimeo embed URL over https; any other iframe is dropped.
 *
 * findUnsafe() runs the same pass but only reports what was removed for a security reason
 * (script and other active elements, on* handlers, javascript:/data: URLs, dangerous CSS),
 * ignoring cosmetic normalisation such as <br /> to <br>, closing unclosed tags, quoting
 * attributes or dropping harmless attributes like id or rel. Used by SanitizeDescriptionsAction
 * to clean only stored text that actually needs it.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    // Allowed tags and their allowed attributes (besides the global ones).
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<>();
    static {
        Set<String> none = Set.of();
        Set<String> align = Set.of("align");
        for (String t : new String[] {"b", "strong", "i", "em", "u", "sub", "sup", "span", "blockquote",
                "small", "s", "strike", "pre", "code", "center", "dl", "dt", "dd", "caption"}) {
            TAGS.put(t, none);
        }
        for (String t : new String[] {"p", "div", "h3", "h4", "h5", "h6", "ul", "ol", "li", "thead", "tbody", "tfoot"}) {
            TAGS.put(t, align);
        }
        TAGS.put("br", none);
        TAGS.put("hr", Set.of("width", "size", "align"));
        TAGS.put("a", Set.of("href", "title", "name", "target"));
        TAGS.put("img", Set.of("src", "alt", "title", "width", "height", "border", "align"));
        TAGS.put("table", Set.of("border", "cellpadding", "cellspacing", "width", "align", "summary"));
        TAGS.put("tr", Set.of("align", "valign"));
        TAGS.put("td", Set.of("colspan", "rowspan", "align", "valign", "width", "height", "nowrap"));
        TAGS.put("th", Set.of("colspan", "rowspan", "align", "valign", "width", "height", "nowrap", "scope"));
        TAGS.put("font", Set.of("color", "size", "face"));
    }
    private static final Set<String> GLOBAL_ATTRS = Set.of("class", "style", "title");
    private static final Set<String> VOID_TAGS = Set.of("br", "hr", "img");
    // Dropped together with everything inside them.
    private static final Set<String> DROP_WITH_CONTENT = Set.of("script", "style", "frame", "frameset",
        "object", "embed", "applet", "form", "textarea", "select", "button", "noscript", "noembed", "noframes",
        "template", "svg", "math", "title", "head", "xmp", "plaintext", "listing", "video", "audio", "canvas");

    // Unknown tags that are dropped (text kept) and are a risk in their own right.
    private static final Set<String> UNSAFE_UNKNOWN = Set.of("meta", "link", "base", "input", "param",
        "source", "track", "keygen", "isindex", "portal");
    private static final Pattern UNSAFE_STYLE = Pattern.compile(
        "url\\(|expression|javascript|vbscript|@import|behavior|binding|image\\(|image-set");
    private static final Pattern TARGET_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,31}");

    /** One thing the sanitizer removes for a security reason: what, and where in the input. */
    public static final class Unsafe {
        public final String reason;
        public final int offset;
        Unsafe(String reason, int offset) { this.reason = reason; this.offset = offset; }
        @Override public String toString() { return reason + " @" + offset; }
    }

    private static final Pattern TAG_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final Pattern NUMBER_OR_PERCENT = Pattern.compile("[0-9]{1,5}%?");
    private static final Pattern SIMPLE_TOKEN = Pattern.compile("[A-Za-z0-9 _.:#,+-]{0,100}");
    private static final Pattern CLASS_VALUE = Pattern.compile("[A-Za-z0-9 _-]{0,200}");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});");

    private static final Pattern VIDEO_EMBED_SRC = Pattern.compile(
        "https://(www\\.)?(youtube\\.com|youtube-nocookie\\.com)/embed/[A-Za-z0-9_-]{1,64}(\\?[A-Za-z0-9_=&;.%-]{0,200})?"
        + "|https://player\\.vimeo\\.com/video/[0-9]{1,15}(\\?[A-Za-z0-9_=&;.%-]{0,200})?");

    /** The sanitized HTML. Null stays null. */
    public static String sanitize(String html) {
        return sanitize(html, false);
    }

    /** The sanitized HTML; allowVideoEmbeds keeps YouTube/Vimeo iframes. Null stays null. */
    public static String sanitize(String html, boolean allowVideoEmbeds) {
        return run(html, allowVideoEmbeds, null);
    }

    /** What sanitize() would remove for a security reason; empty if the text is safe as stored. */
    public static List<Unsafe> findUnsafe(String html) {
        return findUnsafe(html, false);
    }

    /** As findUnsafe(html), with the same allowVideoEmbeds rule as sanitize(). */
    public static List<Unsafe> findUnsafe(String html, boolean allowVideoEmbeds) {
        List<Unsafe> found = new ArrayList<>();
        if (html != null) run(html, allowVideoEmbeds, found);
        return found;
    }

    private static void note(List<Unsafe> found, String reason, int offset) {
        if (found != null) found.add(new Unsafe(reason, offset));
    }

    // The sanitizer pass. When found is not null, security removals are recorded there.
    private static String run(String html, boolean allowVideoEmbeds, List<Unsafe> found) {
        if (html == null) return null;
        StringBuilder out = new StringBuilder(html.length() + 16);
        Deque<String> open = new ArrayDeque<>();
        int n = html.length();
        int i = 0;
        while (i < n) {
            char c = html.charAt(i);
            if (c != '<') {
                int next = html.indexOf('<', i);
                if (next < 0) next = n;
                appendText(out, html.substring(i, next));
                i = next;
                continue;
            }
            // c == '<'
            if (html.startsWith("<!--", i)) {
                int end = html.indexOf("-->", i + 4);
                i = end < 0 ? n : end + 3;
                continue;
            }
            if (i + 1 < n && (html.charAt(i + 1) == '!' || html.charAt(i + 1) == '?')) {
                int end = html.indexOf('>', i);
                i = end < 0 ? n : end + 1;
                continue;
            }
            boolean closing = i + 1 < n && html.charAt(i + 1) == '/';
            int nameStart = i + (closing ? 2 : 1);
            int nameEnd = nameStart;
            while (nameEnd < n && isNameChar(html.charAt(nameEnd))) nameEnd++;
            if (nameEnd == nameStart || !Character.isLetter(html.charAt(nameStart))) {
                // Not a tag: a literal "<".
                out.append("&lt;");
                i++;
                continue;
            }
            String name = html.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);
            int tagStart = i;
            Tag tag = parseTagRest(html, nameEnd);
            i = tag.end;

            if (found != null && !closing && !DROP_WITH_CONTENT.contains(name)) {
                checkAttrs(found, name, tag, tagStart, allowVideoEmbeds);
            }

            if (closing) {
                if (TAGS.containsKey(name) && !VOID_TAGS.contains(name) && open.contains(name)) {
                    while (!open.isEmpty()) {
                        String top = open.pop();
                        out.append("</").append(top).append('>');
                        if (top.equals(name)) break;
                    }
                }
                continue;
            }
            if (DROP_WITH_CONTENT.contains(name)) {
                note(found, "<" + name + "> element", tagStart);
                if (!tag.selfClosing) i = skipElement(html, i, name);
                continue;
            }
            if ("iframe".equals(name)) {
                if (!tag.selfClosing) i = skipElement(html, i, name);
                String src = tag.attrs.get("src");
                if (allowVideoEmbeds && src != null && VIDEO_EMBED_SRC.matcher(src.trim()).matches()) {
                    out.append("<iframe src=\"").append(escape(src.trim())).append('"');
                    for (String an : new String[] {"width", "height"}) {
                        String v = tag.attrs.get(an);
                        if (v != null && NUMBER_OR_PERCENT.matcher(v.trim()).matches()) out.append(' ').append(an).append("=\"").append(v.trim()).append('"');
                    }
                    String t = tag.attrs.get("title");
                    if (t != null) out.append(" title=\"").append(escape(t.length() > 200 ? t.substring(0, 200) : t)).append('"');
                    out.append(" frameborder=\"0\" allowfullscreen referrerpolicy=\"strict-origin-when-cross-origin\"")
                       .append(" allow=\"accelerometer; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen\"></iframe>");
                } else {
                    note(found, allowVideoEmbeds ? "<iframe> not a YouTube/Vimeo embed" : "<iframe> element", tagStart);
                }
                continue;
            }
            Set<String> allowed = TAGS.get(name);
            if (allowed == null) {  // unknown tag: drop it, keep its text
                if (UNSAFE_UNKNOWN.contains(name)) note(found, "<" + name + "> tag", tagStart);
                continue;
            }

            out.append('<').append(name);
            for (Map.Entry<String, String> attr : tag.attrs.entrySet()) {
                String an = attr.getKey();
                if (!allowed.contains(an) && !GLOBAL_ATTRS.contains(an)) continue;
                String value = cleanAttr(name, an, attr.getValue());
                if (value == null) continue;
                out.append(' ').append(an).append("=\"").append(escape(value)).append('"');
            }
            if ("a".equals(name) && tag.attrs.containsKey("target")
                    && cleanAttr(name, "target", tag.attrs.get("target")) != null) out.append(" rel=\"noopener noreferrer\"");
            out.append('>');
            if (!VOID_TAGS.contains(name)) {
                if (tag.selfClosing) out.append("</").append(name).append('>');
                else open.push(name);
            }
        }
        while (!open.isEmpty()) out.append("</").append(open.pop()).append('>');
        return out.toString();
    }

    // Record the attributes of one tag that are removed for a security reason.
    private static void checkAttrs(List<Unsafe> found, String name, Tag tag, int offset, boolean allowVideoEmbeds) {
        for (Map.Entry<String, String> attr : tag.attrs.entrySet()) {
            String an = attr.getKey();
            String v = attr.getValue();
            if (an.startsWith("on")) {
                note(found, an + " attribute on <" + name + ">", offset);
            } else if ("href".equals(an) || "src".equals(an) || "action".equals(an) || "formaction".equals(an)
                    || "xlink:href".equals(an) || "data".equals(an) || "background".equals(an)
                    || "lowsrc".equals(an) || "dynsrc".equals(an) || "poster".equals(an)) {
                String scheme = dangerousScheme(v);
                if (scheme != null) note(found, scheme + ": URL in " + an + " of <" + name + ">", offset);
            } else if ("style".equals(an)) {
                String why = unsafeStyle(v);
                if (why != null) note(found, "style with " + why + " on <" + name + ">", offset);
            } else if ("srcdoc".equals(an)) {
                note(found, "srcdoc attribute on <" + name + ">", offset);
            }
        }
    }

    // javascript, vbscript, data or livescript if the URL uses that scheme (whitespace and
    // control characters ignored, as browsers do); else null.
    static String dangerousScheme(String url) {
        if (url == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < url.length() && sb.length() < 16; k++) {
            char c = url.charAt(k);
            if (c > 0x20 && c != 0x7F) sb.append(Character.toLowerCase(c));
        }
        String u = sb.toString();
        for (String s : new String[] {"javascript", "vbscript", "data", "livescript"}) {
            if (u.startsWith(s + ":")) return s;
        }
        return null;
    }

    // Why a style value is dangerous (url(), expression() and similar, or a CSS escape or
    // comment that could hide one); null if it is not.
    static String unsafeStyle(String style) {
        if (style == null) return null;
        String compact = style.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        java.util.regex.Matcher m = UNSAFE_STYLE.matcher(compact);
        if (m.find()) return m.group();
        if (style.indexOf('\\') >= 0) return "a CSS escape";
        if (style.contains("/*")) return "a CSS comment";
        return null;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static final class Tag {
        final Map<String, String> attrs = new LinkedHashMap<>();
        boolean selfClosing;
        int end;
    }

    // Parse attributes from pos to the closing '>' (quotes respected).
    private static Tag parseTagRest(String s, int pos) {
        Tag tag = new Tag();
        int n = s.length();
        int i = pos;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '>') { i++; break; }
            if (c == '/' ) {
                if (i + 1 < n && s.charAt(i + 1) == '>') { tag.selfClosing = true; i += 2; break; }
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) { i++; continue; }
            int ns = i;
            while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '=' && s.charAt(i) != '>'
                    && !(s.charAt(i) == '/' && i + 1 < n && s.charAt(i + 1) == '>')) i++;
            String an = s.substring(ns, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(s.charAt(i))) i++;
            String value = "";
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i);
                    int ve = s.indexOf(q, i + 1);
                    if (ve < 0) ve = n;
                    value = s.substring(i + 1, ve);
                    i = Math.min(n, ve + 1);
                } else {
                    int vs = i;
                    while (i < n && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '>') i++;
                    value = s.substring(vs, i);
                }
            }
            if (!an.isEmpty() && !tag.attrs.containsKey(an)) tag.attrs.put(an, decode(value));
        }
        tag.end = i;
        return tag;
    }

    // Skip to just after the matching </name> (nested same-name elements counted), or the end.
    private static int skipElement(String s, int from, String name) {
        String lower = s.toLowerCase(Locale.ROOT);
        int depth = 1;
        int i = from;
        int n = s.length();
        boolean rawText = "script".equals(name) || "style".equals(name) || "xmp".equals(name)
            || "textarea".equals(name) || "title".equals(name) || "plaintext".equals(name);
        while (i < n) {
            int lt = lower.indexOf('<', i);
            if (lt < 0) return n;
            if (lower.startsWith("</" + name, lt) && (lt + 2 + name.length() >= n || !isNameChar(lower.charAt(lt + 2 + name.length())))) {
                int gt = lower.indexOf('>', lt);
                depth--;
                i = gt < 0 ? n : gt + 1;
                if (depth == 0) return i;
                continue;
            }
            if (!rawText && lower.startsWith("<" + name, lt) && (lt + 1 + name.length() >= n || !isNameChar(lower.charAt(lt + 1 + name.length())))) {
                Tag inner = parseTagRest(s, lt + 1 + name.length());
                if (!inner.selfClosing) depth++;
                i = inner.end;
                continue;
            }
            i = lt + 1;
        }
        return n;
    }

    // Returns the value to keep, or null to drop the attribute.
    private static String cleanAttr(String tag, String attr, String value) {
        if (value == null) return null;
        if (hasControlChars(value)) value = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        switch (attr) {
            case "href":
                return safeUrl(value, true);
            case "src":
                return safeUrl(value, false);
            case "width": case "height": case "border": case "cellpadding": case "cellspacing":
            case "colspan": case "rowspan": case "size":
                return NUMBER_OR_PERCENT.matcher(value.trim()).matches() ? value.trim() : null;
            case "align": case "valign":
                String a = value.trim().toLowerCase(Locale.ROOT);
                return Set.of("left", "right", "center", "justify", "top", "middle", "bottom", "baseline").contains(a) ? a : null;
            case "target":
                // A window name such as _blank or "new" (old pages use target="new").
                String tg = value.trim();
                return TARGET_VALUE.matcher(tg).matches() ? tg : null;
            case "nowrap":
                return "nowrap";
            case "scope":
                String sc = value.trim().toLowerCase(Locale.ROOT);
                return Set.of("row", "col", "rowgroup", "colgroup").contains(sc) ? sc : null;
            case "class":
                return CLASS_VALUE.matcher(value).matches() ? value : null;
            case "name": case "color": case "face":
                return SIMPLE_TOKEN.matcher(value).matches() ? value : null;
            case "style":
                return safeStyle(value);
            case "title": case "alt": case "summary":
                return value.length() > 1000 ? value.substring(0, 1000) : value;
            default:
                return null;
        }
    }

    private static boolean hasControlChars(String s) {
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if ((c < 0x20 && c != '\t' && c != '\n' && c != '\r') || c == 0x7F) return true;
        }
        return false;
    }

    /** http, https, (mailto if allowed) or a relative URL; else null. */
    static String safeUrl(String url, boolean allowMailto) {
        String u = url.trim();
        if (u.isEmpty()) return null;
        // Whitespace or backslashes inside a URL are never needed here and can hide a scheme.
        for (int k = 0; k < u.length(); k++) {
            char c = u.charAt(k);
            if (c < 0x21 || c == 0x7F || c == '\\') return null;
        }
        int colon = u.indexOf(':');
        int firstDelim = u.length();
        for (char d : new char[] {'/', '?', '#'}) {
            int p = u.indexOf(d);
            if (p >= 0 && p < firstDelim) firstDelim = p;
        }
        if (colon >= 0 && colon < firstDelim) {
            String scheme = u.substring(0, colon).toLowerCase(Locale.ROOT);
            if ("http".equals(scheme) || "https".equals(scheme)) return u;
            if (allowMailto && "mailto".equals(scheme)) return u;
            return null;
        }
        return u;  // relative (or protocol-relative //host/...)
    }

    private static String safeStyle(String style) {
        String s = style.toLowerCase(Locale.ROOT);
        String compact = s.replaceAll("\\s+", "");
        if (compact.contains("url(") || compact.contains("expression") || compact.contains("javascript")
                || compact.contains("vbscript") || compact.contains("@import") || compact.contains("behavior")
                || compact.contains("binding") || compact.contains("image(") || compact.contains("image-set")
                || compact.contains("position:fixed") || compact.contains("position:absolute")) return null;
        for (int k = 0; k < style.length(); k++) {
            char c = style.charAt(k);
            if (c == '\\' || c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || c == '/' && k + 1 < style.length() && style.charAt(k + 1) == '*') return null;
        }
        return style.length() > 500 ? null : style;
    }

    // Text: keep valid character references, escape everything else that matters.
    private static void appendText(StringBuilder out, String text) {
        int n = text.length();
        for (int k = 0; k < n; k++) {
            char c = text.charAt(k);
            if (c == '&') {
                java.util.regex.Matcher m = ENTITY.matcher(text);
                m.region(k, n);
                if (m.lookingAt()) {
                    out.append(m.group());
                    k = m.end() - 1;
                } else {
                    out.append("&amp;");
                }
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == 0) {
                // drop NUL
            } else {
                out.append(c);
            }
        }
    }

    private static String escape(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final Map<String, String> NAMED = Map.of(
        "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ",
        "colon", ":", "tab", "\t", "newline", "\n", "sol", "/");

    /**
     * Decode character references in an attribute value, so checks see what the browser
     * sees (e.g. "jav&#x61;script:"). An unknown named reference is kept as text, which is
     * then escaped on output (shown literally, never interpreted).
     */
    static String decode(String v) {
        if (v.indexOf('&') < 0) return v;
        StringBuilder sb = new StringBuilder(v.length());
        int n = v.length();
        int k = 0;
        while (k < n) {
            char c = v.charAt(k);
            if (c != '&') { sb.append(c); k++; continue; }
            int j = k + 1;
            if (j < n && v.charAt(j) == '#') {
                j++;
                boolean hex = j < n && (v.charAt(j) == 'x' || v.charAt(j) == 'X');
                if (hex) j++;
                int ds = j;
                while (j < n && (hex ? Character.digit(v.charAt(j), 16) >= 0 : Character.isDigit(v.charAt(j))) && j - ds < 8) j++;
                if (j > ds) {
                    int cp;
                    try {
                        cp = Integer.parseInt(v.substring(ds, j), hex ? 16 : 10);
                    } catch (NumberFormatException e) {
                        cp = 0xFFFD;
                    }
                    if (cp <= 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) cp = 0xFFFD;
                    sb.appendCodePoint(cp);
                    if (j < n && v.charAt(j) == ';') j++;
                    k = j;
                    continue;
                }
                sb.append('&');
                k++;
                continue;
            }
            int ns = j;
            while (j < n && Character.isLetterOrDigit(v.charAt(j)) && j - ns < 32) j++;
            String name = v.substring(ns, j);
            String rep = NAMED.get(name.toLowerCase(Locale.ROOT));
            if (rep != null && !name.isEmpty()) {
                sb.append(rep);
                if (j < n && v.charAt(j) == ';') j++;
                k = j;
            } else {
                sb.append('&');
                k++;
            }
        }
        return sb.toString();
    }
}

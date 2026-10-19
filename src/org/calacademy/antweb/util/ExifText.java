package org.calacademy.antweb.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the raw EXIF fields read by com.zonageek.jpeg.ExifBlock into readable text.
 *
 * ExifBlock returns text and binary fields as strings with one char per byte
 * (ISO-8859-1). EXIF text has no declared encoding: older AntWeb images were tagged
 * by tools that wrote Latin-1 / Windows-1252, newer ones write UTF-8. decodeText()
 * tries strict UTF-8 first and falls back to Windows-1252, so "José" reads correctly
 * either way. Binary fields (maker notes, the embedded thumbnail) are summarized,
 * never printed. Values returned here are plain text: escape them once when writing
 * HTML (Html.esc).
 */
public final class ExifText {

    private ExifText() {}

    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** One labelled value for the image page. */
    public static final class Row {
        public final String label;
        public final String value;
        Row(String label, String value) { this.label = label; this.value = value; }
        @Override public String toString() { return label + ": " + value; }
    }

    // Fields shown with a plain-language label, in this order.
    private static final String[][] LABELS = {
        {"Make", "Camera make"},
        {"Model", "Camera model"},
        {"BodySerialNumber", "Camera serial number"},
        {"LensMake", "Lens make"},
        {"LensModel", "Lens"},
        {"ExposureTime", "Exposure time"},
        {"FNumber", "Aperture"},
        {"ISOSpeedRatings", "ISO"},
        {"FocalLength", "Focal length"},
        {"Flash", "Flash"},
        {"DatetimeOriginal", "Date taken"},
        {"DatetimeDigitized", "Date digitized"},
        {"DateTime", "Date last changed"},
        {"Artist", "Artist"},
        {"CameraOwnerName", "Camera owner"},
        {"Copyright", "Copyright"},
        {"ImageDescription", "Description"},
        {"UserComment", "Comment"},
        {"Software", "Software"},
        {"PixelXDimension", "Width (pixels)"},
        {"PixelYDimension", "Height (pixels)"},
        {"GPSLatitude", "Latitude"},
        {"GPSLongitude", "Longitude"},
        {"GPSAltitude", "Altitude"},
        {"EXIFVersion", "EXIF version"},
    };

    // Binary or purely technical fields: summarized or left out.
    private static final String[] BINARY = {"MakerNote", "JFIFThumbnail", "TIFFStrips"};
    private static final String[] SKIP = {
        "OECF", "ComponentsConfiguration", "SpatialFrequencyResponse", "CFAPattern",
        "FileSource", "SceneType", "FlashPixVersion", "InteroperabilityVersion",
        "GPSLatitudeRef", "GPSLongitudeRef", "GPSAltitudeRef", "GPSVersionID",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
    };

    /**
     * Decodes an EXIF text value given as one char per byte: cuts at the first NUL,
     * UTF-8 if the bytes are valid UTF-8, otherwise Windows-1252. Control characters
     * are dropped, "&#44;" (written for commas by the old image script) becomes ",",
     * and the result is trimmed. Null stays null.
     */
    public static String decodeText(String raw) {
        if (raw == null) return null;
        byte[] bytes = toBytes(raw);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) end++;
        return clean(decodeBytes(bytes, 0, end));
    }

    /**
     * Decodes an EXIF UserComment: 8 bytes naming the character code ("ASCII",
     * "UNICODE", "JIS" or blank) followed by the text.
     */
    public static String decodeUserComment(String raw, boolean bigEndian) {
        if (raw == null) return null;
        byte[] b = toBytes(raw);
        if (b.length < 8) return decodeText(raw);
        String code = new String(b, 0, 8, StandardCharsets.ISO_8859_1);
        if (code.startsWith("UNICODE")) {
            // UCS-2. Use the byte order mark if there is one, else the file's byte order.
            Charset cs = bigEndian ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE;
            int len = b.length - 8;
            if (len >= 2) {
                if ((b[8] & 0xFF) == 0xFE && (b[9] & 0xFF) == 0xFF) cs = StandardCharsets.UTF_16BE;
                else if ((b[8] & 0xFF) == 0xFF && (b[9] & 0xFF) == 0xFE) cs = StandardCharsets.UTF_16LE;
            }
            String s = new String(b, 8, len - (len % 2), cs);
            int nul = s.indexOf('\0');
            if (nul >= 0) s = s.substring(0, nul);
            return clean(s);
        }
        if (code.startsWith("JIS")) {
            return "(text in the Japanese JIS character code, not shown)";
        }
        // "ASCII\0\0\0" or 8 undefined bytes: plain text (often really Latin-1 or UTF-8).
        String rest = new String(b, 8, b.length - 8, StandardCharsets.ISO_8859_1);
        return decodeText(rest);
    }

    /**
     * The fields worth showing, with plain-language labels and readable values,
     * in a fixed order; other text fields follow under their EXIF names. Binary
     * fields appear only as a size. Values are plain text (not HTML-escaped).
     */
    @SuppressWarnings("rawtypes")
    public static List<Row> rows(Map fields, boolean bigEndian) {
        List<Row> rows = new ArrayList<>();
        if (fields == null || fields.isEmpty()) return rows;
        Map<String, Object> left = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object k : fields.keySet()) left.put(String.valueOf(k), fields.get(k));

        for (String[] l : LABELS) {
            String name = l[0];
            if (!left.containsKey(name)) continue;
            Object v = left.remove(name);
            String text = format(name, v, left, bigEndian);
            if (text != null && !text.isEmpty()) rows.add(new Row(l[1], text));
        }
        for (String s : SKIP) left.remove(s);
        // TIFF* fields describe the embedded thumbnail (second image directory), not the photo.
        left.keySet().removeIf(k -> k.startsWith("TIFF") && !"TIFFStrips".equals(k));
        List<Row> binary = new ArrayList<>();
        for (String name : BINARY) {
            Object v = left.remove(name);
            if (v == null) continue;
            int n = byteLength(v);
            String what = "MakerNote".equals(name) ? "Camera maker's notes"
                        : "JFIFThumbnail".equals(name) ? "Embedded thumbnail" : "Embedded TIFF thumbnail";
            binary.add(new Row(what, "binary data, " + n + " bytes (not shown)"));
        }
        for (Map.Entry<String, Object> e : left.entrySet()) {
            String text = formatGeneric(e.getValue());
            if (text != null && !text.isEmpty()) rows.add(new Row(e.getKey(), text));
        }
        rows.addAll(binary);
        return rows;
    }

    // ---------------------------------------------------------------------------

    private static String format(String name, Object v, Map<String, Object> all, boolean bigEndian) {
        v = single(v);
        switch (name) {
            case "UserComment":
                return v instanceof String ? decodeUserComment((String) v, bigEndian) : formatGeneric(v);
            case "ExposureTime": {
                Rational r = rational(v);
                if (r == null) return formatGeneric(v);
                if (r.val() > 0 && r.val() < 1) return "1/" + round(1 / r.val(), 0) + " s";
                return round(r.val(), 2) + " s";
            }
            case "FNumber": {
                Rational r = rational(v);
                return r == null ? formatGeneric(v) : "f/" + round(r.val(), 1);
            }
            case "FocalLength": {
                Rational r = rational(v);
                return r == null ? formatGeneric(v) : round(r.val(), 1) + " mm";
            }
            case "Flash": {
                if (v instanceof Integer) return (((Integer) v) & 1) == 1 ? "fired" : "did not fire";
                return formatGeneric(v);
            }
            case "DatetimeOriginal": case "DatetimeDigitized": case "DateTime": {
                String s = decodeText(v instanceof String ? (String) v : String.valueOf(v));
                // "2006:02:21 09:09:50" -> "2006-02-21 09:09:50"
                if (s != null && s.matches("\\d{4}:\\d{2}:\\d{2}.*")) s = s.substring(0, 10).replace(':', '-') + s.substring(10);
                return s;
            }
            case "GPSLatitude": case "GPSLongitude": {
                String ref = all.containsKey(name + "Ref") ? decodeText(String.valueOf(single(all.get(name + "Ref")))) : "";
                return gps(v, ref);
            }
            case "GPSAltitude": {
                Rational r = rational(v);
                if (r == null) return formatGeneric(v);
                Object refObj = single(all.get("GPSAltitudeRef"));
                boolean below = refObj != null && String.valueOf(refObj).startsWith("\u0001");
                return (below ? "-" : "") + round(r.val(), 1) + " m";
            }
            case "EXIFVersion": {
                String s = v instanceof String ? decodeText((String) v) : null;
                if (s != null && s.matches("\\d{4}")) return Integer.parseInt(s.substring(0, 2)) + "." + s.substring(2);
                return s;
            }
            default:
                return formatGeneric(v);
        }
    }

    @SuppressWarnings("rawtypes")
    private static String gps(Object v, String ref) {
        if (!(v instanceof Collection) || ((Collection) v).size() != 3) return formatGeneric(v);
        double[] d = new double[3];
        int i = 0;
        for (Object o : (Collection) v) {
            Rational r = rational(o);
            if (r == null) return formatGeneric(v);
            d[i++] = r.val();
        }
        double dec = d[0] + d[1] / 60 + d[2] / 3600;
        boolean neg = "S".equalsIgnoreCase(ref) || "W".equalsIgnoreCase(ref);
        String dms = round(d[0], 0) + "° " + round(d[1], 0) + "' " + round(d[2], 2) + "\"" + (ref.isEmpty() ? "" : " " + ref);
        return dms + " (" + round(neg ? -dec : dec, 5) + ")";
    }

    @SuppressWarnings("rawtypes")
    private static String formatGeneric(Object v) {
        if (v == null) return null;
        if (v instanceof String) {
            String raw = (String) v;
            if (looksBinary(raw)) return "binary data, " + raw.length() + " bytes (not shown)";
            String s = decodeText(raw);
            return (s == null || s.isEmpty()) ? null : s;
        }
        Rational r = rational(v);
        if (r != null) return round(r.val(), 4);
        if (v instanceof Collection) {
            Collection c = (Collection) v;
            List<String> parts = new ArrayList<>();
            for (Object o : c) {
                String p = formatGeneric(o);
                if (p != null && !parts.contains(p)) parts.add(p);
            }
            return String.join("; ", parts);
        }
        return String.valueOf(v);
    }

    /** A field that appears in both image directories is a list of two equal values; use the first. */
    @SuppressWarnings("rawtypes")
    private static Object single(Object v) {
        if (v instanceof Collection) {
            Collection c = (Collection) v;
            if (!c.isEmpty()) {
                Object first = c.iterator().next();
                // A list of rationals (GPS coordinates) is a value of its own, not a duplicate.
                if (first instanceof Collection || first instanceof String || first instanceof Integer) {
                    boolean allSame = true;
                    for (Object o : c) if (!String.valueOf(o).equals(String.valueOf(first))) { allSame = false; break; }
                    if (allSame || first instanceof String) return first;
                }
            }
        }
        return v;
    }

    private record Rational(long num, long den) {
        double val() { return den == 0 ? 0 : (double) num / (double) den; }
    }

    @SuppressWarnings("rawtypes")
    private static Rational rational(Object v) {
        if (!(v instanceof Map)) return null;
        Map m = (Map) v;
        Object n = m.get("num"), d = m.get("den");
        if (!(n instanceof Number) || !(d instanceof Number)) return null;
        return new Rational(((Number) n).longValue(), ((Number) d).longValue());
    }

    private static String round(double d, int places) {
        if (places == 0) return String.valueOf(Math.round(d));
        java.math.BigDecimal b = java.math.BigDecimal.valueOf(d).setScale(places, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return b.toPlainString();
    }

    /** Control bytes before the trailing NULs: binary data stored in a text or undefined field. */
    static boolean looksBinary(String raw) {
        int end = raw.length();
        while (end > 0 && raw.charAt(end - 1) == 0) end--;
        for (int i = 0; i < end; i++) {
            char c = raw.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') return true;
        }
        return false;
    }

    private static int byteLength(Object v) {
        if (v instanceof String) return ((String) v).length();
        return 0;
    }

    private static byte[] toBytes(String raw) {
        // ExifBlock maps bytes to chars 0-255. A char above 255 means the value is already
        // decoded text; keep it as UTF-8 so the decoding below leaves it unchanged.
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) > 0xFF) return raw.getBytes(StandardCharsets.UTF_8);
        }
        return raw.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String decodeBytes(byte[] b, int off, int len) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b, off, len)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, off, len, CP1252);
        }
    }

    private static String clean(String s) {
        if (s == null) return null;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') out.append(' ');
            else if (c >= 0x20 && c != 0x7F && c != '�' && !(c >= 0x80 && c < 0xA0)) out.append(c);
        }
        return out.toString().replace("&#44;", ",").replace("&#44", ",").trim();
    }
}

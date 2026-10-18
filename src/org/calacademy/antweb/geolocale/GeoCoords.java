package org.calacademy.antweb.geolocale;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks and tidies the centroid and bounding box typed on the Edit Geolocale page.
 *
 * Centroid: "lat, lon". Bounding box: "west lon, south lat, east lon, north lat" (the order
 * LocalityOverview reads). Numbers may be separated by commas and/or spaces, and may be wrapped in
 * parentheses. A box whose west longitude is east of its east longitude (west positive, east
 * negative) crosses longitude 180, as for Fiji: 174.8662, -21.0171, -178.2032, -12.4662.
 *
 * Each check returns the value in the stored format ("" for an empty field) or throws
 * IllegalArgumentException with a message for the curator.
 */
public final class GeoCoords {

    private GeoCoords() {
    }

    public static String normalizeCentroid(String value) {
        List<Double> n = numbers(value, 2, "Centroid", "lat, lon");
        if (n == null) return "";
        double lat = n.get(0), lon = n.get(1);
        checkLat("Centroid", "latitude", lat);
        checkLon("Centroid", "longitude", lon);
        return format(lat) + ", " + format(lon);
    }

    public static String normalizeBoundingBox(String value) {
        List<Double> n = numbers(value, 4, "Bounding box", "west lon, south lat, east lon, north lat");
        if (n == null) return "";
        double west = n.get(0), south = n.get(1), east = n.get(2), north = n.get(3);
        checkLon("Bounding box", "west longitude", west);
        checkLat("Bounding box", "south latitude", south);
        checkLon("Bounding box", "east longitude", east);
        checkLat("Bounding box", "north latitude", north);
        if (!(south < north)) {
            throw new IllegalArgumentException("Bounding box: the south latitude (" + format(south)
                + ") must be less than the north latitude (" + format(north) + ").");
        }
        boolean crosses180 = west > 0 && east < 0;
        if (!(west < east) && !crosses180) {
            throw new IllegalArgumentException("Bounding box: the west longitude (" + format(west)
                + ") must be less than the east longitude (" + format(east)
                + "), unless the box crosses longitude 180 (west positive, east negative).");
        }
        return format(west) + ", " + format(south) + ", " + format(east) + ", " + format(north);
    }

    /** The numbers in value, or null if it is blank. */
    private static List<Double> numbers(String value, int count, String field, String expected) {
        if (value == null) return null;
        String s = value.trim();
        if (s.startsWith("(") && s.endsWith(")")) s = s.substring(1, s.length() - 1).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        String[] parts = s.split("[,\\s]+");
        List<Double> out = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                double d = Double.parseDouble(p);
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
                out.add(d);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(field + ": \"" + p + "\" is not a number. Enter " + count
                    + " numbers: " + expected + ".");
            }
        }
        if (out.size() != count) {
            throw new IllegalArgumentException(field + ": enter " + count + " numbers (" + expected + "), not "
                + out.size() + ".");
        }
        return out;
    }

    private static void checkLat(String field, String name, double v) {
        if (v < -90 || v > 90) {
            throw new IllegalArgumentException(field + ": the " + name + " (" + format(v) + ") must be between -90 and 90.");
        }
    }

    private static void checkLon(String field, String name, double v) {
        if (v < -180 || v > 180) {
            throw new IllegalArgumentException(field + ": the " + name + " (" + format(v) + ") must be between -180 and 180.");
        }
    }

    /** Plain decimal, no exponent, no trailing zeros ("12.5", "-17.983958", "180"). */
    private static String format(double v) {
        String s = java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
        return "-0".equals(s) ? "0" : s;
    }
}

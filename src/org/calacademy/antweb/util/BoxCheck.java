package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.geolocale.Geolocale;

/**
 * Daily check, after the nightly run (Scheduler, step "boxCheck" of the daily update): the states (adm1) whose stored
 * box flags many of AntWeb's own specimens filed under them. The upload check (SpecimenUploadParse.isWithinAdm1Bounds,
 * LocalityOverview.isWithinBounds: the box plus 0.2 degree) then tells curators that correct records are out of bounds:
 * Betsiboka (Madagascar) told Phil Ward to change correct UC Davis records to Boeny, and 26 states had such a box
 * (fixed 2026-11-18, 1,321 specimens). Brian Fisher 2026-11-18: run that check every day, before people change their
 * data. A state is listed when its box flags at least MIN_FLAGGED specimens and at least PERCENT of its specimens with
 * coordinates: a wrong box flags a large share, a few badly georeferenced specimens do not. The step ends yellow
 * (warning: one email per run, as the count check) and an admin alert is added at most once a week. States with
 * specimens but no box are counted (the upload check cannot flag them). Read-only. Tests: BoxCheckTest.
 */
public final class BoxCheck {

    private static final Log s_log = LogFactory.getLog(BoxCheck.class);

    public static final int MIN_FLAGGED = 10;
    public static final int PERCENT = 20;
    static final String TITLE = "Box check";
    static final int LISTED = 10;

    private BoxCheck() {}

    /** One state: its specimens with coordinates, how many its box flags, whether it has a box. */
    public static final class Tally {
        public final String country;
        public final String adm1;
        public final boolean hasBox;
        public int specimens;
        public int flagged;
        public Tally(String country, String adm1, boolean hasBox) {
            this.country = country;
            this.adm1 = adm1;
            this.hasBox = hasBox;
        }
    }

    /** The specimens of one place and point. */
    public interface Box {
        boolean hasBox();
        boolean isWithin(double lat, double lon);
    }

    /** Adds count specimens at (lat, lon) to the state's tally. box null: not a valid state of AntWeb (not checked). */
    public static void add(Map<String, Tally> tallies, String country, String adm1, Box box, double lat, double lon, int count) {
        if (box == null) return;
        Tally t = tallies.computeIfAbsent(country + "\t" + adm1, k -> new Tally(country, adm1, box.hasBox()));
        t.specimens += count;
        if (box.hasBox() && !box.isWithin(lat, lon)) t.flagged += count;
    }

    /** The states whose box flags their own specimens, worst first. */
    public static List<Tally> wrongBoxes(Iterable<Tally> tallies, int minFlagged, int percent) {
        List<Tally> out = new ArrayList<>();
        for (Tally t : tallies) {
            if (t.hasBox && t.flagged >= minFlagged && t.flagged * 100L >= (long) percent * t.specimens) out.add(t);
        }
        out.sort(Comparator.comparingInt((Tally t) -> -t.flagged).thenComparing(t -> t.country).thenComparing(t -> t.adm1));
        return out;
    }

    public static int withoutBox(Iterable<Tally> tallies) {
        int n = 0;
        for (Tally t : tallies) if (!t.hasBox && t.specimens > 0) n++;
        return n;
    }

    /** The compute.log line and step message; starts with "Box check: ok" when no box is wrong. */
    public static String line(List<Tally> wrong, int withoutBox) {
        String tail = withoutBox > 0 ? "; " + withoutBox + " states with specimens have no box (not checked)" : "";
        if (wrong.isEmpty()) return TITLE + ": ok" + tail;
        StringBuilder sb = new StringBuilder(TITLE + ": " + wrong.size() + (wrong.size() == 1 ? " state's" : " states'")
            + " box flags many of its own specimens as out of bounds (the upload report would tell curators to change them): ");
        for (int i = 0; i < Math.min(LISTED, wrong.size()); i++) {
            Tally t = wrong.get(i);
            if (i > 0) sb.append("; ");
            sb.append(t.adm1).append(" (").append(t.country).append(") ").append(t.flagged).append(" of ").append(t.specimens);
        }
        if (wrong.size() > LISTED) sb.append("; and ").append(wrong.size() - LISTED).append(" more");
        sb.append(". Fix the box in the Geolocale Manager (bounding box fixed)").append(tail);
        return sb.toString();
    }

    static final String SPECIMEN_POINTS = "select country, adm1, decimal_latitude, decimal_longitude, count(*) from specimen"
        + " where adm1 is not null and adm1 != '' and country is not null"
        + " and decimal_latitude is not null and decimal_longitude is not null"
        + " group by country, adm1, decimal_latitude, decimal_longitude";

    /** Run the check (the box of each state as the upload check finds it, GeolocaleMgr.getAdm1). Never throws. */
    public static String run(Connection connection) {
        long started = System.currentTimeMillis();
        try {
            Map<String, Tally> tallies = new LinkedHashMap<>();
            Map<String, Box> boxes = new java.util.HashMap<>();
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(SPECIMEN_POINTS)) {
                while (rs.next()) {
                    String country = rs.getString(1);
                    String adm1 = rs.getString(2);
                    Box box = boxes.computeIfAbsent(country + "\t" + adm1, k -> boxOf(GeolocaleMgr.getAdm1(adm1, country)));
                    add(tallies, country, adm1, box, rs.getDouble(3), rs.getDouble(4), rs.getInt(5));
                }
            }
            List<Tally> wrong = wrongBoxes(tallies.values(), MIN_FLAGGED, PERCENT);
            String line = line(wrong, withoutBox(tallies.values())) + " (" + (System.currentTimeMillis() - started) / 1000 + " s)";
            if (!wrong.isEmpty()) {
                s_log.warn("run() " + line);
                AdminAlertMgr.addIfFresh(line, TITLE, AdminAlertMgr.WEEK, connection);
                DailyUpdate.noteWarning("boxCheck", line);
            }
            return line;
        } catch (Exception e) {
            s_log.error("run() e:" + e);
            DailyUpdate.noteFailed("boxCheck", String.valueOf(e));
            return TITLE + " FAILED e:" + e;
        }
    }

    /** null for a name that is not a valid state (the upload check does not check it either). */
    static Box boxOf(Geolocale adm1) {
        if (adm1 == null) return null;
        String bounds = adm1.useBoundingBox();
        boolean has = bounds != null && !bounds.trim().isEmpty() && !"null".equals(bounds.trim());
        return new Box() {
            public boolean hasBox() { return has; }
            public boolean isWithin(double lat, double lon) { return adm1.isWithinBounds(lat, lon); }
        };
    }
}

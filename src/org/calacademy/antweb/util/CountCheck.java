package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Count check (2026-10, Brian Fisher: "a test that would validate the counts based on real specimen
 * data to make sure these counts are true"). Read-only.
 *
 * The numbers on taxon lists are stored, not counted when a page is shown: the nightly run (sets 2
 * and 3, from about 05:00 to 08:00 Pacific) writes them into geolocale_taxon, proj_taxon,
 * museum_taxon and bioregion_taxon, and the "Global" column comes from the allantwebants project
 * (proj_taxon). This check counts again, straight from the specimen and taxon tables, for a few
 * places, and lists every number that differs, with both values:
 *
 *   specimens  species and subspecies rows: specimen_count = specimens filed under that name in the
 *              place (geolocale: specimen.country, adm1 or island_country; museum: specimen.museum;
 *              bioregion: specimen.bioregion; project: all specimens), of a countable status
 *              (valid, unrecognized, morphotaxon, indetermined, unidentifiable; projects: any status).
 *   species    genus rows: species_count = species and subspecies rows of that genus in the same list
 *              (taxon status valid, unrecognized, morphotaxon, indetermined or unidentifiable; projects: any).
 *   genera     subfamily rows: genus_count = genus rows of that subfamily in the same list (same statuses).
 *   global     species rows of the places checked: the allantwebants specimen_count ("Global" column) =
 *              all specimens filed under that name.
 *
 * These are the rules of the nightly count crawls (CountDb and the Countable queries of Geolocale,
 * Project, Museum and Bioregion), written again here, so a stored number that is stale or was never
 * updated shows up.
 *
 * Expected differences: counts are only recomputed by the nightly run, which starts at 05:00
 * Pacific and has written them by 08:15 (the end of the last completed run is read from compute.log;
 * without one, the last 08:15 is assumed and the page and the e2e test say so) (the AntCat import, the TaxonWorks upload and the name
 * update come before the counts, so their changes are counted). A difference is "pending" (expected
 * to disappear after the next nightly run) when, after the end of the last nightly run (08:15): a
 * specimen of that name was uploaded, or specimens were moved to or from that name (name update by
 * hand), or the list row was added, or (for a stored number higher than the recount, or a species or
 * genus count) any specimen upload happened (an upload can remove specimens and list rows). Every
 * other difference is "real". Between 05:00 and 08:15 the nightly run is still writing the numbers:
 * the check says so and the e2e test skips.
 */
public class CountCheck {

    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";
    public static final String MUSEUM = "museum";
    public static final String BIOREGION = "bioregion";

    /** Countable specimen statuses (StatusSet.getCountables) and list statuses (StatusSet ALL). */
    public static final String COUNTABLE = "('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";

    public static final class Place {
        public final String type;
        public final String key;          // geolocale id, project name, museum code, bioregion name
        public final String label;
        public final String specimenColumn;   // specimen column that places a specimen here; null = all specimens
        public final String specimenValue;

        public Place(String type, String key, String label, String specimenColumn, String specimenValue) {
            this.type = type;
            this.key = key;
            this.label = label;
            this.specimenColumn = specimenColumn;
            this.specimenValue = specimenValue;
        }

        String table() {
            switch (type) {
                case GEOLOCALE: return "geolocale_taxon";
                case PROJECT: return "proj_taxon";
                case MUSEUM: return "museum_taxon";
                default: return "bioregion_taxon";
            }
        }

        String keyColumn() {
            switch (type) {
                case GEOLOCALE: return "geolocale_id";
                case PROJECT: return "project_name";
                case MUSEUM: return "code";
                default: return "bioregion_name";
            }
        }

        boolean statusFiltered() {
            return !PROJECT.equals(type) || "allantwebants".equals(key);
        }

        void setKey(PreparedStatement stmt, int i) throws SQLException {
            if (GEOLOCALE.equals(type)) stmt.setInt(i, Integer.parseInt(key));
            else stmt.setString(i, key);
        }

        public String toString() { return type + " " + label; }
    }

    public static final class Mismatch {
        public final Place place;
        public final String check;      // specimens, species, genera, global
        public final String taxonName;
        public final int stored;
        public final int recount;
        public final String pendingReason;   // null = real

        Mismatch(Place place, String check, String taxonName, int stored, int recount, String pendingReason) {
            this.place = place;
            this.check = check;
            this.taxonName = taxonName;
            this.stored = stored;
            this.recount = recount;
            this.pendingReason = pendingReason;
        }

        public boolean isReal() { return pendingReason == null; }

        public String toString() {
            return place.label + " " + check + " " + taxonName + " stored:" + stored + " recount:" + recount
                + (pendingReason == null ? "" : " (pending: " + pendingReason + ")");
        }
    }

    public static final class Result {
        /** End of the last nightly count run: changes after it are not counted yet. */
        public Timestamp countedUntil;
        /** End of the last completed nightly run found in compute.log, or null when none was found. */
        public Timestamp lastRunEnd;
        public boolean inNightlyWindow;
        public boolean uploadSinceNightly;
        public final List<Place> places = new ArrayList<>();
        public final Map<Place, Integer> rowsChecked = new HashMap<>();
        public final List<Mismatch> mismatches = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();

        public int real() {
            int n = 0;
            for (Mismatch m : mismatches) if (m.isReal()) ++n;
            return n;
        }

        public int pending() { return mismatches.size() - real(); }
    }

    private final Connection connection;

    public CountCheck(Connection connection) {
        this.connection = connection;
    }

    /**
     * End of the last nightly count run: today 08:15 (database time, Pacific) or, before 08:15,
     * yesterday 08:15; and whether the nightly run is going on now (05:00 to 08:15).
     */
    static final String LAST_NIGHTLY_SQL = "select if(time(now()) >= '08:15:00', timestamp(curdate(), '08:15:00'),"
        + " timestamp(curdate() - interval 1 day, '08:15:00')), time(now()) >= '05:00:00' and time(now()) < '08:15:00'";

    public Result run(List<Place> places) throws SQLException {
        return run(places, null);
    }

    /**
     * lastRunEnd: end of the last completed nightly run (compute.log, lastCompletedRun()); the counts
     * are from that run. Without it, the last 08:15 is assumed, and the result says so (lastRunEnd null).
     */
    public Result run(List<Place> places, Timestamp lastRunEnd) throws SQLException {
        Result result = new Result();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(LAST_NIGHTLY_SQL)) {
            r.next();
            result.countedUntil = r.getTimestamp(1);
            result.inNightlyWindow = r.getBoolean(2);
        }
        result.lastRunEnd = lastRunEnd;
        if (lastRunEnd != null && !result.inNightlyWindow) result.countedUntil = lastRunEnd;
        Set<String> changedNames = changedNamesSince(result);

        for (Place place : places) {
            result.places.add(place);
            int rows = 0;
            rows += checkSpecimens(place, result, changedNames);
            rows += checkChildren(place, "genus", "species_count", "('species', 'subspecies')", "species", result, changedNames);
            rows += checkChildren(place, "subfamily", "genus_count", "('genus')", "genera", result, changedNames);
            if (!PROJECT.equals(place.type)) rows += checkGlobal(place, result, changedNames);
            result.rowsChecked.put(place, rows);
        }
        return result;
    }

    private static final java.util.regex.Pattern LOG_LINE = java.util.regex.Pattern.compile("^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d) (Begin|End) run\\b");

    /**
     * End of the last nightly run in compute.log ("<date time> Begin run" ... "<date time> End run")
     * that has no "FAILED" line between its Begin and End; null if there is none.
     */
    public static Timestamp lastCompletedRun(List<String> lines) {
        Timestamp last = null;
        boolean inRun = false;
        boolean failed = false;
        for (String line : lines) {
            java.util.regex.Matcher m = LOG_LINE.matcher(line);
            if (m.find()) {
                if ("Begin".equals(m.group(2))) {
                    inRun = true;
                    failed = false;
                } else if (inRun) {
                    if (!failed) last = Timestamp.valueOf(m.group(1));
                    inRun = false;
                }
            } else if (inRun && line.contains("FAILED") && !line.contains("name update")) {
                failed = true;
            }
        }
        return last;
    }

    /** Names whose specimens changed since the last nightly start: uploads, and the name update outside the nightly. */
    Set<String> changedNamesSince(Result result) throws SQLException {
        Set<String> names = new HashSet<>();
        if (tableExists("upload")) {
            try (PreparedStatement stmt = connection.prepareStatement("select count(*) from upload where created >= ?")) {
                stmt.setTimestamp(1, result.countedUntil);
                try (ResultSet r = stmt.executeQuery()) {
                    r.next();
                    result.uploadSinceNightly = r.getInt(1) > 0;
                }
            }
            if (result.uploadSinceNightly) {
                try (PreparedStatement stmt = connection.prepareStatement("select distinct taxon_name from specimen"
                        + " where upload_id in (select upload_id from upload where created >= ?)")) {
                    stmt.setTimestamp(1, result.countedUntil);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) names.add(r.getString(1));
                    }
                }
            }
        }
        if (tableExists("specimen_name_update")) {
            try (PreparedStatement stmt = connection.prepareStatement("select distinct old_name, new_name from specimen_name_update"
                    + " where mode = 'changed' and run_date >= ?")) {
                stmt.setTimestamp(1, result.countedUntil);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        names.add(r.getString(1));
                        names.add(r.getString(2));
                    }
                }
            }
        }
        return names;
    }

    private boolean tableExists(String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            stmt.setString(1, table);
            try (ResultSet r = stmt.executeQuery()) { return r.next(); }
        }
    }

    private String rowPending(Timestamp created, Result result) {
        if (created != null && !created.before(result.countedUntil)) return "list row added after the last nightly run";
        return null;
    }

    /** Species and subspecies rows: specimen_count against the specimens in the place. */
    int checkSpecimens(Place place, Result result, Set<String> changedNames) throws SQLException {
        String t = place.table();
        String k = place.keyColumn();
        Map<String, Integer> recount = new HashMap<>();
        String query = "select s.taxon_name, count(*) from specimen s"
            + " join " + t + " l on l.taxon_name = s.taxon_name and l." + k + " = ?"
            + " where 1 = 1"
            + (place.specimenColumn != null ? " and s." + place.specimenColumn + " = ?" : "")
            + (place.statusFiltered() ? " and s.status in " + COUNTABLE : "")
            + " group by s.taxon_name";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            place.setKey(stmt, 1);
            if (place.specimenColumn != null) stmt.setString(2, place.specimenValue);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) recount.put(r.getString(1), r.getInt(2));
            }
        }
        int rows = 0;
        String stored = "select l.taxon_name, l.specimen_count, l.created from " + t + " l"
            + " join taxon on taxon.taxon_name = l.taxon_name"
            + " where l." + k + " = ? and taxon.taxarank in ('species', 'subspecies')";
        try (PreparedStatement stmt = connection.prepareStatement(stored)) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    ++rows;
                    String name = r.getString(1);
                    int s = r.getInt(2);
                    int c = recount.getOrDefault(name, 0);
                    if (s == c) continue;
                    String pending = rowPending(r.getTimestamp(3), result);
                    if (pending == null && changedNames.contains(name)) pending = "specimens of this name uploaded or renamed after the last nightly run";
                    if (pending == null && s > c && result.uploadSinceNightly) pending = "an upload after the last nightly run may have removed specimens";
                    result.mismatches.add(new Mismatch(place, "specimens", name, s, c, pending));
                }
            }
        }
        return rows;
    }

    /** Genus rows (species_count) or subfamily rows (genus_count) against their child rows in the same list. */
    int checkChildren(Place place, String parentRank, String column, String childRanks, String check, Result result, Set<String> changedNames) throws SQLException {
        String t = place.table();
        String k = place.keyColumn();
        Map<String, Integer> recount = new HashMap<>();
        Map<String, Boolean> childPending = new HashMap<>();
        String query = "select taxon.parent_taxon_name, l.taxon_name, l.created from taxon"
            + " join " + t + " l on l.taxon_name = taxon.taxon_name and l." + k + " = ?"
            + " where taxon.taxarank in " + childRanks
            + (place.statusFiltered() ? " and taxon.status in " + COUNTABLE : "");
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    String parent = r.getString(1);
                    if (parent == null) continue;
                    recount.merge(parent, 1, Integer::sum);
                    Timestamp created = r.getTimestamp(3);
                    boolean p = (created != null && !created.before(result.countedUntil)) || changedNames.contains(r.getString(2));
                    if (p) childPending.put(parent, true);
                }
            }
        }
        int rows = 0;
        String stored = "select l.taxon_name, l." + column + ", l.created from " + t + " l"
            + " join taxon on taxon.taxon_name = l.taxon_name"
            + " where l." + k + " = ? and taxon.taxarank = ?";
        try (PreparedStatement stmt = connection.prepareStatement(stored)) {
            place.setKey(stmt, 1);
            stmt.setString(2, parentRank);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    ++rows;
                    String name = r.getString(1);
                    int s = r.getInt(2);
                    int c = recount.getOrDefault(name, 0);
                    if (s == c) continue;
                    String pending = rowPending(r.getTimestamp(3), result);
                    if (pending == null && childPending.containsKey(name)) pending = "a row under it was added, or its specimens changed, after the last nightly run";
                    if (pending == null && result.uploadSinceNightly) pending = "an upload after the last nightly run may have removed list rows";
                    result.mismatches.add(new Mismatch(place, check, name, s, c, pending));
                }
            }
        }
        return rows;
    }

    /** The "Global" column: allantwebants specimen_count of the place's species rows against all their specimens. */
    int checkGlobal(Place place, Result result, Set<String> changedNames) throws SQLException {
        String t = place.table();
        String k = place.keyColumn();
        Map<String, Integer> recount = new HashMap<>();
        String query = "select s.taxon_name, count(*) from specimen s"
            + " where s.taxon_name in (select l.taxon_name from " + t + " l join taxon on taxon.taxon_name = l.taxon_name"
            + "   where l." + k + " = ? and taxon.taxarank in ('species', 'subspecies'))"
            + " group by s.taxon_name";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) recount.put(r.getString(1), r.getInt(2));
            }
        }
        int rows = 0;
        String stored = "select a.taxon_name, a.specimen_count, a.created from proj_taxon a"
            + " where a.project_name = 'allantwebants' and a.taxon_name in (select l.taxon_name from " + t + " l"
            + "   join taxon on taxon.taxon_name = l.taxon_name where l." + k + " = ? and taxon.taxarank in ('species', 'subspecies'))";
        try (PreparedStatement stmt = connection.prepareStatement(stored)) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    ++rows;
                    String name = r.getString(1);
                    int s = r.getInt(2);
                    int c = recount.getOrDefault(name, 0);
                    if (s == c) continue;
                    String pending = rowPending(r.getTimestamp(3), result);
                    if (pending == null && changedNames.contains(name)) pending = "specimens of this name uploaded or renamed after the last nightly run";
                    if (pending == null && s > c && result.uploadSinceNightly) pending = "an upload after the last nightly run may have removed specimens";
                    result.mismatches.add(new Mismatch(place, "global", name, s, c, pending));
                }
            }
        }
        return rows;
    }
}

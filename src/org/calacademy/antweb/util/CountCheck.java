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
 *   place images  (2026-10-20) the place's own numbers: Images (all files, labels too), images of each
 *              view (shot type) and Imaged specimens (specimens with an image that is not a label), as
 *              stored by PlaceImageCountDb (place_image_count and the image_count / imaged_specimen_count of
 *              the place table), against a recount from the image table of the place's specimens (the
 *              specimens of the "specimens" check above: geolocale and project by their list, geolocale
 *              of a countable status; museum and bioregion all their specimens). Also: Images is never
 *              smaller than Imaged specimens (a difference there is always "real").
 *   place specimens  (2026-10-20) geolocales: the place's own Specimens number (geolocale.specimen_count)
 *              against a recount of the specimens of its species and subspecies rows.
 *
 *   images     (2026-10-21) species and subspecies rows: the list's Images column = the images (labels included) of
 *              the specimens of that name in the place, as nightly set 4 counts them.
 *   place specimens of projects (2026-10-21): project.specimen_count = all specimens of its species and subspecies.
 *
 * Since 2026-10-21 run() also checks every place with CountInvariants (numbers that must agree, all places at once)
 * and recounts a rotating sample of places (CountCheckAction.rotatingSample) until a time limit, so every place is
 * recounted within a cycle. docs/numbers.md lists every number on the site and its check.
 *
 * Regions and subregions (Place.region, 2026-10-20): their specimens are those counted on the pages of
 * their countries (Geolocale.regionSpecimensSql: collected in one of its countries, of a countable status,
 * identified to a name on that country's list), with the name also on the region's list. Until a nightly
 * run has counted them by this rule (compute.log line with GeolocaleDb.REGION_RULE_LOG), their
 * differences are pending.
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

    /** specimenColumn of a region or subregion: the specimens of its countries. */
    public static final String COUNTRIES = "*countries";

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

        /** A region or subregion (geolocale id key): its specimens are those of its countries. */
        public static Place region(String geolocaleId, String label) {
            return new Place(GEOLOCALE, geolocaleId, label, COUNTRIES, null);
        }

        boolean isFromCountries() { return COUNTRIES.equals(specimenColumn); }

        /** The specimen column that places a specimen here, or null (all specimens; regions: their own rule). */
        String column() { return isFromCountries() ? null : specimenColumn; }

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

        @Override
        public boolean equals(Object o) {
            return o instanceof Place && type.equals(((Place) o).type) && key.equals(((Place) o).key);
        }

        @Override
        public int hashCode() { return type.hashCode() * 31 + key.hashCode(); }
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
        /** Images were added after the last nightly run (image.upload_date). */
        public boolean imagesSinceNightly;
        /** place_image_count exists and has rows. */
        public boolean placeImagesComputed;
        /** A nightly run has counted regions and subregions by the 2026-10-20 rule (their countries). */
        public boolean regionRuleCounted = true;
        /** A nightly run has counted by the rules of 2026-10-21 (CountInvariants.RULES_LOG). */
        public boolean rulesCounted = true;
        /** The invariants over every place (null when not run). */
        public CountInvariants invariants;
        /** The places of today's rotating sample, and how many of them were recounted before the time ran out. */
        public int rotatingPlanned;
        public int rotatingChecked;
        public final List<Place> rotatingSkipped = new ArrayList<>();
        /** Run time of the whole check, and of the recounts of the sample. */
        public long millis;
        public long recountMillis;
        /** Recount of the core places, of the rotating places (part of recountMillis). */
        public long coreMillis;
        public long rotatingMillis;

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
        return run(places, lastRunEnd, true);
    }

    /** regionRuleCounted: regionRuleCounted(compute.log lines); false makes region and subregion differences pending. */
    public Result run(List<Place> places, Timestamp lastRunEnd, boolean regionRuleCounted) throws SQLException {
        return run(places, new ArrayList<>(), 0, lastRunEnd, regionRuleCounted, true, false, null);
    }

    /**
     * The whole check (2026-10-21): the invariants over every place (CountInvariants, when invariants is true),
     * the recount of the core places (always) and of the rotating places (until the whole check has run for
     * secondsForRotating seconds; the rest are listed as not checked this time, so the request stays short).
     * statsPageValidSpecies: statsPage's first number (TaxonMgr) or null.
     */
    public Result run(List<Place> places, List<Place> rotating, int secondsForRotating, Timestamp lastRunEnd, boolean regionRuleCounted,
                      boolean rulesCounted, boolean invariants, Integer statsPageValidSpecies) throws SQLException {
        long start = System.currentTimeMillis();
        Result result = new Result();
        result.regionRuleCounted = regionRuleCounted;
        result.rulesCounted = rulesCounted;
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(LAST_NIGHTLY_SQL)) {
            r.next();
            result.countedUntil = r.getTimestamp(1);
            result.inNightlyWindow = r.getBoolean(2);
        }
        result.lastRunEnd = lastRunEnd;
        if (lastRunEnd != null && !result.inNightlyWindow) result.countedUntil = lastRunEnd;
        Set<String> changedNames = changedNamesSince(result);
        imagesSince(result);

        if (invariants) {
            result.invariants = new CountInvariants(connection, result, changedNames, rulesCounted);
            result.invariants.statsPageValidSpecies = statsPageValidSpecies;
            result.invariants.run();
        }

        long recountStart = System.currentTimeMillis();
        // With the invariants run, every place's genus and subfamily rows and Global numbers are already checked
        // (list.genus, list.subfamily, global.specimens): the recounts skip them (2026-10-21: core places took 76 s on staging).
        for (Place place : places) recount(place, result, changedNames, !invariants);
        result.coreMillis = System.currentTimeMillis() - recountStart;
        result.rotatingPlanned = rotating.size();
        long rotatingStart = System.currentTimeMillis();
        for (Place place : rotating) {
            if (result.places.contains(place)) continue;
            // the time limit counts the whole check (the invariants and the core places too)
            if (System.currentTimeMillis() - start > secondsForRotating * 1000L) {
                result.rotatingSkipped.add(place);
                continue;
            }
            // With the invariants run, the rotating places skip what they already checked for every place
            // (genus and subfamily rows against their children: list.genus, list.subfamily; Global: global.specimens).
            recount(place, result, changedNames, !invariants);
            ++result.rotatingChecked;
        }
        result.rotatingMillis = System.currentTimeMillis() - rotatingStart;
        result.recountMillis = System.currentTimeMillis() - recountStart;
        result.millis = System.currentTimeMillis() - start;
        return result;
    }

    /** The recounts from the specimen records of one place. */
    void recount(Place place, Result result, Set<String> changedNames, boolean all) throws SQLException {
        result.places.add(place);
        int rows = 0;
        rows += checkSpecimens(place, result, changedNames);
        if (all) {
            rows += checkChildren(place, "genus", "species_count", "('species', 'subspecies')", "species", result, changedNames);
            rows += checkChildren(place, "subfamily", "genus_count", "('genus')", "genera", result, changedNames);
            if (!PROJECT.equals(place.type)) rows += checkGlobal(place, result, changedNames);
        }
        rows += checkPlaceImages(place, result, changedNames);
        rows += checkPlaceSpecimens(place, result, changedNames);
        rows += checkRowImages(place, result, changedNames);
        result.rowsChecked.put(place, rows);
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

    /** Whether compute.log has a line written by the region and subregion counts of the 2026-10-20 rule. */
    public static boolean regionRuleCounted(List<String> lines) {
        for (String line : lines) {
            if (line.contains(org.calacademy.antweb.home.GeolocaleDb.REGION_RULE_LOG)) return true;
        }
        return false;
    }

    static final String REGION_RULE_PENDING = "regions and subregions are counted from their countries since 2026-10-20;"
        + " no nightly run has done it yet";

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

    void imagesSince(Result result) throws SQLException {
        result.placeImagesComputed = new org.calacademy.antweb.home.PlaceImageCountDb(connection).isComputed();
        if (!result.placeImagesComputed) {
            result.notes.add("The place image numbers (Images, Imaged specimens) have not been computed yet: they are"
                + " written by nightly set 4 (place_image_count). Their check starts after the next nightly run.");
        }
        if (tableExists("image")) {
            try (PreparedStatement stmt = connection.prepareStatement("select count(*) from image where upload_date >= ?")) {
                stmt.setTimestamp(1, result.countedUntil);
                try (ResultSet r = stmt.executeQuery()) {
                    r.next();
                    result.imagesSinceNightly = r.getInt(1) > 0;
                }
            }
        }
    }

    /** Regions and subregions: the specimens of their countries, each once (alias rsc, column code); 4 parameters (the id). */
    static String regionSpecimens() {
        return "(select distinct rs.code from (" + org.calacademy.antweb.geolocale.Geolocale.regionSpecimensSql(" and r.id = ?") + ") rs) rsc";
    }

    /**
     * The specimens of a place joined to their images (alias s, i), with the place's own rules;
     * parameters: (regions: 4 times the id), key, column value.
     */
    static String placeImageFrom(Place place) {
        boolean list = GEOLOCALE.equals(place.type) || PROJECT.equals(place.type);
        return " from specimen s"
            + (place.isFromCountries() ? " join " + regionSpecimens() + " on rsc.code = s.code" : "")
            + " join image i on i.image_of_id = s.code"
            + (list ? " join " + place.table() + " l on l.taxon_name = s.taxon_name and l." + place.keyColumn() + " = ?"
                    + " join taxon t on t.taxon_name = s.taxon_name and t.taxarank in ('species', 'subspecies') and t.family = 'formicidae'"
                  : "")
            + " where 1 = 1"
            + (list ? "" : " and s.family = 'formicidae'")   // ants only (2026-10-21)
            + (place.column() != null ? " and s." + place.column() + " = ?" : "")
            + (GEOLOCALE.equals(place.type) ? " and s.status in " + COUNTABLE : "");
    }

    private static int setPlaceImageParams(Place place, PreparedStatement stmt) throws SQLException {
        int i = 1;
        if (place.isFromCountries()) for (int n = 0; n < 4; n++) place.setKey(stmt, i++);
        if (GEOLOCALE.equals(place.type) || PROJECT.equals(place.type)) place.setKey(stmt, i++);
        if (place.column() != null) stmt.setString(i++, place.specimenValue);
        return i;
    }

    static String placeTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale";
            case PROJECT: return "project";
            case MUSEUM: return "museum";
            default: return "bioregion";
        }
    }

    static String placeKeyColumn(String type) {
        switch (type) {
            case GEOLOCALE: return "id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            default: return "name";
        }
    }

    /** The place's Images, images per view and Imaged specimens against a recount from the image table. */
    int checkPlaceImages(Place place, Result result, Set<String> changedNames) throws SQLException {
        if (!result.placeImagesComputed) return 0;
        // The list decides which specimens count (geolocales, projects): a row added after the nightly run.
        boolean listChanged = false;
        if (GEOLOCALE.equals(place.type) || PROJECT.equals(place.type)) {
            try (PreparedStatement stmt = connection.prepareStatement("select count(*) from " + place.table()
                    + " where " + place.keyColumn() + " = ? and created >= ?")) {
                place.setKey(stmt, 1);
                stmt.setTimestamp(2, result.countedUntil);
                try (ResultSet r = stmt.executeQuery()) {
                    r.next();
                    listChanged = r.getInt(1) > 0;
                }
            }
        }
        String shot = "lower(trim(coalesce(i.shot_type, '')))";
        Map<String, Integer> recount = new java.util.TreeMap<>();
        try (PreparedStatement stmt = connection.prepareStatement("select " + shot + ", count(distinct i.id)" + placeImageFrom(place) + " group by 1")) {
            setPlaceImageParams(place, stmt);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) recount.merge(r.getString(1) == null ? "" : r.getString(1), r.getInt(2), Integer::sum);
            }
        }
        int imagedRecount;
        try (PreparedStatement stmt = connection.prepareStatement("select count(distinct s.code)" + placeImageFrom(place) + " and " + shot + " <> 'l'")) {
            setPlaceImageParams(place, stmt);
            try (ResultSet r = stmt.executeQuery()) {
                r.next();
                imagedRecount = r.getInt(1);
            }
        }
        int allRecount = 0;
        for (int n : recount.values()) allRecount += n;

        org.calacademy.antweb.home.PlaceImageCountDb.PlaceImages stored = new org.calacademy.antweb.home.PlaceImageCountDb(connection).read(place.type, place.key);
        Map<String, Integer> storedShots = stored == null ? new HashMap<>() : stored.shotImages;
        int storedAll = -1;
        int storedImaged = -1;
        try (PreparedStatement stmt = connection.prepareStatement("select image_count, imaged_specimen_count from "
                + placeTable(place.type) + " where " + placeKeyColumn(place.type) + " = ?")) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                if (r.next()) {
                    storedAll = r.getInt(1);
                    storedImaged = r.getInt(2);
                }
            }
        }
        String pending = !result.rulesCounted ? CountInvariants.RULES_PENDING
            : place.isFromCountries() && !result.regionRuleCounted ? REGION_RULE_PENDING
            : result.imagesSinceNightly ? "images were added after the last nightly run"
            : result.uploadSinceNightly ? "specimens were uploaded after the last nightly run"
            : listChanged ? "the list got a new row after the last nightly run"
            : !changedNames.isEmpty() ? "specimens were renamed after the last nightly run" : null;
        int rows = 0;
        Set<String> shots = new java.util.TreeSet<>(recount.keySet());
        shots.addAll(storedShots.keySet());
        for (String s : shots) {
            ++rows;
            int st = storedShots.getOrDefault(s, 0);
            int c = recount.getOrDefault(s, 0);
            if (st != c) result.mismatches.add(new Mismatch(place, "place images", "view '" + s + "'", st, c, pending));
        }
        if (storedAll >= 0) {
            rows += 3;
            if (storedAll != allRecount) result.mismatches.add(new Mismatch(place, "place images", "Images (all files)", storedAll, allRecount, pending));
            if (storedImaged != imagedRecount) result.mismatches.add(new Mismatch(place, "place images", "Imaged specimens", storedImaged, imagedRecount, pending));
            if (storedAll < storedImaged) {
                result.mismatches.add(new Mismatch(place, "place images", "Images smaller than Imaged specimens", storedAll, storedImaged, null));
            }
        }
        rows += checkSubfamilyTotal(place, result, listChanged);
        return rows;
    }

    /**
     * Geolocales: the Specimens number of the page (geolocale.specimen_count) against a recount of the
     * specimens of the species and subspecies rows of its list (the specimens of the "specimens" check).
     */
    int checkPlaceSpecimens(Place place, Result result, Set<String> changedNames) throws SQLException {
        if (PROJECT.equals(place.type)) return checkProjectSpecimens(place, result, changedNames);
        if (!GEOLOCALE.equals(place.type) || !columnExists("geolocale", "specimen_count")) return 0;
        int stored;
        try (PreparedStatement stmt = connection.prepareStatement("select specimen_count from geolocale where id = ?")) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                if (!r.next()) return 0;
                stored = r.getInt(1);
            }
        }
        String query = "select count(*) from specimen s"
            + (place.isFromCountries() ? " join " + regionSpecimens() + " on rsc.code = s.code" : "")
            + " join geolocale_taxon l on l.taxon_name = s.taxon_name and l.geolocale_id = ?"
            + " join taxon t on t.taxon_name = s.taxon_name and t.taxarank in ('species', 'subspecies') and t.family = 'formicidae'"
            + " where s.status in " + COUNTABLE
            + (place.column() != null ? " and s." + place.column() + " = ?" : "");
        int recount;
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int i = 1;
            if (place.isFromCountries()) for (int n = 0; n < 4; n++) place.setKey(stmt, i++);
            place.setKey(stmt, i++);
            if (place.column() != null) stmt.setString(i, place.specimenValue);
            try (ResultSet r = stmt.executeQuery()) {
                r.next();
                recount = r.getInt(1);
            }
        }
        if (stored != recount) {
            boolean listChanged;
            try (PreparedStatement stmt = connection.prepareStatement("select count(*) from geolocale_taxon where geolocale_id = ? and created >= ?")) {
                place.setKey(stmt, 1);
                stmt.setTimestamp(2, result.countedUntil);
                try (ResultSet r = stmt.executeQuery()) {
                    r.next();
                    listChanged = r.getInt(1) > 0;
                }
            }
            String pending = !result.rulesCounted ? CountInvariants.RULES_PENDING
                : place.isFromCountries() && !result.regionRuleCounted ? REGION_RULE_PENDING
                : result.uploadSinceNightly ? "specimens were uploaded after the last nightly run"
                : listChanged ? "the list got a new row after the last nightly run"
                : !changedNames.isEmpty() ? "specimens were renamed after the last nightly run" : null;
            result.mismatches.add(new Mismatch(place, "place specimens", "Specimens", stored, recount, pending));
        }
        return 1;
    }

    /**
     * Projects (2026-10-21): the Specimens number of the page (project.specimen_count) against a recount of all
     * specimens (any status, collected anywhere) of the species and subspecies on the project's list.
     */
    int checkProjectSpecimens(Place place, Result result, Set<String> changedNames) throws SQLException {
        if (!columnExists("project", "specimen_count")) return 0;
        int stored;
        try (PreparedStatement stmt = connection.prepareStatement("select specimen_count from project where project_name = ?")) {
            stmt.setString(1, place.key);
            try (ResultSet r = stmt.executeQuery()) {
                if (!r.next()) return 0;
                stored = r.getInt(1);
            }
        }
        int recount;
        try (PreparedStatement stmt = connection.prepareStatement("select count(*) from specimen s"
                + " join proj_taxon l on l.taxon_name = s.taxon_name and l.project_name = ?"
                + " join taxon t on t.taxon_name = s.taxon_name and t.taxarank in ('species', 'subspecies') and t.family = 'formicidae'")) {
            stmt.setString(1, place.key);
            try (ResultSet r = stmt.executeQuery()) {
                r.next();
                recount = r.getInt(1);
            }
        }
        if (stored != recount) {
            String pending = !result.rulesCounted ? CountInvariants.RULES_PENDING
                : result.uploadSinceNightly ? "specimens were uploaded after the last nightly run"
                : listChangedSince(place, result) ? "the list got a new row after the last nightly run"
                : !changedNames.isEmpty() ? "specimens were renamed after the last nightly run" : null;
            result.mismatches.add(new Mismatch(place, "place specimens", "Specimens", stored, recount, pending));
        }
        return 1;
    }

    private boolean listChangedSince(Place place, Result result) throws SQLException {
        if (!columnExists(place.table(), "created")) return false;
        try (PreparedStatement stmt = connection.prepareStatement("select count(*) from " + place.table()
                + " where " + place.keyColumn() + " = ? and created >= ?")) {
            place.setKey(stmt, 1);
            stmt.setTimestamp(2, result.countedUntil);
            try (ResultSet r = stmt.executeQuery()) {
                r.next();
                return r.getInt(1) > 0;
            }
        }
    }

    /**
     * The Images column of the place's list (2026-10-21), species and subspecies rows: image_count = the images
     * (labels included) of the specimens of that name in the place, as nightly set 4 counts them
     * (getTaxonImageCountQuery of Geolocale, Project, Museum, Bioregion): geolocales of a countable status,
     * museums and bioregions their own specimens, projects all specimens of the name.
     */
    int checkRowImages(Place place, Result result, Set<String> changedNames) throws SQLException {
        if (!columnExists(place.table(), "image_count")) return 0;
        Map<String, Integer> recount = CountInvariants.keyMap();   // names as MySQL compares them
        String query = "select s.taxon_name, sum(s.image_count) from specimen s"
            + (place.isFromCountries() ? " join " + regionSpecimens() + " on rsc.code = s.code" : "")
            + " join " + place.table() + " l on l.taxon_name = s.taxon_name and l." + place.keyColumn() + " = ?"
            + " where 1 = 1"
            + (place.column() != null ? " and s." + place.column() + " = ?" : "")
            + (GEOLOCALE.equals(place.type) ? " and s.status in " + COUNTABLE : "")
            + " group by s.taxon_name";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int i = 1;
            if (place.isFromCountries()) for (int n = 0; n < 4; n++) place.setKey(stmt, i++);
            place.setKey(stmt, i++);
            if (place.column() != null) stmt.setString(i, place.specimenValue);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) recount.put(r.getString(1), r.getInt(2));
            }
        }
        int rows = 0;
        try (PreparedStatement stmt = connection.prepareStatement("select l.taxon_name, l.image_count, " + CountInvariants.safeDate("l.created") + " from " + place.table() + " l"
                + " join taxon t on t.taxon_name = l.taxon_name where l." + place.keyColumn() + " = ? and t.taxarank in ('species', 'subspecies')")) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    ++rows;
                    String name = r.getString(1);
                    int s = r.getInt(2);
                    int c = recount.getOrDefault(name, 0);
                    if (s == c) continue;
                    String pending = rowPending(r.getTimestamp(3), result);
                    if (pending == null && place.isFromCountries() && !result.regionRuleCounted) pending = REGION_RULE_PENDING;
                    if (pending == null && result.imagesSinceNightly) pending = "images were added after the last nightly run";
                    if (pending == null && changedNames.contains(name)) pending = "specimens of this name uploaded or renamed after the last nightly run";
                    if (pending == null && result.uploadSinceNightly) pending = "specimens were uploaded after the last nightly run";
                    result.mismatches.add(new Mismatch(place, "images", name, s, c, pending));
                }
            }
        }
        return rows;
    }

    /** The place's Subfamilies number (2026-10-20: staging's Madagascar page showed 0 with 8 subfamilies on
     *  its list): stored subfamily_count against the subfamily rows on the place's list with a counted status. */
    int checkSubfamilyTotal(Place place, Result result, boolean listChanged) throws SQLException {
        if (!GEOLOCALE.equals(place.type) && !PROJECT.equals(place.type)) return 0;
        if (!columnExists(placeTable(place.type), "subfamily_count")) return 0;
        int stored = -1;
        try (PreparedStatement stmt = connection.prepareStatement("select subfamily_count from "
                + placeTable(place.type) + " where " + placeKeyColumn(place.type) + " = ?")) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) { if (r.next()) stored = r.getInt(1); }
        }
        if (stored < 0) return 0;
        int recount;
        try (PreparedStatement stmt = connection.prepareStatement("select count(distinct l.taxon_name) from " + place.table() + " l"
                + " join taxon t on t.taxon_name = l.taxon_name"
                + " where l." + place.keyColumn() + " = ? and t.taxarank = 'subfamily' and t.status in " + COUNTABLE + " and t.family = 'formicidae'")) {
            place.setKey(stmt, 1);
            try (ResultSet r = stmt.executeQuery()) { r.next(); recount = r.getInt(1); }
        }
        if (stored != recount) {
            result.mismatches.add(new Mismatch(place, "place totals", "Subfamilies", stored, recount,
                !result.rulesCounted ? CountInvariants.RULES_PENDING : listChanged ? "the list got a new row after the last nightly run" : null));
        }
        return 1;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?")) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet r = stmt.executeQuery()) { return r.next(); }
        }
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
        Map<String, Integer> recount = CountInvariants.keyMap();   // names as MySQL compares them
        String query = "select s.taxon_name, count(*) from specimen s"
            + (place.isFromCountries() ? " join " + regionSpecimens() + " on rsc.code = s.code" : "")
            + " join " + t + " l on l.taxon_name = s.taxon_name and l." + k + " = ?"
            + " where 1 = 1"
            + (place.column() != null ? " and s." + place.column() + " = ?" : "")
            + (place.statusFiltered() ? " and s.status in " + COUNTABLE : "")
            + " group by s.taxon_name";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int i = 1;
            if (place.isFromCountries()) for (int n = 0; n < 4; n++) place.setKey(stmt, i++);
            place.setKey(stmt, i++);
            if (place.column() != null) stmt.setString(i, place.specimenValue);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) recount.put(r.getString(1), r.getInt(2));
            }
        }
        int rows = 0;
        String stored = "select l.taxon_name, l.specimen_count, " + CountInvariants.safeDate("l.created") + " from " + t + " l"
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
                    if (pending == null && place.isFromCountries() && !result.regionRuleCounted) pending = REGION_RULE_PENDING;
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
        Map<String, Integer> recount = CountInvariants.keyMap();   // names as MySQL compares them
        Map<String, Boolean> childPending = CountInvariants.keyMap();
        String query = "select taxon.parent_taxon_name, l.taxon_name, " + CountInvariants.safeDate("l.created") + " from taxon"
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
        String stored = "select l.taxon_name, l." + column + ", " + CountInvariants.safeDate("l.created") + " from " + t + " l"
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
        Map<String, Integer> recount = CountInvariants.keyMap();   // names as MySQL compares them
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
        String stored = "select a.taxon_name, a.specimen_count, " + CountInvariants.safeDate("a.created") + " from proj_taxon a"
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

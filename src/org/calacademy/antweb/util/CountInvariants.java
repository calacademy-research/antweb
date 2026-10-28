package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.calacademy.antweb.home.PlaceTotals;

/**
 * Invariants of the count check (countCheck.do), 2026-10-21. Brian Fisher, 2026-10-20: "every number shown on
 * the site must be validated by a test, so new code can't undo past progress".
 *
 * CountCheck recounts a sample of places from the specimen records (slow: it reads specimens). This class checks
 * every place of every kind (country, adm1, island, region, subregion, project, museum, bioregion) on every run,
 * with grouped queries over the place tables, the taxon lists and the image table (fast: one query per check for
 * all places at once). Each check compares a number the site shows with the number it must equal (or not exceed),
 * as listed in docs/numbers.md:
 *
 *   place.subfamilies, place.genera, place.species   the place's totals = the rows of that rank on its list
 *                                                    (PlaceTotals: counted status; species: species and subspecies)
 *   place.valid              Valid species = valid extant species and subspecies on the list; and Valid <= Species
 *   place.specimens          geolocales, projects: Specimens = sum of the species and subspecies rows' specimens;
 *                            museums, bioregions: = their specimens (specimen.museum, specimen.bioregion)
 *   place.endemic, place.introduced   = the rows flagged on the list with a counted status (the rows endemic.do and
 *                                       introduced.do show; PlaceTotals.flaggedCondition, 2026-10-27)
 *   place.pies               "Species per Subfamily" adds up to Species, "Specimens per Subfamily" to Specimens
 *   place.images             Images >= Imaged specimens <= Specimens; Images = all views + labels; Ant images =
 *                            sum of the views; specimens with images = imaged + label-only; the place table's
 *                            Images and Imaged specimens = place_image_count
 *   list.genus, list.subfamily, list.family   per row of every list: a genus row's Species = its species and
 *                            subspecies rows, its Specimens = their sum; a subfamily row's Genera = its genus rows,
 *                            Species and Specimens = their sums; the Formicidae row likewise
 *   list.global              a species row's specimens in a place <= its "Global" specimens (allantwebants)
 *   global.specimens         "Global" specimens of every species = all its specimen records
 *   home                     home page and statsPage numbers (latest statistics row) = a recount
 *   taxon.images             taxon image_count = images of its own specimens + of the taxa under it;
 *                            specimen image_count = its images
 *   geo.regions              a subregion's or region's Specimens <= the sum of its countries' Specimens
 *
 * A difference is "pending" (expected to go away after the next nightly run) when something changed after the
 * last nightly run (the place's list got a row, an upload, new images, renamed specimens) or when the number's
 * rule changed on 2026-10-21 and no nightly run has counted it that way yet (compute.log has no RULES_LOG line).
 * Every other difference is "real". Read-only.
 */
public final class CountInvariants {

    /** Written to compute.log by nightly set 4 since 2026-10-21; countCheck.do reads it. */
    public static final String RULES_LOG = "numbers counted by the rules of 2026-10-21";

    /** Every check id (docs/numbers.md names them in its Test column as inv:<id>; tools/numbers/check_numbers_doc.py). */
    public static final String[] IDS = {"place.totals.subfamily", "place.totals.genus", "place.totals.species", "place.totals.valid",
        "place.totals.specimens", "place.totals.endemic", "place.totals.introduced", "place.pies", "place.images.order", "place.images",
        "list.genus", "list.subfamily", "list.family", "list.global", "global.specimens", "home", "taxon.images", "geo.regions",
        // NumberChecks (2026-10-22)
        "stats.global", "stats.images", "stats.imaged", "stats.bioregions", "stats.museums", "stats.nonants", "list.images", "admin.statistics", "admin.groups",
        "admin.curators", "admin.artists", "admin.imageUploads", "admin.imageUploadArtists", "api.taxa", "place.endemic.flags", "taxon.parents", "geo.adm1.country"};

    /**
     * At most this many differences of one check are listed in the report (2026-10-22: API /taxa, B11, can differ for
     * every taxon); the others are counted (CountCheck.Result.unlisted) and named in one line.
     */
    static final int LIST_CAP = 300;

    static final String RULES_PENDING = "this number's rule changed on 2026-10-21; no nightly run has counted it that way yet";
    /** The note of the report on the projects the check skips (Project.isSpeciesListOnly). */
    public static final String SPECIES_LIST_ONLY_NOTE = "Port of Entry (project " + org.calacademy.antweb.Project.PORT_OF_ENTRY + ") is not checked: it is a"
        + " list of species intercepted at ports of entry, kept without specimen or image numbers (Brian Fisher, 2026-10-22), so no nightly"
        + " step counts them.";
    /** A number whose rule changed on 2026-10-22 (docs/numbers.md Q7, Q8): it still equals the count by the old rule. */
    static final String ANT_IMAGES_PENDING = "counted every image file, labels included, before 2026-10-27; the next nightly run counts images of the ant";
    static final String Q7_PENDING = "counted by the rule before 2026-10-22 (ants only, Q7); the next nightly run counts it by the new rule";

    public static boolean rulesCounted(List<String> lines) {
        for (String line : lines) if (line.contains(RULES_LOG)) return true;
        return false;
    }

    static final String COUNTABLE = PlaceTotals.COUNTABLE;

    /**
     * Keys (taxon names, specimen codes, museum codes, place names) compared as MySQL compares them: the
     * database's collation (utf8_general_ci) ignores case and trailing spaces, so a key read from one table
     * can differ in case from the same key in another (2026-10-21: specimen.code against image.image_of_id
     * on staging). Java maps of keys use this order.
     */
    static final java.util.Comparator<String> KEYS = java.util.Comparator.comparing(CountInvariants::key);

    static String key(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') --end;
        return s.substring(0, end).toLowerCase(java.util.Locale.ROOT);
    }

    static <V> Map<String, V> keyMap() { return new java.util.TreeMap<>(KEYS); }

    static Set<String> keySet() { return new java.util.TreeSet<>(KEYS); }

    /** A datetime column as SQL that never reads a zero date ('0000-00-00', which Connector/J refuses to read). */
    static String safeDate(String column) {
        return "if(" + column + " < '1971-01-01', null, " + column + ")";
    }

    /** The SQL last run by the current check (shown with its error). */
    String lastSql;

    String track(String sql) {
        lastSql = sql;
        return sql;
    }
    static final String SP = "('species', 'subspecies')";

    /** One check, run over all places. */
    public static final class Check {
        public final String id;
        public final String title;
        /** Rows of docs/numbers.md this check covers. */
        public final String rows;
        boolean rule;      // rule changed on 2026-10-21
        boolean list;      // pending when the place's list got a row after the nightly run
        boolean upload;    // pending after a specimen upload
        boolean images;    // pending after new images
        public int compared;
        public int real;
        public int pending;
        public long millis;
        public String error;
        /** Differences listed in the report (at most LIST_CAP). */
        int listed;

        Check(String id, String title, String rows) {
            this.id = id;
            this.title = title;
            this.rows = rows;
        }

        Check pendingOn(boolean rule, boolean list, boolean upload, boolean images) {
            this.rule = rule;
            this.list = list;
            this.upload = upload;
            this.images = images;
            return this;
        }
    }

    final Connection connection;
    final CountCheck.Result result;
    private final Set<String> changedNames;   // KEYS order
    private final boolean rulesCounted;
    /** statsPage's first number (TaxonMgr, in memory); null when not known (tests). */
    public Integer statsPageValidSpecies;
    /**
     * The numbers of curators.do and curator.do as the pages show them (LoginMgr): login id -> Specimen Uploads,
     * Description Edits, image uploads, images uploaded; null when not known (the check is then not run).
     */
    public Map<Integer, int[]> curatorNumbers;
    /** When the pages' curator numbers were read (LoginMgr.getCuratorsRead); null: the last nightly run. */
    public java.sql.Timestamp curatorsRead;
    /** artists.do as the page shows it (ArtistMgr): artist id -> Images, Specimen; null when not known. */
    public Map<Integer, int[]> artistNumbers;
    /** Numbers the end-to-end tests compare with the pages (report: JSON in the element with id count-check-expected). */
    public final Map<String, Object> expected = new LinkedHashMap<>();

    public final List<Check> checks = new ArrayList<>();
    public long millis;
    /** Part of millis: the checks of NumberChecks (statsPage, admin pages, API, endemic flags, B25, B26). */
    public long otherMillis;

    /** type -> key -> label of the places in scope (the places whose pages show these numbers). */
    final Map<String, Map<String, String>> places = new LinkedHashMap<>();
    /** type -> key -> georank (geolocales). */
    final Map<String, String> georank = new HashMap<>();
    final Map<String, Set<String>> listChanged = new HashMap<>();
    private final Map<String, CountCheck.Place> placeObjects = new HashMap<>();

    public CountInvariants(Connection connection, CountCheck.Result result, Set<String> changedNames, boolean rulesCounted) {
        this.connection = connection;
        this.result = result;
        this.changedNames = keySet();
        if (changedNames != null) this.changedNames.addAll(changedNames);
        this.rulesCounted = rulesCounted;
    }

    // ------------------------------------------------------------------ running

    interface Body {
        void run(Check check) throws SQLException;
    }

    void check(Check check, Body body) {
        long start = System.currentTimeMillis();
        try {
            body.run(check);
        } catch (SQLException | RuntimeException e) {
            check.error = errorText(e, lastSql);
            CountCheck.Place p = place("site", "*", "site");
            result.mismatches.add(new CountCheck.Mismatch(p, check.id, "the check could not run: " + check.error, 0, 0, null));
            ++check.real;
        }
        lastSql = null;
        check.millis = System.currentTimeMillis() - start;
        checks.add(check);
    }

    /** The exception, where in AntWeb's code it happened, and the SQL last run (cut to 400 characters). */
    static String errorText(Exception e, String sql) {
        StringBuilder b = new StringBuilder(e.toString());
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith("org.calacademy")) {
                b.append(" (at ").append(f.getClassName().replaceAll(".*\\.", "")).append(".java:").append(f.getLineNumber()).append(")");
                break;
            }
        }
        if (sql != null) b.append("; SQL: ").append(sql.length() > 400 ? sql.substring(0, 400) + "..." : sql);
        return b.toString();
    }

    public void run() throws SQLException {
        long start = System.currentTimeMillis();
        loadPlaces();
        for (String type : PlaceTotals.TYPES) {
            if (!places.containsKey(type)) continue;
            totals(type);
        }
        placeImages();
        for (String type : PlaceTotals.TYPES) {
            if (!places.containsKey(type)) continue;
            listRows(type);
        }
        globalPerPlace();
        globalSpecimens();
        home();
        taxonImages();
        regions();
        long other = System.currentTimeMillis();
        new NumberChecks(this).run();
        otherMillis = System.currentTimeMillis() - other;
        millis = System.currentTimeMillis() - start;
    }

    // ------------------------------------------------------------------ places

    static String scopeSql(String type) {
        switch (type) {
            case PlaceTotals.GEOLOCALE:
                return "select id, name, georank from geolocale where is_valid = 1 and georank in ('country', 'adm1', 'subregion', 'region')"
                    + " and (georank <> 'adm1' or is_live = 1)";
            // the projects whose totals the nightly run counts (ProjectDb.updateCounts: scope PROJECT and GLOBAL) and whose
            // lists it recounts (live projects)
            // The Port of Entry project is a species list only (2026-10-22): its numbers are not counted, so not checked.
            case PlaceTotals.PROJECT: return "select project_name, project_name, null from project where is_live = 1 and scope in ('PROJECT', 'GLOBAL')"
                + " and project_name not in " + org.calacademy.antweb.Project.SPECIES_LIST_ONLY_SQL;
            case PlaceTotals.MUSEUM: return "select code, code, null from museum";
            default: return "select name, name, null from bioregion";
        }
    }

    void loadPlaces() throws SQLException {
        for (String type : PlaceTotals.TYPES) {
            if (!tableExists(PlaceTotals.placeTable(type)) || !tableExists(PlaceTotals.listTable(type))) continue;
            // a type whose places cannot be read is reported as a failed check, the other types go on
            check(new Check("places." + type, "read the " + type + " places", ""), c -> loadPlaces(type));
            if (checks.get(checks.size() - 1).error == null) checks.remove(checks.size() - 1);
        }
    }

    private void loadPlaces(String type) throws SQLException {
        {
            Map<String, String> m = keyMap();
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(scopeSql(type)))) {
                while (r.next()) {
                    String key = r.getString(1);
                    String rank = r.getString(3);
                    String label;
                    switch (type) {
                        case PlaceTotals.GEOLOCALE:
                            label = "country".equals(rank) ? r.getString(2) : r.getString(2) + " (" + rank + ")";
                            georank.put(key, rank);
                            break;
                        case PlaceTotals.PROJECT: label = "project " + r.getString(2); break;
                        case PlaceTotals.MUSEUM: label = "museum " + r.getString(2); break;
                        default: label = r.getString(2) + " bioregion";
                    }
                    m.put(key, label);
                }
            }
            places.put(type, m);
            if (PlaceTotals.PROJECT.equals(type)) result.notes.add(SPECIES_LIST_ONLY_NOTE);
            Set<String> changed = keySet();
            if (columnExists(PlaceTotals.listTable(type), "created")) {
                try (PreparedStatement stmt = connection.prepareStatement(track("select distinct " + PlaceTotals.listKey(type) + " from "
                        + PlaceTotals.listTable(type) + " where created >= ?"))) {
                    stmt.setTimestamp(1, result.countedUntil);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) changed.add(r.getString(1));
                    }
                }
            }
            listChanged.put(type, changed);
        }
    }

    CountCheck.Place place(String type, String key, String label) {
        return placeObjects.computeIfAbsent(type + "\u0000" + key, k -> new CountCheck.Place(type, key, label, null, null));
    }

    private boolean isRegion(String type, String key) {
        String rank = georank.get(key);
        return PlaceTotals.GEOLOCALE.equals(type) && ("region".equals(rank) || "subregion".equals(rank));
    }

    /** Why a difference of this check at this place is expected, or null (real). */
    String why(Check check, String type, String key) {
        if (check.rule && !rulesCounted) return RULES_PENDING;
        if (key != null && isRegion(type, key) && !result.regionRuleCounted) return CountCheck.REGION_RULE_PENDING;
        if (check.list && key != null && listChanged.getOrDefault(type, new HashSet<>()).contains(key)) {
            return "the list got a new row after the last nightly run";
        }
        if (check.upload && result.uploadSinceNightly) return "specimens were uploaded after the last nightly run";
        if (check.images && result.imagesSinceNightly) return "images were added after the last nightly run";
        return null;
    }

    /**
     * Compares one number. relation: "=" (shown must equal expected), "<=" (shown <= expected), ">=".
     * Returns whether it holds.
     */
    boolean compare(Check check, String type, String key, String what, long shown, long expected, String relation, String pendingReason) {
        ++check.compared;
        boolean ok;
        switch (relation) {
            case "<=": ok = shown <= expected; break;
            case ">=": ok = shown >= expected; break;
            default: ok = shown == expected;
        }
        if (ok) return true;
        if (pendingReason == null) ++check.real; else ++check.pending;
        if (++check.listed > LIST_CAP) {
            result.unlisted.merge(check.id + (pendingReason == null ? "" : " (pending)"), 1, Integer::sum);
            return false;
        }
        String label = places.getOrDefault(type, new HashMap<>()).getOrDefault(key, key);
        String text = "=".equals(relation) ? what : what + " (must be " + relation + " " + expected + ")";
        result.mismatches.add(new CountCheck.Mismatch(place(type, key, label), check.id, text, (int) shown, (int) expected, pendingReason));
        return false;
    }

    // ------------------------------------------------------------------ place totals

    /** key -> the columns 2.. of the query as longs. */
    Map<String, long[]> rows(String sql, Object... params) throws SQLException {
        Map<String, long[]> m = keyMap();
        lastSql = sql;
        try (PreparedStatement stmt = connection.prepareStatement(track(sql))) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet r = stmt.executeQuery()) {
                int n = r.getMetaData().getColumnCount();
                while (r.next()) {
                    long[] v = new long[n - 1];
                    for (int c = 2; c <= n; c++) v[c - 2] = r.getLong(c);
                    m.put(r.getString(1), v);
                }
            }
        }
        return m;
    }

    private static long v(Map<String, long[]> m, String key, int i) {
        long[] a = m.get(key);
        return a == null ? 0 : a[i];
    }

    void totals(String type) {
        String t = PlaceTotals.placeTable(type);
        String k = PlaceTotals.placeKey(type);
        String l = PlaceTotals.listTable(type);
        String lk = PlaceTotals.listKey(type);
        Map<String, String> inScope = places.get(type);
        boolean flags = PlaceTotals.GEOLOCALE.equals(type) || PlaceTotals.BIOREGION.equals(type);

        Map<String, long[]> stored = keyMap();
        String extra = flags ? ", endemic_species_count, introduced_species_count" : ", 0, 0";
        check(new Check("place." + type + ".stored", "read the " + t + " table", ""), c -> stored.putAll(rows(
            "select " + k + ", subfamily_count, genus_count, species_count, valid_species_count, specimen_count, image_count,"
            + " imaged_specimen_count" + extra + " from " + t)));
        // not a check of its own, unless it failed: then the table shows it (with the error) instead of 0/0 rows below
        if (checks.get(checks.size() - 1).error == null) checks.remove(checks.size() - 1);

        String[] ranks = {PlaceTotals.SUBFAMILY, PlaceTotals.GENUS, PlaceTotals.SPECIES};
        String[] words = {"Subfamilies", "Genera", "Species"};
        for (int i = 0; i < 3; i++) {
            final int col = i;
            Check check = new Check("place.totals." + ranks[i], words[i] + " of " + type + " pages = " + ranks[i] + " rows on the list",
                ranks[i].equals(PlaceTotals.SUBFAMILY) ? "20" : ranks[i].equals(PlaceTotals.GENUS) ? "21" : "22, 36, 108")
                .pendingOn(true, true, false, false);
            check(check, c -> {
                Map<String, long[]> counted = rows(PlaceTotals.listedCountsByPlaceSql(type, ranks[col]));
                for (String key : inScope.keySet()) {
                    if (!stored.containsKey(key)) continue;
                    if (PlaceTotals.PROJECT.equals(type) && "worldants".equals(key) && v(stored, key, 0) == 0) continue;
                    compare(c, type, key, words[col], v(stored, key, col), v(counted, key, 0), "=", why(c, type, key));
                }
            });
        }

        Check valid = new Check("place.totals.valid", "Valid species of " + type + " pages = valid extant species rows; <= Species", "23, 73")
            .pendingOn(true, true, false, false);
        check(valid, c -> {
            Map<String, long[]> counted = rows(PlaceTotals.validSpeciesByPlaceSql(type));
            for (String key : inScope.keySet()) {
                if (!stored.containsKey(key)) continue;
                String p = why(c, type, key);
                compare(c, type, key, "Valid species", v(stored, key, 3), v(counted, key, 0), "=", p);
                compare(c, type, key, "Valid species", v(stored, key, 3), v(stored, key, 2), "<=", p);
            }
        });

        Check specimens = new Check("place.totals.specimens", "Specimens of " + type + " pages", "26, 74, 108");
        if (PlaceTotals.MUSEUM.equals(type) || PlaceTotals.BIOREGION.equals(type)) {
            String column = PlaceTotals.MUSEUM.equals(type) ? "museum" : "bioregion";
            specimens.pendingOn(true, false, true, false);
            check(specimens, c -> {
                Map<String, long[]> counted = rows("select " + column + ", count(*) from specimen where " + column + " is not null"
                    + " and family = 'formicidae' group by " + column);
                for (String key : inScope.keySet()) {
                    if (!stored.containsKey(key)) continue;
                    compare(c, type, key, "Specimens", v(stored, key, 4), v(counted, key, 0), "=", why(c, type, key));
                }
            });
        } else {
            specimens.pendingOn(true, true, false, false);
            check(specimens, c -> {
                Map<String, long[]> summed = rows("select l." + lk + ", sum(l.specimen_count) from " + l + " l join taxon t on t.taxon_name = l.taxon_name"
                    + " where t.taxarank in " + SP + PlaceTotals.ANTS + " group by l." + lk);
                for (String key : inScope.keySet()) {
                    if (!stored.containsKey(key)) continue;
                    compare(c, type, key, "Specimens", v(stored, key, 4), v(summed, key, 0), "=", why(c, type, key));
                }
            });
        }

        if (flags) {
            for (String field : new String[] {"endemic", "introduced"}) {
                final int col = "endemic".equals(field) ? 7 : 8;
                Check check = new Check("place.totals." + field, ("endemic".equals(field) ? "Endemic" : "Introduced") + " of " + type
                    + " pages = rows flagged on the list (" + field + ".do)", "endemic".equals(field) ? "24, 78, 110" : "25, 79, 110")
                    .pendingOn("endemic".equals(field), true, false, false);
                check(check, c -> {
                    if (!columnExists(l, "is_" + field)) return;
                    // the rule of the lists and the numbers: flagged rows of a counted status (B70, 2026-10-27)
                    Map<String, long[]> flagged = rows("select l." + lk + ", count(*) from " + l + " l where "
                        + PlaceTotals.flaggedCondition(field) + " group by l." + lk);
                    for (String key : inScope.keySet()) {
                        if (!stored.containsKey(key)) continue;
                        compare(c, type, key, "endemic".equals(field) ? "Endemic" : "Introduced", v(stored, key, col), v(flagged, key, 0), "=", why(c, type, key));
                    }
                });
            }
        }

        Check pies = new Check("place.pies", "Pies of " + type + " pages add up to Species and Specimens", "34, 35")
            .pendingOn(true, true, PlaceTotals.MUSEUM.equals(type) || PlaceTotals.BIOREGION.equals(type), false);
        check(pies, c -> {
            try (Statement stmt = connection.createStatement();
                 ResultSet r = stmt.executeQuery(track("select " + k + ", taxon_subfamily_dist_json, specimen_subfamily_dist_json, species_count, specimen_count from " + t))) {
                while (r.next()) {
                    String key = r.getString(1);
                    if (!inScope.containsKey(key)) continue;
                    if (PlaceTotals.PROJECT.equals(type) && "worldants".equals(key)) continue;
                    String p = why(c, type, key);
                    compare(c, type, key, "Species per Subfamily (sum of slices) = Species", PlaceTotals.pieSum(r.getString(2)), r.getLong(4), "=", p);
                    compare(c, type, key, "Specimens per Subfamily (sum of slices) = Specimens", PlaceTotals.pieSum(r.getString(3)), r.getLong(5), "=", p);
                }
            }
        });

        Check images = new Check("place.images.order", "Images >= Imaged specimens <= Specimens on " + type + " pages", "27, 29, 33, 75, 109")
            .pendingOn(false, true, true, true);
        check(images, c -> {
            for (String key : inScope.keySet()) {
                if (!stored.containsKey(key)) continue;
                String p = why(c, type, key);
                compare(c, type, key, "Imaged specimens", v(stored, key, 6), v(stored, key, 5), "<=", p);
                compare(c, type, key, "Imaged specimens", v(stored, key, 6), v(stored, key, 4), "<=", p);
            }
        });
    }

    // ------------------------------------------------------------------ place images

    void placeImages() {
        if (!tableExistsQuietly("place_image_count")) return;
        Check check = new Check("place.images", "Images = views + labels; Ant images = views; place table = place_image_count", "27-33, 75, 109")
            .pendingOn(false, true, true, true);
        check(check, c -> {
            // per place: all, ant (stored summary rows) and the sums of the shot rows
            String sql = "select place_type, place_key,"
                + " sum(case when shot_type = '*all' then image_count else 0 end),"
                + " sum(case when shot_type = '*ant' then image_count else 0 end),"
                + " sum(case when shot_type not like '*%' then image_count else 0 end),"
                + " sum(case when shot_type not like '*%' and shot_type <> 'l' then image_count else 0 end),"
                + " sum(case when shot_type = '*all' then specimen_count else 0 end),"
                + " sum(case when shot_type = '*ant' then specimen_count else 0 end),"
                + " sum(case when shot_type = '*label_only' then specimen_count else 0 end)"
                + " from place_image_count group by place_type, place_key";
            Map<String, Map<String, long[]>> byType = new HashMap<>();
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(sql))) {
                while (r.next()) {
                    long[] a = new long[7];
                    for (int i = 0; i < 7; i++) a[i] = r.getLong(i + 3);
                    byType.computeIfAbsent(r.getString(1), x -> keyMap()).put(r.getString(2), a);
                }
            }
            for (String type : PlaceTotals.TYPES) {
                Map<String, String> inScope = places.get(type);
                if (inScope == null) continue;
                Map<String, long[]> placeTable = rows("select " + PlaceTotals.placeKey(type) + ", image_count, imaged_specimen_count from " + PlaceTotals.placeTable(type));
                Map<String, long[]> pic = byType.getOrDefault(type, keyMap());
                for (String key : inScope.keySet()) {
                    long[] a = pic.get(key);
                    if (a == null) continue;   // no images there
                    String p = why(c, type, key);
                    compare(c, type, key, "Images = sum of the views and labels", a[0], a[2], "=", p);
                    compare(c, type, key, "Ant images = sum of the views", a[1], a[3], "=", p);
                    compare(c, type, key, "specimens with images = imaged + label-only", a[4], a[5] + a[6], "=", p);
                    if (placeTable.containsKey(key)) {
                        compare(c, type, key, "Images (place table) = place_image_count", v(placeTable, key, 0), a[0], "=", p);
                        compare(c, type, key, "Imaged specimens (place table) = place_image_count", v(placeTable, key, 1), a[5], "=", p);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ list rows

    /** Status condition (alias t) of the child rows the crawl counts for a list (Geolocale, Project, ... getCountChildrenQuery). */
    static String childStatus(String type, String key) {
        if (!PlaceTotals.PROJECT.equals(type)) return " and t.status in " + COUNTABLE;
        if ("worldants".equals(key)) return " and t.status = 'valid'";
        if ("allantwebants".equals(key)) return " and t.status in " + COUNTABLE;
        return "";
    }

    /**
     * Rows of one level whose stored counts differ from their child rows (parentRank genus: children species and
     * subspecies; subfamily: genera; family: subfamilies). Columns compared: count of children into countColumn,
     * sums of the children's sumColumns into the same columns. Only differing rows come back.
     */
    void listRows(String type) {
        String[][] levels = {
            {"genus", SP, "species_count", "specimen_count"},
            {"subfamily", "('genus')", "genus_count", "species_count,specimen_count"},
            {"family", "('subfamily')", "subfamily_count", "genus_count,species_count,specimen_count"},
        };
        String l = PlaceTotals.listTable(type);
        String lk = PlaceTotals.listKey(type);
        Map<String, String> inScope = places.get(type);
        for (String[] level : levels) {
            String parentRank = level[0];
            String[] sums = level[3].split(",");
            Check check = new Check("list." + parentRank, parentRank + " rows of " + type + " lists = their child rows",
                "genus".equals(parentRank) ? "41, 48, 112, 116" : "subfamily".equals(parentRank) ? "42, 48" : "48")
                .pendingOn(true, true, false, false);
            List<String> filters = new ArrayList<>();
            if (PlaceTotals.PROJECT.equals(type)) {
                filters.add("worldants");
                filters.add("allantwebants");
                filters.add(null);     // every other project
            } else {
                filters.add("");
            }
            check(check, c -> {
                for (String filter : filters) {
                    String keyCondition;
                    String status;
                    if (filter == null) {
                        keyCondition = " and c." + lk + " not in ('worldants', 'allantwebants')";
                        status = "";
                    } else if (filter.isEmpty()) {
                        keyCondition = "";
                        status = childStatus(type, "x");
                    } else {
                        keyCondition = " and c." + lk + " = '" + filter + "'";
                        status = childStatus(type, filter);
                    }
                    StringBuilder sel = new StringBuilder("select p." + lk + ", p.taxon_name, p." + level[2] + ", coalesce(x.n, 0)");
                    // a genus row's Species counts the names identified to genus only (Brian Fisher, 2026-10-22,
                    // PlaceTotals.identifiedToGenus); its Specimens and Images still add up every row under it
                    String n = "genus".equals(parentRank) ? "sum(case when " + PlaceTotals.identifiedToGenus("t") + " then 1 else 0 end)" : "count(*)";
                    StringBuilder sub = new StringBuilder("select c." + lk + " k, t.parent_taxon_name parent, " + n + " n");
                    StringBuilder diff = new StringBuilder("p." + level[2] + " <> coalesce(x.n, 0)");
                    int i = 0;
                    for (String s : sums) {
                        sel.append(", p.").append(s).append(", coalesce(x.s").append(i).append(", 0)");
                        sub.append(", sum(c.").append(s).append(") s").append(i);
                        diff.append(" or p.").append(s).append(" <> coalesce(x.s").append(i).append(", 0)");
                        ++i;
                    }
                    sub.append(" from ").append(l).append(" c join taxon t on t.taxon_name = c.taxon_name where t.taxarank in ").append(level[1])
                       .append(status).append(keyCondition).append(" group by c.").append(lk).append(", t.parent_taxon_name");
                    String parentKey = keyCondition.replace("c." + lk, "p." + lk);
                    String sql = sel + ", p.created_flag from (select pp.*, " + (columnExists(l, "created") ? "(pp.created >= ?)" : "0") + " created_flag from " + l + " pp"
                        + " join taxon pt on pt.taxon_name = pp.taxon_name where pt.taxarank = '" + parentRank + "') p"
                        + " left join (" + sub + ") x on x.k = p." + lk + " and x.parent = p.taxon_name"
                        + " where (" + diff + ")" + parentKey;
                    if (!columnExists(l, "created")) sql = sql.replace("(pp.created >= ?)", "0");
                    // children added after the nightly run (their parent's counts wait for it)
                    Set<String> childAdded = keySet();
                    if (columnExists(l, "created")) {
                        try (PreparedStatement stmt = connection.prepareStatement(track("select c." + lk + ", t.parent_taxon_name from " + l
                                + " c join taxon t on t.taxon_name = c.taxon_name where c.created >= ? and t.taxarank in " + level[1] + keyCondition))) {
                            stmt.setTimestamp(1, result.countedUntil);
                            try (ResultSet r = stmt.executeQuery()) {
                                while (r.next()) childAdded.add(r.getString(1) + "\u0000" + r.getString(2));
                            }
                        }
                    }
                    try (PreparedStatement stmt = connection.prepareStatement(track(sql))) {
                        if (columnExists(l, "created")) stmt.setTimestamp(1, result.countedUntil);
                        try (ResultSet r = stmt.executeQuery()) {
                            while (r.next()) {
                                String key = r.getString(1);
                                if (!inScope.containsKey(key)) continue;
                                String taxon = r.getString(2);
                                String p = why(c, type, key);
                                if (p == null && (r.getInt(r.getMetaData().getColumnCount()) == 1 || childAdded.contains(key + "\u0000" + taxon))) {
                                    p = "a row was added to this list after the last nightly run";
                                }
                                compare(c, type, key, taxon + ": " + level[2] + " = its child rows", r.getLong(3), r.getLong(4), "=", p);
                                for (int j = 0; j < sums.length; j++) {
                                    --c.compared;   // one row, counted once
                                    compare(c, type, key, taxon + ": " + sums[j] + " = sum of its child rows", r.getLong(5 + 2 * j), r.getLong(6 + 2 * j), "=", p);
                                }
                            }
                        }
                    }
                }
                // rows compared (not only the differing ones): one per parent row in scope
                try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track("select count(*) from " + l
                        + " p join taxon pt on pt.taxon_name = p.taxon_name where pt.taxarank = '" + parentRank + "'"))) {
                    if (r.next()) c.compared = Math.max(c.compared, r.getInt(1));
                }
            });
        }
    }

    // ------------------------------------------------------------------ global

    /** A species row's specimens in a place cannot exceed its Global specimens (allantwebants). */
    void globalPerPlace() {
        Check check = new Check("list.global", "specimens of a species in a place <= its Global specimens", "40, 43")
            .pendingOn(false, true, true, false);
        check(check, c -> {
            for (String type : PlaceTotals.TYPES) {
                Map<String, String> inScope = places.get(type);
                if (inScope == null) continue;
                String l = PlaceTotals.listTable(type);
                String lk = PlaceTotals.listKey(type);
                // Museums, bioregions, projects (2026-10-22): a row counts the specimens of the name's synonyms too
                // (PlaceTotals.specimensByListNameSql), so its bound is the specimen records of the name and of its synonyms.
                boolean syn = !PlaceTotals.GEOLOCALE.equals(type);
                String global = syn
                    ? " join (select x.list_name, sum(g.specimen_count) specimen_count from (select taxon_name src, taxon_name list_name from taxon"
                        + " union all select t2.taxon_name, t2.current_valid_name from taxon t2 where " + PlaceTotals.renamedCondition("t2") + ") x"
                        // the specimen records of each name: a synonym has no Global row (the Global column lists counted
                        // statuses only), so its specimens were missing from the bound (2026-10-27, finding B66: e.g.
                        // Diacamma solitaria 5 specimens in introducedants against a bound of 1)
                        + " join (select taxon_name, count(*) specimen_count from specimen group by taxon_name) g on g.taxon_name = x.src"
                        + " group by x.list_name) a on a.list_name = l.taxon_name"
                    : " join proj_taxon a on a.project_name = 'allantwebants' and a.taxon_name = l.taxon_name";
                String sql = "select l." + lk + ", l.taxon_name, l.specimen_count, a.specimen_count from " + l + " l"
                    + " join taxon t on t.taxon_name = l.taxon_name"
                    + global
                    + " where t.taxarank in " + SP + " and l.specimen_count > a.specimen_count"
                    + (PlaceTotals.PROJECT.equals(type) ? " and l.project_name <> 'allantwebants'" : "");
                try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(sql))) {
                    while (r.next()) {
                        String key = r.getString(1);
                        if (!inScope.containsKey(key)) continue;
                        String name = r.getString(2);
                        String p = why(c, type, key);
                        if (p == null && changedNames.contains(name)) p = "specimens of this name uploaded or renamed after the last nightly run";
                        compare(c, type, key, name + (syn ? ": specimens here <= Global (with its synonyms)" : ": specimens here <= Global"),
                            r.getLong(3), r.getLong(4), "<=", p);
                    }
                }
            }
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(
                    "select count(*) from proj_taxon a join taxon t on t.taxon_name = a.taxon_name where a.project_name = 'allantwebants' and t.taxarank in " + SP))) {
                if (r.next()) c.compared += r.getInt(1);
            }
        });
    }

    /** The Global specimens of every species = all its specimen records (any status). */
    void globalSpecimens() {
        Check check = new Check("global.specimens", "Global specimens of every species and subspecies = its specimen records", "43")
            .pendingOn(false, false, false, false);
        check(check, c -> {
            Map<String, long[]> counted = rows("select taxon_name, count(*) from specimen group by taxon_name");
            String sql = "select a.taxon_name, a.specimen_count, " + (columnExists("proj_taxon", "created") ? safeDate("a.created") : "null")
                + " from proj_taxon a join taxon t on t.taxon_name = a.taxon_name where a.project_name = 'allantwebants' and t.taxarank in " + SP;
            CountCheck.Place site = place("project", "allantwebants", "Global (all AntWeb)");
            places.computeIfAbsent("project", x -> keyMap()).putIfAbsent("allantwebants", "Global (all AntWeb)");
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(sql))) {
                while (r.next()) {
                    String name = r.getString(1);
                    long stored = r.getLong(2);
                    long recount = v(counted, name, 0);
                    java.sql.Timestamp created = r.getTimestamp(3);
                    String p = null;
                    if (created != null && !created.before(result.countedUntil)) p = "list row added after the last nightly run";
                    if (p == null && changedNames.contains(name)) p = "specimens of this name uploaded or renamed after the last nightly run";
                    if (p == null && stored > recount && result.uploadSinceNightly) p = "an upload after the last nightly run may have removed specimens";
                    compare(c, "project", "allantwebants", name + ": Global specimens", stored, recount, "=", p);
                }
            }
        });
    }

    // ------------------------------------------------------------------ home page, statsPage

    void home() {
        if (!tableExistsQuietly("statistics")) return;
        Check check = new Check("home", "home page and statsPage numbers = a recount", "1-10")
            .pendingOn(false, false, true, true);
        check(check, c -> {
            long[] row = null;
            java.sql.Timestamp created = null;
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(
                    "select extant_taxa, specimens, species_imaged, specimens_imaged, total_images, " + safeDate("created")
                    + " from statistics order by created desc, id desc limit 1"))) {
                if (r.next()) {
                    row = new long[] {r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getLong(5)};
                    created = r.getTimestamp(6);
                }
            }
            if (row == null) return;
            boolean nightlyRow;
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track("select count(*) from statistics where action = 'nightly'"))) {
                nightlyRow = r.next() && r.getInt(1) > 0;
            }
            // Changes after the statistics row was written (an upload rewrites it; images do not).
            boolean imagesSince = false;
            boolean uploadSince = false;
            if (created != null && tableExistsQuietly("image")) {
                try (PreparedStatement stmt = connection.prepareStatement(track("select count(*) from image where upload_date > ?"))) {
                    stmt.setTimestamp(1, created);
                    try (ResultSet r = stmt.executeQuery()) { imagesSince = r.next() && r.getInt(1) > 0; }
                }
            }
            if (created != null && tableExistsQuietly("upload")) {
                try (PreparedStatement stmt = connection.prepareStatement(track("select count(*) from upload where created > ?"))) {
                    stmt.setTimestamp(1, created);
                    try (ResultSet r = stmt.executeQuery()) { uploadSince = r.next() && r.getInt(1) > 0; }
                }
            }
            String[] what = {"valid species + ssp.", "specimen records", "species + ssp. imaged", "specimens imaged", "total specimen images"};
            String[] sql = {
                org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_SQL,
                "select count(*) from specimen",
                org.calacademy.antweb.home.StatisticsDb.IMAGED_SPECIES_SQL,
                org.calacademy.antweb.home.StatisticsDb.IMAGED_SPECIMENS_SQL,
                org.calacademy.antweb.home.StatisticsDb.TOTAL_IMAGES_SQL};   // images of the ant since 2026-10-27
            places.computeIfAbsent("site", x -> keyMap()).put("home", "home page and statsPage");
            for (int i = 0; i < what.length; i++) {
                long live;
                try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(sql[i]))) {
                    live = r.next() ? r.getLong(1) : 0;
                }
                String p = null;
                if (!nightlyRow && (i == 2 || i == 3)) p = RULES_PENDING;
                // Q7 (2026-10-22): ants only. A row written before that rule equals the old count: pending until the next nightly run.
                String before = i == 0 ? org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_SQL_BEFORE_Q7
                    : i == 2 ? org.calacademy.antweb.home.StatisticsDb.IMAGED_SPECIES_SQL_BEFORE_Q7
                    : i == 4 ? org.calacademy.antweb.home.StatisticsDb.TOTAL_IMAGES_SQL_BEFORE : null;
                if (p == null && before != null && row[i] != live) {
                    try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(before))) {
                        if (r.next() && r.getLong(1) == row[i]) p = i == 4 ? ANT_IMAGES_PENDING : Q7_PENDING;
                    }
                }
                if (p == null && uploadSince) p = "specimens were uploaded after the numbers were written";
                if (p == null && imagesSince && i >= 2) p = "images were added after the numbers were written";
                compare(c, "site", "home", what[i], row[i], live, "=", p);
                if (i == 0 && statsPageValidSpecies != null) {
                    // statsPage's first number comes from the taxa in memory (TaxonMgr, reloaded by nightly set 1).
                    compare(c, "site", "home", "statsPage valid species + ssp. (taxa in memory)", statsPageValidSpecies, live, "=", null);
                }
            }
        });
    }

    // ------------------------------------------------------------------ taxon and specimen image counts

    void taxonImages() {
        Check check = new Check("taxon.images", "taxon image_count = images of its specimens and of the taxa under it; specimen image_count = its images",
            "54, 56, 61, 63, 100, 104").pendingOn(true, false, true, true);
        check(check, c -> {
            places.computeIfAbsent("site", x -> keyMap()).put("taxa", "taxon table");
            // Specimens with images: their image_count and name (one pass over specimen); images per specimen.
            Map<String, Long> own = keyMap();
            Map<String, Long> specimenCount = keyMap();
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(
                    "select code, taxon_name, image_count from specimen where image_count <> 0"))) {
                while (r.next()) {
                    specimenCount.put(r.getString(1), r.getLong(3));
                    if (r.getString(2) != null) own.merge(r.getString(2), r.getLong(3), Long::sum);
                }
            }
            Map<String, Long> children = keyMap();
            String in = "('" + String.join("', '", org.calacademy.antweb.home.ImageCountDb.ROLL_UP_RANKS) + "')";
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track(
                    "select parent_taxon_name, sum(image_count) from taxon where taxarank in " + in + " and parent_taxon_name is not null group by parent_taxon_name"))) {
                while (r.next()) children.put(r.getString(1), r.getLong(2));
            }
            String p = why(c, "site", null);
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track("select taxon_name, coalesce(image_count, 0) from taxon"))) {
                while (r.next()) {
                    String name = r.getString(1);
                    long expected = own.getOrDefault(name, 0L) + children.getOrDefault(name, 0L);
                    compare(c, "site", "taxa", name + ": image_count", r.getLong(2), expected, "=", p);
                }
            }
            // specimens: image_count = its rows in image
            places.get("site").put("specimens", "specimen table");
            Check imagesOnly = new Check("x", "", "").pendingOn(false, false, true, true);
            String sp = why(imagesOnly, "site", null);
            Map<String, Long> images = keyMap();
            try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(track("select image_of_id, count(*) from image group by image_of_id"))) {
                while (r.next()) images.put(r.getString(1), r.getLong(2));
            }
            List<String> zero = new ArrayList<>();   // images, but image_count 0 or not a specimen (orphan images)
            for (Map.Entry<String, Long> e : images.entrySet()) {
                Long stored = specimenCount.get(e.getKey());
                if (stored == null) zero.add(e.getKey());
                else compare(c, "site", "specimens", e.getKey() + ": image_count", stored, e.getValue(), "=", sp);
            }
            for (Map.Entry<String, Long> e : specimenCount.entrySet()) {
                if (!images.containsKey(e.getKey())) compare(c, "site", "specimens", e.getKey() + ": image_count", e.getValue(), 0, "=", sp);
            }
            for (int from = 0; from < zero.size(); from += 1000) {
                List<String> part = zero.subList(from, Math.min(zero.size(), from + 1000));
                StringBuilder q = new StringBuilder("select code from specimen where code in (");
                for (int i = 0; i < part.size(); i++) q.append(i == 0 ? "?" : ", ?");
                try (PreparedStatement stmt = connection.prepareStatement(track(q.append(")").toString()))) {
                    for (int i = 0; i < part.size(); i++) stmt.setString(i + 1, part.get(i));
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) {
                            Long n = images.get(r.getString(1));
                            if (n != null) compare(c, "site", "specimens", r.getString(1) + ": image_count", 0, n, "=", sp);
                        }
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ geography

    /** A subregion's or region's Specimens are those of its countries, so at most their sum. */
    void regions() {
        if (!places.containsKey(PlaceTotals.GEOLOCALE)) return;
        Check check = new Check("geo.regions", "Specimens of a subregion or region <= sum of its countries' Specimens", "26, 77")
            .pendingOn(false, true, false, false);
        check(check, c -> {
            Map<String, long[]> sub = rows("select s.id, s.specimen_count, coalesce(sum(c.specimen_count), 0) from geolocale s"
                + " left join geolocale c on c.parent = s.name and c.georank = 'country' and c.is_valid = 1"
                + " where s.georank = 'subregion' group by s.id, s.specimen_count");
            Map<String, long[]> reg = rows("select r.id, r.specimen_count, coalesce(sum(c.specimen_count), 0) from geolocale r"
                + " left join geolocale s on s.parent = r.name and s.georank = 'subregion'"
                + " left join geolocale c on c.parent = s.name and c.georank = 'country' and c.is_valid = 1"
                + " where r.georank = 'region' group by r.id, r.specimen_count");
            Map<String, String> inScope = places.get(PlaceTotals.GEOLOCALE);
            for (Map<String, long[]> m : java.util.Arrays.asList(sub, reg)) {
                for (Map.Entry<String, long[]> e : m.entrySet()) {
                    if (!inScope.containsKey(e.getKey())) continue;
                    compare(c, PlaceTotals.GEOLOCALE, e.getKey(), "Specimens <= sum of its countries", e.getValue()[0], e.getValue()[1], "<=",
                        why(c, PlaceTotals.GEOLOCALE, e.getKey()));
                }
            }
        });
    }

    // ------------------------------------------------------------------ schema

    private final Map<String, Boolean> columnCache = new HashMap<>();

    boolean columnExists(String table, String column) throws SQLException {
        String k = table + "." + column;
        Boolean b = columnCache.get(k);
        if (b != null) return b;
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?")) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet r = stmt.executeQuery()) { b = r.next(); }
        }
        columnCache.put(k, b);
        return b;
    }

    boolean tableExists(String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            stmt.setString(1, table);
            try (ResultSet r = stmt.executeQuery()) { return r.next(); }
        }
    }

    private boolean tableExistsQuietly(String table) {
        try { return tableExists(table); } catch (SQLException e) { return false; }
    }

    /** Checks, real and pending differences per check id (for the page and the tests). */
    public Map<String, int[]> summary() {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (Check c : checks) {
            int[] a = m.computeIfAbsent(c.id, x -> new int[3]);
            a[0] += c.compared;
            a[1] += c.real;
            a[2] += c.pending;
        }
        return m;
    }
}

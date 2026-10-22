package org.calacademy.antweb.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.calacademy.antweb.home.PlaceTotals;

/**
 * The count check's checks of the numbers that are not place totals (2026-10-22, docs/numbers.md "every number
 * checked"): statsPage tables, the admin pages of curators, upload groups, artists, image uploads and statistics,
 * the API's taxon counts, the endemic flags, and the two suspects B25 (taxon parents) and B26 (adm1 by name).
 * Each check runs over all rows at once (one or two grouped queries), read-only, like CountInvariants, and adds
 * the numbers the end-to-end tests compare with the pages (CountInvariants.expected, printed in the report as
 * JSON with id count-check-expected).
 *
 * Kinds of comparison:
 *   - the value a page shows, computed by the page's own code (StatisticsDb, ImageDb) or read from the in-memory
 *     managers the page reads (LoginMgr, ArtistMgr; given by CountCheckJob as curatorNumbers, artistNumbers),
 *     against an independent recount from the records;
 *   - stored numbers (upload rows, statistics rows, image_upload.image_count) against a recount by the rule that
 *     wrote them;
 *   - relations that must hold (a part is at most the whole; numbers on two pages that count the same thing).
 *
 * None of these numbers changed rule on 2026-10-21, so no difference is pending for that reason; a difference is
 * pending only when something changed after the number was written (an upload, a list edit, an image upload).
 */
public final class NumberChecks {

    private final CountInvariants inv;

    NumberChecks(CountInvariants inv) {
        this.inv = inv;
    }

    void run() {
        statsGlobal();
        statsImages();
        statsImagedTaxa();
        statsPlaces("bioregion");
        statsPlaces("museum");
        listImages();
        adminStatistics();
        adminGroups();
        adminCurators();
        adminArtists();
        adminImageUploads();
        apiTaxa();
        endemicFlags();
        taxonParents();
        adm1ByCountry();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, String> site() {
        return inv.places.computeIfAbsent("site", x -> CountInvariants.keyMap());
    }

    private long count(String sql, Object... params) throws SQLException {
        try (PreparedStatement stmt = inv.connection.prepareStatement(inv.track(sql))) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    private final Map<String, Boolean> tables = new java.util.HashMap<>();

    /** Whether the table exists (cached: information_schema is slow). */
    private boolean has(String table) {
        return tables.computeIfAbsent(table, t -> {
            try { return inv.tableExists(t); } catch (SQLException e) { return false; }
        });
    }

    private boolean has(String table, String column) {
        try { return has(table) && inv.columnExists(table, column); } catch (SQLException e) { return false; }
    }

    private static long[] add(long[] a, long[] b) {
        long[] c = a.clone();
        for (int i = 0; i < c.length; i++) c[i] += b[i];
        return c;
    }

    /** Whether rows were added to the table after a moment (column created), or false when it has no such column. */
    private boolean createdSince(String table, Timestamp since) throws SQLException {
        if (since == null || !has(table, "created")) return false;
        return count("select count(*) from " + table + " where created > ?", since) > 0;
    }

    private Map<String, Object> expected(String page) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) inv.expected.computeIfAbsent(page, x -> new LinkedHashMap<String, Object>());
        return m;
    }

    static final String[] RANKS = {"subfamily", "genus", "species", "subspecies"};

    // ------------------------------------------------------------------ statsPage

    /**
     * statsPage Global Data (rows 11, 12): valid extant and valid fossil subfamilies, genera, species, subspecies, as
     * the page computes them (StatisticsDb.getExtantData, getFossilData), against a grouped recount of the taxon
     * table; and extant + fossil species and subspecies = the home page's "valid species + ssp." (a taxon whose
     * fossil flag is neither 0 nor 1 is in neither row).
     */
    void statsGlobal() {
        CountInvariants.Check check = new CountInvariants.Check("stats.global",
            "statsPage Global Data (Extant, Fossil) = a recount of the taxon table; extant + fossil species and subspecies = home valid species + ssp.", "11, 12");
        inv.check(check, c -> {
            site().put("statsPage", "statsPage.do");
            org.calacademy.antweb.home.StatisticsDb db = new org.calacademy.antweb.home.StatisticsDb(inv.connection);
            StatSet extant = db.getExtantData();
            StatSet fossil = db.getFossilData();
            long[][] recount = new long[2][4];
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select fossil, taxarank, count(*) from taxon where status = 'valid' and fossil in (0, 1)"
                    + " and taxarank in ('subfamily', 'genus', 'species', 'subspecies') group by fossil, taxarank"))) {
                while (r.next()) {
                    int row = r.getInt(1) == 1 ? 1 : 0;
                    int col = java.util.Arrays.asList(RANKS).indexOf(r.getString(2));
                    if (col >= 0) recount[row][col] += r.getLong(3);
                }
            }
            String[] what = {"Valid Subfamilies", "Valid Genera", "Valid Species", "Valid Subspecies"};
            for (int i = 0; i < 4; i++) {
                inv.compare(c, "site", "statsPage", "Global Data, Extant: " + what[i], extant.getValue(i + 1), recount[0][i], "=", null);
                inv.compare(c, "site", "statsPage", "Global Data, Fossil: " + what[i], fossil.getValue(i + 1), recount[1][i], "=", null);
            }
            long home = count(org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_SQL);
            inv.compare(c, "site", "statsPage", "Global Data: extant + fossil species and subspecies = valid species + ssp. (home; taxa whose fossil flag is not 0 or 1 are in neither row)",
                recount[0][2] + recount[0][3] + recount[1][2] + recount[1][3], home, "=", null);
            expected("statsPage").put("extant", recount[0]);
            expected("statsPage").put("fossil", recount[1]);
        });
    }

    /**
     * statsPage Images table (rows 13 to 15): Total, Worker, Male, Queen, Other per specimen status as the page
     * computes them (ImageDb.getImageStats) against a recount grouped by status and caste; Worker + Male + Queen +
     * Other = Total; the Sum row (every status the table shows) = the images whose specimen is in AntWeb = all images
     * minus the orphan images (images of a code that is no specimen; row 99 counts their codes).
     */
    void statsImages() {
        CountInvariants.Check check = new CountInvariants.Check("stats.images",
            "statsPage Images table = a recount by status and caste; the four castes add up to Total; Sum = all images minus orphan images", "13, 14, 15, 99");
        inv.check(check, c -> {
            site().put("statsPage", "statsPage.do");
            Map<String, int[]> page = new org.calacademy.antweb.home.ImageDb(inv.connection).getImageStats();
            Map<String, long[]> recount = new TreeMap<>();
            long nullStatus = 0;
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select s.status, s.caste, count(*) from image i join specimen s on s.code = i.image_of_id group by s.status, s.caste"))) {
                while (r.next()) {
                    String status = r.getString(1);
                    String caste = r.getString(2);
                    long n = r.getLong(3);
                    if (status == null) { nullStatus += n; continue; }
                    long[] a = recount.computeIfAbsent(status.toLowerCase(java.util.Locale.ROOT), x -> new long[5]);
                    a[0] += n;
                    String k = caste == null ? "" : caste.toLowerCase(java.util.Locale.ROOT);
                    a["worker".equals(k) ? 1 : "male".equals(k) ? 2 : "queen".equals(k) ? 3 : 4] += n;
                }
            }
            String[] cols = {"Total", "Worker", "Male", "Queen", "Other"};
            long[] sum = new long[5];
            for (Map.Entry<String, long[]> e : recount.entrySet()) {
                int[] shown = null;
                for (Map.Entry<String, int[]> p : page.entrySet()) if (p.getKey() != null && p.getKey().equalsIgnoreCase(e.getKey())) shown = p.getValue();
                long[] a = e.getValue();
                for (int i = 0; i < 5; i++) {
                    inv.compare(c, "site", "statsPage", "Images table, " + e.getKey() + ": " + cols[i], shown == null ? 0 : shown[i], a[i], "=", null);
                }
                if (shown != null) {
                    inv.compare(c, "site", "statsPage", "Images table, " + e.getKey() + ": Worker + Male + Queen + Other = Total",
                        (long) shown[1] + shown[2] + shown[3] + shown[4], shown[0], "=", null);
                }
                sum = add(sum, a);
            }
            // a status the page shows that the recount does not have
            for (Map.Entry<String, int[]> p : page.entrySet()) {
                if (p.getKey() == null || recount.containsKey(p.getKey().toLowerCase(java.util.Locale.ROOT))) continue;
                inv.compare(c, "site", "statsPage", "Images table, " + p.getKey() + ": Total", p.getValue()[0], 0, "=", null);
            }
            long all = count("select count(*) from image");
            long orphans = 0;
            long orphanCodes = 0;
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select count(*), count(distinct i.image_of_id) from image i where not exists (select 1 from specimen s where s.code = i.image_of_id)"))) {
                if (r.next()) {
                    orphans = r.getLong(1);
                    orphanCodes = r.getLong(2);
                }
            }
            inv.compare(c, "site", "statsPage", "Images table: Sum (statuses shown) + images of specimens without a status = all images (" + all
                + ") minus orphan images (" + orphans + ")", sum[0] + nullStatus, all - orphans, "=", null);
            // Images of specimens without a status are in no row of the table (so not in its Sum): a data finding.
            inv.compare(c, "site", "statsPage", "Images table: images of specimens without a status (in no row, not in the Sum)", nullStatus, 0, "=", null);
            Map<String, Object> images = new LinkedHashMap<>();
            for (Map.Entry<String, long[]> e : recount.entrySet()) images.put(e.getKey(), e.getValue());
            expected("statsPage").put("images", images);
            expected("statsPage").put("imagesSum", sum);
            expected("orphanedImages").put("codes", orphanCodes);
            expected("orphanedImages").put("images", orphans);
        });
    }

    /** statsPage "Imaged Species" per taxon status (row 16) as the page computes it (ImageDb.getImageTaxonStats). */
    void statsImagedTaxa() {
        CountInvariants.Check check = new CountInvariants.Check("stats.imaged",
            "statsPage Imaged Species per status = a recount; counted statuses >= home species + ssp. imaged", "8, 16");
        inv.check(check, c -> {
            site().put("statsPage", "statsPage.do");
            Map<String, Integer> page = new org.calacademy.antweb.home.ImageDb(inv.connection).getImageTaxonStats();
            Map<String, Long> recount = new TreeMap<>();
            // names with at least one specimen that has an image (labels too, any rank), by taxon status
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select t.status, count(*) from taxon t where t.taxon_name in (select s.taxon_name from specimen s"
                    + " where s.code in (select i.image_of_id from image i)) group by t.status"))) {
                while (r.next()) if (r.getString(1) != null) recount.merge(r.getString(1).toLowerCase(java.util.Locale.ROOT), r.getLong(2), Long::sum);
            }
            long countable = 0;
            for (Map.Entry<String, Long> e : recount.entrySet()) {
                Integer shown = null;
                for (Map.Entry<String, Integer> p : page.entrySet()) if (p.getKey() != null && p.getKey().equalsIgnoreCase(e.getKey())) shown = p.getValue();
                inv.compare(c, "site", "statsPage", "Imaged Species, " + e.getKey(), shown == null ? 0 : shown, e.getValue(), "=", null);
                if (org.calacademy.antweb.StatusSet.isAllAntwebAnts(e.getKey())) countable += e.getValue();
            }
            // the home number as shown (the statistics row; check "home" recounts it)
            if (has("statistics", "species_imaged")) {
                long homeImaged = count("select species_imaged from statistics order by created desc, id desc limit 1");
                inv.compare(c, "site", "statsPage", "Imaged Species of counted statuses (any rank, labels too) >= species + ssp. imaged (home)",
                    countable, homeImaged, ">=", inv.result.imagesSinceNightly || inv.result.uploadSinceNightly ? "images or specimens were added after the last nightly run" : null);
            }
            expected("statsPage").put("imagedSpecies", recount);
        });
    }

    /**
     * statsPage Bioregion Data (row 17) and Museum Data, Valid Extant and Valid Fossil (rows 18, 19): valid subfamilies,
     * genera, species, subspecies on each list (the page's queries, StatisticsDb.getBioregionData, getExtantMuseumData,
     * getFossilMuseumData, grouped for all places at once; test: NumberChecksTest compares the two). They must agree
     * with the place's own page: extant species + subspecies = its Valid species (row 23); subfamilies and genera
     * <= its Subfamilies and Genera; valid extant + valid fossil species and subspecies <= its Species; and each
     * <= the Global Data numbers of all AntWeb (rows 11, 12).
     */
    void statsPlaces(String type) {
        boolean museum = "museum".equals(type);
        String list = museum ? "museum_taxon" : "bioregion_taxon";
        String key = museum ? "code" : "bioregion_name";
        if (!has(list) || !has(type)) return;
        CountInvariants.Check check = new CountInvariants.Check("stats." + type + "s", museum
            ? "statsPage Museum Data (Valid Extant, Valid Fossil) agree with each museum's page and Global Data"
            : "statsPage Bioregion Data agrees with each bioregion's page and Global Data", museum ? "18, 19" : "17")
            .pendingOn(true, true, false, false);   // the place pages' numbers follow the rules of 2026-10-21
        Map<String, long[][]> data = CountInvariants.keyMap();   // every family, as the page counts
        Map<String, long[][]> ants = CountInvariants.keyMap();   // Formicidae only, as the place pages count
        String page = museum ? "statsPage Museum Data" : "statsPage Bioregion Data";
        inv.check(check, c -> {
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(statsPlacesSql(type)))) {
                while (r.next()) {
                    int col = java.util.Arrays.asList(RANKS).indexOf(r.getString(3));
                    if (col < 0) continue;
                    int f = r.getInt(2) == 1 ? 1 : 0;
                    data.computeIfAbsent(r.getString(1), x -> new long[2][4])[f][col] += r.getLong(4);
                    ants.computeIfAbsent(r.getString(1), x -> new long[2][4])[f][col] += r.getLong(5);
                }
            }
            long[][] global = new long[2][4];
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select fossil, taxarank, count(*) from taxon where status = 'valid' and fossil in (0, 1) group by fossil, taxarank"))) {
                while (r.next()) {
                    int col = java.util.Arrays.asList(RANKS).indexOf(r.getString(2));
                    if (col >= 0) global[r.getInt(1) == 1 ? 1 : 0][col] += r.getLong(3);
                }
            }
            String t = museum ? "museum" : "bioregion";
            String k = museum ? "code" : "name";
            Map<String, long[]> stored = inv.rows("select " + k + ", subfamily_count, genus_count, species_count, valid_species_count from " + t);
            Map<String, Object> exp = new TreeMap<>();
            Map<String, String> labels = inv.places.getOrDefault(type, CountInvariants.keyMap());
            String[] what = {"Subfamilies", "Genera", "Species", "Subspecies"};
            for (Map.Entry<String, long[]> e : stored.entrySet()) {
                String place = e.getKey();
                long[] s = e.getValue();
                long[][] d = data.getOrDefault(place, new long[2][4]);
                long[][] a = ants.getOrDefault(place, new long[2][4]);
                exp.put(place, museum ? d : d[0]);
                if (!labels.containsKey(place)) continue;
                String p = inv.why(c, type, place);
                // Against the place page with the ants of the statsPage numbers (the page counts every family: stats.nonants, B31).
                inv.compare(c, type, place, page + (museum ? ", Valid Extant" : "") + ": Species + Subspecies (ants) = Valid species of the " + type + " page",
                    a[0][2] + a[0][3], s[3], "=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil Subfamilies (ants) <= Subfamilies of the " + type + " page", a[0][0] + a[1][0], s[0], "<=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil Genera (ants) <= Genera of the " + type + " page", a[0][1] + a[1][1], s[1], "<=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil species and subspecies (ants) <= Species of the " + type + " page",
                    a[0][2] + a[0][3] + a[1][2] + a[1][3], s[2], "<=", p);
                for (int f = 0; f < (museum ? 2 : 1); f++) {
                    for (int i = 0; i < 4; i++) {
                        inv.compare(c, type, place, page + (f == 0 ? (museum ? ", Valid Extant " : " ") : ", Valid Fossil ") + what[i]
                            + " <= Global Data " + (f == 0 ? "Extant" : "Fossil"), d[f][i], global[f][i], "<=", null);
                    }
                }
            }
            expected("statsPage").put(museum ? "museums" : "bioregions", exp);
        });
        // B31: the statsPage numbers count valid taxa of every family on the list; the place pages count ants only.
        CountInvariants.Check nonAnts = new CountInvariants.Check("stats.nonants",
            "B31: statsPage Bioregion and Museum Data count valid taxa of other families (the place pages count ants only)", museum ? "18, 19" : "17");
        if (check.error != null) return;   // the numbers were not read completely
        inv.check(nonAnts, c -> {
            Map<String, String> labels = inv.places.getOrDefault(type, CountInvariants.keyMap());
            for (Map.Entry<String, long[][]> e : data.entrySet()) {
                if (!labels.containsKey(e.getKey())) continue;
                long[][] d = e.getValue();
                long[][] a = ants.getOrDefault(e.getKey(), new long[2][4]);
                for (int f = 0; f < (museum ? 2 : 1); f++) {
                    long all = d[f][0] + d[f][1] + d[f][2] + d[f][3];
                    long antsOnly = a[f][0] + a[f][1] + a[f][2] + a[f][3];
                    inv.compare(c, type, e.getKey(), page + (f == 1 ? ", Valid Fossil" : museum ? ", Valid Extant" : "")
                        + ": valid taxa counted = valid ant taxa (B31)", all, antsOnly, "=", null);
                }
            }
        });
    }

    /**
     * The statsPage queries of Bioregion Data and Museum Data (StatisticsDb), for every place at once: key, fossil, rank,
     * count (as the page counts: every family), and the ants among them.
     */
    public static String statsPlacesSql(String type) {
        boolean museum = "museum".equals(type);
        String list = museum ? "museum_taxon" : "bioregion_taxon";
        String key = museum ? "code" : "bioregion_name";
        return "select l." + key + ", t.fossil, t.taxarank, count(*), sum(case when t.family = 'formicidae' then 1 else 0 end) from taxon t join " + list + " l on t.taxon_name = l.taxon_name"
            + " where t.status = 'valid' and t.fossil in (" + (museum ? "0, 1" : "0") + ") and t.taxarank in ('subfamily', 'genus', 'species', 'subspecies')"
            + " group by l." + key + ", t.fossil, t.taxarank";
    }

    // ------------------------------------------------------------------ list rows: Images column

    /**
     * The Images column of every list (rows 44, 48, 56, 61, 112, 116): a genus row's images are at least those of its
     * species and subspecies rows, a subfamily row's at least those of its genus rows, the Formicidae row's at least
     * those of its subfamily rows (nightly set 4 rolls the images of the specimens up by their genus and subfamily,
     * CountDb.imageCountCrawl; a specimen named only to genus adds to its genus row, so "at least"). The species
     * rows themselves are recounted for the rotating sample (CountCheck, check "images").
     */
    void listImages() {
        CountInvariants.Check check = new CountInvariants.Check("list.images",
            "Images column of every list: a genus, subfamily or family row has at least the images of the rows under it", "44, 48, 56, 61, 112, 116")
            .pendingOn(true, true, false, true);
        String[][] levels = {{"genus", "('species', 'subspecies')"}, {"subfamily", "('genus')"}, {"family", "('subfamily')"}};
        inv.check(check, c -> {
            for (String type : PlaceTotals.TYPES) {
                Map<String, String> inScope = inv.places.get(type);
                if (inScope == null || !has(PlaceTotals.listTable(type), "image_count")) continue;
                String l = PlaceTotals.listTable(type);
                String lk = PlaceTotals.listKey(type);
                for (String[] level : levels) {
                    String sql = "select p." + lk + ", p.taxon_name, coalesce(p.image_count, 0), x.s from " + l + " p"
                        + " join taxon pt on pt.taxon_name = p.taxon_name and pt.taxarank = '" + level[0] + "'"
                        + " join (select c." + lk + " k, t.parent_taxon_name parent, sum(coalesce(c.image_count, 0)) s from " + l + " c"
                        + "   join taxon t on t.taxon_name = c.taxon_name where t.taxarank in " + level[1] + " group by c." + lk + ", t.parent_taxon_name) x"
                        + " on x.k = p." + lk + " and x.parent = p.taxon_name where coalesce(p.image_count, 0) < x.s";
                    try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql))) {
                        while (r.next()) {
                            String key = r.getString(1);
                            if (!inScope.containsKey(key)) continue;
                            --c.compared;   // counted below, with the rows that agree
                            inv.compare(c, type, key, r.getString(2) + ": Images >= the images of the rows under it", r.getLong(3), r.getLong(4), ">=",
                                inv.why(c, type, key));
                        }
                    }
                    // rows compared: the parent rows of the places in scope
                    for (Map.Entry<String, long[]> e : inv.rows("select p." + lk + ", count(*) from " + l + " p join taxon pt on pt.taxon_name = p.taxon_name"
                            + " and pt.taxarank = '" + level[0] + "' group by p." + lk).entrySet()) {
                        if (inScope.containsKey(e.getKey())) c.compared += (int) e.getValue()[0];
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ admin pages

    /**
     * Admin statistics.do (rows 97, 98): the latest statistics row's list and taxon counts (the home and statsPage
     * numbers are checked by "home") against a recount by the queries that wrote it (StatisticsDb.populateStatistics);
     * and the "by upload" breakdown (specimens per upload group, live) against each group's stored upload_specimens.
     */
    void adminStatistics() {
        if (!has("statistics")) return;
        CountInvariants.Check check = new CountInvariants.Check("admin.statistics",
            "statistics.do: latest row = a recount by the queries that wrote it; specimens per upload group = ant_group.upload_specimens", "97, 98");
        inv.check(check, c -> {
            site().put("statistics", "statistics.do (latest row)");
            String[] cols = {"total_taxa", "proj_taxa", "bioregion_taxa", "museum_taxa", "geolocale_taxa", "geolocale_taxa_introduced",
                "geolocale_taxa_endemic", "valid_species_imaged"};
            String[] sql = {"select count(*) from taxon where taxarank in ('species', 'subspecies')", "select count(*) from proj_taxon",
                "select count(*) from bioregion_taxon", "select count(*) from museum_taxon", "select count(*) from geolocale_taxon",
                "select count(*) from geolocale_taxon where is_introduced = 1", "select count(*) from geolocale_taxon where is_endemic = 1",
                org.calacademy.antweb.home.StatisticsDb.VALID_SPECIES_IMAGED_SQL};
            String[] from = {"taxon", "proj_taxon", "bioregion_taxon", "museum_taxon", "geolocale_taxon", "geolocale_taxon", "geolocale_taxon", "image"};
            long[] row = null;
            Timestamp created = null;
            StringBuilder select = new StringBuilder("select ");
            for (String col : cols) select.append(has("statistics", col) ? col : "0").append(", ");
            select.append(CountInvariants.safeDate("created")).append(" from statistics order by created desc, id desc limit 1");
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(select.toString()))) {
                if (r.next()) {
                    row = new long[cols.length];
                    for (int i = 0; i < cols.length; i++) row[i] = r.getLong(i + 1);
                    created = r.getTimestamp(cols.length + 1);
                }
            }
            if (row != null) {
                // rows added after the row was written, or any upload after the last nightly run (an upload recomputes
                // endemic flags and lists in place, without new created dates)
                boolean listsChanged = createdSince("proj_taxon", created) || createdSince("geolocale_taxon", created)
                    || createdSince("museum_taxon", created) || createdSince("bioregion_taxon", created) || createdSince("upload", created)
                    || inv.result.uploadSinceNightly;
                boolean imagesChanged = created != null && has("image", "upload_date") && count("select count(*) from image where upload_date > ?", created) > 0;
                for (int i = 0; i < cols.length; i++) {
                    if (!has("statistics", cols[i])) continue;
                    if (!has(from[i])) continue;
                    String p = listsChanged ? "lists or specimens changed after the row was written"
                        : i == cols.length - 1 && imagesChanged ? "images were added after the row was written" : null;
                    inv.compare(c, "site", "statistics", "statistics.do latest row: " + cols[i], row[i], count(sql[i]), "=", p);
                }
            }
            if (has("ant_group", "upload_specimens") && has("specimen", "access_group")) {
                site().put("statistics.byUpload", "statistics.do?action=byUpload");
                Map<String, long[]> live = groupNumbers();
                Map<String, long[]> stored = inv.rows("select id, upload_specimens from ant_group where upload_specimens > 0");
                for (Map.Entry<String, long[]> e : stored.entrySet()) {
                    inv.compare(c, "site", "statistics.byUpload", "group " + e.getKey() + ": specimens (statistics.do by upload, live) = ant_group.upload_specimens",
                        e.getValue()[0], live.containsKey(e.getKey()) ? live.get(e.getKey())[0] : 0, "=",
                        inv.result.uploadSinceNightly ? "specimens were uploaded after the last nightly run" : null);
                }
            }
        });
    }

    /**
     * The specimens of every upload group in one pass (grouped by an expression, so MySQL reads the table once instead
     * of in index order: 4 s instead of 3 x 10 s on a production-size copy): group -> all specimens (statistics.do by
     * upload), then as UploadDb.getCounts counts them (groups.do, group.do, curator.do): Specimens, Collections,
     * Localities, Ungeoreferenced, Subfamilies, Genera, Species (valid names of AntWeb's subfamilies and extant genera:
     * getAntwebSubfamilyCriteria, getAntwebGenusCriteria), and Flagged (any specimen of the group with a red flag).
     */
    public static String groupNumbersSql() {
        // The rule of the nightly group recount (GroupCountDb, 2026-10-22), written once.
        return org.calacademy.antweb.home.GroupCountDb.countsSql();
    }

    private Map<String, long[]> groupNumbers;

    private Map<String, long[]> groupNumbers() throws SQLException {
        if (groupNumbers == null) groupNumbers = inv.rows(groupNumbersSql());
        return groupNumbers;
    }

    /**
     * groups.do, group.do and curator.do (rows 83, 85 to 91): each group's latest upload row (Specimens, Collections,
     * Localities, Ungeoreferenced, Flagged, Subfamilies, Genera, Species, written at the upload by UploadDb.updateCounts)
     * against a recount of the group's specimens now by the same rule; ant_group.upload_count = its uploads (groups.do
     * shows the live count; the stored one orders the page). A difference is pending when specimens were uploaded or
     * renamed after the group's last upload.
     */
    void adminGroups() {
        if (!has("upload", "group_id") || !has("upload", "specimens") || !has("specimen", "access_group")) return;
        CountInvariants.Check check = new CountInvariants.Check("admin.groups",
            "groups.do, group.do, curator.do: each group's numbers (today's, recounted every night since 2026-10-22) = a recount of its specimens; ant_group.upload_count = its uploads", "83, 85, 86, 87, 88, 89, 90, 91");
        inv.check(check, c -> {
            Map<String, String> groups = inv.places.computeIfAbsent("group", x -> CountInvariants.keyMap());
            Map<String, long[]> counts = groupNumbers();
            Map<String, long[]> uploads = inv.rows("select group_id, count(*) from upload where group_id is not null group by group_id");
            // groups that uploaded after the last nightly run: an upload may be running (its specimens replaced, its row
            // not written yet), and ant_group.upload_count is written at its end
            java.util.Set<String> uploadedToday = CountInvariants.keySet();
            if (has("upload", "created")) {
                try (PreparedStatement stmt = inv.connection.prepareStatement(inv.track("select distinct group_id from upload where group_id is not null and created >= ?"))) {
                    stmt.setTimestamp(1, inv.result.countedUntil);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) uploadedToday.add(r.getString(1));
                    }
                }
            }
            // the last name update of each group's specimens (one query; index by_group_date)
            Map<String, Timestamp> renamed = CountInvariants.keyMap();
            if (has("specimen_name_update", "run_date") && has("specimen_name_update", "access_group")) {
                try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                        "select access_group, max(run_date) from specimen_name_update where mode = 'changed' group by access_group"))) {
                    while (r.next()) renamed.put(r.getString(1), r.getTimestamp(2));
                }
            }
            Map<String, Object> exp = new TreeMap<>();
            String sql = "select u.group_id, u.upload_id, u.specimens, u.collections, u.localities, u.ungeoreferenced, u.flagged, u.subfamilies,"
                + " u.genera, u.species, " + CountInvariants.safeDate("u.created") + " from upload u"
                + " join (select group_id, max(upload_id) m from upload where group_id is not null group by group_id) x on x.m = u.upload_id";
            List<Object[]> rows = new ArrayList<>();
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql))) {
                while (r.next()) {
                    long[] v = new long[8];
                    for (int i = 0; i < 8; i++) v[i] = r.getLong(i + 3);
                    rows.add(new Object[] {r.getString(1), r.getInt(2), v, r.getTimestamp(11)});
                }
            }
            // Since 2026-10-22 (Q13) the pages show today's numbers of the group's specimens, recounted every night
            // (group_count, GroupCountDb); a group not counted yet still shows its last upload's numbers.
            Map<String, long[]> today = CountInvariants.keyMap();
            Map<String, Timestamp> countedAt = CountInvariants.keyMap();
            if (has(org.calacademy.antweb.home.GroupCountDb.TABLE, "counted")) {
                try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                        "select group_id, specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera, species, "
                        + CountInvariants.safeDate("counted") + " from " + org.calacademy.antweb.home.GroupCountDb.TABLE + " where counted is not null"))) {
                    while (r.next()) {
                        long[] v = new long[8];
                        for (int i = 0; i < 8; i++) v[i] = r.getLong(i + 2);
                        today.put(r.getString(1), v);
                        countedAt.put(r.getString(1), r.getTimestamp(10));
                    }
                }
            }
            String[] what = {"Specimens", "Collections", "Localities", "Ungeoreferenced", "Flagged", "Subfamilies", "Genera", "Species"};
            for (Object[] row : rows) {
                String g = (String) row[0];
                boolean counted = today.containsKey(g);
                long[] v = counted ? today.get(g) : (long[]) row[2];
                // the moment the numbers shown were counted: the nightly recount, or the group's last upload
                Timestamp created = counted ? countedAt.get(g) : (Timestamp) row[3];
                if (v[0] == 0 && !counts.containsKey(g)) continue;   // a group whose specimens are gone and whose last upload counted none
                groups.putIfAbsent(g, "upload group " + g);
                long[] n = counts.getOrDefault(g, new long[9]);
                // upload columns: specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera, species
                long[] recount = {n[1], n[2], n[3], n[4], n[8], n[5], n[6], n[7]};
                String p = uploadedToday.contains(g) ? "the group uploaded after the last nightly run (an upload may still be running)" : null;
                if (p == null && created != null && renamed.containsKey(g) && renamed.get(g) != null && renamed.get(g).after(created)) {
                    p = counted ? "specimens of the group were renamed after the nightly recount"
                        : "specimens of the group were renamed after its last upload (the numbers are from the upload)";
                }
                if (p == null && !counted) p = "no nightly run has recounted the group yet (the page shows its last upload's numbers)";
                for (int i = 0; i < 8; i++) {
                    inv.compare(c, "group", g, (counted ? "today's " : "last upload (" + row[1] + ") ") + what[i]
                        + " (groups.do, group.do) = a recount of the group's specimens", v[i], recount[i], "=", p);
                }
                long shownUploads = uploads.containsKey(g) ? uploads.get(g)[0] : 0;
                exp.put(g, new long[] {shownUploads, v[0], v[3], v[4], v[1], v[2], v[5], v[6], v[7]});
            }
            if (has("ant_group", "upload_count")) {
                Map<String, long[]> stored = inv.rows("select id, upload_count from ant_group where upload_count > 0");
                for (Map.Entry<String, long[]> e : stored.entrySet()) {
                    groups.putIfAbsent(e.getKey(), "upload group " + e.getKey());
                    inv.compare(c, "group", e.getKey(), "ant_group.upload_count (orders groups.do) = its uploads", e.getValue()[0],
                        uploads.containsKey(e.getKey()) ? uploads.get(e.getKey())[0] : 0, "=",
                        uploadedToday.contains(e.getKey()) ? "the group uploaded after the last nightly run (an upload may still be running)" : null);
                }
            }
            // groups.do columns: uploads, specimens, ungeoreferenced, flagged, collections, localities, subfamilies, genera, species
            expected("groups").putAll(exp);
        });
    }

    /**
     * curators.do and curator.do (rows 80 to 82, 84): each curator's Specimen Uploads, Description Edits, image uploads
     * and images uploaded as the pages show them (LoginMgr, read at start-up and by the nightly reload; CountCheckJob
     * passes them as curatorNumbers) against a recount. Pending when the table got rows after the last nightly run.
     */
    void adminCurators() {
        if (inv.curatorNumbers == null) return;
        CountInvariants.Check check = new CountInvariants.Check("admin.curators",
            "curators.do, curator.do: Specimen Uploads, Description Edits, image uploads, images uploaded = a recount", "80, 81, 82, 84");
        inv.check(check, c -> {
            Map<String, String> curators = inv.places.computeIfAbsent("curator", x -> CountInvariants.keyMap());
            // Description Edits = page-text edits, every saved version: taxon texts (description_edit, description_hist) and
            // place and project texts (object_edit, object_hist) (Brian Fisher, 2026-10-22; the check counted
            // description_edit only, the site also description_hist).
            List<String> editTables = new ArrayList<>();
            for (String t : org.calacademy.antweb.home.LoginDb.PAGE_TEXT_EDIT_TABLES) if (has(t, "access_login")) editTables.add(t);
            String[] sql = {
                has("upload", "login_id") ? "select login_id, count(*) from upload where login_id is not null group by login_id" : null,
                editTables.isEmpty() ? null : org.calacademy.antweb.home.LoginDb.pageTextEditsByLoginSql(editTables),
                has("image_upload", "curator_id") ? "select curator_id, count(*) from image_upload where curator_id is not null group by curator_id" : null,
                has("image_uploaded", "image_upload_id") && has("image_upload", "curator_id")
                    ? "select u.curator_id, count(*) from image_upload u join image_uploaded d on u.id = d.image_upload_id group by u.curator_id" : null};
            String[] tables = {"upload", "description_edit", "image_upload", "image_uploaded"};
            Map<String, Object> shownNumbers = new TreeMap<>();
            String[] what = {"Specimen Uploads", "Description Edits", "image uploads", "images uploaded"};
            List<Map<String, long[]>> recount = new ArrayList<>();
            boolean[] since = new boolean[4];
            for (int i = 0; i < 4; i++) {
                recount.add(sql[i] == null ? null : inv.rows(sql[i]));
                // the pages show LoginMgr's numbers as read at its last load (after the nightly run, in a thread that may
                // finish after this check started): rows added since that load are pending
                Timestamp read = inv.curatorsRead != null ? inv.curatorsRead : inv.result.countedUntil;
                since[i] = sql[i] != null && createdSince(tables[i], read);
                if (i == 1) for (String t : editTables) since[i] |= createdSince(t, read);
            }
            Map<String, Object> exp = new TreeMap<>();
            for (Map.Entry<Integer, int[]> e : inv.curatorNumbers.entrySet()) {
                String id = "" + e.getKey();
                curators.putIfAbsent(id, "curator " + id);
                long[] r = new long[4];
                for (int i = 0; i < 4; i++) {
                    if (recount.get(i) == null) continue;
                    r[i] = recount.get(i).containsKey(id) ? recount.get(i).get(id)[0] : 0;
                    inv.compare(c, "curator", id, what[i] + " (curators.do, curator.do)", e.getValue()[i], r[i], "=",
                        since[i] ? "rows were added after the pages read the numbers (LoginMgr, at the reload after the nightly run)" : null);
                }
                exp.put(id, r);
                shownNumbers.put(id, e.getValue());
            }
            // the e2e test compares the pages with the numbers the check compared (LoginMgr's, read at the nightly reload)
            expected("curators").putAll(shownNumbers);
            expected("curatorsRecount").putAll(exp);
        });
    }

    /** artists.do (row 92): each artist's Images and Specimen (ArtistMgr; artistNumbers) = a recount; Specimen <= Images. */
    void adminArtists() {
        if (inv.artistNumbers == null || !has("image", "artist")) return;
        CountInvariants.Check check = new CountInvariants.Check("admin.artists", "artists.do: Images and Specimen of each artist = a recount; Specimen <= Images", "92");
        inv.check(check, c -> {
            Map<String, String> artists = inv.places.computeIfAbsent("artist", x -> CountInvariants.keyMap());
            Map<String, long[]> recount = inv.rows("select artist, count(*), count(distinct image_of_id) from image where artist is not null group by artist");
            String p = inv.result.imagesSinceNightly ? "images were added after the last nightly run" : null;
            Map<String, Object> exp = new TreeMap<>();
            for (Map.Entry<Integer, int[]> e : inv.artistNumbers.entrySet()) {
                String id = "" + e.getKey();
                artists.putIfAbsent(id, "artist " + id);
                long[] r = recount.getOrDefault(id, new long[2]);
                inv.compare(c, "artist", id, "Images (artists.do)", e.getValue()[0], r[0], "=", p);
                inv.compare(c, "artist", id, "Specimen (artists.do)", e.getValue()[1], r[1], "=", p);
                inv.compare(c, "artist", id, "Specimen <= Images", r[1], r[0], "<=", null);
                exp.put(id, e.getValue());
            }
            expected("artists").putAll(exp);
        });
    }

    /**
     * listImageUploads.do and imageUploadReport.do (rows 93, 94): Total image uploads = the image_upload rows; each
     * upload's Image Count (written when it ends) = its image_uploaded rows. Pending while an upload is not complete.
     */
    void adminImageUploads() {
        if (!has("image_upload", "image_count") || !has("image_uploaded", "image_upload_id")) return;
        CountInvariants.Check check = new CountInvariants.Check("admin.imageUploads",
            "listImageUploads.do: each image upload's Image Count = its files (image_uploaded rows)", "93, 94");
        inv.check(check, c -> {
            Map<String, String> uploads = inv.places.computeIfAbsent("image upload", x -> CountInvariants.keyMap());
            Map<String, long[]> files = inv.rows("select image_upload_id, count(*) from image_uploaded group by image_upload_id");
            boolean complete = has("image_upload", "complete");
            String sql = "select id, image_count, " + (complete ? "complete" : "1") + " from image_upload";
            long total = 0;
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql))) {
                while (r.next()) {
                    ++total;
                    String id = r.getString(1);
                    uploads.putIfAbsent(id, "image upload " + id);
                    inv.compare(c, "image upload", id, "Image Count (listImageUploads.do) = its files", r.getLong(2),
                        files.containsKey(id) ? files.get(id)[0] : 0, "=", r.getInt(3) == 1 ? null : "the upload is not complete");
                }
            }
            expected("imageUploads").put("total", total);
        });
    }

    // ------------------------------------------------------------------ API

    /**
     * API /taxa subfamilyCount, genusCount, speciesCount, specimenCount (row 103, B11): read from taxon columns that no
     * current code writes. What they should be is the Global row (allantwebants, proj_taxon) of the name: the same
     * numbers as its Global column. Real differences until B11 is fixed (the nightly run does not write them).
     */
    void apiTaxa() {
        if (!has("taxon", "specimen_count")) return;
        CountInvariants.Check check = new CountInvariants.Check("api.taxa",
            "API /taxa subfamilyCount, genusCount, speciesCount, specimenCount (taxon columns) = the name's Global row", "103");
        inv.check(check, c -> {
            site().put("api.taxa", "API /taxa (taxon table columns)");
            String[] cols = {"subfamily_count", "genus_count", "species_count", "specimen_count"};
            StringBuilder sql = new StringBuilder("select t.taxon_name, t.taxarank");
            for (String col : cols) sql.append(", ").append(has("taxon", col) ? "coalesce(t." + col + ", 0)" : "0").append(", coalesce(a.").append(col).append(", 0)");
            sql.append(" from taxon t join proj_taxon a on a.project_name = 'allantwebants' and a.taxon_name = t.taxon_name");
            String[] fields = {"subfamilyCount", "genusCount", "speciesCount", "specimenCount"};
            boolean[] present = new boolean[cols.length];
            for (int i = 0; i < cols.length; i++) present[i] = has("taxon", cols[i]);
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql.toString()))) {
                while (r.next()) {
                    for (int i = 0; i < cols.length; i++) {
                        if (!present[i]) continue;
                        inv.compare(c, "site", "api.taxa", r.getString(1) + ": /taxa " + fields[i] + " = its Global row (B11)",
                            r.getLong(3 + 2 * i), r.getLong(4 + 2 * i), "=", null);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ endemic flags

    /**
     * The endemic flags of country and adm1 lists (rows 24, 78, 110): the rows endemic.do lists and the Endemic number
     * counts, recounted by the rule of GeolocaleDb.calcCountryEndemism and calcAdm1Endemism: a species or subspecies of
     * a counted status other than morphotaxon, not fossil, an ant, on the list of exactly one country (of any validity);
     * an adm1 row when the name is endemic to the adm1's country and on the list of exactly one adm1. Regions,
     * subregions (calcHigherEndemism) and bioregions are not recounted here.
     */
    void endemicFlags() {
        if (!has("geolocale_taxon", "is_endemic") || !inv.places.containsKey(PlaceTotals.GEOLOCALE)) return;
        CountInvariants.Check check = new CountInvariants.Check("place.endemic.flags",
            "Endemic flags of country and adm1 lists = a recount by the rule of calcEndemic (rows of endemic.do)", "24, 78, 110")
            .pendingOn(false, true, false, false);
        inv.check(check, c -> {
            String rule = " and t.status != 'morphotaxon' and t.taxarank in ('species', 'subspecies') and t.fossil = 0"
                + " and t.status in " + CountInvariants.COUNTABLE + " and t.family = 'formicidae'";
            String countryEndemic = "select gt.taxon_name from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " join taxon t on t.taxon_name = gt.taxon_name where g.georank = 'country'" + rule
                + " group by gt.taxon_name having count(*) = 1";
            // per country: rows flagged, rows that should be, and rows both
            String countries = "select gt.geolocale_id, sum(gt.is_endemic = 1), sum(e.taxon_name is not null), sum(gt.is_endemic = 1 and e.taxon_name is not null)"
                + " from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " left join (" + countryEndemic + ") e on e.taxon_name = gt.taxon_name"
                + " where g.georank = 'country' group by gt.geolocale_id";
            compareFlags(c, inv.rows(countries), "country");
            // names endemic to a country by the recount above (not by the stored country flags, which the country half
            // of this check tests), with that country's name
            String countryEndemicByName = "select gt.taxon_name, max(g.name) country from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " join taxon t on t.taxon_name = gt.taxon_name where g.georank = 'country'" + rule
                + " group by gt.taxon_name having count(*) = 1";
            String adm1Endemic = "select gt.taxon_name from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " join taxon t on t.taxon_name = gt.taxon_name"
                + " join (" + countryEndemicByName + ") ce on ce.taxon_name = gt.taxon_name and ce.country = g.parent"
                + " where g.georank = 'adm1'" + rule
                + " group by gt.taxon_name having count(*) = 1";
            String adm1s = "select gt.geolocale_id, sum(gt.is_endemic = 1), sum(e.taxon_name is not null), sum(gt.is_endemic = 1 and e.taxon_name is not null)"
                + " from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " left join (" + adm1Endemic + ") e on e.taxon_name = gt.taxon_name"
                + " where g.georank = 'adm1' group by gt.geolocale_id";
            compareFlags(c, inv.rows(adm1s), "adm1");
        });
    }

    private void compareFlags(CountInvariants.Check c, Map<String, long[]> m, String rank) {
        Map<String, String> inScope = inv.places.get(PlaceTotals.GEOLOCALE);
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            if (!inScope.containsKey(e.getKey())) continue;
            long[] v = e.getValue();
            String p = inv.why(c, PlaceTotals.GEOLOCALE, e.getKey());
            inv.compare(c, PlaceTotals.GEOLOCALE, e.getKey(), "endemic rows (endemic.do) = endemic by the " + rank + " rule", v[0], v[1], "=", p);
            --c.compared;
            inv.compare(c, PlaceTotals.GEOLOCALE, e.getKey(), "endemic rows flagged that the " + rank + " rule says are endemic", v[2], v[0], "=", p);
        }
    }

    // ------------------------------------------------------------------ B25, B26

    /**
     * B25 (2026-10-22): the crawl sums the rows of a list by parent_taxon_name (CountDb.countGrandChildren), so a
     * subspecies whose parent is its species (not its genus) puts its specimens into the species row, replacing the
     * species' own. Uploads set the parent from the name (Taxon.getParentTaxonNameFromName: the genus), so this check
     * compares every species' and subspecies' parent with its genus by name, and counts, per place, the species rows
     * that such a subspecies on the same list would overwrite.
     */
    void taxonParents() {
        CountInvariants.Check check = new CountInvariants.Check("taxon.parents",
            "B25: ants of a counted status have their genus as parent (a subspecies under its species changes the species row's Specimens)", "22, 40, 41");
        inv.check(check, c -> {
            site().put("taxa", "taxon table");
            // Only the names the lists count (counted statuses). Synonyms, homonyms and obsolete combinations take the
            // parent of their current valid name (AntwebUpload.saveTaxon), so their own genus differs by design; and a
            // name whose current valid name is another name is left out for the same reason (2026-10-22: 6,503 such
            // rows on staging were not findings).
            boolean current = has("taxon", "current_valid_name");
            String sql = "select c.taxon_name, c.taxarank, c.parent_taxon_name, p.taxarank, c.status from taxon c"
                + " left join taxon p on p.taxon_name = c.parent_taxon_name"
                + " where c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %' and c.family = 'formicidae'"
                + " and c.status in " + CountInvariants.COUNTABLE
                + (current ? " and (c.current_valid_name is null or c.current_valid_name = '' or c.current_valid_name = c.taxon_name)" : "");
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql))) {
                while (r.next()) {
                    String name = r.getString(1);
                    String parent = r.getString(3);
                    String byName = name.substring(0, name.indexOf(' '));
                    ++c.compared;
                    if (parent != null && CountInvariants.key(parent).equals(CountInvariants.key(byName))) continue;
                    --c.compared;
                    boolean b25 = "species".equals(r.getString(4));
                    inv.compare(c, "site", "taxa", name + " (" + r.getString(2) + ", " + r.getString(5) + "): parent " + parent
                        + (r.getString(4) == null ? (parent == null ? "" : " (not a taxon)") : " (" + r.getString(4) + ")") + ", genus by its name " + byName
                        + (b25 ? " (B25: its specimens replace the species row's)" : parent == null ? " (B36: no parent; the genus row does not count it)"
                            : " (B36: counted under another genus row)"), 1, 0, "=", null);
                }
            }
            // rows of lists that B25 changes: a species row with a subspecies row whose parent is that species
            if (count("select count(*) from taxon c join taxon p on p.taxon_name = c.parent_taxon_name where c.taxarank = 'subspecies' and p.taxarank = 'species'") == 0) return;
            for (String type : PlaceTotals.TYPES) {
                Map<String, String> inScope = inv.places.get(type);
                if (inScope == null) continue;
                String l = PlaceTotals.listTable(type);
                String lk = PlaceTotals.listKey(type);
                Map<String, long[]> affected = inv.rows("select ss." + lk + ", count(distinct sp.taxon_name) from " + l + " ss"
                    + " join taxon t on t.taxon_name = ss.taxon_name and t.taxarank = 'subspecies'"
                    + " join taxon pt on pt.taxon_name = t.parent_taxon_name and pt.taxarank = 'species'"
                    + " join " + l + " sp on sp." + lk + " = ss." + lk + " and sp.taxon_name = t.parent_taxon_name group by ss." + lk);
                for (Map.Entry<String, long[]> e : affected.entrySet()) {
                    if (!inScope.containsKey(e.getKey())) continue;
                    inv.compare(c, type, e.getKey(), "B25: species rows whose Specimens the crawl replaces by their subspecies' sum", e.getValue()[0], 0, "=", null);
                }
            }
        });
    }

    /**
     * B26 (2026-10-22): adm1 numbers take the specimens by adm1 name only (Geolocale.getSpecimenPlaceCriteria:
     * specimen.adm1 = name; also PlaceImageCountDb, TaxaPage, downloads), so a state with the same name in another
     * country, or a specimen with that adm1 and no country, adds to its Specimens and Images. Per live adm1: the
     * specimens counted on its page (ants of a counted status, of a species or subspecies on its list) that were not
     * collected in its country. Real until B26 is fixed. "Its country" is specimen.country equal to the adm1's parent,
     * as the country pages match their specimens (specimen.country = name): a specimen whose country is spelled
     * otherwise (an alias) is counted for the state today and is not counted for the country, so it is reported too.
     */
    void adm1ByCountry() {
        if (!inv.places.containsKey(PlaceTotals.GEOLOCALE) || !has("specimen", "adm1")) return;
        CountInvariants.Check check = new CountInvariants.Check("geo.adm1.country",
            "B26: an adm1's Specimens hold only specimens of its own country (adm1 matched by name only)", "26, 27, 29")
            .pendingOn(false, true, false, false);
        inv.check(check, c -> {
            // 1. the (country, adm1) pairs of the specimens (index country_adm1_idx: under a second); 2. the pairs whose adm1
            // is the name of a valid adm1 of another country (or that have no country); 3. for those only, the specimens
            // its page counts (ants of a counted status, of a species or subspecies on its list).
            Map<String, List<String[]>> adm1s = CountInvariants.keyMap();   // name -> [id, parent]
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select id, name, parent from geolocale where georank = 'adm1' and is_valid = 1 and coalesce(is_island, 0) = 0"))) {
                while (r.next()) adm1s.computeIfAbsent(r.getString(2), x -> new ArrayList<>()).add(new String[] {r.getString(1), r.getString(3)});
            }
            Map<String, long[]> foreign = CountInvariants.keyMap();
            List<String[]> pairs = new ArrayList<>();
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(
                    "select country, adm1 from specimen where adm1 is not null group by country, adm1"))) {
                while (r.next()) pairs.add(new String[] {r.getString(1), r.getString(2)});
            }
            String sql = "select count(*) from specimen s join geolocale_taxon gt on gt.geolocale_id = ? and gt.taxon_name = s.taxon_name"
                + " join taxon t on t.taxon_name = s.taxon_name where s.country <=> ? and s.adm1 = ?"
                + " and t.taxarank in ('species', 'subspecies') and t.family = 'formicidae' and s.status in " + CountInvariants.COUNTABLE;
            // one indexed query per pair whose adm1 name is a state of another country; at most 60 s (the invariants have
            // no other time limit), the pairs not reached are named in one difference
            long until = System.currentTimeMillis() + 60_000;
            int notReached = 0;
            for (String[] pair : pairs) {
                for (String[] g : adm1s.getOrDefault(pair[1], new ArrayList<>())) {
                    if (pair[0] != null && CountInvariants.key(pair[0]).equals(CountInvariants.key(g[1]))) continue;
                    if (System.currentTimeMillis() > until) {
                        ++notReached;
                        continue;
                    }
                    long n = count(sql, Integer.parseInt(g[0]), pair[0], pair[1]);
                    if (n > 0) foreign.computeIfAbsent(g[0], x -> new long[1])[0] += n;
                }
            }
            if (notReached > 0) {
                site().put("specimens", "specimen table");
                inv.compare(c, "site", "specimens", "B26: (country, adm1) pairs not counted this time (60 s limit)", notReached, 0, "=",
                    "the check stopped at its time limit; the next run counts them");
            }
            Map<String, long[]> stored = inv.rows("select id, specimen_count from geolocale where georank = 'adm1' and is_valid = 1");
            Map<String, String> inScope = inv.places.get(PlaceTotals.GEOLOCALE);
            Map<String, Object> exp = new TreeMap<>();
            for (Map.Entry<String, long[]> e : stored.entrySet()) {
                if (!inScope.containsKey(e.getKey())) continue;
                long n = foreign.containsKey(e.getKey()) ? foreign.get(e.getKey())[0] : 0;
                long shown = e.getValue()[0];
                inv.compare(c, PlaceTotals.GEOLOCALE, e.getKey(), "Specimens without the " + n + " specimens of another country (or none) with this adm1 name (B26)",
                    shown, shown - n, "=", null);
                if (n > 0) exp.put(e.getKey(), n);
            }
            expected("b26").put("adm1s", exp.size());
            expected("b26").put("specimens", exp.values().stream().mapToLong(o -> (Long) o).sum());
        });
    }

    /** The values for the end-to-end tests as JSON (in a script element of the report; no "</" inside). */
    public static String json(Map<String, Object> expected) {
        String s = new com.google.gson.Gson().toJson(expected);
        return s.replace("</", "<\\/");
    }
}

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
            inv.compare(c, "site", "statsPage", "Images table: Sum (statuses shown) = all images (" + all + ") minus orphan images (" + orphans + ")"
                + (nullStatus > 0 ? "; " + nullStatus + " images of specimens without a status are in no row" : ""), sum[0], all - orphans, "=", null);
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
            : "statsPage Bioregion Data agrees with each bioregion's page and Global Data", museum ? "18, 19" : "17");
        inv.check(check, c -> {
            Map<String, long[][]> data = CountInvariants.keyMap();
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(statsPlacesSql(type)))) {
                while (r.next()) {
                    int col = java.util.Arrays.asList(RANKS).indexOf(r.getString(3));
                    if (col < 0) continue;
                    data.computeIfAbsent(r.getString(1), x -> new long[2][4])[r.getInt(2) == 1 ? 1 : 0][col] += r.getLong(4);
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
                exp.put(place, museum ? d : d[0]);
                if (!labels.containsKey(place)) continue;
                String p = inv.why(c, type, place);
                String page = museum ? "statsPage Museum Data" : "statsPage Bioregion Data";
                inv.compare(c, type, place, page + (museum ? ", Valid Extant" : "") + ": Species + Subspecies = Valid species of the " + type + " page",
                    d[0][2] + d[0][3], s[3], "=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil Subfamilies <= Subfamilies of the " + type + " page", d[0][0] + d[1][0], s[0], "<=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil Genera <= Genera of the " + type + " page", d[0][1] + d[1][1], s[1], "<=", p);
                inv.compare(c, type, place, page + ": valid extant and fossil species and subspecies <= Species of the " + type + " page",
                    d[0][2] + d[0][3] + d[1][2] + d[1][3], s[2], "<=", p);
                for (int f = 0; f < (museum ? 2 : 1); f++) {
                    for (int i = 0; i < 4; i++) {
                        inv.compare(c, type, place, page + (f == 0 ? (museum ? ", Valid Extant " : " ") : ", Valid Fossil ") + what[i]
                            + " <= Global Data " + (f == 0 ? "Extant" : "Fossil"), d[f][i], global[f][i], "<=", null);
                    }
                }
            }
            expected("statsPage").put(museum ? "museums" : "bioregions", exp);
        });
    }

    /** The statsPage queries of Bioregion Data and Museum Data (StatisticsDb), for every place at once: key, fossil, rank, count. */
    public static String statsPlacesSql(String type) {
        boolean museum = "museum".equals(type);
        String list = museum ? "museum_taxon" : "bioregion_taxon";
        String key = museum ? "code" : "bioregion_name";
        return "select l." + key + ", t.fossil, t.taxarank, count(*) from taxon t join " + list + " l on t.taxon_name = l.taxon_name"
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
                    c.compared += (int) count("select count(*) from " + l + " p join taxon pt on pt.taxon_name = p.taxon_name and pt.taxarank = '" + level[0] + "'");
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
                boolean listsChanged = createdSince("proj_taxon", created) || createdSince("geolocale_taxon", created)
                    || createdSince("museum_taxon", created) || createdSince("bioregion_taxon", created) || createdSince("upload", created);
                boolean imagesChanged = created != null && has("image", "upload_date") && count("select count(*) from image where upload_date > ?", created) > 0;
                for (int i = 0; i < cols.length; i++) {
                    if (!has("statistics", cols[i])) continue;
                    String table = sql[i].replaceAll(".* from (\\w+).*", "$1");
                    if (!has(table)) continue;
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
        String c1 = "(" + org.calacademy.antweb.home.SpecimenDb.getFlagCriteria() + " and " + org.calacademy.antweb.home.SpecimenDb.getStatusCriteria()
            + " and " + org.calacademy.antweb.home.SpecimenDb.getTaxaCriteria() + ")";
        String c2 = c1 + " and (" + org.calacademy.antweb.home.SpecimenDb.getAntwebSubfamilyCriteria() + " and "
            + org.calacademy.antweb.home.SpecimenDb.getAntwebGenusCriteria() + ")";
        return "select access_group + 0 g, count(*), count(case when " + c1 + " then code end),"
            + " count(distinct case when " + c1 + " then collectioncode end), count(distinct case when " + c1 + " then localitycode end),"
            + " count(case when " + c1 + " and decimal_latitude is null then 1 end),"
            + " count(distinct case when " + c2 + " then subfamily end), count(distinct case when " + c2 + " then genus end),"
            + " count(distinct case when " + c2 + " then species end), count(case when flag = 'red' then 1 end)"
            + " from specimen where access_group is not null group by g";
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
            "groups.do, group.do, curator.do: each group's last upload numbers = a recount of its specimens; ant_group.upload_count = its uploads", "83, 85, 86, 87, 88, 89, 90, 91");
        inv.check(check, c -> {
            Map<String, String> groups = inv.places.computeIfAbsent("group", x -> CountInvariants.keyMap());
            Map<String, long[]> counts = groupNumbers();
            Map<String, long[]> uploads = inv.rows("select group_id, count(*) from upload where group_id is not null group by group_id");
            boolean names = has("specimen_name_update", "run_date");
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
            String[] what = {"Specimens", "Collections", "Localities", "Ungeoreferenced", "Flagged", "Subfamilies", "Genera", "Species"};
            for (Object[] row : rows) {
                String g = (String) row[0];
                long[] v = (long[]) row[2];
                Timestamp created = (Timestamp) row[3];
                if (v[0] == 0 && !counts.containsKey(g)) continue;   // a group whose specimens are gone and whose last upload counted none
                groups.putIfAbsent(g, "upload group " + g);
                long[] n = counts.getOrDefault(g, new long[9]);
                // upload columns: specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera, species
                long[] recount = {n[1], n[2], n[3], n[4], n[8], n[5], n[6], n[7]};
                // The numbers are written at the group's upload only: specimens of the group renamed since (the nightly name
                // update) can change them until its next upload (question Q13 in docs/numbers.md).
                String p = null;
                if (created != null && names && count("select count(*) from specimen_name_update where mode = 'changed' and access_group = ? and run_date > ?",
                        Integer.parseInt(g), created) > 0) {
                    p = "specimens of the group were renamed after its last upload (the numbers are from the upload)";
                }
                for (int i = 0; i < 8; i++) {
                    inv.compare(c, "group", g, "last upload (" + row[1] + ") " + what[i] + " = a recount of the group's specimens", v[i], recount[i], "=", p);
                }
                long shownUploads = uploads.containsKey(g) ? uploads.get(g)[0] : 0;
                exp.put(g, new long[] {shownUploads, v[0], v[3], v[4], v[1], v[2], v[5], v[6], v[7]});
            }
            if (has("ant_group", "upload_count")) {
                Map<String, long[]> stored = inv.rows("select id, upload_count from ant_group where upload_count > 0");
                for (Map.Entry<String, long[]> e : stored.entrySet()) {
                    groups.putIfAbsent(e.getKey(), "upload group " + e.getKey());
                    inv.compare(c, "group", e.getKey(), "ant_group.upload_count (orders groups.do) = its uploads", e.getValue()[0],
                        uploads.containsKey(e.getKey()) ? uploads.get(e.getKey())[0] : 0, "=", null);
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
            String[] sql = {
                has("upload", "login_id") ? "select login_id, count(*) from upload where login_id is not null group by login_id" : null,
                has("description_edit", "access_login") ? "select access_login, count(*) from description_edit where access_login is not null group by access_login" : null,
                has("image_upload", "curator_id") ? "select curator_id, count(*) from image_upload where curator_id is not null group by curator_id" : null,
                has("image_uploaded", "image_upload_id") && has("image_upload", "curator_id")
                    ? "select u.curator_id, count(*) from image_upload u join image_uploaded d on u.id = d.image_upload_id group by u.curator_id" : null};
            String[] tables = {"upload", "description_edit", "image_upload", "image_upload"};
            String[] what = {"Specimen Uploads", "Description Edits", "image uploads", "images uploaded"};
            List<Map<String, long[]>> recount = new ArrayList<>();
            boolean[] since = new boolean[4];
            for (int i = 0; i < 4; i++) {
                recount.add(sql[i] == null ? null : inv.rows(sql[i]));
                since[i] = sql[i] != null && createdSince(tables[i], inv.result.countedUntil);
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
                        since[i] ? "rows were added after the last nightly run (the pages read the numbers at the nightly reload)" : null);
                }
                inv.compare(c, "curator", id, "images uploaded >= image uploads", r[3], r[2], ">=", null);
                exp.put(id, r);
            }
            expected("curators").putAll(exp);
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
                exp.put(id, r);
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
            String adm1Endemic = "select gt.taxon_name from geolocale g join geolocale_taxon gt on g.id = gt.geolocale_id"
                + " join taxon t on t.taxon_name = gt.taxon_name where g.georank = 'adm1'"
                + " and (gt.taxon_name, g.parent) in (select gt2.taxon_name, g2.name from geolocale g2 join geolocale_taxon gt2 on g2.id = gt2.geolocale_id"
                + "   where gt2.is_endemic = 1 and g2.georank = 'country')" + rule
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
            "B25: species and subspecies have their genus as parent (a subspecies under its species changes the species row's Specimens)", "22, 40, 41");
        inv.check(check, c -> {
            site().put("taxa", "taxon table");
            String sql = "select c.taxon_name, c.taxarank, c.parent_taxon_name, p.taxarank from taxon c left join taxon p on p.taxon_name = c.parent_taxon_name"
                + " where c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %'";
            try (Statement stmt = inv.connection.createStatement(); ResultSet r = stmt.executeQuery(inv.track(sql))) {
                while (r.next()) {
                    String name = r.getString(1);
                    String parent = r.getString(3);
                    String byName = name.substring(0, name.indexOf(' '));
                    ++c.compared;
                    if (parent != null && CountInvariants.key(parent).equals(CountInvariants.key(byName))) continue;
                    --c.compared;
                    inv.compare(c, "site", "taxa", name + " (" + r.getString(2) + "): parent " + parent + (r.getString(4) == null ? "" : " (" + r.getString(4) + ")")
                        + ", by its name " + byName + (("species".equals(r.getString(4))) ? " (B25: its specimens go into the species row)" : ""), 1, 0, "=", null);
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
     * collected in its country. Real until B26 is fixed.
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
            for (String[] pair : pairs) {
                for (String[] g : adm1s.getOrDefault(pair[1], new ArrayList<>())) {
                    if (pair[0] != null && CountInvariants.key(pair[0]).equals(CountInvariants.key(g[1]))) continue;
                    long n = count(sql, Integer.parseInt(g[0]), pair[0], pair[1]);
                    if (n > 0) foreign.computeIfAbsent(g[0], x -> new long[1])[0] += n;
                }
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

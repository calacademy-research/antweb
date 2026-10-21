package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The rules of the numbers at the top of a place page (country, adm1, island, region, subregion,
 * project, museum, bioregion), written once, used by the nightly run that stores them (GeolocaleDb,
 * ProjectDb, MuseumDb, BioregionDb) and by the count check (CountCheck, CountInvariants) that checks
 * them (Brian Fisher, 2026-10-20: every number on the site validated by a test).
 *
 *   Subfamilies  subfamily rows on the place's list whose taxon has a counted status
 *   Genera       genus rows on the list, counted status
 *   Species      species and subspecies rows on the list, counted status, ants (family formicidae)
 *   Valid        species and subspecies rows on the list, status valid, not fossil
 *
 * Counted status: valid, unrecognized, morphotaxon, indetermined, unidentifiable (CountCheck.COUNTABLE).
 *
 * Until 2026-10-21 Genera and Species (and the Subfamilies of museums and bioregions) were roll-up
 * sums of the per-row child counts of the list (CountDb.getCountableTaxonCount: sum(genus_count) of
 * the subfamily rows, sum(species_count) of the genus rows). A child count that was stale, of the
 * wrong rank, or a species whose genus row was missing from the list made the total wrong; projects
 * also summed yesterday's row counts (the list was recounted after the totals). Now they are counted
 * directly, so the total is right whatever the row counts are.
 *
 * Pie charts on the place page:
 *   Species per Subfamily   the rows counted by Species, grouped by subfamily: the slices add up to Species
 *   Specimens per Subfamily geolocales and projects: the specimen counts of the species and subspecies
 *                           rows, grouped by subfamily; museums and bioregions: the place's own specimens
 *                           (specimen.museum, specimen.bioregion) by subfamily. Either way the slices add
 *                           up to Specimens (one slice "Other" for names that are not a subfamily of the taxon table, and when the JSON would not fit its column, the smallest slices merged into it). Before 2026-10-21 museums and bioregions
 *                           counted every specimen in AntWeb of the names on their list, and project and
 *                           bioregion species pies counted rows of every rank.
 */
public final class PlaceTotals {

    private PlaceTotals() {}

    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";
    public static final String MUSEUM = "museum";
    public static final String BIOREGION = "bioregion";
    public static final String[] TYPES = {GEOLOCALE, PROJECT, MUSEUM, BIOREGION};

    /** Counted statuses (StatusSet.getCountables). */
    public static final String COUNTABLE = "('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";

    public static final String SUBFAMILY = "subfamily";
    public static final String GENUS = "genus";
    public static final String SPECIES = "species";

    /** The place's list table. */
    public static String listTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale_taxon";
            case PROJECT: return "proj_taxon";
            case MUSEUM: return "museum_taxon";
            case BIOREGION: return "bioregion_taxon";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The column of the list table that names the place. */
    public static String listKey(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale_id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            case BIOREGION: return "bioregion_name";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The place's own table (its totals). */
    public static String placeTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale";
            case PROJECT: return "project";
            case MUSEUM: return "museum";
            case BIOREGION: return "bioregion";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** The key column of the place's own table. */
    public static String placeKey(String type) {
        switch (type) {
            case GEOLOCALE: return "id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            case BIOREGION: return "name";
            default: throw new IllegalArgumentException(type);
        }
    }

    /** Taxon conditions (alias t) of the rows a total counts. rank: subfamily, genus or species. */
    public static String rankCondition(String rank) {
        switch (rank) {
            case SUBFAMILY: return "t.taxarank = 'subfamily' and t.status in " + COUNTABLE;
            case GENUS: return "t.taxarank = 'genus' and t.status in " + COUNTABLE;
            case SPECIES: return "t.taxarank in ('species', 'subspecies') and t.status in " + COUNTABLE + " and t.family = 'formicidae'";
            default: throw new IllegalArgumentException(rank);
        }
    }

    /** Valid species and subspecies, not fossil (alias t). */
    public static final String VALID_CONDITION = "t.taxarank in ('species', 'subspecies') and t.status = 'valid' and t.fossil = 0";

    /** One place (parameter: its key): the number of rows of that rank on its list. */
    public static String listedCountSql(String type, String rank) {
        return "select count(distinct l.taxon_name) from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and " + rankCondition(rank);
    }

    /** All places of a type: place key, number of rows of that rank on its list (places with none are missing). */
    public static String listedCountsByPlaceSql(String type, String rank) {
        return "select l." + listKey(type) + ", count(distinct l.taxon_name) from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where " + rankCondition(rank) + " group by l." + listKey(type);
    }

    /** One place (parameter: its key): valid extant species and subspecies on its list. */
    public static String validSpeciesSql(String type) {
        return "select count(distinct l.taxon_name) from " + listTable(type) + " l join taxon t on t.taxon_name = l.taxon_name"
            + " where l." + listKey(type) + " = ? and " + VALID_CONDITION;
    }

    /** All places of a type: place key, valid extant species and subspecies on its list. */
    public static String validSpeciesByPlaceSql(String type) {
        return "select l." + listKey(type) + ", count(distinct l.taxon_name) from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where " + VALID_CONDITION + " group by l." + listKey(type);
    }

    /**
     * "Species per Subfamily" pie of one place (parameter: its key): subfamily, count, chart_color (null:
     * the subfamily's colour). The rows of the Species number, so the slices add up to Species.
     */
    public static String speciesPieSql(String type) {
        return "select " + SLICE + " subfamily, count(distinct l.taxon_name) count from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name" + sliceJoin("t.subfamily")
            + " where l." + listKey(type) + " = ? and " + rankCondition(SPECIES)
            + " group by " + SLICE + " order by " + SLICE;
    }

    /** Label of the slice of everything that is not a subfamily in the taxon table (other families, typos, none). */
    public static final String OTHER = "Other";

    /** The slice of a row: the subfamily when it is a subfamily of the taxon table, else Other (alias sf). */
    static final String SLICE = "coalesce(sf.taxon_name, '" + OTHER + "')";

    static String sliceJoin(String subfamilyColumn) {
        return " left join taxon sf on sf.taxon_name = " + subfamilyColumn + " and sf.taxarank = 'subfamily'";
    }

    /**
     * "Specimens per Subfamily" pie of one place (parameter: its key): subfamily, count. Geolocales and
     * projects: the specimen counts of the species and subspecies rows of the list (as the Specimens
     * number); museums and bioregions: the specimens of the museum or bioregion. A slice per subfamily of the
     * taxon table, one Other slice for the rest (2026-10-21: specimen.subfamily of other families or typos made
     * one slice each, and the JSON outgrew its column; see pieJson).
     */
    public static String specimenPieSql(String type) {
        if (MUSEUM.equals(type) || BIOREGION.equals(type)) {
            String column = MUSEUM.equals(type) ? "museum" : "bioregion";
            return "select " + SLICE + " subfamily, count(*) count from specimen s" + sliceJoin("s.subfamily")
                + " where s." + column + " = ? group by " + SLICE + " order by " + SLICE;
        }
        return "select " + SLICE + " subfamily, sum(l.specimen_count) count from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name" + sliceJoin("t.subfamily")
            + " where l." + listKey(type) + " = ? and t.taxarank in ('species', 'subspecies')"
            + " group by " + SLICE + " having sum(l.specimen_count) > 0 order by " + SLICE;
    }

    /** Runs a one-number query with one parameter (the place key). */
    public static int count(Connection connection, String sql, String key) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    /** The place's Subfamilies, Genera and Species numbers, counted from its list. */
    public static int[] listedCounts(Connection connection, String type, String key) throws SQLException {
        return new int[] {
            count(connection, listedCountSql(type, SUBFAMILY), key),
            count(connection, listedCountSql(type, GENUS), key),
            count(connection, listedCountSql(type, SPECIES), key)
        };
    }

    /** Colour of a slice whose subfamily has no colour of its own (the slice is kept, so the pie still adds up). */
    public static final String NO_COLOR = "#999999";

    private static final org.apache.commons.logging.Log s_log = org.apache.commons.logging.LogFactory.getLog(PlaceTotals.class);

    /** One slice: label (subfamily or Other), count. */
    static final class Slice {
        String subfamily;
        long count;
        Slice(String subfamily, long count) { this.subfamily = subfamily; this.count = count; }
    }

    static java.util.List<Slice> slices(Connection connection, String sql, String key) throws SQLException {
        java.util.List<Slice> list = new java.util.ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) list.add(new Slice(r.getString(1), r.getLong(2)));
            }
        }
        return list;
    }

    static String json(java.util.List<Slice> slices) {
        StringBuilder json = new StringBuilder();
        int i = 0;
        for (Slice sl : slices) {
            String color = null;
            if (sl.subfamily != null && !OTHER.equals(sl.subfamily)) {
                try {
                    org.calacademy.antweb.Subfamily s = org.calacademy.antweb.util.TaxonMgr.getSubfamily(sl.subfamily);
                    if (s != null) color = s.getChartColor();
                } catch (RuntimeException e) {
                    // taxa not loaded (tests): the slice keeps NO_COLOR
                }
            }
            if (color == null) color = NO_COLOR;
            if (i++ > 0) json.append(",");
            String label = sl.subfamily == null || OTHER.equals(sl.subfamily) ? OTHER : org.calacademy.antweb.Formatter.initCap(sl.subfamily);
            json.append(org.calacademy.antweb.util.HttpUtil.getJsonElement(i, label, (int) sl.count, color));
        }
        return json.toString();
    }

    /**
     * The pie's JSON, at most maxLength characters (the column's size; 0 = no limit): if longer, the smallest
     * slices are merged into Other until it fits, so the slices still add up. Same format as before
     * (HttpUtil.getJsonElement), read by overview-body.jsp.
     */
    public static String fit(java.util.List<Slice> in, int maxLength) {
        java.util.List<Slice> slices = new java.util.ArrayList<>();
        Slice other = null;
        for (Slice s : in) {
            if (OTHER.equals(s.subfamily) || s.subfamily == null) {
                if (other == null) other = new Slice(OTHER, 0);
                other.count += s.count;
            } else {
                slices.add(new Slice(s.subfamily, s.count));
            }
        }
        while (true) {
            java.util.List<Slice> all = new java.util.ArrayList<>(slices);
            if (other != null) all.add(other);
            String json = json(all);
            if (maxLength <= 0 || json.length() <= maxLength) return json;
            if (slices.isEmpty()) return "";       // not even one slice fits: no chart
            Slice smallest = slices.get(0);
            for (Slice s : slices) if (s.count < smallest.count) smallest = s;
            slices.remove(smallest);
            if (other == null) other = new Slice(OTHER, 0);
            other.count += smallest.count;
        }
    }

    /** The JSON of one pie (one place, parameter: its key), unlimited length. */
    public static String pieJson(Connection connection, String sql, String key) throws SQLException {
        return fit(slices(connection, sql, key), 0);
    }

    private static final java.util.Map<String, Integer> s_columnLength = new java.util.concurrent.ConcurrentHashMap<>();

    /** Characters the column holds (information_schema), 0 when unknown or very large. */
    static int columnLength(Connection connection, String table, String column) {
        return s_columnLength.computeIfAbsent(table + "." + column, k -> {
            try (PreparedStatement stmt = connection.prepareStatement("select character_maximum_length from information_schema.columns"
                    + " where table_schema = database() and table_name = ? and column_name = ?")) {
                stmt.setString(1, table);
                stmt.setString(2, column);
                try (ResultSet r = stmt.executeQuery()) {
                    if (!r.next()) return 0;
                    long n = r.getLong(1);
                    return n <= 0 || n > 1000000 ? 0 : (int) n;
                }
            } catch (SQLException e) {
                return 0;
            }
        });
    }

    /** Forgets the column sizes read (tests that change the schema). */
    public static void clearColumnLengths() { s_columnLength.clear(); }

    /**
     * Writes both pies of one place (nightly run, Recalculate, museum recount after an upload). A chart never
     * stops the work around it (2026-10-21: an upload rolled back with "Data too long for column
     * specimen_subfamily_dist_json"): the JSON is fitted to the column, and any error is logged and the old
     * charts are kept.
     */
    public static void writePies(Connection connection, String type, String key) {
        try {
            String table = placeTable(type);
            String species = fit(slices(connection, speciesPieSql(type), key), columnLength(connection, table, "taxon_subfamily_dist_json"));
            String specimens = fit(slices(connection, specimenPieSql(type), key), columnLength(connection, table, "specimen_subfamily_dist_json"));
            try (PreparedStatement stmt = connection.prepareStatement("update " + table
                    + " set taxon_subfamily_dist_json = ?, specimen_subfamily_dist_json = ? where " + placeKey(type) + " = ?")) {
                stmt.setString(1, species);
                stmt.setString(2, specimens);
                stmt.setString(3, key);
                stmt.executeUpdate();
            }
        } catch (SQLException | RuntimeException e) {
            s_log.error("writePies() " + type + " " + key + ": charts not written, the old ones are kept. e:" + e);
        }
    }

    /** Writes the Subfamilies, Genera and Species numbers of one place, counted from its list. */
    public static int[] writeListedCounts(Connection connection, String type, String key) throws SQLException {
        int[] counts = listedCounts(connection, type, key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set subfamily_count = ?, genus_count = ?, species_count = ? where " + placeKey(type) + " = ?")) {
            stmt.setInt(1, counts[0]);
            stmt.setInt(2, counts[1]);
            stmt.setInt(3, counts[2]);
            stmt.setString(4, key);
            stmt.executeUpdate();
        }
        return counts;
    }

    /** Writes the Valid species number of one place, counted from its list. */
    public static int writeValidSpecies(Connection connection, String type, String key) throws SQLException {
        int n = count(connection, validSpeciesSql(type), key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set valid_species_count = ? where " + placeKey(type) + " = ?")) {
            stmt.setInt(1, n);
            stmt.setString(2, key);
            stmt.executeUpdate();
        }
        return n;
    }

    /**
     * Endemic and Introduced numbers of every geolocale or bioregion from the flags of its list
     * (is_endemic, is_introduced), the rows endemic.do and introduced.do list (2026-10-21). field: endemic
     * or introduced. Before, the endemic loop added the first species of the next place to the previous
     * one (first place +1, last place -1), and regions and subregions got flags but no number.
     */
    public static String countsFromFlagsSql(String type, String field) {
        if (!"endemic".equals(field) && !"introduced".equals(field)) throw new IllegalArgumentException(field);
        if (!GEOLOCALE.equals(type) && !BIOREGION.equals(type)) throw new IllegalArgumentException(type);
        return "update " + placeTable(type) + " p set p." + field + "_species_count = (select count(*) from " + listTable(type) + " l"
            + " where l." + listKey(type) + " = p." + placeKey(type) + " and l.is_" + field + " = 1)";
    }

    public static int writeCountsFromFlags(Connection connection, String type, String field) throws SQLException {
        try (java.sql.Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(countsFromFlagsSql(type, field));
        }
    }

    /** Sum of the "value" fields of a pie chart's JSON (as written by HttpUtil.getJsonElement). */
    public static int pieSum(String json) {
        if (json == null) return 0;
        int sum = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"value\":\\s*(-?\\d+)").matcher(json);
        while (m.find()) sum += Integer.parseInt(m.group(1));
        return sum;
    }
}

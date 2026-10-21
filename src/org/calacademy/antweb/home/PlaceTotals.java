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
 *                           up to Specimens (a slice "(no subfamily)" when needed). Before 2026-10-21 museums and bioregions
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
        return "select t.subfamily subfamily, count(distinct l.taxon_name) count, null chart_color from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where l." + listKey(type) + " = ? and " + rankCondition(SPECIES)
            + " group by t.subfamily order by t.subfamily";
    }

    /**
     * "Specimens per Subfamily" pie of one place (parameter: its key): subfamily, count. Geolocales and
     * projects: the specimen counts of the species and subspecies rows of the list (as the Specimens
     * number); museums and bioregions: the specimens of the museum or bioregion.
     */
    public static String specimenPieSql(String type) {
        if (MUSEUM.equals(type) || BIOREGION.equals(type)) {
            String column = MUSEUM.equals(type) ? "museum" : "bioregion";
            return "select s.subfamily subfamily, count(*) count from specimen s where s." + column + " = ?"
                + " group by s.subfamily order by s.subfamily";
        }
        return "select t.subfamily subfamily, sum(l.specimen_count) count from " + listTable(type) + " l"
            + " join taxon t on t.taxon_name = l.taxon_name where l." + listKey(type) + " = ?"
            + " and t.taxarank in ('species', 'subspecies')"
            + " group by t.subfamily having sum(l.specimen_count) > 0 order by t.subfamily";
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

    /**
     * The JSON of one pie (one place, parameter: its key): one element per row of the query
     * (subfamily, count[, chart_color]), coloured by the subfamily (TaxonMgr). No row is left out.
     */
    public static String pieJson(Connection connection, String sql, String key) throws SQLException {
        StringBuilder json = new StringBuilder();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet r = stmt.executeQuery()) {
                int i = 0;
                while (r.next()) {
                    String subfamily = r.getString(1);
                    int count = r.getInt(2);
                    String color = null;
                    if (subfamily != null) {
                        try {
                            org.calacademy.antweb.Subfamily s = org.calacademy.antweb.util.TaxonMgr.getSubfamily(subfamily);
                            if (s != null) color = s.getChartColor();
                        } catch (RuntimeException e) {
                            // taxa not loaded (tests): the slice keeps NO_COLOR
                        }
                    }
                    if (color == null) color = NO_COLOR;
                    if (i++ > 0) json.append(",");
                    json.append(org.calacademy.antweb.util.HttpUtil.getJsonElement(i,
                        subfamily == null ? "(no subfamily)" : org.calacademy.antweb.Formatter.initCap(subfamily), count, color));
                }
            }
        }
        return json.toString();
    }

    /** Writes both pies of one place (nightly run, Recalculate). */
    public static void writePies(Connection connection, String type, String key) throws SQLException {
        String species = pieJson(connection, speciesPieSql(type), key);
        String specimens = pieJson(connection, specimenPieSql(type), key);
        try (PreparedStatement stmt = connection.prepareStatement("update " + placeTable(type)
                + " set taxon_subfamily_dist_json = ?, specimen_subfamily_dist_json = ? where " + placeKey(type) + " = ?")) {
            stmt.setString(1, species);
            stmt.setString(2, specimens);
            stmt.setString(3, key);
            stmt.executeUpdate();
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

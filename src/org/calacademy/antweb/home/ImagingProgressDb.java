package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Imaging progress (Brian Fisher, 2026-11-08; page imagingProgress.do, ImagingProgressAction): how many valid living ant
 * species and subspecies are imaged, for all of AntWeb and by bioregion, country, genus and museum, and which can be
 * imaged now because AntWeb has specimens of them. The imaging labs' work queue.
 *
 * The rule of "imaged" is the one of the home page and the lists (StatisticsDb.ANT_IMAGE, docs/numbers.md Q6, Q12): a
 * name is imaged when a specimen filed under it, anywhere in AntWeb, has a photo of the ant; label photos never count,
 * nor do photos of red-flagged specimens (Brian Fisher, 2026-11-09; SpecimenDb.notRedFlagged). A red-flagged specimen
 * is still a specimen: it counts for "unimaged, with specimens" (it can be fixed and unflagged, or imaged again).
 *
 * Table imaging_progress, one row per place, filled once a day by the daily update (set 4, after the statistics row;
 * its own step "imagingProgress" on dailyUpdate.do; admins: utilData.do?action=imagingProgress), one grouped query per
 * kind of place:
 *
 *   place_type  all        place_key living (valid living species and subspecies, the headline), living-species
 *                          (species only: equals statistics.living_species and living_species_imaged, the home page),
 *                          fossil (valid fossil species and subspecies)
 *               bioregion  key the bioregion name; the valid living species and subspecies of its list (the Valid view)
 *               country    key the geolocale id; the same of the country's list
 *               genus      key subfamily + genus (the genus taxon_name); the valid living species and subspecies of it
 *               museum     key the museum code; the valid living species and subspecies with a specimen there
 *   total                    the names counted (above)
 *   imaged                   of them, those with a photo of the ant anywhere in AntWeb (not of a red-flagged specimen)
 *   imaged_here              of them, those with a photo of the ant of a specimen collected in this place (bioregion:
 *                            specimen.bioregion; country: specimen.country, or island_country for island countries, as
 *                            the place's Images, PlaceImageCountDb; museum: a specimen of this museum). Null for all and genus.
 *   unimaged_with_specimens  not imaged, with at least one specimen record anywhere in AntWeb (can be imaged now)
 *   unimaged_casc            of those, with a specimen of museum CASC
 *
 * imaged_here <= imaged <= total, checked every morning by the count check (CountInvariants id imaging.progress,
 * which also recounts a sample of rows with a query of its own). The unimaged list (CSV and genus view) is read live
 * (unimaged()), once per day's fill. Record and undo: db/upgrade/9.4.0/2026-11-08-imaging-progress.sql. Docs:
 * docs/numbers.md rows 124 to 128, docs/data-model.md. Tests: test/.../home/ImagingProgressDbTest.java,
 * tests/e2e/test_imaging_progress.py.
 */
public class ImagingProgressDb {

    private static final Log s_log = LogFactory.getLog(ImagingProgressDb.class);

    public static final String TABLE = "imaging_progress";
    /** Written to compute.log by the daily update. */
    public static final String LOG = "Imaging progress counted";

    public static final String ALL = "all";
    public static final String BIOREGION = "bioregion";
    public static final String COUNTRY = "country";
    public static final String GENUS = "genus";
    public static final String MUSEUM = "museum";
    public static final String[] KINDS = {ALL, BIOREGION, COUNTRY, GENUS, MUSEUM};

    /** Keys of the rows of kind all. */
    public static final String LIVING = "living";
    public static final String LIVING_SPECIES = "living-species";
    public static final String FOSSIL = "fossil";

    /** The museum the labs image first (CAS, San Francisco; the MBC lab images CAS specimens too). */
    public static final String CASC = "CASC";

    /** The day the "imaged" rule last changed: statistics rows before it are not comparable (trend). 2026-11-09: photos
     *  of red-flagged specimens no longer count (was 2026-10-28). */
    public static final String RULE_SINCE = "2026-11-09";

    private final Connection connection;

    public ImagingProgressDb(Connection connection) {
        this.connection = connection;
    }

    // ------------------------------------------------------------------------------------------- table

    public static String createTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + " place_type varchar(16) NOT NULL,"
            + " place_key varchar(128) NOT NULL,"
            + " place_name varchar(250) NULL,"
            + " parent_name varchar(128) NULL,"
            + " total int NOT NULL DEFAULT 0,"
            + " imaged int NOT NULL DEFAULT 0,"
            + " imaged_here int NULL,"
            + " unimaged_with_specimens int NOT NULL DEFAULT 0,"
            + " unimaged_casc int NOT NULL DEFAULT 0,"
            + " computed datetime NULL,"
            + " PRIMARY KEY (place_type, place_key)"
            + ") ENGINE=InnoDB" + PlaceGenusCurationDb.defaults(charset, collation);
    }

    private static volatile boolean s_schemaChecked = false;

    /** Creates the table when missing (only then: CREATE TABLE commits the caller's transaction), in taxon_name's collation. */
    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        if (LicenseUpdateDb.tableExists(connection, TABLE)) {
            s_schemaChecked = true;
            return;
        }
        String charset = null;
        String collation = null;
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery("select character_set_name, collation_name from information_schema.columns"
                    + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'")) {
                if (r.next()) { charset = r.getString(1); collation = r.getString(2); }
            }
            stmt.executeUpdate(createTable(charset, collation));
        }
        s_schemaChecked = true;
    }

    /** Whether the table exists (a read; the page and the count check never create it). */
    public boolean tableExists() throws SQLException {
        return LicenseUpdateDb.tableExists(connection, TABLE);
    }

    // ------------------------------------------------------------------------------------------- the rules

    /** A valid living ant species or subspecies identified to genus (alias t): the headline's names. */
    public static String living(String t) {
        return "(" + PlaceTotals.validAntTaxonCondition(t, "species", 0) + " or " + PlaceTotals.validAntTaxonCondition(t, "subspecies", 0) + ")";
    }

    /** A valid fossil ant species or subspecies identified to genus (alias t). */
    public static String fossil(String t) {
        return "(" + PlaceTotals.validAntTaxonCondition(t, "species", 1) + " or " + PlaceTotals.validAntTaxonCondition(t, "subspecies", 1) + ")";
    }

    /** The names with a photo of the ant anywhere in AntWeb, each once (alias x, column taxon_name). */
    // "is not null": the list query reads it with NOT IN, and one null name in it would empty the whole list (SQL's
    // NOT IN with a null; review 2026-11-08: one imaged specimen without a name took the list from 4,320 names to 0).
    static final String IMAGED_NAMES = "(select distinct s.taxon_name from image i join specimen s on s.code = i.image_of_id where "
        + StatisticsDb.ANT_IMAGE + " and " + SpecimenDb.notRedFlagged("s") + " and s.taxon_name is not null)";

    /** Specimens per name and those of CASC (alias sp: taxon_name, n, casc). */
    static final String SPECIMENS = "(select s.taxon_name, count(*) n, sum(s.museum = '" + CASC + "') casc from specimen s"
        + " where s.taxon_name is not null group by s.taxon_name)";

    /** (place key k, name) of the specimens with a photo of the ant collected in each place of the kind, each pair once. */
    static String hereSql(String kind) {
        // photos of red-flagged specimens do not count (2026-11-09), as IMAGED_NAMES
        String counted = StatisticsDb.ANT_IMAGE + " and " + SpecimenDb.notRedFlagged("s");
        String imaged = " from image i join specimen s on s.code = i.image_of_id where " + counted;
        switch (kind) {
            case BIOREGION: return "select distinct s.bioregion k, s.taxon_name" + imaged + " and s.bioregion is not null";
            case MUSEUM: return "select distinct s.museum k, s.taxon_name" + imaged + " and s.museum is not null";
            case COUNTRY:
                // as the country's Images (PlaceImageCountDb): island countries by specimen.island_country
                String g = " join geolocale g on g.georank = 'country' and g.is_valid = 1 and ";
                return "select g.id k, s.taxon_name from image i join specimen s on s.code = i.image_of_id" + g + "coalesce(g.is_island, 0) = 0 and g.name = s.country"
                    + " where " + counted
                    + " union select g.id k, s.taxon_name from image i join specimen s on s.code = i.image_of_id" + g + "coalesce(g.is_island, 0) = 1 and g.name = s.island_country"
                    + " where " + counted;
            default: return null;
        }
    }

    /** (k, taxon_name, extra columns) of the names counted for each place of the kind, each pair once. */
    static String namesSql(String kind) {
        switch (kind) {
            case ALL:
                return "select '" + LIVING + "' k, t.taxon_name from taxon t where " + living("t")
                    + " union all select '" + LIVING_SPECIES + "' k, t.taxon_name from taxon t where " + StatisticsDb.validAntSpeciesOnly("t", 0)
                    + " union all select '" + FOSSIL + "' k, t.taxon_name from taxon t where " + fossil("t");
            case BIOREGION:
                // the Valid view of the list (PlaceTotals.validSpeciesByPlaceSql: the place's Valid species)
                return "select distinct l.bioregion_name k, l.taxon_name from bioregion_taxon l join taxon t on t.taxon_name = l.taxon_name"
                    + " where " + PlaceTotals.VALID_CONDITION;
            case COUNTRY:
                return "select distinct g.id k, l.taxon_name, g.name pname, " + (s_geolocaleBioregion ? "g.bioregion" : "null") + " parent"
                    + " from geolocale g join geolocale_taxon l on l.geolocale_id = g.id"
                    + " join taxon t on t.taxon_name = l.taxon_name where g.georank = 'country' and g.is_valid = 1 and " + PlaceTotals.VALID_CONDITION;
            case GENUS:
                return "select concat(coalesce(t.subfamily, ''), t.genus) k, t.taxon_name, t.genus pname, t.subfamily parent from taxon t"
                    + " where " + living("t") + " and coalesce(t.genus, '') <> ''";
            case MUSEUM:
                return "select distinct s.museum k, s.taxon_name from specimen s join taxon t on t.taxon_name = s.taxon_name"
                    + " where " + living("t") + " and coalesce(s.museum, '') <> ''";
            default: throw new IllegalArgumentException(kind);
        }
    }

    /** Whether geolocale has its bioregion column (the live table has; small test tables may not): the country's parent. */
    private static volatile boolean s_geolocaleBioregion = true;

    private void checkColumns() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("select 1 from information_schema.columns where table_schema = database()"
                + " and table_name = 'geolocale' and column_name = 'bioregion'"); ResultSet r = stmt.executeQuery()) {
            s_geolocaleBioregion = r.next();
        }
    }

    static boolean hasNameColumns(String kind) {
        return COUNTRY.equals(kind) || GENUS.equals(kind);
    }

    /**
     * One grouped query for every place of the kind: k, [place name, parent], total, imaged, imaged here (null where the
     * kind has none), unimaged with specimens, unimaged with a CASC specimen.
     */
    public static String progressSql(String kind) {
        String here = hereSql(kind);
        String extra = hasNameColumns(kind) ? ", L.pname, L.parent" : "";
        return "select L.k" + extra + ", count(*) total, sum(x.taxon_name is not null) imaged, "
            + (here == null ? "null" : "sum(h.taxon_name is not null)") + " here,"
            + " sum(x.taxon_name is null and sp.taxon_name is not null) unimaged_spec, sum(x.taxon_name is null and sp.casc > 0) unimaged_casc"
            + " from (" + namesSql(kind) + ") L"
            + " left join " + IMAGED_NAMES + " x on x.taxon_name = L.taxon_name"
            + " left join " + SPECIMENS + " sp on sp.taxon_name = L.taxon_name"
            + (here == null ? "" : " left join (" + here + ") h on h.k = L.k and h.taxon_name = L.taxon_name")
            + " group by L.k" + extra;
    }

    // ------------------------------------------------------------------------------------------- rows

    /** One row of the table. */
    public static final class Row {
        public String type;
        public String key;
        public String name;
        public String parent;
        public int total;
        public int imaged;
        /** -1 when the kind has none (all, genus). */
        public int imagedHere = -1;
        public int unimagedWithSpecimens;
        public int unimagedCasc;
        public Timestamp computed;

        public int unimaged() { return total - imaged; }

        /** Imaged share in percent, one decimal ("12.3%"), "" when total is 0. */
        public String percent() { return percent(imaged, total); }

        public static String percent(long part, long whole) {
            if (whole <= 0) return "";
            return String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
        }

        /** The name shown: the place's name, the genus capitalised. */
        public String label() {
            String n = name == null || name.isEmpty() ? key : name;
            if (GENUS.equals(type) && n != null && !n.isEmpty()) n = n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
            return n;
        }

        public String toString() {
            return type + " " + key + " total:" + total + " imaged:" + imaged + " here:" + imagedHere + " unimagedWithSpecimens:"
                + unimagedWithSpecimens + " casc:" + unimagedCasc;
        }
    }

    /** Counts every place of one kind (nothing written). */
    public List<Row> count(String kind) throws SQLException {
        List<Row> out = new ArrayList<>();
        boolean names = hasNameColumns(kind);
        if (COUNTRY.equals(kind)) checkColumns();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(progressSql(kind))) {
            while (r.next()) {
                Row row = new Row();
                int c = 1;
                row.type = kind;
                row.key = r.getString(c++);
                if (row.key == null) continue;
                if (names) {
                    row.name = r.getString(c++);
                    row.parent = r.getString(c++);
                } else {
                    row.name = row.key;
                }
                row.total = r.getInt(c++);
                row.imaged = r.getInt(c++);
                int here = r.getInt(c++);
                row.imagedHere = r.wasNull() ? -1 : here;
                row.unimagedWithSpecimens = r.getInt(c++);
                row.unimagedCasc = r.getInt(c);
                out.add(row);
            }
        }
        return out;
    }

    /**
     * The daily fill: every kind counted, then the table replaced in one transaction (readers see yesterday's rows until
     * the commit). Returns the compute.log line. A kind that fails is named; the others are still written.
     */
    public String fill() throws SQLException {
        ensureSchema();
        long start = System.currentTimeMillis();
        Map<String, List<Row>> rows = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (String kind : KINDS) {
            long t0 = System.currentTimeMillis();
            try {
                rows.put(kind, count(kind));
                s_log.info("fill() " + kind + " " + rows.get(kind).size() + " rows in " + (System.currentTimeMillis() - t0) + " ms");
            } catch (SQLException e) {
                failed.add(kind + " (" + e.getMessage() + ")");
                s_log.warn("fill() " + kind + " e:" + e);
            }
        }
        if (rows.isEmpty()) return LOG + ": FAILED, " + String.join(", ", failed);
        boolean auto = connection.getAutoCommit();
        try {
            if (auto) connection.setAutoCommit(false);
            try (PreparedStatement del = connection.prepareStatement("delete from " + TABLE + " where place_type = ?")) {
                for (String kind : rows.keySet()) { del.setString(1, kind); del.addBatch(); }
                del.executeBatch();
            }
            try (PreparedStatement ins = connection.prepareStatement("insert into " + TABLE + " (place_type, place_key, place_name, parent_name,"
                    + " total, imaged, imaged_here, unimaged_with_specimens, unimaged_casc, computed) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())")) {
                for (List<Row> list : rows.values()) {
                    for (Row row : list) {
                        ins.setString(1, row.type);
                        ins.setString(2, row.key);
                        ins.setString(3, cut(row.name, 250));
                        ins.setString(4, cut(row.parent, 128));
                        ins.setInt(5, row.total);
                        ins.setInt(6, row.imaged);
                        if (row.imagedHere < 0) ins.setNull(7, java.sql.Types.INTEGER); else ins.setInt(7, row.imagedHere);
                        ins.setInt(8, row.unimagedWithSpecimens);
                        ins.setInt(9, row.unimagedCasc);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException e2) { s_log.warn("fill() rollback e:" + e2); }
            throw e;
        } finally {
            if (auto) connection.setAutoCommit(true);
        }
        StringBuilder b = new StringBuilder(LOG + ": ");
        Row headline = null;
        for (Row row : rows.getOrDefault(ALL, Collections.emptyList())) if (LIVING.equals(row.key)) headline = row;
        if (headline != null) b.append(headline.imaged).append(" of ").append(headline.total).append(" valid living species and subspecies imaged; ");
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<Row>> e : rows.entrySet()) if (!ALL.equals(e.getKey())) parts.add(e.getValue().size() + " " + e.getKey() + " rows");
        b.append(String.join(", ", parts)).append(" in ").append((System.currentTimeMillis() - start) / 1000).append(" s");
        if (!failed.isEmpty()) b.append("; FAILED for ").append(String.join(", ", failed));
        s_unimaged = null;   // the list is read again for the new day
        return b.toString();
    }

    private static String cut(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    /** The stored rows of one kind, in the order the page shows them first (most unimaged first; all: living first). */
    public List<Row> read(String kind) throws SQLException {
        List<Row> out = new ArrayList<>();
        if (!tableExists()) return out;
        String order = ALL.equals(kind) ? "field(place_key, '" + LIVING + "', '" + LIVING_SPECIES + "', '" + FOSSIL + "')"
            : MUSEUM.equals(kind) ? "place_key = '" + CASC + "' desc, unimaged_with_specimens desc, place_key"
            : "total - imaged desc, place_name, place_key";
        try (PreparedStatement stmt = connection.prepareStatement("select place_type, place_key, place_name, parent_name, total, imaged, imaged_here,"
                + " unimaged_with_specimens, unimaged_casc, computed from " + TABLE + " where place_type = ? order by " + order)) {
            stmt.setString(1, kind);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    Row row = new Row();
                    row.type = r.getString(1);
                    row.key = r.getString(2);
                    row.name = r.getString(3);
                    row.parent = r.getString(4);
                    row.total = r.getInt(5);
                    row.imaged = r.getInt(6);
                    int here = r.getInt(7);
                    row.imagedHere = r.wasNull() ? -1 : here;
                    row.unimagedWithSpecimens = r.getInt(8);
                    row.unimagedCasc = r.getInt(9);
                    row.computed = r.getTimestamp(10);
                    out.add(row);
                }
            }
        }
        return out;
    }

    /** One stored row, or null. */
    public Row read(String kind, String key) throws SQLException {
        for (Row row : read(kind)) if (row.key.equalsIgnoreCase(key)) return row;
        return null;
    }

    // ------------------------------------------------------------------------------------------- the unimaged list

    /** One valid living species or subspecies with no photo of the ant: the CSV row and the genus view's row. */
    public static final class Unimaged {
        public String taxonName;
        public String subfamily;
        public String genus;
        public String species;
        public String rank;
        public int specimens;
        public int cascSpecimens;
        /** Museum codes of its specimens, space separated, in alphabetical order. */
        public String museums = "";

        /** "Pheidole megacephala", subspecies with their third name: the genus, then the words of the name after the genus part. */
        public String displayName() {
            StringBuilder b = new StringBuilder();
            if (genus != null && !genus.isEmpty()) b.append(genus.substring(0, 1).toUpperCase(Locale.ROOT)).append(genus.substring(1));
            if (taxonName != null && taxonName.indexOf(' ') > 0) b.append(taxonName.substring(taxonName.indexOf(' ')));
            else if (species != null && !species.isEmpty()) b.append(' ').append(species);
            return b.length() == 0 ? taxonName : b.toString();
        }

        /** The genus key (subfamily + genus), as imaging_progress rows of kind genus. */
        public String genusKey() {
            return (subfamily == null ? "" : subfamily) + (genus == null ? "" : genus);
        }
    }

    /**
     * Every valid living ant species and subspecies with no photo of the ant (the rule of the table), with its specimen
     * count, CASC specimens and museums; CASC specimens first, then most specimens, then name: the labs' work queue.
     */
    public static final String UNIMAGED_SQL = "select t.taxon_name, t.subfamily, t.genus, t.species, t.taxarank,"
        + " count(s.code) specimens, coalesce(sum(s.museum = '" + CASC + "'), 0) casc,"
        + " coalesce(group_concat(distinct s.museum order by s.museum separator ' '), '') museums"
        + " from taxon t left join specimen s on s.taxon_name = t.taxon_name"
        + " where " + living("t") + " and t.taxon_name not in " + IMAGED_NAMES
        + " group by t.taxon_name, t.subfamily, t.genus, t.species, t.taxarank"
        + " order by casc desc, specimens desc, t.taxon_name";

    /** The list read for the current fill (the stored computed time of the headline row); null: read it again. */
    private static volatile Object[] s_unimaged = null;
    private static final Object UNIMAGED_LOCK = new Object();

    /** The unimaged list as of the last fill, read once per fill (the page and its CSV share it). */
    public List<Unimaged> unimaged() throws SQLException {
        Row headline = read(ALL, LIVING);
        Timestamp at = headline == null ? null : headline.computed;
        Object[] kept = s_unimaged;
        if (kept != null && java.util.Objects.equals(kept[0], at)) {
            @SuppressWarnings("unchecked") List<Unimaged> list = (List<Unimaged>) kept[1];
            return list;
        }
        synchronized (UNIMAGED_LOCK) {
            kept = s_unimaged;
            if (kept != null && java.util.Objects.equals(kept[0], at)) {
                @SuppressWarnings("unchecked") List<Unimaged> list = (List<Unimaged>) kept[1];
                return list;
            }
            List<Unimaged> list = Collections.unmodifiableList(readUnimaged());
            s_unimaged = new Object[] {at, list};
            return list;
        }
    }

    /** The unimaged list, read now. */
    public List<Unimaged> readUnimaged() throws SQLException {
        List<Unimaged> out = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            try { stmt.execute("set session group_concat_max_len = 65536"); } catch (SQLException e) { s_log.debug("group_concat_max_len e:" + e); }
            try (ResultSet r = stmt.executeQuery(UNIMAGED_SQL)) {
                while (r.next()) {
                    Unimaged u = new Unimaged();
                    u.taxonName = r.getString(1);
                    u.subfamily = r.getString(2);
                    u.genus = r.getString(3);
                    u.species = r.getString(4);
                    u.rank = r.getString(5);
                    u.specimens = r.getInt(6);
                    u.cascSpecimens = r.getInt(7);
                    u.museums = r.getString(8) == null ? "" : r.getString(8);
                    out.add(u);
                }
            }
        }
        return out;
    }

    /** The CSV of the list: a header line, then one line per name (RFC 4180 quoting). */
    public static String csv(List<Unimaged> list) {
        StringBuilder b = new StringBuilder("taxon_name,name,rank,subfamily,genus,specimens,casc_specimens,museums\r\n");
        for (Unimaged u : list) {
            b.append(q(u.taxonName)).append(',').append(q(u.displayName())).append(',').append(q(u.rank)).append(',')
                .append(q(u.subfamily)).append(',').append(q(u.genus)).append(',').append(u.specimens).append(',')
                .append(u.cascSpecimens).append(',').append(q(u.museums)).append("\r\n");
        }
        return b.toString();
    }

    /** One CSV field: quoted when it holds a comma, quote or line break; a leading = + - @ is prefixed with ' (spreadsheet formulas). */
    static String q(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ------------------------------------------------------------------------------------------- trend

    /** One day of the trend: valid species and subspecies imaged (fossils included) and their total; living species imaged. */
    public static final class Day {
        public String date;
        public int validImaged;
        public int validTotal;
        /** -1 before 2026-11-06 (the column was not written). */
        public int livingSpeciesImaged = -1;
        public int livingSpecies = -1;
    }

    /** The last statistics row of each day since RULE_SINCE, oldest first. */
    public List<Day> trend() throws SQLException {
        List<Day> out = new ArrayList<>();
        if (!LicenseUpdateDb.tableExists(connection, "statistics")) return out;
        boolean home = StatisticsDb.homeColumnsExist(connection);
        String cols = "date(s.created) d, s.valid_species_imaged, s.extant_taxa" + (home ? ", s.living_species_imaged, s.living_species" : "");
        String sql = "select " + cols + " from statistics s join (select max(id) id from statistics where created >= '" + RULE_SINCE + "'"
            + " and valid_species_imaged is not null group by date(created)) m on m.id = s.id order by s.created";
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(sql)) {
            while (r.next()) {
                Day d = new Day();
                d.date = r.getString(1);
                d.validImaged = r.getInt(2);
                d.validTotal = r.getInt(3);
                if (home) {
                    int v = r.getInt(4);
                    if (!r.wasNull()) d.livingSpeciesImaged = v;
                    v = r.getInt(5);
                    if (!r.wasNull()) d.livingSpecies = v;
                }
                out.add(d);
            }
        }
        return out;
    }
}

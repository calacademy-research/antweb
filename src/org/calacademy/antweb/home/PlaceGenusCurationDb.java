package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.calacademy.antweb.ListView;
import org.calacademy.antweb.Museum;
import org.calacademy.antweb.Overview;
import org.calacademy.antweb.geolocale.Adm1;
import org.calacademy.antweb.geolocale.Bioregion;
import org.calacademy.antweb.geolocale.Country;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Not curated column of genus lists, counted once a day (Brian Fisher, 2026-11-02, 19:44; design rules "Not curated
 * column"). Replaces the Other specimens column, which read every specimen of the place on every page (1.4 to 2.2 s
 * more per genus list on staging).
 *
 * Two tables, filled together by nightly set 4 and by the admin action utilData.do?action=placeGenusCuration
 * (fillAll; name=country, adm1, museum or bioregion fills one kind), each kind replaced in one transaction:
 *
 *   place_specimen_name   the not red-flagged ant specimens collected in a place, by name (list_type, list_key,
 *                         taxon_name, specimens). The genus lists read it instead of the specimen table (ListView:
 *                         the names the views Curated and All specimen names add on countries and adm1s, the genus
 *                         rows that are only in All specimen names, the specimen source icon), one indexed read per page.
 *   place_genus_curation  per place and genus (the genus row's taxon_name, subfamily + genus): the specimens of the
 *                         genus collected there (specimens), those whose name is not curated (not_curated: not a valid
 *                         name and not a curated morphospecies, ListView.notCuratedCondition), and its parts: uncurated
 *                         morphospecies (morpho_names names, morpho_specimens specimens), specimens identified only to
 *                         genus (indet_specimens: indetermined, unidentifiable, "(indet)"), the rest (other_specimens:
 *                         unrecognized names). The genus row's Not curated cell and the header of the view Not curated.
 *
 * Places (list_type, list_key) select specimens as their pages do (Overview.getOverviewCriteria): country (country
 * or island_country = the name), adm1 (specimen.adm1 = the name, as the adm1 pages select them), museum (code),
 * bioregion (name). Regions, subregions and projects have no column. Set-based: one grouped query per kind over the
 * specimen table, then one grouped query from place_specimen_name; never one query per place.
 *
 * The page never fails on these tables: missing table or no rows, the cell is empty (read returns null).
 * Record and undo: db/upgrade/8.115/2026-11-02-place-genus-curation.sql. Tests: home.PlaceGenusCurationDbTest.
 */
public class PlaceGenusCurationDb {

    private static final Log s_log = LogFactory.getLog(PlaceGenusCurationDb.class);

    public static final String COUNTRY = "country";
    public static final String ADM1 = "adm1";
    public static final String MUSEUM = "museum";
    public static final String BIOREGION = "bioregion";
    public static final String[] TYPES = {COUNTRY, ADM1, MUSEUM, BIOREGION};

    /** Written to compute.log by the fill (the e2e tests and the admin read it). */
    public static final String LOG = "Not curated counted";

    private final Connection connection;

    public PlaceGenusCurationDb(Connection connection) {
        this.connection = connection;
    }

    /** The list_type and list_key of a place with the column, or null (regions, subregions, projects, all of AntWeb). */
    public static String[] typeAndKey(Overview overview) {
        if (overview instanceof Country) return new String[] {COUNTRY, overview.getName()};
        if (overview instanceof Adm1) return new String[] {ADM1, overview.getName()};
        if (overview instanceof Museum) return new String[] {MUSEUM, ((Museum) overview).getCode()};
        if (overview instanceof Bioregion) return new String[] {BIOREGION, overview.getName()};
        return null;
    }

    // ------------------------------------------------------------------ tables

    static String defaults(String charset, String collation) {
        String d = "";
        if (charset != null && charset.matches("[a-z0-9_]+")) d += " DEFAULT CHARSET=" + charset;
        if (collation != null && collation.matches("[a-z0-9_]+")) d += " COLLATE=" + collation;
        return d;
    }

    public static String createGenusTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS place_genus_curation ("
            + " list_type varchar(16) NOT NULL,"
            + " list_key varchar(128) NOT NULL,"
            + " taxon_name varchar(128) NOT NULL,"
            + " subfamily varchar(64) NULL,"
            + " genus varchar(64) NULL,"
            + " specimens int NOT NULL DEFAULT 0,"
            + " not_curated int NOT NULL DEFAULT 0,"
            + " morpho_names int NOT NULL DEFAULT 0,"
            + " morpho_specimens int NOT NULL DEFAULT 0,"
            + " indet_specimens int NOT NULL DEFAULT 0,"
            + " other_specimens int NOT NULL DEFAULT 0,"
            + " counted datetime NULL,"
            + " PRIMARY KEY (list_type, list_key, taxon_name)"
            + ") ENGINE=InnoDB" + defaults(charset, collation);
    }

    public static String createNameTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS place_specimen_name ("
            + " list_type varchar(16) NOT NULL,"
            + " list_key varchar(128) NOT NULL,"
            + " taxon_name varchar(128) NOT NULL,"
            + " specimens int NOT NULL DEFAULT 0,"
            + " counted datetime NULL,"
            + " PRIMARY KEY (list_type, list_key, taxon_name)"
            + ") ENGINE=InnoDB" + defaults(charset, collation);
    }

    private static volatile boolean s_schemaChecked = false;

    /** Creates both tables when missing, in the character set and collation of taxon.taxon_name. */
    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        String charset = null;
        String collation = null;
        String query = "select character_set_name, collation_name from information_schema.columns"
            + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'";
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rset = stmt.executeQuery(query)) {
                if (rset.next()) {
                    charset = rset.getString(1);
                    collation = rset.getString(2);
                }
            }
            stmt.executeUpdate(createGenusTable(charset, collation));
            stmt.executeUpdate(createNameTable(charset, collation));
        }
        s_schemaChecked = true;
    }

    // ------------------------------------------------------------------ the nightly fill

    /** The specimens of the places of a kind: (place, taxon_name) rows, not red-flagged, ants (alias x). */
    static String placeSource(String type) {
        String base = " from specimen where" + SpecimenDb.getAntSpecimenCriteria("specimen") + " and " + SpecimenDb.notRedFlagged("specimen");
        switch (type) {
            case COUNTRY:
                // country = X or island_country = X, each specimen once (as Overview.getOverviewCriteria selects it)
                return "select specimen.country place, specimen.taxon_name" + base + " and coalesce(specimen.country, '') <> ''"
                    + " union all select specimen.island_country place, specimen.taxon_name" + base
                    + " and coalesce(specimen.island_country, '') <> '' and specimen.island_country <> coalesce(specimen.country, '')";
            case ADM1:
                return "select specimen.adm1 place, specimen.taxon_name" + base + " and coalesce(specimen.adm1, '') <> ''";
            case MUSEUM:
                return "select specimen.museum place, specimen.taxon_name" + base + " and coalesce(specimen.museum, '') <> ''";
            case BIOREGION:
                return "select specimen.bioregion place, specimen.taxon_name" + base + " and coalesce(specimen.bioregion, '') <> ''";
            default:
                throw new IllegalArgumentException("type:" + type);
        }
    }

    /** Fills place_specimen_name of one kind (parameter: none; the kind is a literal). */
    public static String fillNamesSql(String type) {
        return "insert into place_specimen_name (list_type, list_key, taxon_name, specimens, counted)"
            + " select '" + type + "', left(x.place, 128), x.taxon_name, count(*), now() from (" + placeSource(type) + ") x"
            + " join taxon t on t.taxon_name = x.taxon_name where t.family = 'formicidae'"
            + " group by left(x.place, 128), x.taxon_name"
            // two spellings the key's collation holds equal ("Sao Tome", "São Tomé"; or two places cut to the same 128
            // characters) add up instead of failing the whole kind (review of #316)
            + " on duplicate key update specimens = specimens + values(specimens)";
    }

    /** The statuses of ListView's counted names (StatusSet.getCountables). */
    static final String COUNTED = "('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";

    /** Fills place_genus_curation of one kind from its place_specimen_name rows. */
    public static String fillGenusSql(String type) {
        String nc = ListView.notCuratedCondition("t");
        String morpho = "(t.status = 'morphotaxon' and " + nc + ")";
        String indet = "(" + nc + " and (t.status in " + PlaceTotals.GENUS_ONLY_STATUSES + " or coalesce(t.species, '') = '(indet)'))";
        return "insert into place_genus_curation (list_type, list_key, taxon_name, subfamily, genus, specimens, not_curated,"
            + " morpho_names, morpho_specimens, indet_specimens, other_specimens, counted)"
            + " select p.list_type, p.list_key, concat(t.subfamily, t.genus), t.subfamily, t.genus, sum(p.specimens),"
            + " sum(case when " + nc + " then p.specimens else 0 end),"
            + " count(distinct case when " + morpho + " then t.taxon_name end),"
            + " sum(case when " + morpho + " then p.specimens else 0 end),"
            + " sum(case when " + indet + " and not " + morpho + " then p.specimens else 0 end),"
            + " sum(case when " + nc + " and not " + morpho + " and not " + indet + " then p.specimens else 0 end),"
            + " now()"
            + " from place_specimen_name p join taxon t on t.taxon_name = p.taxon_name"
            + " where p.list_type = '" + type + "' and t.taxarank in ('genus', 'species', 'subspecies') and t.status in " + COUNTED
            + " and coalesce(t.genus, '') <> '' and coalesce(t.subfamily, '') <> '' and " + PlaceTotals.identifiedToGenus("t")
            + " group by p.list_type, p.list_key, t.subfamily, t.genus";
    }

    /** Replaces one kind's rows of both tables in one transaction; returns the places and genus rows written. */
    public int[] fill(String type) throws SQLException {
        if (java.util.Arrays.asList(TYPES).indexOf(type) < 0) throw new IllegalArgumentException("type:" + type);
        ensureSchema();
        boolean auto = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement d1 = connection.prepareStatement("delete from place_specimen_name where list_type = ?");
                 PreparedStatement d2 = connection.prepareStatement("delete from place_genus_curation where list_type = ?");
                 Statement s = connection.createStatement()) {
                d1.setString(1, type);
                d1.executeUpdate();
                d2.setString(1, type);
                d2.executeUpdate();
                s.executeUpdate(fillNamesSql(type));
                int genera = s.executeUpdate(fillGenusSql(type));
                int places = 0;
                try (ResultSet r = s.executeQuery("select count(distinct list_key) from place_genus_curation where list_type = '" + type + "'")) {
                    if (r.next()) places = r.getInt(1);
                }
                connection.commit();
                return new int[] {places, genera};
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    /** Every kind (nightly set 4); "Not curated counted: country 230 places, 4,512 genus rows in 12.3 s; ...". */
    public String fillAll() throws SQLException {
        StringBuilder b = new StringBuilder(LOG + ":");
        for (String type : TYPES) {
            long start = System.currentTimeMillis();
            int[] n = fill(type);
            b.append(" ").append(type).append(" ").append(n[0]).append(" places, ").append(String.format(Locale.US, "%,d", n[1]))
             .append(" genus rows in ").append(String.format(Locale.US, "%.1f", (System.currentTimeMillis() - start) / 1000.0)).append(" s;");
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ the page

    /** One genus row of a place. */
    public static final class Row {
        public String taxonName;
        public String subfamily;
        public String genus;
        public int specimens;
        public int notCurated;
        public int morphoNames;
        public int morphoSpecimens;
        public int indetSpecimens;
        public int otherSpecimens;
        public java.sql.Timestamp counted;

        /** "3,392 specimens not curated: 298 morphospecies (2,820 specimens), 572 identified only to genus" (and other names). */
        public String breakdown() {
            return breakdownText(notCurated, morphoNames, morphoSpecimens, indetSpecimens, otherSpecimens);
        }
    }

    static String n(int i) {
        return String.format(Locale.US, "%,d", i);
    }

    /**
     * The header of the view Not curated (Brian Fisher, 2026-11-02): "3,392 specimens not curated: 298 morphospecies
     * (2,820 specimens), 572 identified only to genus", with ", 10 under other names" when some are neither.
     */
    public static String breakdownText(int notCurated, int morphoNames, int morphoSpecimens, int indet, int other) {
        StringBuilder b = new StringBuilder(n(notCurated)).append(notCurated == 1 ? " specimen" : " specimens").append(" not curated");
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (morphoNames > 0) parts.add(n(morphoNames) + " morphospecies (" + n(morphoSpecimens)
            + (morphoSpecimens == 1 ? " specimen)" : " specimens)"));
        if (indet > 0) parts.add(n(indet) + " identified only to genus");
        if (other > 0) parts.add(n(other) + " under other names (not in AntCat)");
        if (!parts.isEmpty()) b.append(": ").append(String.join(", ", parts));
        return b.toString();
    }

    /** The key of a genus row: the same as ListView.GenusRows (subfamily and genus, lower case). */
    public static String key(String subfamily, String genus) {
        return (subfamily == null ? "" : subfamily.toLowerCase(Locale.ROOT)) + "|" + (genus == null ? "" : genus.toLowerCase(Locale.ROOT));
    }

    public static final String READ_SQL = "select taxon_name, subfamily, genus, specimens, not_curated, morpho_names, morpho_specimens,"
        + " indet_specimens, other_specimens, counted from place_genus_curation where list_type = ? and list_key = ?";

    /**
     * The place's genus rows by key (one query), or null when the place has no column, the table is missing or has no
     * rows for the place (the cells are then empty; never an error).
     */
    public static Map<String, Row> read(Connection connection, Overview overview) {
        String[] tk = typeAndKey(overview);
        if (tk == null || connection == null) return null;
        Map<String, Row> out = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(READ_SQL)) {
            stmt.setString(1, tk[0]);
            stmt.setString(2, tk[1]);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    Row row = new Row();
                    row.taxonName = r.getString(1);
                    row.subfamily = r.getString(2);
                    row.genus = r.getString(3);
                    row.specimens = r.getInt(4);
                    row.notCurated = r.getInt(5);
                    row.morphoNames = r.getInt(6);
                    row.morphoSpecimens = r.getInt(7);
                    row.indetSpecimens = r.getInt(8);
                    row.otherSpecimens = r.getInt(9);
                    row.counted = r.getTimestamp(10);
                    out.put(key(row.subfamily, row.genus), row);
                }
            }
        } catch (SQLException e) {
            s_log.warn("read() overview:" + overview + " e:" + e);   // table missing (not yet created): no column numbers
            return null;
        }
        return out.isEmpty() ? null : out;
    }

    /** One genus of a place (the header of the view Not curated), or null. */
    public static Row read(Connection connection, Overview overview, String subfamily, String genus) {
        Map<String, Row> all = read(connection, overview);
        return all == null ? null : all.get(key(subfamily, genus));
    }

    /** Whether the kind of place has rows in place_specimen_name (filled at least once); false when the table is missing. */
    public static boolean namesFilled(Connection connection, String type) {
        try (PreparedStatement stmt = connection.prepareStatement("select 1 from place_specimen_name where list_type = ? limit 1")) {
            stmt.setString(1, type);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}

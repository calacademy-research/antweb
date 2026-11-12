package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.calacademy.antweb.geolocale.Geolocale;

/**
 * Image numbers of places (country, adm1, island, region and subregion pages, bioregions, museums,
 * projects), counted straight from the image table (Brian Fisher, 2026-10-20).
 *
 * Before: geolocale.image_count and project.image_count were the sum of the per-taxon image counts of
 * the place's list, read in nightly sets 2 and 3, before set 4 had filled those per-taxon counts in
 * again (set 2 recreates the specimen rows of geolocale_taxon with image_count 0). Madagascar showed
 * 3,435 images instead of about 29,700.
 *
 * Which images belong to a place: the images of the specimens that the place's "Specimens" number
 * counts (the same rule, so Images and Imaged specimens can never count specimens that Specimens leaves out),
 * red-flagged specimens left out (Brian Fisher, 2026-11-09: their images are not counted anywhere):
 *
 *   country      specimen.country = the country, status valid, unrecognized, morphotaxon,
 *                indetermined or unidentifiable, name (species or subspecies) on the country's list
 *   island       the same with specimen.island_country (geolocale.is_island = 1, any rank)
 *   adm1         the same with specimen.adm1 and specimen.country = its parent country (B26, fixed
 *                2026-11-09; before: specimen.adm1 alone, so a state of the same name elsewhere added to it)
 *   region,      since 2026-10-20 (as their Specimens number, Geolocale.getCountSpecimensQuery and
 *   subregion    regionSpecimensSql): the specimens that the pages of its countries count (collected in
 *                one of its countries, countable status, name on that country's list), name also on
 *                the region's list; before: specimens of the names on the list, collected anywhere
 *   bioregion    since 2026-10-28 the specimens of the species and subspecies rows of its list, as its Specimens
 *   museum       number (Brian Fisher: the sum of the list rows, like countries): specimen.bioregion or
 *                specimen.museum = the place, counted status, filed under a name on the list or under a synonym
 *                (renamed name) whose valid name is on it (PlaceTotals.listSpecimensFrom); before: every ant
 *                specimen of the bioregion or museum, any status, any name
 *   project      specimens, collected anywhere and of any status, of the species and subspecies on
 *                the project's list (proj_taxon; Project.getCountSpecimensQuery); since 2026-10-28 a curated
 *                project's specimens filed under a synonym of a name on its list count too (Brian Fisher:
 *                images follow the Specimens rule)
 *
 * Stored in place_image_count (created by the application, ensureSchema(); record and undo:
 * db/upgrade/8.113/2026-10-20-place-image-count.sql), one row per place and shot type
 * (image.shot_type in lower case: h head, p profile, d dorsal, v ventral, f field, l label;
 * '' when empty), image_count = images of that shot type, specimen_count = specimens with at least
 * one of them; and three summary rows:
 *
 *   *all          all image files; specimens with any image
 *   *ant          images that are not labels; specimens with at least one of them ("Imaged specimens")
 *   *label_only   specimens whose only images are labels, and their label images
 *
 * Also written into the place tables: image_count = all files (labels included),
 * imaged_specimen_count = specimens with at least one image of the ant (labels not counted).
 *
 * updateAll() (daily update set 4, utilData.do?action=placeImageCounts): one grouped query pair per source
 * (see below), all places at once; the work is done by MySQL, the results (a few rows per place) are
 * small. updatePlace() (the Recalculate button of a place page): the same queries for one place.
 *
 * THE STATEMENTS AND WHAT THEY COST (2026-11-11; production: about 917,000 specimens, about 67,000 of them with an
 * image, about 280,000 image rows; the step took 13.6 minutes on production on 2026-11-10, of which geolocale 639 s,
 * and about 10 minutes on staging on 2026-11-11).
 *
 * Seven sources, each a SELECT giving one row (place key k, specimen code, image id, shot type) per image of a
 * specimen the place counts (sources()):
 *
 *   islands                  geolocale (islands) -> specimen by island_country -> geolocale_taxon -> taxon -> image
 *   countries                geolocale (countries) -> specimen by country -> geolocale_taxon -> taxon -> image
 *   adm1s                    geolocale (adm1s) -> specimen by country and adm1 -> geolocale_taxon -> taxon -> image
 *   regions and subregions   (the countries of each region and subregion -> specimen by country, and by
 *                            island_country for islands -> geolocale_taxon of the country; distinct place and code)
 *                            -> specimen by code -> geolocale_taxon of the region -> taxon -> image
 *   bioregion, museum,       (the list rows, and the synonyms of the list rows) -> specimen by taxon_name -> taxon
 *   project                  -> image
 *
 * Each source is run twice, inside two grouped SELECTs (count()): per place and shot type (images, specimens), and per
 * place and specimen (has it an image that is not a label), summed per place. 14 SELECTs, no other statement reads.
 *
 * Rows read by the old statements, per run of a source (so twice each): every specimen row the place rule reaches,
 * imaged or not, and for each of them one lookup in the place's list, one in taxon and one in image. Only the image
 * lookup tells that 13 of 14 specimens have no image, and it comes last:
 *
 *   islands, countries       about 917,000 specimen rows between them (every specimen with a country), 3 lookups each
 *   adm1s                    every specimen with an adm1 of a known state (most of the 917,000), 3 lookups each
 *   regions and subregions   every specimen with a country twice (once for its subregion, once for its region) with
 *                            one list lookup, then the distinct (place, code) rows again by code with 3 lookups
 *   bioregion, museum        every specimen of a name on the list (most of the 917,000 each), 2 lookups each
 *   project                  every specimen once per project whose list has its name (allantwebants and worldants
 *                            each list nearly every name: about 3 times the specimen table), 2 lookups each
 *
 * Together about 10 million specimen rows and about 20 million lookups per pass, two passes (an estimate from the
 * table sizes, not a measurement; MySQL picks the order of the joins). The rows that come out are few: the 280,000
 * image rows, once per place their specimen belongs to.
 *
 * SINCE 2026-11-11 (Brian Fisher: the daily update in one hour), all places at once (updateAll, updateType): the same
 * 14 SELECTs, character for character, except that they read the specimens from a working set of the specimens that
 * have an image (about 67,000 rows) instead of the specimen table. Every source joins image on
 * image.image_of_id = specimen.code, so a specimen without an image never gave a row: leaving those specimens out
 * before the joins changes no row of any source, so no number.
 *
 * The working set (openWorkingSet): session-private (TEMPORARY) tables, made once per run and dropped at its end.
 *   1. CREATE TEMPORARY TABLE ... AS SELECT <the nine specimen columns the sources read> FROM specimen WHERE 1 = 0:
 *      the columns with the types and collations of specimen, no row read. Not CREATE TABLE (that would commit set
 *      4's transaction; a TEMPORARY table does not), and no ALTER or CREATE INDEX afterwards (those commit even on a
 *      temporary table): the keys are part of the CREATE.
 *   2. One plain SELECT of those columns of the specimens for which an image row exists (reads the specimen table or
 *      the image index once), read into memory and stored with multi-row INSERTs. Not INSERT ... SELECT from
 *      specimen: under REPEATABLE READ that puts shared locks on every specimen row it reads until set 4 commits,
 *      which would hold up a curator's upload. A plain SELECT locks nothing, as the old statements.
 *   3. Checked in the database: as many rows stored as read, and every stored row equal to its specimen row, byte for
 *      byte in each of the nine columns.
 *   4. Two copies (INSERT ... SELECT from the first working table; locks only session-private rows): the regions source
 *      reads the specimens three times, and MySQL cannot read one temporary table twice in a statement (error 1137).
 *   5. Each source is prepared once without running it (LIMIT 0), so a statement MySQL refuses is found here.
 * If any of this fails (for example the database user may not create temporary tables), nothing was written yet: the
 * old statements are run instead and the log line says so ("working set not used"). The numbers are the same either
 * way; only the time differs.
 *
 * One place (updatePlace, the Recalculate button) keeps the old statements: they start from that one place.
 *
 * Proof: PlaceImageCountDbTest runs the old statements (countReference) and the new ones on one fixture and compares
 * every row; utilData.do?action=placeImageCountCompare (admins, writes nothing to the site's tables) does the same on
 * the live data and lists every place whose numbers differ.
 *
 * The line in compute.log names its parts: "Place image counts: imaged specimens N in S s (read S s, stored S s,
 * checked S s, copies S s, statements prepared S s); geolocale N places with images in S s (islands S s, countries S s,
 * adm1s S s, regions and subregions S s, written S s); bioregion N places with images in S s (counted S s, written
 * S s); ...".
 *
 * Measured 2026-11-11 on a synthetic database of production's size (917,000 specimens, 65,500 imaged, 253,000 images,
 * 2,345 places with images; MySQL 8.0, not production's data or machine): the old statements 775 s, the new 26 s plus
 * 4 s for the working set, the same rows for every place.
 */
public class PlaceImageCountDb {

    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";
    public static final String MUSEUM = "museum";
    public static final String BIOREGION = "bioregion";
    public static final String[] TYPES = {GEOLOCALE, BIOREGION, MUSEUM, PROJECT};

    public static final String ALL = "*all";
    public static final String ANT = "*ant";
    public static final String LABEL_ONLY = "*label_only";
    public static final String LABEL = "l";

    /** Specimen statuses counted by the geolocale Specimens number (StatusSet.getCountables). */
    static final String COUNTABLE = "('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')";
    static final String SPECIES = "('species', 'subspecies')";
    static final String SHOT = "left(lower(trim(coalesce(i.shot_type, ''))), 32)";

    private final Connection connection;

    public PlaceImageCountDb(Connection connection) {
        this.connection = connection;
    }

    // ------------------------------------------------------------------ table

    public static String createTable(String charset, String collation) {
        String defaults = "";
        if (charset != null && charset.matches("[a-z0-9_]+")) defaults += " DEFAULT CHARSET=" + charset;
        if (collation != null && collation.matches("[a-z0-9_]+")) defaults += " COLLATE=" + collation;
        return "CREATE TABLE IF NOT EXISTS place_image_count ("
            + " place_type varchar(16) NOT NULL,"
            + " place_key varchar(128) NOT NULL,"
            + " shot_type varchar(32) NOT NULL,"
            + " image_count int NOT NULL DEFAULT 0,"
            + " specimen_count int NOT NULL DEFAULT 0,"
            + " computed datetime NULL,"
            + " PRIMARY KEY (place_type, place_key, shot_type)"
            + ") ENGINE=InnoDB" + defaults;
    }

    private static volatile boolean s_schemaChecked = false;

    /** Creates place_image_count when missing, in the character set and collation of taxon.taxon_name. */
    public void ensureSchema() throws SQLException {
        if (s_schemaChecked) return;
        // Created only when missing: CREATE TABLE commits the caller's transaction (nightly set), 2026-11-02.
        if (LicenseUpdateDb.tableExists(connection, "place_image_count")) {
            s_schemaChecked = true;
            return;
        }
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
            stmt.executeUpdate(createTable(charset, collation));
        }
        s_schemaChecked = true;
    }

    // ------------------------------------------------------------------ which images belong to a place

    /** One source of (place key k, specimen code, image id, shot type) rows. key: null = all places. */
    public static final class Source {
        public final String sql;
        final boolean keyIsInt;
        /** For the log line's times: "islands", "countries", "adm1s", "regions and subregions", or the kind. */
        public final String name;
        Source(String sql, boolean keyIsInt, String name) { this.sql = sql; this.keyIsInt = keyIsInt; this.name = name; }
    }

    /**
     * Ants only (Brian Fisher, 2026-10-21: specimens of other families stay in AntWeb but are not counted on place
     * pages), as every number of the place page (PlaceTotals.ANTS).
     */
    static final String ANTS = " and t.family = 'formicidae'";

    /**
     * The images of red-flagged specimens are not counted (Brian Fisher, 2026-11-09; StatisticsDb.NOT_RED_FLAGGED_IMAGE):
     * every source below leaves those specimens out, so Images, Imaged specimens and the label rows of a place have none.
     */
    static final String NOT_RED_FLAGGED = " and " + SpecimenDb.notRedFlagged("s");

    /** The specimen columns the sources read (alias s); the working tables of the imaged specimens hold exactly these. */
    public static final List<String> SPECIMEN_COLUMNS = List.of(
        "code", "taxon_name", "status", "country", "adm1", "island_country", "museum", "bioregion", "flag");

    /**
     * The session-private (TEMPORARY) tables of the specimens that have an image (openWorkingSet). Three with the same
     * rows: the regions source reads the specimens three times and MySQL cannot read one temporary table twice in a
     * statement; every other source reads the first.
     */
    public static final String[] WORK_TABLES = {"tmp_place_image_specimen_a", "tmp_place_image_specimen_b", "tmp_place_image_specimen_c"};

    /** The specimen table itself in the three places a source reads specimens: the old statements. */
    static final String[] SPECIMEN_TABLE = {"specimen", "specimen", "specimen"};

    private static String geolocaleColumnSource(String column, String georankCondition, boolean one, String specimenTable) {
        return "select g.id k, s.code code, i.id id, " + SHOT + " shot"
            + " from geolocale g"
            // an adm1 by its country and name (B26, fixed 2026-11-09; index country_adm1_idx)
            + " join " + specimenTable + " s on s." + column + " = g.name" + ("adm1".equals(column) ? " and s.country = g.parent" : "")
            + " join geolocale_taxon gt on gt.geolocale_id = g.id and gt.taxon_name = s.taxon_name"
            + " join taxon t on t.taxon_name = s.taxon_name"
            + " join image i on i.image_of_id = s.code"
            + " where " + georankCondition
            + " and t.taxarank in " + SPECIES + " and s.status in " + COUNTABLE + ANTS + NOT_RED_FLAGGED
            + (one ? " and g.id = ?" : "");
    }

    /** The sources of one kind of place (geolocale: islands, countries, adm1s, regions and subregions): the old statements. */
    public static List<Source> sources(String type, boolean one) {
        return sources(type, one, SPECIMEN_TABLE);
    }

    /**
     * The same sources reading the specimens from the three tables given (SPECIMEN_TABLE: the specimen table, the old
     * statements; WORK_TABLES: the imaged specimens). The table names are the only difference between the two
     * (PlaceImageCountDbTest.testNewStatementsAreTheOldOnesOverTheWorkingSet).
     */
    public static List<Source> sources(String type, boolean one, String[] specimens) {
        List<Source> list = new ArrayList<>();
        switch (type) {
            case GEOLOCALE:
                list.add(new Source(geolocaleColumnSource("island_country", "coalesce(g.is_island, 0) = 1", one, specimens[0]), true, "islands"));
                list.add(new Source(geolocaleColumnSource("country", "g.georank = 'country' and coalesce(g.is_island, 0) = 0", one, specimens[0]), true, "countries"));
                list.add(new Source(geolocaleColumnSource("adm1", "g.georank = 'adm1' and coalesce(g.is_island, 0) = 0", one, specimens[0]), true, "adm1s"));
                // Regions and subregions (since 2026-10-20): the specimens of their countries, each once.
                list.add(new Source("select d.k k, s.code code, i.id id, " + SHOT + " shot"
                    + " from (select distinct rs.k, rs.code from ("
                    + Geolocale.regionSpecimensSql(one ? " and r.id = ?" : "", specimens[0], specimens[1]) + ") rs) d"
                    + " join " + specimens[2] + " s on s.code = d.code"
                    + " join geolocale_taxon gt on gt.geolocale_id = d.k and gt.taxon_name = s.taxon_name"
                    + " join taxon t on t.taxon_name = s.taxon_name"
                    + " join image i on i.image_of_id = s.code"
                    + " where t.taxarank in " + SPECIES + ANTS + NOT_RED_FLAGGED, true, "regions and subregions"));
                break;
            case BIOREGION:
            case MUSEUM:
            case PROJECT:
                list.add(new Source(listSource(type, one, specimens[0]), false, type));
                break;
            default:
                throw new IllegalArgumentException("place type " + type);
        }
        return list;
    }

    /**
     * Museums, bioregions and projects (Brian Fisher, 2026-10-28: images follow the Specimens rule): the images of the
     * specimens their Specimens number counts, the specimens of the species and subspecies rows of the list, a specimen
     * filed under a synonym under its valid name (PlaceTotals.listSpecimensFrom). Museums and bioregions: their own
     * specimens (specimen.museum, specimen.bioregion) of a counted status; projects: every specimen of the name. The count
     * check recounts with the same source (CountCheck.checkPlaceImages).
     */
    public static String listSource(String type, boolean one) {
        return listSource(type, one, "specimen");
    }

    /** The same source reading the specimens from specimenTable ("specimen": the statement above). */
    static String listSource(String type, boolean one, String specimenTable) {
        String own = MUSEUM.equals(type) ? " and s.museum = m.pk and s.status in " + COUNTABLE
            : BIOREGION.equals(type) ? " and s.bioregion = m.pk and s.status in " + COUNTABLE
            // the Port of Entry project is a species list only: no image numbers (2026-10-22)
            : " and m.pk not in " + org.calacademy.antweb.Project.SPECIES_LIST_ONLY_SQL;
        return "select m.pk k, s.code code, i.id id, " + SHOT + " shot"
            + PlaceTotals.listSpecimensFrom(type, one ? " = ?" : null,
                " join taxon t on t.taxon_name = m.list_name join image i on i.image_of_id = s.code", specimenTable)
            + " and t.taxarank in " + SPECIES + ANTS + NOT_RED_FLAGGED + own;
    }

    // ------------------------------------------------------------------ counting

    /** The image numbers of one place. */
    public static final class PlaceImages {
        public final String type;
        public final String key;
        /** Shot type (lower case, '' when empty) -> images; specimens with at least one. */
        public final Map<String, Integer> shotImages = new LinkedHashMap<>();
        public final Map<String, Integer> shotSpecimens = new LinkedHashMap<>();
        public int allImages;
        public int specimensWithImages;
        public int antImages;
        public int imagedSpecimens;
        public int labelOnlySpecimens;
        public int labelOnlyImages;

        public PlaceImages(String type, String key) {
            this.type = type;
            this.key = key;
        }

        public int labelImages() { return shotImages.getOrDefault(LABEL, 0); }

        /** Recomputes allImages and antImages from the shot rows. */
        void totals() {
            allImages = 0;
            antImages = 0;
            for (Map.Entry<String, Integer> e : shotImages.entrySet()) {
                allImages += e.getValue();
                if (!LABEL.equals(e.getKey())) antImages += e.getValue();
            }
        }

        public String toString() {
            return type + " " + key + " images:" + allImages + " ant:" + antImages + " labels:" + labelImages()
                + " imagedSpecimens:" + imagedSpecimens + " labelOnly:" + labelOnlySpecimens + " shots:" + shotImages;
        }
    }

    /** The time of each source of the last count (two grouped queries each), for the log line. */
    private StringBuilder lastTimes = new StringBuilder();

    private static String seconds(long millis) {
        return String.format(Locale.US, "%.1f", millis / 1000.0);
    }

    /**
     * Counts the places of one kind with the old statements, which read the specimen table (key null: all of them;
     * else that one place). One place (the Recalculate button) is always counted this way.
     */
    Map<String, PlaceImages> count(String type, String key) throws SQLException {
        return count(sources(type, key != null), type, key);
    }

    /**
     * All places of one kind with the old statements: what updateAll() ran until 2026-11-11. Kept for the comparison
     * (compare(), the tests) and for a run whose working set could not be made.
     */
    public Map<String, PlaceImages> countReference(String type) throws SQLException {
        return count(sources(type, false), type, null);
    }

    /**
     * All places of one kind from the working set of the imaged specimens, which must be open (openWorkingSet): the
     * statements of countReference with the working tables in the place of the specimen table.
     */
    public Map<String, PlaceImages> countFromWorkingSet(String type) throws SQLException {
        return count(sources(type, false, WORK_TABLES), type, null);
    }

    private Map<String, PlaceImages> count(List<Source> sources, String type, String key) throws SQLException {
        Map<String, PlaceImages> places = new HashMap<>();
        boolean one = key != null;
        lastTimes = new StringBuilder();
        for (Source source : sources) {
            long sourceStart = System.currentTimeMillis();
            String perShot = "select x.k, x.shot, count(distinct x.id), count(distinct x.code)"
                + " from (" + source.sql + ") x group by x.k, x.shot";
            try (PreparedStatement stmt = connection.prepareStatement(perShot)) {
                if (one) setKey(stmt, source, key);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        PlaceImages p = places.computeIfAbsent(r.getString(1), k -> new PlaceImages(type, k));
                        String shot = r.getString(2) == null ? "" : r.getString(2);
                        p.shotImages.merge(shot, r.getInt(3), Integer::sum);
                        p.shotSpecimens.merge(shot, r.getInt(4), Integer::sum);
                    }
                }
            }
            // Per specimen: does it have an image that is not a label? Then summed per place.
            String perSpecimen = "select y.k, count(*), sum(y.a > 0), sum(y.a = 0), sum(case when y.a = 0 then y.n else 0 end)"
                + " from (select x.k, x.code, sum(x.shot <> '" + LABEL + "') a, count(distinct x.id) n"
                + "   from (" + source.sql + ") x group by x.k, x.code) y group by y.k";
            try (PreparedStatement stmt = connection.prepareStatement(perSpecimen)) {
                if (one) setKey(stmt, source, key);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        PlaceImages p = places.computeIfAbsent(r.getString(1), k -> new PlaceImages(type, k));
                        p.specimensWithImages += r.getInt(2);
                        p.imagedSpecimens += r.getInt(3);
                        p.labelOnlySpecimens += r.getInt(4);
                        p.labelOnlyImages += r.getInt(5);
                    }
                }
            }
            if (lastTimes.length() > 0) lastTimes.append(", ");
            lastTimes.append(source.name).append(" ").append(seconds(System.currentTimeMillis() - sourceStart)).append(" s");
        }
        for (PlaceImages p : places.values()) p.totals();
        return places;
    }

    /** Sets every parameter of the source's statement to the place key (the region source has several). */
    private static void setKey(PreparedStatement stmt, Source source, String key) throws SQLException {
        int n = source.sql.length() - source.sql.replace("?", "").length();
        for (int i = 1; i <= n; i++) {
            if (source.keyIsInt) stmt.setInt(i, Integer.parseInt(key));
            else stmt.setString(i, key);
        }
    }

    // ------------------------------------------------------------------ the imaged specimens (working set)

    private static String specimenColumns(String alias) {
        StringBuilder b = new StringBuilder();
        for (String column : SPECIMEN_COLUMNS) b.append(b.length() > 0 ? ", " : "").append(alias).append(".").append(column);
        return b.toString();
    }

    /**
     * A working table with the columns of SPECIMEN_COLUMNS as specimen has them (types, collations), no row: the
     * SELECT reads nothing (WHERE 1 = 0). The keys are those the sources join by; they are part of the CREATE because
     * an ALTER TABLE or CREATE INDEX would commit the caller's transaction, a CREATE TEMPORARY TABLE does not.
     */
    public static String createWorkTable(String table) {
        return "create temporary table " + table
            + " (primary key (code), key wk_country_adm1 (country, adm1), key wk_island (island_country),"
            + " key wk_taxon (taxon_name), key wk_museum (museum), key wk_bioregion (bioregion))"
            + " as select " + specimenColumns("s") + " from specimen s where 1 = 0";
    }

    /** The specimens that have an image: the columns of SPECIMEN_COLUMNS. A plain SELECT, it locks no row. */
    public static final String IMAGED_SPECIMENS = "select " + specimenColumns("s") + " from specimen s"
        + " where exists (select 1 from image i where i.image_of_id = s.code)";

    /** Rows of a working table that are not, byte for byte in every column, the specimen row of their code: must be 0. */
    public static String workTableDifferences(String table) {
        StringBuilder on = new StringBuilder("s.code = w.code");
        for (String column : SPECIMEN_COLUMNS) {
            on.append(" and cast(s.").append(column).append(" as binary) <=> cast(w.").append(column).append(" as binary)");
        }
        return "select count(*) from " + table + " w left join specimen s on " + on + " where s.code is null";
    }

    private static final int INSERT_ROWS = 500;

    private boolean workingSetOpen = false;

    /** What the last openWorkingSet() did, for the log line: "imaged specimens N in S s (read S s, ...)". */
    private String workingSetLine = "";

    private int intOf(String query) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(query)) {
            r.next();
            return r.getInt(1);
        }
    }

    /**
     * Makes the working set of the imaged specimens (see the class comment) and returns the number of specimens in it.
     * Nothing here writes to a table of the site, commits, or locks a row another session can see. Any failure is an
     * SQLException; the caller then drops what was made (closeWorkingSet) and uses the old statements.
     */
    protected int openWorkingSet() throws SQLException {
        long t0 = System.currentTimeMillis();
        dropWorkTables();   // left by a run that ended badly on this pooled connection
        workingSetOpen = true;
        try (Statement stmt = connection.createStatement()) {
            for (String table : WORK_TABLES) stmt.executeUpdate(createWorkTable(table));
        }
        int width = SPECIMEN_COLUMNS.size();
        List<String[]> rows = new ArrayList<>();
        // a country, status, museum or name comes in thousands of rows: one String for each value (a few MB in all)
        Map<String, String> values = new HashMap<>();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(IMAGED_SPECIMENS)) {
            while (r.next()) {
                String[] row = new String[width];
                row[0] = r.getString(1);   // the code: every row its own
                for (int i = 1; i < width; i++) {
                    String value = r.getString(i + 1);
                    if (value != null) {
                        String seen = values.putIfAbsent(value, value);
                        row[i] = seen != null ? seen : value;
                    }
                }
                rows.add(row);
            }
        }
        values = null;
        long t1 = System.currentTimeMillis();
        insertRows(WORK_TABLES[0], rows);
        long t2 = System.currentTimeMillis();
        int stored = intOf("select count(*) from " + WORK_TABLES[0]);
        if (stored != rows.size()) {
            throw new SQLException("the working set has " + stored + " rows, " + rows.size() + " imaged specimens were read");
        }
        int differ = intOf(workTableDifferences(WORK_TABLES[0]));
        if (differ != 0) {
            throw new SQLException(differ + " rows of the working set are not equal to their specimen row");
        }
        long t3 = System.currentTimeMillis();
        try (Statement stmt = connection.createStatement()) {
            for (int i = 1; i < WORK_TABLES.length; i++) {
                int copied = stmt.executeUpdate("insert into " + WORK_TABLES[i] + " (" + String.join(", ", SPECIMEN_COLUMNS) + ")"
                    + " select " + specimenColumns("w") + " from " + WORK_TABLES[0] + " w");
                if (copied != stored) throw new SQLException("copy " + i + " of the working set has " + copied + " rows of " + stored);
            }
        }
        long t4 = System.currentTimeMillis();
        try (Statement stmt = connection.createStatement()) {
            // Each statement prepared once without running it: one that the database refuses (a column the working
            // tables lack, a temporary table read twice) is found here, before anything is counted or written.
            for (String type : TYPES) {
                for (Source source : sources(type, false, WORK_TABLES)) {
                    try (ResultSet r = stmt.executeQuery("select 1 from (" + source.sql + ") x limit 0")) {
                        r.next();
                    }
                }
            }
        }
        long t5 = System.currentTimeMillis();
        workingSetLine = "imaged specimens " + stored + " in " + seconds(t5 - t0) + " s (read " + seconds(t1 - t0)
            + " s, stored " + seconds(t2 - t1) + " s, checked " + seconds(t3 - t2) + " s, copies " + seconds(t4 - t3)
            + " s, statements prepared " + seconds(t5 - t4) + " s)";
        return stored;
    }

    /** Multi-row INSERTs of INSERT_ROWS rows (one statement for the full blocks, one for the rest). */
    private void insertRows(String table, List<String[]> rows) throws SQLException {
        int full = rows.size() / INSERT_ROWS;
        insertBlocks(table, rows, 0, full, INSERT_ROWS);
        insertBlocks(table, rows, full * INSERT_ROWS, 1, rows.size() - full * INSERT_ROWS);
    }

    private void insertBlocks(String table, List<String[]> rows, int from, int blocks, int rowsPerBlock) throws SQLException {
        if (blocks == 0 || rowsPerBlock == 0) return;
        String one = "(" + "?, ".repeat(SPECIMEN_COLUMNS.size() - 1) + "?)";
        StringBuilder sql = new StringBuilder("insert into " + table + " (" + String.join(", ", SPECIMEN_COLUMNS) + ") values ");
        for (int i = 0; i < rowsPerBlock; i++) sql.append(i > 0 ? ", " : "").append(one);
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            for (int block = 0; block < blocks; block++) {
                int at = 1;
                for (int i = 0; i < rowsPerBlock; i++) {
                    for (String value : rows.get(from + block * rowsPerBlock + i)) stmt.setString(at++, value);
                }
                stmt.executeUpdate();
            }
        }
    }

    private void dropWorkTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // DROP TEMPORARY TABLE: never a table of the site, and no commit
            for (String table : WORK_TABLES) stmt.executeUpdate("drop temporary table if exists " + table);
        }
    }

    /** Drops the working tables (the connection goes back to its pool). Never throws. */
    protected void closeWorkingSet() {
        if (!workingSetOpen) return;
        workingSetOpen = false;
        try {
            dropWorkTables();
        } catch (SQLException e) {
            // a temporary table ends with its session at the latest; the next run drops it before it starts
        }
    }

    /**
     * Opens the working set; false when it could not be made (then nothing of it is left and workingSetLine says why,
     * in a few words, for the log line).
     */
    private boolean tryOpenWorkingSet() {
        try {
            openWorkingSet();
            return true;
        } catch (SQLException e) {
            closeWorkingSet();
            String why = String.valueOf(e.getMessage()).replaceAll("\\s+", " ").trim();
            if (why.length() > 160) why = why.substring(0, 160) + "...";
            workingSetLine = WORKING_SET_NOT_USED + " (" + why + "), the old statements ran";
            return false;
        }
    }

    /** In the log line when the old statements ran because the working set could not be made (the e2e tests look for it). */
    public static final String WORKING_SET_NOT_USED = "imaged specimens: working set not used";

    // ------------------------------------------------------------------ writing

    /** All places of every kind (daily update set 4). Returns one line for compute.log. */
    public String updateAll() throws SQLException {
        ensureSchema();
        StringBuilder b = new StringBuilder("Place image counts:");
        boolean fromWorkingSet = tryOpenWorkingSet();
        b.append(" ").append(workingSetLine).append(";");
        try {
            for (String type : TYPES) {
                long start = System.currentTimeMillis();
                Map<String, PlaceImages> places = fromWorkingSet ? countFromWorkingSet(type) : countReference(type);
                // the time of each source (geolocale: islands, countries, adm1s, regions and subregions; 2026-11-10:
                // 639 s on production, and the line did not say which), and of the writing
                String counted = lastTimes.indexOf(",") > 0 ? lastTimes.toString()
                    : "counted " + seconds(System.currentTimeMillis() - start) + " s";
                long written = System.currentTimeMillis();
                write(type, null, places);
                long end = System.currentTimeMillis();
                b.append(" ").append(type).append(" ").append(places.size()).append(" places with images in ")
                 .append(seconds(end - start)).append(" s (").append(counted)
                 .append(", written ").append(seconds(end - written)).append(" s);");
            }
        } finally {
            closeWorkingSet();
        }
        return b.toString();
    }

    /** One kind of place (all of them). */
    public int updateType(String type) throws SQLException {
        ensureSchema();
        boolean fromWorkingSet = tryOpenWorkingSet();
        try {
            Map<String, PlaceImages> places = fromWorkingSet ? countFromWorkingSet(type) : countReference(type);
            write(type, null, places);
            return places.size();
        } finally {
            closeWorkingSet();
        }
    }

    /** One place (Recalculate button). type: GEOLOCALE (key = geolocale id), PROJECT, MUSEUM or BIOREGION. */
    public PlaceImages updatePlace(String type, String key) throws SQLException {
        ensureSchema();
        Map<String, PlaceImages> places = count(type, key);
        write(type, key, places);
        PlaceImages p = places.get(key);
        return p != null ? p : new PlaceImages(type, key);
    }

    private static String placeTable(String type) {
        switch (type) {
            case GEOLOCALE: return "geolocale";
            case PROJECT: return "project";
            case MUSEUM: return "museum";
            default: return "bioregion";
        }
    }

    private static String placeKeyColumn(String type) {
        switch (type) {
            case GEOLOCALE: return "id";
            case PROJECT: return "project_name";
            case MUSEUM: return "code";
            default: return "name";
        }
    }

    private void write(String type, String key, Map<String, PlaceImages> places) throws SQLException {
        String delete = "delete from place_image_count where place_type = ?" + (key != null ? " and place_key = ?" : "");
        try (PreparedStatement stmt = connection.prepareStatement(delete)) {
            stmt.setString(1, type);
            if (key != null) stmt.setString(2, key);
            stmt.executeUpdate();
        }
        String insert = "insert into place_image_count (place_type, place_key, shot_type, image_count, specimen_count, computed)"
            + " values (?, ?, ?, ?, ?, now())"
            + " on duplicate key update image_count = image_count + values(image_count), specimen_count = specimen_count + values(specimen_count)";
        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            int n = 0;
            for (PlaceImages p : places.values()) {
                for (Map.Entry<String, Integer> e : p.shotImages.entrySet()) {
                    add(stmt, type, p.key, e.getKey(), e.getValue(), p.shotSpecimens.getOrDefault(e.getKey(), 0));
                }
                add(stmt, type, p.key, ALL, p.allImages, p.specimensWithImages);
                add(stmt, type, p.key, ANT, p.antImages, p.imagedSpecimens);
                add(stmt, type, p.key, LABEL_ONLY, p.labelOnlyImages, p.labelOnlySpecimens);
                if (++n % 500 == 0) stmt.executeBatch();
            }
            stmt.executeBatch();
        }
        // The numbers on the place pages: all files, and specimens with an image of the ant.
        String table = placeTable(type);
        String column = placeKeyColumn(type);
        String all = GEOLOCALE.equals(type)
            ? " where (georank in ('region', 'subregion', 'country', 'adm1') or coalesce(is_island, 0) = 1)" : "";
        String zero = "update " + table + " set image_count = 0, imaged_specimen_count = 0"
            + (key != null ? " where " + column + " = ?" : all);
        try (PreparedStatement stmt = connection.prepareStatement(zero)) {
            if (key != null) {
                if (GEOLOCALE.equals(type)) stmt.setInt(1, Integer.parseInt(key)); else stmt.setString(1, key);
            }
            stmt.executeUpdate();
        }
        String update = "update " + table + " set image_count = ?, imaged_specimen_count = ? where " + column + " = ?";
        try (PreparedStatement stmt = connection.prepareStatement(update)) {
            int n = 0;
            for (PlaceImages p : places.values()) {
                stmt.setInt(1, p.allImages);
                stmt.setInt(2, p.imagedSpecimens);
                if (GEOLOCALE.equals(type)) stmt.setInt(3, Integer.parseInt(p.key)); else stmt.setString(3, p.key);
                stmt.addBatch();
                if (++n % 500 == 0) stmt.executeBatch();
            }
            stmt.executeBatch();
        }
    }

    private static void add(PreparedStatement stmt, String type, String key, String shot, int images, int specimens) throws SQLException {
        stmt.setString(1, type);
        stmt.setString(2, key);
        stmt.setString(3, shot);
        stmt.setInt(4, images);
        stmt.setInt(5, specimens);
        stmt.addBatch();
    }

    // ------------------------------------------------------------------ comparison (old statements against new)

    /**
     * The rows write() stores for one place, by shot type (and *all, *ant, *label_only): {image_count, specimen_count}.
     * Sorted, so two counts of the same place compare whatever order the database gave their rows in.
     */
    public static java.util.SortedMap<String, long[]> rows(PlaceImages p) {
        java.util.SortedMap<String, long[]> rows = new java.util.TreeMap<>();
        for (Map.Entry<String, Integer> e : p.shotImages.entrySet()) {
            addRow(rows, e.getKey(), e.getValue(), p.shotSpecimens.getOrDefault(e.getKey(), 0));
        }
        addRow(rows, ALL, p.allImages, p.specimensWithImages);
        addRow(rows, ANT, p.antImages, p.imagedSpecimens);
        addRow(rows, LABEL_ONLY, p.labelOnlyImages, p.labelOnlySpecimens);
        return rows;
    }

    private static void addRow(Map<String, long[]> rows, String shot, long images, long specimens) {
        long[] row = rows.computeIfAbsent(shot, k -> new long[2]);   // a key twice adds up, as write()'s insert does
        row[0] += images;
        row[1] += specimens;
    }

    /** "h 12/7, l 3/3, *all 15/8, ..." (images/specimens per row), "no rows" for a place without images. */
    static String rowsText(java.util.SortedMap<String, long[]> rows) {
        if (rows == null || rows.isEmpty()) return "no rows";
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, long[]> e : rows.entrySet()) {
            b.append(b.length() > 0 ? ", " : "").append(e.getKey().isEmpty() ? "''" : e.getKey())
             .append(" ").append(e.getValue()[0]).append("/").append(e.getValue()[1]);
        }
        return b.toString();
    }

    private static boolean sameRows(java.util.SortedMap<String, long[]> a, java.util.SortedMap<String, long[]> b) {
        if (a == null || b == null) return a == b;
        return rowsText(a).equals(rowsText(b));
    }

    /** What compare() found. */
    public static final class Comparison {
        /** Kinds of place compared (all four unless the working set could not be made). */
        public int typesCompared;
        /** Places whose rows by the old statements and by the new ones differ, all kinds. Must be 0. */
        public int differing;
        /** Places whose stored numbers differ from the old statements' fresh count (uploads since the last run explain these). */
        public int storedDiffering;
        /** Why nothing was compared, or null. */
        public String notCompared;
        /** False when nothing is stored yet (no table place_image_count): then storedDiffering says nothing. */
        public boolean storedCompared;
        /** The report, one sentence per line (plain text, not escaped). */
        public final List<String> lines = new ArrayList<>();

        /** The first line: the verdict. */
        public String summary() {
            if (notCompared != null) return COMPARE_LOG + " NOT compared, " + notCompared;
            return COMPARE_LOG + " " + (differing == 0
                ? "the old and the new statements give the same rows for every place of " + typesCompared + " kinds"
                : "the old and the new statements DIFFER for " + differing + " places")
                + (storedCompared ? "; stored numbers that differ from a fresh count: " + storedDiffering + " places"
                                  : "; nothing is stored yet, so no stored number was compared");
        }
    }

    public static final String COMPARE_LOG = "Place image count comparison:";
    static final int COMPARE_EXAMPLES = 20;

    /**
     * Counts every place twice, with the old statements (the specimen table) and with the new ones (the working set of
     * the imaged specimens), and compares every row write() would store, place by place; also compares what is stored
     * (place_image_count, and image_count and imaged_specimen_count of the place tables) with the old statements'
     * count. Writes nothing to a table of the site (its only writes are to its own temporary tables). Both counts read
     * the same state of the data when the connection is in a transaction (autocommit off), as a data job's is. Takes
     * as long as the old step did, plus the new one. utilData.do?action=placeImageCountCompare.
     */
    public Comparison compare() throws SQLException {
        Comparison c = new Comparison();
        boolean stored = LicenseUpdateDb.tableExists(connection, "place_image_count");
        c.storedCompared = stored;
        if (!tryOpenWorkingSet()) {
            c.notCompared = workingSetLine;
            c.lines.add(c.summary());
            return c;
        }
        try {
            c.lines.add(workingSetLine + ".");
            for (String type : TYPES) {
                long t0 = System.currentTimeMillis();
                Map<String, PlaceImages> fresh = countFromWorkingSet(type);
                String freshTimes = lastTimes.toString();
                long t1 = System.currentTimeMillis();
                Map<String, PlaceImages> old = countReference(type);
                String oldTimes = lastTimes.toString();
                long t2 = System.currentTimeMillis();

                java.util.SortedSet<String> keys = new java.util.TreeSet<>(old.keySet());
                keys.addAll(fresh.keySet());
                int differ = 0;
                List<String> examples = new ArrayList<>();
                for (String key : keys) {
                    java.util.SortedMap<String, long[]> a = old.containsKey(key) ? rows(old.get(key)) : null;
                    java.util.SortedMap<String, long[]> b = fresh.containsKey(key) ? rows(fresh.get(key)) : null;
                    if (sameRows(a, b)) continue;
                    if (++differ <= COMPARE_EXAMPLES) {
                        examples.add(type + " " + key + ": old statements " + rowsText(a) + "; new statements " + rowsText(b) + ".");
                    }
                }
                c.differing += differ;
                c.typesCompared++;
                c.lines.add(type + ": " + old.size() + " places with images by the old statements in " + seconds(t2 - t1) + " s (" + oldTimes
                    + "), " + fresh.size() + " by the new in " + seconds(t1 - t0) + " s (" + freshTimes + "); places that differ: " + differ + ".");
                c.lines.addAll(examples);

                if (stored) {
                    List<String> storedExamples = new ArrayList<>();
                    int storedDiffer = compareStored(type, old, storedExamples);
                    c.storedDiffering += storedDiffer;
                    c.lines.add(type + ": places whose stored numbers differ from the old statements' count now: " + storedDiffer + ".");
                    c.lines.addAll(storedExamples);
                }
            }
            if (!stored) c.lines.add("Nothing is stored yet (no table place_image_count).");
        } finally {
            closeWorkingSet();
        }
        c.lines.add(0, c.summary());
        return c;
    }

    /** Places of one kind whose stored rows or place-table numbers differ from a count; up to COMPARE_EXAMPLES examples. */
    private int compareStored(String type, Map<String, PlaceImages> counted, List<String> examples) throws SQLException {
        Map<String, java.util.SortedMap<String, long[]>> storedRows = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "select place_key, shot_type, image_count, specimen_count from place_image_count where place_type = ?")) {
            stmt.setString(1, type);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    addRow(storedRows.computeIfAbsent(r.getString(1), k -> new java.util.TreeMap<>()), r.getString(2), r.getLong(3), r.getLong(4));
                }
            }
        }
        // the two numbers on the place pages; a place without images has 0 and 0 (write() zeroes every place first)
        Map<String, long[]> pageNumbers = new HashMap<>();
        String all = GEOLOCALE.equals(type)
            ? " and (georank in ('region', 'subregion', 'country', 'adm1') or coalesce(is_island, 0) = 1)" : "";
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select " + placeKeyColumn(type) + ", image_count, imaged_specimen_count from " + placeTable(type)
                 + " where (coalesce(image_count, 0) <> 0 or coalesce(imaged_specimen_count, 0) <> 0)" + all)) {
            // by the key in lower case: write() finds the place's row by its key as the database compares it
            while (r.next()) pageNumbers.put(r.getString(1).toLowerCase(Locale.ROOT), new long[] {r.getLong(2), r.getLong(3)});
        }
        java.util.SortedSet<String> keys = new java.util.TreeSet<>(counted.keySet());
        keys.addAll(storedRows.keySet());
        int differ = 0;
        for (String key : keys) {
            PlaceImages p = counted.get(key);
            java.util.SortedMap<String, long[]> a = p == null ? null : rows(p);
            java.util.SortedMap<String, long[]> b = storedRows.get(key);
            long[] page = pageNumbers.remove(key.toLowerCase(Locale.ROOT));
            if (page == null) page = new long[2];
            long images = p == null ? 0 : p.allImages;
            long imaged = p == null ? 0 : p.imagedSpecimens;
            if (sameRows(a, b) && page[0] == images && page[1] == imaged) continue;
            if (++differ <= COMPARE_EXAMPLES) {
                examples.add(type + " " + key + ": counted now " + rowsText(a) + "; stored " + rowsText(b)
                    + "; on the page " + page[0] + " images, " + page[1] + " imaged specimens.");
            }
        }
        // numbers on the page of a place that has no images by the count and no stored rows
        for (Map.Entry<String, long[]> e : new java.util.TreeMap<>(pageNumbers).entrySet()) {
            if (++differ <= COMPARE_EXAMPLES) {
                examples.add(type + " " + e.getKey() + ": counted now no rows; stored no rows; on the page " + e.getValue()[0]
                    + " images, " + e.getValue()[1] + " imaged specimens.");
            }
        }
        return differ;
    }

    // ------------------------------------------------------------------ reading (place pages, count check)

    /** The stored numbers of one place, or null when none are stored (no images, or not computed yet). */
    public PlaceImages read(String type, String key) throws SQLException {
        PlaceImages p = new PlaceImages(type, key);
        boolean any = false;
        String query = "select shot_type, image_count, specimen_count from place_image_count where place_type = ? and place_key = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, type);
            stmt.setString(2, key);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    any = true;
                    String shot = r.getString(1);
                    int images = r.getInt(2);
                    int specimens = r.getInt(3);
                    if (ALL.equals(shot)) {
                        p.allImages = images;
                        p.specimensWithImages = specimens;
                    } else if (ANT.equals(shot)) {
                        p.antImages = images;
                        p.imagedSpecimens = specimens;
                    } else if (LABEL_ONLY.equals(shot)) {
                        p.labelOnlyImages = images;
                        p.labelOnlySpecimens = specimens;
                    } else {
                        p.shotImages.put(shot, images);
                        p.shotSpecimens.put(shot, specimens);
                    }
                }
            }
        }
        return any ? p : null;
    }

    /** Whether the table exists and has rows (the nightly run has computed the numbers at least once). */
    public boolean isComputed() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select 1 from information_schema.tables where table_schema = database()"
                 + " and table_name = 'place_image_count'")) {
            if (!r.next()) return false;
        }
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select 1 from place_image_count limit 1")) {
            return r.next();
        }
    }

    // ------------------------------------------------------------------ words for the page

    /** Shot type letters as the image pages name them (SpecimenImage.getShotText, bigPicture-body.jsp). */
    static final String[][] SHOT_NAMES = {{"h", "head"}, {"p", "profile"}, {"d", "dorsal"}, {"v", "ventral"}, {"f", "field image"}};

    private static String num(int n) {
        return NumberFormat.getNumberInstance(Locale.US).format(n);
    }

    /**
     * "24,264 of the ant (7,849 head, 9,332 profile, 6,806 dorsal, 276 ventral, 1 field photo) and 5,436 of labels":
     * the images that are not labels by view, other letters together as "other". Plain text (no HTML).
     */
    /**
     * ": 6,649 head, 7,576 profile, 5,407 dorsal, 7 ventral, and 1 field image" for the Ant images row of the
     * statistics table (Brian Fisher, 2026-10-20); other letters together as "other"; "" when there are none.
     */
    public static String antViews(PlaceImages p) {
        List<String> parts = new ArrayList<>();
        int known = 0;
        for (String[] s : SHOT_NAMES) {
            int n = p.shotImages.getOrDefault(s[0], 0);
            if (n == 0) continue;
            known += n;
            String name = s[1];
            if (n != 1 && name.endsWith("image")) name += "s";
            parts.add(num(n) + " " + name);
        }
        int other = p.antImages - known;
        if (other > 0) parts.add(num(other) + " other");
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return ": " + parts.get(0);
        return ": " + String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.get(parts.size() - 1);
    }

    public static String breakdown(PlaceImages p) {
        List<String> parts = new ArrayList<>();
        int known = 0;
        for (String[] s : SHOT_NAMES) {
            int n = p.shotImages.getOrDefault(s[0], 0);
            if (n == 0) continue;
            known += n;
            String name = s[1];
            if (n != 1 && (name.endsWith("photo") || name.endsWith("image"))) name += "s";
            parts.add(num(n) + " " + name);
        }
        int other = p.antImages - known;
        if (other > 0) parts.add(num(other) + " other");
        StringBuilder b = new StringBuilder();
        b.append(num(p.antImages)).append(" of the ant");
        if (!parts.isEmpty()) b.append(" (").append(String.join(", ", parts)).append(")");
        b.append(" and ").append(num(p.labelImages())).append(" of labels");
        return b.toString();
    }
}

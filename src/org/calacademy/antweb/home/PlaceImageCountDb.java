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
 * counts (the same rule, so Images and Imaged specimens can never count specimens that Specimens leaves out):
 *
 *   country      specimen.country = the country, status valid, unrecognized, morphotaxon,
 *                indetermined or unidentifiable, name (species or subspecies) on the country's list
 *   island       the same with specimen.island_country (geolocale.is_island = 1, any rank)
 *   adm1         the same with specimen.adm1
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
 * updateAll() (nightly set 4, utilData.do?action=placeImageCounts): one grouped query pair per kind
 * of place, all places at once; the work is done by MySQL, the results (a few rows per place) are
 * small. updatePlace() (the Recalculate button of a place page): the same queries for one place.
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
    static final class Source {
        final String sql;
        final boolean keyIsInt;
        Source(String sql, boolean keyIsInt) { this.sql = sql; this.keyIsInt = keyIsInt; }
    }

    /**
     * Ants only (Brian Fisher, 2026-10-21: specimens of other families stay in AntWeb but are not counted on place
     * pages), as every number of the place page (PlaceTotals.ANTS).
     */
    static final String ANTS = " and t.family = 'formicidae'";

    private static String geolocaleColumnSource(String column, String georankCondition, boolean one) {
        return "select g.id k, s.code code, i.id id, " + SHOT + " shot"
            + " from geolocale g"
            + " join specimen s on s." + column + " = g.name"
            + " join geolocale_taxon gt on gt.geolocale_id = g.id and gt.taxon_name = s.taxon_name"
            + " join taxon t on t.taxon_name = s.taxon_name"
            + " join image i on i.image_of_id = s.code"
            + " where " + georankCondition
            + " and t.taxarank in " + SPECIES + " and s.status in " + COUNTABLE + ANTS
            + (one ? " and g.id = ?" : "");
    }

    /** The sources of one kind of place (geolocale: islands, countries, adm1s, regions and subregions). */
    static List<Source> sources(String type, boolean one) {
        List<Source> list = new ArrayList<>();
        switch (type) {
            case GEOLOCALE:
                list.add(new Source(geolocaleColumnSource("island_country", "coalesce(g.is_island, 0) = 1", one), true));
                list.add(new Source(geolocaleColumnSource("country", "g.georank = 'country' and coalesce(g.is_island, 0) = 0", one), true));
                list.add(new Source(geolocaleColumnSource("adm1", "g.georank = 'adm1' and coalesce(g.is_island, 0) = 0", one), true));
                // Regions and subregions (since 2026-10-20): the specimens of their countries, each once.
                list.add(new Source("select d.k k, s.code code, i.id id, " + SHOT + " shot"
                    + " from (select distinct rs.k, rs.code from (" + Geolocale.regionSpecimensSql(one ? " and r.id = ?" : "") + ") rs) d"
                    + " join specimen s on s.code = d.code"
                    + " join geolocale_taxon gt on gt.geolocale_id = d.k and gt.taxon_name = s.taxon_name"
                    + " join taxon t on t.taxon_name = s.taxon_name"
                    + " join image i on i.image_of_id = s.code"
                    + " where t.taxarank in " + SPECIES + ANTS, true));
                break;
            case BIOREGION:
            case MUSEUM:
            case PROJECT:
                list.add(new Source(listSource(type, one), false));
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
        String own = MUSEUM.equals(type) ? " and s.museum = m.pk and s.status in " + COUNTABLE
            : BIOREGION.equals(type) ? " and s.bioregion = m.pk and s.status in " + COUNTABLE
            // the Port of Entry project is a species list only: no image numbers (2026-10-22)
            : " and m.pk not in " + org.calacademy.antweb.Project.SPECIES_LIST_ONLY_SQL;
        return "select m.pk k, s.code code, i.id id, " + SHOT + " shot"
            + PlaceTotals.listSpecimensFrom(type, one ? " = ?" : null,
                " join taxon t on t.taxon_name = m.list_name join image i on i.image_of_id = s.code")
            + " and t.taxarank in " + SPECIES + ANTS + own;
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

    /** Counts the places of one kind (key null: all of them; else that one place). */
    Map<String, PlaceImages> count(String type, String key) throws SQLException {
        Map<String, PlaceImages> places = new HashMap<>();
        boolean one = key != null;
        for (Source source : sources(type, one)) {
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

    // ------------------------------------------------------------------ writing

    /** All places of every kind (nightly set 4). Returns one line for compute.log. */
    public String updateAll() throws SQLException {
        ensureSchema();
        StringBuilder b = new StringBuilder("Place image counts:");
        for (String type : TYPES) {
            long start = System.currentTimeMillis();
            Map<String, PlaceImages> places = count(type, null);
            write(type, null, places);
            b.append(" ").append(type).append(" ").append(places.size()).append(" places with images in ")
             .append(String.format(Locale.US, "%.1f", (System.currentTimeMillis() - start) / 1000.0)).append(" s;");
        }
        return b.toString();
    }

    /** One kind of place (all of them). */
    public int updateType(String type) throws SQLException {
        ensureSchema();
        Map<String, PlaceImages> places = count(type, null);
        write(type, null, places);
        return places.size();
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

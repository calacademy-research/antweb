package org.calacademy.antweb.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The places whose numbers can have changed since the last nightly count, for the count check (CountCheck,
 * CountInvariants). Brian Fisher, 2026-11-03 (audit of 2026-11-02): a difference is "pending" only when the place's or
 * the name's numbers could have changed since the count: an upload or a rename that touched that place, not every place
 * after any upload. Until 2026-11-03 one specimen upload anywhere (or one rename, or one image) made every difference of
 * every place pending until the next nightly run, so the daily check found nothing on a day with an upload.
 *
 * A place is touched by specimens when one of its specimens now (the specimens of the uploads after the count, or
 * specimens renamed by hand after it) is collected there, owned there, or filed under a name of its list:
 *   geolocale  a country or island by specimen.country or island_country, an adm1 by its name and country, a subregion
 *              or region through its countries (geolocale.parent)
 *   museum     specimen.museum, or ownedBy naming the museum's code (nightly set 3 sets specimen.museum from ownedBy:
 *              MuseumDb.ownedByPattern; a new specimen has none yet)
 *   bioregion  specimen.bioregion (set at upload)
 *   project    a name on its list: the specimen's name, its current valid name (curated projects count a synonym's
 *              specimens under it), or the old name of a rename
 * A place is touched by images when an image added after the count is of one of its specimens, by the same rules.
 *
 * A re-upload replaces all the specimens of its group (SpecimenUploadDb.dropSpecimens): the specimens it keeps get the
 * new upload id, so the places of the group are touched. A specimen it removed leaves no record: if it was the group's
 * only specimen in a place, or of a name on a project's list, that difference shows as real until the nightly run.
 */
public final class ChangedPlaces {

    /** type (CountCheck.GEOLOCALE, PROJECT, MUSEUM, BIOREGION) -> keys (geolocale id, project name, museum code, bioregion name). */
    private final Map<String, Set<String>> bySpecimens = new HashMap<>();
    private final Map<String, Set<String>> byImages = new HashMap<>();

    public static final String SPECIMENS_REASON = "specimens of this place were uploaded or renamed after the last nightly run";
    public static final String IMAGES_REASON = "images of this place's specimens were added after the last nightly run";

    private ChangedPlaces() {
    }

    /** Whether specimens of the place were uploaded or renamed after the count. */
    public boolean specimens(String type, String key) {
        Set<String> s = bySpecimens.get(type);
        return key != null && s != null && s.contains(key);
    }

    /** Whether images of the place's specimens were added after the count. */
    public boolean images(String type, String key) {
        Set<String> s = byImages.get(type);
        return key != null && s != null && s.contains(key);
    }

    /** The keys of one type touched by specimens (tests, the report). */
    public Set<String> specimenKeys(String type) {
        Set<String> s = bySpecimens.get(type);
        return s == null ? CountInvariants.keySet() : s;
    }

    /** For tests: a place marked as touched by specimens and images. */
    public static ChangedPlaces of(String type, String... keys) {
        ChangedPlaces c = new ChangedPlaces();
        Set<String> s = CountInvariants.keySet();
        for (String k : keys) s.add(k);
        c.bySpecimens.put(type, s);
        c.byImages.put(type, CountInvariants.keySet());
        c.byImages.get(type).addAll(s);
        return c;
    }

    /**
     * Reads the places touched after since (the end of the last nightly count). uploads: whether an upload came after it
     * (CountCheck.Result.uploadSinceNightly); images: whether images did (imagesSinceNightly). Two passes over the
     * specimens at most (one for the specimens, one for the images), each only when there was such a change.
     */
    public static ChangedPlaces load(Connection connection, Timestamp since, boolean uploads, boolean images) throws SQLException {
        ChangedPlaces c = new ChangedPlaces();
        Db db = new Db(connection);
        List<String> specimenConditions = new ArrayList<>();
        int specimenParams = 0;
        if (uploads && db.tableExists("upload") && db.columnExists("specimen", "upload_id")) {
            specimenConditions.add("s.upload_id in (select upload_id from upload where created >= ?)");
            ++specimenParams;
        }
        boolean renames = db.tableExists("specimen_name_update") && db.renamedSince(since);
        if (renames) {
            specimenConditions.add("s.taxon_name in (select new_name from specimen_name_update where mode = 'changed' and run_date >= ?)");
            ++specimenParams;
        }
        if (!specimenConditions.isEmpty()) {
            String cond = "(" + String.join(" or ", specimenConditions) + ")";
            c.fill(db, c.bySpecimens, cond, specimenParams, since, renames);
        }
        if (images && db.tableExists("image")) {
            c.fill(db, c.byImages, "s.code in (select image_of_id from image where upload_date >= ?)", 1, since, false);
        }
        return c;
    }

    /** The places of the specimens that meet cond (alias s; nParams parameters, all since) into out. */
    private void fill(Db db, Map<String, Set<String>> out, String cond, int nParams, Timestamp since, boolean renames) throws SQLException {
        Set<String> countries = CountInvariants.keySet();
        Set<String> adm1s = CountInvariants.keySet();   // adm1 + "\u0000" + country
        Set<String> museums = CountInvariants.keySet();
        Set<String> ownedBy = new java.util.HashSet<>();
        Set<String> bioregions = CountInvariants.keySet();
        boolean hasIsland = db.columnExists("specimen", "island_country");
        boolean hasAdm1 = db.columnExists("specimen", "adm1");
        boolean hasMuseum = db.columnExists("specimen", "museum");
        boolean hasOwnedBy = db.columnExists("specimen", "ownedBy");
        boolean hasBioregion = db.columnExists("specimen", "bioregion");
        String cols = "s.country, " + (hasIsland ? "s.island_country" : "null") + ", " + (hasAdm1 ? "s.adm1" : "null") + ", "
            + (hasMuseum ? "s.museum" : "null") + ", " + (hasOwnedBy ? "s.ownedBy" : "null") + ", " + (hasBioregion ? "s.bioregion" : "null");
        try (PreparedStatement stmt = db.connection.prepareStatement("select distinct " + cols + " from specimen s where " + cond)) {
            for (int i = 1; i <= nParams; i++) stmt.setTimestamp(i, since);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    String country = r.getString(1);
                    if (country != null) countries.add(country);
                    if (r.getString(2) != null) countries.add(r.getString(2));
                    if (r.getString(3) != null && country != null) adm1s.add(r.getString(3) + "\u0000" + country);
                    if (r.getString(4) != null) museums.add(r.getString(4));
                    if (r.getString(5) != null) ownedBy.add(r.getString(5));
                    if (r.getString(6) != null) bioregions.add(r.getString(6));
                }
            }
        }
        out.put(CountCheck.GEOLOCALE, geolocales(db, countries, adm1s));
        if (hasOwnedBy && !ownedBy.isEmpty() && db.tableExists("museum")) {
            try (PreparedStatement stmt = db.connection.prepareStatement("select code from museum");
                 ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    String code = r.getString(1);
                    if (code == null || museums.contains(code)) continue;
                    java.util.regex.Pattern p = ownedByPattern(code);
                    for (String o : ownedBy) {
                        if (p.matcher(o).find()) {
                            museums.add(code);
                            break;
                        }
                    }
                }
            }
        }
        out.put(CountCheck.MUSEUM, museums);
        out.put(CountCheck.BIOREGION, bioregions);
        out.put(CountCheck.PROJECT, projects(db, cond, nParams, since, renames));
    }

    /**
     * The same rule as MuseumDb.ownedByPattern (MySQL regexp, case-insensitive like the column): the code as a whole word
     * of ownedBy.
     */
    static java.util.regex.Pattern ownedByPattern(String code) {
        return java.util.regex.Pattern.compile("(^|[^\\p{Alnum}])" + java.util.regex.Pattern.quote(code) + "($|[^\\p{Alnum}])",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    /** Geolocale ids: the countries (and islands) named, their adm1s, and the subregions and regions above them. */
    private static Set<String> geolocales(Db db, Set<String> countries, Set<String> adm1s) throws SQLException {
        Set<String> ids = CountInvariants.keySet();
        if (!db.tableExists("geolocale") || (countries.isEmpty() && adm1s.isEmpty())) return ids;
        Map<String, String[]> byId = new HashMap<>();   // id -> {name, georank, parent}
        try (PreparedStatement stmt = db.connection.prepareStatement("select id, name, georank, parent from geolocale");
             ResultSet r = stmt.executeQuery()) {
            while (r.next()) byId.put(r.getString(1), new String[] {r.getString(2), r.getString(3), r.getString(4)});
        }
        Set<String> subregions = CountInvariants.keySet();
        for (Map.Entry<String, String[]> e : byId.entrySet()) {
            String[] g = e.getValue();
            if (g[0] == null) continue;
            if ("country".equals(g[1]) && countries.contains(g[0])) {
                ids.add(e.getKey());
                if (g[2] != null) subregions.add(g[2]);
            } else if ("adm1".equals(g[1]) && g[2] != null && adm1s.contains(g[0] + "\u0000" + g[2])) {
                ids.add(e.getKey());
            }
        }
        Set<String> regions = CountInvariants.keySet();
        for (Map.Entry<String, String[]> e : byId.entrySet()) {
            String[] g = e.getValue();
            if ("subregion".equals(g[1]) && g[0] != null && subregions.contains(g[0])) {
                ids.add(e.getKey());
                if (g[2] != null) regions.add(g[2]);
            }
        }
        for (Map.Entry<String, String[]> e : byId.entrySet()) {
            String[] g = e.getValue();
            if ("region".equals(g[1]) && g[0] != null && regions.contains(g[0])) ids.add(e.getKey());
        }
        return ids;
    }

    /** Projects with one of the names on their list: the specimens' names, their current valid names, renamed old names. */
    private static Set<String> projects(Db db, String cond, int nParams, Timestamp since, boolean renames) throws SQLException {
        Set<String> out = CountInvariants.keySet();
        if (!db.tableExists("proj_taxon")) return out;
        boolean cvn = db.tableExists("taxon") && db.columnExists("taxon", "current_valid_name");
        String names = "select s.taxon_name from specimen s where " + cond
            + (cvn ? " union select t.current_valid_name from specimen s join taxon t on t.taxon_name = s.taxon_name where " + cond
                + " and t.current_valid_name is not null" : "")
            + (renames ? " union select old_name from specimen_name_update where mode = 'changed' and run_date >= ?" : "");
        // The names as a derived table, joined once (2026-11-03): "taxon_name in (... union ...)" is not turned into a
        // semi-join by MySQL, so the union ran again for every proj_taxon row and the count check job never ended
        // within its 10 minutes on staging.
        try (PreparedStatement stmt = db.connection.prepareStatement("select distinct l.project_name from (" + names + ") n"
                + " join proj_taxon l on l.taxon_name = n.taxon_name")) {
            int n = nParams * (cvn ? 2 : 1) + (renames ? 1 : 0);
            for (int i = 1; i <= n; i++) stmt.setTimestamp(i, since);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) out.add(r.getString(1));
            }
        }
        return out;
    }

    /** Table and column lookups (information_schema). */
    private static final class Db {
        final Connection connection;

        Db(Connection connection) {
            this.connection = connection;
        }

        boolean tableExists(String table) throws SQLException {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
                stmt.setString(1, table);
                try (ResultSet r = stmt.executeQuery()) { return r.next(); }
            }
        }

        /** Whether specimens were renamed by hand after since (specimen_name_update, mode changed). */
        boolean renamedSince(Timestamp since) throws SQLException {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "select 1 from specimen_name_update where mode = 'changed' and run_date >= ? limit 1")) {
                stmt.setTimestamp(1, since);
                try (ResultSet r = stmt.executeQuery()) { return r.next(); }
            }
        }

        boolean columnExists(String table, String column) throws SQLException {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "select 1 from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?")) {
                stmt.setString(1, table);
                stmt.setString(2, column);
                try (ResultSet r = stmt.executeQuery()) { return r.next(); }
            }
        }
    }
}

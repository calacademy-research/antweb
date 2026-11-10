package org.calacademy.antweb.util;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * The specimen extract for a collaborator: every specimen of a set of countries, with a manifest that proves the
 * file is complete. The engine behind the admin page dataExtract.do (DataExtractJob runs it as a background job);
 * a port of tools/export/specimen_extract.py, which an administrator runs on a server, and it writes the same
 * files with the same columns (2026-11-10, Brian Fisher: the Ants of the Nearctic project's extract of March 2026
 * was short, Stenamma 14 of 42 species, and nothing in it said so).
 *
 * Files written into one folder:
 *   specimens.csv.gz    every specimen whose country (or island country) is in the list: every column of the
 *                       specimen table, then taxon_status, taxon_fossil, taxon_rank, taxon_current_valid_name,
 *                       taxon_author_date, group_name, no_taxon_row and has_coordinates. Specimens without
 *                       coordinates and specimens whose name has no taxon row are in the file, marked.
 *   genus_summary.csv   per genus: specimens, names, names with a specimen with coordinates (and the same for the
 *                       chosen bioregion).
 *   bioregion_list.csv  with a bioregion: AntWeb's species list of it, each name with its specimens in the extract.
 *   manifest.json/.txt  filters, commit, date, counts by country and bioregion, without coordinates, no taxon row,
 *                       flagged, the sha256 of each file, and the check.
 *
 * The check ("verified"): the rows written per (country, island country), by the exact spelling of the names, are
 * compared with a separate count(*) taken after the export. They must be equal.
 *
 * Which specimens: a requested name must be a valid country row (an old name is refused, with the valid one). The
 * specimens still filed under an old name of a requested country (a country row that is not valid and names the
 * country as valid_name: USA for United States) are included, named and counted in the manifest; a requested
 * country with no specimen at all is a WARNING line.
 *
 * Reads only: the connection is set read-only and READ COMMITTED for the run. The specimens are read in pages by
 * primary key (code greater than the last one written, in code order, PAGE_ROWS at a time), each page streamed
 * one row at a time: memory stays flat whatever the size (staging has 4 GB), and no statement or transaction
 * stays open between pages, so a change of the tables (ALTER TABLE) waits for one page at most, not for the whole
 * extract. The price: the file is not one moment of the database. A record changed while the extract runs is in
 * the file as it was when its page was read; a record added behind the page being read is not in it, and the
 * count after the last page then differs (not verified). Uploads started from a page never run beside an extract
 * (one background job at a time).
 *
 * Values are written as the database prints them (numbers and dates from the raw text of the answer, not through
 * Java's number formatting), NULL as an empty field. The session's results are asked for as utf8mb4 for the run,
 * so a four-byte character comes out whole whatever the pool's own setting (the pools say characterSetResults=utf8,
 * which the driver in use sends as utf8mb4; the extract does not depend on that).
 *
 * Tests: test/org/calacademy/antweb/util/DataExtractTest.java (no database), DataExtractDbTest.java (MySQL),
 * tests/e2e/test_data_extract.py. Docs: docs/guides/data-extract.md.
 */
public final class DataExtract {

    private DataExtract() {}

    private static final org.apache.commons.logging.Log s_log = org.apache.commons.logging.LogFactory.getLog(DataExtract.class);

    /** The format of the files; the same number as tools/export/specimen_extract.py while they write the same. */
    public static final String VERSION = "4";

    /** Rows read by one statement (one page). */
    public static final int PAGE_ROWS = 10000;

    /** While a file is being written it has this ending; it gets its name when it is complete. */
    public static final String PART = ".part";

    public static final String SPECIMENS = "specimens.csv.gz";
    public static final String GENUS_SUMMARY = "genus_summary.csv";
    public static final String BIOREGION_LIST = "bioregion_list.csv";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String MANIFEST_TXT = "manifest.txt";

    /** A place name as AntWeb writes it: no control characters, semicolons or backslashes, at most 64 characters. */
    static final Pattern PLACE_NAME = Pattern.compile("[^\\x00-\\x1f;\\\\]{1,64}");

    /** Rows between two progress reports. */
    static final int PROGRESS_EVERY = 5000;

    // ------------------------------------------------------------------ what to extract

    /** The filters of one extract. countries: names as the geolocale table writes them (resolve()). */
    public static final class Request {
        public final List<String> countries;
        public final boolean islands;
        public final String bioregion;

        public Request(List<String> countries, boolean islands, String bioregion) {
            this.countries = new ArrayList<>(countries);
            this.islands = islands;
            this.bioregion = bioregion == null ? "" : bioregion.trim();
        }
    }

    /** What the manifest says about the site: the time, the commit and the version. */
    public static final class Site {
        public final Date now;
        public final String commit;
        public final String version;

        public Site(Date now, String commit, String version) {
            this.now = now;
            this.commit = commit == null || commit.isEmpty() ? "unknown" : commit;
            this.version = version == null ? "" : version;
        }
    }

    /** The outcome: the manifest (as written to manifest.json), its text, and the files written. */
    public static final class Result {
        public final Map<String, Object> manifest;
        public final String text;
        public final boolean verified;
        public final long specimens;
        public final List<String> files;

        public Result(Map<String, Object> manifest, String text, boolean verified, long specimens, List<String> files) {
            this.manifest = manifest;
            this.text = text;
            this.verified = verified;
            this.specimens = specimens;
            this.files = files;
        }
    }

    /** A request that cannot be run, with a message for the person who made it. */
    public static final class BadRequest extends Exception {
        public BadRequest(String message) { super(message); }
    }

    /** Thrown by the progress callback to stop a run (disk nearly full, too long); the message is for the person. */
    public static final class Stopped extends RuntimeException {
        public Stopped(String message) { super(message); }
    }

    // ------------------------------------------------------------------ names

    /** "United States, Canada ,Mexico" -> the three names (also split at line ends), in order, once each. */
    public static List<String> parseCountries(String text) throws BadRequest {
        Set<String> names = new LinkedHashSet<>();
        for (String part : (text == null ? "" : text).split("[,\\r\\n]")) {
            String n = part.trim();
            if (n.isEmpty()) continue;
            if (!PLACE_NAME.matcher(n).matches()) throw new BadRequest("Not a place name: " + n);
            names.add(n);
        }
        return new ArrayList<>(names);
    }

    /**
     * The country rows of the names, as the geolocale table spells them (the database compares, so "united states"
     * finds United States). A name with no country row, or whose row is not valid (an old name; its valid name is
     * given when the row has one), is a BadRequest that says so.
     */
    public static List<String> resolve(Connection c, List<String> names) throws SQLException, BadRequest {
        if (names == null || names.isEmpty()) {
            throw new BadRequest("Give at least one country, as AntWeb writes it (for example United States).");
        }
        Set<String> found = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        try (PreparedStatement stmt = c.prepareStatement(
            "select name, is_valid, valid_name from geolocale where georank = 'country' and name = ? order by is_valid desc, name")) {
            for (String name : names) {
                if (name == null || !PLACE_NAME.matcher(name).matches()) throw new BadRequest("Not a place name: " + name);
                stmt.setString(1, name);
                try (ResultSet rset = stmt.executeQuery()) {
                    if (!rset.next()) {
                        unknown.add(name);
                        continue;
                    }
                    String validName = rset.getString("valid_name");
                    if (rset.getInt("is_valid") == 1) {
                        found.add(rset.getString("name"));
                    } else if (validName != null && !validName.trim().isEmpty()) {
                        invalid.add(name + " (AntWeb files its specimens under " + validName.trim() + ")");
                    } else {
                        invalid.add(name + " (AntWeb keeps this name only as one that is not valid)");
                    }
                }
            }
        }
        if (!unknown.isEmpty() || !invalid.isEmpty()) {
            StringBuilder m = new StringBuilder();
            if (!unknown.isEmpty()) {
                m.append("AntWeb has no country named: ").append(String.join(", ", unknown))
                 .append(". Write the names as on AntWeb's country pages.");
            }
            if (!invalid.isEmpty()) {
                if (m.length() > 0) m.append(" ");
                m.append("Not a valid country name: ").append(String.join("; ", invalid)).append(".");
            }
            throw new BadRequest(m.toString());
        }
        return new ArrayList<>(found);
    }

    /** The bioregion as the bioregion table spells it; "" for none. An unknown one is a BadRequest. */
    public static String resolveBioregion(Connection c, String bioregion) throws SQLException, BadRequest {
        String b = bioregion == null ? "" : bioregion.trim();
        if (b.isEmpty()) return "";
        if (!PLACE_NAME.matcher(b).matches()) throw new BadRequest("Not a bioregion name: " + b);
        try (PreparedStatement stmt = c.prepareStatement("select name from bioregion where name = ?")) {
            stmt.setString(1, b);
            try (ResultSet rset = stmt.executeQuery()) {
                if (rset.next()) return rset.getString(1);
            }
        }
        throw new BadRequest("AntWeb has no bioregion named: " + b + ".");
    }

    /**
     * The islands AntWeb treats as countries (is_island = 1) whose geolocale row names one of the countries as
     * parent or country, sorted; the countries themselves are not repeated.
     */
    public static List<String> islands(Connection c, List<String> countries) throws SQLException {
        String list = sqlList(countries);
        TreeSet<String> islands = new TreeSet<>();
        try (Statement stmt = c.createStatement();
             ResultSet rset = stmt.executeQuery("select name from geolocale where is_island = 1 and (parent in " + list
                 + " or country in " + list + ")")) {
            while (rset.next()) {
                String name = rset.getString(1);
                if (name != null && !countries.contains(name)) islands.add(name);
            }
        }
        return new ArrayList<>(islands);
    }

    /**
     * The old names of the countries: geolocale country rows that are not valid and name one of the countries as
     * valid_name (USA for United States). Each as {"name", "valid_name" (the requested country)}, by name; a
     * requested name is never an old name, and a name pointing to two requested countries counts for the first.
     */
    public static List<Map<String, Object>> outdatedNames(Connection c, List<String> countries) throws SQLException {
        Map<String, String> found = new TreeMap<>();
        try (PreparedStatement stmt = c.prepareStatement(
            "select name from geolocale where georank = 'country' and (is_valid is null or is_valid <> 1) and valid_name = ?")) {
            for (String country : countries) {
                stmt.setString(1, country);
                try (ResultSet rset = stmt.executeQuery()) {
                    while (rset.next()) {
                        String name = rset.getString(1);
                        if (name != null && !name.isEmpty() && !countries.contains(name) && !found.containsKey(name)
                            && PLACE_NAME.matcher(name).matches()) found.put(name, country);
                    }
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", e.getKey());
            o.put("valid_name", e.getValue());
            out.add(o);
        }
        return out;
    }

    // ------------------------------------------------------------------ SQL (the same as the Python tool's)

    /** A string literal. Names reach here only after PLACE_NAME (no backslash, no control character). */
    public static String sqlString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    static String sqlList(Collection<String> values) {
        StringBuilder b = new StringBuilder("(");
        for (String v : values) {
            if (b.length() > 1) b.append(", ");
            b.append(sqlString(v));
        }
        return b.append(")").toString();
    }

    public static String whereSql(List<String> countries, List<String> islands) {
        String w = "s.country in " + sqlList(countries);
        if (!islands.isEmpty()) w += " or s.island_country in " + sqlList(islands);
        return "(" + w + ")";
    }

    /**
     * One page of the export: the specimens of the filter in code order, PAGE_ROWS at most; after the first page
     * only those whose code is greater than the last one written (the "?" of the statement).
     */
    static String pageSql(List<String> countries, List<String> islands, boolean first, int pageRows) {
        return "select s.*, t.status as taxon_status, t.fossil as taxon_fossil, t.taxarank as taxon_rank, "
            + "t.current_valid_name as taxon_current_valid_name, t.author_date as taxon_author_date, g.name as group_name, "
            + "case when t.taxon_name is null then 1 else 0 end as no_taxon_row "
            + "from specimen s left join taxon t on t.taxon_name = s.taxon_name "
            + "left join ant_group g on g.id = s.access_group "
            + "where " + whereSql(countries, islands) + (first ? "" : " and s.code > ?") + " order by s.code limit " + pageRows;
    }

    /**
     * The database's count per (country, island country), grouped by the bytes of the names: MySQL compares names
     * without accents or case ("México" = "Mexico"), so a plain GROUP BY adds two spellings up while the file has
     * them apart. hex(convert(... using utf8mb4)): the bytes are UTF-8 whatever the column's character set.
     */
    static String countSql(List<String> countries, List<String> islands) {
        return "select hex(convert(ifnull(s.country, '') using utf8mb4)), hex(convert(ifnull(s.island_country, '') using utf8mb4)), "
            + "count(*) from specimen s where " + whereSql(countries, islands) + " group by 1, 2 order by 1, 2";
    }

    /** (index of the country, its specimens in the database, also under its old names): 0 is worth a warning. */
    static String perCountrySql(List<String> countries, List<Map<String, Object>> outdated) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < countries.size(); i++) {
            List<String> names = new ArrayList<>();
            names.add(countries.get(i));
            for (Map<String, Object> o : outdated) if (countries.get(i).equals(o.get("valid_name"))) names.add((String) o.get("name"));
            if (i > 0) b.append(" union all ");
            b.append("select ").append(i).append(", count(*) from specimen s where s.country in ").append(sqlList(names));
        }
        return b.append(" /* per country */").toString();
    }

    /** The text of hex(...): the bytes as UTF-8. */
    public static String unhex(String hex) {
        if (hex == null) return "";
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String bioregionListSql(String bioregion) {
        return "select bt.taxon_name, ifnull(t.subfamily, ''), ifnull(t.genus, ''), ifnull(t.species, ''), ifnull(t.subspecies, ''), "
            + "ifnull(t.taxarank, ''), ifnull(t.status, ''), ifnull(t.fossil, ''), ifnull(bt.source, ''), ifnull(bt.is_endemic, ''), "
            + "ifnull(bt.is_introduced, '') from bioregion_taxon bt left join taxon t on t.taxon_name = bt.taxon_name "
            + "where bt.bioregion_name = " + sqlString(bioregion) + " and (t.taxarank in ('species', 'subspecies') or t.taxon_name is null) "
            + "order by t.subfamily, t.genus, bt.taxon_name";
    }

    // ------------------------------------------------------------------ the counts

    /** True for a latitude and longitude that are numbers in range and not 0/0. */
    public static boolean hasCoordinates(String lat, String lon) {
        double la, lo;
        try {
            la = Double.parseDouble(lat.trim());
            lo = Double.parseDouble(lon.trim());
        } catch (RuntimeException e) {
            return false;
        }
        return !(la == 0 && lo == 0) && la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
    }

    static final class Genus {
        long specimens;
        final Set<String> names = new java.util.HashSet<>();
        final Set<String> namesCoords = new java.util.HashSet<>();
        long bioSpecimens;
        final Set<String> bioNames = new java.util.HashSet<>();
    }

    /** What the manifest and the summaries count, row by row. Memory: one entry per name and per genus. */
    static final class Stats {
        final String bioregion;
        /** The old country names included (exact spelling), and the rows filed under one of them. */
        final Set<String> outdated = new java.util.HashSet<>();
        long filedOutdated;
        long rows;
        final Map<List<String>, Long> byPlace = new TreeMap<>(DataExtract::compareLists);   // (country, island_country)
        final Map<String, Long> byBioregion = new TreeMap<>();
        final Map<String, Long> byStatus = new TreeMap<>();
        long noCoordinates;
        long noTaxonRow;
        long flagged;
        long notAnts;
        final Map<List<String>, Genus> genus = new TreeMap<>(DataExtract::compareLists);    // (subfamily, genus)
        final Map<String, Long> nameSpecimens = new HashMap<>();
        final Map<String, Long> nameSpecimensBio = new HashMap<>();

        Stats(String bioregion) {
            this.bioregion = bioregion == null ? "" : bioregion;
        }

        /** Counts one row (column name -> value, "" for NULL); returns whether it has coordinates. */
        boolean add(Map<String, String> row) {
            rows++;
            byPlace.merge(List.of(get(row, "country"), get(row, "island_country")), 1L, Long::sum);
            if (outdated.contains(get(row, "country"))) filedOutdated++;
            String bio = get(row, "bioregion");
            byBioregion.merge(bio, 1L, Long::sum);
            String status = get(row, "taxon_status");
            byStatus.merge(status.isEmpty() ? "(no taxon row)" : status, 1L, Long::sum);
            boolean coords = hasCoordinates(get(row, "decimal_latitude"), get(row, "decimal_longitude"));
            if (!coords) noCoordinates++;
            if ("1".equals(get(row, "no_taxon_row"))) noTaxonRow++;
            if (!get(row, "flag").isEmpty()) flagged++;
            if (!"formicidae".equals(get(row, "family").toLowerCase(Locale.ROOT))) notAnts++;
            String name = get(row, "taxon_name");
            Genus g = genus.computeIfAbsent(List.of(get(row, "subfamily").toLowerCase(Locale.ROOT),
                get(row, "genus").toLowerCase(Locale.ROOT)), k -> new Genus());
            g.specimens++;
            g.names.add(name);
            if (coords) g.namesCoords.add(name);
            nameSpecimens.merge(name, 1L, Long::sum);
            if (!bioregion.isEmpty() && bio.equals(bioregion)) {
                g.bioSpecimens++;
                g.bioNames.add(name);
                nameSpecimensBio.merge(name, 1L, Long::sum);
            }
            return coords;
        }

        private static String get(Map<String, String> row, String column) {
            String v = row.get(column);
            return v == null ? "" : v;
        }
    }

    static int compareLists(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = a.get(i).compareTo(b.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(a.size(), b.size());
    }

    /** The rows written per (country, island country) against the database's count: the differences (none = verified). */
    public static List<Map<String, Object>> compareCounts(Map<List<String>, Long> written, Map<List<String>, Long> database) {
        TreeSet<List<String>> keys = new TreeSet<>(DataExtract::compareLists);
        keys.addAll(written.keySet());
        keys.addAll(database.keySet());
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (List<String> key : keys) {
            long a = written.getOrDefault(key, 0L);
            long b = database.getOrDefault(key, 0L);
            if (a != b) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("country", key.get(0));
                d.put("island_country", key.get(1));
                d.put("in_file", a);
                d.put("in_database", b);
                diffs.add(d);
            }
        }
        return diffs;
    }

    // ------------------------------------------------------------------ CSV (as Python's csv module writes it)

    /** One CSV field: quoted only when it holds a comma, a quote or a line break; quotes doubled. */
    public static String csvField(String value) {
        String s = value == null ? "" : value;
        boolean quote = false;
        for (int i = 0; i < s.length() && !quote; i++) {
            char ch = s.charAt(i);
            quote = ch == ',' || ch == '"' || ch == '\n' || ch == '\r';
        }
        return quote ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** One CSV line, ended with CR LF. */
    public static void csvRow(Writer w, List<?> fields) throws IOException {
        if (fields.size() == 1 && String.valueOf(fields.get(0)).isEmpty()) {
            w.write("\"\"\r\n");   // a lone empty field would otherwise be an empty line
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) w.write(',');
            Object f = fields.get(i);
            w.write(csvField(f == null ? "" : String.valueOf(f)));
        }
        w.write("\r\n");
    }

    private static Writer utf8Writer(File file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ the values of a row

    /** True for the column types whose text is taken as the server sent it (numbers, dates). */
    public static boolean isRawText(int sqlType) {
        switch (sqlType) {
            case Types.BIT: case Types.BOOLEAN: case Types.TINYINT: case Types.SMALLINT: case Types.INTEGER: case Types.BIGINT:
            case Types.FLOAT: case Types.REAL: case Types.DOUBLE: case Types.NUMERIC: case Types.DECIMAL:
            case Types.DATE: case Types.TIME: case Types.TIMESTAMP:
                return true;
            default:
                return false;
        }
    }

    /**
     * A column's value as text, "" for NULL. Numbers and dates come from the bytes of the answer (a plain statement
     * gets them as text: 37.9, 0, 2026-11-10 06:49:21, 0000-00-00), so they read as the database prints them; text
     * columns through the driver, which knows their character set.
     */
    static String value(ResultSet rset, int column, boolean raw) throws SQLException {
        if (raw) {
            byte[] bytes = rset.getBytes(column);
            return bytes == null ? "" : new String(bytes, StandardCharsets.ISO_8859_1);
        }
        String s = rset.getString(column);
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ the run

    /**
     * Writes the extract into dir (an existing, empty folder) and returns the manifest. The connection is used
     * read-only, READ COMMITTED, with results as utf8mb4, and is given back as it was. progress, if not null, gets
     * the number of rows written every PROGRESS_EVERY rows; it may throw Stopped to end the run.
     */
    public static Result run(Connection c, File dir, Request request, Site site, LongConsumer progress)
        throws SQLException, IOException, BadRequest {
        return run(c, dir, request, site, progress, null, PAGE_ROWS);
    }

    /**
     * As run(); afterExport, if not null, runs between the export and the count of the database (the tests use it
     * to change the database at that moment, as an upload during an extract would).
     */
    public static Result run(Connection c, File dir, Request request, Site site, LongConsumer progress, Runnable afterExport)
        throws SQLException, IOException, BadRequest {
        return run(c, dir, request, site, progress, afterExport, PAGE_ROWS);
    }

    /** As run(), with the number of rows of a page (the tests use small pages). */
    public static Result run(Connection c, File dir, Request request, Site site, LongConsumer progress, Runnable afterExport,
                             int pageRows) throws SQLException, IOException, BadRequest {
        if (pageRows < 1) throw new IllegalArgumentException("pageRows " + pageRows);
        boolean wasReadOnly = c.isReadOnly();
        boolean wasAutoCommit = c.getAutoCommit();
        int wasIsolation = c.getTransactionIsolation();
        String wasResults = null;
        boolean resultsChanged = false;
        try {
            if (!wasAutoCommit) c.rollback();   // nothing of ours; a clean start for the settings below
            c.setAutoCommit(true);              // every statement on its own: no transaction stays open between pages
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            wasResults = sessionValue(c, "select @@character_set_results");
            exec(c, "set character_set_results = utf8mb4");
            resultsChanged = true;
            c.setReadOnly(true);
            return export(c, dir, request, site, progress, afterExport, pageRows);
        } finally {
            // A setting that cannot be put back is not passed over in silence: the pool tests a connection when it
            // comes back, and one that fails here is most likely broken and is then thrown away.
            try { c.setReadOnly(wasReadOnly); } catch (SQLException e) { s_log.error("run() read-only not put back e:" + e); }
            if (resultsChanged) {
                try {
                    exec(c, "set character_set_results = " + (wasResults != null && wasResults.matches("[A-Za-z0-9_]+") ? wasResults : "NULL"));
                } catch (SQLException e) {
                    s_log.error("run() character_set_results not put back e:" + e);
                }
            }
            try { c.setTransactionIsolation(wasIsolation); } catch (SQLException e) { s_log.error("run() isolation not put back e:" + e); }
            try { c.setAutoCommit(wasAutoCommit); } catch (SQLException e) { s_log.error("run() autocommit not put back e:" + e); }
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement stmt = c.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static String sessionValue(Connection c, String sql) throws SQLException {
        try (Statement stmt = c.createStatement(); ResultSet rset = stmt.executeQuery(sql)) {
            return rset.next() ? rset.getString(1) : null;
        }
    }

    /**
     * Numbers and dates are taken from the bytes of the answer, which are text only when the statement is not
     * prepared on the server (the application's pools do not ask for that). Checked once, so a change of the
     * connection settings gives an error and not a file of unreadable numbers.
     */
    static void checkTextAnswers(Connection c) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement("select 12345, cast('2026-11-10' as date) from dual where 1 = ?")) {
            stmt.setInt(1, 1);
            try (ResultSet rset = stmt.executeQuery()) {
                if (rset.next() && "12345".equals(value(rset, 1, true)) && "2026-11-10".equals(value(rset, 2, true))) return;
            }
        }
        throw new SQLException("the database connection does not send numbers and dates as text (server-side prepared statements?): no extract written");
    }

    private static Result export(Connection c, File dir, Request request, Site site, LongConsumer progress, Runnable afterExport,
                                 int pageRows) throws SQLException, IOException, BadRequest {
        List<String> countries = resolve(c, request.countries);
        String bioregion = resolveBioregion(c, request.bioregion);
        List<String> islands = request.islands ? islands(c, countries) : new ArrayList<>();
        // Specimens still filed under an old name of a requested country are part of the extract.
        List<Map<String, Object>> outdated = outdatedNames(c, countries);
        List<String> filter = new ArrayList<>(countries);
        for (Map<String, Object> o : outdated) filter.add((String) o.get("name"));
        checkTextAnswers(c);

        Stats stats = new Stats(bioregion);
        for (Map<String, Object> o : outdated) stats.outdated.add((String) o.get("name"));
        File specimens = new File(dir, SPECIMENS);
        File part = new File(dir, SPECIMENS + PART);
        List<String> header = writeSpecimens(c, filter, islands, part, stats, progress, pageRows);
        Files.move(part.toPath(), specimens.toPath(), StandardCopyOption.ATOMIC_MOVE);
        if (afterExport != null) afterExport.run();

        Map<List<String>, Long> database = new TreeMap<>(DataExtract::compareLists);
        try (Statement stmt = c.createStatement(); ResultSet rset = stmt.executeQuery(countSql(filter, islands))) {
            while (rset.next()) database.merge(List.of(unhex(rset.getString(1)), unhex(rset.getString(2))), rset.getLong(3), Long::sum);
        }
        List<Map<String, Object>> diffs = compareCounts(stats.byPlace, database);
        boolean verified = diffs.isEmpty();

        // A requested country without a single specimen (also under its old names) is worth a warning.
        List<String> empty = new ArrayList<>();
        Map<Integer, Long> perCountry = new HashMap<>();
        try (Statement stmt = c.createStatement(); ResultSet rset = stmt.executeQuery(perCountrySql(countries, outdated))) {
            while (rset.next()) perCountry.put(rset.getInt(1), rset.getLong(2));
        }
        for (int i = 0; i < countries.size(); i++) if (perCountry.getOrDefault(i, 0L) == 0) empty.add(countries.get(i));

        List<File> files = new ArrayList<>();
        files.add(specimens);
        File genusFile = new File(dir, GENUS_SUMMARY);
        writeGenusSummary(genusFile, stats);
        files.add(genusFile);
        Map<String, Object> bio = null;
        if (!bioregion.isEmpty()) {
            List<List<String>> rows = new ArrayList<>();
            try (Statement stmt = c.createStatement(); ResultSet rset = stmt.executeQuery(bioregionListSql(bioregion))) {
                while (rset.next()) {
                    List<String> r = new ArrayList<>();
                    for (int i = 1; i <= 11; i++) {
                        String s = rset.getString(i);
                        r.add(s == null ? "" : s);
                    }
                    rows.add(r);
                }
            }
            File listFile = new File(dir, BIOREGION_LIST);
            long missing = writeBioregionList(listFile, rows, stats);
            bio = new LinkedHashMap<>();
            bio.put("names", (long) rows.size());
            bio.put("names_without_specimen_in_extract", missing);
            files.add(listFile);
        }

        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_version", VERSION);
        m.put("made_utc", utc.format(site.now));
        m.put("antweb_commit", site.commit);
        m.put("antweb_version", site.version);
        m.put("countries", countries);
        m.put("islands", islands);
        m.put("outdated_names", outdated);
        m.put("bioregion", bioregion);
        List<String> columns = new ArrayList<>(header);
        columns.add("has_coordinates");
        m.put("columns", columns);
        m.put("specimens", stats.rows);
        m.put("verified", verified);
        m.put("differences", diffs);
        List<Map<String, Object>> byPlace = new ArrayList<>();
        for (Map.Entry<List<String>, Long> e : stats.byPlace.entrySet()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("country", e.getKey().get(0));
            p.put("island_country", e.getKey().get(1));
            p.put("specimens", e.getValue());
            byPlace.add(p);
        }
        m.put("by_place", byPlace);
        m.put("by_bioregion", new LinkedHashMap<>(stats.byBioregion));
        m.put("by_taxon_status", new LinkedHashMap<>(stats.byStatus));
        m.put("without_coordinates", stats.noCoordinates);
        m.put("no_taxon_row", stats.noTaxonRow);
        m.put("flagged", stats.flagged);
        m.put("not_ants", stats.notAnts);
        m.put("filed_under_outdated_name", stats.filedOutdated);
        m.put("countries_without_specimens", empty);
        m.put("genera", (long) stats.genus.size());
        m.put("names", (long) stats.nameSpecimens.size());
        m.put("bioregion_list", bio);
        // The command-line tool can also write the specimens that have no country at all to a file of their own
        // (--with-no-country) and describes it here; the page does not offer that file, so the key is always null.
        m.put("no_country", null);
        Map<String, Object> fileInfo = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (File f : files) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("sha256", sha256(f));
            info.put("bytes", f.length());
            fileInfo.put(f.getName(), info);
            names.add(f.getName());
        }
        m.put("files", fileInfo);

        String text = manifestText(m);
        try (Writer w = utf8Writer(new File(dir, MANIFEST_TXT))) {
            w.write(text);
        }
        // manifest.json last and in one step: it is the mark of a finished extract (DataExtractFiles offers the
        // files only when it is there and can be read).
        File jsonPart = new File(dir, MANIFEST_JSON + PART);
        try (Writer w = utf8Writer(jsonPart)) {
            w.write(json(m));
        }
        Files.move(jsonPart.toPath(), new File(dir, MANIFEST_JSON).toPath(), StandardCopyOption.ATOMIC_MOVE);
        names.add(MANIFEST_JSON);
        names.add(MANIFEST_TXT);
        return new Result(m, text, verified, stats.rows, names);
    }

    /**
     * Reads the specimens page by page (pageSql) and writes them to file as gzip CSV; returns the column names
     * (without has_coordinates). Each page is one statement, streamed and closed before the next one starts.
     */
    static List<String> writeSpecimens(Connection c, List<String> countries, List<String> islands, File file, Stats stats,
                                       LongConsumer progress, int pageRows) throws SQLException, IOException {
        List<String> header = new ArrayList<>();
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                 new BufferedOutputStream(Files.newOutputStream(file.toPath()), 1 << 16)), StandardCharsets.UTF_8), 1 << 16)) {
            String last = null;
            int codeColumn = -1;
            boolean[] raw = null;
            Map<String, String> row = new HashMap<>();
            List<String> values = new ArrayList<>();
            while (true) {
                int inPage = 0;
                try (PreparedStatement stmt = c.prepareStatement(pageSql(countries, islands, last == null, pageRows),
                         ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    // MySQL's driver sends the rows one at a time only with this fetch size; any other value loads
                    // the whole page into memory first.
                    stmt.setFetchSize(Integer.MIN_VALUE);
                    if (last != null) stmt.setString(1, last);
                    try (ResultSet rset = stmt.executeQuery()) {
                        ResultSetMetaData meta = rset.getMetaData();
                        int n = meta.getColumnCount();
                        if (raw == null) {
                            raw = new boolean[n + 1];
                            for (int i = 1; i <= n; i++) {
                                header.add(meta.getColumnLabel(i));
                                raw[i] = isRawText(meta.getColumnType(i));
                            }
                            codeColumn = header.indexOf("code");
                            if (codeColumn < 0) throw new SQLException("the specimen table has no column code to page by");
                            List<String> first = new ArrayList<>(header);
                            first.add("has_coordinates");
                            csvRow(w, first);
                        } else if (n != header.size()) {
                            throw new SQLException("the specimen table changed while the extract ran (" + header.size() + " columns, now " + n + ")");
                        }
                        while (rset.next()) {
                            values.clear();
                            for (int i = 1; i <= n; i++) {
                                String v = value(rset, i, raw[i]);
                                values.add(v);
                                row.put(header.get(i - 1), v);
                            }
                            last = values.get(codeColumn);
                            inPage++;
                            boolean coords = stats.add(row);
                            values.add(coords ? "1" : "0");
                            csvRow(w, values);
                            if (progress != null && stats.rows % PROGRESS_EVERY == 0) progress.accept(stats.rows);
                        }
                    }
                }
                if (inPage < pageRows) break;
            }
        }
        return header;
    }

    static void writeGenusSummary(File file, Stats stats) throws IOException {
        try (Writer w = utf8Writer(file)) {
            List<Object> head = new ArrayList<>(List.of("subfamily", "genus", "specimens", "names", "names_with_coordinates"));
            if (!stats.bioregion.isEmpty()) {
                head.add("specimens_in_" + stats.bioregion);
                head.add("names_in_" + stats.bioregion);
            }
            csvRow(w, head);
            for (Map.Entry<List<String>, Genus> e : stats.genus.entrySet()) {
                Genus g = e.getValue();
                List<Object> row = new ArrayList<>(List.of(e.getKey().get(0), e.getKey().get(1), g.specimens, g.names.size(), g.namesCoords.size()));
                if (!stats.bioregion.isEmpty()) {
                    row.add(g.bioSpecimens);
                    row.add(g.bioNames.size());
                }
                csvRow(w, row);
            }
        }
    }

    /** AntWeb's list of the bioregion with the extract's specimen counts; returns the names with no specimen here. */
    static long writeBioregionList(File file, List<List<String>> rows, Stats stats) throws IOException {
        long missing = 0;
        try (Writer w = utf8Writer(file)) {
            csvRow(w, List.of("taxon_name", "subfamily", "genus", "species", "subspecies", "rank", "status", "fossil", "list_source",
                "is_endemic", "is_introduced", "specimens_in_extract", "specimens_in_extract_in_" + stats.bioregion));
            for (List<String> r : rows) {
                long all = stats.nameSpecimens.getOrDefault(r.get(0), 0L);
                long bio = stats.nameSpecimensBio.getOrDefault(r.get(0), 0L);
                if (all == 0) missing++;
                List<Object> row = new ArrayList<>(r);
                row.add(all);
                row.add(bio);
                csvRow(w, row);
            }
        }
        return missing;
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            try (InputStream in = Files.newInputStream(file.toPath())) {
                int n;
                while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ the manifest as text

    private static String n(Object number) {
        return String.format(Locale.ROOT, "%,d", ((Number) number).longValue());
    }

    @SuppressWarnings("unchecked")
    public static String manifestText(Map<String, Object> m) {
        List<String> lines = new ArrayList<>();
        lines.add("AntWeb specimen extract");
        lines.add("");
        lines.add("Made: " + m.get("made_utc") + " (UTC)   AntWeb commit: " + m.get("antweb_commit") + "   tool version " + m.get("tool_version"));
        lines.add("Countries: " + String.join(", ", (List<String>) m.get("countries")));
        List<String> islands = (List<String>) m.get("islands");
        lines.add("Islands included: " + (islands.isEmpty() ? "none" : String.join(", ", islands)));
        List<Map<String, Object>> outdated = m.get("outdated_names") == null ? new ArrayList<>() : (List<Map<String, Object>>) m.get("outdated_names");
        List<String> old = new ArrayList<>();
        for (Map<String, Object> o : outdated) old.add(o.get("name") + " (now " + o.get("valid_name") + ")");
        lines.add("Outdated country names included: " + (old.isEmpty() ? "none" : String.join(", ", old)));
        lines.add("");
        lines.add("Specimens in the file: " + n(m.get("specimens")));
        lines.add("Check against the database: " + (Boolean.TRUE.equals(m.get("verified")) ? "VERIFIED, every count equal"
            : "DIFFERENT, see differences; run again"));
        List<String> empty = m.get("countries_without_specimens") == null ? new ArrayList<>() : (List<String>) m.get("countries_without_specimens");
        if (!empty.isEmpty()) {
            lines.add("WARNING: AntWeb has no specimen for: " + String.join(", ", empty) + " (check that these are the countries meant)");
        }
        lines.add("");
        lines.add("By country (country / island country):");
        for (Map<String, Object> p : (List<Map<String, Object>>) m.get("by_place")) {
            String island = (String) p.get("island_country");
            lines.add(String.format(Locale.ROOT, "  %-40s %,10d", p.get("country") + (island.isEmpty() ? "" : " / " + island),
                ((Number) p.get("specimens")).longValue()));
        }
        lines.add("");
        lines.add("By AntWeb bioregion:");
        for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("by_bioregion")).entrySet()) {
            lines.add(String.format(Locale.ROOT, "  %-40s %,10d", e.getKey().isEmpty() ? "(none)" : e.getKey(), ((Number) e.getValue()).longValue()));
        }
        // Spellings of a country in the file other than the names asked for (and the old names listed above).
        TreeSet<String> spellings = new TreeSet<>();
        for (Map<String, Object> p : (List<Map<String, Object>>) m.get("by_place")) spellings.add((String) p.get("country"));
        spellings.removeAll((List<String>) m.get("countries"));
        spellings.removeAll(islands);
        for (Map<String, Object> o : outdated) spellings.remove(o.get("name"));
        if (!spellings.isEmpty()) {
            lines.add("");
            lines.add("Country spellings in the file besides the names asked for (MySQL matches names without accents): "
                + String.join(", ", spellings));
        }
        lines.add("");
        lines.add("Without usable coordinates: " + n(m.get("without_coordinates"))
            + " (in the file, has_coordinates = 0; a cut by a shape file loses them)");
        lines.add("Name has no taxon row: " + n(m.get("no_taxon_row"))
            + " (in the file, no_taxon_row = 1; the API's specimen view leaves these out)");
        lines.add("Red-flagged at upload: " + n(m.get("flagged")) + " (column flag)");
        lines.add("Family not Formicidae: " + n(m.get("not_ants")));
        lines.add("Filed under an outdated country name: " + n(m.get("filed_under_outdated_name") == null ? 0L : m.get("filed_under_outdated_name")));
        lines.add("Genera: " + n(m.get("genera")) + "   distinct names: " + n(m.get("names")));
        String bioregion = (String) m.get("bioregion");
        if (bioregion != null && !bioregion.isEmpty()) {
            Map<String, Object> b = (Map<String, Object>) m.get("bioregion_list");
            lines.add("");
            lines.add("AntWeb's " + bioregion + " species list: " + n(b.get("names")) + " names; "
                + n(b.get("names_without_specimen_in_extract")) + " of them have no specimen in this extract (literature, or other countries)");
        }
        lines.add("");
        lines.add("Files (sha256):");
        for (Map.Entry<String, Object> e : ((Map<String, Object>) m.get("files")).entrySet()) {
            lines.add("  " + ((Map<String, Object>) e.getValue()).get("sha256") + "  " + e.getKey());
        }
        List<Map<String, Object>> diffs = (List<Map<String, Object>>) m.get("differences");
        if (!diffs.isEmpty()) {
            lines.add("");
            lines.add("Differences:");
            for (Map<String, Object> d : diffs) lines.add("  " + jsonLine(d));
        }
        String version = (String) m.get("antweb_version");
        lines.add("");
        lines.add("Spreadsheets: import the columns of specimens.csv as text. Values are written as they are in the database,");
        lines.add("also when they start with =, +, - or @.");
        lines.add("");
        lines.add("Cite: AntWeb. Version " + (version == null || version.isEmpty() ? "[version]" : version)
            + ". California Academy of Sciences, online at https://www.antweb.org. Extract of "
            + String.valueOf(m.get("made_utc")).substring(0, 10) + ".");
        lines.add("Images and data on AntWeb are licensed CC BY 4.0.");
        return String.join("\n", lines) + "\n";
    }

    // ------------------------------------------------------------------ JSON (as Python's json module writes it)

    /** A value as JSON with one space of indent per level, non-ASCII characters as they are (manifest.json). */
    public static String json(Object value) {
        StringBuilder b = new StringBuilder();
        json(b, value, 0, true, false);
        return b.toString();
    }

    /** A flat map on one line, non-ASCII characters escaped (the Differences lines of manifest.txt). */
    public static String jsonLine(Map<String, Object> map) {
        StringBuilder b = new StringBuilder();
        json(b, map, 0, false, true);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void json(StringBuilder b, Object value, int level, boolean indent, boolean ascii) {
        if (value == null) {
            b.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            b.append(value);
        } else if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append('{');
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                b.append(i++ == 0 ? "" : indent ? "," : ", ");
                if (indent) newline(b, level + 1);
                jsonString(b, e.getKey(), ascii);
                b.append(": ");
                json(b, e.getValue(), level + 1, indent, ascii);
            }
            if (indent) newline(b, level);
            b.append('}');
        } else if (value instanceof Collection) {
            Collection<Object> list = (Collection<Object>) value;
            if (list.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append('[');
            int i = 0;
            for (Object o : list) {
                b.append(i++ == 0 ? "" : indent ? "," : ", ");
                if (indent) newline(b, level + 1);
                json(b, o, level + 1, indent, ascii);
            }
            if (indent) newline(b, level);
            b.append(']');
        } else {
            jsonString(b, String.valueOf(value), ascii);
        }
    }

    private static void newline(StringBuilder b, int level) {
        b.append('\n');
        for (int i = 0; i < level; i++) b.append(' ');
    }

    private static void jsonString(StringBuilder b, String s, boolean ascii) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (ch < 0x20 || (ascii && ch > 0x7f)) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
            }
        }
        b.append('"');
    }
}

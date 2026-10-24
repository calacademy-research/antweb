package org.calacademy.antweb.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Schema check (2026-10-22). Brian Fisher: "AntWeb needs to be a source of trusted data ... once we have it right the
 * right stays right." Read-only.
 *
 * The production schema (every table and view, with its columns and their types) was exported on 2026-10-22 and
 * committed as doc/schema/live-schema-2026-10-22.tsv (refreshed as live-schema-2026-10-23.tsv when the old unused
 * tables were dropped); db/upgrade/8.115 brought the repository's SQL to it, and CI
 * (tools/datamodel/list_tables.py --check) keeps the repository's SQL and the snapshot the same. This check keeps the
 * live database and the snapshot the same: it reads information_schema of the database the site uses and lists every
 * table or view added or removed, and every column added, removed or retyped (views: added or removed), since the
 * snapshot. Tables whose names
 * say they are dated backups (BACKUP, the same pattern as list_tables.py BACKUP_PATTERN) are left out: they come and
 * go by hand. The count check (countCheck.do, and daily after the nightly run: CountCheckJob) runs it and shows the
 * result; docs/data-model.md says how to refresh the snapshot after a change made on purpose.
 *
 * The snapshot is read from the checkout the container mounts read-only (doc/ is /antweb/deploy/doc); the newest
 * live-schema-*.tsv there is used.
 */
public final class SchemaCheck {

    /** The folder with the committed snapshots, as the container sees it (docker-compose.yml mounts ./doc there). */
    public static final String DIR = "/antweb/deploy/doc/schema";

    /** Dated backup tables, left out of the comparison. Same pattern as tools/datamodel/list_tables.py BACKUP_PATTERN. */
    public static final Pattern BACKUP = Pattern.compile("(_bak_|_backup_|_deleted_)|_?([0-9]{8}|[0-9]{4}_[0-9]{2}_[0-9]{2})$");

    /** The snapshot's query (docs/data-model.md has the full command). */
    public static final String SQL = "SELECT c.table_name, c.column_name, c.column_type, t.table_type FROM information_schema.columns c"
        + " JOIN information_schema.tables t USING (table_schema, table_name) WHERE c.table_schema = DATABASE()"
        + " ORDER BY c.table_name, c.ordinal_position";

    /** Lines shown at most. */
    static final int MAX_SHOWN = 500;

    private SchemaCheck() {}

    /**
     * A schema: table -> column (lower case) -> type, and table -> table type ("BASE TABLE", "VIEW"). Table names keep
     * their case: production's MySQL tells tables apart by case (proj_taxonT is not proj_taxont).
     */
    public static final class Schema {
        public final Map<String, Map<String, String>> columns = new LinkedHashMap<>();
        public final Map<String, String> kinds = new LinkedHashMap<>();

        void add(String table, String column, String type, String kind) {
            String t = table;
            columns.computeIfAbsent(t, k -> new LinkedHashMap<>()).put(column.toLowerCase(Locale.ROOT), type);
            kinds.put(t, kind == null || kind.isEmpty() ? "BASE TABLE" : kind);
        }
    }

    public static final class Report {
        /** File name of the snapshot used, or null. */
        public String snapshot;
        /** Why the check could not run, or null. */
        public String error;
        public final List<String> differences = new ArrayList<>();
        public int liveTables;
        public int snapshotTables;
        public int ignored;
        public long millis;

        public boolean ok() { return error == null && differences.isEmpty(); }

        /** One line for admin.log and the job summary. */
        public String summary() {
            if (error != null) return "Schema check could not run: " + error + ".";
            return "Schema: " + (differences.isEmpty() ? "no change" : differences.size() + (differences.size() == 1 ? " change" : " changes"))
                + " since the snapshot " + snapshot + ".";
        }
    }

    /**
     * Note: a real table whose name ends in a date (8 digits or YYYY_MM_DD) is treated as a backup too and not reported
     * when it comes or goes; name new tables without a trailing date.
     */
    public static boolean isBackup(String table) {
        return BACKUP.matcher(table.toLowerCase(Locale.ROOT)).find();
    }

    /** Types compared as MySQL 8 shows them: lower case, without the display width of integer types. */
    static String normType(String type) {
        if (type == null) return "";
        String t = type.toLowerCase(Locale.ROOT).replace(" ", "");
        t = t.replaceFirst("^(tinyint|smallint|mediumint|int|bigint)\\(\\d+\\)", "$1");
        if (t.equals("long") || t.equals("longvarchar")) t = "mediumtext";   // MySQL's aliases (as list_tables.py norm_type)
        return t.replace("unsigned", " unsigned");
    }

    /** The snapshot file's lines (tab separated: table, column, type, table type; no header). */
    public static Schema parse(List<String> lines) {
        Schema s = new Schema();
        for (String line : lines) {
            String[] p = line.split("\t", -1);
            if (p.length < 3 || p[0].isEmpty()) continue;
            s.add(p[0], p[1], p[2], p.length > 3 ? p[3] : null);
        }
        return s;
    }

    /** The newest live-schema-*.tsv in dir, or null. */
    public static Path latest(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return null;
        TreeSet<Path> files = new TreeSet<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "live-schema-*.tsv")) {
            for (Path p : ds) files.add(p);
        }
        return files.isEmpty() ? null : files.last();
    }

    /** Every difference of live against the snapshot, dated backup tables left out (counted in report.ignored). */
    public static void compare(Schema snapshot, Schema live, Report report) {
        TreeSet<String> names = new TreeSet<>(snapshot.kinds.keySet());
        names.addAll(live.kinds.keySet());
        for (String t : names) {
            String sk = snapshot.kinds.get(t);
            String lk = live.kinds.get(t);
            if (isBackup(t) && (sk == null || lk == null)) {
                ++report.ignored;
                continue;
            }
            String what = "VIEW".equals(sk != null ? sk : lk) ? "view " : "table ";
            if (sk == null) {
                report.differences.add(what + t + " added (" + live.columns.get(t).size() + " columns)");
                continue;
            }
            if (lk == null) {
                report.differences.add(what + t + " removed");
                continue;
            }
            if (!sk.equals(lk)) report.differences.add("table " + t + " was a " + sk + ", is a " + lk);
            Map<String, String> sc = snapshot.columns.get(t);
            Map<String, String> lc = live.columns.get(t);
            for (Map.Entry<String, String> e : lc.entrySet()) {
                String old = sc.get(e.getKey());
                if (old == null) report.differences.add("column " + t + "." + e.getKey() + " added (" + e.getValue() + ")");
                // a view's column types follow its tables (a widened table column is reported once, on the table)
                else if (!"VIEW".equals(lk) && !normType(old).equals(normType(e.getValue()))) {
                    report.differences.add("column " + t + "." + e.getKey() + " retyped: " + old + " -> " + e.getValue());
                }
            }
            for (String c : sc.keySet()) {
                if (!lc.containsKey(c)) report.differences.add("column " + t + "." + c + " removed");
            }
        }
    }

    /** The live schema of the connection's database (read-only). */
    public static Schema live(Connection connection) throws SQLException {
        Schema s = new Schema();
        try (Statement stmt = connection.createStatement(); ResultSet r = stmt.executeQuery(SQL)) {
            while (r.next()) s.add(r.getString(1), r.getString(2), r.getString(3), r.getString(4));
        }
        return s;
    }

    public static Report run(Connection connection) {
        return run(connection, Paths.get(DIR));
    }

    /** Never throws: a failure is in report.error. */
    public static Report run(Connection connection, Path dir) {
        long start = System.currentTimeMillis();
        Report report = new Report();
        try {
            Path file = latest(dir);
            if (file == null) {
                report.error = "no snapshot live-schema-*.tsv in " + dir;
            } else {
                report.snapshot = file.getFileName().toString();
                Schema snapshot = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
                Schema live = live(connection);
                report.snapshotTables = snapshot.kinds.size();
                report.liveTables = live.kinds.size();
                if (live.kinds.isEmpty()) report.error = "information_schema returned no tables";
                else compare(snapshot, live, report);
            }
        } catch (IOException | SQLException | RuntimeException e) {
            report.error = e.toString();
        }
        report.millis = System.currentTimeMillis() - start;
        return report;
    }

    /** The report's section of the count check page (ids for the end-to-end test). */
    public static String html(Report report) {
        StringBuilder b = new StringBuilder("<h3>Database structure</h3>");
        b.append("<p id=\"schema-check\" data-differences=\"").append(report.differences.size()).append("\" data-error=\"")
         .append(report.error == null ? 0 : 1).append("\">");
        if (report.error != null) {
            b.append("<b>The schema check could not run:</b> ").append(Html.esc(report.error)).append("</p>");
            return b.toString();
        }
        b.append("The tables and columns of the database compared with the snapshot ").append(Html.esc(report.snapshot))
         .append(" (doc/schema; docs/data-model.md): ").append(report.liveTables).append(" tables and views now, ")
         .append(report.snapshotTables).append(" in the snapshot");
        if (report.ignored > 0) b.append("; ").append(report.ignored).append(" dated backup tables left out");
        b.append(". ");
        if (report.differences.isEmpty()) {
            b.append("No change.</p>");
            return b.toString();
        }
        b.append("<b>").append(report.differences.size()).append(" changes</b> since the snapshot. A change made on purpose"
            + " (an upgrade file run, an old table dropped) needs the snapshot refreshed and the repository's db/upgrade updated;"
            + " any other change was made by hand on the server and should be explained.</p><ul id=\"schema-differences\">");
        int n = 0;
        for (String d : report.differences) {
            if (++n > MAX_SHOWN) {
                b.append("<li>... ").append(report.differences.size() - MAX_SHOWN).append(" more</li>");
                break;
            }
            b.append("<li class=\"schema-difference\">").append(Html.esc(d)).append("</li>");
        }
        b.append("</ul>");
        return b.toString();
    }
}

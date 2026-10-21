package org.calacademy.antweb.upload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Table specimen_name_update: one row per specimen moved (mode 'changed') or that would be moved
 * (mode 'would_change', report mode and the Preview button) by the nightly name update
 * (SpecimenNameUpdate), and per specimen held back for the curator because its name belongs to two or
 * more AntCat taxa (mode 'shared_name', new_name empty; replaced at each run), and per specimen left on a
 * name AntCat calls valid while its uploaded name points elsewhere (mode 'kept_valid', new_name where the
 * uploaded name points; replaced at each run). Read by the Name
 * updates page (nameUpdates.do).
 *
 * ensureSchema() creates the table when missing (same DDL: db/upgrade/8.113/2026-10-20-specimen-name-update.sql),
 * in the character set and collation of taxon.taxon_name, as CuratorTaxonSetDb does for taxon_set_parked.
 */
public class SpecimenNameUpdateDb {

    private final Connection connection;

    public SpecimenNameUpdateDb(Connection connection) {
        this.connection = connection;
    }

    public static String createTable(String charset, String collation) {
        String defaults = "";
        if (charset != null && charset.matches("[a-z0-9_]+")) defaults += " DEFAULT CHARSET=" + charset;
        if (collation != null && collation.matches("[a-z0-9_]+")) defaults += " COLLATE=" + collation;
        return "CREATE TABLE IF NOT EXISTS specimen_name_update ("
            + " id int NOT NULL AUTO_INCREMENT,"
            + " run_date datetime NOT NULL,"
            + " mode varchar(12) NOT NULL,"
            + " code varchar(128) NOT NULL,"
            + " access_group int NOT NULL,"
            + " uploaded_name varchar(128) NOT NULL,"
            + " old_name varchar(128) NOT NULL,"
            + " new_name varchar(128) NOT NULL,"
            + " antcat_status varchar(40) NULL,"
            + " reason varchar(500) NULL,"
            + " antcat_id int NULL,"
            + " PRIMARY KEY (id),"
            + " KEY by_group_date (access_group, run_date),"
            + " KEY by_date (run_date),"
            + " KEY by_code (code)"
            + ") ENGINE=InnoDB" + defaults;
    }

    private static volatile boolean s_schemaChecked = false;

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
            // Added 2026-10-20 (reason and AntCat id of each move): tables made before get the columns.
            boolean hasReason;
            try (ResultSet rset = stmt.executeQuery("select count(*) from information_schema.columns"
                    + " where table_schema = database() and table_name = 'specimen_name_update' and column_name = 'reason'")) {
                hasReason = rset.next() && rset.getInt(1) > 0;
            }
            if (!hasReason) stmt.executeUpdate(ADD_REASON);
        }
        s_schemaChecked = true;
    }

    /** Same as db/upgrade/8.113/2026-10-20-specimen-name-update.sql. */
    public static final String ADD_REASON = "ALTER TABLE specimen_name_update ADD COLUMN reason varchar(500) NULL,"
        + " ADD COLUMN antcat_id int NULL, ALGORITHM=INSTANT";

    /** Removes the previous preview (would_change rows). The changed rows are kept: they are the history and the undo record. */
    public int deletePreview() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate("delete from specimen_name_update where mode = '" + SpecimenNameUpdate.WOULD_CHANGE + "'");
        }
    }

    /** Removes the previous list of specimens held back for the curator (shared names): each run makes a new one. */
    public int deleteShared() throws SQLException {
        return deleteMode(SpecimenNameUpdate.SHARED);
    }

    /** Removes the rows of a list replaced at each run (shared_name, kept_valid). Never the changed rows (the history). */
    public int deleteMode(String mode) throws SQLException {
        if (SpecimenNameUpdate.CHANGED.equals(mode)) throw new IllegalArgumentException("the changed rows are the history");
        try (PreparedStatement stmt = connection.prepareStatement("delete from specimen_name_update where mode = ?")) {
            stmt.setString(1, mode);
            return stmt.executeUpdate();
        }
    }

    public int insert(Timestamp runDate, String mode, List<SpecimenNameUpdate.Change> changes) throws SQLException {
        String dml = "insert into specimen_name_update (run_date, mode, code, access_group, uploaded_name, old_name, new_name, antcat_status, reason, antcat_id)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int n = 0;
        try (PreparedStatement stmt = connection.prepareStatement(dml)) {
            for (SpecimenNameUpdate.Change c : changes) {
                for (String[] specimen : c.specimens) {
                    stmt.setTimestamp(1, runDate);
                    stmt.setString(2, mode);
                    stmt.setString(3, specimen[0]);
                    stmt.setInt(4, c.groupId);
                    stmt.setString(5, c.uploadedName);
                    stmt.setString(6, c.oldName);
                    stmt.setString(7, c.newName);
                    stmt.setString(8, c.antcatStatus);
                    stmt.setString(9, c.reason == null ? null : c.reason.text);
                    if (c.reason == null || c.reason.antcatId == null) stmt.setNull(10, java.sql.Types.INTEGER);
                    else stmt.setInt(10, c.reason.antcatId);
                    stmt.addBatch();
                    if (++n % 1000 == 0) stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        return n;
    }

    // ------------------------------------------------------------ for the page

    /** One line of the page: the specimens of one group moved from one name to another in one run. */
    public static final class Line {
        public Timestamp runDate;
        public String mode;
        public int groupId;
        public String oldName;
        public String newName;
        public String uploadedName;
        public String antcatStatus;
        public String reason;
        public Integer antcatId;
        public int count;
        public final List<String> codes = new ArrayList<>();
    }

    /** A run (date and mode) and its number of specimens. */
    public static final class Run {
        public Timestamp runDate;
        public String mode;
        public int specimens;
    }

    public static final int MAX_CODES_PER_LINE = 200;

    /**
     * Lines of the given runs, in the order of the runs, then group, then old name. groupId 0 = all groups.
     * Specimens of one old and new name with different reasons (uploaded under different names) are separate lines.
     */
    public List<Line> getLines(int groupId, List<Run> runs) throws SQLException {
        return getLines(groupId, runs, false);
    }

    /** byUploadedName: specimens uploaded under different names are separate lines (the kept_valid list). */
    public List<Line> getLines(int groupId, List<Run> runs, boolean byUploadedName) throws SQLException {
        ensureSchema();
        List<Line> lines = new ArrayList<>();
        for (Run run : runs) {
            String query = "select access_group, old_name, new_name, antcat_status, code, uploaded_name, reason, antcat_id from specimen_name_update"
                + " where run_date = ? and mode = ?" + (groupId > 0 ? " and access_group = ?" : "")
                + " order by access_group, old_name, new_name, reason" + (byUploadedName ? ", uploaded_name" : "") + ", code";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setTimestamp(1, run.runDate);
                stmt.setString(2, run.mode);
                if (groupId > 0) stmt.setInt(3, groupId);
                try (ResultSet r = stmt.executeQuery()) {
                    Line line = null;
                    while (r.next()) {
                        int g = r.getInt(1);
                        String oldName = r.getString(2);
                        String newName = r.getString(3);
                        String reason = r.getString(7);
                        if (line == null || line.groupId != g || !line.oldName.equals(oldName) || !line.newName.equals(newName)
                                || !java.util.Objects.equals(line.reason, reason)
                                || (byUploadedName && !java.util.Objects.equals(line.uploadedName, r.getString(6)))) {
                            line = new Line();
                            line.runDate = run.runDate;
                            line.mode = run.mode;
                            line.groupId = g;
                            line.oldName = oldName;
                            line.newName = newName;
                            line.antcatStatus = r.getString(4);
                            line.uploadedName = r.getString(6);
                            line.reason = reason;
                            int id = r.getInt(8);
                            line.antcatId = r.wasNull() ? null : id;
                            lines.add(line);
                        }
                        ++line.count;
                        if (line.codes.size() < MAX_CODES_PER_LINE) line.codes.add(r.getString(5));
                    }
                }
            }
        }
        return lines;
    }

    /** A day as yyyy-mm-dd, or null if it is not one (so it can go into the query). */
    public static String day(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.matches("\\d{4}-\\d{2}-\\d{2}") ? s : null;
    }

    /**
     * The runs that have rows (for groupId, or all when 0), newest first, at most maxRuns. fromDay and
     * toDay (yyyy-mm-dd, inclusive, null = open) limit the run dates.
     */
    public List<Run> getRuns(int groupId, String fromDay, String toDay, int maxRuns) throws SQLException {
        ensureSchema();
        fromDay = day(fromDay);
        toDay = day(toDay);
        List<String> where = new ArrayList<>();
        where.add("mode not in ('" + SpecimenNameUpdate.SHARED + "', '" + SpecimenNameUpdate.KEPT_VALID + "')");   // listed apart: getShared, getKeptValid
        if (groupId > 0) where.add("access_group = ?");
        if (fromDay != null) where.add("run_date >= ?");
        if (toDay != null) where.add("run_date < date_add(?, interval 1 day)");
        String query = "select run_date, mode, count(*) from specimen_name_update"
            + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
            + " group by run_date, mode order by run_date desc, mode limit " + Math.max(1, maxRuns);
        List<Run> runs = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            int i = 1;
            if (groupId > 0) stmt.setInt(i++, groupId);
            if (fromDay != null) stmt.setString(i++, fromDay);
            if (toDay != null) stmt.setString(i, toDay);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) {
                    Run run = new Run();
                    run.runDate = r.getTimestamp(1);
                    run.mode = r.getString(2);
                    run.specimens = r.getInt(3);
                    runs.add(run);
                }
            }
        }
        return runs;
    }

    /**
     * The specimens held back for the curator by the latest run (name shared by two or more AntCat taxa):
     * one line per group, name and uploaded name, its reason listing the candidates. groupId 0 = all groups.
     */
    public List<Line> getShared(int groupId) throws SQLException {
        return getLatest(groupId, SpecimenNameUpdate.SHARED, false);
    }

    /**
     * The names the latest run left on a name AntCat calls valid while the uploaded name points elsewhere
     * (kept_valid): one line per group, name, where the uploaded name points, and uploaded name. groupId 0 = all groups.
     */
    public List<Line> getKeptValid(int groupId) throws SQLException {
        return getLatest(groupId, SpecimenNameUpdate.KEPT_VALID, true);
    }

    private List<Line> getLatest(int groupId, String mode, boolean byUploadedName) throws SQLException {
        ensureSchema();
        List<Run> runs = new ArrayList<>();
        String query = "select run_date, count(*) from specimen_name_update where mode = ?" + (groupId > 0 ? " and access_group = ?" : "")
            + " group by run_date order by run_date desc limit 1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, mode);
            if (groupId > 0) stmt.setInt(2, groupId);
            try (ResultSet r = stmt.executeQuery()) {
                if (r.next()) {
                    Run run = new Run();
                    run.runDate = r.getTimestamp(1);
                    run.mode = mode;
                    run.specimens = r.getInt(2);
                    runs.add(run);
                }
            }
        }
        return getLines(groupId, runs, byUploadedName);
    }

    /** Groups that have rows. */
    public List<Integer> getGroupIds() throws SQLException {
        ensureSchema();
        List<Integer> ids = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet r = stmt.executeQuery("select distinct access_group from specimen_name_update order by access_group")) {
            while (r.next()) ids.add(r.getInt(1));
        }
        return ids;
    }
}

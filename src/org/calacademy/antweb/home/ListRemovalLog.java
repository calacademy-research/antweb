package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The record of every name removed from a country's, adm1's, region's, subregion's or project's species list (Brian
 * Fisher, 2026-11-03: transparency: "We need transparency: transparency in source, and if something is deleted, we
 * still need transparency"). Table list_removal_log, one row per removed list row:
 *
 *   list_type         'geolocale' or 'project'
 *   list_key          the geolocale id or the project name
 *   taxon_name, source  the removed row's name and source
 *   removed_by_login  the curator (Species List Tool), or null
 *   removed_by_job    the job that removed it (the nightly clean-up, the orphan tool, a data script), or null
 *   reason            the curator's reason (Species List Tool, optional) or the job's
 *   removed_at        when
 *
 * Written by the Species List Tool's save (SpeciesListDb.saveTaxonSet, with the reason the curator typed; the dispute
 * row keeps the reason too) and by the jobs that remove names, not by the routine rebuilds (the nightly delete and
 * re-insert of the rows from specimens, the AntCat rows of set 1, the Global list): the nightly clean-up of morphospecies
 * without specimens and of names no longer in AntWeb (TaxonSetDb.dataCleanup; the curators' rows are parked first and
 * come back), and the orphan tool (OrphansDb.deleteTaxon). Rows are recorded just before the delete, by the same
 * condition (recordWhere), in one insert per step. Read by the lists' view All specimen names (ListView.loadRemovals:
 * "removed from the list by ... (date): reason").
 *
 * One-off data scripts write the same record before they delete (docs/data-model.md, list_removal_log):
 *   insert into list_removal_log (list_type, list_key, taxon_name, source, removed_by_job, reason, removed_at)
 *     select 'geolocale', l.geolocale_id, l.taxon_name, l.source, 'script 2026-11-03 <name>', '<why>', now()
 *     from geolocale_taxon l where <the delete's condition on l>;
 * (scriptSql gives it for a list type.)
 *
 * The application creates the table when missing (ensureSchema, at startup and before each write); the record of the
 * change is db/upgrade/8.115/2026-11-03-list-removal-log.sql.
 */
public final class ListRemovalLog {

    private static final Log s_log = LogFactory.getLog(ListRemovalLog.class);

    private ListRemovalLog() {
    }

    public static final String TABLE = "list_removal_log";
    public static final String GEOLOCALE = "geolocale";
    public static final String PROJECT = "project";

    /** The job names written by the code (removed_by_job). */
    public static final String JOB_MORPHO_NO_SPECIMEN = "nightly clean-up: morphospecies with no specimens";
    public static final String JOB_NO_TAXON = "nightly clean-up: name no longer in AntWeb";
    public static final String JOB_ORPHAN = "orphan tool: name deleted";

    /** The reasons the jobs give. */
    public static final String REASON_MORPHO_NO_SPECIMEN = "a morphospecies with no specimens left in AntWeb";
    public static final String REASON_NO_TAXON = "the name is no longer in AntWeb; a curator's entry comes back if the name does";

    public static String createTable(String charset, String collation) {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + " id int NOT NULL AUTO_INCREMENT,"
            + " list_type varchar(16) NOT NULL,"
            + " list_key varchar(128) NOT NULL,"
            + " taxon_name varchar(128) NOT NULL,"
            + " source varchar(64) NULL,"
            + " removed_by_login int NULL,"
            + " removed_by_job varchar(80) NULL,"
            + " reason varchar(255) NULL,"
            + " removed_at datetime NOT NULL,"
            + " PRIMARY KEY (id),"
            + " KEY list_removal_place (list_type, list_key, taxon_name),"
            + " KEY list_removal_at (removed_at)"
            + ") ENGINE=InnoDB" + PlaceGenusCurationDb.defaults(charset, collation);
    }

    private static volatile boolean s_schemaChecked = false;

    /**
     * Creates list_removal_log when missing (in the character set and collation of taxon.taxon_name) and adds the reason
     * column to geolocale_taxon_dispute and proj_taxon_dispute when missing. Safe to run repeatedly; done once per start.
     */
    public static void ensureSchema(Connection connection) throws SQLException {
        if (s_schemaChecked) return;
        String charset = null;
        String collation = null;
        try (Statement stmt = connection.createStatement()) {
            try (ResultSet r = stmt.executeQuery("select character_set_name, collation_name from information_schema.columns"
                    + " where table_schema = database() and table_name = 'taxon' and column_name = 'taxon_name'")) {
                if (r.next()) {
                    charset = r.getString(1);
                    collation = r.getString(2);
                }
            }
            // DDL only when something is missing: a CREATE or ALTER commits the open transaction (the nightly sets' own;
            // GroupCountDb, 2026-10-22), so the table is normally made at startup (LoginMgr)
            if (!tableExists(connection, TABLE)) stmt.executeUpdate(createTable(charset, collation));
            for (String t : new String[] {"geolocale_taxon_dispute", "proj_taxon_dispute"}) {
                if (tableExists(connection, t) && !columnExists(connection, t, "reason")) {
                    stmt.executeUpdate("ALTER TABLE " + t + " ADD COLUMN reason varchar(255) NULL");
                    s_log.warn("ensureSchema() added " + t + ".reason");
                }
            }
        }
        s_schemaChecked = true;
    }

    /** For tests that drop and create the tables. */
    public static void forgetSchema() {
        s_schemaChecked = false;
    }

    /** The list table and key column of a list type. */
    static String table(String listType) {
        if (GEOLOCALE.equals(listType)) return "geolocale_taxon";
        if (PROJECT.equals(listType)) return "proj_taxon";
        throw new IllegalArgumentException(listType);
    }

    static String keyColumn(String listType) {
        if (GEOLOCALE.equals(listType)) return "geolocale_id";
        if (PROJECT.equals(listType)) return "project_name";
        throw new IllegalArgumentException(listType);
    }

    /** A reason or job text as stored: trimmed, at most max characters, null when empty. */
    public static String clip(String text, int max) {
        if (text == null) return null;
        String t = text.replaceAll("\\s+", " ").trim();   // one line
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    /**
     * The insert a one-off data script runs before its delete (job, reason as SQL literals; where: the delete's condition
     * on the list table, alias l).
     */
    public static String scriptSql(String listType, String job, String reason, String where) {
        return "insert into " + TABLE + " (list_type, list_key, taxon_name, source, removed_by_job, reason, removed_at)"
            + " select '" + listType + "', l." + keyColumn(listType) + ", l.taxon_name, l.source, "
            + org.calacademy.antweb.util.DBUtil.sqlLiteral(clip(job, 80)) + ", " + org.calacademy.antweb.util.DBUtil.sqlLiteral(clip(reason, 255))
            + ", now() from " + table(listType) + " l where " + where;
    }

    /**
     * Records the rows of a list table (alias l) that meet where, removed by a job; called just before the delete with
     * the same condition. Returns the rows recorded. A failure is logged and never stops the job (the record is for
     * people; the job's delete is what the data needs).
     */
    public static int recordWhere(Connection connection, String listType, String where, String job) {
        return recordWhere(connection, listType, where, job, null);
    }

    /** The same, with the job's reason. */
    public static int recordWhere(Connection connection, String listType, String where, String job, String reason) {
        try {
            ensureSchema(connection);
            try (Statement stmt = connection.createStatement()) {
                return stmt.executeUpdate(scriptSql(listType, job, reason, where));
            }
        } catch (SQLException | RuntimeException e) {
            s_log.warn("recordWhere() listType:" + listType + " job:" + job + " e:" + e);
            return 0;
        }
    }

    /** One removal by a curator or a job. */
    public static final class Entry {
        public final String listType;
        public final String listKey;
        public final String taxonName;
        public final String source;
        public final Integer login;
        public final String job;
        public final String reason;

        public Entry(String listType, String listKey, String taxonName, String source, Integer login, String job, String reason) {
            this.listType = listType;
            this.listKey = listKey;
            this.taxonName = taxonName;
            this.source = source;
            this.login = login;
            this.job = clip(job, 80);
            this.reason = clip(reason, 255);
        }
    }

    /** Records removals in one batch (the Species List Tool's save). A failure is logged, never thrown. */
    public static int record(Connection connection, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        try {
            ensureSchema(connection);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            int n = 0;
            try (PreparedStatement stmt = connection.prepareStatement("insert into " + TABLE
                    + " (list_type, list_key, taxon_name, source, removed_by_login, removed_by_job, reason, removed_at) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (Entry e : entries) {
                    stmt.setString(1, e.listType);
                    stmt.setString(2, e.listKey);
                    stmt.setString(3, e.taxonName);
                    stmt.setString(4, e.source);
                    if (e.login == null) stmt.setNull(5, java.sql.Types.INTEGER); else stmt.setInt(5, e.login);
                    stmt.setString(6, e.job);
                    stmt.setString(7, e.reason);
                    stmt.setTimestamp(8, now);
                    stmt.addBatch();
                }
                for (int c : stmt.executeBatch()) if (c > 0) n += c;
            }
            return n;
        } catch (SQLException | RuntimeException e) {
            s_log.warn("record() entries:" + entries.size() + " e:" + e);
            return 0;
        }
    }

    static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.tables where table_schema = database() and table_name = ?")) {
            stmt.setString(1, table);
            try (ResultSet r = stmt.executeQuery()) { return r.next(); }
        }
    }

    static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?")) {
            stmt.setString(1, table);
            stmt.setString(2, column);
            try (ResultSet r = stmt.executeQuery()) { return r.next(); }
        }
    }
}

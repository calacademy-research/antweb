package org.calacademy.antweb.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.upload.Upload;
import org.calacademy.antweb.util.LogMgr;

/**
 * Today's numbers of each upload group's specimens (Brian Fisher, 2026-10-22, question Q13 in docs/numbers.md): the
 * group pages (groups.do, group.do) and a curator's page (curator.do, its last upload's group) show Specimens,
 * Collections, Localities, Ungeoreferenced, Flagged, Subfamilies, Genera and Species of the group's specimens as they
 * are now, recounted every night (set 3), by the rule of the upload counts (UploadDb.getCounts); the date of the
 * group's last upload is shown apart. Until 2026-10-22 the pages showed the numbers written at the group's last
 * upload, which went stale when specimens were renamed, flagged or deleted afterwards. The upload rows (table upload)
 * keep what each upload had, and the upload report pages and listUploads.do still show those.
 *
 * Table group_count, one row per group (created by the application; record in docs/data-model.md).
 */
public class GroupCountDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(GroupCountDb.class);

    public static final String TABLE = "group_count";

    public GroupCountDb(Connection connection) {
        super(connection);
    }

    public static String createTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + " group_id int NOT NULL PRIMARY KEY,"
            + " specimens int NOT NULL DEFAULT 0,"
            + " collections int NOT NULL DEFAULT 0,"
            + " localities int NOT NULL DEFAULT 0,"
            + " ungeoreferenced int NOT NULL DEFAULT 0,"
            + " flagged int NOT NULL DEFAULT 0,"
            + " subfamilies int NOT NULL DEFAULT 0,"
            + " genera int NOT NULL DEFAULT 0,"
            + " species int NOT NULL DEFAULT 0,"
            + " counted datetime NULL"
            + ") ENGINE=InnoDB";
    }

    private static volatile boolean s_tableMade = false;

    /** Creates the table if it is missing (once per start of the application). */
    public static void ensureTable(Connection connection) throws SQLException {
        if (s_tableMade) return;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTable());
        }
        s_tableMade = true;
    }

    /** Forgets that the table was made (tests that drop it). */
    public static void forgetTable() { s_tableMade = false; }

    /**
     * The specimens of every upload group in one pass (grouped by an expression, so MySQL reads the table once instead
     * of in index order: 4 s instead of 3 x 10 s on a production-size copy): group, all its specimens (statistics.do by
     * upload), then as UploadDb.getCounts counts them: Specimens, Collections, Localities, Ungeoreferenced (not flagged,
     * countable status, ant names: getFlagCriteria, getStatusCriteria, getTaxaCriteria), Subfamilies, Genera, Species
     * (of those, valid names of AntWeb's subfamilies and extant genera: getAntwebSubfamilyCriteria,
     * getAntwebGenusCriteria), and Flagged (any specimen of the group with a red flag).
     */
    public static String countsSql() {
        String c1 = "(" + SpecimenDb.getFlagCriteria() + " and " + SpecimenDb.getStatusCriteria() + " and " + SpecimenDb.getTaxaCriteria() + ")";
        String c2 = c1 + " and (" + SpecimenDb.getAntwebSubfamilyCriteria() + " and " + SpecimenDb.getAntwebGenusCriteria() + ")";
        return "select cast(access_group as signed) g, count(*), count(case when " + c1 + " then code end),"
            + " count(distinct case when " + c1 + " then collectioncode end), count(distinct case when " + c1 + " then localitycode end),"
            + " count(case when " + c1 + " and decimal_latitude is null then 1 end),"
            + " count(distinct case when " + c2 + " then subfamily end), count(distinct case when " + c2 + " then genus end),"
            + " count(distinct case when " + c2 + " then species end), count(case when flag = 'red' then 1 end)"
            + " from specimen where access_group is not null group by g";
    }

    /**
     * Recounts every group (nightly set 3): every group of ant_group gets a row (0 when it has no specimens left), with
     * the time of the count; rows are updated in place, rows of groups that no longer exist are deleted. Returns a line for the nightly message; a failure is logged and reported, never thrown
     * (the rest of set 3 goes on).
     */
    public String recountAll() {
        long start = System.currentTimeMillis();
        try {
            ensureTable(getConnection());
            Timestamp now = new Timestamp(start / 1000 * 1000);   // whole seconds, as the datetime column keeps it
            int groups = 0;
            String upsert = "insert into " + TABLE + " (group_id, specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera,"
                + " species, counted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update specimens = values(specimens),"
                + " collections = values(collections), localities = values(localities), ungeoreferenced = values(ungeoreferenced),"
                + " flagged = values(flagged), subfamilies = values(subfamilies), genera = values(genera), species = values(species),"
                + " counted = values(counted)";
            try (Statement q = getConnection().createStatement(); ResultSet r = q.executeQuery(countsSql());
                 PreparedStatement stmt = getConnection().prepareStatement(upsert)) {
                while (r.next()) {
                    stmt.setInt(1, r.getInt(1));
                    stmt.setInt(2, r.getInt(3));    // specimens
                    stmt.setInt(3, r.getInt(4));    // collections
                    stmt.setInt(4, r.getInt(5));    // localities
                    stmt.setInt(5, r.getInt(6));    // ungeoreferenced
                    stmt.setInt(6, r.getInt(10));   // flagged
                    stmt.setInt(7, r.getInt(7));    // subfamilies
                    stmt.setInt(8, r.getInt(8));    // genera
                    stmt.setInt(9, r.getInt(9));    // species
                    stmt.setTimestamp(10, now);
                    stmt.addBatch();
                    ++groups;
                }
                stmt.executeBatch();
            }
            // Groups without specimens now: 0 (rows are updated in place, so a page never sees a group without numbers)
            try (PreparedStatement stmt = getConnection().prepareStatement("insert into " + TABLE + " (group_id, counted)"
                    + " select g.id, ? from ant_group g on duplicate key update group_id = " + TABLE + ".group_id")) {
                stmt.setTimestamp(1, now);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = getConnection().prepareStatement("update " + TABLE + " set specimens = 0, collections = 0, localities = 0,"
                    + " ungeoreferenced = 0, flagged = 0, subfamilies = 0, genera = 0, species = 0, counted = ? where counted is null or counted <> ?")) {
                stmt.setTimestamp(1, now);
                stmt.setTimestamp(2, now);
                stmt.executeUpdate();
            }
            try (Statement stmt = getConnection().createStatement()) {
                stmt.executeUpdate("delete from " + TABLE + " where group_id not in (select id from ant_group)");
            }
            String message = "Group counts: " + groups + " groups with specimens recounted in " + (System.currentTimeMillis() - start) / 1000 + " s";
            LogMgr.appendLog("compute.log", "  " + message, true);
            return message;
        } catch (SQLException e) {
            s_log.error("recountAll() e:" + e);
            return "Group counts not written (" + e + ")";
        }
    }

    /**
     * Puts the group's numbers of the last nightly count into the upload (the group's last upload, shown on the group
     * and curator pages); leaves the upload's own numbers when the group was not counted yet. Returns the upload.
     */
    public Upload apply(Upload upload) {
        if (upload == null || upload.getGroupId() <= 0) return upload;
        try {
            ensureTable(getConnection());
            try (PreparedStatement stmt = getConnection().prepareStatement("select specimens, collections, localities, ungeoreferenced, flagged,"
                    + " subfamilies, genera, species, counted from " + TABLE + " where group_id = ?")) {
                stmt.setInt(1, upload.getGroupId());
                try (ResultSet r = stmt.executeQuery()) {
                    if (!r.next() || r.getTimestamp(9) == null) return upload;
                    upload.setSpecimens(r.getInt(1));
                    upload.setCollections(r.getInt(2));
                    upload.setLocalities(r.getInt(3));
                    upload.setUngeoreferenced(r.getInt(4));
                    upload.setFlagged(r.getInt(5));
                    upload.setSubfamilies(r.getInt(6));
                    upload.setGenera(r.getInt(7));
                    upload.setSpecies(r.getInt(8));
                    upload.setCountedAt(r.getTimestamp(9));
                }
            }
        } catch (SQLException e) {
            s_log.warn("apply() group:" + upload.getGroupId() + " e:" + e);
        }
        return upload;
    }
}

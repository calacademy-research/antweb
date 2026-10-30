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

    /** The row of group_count that holds the Totals row of groups.do: every listed group's specimens together (Q8). */
    public static final int ALL_GROUPS = 0;

    /**
     * The groups groups.do lists (GroupDb.getUploadGroups): groups with uploaded specimens and an upload row. Their
     * specimens together make the Totals row (ALL_GROUPS).
     */
    public static final String LISTED_GROUPS_SQL = "select g.id from ant_group g where g.upload_specimens > 0 and exists (select 1 from upload u where u.group_id = g.id)";

    /*
     * The rule of every number of an upload group (Brian Fisher, 2026-10-22, docs/numbers.md Q8), used by the nightly
     * recount (recountAll), by each upload's own row (UploadDb.getCounts, the upload reports) and by the count check
     * (NumberChecks admin.groups):
     *
     *   counted specimens  not red-flagged, specimen status valid, unrecognized, morphotaxon, indetermined or
     *                      unidentifiable, family Formicidae (SpecimenDb.getFlagCriteria, getStatusCriteria, getTaxaCriteria)
     *   Specimens, Collections, Localities, Ungeoreferenced   of the counted specimens (unchanged); Collections leaves out
     *                specimens without a collection code (COLLECTION; Brian Fisher, 2026-10-30)
     *   Subfamilies  distinct subfamilies of the counted specimens that are valid ant subfamilies (SpecimenDb.getAntSpecimenCriteria)
     *   Genera       distinct genera of the counted specimens identified to genus (PlaceTotals.identifiedToGenus) whose
     *                genus is a valid genus or a morphogenus (Brian Fisher, 2026-10-30: COUNTED_GENERA; a genus name
     *                AntCat does not know, status unrecognized, is not a genus)
     *   Species      distinct full names (taxon_name) of the counted specimens whose name is a species or subspecies of
     *                status valid or morphotaxon (since 2026-10-28; before: a counted status), ant, identified to genus
     *                (PlaceTotals.rankCondition(SPECIES), as the place pages)
     *   Flagged      specimens with a red flag
     *
     * Until 2026-10-22 Species counted distinct species epithets (Pheidole minor and Camponotus minor once) of valid names
     * of AntWeb's extant genera only (morphospecies not counted), and Subfamilies and Genera valid names only; and the
     * Totals row of groups.do showed the largest group's Subfamilies, Genera and Species. Now the Totals row is the same
     * count over the specimens of all listed groups together (ALL_GROUPS).
     */
    /**
     * SQL: the specimen (alias s) is of a genus that counts as a genus (Brian Fisher, 2026-10-30): its genus row (taxon name
     * = subfamily + genus, as uploads name it) counts (PlaceTotals.countsAsGenus: valid or morphotaxon, not a placeholder).
     * A semi-join on the taxon table's few thousand genus rows, so the groups' one pass over the specimens stays one pass.
     */
    public static final String COUNTED_GENERA = "concat(coalesce(s.subfamily, ''), s.genus) in (select g.taxon_name from taxon g"
        + " where g.taxarank = 'genus' and g.family = 'formicidae' and " + PlaceTotals.countsAsGenus("g") + ")";

    /**
     * SQL: the specimen's collection, for Collections: its collection code, null when it has none (null or empty). A
     * specimen without a collection code is in no collection and not counted (Brian Fisher, 2026-10-30; uploads store
     * none since then, SpecimenUploadParse.storedCollectionCode).
     */
    public static final String COLLECTION = "nullif(trim(s.collectioncode), '')";

    public static String countColumns() {
        String c1 = "((s.flag is null or s.flag != 'red') and s.status in " + PlaceTotals.COUNTABLE + " and s.family = 'formicidae')";
        return " count(*), count(case when " + c1 + " then s.code end),"
            + " count(distinct case when " + c1 + " then " + COLLECTION + " end), count(distinct case when " + c1 + " then s.localitycode end),"
            + " count(case when " + c1 + " and s.decimal_latitude is null then 1 end),"
            + " count(distinct case when " + c1 + " and " + SpecimenDb.getAntSpecimenCriteria("s") + " then s.subfamily end),"
            + " count(distinct case when " + c1 + " and " + PlaceTotals.identifiedToGenus("s") + " and coalesce(s.genus, '') <> '' and " + COUNTED_GENERA + " then s.genus end),"
            + " count(distinct case when " + c1 + " and " + PlaceTotals.rankCondition(PlaceTotals.SPECIES) + " then s.taxon_name end),"
            + " count(case when s.flag = 'red' then 1 end)"
            + " from specimen s left join taxon t on t.taxon_name = s.taxon_name";
    }

    /**
     * The specimens of every upload group in one pass (grouped by an expression, so MySQL reads the table once instead
     * of in index order): group, all its specimens (statistics.do by upload), then by the rule above: Specimens,
     * Collections, Localities, Ungeoreferenced, Subfamilies, Genera, Species, Flagged.
     */
    public static String countsSql() {
        return "select cast(s.access_group as signed) g," + countColumns() + " where s.access_group is not null group by g";
    }

    /** The same numbers for one group (parameter: the group id): the numbers of an upload (UploadDb.getCounts). */
    public static String groupCountsSql() {
        return "select cast(s.access_group as signed) g," + countColumns() + " where s.access_group = ? group by g";
    }

    /**
     * Subfamilies, Genera, Species of every group by the rule before 2026-10-22 (UploadDb.getCounts then: valid names of
     * AntWeb's subfamilies and extant genera, species epithets). Not used by any page: the count check uses it to tell a
     * number counted before the change (pending until the next nightly run) from a wrong one.
     */
    public static String countsSqlBeforeQ8() {
        String c1 = "(" + SpecimenDb.getFlagCriteria() + " and " + SpecimenDb.getStatusCriteria() + " and " + SpecimenDb.getTaxaCriteria() + ")";
        String c2 = c1 + " and (" + SpecimenDb.getAntwebSubfamilyCriteria() + " and " + SpecimenDb.getAntwebGenusCriteria() + ")";
        return "select cast(access_group as signed) g, count(distinct case when " + c2 + " then subfamily end), count(distinct case when " + c2
            + " then genus end), count(distinct case when " + c2 + " then species end) from specimen where access_group is not null group by g";
    }

    /**
     * The Totals row of groups.do: the same numbers over the specimens of every listed group together, one query per
     * distinct number (each keeps only the distinct values of one column). Until 2026-10-24 this was one query with five
     * count(distinct case ...) columns over all listed groups' specimens; on staging (1 million specimens) MySQL wrote
     * about 23 GB of temporary tables for it and filled the disk. Order of the queries and of countTotals:
     * specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera, species (the first query gives
     * specimens, ungeoreferenced and flagged).
     * groupIds: the listed groups as a list of numbers (listedGroupIds). Until 2026-10-24 the queries had the subquery
     * LISTED_GROUPS_SQL in place of the list: MySQL joined every specimen to every upload row of its group to answer it
     * (CAS has thousands of uploads) and that, not the distinct counts, wrote the 23 GB of temporary tables.
     */
    public static String[] totalsSqls(String groupIds) {
        String c1 = "(s.flag is null or s.flag != 'red') and s.status in " + PlaceTotals.COUNTABLE + " and s.family = 'formicidae'";
        String from = " from specimen s where s.access_group in (" + groupIds + ")";
        return new String[] {
            "select count(case when " + c1 + " then 1 end), count(case when " + c1 + " and s.decimal_latitude is null then 1 end),"
                + " count(case when s.flag = 'red' then 1 end)" + from,
            distinct(COLLECTION, from + " and " + c1),
            distinct("s.localitycode", from + " and " + c1),
            distinct("s.subfamily", from + " and " + c1 + " and " + SpecimenDb.getAntSpecimenCriteria("s")),
            distinct("s.genus", from + " and " + c1 + " and " + PlaceTotals.identifiedToGenus("s") + " and coalesce(s.genus, '') <> '' and " + COUNTED_GENERA),
            distinct("s.taxon_name", " from specimen s join taxon t on t.taxon_name = s.taxon_name where s.access_group in (" + groupIds
                + ") and " + c1 + " and " + PlaceTotals.rankCondition(PlaceTotals.SPECIES)),
        };
    }

    /** The ids of the groups groups.do lists (LISTED_GROUPS_SQL), comma separated; empty when there are none. */
    public static String listedGroupIds(Connection connection, java.util.function.UnaryOperator<String> track) throws SQLException {
        StringBuilder ids = new StringBuilder();
        try (Statement q = connection.createStatement(); ResultSet r = q.executeQuery(track == null ? LISTED_GROUPS_SQL + " order by g.id" : track.apply(LISTED_GROUPS_SQL + " order by g.id"))) {
            while (r.next()) {
                if (ids.length() > 0) ids.append(',');
                ids.append(r.getInt(1));
            }
        }
        return ids.toString();
    }

    private static String distinct(String column, String fromWhere) {
        return "select count(*) from (select distinct " + column + fromWhere + " and " + column + " is not null) x";
    }

    /**
     * Counts the Totals row (totalsSqls): specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera,
     * species. track, when not null, is given each query first (the count check's query log).
     */
    public static long[] countTotals(Connection connection, java.util.function.UnaryOperator<String> track) throws SQLException {
        String ids = listedGroupIds(connection, track);
        if (ids.isEmpty()) return new long[8];
        String[] sqls = totalsSqls(ids);
        long[] one = new long[sqls.length + 2];
        try (Statement q = connection.createStatement()) {
            for (int i = 0; i < sqls.length; i++) {
                try (ResultSet r = q.executeQuery(track == null ? sqls[i] : track.apply(sqls[i]))) {
                    if (!r.next()) continue;
                    if (i == 0) { one[0] = r.getLong(1); one[1] = r.getLong(2); one[2] = r.getLong(3); }
                    else one[i + 2] = r.getLong(1);
                }
            }
        }
        // one: specimens, ungeoreferenced, flagged, collections, localities, subfamilies, genera, species
        return new long[] {one[0], one[3], one[4], one[1], one[2], one[5], one[6], one[7]};
    }

    /**
     * Recounts every group (nightly set 3): every group of ant_group gets a row (0 when it has no specimens left), with
     * the time of the count; rows are updated in place, rows of groups that no longer exist are deleted. Returns a line for the nightly message; a failure is logged and reported, never thrown
     * (the rest of set 3 goes on).
     */
    public String recountAll() {
        long start = System.currentTimeMillis();
        java.sql.Savepoint savepoint = null;
        try {
            ensureTable(getConnection());
            // A failure after some of the writes below rolls back to here, so the rest of set 3 is not committed with
            // half of the group numbers.
            if (!getConnection().getAutoCommit()) savepoint = getConnection().setSavepoint();
            Timestamp now = new Timestamp(start / 1000 * 1000);   // whole seconds, as the datetime column keeps it
            int groups = 0;
            String upsert = "insert into " + TABLE + " (group_id, specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera,"
                + " species, counted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update specimens = values(specimens),"
                + " collections = values(collections), localities = values(localities), ungeoreferenced = values(ungeoreferenced),"
                + " flagged = values(flagged), subfamilies = values(subfamilies), genera = values(genera), species = values(species),"
                + " counted = values(counted)";
            try (PreparedStatement stmt = getConnection().prepareStatement(upsert)) {
                try (Statement q = getConnection().createStatement(); ResultSet r = q.executeQuery(countsSql())) {
                    while (r.next()) {
                        if (r.getInt(1) == ALL_GROUPS) continue;   // no real group has id 0; that row is the Totals row
                        addRow(stmt, r, now);
                        ++groups;
                    }
                }
                // the Totals row of groups.do (Q8, 2026-10-22): all listed groups' specimens together, distinct over them
                long[] t = countTotals(getConnection(), null);
                if (t[0] > 0) {
                    stmt.setInt(1, ALL_GROUPS);
                    for (int i = 0; i < 8; i++) stmt.setLong(i + 2, t[i]);
                    stmt.setTimestamp(10, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            // Groups without specimens now: 0 (rows are updated in place, so a page never sees a group without numbers)
            try (PreparedStatement stmt = getConnection().prepareStatement("insert into " + TABLE + " (group_id, counted)"
                    + " select g.id, ? from ant_group g where g.id <> " + ALL_GROUPS + " on duplicate key update group_id = " + TABLE + ".group_id")) {
                stmt.setTimestamp(1, now);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = getConnection().prepareStatement("update " + TABLE + " set specimens = 0, collections = 0, localities = 0,"
                    + " ungeoreferenced = 0, flagged = 0, subfamilies = 0, genera = 0, species = 0, counted = ? where group_id <> " + ALL_GROUPS
                    + " and (counted is null or counted <> ?)")) {
                stmt.setTimestamp(1, now);
                stmt.setTimestamp(2, now);
                stmt.executeUpdate();
            }
            try (Statement stmt = getConnection().createStatement()) {
                stmt.executeUpdate("delete from " + TABLE + " where group_id <> " + ALL_GROUPS + " and group_id not in (select id from ant_group)");
            }
            // The Totals row is never set to 0 or left from an older count: written by this count, or removed (the page
            // then shows "-" until the next count). Staging 2026-10-24: a Totals row of zeros was shown as numbers.
            try (PreparedStatement stmt = getConnection().prepareStatement("delete from " + TABLE + " where group_id = " + ALL_GROUPS
                    + " and (counted is null or counted <> ?)")) {
                stmt.setTimestamp(1, now);
                stmt.executeUpdate();
            }
            String message = "Group counts: " + groups + " groups with specimens recounted in " + (System.currentTimeMillis() - start) / 1000 + " s";
            LogMgr.appendLog("compute.log", "  " + message, true);
            return message;
        } catch (SQLException e) {
            s_log.error("recountAll() e:" + e);
            if (savepoint != null) {
                try { getConnection().rollback(savepoint); } catch (SQLException e2) { s_log.error("recountAll() rollback e:" + e2); }
            }
            return "Group counts not written (" + e + ")";
        }
    }

    /**
     * The group's row recounted now, by the same rule (groupCountsSql), at the end of each of its specimen uploads
     * (SpecimenUploadDb.recordUpload, 2026-10-29): groups.do and group.do show the group's specimens as the upload
     * left them, not as the last nightly run counted them (staging run 40940710186: groups.do showed 1 specimen of the
     * test group, counted by a set 3 run before the e2e uploads, after an upload that left 0 counted). The Totals row
     * (ALL_GROUPS, distinct over all groups) stays the nightly run's. In the caller's transaction; never throws:
     * returns false when the row was not written (the nightly run writes it).
     */
    public boolean recountGroup(int groupId) {
        if (groupId == ALL_GROUPS) return false;
        try {
            ensureTable(getConnection());
            Timestamp now = new Timestamp(System.currentTimeMillis() / 1000 * 1000);
            String upsert = "insert into " + TABLE + " (group_id, specimens, collections, localities, ungeoreferenced, flagged, subfamilies, genera,"
                + " species, counted) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update specimens = values(specimens),"
                + " collections = values(collections), localities = values(localities), ungeoreferenced = values(ungeoreferenced),"
                + " flagged = values(flagged), subfamilies = values(subfamilies), genera = values(genera), species = values(species),"
                + " counted = values(counted)";
            try (PreparedStatement stmt = getConnection().prepareStatement(upsert);
                 PreparedStatement q = getConnection().prepareStatement(groupCountsSql())) {
                q.setInt(1, groupId);
                try (ResultSet r = q.executeQuery()) {
                    if (r.next()) {
                        addRow(stmt, r, now);
                    } else {   // no specimens now
                        stmt.setInt(1, groupId);
                        for (int i = 2; i <= 9; i++) stmt.setInt(i, 0);
                        stmt.setTimestamp(10, now);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
            return true;
        } catch (SQLException e) {
            s_log.warn("recountGroup() group:" + groupId + " e:" + e);
            return false;
        }
    }

    /** One row of countsSql (columns: g, all, specimens, collections, localities, ungeoreferenced, subfamilies,
     *  genera, species, flagged) into the upsert. */
    private static void addRow(PreparedStatement stmt, ResultSet r, Timestamp now) throws SQLException {
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
    }

    /**
     * The Totals row of groups.do (Q8) as the last nightly count wrote it: specimens, collections, localities,
     * ungeoreferenced, flagged, subfamilies, genera, species; null when no nightly run has counted it yet.
     */
    public Upload totals() {
        Upload all = new Upload();
        all.setGroupId(ALL_GROUPS);
        try {
            ensureTable(getConnection());
            try (PreparedStatement stmt = getConnection().prepareStatement("select specimens, collections, localities, ungeoreferenced, flagged,"
                    + " subfamilies, genera, species, counted from " + TABLE + " where group_id = " + ALL_GROUPS)) {
                try (ResultSet r = stmt.executeQuery()) {
                    // no count yet, or a row of zeros (written by a build before 2026-10-24): the page shows "-"
                    if (!r.next() || r.getTimestamp(9) == null || r.getInt(1) == 0) return null;
                    all.setSpecimens(r.getInt(1));
                    all.setCollections(r.getInt(2));
                    all.setLocalities(r.getInt(3));
                    all.setUngeoreferenced(r.getInt(4));
                    all.setFlagged(r.getInt(5));
                    all.setSubfamilies(r.getInt(6));
                    all.setGenera(r.getInt(7));
                    all.setSpecies(r.getInt(8));
                    all.setCountedAt(r.getTimestamp(9));
                }
            }
        } catch (SQLException e) {
            s_log.warn("totals() e:" + e);
            return null;
        }
        return all;
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

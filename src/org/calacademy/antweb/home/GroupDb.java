package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.GroupMgr;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Upload groups (table ant_group, usually one per institution): read for GroupMgr (cache) and the group pages
 * (group.do, groups.do), saved and deleted by admins (newGroup.do, saveGroup.do, viewGroups.do; whyNotDeletable
 * refuses a group that still has logins or specimens), the curators of a group, and upload_specimens (the
 * group's specimen count): written for the uploading group after its upload (updateUploadSpecimens(group)) and for
 * every group by the daily update (recountUploadSpecimens, the last statement of set 3). Tables ant_group, login, specimen, upload.
 * Docs: docs/flows/specimen-upload.md, docs/flows/nightly.md. Tests: UploaderOnboardingDbTest, GroupSpecimenCountDbTest.
 */
public class GroupDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(GroupDb.class);
        
    public GroupDb(Connection connection) {
      super(connection);
    }

    public ArrayList<Group> getAllGroups() throws SQLException {
        // todo only use one db call for this
        String theQuery = "select id from ant_group order by id";
        ArrayList<Group> groupList = new ArrayList<>();

        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getAllGroups()", theQuery);
            rset = stmt.executeQuery();
                
            while (rset.next()) {
                Group group = getGroup(rset.getInt("id"));
                groupList.add(group);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getAllGroups()");        
        }
        return groupList;      
    }

    public String getCuratorList(int groupId) throws SQLException {
        String curatorList = null;
        String theQuery = "select first_name, last_name from login where group_id = ?";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getCuratorList()", theQuery);
            stmt.setInt(1, groupId);
            rset = stmt.executeQuery();

            int i = 0;
            while (rset.next()) {
                ++i;
                String curator = rset.getString("first_name") + " " + rset.getString("last_name");
                if (i > 1) curatorList += ", " + curator;
                  else curatorList = curator;
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getCuratorList()");        
        }
        return curatorList;
    }

    /** Starts the line recountUploadSpecimens returns (compute.log, written by the daily update's set 3). */
    public static final String SPECIMENS_LOG = "Group specimen counts";

    /** How many changed groups the line names. */
    static final int LOG_EXAMPLES = 10;

    /** Tests only: called after the numbers are read and before the first update. */
    private static volatile Runnable s_beforeUpdateForTests = null;

    public static void setBeforeUpdateForTests(Runnable beforeUpdate) {
        s_beforeUpdateForTests = beforeUpdate;
    }

    /**
     * Every group's stored specimen count (ant_group.upload_specimens) is set to its specimen records: the rows of
     * specimen with access_group = the group's id; 0 for a group without specimens. Called by the daily update as the
     * last statement of set 3, right before the set commits, in the set's transaction: nothing is committed here.
     *
     * Until 2026-11-11 the number was written only at the end of a group's own upload (updateUploadSpecimens(group)).
     * Whatever else changed a group's specimens (a correction, a deletion, a clean-up) left it as it was, and the count
     * check (admin.statistics: "specimens (statistics.do by upload, live) = ant_group.upload_specimens") reported a
     * real difference every day until the group's next upload: production 2026-11-11, group 52, no upload for months,
     * 21,102 specimens, stored 21,099.
     *
     * Two plain selects (the specimens per group, the stored numbers), then one bound update for each group whose
     * number differs. Not one UPDATE with the specimen count as a subquery: inside the set's transaction that statement
     * keeps shared locks on every specimen row it read until the set commits, and specimens cannot be added or removed
     * meanwhile (tried on the test database: an insert into specimen waited until its lock timeout).
     *
     * A number is written only when the stored one is still the one read ("and upload_specimens <=> ?"). The selects
     * read the data as it was when set 3 made its first read, minutes earlier (the set is one transaction); an upload
     * that ended since then has written its group's number from newer data, and that number stays. Such groups are
     * counted in the line as left, and the next daily update looks at them again.
     *
     * A changed row stays locked until the set commits, and an upload of that group that ends meanwhile waits for it.
     * That is why this is the set's last statement: the wait is a moment. The group counts earlier in the set
     * (GroupCountDb.recountAll, which lists the groups whose number is above 0) therefore read the numbers of the day
     * before: a group whose number goes to or from 0 enters or leaves that list one day later.
     *
     * Returns the line for compute.log: "Group specimen counts: 87 groups recounted, 2 numbers changed (group 52: 21099
     * to 21102; group 7: 5 to 0) in 0 s" (up to LOG_EXAMPLES groups are named; only numbers that were written are
     * counted and named); with groups left: "..., 1 left as stored (written by an upload since it was read: group 9)".
     * Never throws: a failure is logged and said in the line, and the rest of the set goes on (as
     * GroupCountDb.recountAll). The updates run one at a time, so the line says what was written before the failure:
     * "Group specimen counts: FAILED (...)" when no number was written, else "Group specimen counts: 2 numbers changed
     * (group 52: 3 to 4; group 60: 7 to 0), then FAILED (...)"; the numbers written before it are right and stay.
     */
    public String recountUploadSpecimens() {
        long start = System.currentTimeMillis();
        int groups = 0;
        int changed = 0;
        List<String> examples = new ArrayList<>();
        List<Integer> left = new ArrayList<>();
        try {
            java.util.Map<Integer, Integer> specimens = new java.util.HashMap<>();
            try (Statement stmt = getConnection().createStatement();
                 ResultSet r = stmt.executeQuery("select access_group, count(*) from specimen where access_group is not null group by access_group")) {
                while (r.next()) specimens.put(r.getInt(1), r.getInt(2));
            }
            List<Integer[]> changes = new ArrayList<>();   // group id, the stored number (null: none), the number now
            try (Statement stmt = getConnection().createStatement();
                 ResultSet r = stmt.executeQuery("select id, upload_specimens from ant_group order by id")) {
                while (r.next()) {
                    ++groups;
                    int id = r.getInt(1);
                    int stored = r.getInt(2);
                    boolean none = r.wasNull();
                    int now = specimens.getOrDefault(id, 0);
                    if (none || stored != now) changes.add(new Integer[] {id, none ? null : stored, now});
                }
            }
            Runnable hook = s_beforeUpdateForTests;
            if (hook != null) hook.run();
            if (!changes.isEmpty()) {
                // one row at a time: after a failure the line says exactly which numbers were written
                try (PreparedStatement stmt = getConnection().prepareStatement(
                        "update ant_group set upload_specimens = ? where id = ? and upload_specimens <=> ?")) {
                    for (Integer[] change : changes) {
                        stmt.setInt(1, change[2]);
                        stmt.setInt(2, change[0]);
                        if (change[1] == null) stmt.setNull(3, Types.INTEGER); else stmt.setInt(3, change[1]);
                        if (stmt.executeUpdate() > 0) {
                            ++changed;
                            if (examples.size() < LOG_EXAMPLES) examples.add("group " + change[0] + ": " + (change[1] == null ? "no number" : "" + change[1]) + " to " + change[2]);
                        } else {
                            left.add(change[0]);   // another number is stored by now: an upload of the group wrote it
                        }
                    }
                }
            }
            return SPECIMENS_LOG + ": " + groups + " groups recounted, " + changed + " numbers changed" + named(examples, changed) + leftAsStored(left)
                + " in " + (System.currentTimeMillis() - start) / 1000 + " s";
        } catch (SQLException | RuntimeException e) {
            s_log.error("recountUploadSpecimens() e:" + e);
            return SPECIMENS_LOG + ": " + (changed == 0 ? "" : changed + " numbers changed" + named(examples, changed) + leftAsStored(left) + ", then ")
                + "FAILED (" + e + ")";
        }
    }

    /** " (group 52: 3 to 4; group 60: 7 to 0; ...)": the changed groups the line names, "" when none changed. */
    private static String named(List<String> examples, int changed) {
        return examples.isEmpty() ? "" : " (" + String.join("; ", examples) + (changed > examples.size() ? "; ..." : "") + ")";
    }

    /** ", 1 left as stored (written by an upload since it was read: group 9)", "" when none was left. */
    private static String leftAsStored(List<Integer> left) {
        if (left.isEmpty()) return "";
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < left.size() && i < LOG_EXAMPLES; i++) ids.add("group " + left.get(i));
        return ", " + left.size() + " left as stored (written by an upload since " + (left.size() == 1 ? "it was" : "they were") + " read: "
            + String.join(", ", ids) + (left.size() > ids.size() ? ", ..." : "") + ")";
    }

    
    public void updateUploadSpecimens(Group group) {
        String dml = "update ant_group set upload_specimens = (select count(*) from specimen where access_group = " + group.getId() + ") where id = " + group.getId();
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateUploadSpecimens()");
            stmt.executeUpdate(dml);
            s_log.debug("updateUploadSpecimens() dml:" + dml);
        } catch (SQLException e) {
          s_log.error("problem updating group:" + group.getName() + " e:" + e);
        } finally {
          DBUtil.close(stmt, null, this, "updateUploadSpecimens()");
        }    
    }
    
    public ArrayList<Group> getAllGroupsWithSpecimenData() throws SQLException {          
        //String theQuery = "select id from ant_group g where id in (select distinct access_group from specimen) order by name";
        String query = "select id from ant_group g where upload_specimens > 0 order by name";
        ArrayList<Group> groupList = new ArrayList<>();

        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getAllGroupsWithSpecimenData()", query);
            rset = stmt.executeQuery();

            while (rset.next()) {
                Group group = getGroup(rset.getInt("id"));
                groupList.add(group);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getAllGroupsWithSpecimenData()");        
        }
        //A.log("getAllGroupsWithSpecimenData() groupList:" + groupList);
        return groupList;      
    }

    public Group getGroup(int id) throws SQLException {
        Group group;
        String theQuery = "select g.id, g.name, g.admin_login_id, g.abbrev "
                + " from ant_group g "
                + " where g.id = ?";

        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getGroup()", theQuery);
            stmt.setInt(1, id);
            rset = stmt.executeQuery();

            group = instantiateGroup(rset);

        } finally {
            DBUtil.close(stmt, rset, this, "getGroup()");
        }
        return group;
    }

    public Group getGroup(String name) throws SQLException {
        Group group = null;
        if (name != null) {
            String theQuery = "select g.id, g.name, g.admin_login_id, g.abbrev "
                    + " from ant_group g "
                    + " where g.name = ?";

            PreparedStatement stmt = null;
            ResultSet rset = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "getGroup()", theQuery);

                stmt.setString(1, name);
                rset = stmt.executeQuery();

                // s_log.info("findByName() theQuery:" + theQuery);
                group = instantiateGroup(rset);

                // s_log.info("findByName() group:" + group);
            } finally {
                DBUtil.close(stmt, rset, this, "getGroup()");
            }
        }
        return group;
    }

    private Group instantiateGroup(ResultSet rset) throws SQLException {
        Group group = new Group();
        while (rset.next()) {
            group.setId(rset.getInt("g.id"));
            group.setName(rset.getString("g.name"));
            group.setAdminLoginId(rset.getInt("g.admin_login_id"));
            group.setAbbrev(rset.getString("g.abbrev"));
        }

        return group;    
    }
    
    private int getMaxId() throws SQLException {          
        int maxId = 0;
        String theQuery = "select max(id) as maxId from ant_group ";
        PreparedStatement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getMaxId()", theQuery);
            rset = stmt.executeQuery();

            while (rset.next()) {
              maxId = rset.getInt("maxId");
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getMaxId()");
        }

        return maxId;
    }
    

    public ArrayList<Group> getUploadGroups(String orderBy) throws SQLException {       
        ArrayList<Group> groupList = new ArrayList<>();

        // create list of each group's most recent upload id (when the group has uploads)
        String recentUploadIds = "select max(upload_id)" +
                "from upload " +
                "where group_id in (select id from ant_group where upload_specimens > 0) " +
                "  and upload_id is not null " +
                "group by group_id";

        if ("firstUpload".equals(orderBy)) orderBy = "first_specimen_upload";
        if ("lastUpload".equals(orderBy)) orderBy = "created desc"; //"last_specimen_upload";
        if ("uploads".equals(orderBy)) orderBy = "upload_count desc";
        // The numbers shown are today's (group_count, recounted every night since 2026-10-22, Q13); the last upload's
        // until a nightly run has counted the group.
        for (String n : new String[] {"specimens", "ungeoreferenced", "flagged", "collections", "localities", "subfamilies", "genera", "species"}) {
            if (n.equals(orderBy)) orderBy = "coalesce(c." + n + ", u." + n + ") desc";
        }

        // ORDER BY cannot be a bound parameter. Only allow the mapped values above (and name); anything else gets no ordering.
        List<String> allowedOrderBys = new ArrayList<>(Arrays.asList("name", "first_specimen_upload", "created desc", "upload_count desc"));
        for (String n : new String[] {"specimens", "ungeoreferenced", "flagged", "collections", "localities", "subfamilies", "genera", "species"}) {
            allowedOrderBys.add("coalesce(c." + n + ", u." + n + ") desc");
        }
        if (orderBy != null && !allowedOrderBys.contains(orderBy)) {
            s_log.debug("getUploadGroups() ignoring orderBy:" + orderBy);
            orderBy = null;
        }

        String orderClause = "";
        if (!Utility.isBlank(orderBy)) orderClause = " order by " + orderBy;
        GroupCountDb.ensureTable(getConnection());
        String query = "select g.id from ant_group g join upload u on g.id = u.group_id"
            + " left join " + GroupCountDb.TABLE + " c on c.group_id = g.id and c.counted is not null"
            + " where u.upload_id in (" + recentUploadIds + ") " + orderClause;
        //A.log("getUploadGroups() query:" + query);
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getUploadGroups()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                int groupId = rset.getInt("id");
                Group group = getGroup(groupId);
                //A.log("getUploadGroups() groupId:" + groupId + " group.getId():" + group.getId());
                //group.setLastUpload(uploadDb.getLastUpload(group.getId()));
                groupList.add(group);
            }
        } finally {
            DBUtil.close(stmt, rset, this, "getUploadGroups()");        
        }
        return groupList;      
    }

    public void saveGroup(Group group) throws SQLException {

        if (group.getId() == 0) {
            s_log.warn("Attempt to save groupId = 0");
            return;
        }

        int id = getMaxId() + 1;
        if (id == 0) {
            s_log.error("saveGroup().  MaxId should not be 0");
            return;
        } else {
            group.setId(id);
        }
        String theInsert = "insert into ant_group (id, name, admin_login_id, abbrev)  values (?, ?, ?, ?)";
        PreparedStatement stmt = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "saveGroup()", theInsert);

            stmt.setInt(1, group.getId());
            stmt.setString(2, group.getName());
            stmt.setInt(3, group.getAdminLoginId());
            stmt.setString(4, group.getAbbrev());

            s_log.info(theInsert);
            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("problem saving to DB group:" + group.getName());
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "saveGroup()");
        }
    }

    public void updateGroup(Group group) throws SQLException {

        if (group.getId() != 0) {
            String theUpdate = "update ant_group set name = ?,  admin_login_id = ?,  abbrev = ? where id = ?";
            PreparedStatement stmt = null;
            try {
                stmt = DBUtil.getPreparedStatement(getConnection(), "updateGroup()", theUpdate);
                stmt.setString(1, group.getName());
                stmt.setInt(2, group.getAdminLoginId());
                stmt.setString(3, group.getAbbrev());
                stmt.setInt(4, group.getId());
/*
                stmt.setInt(1, group.getId());
                stmt.setString(2, group.getName());
                stmt.setInt(3, group.getAdminLoginId());
                stmt.setString(4, group.getAbbrev());
*/
                s_log.info(theUpdate);
                stmt.executeUpdate();
            } catch (SQLException e) {
                s_log.error("updateGroup() " + group.getName());
                throw e;
            } finally {
                DBUtil.close(stmt, null, this, "updateGroup()");
            }
        } else {
            s_log.error("Attempt to update groupId == 0");
        }
    }

    /**
     * Null if the group may be deleted, else why not (plain text): it still has logins or
     * specimens. Used by SaveGroupAction before a delete.
     */
    public static String whyNotDeletable(Connection connection, int id) throws SQLException {
        int logins = count(connection, "select count(*) from login where group_id = ?", id);
        int specimens = count(connection, "select count(*) from specimen where access_group = ?", id);
        if (logins == 0 && specimens == 0) return null;
        return "Upload group " + id + " was not deleted: it still has " + logins + (logins == 1 ? " login" : " logins")
            + " and " + specimens + (specimens == 1 ? " specimen" : " specimens")
            + ". Move the logins to another upload group (Login Manager) and remove the specimens first.";
    }

    private static int count(Connection connection, String query, int id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            try (ResultSet rset = stmt.executeQuery()) {
                return rset.next() ? rset.getInt(1) : 0;
            }
        }
    }

    public void deleteById(int id) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String theQuery = "delete from ant_group where id = ?";
            stmt = DBUtil.getPreparedStatement(getConnection(), "deleteById()", theQuery);
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            s_log.error("deleteById(" + id + ") e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "deleteById()");
        }
    }

}
package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Utility;
import org.calacademy.antweb.util.A;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.GroupMgr;

import java.sql.*;
import java.util.ArrayList;

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

    public void updateUploadSpecimens() {
      ArrayList<Group> groups = GroupMgr.getGroups();
      for (Group group : groups) {
        //A.log("update group:" + group);
        updateUploadSpecimens(group);
      }
    }

    
    public void updateUploadSpecimens(Group group) {
        String dml = "update ant_group set upload_specimens = (select count(*) from specimen where access_group = " + group.getId() + ") where id = " + group.getId();
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateUploadSpecimens()");
            stmt.executeUpdate(dml);
            A.log("updateUploadSpecimens() dml:" + dml);
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
        if ("specimens".equals(orderBy)) orderBy = "specimens desc";
        if ("ungeoreferenced".equals(orderBy)) orderBy = "ungeoreferenced desc";
        if ("flagged".equals(orderBy)) orderBy = "flagged desc";
        if ("collections".equals(orderBy)) orderBy = "collections desc";
        if ("localities".equals(orderBy)) orderBy = "localities desc";
        if ("subfamilies".equals(orderBy)) orderBy = "subfamilies desc";
        if ("genera".equals(orderBy)) orderBy = "genera desc";
        if ("species".equals(orderBy)) orderBy = "species desc";

        String orderClause = "";
        if (!Utility.isBlank(orderBy)) orderClause = " order by " + orderBy;
        String query = "select g.id from ant_group g, upload u where g.id = u.group_id and u.upload_id in (" + recentUploadIds + ") " + orderClause;
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
                stmt.setInt(1, group.getId());
                stmt.setString(2, group.getName());
                stmt.setInt(3, group.getAdminLoginId());
                stmt.setString(4, group.getAbbrev());
                s_log.info(theUpdate);
                stmt.executeUpdate();
            } catch (SQLException e) {
                s_log.error("updateGroupInDb() " + group.getName() + ": ");
                throw e;
            } finally {
                DBUtil.close(stmt, null, this, "updateGroup()");
            }
        } else {
            s_log.error("Attempt to update groupId == 0");
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
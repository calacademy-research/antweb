package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class TeamMemberDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(TeamMemberDb.class);
        
    public TeamMemberDb(Connection connection) {
      super(connection);
    }
    
    public TeamMember findById(int id) throws SQLException {
        TeamMember teamMember = null;
        if (id > 0) {
            Statement stmt = null;
            ResultSet rset = null;
            try {
              stmt = DBUtil.getStatement(getConnection(), "TeamMemberDb.findById()");    
              String theQuery = "select * from team_member where id = " + id;
              rset = stmt.executeQuery(theQuery);
            
              //s_log.info("findById(" + id + ") theQuery:" + theQuery);                

              teamMember = instantiateFromResultSet(teamMember, rset);

            } finally {
              DBUtil.close(stmt, rset, "TeamMemberDb.findById()");
            }
        }
        return teamMember;
    }
        
            
    private int getNewMaxRank() {
 
        int max;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select max(teamrank) as maxrank from team_member";
            stmt = DBUtil.getStatement(getConnection(), "TeamMemberDb.getNewMaxRank()");
            rset = stmt.executeQuery(theQuery);
            max = 0;
            while (rset.next()) {
                max = rset.getInt("maxrank");
            }
            if (max > 0) {
                max = max + 100;
            }
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return 500;
        } finally {
          DBUtil.close(stmt, rset, "TeamMemberDb.getNewMaxRank()");
        }           
        return max;
    }
        

    private TeamMember instantiateFromResultSet(TeamMember teamMember, ResultSet rset) throws SQLException {
        while (rset.next()) {
            if (teamMember == null) teamMember = new TeamMember();
            teamMember.setId(rset.getInt("id"));
            teamMember.setName(rset.getString("name"));
            teamMember.setRoleOrg(rset.getString("role_org"));
            teamMember.setEmail(rset.getString("email"));
            teamMember.setSection(rset.getInt("section"));
            teamMember.setRank(rset.getInt("teamrank"));
            teamMember.setText(rset.getString("text"));
            teamMember.setCreated(rset.getDate("created"));
            teamMember.setImgFileBin(rset.getBlob("img_file_bin"));
            teamMember.setImgFileType(rset.getString("img_file_type"));
        }    
        return teamMember;
    }
    
    public ArrayList getCurators() throws SQLException {          
        ArrayList teamMemberList = new ArrayList();
        String theQuery = "select id from team_member order by teamrank";
        Statement stmt = null;
        ResultSet rset = null;
        try {            
            stmt = DBUtil.getStatement(getConnection(), "TeamMemberDb.getCurators()");

            rset = stmt.executeQuery(theQuery);
        
            while (rset.next()) {
                TeamMember teamMember = findById(rset.getInt("id"));
                teamMemberList.add(teamMember);
            }
        } finally {
          DBUtil.close(stmt, rset, "TeamMemberDb.getCurators()");
        }
        return teamMemberList;      
    }    
        
    public void save(TeamMember teamMember) throws SQLException {
        if (teamMember.getId() != 0) {
        

          // Use of ternary operator.  short conditional statement
          int isPublished = teamMember.isPublished() ? 1 : 0;
          String theDML = "";
          
          TeamMember existingTeamMember = findById(teamMember.getId());     
          if (existingTeamMember == null) {

            teamMember.setRank(getNewMaxRank());
            theDML = "insert into team_member (id, name, role_org, " 
              + "email, img_loc, section, teamrank, is_published, text) values ("
              + teamMember.getId() + ", '" + teamMember.getName() + "', " 
              + "'" + teamMember.getRoleOrg() + "', '" + teamMember.getEmail() + "', '" + teamMember.getImgLoc() 
              + "', '" + teamMember.getSection() + "', " + teamMember.getRank() 
              + ", " + isPublished + ", '" + teamMember.getText() + "')";
                    
          } else {
            theDML = "update team_member set " 
                   + " name='" + teamMember.getName() + "', " 
                   + " role_org='" + teamMember.getRoleOrg() + "', " 
                   + " email='" + teamMember.getEmail() + "', " 
                   + " text='" + teamMember.getText() + "' " 
                   + " where id=" + teamMember.getId();
          }
          
            s_log.info("save() DML:" + theDML);
             
            try {
                Statement stmt = getConnection().createStatement();                                                                                               
                stmt.executeUpdate(theDML);
                stmt.close();
            } catch (SQLException e) {
                s_log.error("save() name:" + teamMember.getName() + " query:" + theDML);
                throw e;
            }   
        } else {
            s_log.error("save().  Id = 0");
        }
    }
    
    public void saveImage(TeamMember teamMember) throws SQLException {
        if (teamMember.getId() != 0) {

          String theDML = "";
          
          TeamMember existingTeamMember = findById(teamMember.getId());     
          if (existingTeamMember == null) {
            s_log.error("saveImage() TeamMember:" + teamMember.getId() + " not found.");
          } else {
            try {

              theDML = "update team_member set " 
                   + " img_loc='" + teamMember.getImgLoc() + "', " 
                   + " img_width='" + teamMember.getImgWidth() + "', " 
                   + " img_height='" + teamMember.getImgHeight() + "', " 
                   + " img_file_name='" + teamMember.getImgFileName() + "', " 
                   + " img_file_size='" + teamMember.getImgFileSize() + "', " 
                   + " img_file_type='" + teamMember.getImgFileType() + "' " 
                   + " where id=" + teamMember.getId();
              //s_log.warn("saveImage() stream:" + teamMember.getImgFileInputStream());          
              s_log.info("saveImage() DML:" + theDML);
/*
              Statement stmt = getConnection().createStatement();                                                                                               
              stmt.executeUpdate(theDML);
              stmt.close();
*/
 //             Statement stmt = getConnection().createStatement();
              PreparedStatement pre = getConnection().prepareStatement("update team_member set img_file_type='" + teamMember.getImgFileType() + "', " 
                + " img_file_bin = ? where id=" + teamMember.getId());
              pre.setBinaryStream(1, teamMember.getImgFileInputStream(), 150000);    
              pre.executeUpdate();
              pre.close();
            } catch (SQLException e) {
                s_log.error("saveImage() name:" + teamMember.getName() + " query:" + theDML);
                throw e;
            }   
          }            
        } else {
            s_log.error("save().  Id = 0");
        }
    }
    
    public void deleteById(int id) throws SQLException {
          try {
            Statement stmt = getConnection().createStatement();
            String theQuery = "delete from team_member where id = " + id;
            int returnVal = stmt.executeUpdate(theQuery);
            stmt.close();
          } catch (SQLException e) {
            s_log.error("deleteById(" + id + ") e:" + e);
            throw e;
          }
    }        
    
}
package org.calacademy.antweb.home;


import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;


public class SpecimenUploadDb extends UploadDb {

    private static Log s_log = LogFactory.getLog(SpecimenUploadDb.class);
    private static final Log uploadLog = LogFactory.getLog("uploadLog");

    //ArrayList goodHeaders = new ArrayList(Arrays.asList(biotaHeaders));    

    public SpecimenUploadDb(Connection connection) {
      super(connection);
    }

    public void deleteSpecimens(ArrayList<String> specimens) throws SQLException {
        StringBuffer codeString = new StringBuffer();
        int count = 0;
        for (String code : specimens) {            
            if (code.length() > 0) {
              codeString.append(",'" + code.toLowerCase() + "'");
            }
        }
        if (codeString.length() > 0) {
            String query = "delete from specimen where code in (" + codeString.substring(1) + ")";
            Statement stmt = null; 

            try {
                stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.deleteSpecimens()");
                count = stmt.executeUpdate(query);
            } catch (SQLException e) {
                uploadLog.error("problem in deleteSpecimens(connect, specimens) - queryLength:" + query.length() + " " + e);
            } finally {
                DBUtil.close(stmt, "SpecimenUploadDb.deleteSpecimens()");
            }

            uploadLog.info("deleteSpecimens() count:" + count + " query:" + query);
        }   
    }
    
    public void updateSpecimenUploadDate(Group group) {
        
        int id = group.getId();
        String dml = "update ant_group set last_specimen_upload = now() where id = ?";
        PreparedStatement stmt = null;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.uploadSpecimenUploadDate()", dml);
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            uploadLog.error("updateSpecimenUploadDate() e: " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.uploadSpecimenUploadDate()");
        }        
    }
    
    void deleteSpecimen(String code) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String dml = "delete from specimen where code = ?";
            stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.deleteSpecimen()", dml);
            stmt.setString(1, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            uploadLog.error("problem deleteSpecimen() code:" + code + " " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.deleteSpecimen()");
        }
    }
    
    public void dropSpecimens(Group group) throws SQLException {
        int count;
        String query = "";
        PreparedStatement stmt = null;
        try {
          query = "delete from specimen where access_group = ?";
          //s_log.warn("dropSpecimens() why is this query so slow:" + query);                    
          stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.dropSpecimens()", query);
          stmt.setInt(1, group.getId());
          count = stmt.executeUpdate();

          LogMgr.appendLog("taxonSet.log", "specimenUploadDb.dropSpecimens(" + group + "):" + count, true);
          
          uploadLog.info("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 1 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens()");
        }

        try {
          query = "delete from taxon where access_group = ? and insert_method in ('addMissingGenus', 'homononymMirroringTaxon')";
          //s_log.warn("dropSpecimens() query:" + query);                    
          stmt = DBUtil.getPreparedStatement(getConnection(), "SpecimenUploadDb.dropSpecimens2()", query);
          stmt.setInt(1, group.getId());
          count = stmt.executeUpdate();
          //s_log.warn("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 2 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens2()");
        }
    }

    public void deleteTaxonOrphans() throws SQLException {
      // Retired.  This would just remove old Bolton records.  Does Bolton not clear in/out or reload?

        String query = "";
        Statement stmt = null;
        try {

          //s_log.warn("removeTaxonOrphan action suspended");
          if (true) return;

            stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.deleteTaxonOrphans()");
            query = "delete from taxon where "
                    + " taxon_name not in (select taxon_name from proj_taxon) and "
                    + " taxon_name not in (select taxon_name from specimen) and status = 'valid'";
            stmt.executeUpdate(query);

        } catch (SQLException e) {
            uploadLog.error("problem removeTaxonOrphans e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.deleteTaxonOrphans()");
        }
    }


    public void updateUpload(Login accessLogin, UploadDetails uploadDetails) {
        //s_log.warn("updateUpload()");
        String logFileName = uploadDetails.getLogFileName();
        String backupDirFile = uploadDetails.getBackupDirFile();
        Group accessGroup = accessLogin.getGroup();
        String insert = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateUpload()");
            insert = "insert into upload(upload_id, login_id, group_name, group_id, log_file_name, backup_dir_file) "
                    + "values (" + AntwebMgr.getNextSpecimenUploadId() + ", " + accessLogin.getId() + ", '" + accessGroup.getName() + "', " + accessGroup.getId() + ", '" + logFileName + "', '" + backupDirFile + "')";
            A.log("updateUpload() insert:" + insert);
            stmt.executeUpdate(insert);
        } catch (SQLException e) {
            s_log.error("updateUpload() logFileName:" + logFileName + " e:" + e);
        } finally {
            DBUtil.close(stmt, null, this, "updateUpload()");
        }
        updateCounts(accessGroup.getId());
    }

    
    /*
    /web/upload/20220224-11:49:08-specimen2.txt

Was looking in the working dir. That file does not exist:
 /usr/local/antweb/workingDir/specimen2.txt 

It has been archived to here:
/web/upload/20220224-11:49:08-specimen2.txt

/data/antweb/web/upload/20220224-11:49:08-specimen2.txt
    */
    
/*
        // I think this would work during upload, but not during reload.
        // During reload: https://www.antweb.org/query.do?action=curiousQuery&name=lastSpecimenUpload
        // Look here: /web/upload/20220224-11:49:08-specimen2.txt
        // Full path: /data/antweb/web/upload/
 */

    public String getLastUploadFileLoc(int accessGroup, boolean archived) {
        String specimenFileLoc = null;
        if (!archived) {
            String specimenFileName = "specimen" + accessGroup + ".txt";
            specimenFileLoc = AntwebProps.getWorkingDir() + specimenFileName;
        } else {
            // Called from here: https://localhost/query.do?action=curiousQuery&name=lastSpecimenUpload
            // Invoked like: https://localhost/upload.do?action=reloadSpecimenList&groupId=2
            String backupDirFile = getBackupDirFile(accessGroup);
            specimenFileLoc = AntwebProps.getWebDir() + backupDirFile;
        }
        A.log("getLastUploadFileLoc() specimenFileLoc:" + specimenFileLoc + " archived:" + archived);
        return specimenFileLoc;
    }

    private String getBackupDirFile(int accessGroup) {
        String backupDirFile = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select backup_dir_file from upload where group_id = " + accessGroup + " order by created desc limit 1";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getLastFileName()");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                backupDirFile = rset.getString("backup_dir_file");
            }
        } catch (SQLException e) {
            s_log.warn("getLastFileName() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getLastFileName()");
        }

        if (backupDirFile == null || "".equals(backupDirFile)) {
          A.log("getBackupDirFile not found for :" + accessGroup);
        }

        return backupDirFile;
    }
}


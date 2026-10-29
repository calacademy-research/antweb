package org.calacademy.antweb.home;

import java.util.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.util.*;

public class WorldantsUploadDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(WorldantsUploadDb.class);

    public WorldantsUploadDb(Connection connection) {
      super(connection);
    }

    /*
     Worldants file is fetched from a machine that runs a job off of a snapshot of the 
     antcat database.

     http://ibss-info/antcat.antweb.txt   <- This is the worldants download file.
     http://ibss-info/download_results.log   <- log file?

     // File is here: http://antcat-export:9090/antcat.antweb.txt
     // Requires VPN access. Can be fetched from server:
     //   wget http://antcat-export:9090/antcat.antweb.txt .

     // Copies are stored here: /data/antweb/web/upload/

     // Working directory is /data/antweb/web/workingdir/ which is /usr/local/antweb/web/workingdir
     //  -rw-r--r-- 1 root root 102137857 Mar 16 11:43 /data/antweb/web/workingdir/worldants.txt
     //  -rw-r--r-- 1 root root 102137857 Mar 16 06:00 /data/antweb/web/workingdir/worldants_speciesList.txt     

     Stats from each upload are stored in the worldants_upload table. Visible here:
       https://www.antweb.org/query.do?name=worldantsUploads

    */    
    
    public void insertWorldantsUpload(String backupFileName, int backupFileSize, int origWorldantsCount, String validateMessage, int fileSize) throws SQLException {

        // Bound values: the validation message may quote the downloaded file (a name with an apostrophe).
        String insert = "insert into worldants_upload(backup_file_name, backup_file_size, orig_worldants_count, validate_message, file_size) "
              + "values (?, ?, ?, ?, ?)";
        PreparedStatement stmt = null;
        try {
            stmt = getConnection().prepareStatement(insert);
            stmt.setString(1, String.valueOf(backupFileName));   // null stays the text 'null', as before
            stmt.setInt(2, backupFileSize);
            stmt.setInt(3, origWorldantsCount);
            stmt.setString(4, String.valueOf(validateMessage));
            stmt.setInt(5, fileSize);
            stmt.executeUpdate();

            if (!getConnection().getAutoCommit()) getConnection().commit();
        } catch (SQLException e) {
            s_log.error("insertWorldantsUpload() e:" + e);
            throw e;
        } finally {
           DBUtil.close(stmt, null, this, "insertWorldantsUpload()");        
        }    
    }

    public void updateWorldantsUpload(UploadDetails uploadDetails) throws SQLException {
        //AntwebUtil.logStackTrace();
        int id = getMaxWorldantsId(); 
        String logFileName = uploadDetails.getLogFileName();
        String execTime = uploadDetails.getExecTime();
        
        String update = "update worldants_upload set log_file_name = ?, exec_time = ?, operation = ? where id = ?";
        PreparedStatement stmt = null;
        try {
            stmt = getConnection().prepareStatement(update);
            // null was written as the text 'null' when these were concatenated: kept
            stmt.setString(1, String.valueOf(logFileName));
            stmt.setString(2, String.valueOf(execTime));
            stmt.setString(3, String.valueOf(uploadDetails.getOperation()));
            stmt.setInt(4, id);
            stmt.executeUpdate();

            s_log.debug("updateWorldantsUpload() id:" + id);

            if (!getConnection().getAutoCommit()) getConnection().commit();
        } catch (SQLException e) {
            s_log.error("updateWorldantsUpload() e:" + e + " update:" + update);
            throw e;
        } finally {
           DBUtil.close(stmt, null, this, "updateWorldantsUpload()");        
        }    
    }

        
    public int getMaxWorldantsId() throws SQLException {
      int maxWorldantsId = 0;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select max(id) id from worldants_upload";
        stmt = DBUtil.getStatement(getConnection(), "WorldantsUploadDb.getMaxWorldantsId()");
        stmt.execute(query);
        rset = stmt.getResultSet();
        while (rset.next()) {
          maxWorldantsId = rset.getInt("id");        
        }        
      } catch (SQLException e) {
        s_log.warn("getMaxWorldantsId() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, rset, "WorldantsUploadDb.getMaxWorldantsId()");      
      }
      //A.log("getMaxSpecimenUploadId() id:" + maxUploadId);
      return maxWorldantsId;     
    }
            
    /**
     * The number of AntCat imports of the last 7 days whose file differed in size from the one before it
     * (2026-10-29). Before, the distinct sizes of a window of 7 to 13 days were counted minus one: a missing
     * backup size added one, a file that came back to an earlier size was not counted, and no row gave -1.
     */
    public static final String CHANGE_COUNT_SQL = "select count(*) from worldants_upload where created >= now() - interval 7 day"
        + " and backup_file_size > 0 and file_size <> backup_file_size";   // 0: no earlier file to compare with

    public int getWorldantsChangeCount() throws SQLException {
        int count = 0;
        String query = CHANGE_COUNT_SQL;
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "WorldantsUploadDb.getWorldantsChangeCount()");
          rset = stmt.executeQuery(query);
          if (rset.next()) count = Math.max(0, rset.getInt(1));
        } catch (SQLException e) {
          s_log.warn("getWorldantsAlert() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "UploadDb.getWorldantsAlert()");
        }      
        s_log.debug("getWorldantsAlert() count:" + count);
        return count;
    }

    public void deleteHomonymsWithoutTaxa() throws SQLException {
        // Delete from the proj_taxon table.
        // Should we also delete from the homonym table?
        UtilDb utilDb = new UtilDb(getConnection());
        String dml = "delete from proj_taxon where taxon_name not in (select taxon_name from taxon) "
                + " and source not in ('worldants', 'fossilants') and taxon_name in (select taxon_name from homonym)";
        utilDb.runDml(dml);

        dml = "delete from homonym where taxon_name not in (select taxon_name from taxon)";
        utilDb.runDml(dml);
    }
}

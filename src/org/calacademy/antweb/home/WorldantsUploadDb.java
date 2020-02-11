package org.calacademy.antweb.home;

import java.util.*;
import java.util.Date;

import javax.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.regexp.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.AntwebUpload;

public class WorldantsUploadDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(WorldantsUploadDb.class);

    public WorldantsUploadDb(Connection connection) {
      super(connection);
    }

    /*

     Worldants file is fetched from a machine that runs a job off of a snapshot of the 
     antcat database.
    
     ssh mjohnson@ibss-info.calacademy.org
 
     http://ibss-info/antcat.antweb.txt   <- This is the worldants download file.
     http://ibss-info/download_results.log   <- log file?

     Located here:
       /home/antcat-download/antcat.antweb.txt

     Stats from each upload are stored in the worldants_upload table. Visible here:
       https://www.antweb.org/query.do?name=worldantsUploads

    */    
    
    public void insertWorldantsUpload(String backupFileName, int backupFileSize, int origWorldantsCount, String validateMessage, int fileSize) {

        String insert = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertWorldantsUpload()");
            insert = "insert into worldants_upload(backup_file_name, backup_file_size, orig_worldants_count, validate_message, file_size) " 
              + "values ('" + backupFileName + "', " + backupFileSize + ", " + origWorldantsCount + ", '" + validateMessage + "', " + fileSize + ")";  
            stmt.executeUpdate(insert);

            //A.log("insertWorldantsUpload() insert:" + insert);

            getConnection().commit();
        } catch (SQLException e) {
            s_log.error("insertWorldantsUpload() e:" + e);
        } finally {
           DBUtil.close(stmt, null, this, "insertWorldantsUpload()");        
        }    
    }

    public void updateWorldantsUpload(UploadDetails uploadDetails) {
        //AntwebUtil.logStackTrace();
        int id = getMaxWorldantsId(); 
        String logFileName = uploadDetails.getLogFileName();
        String execTime = uploadDetails.getExecTime();
        
        String update = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateWorldantsUpload()");
            update = "update worldants_upload set log_file_name = '" + logFileName + "', exec_time = '" + execTime + "', operation = '" + uploadDetails.getOperation() + "' where id = " + id; 
            stmt.executeUpdate(update);

            A.log("updateWorldantsUpload() update:" + update);

            getConnection().commit();
        } catch (SQLException e) {
            s_log.error("updateWorldantsUpload() e:" + e + " update:" + update);
        } finally {
           DBUtil.close(stmt, null, this, "updateWorldantsUpload()");        
        }    
    }

        
    public int getMaxWorldantsId()
    {
      int maxWorldantsId = 0;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select max(id) id from worldants_upload";
        stmt = DBUtil.getStatement(getConnection(), "UploadDb.getMaxWorldantsId()");
        stmt.execute(query);
        rset = stmt.getResultSet();
        while (rset.next()) {
          maxWorldantsId = rset.getInt("id");        
        }        
      } catch (SQLException e) {
        s_log.warn("getMaxUploadId() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "UploadDb.getMaxWorldantsId()");      
      }
      //A.log("getMaxSpecimenUploadId() id:" + maxUploadId);
      return maxWorldantsId;     
    }
            
    // The number of times that the worldants upload file has changed in the last week.        
    public int getWorldantsChangeCount() {
        int count = 0;
        String query = "SELECT file_size, backup_file_size FROM worldants_upload WHERE created >= curdate() - INTERVAL DAYOFWEEK(curdate())+6 DAY";
        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "UploadDb.getWorldantsAlert()");
          stmt.execute(query);
          rset = stmt.getResultSet();
          HashSet<Integer> intSet = new HashSet<Integer>();
          while (rset.next()) {
            int fileSize = rset.getInt("file_size");        
            intSet.add(fileSize);
            int backupFileSize = rset.getInt("backup_file_size");        
            intSet.add(backupFileSize);
          }
          count = intSet.size() - 1;
        } catch (SQLException e) {
          s_log.warn("getWorldantsAlert() e:" + e);
        } finally {
          DBUtil.close(stmt, rset, "UploadDb.getWorldantsAlert()");
        }      
        A.log("getWorldantsAlert() count:" + count);
        return count;
    }

    public void deleteHomonymsWithoutTaxa() {
        // Delete from the proj_taxon table.
        // Should we also delete from the homonym table?
    
        String dml = "delete from proj_taxon where taxon_name not in (select taxon_name from taxon) " 
          + " and source not in ('worldants', 'fossilants') and taxon_name in (select taxon_name from homonym)";

        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteHomonymsWithoutTaxa()");
            stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("deleteHomonymsWithoutTaxa() e:" + e + " dml:" + dml);
        } finally {
           DBUtil.close(stmt, null, this, "deleteHomonymsWithoutTaxa()");        
        }    
    }    
    
}

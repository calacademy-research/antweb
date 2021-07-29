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
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;


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
    
    public void updateSpecimenUploadDate(Group group) throws SQLException {
        
        int id = group.getId();
        String query = "update ant_group set last_specimen_upload=" + currentDateFunction + " where id =" +  id;
        Statement stmt = null; 
        try {
            stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.uploadSpecimenUploadDate()");
            stmt.executeUpdate(query);
        } catch (SQLException e) {
            uploadLog.error("updateSpecimenUploadDate() e: " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.updateSpecimenUploadDate()");
        }        
    }
    
    void deleteSpecimen(String code) throws SQLException {
        String query = "";
        Statement stmt = null; 
        try {
            query = "delete from specimen where code ='" + code + "'";
            stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.deleteSpecimen()");
            stmt.executeUpdate(query);
        } catch (SQLException e) {
            uploadLog.error("problem deleteSpecimen() code:" + code + " " + e);
        } finally {
            DBUtil.close(stmt, "SpecimenUploadDb.deleteSpecimen()");
        }
    }
    
    public void dropSpecimens(Group group) throws SQLException {
        int count = 0;
        String query = "";
        Statement stmt = null; 
        try {
        
        /*
          if ((AntwebProps.isDevMode()) && (group.getId() == 2) && (false)) {
            query = "delete from specimen where code = " + "\"CASENT0625035\"";
            stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.dropSpecimens()");
            stmt.executeUpdate(query);
            s_log.warn("dropSpecimens() just deleting specimen CASENT0625035.");
            return;
          }
          */

          query = "delete from specimen where access_group = " + group.getId();
          //s_log.warn("dropSpecimens() why is this query so slow:" + query);                    
          stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.dropSpecimens()");
          count = stmt.executeUpdate(query);

          LogMgr.appendLog("taxonSet.log", "specimenUploadDb.dropSpecimens(" + group + "):" + count, true);
          
          uploadLog.info("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 1 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens()");
        }

        try {
          query = "delete from taxon where access_group = " + group.getId() + " and insert_method in ('addMissingGenus', 'homononymMirroringTaxon')";
          //s_log.warn("dropSpecimens() query:" + query);                    
          stmt = DBUtil.getStatement(getConnection(), "SpecimenUploadDb.dropSpecimens()");
          count = stmt.executeUpdate(query);
          //s_log.warn("dropSpecimens() count:" + count + " query completed");
        } catch (SQLException e) {
          uploadLog.error("dropSpecimens() 2 e:" + e + " query:" + query);
        } finally {
          DBUtil.close(stmt, "SpecimenUploadDb.dropSpecimens()");
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

}


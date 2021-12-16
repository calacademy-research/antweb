package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.LogMgr;

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

}


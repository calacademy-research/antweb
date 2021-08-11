package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.curate.speciesList.*;

public class ProjTaxonLogDb extends TaxonSetLogDb {
    
    private static Log s_log = LogFactory.getLog(ProjTaxonLogDb.class);
        
    public ProjTaxonLogDb(Connection connection) {
      super(connection);
    }

/*  Proj_taxon_log */

    public void deleteAllLogs() throws SQLException {
      Statement stmt = DBUtil.getStatement(getConnection(), "ProjTaxonDb.deleteAllLogs()");

      boolean debug = true;   
      String dml = null;
               
      try {
        dml = "delete from proj_taxon_log";
        stmt.executeUpdate(dml);        

        dml = "delete from proj_taxon_log_detail";
        stmt.executeUpdate(dml);        

      } finally {
        DBUtil.close(stmt, "ProjTaxonDb.deleteAllLogs()");
      }   
    }

    public void archiveSpeciesList(String projectName, Login curatorLogin) throws SQLException {

      A.log("archiveSpeciesList() projectName:" + projectName + " curatorLogin:" + curatorLogin);

      boolean debug = false; //true;            
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "ProjTaxonDb.archiveSpeciesList()");

        String dml = "";
        // Update the existing proj_taxon_log record for the current proj_taxon data, or insert one.  Old curator id remains intact.
        dml = "update proj_taxon_log set is_current = 0 where project_name = '" + projectName + "' and is_current = 1";
        if (debug) s_log.warn("archiveSpeciesList() 1 dml:" + dml);
        int result = stmt.executeUpdate(dml);
        if (result == 0) {
          // We could not update it.  Record did not exist.  Create it with curator_id = 0;
          dml = "insert into proj_taxon_log (project_name, curator_id, is_current) values ('" + projectName + "', 0, 0)";
          stmt.executeUpdate(dml);
        }
        if (debug) s_log.warn("archiveSpeciesList() 2 result:" + result + " dml:" + dml);

        // Get the maxLogId.
        int maxLogId = 0;
        String query = "select max(log_id) as log_id from proj_taxon_log where project_name = '" + projectName + "'";
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            maxLogId = rset.getInt("log_id");
        }
        if (debug) s_log.warn("archiveSpeciesList() 3 maxLogId:" + maxLogId + " projectName:" + projectName);

        // Create a new proj_taxon_log to point at the live data with this curator's id
        dml = "insert into proj_taxon_log (project_name, curator_id, is_current) values ('" + projectName + "', '" + curatorLogin.getId() + "', 1)";
        stmt.executeUpdate(dml);        

        if (debug) s_log.warn("archiveSpeciesList() 4 dml:" + dml);

        dml = "insert into proj_taxon_log_detail (project_name, taxon_name, created, subfamily_count, genus_count, species_count, specimen_count, image_count) " 
          + " select project_name, pt.taxon_name, pt.created, pt.subfamily_count, pt.genus_count, pt.species_count, pt.specimen_count, pt.image_count " 
          + " from proj_taxon pt, taxon t where pt.taxon_name = t.taxon_name " 
          + " and ( taxarank = 'species' or taxarank = 'subspecies')"
          + " and project_name = '" + projectName + "'";
        stmt.executeUpdate(dml);

        if (debug) s_log.warn("archiveSpeciesList() 5 dml:" + dml);

        dml = "update proj_taxon_log_detail set log_id = " + maxLogId + " where log_id = 0";
        stmt.executeUpdate(dml);        

        if (debug) s_log.warn("archiveSpeciesList() 6 dml:" + dml);

      } finally {
        DBUtil.close(stmt, rset, "ProjTaxonDb.archiveSpeciesList()");
      }        
    }

    public ArrayList<ProjTaxonLog> getProjTaxonLogs(String projectName, int logId) {
        return getProjTaxonLogs(projectName, logId, null);
    }
    
    public ArrayList<ProjTaxonLog> getProjTaxonLogs(String projectName, int logId, String displaySubfamily) {
    
        ArrayList<ProjTaxonLog> projTaxonLogs = new ArrayList<>();
        
        // The first ProjTaxonLog in the list will be the master list.
        ProjTaxonLog masterLog = new ProjTaxonLog();
        ArrayList<ProjTaxonLogDetail> masterDetails = new ArrayList<>();
        masterLog.setDetails(masterDetails);
        projTaxonLogs.add(masterLog);

        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getProjTaxonLogs() projectName:" + projectName);

            theQuery = " select log_id, project_name, created, curator_id, is_current" 
              + " from proj_taxon_log " 
              + " where 1=1";
              if (projectName != null) theQuery += " and project_name='" + projectName + "'";
              if (logId != 0) theQuery += " and (log_id <= " + logId + " or is_current = 1)";
              //if (created != null) theQuery += " and created = '" + AntwebUtil.getFormatDateTimeStr(created) + "'";
              //if (curatorId != 0) theQuery += " and curator_id = " + curatorId;
            theQuery += " order by log_id desc";
            theQuery += " limit 6";

            //A.log("getProjTaxonLogs() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);
 
            int count = 0;
            while (rset.next()) {
                ++count;
                ProjTaxonLog projTaxonLog = new ProjTaxonLog();
                logId = rset.getInt("log_id");
                projTaxonLog.setLogId(logId);
                String selectedProjectName = rset.getString("project_name");
                projTaxonLog.setProjectName(selectedProjectName);
                projTaxonLog.setCreated(rset.getTimestamp("created"));
                projTaxonLog.setCuratorId(rset.getInt("curator_id"));

                int isCurrent = rset.getInt("is_current");
                projTaxonLog.setIsCurrent(isCurrent == 1);
                ArrayList<ProjTaxonLogDetail> projTaxonLogDetails = null;
                if (projTaxonLog.getIsCurrent()) {
                  projTaxonLogDetails = getProjTaxonLogDetails(projectName, displaySubfamily);
                } else {
                  projTaxonLogDetails = getProjTaxonLogDetails(logId, displaySubfamily);
                }
                Collections.sort(projTaxonLogDetails);
                projTaxonLog.setDetails(projTaxonLogDetails);

                for (ProjTaxonLogDetail detail : projTaxonLogDetails) {
                  //A.log("getProjTaxonLogs() add detail:" + detail);
                  masterDetails.remove(detail);  // So that the list remains unique.
                  masterDetails.add(detail);
                }
                projTaxonLogs.add(projTaxonLog);
            }

            Collections.sort(masterDetails);
            //if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getProjTaxonLogs() not found projectTaxonLog:" + projectName);
        } catch (SQLException e) {
            s_log.error("getProjTaxonLogs() projectName:" + projectName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getProjTaxonLogs() projectName:" + projectName);
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return projTaxonLogs;
    }       

    public ArrayList<ProjTaxonLogDetail> getProjTaxonLogDetails(String projectName, String displaySubfamily) {

        ArrayList<ProjTaxonLogDetail> projTaxonLogDetails = new ArrayList<>();

        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getProjTaxonLogDetails() projectName:" + projectName);

            String subfamilyClause = "";
            if (displaySubfamily != null) subfamilyClause = " and t.subfamily = '" + displaySubfamily + "'";

            String fields = " pt.project_name, pt.taxon_name, pt.created"
              + ", pt.subfamily_count, pt.genus_count, pt.species_count, pt.specimen_count, pt.image_count";
            theQuery = "select " + fields
              + " from proj_taxon pt, taxon t"
              + " where pt.taxon_name = t.taxon_name " 
              + " and (t.taxarank = 'species' or t.taxarank = 'subspecies')"
              + subfamilyClause
              ;
            if (projectName != null) theQuery += " and pt.project_name = '" + projectName + "'";
                            
            //A.log("getProjTaxonLogDetails() query:" + theQuery);

            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                ProjTaxonLogDetail projTaxonLogDetail = new ProjTaxonLogDetail();
                projTaxonLogDetail.setProjectName(rset.getString("project_name"));
                String taxonName = rset.getString("taxon_name");
                projTaxonLogDetail.setTaxonName(taxonName);
                //projTaxonLogDetail.setTaxon(TaxonDb.getInfoInstance(getConnection(), taxonName));
                projTaxonLogDetail.setCreated(rset.getTimestamp("created"));
                projTaxonLogDetail.setSubfamilyCount(rset.getInt("subfamily_count"));
                projTaxonLogDetail.setGenusCount(rset.getInt("genus_count"));
                projTaxonLogDetail.setSpeciesCount(rset.getInt("species_count"));
                projTaxonLogDetail.setSpecimenCount(rset.getInt("specimen_count"));
                projTaxonLogDetail.setImageCount(rset.getInt("image_count"));
                projTaxonLogDetails.add(projTaxonLogDetail);
            }

            //A.log("getProjTaxonLogDetails() projectName:" + projectName + " count:" + count);

        } catch (SQLException e) {
            s_log.error("getProjTaxonLogDetails() projectName:" + projectName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getProjTaxonLogDetails() projectName:" + projectName);
        }

        //A.log("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return projTaxonLogDetails;
    }    
         
    public ArrayList<ProjTaxonLogDetail> getProjTaxonLogDetails(int logId, String displaySubfamily) {

        ArrayList<ProjTaxonLogDetail> projTaxonLogDetails = new ArrayList<>();

        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getProjTaxonLogDetails() logId:" + logId);

            String fields = " project_name, taxon_name, created"
              + ", subfamily_count, genus_count, species_count, specimen_count, image_count"
              + ", log_id";
            theQuery = "select " + fields 
              + " from proj_taxon_log_detail" 
              + " where 1=1";
            if (logId != 0) theQuery += " and log_id = " + logId;
            if (displaySubfamily != null && !"none".equals(displaySubfamily)) 
              theQuery += " and taxon_name like '" + displaySubfamily + "%'"; 
                            
            //A.log("getProjTaxonLogDetails() query:" + theQuery);

            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                ProjTaxonLogDetail projTaxonLogDetail = new ProjTaxonLogDetail();
                projTaxonLogDetail.setProjectName(rset.getString("project_name"));
                String taxonName = rset.getString("taxon_name");
                projTaxonLogDetail.setTaxonName(taxonName);
                //projTaxonLogDetail.setTaxon(TaxonDb.getInfoInstance(getConnection(), taxonName));
                projTaxonLogDetail.setCreated(rset.getTimestamp("created"));
                projTaxonLogDetail.setSubfamilyCount(rset.getInt("subfamily_count"));
                projTaxonLogDetail.setGenusCount(rset.getInt("genus_count"));
                projTaxonLogDetail.setSpeciesCount(rset.getInt("species_count"));
                projTaxonLogDetail.setSpecimenCount(rset.getInt("specimen_count"));
                projTaxonLogDetail.setImageCount(rset.getInt("image_count"));
                projTaxonLogDetail.setLogId(rset.getInt("log_id"));
                projTaxonLogDetails.add(projTaxonLogDetail);
            }

            //A.log("getProjTaxonLogDetails() logId:" + logId + " count:" + count);

        } catch (SQLException e) {
            s_log.error("getProjTaxonLogDetails() logId:" + logId + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getProjTaxonLogDetails() logId:" + logId);
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return projTaxonLogDetails;
    }                  
    

    public ArrayList<Login> getCuratorLogins(Login login) {
        // Login is administrator or curator conducting the search.

        ArrayList<Login> logins = new ArrayList<>();

        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCuratorLogins()");

            theQuery = " select distinct curator_id, l.name" 
              + " from proj_taxon_log ptl, login l" 
              + " where ptl.curator_id = l.id";
              if (!login.isAdmin()) theQuery += " and curator_id = " + login.getId();
            theQuery += " order by l.name";  
                            
            //A.log("getCuratorLogins() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            LoginDb loginDb = new LoginDb(getConnection());
            while (rset.next()) {
                ++count;
                int curatorId = rset.getInt("curator_id");
        //A.log("getCuratorLogins() curatorId:" + curatorId);        
                logins.add(loginDb.getLogin(curatorId));
            }
            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getCuratorLogins() not found loginId:" + login.getId());
        } catch (SQLException e) {
            s_log.error("getCuratorLogins() curatorId:" + login.getId() + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCuratorLogins()");
        }
 
        A.log("getCuratorLogins() login:" + login);        
        return logins;
    }        
     
 
// ----------------- Dispute ----------------------------------------    
     
    public void insertDispute(TaxonSet taxonSet) throws SQLException {
    
        ProjTaxon projTaxon = (ProjTaxon) taxonSet;
        
        if (projTaxon == null) {
          s_log.error("insertDispute() projTaxon is null");
          AntwebUtil.logStackTrace();
        }
    
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertDispute()");

            dml = "insert into proj_taxon_dispute (project_name, taxon_name, source, rev, curator_id) " 
              + " values ('" + projTaxon.getProjectName() + "', '" + projTaxon.getTaxonName() + "', '" + projTaxon.getSource() + "', " + projTaxon.getRev() + ", " + projTaxon.getCuratorId() + " )";  
            stmt.execute(dml);

            //A.log("insertDispute() projTaxon:" + projTaxon);
        } catch (SQLException e) {
            s_log.error("insertDispute() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "insertDispute()");
        }
    }

    public void removeDispute(String projectName, String taxonName) throws SQLException {
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "removeDispute()");

            dml = "delete from proj_taxon_dispute "
              + " where project_name = '" + projectName + "'"
              + "   and taxon_name = '" + taxonName + "'";

            stmt.execute(dml);

            //if (AntwebProps.isDevMode()) s_log.error("removeDispute() projectName:" + projectName + " taxonName:" + taxonName);
        } catch (SQLException e) {
            s_log.error("removeDispute() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "removeDispute()");
        }
    }
    
    public ProjTaxon getDispute(String projectName, String taxonName) throws SQLException {
        String query = "";
        ProjTaxon projTaxon = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {

            stmt = DBUtil.getStatement(getConnection(), "getDispute()");
            query = "select project_name, taxon_name, source, rev from proj_taxon_dispute " 
               + " where project_name = \"" + projectName + "\""
               + " and taxon_name = \"" + taxonName + "\"";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                projTaxon = new ProjTaxon();
                projTaxon.setProjectName((String) rset.getString("project_name"));
                projTaxon.setTaxonName((String) rset.getString("taxon_name"));
                projTaxon.setSource((String) rset.getString("source"));
                projTaxon.setRev(rset.getInt("rev"));
            }
        } catch (SQLException e) {
            s_log.error("getDspute:" + projectName + ", " + taxonName);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getDispute()");
        }
        //if (!exists) s_log.info("getDispute() false.  query:" + query);
        return projTaxon;
    }
    
    public ArrayList<TaxonSet> getDisputes() throws SQLException {
      return getDisputes(null, null);
    }
        
    public ArrayList<TaxonSet> getDisputes(String projectName, String taxonName) throws SQLException {
        ArrayList<TaxonSet> disputes = new ArrayList<>();

        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        String projectClause = ""; if (projectName != null) projectClause = " and project_name = '" + projectName + "'";
        String taxonClause = ""; if (taxonName != null) taxonClause = " and taxon_name = '" + taxonName + "'";
        try {

            stmt = DBUtil.getStatement(getConnection(), "getDisputes()");
            query = "select project_name, taxon_name, source, rev from proj_taxon_dispute "
               + " where 1 = 1 " 
               + projectClause
               + taxonClause
            ;
            
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                ProjTaxon projTaxon = new ProjTaxon();
                projTaxon.setProjectName((String) rset.getString("project_name"));
                projTaxon.setTaxonName((String) rset.getString("taxon_name"));
                projTaxon.setSource((String) rset.getString("source"));
                projTaxon.setRev(rset.getInt("rev"));
                disputes.add(projTaxon);
            }
        } catch (SQLException e) {
            s_log.error("getDisputes:" + projectName + ", " + taxonName);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getDisputes()");
        }
        return disputes;
    }    
    
                    
}
package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.curate.speciesList.*;

public class GeolocaleTaxonLogDb extends TaxonSetLogDb {
    
    private static Log s_log = LogFactory.getLog(GeolocaleTaxonLogDb.class);
        
    public GeolocaleTaxonLogDb(Connection connection) {
      super(connection);
    }

    public void deleteAllLogs() throws SQLException {
      Statement stmt = DBUtil.getStatement(getConnection(), "GeolocaleTaxonDb.deleteAllLogs()");

      boolean debug = true;   
      String dml = null;
               
      try {
        dml = "delete from geolocale_taxon_log";
        stmt.executeUpdate(dml);        

        dml = "delete from geolocale_taxon_log_detail";
        stmt.executeUpdate(dml);        

      } finally {
        DBUtil.close(stmt, "GeolocaleTaxonDb.deleteAllLogs()");
      }   
    }
    
    public void archiveSpeciesList(String speciesListName, Login curatorLogin) throws SQLException {

      int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);

      Statement stmt = null;
      ResultSet rset = null;
      boolean debug = false;            
      try {

        stmt = DBUtil.getStatement(getConnection(), "GeolocaleTaxonLogDb.archiveSpeciesList()");

        String dml = "";
        // Update the existing geolocale_taxon_log record for the current geolocale_taxon data, or insert one.  Old curator id remains intact.
        dml = "update geolocale_taxon_log set is_current = 0 where geolocale_id = " + geolocaleId + " and is_current = 1";
        if (debug) s_log.warn("archiveSpeciesList() 1 dml:" + dml);
        int result = stmt.executeUpdate(dml);
        if (result == 0) {
          // We could not update it.  Record did not exist.  Create it with curator_id = 0;
          dml = "insert into geolocale_taxon_log (geolocale_id, curator_id, is_current) values ('" + geolocaleId + "', 0, 0)";
          stmt.executeUpdate(dml);
        }
        if (debug) s_log.warn("archiveSpeciesList() 2 result:" + result + " dml:" + dml);

        // Get the maxLogId.
        int maxLogId = 0;
        String query = "select max(log_id) as log_id from geolocale_taxon_log where geolocale_id = " + geolocaleId;
        rset = stmt.executeQuery(query);
        while (rset.next()) {
            maxLogId = rset.getInt("log_id");
        }
        if (debug) s_log.warn("archiveSpeciesList() 3 maxLogId:" + maxLogId + " geolocaleId:" + geolocaleId);

        // Create a new geolocale_taxon_log to point at the live data with this curator's id
        dml = "insert into geolocale_taxon_log (geolocale_id, curator_id, is_current) values (" + geolocaleId + ", '" + curatorLogin.getId() + "', 1)";
        stmt.executeUpdate(dml);        

        if (debug) s_log.warn("archiveSpeciesList() 4 dml:" + dml);

        dml = "insert into geolocale_taxon_log_detail (geolocale_id, taxon_name, created, subfamily_count, genus_count, species_count, specimen_count, image_count) " 
          + " select gt.geolocale_id, gt.taxon_name, gt.created, gt.subfamily_count, gt.genus_count, gt.species_count, gt.specimen_count, gt.image_count " 
          + " from geolocale_taxon gt, taxon t where gt.taxon_name = t.taxon_name " 
          + " and ( taxarank = 'species' or taxarank = 'subspecies')"
          + " and geolocale_id = " + geolocaleId;
        stmt.executeUpdate(dml);

        if (debug) s_log.warn("archiveSpeciesList() 5 dml:" + dml);

        dml = "update geolocale_taxon_log_detail set log_id = " + maxLogId + " where log_id = 0";
        stmt.executeUpdate(dml);        

        if (debug) s_log.warn("archiveSpeciesList() 6 dml:" + dml);

      } finally {
        DBUtil.close(stmt, rset, "GeolocaleTaxonLogDb.archiveSpeciesList()");
      }        
    }

    public ArrayList<GeolocaleTaxonLog> getGeolocaleTaxonLogs(int geolocaleId, int logId) {
        return getGeolocaleTaxonLogs(geolocaleId, logId, null);
    }

    public ArrayList<GeolocaleTaxonLog> getGeolocaleTaxonLogs(int geolocaleId, int logId, String displaySubfamily) {

        if (geolocaleId == 0) { 
          s_log.warn("getGeolocaleTaxonLog geolocaleId:0 displaySubfamily:" + displaySubfamily);
          return null;
        }
        
        ArrayList<GeolocaleTaxonLog> geolocaleTaxonLogs = new ArrayList<>();

        // The first GeolocaleTaxonLog in the list will be the master list.
        GeolocaleTaxonLog masterLog = new GeolocaleTaxonLog();
        ArrayList<GeolocaleTaxonLogDetail> masterDetails = new ArrayList<>();
        masterLog.setDetails(masterDetails);
        geolocaleTaxonLogs.add(masterLog);

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getGeolocaleTaxonLogs()");

            query = " select log_id, geolocale_id, created, curator_id, is_current" 
              + " from geolocale_taxon_log " 
              + " where 1=1";
              if (geolocaleId != 0) query += " and geolocale_id =" + geolocaleId;
              if (logId != 0) query += " and (log_id <= " + logId + " or is_current = 1)";
              //if (created != null) query += " and created = '" + AntwebUtil.getFormatDateTimeStr(created) + "'";
              //if (curatorId != 0) query += " and curator_id = " + curatorId;
            query += " order by log_id desc";
            query += " limit 6";

            //A.log("getGeolocaleTaxonLogs() query:" + query);
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                GeolocaleTaxonLog geolocaleTaxonLog = new GeolocaleTaxonLog();
                logId = rset.getInt("log_id");
                geolocaleTaxonLog.setLogId(logId);
                int selectedGeolocaleId = rset.getInt("geolocale_id");
                geolocaleTaxonLog.setGeolocaleId(selectedGeolocaleId);
                geolocaleTaxonLog.setCreated(rset.getTimestamp("created"));
                geolocaleTaxonLog.setCuratorId(rset.getInt("curator_id"));

                int isCurrent = rset.getInt("is_current");
                geolocaleTaxonLog.setIsCurrent((isCurrent == 1) ? true : false);
                ArrayList<GeolocaleTaxonLogDetail> geolocaleTaxonLogDetails = null;
                if (geolocaleTaxonLog.getIsCurrent()) {
                  geolocaleTaxonLogDetails = getGeolocaleTaxonLogDetailsFromGeo(geolocaleId, displaySubfamily);
                } else {
                  geolocaleTaxonLogDetails = getGeolocaleTaxonLogDetails(logId, displaySubfamily);
                }
                Collections.sort(geolocaleTaxonLogDetails);
                geolocaleTaxonLog.setDetails(geolocaleTaxonLogDetails);

                for (GeolocaleTaxonLogDetail detail : geolocaleTaxonLogDetails) {
                  //A.log("getGeolocaleTaxonLogs() add detail:" + detail);
                  masterDetails.remove(detail);  // So that the list remains unique.
                  masterDetails.add(detail);
                }
                geolocaleTaxonLogs.add(geolocaleTaxonLog);
            }

            Collections.sort(masterDetails);
            //if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getGeolocaleTaxonLogs() not found geolocaleId:" + geolocaleId);
        } catch (SQLException e) {
            s_log.error("getGeolocaleTaxonLogs() geolocaleId:" + geolocaleId + " exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getGeolocaleTaxonLogs()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);        
        return geolocaleTaxonLogs;
    }       

    private ArrayList<GeolocaleTaxonLogDetail> getGeolocaleTaxonLogDetailsFromGeo(int geolocaleId, String displaySubfamily) {

        ArrayList<GeolocaleTaxonLogDetail> geolocaleTaxonLogDetails = new ArrayList<>();

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getGeolocaleTaxonLogDetailsFromGeo()");

            String subfamilyClause = "";
            if (displaySubfamily != null) subfamilyClause = " and t.subfamily = '" + displaySubfamily + "'";

            String fields = " gt.geolocale_id, gt.taxon_name, gt.created"
              + ", gt.subfamily_count, gt.genus_count, gt.species_count, gt.specimen_count, gt.image_count";
            query = "select " + fields
              + " from geolocale_taxon gt, taxon t"
              + " where gt.taxon_name = t.taxon_name " 
              + " and (t.taxarank = 'species' or t.taxarank = 'subspecies')"
              + subfamilyClause
              ;
            if (geolocaleId != 0) query += " and gt.geolocale_id = '" + geolocaleId + "'";
                                              
            //A.log("getGeolocaleTaxonLogDetailsFromGeo() query:" + query);

            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                GeolocaleTaxonLogDetail geolocaleTaxonLogDetail = new GeolocaleTaxonLogDetail();
                geolocaleTaxonLogDetail.setGeolocaleId(rset.getInt("geolocale_id"));
                String taxonName = rset.getString("taxon_name");
                geolocaleTaxonLogDetail.setTaxonName(taxonName);
                //geolocaleTaxonLogDetail.setTaxon(TaxonDb.getInfoInstance(getConnection(), taxonName));
                geolocaleTaxonLogDetail.setCreated(rset.getTimestamp("created"));
                geolocaleTaxonLogDetail.setSubfamilyCount(rset.getInt("subfamily_count"));
                geolocaleTaxonLogDetail.setGenusCount(rset.getInt("genus_count"));
                geolocaleTaxonLogDetail.setSpeciesCount(rset.getInt("species_count"));
                geolocaleTaxonLogDetail.setSpecimenCount(rset.getInt("specimen_count"));
                geolocaleTaxonLogDetail.setImageCount(rset.getInt("image_count"));
                //geolocaleTaxonLogDetail.setLogId(rset.getInt("log_id"));
                geolocaleTaxonLogDetails.add(geolocaleTaxonLogDetail);
            }

            //A.log("getGeolocaleTaxonLogDetailsFromGeo() geolocaleId:" + geolocaleId + " count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getGeolocaleTaxonLogDetailsFromGeo() geolocaleId:" + geolocaleId + " exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getGeolocaleTaxonLogDetailsFromGeo()");
        }

        //A.log("getInfoInstance() name:" + taxonName + " query:" + query);        
        return geolocaleTaxonLogDetails;
    }     
    
    public ArrayList<GeolocaleTaxonLogDetail> getGeolocaleTaxonLogDetails(int logId, String displaySubfamily) {

        ArrayList<GeolocaleTaxonLogDetail> geolocaleTaxonLogDetails = new ArrayList<>();

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getGeolocaleTaxonLogDetails()");

            String fields = " gt.geolocale_id, gt.taxon_name, gt.created"
              + ", gt.subfamily_count, gt.genus_count, gt.species_count, gt.specimen_count, gt.image_count, gt.log_id";
            query = "select " + fields
              + " from geolocale_taxon_log_detail gt"
              //+ " where gt.taxon_name = t.taxon_name " 
              //+ " and (t.taxarank = 'species' or t.taxarank = 'subspecies')"
              //+ subfamilyClause
              + " where 1=1";
            if (logId != 0) query += " and log_id = " + logId;
            if (displaySubfamily != null && !"none".equals(displaySubfamily)) 
              query += " and taxon_name like '" + displaySubfamily + "%'";

            //if (geolocaleId != 0) query += " and gt.geolocale_id = " + geolocaleId;
                            
            //A.log("getGeolocaleTaxonLogDetails() query:" + query);

            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                GeolocaleTaxonLogDetail geolocaleTaxonLogDetail = new GeolocaleTaxonLogDetail();
                geolocaleTaxonLogDetail.setGeolocaleId(rset.getInt("geolocale_id"));
                String taxonName = rset.getString("taxon_name");
                geolocaleTaxonLogDetail.setTaxonName(taxonName);
                //geolocaleTaxonLogDetail.setTaxon(TaxonDb.getInfoInstance(getConnection(), taxonName));
                geolocaleTaxonLogDetail.setCreated(rset.getTimestamp("created"));
                geolocaleTaxonLogDetail.setSubfamilyCount(rset.getInt("subfamily_count"));
                geolocaleTaxonLogDetail.setGenusCount(rset.getInt("genus_count"));
                geolocaleTaxonLogDetail.setSpeciesCount(rset.getInt("species_count"));
                geolocaleTaxonLogDetail.setSpecimenCount(rset.getInt("specimen_count"));
                geolocaleTaxonLogDetail.setImageCount(rset.getInt("image_count"));
                geolocaleTaxonLogDetail.setLogId(rset.getInt("log_id"));
                geolocaleTaxonLogDetails.add(geolocaleTaxonLogDetail);
            }

            //A.log("getGeolocaleTaxonLogDetails() logId:" + logId + " count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("getGeolocaleTaxonLogDetails() logId:" + logId + " exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getGeolocaleTaxonLogDetails()");
        }

        //A.log("getInfoInstance() name:" + taxonName + " query:" + query);        
        return geolocaleTaxonLogDetails;
    }       


    public ArrayList<Login> getCuratorLogins(Login login) {
        // Login is administrator or curator conducting the search.

        ArrayList<Login> logins = new ArrayList<>();

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCuratorLogins()");

            query = " select curator_id" 
              + " from geolocale_taxon_log gtl, login l" 
              + " where gtl.curator_id = l.id";
              if (!login.isAdmin()) query += " and curator_id = " + login.getId();
            query += " order by l.name";  
                            
            //A.log("getCuratorLogins() query:" + query);

            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                int curatorId = rset.getInt("curator_id");
                LoginDb loginDb = new LoginDb(getConnection());
                logins.add(loginDb.getLogin(curatorId));
            }
            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getCuratorLogins() not found loginId:" + login.getId());
        } catch (SQLException e) {
            s_log.error("getCuratorLogins() curatorId:" + login.getId() + " exception:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCuratorLogins()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);        
        return logins;
    }        


    public GeolocaleTaxon getGeolocaleTaxon(String taxonName, int geolocaleId) {
        GeolocaleTaxon geolocaleTaxon = null;

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCuration()");

            query = " select gt.source " 
              + " from geolocale_taxon gt"
              + " and gt.geolocale_id = " + geolocaleId
              + " and gt.taxon_name = '" + taxonName + "'";
                            
            //A.log("getCuration() query:" + query);
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                geolocaleTaxon = new GeolocaleTaxon();
                geolocaleTaxon.setTaxonName(taxonName);
                geolocaleTaxon.setGeolocaleId(geolocaleId);
                geolocaleTaxon.setSource(rset.getString("source"));
                //curation.setCuratorId(rset.getInt("curator_id"));
                //curation.setCreated(rset.getTimestamp("created"));
            }
            if (count > 1) A.log("getCuration() count over 1?");
        } catch (SQLException e) {
            s_log.error("getCuration() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCuration()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return geolocaleTaxon;
    } 
    
   
    public Curation getCuration(String taxonName, int geolocaleId) {
        Curation curation = null;

        String query = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getCuration()");

            query = " select gtl.curator_id, gtl.created	 " 
              + " from geolocale_taxon_log gtl, geolocale_taxon_log_detail gtld" 
              + " where gtl.log_id = gtld.log_id"
              + " and gtld.geolocale_id = " + geolocaleId
              + " and gtld.taxon_name = '" + taxonName + "'"
              + " order by created asc"; // get the first instance
              ;

            //A.log("getCuration() query:" + query);
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                if (count > 1) break;
                curation = new Curation();
                curation.setTaxonName(taxonName);
                curation.setGeolocaleId(geolocaleId);
                curation.setCuratorId(rset.getInt("curator_id"));
                curation.setCreated(rset.getTimestamp("created"));
            }
            //if (count > 1) A.log("getCuration() count over 1?");
        } catch (SQLException e) {
            s_log.error("getCuration() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCuration()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return curation;
    } 
       
    // Used on the Curator page to show curations. But is this CORRECT?
    // Isn't the geolocale_taxon table the master? not right.
    // If allowed to show up on the curator-body.jsp page it will NPE if you go here:
    // http://localhost/antweb/curator.do?id=1 
    public ArrayList<Curation> getCurations(int curatorId) {
	  ArrayList<Curation> curations = new ArrayList<>();

	  String query = "";
	  ResultSet rset = null;
	  Statement stmt = null;
	  try {
		  stmt = DBUtil.getStatement(getConnection(), "getCurations()");
    
		  query = "select distinct gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl " 
			+ " where gtld.log_id = gtl.log_id and curator_id = " + curatorId 
			+ " and (gtld.taxon_name, gtld.geolocale_id) in " 
			+ "   (select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt where gt.source = 'curator')";

            //A.log("getCurations() query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                int geolocaleId = rset.getInt("geolocale_id");
                Curation curation = getCuration(taxonName, geolocaleId);
                //A.log("getCurations() curation:" + curation);
                curations.add(curation);
            }
        } catch (SQLException e) {
            s_log.error("getCurations() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCurations()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return curations;
    }



    public ArrayList<Curation> getCurations() {
	  ArrayList<Curation> curations = new ArrayList<>();

      String logIds = getFirstLogIdsPerGeolocale();
      
	  String query = "";
	  ResultSet rset = null;
	  Statement stmt = null;
	  try {
		  stmt = DBUtil.getStatement(getConnection(), "getCurations()");
    
		  query = "select taxon_name, geolocale_id from geolocale_taxon, geolocale " 
		    + " where geolocale_taxon.geolocale_id = geolocale.id "
		    + " and taxon_name like '% %' and geolocale_taxon.source = '" + Source.CURATOR + "'" 
		    + " and (geolocale_id, taxon_name) not in (" 
		    + "   select geolocale_id, taxon_name from geolocale_taxon_log_detail" 
		    + "   where log_id in (" + logIds + ")"
		    + ") order by name, taxon_name";

          //A.log("getCurations() query:" + query);
          
          rset = stmt.executeQuery(query);

          while (rset.next()) {
              String taxonName = rset.getString("taxon_name");
              int geolocaleId = rset.getInt("geolocale_id");
              Curation curation = getCuration(taxonName, geolocaleId);
              //A.log("getCurations() curation:" + curation);
              curations.add(curation);
          }
        } catch (SQLException e) {
            s_log.error("getCurations() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCurations()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return curations;
    } 


    public String getFirstLogIdsPerGeolocale() {
      String firstLogIdsPerGeolocale = "";
	  String query = "select min(log_id) minId from geolocale_taxon_log group by geolocale_id";
	  ResultSet rset = null;
	  Statement stmt = null;
	  try {
		  stmt = DBUtil.getStatement(getConnection(), "getFirstLogIdsPerGeolocale()");
          rset = stmt.executeQuery(query);
          int i = 0;
          while (rset.next()) {
              int minId = rset.getInt("minId");
              if (i > 0) firstLogIdsPerGeolocale += ", ";
              ++i;
              firstLogIdsPerGeolocale += minId;
          }
      } catch (SQLException e) {
          s_log.error("getFirstLogIdsPerGeolocale() e:" + e);
      } finally {
          DBUtil.close(stmt, rset, "this", "getFirstLogIdsPerGeolocale()");
      }
      return firstLogIdsPerGeolocale;
    }   

/*
// select * from geolocale_taxon where taxon_name like '% %' and source = "curator" and (geolocale_id, taxon_name) not in (select geolocale_id, taxon_name from geolocale_taxon_log_detail) order by geolocale_id, taxon_name;
// Do this but use the first log_id from each geolocale instead of all.

    public ArrayList<Curation> getCurationsByGeolocale(int geolocaleId) {

      ArrayList<Curation> Get the 1st record for each geolocale.
      Get the TaxonSet for the geolocale. 
      If a curator record exists in the taxonSet then insert it geolocale_taxon_assert
    }

    
    
		  query = "select distinct gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl " 
			+ " where gtld.log_id = gtl.log_id and geolocale_id = " + geolocaleId 
			+ " and (gtld.taxon_name, gtld.geolocale_id) in " 
			+ "   (select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt where gt.source = 'curator')";

            A.log("getCurationsByGeolocale() query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                Curation curation = getCuration(taxonName, geolocaleId);
                //A.log("getCurations() curation:" + curation);
                curations.add(curation);
            }
        } catch (SQLException e) {
            s_log.error("getCurationsByGeolocale() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCurationsByGeolocale()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return curations;
    }   
        
	  ArrayList<Curation> curations = new ArrayList<Curation>();

	  String query = "";
	  ResultSet rset = null;
	  Statement stmt = null;
	  try {
		  stmt = DBUtil.getStatement(getConnection(), "getCurationsByGeolocale()");
    
		  query = "select distinct gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl " 
			+ " where gtld.log_id = gtl.log_id and geolocale_id = " + geolocaleId 
			+ " and (gtld.taxon_name, gtld.geolocale_id) in " 
			+ "   (select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt where gt.source = 'curator')";

            A.log("getCurationsByGeolocale() query:" + query);
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                Curation curation = getCuration(taxonName, geolocaleId);
                //A.log("getCurations() curation:" + curation);
                curations.add(curation);
            }
        } catch (SQLException e) {
            s_log.error("getCurationsByGeolocale() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCurationsByGeolocale()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + query);
        return curations;
    }       
     */  
// ----------------- Dispute ----------------------------------------
     
    public void insertDispute(TaxonSet taxonSet) throws SQLException {
    
        GeolocaleTaxon geolocaleTaxon = (GeolocaleTaxon) taxonSet;
        if (geolocaleTaxon == null) {
          s_log.error("insertDispute() geolocaleTaxon is null");
          AntwebUtil.logStackTrace();
        }
    
        // Once we are getting a value here, manage to insert it... Also for project.
        //A.log("insertDispute() curatorId:" + taxonSet.getCuratorId());
    
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertDispute()");

            dml = "insert into geolocale_taxon_dispute (geolocale_id, taxon_name, source, rev, curator_id) " 
              + " values (" + geolocaleTaxon.getGeolocaleId() + ", '" + geolocaleTaxon.getTaxonName() + "', '" + geolocaleTaxon.getSource() + "', " + geolocaleTaxon.getRev() + ", " + geolocaleTaxon.getCuratorId() + " )";  
            stmt.execute(dml);

            //A.log("insertDispute() geolocaleTaxon:" + geolocaleTaxon);
        } catch (SQLException e) {
            s_log.error("insertDispute() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "insertDispute()");
        }
    }

    public void removeDispute(String speciesListName, String taxonName) throws SQLException {

        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);
        
        String dml = "";
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "removeDispute()");

            dml = "delete from geolocale_taxon_dispute "
              + " where geolocale_id = " + geolocaleId
              + "   and taxon_name = '" + taxonName + "'";

            stmt.execute(dml);

            //if (AntwebProps.isDevMode()) s_log.error("removeDispute() speciesListName:" + speciesListName + " taxonName:" + taxonName);
        } catch (SQLException e) {
            s_log.error("removeDispute() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "removeDispute()");
        }
    }


	private static ArrayList<TaxonSet> s_disputes = null;

    public TaxonSet getDispute(int geolocaleId, String taxonName) throws SQLException {

        if (s_disputes == null) {
          s_disputes = getDisputes();
          //s_log.warn("getDispute(" + geolocaleId + ", " + taxonName + ") fetched size:" + s_disputes.size() + " s_disputes:" + s_disputes);
        }
        for (TaxonSet taxonSet : s_disputes) {
          GeolocaleTaxon geolocaleTaxon = (GeolocaleTaxon) taxonSet;
          if (geolocaleTaxon.getGeolocaleId() == geolocaleId && geolocaleTaxon.getTaxonName().equals(taxonName)) {
            //s_log.warn("getDispute(" + geolocaleId + ", " + taxonName + ") found.");
            return taxonSet;
          }
        }                
        return null;                
    }
    
    public TaxonSet getDispute(String speciesListName, String taxonName) throws SQLException {

        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);

        String query = "";
        GeolocaleTaxon geolocaleTaxon = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {

            stmt = DBUtil.getStatement(getConnection(), "getDispute()");
            query = "select geolocale_id, taxon_name, source, rev, curator_id, created from geolocale_taxon_dispute " 
               + " where geolocale_id = " + geolocaleId
               + " and taxon_name = '" + taxonName + "'";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                geolocaleTaxon = new GeolocaleTaxon();
                geolocaleTaxon.setGeolocaleId(rset.getInt("geolocale_id"));
                geolocaleTaxon.setTaxonName((String) rset.getString("taxon_name"));
                geolocaleTaxon.setSource((String) rset.getString("source"));
                geolocaleTaxon.setRev(rset.getInt("rev"));
                geolocaleTaxon.setCuratorId(rset.getInt("curator_id"));
                geolocaleTaxon.setCreated(rset.getTimestamp("created"));
            }
        } catch (SQLException e) {
            s_log.error("getDispute(" + geolocaleId + ", " + taxonName + ") e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, "getDispute()");
        }
        //if (!exists) s_log.info("getDispute() false.  query:" + query);
        return geolocaleTaxon;
    }
    
    public ArrayList<TaxonSet> getDisputes() throws SQLException {
      return getDisputes(null, null);
    }
        
    public ArrayList<TaxonSet> getDisputes(String speciesListName, String taxonName) throws SQLException {

        int geolocaleId = GeolocaleMgr.getGeolocaleId(speciesListName);

        ArrayList<TaxonSet> disputes = new ArrayList<>();

        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        String geolocaleIdClause = ""; if (geolocaleId > 0) geolocaleIdClause = " and geolocale_id = " + geolocaleId;
        String taxonClause = ""; if (taxonName != null) taxonClause = " and taxon_name = '" + taxonName + "'";
        try {

            stmt = DBUtil.getStatement(getConnection(), "getDisputes()");
            query = "select geolocale_id, taxon_name, source, rev, curator_id, created  from geolocale_taxon_dispute "
               + " where 1 = 1 " 
               + geolocaleIdClause
               + taxonClause
            ;

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                GeolocaleTaxon geolocaleTaxon = new GeolocaleTaxon();
                geolocaleTaxon.setGeolocaleId(rset.getInt("geolocale_id"));
                geolocaleTaxon.setTaxonName((String) rset.getString("taxon_name"));
                geolocaleTaxon.setSource((String) rset.getString("source"));
                geolocaleTaxon.setRev(rset.getInt("rev"));
                geolocaleTaxon.setCuratorId(rset.getInt("curator_id"));
                geolocaleTaxon.setCreated(rset.getTimestamp("created"));                
                disputes.add(geolocaleTaxon);
            }
        } catch (SQLException e) {
            s_log.error("getDisputes(" + geolocaleId + ", " + taxonName + ") e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, "getDisputes()");
        }
        return disputes;
    }

}
package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

//import java.sql.SQLIntegrityConstraintViolationException;

public class SpeciesListUploadDb extends UploadDb {
    
    private static Log s_log = LogFactory.getLog(SpeciesListUploadDb.class);

    public static int s_taxonCountryPrimaryKeyViolations = 0;
    
    public SpeciesListUploadDb(Connection connection) {
      super(connection);
    }

/*
    public ArrayList xgetBioGeoRegions() {
        ArrayList result = new ArrayList();
        String query = "select distinct bioregion from country_bioregion";
        try {
            Statement stmt = getConnection().createStatement();
            stmt.execute(query);
            ResultSet rset = null;
            rset = stmt.getResultSet();
            while (rset.next()) {
                result.add(rset.getString(1));
            }
            stmt.close();
            rset.close()
        } catch (Exception e) {
            s_log.error("getBioGeoRegions() - error getting distinct bioregions e:" + e);
        }
        if (result.size() > 0) {
            return result;
        } else {
            return null;
        }
    }
*/
    public void updateProjectUploadDate(String projectName) {
        String query = "update project set last_changed=" + currentDateFunction + " where project_name='" + projectName + "'"  ;

        try {
            Statement stmt = getConnection().createStatement();
            stmt.executeUpdate(query);
            stmt.close();    
        } catch (SQLException e) {
            s_log.error("updateProjectUploadDate() projectName:" + projectName + " e:" + e);
        }
    }
    

/*
Mid Jan 2012 change...

Original:
select taxon_name from proj_taxon 
 where project_name = 'eurasianants' 
   and taxon_name in (select taxon_name from proj_taxon group by taxon_name having count(taxon_name) < 2)  
   and taxon_name not in (select distinct taxon_name from specimen)

New:
 select taxon_name from proj_taxon
 where project_name = 'eurasianants' 
   and taxon_name not in (select distinct taxon_name from specimen)
    group by taxon_name having count(taxon_name) < 2

Testing comparison:
select taxon_name from proj_taxon 
   where taxon_name in (select taxon_name from proj_taxon group by taxon_name having count(taxon_name) < 2)  
   and taxon_name not in (select distinct taxon_name from specimen)
   
equals:   
 select taxon_name from proj_taxon
  where taxon_name not in (select distinct taxon_name from specimen)
    group by taxon_name having count(taxon_name) < 2

*/
    
    public void prepareDatabase(String project) {
        if (!"worldants".equals(project)) {
          s_log.warn("prepareDatabase(). BIG PROBLEM! Only worldants should be uploaded via species list.");
          return;
        }
        String log = "";
        String query = "";
        int count = 0;
        try {
            Statement dmlstmt, stmt;
			query = "delete from taxon where antcat = 1"; 
				//" taxon_name in (select taxon_name from proj_taxon where project_name='" + Project.WORLDANTS + "')";
			dmlstmt = getConnection().createStatement();
			log += "taxa:" + dmlstmt.executeUpdate(query);
			dmlstmt.close();
			A.log("prepareDatabase() query:" + query);

			dmlstmt = getConnection().createStatement();
			query = "delete from homonym where antcat = 1";    // all records
			log += " homonym:" + dmlstmt.executeUpdate(query);
			dmlstmt.close();

			// We want to leave the description table alone, unless it is bolton, going foward.
			// drop everything from the description table with this project
			dmlstmt = getConnection().createStatement();
			query = "delete from description_edit where title = 'taxonomichistory'";
			log += " description_edit:" + dmlstmt.executeUpdate(query);
			dmlstmt.close();

			dmlstmt = getConnection().createStatement();
			query = "delete from description_homonym where title = 'taxonomichistory'";  // all records
			log += " description_homonym:" + dmlstmt.executeUpdate(query);
			dmlstmt.close();

            // then delete everything from the proj_taxon table with this project
            dmlstmt = getConnection().createStatement();
            query = "delete from proj_taxon where project_name = '" + project + "'";
            count = dmlstmt.executeUpdate(query);
            log += " proj_taxon:" + count;
            dmlstmt.close();
            LogMgr.appendLog("taxonSet.log", "SpeciesListUploadDb.prepareDatabase(" + project + ") proj_taxon:" + project + " del:" + count, true);

            // then delete everything from the geolocale_taxon table with this source
            dmlstmt = getConnection().createStatement();
            query = "delete from geolocale_taxon where source like '%" + Source.ANTCAT + "'";
            count = dmlstmt.executeUpdate(query);
            log += " geolocale_taxon:" + count;
            dmlstmt.close();
            LogMgr.appendLog("taxonSet.log", "SpeciesListUploadDb.prepareDatabase(" + project + ") geolocale_taxon source:antcat del:" + count, true);

            // then delete everything from the proj_taxon table with this source
            dmlstmt = getConnection().createStatement();
            query = "delete from proj_taxon where source like '%" + Source.ANTCAT + "' and project_name = 'fossilants'";
            count = dmlstmt.executeUpdate(query);
            log += " proj_taxon:" + count;
            dmlstmt.close();
            LogMgr.appendLog("taxonSet.log", "SpeciesListUploadDb.prepareDatabase(" + project + ") proj_taxon source:antcat project:fossilants del:" + count, true);
 
            A.log("prepareDatabase() log:" + log);
            
        } catch (Exception e) {
            s_log.error("prepareDatabase() problem for project:" + project + " e:" + e);
        }
    }

    public static int reportTaxonCountryPrimaryKeyViolations() {
      int returnVal = s_taxonCountryPrimaryKeyViolations;
      s_taxonCountryPrimaryKeyViolations = 0;
      return returnVal;
    }
}
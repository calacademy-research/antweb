package org.calacademy.antweb.home;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.*;

public class BioregionDb extends AntwebDb {
/* Data from this table is manually fetched in UploadAction to create the search select box. */

    private static Log s_log = LogFactory.getLog(BioregionDb.class);

    public BioregionDb(Connection connection) {
      super(connection);
    }

    public Bioregion getBioregion(String bioregionName) {
      return getBioregionWithClause(" name = '" + bioregionName + "'");
    }
    public Bioregion getBioregionWithClause(String keyClause) {
      Bioregion bioregion = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getBioregionWithClause()");

        String theQuery = "select *" 
          + " from bioregion " 
          + " where " + keyClause;
        rset = stmt.executeQuery(theQuery);

        while (rset.next()) {
          bioregion = new Bioregion();
          bioregion.setName(rset.getString("name"));
          bioregion.setTitle(rset.getString("title"));
          //bioregion.setDescription(rset.getString("description"));
              
           bioregion.setSubfamilyCount(rset.getInt("subfamily_count"));
           bioregion.setGenusCount(rset.getInt("genus_count"));
           bioregion.setSpeciesCount(rset.getInt("species_count"));
           bioregion.setSpecimenCount(rset.getInt("specimen_count"));
           bioregion.setImageCount(rset.getInt("image_count"));          
           bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
           bioregion.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
           bioregion.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
              
          
          bioregion.setChartColor(rset.getString("chart_color"));
          bioregion.setCreated(rset.getTimestamp("created"));
         
          // bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
          // subfamily_count, genus_count, species_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json
          // extent, locality, project_name?         
         
          Hashtable description = (new DescEditDb(getConnection())).getDescription(bioregion.getName());
          bioregion.setDescription(description);         
        }

        //A.log("getBioregionWithClause() query:" + theQuery);   
      } catch (SQLException e) {
        s_log.warn("getBioregionWithClause() e:" + e);            
      } finally {
        DBUtil.close(stmt, rset, "getBioregionWithClause()");        
      }      
      return bioregion;
    }


    public ArrayList<Bioregion> getBioregions() {
      return getBioregions(false);
    }

    public ArrayList<Bioregion> getBioregions(boolean deep) {
        ArrayList<Bioregion> bioregions = new ArrayList<Bioregion>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            ProjectDb projectDb = new ProjectDb(getConnection());
            
            String theQuery = "select * from bioregion order by title";

            stmt = DBUtil.getStatement(getConnection(), "BioregionDb.getBioregions()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                Bioregion bioregion = new Bioregion();
                bioregion.setName(rset.getString("name"));
                //bioregion.setDescription(rset.getString("description"));
                bioregion.setTitle(rset.getString("title"));
                bioregion.setLocality(rset.getString("locality"));
                bioregion.setExtent(rset.getString("extent"));
                //bioregion.setProjectName(rset.getString("project_name"));
                if (deep) {
                  ArrayList<Project> projects = projectDb.getProjects(bioregion.getProjectName());      
                  bioregion.setProjects(projects);
                  
                  bioregion.setCountries(getCountryNames(bioregion.getName()));
                }

		 	    bioregion.setSubfamilyCount(rset.getInt("subfamily_count"));
	 		    bioregion.setGenusCount(rset.getInt("genus_count"));
			    bioregion.setSpeciesCount(rset.getInt("species_count"));
			    bioregion.setSpecimenCount(rset.getInt("specimen_count"));
			    bioregion.setImageCount(rset.getInt("image_count"));          
			    bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
			    bioregion.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
			    bioregion.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
			    bioregion.setChartColor(rset.getString("chart_color"));             
                
                Hashtable description = (new DescEditDb(getConnection())).getDescription(bioregion.getName());
                bioregion.setDescription(description);     
                          
                bioregions.add(bioregion);
                //if ("Oceania".equals(bioregion.getName())) A.log("BioregionDb.getBioregions() bioregion:" + bioregion + " name:" + bioregion.getName() + " subfamily:" + bioregion.getSubfamilyCount() + " genus:" + bioregion.getGenusCount() + " species:" + bioregion.getSpeciesCount());
            }
        } catch (SQLException e) {
            s_log.warn("getBioregions() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getBioregions()");        
        }
        //A.log("getBioregions() bioregions:" + bioregions);
        return bioregions;
    }

    public ArrayList<String> getBioregionNames() {
        ArrayList<String> bioregionNames = new ArrayList<String>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select name, description from bioregion order by name";

            stmt = DBUtil.getStatement(getConnection(), "BioregionDb.getBioregionNames()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String name = rset.getString("name");
                //String description = rset.getString("description");
                bioregionNames.add(name);
            }
        } catch (SQLException e) {
            s_log.warn("getBioregionNames() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getBioregionNames()");        
        }
        return bioregionNames;
    }

    public ArrayList<String> getCountryNames(String bioregionName) {
        ArrayList<String> countryNames = new ArrayList<String>();
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String theQuery = "select name from geolocale" 
              + " where georank = 'country' and is_valid = 1 and is_live = 1"
              + " and bioregion = '" + bioregionName + "' order by name";

            stmt = DBUtil.getStatement(getConnection(), "BioregionDb.getCountryNames()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                String name = rset.getString("name");
                //String description = rset.getString("description");
                countryNames.add(name);
            }
        } catch (SQLException e) {
            s_log.warn("getCountryNames() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getCountryNames()");        
        }
        return countryNames;
    }

    
// ------------- Get From Specimen Data ----------------------
    
    public Bioregion getFromSpecimenData(String bioregionName) {

        Bioregion bioregion = new Bioregion();
        bioregion.setName(bioregionName);

        getSpecimenCount(bioregion);
        getImageCount(bioregion);
        
        A.log("getFromSpecimenData() bioregion:" + bioregion.getName() + " s:" + bioregion.getSpecimenCount() + " i:" + bioregion.getImageCount());
    
        return bioregion;
    }

    private void getSpecimenCount(Bioregion bioregion) {

      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getSpecimenCount()");
            
        String theQuery = "select count(distinct code) specimen_count" 
          + " from specimen "
          + " where specimen.bioregion = '" + bioregion.getName() + "'";

        //A.log("getSpecimenCount() query:" + theQuery);  
          
        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
          bioregion.setSpecimenCount(rset.getInt("specimen_count"));
        }

      } catch (SQLException e) {
        s_log.error("getSpecimenCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getSpecimenCount()");
      }
    }

    public void getImageCount(Bioregion bioregion) {

      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getImageCount()");
            
        String query = "select count(distinct id) image_count" 
          + " from specimen left join image on specimen.code = image.image_of_id"
          + " where specimen.bioregion = '" + bioregion.getName() + "'";
          
        //A.log("getImageCount() query:" + query);  
          
        rset = stmt.executeQuery(query);
        while (rset.next()) {
          
          bioregion.setImageCount(rset.getInt("image_count"));
        }

      } catch (SQLException e) {
        s_log.error("getImageCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "getImageCount()");
      } 
    }           
  
    private void populateFromSpecimenData(String bioregionName) {
      
      Bioregion bioregion = getFromSpecimenData(bioregionName);
      if (bioregion == null) {
        s_log.error("populateFromSpecimenData() Bioregion not found:" + bioregionName);
        return;
      }

      String dml = null;
      Statement stmt = null;      
      try {

          stmt = DBUtil.getStatement(getConnection(), "populateFromSpecimenData()");
          int x = 0;

          dml = "update bioregion " 
            + " set image_count = " + bioregion.getImageCount()
            + "  , specimen_count = " + bioregion.getSpecimenCount()
            + " where name = '" + bioregion.getName() + "'";

          //A.log("populateFromSpecimenData() dml:" + dml);
          x = stmt.executeUpdate(dml);
        
      } catch (SQLException e) {
          s_log.error("populateFromSpecimenData() e:" + e);
      } finally {
          DBUtil.close(stmt, null, "populateFromSpecimenData()");
      }      
    }
               
// ---------------------- Populate Bioregion_Taxon ---------------------------

    public void populate() throws SQLException {
      ArrayList<Bioregion> bioregionList = BioregionMgr.getBioregions();
      for (Bioregion bioregion : bioregionList) {
        populate(bioregion.getName());
      }
      updateBioregion();

	  LogMgr.appendLog("compute.log", "  Bioregions populated", true);                    
    }

    public void populate(String bioregionName) throws SQLException {
      Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
      
      if (bioregion == null) {
        s_log.warn("populate() bioregionName not found:" + bioregionName);
        return;
      }
          
      freshStart(bioregionName);      

      // Populate bioregion table with data from specimen table.
        populateFromSpecimenData(bioregionName);
      
      // Populate the bioregion_taxon table.
      populateSpecies(bioregionName);
      (new BioregionTaxonDb(getConnection())).populateHigherTaxa(bioregionName);
      
      // Crawl the Geolocale_taxon table to find the counts.
      (new BioregionTaxonCountDb(getConnection())).countCrawls(bioregionName);

      // update bioregion fields (title, image_count, subfamily_count, genus_count, species_count).                    
      finish(bioregion); 
    }

    private void freshStart(String bioregionName) {
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("bioregion", "subfamily_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "genus_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "species_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "specimen_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "image_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "imaged_specimen_count", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "taxon_subfamily_dist_json", null, "name = '" + bioregionName + "'");    
      utilDb.updateField("bioregion", "specimen_subfamily_dist_json", null, "name = '" + bioregionName + "'");    

      int count = utilDb.deleteFrom("bioregion_taxon", " where bioregion_name = '" + bioregionName + "' and source != 'antcat'");    
    }

    private String populateSpecies() {
      A.log("populateSpecies()");
    
      for (String bioregionName : BioregionMgr.getBioregionNames()) {
          populateSpecies(bioregionName);
      }          
      return "Bioregion_Taxon populated";
    }

    public void populateSpecies(String bioregionName) {

      Bioregion bioregion = null;
      
	  if (bioregionName == null || "null".equals(bioregionName)) {
		s_log.error("populateSpecies(" + bioregionName + ") bioregion name is 'null'.");
		return;
	  }
				 
	  bioregion = getBioregion(bioregionName);

	  if (bioregion == null) {
		s_log.error("populateSpecies(" + bioregionName + ") bioregion not found.");
		return;
	  }
  
	  (new UtilDb(getConnection())).deleteFrom("bioregion_taxon", " where bioregion_name = '" + bioregionName + "' and source != 'antcat'");

      BioregionTaxonDb bioregionTaxonDb = new BioregionTaxonDb(getConnection());
	  int fromSpecimenCount = bioregionTaxonDb.populateSpeciesFromSpecimen(bioregion);
	  int fromGeolocaleTaxonCount = bioregionTaxonDb.populateSpeciesFromGeolocaleTaxon(bioregion);
      //s_log.warn("populateSpecies(" + bioregionName + " fromSpecimenCount:" + fromSpecimenCount + " fromGeolocaleTaxonCount:" + fromGeolocaleTaxonCount);
    }

    public void updateBioregion() {
      updateColors();
    }
    
    private void updateColors() {    
      String[] colors = HttpUtil.getColors();
      ArrayList<Bioregion> bioregionList = BioregionMgr.getBioregions();    
      int i = 0;
      for (Bioregion bioregion : bioregionList) {
        updateColor(bioregion.getName(), colors[i]);
        ++i;      
      }
    }

    private void updateColor(String bioregionName, String color) {    
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("bioregion", "chart_color", "'" + color + "'", "name = '" + bioregionName + "'" );
    }

    private String finish() {    
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        finish(bioregion);
      }  
      return "Bioregion Finished";
    }

    private String finish(Bioregion bioregion) {    
      updateCountableTaxonData(bioregion);
      updateImagedSpecimenCount(bioregion);
      makeCharts(bioregion);
      return "Bioregion Finished:" + bioregion;
    }       
    

    private void updateCountableTaxonData(Bioregion bioregion) {        
        BioregionTaxonCountDb bioregionTaxonCountDb = new BioregionTaxonCountDb(getConnection());
        String criteria = "bioregion_name = '" + bioregion + "'";
        int subfamilyCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "subfamily");
        
        int genusCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "genus");

        //A.log("updateCountableTaxonData() bioregionName:" + bioregionName + " genusCount:" + genusCount);
        int speciesCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "species");

        criteria = "name = '" + bioregion + "'";
        bioregionTaxonCountDb.updateCountableTaxonCounts("bioregion", criteria, subfamilyCount, genusCount, speciesCount);                  
    }    

    private void updateImagedSpecimen() {
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        updateImagedSpecimenCount(bioregion);
      }  
    }

    private void updateImagedSpecimenCount(Bioregion bioregion) {
        int count = getImagedSpecimenCount(bioregion);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("bioregion", "imaged_specimen_count", (new Integer(count)).toString(), "name = '" + bioregion + "'");
    }

    private int getImagedSpecimenCount(Bioregion bioregion) {
      int imagedSpecimenCount = 0;
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = getConnection().createStatement();

        query = "select count(distinct(code)) count from specimen s, image i where s.code = i.image_of_id and bioregion = '" + bioregion + "'";

        rset = stmt.executeQuery(query);
        while (rset.next()) {
         imagedSpecimenCount = rset.getInt("count");
        }

        //A.log("getImagedSpecimenCount() query:" + query + " imagedSpecimenCount:" + imagedSpecimenCount);       
      } catch (SQLException e2) {
        s_log.error("getImagedSpecimenCount() e:" + e2);
      } finally {
          DBUtil.close(stmt, rset, "getImagedSpecimenCount()");
      }  
      return imagedSpecimenCount;
    }    
    
    // --- Charts ---
        
    public void makeCharts() {
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        makeCharts(bioregion);
      }  
    }     
            
    public void makeCharts(Bioregion bioregion) {
      //A.log("makeCharts(" + bioregion + ")");
      UtilDb utilDb = new UtilDb(getConnection());
      BioregionTaxonCountDb bioregionTaxonCountDb = new BioregionTaxonCountDb(getConnection());
      String criteria = "name = '" + bioregion + "'";
      String taxonCountQuery = getTaxonSubfamilyDistJsonQuery(criteria);
      String specimenCountQuery = getSpecimenSubfamilyDistJsonQuery(criteria);

      utilDb.updateField("bioregion", "taxon_subfamily_dist_json", "'" + bioregionTaxonCountDb.getTaxonSubfamilyDistJson(taxonCountQuery) + "'", criteria);
      utilDb.updateField("bioregion", "specimen_subfamily_dist_json", "'" + bioregionTaxonCountDb.getSpecimenSubfamilyDistJson(specimenCountQuery) + "'", criteria);
    }

// Add SpecimenDb.getFlagCriteria() ?
    public String getTaxonSubfamilyDistJsonQuery(String criteria) {
      String query = "select t.subfamily, count(*) count, t2.chart_color " 
          + " from bioregion_taxon bt, taxon t, taxon t2, bioregion b " 
          + " where bt.taxon_name = t.taxon_name " 
          + " and t.subfamily = t2.taxon_name "                
          + " and b.name = bt.bioregion_name "
          + " and b." + criteria
          + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and t.family = 'formicidae' " 
          + " group by t.subfamily"; 
      return query;
    }

    public String getSpecimenSubfamilyDistJsonQuery(String criteria) {
      String query = "select subfamily, count(*) count " 
          + " from bioregion_taxon bt, specimen s, bioregion b " 
          + " where bt.taxon_name = s.taxon_name " 
          + " and b.name = bt.bioregion_name "
          + " and b." + criteria
          + " and s.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') " 
          + " and s.family = 'formicidae' " 
          + " group by subfamily"; 
      return query;
    }     

           
}

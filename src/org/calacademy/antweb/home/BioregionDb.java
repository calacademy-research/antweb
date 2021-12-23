package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

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

    public Bioregion getBioregion(String bioregionName) throws SQLException {
      return getBioregionWithClause(" name = '" + bioregionName + "'");
    }
    public Bioregion getBioregionWithClause(String keyClause) throws SQLException {
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
            bioregion.setValidSpeciesCount(rset.getInt("valid_species_count"));
            bioregion.setSpecimenCount(rset.getInt("specimen_count"));
            bioregion.setImageCount(rset.getInt("image_count"));
            bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
            bioregion.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
            bioregion.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
          
            bioregion.setChartColor(rset.getString("chart_color"));
            bioregion.setCreated(rset.getTimestamp("created"));

            bioregion.setEndemicSpeciesCount(rset.getInt("endemic_species_count"));
            bioregion.setIntroducedSpeciesCount(rset.getInt("introduced_species_count"));

          // bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
          // subfamily_count, genus_count, species_count, taxon_subfamily_dist_json, specimen_subfamily_dist_json
          // extent, locality, project_name?         
         
            Hashtable description = (new DescEditDb(getConnection())).getDescription(bioregion.getName());
            bioregion.setDescription(description);
        }

        //A.log("getBioregionWithClause() query:" + theQuery);   
      } catch (SQLException e) {
        s_log.warn("getBioregionWithClause() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, rset, "getBioregionWithClause()");        
      }      
      return bioregion;
    }


    public ArrayList<Bioregion> getBioregions()throws SQLException  {
      return getBioregions(false);
    }

    public ArrayList<Bioregion> getBioregions(boolean deep) throws SQLException {
        ArrayList<Bioregion> bioregions = new ArrayList<>();
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
                bioregion.setValidSpeciesCount(rset.getInt("valid_species_count"));
			    bioregion.setSpecimenCount(rset.getInt("specimen_count"));
			    bioregion.setImageCount(rset.getInt("image_count"));          
			    bioregion.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
			    bioregion.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
			    bioregion.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
			    bioregion.setChartColor(rset.getString("chart_color"));

                bioregion.setEndemicSpeciesCount(rset.getInt("endemic_species_count"));
                bioregion.setIntroducedSpeciesCount(rset.getInt("introduced_species_count"));

                Hashtable description = (new DescEditDb(getConnection())).getDescription(bioregion.getName());
                bioregion.setDescription(description);     
                          
                bioregions.add(bioregion);
                //if ("Oceania".equals(bioregion.getName())) A.log("BioregionDb.getBioregions() bioregion:" + bioregion + " name:" + bioregion.getName() + " subfamily:" + bioregion.getSubfamilyCount() + " genus:" + bioregion.getGenusCount() + " species:" + bioregion.getSpeciesCount());
            }
        } catch (SQLException e) {
            s_log.warn("getBioregions() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getBioregions()");        
        }
        //A.log("getBioregions() bioregions:" + bioregions);
        return bioregions;
    }

    public ArrayList<String> getBioregionNames() throws SQLException {
        ArrayList<String> bioregionNames = new ArrayList<>();
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
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getBioregionNames()");        
        }
        return bioregionNames;
    }

    public ArrayList<String> getCountryNames(String bioregionName) throws SQLException {
        ArrayList<String> countryNames = new ArrayList<>();
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
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "BioregionDb.getCountryNames()");        
        }
        return countryNames;
    }

    
// ------------- Get From Specimen Data ----------------------
    
    public Bioregion getFromSpecimenData(String bioregionName) throws SQLException {

        Bioregion bioregion = new Bioregion();
        bioregion.setName(bioregionName);

        getSpecimenCount(bioregion);
        getImageCount(bioregion);
        
        s_log.debug("getFromSpecimenData() bioregion:" + bioregion.getName() + " s:" + bioregion.getSpecimenCount() + " i:" + bioregion.getImageCount());
    
        return bioregion;
    }

    private void getSpecimenCount(Bioregion bioregion) throws SQLException {

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
         throw e;
      } finally {
        DBUtil.close(stmt, rset, "getSpecimenCount()");
      }
    }

    public void getImageCount(Bioregion bioregion) throws SQLException {

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
        throw e;
      } finally {
        DBUtil.close(stmt, rset, "getImageCount()");
      } 
    }           
  
    private void populateFromSpecimenData(String bioregionName) throws SQLException {
      
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
          throw e;
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

        calcEndemic(); //bioregion
        calcIntroduced();       //bioregion

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
      (new BioregionTaxonCountDb(getConnection())).childrenCountCrawl(bioregionName);

      // update bioregion fields (title, image_count, subfamily_count, genus_count, species_count).                    
      finish(bioregion); 
    }

    private void freshStart(String bioregionName) throws SQLException {
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

    private String populateSpecies() throws SQLException {
      A.log("populateSpecies()");
    
      for (String bioregionName : BioregionMgr.getBioregionNames()) {
          populateSpecies(bioregionName);
      }          
      return "Bioregion_Taxon populated";
    }

    public void populateSpecies(String bioregionName) throws SQLException {

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

    public void updateBioregion() throws SQLException {
      updateColors();
    }
    
    private void updateColors() throws SQLException {
      String[] colors = HttpUtil.getColors();
      ArrayList<Bioregion> bioregionList = BioregionMgr.getBioregions();    
      int i = 0;
      for (Bioregion bioregion : bioregionList) {
        updateColor(bioregion.getName(), colors[i]);
        ++i;      
      }
    }

    private void updateColor(String bioregionName, String color) throws SQLException {
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("bioregion", "chart_color", "'" + color + "'", "name = '" + bioregionName + "'" );
    }

    public String finish() throws SQLException {
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        finish(bioregion);
      }  
      return "Bioregion Finished";
    }

    private String finish(Bioregion bioregion) throws SQLException {
      updateCountableTaxonData(bioregion);
      updateImagedSpecimenCount(bioregion);
      updateValidSpeciesCount(bioregion);
      makeCharts(bioregion);
      return "Bioregion Finished:" + bioregion;
    }       
    

    private void updateCountableTaxonData(Bioregion bioregion) throws SQLException {
        BioregionTaxonCountDb bioregionTaxonCountDb = new BioregionTaxonCountDb(getConnection());
        String criteria = "bioregion_name = '" + bioregion + "'";
        int subfamilyCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "subfamily");
        
        int genusCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "genus");

        //A.log("updateCountableTaxonData() bioregionName:" + bioregionName + " genusCount:" + genusCount);
        int speciesCount = bioregionTaxonCountDb.getCountableTaxonCount("bioregion_taxon", criteria, "species");

        criteria = "name = '" + bioregion + "'";
        bioregionTaxonCountDb.updateCountableTaxonCounts("bioregion", criteria, subfamilyCount, genusCount, speciesCount);                  
    }    

    private void updateImagedSpecimen() throws SQLException {
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        updateImagedSpecimenCount(bioregion);
      }  
    }

    private void updateImagedSpecimenCount(Bioregion bioregion) throws SQLException {
        int count = getImagedSpecimenCount(bioregion);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("bioregion", "imaged_specimen_count", (Integer.valueOf(count)).toString(), "name = '" + bioregion + "'");
    }

    private int getImagedSpecimenCount(Bioregion bioregion) throws SQLException {
      int imagedSpecimenCount = 0;
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getImagedSpecimenCount()");

        query = "select count(distinct(code)) count from specimen s, image i where s.code = i.image_of_id and bioregion = '" + bioregion + "'";

        rset = stmt.executeQuery(query);
        while (rset.next()) {
         imagedSpecimenCount = rset.getInt("count");
        }

        //A.log("getImagedSpecimenCount() query:" + query + " imagedSpecimenCount:" + imagedSpecimenCount);       
      } catch (SQLException e) {
        s_log.error("getImagedSpecimenCount() e:" + e);
        throw e;
      } finally {
          DBUtil.close(stmt, rset, "getImagedSpecimenCount()");
      }  
      return imagedSpecimenCount;
    }

    private void updateValidSpeciesCount(Bioregion bioregion) throws SQLException {
        int count = getValidSpeciesCount(bioregion);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("bioregion", "valid_species_count", (Integer.valueOf(count)).toString(), "name = '" + bioregion + "'");
    }

    private int getValidSpeciesCount(Bioregion bioregion) throws SQLException {
        int validSpeciesCount = 0;
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();

            query = "select count(*) count from taxon, bioregion_taxon bt where taxon.taxon_name = bt.taxon_name"
                + " and taxarank in ('species', 'subspecies') and status = 'valid' and fossil = 0"
                + " and bioregion_name = '" + bioregion + "'";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validSpeciesCount = rset.getInt("count");
            }

        } catch (SQLException e) {
            s_log.error("getValidSpeciesCount() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "getValidSpeciesCount()");
        }
        s_log.debug("getValidSpeciesCount() bioregion:" + bioregion + " count:" + validSpeciesCount);
        return validSpeciesCount;
    }

    // --- Charts ---
        
    public void makeCharts() throws SQLException {
      for (Bioregion bioregion : BioregionMgr.getBioregions()) {
        makeCharts(bioregion);
      }  
    }     
            
    public void makeCharts(Bioregion bioregion) throws SQLException {
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
    public String getTaxonSubfamilyDistJsonQuery(String criteria) throws SQLException {
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

    public String getSpecimenSubfamilyDistJsonQuery(String criteria) throws SQLException {
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

    // -----------------------------

    /* THIS CODE IS INCOMPLETE. CLOSE TO CORRECT BUT THE QUERY IS STRAIGNT COPIED FROM GeolocaleDb */

    public int calcIntroduced() throws SQLException {
        int sum = 0;
        ArrayList<Bioregion> bioregionList = BioregionMgr.getBioregions();
        for (Bioregion bioregion : bioregionList) {
            sum += calcIntroduced(bioregion);
        }
        return sum;
    }
    public int calcIntroduced(Bioregion bioregion) throws SQLException {
        // Set all to false prior to setting specifics to true below...
        updateBioregionTaxonField("introduced", bioregion.getName(), null);
        updateBioregionFieldCount("introduced", bioregion.getName(), 0);
        int totCount = 0;

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcIntroduced()");

            // Loop through all of the geolocale_taxa records of taxa on the introduced list.
            String query = "select bioregion_name, taxon_name"
                    + " from bioregion_taxon "
                    + " where taxon_name in "
                    + "     (select pt.taxon_name from proj_taxon pt, taxon t "
                    + "      where pt.taxon_name = t.taxon_name "
                    + " and (t.taxarank = 'species' or t.taxarank = 'subspecies') "
                    + "        and project_name = 'introducedants')"
                    + " and bioregion_taxon.bioregion_name = '" + bioregion.getName() + "'"
                    + " order by bioregion_name";

            // Break on geolocale to record the count.
            String lastBioregionName = null;
            int count = 0;

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                String bioregionName = rset.getString("bioregion_name");

                // If a break on geolocale, then update the count appropriately.
                if (lastBioregionName != null && !lastBioregionName.equals(bioregionName)) {
                    // A break has occured.
                    //A.log("calcIntroduceGeolocales() geolocaleId:" + geolocaleId + " lastGeolocaleId:" + lastGeolocaleId + " count:" + count);
                    updateBioregionFieldCount("introduced", lastBioregionName, count);
                    count = 0;
                }

                // If the taxon is introduced in this bioregion then flag it as such.
                boolean isIntroduced = TaxonPropMgr.isIntroduced(taxonName, bioregionName);

                if (isIntroduced) {
                    //if (2 == geolocaleId) A.log("calcIntroducedGeolocales() isIntroduced:" + isIntroduced + " taxonName:" + taxonName + " bioregion:" + bioregion);
                    ++totCount;
                    ++count;
                }

                //if (taxonName.contains("corde")) A.log("calcIntroducedGeolocales() isIntroduced:" + isIntroduced + " geolocaleId:" + geolocaleId + " taxonName:" + taxonName + " bioregion:" + bioregion);
                if (isIntroduced) {
                    updateBioregionTaxonField("introduced", bioregionName, taxonName);
                }

                lastBioregionName = bioregionName;
            }
            // After the loop is complete, update the last record that didn't "break on".
            if (count > 0) {
                updateBioregionFieldCount("introduced", lastBioregionName, count);
            }

            //A.log("calcIntroduced() count:" + count + " query:" + query);


        } catch (SQLException e) {
            s_log.error("calcIntroduced() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "calcIntroduced()");
        }
        return totCount;
    }

// ------------------------

    /*
    public int calcEndemic() {
        int sum = 0;
        ArrayList<Bioregion> bioregionList = BioregionMgr.getBioregions();
        for (Bioregion bioregion : bioregionList) {
            sum += calcEndemic(bioregion);
        }
        return sum;
    }
    */

    public int calcEndemic() throws SQLException { //Bioregion bioregion) {
        int c = 0;
        // Set all to false prior to setting specifics to true below...
        c += updateBioregionTaxonField("endemic", null, null); //bioregion.getName()
        c += updateBioregionFieldCount("endemic", null, 0); //bioregion.getName()

        String query = "select bt.taxon_name taxon_name, max(b.name) bioregionName, count(*) count"
                + " from bioregion b, bioregion_taxon bt, taxon where b.name = bt.bioregion_name"
                + " and taxon.taxon_name = bt.taxon_name"
               // + " and b.name = '" + bioregion.getName() + "'"
                + " and taxon.taxarank in ('species', 'subspecies')"
                + " and taxon.fossil = 0"
                + (new StatusSet()).getAndCriteria(Project.ALLANTWEBANTS)
                //+ " and  taxon.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')"
                + " and taxon.family = 'formicidae'"
                + " group by bt.taxon_name having count(*) = 1"
                + " order by bioregionName";

        return calcEndemism(query);
    }

    private int calcEndemism(String query) throws SQLException {
        int c = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "calcEndemism()");
            rset = stmt.executeQuery(query);

            String lastBioregionName = null;
            int count = 0;
            while (rset.next()) {
                ++count;
                String bioregionName = rset.getString("bioregionName");
                String taxonName = rset.getString("taxon_name");

                //if (GeolocaleMgr.getGeolocale(geolocaleId).getGeorank().equals("adm1")) A.log("calcEndemism() adm1:" + geolocaleId);

                c += updateBioregionTaxonField("endemic", bioregionName, taxonName);

                // If a break on bioregion, then update the count appropriately.
                if (lastBioregionName != null && !lastBioregionName.equals(bioregionName)) {
                    // A break has occured.
                    //A.log("calcEndemism() bioregionName:" + bioregionName + " lastBioregionName:" + lastBioregionName + " count:" + count);
                    c += updateBioregionFieldCount("endemic", lastBioregionName, count);
                    count = 0;
                }

                lastBioregionName = bioregionName;
            }
            // After the loop is complete, update the last record that didn't "break on".
            if (count > 0) {
                c += updateBioregionFieldCount("endemic", lastBioregionName, count);
            }

            s_log.debug("calcEndemism() count:" + count + " query:" + query);

        } catch (SQLException e) {
            s_log.error("calcEndemism() query:" + query + " e:" + e.toString());
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "calcEndemism()");
        }
        return c;
    }

    private int updateBioregionFieldCount(String field, String bioregionName, int count) throws SQLException {
        int c = 0;
        String updateDml = "update bioregion set " + field + "_species_count = ";
        if (bioregionName == null) {
            updateDml += 0;
        } else {
            updateDml += count + " where name = '" + bioregionName + "'";
        }

        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateBioregionFieldCount()");
            c = stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateBioregionFieldCount() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateBioregionFieldCount()");
        }
        return c;
    }

    // Will set to true (1)
    private int updateBioregionTaxonField(String field, String bioregionName, String taxonName) throws SQLException {
        int c = 0;
        String updateDml = "update bioregion_taxon set is_" + field + " = "; // will be is_endemic or is_introduced
        if (bioregionName == null) {
            updateDml += "false";
        } else {
            updateDml += "true where bioregion_name = '" + bioregionName + "' and taxon_name = '" + taxonName + "'";
        }

        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateBioregionTaxonField()");
            c = stmt.executeUpdate(updateDml);
        } catch (SQLException e) {
            s_log.error("updateBioregionTaxonField() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "updateBioregionTaxonField()");
        }
        return c;
    }

}

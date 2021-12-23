package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

//import org.apache.regexp.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class MuseumDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(MuseumDb.class);

    public MuseumDb(Connection connection) {
      super(connection);
    }

    public ArrayList<Museum> getMuseums()throws SQLException  {
      return getMuseums(false);
    }

    public ArrayList<Museum> getMuseums(boolean deepCopy) throws SQLException {

      ArrayList<Museum> museums = new ArrayList<>();
      String theQuery = "select * "
          + " from museum"
          + " order by code";
      Statement stmt = null;      
      ResultSet rset = null;
      try { 
        stmt = DBUtil.getStatement(getConnection(), "MuseumDb.getMuseums()");
        rset = stmt.executeQuery(theQuery);
        
        Museum museum = null;
        while (rset.next()) {
           museum = new Museum();
           museum.setCode(rset.getString("code"));
           museum.setName(rset.getString("name"));
           museum.setTitle(rset.getString("title"));
           museum.setIsActive(rset.getInt("active") == 1);
           museum.setSubfamilyCount(rset.getInt("subfamily_count"));
           museum.setGenusCount(rset.getInt("genus_count"));
           museum.setSpeciesCount(rset.getInt("species_count"));
           museum.setValidSpeciesCount(rset.getInt("valid_species_count"));
           museum.setSpecimenCount(rset.getInt("specimen_count"));
           museum.setImageCount(rset.getInt("image_count"));
           museum.setImagedSpecimenCount(rset.getInt("imaged_specimen_count"));
           museum.setTaxonSubfamilyDistJson(rset.getString("taxon_subfamily_dist_json"));
           museum.setSpecimenSubfamilyDistJson(rset.getString("specimen_subfamily_dist_json"));
           museum.setChartColor(rset.getString("chart_color"));
           museum.setCreated(rset.getTimestamp("created"));
           if (deepCopy) {
             Hashtable<String, String> description = (new DescEditDb(getConnection())).getDescription(museum.getCode());
             museum.setDescription(description);

             // museum.setTaxonSubfamilyDistJson(getSpecimenSubfamilyDistJson(museum.getCode()));
             // museum.setSpecimenSubfamilyDistJson(getSpecimenSubfamilyDistJson(museum.getCode()));
           }
           
           //A.log("getMuseums(" + deepCopy + ") code:" + museum.getCode());

           museums.add(museum);
        }

      } catch (SQLException e) {
          s_log.error("getMuseums() e:" + e);
          throw e;
      } finally {
          DBUtil.close(stmt, rset, "MuseumDb.getMuseums()");        
      }

      return museums;
    }

    public Museum getMuseum(String code) throws SQLException {
      return getMuseumWithClause(" code = '" + code + "'");
    }
    private Museum getMuseumWithClause(String keyClause) throws SQLException {
      Museum museum = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "getMuseumWithClause()");  
        String theQuery = "select *" 
          + " from museum " 
          + " where " + keyClause;
        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
           museum = new Museum();
           museum.setCode(rset.getString("code"));
           museum.setName(rset.getString("name"));
           museum.setTitle(rset.getString("title"));
           museum.setIsActive(rset.getBoolean("active"));
           museum.setValidSpeciesCount(rset.getInt("valid_species_count"));
           museum.setSpecimenCount(rset.getInt("specimen_count"));
           museum.setImageCount(rset.getInt("image_count"));
           museum.setChartColor(rset.getString("chart_color"));
           museum.setCreated(rset.getTimestamp("created"));
         
           Hashtable<String, String> description = (new DescEditDb(getConnection())).getDescription(museum.getCode());
           museum.setDescription(description);         
        }
      } finally {
          DBUtil.close(stmt, rset, "getMuseumWithClause()");
      }
      //A.log("getMuseum() museum:" + museum.toString());       
      
      return museum;
    }

    public String saveMuseum(Museum museum) throws SQLException {
    
        String message = "";
        if (museum.getCode() == null) {
          message = "Attempt to save museum with null code";
          s_log.info("saveMuseum() message:" + message);
          return message;
        }
        int activeInt = 1;
        if (!museum.getIsActive()) activeInt = 0;
            
        String dml = "insert into museum (code, name, title, active) " 
            + " values ('"+ museum.getCode() + "', '" + museum.getName() + "', '" + museum.getTitle() + "', " + activeInt + ")";

        Statement stmt = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "saveMuseum()");  
                                                                                                                         
          //A.log("updateMuseum() dml:" + dml);
          int c = stmt.executeUpdate(dml);
          message = c + " record inserted.";
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
          // no problem
          message = updateMuseum(museum);
        } catch (SQLException e) {
          s_log.error("saveMuseum() e:" + e + " dml:" + dml);
          throw e;
        } finally {
          DBUtil.close(stmt, null, this, "saveMuseum()");
        }
        return message;
    }

    private String updateMuseum(Museum museum) throws SQLException {
        String message = "";
        if (museum.getCode() != null) {
        
            int activeInt = 1;
              if (!museum.getIsActive()) activeInt = 0;
        
            //String title = museum.getTitle();
            //if (title == null) title = museum.getName();
            String dml = "update museum set " 
              + " name = '" + museum.getName() + "', "
              + " title = '" + museum.getTitle() + "', "
              + " active = " + activeInt
              + " where code='" + museum.getCode() + "'"; 
            Statement stmt = null;
            try {
              stmt = DBUtil.getStatement(getConnection(), "updateMuseum()");           
              s_log.info(dml);
              int c = stmt.executeUpdate(dml);
              s_log.debug("updateMuseum() dml:" + dml);
              message = c + " record updated.";
            } catch (SQLException e) {
              s_log.error("updateMuseum() e:" + e + " dml:" + dml);
              throw e;
            } finally {
              DBUtil.close(stmt, null, this, "updateMuseum()");
            }
        } else {
            s_log.error("Attempt to update code null");
        }
        return message;
    }    

    public String deleteByCode(String code) throws SQLException {
        String message = "";
        Statement stmt = null;
        String dml = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteByCode()");            
            dml = "delete from museum where code = '" + code + "'";
            int c = stmt.executeUpdate(dml);
            message = c + " record deleted.";
        } catch (SQLException e) {
            s_log.error("deleteByCode() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, null, this, "deleteByCode()");
        }
        return message;
    }    
    

// ------------- Get From Specimen Data ----------------------

    //select distinct access_group id, ownedby name from specimen right join image on specimen.code = image.image_of_id, groups  where specimen.access_group = groups.id order by id, name;
    public Museum XgetFromSpecimenData() {
      return getFromSpecimenData(null);
    }
    
    private Museum getFromSpecimenData(String museumCode) {

        Museum museum = new Museum();
        museum.setCode(museumCode);
        
        museum.setSpecimenCount(getSpecimenCount(museumCode));
        museum.setImageCount(getImageCount(museumCode));
              
      return museum;
    }


// Very different from the geolocale method. Verify!!!

    private int getSpecimenCount(String museumCode) {
      int count = 0;
      
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "MuseumDb.getSpecimenCount()");
            
        String theQuery = "select count(distinct code) specimen_count" 
          + " from specimen "
          + " where specimen.museum = '" + museumCode + "'";

        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
          count = rset.getInt("specimen_count");
        }

          s_log.debug("getMuseumSpecimenCount() count:" + count + " query:" + theQuery);

      } catch (SQLException e) {
        s_log.error("MuseumDb.getSpecimenCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "MuseumDb.getSpecimenCount()");
      }
      return count;
    }

    private int getImageCount(String museumCode) {
      int count = 0;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "MuseumDb.getImageCount()");
            
        String theQuery = "select count(distinct id) image_count" 
          + " from specimen left join image on specimen.code = image.image_of_id"
          + " where specimen.museum = '" + museumCode + "'";

        rset = stmt.executeQuery(theQuery);
        while (rset.next()) {
          count = rset.getInt("image_count");
        }

        s_log.debug("getMuseumImageCount() count:" + count + " query:" + theQuery);

      } catch (SQLException e) {
        s_log.error("MuseumDb.getImageCount() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "MuseumDb.getImageCount()");
      } 
      return count;
    }
    

    // ------ Population -----
    
    private final static int MIN_MUSEUM_COUNT_FOR_CALC = 0;
    
    public void populate(java.util.Map<String, Integer> museumMap) throws SQLException {
      // could run a query here and do the set of museums found.  Usually one, but not for CAS.
      // Better, faster by a minute, to collect the museums as we go through the upload.
      ArrayList<String> museumCodes = new ArrayList<>(museumMap.keySet());
      for (String code : museumCodes) {
        Integer count = museumMap.get(code);
        if (count > MIN_MUSEUM_COUNT_FOR_CALC) {
          //A.log("populate(Map) code:" + code + " count:" + count);
          populate(code);      
        }
      }
      if (museumMap.size() > 1) s_log.info("populate(Map) multiple museums:" + museumMap);
    }

    public void populate() throws SQLException {
      for (Museum museum : MuseumMgr.getMuseums()) {
        populate(museum.getCode());
      }
      LogMgr.appendLog("compute.log", "  Museums populated", true);   
    }

    private void freshStart(String code) throws SQLException {
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.deleteFrom("museum_taxon", " where code = '" + code + "'");    
    }

    public void populate(String code) throws SQLException {

      freshStart(code);
      // Set the specimen.museum field to be the museum code. Prepare.
      populateSpecimenMuseum(code);
      
      // Populate Museum table with data from specimen table.
      populateFromSpecimenData(code);

      // Populate the Museum_taxon table.
      populateSpecies(code);

      populateHigherTaxa(code);
      
      // Crawl the Museum_taxon table to find the counts.
      MuseumTaxonCountDb MuseumTaxonCountDb = new MuseumTaxonCountDb(getConnection());
      MuseumTaxonCountDb.childrenCountCrawl(code);

      // update museum fields (title, image_count, subfamily_count, genus_count, species_count).                    
      finish(code); 
     // A.log("populate() code:" + code + " after finish");
    }


    private void populateFromSpecimenData(String museumCode) throws SQLException {
      Museum museum = getFromSpecimenData(museumCode);
      if (museum == null) {
        s_log.error("populateFromSpecimenData() Museum not found:" + museumCode);
        return;
      }

      Statement stmt = null;      
      String dml = null;
      int c = 0;
      try {

          stmt = DBUtil.getStatement(getConnection(), "MuseumDb.populateFromSpecimenData()");      

          dml = "update museum " 
            + " set image_count = " + museum.getImageCount()
            + "  , specimen_count = " + museum.getSpecimenCount()
            + " where code = '" + museum.getCode() + "'";

          c = stmt.executeUpdate(dml);        
          
          s_log.debug("populateFromSpecimenData() c:" + c + " dml:" + dml);
      } catch (SQLException e) {
        s_log.error("MuseumDb.populateFromSpecimenData() 1 e:" + e);
        throw e;
      } finally {
          DBUtil.close(stmt, null, "MuseumDb.populateFromSpecimenData()");
      }      
    }
    

    // Lightweight.
    public void updateMuseum() throws SQLException {
      updateColors(); 
    }

    private void updateColors() throws SQLException {
      String[] colors = HttpUtil.getColors();
    
      int i = 0;
      for (Museum museum : MuseumMgr.getMuseums()) {
        updateColor(museum.getCode(), colors[i]);
        ++i;      
      }
    }   
    private void updateColor(String code, String color) throws SQLException {
      UtilDb utilDb = new UtilDb(getConnection());
      utilDb.updateField("museum", "chart_color", "'" + color + "'", "code = '" + code + "'");
    }

    public String finish() throws SQLException {
      for (Museum museum : MuseumMgr.getMuseums()) {
        finish(museum.getCode());
      }  
      return "Museum Finished";
    }
    private void finish(String code) throws SQLException {
      updateCountableTaxonData(code);      
      updateImagedSpecimenCount(code);
      updateValidSpeciesCount(code);
      makeCharts(code);
    }    
    
    private void updateCountableTaxonData() throws SQLException {
        for (Museum museum : MuseumMgr.getMuseums()) {
            updateCountableTaxonData(museum.getCode());
        }          
    }
    private void updateCountableTaxonData(String code) throws SQLException {
        MuseumTaxonCountDb museumTaxonCountDb = new MuseumTaxonCountDb(getConnection());

        String criteria = "code = '" + code + "'";
        int subfamilyCount = museumTaxonCountDb.getCountableTaxonCount("museum_taxon", criteria, "subfamily");
        
        int genusCount = museumTaxonCountDb.getCountableTaxonCount("museum_taxon", criteria, "genus");

        //A.log("updateMuseumTaxonData() code:" + code + " genusCount:" + genusCount);
        int speciesCount = museumTaxonCountDb.getCountableTaxonCount("museum_taxon", criteria, "species");

        museumTaxonCountDb.updateCountableTaxonCounts("museum", criteria, subfamilyCount, genusCount, speciesCount);                  
    }

    private void updateImagedSpecimen() throws SQLException {
      for (Museum museum : MuseumMgr.getMuseums()) {
        updateImagedSpecimenCount(museum.getCode());
      }  
    }

    private void updateImagedSpecimenCount(String code) throws SQLException {
        int count = getImagedSpecimenCount(code);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("museum", "imaged_specimen_count", (Integer.valueOf(count)).toString(), "code = '" + code + "'");
    }

    private int getImagedSpecimenCount(String code) {
      int imagedSpecimenCount = 0;
      String query = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "getImagedSpecimenCount()");  
          query = "select count(distinct(code)) count from specimen s, image i where s.code = i.image_of_id and museum = '" + code + "'";

          rset = stmt.executeQuery(query);
          while (rset.next()) {
           imagedSpecimenCount = rset.getInt("count");
          }

          //A.log("getMuseumImagedSpecimenCount() query:" + query + " taxonCount:" + imagedSpecimenCount);       
      } catch (SQLException e2) {
          s_log.error("getImagedSpecimenCount() 2 e:" + e2);
      } finally {
          DBUtil.close(stmt, rset, "getImagedSpecimenCount()");
      }
      return imagedSpecimenCount;
    }

    private void updateValidSpeciesCount(String code) throws SQLException {
        int count = getValidSpeciesCount(code);
        UtilDb utilDb = new UtilDb(getConnection());
        utilDb.updateField("museum", "valid_species_count", (Integer.valueOf(count)).toString(), "code = '" + code + "'");
    }

    private int getValidSpeciesCount(String code) {
        int validSpeciesCount = 0;
        String query = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getValidSpeciesCount()");

            query = "select count(*) count from taxon, museum_taxon mt where taxon.taxon_name = mt.taxon_name"
                    + " and taxarank in ('species', 'subspecies') and status = 'valid' and fossil = 0"
                    + " and code = '" + code + "'";

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                validSpeciesCount = rset.getInt("count");
            }

        } catch (SQLException e2) {
            s_log.error("getValidSpeciesCount() e:" + e2 + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "getValidSpeciesCount()");
        }
        s_log.debug("getValidSpeciesCount() code:" + code + " count:" + validSpeciesCount);
        return validSpeciesCount;
    }

    // Populate the museum field of the specimen table if can be determined from the ownedby field.
    private void populateSpecimenMuseum(String museumCode) throws SQLException {
      A.log("populateSpecimenMuseum(" + museumCode + ")");
      String whereClause = "museum = '" + museumCode + "'";
      
      (new UtilDb(getConnection())).updateField("specimen", "museum", null, whereClause);

      //A.log("populateSpecimenMuseum() emptied specimen.museum field whereClause:" + whereClause);
      Statement stmt = null;
      try {
    
          String dml = "update specimen set museum = '" + museumCode + "' where ownedBy like ('%" + museumCode + "%')";
            
          stmt = DBUtil.getStatement(getConnection(), "MuseumDb.populateSpecimenMuseum()");

          int c = stmt.executeUpdate(dml);

          if ("AFRC".equals(museumCode)) s_log.debug("populateSpecimenMuseum() updated:" + c + " dml:" + dml);
          
      } catch (SQLException e) {
        s_log.error("MuseumDb.populateSpecimenMuseum() e:" + e);
      } finally {
        DBUtil.close(stmt, null, "MuseumDb.populateSpecimenMuseum()");
      }    
    }

    private String populateSpecies() {

      //A.log("populateMuseumSpecies()");
    
      for (Museum museum : MuseumMgr.getMuseums()) {
          populateSpecies(museum.getCode());
      }          
      return "Museum_Taxon populated";
    }

    private void populateSpecies(String museumCode) {

      Museum museum = null;

      Statement stmt = null;
      ResultSet rset = null;
      try {    
      
          if (museumCode == null || "null".equals(museumCode)) {
            s_log.error("populateSpecies(" + museumCode + ") museum code is 'null'.");
            return;
          }
                     
          museum = getMuseum(museumCode);
  
          if (museum == null) {
            s_log.error("populateSpecies(" + museumCode + ") museum not found.");
            return;
          }
      
          (new UtilDb(getConnection())).deleteFrom("museum_taxon", " where code = '" + museumCode + "'");

          String query = "select taxon_name, count(*) specimenCount, count(id) imageCount " 
            + " from specimen s left join image i on s.code = i.image_of_id " 
            + " where museum = '" + museum.getCode() + "'" 
            + " and " + SpecimenDb.getFlagCriteria()            
            + " and s.status in " + StatusSet.getCountables()
            + " group by taxon_name";

          //A.log("populateSpecies() query:" + query);

          stmt = DBUtil.getStatement(getConnection(), "MuseumDb.populateSpecies()"); 
          rset = stmt.executeQuery(query);

          int taxaCount = 0;
          while (rset.next()) {
            ++taxaCount;
            String taxonName = rset.getString("taxon_name");
            int specimenCount = rset.getInt("specimenCount");
            int imageCount = rset.getInt("imageCount");
            
            insertSpecies(museum, taxonName, specimenCount, imageCount);
          }

          //A.log("populateMuseumSpecies() museum code:" + museum.getCode() + " name:" + museum.getName() + " taxaCount:" + taxaCount + " query:" + query);

      } catch (SQLException e) {
        s_log.error("MuseumDb.populateSpecies() e:" + e);
      } finally {
        DBUtil.close(stmt, rset, "MuseumDb.populateSpecies()");
      }
    }

    private void populateHigherTaxa()throws SQLException  {
        populateHigherTaxa(null);
    }
    
    private void populateHigherTaxa(String code) throws SQLException {
        String codeClause = "";
        if (code != null) codeClause = " and code = '" + code + "'";
        String query = "";

        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "populateHigherTaxa()");

            query = "select code, taxon.taxon_name" // , subfamily, genus, species, subspecies "
              + " from museum_taxon, taxon"
              + " where museum_taxon.taxon_name = taxon.taxon_name"
              + codeClause
              + " and taxon.status in " + StatusSet.getCountables()
              + " order by code, taxon.taxon_name"
              ;

            //A.log("populateHigherTaxa() query:" + query);
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                ++count;
                String rsetCode = rset.getString("code");
                String taxonName = rset.getString("taxon_name");

                //if (AntwebProps.isDevMode()) if (taxonName.contains("pseudomyrmecinae")) s_log.error("populateHigherMuseumTaxa() taxonName:" + taxonName);

                insertHigherTaxon(rsetCode, taxonName);
            }

        } catch (SQLException e) {
            s_log.error("populateHigherTaxa() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "populateHigherTaxa()");
        }
    }

    private int s_subfamilyCount = 0;
    private int s_generaCount = 0;
    private String s_lastSubfamily = "";
    private String s_lastGenus = "";
    private String s_lastCode = "";

    private void insertHigherTaxon(String code, String taxonName) throws SQLException {
        // On break of subfamily, create and insert a subfamily musuem record.
        String thisSubfamily = Taxon.getSubfamilyFromName(taxonName);
        String thisGenus = Taxon.getGenusFromName(taxonName);

        if (thisSubfamily == null || thisGenus == null) {
          //s_log.warn("insertHigherMusemTaxon() thisSubfamily null for code:" + code + " taxonName:" + taxonName + " subfamily:" + thisSubfamily + " lastSubfamily:" + s_lastSubfamily + " genus:" + thisGenus + " lastGenus:" + s_lastGenus);
          return;
        }

        if (!code.equals(s_lastCode)) {
          s_lastCode = code;
          //A.log("insertHigherTaxon() formicidae");
          boolean inserted = insertTaxon(code, "formicidae", "higherTaxonFamily");
        }

        if (!thisSubfamily.equals(s_lastSubfamily)) {
          s_lastSubfamily = thisSubfamily;
          boolean inserted = insertTaxon(code, thisSubfamily, "higherTaxonSubfamily");
          //A.log("insertHigherMusemTaxon() subfamily code:" + code + " taxonName:" + taxonName + " subfamily:" + thisSubfamily);
          if (inserted) ++s_subfamilyCount;
        }

        if (!thisGenus.equals(s_lastGenus)) {
          s_lastGenus = thisGenus;
          boolean inserted = insertTaxon(code, thisSubfamily + thisGenus, "higherTaxonGenus");
          // A.log("insertHigherMusemTaxon() genus code:" + code + " taxonName:" + thisSubfamily + thisGenus );
          if (inserted) ++s_generaCount;
        }    
    }

    private boolean insertTaxon(String code, String taxonName, String insertMethod) throws SQLException {
        // for each record, insert into proj_taxon.  Ignore constraint conflicts.    
        String dml = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "insertTaxon()");

            dml = "insert into museum_taxon(code, taxon_name, insert_method"
              + " ) values ('" + code + "', '" + taxonName + "','" + insertMethod + "')";
            //A.log("insertProjTaxon() dml:" + dml);
            stmt.executeUpdate(dml);
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) {
            s_log.error("insertTaxon() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "insertTaxon()");
        }     
        return true;
    }     
    
    private void insertSpecies(Museum museum, String taxonName, int specimenCount, int imageCount) throws SQLException {
      Statement stmt = null;
      try {
          
          String dml = "insert into museum_taxon (code, taxon_name, specimen_count, image_count, insert_method) values ('" 
            + museum.getCode() + "', '" + taxonName + "', " + specimenCount + ", " + imageCount + ",'insertMuseumSpecies')";
            
          stmt = DBUtil.getStatement(getConnection(), "MuseumDb.insertSpecies()");

          int x = stmt.executeUpdate(dml);
          
      } catch (SQLException e) {
        s_log.error("MuseumDb.insertSpecies() e:" + e);
        throw e;
      } finally {
        DBUtil.close(stmt, null, "MuseumDb.insertSpecies()");
      }
    }


    // --- Charts ---
        
        
    public void makeCharts() throws SQLException {
      for (Museum museum : MuseumMgr.getMuseums()) {
        makeCharts(museum.getCode());
      }  
    }     

    private void makeCharts(String code) throws SQLException {
      //A.log("makeCharts(" + code + ")");
      MuseumTaxonCountDb museumTaxonCountDb = new MuseumTaxonCountDb(getConnection());
      UtilDb utilDb = new UtilDb(getConnection());
      String criteria = "code = '" + code + "'";
      String taxonCountQuery = getTaxonSubfamilyDistJsonQuery(criteria);
      String specimenCountQuery = getSpecimenSubfamilyDistJsonQuery(criteria);
      utilDb.updateField("museum", "taxon_subfamily_dist_json", "'" + museumTaxonCountDb.getTaxonSubfamilyDistJson(taxonCountQuery) + "'", criteria);
      utilDb.updateField("museum", "specimen_subfamily_dist_json", "'" + museumTaxonCountDb.getSpecimenSubfamilyDistJson(specimenCountQuery) + "'", criteria);
    }    

    private String getTaxonSubfamilyDistJsonQuery(String criteria) {
        //A.log("getTaxonSubfamilyDistJsonQuery() query:" + query);
      return "select t.subfamily, count(*) count, t2.chart_color "
          + " from museum_taxon mt, taxon t, taxon t2, museum m "
          + " where mt.taxon_name = t.taxon_name "
          + " and t.subfamily = t2.taxon_name "
          + " and m.code = mt.code "
          + " and m." + criteria
          + " and t.taxarank in ('species', 'subspecies') "
          + " and t.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') "
          + " and t.family = 'formicidae' "
          + " group by t.subfamily";
    }

    private String getSpecimenSubfamilyDistJsonQuery(String criteria) {
        return "select subfamily, count(*) count "
            + " from museum_taxon mt, specimen s, museum m "
            + " where mt.taxon_name = s.taxon_name "
            + " and m.code = mt.code "
            + " and m." + criteria
            + " and s.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') "
            + " and s.family = 'formicidae' "
            + " group by subfamily";
    }
}

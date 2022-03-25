package org.calacademy.antweb.home;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.search.ResultItem;
import org.calacademy.antweb.upload.UploadUtil;
import org.calacademy.antweb.util.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


public class TaxonDb extends AntwebDb {
    
    private static final Log s_log = LogFactory.getLog(TaxonDb.class);
        
    public TaxonDb(Connection connection) {
      super(connection);
    }

    public static String DUMMY = "DUMMY";  // Minimal data. Can be constructed during code execution without database access.
    public static String INFO = "INFO";    // Default. Standard. Just member data. Supporting queries are extra.
    public static String FULL = "FULL";    // Includes: SeeAlso.

    public boolean exists(String taxonName) throws SQLException {
        return getTaxon(taxonName) != null;
    }

    public Subfamily getSubfamily(String subfamily) throws SQLException, AntwebException {
        String taxonName = getTaxonName(subfamily, null, null, null, "subfamily");
        return (Subfamily) getTaxon(taxonName);
    }
    public Genus getGenus(String subfamily, String genus) throws SQLException, AntwebException {
        String taxonName = getTaxonName(subfamily, genus, null, null, "genus");
        return (Genus) getTaxon(taxonName);
    }
    public Species getSpecies(String subfamily, String genus, String species) throws SQLException, AntwebException {
        String taxonName = getTaxonName(subfamily, genus, species, null, "species");
        return (Species) getTaxon(taxonName);
    }
    public Subspecies getSubspecies(String subfamily, String genus, String species, String subspecies) throws SQLException, AntwebException {
        String taxonName = getTaxonName(subfamily, genus, species, subspecies, "subspecies");
        return (Subspecies) getTaxon(taxonName);
    }

    public Taxon getTaxon(String taxonName) throws SQLException { //, tring tableName, String taxonNameClause
        Taxon taxon = null;

        //if ("amblyoponinaestigmatomma pallipes".equals(taxonName)) {
        //    A.log("getTaxon() taxonName:" + taxonName);
        //    AntwebUtil.logShortStackTrace();
        //}

        String tableName = "taxon";
        String taxonNameClause = " taxon_name = '" + taxonName + "'";
        //ProfileCounter.add("getTaxon() " + AntwebUtil.getShortStackTrace(9));

        if (taxonName == null) {
            return null;
        }
        
        //taxonName = DBUtil.escapeQuotes(taxonName);
        if (taxonName.contains("'")) {
          s_log.warn("getTaxon() singleQuote found in taxonName:" + taxonName);
          return null;
        }

        String theQuery = "";

        boolean log = true && "myrmicinaetetramorium vernicosum".equals(taxonName);
        ResultSet rset = null;
        PreparedStatement stmt = null;
        try {

            theQuery = "select taxon_name, taxarank, kingdom_name, phylum_name, order_name, class_name"
              + ", family, subfamily, tribe, genus, subgenus, species, subspecies "
              + ", status, access_group, source, line_num, insert_method, created, fossil, type, antcat, antcat_id"
              + ", pending , author_date, author_date_html, authors, year, available "
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", parent_taxon_name, image_count, hol_id, chart_color"
              // country, bioregion,  // These not used. Could be in multiple. See bioregion_taxon and taxon_country. And bioregion_map.
              + " from taxon where taxon_name = ?";

            stmt = DBUtil.getPreparedStatement(getConnection(), "getTaxon() taxonName:" + taxonName, theQuery);

            stmt.setString(1, taxonName);

            //if (log) A.log("getTaxon() query:" + theQuery);
            rset = stmt.executeQuery();

            int count = 0;
            while (rset.next()) {
                if (log) s_log.debug("getTaxon() IN query:" + theQuery);

                ++count; // Only one record expected

                // taxon_name is not actually used. It is derived from the taxonomic info.

                String rank = rset.getString("taxarank");
                taxon = Taxon.getTaxonOfRank(rank);

                /* Deprecated.
                if ("taxon".equals(tableName)) {
                  taxon = Taxon.getTaxonOfRank(rank);
                } else {
                  taxon = new Homonym();
                  taxon.setRank(rank);
                }
                 */

                taxon.setRank(rank);

                //taxon.setTaxonName(rset.getString("taxon_name")); // taxonName is derived from data below.
                taxon.setKingdomName(rset.getString("kingdom_name"));
                taxon.setPhylumName(rset.getString("phylum_name"));
                taxon.setOrderName(rset.getString("order_name"));
                taxon.setClassName(rset.getString("class_name"));
                taxon.setFamily(rset.getString("family"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setTribe(rset.getString("tribe"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSubgenus(rset.getString("subgenus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));

                taxon.setStatus(rset.getString("status"));
                taxon.setGroupId(rset.getInt("access_group"));
                taxon.setSource(rset.getString("source"));
                taxon.setLineNum(rset.getInt("line_num"));
                taxon.setInsertMethod(rset.getString("insert_method"));
                taxon.setCreated(rset.getTimestamp("created"));
                taxon.setIsFossil(rset.getInt("fossil") == 1);
                taxon.setIsType(rset.getInt("type") == 1);
                taxon.setIsAntCat(rset.getInt("antcat") == 1);
                taxon.setAntcatId(rset.getInt("antcat_id"));
                taxon.setIsPending(rset.getInt("pending") == 1);
                taxon.setAuthorDate(rset.getString("author_date"));
                taxon.setAuthorDateHtml(rset.getString("author_date_html"));
                taxon.setAuthors(rset.getString("authors"));
                taxon.setYear(rset.getString("year"));
                taxon.setIsAvailable(rset.getInt("available") == 1);
                String currentValidName = rset.getString("current_valid_name");
                if (currentValidName != null && !taxonName.equalsIgnoreCase(currentValidName)) {
                    taxon.setCurrentValidName(currentValidName);
                }
                taxon.setCurrentValidRank(rset.getString("current_valid_rank"));
                taxon.setCurrentValidParent(rset.getString("current_valid_parent"));
                taxon.setIsOriginalCombination(rset.getInt("original_combination") == 1);
                taxon.setWasOriginalCombination(rset.getString("was_original_combination"));
                taxon.setParentTaxonName(rset.getString("parent_taxon_name"));
                taxon.setImageCount(rset.getInt("image_count"));
                taxon.setHolId(rset.getInt("hol_id"));
                taxon.setChartColor(rset.getString("chart_color"));

                taxon.setIsExtant(true);
                //country, bioregion?
                // old_speciesGroup
            }

            //if (count == 0) A.log("getTaxon() not found taxonName:" + taxonName);

            if (count > 1 && "taxon".equals(tableName)) s_log.error("getTaxon() count:" + count + " should never be more than 1.  TaxonName:" + taxonName);

        } catch (SQLException e) {
            s_log.error("getTaxon() taxonName:" + taxonName + " e:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxon() taxonName:" + taxonName);
        }

        // This makes the populateMgrs() process take 44 seconds instead of 17...
        // Failure to execute this code block will prevent default specimens from displaying.
        // BioregionMap and IntroducedMap are required for...

        if (AntwebProps.isDevMode()) { // Don't run in dev because it is slow.
            A.logi("getTaxon", "getTaxon() DEFAULT SPECIMEN functionality disabled in dev to speed up launch time.");
        } else {  // Do run it in production.
            if (taxon != null) {
                if (Rank.SUBFAMILY.equals(taxon.getRank()) || taxon.isSpeciesOrSubspecies()) {
                    // if species we use "=" if subfamily we use "like". Genera are fetched with an overview specific child speciesStr.
                    ImagePickDb imagePickDb = new ImagePickDb(getConnection());
                    taxon.setDefaultSpecimen(Caste.MALE, imagePickDb.getDefaultSpecimen(Caste.MALE, taxon));
                    taxon.setDefaultSpecimen(Caste.WORKER, imagePickDb.getDefaultSpecimen(Caste.WORKER, taxon));
                    taxon.setDefaultSpecimen(Caste.QUEEN, imagePickDb.getDefaultSpecimen(Caste.QUEEN, taxon));
                    //A.log("getTaxon() taxonName:" + taxonName + " class:" + this.getClass() + " workerDefault:" + taxon.getDefaultSpecimen(Caste.WORKER));
                }
                TaxonPropDb taxonPropDb = new TaxonPropDb(getConnection());
                if (Rank.GENUS.equals(taxon.getRank())) {
                    taxon.setBioregionMap(taxonPropDb.getBioregionMap(taxonName));
                }
                if (taxon.isSpeciesOrSubspecies()) {
                    taxon.setIntroducedMap(taxonPropDb.getIntroducedMap(taxonName));
                }
            } else {
                A.logi("TaxonDbNullTaxon", 2, "getTaxon() taxon is null:" + taxonName + " " + AntwebUtil.getShortStackTrace());
            }
        }


        if (taxon == null && log) {
          //It may not be found during Worldants, for instance amblyoponinae, but after the cleanup process it will...
          String warning = " taxon not found taxonName:" + taxonName;
          //if (AntwebProps.isDevMode()) warning += " theQuery:" + theQuery;
          s_log.debug("getTaxon() " + warning + " query:" + theQuery + " taxonFromMgr:" + TaxonMgr.getTaxon(taxonName) + " antcatCount:" + getAntcatCount());
          return null;
        }
        
        if (AntwebProps.isDevMode() && "myrmicinaestrumigenys emmae".equals("taxonName")) {
            s_log.warn("getTaxon() name:" + taxonName + " taxon:" + taxon.getTaxonName() + " query:" + theQuery);
        }
        return taxon;
    }


    public Taxon getFullTaxon(String subfamily, String genus, String species, String subspecies, String rank) throws SQLException, AntwebException {
        return getFullTaxon("Formicidae", subfamily, genus, species, subspecies, rank);
    }

    public Taxon getFullTaxon(String family, String subfamily, String genus, String species, String subspecies, String rank) throws SQLException, AntwebException {
        Taxon taxon = null;

        String taxonName = getTaxonName(family, subfamily, genus, species, subspecies, rank);

        if (taxonName == null) {
            s_log.debug("getFullTaxon() taxon not found for:" + family + " " + subfamily + " " + genus + " " + species + " " + subspecies + " " + rank);
            return null;
        }

        taxon = getFullTaxon(taxonName);
        return taxon;
    }

    // Will contain all of the data items including countries and bioregions. Expensive.
    public Taxon getFullTaxon(String taxonName) throws SQLException {
        if (false && "amblyoponinaestigmatomma pallipes".equals(taxonName)) {
            A.log("getFullTaxon() taxonName:" + taxonName);
            AntwebUtil.logShortStackTrace();
        }

        Taxon taxon = getTaxon(taxonName);
        if (taxon == null) return null;

        taxon.setSeeAlso();
        //taxon.setBioregionMap();

        taxon.setDescription(new DescEditDb(getConnection()).getDescEdits(taxon, false)); // false is isManualEntry
        taxon.setHabitats(getConnection());
        taxon.setMicrohabitats(getConnection());
        taxon.setMethods(getConnection());
        taxon.setTypes(getConnection());

        taxon.setElevations(getConnection());
        taxon.setCollectDateRange(getConnection());

        taxon.setCountries(new GeolocaleTaxonDb(getConnection()).getCountries(taxon.getTaxonName()));
        taxon.setBioregions(new BioregionTaxonDb(getConnection()).getBioregions(taxon.getTaxonName()));

        taxon.setHomonymAuthorDates(getConnection());

        return taxon;
    }

    private String getTaxonName(String subfamily, String genus, String species, String subspecies, String rank) throws SQLException, AntwebException {
        return getTaxonName(null, subfamily, genus, species, subspecies, rank);
    }
    private String getTaxonName(String family, String subfamily, String genus, String species, String subspecies, String rank) throws SQLException, AntwebException {
        String taxonName = null;

        String familyClause = "";
        if (family != null) familyClause = " and family = '" + AntFormatter.escapeQuotes(family) + "'";

        String subfamilyClause = "";
        if (subfamily != null) subfamilyClause = " and subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "'";

        String genusClause = "";
        if (genus != null) genusClause = " and genus = '" + AntFormatter.escapeQuotes(genus) + "'";

        String speciesClause = "";
        if (species != null) speciesClause = " and species = '" + AntFormatter.escapeQuotes(species) + "'";

        String subspeciesClause = "";
        if (subspecies != null) subspeciesClause = " and subspecies = '" + AntFormatter.escapeQuotes(subspecies) + "'";

        String theQuery = "select taxon_name from taxon"
                + " where 1 = 1"
                + familyClause + subfamilyClause + genusClause + speciesClause + subspeciesClause
                + " and taxarank = '" + rank + "'";

        //A.log("getTaxonName() theQuery:" + theQuery);

        int i = 0;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt =  DBUtil.getStatement(getConnection(), "getTaxonName()");
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                i = i + 1;
                taxonName = rset.getString("taxon_name");
            }
            if (i > 1) {
                String message = "getTaxonName() did not get unique result." + family + " " + subfamily + " " + genus + " " + species + " " + subspecies + " " + rank
                        + " query:" + theQuery;
                //  AntwebUtil.logStackTrace();
                throw new AntwebException(message);
            }
        } catch (SQLException e) {
            s_log.error("getTaxonName() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "getTaxonName()");
        }

        return taxonName;
    }


    public ArrayList<Taxon> getTaxa() {
        ArrayList<Taxon> taxa = new ArrayList<>();
        String taxonName;

        PreparedStatement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name from taxon";
        //A.log("getTaxa() query:" + query);
        try {            
            stmt = DBUtil.getPreparedStatement(getConnection(), "getTaxa()", query);
            rset = stmt.executeQuery();

            while (rset.next()) {
              taxonName = rset.getString("taxon_name");
              Taxon taxon = getTaxon(taxonName);
              if (taxon == null) {
                s_log.debug("getTaxa() taxon not found:" + taxonName);
                continue;
              }
              taxa.add(taxon);
            }
        } catch (SQLException e) {
            s_log.error("getTaxa() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxa()");
        }
        
        //A.log("getTaxa() taxonName:" + taxonName + " query:" + query);
        
        return taxa;
    }

    // Info with bioregionMap for genus and introducedMap for species.
    public ArrayList<Taxon> getTaxa(String rank) {
      return getTaxa(rank, null);
    }
    public ArrayList<Taxon> getTaxa(String rank, String status) {
      String rankClause = "";
      if (rank.contains("taxarank in")) {
        rankClause = rank; // it is already a clause, so use it.
      } else {
        rankClause = "taxarank = '" + rank + "'";
      }
      String statusClause = "1 = 1";
      if (status != null) statusClause = "status = '" + status + "'";
      return getTaxaWithClause(rankClause,  statusClause);
    }
    private ArrayList<Taxon> getTaxaWithClause(String rankClause, String statusClause) {
        ArrayList<Taxon> taxa = new ArrayList<>();
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name, taxarank from taxon where " + rankClause + " and " + statusClause;
        //A.log("getTaxaWithClause() rankClause:" + rankClause + " statusClause:" + statusClause + " query:" + query);
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getTaxaWithClause()");
            rset = stmt.executeQuery(query);

            //A.log("getTaxaWithClause() query:" + query);

            int count = 0;
            while (rset.next()) {
              taxonName = rset.getString("taxon_name");
              Taxon taxon = getTaxonForMgr(taxonName);
              if (taxon == null) {
                s_log.debug("getTaxaWithClause() taxon not found:" + taxonName);
                continue;
              }
              taxa.add(taxon);
            }
        } catch (SQLException e) {
            s_log.error("getTaxaWithClause() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxaWithClause()");
        }
        
        if ("subfamily".equals(rankClause)) s_log.debug("getTaxon() taxonName:" + taxonName + " query:" + query + " taxa:" + taxa);

        return taxa;
    }

    // Taxa for the TaxonMgr need a couple of extra things.
    public Taxon getTaxonForMgr(String taxonName) throws SQLException {
        Taxon taxon = getTaxon(taxonName);
        if (Rank.GENUS.equals(taxon.getRank())) {
            taxon.setBioregionMap(new TaxonPropDb(getConnection()).getBioregionMap(taxonName));
        }
/* Not gotten in the first placee, so not gotten when refetched.
        if (Rank.SPECIES.equals(taxon.getRank())) {
            //A.log("getTaxon() introduced:" + getIntroducedMap(taxonName));
            taxon.setIntroducedMap(new TaxonPropDb(getConnection()).getIntroducedMap(taxonName));
        }
*/
        return taxon;
    }

    // Info level.
    public ArrayList<Taxon> getTaxa(ArrayList<ResultItem> speciesList, String subfamilyFilter) throws SQLException {
    /* To make fast, get a list of genera for a given subfamily.  Restrict prior to fetch */
    
       ArrayList<Taxon> taxa = new ArrayList<>();
       
       for (ResultItem item : speciesList) {
          String taxonName = item.getTaxonName();
          //A.log("getTaxa() taxonName:" + taxonName + " item:" + item); 
         
          Taxon taxon = getTaxon(item.getTaxonName());
          //A.log("TaxonDb.getTaxa() taxonName:" + taxonName + " taxon:" + taxon + " subfamilyFilter:" + subfamilyFilter);

          if (taxon != null) {
            if ("none".equals(subfamilyFilter) || subfamilyFilter == null || subfamilyFilter.equals(taxon.getSubfamily())) {
              taxa.add(taxon);
            }
          }
       } 
       return taxa;
    }


    private int getAntcatCount() {
        String countStr = null;
        ResultSet rset = null;
        Statement stmt = null;
        String theQuery = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getAntcatCount()");

            theQuery = " select count(*) count from taxon where antcat = 1";
            rset = stmt.executeQuery(theQuery);

            while (rset.next()) {
                countStr = rset.getString("count");
            }
        } catch (SQLException e) {
            s_log.error("getAntcatCount)");
        } finally {
            DBUtil.close(stmt, rset, "this", "getAntcatCount()");
        }
        if (countStr == null) return 0;
        return Integer.parseInt(countStr);
    }

    public int getWorldantsCount() {
        PreparedStatement stmt = null;
        ResultSet rset = null;
        String theQuery = "select count(*) count from taxon where source like '%worldants%'";
        try {            
            stmt = DBUtil.getPreparedStatement(getConnection(), "getWorldantsCount()", theQuery);
            rset = stmt.executeQuery();

            while (rset.next()) {
                return rset.getInt("count");
            }
        } catch (SQLException e) {
            s_log.error("getWorldantsCount() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getWorldantsCount()");
        }
        return 0;
    }

    public String getTaxonNameFromAntcatId(String tableName, int antcatId) {
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from " + tableName + " where antcat_id = " + antcatId;
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getTaxonNameFromAntcatId() antcatId:" + antcatId);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                taxonName = rset.getString("taxon_name");
            }
        } catch (SQLException e) {
            s_log.error("getTaxonNameFromAntcatId() antcatId:" + antcatId + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxonNameFromAntcatId() antcatId:" + antcatId);
        }
        
        //if (AntwebProps.isDevMode()) s_log.info("getTaxonNameFromAntcatId() name:" + taxonName + " query:" + theQuery);        
        return taxonName;
    }
        
    
    public void deleteTaxon(String taxonName) throws SQLException {   
      // Called from ProjTaxonDb
      
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "deleteTaxon()");

        // Delete the taxon
        String delete = "delete from taxon " 
          + " where taxon_name = '" + taxonName + "'"; 
        s_log.debug("TaxonDb.deleteTaxon() delete:" + delete);
        stmt.executeUpdate(delete);
 
        // Delete the allantwebants projTaxon
        delete = "delete from proj_taxon " 
          + " where taxon_name = '" + taxonName + "'";
        stmt.executeUpdate(delete);
        s_log.debug("TaxonDb.deleteTaxon() delete:" + delete);
      } finally {
        DBUtil.close(stmt, "deleteTaxon()");
      }
    }
    
    // Do Not update specimen records!?

    public String prepareMoveTaxon(String moveTaxonName, String toTaxonName) 
      throws SQLException {    
 
       String message = "Are you sure that you want to change the name of:<br><br><b>" + moveTaxonName + "</b> (found in ";
       Statement stmt = null;
       ResultSet rset = null;
       try {
         stmt = DBUtil.getStatement(getConnection(), "prepareMoveTaxon()");
 
         int i = 0;        
         String projectsQuery = "select project_name from proj_taxon where taxon_name = '" + moveTaxonName + "'"; 
         rset = stmt.executeQuery(projectsQuery);
         while (rset.next()) {
           ++i;
           if (i > 1) message += ", ";
           String projectName = rset.getString("project_name");
           message += projectName;          
         }
         if (i == 0) message += "no lists";
         message += ")";
                
         message += "<br><br> to <br><br><b>" + toTaxonName + "</b> (found in ";
         i = 0;        
         projectsQuery = "select project_name from proj_taxon where taxon_name = '" + toTaxonName + "'"; 
         rset = stmt.executeQuery(projectsQuery);
         while (rset.next()) {
           ++i;
           if (i > 1) message += ", ";
           String projectName = rset.getString("project_name");
           message += projectName;          
         }
         if (i == 0) message += "no lists";
         message += ")";

         s_log.warn("prepareMoveTaxon() moveProjects:" + message);
                        
       } finally {
         DBUtil.close(stmt, rset, "prepareMoveTaxon()");
       }
       return message;
    }
     
    public String renameTaxon(String fromTaxonName, String toTaxonName) 
      throws SQLException {    
      
      String message = null;
      Statement stmt = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "renameTaxon()");
        
        String updateTaxon = "update taxon " 
          + " set taxon_name = '" + toTaxonName + "'" ;
        String subfamily = Taxon.getSubfamilyFromName(toTaxonName);
        if (subfamily != null) updateTaxon += " , subfamily = '" + subfamily + "'"; 
        String genus = Taxon.getGenusFromName(toTaxonName);
        if (genus != null) updateTaxon += " , genus = '" + genus + "'";
        String species = Taxon.getSpeciesFromName(toTaxonName);
        if (species != null) updateTaxon += " , species = '" + species + "'"; 
        String subspecies = Taxon.getSubspeciesFromName(toTaxonName);
        if (subspecies != null) updateTaxon += " , subspecies = '" + subspecies + "'";
        updateTaxon += " where taxon_name = '" + fromTaxonName + "'"; 
        s_log.debug("moveTaxon() update:" + updateTaxon);
        int taxonUpdateCount = stmt.executeUpdate(updateTaxon);

        String setStatement = " set taxon_name = '" + toTaxonName + "' where taxon_name = '" + fromTaxonName + "'";
 
        String updateProjTaxon = "update proj_taxon " + setStatement;
        int projTaxonUpdateCount = stmt.executeUpdate(updateProjTaxon);
        
        stmt.executeUpdate("update description_edit " + setStatement);        
        stmt.executeUpdate("update description_hist " + setStatement);        
        stmt.executeUpdate("update specimen " + setStatement);             
        stmt.executeUpdate("update taxon_prop " + setStatement);        

        //stmt.executeUpdate("update synonymy " + setStatement);
        //stmt.executeUpdate("update tapir_specimen " + setStatement);

        message = "Taxon <b>" + fromTaxonName + "</b> renamed <b>" + toTaxonName + "</b>.  " + taxonUpdateCount + " taxon updated and " + projTaxonUpdateCount + " projects updated.";
        
      } finally {
         DBUtil.close(stmt, "renameTaxon()");
      }

      //s_log.warn("moveTaxon() projTaxonUpdateCount:" + projTaxonUpdateCount);
      return message;
    }

    public String combineTaxa(String fromTaxonName, String toTaxonName) 
      throws SQLException {    
        
      String message = null;
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "combineTaxa()");

        // Delete the old taxon record
// ! But couldn't it be the legitamate taxon record create for a specimen?  Should we check
// here if specimens exist for this taxon?        
        String deleteTaxon = "delete from taxon " 
          + " where taxon_name = '" + fromTaxonName + "'"; 
        s_log.warn("combineTaxon() update:" + deleteTaxon);
        int taxonDeleteCount = stmt.executeUpdate(deleteTaxon);

        // Delete the fromTaxon proj_taxon records which are already extant for the toTaxon
        int projTaxonDeleteCount = 0;
        String dupsQuery = "select project_name from proj_taxon where taxon_name = '" + toTaxonName + "'"; 
        rset = stmt.executeQuery(dupsQuery);
        while (rset.next()) {
          String projectName = rset.getString("project_name");
          String deleteProjTaxon = "delete from proj_taxon "
            + " where taxon_name = '" + fromTaxonName + "'"
            + " and project_name = '" + projectName + "'";
            
          Statement stmt2 = getConnection().createStatement();
          projTaxonDeleteCount = stmt2.executeUpdate(deleteProjTaxon);
          stmt2.close();
          s_log.warn("combineTaxon() deleted:" + projTaxonDeleteCount + " deleteProjTaxon:" + deleteProjTaxon);
        }
       
        String setStatement = " set taxon_name = '" + toTaxonName + "' where taxon_name = '" + fromTaxonName + "'";
        
        String updateProjTaxon = "update proj_taxon " + setStatement;
        //s_log.warn("combineTaxon() updateProjTaxon:" + updateProjTaxon);
        int projTaxonUpdateCount = stmt.executeUpdate(updateProjTaxon);

        stmt.executeUpdate("update description_edit " + setStatement);        
        stmt.executeUpdate("update description_hist " + setStatement);        
        stmt.executeUpdate("update specimen " + setStatement);       
        stmt.executeUpdate("update taxon_prop " + setStatement);     
        
         message = "Taxa combined.  " + projTaxonDeleteCount + " project mappings removed.  " + projTaxonUpdateCount + " project mappings updated.";
         s_log.warn("combineTaxon() projTaxonUpdateCount:" + projTaxonUpdateCount 
           + " projTaxonDeleteCount:" + projTaxonDeleteCount);
        
       } finally {
         DBUtil.close(stmt, "combineTaxa()");
       }

       return message;
    }

    // Flag taxon records where exists a specimen with type
    public String crawlForType()
      throws SQLException {
      int count = 0;
      String message = null;
      Statement stmt = null;
      try {

        new UtilDb(getConnection()).updateField("taxon", "type", "null"); //, "status = 'morphotaxon'");
      
        stmt = DBUtil.getStatement(getConnection(), "crawlForType()");
        s_log.info("crawlForType()");
        
        String dml = "update taxon " 
          + " set type = 1 where taxarank = 'species' and status != 'morphotaxon' and taxon_name in "
          + " (select taxon_name from specimen where type_status != \"\")";

        count = stmt.executeUpdate(dml);

        s_log.debug("crawlForType() count:" + count + " dml:" + dml);

      } finally {
        DBUtil.close(stmt, "crawlForType()");
      }

	  LogMgr.appendLog("compute.log", "  Types Crawl completed", true);                    
      return count + " records updated";
    }          

    public static int s_currentValidFetchCount = 0;
    private static String s_lastCurrentValidTaxonName;
    public static String getCurrentValidTaxonName(Connection connection, String currentValidName) {
        if (currentValidName == null) return null;
        if (currentValidName.equals(s_lastCurrentValidTaxonName)) return s_lastCurrentValidTaxonName;
        String taxonName = null;
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from taxon where taxon_name = '" + currentValidName.toLowerCase() + "'";
        int count = 0;
        try {            
            ++count;
            stmt = DBUtil.getStatement(connection, "getCurrentValidTaxonName() currentValidName:" + currentValidName);
            rset = stmt.executeQuery(theQuery);
            while (rset.next()) {
                taxonName = rset.getString("taxon_name");
                s_lastCurrentValidTaxonName = taxonName;
                s_currentValidFetchCount = s_currentValidFetchCount + 1;
                //A.log("getCurrentValidTaxonName() taxonName:" + taxonName);
            }
        } catch (SQLException e) {
            s_log.error("getCurrentValidTaxonName() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getCurrentValidTaxonName() currentValidName:" + currentValidName);
        }

        //if (AntwebProps.isDevMode()) s_log.info("getCurrentValidTaxonName() name:" + taxonName + " query:" + theQuery);        
        if (count == 0) s_log.error("getCurrentValidTaxonName() Investigate.  CurrentValidName:" + currentValidName + " not found");
        if (count > 1) s_log.error("getCurrentValidTaxonName() Investigate.  should not have multiple valid entries for currentValidName:" + currentValidName);
        return taxonName;
    }

    public boolean isExistingSubfamilyForAGenus(String family, String subfamily, String genus) 
      throws SQLException {

         String query = "select distinct subfamily from taxon " 
            + " where taxarank = 'genus' and "; // Added Jun 26, 2014";
        if (family != null && !"null".equals(family)) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        return isSubfamilyForGenus(query, subfamily);        
    }

    public boolean isValidSubfamilyForGenus(String family, String subfamily, String genus) 
    // As opposed to UploadDB.isExistingSubfamilyForAGenus(), used in Specimen upload, we do not specify rank so they could come from species (or specimen upload)
    // *** verify good.
      throws SQLException {
         String query = "select distinct subfamily from taxon " 
            + " where ";
        if (family != null && !"null".equals(family)) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        boolean isValid = isSubfamilyForGenus(query, subfamily);
        
        if (!isValid) s_log.debug("isValidSubfamilyForGenus() isValid:" + isValid + " subfamily:" + subfamily + " query:" + query);
        return isValid;
    }

// --------------- AutoComplete -----------------
    // To support autoComplete search box.
    public List<String> getTaxonNames() {
      return getTaxonNames("");
    }
    public List<String> getTaxonNames(String text) {
      return getTaxonNames(text, false);
    }
    public List<String> getTaxonNames(String text, boolean asHtml) {
        List<String> taxonNames = new ArrayList<>();
        String taxonName = null;

        PreparedStatement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from taxon where taxon_name like ? and status = 'valid'";
        try {            
            stmt = DBUtil.getPreparedStatement(getConnection(), "getTaxonNames()", theQuery);
            stmt.setString(1, "%"+text+"%");
            rset = stmt.executeQuery();

            int count = 0;
            while (rset.next()) {
                taxonName = rset.getString("taxon_name");
                if (asHtml) taxonName = "<br><a href='" + AntwebProps.getDomainApp() + "/description.do?taxonName=" + taxonName + "'>" + taxonName + "</a>";
                taxonNames.add(taxonName);
            }
        } catch (SQLException e) {
            s_log.error("getTaxonNames() exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxonNames()");
        }
        
        //if (AntwebProps.isDevMode()) s_log.info("getTaxonNameFromAntcatId() name:" + taxonName + " query:" + theQuery);        
        return taxonNames;    
    }

// --------------- End AutoComplete -----------------
    

    // Single use method.
    public void fixHtmlAuthorDates() {

      UtilDb utilDb = new UtilDb(getConnection());

      PreparedStatement stmt = null;
      ResultSet rset = null;
      String query = "select taxon_name, author_date from taxon where author_date like '%>%'";
	  try {            
            stmt = DBUtil.getPreparedStatement(getConnection(), "fixHtmlAuthorDates()", query);
            rset = stmt.executeQuery();
            int count = 0;
            while (rset.next()) {
                ++count;
                String taxonName = rset.getString("taxon_name");
                String authorDate = rset.getString("author_date");
                String newAuthorDate = UploadUtil.cleanHtml(authorDate);
                
                utilDb.updateField("taxon", "author_date", "'" + newAuthorDate + "'", " taxon_name = '" + taxonName + "'");
                utilDb.updateField("homonym", "author_date", "'" + newAuthorDate + "'", " taxon_name = '" + taxonName + "'");
            }
            s_log.debug("fixHtmlAuthorDates() count:" + count);
	  } catch (SQLException e) {
		  s_log.error("fixHtmlAuthorDates() exception:" + e + " query:" + query);
	  } finally {
		  DBUtil.close(stmt, rset, "this", "fixHtmlAuthorDates()");
	  }    
    }       
    
    // Called in Specimen post process.
    public void removeIndetWithoutSpecimen() throws SQLException {
      new UtilDb(getConnection()).deleteFrom("taxon", "where source like 'specimen%' and status in ('indetermined', 'unrecognized') and taxon_name not in (select taxon_name from specimen)");
    }

    public static String deleteSpeciesWithoutSpecimenOrAntcatSource(Connection connection) throws SQLException {
      String dmlWhereClause = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and taxon_name not in (select taxon_name from specimen) "
        + " and taxarank in ('species', 'subspecies')";
      int retVal = new UtilDb(connection).deleteFrom("taxon", dmlWhereClause);
      return retVal + " deleteSpeciesWithoutSpecimenOrAntcatSource. ";
    }
    public static String deleteGeneraWithoutSpecimenOrAntcatSource(Connection connection) throws SQLException {
      String dmlWhereClause = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and (subfamily, genus) not in (select subfamily, genus from specimen) "
        + " and taxarank in ('genus')";
      int retVal = new UtilDb(connection).deleteFrom("taxon", dmlWhereClause);
      return retVal + " deletedGeneraWithoutSpecimenOrAntcatSource. ";
    }


    public HashMap<String, ArrayList<String>> getSubgenusHashMap() throws SQLException {
        HashMap<String, ArrayList<String>> subgenusHashMap = new HashMap<>();
        PreparedStatement stmt = null;
        ResultSet rset = null;
        String query = "select distinct genus, subgenus from taxon where subgenus is not null and status != 'morphotaxon'";
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getSubgenusHashMap()", query);
            rset = stmt.executeQuery();

            while (rset.next()) {
                String genusName = rset.getString(1);
                ArrayList<String> subgenusList = null;
                if (subgenusHashMap.containsKey(genusName)) {
                    subgenusList = subgenusHashMap.get(genusName);
                } else {
                    subgenusList = new ArrayList<>();
                }
                String subgenusName = rset.getString(2);
                subgenusList.add(subgenusName);
                Collections.sort(subgenusList);
                subgenusHashMap.put(genusName, subgenusList);
            }
        } catch (SQLException e) {
            s_log.error("getSubgenusHashMap() e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "getSubgenusHashMap()");
        }
        return subgenusHashMap;
    }


    public void setSubfamilyChartColor() throws SQLException {
        UtilDb utilDb = new UtilDb(getConnection());
        String[] colors = HttpUtil.getColors();
        ArrayList<Taxon> taxa = getTaxa("subfamily");
        int i = 0;
        for (Taxon taxon : taxa) {
            String whereClause = "taxon_name = '" + taxon.getTaxonName() + "'";
            //A.log("setSubfamilyChartColor() whereClause:" + whereClause);
            utilDb.updateField("taxon", "chart_color", "'" + colors[i] + "'", whereClause);
            ++i;
        }
    }


    /*
    We want to be rid of taxa generated from specimen uploads for which the specimen and taxa no longer exist. Below
    we find the specimen upload date and delete taxa from before that. But it is a big list. Why is it not the same
    (about 42) as this:
      select taxon_name, taxarank, status, access_group from taxon where taxon_name not in (select taxon_name from specimen) 
        and taxon.status in ('morphotaxon', 'indetermined')
        and taxarank in ('species', 'subspecies');  // No records returned with this clause.

    public String deleteOldSpecimenUploadTaxa() {
        // Loop through each access Group...
        String report = "";
        UtilDb utilDb = new UtilDb(getConnection());
        ArrayList<Group> groups = GroupMgr.getGroups();
        for (Group group : groups) {

            String created = utilDb.getDateValue("select distinct date(created) theDate from specimen where access_group = " + group.getId());

            // for each distinct created...
            if (created != null) {
                String query = "select concat(access_group, concat(', ', concat(concat(taxon_name, ','), created))) from taxon "
                    + " where created < '" + created + "' and access_group = " + group.getId() + " and " + StatusSet.getAllAntwebClause();
                String result = utilDb.runQuery(query);
                A.log("deleteOldSpecimeUploadTaxa() result:" + result);
                //String dml = "delete from taxon where created < '" + created + "' and access_group = " + group.getId() + " and " + StatusSet.getAllAntwebClause();
                //int count = utilDb.runDml(dml);
                //A.log("deleteOldSpecimenUploadTaxa() count:" + count + " dml:" + dml);
                //report += group.getId() + ":" + count + ", ";
            }
            if (report.length() > 2) report = report.substring(0, report.length() - 2);
        }
        return report;
    }
     */
    
/*
    All of the taxa with subgenus fields are from specimen data. If I understand correctly, 
      those taxa records should not exist because they were created from old specimen uploads... 
      I will see if I can find all taxa records that should not exist and systematically remove them. 
      List of taxa with subgenus data (100 records) included below...

// This shows the taxa generated from specimen data that have subgenera. Don't want to take subgenera from specimen records.
See: select taxon.taxon_name, taxon.status, taxon.access_group, source, taxon.created, code, specimen.subgenus, specimen.created from taxon, specimen where taxon.taxon_name = specimen.taxon_name and taxon.subgenus is not null;

1) Are all these good to go?
  select taxon_name, source, created from taxon where taxon_name not in (select taxon_name from proj_taxon where project_name = "worldants") and taxon_name not in (select taxon_name from specimen) and rank in ('species', 'subspecies');   

2) Even these?
  select taxon_name, source, created from taxon where taxon_name not in (select taxon_name from proj_taxon where project_name = "worldants") and taxon_name not in (select taxon_name from specimen) and taxon_name like '%(%';


Delete this:
select taxon_name, source, created from taxon where taxon_name not in (select taxon_name from proj_taxon where project_name = "worldants") and genus not in (select genus from specimen) and taxon_name not like '%(%' and rank in ('genus') order by source, created;   
*/
    
    
}
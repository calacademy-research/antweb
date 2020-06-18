package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.search.ResultItem;


public class TaxonDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(TaxonDb.class);
        
    public TaxonDb(Connection connection) {
      super(connection);
    }
    
    public static Taxon getTaxonFromShortTaxonName(Connection connection, String shortTaxonName) {
      // If we don't know the subfamily, no worries.  We don't use this because it is slow for batches.

      String taxonNameClause = " taxon_name like '%" + shortTaxonName + "'";
      Taxon taxon = getInfoInstance(connection, "taxon", shortTaxonName, taxonNameClause);

      return taxon;
    }
    
    public static Taxon getInfoInstance(Connection connection, String taxonName) {
      return TaxonDb.getInfoInstance(connection, "taxon", taxonName);
    }
        
    public static Taxon getInfoInstance(Connection connection, String tableName, String taxonName) {
        String taxonNameClause = " taxon_name = '" + taxonName + "'";
        return getInfoInstance(connection, tableName, taxonName, taxonNameClause);
    }
    
    public static Taxon getInfoInstance(Connection connection, String tableName, String taxonName, String taxonNameClause) {
    
        /* New.  Mar 2012.  Mark */
        /* Used by OrphanTaxons and OrphanDescEdits, DescriptionAction, etc...   Useful, but because the 
           return object is not created as the appropriate subclass, of limited utility.
           Also see: DummyTaxon.getInstance()
           */
        Taxon taxon = null;
        if (taxonName == null) return null;
        
        //taxonName = DBUtil.escapeQuotes(taxonName);
        if (taxonName.contains("'")) {
          s_log.warn("getInfoInstance() singleQuote found in taxonName:" + taxonName);
          return null;
        }

        String theQuery = "";

        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(connection, "getCurrentValidTaxonNamestance() taxonName:" + taxonName);
                  
            theQuery = " select taxarank, taxon_name, kingdom_name, phylum_name, order_name, class_name"
              + ", family, subfamily, genus, subgenus, species, subspecies "
              + ", source, insert_method, created, fossil, antcat, pending, type "
              + ", antcat_id, author_date, author_date_html, authors, year, status, available " 
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", country, bioregion, chart_color, parent_taxon_name" //, bioregion_map "              
              + " from " + tableName + " where " + taxonNameClause;

            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {

                //A.log("getInfoInstance() IN query:" + theQuery);

                ++count;
                // Only one record expected
                String rank = rset.getString("taxarank");
                if ("taxon".equals(tableName)) {
                  taxon = Taxon.getTaxonOfRank(rank);
                } else {
                  taxon = new Homonym();
                  taxon.setRank(rank);
                }
                //taxon.setTaxonName(rset.getString("taxon_name"));
                // Taxon.getTaxonName() is weird.  It is constructed out of the comprising fields.  Can be bad.
                taxon.setKingdomName(rset.getString("kingdom_name"));
                taxon.setPhylumName(rset.getString("phylum_name"));
                taxon.setOrderName(rset.getString("order_name"));
                taxon.setClassName(rset.getString("class_name"));
                taxon.setFamily(rset.getString("family"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSubgenus(rset.getString("subgenus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));                                

                taxon.setSource(rset.getString("source"));
                taxon.setInsertMethod(rset.getString("insert_method"));
                taxon.setCreated(rset.getTimestamp("created"));
                int fossil = rset.getInt("fossil");
                taxon.setIsFossil((fossil == 1) ? true : false);
                taxon.setIsType((rset.getInt("type") == 1) ? true : false);
                taxon.setIsAntCat((rset.getInt("antcat") == 1) ? true : false);
                taxon.setIsPending((rset.getInt("pending") == 1) ? true : false);
                taxon.setAntcatId(rset.getInt("antcat_id"));
                taxon.setAuthorDate(rset.getString("author_date"));
                taxon.setAuthorDateHtml(rset.getString("author_date_html"));
                taxon.setAuthors(rset.getString("authors"));
                taxon.setYear(rset.getString("year"));
                taxon.setStatus(rset.getString("status"));
                taxon.setIsAvailable((rset.getInt("available") == 1) ? true : false);
                taxon.setCurrentValidName(rset.getString("current_valid_name"));
                taxon.setCurrentValidRank(rset.getString("current_valid_rank"));
                taxon.setCurrentValidParent(rset.getString("current_valid_parent"));
                taxon.setIsOriginalCombination((rset.getInt("original_combination") == 1) ? true : false);
                //taxon.setWasOriginalCombination((rset.getInt("was_original_combination") == 1) ? true : false);
                taxon.setWasOriginalCombination(rset.getString("was_original_combination"));  
                taxon.setChartColor(rset.getString("chart_color"));
                taxon.setParentTaxonName(rset.getString("parent_taxon_name"));
                //taxon.setBioregionMap(rset.getString("bioregion_map"));
                //taxon.setCountry(rset.getString("country"));
                //taxon.setBioregion(rset.getString("bioregion"));  
                taxon.setConnection(connection);
            }

            if (count == 0) A.log("getInfoInstance() not found taxonName:" + taxonName);

            if (count > 1 && "taxon".equals(tableName)) s_log.error("getInfoInstance() count:" + count + " should never be more than 1.  TaxonName:" + taxonName);

        } catch (SQLException e) {
            s_log.error("getInfoInstance() taxonName:" + taxonName + " e:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getInfoInstance() taxonName:" + taxonName);
        }
        if (taxon == null) {
          //It may not be found during Worldants, for instance amblyoponinae, but after the cleanup process it will...
          String warning = " taxon not found taxonName:" + taxonName;
          //if (AntwebProps.isDevMode()) warning += " theQuery:" + theQuery;
          A.log("getInfoInstance() " + warning);
          return null;
        }
        
        if (AntwebProps.isDevMode()) {
        
          //s_log.warn("getInfoInstance() taxonName:" + taxonName + " taxon:" + taxon + " query:" + theQuery);
        
          if ("myrmicinaestrumigenys emmae".equals("taxonName")) {
            s_log.warn("getInfoInstance() name:" + taxonName + " taxon:" + taxon.getTaxonName() + " query:" + theQuery);        
          }
        }
        return taxon;
    }       
    

    public void setTaxonomicInfo(String query, Taxon taxon) throws SQLException {

		Statement stmt = null;
		ResultSet rset = null;
 
		//s_log.warn("setTaxonomicInfo(query, taxon): " + query);
		
		try {
			stmt =  DBUtil.getStatement(getConnection(), "setTaxonomicInfo(query, taxon)");
            rset = stmt.executeQuery(query);

            while (rset.next()) {
                taxon.setIsExtant(true);

                String val = null;
                if (query.contains("taxon.kingdom_name")) {
					val = rset.getString("taxon.kingdom_name");
					if (Utility.notBlank(val)) taxon.setKingdomName(val);
                }
                if (query.contains("taxon.phylum_name")) {
					val = rset.getString("taxon.phylum_name");
					if (Utility.notBlank(val)) taxon.setPhylumName(val);
                }
                if (query.contains("taxon.class_name")) {
					val = rset.getString("taxon.class_name");
					if (Utility.notBlank(val)) taxon.setClassName(val);
                }
                if (query.contains("taxon.order_name")) {
					val = rset.getString("taxon.order_name");
					if (Utility.notBlank(val)) taxon.setOrderName(val);
                }
                if (query.contains("taxon.family")) {
					val = rset.getString("taxon.family");
					if (Utility.notBlank(val)) {
						taxon.setFamily(val);
						//if (getFamily() == null) s_log.warn("setTaxonomicInfo(" + project + ") family is null in query:" + theQuery);
					} 
                }
                if (query.contains("taxon.subfamily")) {
					val = rset.getString("taxon.subfamily");
					if (Utility.notBlank(val)) taxon.setSubfamily(val);
                }
                if (query.contains("taxon.tribe")) {
					val = rset.getString("taxon.tribe");
					if (Utility.notBlank(val)) taxon.setTribe(val);
				}
                if (query.contains("taxon.subgenus")) {
					val = rset.getString("taxon.subgenus");
					if (Utility.notBlank(val)) taxon.setSubgenus(val);
                }
                if (query.contains("taxon.speciesgroup")) {
					val = rset.getString("taxon.speciesgroup");
					if (Utility.notBlank(val)) taxon.setSpeciesGroup(val);
                }
                if (query.contains("taxon.taxarank")) {
                  String rank = rset.getString("taxon.taxarank");
                  if (!taxon.getRank().equals(rank)) {
                    taxon.setIsExtant(false);
                    s_log.warn("setTaxonomicInfo(query, taxon).  Incorrect rank should be:" + taxon.getRank() + " but is:" + rank + " query:" + query);
                  }
                }
                if (query.contains("taxon.type")) {
					taxon.setIsType((rset.getInt("taxon.type") == 1) ? true : false);            
                }
                if (query.contains("taxon.status")) {
					val = rset.getString("taxon.status");
					if (Utility.notBlank(val)) taxon.setStatus(val);
                }
            }
        } catch (SQLException e) {
            s_log.error("setTaxonomicInfo(query, taxon) e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setTaxonomicInfo(query, taxon)");
        }
    }

    public Taxon getTaxon(String genus, String species, String subspecies) {
        // Could be faster by being a single query, instead of just getting the taxonName here.
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name from taxon where genus = '" + genus + "'";
        if (species != null && !"".equals(species)) {
          query += " and species = '" + species + "'";
        } else {
          query += " and species is null";
        }
        if (subspecies != null && !"".equals(subspecies)) {
          query += " and subspecies = '" + subspecies + "'";
        } else {
          query += " and subspecies is null";
        }
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getTaxon()");
            rset = stmt.executeQuery(query);
            int count = 0;
            while (rset.next()) {
                ++count;
                taxonName = rset.getString("taxon_name");
            }
            //A.log("getTaxon(3) query:" + query + " count:" + count);

        } catch (SQLException e) {
            s_log.error("getTaxon() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxon()");
        }
        
        //A.log("getTaxon() taxonName:" + taxonName + " query:" + query);        
        
        return getInfoInstance(getConnection(), taxonName);
    }

    public HashMap<String, ArrayList<String>> getSubgenusHashMap() {
        HashMap<String, ArrayList<String>> subgenusHashMap = new HashMap<String, ArrayList<String>>();
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select distinct genus, subgenus from taxon where subgenus is not null and status != 'morphotaxon'";
        try {
            stmt = DBUtil.getStatement(getConnection(), "getShallowTaxa()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
                String genusName = rset.getString(1);
                ArrayList<String> subgenusList = null;
                if (subgenusHashMap.containsKey(genusName)) {
                    subgenusList = (ArrayList<String>) subgenusHashMap.get(genusName);
                } else {
                    subgenusList = new ArrayList<String>();
                }
                String subgenusName = rset.getString(2);
                subgenusList.add(subgenusName);
                Collections.sort(subgenusList);
                subgenusHashMap.put(genusName, subgenusList);
            }
        } catch (SQLException e) {
            s_log.error("getSubgenusHashMap() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getSubgenusHashMap()");
        }
        return subgenusHashMap;
    }


    public void setSubfamilyChartColor() {
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

    public ArrayList<Taxon> getShallowTaxa() {
        ArrayList<Taxon> taxa = new ArrayList<Taxon>();
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name from taxon";
        //A.log("getShallowTaxa() query:" + query);
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getShallowTaxa()");
            rset = stmt.executeQuery(query);

            int count = 0;
            while (rset.next()) {
              taxonName = rset.getString("taxon_name");
              Taxon taxon = getInfoInstance(getConnection(), taxonName);
              if (taxon == null) {
                A.log("getShallowTaxa() taxon not found:" + taxonName);
                continue;
              }
              taxa.add(taxon);
            }
        } catch (SQLException e) {
            s_log.error("getShallowTaxa() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getShallowTaxa()");
        }
        
        //A.log("getTaxon() taxonName:" + taxonName + " query:" + query);        
        
        return taxa;
    }    
        
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
        ArrayList<Taxon> taxa = new ArrayList<Taxon>();
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String query = "select taxon_name from taxon where " + rankClause + " and " + statusClause;
        //A.log("getTaxaWithClause() rankClause:" + rankClause + " statusClause:" + statusClause + " query:" + query);
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getTaxaWithClause()");
            rset = stmt.executeQuery(query);

            //A.log("getTaxaWithClause() query:" + query);

            int count = 0;
            while (rset.next()) {
              taxonName = rset.getString("taxon_name");
              Taxon taxon = getInfoInstance(getConnection(), taxonName);
              if (taxon == null) {
                A.log("getTaxaWithClause() taxon not found:" + taxonName);
                continue;
              }
              if (rankClause.contains(Rank.GENUS)) {  
                taxon.setBioregionMap(new TaxonPropDb(getConnection()).getBioregionMap(taxonName));        
              }
              if (rankClause.contains(Rank.SPECIES)) {  
                //A.log("getTaxon() introduced:" + getIntroducedMap(taxonName));
                taxon.setIntroducedMap(new TaxonPropDb(getConnection()).getIntroducedMap(taxonName));        
              }

              taxa.add(taxon);
            }
        } catch (SQLException e) {
            s_log.error("getTaxaWithClause() e:" + e + " query:" + query);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxaWithClause()");
        }
        
        if ("subfamily".equals(rankClause)) A.log("getTaxon() taxonName:" + taxonName + " query:" + query + " taxa:" + taxa);        
        
        return taxa;
    }    

    public ArrayList<Taxon> getTaxa(ArrayList<ResultItem> speciesList, String subfamilyFilter) {
    /* To make fast, get a list of genera for a given subfamily.  Restrict prior to fetch */
    
       ArrayList<Taxon> taxa = new ArrayList<Taxon>();
       
       for (ResultItem item : speciesList) {
          String taxonName = item.getTaxonName();
          //A.log("getTaxa() taxonName:" + taxonName + " item:" + item); 
         
          Taxon taxon = getInfoInstance(getConnection(), "taxon", item.getTaxonName());
          //A.log("TaxonDb.getTaxa() taxonName:" + taxonName + " taxon:" + taxon + " subfamilyFilter:" + subfamilyFilter);

          if (taxon != null) {
            if (("none".equals(subfamilyFilter) || subfamilyFilter == null) || subfamilyFilter.equals(taxon.getSubfamily())) {
              taxa.add(taxon);
            }
          }
       } 
       return taxa;
    }
    
    public int getWorldantsCount() {
        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select count(*) count from taxon where source like '%worldants%'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getWorldantsCount()");
            rset = stmt.executeQuery(theQuery);

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
    
    public String getTaxonNameFromAntcatId(Connection connection, String tableName, int antcatId) {
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from " + tableName + " where antcat_id = " + antcatId;
        try {            
            stmt = DBUtil.getStatement(connection, "getTaxonNameFromAntcatId() antcatId:" + antcatId);
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
        A.log("TaxonDb.deleteTaxon() delete:" + delete);
        stmt.executeUpdate(delete);
 
        // Delete the allantwebants projTaxon
        delete = "delete from proj_taxon " 
          + " where taxon_name = '" + taxonName + "'";
        stmt.executeUpdate(delete);
        A.log("TaxonDb.deleteTaxon() delete:" + delete);    
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
        A.log("moveTaxon() update:" + updateTaxon);
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

        (new UtilDb(getConnection())).updateField("taxon", "type", "null"); //, "status = 'morphotaxon'");      
      
        stmt = DBUtil.getStatement(getConnection(), "crawlForType()");
        s_log.warn("crawlForType()");
        
        String dml = "update taxon " 
          + " set type = 1 where taxarank = 'species' and status != 'morphotaxon' and taxon_name in "
          + " (select taxon_name from specimen where type != \"\")";

        count = stmt.executeUpdate(dml);

        A.log("crawlForType() count:" + count + " dml:" + dml);

      } finally {
        DBUtil.close(stmt, "crawlForType()");
      }

	  LogMgr.appendLog("compute.log", "  Types Crawl completed", true);                    
      return count + " records updated";
    }          

    private static String s_lastCurrentValidTaxonName = null;
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
        if ((family != null) && !("null".equals(family))) {
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
        if ((family != null) && !("null".equals(family))) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        boolean isValid = isSubfamilyForGenus(query, subfamily);
        
        if (!isValid) A.log("isValidSubfamilyForGenus() isValid:" + isValid + " subfamily:" + subfamily + " query:" + query);
        return isValid;
    }
 
    public DummyTaxon getDummyTaxon(String taxonName) 
      throws SQLException {
      
      return super.getDummyTaxon(taxonName, "taxon");
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
        List<String> taxonNames = new ArrayList<String>();
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from taxon " 
          + " where taxon_name like '%" + text + "%'"
          + "   and status = 'valid'";
        try {            
            stmt = DBUtil.getStatement(getConnection(), "getTaxonNames()");
            rset = stmt.executeQuery(theQuery);

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

      Statement stmt = null;
      ResultSet rset = null;
      String query = "select taxon_name, author_date from taxon where author_date like '%>%'";
	  try {            
            stmt = DBUtil.getStatement(getConnection(), "fixHtmlAuthorDates()");
            rset = stmt.executeQuery(query);
            int count = 0;
            while (rset.next()) {
                ++count;
                String taxonName = rset.getString("taxon_name");
                String authorDate = rset.getString("author_date");
                String newAuthorDate = UploadUtil.cleanHtml(authorDate);
                
                utilDb.updateField("taxon", "author_date", "'" + newAuthorDate + "'", " taxon_name = '" + taxonName + "'");
                utilDb.updateField("homonym", "author_date", "'" + newAuthorDate + "'", " taxon_name = '" + taxonName + "'");
            }
            A.log("fixHtmlAuthorDates() count:" + count);
	  } catch (SQLException e) {
		  s_log.error("fixHtmlAuthorDates() exception:" + e + " query:" + query);
	  } finally {
		  DBUtil.close(stmt, rset, "this", "fixHtmlAuthorDates()");
	  }    
    }       
    
    // Called in Specimen post process.
    public void removeIndetWithoutSpecimen() {
      (new UtilDb(getConnection())).deleteFrom("taxon", "where source like 'specimen%' and status in ('indetermined', 'unrecognized') and taxon_name not in (select taxon_name from specimen)");
    }

    public static String deleteSpeciesWithoutSpecimenOrAntcatSource(Connection connection) {
      String dmlWhereClause = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and taxon_name not in (select taxon_name from specimen) "
        + " and taxarank in ('species', 'subspecies')";
      int retVal = (new UtilDb(connection)).deleteFrom("taxon", dmlWhereClause);
      return retVal + " deleteSpeciesWithoutSpecimenOrAntcatSource. ";
    }
    public static String deleteGeneraWithoutSpecimenOrAntcatSource(Connection connection) {
      String dmlWhereClause = "where taxon_name not in (select taxon_name from proj_taxon where project_name = 'worldants') "
        + " and (subfamily, genus) not in (select subfamily, genus from specimen) "
        + " and taxarank in ('genus')";
      int retVal = (new UtilDb(connection)).deleteFrom("taxon", dmlWhereClause);
      return retVal + " deletedGeneraWithoutSpecimenOrAntcatSource. ";
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
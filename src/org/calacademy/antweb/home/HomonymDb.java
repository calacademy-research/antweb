package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class HomonymDb extends AntwebDb {
    
    private static Log s_log = LogFactory.getLog(HomonymDb.class);
        
    public HomonymDb(Connection connection) {
      super(connection);
    }

    public ArrayList<Taxon> getHomonyms() {
        ArrayList<Taxon> homonyms = new ArrayList<>();
        String query = "select taxon_name from homonym order by taxon_name";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getHomonyms()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                Taxon taxon = Homonym.getInfoInstance(getConnection(), taxonName);
                homonyms.add(taxon);
            }
        } catch (SQLException e) {
            s_log.error("getHomonyms() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "getHomonyms", "getHomonyms()");
        }
     
        return homonyms;
    }
    
    public boolean isHomonym(String taxonName) {
        boolean isHomonym = false;
        String query = "select taxon_name from homonym where taxon_name = '" + taxonName + "'";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "isHomonym()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                return true;
            }
        } catch (SQLException e) {
            s_log.error("isHomonym() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "isHomonym", "isHomonym()");
        }
     
        return isHomonym;
    }    
    
    public String getTaxonNameFromAntcatId(Connection connection, int antcatId) {
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        String theQuery = "select taxon_name from homonym where antcat_id = " + antcatId;
        try {            
            stmt = connection.createStatement();
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
        
        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return taxonName;
    }

    public static Taxon getInfoInstance(Connection connection, String taxonName, String authorDate) {
        /* New.  Mar 2012.  Mark */
        /* Used by OrphanTaxons and OrphanDescEdits, DescriptionAction, etc...   Useful, but because the 
           return object is not created as the appropriate subclass, of limited utility.
           Also see: DummyTaxon.getInstance()
           */
        Taxon taxon = null;
        if (taxonName == null) return null;

        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {            
            theQuery = " select taxarank, taxon_name, kingdom_name, phylum_name, order_name, class_name"
              + ", family, subfamily, genus, species, subspecies " 
              + ", source, insert_method, created, fossil, antcat, pending, type "
              + ", antcat_id, author_date, author_date_html, authors, year, status, available " 
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", country, bioregion "              
              + " from homonym where taxon_name='" + taxonName + "'"
              + " and author_date = '" + authorDate + "'";

            A.log("getInfoInstance() query:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                // Only one record expected
                String rank = rset.getString("taxarank");
                taxon = new Homonym();
                taxon.setRank(rank);

                //taxon.setTaxonName(rset.getString("taxon_name"));
                taxon.setKingdomName(rset.getString("kingdom_name"));
                taxon.setPhylumName(rset.getString("phylum_name"));
                taxon.setOrderName(rset.getString("order_name"));
                taxon.setClassName(rset.getString("class_name"));
                taxon.setFamily(rset.getString("family"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
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
                //taxon.setCountry(rset.getString("country"));
                //taxon.setBioregion(rset.getString("bioregion"));  

                taxon.setConnection(connection);
            }

            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getInfoInstance() not found taxonName:" + taxonName + " authorDate:" + authorDate);
            if (count > 1) s_log.error("getInfoInstance() count:" + count + " should never be more than 1.  TaxonName:" + taxonName+ " authorDate:" + authorDate);

        } catch (SQLException e) {
            s_log.error("getInfoInstance() taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getInfoInstance()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return taxon;
    }       
    
    public static Taxon getInfoInstance(Connection connection, int antcatId) {
        Taxon taxon = null;

        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {            
            theQuery = " select taxarank, taxon_name, kingdom_name, phylum_name, order_name, class_name"
              + ", family, subfamily, genus, species, subspecies " 
              + ", source, insert_method, created, fossil, antcat, pending, type "
              + ", antcat_id, author_date, author_date_html, authors, year, status, available " 
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", country, bioregion "              
              + " from homonym where antcat_id ='" + antcatId + "'";

            A.log("getInfoInstance() query:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                // Only one record expected
                String rank = rset.getString("taxarank");

                taxon = new Homonym();
                taxon.setRank(rank);

                //taxon.setTaxonName(rset.getString("taxon_name"));
                taxon.setKingdomName(rset.getString("kingdom_name"));
                taxon.setPhylumName(rset.getString("phylum_name"));
                taxon.setOrderName(rset.getString("order_name"));
                taxon.setClassName(rset.getString("class_name"));
                taxon.setFamily(rset.getString("family"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
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
                //taxon.setCountry(rset.getString("country"));
                //taxon.setBioregion(rset.getString("bioregion"));  

                taxon.setConnection(connection);
            }

            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getInfoInstance() not found antcatId:" + antcatId);
            if (count > 1) s_log.error("getInfoInstance() count:" + count + " should never be more than 1.  antcatId:" + antcatId);

        } catch (SQLException e) {
            s_log.error("getInfoInstance() antcatId:" + antcatId + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getInfoInstance() antcatId:" + antcatId);
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoInstance() name:" + taxonName + " query:" + theQuery);        
        return taxon;
    }       
     

    public boolean isExistingSubfamilyForAGenus(String family, String subfamily, String genus) 
      throws SQLException {
        boolean isSubfamilyForGenus = false;
        String query = "select distinct subfamily from homonym " 
            + " where taxarank = 'genus' and "; // Added Jun 26, 2014";
        if ((family != null) && !("null".equals(family))) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        isSubfamilyForGenus = isSubfamilyForGenus(query, subfamily);        

        if (isSubfamilyForGenus) {
          A.log("isExistingSubfamilyForAGenus() subfamily:" + subfamily + " query:" + query);
        }
        return isSubfamilyForGenus;
    }

    public boolean isValidSubfamilyForGenus(String family, String subfamily, String genus) 
    // As opposed to UploadDB.isExistingSubfamilyForAGenus(), used in Specimen upload, we do not specify rank so they could come from species (or specimen upload)
    // *** verify good.
      throws SQLException {

         String query = "select distinct subfamily from homonym " 
            + " where ";
        if ((family != null) && !("null".equals(family))) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        return isSubfamilyForGenus(query, subfamily);  // In AntwebDb.java
    }         

    public DummyTaxon getDummyTaxon(String taxonName) 
      throws SQLException {
      
      return super.getDummyTaxon(taxonName, "homonym");
    }

     
}
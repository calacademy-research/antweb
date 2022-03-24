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

    // table name? getInfoInstance
    // Without the authorDate, will be correct taxonName, but random homonym
    public Homonym getHomonym(String taxonName) {
        Homonym homonym = (Homonym) new HomonymDb(getConnection()).getInfoHomonym(taxonName, null);
        if (homonym == null) {
          return null;
        }
        try {
          homonym.setSeniorHomonym(getConnection());
        } catch (SQLException e) {
          s_log.warn("getHomonym() e:" + e);
        }
        s_log.debug("getHomonym(taxonName) taxonName:" + taxonName + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());
        return homonym;
    }

    public Homonym getHomonym(String taxonName, String authorDate) {
        Homonym homonym = (Homonym) new HomonymDb(getConnection()).getInfoHomonym(taxonName, authorDate);
        if (homonym == null) {
          return null;
        }
        try {
          homonym.setSeniorHomonym(getConnection());
        } catch (SQLException e) {
          s_log.warn("getHomonym() e:" + e);
        }
        s_log.debug("getHomonym(taxonName, authorDate) taxonName:" + taxonName + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());
        return homonym;
    }


    /*
    // This does not carry the authorDate over from the antcat ID.  Will be correct taxonName, but could be wrong homonym
    public static String getTaxonNameFromAntcatId(Connection connection, int antcatId) {
        TaxonDb taxonDb = new TaxonDb(connection);
        return taxonDb.getTaxonNameFromAntcatId(connection, "homonym", antcatId);
    } */
    public Homonym getHomonym(int antcatId) {
        Homonym homonym = (Homonym) getInfoHomonym(antcatId);
        try {
            homonym.setSeniorHomonym(getConnection());
        } catch (SQLException e) {
            s_log.warn("getHomonym() e:" + e);
        }
        s_log.debug("getHomonym(conn, antcatId) antcatId:" + antcatId + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());
        return homonym;
    }

    public Homonym getHomonym(String family, String subfamily, String genus
            , String species, String subspecies, String authorDate) throws SQLException {
        //This method gets taxonName from info (relatively) quickly.  Used to determine caching.

        Homonym homonym = new Homonym();

        if (family != null) homonym.setFamily(family);
        if (subfamily != null) homonym.setSubfamily(subfamily);
        if (genus != null) homonym.setGenus(genus);
        if (species != null) homonym.setSpecies(species);
        if (subspecies != null) homonym.setSubspecies(subspecies);

        homonym.setAuthorDate(authorDate);

        //A.log("getHomonym() order:" + homonym.getOrderName() + " family:" + homonym.getFamily()
        //  + " subfamily:" + homonym.getSubfamily() + " genus:" + homonym.getGenus());

        homonym.setTaxonomicInfo(getConnection());

        if (!homonym.isExtant()) {
            s_log.debug("getHomonym() homonym not extant:" + homonym + " isExtant:" + homonym.isExtant());
            return null;
        }
        homonym.setSeeAlso();

        //A.log("getHomonym() taxon:" + homonym.getClass() + " isExtant:" + homonym.isExtant()
        //  + " homonym:" + homonym);

        return homonym;
    }

    public Taxon getFullHomonym(String family, String subfamily, String genus
            , String species, String subspecies, String authorDate) throws SQLException {

        Homonym homonym = getHomonym(family, subfamily, genus, species, subspecies, authorDate);

        if (homonym == null) return null;

        homonym.finishInstance(getConnection());

        return homonym;
    }


    public ArrayList<Taxon> getHomonyms() {
        ArrayList<Taxon> homonyms = new ArrayList<>();
        String query = "select taxon_name, author_date from homonym order by taxon_name";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getHomonyms()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                String authorDate = rset.getString("author_date");
                Taxon taxon = getHomonym(taxonName, authorDate);
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

    private Taxon getInfoHomonym(String taxonName, String authorDate) {
        /* New.  Mar 2012.  Mark */
        /* Used by OrphanTaxons and OrphanDescEdits, DescriptionAction, etc...   Useful, but because the
           return object is not created as the appropriate subclass, of limited utility.
           */
        Taxon taxon = null;
        if (taxonName == null) return null;

        String criteria = " taxon_name = '" + taxonName + "'";

        // Criteria should not be null.
        if (authorDate != null) {
            criteria += " and author_date = '" + authorDate + "'";
        } else {
            if ("myrmicinaecarebara silvestrii".equals(taxonName)) criteria += " and author_date = '(Santschi, 1914)'";
            else if ("myrmicinaesolenopsis pygmaea".equals(taxonName)) criteria += " and author_date = 'Forel, 1905'";
            else if ("myrmicinaepheidole longipes".equals(taxonName)) {
                s_log.warn("No valid homonym for myrmicinaepheidole longipes");
                return null;
            }
            //s_log.info("getInfoHomonym() author_date should not be null for taxonName:" + taxonName);
        }

        return getHomonymWithCriteria(criteria);
    }

    private Taxon getInfoHomonym(int antcatId) {
        String criterion = " antcat_id = '" + antcatId + "'";

        return getHomonymWithCriteria(criterion);
    }

    private Taxon getHomonymWithCriteria(String criterion) {
        Homonym taxon = null;
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
              + " from homonym "
              + " where " + criterion;

            //s_log.info("getInfoHomonym() query:" + theQuery);
            stmt = DBUtil.getStatement(getConnection(), "getInfoHomonym()");
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
                taxon.setIsFossil(fossil == 1);
                taxon.setIsType(rset.getInt("type") == 1);
                taxon.setIsAntCat(rset.getInt("antcat") == 1);
                taxon.setIsPending(rset.getInt("pending") == 1);

                taxon.setAntcatId(rset.getInt("antcat_id"));
                taxon.setAuthorDate(rset.getString("author_date"));
                taxon.setAuthorDateHtml(rset.getString("author_date_html"));
                taxon.setAuthors(rset.getString("authors"));
                taxon.setYear(rset.getString("year"));
                taxon.setStatus(rset.getString("status"));
                taxon.setIsAvailable(rset.getInt("available") == 1);
                taxon.setCurrentValidName(rset.getString("current_valid_name"));
                taxon.setCurrentValidRank(rset.getString("current_valid_rank"));
                taxon.setCurrentValidParent(rset.getString("current_valid_parent"));
                taxon.setIsOriginalCombination(rset.getInt("original_combination") == 1);
                //taxon.setWasOriginalCombination((rset.getInt("was_original_combination") == 1) ? true : false);
                taxon.setWasOriginalCombination(rset.getString("was_original_combination"));  
                //taxon.setCountry(rset.getString("country"));
                //taxon.setBioregion(rset.getString("bioregion"));
            }

            if (AntwebProps.isDevMode()) if (count == 0) s_log.error("getInfoHomonym() not found. query:" + theQuery); // taxonName:" + taxonName + " authorDate:" + authorDate);
            if (count > 1) s_log.error("getInfoHomonym() count:" + count + " should never be more than 1. criterion:" + criterion); // TaxonName:" + taxonName + " authorDate:" + authorDate);

        } catch (SQLException e) {
            s_log.error("getInfoHomonym() criterion: " + criterion + " exception:" + e);
        } finally {
            DBUtil.close(stmt, rset, "this", "getInfoHomonym()");
        }

        //if (AntwebProps.isDevMode()) s_log.info("getInfoHomonym() name:" + taxonName + " query:" + theQuery);
        return taxon;
    }       

    public boolean isExistingSubfamilyForAGenus(String family, String subfamily, String genus) 
      throws SQLException {
        boolean isSubfamilyForGenus = false;
        String query = "select distinct subfamily from homonym " 
            + " where taxarank = 'genus' and "; // Added Jun 26, 2014";
        if (family != null && !"null".equals(family)) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        isSubfamilyForGenus = isSubfamilyForGenus(query, subfamily);        

        if (isSubfamilyForGenus) {
          s_log.debug("isExistingSubfamilyForAGenus() subfamily:" + subfamily + " query:" + query);
        }
        return isSubfamilyForGenus;
    }

    public boolean isValidSubfamilyForGenus(String family, String subfamily, String genus) 
    // As opposed to UploadDB.isExistingSubfamilyForAGenus(), used in Specimen upload, we do not specify rank so they could come from species (or specimen upload)
    // *** verify good.
      throws SQLException {

         String query = "select distinct subfamily from homonym " 
            + " where ";
        if (family != null && !"null".equals(family)) {
            query += " family = '" + family + "' and ";
        } else {
            query += " family = 'formicidae' and ";
        }
        query += "   genus = '" + genus + "'";

        return isSubfamilyForGenus(query, subfamily);  // In AntwebDb.java
    }
     
}
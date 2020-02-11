package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;
import java.math.BigDecimal;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import java.sql.Connection;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public class Homonym extends Taxon implements Serializable {

    private static Log s_log = LogFactory.getLog(Homonym.class);

    private Taxon seniorHomonym = null;

    // Without the authorDate, will be correct taxonName, but random homonym
    public static Homonym getInfoInstance(Connection connection, String taxonName) {
        TaxonDb taxonDb = new TaxonDb(connection);
        Homonym homonym = (Homonym) taxonDb.getInfoInstance(connection, "homonym", taxonName); 
        if (homonym == null) {
          return null; 
        }
        try {
          homonym.setSeniorHomonym();
        } catch (SQLException e) {
          s_log.warn("getInfoInstance() e:" + e);
        }
        A.log("getInfoInstance(conn, taxonName) taxonName:" + taxonName + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());  
        return homonym;
    }  
       
    public static Homonym getInfoInstance(Connection connection, String taxonName, String authorDate) {
        TaxonDb taxonDb = new TaxonDb(connection);
        Homonym homonym = (Homonym) HomonymDb.getInfoInstance(connection, taxonName, authorDate);  
        if (homonym == null) {
          return null; 
        }
        try {
          homonym.setSeniorHomonym();
        } catch (SQLException e) {
          s_log.warn("getInfoInstance() e:" + e);
        }
        A.log("getInfoInstance(conn, taxonName, authorDate) taxonName:" + taxonName + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());  
        return homonym;
    }  
                   
    /*
    // This does not carry the authorDate over from the antcat ID.  Will be correct taxonName, but could be wrong homonym
    public static String getTaxonNameFromAntcatId(Connection connection, int antcatId) {
        TaxonDb taxonDb = new TaxonDb(connection);
        return taxonDb.getTaxonNameFromAntcatId(connection, "homonym", antcatId);
    } */
    public static Homonym getInfoInstance(Connection connection, int antcatId) {
        HomonymDb homonymDb = new HomonymDb(connection);
        Homonym homonym = (Homonym) homonymDb.getInfoInstance(connection, antcatId);  
        try {
          homonym.setSeniorHomonym();
        } catch (SQLException e) {
          s_log.warn("getInfoInstance() e:" + e);
        }
        A.log("getInfoInstance(conn, antcatId) antcatId:" + antcatId + " homonym:" + homonym + " currentValidName:" + homonym.getCurrentValidName());  
        return homonym;
    }             

    public static Homonym getInfoInstance(Connection connection, String family, String subfamily, String genus
      , String species, String subspecies, String authorDate) throws SQLException {
     //This method gets taxonName from info (relatively) quickly.  Used to determine caching.

        Homonym homonym = new Homonym();

        if (family != null) homonym.setFamily(family);     
        if (subfamily != null) homonym.setSubfamily(subfamily);     
        if (genus != null) homonym.setGenus(genus);
        if (species != null) homonym.setSpecies(species); 
        if (subspecies != null) homonym.setSubspecies(subspecies);
                    
        homonym.setAuthorDate(authorDate);                    
        homonym.setConnection(connection);

        //A.log("getInfoInstance() order:" + homonym.getOrderName() + " family:" + homonym.getFamily() 
        //  + " subfamily:" + homonym.getSubfamily() + " genus:" + homonym.getGenus());

        homonym.setTaxonomicInfo();

        if (!homonym.isExtant()) {
          A.log("getInfoInstance() homonym not extant:" + homonym + " isExtant:" + homonym.isExtant());
          return null;        
        }
        
        homonym.setSeeAlso();
       
        //A.log("getInfoInstance() taxon:" + homonym.getClass() + " isExtant:" + homonym.isExtant() 
        //  + " homonym:" + homonym);
          

        return homonym;
    }
    
    public static Taxon getInstance(Connection connection, String family, String subfamily, String genus
      , String species, String subspecies, String authorDate) throws SQLException {

        Homonym homonym = Homonym.getInfoInstance(connection, family, subfamily, genus, species, subspecies, authorDate);
        
        if (homonym == null) return null;

        homonym.finishInstance();

        return homonym;
    }

    public void finishInstance() throws SQLException {    
        //A.log("getInstance() taxon:" + taxon.getClass() + " isExtant:" + taxon.isExtant() + " subfamily:" + taxon.getSubfamily());

        init();
        setDescription(new HomonymDescEditDb(connection).getDescEdits(this, false));
        setHabitats();
        setMethods();
        setTypes();      
              
        setElevations();
        setCollectDateRange();
        
        setSeniorHomonym();
        setHomonymAuthorDates();        
    }

    
    public void setTaxonomicInfo() throws SQLException {
        A.log("setTaxonomicInfo()");

        //setSubspecies(name);

        String theQuery = null;
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            String subfamilyClause = "";
            if (subfamily != null) subfamilyClause = " and subfamily = '" + AntFormatter.escapeQuotes(subfamily) + "' ";
            String genusClause = "";
            if (genus != null) {
              genusClause =  " and genus ='" + AntFormatter.escapeQuotes(genus) + "' ";
            } else {
              genusClause =  " and genus is null ";
            }
            String speciesClause = "";
            if (species != null) {
              speciesClause = " and species ='" + AntFormatter.escapeQuotes(species) + "' ";
            } else {
              speciesClause =  " and species is null ";
            }
            String subspeciesClause = "";
            if (subspecies != null) {
              subspeciesClause = " and subspecies ='" + AntFormatter.escapeQuotes(subspecies) + "' "; 
            } else {
              subspeciesClause =  " and subspecies is null ";
            }
            String authorDateClause = "";
            if (authorDate != null && !"".equals(authorDate)) authorDateClause =   " and author_date = '" + getSqlAuthorDate() + "'";           

            theQuery = "select distinct kingdom_name, phylum_name, class_name, order_name " 
              + ", family, subfamily, genus, subgenus, speciesgroup, species " 
              + ", rank, type, status, author_date "
              + " from homonym "
              + " where 1 = 1 " 
              + subfamilyClause
              + genusClause
              + speciesClause
              + subspeciesClause
              + authorDateClause;

            // theQuery += " and proj_taxon.project_name = '" + project + "'";

            A.log("setTaxonomicInfo() exthant:" + isExtant() + " theQuery:" + theQuery);

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            setIsExtant(false);
            while (rset.next()) {
                setIsExtant(true);

                String val = rset.getString("kingdom_name");
                if (Utility.notBlank(val)) setKingdomName(val);
                val = rset.getString("phylum_name");
                if (Utility.notBlank(val)) setPhylumName(val);
                val = rset.getString("class_name");
                if (Utility.notBlank(val)) setClassName(val);
                val = rset.getString("order_name");
                if (Utility.notBlank(val)) setOrderName(val);
                val = rset.getString("family");
                if (Utility.notBlank(val)) {
                    setFamily(val);
                    if (getFamily() == null) s_log.warn("setTaxonomicInfo() family is null in query:" + theQuery);
                } 
                val = rset.getString("subfamily");
                if (Utility.notBlank(val)) setSubfamily(val);
                val = rset.getString("genus");
                if (Utility.notBlank(val)) setGenus(val);
                val = rset.getString("subgenus");
                if (Utility.notBlank(val)) setSubgenus(val);
                val = rset.getString("speciesgroup");
                if (Utility.notBlank(val)) setSpeciesGroup(val);
                val = rset.getString("species");
                if (Utility.notBlank(val)) setSpecies(val);

                val = rset.getString("rank");
                if (Utility.notBlank(val)) setRank(rank);
                
                setIsType((rset.getInt("type") == 1) ? true : false);            
                val = rset.getString("status");
                if (Utility.notBlank(val)) setStatus(val);
                val = rset.getString("author_date");
                if (Utility.notBlank(val)) setAuthorDate(val);
            }
        } catch (SQLException e) {
            s_log.error("setTaxonomicInfo() e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "setTaxonomicInfo()");
        }
    }
    
    public String getSqlAuthorDate() {
      return AntFormatter.escapeQuotes(authorDate);             
    }
        
    public void init() throws SQLException {
        /* Beginning effort to consolidate initialization process.  Currently BrowseAction calls
           all sorts of methods to instantiate the taxon */

        String theQuery = "";
        String taxonName = null;

        Statement stmt = null;
        ResultSet rset = null;
        try {        
            taxonName = AntFormatter.escapeQuotes(getTaxonName());   

            theQuery = "select source, line_num, insert_method, created, fossil, antcat, pending " 
              + ", parent_taxon_name, image_count, type" 
              + ", antcat_id, author_date, author_date_html, authors, year, status, available " 
              + ", current_valid_name, current_valid_rank, current_valid_parent, original_combination, was_original_combination "
              + ", country, bioregion "    
              + ", hol_id, access_group "
              + " from homonym "
              + " where taxon_name='" + taxonName + "'"
              + " and author_date = '" + getSqlAuthorDate() + "'"
              ;

A.log("init() query:" + theQuery);
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            while (rset.next()) {
                // should return only one record             
                setSource(rset.getString("source"));
                setLineNum(rset.getInt("line_num"));
                setInsertMethod(rset.getString("insert_method"));
                setCreated(rset.getTimestamp("created"));
                setIsFossil((rset.getInt("fossil") == 1) ? true : false);
                setIsType((rset.getInt("type") == 1) ? true : false);
                setIsAntCat((rset.getInt("antcat") == 1) ? true : false);
                setIsPending((rset.getInt("pending") == 1) ? true : false);           

                setAntcatId(rset.getInt("antcat_id"));
                setAuthorDate(rset.getString("author_date"));
                setAuthorDateHtml(rset.getString("author_date_html"));
                setAuthors(rset.getString("authors"));
                setYear(rset.getString("year"));
                setStatus(rset.getString("status"));
                setIsAvailable((rset.getInt("available") == 1) ? true : false);
                setCurrentValidName(rset.getString("current_valid_name"));
                setCurrentValidRank(rset.getString("current_valid_rank"));
                setCurrentValidParent(rset.getString("current_valid_parent"));                
                setIsOriginalCombination((rset.getInt("original_combination") == 1) ? true : false);
                setWasOriginalCombination(rset.getString("was_original_combination"));
                //setCountry(rset.getString("country"));
                //setBioregion(rset.getString("bioregion"));
                
                setParentTaxonName(rset.getString("parent_taxon_name"));                
                setImageCount(rset.getInt("image_count"));
                setHolId(rset.getInt("hol_id"));     
                setGroupId(rset.getInt("access_group"));           
            }             
            //s_log.warn("init() taxonName:" + taxonName + " isFossil;" + isFossil);
        } catch (SQLException e) {
            s_log.error("init() taxonName:" + taxonName + " e:" + e + " theQuery:" + theQuery);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "init()");
        }
    }
    
    public String getName() {
        return getFullName();
    }
    
    public String getFullName() {
        String fullName = Taxon.getPrettyTaxonName(getTaxonName());
        //A.log("getFullName() fullName:" + fullName + " taxonName:" + getTaxonName());
        return fullName;
    }

    public void setDescription(boolean isManualEntry) {
    /* 
     * We have removed project from description_edit table.  This method should work fine with this 
     * property removed.  There will still be a collection of description records per taxon (title).
     * We have aimed this method against description_edit instead of description.
     * To do: Remove taxon_name from query and replace with id.  Include into this class.
     */
        Formatter formatter = new Formatter();
        Hashtable description = new Hashtable();
        String taxonName = null;
        String theQuery = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());       
 
            theQuery = "select * from description_homonym where taxon_name='" + taxonName + "'"
               + " and author_date = '" + getSqlAuthorDate() + "'"
            ;
            
            if (isManualEntry) {
              theQuery += " and is_manual_entry = 1";
            }

            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            String key = null;
            String value = null;
            int recordCount = 0;
            while (rset.next()) {
                recordCount++;
                key = rset.getString(2);
                value = rset.getString(3);
                //key = AntFormatter.unescapeCharacters(key);
                //value = AntFormatter.unescapeCharacters(value);
                value = formatter.dequote(value);
                description.put(key, value);
 
                if (false)
                  if (AntwebProps.isDevOrStageMode())
                    if ("pseudomyrmecinaetetraponera rufonigra".equals(taxonName))
                      if ("taxonomictreatment".equals(key))
                        s_log.warn("setDescription() key:" + key + " value:" + value);
            }

            A.log("setDescription() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (SQLException e) {
            s_log.error("setDescription() for taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setDescription()");
        }
        this.description = description;
    }

    public void setSeniorHomonym() throws SQLException {
        seniorHomonym = Taxon.getInfoInstance(connection, getTaxonName());
        A.log("setSeniorHomonym() homonym:" + getTaxonName() + " seniorHomonym:" + seniorHomonym);
    }
    public Taxon getSeniorHomonym() {
        return seniorHomonym;
    }

    public void setHomonymAuthorDates() throws SQLException {
      Vector<String> homonymAuthorDates = new Vector<String>();;
      String taxonName = getTaxonName();
      Statement stmt = null;
      ResultSet rset = null;
      try {
        String query = "select author_date " 
            + " from homonym " 
            + " where taxon_name = '" + taxonName + "'"
            + " and author_date != '" + getSqlAuthorDate() + "'"
            ;
        stmt = DBUtil.getStatement(connection, "getHomonymAuthorDates()");
        rset = stmt.executeQuery(query);
        A.log("getHomonymAuthorDates() query:" + query);
        while (rset.next()) {
            String authorDate = rset.getString("author_date");
            homonymAuthorDates.add(authorDate);
        }
        A.log("getHomonymAuthorDates() homonymAuthorDates:" + homonymAuthorDates);        
      } finally {
        DBUtil.close(stmt, rset, this, "getHomonymAuthorDates()");
      }
      this.homonymAuthorDates = homonymAuthorDates;
    }

    public String getTaxonUrl() {
      return super.makeUrl(getRank(), getSubfamily(), getGenus(), getSpecies(), getSubspecies());
    }

    public String getUrl() {
      return makeUrl("description.do", getRank(), getSubfamily(), getGenus(), getSpecies(), getSubspecies(), getAuthorDate());
    }
    
    public String makeUrl(String targetDo, String rank, String subfamily, String genus, String species, String subspecies, String authorDate) {
        //Formatter format = new Formatter();
        
        String link = super.makeUrl(targetDo, rank, subfamily, genus, species, subspecies);
        link += "&authorDate=" + authorDate;           

        return link;
    }

}

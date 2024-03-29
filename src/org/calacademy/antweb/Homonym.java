package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
    
public class Homonym extends Taxon implements Serializable {

    private static final Log s_log = LogFactory.getLog(Homonym.class);

    private Taxon seniorHomonym;
    

    public void finishInstance(Connection connection) throws SQLException {
        //A.log("getInstance() taxon:" + taxon.getClass() + " isExtant:" + taxon.isExtant() + " subfamily:" + taxon.getSubfamily());

        init(connection);
        setDescription(new HomonymDescEditDb(connection).getDescEdits(this, false));
        setHabitats(connection);
        setMethods(connection);
        setTypes(connection);
              
        setElevations(connection);
        setCollectDateRange(connection);
        
        setSeniorHomonym(connection);
        setHomonymAuthorDates(connection);
    }

    
    public void setTaxonomicInfo(Connection connection) throws SQLException {
        s_log.debug("setTaxonomicInfo()");

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
              + ", taxarank, type, status, author_date "
              + " from homonym "
              + " where 1 = 1 " 
              + subfamilyClause
              + genusClause
              + speciesClause
              + subspeciesClause
              + authorDateClause;

            // theQuery += " and proj_taxon.project_name = '" + project + "'";

            s_log.debug("setTaxonomicInfo() exthant:" + isExtant() + " theQuery:" + theQuery);

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

                val = rset.getString("taxarank");
                if (Utility.notBlank(val)) setRank(rank);
                
                setIsType(rset.getInt("type") == 1);
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
        
    public void init(Connection connection) throws SQLException {
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

            //A.log("init() query:" + theQuery);
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);
            while (rset.next()) {
                // should return only one record             
                setSource(rset.getString("source"));
                setLineNum(rset.getInt("line_num"));
                setInsertMethod(rset.getString("insert_method"));
                setCreated(rset.getTimestamp("created"));
                setIsFossil(rset.getInt("fossil") == 1);
                setIsType(rset.getInt("type") == 1);
                setIsAntCat(rset.getInt("antcat") == 1);
                setIsPending(rset.getInt("pending") == 1);

                setAntcatId(rset.getInt("antcat_id"));
                setAuthorDate(rset.getString("author_date"));
                setAuthorDateHtml(rset.getString("author_date_html"));
                setAuthors(rset.getString("authors"));
                setYear(rset.getString("year"));
                setStatus(rset.getString("status"));
                setIsAvailable(rset.getInt("available") == 1);
                setCurrentValidName(rset.getString("current_valid_name"));
                setCurrentValidRank(rset.getString("current_valid_rank"));
                setCurrentValidParent(rset.getString("current_valid_parent"));                
                setIsOriginalCombination(rset.getInt("original_combination") == 1);
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

    public void setDescription(Connection connection, boolean isManualEntry) {
    /* 
     * We have removed project from description_edit table.  This method should work fine with this 
     * property removed.  There will still be a collection of description records per taxon (title).
     * We have aimed this method against description_edit instead of description.
     * To do: Remove taxon_name from query and replace with id.  Include into this class.
     */
        Formatter formatter = new Formatter();
        Hashtable<String, String> description = new Hashtable<>();
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
                value = Formatter.dequote(value);
                description.put(key, value);
 
                if (false)
                  if (AntwebProps.isDevOrStageMode())
                    if ("pseudomyrmecinaetetraponera rufonigra".equals(taxonName))
                      if ("taxonomictreatment".equals(key))
                        s_log.warn("setDescription() key:" + key + " value:" + value);
            }

            s_log.debug("setDescription() recordCount:" + recordCount + " query:" + theQuery);
            
        } catch (SQLException e) {
            s_log.error("setDescription() for taxonName:" + taxonName + " exception:" + e + " theQuery:" + theQuery);
            // project:" + project + " 
        } finally {
            DBUtil.close(stmt, rset, this, "setDescription()");
        }
        this.description = description;
    }

    public void setSeniorHomonym(Connection connection) throws SQLException {
        seniorHomonym = new TaxonDb(connection).getTaxon(getTaxonName());
        s_log.debug("setSeniorHomonym() homonym:" + getTaxonName() + " seniorHomonym:" + seniorHomonym);
    }
    public Taxon getSeniorHomonym() {
        return seniorHomonym;
    }

    public void setHomonymAuthorDates(Connection connection) throws SQLException {
      Vector<String> homonymAuthorDates = new Vector<>();
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
        s_log.debug("getHomonymAuthorDates() query:" + query);
        while (rset.next()) {
            String authorDate = rset.getString("author_date");
            homonymAuthorDates.add(authorDate);
        }
        s_log.debug("getHomonymAuthorDates() homonymAuthorDates:" + homonymAuthorDates);
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

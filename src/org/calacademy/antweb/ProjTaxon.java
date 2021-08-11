package org.calacademy.antweb;

import java.sql.*;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public class ProjTaxon extends OverviewTaxon {

    private static Log s_log = LogFactory.getLog(ProjTaxon.class);

    private int rev = 0;
    private String source = null;
    //private boolean isIntroduced = false;   
        
    private String projectName;
    
  /* Next Subtaxon functionality enables a taxon to report how many subtaxons it has.
     For instance, for formicidae family:
       26 subfamilies, 476 genera, 30000 species, 150000 specimens)
   */

    public ProjTaxon() {
      //A.log("ProjTaxon constructor()");
    }

    public ProjTaxon(String projectName, String taxonName, String rank) {
      this.taxonName = taxonName;
      this.projectName = projectName;
      this.rank = rank;
    }


    public void init(Connection connection) throws SQLException {
        String query = "";
        String taxonName = null;
        String projectName = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());            
            projectName = getProjectName();
            query = "select subfamily_count, genus_count, species_count, specimen_count, image_count"  
              + " , created, rev, source " //, is_introduced "          
              + " from " + getTable()
              + " where " + getKeyClause()
              + "   and taxon_name = '" + taxonName + "'";

            stmt = DBUtil.getStatement(connection, "ProjTaxon.init()");            
            rset = stmt.executeQuery(query);
            
            while (rset.next()) {
                exists = true;
                setSubfamilyCount(rset.getInt("subfamily_count"));
                setGenusCount(rset.getInt("genus_count"));
                setSpeciesCount(rset.getInt("species_count"));
                setSpecimenCount(rset.getInt("specimen_count"));           
                setImageCount(rset.getInt("image_count"));
                //setIsEndemic((rset.getInt("is_endemic") == 1) ? true : false);
                
                setCreated(rset.getTimestamp("created"));
                setRev(rset.getInt("rev"));
                setSource(rset.getString("source"));
               // setIsIntroduced((rset.getInt("is_introduced") == 1) ? true : false);

                if (taxonName.contains("formiciinae")) {
                  //A.log("init() taxonName:" + taxonName + " projectName:" + projectName + " genusCount:" + getGenusCount() + " imageCount:" + imageCount + " query:" + query);
                  //AntwebUtil.logStackTrace();
                }
            }             
            //A.log("init() warn() taxonName:" + taxonName + " projectName:" + projectName + " imageCount:" + imageCount);
        } catch (SQLException e) {
            s_log.error("init() taxonName:" + taxonName + " project_name:" + projectName + "  e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "this", "ProjTaxon.init()");
        }
        
    }    
    
    public String toString() {
      return "ProjTaxon project:" + getProjectName() + " taxonName:" + getTaxonName() 
        + " <br>rank:" + rank
        + " <br>created:" + getCreated()
        + " <br>source:" + getSource()
        + " subfamilyCount:" + getSubfamilyCount() + " genusCount:" + getGenusCount()
        + " speciesCount:" + getSpeciesCount() + " specimenCount:" + getSpecimenCount()
        + " imageCount:" + getImageCount() // + " isEndemic:" + getIsEndemic()
        //+ "<br>antwikiRev:" + getRev() 
        //+ " isIntroduced:" + getIsIntroduced()
        ;
    }

    public String getTable() {
      return "proj_taxon";
    }
    
    public String getKeyClause() {
      return " project_name = '" + projectName + "' ";
    }    
    
    public String getProjectName() {
      return projectName;
    }
    public void setProjectName(String projectName) {
      this.projectName = projectName;
    }

    public int getRev() {
        return rev;
    }
    public void setRev(int rev) {
        this.rev = rev;
    }
    
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }

}

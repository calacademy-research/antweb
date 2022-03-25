package org.calacademy.antweb.geolocale;

import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public class BioregionTaxon extends OverviewTaxon {

    private static final Log s_log = LogFactory.getLog(BioregionTaxon.class);

    private String bioregionName;
    //String source = null;
    
    public BioregionTaxon() {
    }

    public BioregionTaxon(Bioregion bioregion, String taxonName, String rank) {
      this.bioregionName = bioregion.getName();
      this.taxonName = taxonName;
      this.rank = rank;
    }
    
    // Overrides so as to get source
    public void init(Connection connection) throws SQLException {
        String query = "";
        String taxonName = null;
        
        Statement stmt = null;
        ResultSet rset = null;            
                    
        try {
            taxonName = Formatter.escapeQuotes(getTaxonName());   
            query = "select subfamily_count, genus_count, species_count, specimen_count, image_count, created, source " //, is_introduced, is_endemic  "
              + " from " + getTable()
              + " where " + getKeyClause()
              + "   and taxon_name = '" + taxonName + "'";

            //A.log("init query:" + query);    //  && getTaxonName().contains("anceps")
        
            stmt = DBUtil.getStatement(connection, "BioregionTaxon.init()");  
            rset = stmt.executeQuery(query);
            
            while (rset.next()) {
                exists = true;
                setSubfamilyCount(rset.getInt("subfamily_count"));
                setGenusCount(rset.getInt("genus_count"));
                setSpeciesCount(rset.getInt("species_count"));
                setSpecimenCount(rset.getInt("specimen_count"));
                setImageCount(rset.getInt("image_count"));
                setCreated(rset.getTimestamp("created"));
                setSource(rset.getString("source"));
                //setIsIntroduced((rset.getInt("is_introduced") == 1) ? true : false);
                //setIsEndemic((rset.getInt("is_endemic") == 1) ? true : false);

                ProjTaxon projTaxon = AllAntwebMgr.get(taxonName);
                setGlobalChildCount(projTaxon);              
             
                //if (AntwebProps.isDevMode() && "ectatomminae".equals(taxonName)) s_log.warn("init() taxonName:" + taxonName + " keyClause:" + getKeyClause() + " genusCount:" + getGenusCount() + " imageCount:" + imageCount);
            }             
            //A.log("init() warn() taxonName:" + taxonName + " projectName:" + projectName + " imageCount:" + imageCount);
        } catch (SQLException e) {
            s_log.error("init() Cannot convert value 0000-00-00 00:00:00? taxonName:" + taxonName + "  e:" + e + " query:" + query);
            throw e;
        } finally {
          DBUtil.close(stmt, rset, "BioregionTaxon.init()");
      }
    }           
    
    public String toString() {
      return "BioregionTaxon bioregionName:" + getBioregionName() 
        + " <br>taxonName:" + getTaxonName() 
        + " <br>rank:" + rank
        + " <br>created:" + getCreated()
        + " <br>source:" + getSource()
        + " <br>subfamilyCount:" + getSubfamilyCount() 
        + " <br>genusCount:" + getGenusCount()
        + " <br>speciesCount:" + getSpeciesCount() 
        + " <br>specimenCount:" + getSpecimenCount()
        + " <br>imageCount:" + getImageCount() 
        ;
    }

    
    public String getTable() {
      return "bioregion_taxon";
    }
    
    public String getKeyClause() {
      return " bioregion_name = '" + bioregionName + "'";
    }       
    
    public String getBioregionName() {
      return bioregionName;
    }
    public void setBioregionName(String bioregionName) {
      this.bioregionName = bioregionName;
    }

/*
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }
*/
}

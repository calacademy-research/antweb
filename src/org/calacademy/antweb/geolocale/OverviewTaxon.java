package org.calacademy.antweb.geolocale;

import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public abstract class OverviewTaxon extends TaxonSet {

    private static final Log s_log = LogFactory.getLog(OverviewTaxon.class);

    public OverviewTaxon() {
    }

    public void init(Connection connection) throws SQLException {
        String query = "";
        String taxonName = null;
        String museumCode = null;
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = AntFormatter.escapeQuotes(getTaxonName());   
            query = "select subfamily_count, genus_count, species_count, specimen_count, image_count " 
              + " from " + getTable()
              + " where " + getKeyClause()
              + "   and taxon_name = '" + taxonName + "'";

            //A.log("OverviewTaxon.init query:" + query);        
        
            stmt = DBUtil.getStatement(connection, "OverviewTaxon.init()");
            rset = stmt.executeQuery(query);
            
            while (rset.next()) {
                exists = true;
                setSubfamilyCount(rset.getInt("subfamily_count"));
                setGenusCount(rset.getInt("genus_count"));
                setSpeciesCount(rset.getInt("species_count"));
                setSpecimenCount(rset.getInt("specimen_count"));               
                setImageCount(rset.getInt("image_count"));
                
                if (AntwebProps.isDevMode() && "myrmicinaepheidole".equals(taxonName)) s_log.warn("init() taxonName:" + taxonName + " keyClause:" + getKeyClause() + " genusCount:" + getGenusCount() + " imageCount:" + imageCount);
                //if (getTaxonName().contains("humile")) {
                  //A.log("init query:" + query);    //  && getTaxonName().contains("anceps")
                //}  

                ProjTaxon projTaxon = AllAntwebMgr.get(taxonName);
                if (projTaxon != null) {
                  setGlobalChildCount(projTaxon);                              
                } else {
                  s_log.debug("init() not found in allantweb taxon:" + taxonName);
                }
            }             
            //A.log("init() warn() taxonName:" + taxonName + " projectName:" + projectName + " imageCount:" + imageCount);
        } catch (SQLException e) {
            s_log.error("init() taxonName:" + taxonName + " museumCode:" + museumCode + "  e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "OverviewTaxon.init()");
        }
    }    

    public abstract String getTable();
    
    public abstract String getKeyClause();
    
    // Will be set upon fetch with Allantweb projTaxon at subclass init() above.
    public void setGlobalChildCount(ProjTaxon projTaxon) {
      if (projTaxon == null) return;
      String taxonName = projTaxon.getTaxonName();
      if (projTaxon.getRank().equals(Rank.SUBFAMILY)) setGlobalChildCount(projTaxon.getGenusCount());
      if (projTaxon.getRank().equals(Rank.GENUS)) setGlobalChildCount(projTaxon.getSpeciesCount());
      if (projTaxon.getRank().equals(Rank.SPECIES)) setGlobalChildCount(projTaxon.getSpecimenCount());      
      if (projTaxon.getRank().equals(Rank.SUBSPECIES)) setGlobalChildCount(projTaxon.getSpecimenCount());      
      //A.log("setGlobalChildCount() rank:" + projTaxon.getRank() + " sfc:" + projTaxon.getSubfamilyCount() + " gc:" + projTaxon.getGenusCount() + " sc:" + projTaxon.getSpeciesCount());
      //if (taxonName.contains("myrmicinaepheidole minima catella")) s_log.debug("init() taxonName:" + taxonName + " childCount:" + projTaxon.getGlobalChildCount());
    }          
}

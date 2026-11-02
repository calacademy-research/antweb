package org.calacademy.antweb.geolocale;

import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

/**
 * A taxon's row in a country, adm1, region or subregion species list (table geolocale_taxon, key geolocale_id and
 * taxon_name): counts, created, source, is_introduced and is_endemic. Made by Geolocale.getTaxonSet when a taxon
 * page or list is seen in that place; init reads the row and the global counts. Written by the nightly sets (set 1
 * AntCat rows, set 2 from specimens: GeolocaleTaxonDb) and the curators' species list tool. Docs:
 * docs/data-model.md (geolocale_taxon), docs/flows/places.md.
 */
public class GeolocaleTaxon extends OverviewTaxon {

    private static final Log s_log = LogFactory.getLog(GeolocaleTaxon.class);

    private int geolocaleId;
    
    private int rev;    
    private boolean isIntroduced;
    private boolean isEndemic;
    
    public GeolocaleTaxon() {
    }

    public GeolocaleTaxon(Geolocale geolocale, String taxonName, String rank) {
      this.taxonName = taxonName;
      this.geolocaleId = geolocale.getId();
      this.rank = rank;
    }

    // Overrides so as to get is_introduced
    public void init(Connection connection) throws SQLException {
        String query = "";
        String taxonName = null;
        String museumCode = null;
        Statement stmt = null;
        ResultSet rset = null;
        try {
            taxonName = Formatter.escapeQuotes(getTaxonName());   
            query = "select subfamily_count, genus_count, species_count, specimen_count, image_count, created, source, is_introduced, is_endemic " 
              + globalImageCountColumn(taxonName)   // the Images column's first number (2026-10-27)
              + " from " + getTable()
              + " where " + getKeyClause()
              + "   and taxon_name = '" + taxonName + "'";


            //A.log("init query:" + query);    //  && getTaxonName().contains("anceps")
        
            stmt = DBUtil.getStatement(connection, "GeolocaleTaxon.init()");  
            rset = stmt.executeQuery(query);
            
            while (rset.next()) {
                exists = true;
                setSubfamilyCount(rset.getInt("subfamily_count"));
                setGenusCount(rset.getInt("genus_count"));
                setSpeciesCount(rset.getInt("species_count"));
                setSpecimenCount(rset.getInt("specimen_count"));
                setImageCount(rset.getInt("image_count"));
                readGlobalImageCount(rset);
                setCreated(rset.getTimestamp("created"));
                setSource(rset.getString("source"));
                setIsIntroduced(rset.getInt("is_introduced") == 1);
                setIsEndemic(rset.getInt("is_endemic") == 1);
                             
                ProjTaxon projTaxon = AllAntwebMgr.get(taxonName);
                setGlobalChildCount(projTaxon);                                           
                                
                //if (AntwebProps.isDevMode() && "ectatomminae".equals(taxonName)) s_log.warn("init() taxonName:" + taxonName + " keyClause:" + getKeyClause() + " genusCount:" + getGenusCount() + " imageCount:" + imageCount);
            }             
            //A.log("init() warn() taxonName:" + taxonName + " projectName:" + projectName + " imageCount:" + imageCount);
            //stmt.close();
        } catch (SQLException e) {
            s_log.error("init() taxonName:" + taxonName + " geolocaleId:" + geolocaleId + "  e:" + e + " query:" + query);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, "GeolocaleTaxon.init()");        
        }
    }       
    
    public String getTable() {
      return "geolocale_taxon";
    }
    
    public String getKeyClause() {
      return " geolocale_id = " + geolocaleId;
    }    
    
    public String toString() {
      return "GeolocaleTaxon " 
        + " <br>geolocaleId:" + getGeolocaleId() + " (" + getName() + ")"
        + " <br>taxonName:" + getTaxonName() 
        + " <br>rank:" + rank
        + " <br>created:" + getCreated()
        + " <br>source:" + getSource()
        + " <br>subfamilyCount:" + getSubfamilyCount() 
        + " <br>genusCount:" + getGenusCount()
        + " <br>speciesCount:" + getSpeciesCount() 
        + " <br>specimenCount:" + getSpecimenCount()
        + " <br>imageCount:" + getImageCount() 
        + " <br>isIntroduced:" + getIsIntroduced() 
        + " <br>isEndemic:" + isEndemic
        ;
    }

    public String getName() {
      Geolocale geolocale = GeolocaleMgr.getGeolocale(getGeolocaleId());
      return geolocale == null ? "geolocale " + getGeolocaleId() : geolocale.toString();  // not in GeolocaleMgr (2026-10-28)
    }

    public int getGeolocaleId() {
      return geolocaleId;
    }
    public void setGeolocaleId(int geolocaleId) {
      this.geolocaleId = geolocaleId;
    }
    
    public int getRev() {
        return rev;
    }
    public void setRev(int rev) {
        this.rev = rev;
    }
 
    public String getSourceAnchor() {
       if ("antwiki".equals(getSource())) return "<a title='From Antwiki data'>A</a>";
       if ("specimen".equals(getSource())) return "<a title='From specimen data'>S</a>";
       if ("adm1Specimen".equals(getSource())) return "<a title='From specimen adm1 data'>s</a>";
       if ("speciesListTool".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       if ("curator".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       if ("speciesListUpload".equals(getSource())) return "<a title='Antweb curator'>T</a>";
       return "";
    }
    
    public String getSourceStr() {
       if ("antwiki".equals(getSource())) return "Source = AntWiki.";
       if ("specimen".equals(getSource())) return "Source = AntWeb specimen.";
       if ("adm1Specimen".equals(getSource())) return "Source = AntWeb specimen (adm1).";
       if ("speciesListTool".equals(getSource())) return "Source = AntWeb curator.";
       if ("curator".equals(getSource())) return "Source = AntWeb curator.";
       if ("speciesListUpload".equals(getSource())) return "Source = AntWeb curator upload.";
       return "";
    }    
    
    public boolean getIsIntroduced() {
        return isIntroduced;
    } 
    public void setIsIntroduced(boolean isIntroduced) {
        this.isIntroduced = isIntroduced;
    }
    
    public boolean getIsEndemic() {
        return isEndemic;
    } 
    public void setIsEndemic(boolean isEndemic) {
        this.isEndemic = isEndemic;
    }
        
}

package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class TaxonCountDb extends AntwebDb {
    
    /* 
       Note that these counts are primarily for the TaxonSet classes (MuseumTaxon, ProjTaxon,
       BioregionTaxon and GeolocaleTaxon).
       
       Image_count of the taxon table is only populated for species and subspecies.
       The [rank]_count fields of taxon are not used as those queries are always against 
         an Overview (generally the project: allantwebants).
    */
    private static Log s_log = LogFactory.getLog(TaxonCountDb.class);
        
    public TaxonCountDb(Connection connection) {
      super(connection);
    }

/*
    // Primary entry point...
    public void allCountCrawls() throws SQLException {    

      boolean debugMode = false && AntwebProps.isDevMode();

      if (!debugMode)
        imageCountCrawl();

      new ProjTaxonCountDb(getConnection()).countCrawls();

       
      if (!debugMode) {
          // All together these crawls will be about 18 minutes.
          // We don't want to wait for these to test on dev...
    	  new GeolocaleTaxonCountDb(getConnection()).countCrawls();
		  new BioregionTaxonCountDb(getConnection()).countCrawls();
		  new MuseumTaxonCountDb(getConnection()).countCrawls();
      }
    }
*/


/*
    // Just to the basics. Preliminary for all counts.
    public void countCrawls() throws SQLException {
      // startParentCrawl(); // This is handled during insert now.
      imageCountCrawl();
    }
*/    
    // ------------------------- Parent ID Crawl ------------------------
/*
    public void startParentCrawl() 
        throws SQLException {
           s_log.info("startParentCrawl()");
           setParents();

 		   LogMgr.appendLog("compute.log", "  Parent Crawl completed", true);                    
           
       //     int formicidaeId = getTaxonId("formicidae");
       //     setParent("subfamily", "formicidae", null, null, null, formicidaeId, "formicidae");
    }

    // This method sweeps through the whole taxon table setting the parent_taxon_name field 
    private void setParents() 
      throws SQLException {

      String taxonName = null, rank = null, kingdomName = null, phylumName = null, className = null
          , orderName = null, family = null, subfamily = null, genus = null, species = null, parentTaxonName = null;

      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = getConnection().createStatement();
        String query = "select taxon_name, kingdom_name, phylum_name, class_name, order_name, family, subfamily, genus, species, rank " 
          + " from taxon ";

        rset = stmt.executeQuery(query);        
        while (rset.next()) {
          // parentTaxonId = rset.getInt("taxon_id");
          taxonName = rset.getString("taxon_name");
          kingdomName = rset.getString("kingdom_name");
          phylumName = rset.getString("phylum_name");
          className = rset.getString("class_name");
          orderName = rset.getString("order_name");
          family = rset.getString("family");
          subfamily = rset.getString("subfamily");
          genus = rset.getString("genus");
          species = rset.getString("species");
          rank = rset.getString("rank");

          if ("phylum".equals(rank)) {
            parentTaxonName = kingdomName;
          }
          if ("class".equals(rank)) {
            parentTaxonName = phylumName;
          }
          if ("order".equals(rank)) {
            parentTaxonName = className;
          }
          if ("family".equals(rank)) {
            parentTaxonName = orderName;
          }
          if ("subfamily".equals(rank)) {
            parentTaxonName = family;
          }
          if ("genus".equals(rank)) {
            parentTaxonName = subfamily; 
          }
          if ("species".equals(rank)) {
            parentTaxonName = subfamily + genus;
          }
         // if specimen, then parentTaxonName would be subfamily + genus + " " + species, but it is already set in taxon_name

         //if (AntwebProps.isDevMode()) s_log.info("setParentId() rank:" + rank + " family:" + family + " subfamily:" + subfamily 
         //   + " genus:" + genus + " species:" + species + " taxonId:" + taxonId);    

          setParentInDb(taxonName, parentTaxonName);
        }
      } finally {
        DBUtil.close(stmt, rset, this, "setParents()");
      }
    }
                
    private String setParentInDb(String taxonName, String parentTaxonName) 
      throws SQLException {
        String message = null;
        Statement stmt = null;
        try {
          stmt = getConnection().createStatement();

          parentTaxonName = AntFormatter.escapeQuotes(parentTaxonName);
          taxonName = AntFormatter.escapeQuotes(taxonName);

          String updateSql = "update taxon ";
          updateSql += " set parent_taxon_name = '" + parentTaxonName + "' " 
            + " where taxon_name = '" + taxonName + "'";
        
          int taxonUpdateCount = stmt.executeUpdate(updateSql);
        } catch (SQLException e) {
          s_log.error("setParentInDb(" + taxonName + ", " + parentTaxonName + ") e:" + e);
        } finally {
            DBUtil.close(stmt, null, this, "setParentInDb()");
        }        
        return message;
     }        
*/
    // ------------------------- Image Count Crawl ----------------------------

     // These methods to count taxon and images are completely project independent
     public void imageCountCrawl()
       throws SQLException {
       
          s_log.warn("imageCountCrawl()");
          countSpecimenImages();
          s_log.warn("done countSpecimenImages()");
          countSpeciesImages();
          s_log.warn("done countSpeciesImages()");
          countTaxonImages("genus");
          s_log.warn("done countTaxonImages('genus')");
          countTaxonImages("subfamily");
          s_log.warn("done countTaxonImages('subfamily')");
          countTaxonImages("family");     
          s_log.warn("done countTaxonImages('family')");
          
  	      LogMgr.appendLog("compute.log", "  Image Count Crawl completed", true);                              
     }
     
     private void countSpecimenImages() 
       throws SQLException {
       String query = "select count(*) count, image_of_id code from image " 
           //+ " where shot_type != 'l'"
           + " group by code";

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = getConnection().createStatement();
          rset = stmt.executeQuery(query);
        
          int count = -1;
          String code = null;
          while (rset.next()) {
            count = rset.getInt("count");
            code = rset.getString("code");
            updateSpecimenImageCount(code, count);
          }
          //if (AntwebProps.isDevMode()) s_log.info("countSpecimen() count:" + count);                        
          stmt.close();       
       } catch (SQLException e) {
         s_log.warn("countSpecimenImages() query:" + query + " e:" + e);
         throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "countSpecimenImages()");
        }
     }     

     private void countSpeciesImages() 
       throws SQLException {
       String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
           query = "select sum(image_count) theSum, taxon_name from specimen " 
             + " group by taxon_name";

            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(query);
        
           int theSum = -1;
           String parentTaxonName = null;
           while (rset.next()) {
             theSum = rset.getInt("theSum");
             parentTaxonName = rset.getString("taxon_name");
             updateTaxonImageCount(parentTaxonName, theSum);
             
             if (AntwebProps.isDevMode()) {
               if ((parentTaxonName.contains("amblyoponinaeadetomyrma"))) {
                 //s_log.warn("countSpeciesImages() amblyoponaeadetomyrma taxonName:" + parentTaxonName + " theSum:" + theSum);
               }
             }               
             
           }
           //if (AntwebProps.isDevMode()) s_log.info("countSpecimen() count:" + count);                        
       } catch (SQLException e) {
          s_log.warn("countSpeciesImages() query:" + query + " e:" + e);
          throw e;
       } finally {
           DBUtil.close(stmt, rset, this, "countSpeciesImages()");
       }
     }     

     
     private void countTaxonImages(String rank) 
       throws SQLException {
       String query = "select sum(image_count) count, parent_taxon_name from taxon " 
           + " where rank = '" + rank + "'"
           + " group by parent_taxon_name";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = getConnection().createStatement();
            rset = stmt.executeQuery(query);
         int count = -1;
         String parentTaxonName = null;
         while (rset.next()) {
           count = rset.getInt("count");
           parentTaxonName = rset.getString("parent_taxon_name");
           updateTaxonImageCount(parentTaxonName, count);
         }
         //if (AntwebProps.isDevMode()) s_log.info("countSpecimen() count:" + count);                        
       } catch (SQLException e) {
         s_log.warn("countTaxonImages() query:" + query + " e:" + e);
         throw e;         
        } finally {
            DBUtil.close(stmt, rset, this, "countTaxonImages()");
        }   
     }

     public void updateSpecimenImageCount(String code, int count)
       throws SQLException {
        Statement stmt = null;
        try {
          stmt = getConnection().createStatement();
        
          String updateSql = "update specimen set image_count = '" + count + "'" 
            + " where code = '" + code + "'";

          //if (AntwebProps.isDevMode()) s_log.info("updateSpecimenImageCount() update:" + updateSql);
          int taxonUpdateCount = stmt.executeUpdate(updateSql);
        } finally {
          DBUtil.close(stmt, null, this, "updateSpecimenImageCount()");
        } 
     }    

     public void updateTaxonImageCount(String taxonName, int count)
       throws SQLException {
        Statement stmt = null;
        try {
          stmt = getConnection().createStatement();
          //String taxonName = getTaxonName(taxonId);
        
          String updateSql = "update taxon set image_count = '" + count + "'" 
            + " where taxon_name = '" + taxonName + "'";

          //s_log.info("updateImageCount() update:" + updateSql);
          int taxonUpdateCount = stmt.executeUpdate(updateSql);
        } finally {
          DBUtil.close(stmt, null, this, "updateTaxonImageCount()");
        }
     }    
    


}
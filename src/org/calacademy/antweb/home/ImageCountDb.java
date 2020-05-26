package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import javax.servlet.http.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.Formatter;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.upload.*;

public class ImageCountDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(ImageCountDb.class);

    public ImageCountDb(Connection connection) {
      super(connection);
    }

    // ------------------------- Image Count Crawl ----------------------------

     // These methods to count taxon and images are completely project independent
     public void imageCountCrawls() throws SQLException {
       
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


         //if (!debugMode) {
             // All together these crawls will be about 18 minutes.
             // We don't want to wait for these to test on dev...
             new GeolocaleTaxonCountDb(getConnection()).imageCountCrawl();
             new ProjTaxonCountDb(getConnection()).imageCountCrawl();
             new BioregionTaxonCountDb(getConnection()).imageCountCrawl();
             new MuseumTaxonCountDb(getConnection()).imageCountCrawl();
         //}

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
           + " where taxarank = '" + rank + "'"
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
package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public class ImageCountDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(ImageCountDb.class);

    public ImageCountDb(Connection connection) {
      super(connection);
    }

    // ------------------------- Image Count Crawl ----------------------------

     // These methods to count taxon and images are completely project independent
     public void imageCountCrawls() throws SQLException {

         s_log.info("imageCountCrawl()");
         countSpecimenImages();
         s_log.info("done countSpecimenImages()");
         countSpeciesImages();
         s_log.info("done countSpeciesImages()");
         countTaxonImages("genus");
         s_log.info("done countTaxonImages('genus')");
         countTaxonImages("subfamily");
         s_log.info("done countTaxonImages('subfamily')");
         countTaxonImages("family");
         s_log.info("done countTaxonImages('family')");

         new GeolocaleTaxonCountDb(getConnection()).imageCountCrawl();
         new ProjTaxonCountDb(getConnection()).imageCountCrawl();
         new BioregionTaxonCountDb(getConnection()).imageCountCrawl();
         new MuseumTaxonCountDb(getConnection()).imageCountCrawl();

  	      LogMgr.appendLog("compute.log", "  Image Count Crawl completed", true);
     }

    /** Sets the image count for each specimen
     * <br>
     * Count is determined by counting rows in image table with matching specimen code
     *
     * @throws SQLException
     */
    private void countSpecimenImages()
            throws SQLException {

        String query = "update specimen " +
                "set image_count = " +
                "(select count(id) " +
                "from image " +
                "where image.image_of_id = specimen.code)";

        Statement stmt = null;

        try {
            stmt = DBUtil.getStatement(getConnection(), "countSpecimenImages()");
            stmt.executeUpdate(query);

            //if (AntwebProps.isDevMode()) s_log.info("countSpecimen() count:" + count);
            stmt.close();

        } catch (SQLException e) {
            s_log.warn("countSpecimenImages() query:" + query + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, this, "countSpecimenImages()");
        }
    }

    /** Update the number of images for each species using the specimen table
     * <p>Count is determined by summing the image counts of all specimens with matching taxon_name</p>
     * @throws SQLException
     */
    private void countSpeciesImages() throws SQLException {
        String query = "update taxon " +
                "set image_count = " +
                "(select COALESCE(SUM(specimen.image_count), 0) " +
                "from ant.specimen " +
                "where taxon.taxon_name = specimen.taxon_name)";

        Statement stmt = null;

        try {
            stmt = DBUtil.getStatement(getConnection(), "countSpeciesImages()");

            stmt.executeUpdate(query);

        } catch (SQLException e) {
            s_log.warn("countSpeciesImages() query:" + query + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, this, "countSpeciesImages()");
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
            stmt = DBUtil.getStatement(getConnection(), "countTaxonImages()");
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
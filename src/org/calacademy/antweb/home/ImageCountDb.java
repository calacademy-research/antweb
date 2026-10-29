package org.calacademy.antweb.home;

import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/**
 * Nightly set 4 (imageCountCrawls): the image counts of the taxon tree, then of every species list. Sets
 * specimen.image_count from the image table, each taxon's from the specimens filed under its name, adds
 * subspecies to species, species to genera and so up to the family (by parent_taxon_name), then runs the image
 * crawls of the project, geolocale, bioregion and museum lists. Tables image, specimen, taxon and the four list
 * tables. Docs: docs/flows/nightly.md (set 4), docs/numbers.md. Tests: PlaceTotalsDbTest.
 */
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
         rollUpTaxonImageCounts();
         s_log.info("done rollUpTaxonImageCounts()");

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
                "from specimen " +   // the connection's own database (was ant.specimen)
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


    /**
     * Ranks whose taxon.image_count is added to their parent's, in this order (2026-10-21). countSpeciesImages()
     * first sets every taxon to the images of the specimens filed under its own name; then subspecies are added
     * to their species, species to their genus, genera to their subfamily, subfamilies to the family. So the
     * image_count of a taxon = images of the specimens of the taxon and of every taxon under it. Before, the
     * species were never added to their genus (a genus counted only the specimens filed under the genus name),
     * and each step replaced the parent's own images instead of adding to them. Checked by CountInvariants.
     */
    public static final String[] ROLL_UP_RANKS = {"subspecies", "species", "genus", "subfamily"};

    public void rollUpTaxonImageCounts() throws SQLException {
        for (String rank : ROLL_UP_RANKS) countTaxonImages(rank);
    }

     private void countTaxonImages(String rank)
       throws SQLException {
       String query = "select sum(image_count) count, parent_taxon_name from taxon "
           + " where taxarank = '" + rank + "' and parent_taxon_name is not null"
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

          // Added to the parent's own images (rollUpTaxonImageCounts).
          String updateSql = "update taxon set image_count = coalesce(image_count, 0) + " + count
            + " where taxon_name = " + DBUtil.sqlLiteral(taxonName);   // quotes and backslashes

          //s_log.info("updateImageCount() update:" + updateSql);
          int taxonUpdateCount = stmt.executeUpdate(updateSql);
        } finally {
          DBUtil.close(stmt, null, this, "updateTaxonImageCount()");
        }
     }
}
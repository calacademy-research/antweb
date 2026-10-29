package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Countable;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;


/**
 * Count crawls of the geolocale species lists (geolocale_taxon); the counting code is in CountDb.
 * childrenCountCrawl: a list's count columns set to 0, then specimens, species, genera and subfamilies of each
 * row (GeolocaleDb.updateCounts, nightly set 2). imageCountCrawl: images of the place's specimens per name
 * (ImageCountDb, set 4). countReport: the counts of one place for an admin (utilData.do?action=countReport).
 * Docs: docs/flows/nightly.md (sets 2 and 4), docs/numbers.md. Tests: TaxonTreeCountDbTest, RegionCountDbTest.
 */
public class GeolocaleTaxonCountDb extends CountDb {
    
    private static final Log s_log = LogFactory.getLog(GeolocaleTaxonCountDb.class);
        
    public GeolocaleTaxonCountDb(Connection connection) {
      super(connection);
    }

    /*
    public void countCrawls() throws SQLException {
      childrenCountCrawl();
      //imageCountCrawl();
    }

    public static boolean s_reported = false;
    public void countCrawls(int geolocaleId) throws SQLException {
      childrenCountCrawl(geolocaleId);

      if (true) { // !AntwebProps.isDevMode()) {
          imageCountCrawl(geolocaleId);
      } else {
          if (!s_reported) {
              s_reported = true;
              A.log("countCrawls() OPTIMIZATION. imageCountCrawl omit on dev.");
          }
      }
    }
*/

    // For debugging: http://localhost/antweb/utilData.do?action=countReport&num=768  (geolocaleId)
    public String countReport(int geolocaleId) throws SQLException {
        return childrenCountReport(geolocaleId);
        //imageCountCrawl(geolocaleId);
    }


    // ------------------------- Countable_ Child Count Crawl ----------------------------

     public void childrenCountCrawl() 
       throws SQLException {
         //s_log.warn("startChildrenCountCrawl()");
          ArrayList<Geolocale> geolocales = GeolocaleMgr.getLiveGeolocales();
          for (Geolocale geolocale : geolocales) {
            if (geolocale.isIsland()) s_log.debug("childrenCountCrawl() island geolocale:" + geolocale);
            if (geolocale.getIsValid()) {
              childrenCountCrawl(geolocale);
            }
          }
     }

     public void childrenCountCrawl(int geolocaleId) throws SQLException {
          //s_log.warn("startChildrenCountCrawl(" + geolocaleId + ")");
          Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
          if (geolocale == null) {
            s_log.warn("childrenCountCrawl() geolocaleId:" + geolocaleId + " is null. Failed to initialize?");
            return;
          }
          if (geolocale.getIsValid()) {
            childrenCountCrawl(geolocale);
          }
     }

    // For debugging: http://localhost/antweb/utilData.do?action=countReport&num=768  (geolocaleId)
    public String childrenCountReport(int geolocaleId) throws SQLException {
        //s_log.warn("startChildrenCountCrawl(" + geolocaleId + ")");
        Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
        if (geolocale == null) {
            String message = "childrenCountReport() geolocaleId:" + geolocaleId + " is null. Failed to initialize?";
            s_log.warn(message);
            return message;
        }
        if (geolocale.getIsValid()) {
            return childrenCountReport(geolocale);
        }
        return null;
    }
     
    /**
     * Before the specimens of a place's names are counted, the species and subspecies rows of its list
     * are set to 0 (2026-10-20): the count query only returns names that have specimens there, so a
     * name whose specimens are gone (or, on a region or subregion, a name with specimens elsewhere only)
     * kept its old number, and the place's Specimens total (the sum of these rows) with it.
     */
    @Override
    protected int countSpecimens(Countable countable) throws SQLException {
        if (countable instanceof Geolocale) resetSpecimenCounts(((Geolocale) countable).getId());
        return super.countSpecimens(countable);
    }

    /**
     * Sets the count columns of every row of one geolocale's list to 0 (2026-10-21; before: only specimen_count
     * of the species and subspecies rows, so a genus or subfamily row whose children were gone kept its old
     * Species, Genera and Specimens numbers). The crawl then writes every row that has children or specimens.
     */
    public int resetSpecimenCounts(int geolocaleId) throws SQLException {
        String dml = "update geolocale_taxon set specimen_count = 0, species_count = 0, genus_count = 0, subfamily_count = 0"
            + " where geolocale_id = ? and (specimen_count <> 0 or species_count <> 0 or genus_count <> 0 or subfamily_count <> 0)";
        try (PreparedStatement stmt = getConnection().prepareStatement(dml)) {
            stmt.setInt(1, geolocaleId);
            return stmt.executeUpdate();
        }
    }

    // ------------------------- Countable_Taxon Image Count Crawl ----------------------------

     // These methods populate Geolocale_taxons (just for subregions!).
     // Get all of the species for a project, and use the species image counts to pupulate
     // the various ranks with summarized image counts
     
     public void imageCountCrawl() 
       throws SQLException {
          //s_log.info("startImageCountCrawl()");
          // Since 2026-10-20 a taxon's count is of the images of this place's specimens only: names
          // with none here get no row from the query, so every count starts at 0.
          resetImageCounts("geolocale_taxon");

          ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales();
          //ArrayList<Project> projects = ProjectMgr.getProjects();
          for (Geolocale geolocale : geolocales) {
             //A.log("GeolocaleTaxonCountDb.imageCountCrawl() project:" + project);
             imageCountCrawl(geolocale);
          }
     }
     
     public void imageCountCrawl(int geolocaleId) 
       throws SQLException {
          //s_log.warn("imageCountCrawl(" + geolocaleId + ")");
                
        // *** does this need DB refressing?  If not, could fix MuseumCountDb:77                
          Geolocale geolocale = GeolocaleMgr.getGeolocale(geolocaleId);
          imageCountCrawl(geolocale);
     }

}    
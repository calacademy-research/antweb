package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;


public class GeolocaleTaxonCountDb extends CountDb {
    
    private static Log s_log = LogFactory.getLog(GeolocaleTaxonCountDb.class);
        
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
            if (geolocale.isIsland()) A.log("childrenCountCrawl() island geolocale:" + geolocale);
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
     
    // ------------------------- Countable_Taxon Image Count Crawl ----------------------------

     // These methods populate Geolocale_taxons (just for subregions!).
     // Get all of the species for a project, and use the species image counts to pupulate
     // the various ranks with summarized image counts
     
     public void imageCountCrawl() 
       throws SQLException {
          //s_log.info("startImageCountCrawl()");

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
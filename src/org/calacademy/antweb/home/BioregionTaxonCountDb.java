package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;


/**
 * Count crawls of the bioregion species lists (bioregion_taxon); the counting code is in CountDb.
 * childrenCountCrawl: specimens, species, genera and subfamilies of each row (BioregionDb.populate, nightly
 * set 3; utilData.do?action=runCountCrawls). imageCountCrawl: images of the bioregion's specimens per name,
 * rows set to 0 first (ImageCountDb, nightly set 4). Docs: docs/flows/nightly.md (sets 3 and 4), docs/numbers.md.
 */
public class BioregionTaxonCountDb extends CountDb {
    
    private static final Log s_log = LogFactory.getLog(BioregionTaxonCountDb.class);
        
    public BioregionTaxonCountDb(Connection connection) {
      super(connection);
    }

    /*
    public void countCrawls() throws SQLException {
      childrenCountCrawl();
      //imageCountCrawl();
    }
    public void countCrawls(String bioregionName) throws SQLException {
      childrenCountCrawl(bioregionName);
      //imageCountCrawl(bioregionName);
    }
    */
    // ------------------------- Countable_ Child Count Crawl ----------------------------

     public void childrenCountCrawl() 
       throws SQLException {
          s_log.warn("childrenCountCrawl()");
          ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
          for (Bioregion bioregion : bioregions) {
             childrenCountCrawl(bioregion);
          }
     }

     public void childrenCountCrawl(String bioregionName)
       throws SQLException {
          //A.log("childrenCountCrawl(" + bioregionName + ")");
          Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
          childrenCountCrawl(bioregion);
     }
     
    // ------------------------- Countable_Taxon Image Count Crawl ----------------------------

     // These methods populate Geolocale_taxons (just for subregions!).
     // Get all of the species for a project, and use the species image counts to pupulate
     // the various ranks with summarized image counts
     
     public void imageCountCrawl() 
       throws SQLException {
          s_log.debug("startImageCountCrawl()");
          // Since 2026-10-20 a taxon's count is of the images of this place's specimens only: names
          // with none here get no row from the query, so every count starts at 0.
          resetImageCounts("bioregion_taxon");

          ArrayList<Bioregion> bioregions = BioregionMgr.getBioregions();
          for (Bioregion bioregion : bioregions) {
             //if (AntwebProps.isDevMode()) s_log.info("startImageCountCrawl() bioregion:" + bioregion);
             imageCountCrawl(bioregion);
          }
     }
     
     private void imageCountCrawl(String bioregionName) 
       throws SQLException {
          //A.log("startImageCountCrawl(" + bioregionName + ")");
                
        // *** does this need DB refressing?  If not, could fix MuseumCountDb:77                
          Bioregion bioregion = BioregionMgr.getBioregion(bioregionName);
          if (bioregion == null) s_log.warn("imageCountCrawl bioregion not found:" + bioregionName);
          imageCountCrawl(bioregion);
     }

}    
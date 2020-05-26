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

public class MuseumTaxonCountDb extends CountDb {
    
    private static Log s_log = LogFactory.getLog(MuseumTaxonCountDb.class);
        
    public MuseumTaxonCountDb(Connection connection) {
      super(connection);
    }

    /*
    public void countCrawls() throws SQLException {
      childrenCountCrawl();
      //imageCountCrawl();
    }

    public void countCrawls(String museumCode) throws SQLException {
      childrenCountCrawl(museumCode);
      //imageCountCrawl(museumCode);
    }
*/

// ------------------------- Countable_Taxon Child Count Crawl ----------------------------

     public void childrenCountCrawl() 
       throws SQLException {
         s_log.warn("childrenCountCrawl()");
          //ArrayList<Museum> museums = (new MuseumDb(getConnection())).getMuseums();
          ArrayList<Museum> museums = MuseumMgr.getMuseums();
          for (Museum museum : museums) {
             childrenCountCrawl(museum);
          }
     }

     public void childrenCountCrawl(String museumCode)
       throws SQLException {
          //A.log("startChildrenCountCrawl(" + museumCode + ")");
	          //Museum museum = (new MuseumDb(getConnection())).getMuseum(museumCode);
          Museum museum = MuseumMgr.getMuseum(museumCode);
          if (museum != null) {
            childrenCountCrawl(museum);
          } else {
            s_log.warn("childrenCountCrawl() museum not found:" + museumCode);
          }
     }

    // ------------------------- Countable_Taxon Image Count Crawl ----------------------------

     // These methods populate Museum_taxons.
     // Get all of the species for a project, and use the species image counts to pupulate
     // the various ranks with summarized image counts
     
     public void imageCountCrawl()
       throws SQLException {
          A.log("imageCountCrawl()");
                
          ArrayList<Museum> museums = (new MuseumDb(getConnection())).getMuseums();
          for (Museum museum : museums) {            
             imageCountCrawl(museum);
          }    
     }

     private void imageCountCrawl(String museumCode) 
       throws SQLException {
          //A.log("imageCountCrawl(" + museumCode + ")");
                
          Museum museum = (new MuseumDb(getConnection())).getMuseum(museumCode);
          if (museum != null) {
            imageCountCrawl(museum);
          } else {
            s_log.warn("imageCountCrawl() museum not found:" + museumCode);
          }
     }

}
     
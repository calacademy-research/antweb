 package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

 public class ProjTaxonCountDb extends CountDb {
    
    private static Log s_log = LogFactory.getLog(ProjTaxonCountDb.class);
        
    public ProjTaxonCountDb(Connection connection) {
      super(connection);
    }

/*
    // Called from TaxonCountDb.allCountCrawls();
    public void countCrawls() throws SQLException {
      childrenCountCrawl();
	  LogMgr.appendLog("compute.log", "  Project Taxon Children Counts crawled", true);                    

	  //imageCountCrawl();
	  //LogMgr.appendLog("compute.log", "  Project Taxon Image Counts crawled", true);
    }

    public void countCrawl(String projectName)         // about a 32 sec.
      throws SQLException {
        // If a projTaxon operation removes records there, but the image counts and parents are still 
        //   correct in the taxon table, then this method will correct a particular out of date project
          s_log.warn("countCrawl() begin project:" + projectName);

        Project project = ProjectMgr.getProject(projectName);
        if (project == null) {
          A.log("countCrawl() project not found:" + projectName);
          return;
        }
        
        childrenCountCrawlchildrenCountCrawl(project);
        //imageCountCrawl(project);
        
        A.log("countCrawl() end project:" + projectName);
    }
*/
    // ------------------------- Proj_Taxon Child Count Crawl ----------------------------

     public void childrenCountCrawl() 
       throws SQLException {
         s_log.warn("childrenCountCrawl()");
          ArrayList<Project> projects = ProjectMgr.getLiveProjects();
          for (Project project : projects) {            
             childrenCountCrawl(project);
          }
          fixExceptions();
     }

    public void childrenCountCrawl(String projectName)
            throws SQLException {
        Project project = ProjectMgr.getProject(projectName);
        if (project != null) {
            childrenCountCrawl(project);
        } else {
            s_log.warn("childrenCountCrawl() project not found:" + projectName);
        }
    }

    // ------------------------- Project Taxon Image Count Crawl -------------------------

     private void fixExceptions() {
       A.log("fixExceptions()");
        String query = "";
        try {
            Statement stmt;
            stmt = getConnection().createStatement();

            query = "update proj_taxon set source = '" + Source.ANTCAT + "' where project_name = 'worldants'";
            stmt.executeUpdate(query);

            query = "update proj_taxon set source = '" + Source.ANTCAT + "' where project_name = 'fossilants' and taxon_name in (select taxon_name from taxon where status = 'valid')";
            stmt.executeUpdate(query);

            query = "update proj_taxon set source = '" + Source.CURATOR + "' where project_name = 'fossilants' and taxon_name not in (select taxon_name from taxon where status = 'valid')";
            stmt.executeUpdate(query);

            query = "update proj_taxon set source = '" + Source.CURATOR + "' where project_name = 'introducedants'";
            stmt.executeUpdate(query);

            stmt.close();
        } catch (SQLException e) {
          s_log.error("fixExceptions() e:" + e + " query:" + query);
        }
     }
    
     // These methods populate Proj_taxons.
     // Get all of the species for a project, and use the species image counts to pupulate
     // the various ranks with summarized image counts
     public void imageCountCrawl() 
       throws SQLException {
          // These two are the same first two steps of startImageCountCrawl()
          if (false) {
            //countSpecimenImages();
            //countSpeciesImages();
          }
                
          //String project = "tokelauants";  
          ArrayList<Project> projects = ProjectMgr.getLiveProjects();
          for (Project project : projects) {

             //if (AntwebProps.isDevMode()) s_log.info("startProjTaxonImageCountCrawl() project:" + project);
             imageCountCrawl(project);
          }
     }

}    
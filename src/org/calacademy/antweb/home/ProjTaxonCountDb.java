 package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/**
 * Count crawls of the project species lists (proj_taxon); the counting code is in CountDb. childrenCountCrawl
 * and recount: specimens, species, genera and subfamilies of each row (worldants after the AntCat import, set 1;
 * every live curated project, curatedProjectsCountCrawl, set 3). imageCountCrawl: images per name of every
 * project list and of allantwebants (set 4; allAntwebImageCount after each specimen upload). dropNumbers: the
 * Port of Entry list keeps no numbers. Docs: docs/flows/nightly.md, docs/numbers.md.
 * Tests: TaxonTreeCountDbTest, PlaceImageCountDbTest, NumberDecisionsDbTest, CountCheckTest.
 */
 public class ProjTaxonCountDb extends CountDb {
    
    private static final Log s_log = LogFactory.getLog(ProjTaxonCountDb.class);
        
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

     public void childrenCountCrawl() throws SQLException {
         s_log.warn("childrenCountCrawl()");
          ArrayList<Project> projects = ProjectMgr.getLiveProjects();
          for (Project project : projects) {            
             if (project.isSpeciesListOnly()) continue;   // Port of Entry: no specimen numbers (2026-10-22)
             childrenCountCrawl(project);
          }
          fixExceptions();
     }

    /**
     * Nightly (set 3, utilData.do?action=projectCountCrawl): the counts on the species lists of every
     * live project except worldants and allantwebants (those two are recounted by the AntCat import).
     * Until 2026-10 the nightly run recounted only the project totals (ProjectDb.updateCounts), so the
     * per-taxon counts of curated project lists (specimens of a species, species of a genus...) were
     * only recounted when a curator saved the list or pressed Recalculate, and went stale; the count
     * check (countCheck.do) found 109 such species in project atolants on staging. The count columns
     * are set to 0 first, so a taxon whose specimens are gone shows 0, not its old count (the crawl
     * only writes taxa it finds). Same transaction as the rest of set 3, so pages never see the zeros.
     */
    public String curatedProjectsCountCrawl() throws SQLException {
        int n = 0;
        for (Project project : ProjectMgr.getLiveProjects()) {
            if (project == null || Project.WORLDANTS.equals(project.getName()) || Project.ALLANTWEBANTS.equals(project.getName())) continue;
            recount(project);   // the Port of Entry project: its numbers are dropped, not counted (recount)
            ++n;
        }
        String message = "Project species-list counts crawled: " + n + " projects";
        LogMgr.appendLog("compute.log", "  " + message, true);
        return message;
    }

    /** SQL that sets a project list's count columns to 0 before the crawl (2026-10-21: subfamily_count too;
     *  the crawl writes it on the formicidae row, other rows kept old values). */
    public static final String RESET_SQL = "update proj_taxon set specimen_count = 0, species_count = 0, genus_count = 0, subfamily_count = 0 where project_name = ?";

    /**
     * One project list: rows of synonyms renamed to (merged into) their valid name (2026-10-22: set 3 counted the
     * projects before its rename; worldants and allantwebants are not renamed), counts set to 0, then counted again
     * from the specimens (a synonym's specimens under the valid name, Project.getCountSpecimensQuery).
     */
    public void recount(Project project) throws SQLException {
        if (project.isSpeciesListOnly()) {
            dropNumbers(project.getName());
            return;
        }
        if (PlaceTotals.synonymsToValid(PlaceTotals.PROJECT, project.getName())) {
            new ProjTaxonDb(getConnection()).renameToValid(PlaceTotals.PROJECT, project.getName());
        }
        try (PreparedStatement stmt = getConnection().prepareStatement(RESET_SQL)) {
            stmt.setString(1, project.getName());
            stmt.executeUpdate();
        }
        childrenCountCrawl(project);
    }

    /**
     * The Port of Entry project (Project.isSpeciesListOnly, Brian Fisher 2026-10-22) is a species list only: its rows'
     * specimen, image and child counts are set to 0 and never counted (they counted every AntWeb specimen of the species,
     * not interceptions). The list itself is kept.
     */
    public void dropNumbers(String projectName) throws SQLException {
        try (PreparedStatement stmt = getConnection().prepareStatement(RESET_SQL)) {
            stmt.setString(1, projectName);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = getConnection().prepareStatement("update proj_taxon set image_count = 0 where project_name = ?")) {
            stmt.setString(1, projectName);
            stmt.executeUpdate();
        }
    }

    public void childrenCountCrawl(String projectName) throws SQLException {
        Project project = ProjectMgr.getProject(projectName);
        A.log("childrenCountCrawl:" + project);
        if (project != null && project.isSpeciesListOnly()) {
            dropNumbers(projectName);
        } else if (project != null) {
            childrenCountCrawl(project);
        } else {
            s_log.warn("childrenCountCrawl() project not found:" + projectName);
        }
    }

    // ------------------------- Project Taxon Image Count Crawl -------------------------

     private void fixExceptions() {
       s_log.debug("fixExceptions()");
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
                
          // Every row starts at 0 (2026-10-21), as for the other lists: the crawl writes only names with
          // specimens, so a project row whose images were deleted kept its old Images number.
          resetImageCounts("proj_taxon");

          //String project = "tokelauants";  
          // The AntWeb-wide list is counted even when the project cache has no allantwebants project (staging
          // 2026-10-28: getProject returned null there, the list was silently skipped and every row stayed 0 while
          // compute.log said it was counted). Its rows need only the list's name.
          Project allAntweb = allAntwebProject();
          boolean countedAllAntweb = false;
          for (Project project : imageCrawlProjects(ProjectMgr.getLiveProjects(), allAntweb)) {
             //if (AntwebProps.isDevMode()) s_log.info("startProjTaxonImageCountCrawl() project:" + project);
             imageCountCrawl(project);
             if (Project.ALLANTWEBANTS.equals(project.getName())) countedAllAntweb = true;
          }
          LogMgr.appendLog("compute.log", "  " + (countedAllAntweb ? ALLANTWEB_IMAGES_LOG : "AntWeb-wide list Images NOT counted (allantwebants missing)"), true);
     }

     /** The allantwebants list (only its name is needed), also when the project cache has none. */
     static Project allAntwebProject() {
          Project allAntweb = ProjectMgr.getProject(Project.ALLANTWEBANTS);
          if (allAntweb == null || !Project.ALLANTWEBANTS.equals(allAntweb.getName())) {
             allAntweb = new Project();
             allAntweb.setName(Project.ALLANTWEBANTS);
          }
          return allAntweb;
     }

     /**
      * Counts the Images of the AntWeb-wide list (allantwebants) only. Called by ProjTaxonDb.regenerateAllAntweb, which
      * every specimen upload runs: it deletes and re-inserts every allantwebants row with image_count 0, so until the
      * next nightly set 4 every list's Images column (the AntWeb-wide number) read 0 after any upload (staging
      * 2026-10-28: 32,247 rows, none with images, after the e2e uploads).
      */
     public void allAntwebImageCount() throws SQLException {
          imageCountCrawl(allAntwebProject());
     }

     /** Written to compute.log by nightly set 4 once the allantwebants rows' Images (the first number of every list's
      *  Images column) are counted with the other lists (2026-10-27). */
     public static final String ALLANTWEB_IMAGES_LOG = "AntWeb-wide list Images counted (allantwebants), 2026-10-27";

     /**
      * The lists whose Images set 4 counts: the live projects (not the Port of Entry, which has no image numbers,
      * 2026-10-22) and, once, allantwebants. The allantwebants rows are set to 0 with the other project rows
      * (resetImageCounts) but it is not a live project, so until 2026-10-27 nothing counted them again and they stayed 0
      * (staging, PR #291): every list's AntWeb-wide Images number (TaxonSet.globalImageCountColumn) read 0.
      */
     public static ArrayList<Project> imageCrawlProjects(java.util.Collection<Project> live, Project allAntweb) {
          ArrayList<Project> out = new ArrayList<>();
          boolean hasAllAntweb = false;
          if (live != null) {
              for (Project project : live) {
                  if (project == null || project.isSpeciesListOnly()) continue;
                  if (Project.ALLANTWEBANTS.equals(project.getName())) {
                      if (hasAllAntweb) continue;
                      hasAllAntweb = true;
                  }
                  out.add(project);
              }
          }
          if (!hasAllAntweb && allAntweb != null) out.add(allAntweb);
          return out;
     }

}    
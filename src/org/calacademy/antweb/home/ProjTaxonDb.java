package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * The project species lists (table proj_taxon), including worldants (AntCat), allantwebants (every name with
 * AntWeb specimens, the Global column of every list) and fossilants. Reads a project's list and per-project
 * statistics (statistics.do); inserts, updates and deletes rows for curators (Species List Tool, through
 * SpeciesListDb), the AntCat import and the specimen upload; regenerateAllAntweb rebuilds allantwebants after each
 * upload and in set 1; nightly set 5 cleans up proxy and empty genus rows. Tables proj_taxon, proj_taxon_dispute,
 * specimen, taxon. Docs: docs/flows/nightly.md, docs/flows/antcat-to-antweb.md, docs/numbers.md.
 * Tests: CuratorTaxonSetTest, tests/e2e/test_curator_species_lists.py.
 */
public class ProjTaxonDb extends EditableTaxonSetDb {
    
    private static final Log s_log = LogFactory.getLog(ProjTaxonDb.class);
        
    public ProjTaxonDb(Connection connection) {
      super(connection);
    }
    
// ----------------- EditableTaxonSet methods ------------------------

    // Satisfies the EditableTaxonSetDb abstract method.
    public ProjTaxon get(String projectName, String taxonName) {
        String query;
        ProjTaxon projTaxon = null;
        Statement stmt = null;
        ResultSet rset;
        try {

            stmt = DBUtil.getStatement(getConnection(), "getTaxonSet()");
            query = "select project_name, taxon_name, source, rev " 
               + " , subfamily_count, genus_count, species_count, specimen_count, image_count " // Added Mar 2, 2019
               + " from proj_taxon " 
               + " where project_name = " + DBUtil.sqlQuote(projectName)
               + " and taxon_name = " + DBUtil.sqlQuote(taxonName);

            rset = stmt.executeQuery(query);
            while (rset.next()) {
                projTaxon = new ProjTaxon();
                projTaxon.setProjectName(rset.getString("project_name"));
                projTaxon.setTaxonName(rset.getString("taxon_name"));
                projTaxon.setSource(rset.getString("source"));
                projTaxon.setRev(rset.getInt("rev"));
                projTaxon.setSubfamilyCount(rset.getInt("subfamily_count"));
                projTaxon.setGenusCount(rset.getInt("genus_count"));
                projTaxon.setSpeciesCount(rset.getInt("species_count"));
                projTaxon.setSpecimenCount(rset.getInt("specimen_count"));
                projTaxon.setImageCount(rset.getInt("image_count"));
           }

          //if (taxonName.contains("myrmicinaepheidole minima catella")) A.log("get() taxonName:" + taxonName + " childCount:" + projTaxon.getGlobalChildCount());

          // A.log("getTaxonSet() query:" + query);

        } catch (SQLException e) {
            s_log.error("e:" + e);
        } finally {
            DBUtil.close(stmt, "getTaxonSet()");
        }
        return projTaxon;
    }

    /** Gets the source for a specified taxonName
     *
     * @param projectName The project to search within
     * @param taxonName The taxonName to search for
     * @return The source of the taxonName if present, null if not found
     */
    public String getTaxonSource(String projectName, String taxonName) {

        String query = "select source from proj_taxon where project_name = ? and taxon_name = ?";
        PreparedStatement stmt = null;
        ResultSet rset;

        String source = null;

        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "getProjTaxonSource()", query);

            stmt.setString(1, projectName);
            stmt.setString(2, taxonName);

            rset = stmt.executeQuery();

            if (rset.next()) {
                source = rset.getString(1);
            }

        } catch (SQLException e) {
            s_log.error("e:" + e);
        } finally {
            DBUtil.close(stmt, "getProjTaxonSource()");
        }

        return source;

    }

    public boolean exists(String project, String taxonName) {
        TaxonSet taxonSet = get(project, taxonName);
        return taxonSet != null;
    }

    public int insert(String projectName, String taxonName, String source) throws SQLException {
      Project project = ProjectMgr.getProject(projectName);
      return insert(project, taxonName, source);
    }
    
    // Satisfies the EditableTaxonSetDb abstract method.
    // Recursive. Will insert records for all taxon parents.
    public int insert(Overview overview, String taxonName, String source) throws SQLException {
      Project project = (Project) overview;
      
      if ("".equals(source) || source == null) s_log.warn("insert() project:" + project + " taxonName:" + taxonName);

      // it is a genus or subfamily. Record it in the list so that we can efficiently avoid re-queries.
      // String key = project.getProjectName() + taxonName;
      // s_queryGovernor must be set in the calling method. As such:
      //   s_queryGovernor = new HashSet<String>();
      // if (s_queryGovernor.contains(key)) return 0;
      // s_queryGovernor.add(key);

      int insertCount = 0;    
      
      try {
		  insertCount = insertItem(project, taxonName, source);
		  
		  if ("formicidae".equals(taxonName)) {
			//A.log("insert() formicidae. name:" + geolocale.getName() + " id:" + geolocale.getId() + " insertCount:" + insertCount);
			return insertCount;
		  }
	  
		  String parentTaxonName = Taxon.getParentTaxonNameFromName(taxonName);
		  if (parentTaxonName == null || parentTaxonName.equals(taxonName)) {
			return insertCount;
		  }

	      if (!source.contains(TaxonSet.PROXY)) source = TaxonSet.PROXY + source;

    /*
    Possibly we want to be sure that the taxon doesn't have a current valid name. If it does
    we don't want to create a parent. See http://localhost/antweb/query.do?name=projTaxaWithoutTaxon
    */

		  insertCount += insert(project, parentTaxonName, source);

      } catch (Exception e) {
        s_log.warn("insert() overview:" + overview + " taxonName:" + taxonName + " source:" + source + " e:" + e);
        if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace(8);
        throw e;
      }
      
      return insertCount;
    }
    
    public int update(Overview overview, String taxonName, String source) throws SQLException {
      Project project = (Project) overview;
      
      //A.log("insert() project:" + project + " taxonName:" + taxonName);

      int updateCount = 0;    
      
      try {
		  updateCount = updateItem(project.getName(), taxonName, source);

		  if ("formicidae".equals(taxonName)) {
			//A.log("insert() formicidae. name:" + geolocale.getName() + " id:" + geolocale.getId() + " insertCount:" + insertCount);
			return updateCount;
		  }
	  
		  String parentTaxonName = Taxon.getParentTaxonNameFromName(taxonName);
		  if (parentTaxonName == null || parentTaxonName.equals(taxonName)) {
			return updateCount;
		  }

	      if (!source.contains(TaxonSet.PROXY)) source = TaxonSet.PROXY + source;

		  updateCount += update(project, parentTaxonName, source);

      } catch (Exception e) {
        s_log.warn("update() overview:" + overview + " taxonName:" + taxonName + " source:" + source + " e:" + e);
        if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace(8);
        throw e;
      }
      
      return updateCount;
    }
        
    // -----------------  Population Support methods ------------------------

    // Should only be used by the above recursive method. Will update if already existing.
    private int insertItem(Project project, String taxonName, String source) 
      throws SQLException {

        String projectName = project.getName();

        // We can't insert. Already exists. Update source if priority allows.
        String existingSource = getTaxonSource(projectName, taxonName);
		if (existingSource != null) {
            if (Source.aTrumpsB(source, existingSource)) {
              update(project, taxonName, source);  
            }
            return 0;
        }

        // No source exists, will insert this one
        String dml = "insert into proj_taxon (project_name, taxon_name, source) values (?, ?, ?)";
        PreparedStatement stmt = null;
        int count;
        try {
            stmt = DBUtil.getPreparedStatement(getConnection(), "insertItem()", dml);

            stmt.setString(1, projectName);
            stmt.setString(2, taxonName);
            stmt.setString(3, source);

            count = stmt.executeUpdate();
	    } catch (SQLException e) {
          //A.log("insertItem() e:" + e + " source:" + source);
          throw e;
        } finally {
            DBUtil.close(stmt, "insertItem()");
        }
        return count;   		
	}

    public boolean hasItem(String projectName, String taxonName) throws SQLException {
        String query = "";
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasItem()");
            query = "select 'x' as x from proj_taxon"
               + " where project_name = " + DBUtil.sqlQuote(projectName)
               + " and taxon_name = " + DBUtil.sqlQuote(taxonName);
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String x = (String) rset.getObject("x");
                return true;
            }
        } catch (SQLException e) {
            s_log.error("hasItem() projectName:" + projectName + " taxonName:" + taxonName);
        } finally {
            DBUtil.close(stmt, rset, "hasItem()");
        }
        //A.log("hasItem() exists:" + exists + " query:" + query);
        return false;    
    }

    // Should only be used by the recursive method above.
    private int updateItem(String projectName, String taxonName, String source) 
        throws SQLException {
        String dml = null;
        Statement stmt = null;
        int count = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "updateItem()");

            dml = "update proj_taxon set source = " + DBUtil.sqlQuote(source) + " where project_name = " + DBUtil.sqlQuote(projectName)
              + " and taxon_name = " + DBUtil.sqlQuote(taxonName);
            count = stmt.executeUpdate(dml);
	    } catch (SQLException e) {
          s_log.debug("updateItem e:" + e + " source:" + source);
          throw e;
        } finally {
            DBUtil.close(stmt, "updateItem()");
        }
        return count;      
	}	
    
/*
  Called from SpeciesListMapping on the save function.
  When removing a taxon from proj_taxon, if it is a morpho, if it is removed from the only
  project it is in (other than allantwebants) then delete the taxon too (if it has no specimens).
  Allow the description_edit to be orphaned. 
*/
    // Satisfies the EditableTaxonSetDb abstract method.
    public int delete(String projectName, String taxonName) throws SQLException {
      int c = 0;
      
      String delete = null;
      ResultSet rset1 = null;
      Statement stmt1 = null;
      try {
        stmt1 = DBUtil.getStatement(getConnection(), "delete()");

        delete = "delete from proj_taxon " 
          + " where project_name = " + DBUtil.sqlQuote(projectName) 
          + "   and taxon_name = " + DBUtil.sqlQuote(taxonName); 
        //A.log("delete() 1 delete:" + delete);
        c = stmt1.executeUpdate(delete);

        // if taxon is in no other project than allantweb, and has no specimens!? 
        // and is not in worldants, and is not valid, then delete the taxon outright, 
        // and the allantweb projTaxon record.
        
        String rank = Taxon.getRankFromName(taxonName);     
           
        if (rank == null) return c;   // not a name of a known rank: the row is removed, nothing else
        boolean isSubfamily = rank.equals(Rank.SUBFAMILY);
        boolean hasOtherProjects = getProjectCount(taxonName) > 0;
        boolean hasSpecimen = getSpecimenCount(taxonName) > 0;
        
        s_log.debug("delete() taxonName:" + taxonName + " isSubfamily:" + isSubfamily + " hasOtherProjects:" + hasOtherProjects + " hasSpecimen:" + hasSpecimen);

        if ("incertae_sedis".equals(taxonName)) s_log.debug("delete() EXISTS:" + exists("fossilants", taxonName));

        if (isSubfamily) return c;
        if (hasOtherProjects) return c;

        if (!"genus".equals(rank) && hasSpecimen) return c;
        
        // Not while a place, bioregion or museum list still holds it (deleteTaxon removes every project row).
        if (Taxon.isMorpho(taxonName) && !new TaxonDb(getConnection()).isOnAnyList(taxonName)) {
			// So we will delete the taxon outright.
			TaxonDb taxonDb = new TaxonDb(getConnection());
			taxonDb.deleteTaxon(taxonName);
        }
        
      } catch (SQLException e) {
        s_log.error("delete() projectName:" + projectName + " e:" + e + " delete:" + delete);
      } finally {
        DBUtil.close(stmt1, rset1, "this", "delete()");
      }   
      return c;
    }    
    
    // Satisfies the TaxonSetDb abstract method.
    String updateTaxonNames() throws SQLException {
      // For each of the following proj_taxon, update the taxon_name with the current_valid_name.
    
      Statement stmt = null;
      ResultSet rset = null;
	  String taxonName = null;
	  String currentValidName = null;
	  String projectName = null;
	  String tableName = null;
	  String whereClause = null;   
	  int c = 0;   
      try {
          
          String query = "select p.project_name, t.taxon_name, t.status, t.current_valid_name " 
            + " from taxon t, proj_taxon pt, project p where t.taxon_name = pt.taxon_name and pt.project_name = p.project_name " 
            + " and t.status != 'valid' and t.current_valid_name is not null and p.project_name != 'allantwebants' " 
            + " and p.project_name != 'worldants' order by project_name, taxon_name";

          stmt = DBUtil.getStatement(getConnection(), "updateTaxonNames()");
          rset = stmt.executeQuery(query);
          while (rset.next()) {
            taxonName = rset.getString("taxon_name");
            currentValidName = rset.getString("current_valid_name");
            projectName = rset.getString("project_name");
            
            tableName = "proj_taxon";
            whereClause = "project_name = " + DBUtil.sqlLiteral(projectName);

            c += updateTaxonSetTaxonName(tableName, taxonName, currentValidName, whereClause);
          }
   
      } catch (SQLException e) {
        s_log.error("updateTaxonNames() e:" + e + " tableName:" + tableName + " projectName:" + projectName + " whereClause:" + whereClause);
        throw e;
      } finally {
        DBUtil.close(stmt, rset, "updateTaxonNames()");
      }
      return c + " Project Taxon Names updated to current valid Taxon Names.  ";
    }

    // Satisfies the EditableTaxonSetDb abstract method.
    public boolean hasTaxonSetSpecies(String speciesListName, String genus) throws SQLException {
        String fromWhereClause = "from proj_taxon where project_name = " + DBUtil.sqlQuote(speciesListName);
		//A.log("ProjTaxonDb.hasTaxonSetSpecies() fromWhereClause:" + fromWhereClause);
        return super.hasTaxonSetSpecies(speciesListName, genus, fromWhereClause);
    }
    // Satisfies the EditableTaxonSetDb abstract method.
    public boolean hasTaxonSetGenera(String speciesListName, String subfamily) throws SQLException {
        String fromWhereClause = "from proj_taxon where project_name = " + DBUtil.sqlQuote(speciesListName);
		//A.log("ProjTaxonDb.hasTaxonSetGenera() fromWhereClause:" + fromWhereClause);
        return super.hasTaxonSetGenera(speciesListName, subfamily, fromWhereClause);
    }


    String deleteUncuratedMorphosWithoutSpecimen() throws SQLException {
        return deleteUncuratedMorphosWithoutSpecimen(BlankNameCleanup.isOn());
    }

    public String deleteUncuratedMorphosWithoutSpecimen(boolean blankSafe) throws SQLException {
        // Not the curators' rows (as in GeolocaleTaxonDb): a curator's morphospecies stays while its
        // name exists; without specimens the name itself is removed and the row is parked
        // (CuratorTaxonSetDb). blankSafe: BlankNameCleanup (2026-10-29).
        String dml = "delete from proj_taxon where taxon_name in (select taxon_name from taxon t where t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon' and "
          + BlankNameCleanup.morphoNoSpecimen(blankSafe) + ")"
          + " and source not in " + CuratorTaxonSetDb.curatorSourceList();
        // Brian Fisher, 2026-11-03 (transparency): the rows are recorded before they go (ListRemovalLog), same condition
        ListRemovalLog.recordWhere(getConnection(), ListRemovalLog.PROJECT, "l.taxon_name in (select t.taxon_name from taxon t where"
            + " t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon' and " + BlankNameCleanup.morphoNoSpecimen(blankSafe) + ")"
            + " and l.source not in " + CuratorTaxonSetDb.curatorSourceList(), ListRemovalLog.JOB_MORPHO_NO_SPECIMEN, ListRemovalLog.REASON_MORPHO_NO_SPECIMEN);
        Statement stmt = null;
        int c = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteUncuratedMorphosWithoutSpecimen()");
            c = stmt.executeUpdate(dml);            
        } catch (SQLException e) {
            s_log.error("deleteUncuratedMorphosWithoutSpecimen() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "deleteUncuratedMorphosWithoutSpecimen()");
        }
        String message = "ProjTaxonDb.deleteUncuratedMorphosWithoutSpecimen:" + c   ;
        return message;
    }

    public String cleanupSpeciesListProxyRecords() throws SQLException {
        String result = null;
        String query = "select pt.taxon_name taxonName, pt.project_name projectName from proj_taxon pt where pt.source = 'proxyspeciesListTool'";
        // For each proxyspeciesListTool find if it has children.

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "cleanupSpeciesListProxyRecords()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String taxonName = rset.getString("taxonName");
                String projectName = rset.getString("projectName");
                if (hasNoChildren(taxonName, projectName)) {
                    A.log("cleanupSpeciesListProxyRecords() taxonName:" + taxonName + " projectName:" + projectName);
                    result = deleteProjTaxon(taxonName, projectName, " and source = 'proxyspeciesListTool'",
                        ListRemovalLog.JOB_PROXY_NO_CHILDREN, ListRemovalLog.REASON_PROXY_NO_CHILDREN);
                }
            }
        } catch (SQLException | RuntimeException e) {
            s_log.error("cleanupSpeciesListProxyRecords() e:" + e);
            cleanupErrors.add("proxy rows: " + e);
        } finally {
            DBUtil.close(stmt, rset, "cleanupSpeciesListProxyRecords()");
        }
        return result;
    }

    private boolean hasNoChildren(String taxonName, String projectName) throws SQLException {
        int count = 0;
        String query = "select pt.taxon_name, pt.project_name from proj_taxon pt where pt.project_name = " + DBUtil.sqlQuote(projectName)
            + " and pt.taxon_name in (select taxon_name from taxon where parent_taxon_name = " + DBUtil.sqlQuote(taxonName) + ")";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasNoChildren()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                count = count + 1;
            }
        } catch (SQLException e) {
            // An error is not "no children": the callers delete the row when the answer is true.
            s_log.error("hasNoChildren() projectName:" + projectName + " taxonName:" + taxonName + " e:" + e);
            return false;
        } finally {
            DBUtil.close(stmt, rset, "hasNoChildren()");
        }
        return count == 0;
    }


    /**
     * The projects whose list lost a row in this object's clean-ups (cleanupSpeciesListProxyRecords, verifyProjTaxon), in
     * the order found, each with its rows removed. In the daily update the clean-ups run in set 2 before any list is
     * counted (cleanupLists, since 2026-11-12). Run by hand as an admin job (utilData.do?action=verifyProjTaxon,
     * cleanupSpeciesListProxyRecords) they remove rows after the counts, so that caller counts these projects' list
     * totals again (UtilDataAction.listTotalsAfterRowsRemoved).
     */
    private final java.util.Map<String, Integer> rowsRemoved = new java.util.LinkedHashMap<>();

    /** The names of those rows, per project, in the order removed (the clean-up's line names them). */
    private final java.util.Map<String, List<String>> namesRemoved = new java.util.LinkedHashMap<>();

    /** How many removed rows of one project the line names; the rest is "and N more". */
    static final int NAMES_PER_PROJECT = 10;

    /**
     * The rows this object's clean-ups removed, by project and name: "fossilants 1: formicinaeleucotaphus; madants 12:
     * a, b, ... and 2 more" (up to NAMES_PER_PROJECT names per project); "" when none. Brian Fisher, 2026-11-03: no job
     * deletes list entries without a record that can be read.
     */
    public String removedRowsText() {
        List<String> parts = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : rowsRemoved.entrySet()) {
            List<String> names = namesRemoved.getOrDefault(e.getKey(), new ArrayList<>());
            int shown = Math.min(names.size(), NAMES_PER_PROJECT);
            parts.add(e.getKey() + " " + e.getValue() + ": " + String.join(", ", names.subList(0, shown))
                + (e.getValue() > shown ? " and " + (e.getValue() - shown) + " more" : ""));
        }
        return String.join("; ", parts);
    }

    /** What failed in this object's clean-ups (they log a database error and go on), for cleanupLists' line. */
    private final List<String> cleanupErrors = new ArrayList<>();

    public Set<String> getProjectsWithRowsRemoved() {
        return rowsRemoved.keySet();
    }

    /** Whether one of this object's clean-ups met a database error (its rows may be partly removed). */
    public boolean cleanupFailed() {
        return !cleanupErrors.isEmpty();
    }

    /** Those errors: "proxy rows: ...; genus rows: ...". */
    public String cleanupErrorsText() {
        return String.join("; ", cleanupErrors);
    }

    /** Starts the line cleanupLists returns (compute.log, written by the daily update's set 2). */
    public static final String CLEANUP_LOG = "Project lists cleaned";

    /**
     * The two clean-ups that take rows off project lists, for the daily update: a proxy row of the Species List Tool
     * whose children are gone from the list (cleanupSpeciesListProxyRecords), and a genus row of any project but the
     * AntCat list that has neither a species row on the list nor, on the AntWeb-wide list, a specimen (verifyProjTaxon;
     * the rule of 2026-11-02, removeGenusRowsWithoutSpeciesOrSpecimen). Set 2 calls it right after the data clean-up and
     * before the first count of a project list, so every number the daily update stores afterwards is counted from the
     * lists as they stay.
     *
     * Until 2026-11-12 the two ran in set 5, production's last set, after set 3 had stored the project totals and set 4
     * the statistics row: the rows they removed were still in those numbers, and the count check reported them every
     * day. Production 2026-11-12, 6 rows removed: "Genera of project pages = genus rows on the list" fossilants 259
     * stored, 258 listed, and allantwebants 536 and 535; statistics row proj_taxa 70,419, 70,413 by a recount. Staging
     * skips set 5, so it never ran them and never showed it.
     *
     * A genus row that loses its last species later in the same run (a synonym's row renamed to a valid name of another
     * genus: set 2's rename of the lists, set 3's rename before each project is counted) stays until the next daily
     * update, on the list and in the numbers alike.
     *
     * Every row removed is recorded in list_removal_log (ListRemovalLog; Brian Fisher, 2026-11-03: no job deletes list
     * entries without a record a curator can see), every one, also beyond the names the line shows. Returns the line for
     * compute.log, which names the rows removed, by project (administrators read compute.log in the Log Manager, logMgr.do):
     * "Project lists cleaned: 6 rows removed in 3 s; fossilants 1: formicinaeleucotaphus; madants 5: a, b, c, d, e";
     * a project with more than NAMES_PER_PROJECT rows ends "... and N more"; "Project lists cleaned: 0 rows removed in
     * 2 s". Never throws; a database error in one of the two is at the end of the line: "...; FAILED: genus rows: ..."
     * (the rows removed before it stay removed, in the set's transaction).
     */
    public String cleanupLists() {
        long start = System.currentTimeMillis();
        try {
            cleanupSpeciesListProxyRecords();
        } catch (SQLException | RuntimeException e) {
            s_log.error("cleanupLists() proxy rows e:" + e);
            cleanupErrors.add("proxy rows: " + e);
        }
        try {
            verifyProjTaxon();
        } catch (SQLException | RuntimeException e) {
            s_log.error("cleanupLists() genus rows e:" + e);
            cleanupErrors.add("genus rows: " + e);
        }
        int rows = 0;
        for (int n : rowsRemoved.values()) rows += n;
        String named = removedRowsText();
        return CLEANUP_LOG + ": " + rows + " rows removed in " + (System.currentTimeMillis() - start) / 1000 + " s"
            + (named.isEmpty() ? "" : "; " + named)
            + (cleanupErrors.isEmpty() ? "" : "; FAILED: " + String.join("; ", cleanupErrors));
    }

    /**
     * Deletes one row of a project list for the clean-ups. job, reason: the record of the removal (ListRemovalLog, written
     * just before the delete by the same condition; Brian Fisher, 2026-11-03: no job deletes list entries without a
     * record a curator can see); null job: no record (a row the rebuild itself had just put on the list).
     */
    private String deleteProjTaxon(String taxonName, String projectName, String extraClause, String job, String reason) throws SQLException {
        String where = " where taxon_name = " + DBUtil.sqlQuote(taxonName)
            + " and project_name = " + DBUtil.sqlQuote(projectName)
            + (extraClause != null ? extraClause : "");
        if (job != null) {
            // not the same record day after day for a row the AntCat import puts back every day (recordWhereOnce)
            ListRemovalLog.recordWhereOnce(getConnection(), ListRemovalLog.PROJECT, "l.taxon_name = " + DBUtil.sqlQuote(taxonName)
                + " and l.project_name = " + DBUtil.sqlQuote(projectName) + (extraClause != null ? extraClause : ""), job, reason);
        }
        String dml = "delete from proj_taxon " + where;
        Statement stmt = null;
        int c = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteProjTaxon()");
            c = stmt.executeUpdate(dml);
            if (c > 0 && projectName != null) {
                rowsRemoved.merge(projectName, c, Integer::sum);
                namesRemoved.computeIfAbsent(projectName, k -> new ArrayList<>()).add(taxonName);
            }
        } catch (SQLException e) {
            s_log.error("deleteProjTaxon() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "deleteProjTaxon()");
        }
        String message = "deleteProjTaxon:" + c;
        return message;
    }


    /**
     * The rule of the genus rows of a list (agreed with Brian Fisher, 2026-11-02, claude/antweb-list-design-rules.md):
     * "A genus stays on a place's list only with a species row of that genus or a specimen of it there." It holds at
     * every moment, not once a day, so the code that builds a list and the code that cleans it read it from this one
     * method: the daily clean-up of every project list (verifyProjTaxon, set 2) and the rebuild of the AntWeb-wide list
     * at each specimen upload and after the name update (rebuildAllAntwebList).
     *
     * A species row of the genus: a row of the same list whose name has the genus row as parent in the taxon tree
     * (taxon.parent_taxon_name, as the counts and ParentRows read the tree): species, subspecies, and the name of
     * specimens identified only to genus, "<Genus> (indet)", which is such a row.
     * A specimen of it there: a project list is no place; its specimens are those filed under the names on the list
     * (Project.getCountSpecimensQuery), and those names are the rows above, so for a project the first half says it
     * all and no specimen is asked for. The AntWeb-wide list (allantwebants) is all of AntWeb: there a genus also stays
     * while AntWeb has a specimen of it (specimen.genus and subfamily), although no name of the genus is on the list
     * (its specimens are filed under names of a status the list leaves out, a synonym for example). A red-flagged
     * specimen is no such specimen ("A red-flagged specimen never counts, in any view, on any list",
     * claude/antweb-list-design-rules.md; SpecimenDb.notRedFlagged, the condition of the lists).
     *
     * Removes the genus rows of the project's list that have neither, and returns how many. One query for the list
     * (GENUS_ROWS_WITHOUT_SPECIES_SQL), one small query per such genus on the AntWeb-wide list (antwebSpecimenOfGenusSql).
     * Every row removed is recorded (ListRemovalLog, JOB_GENUS_NO_SPECIES: Brian Fisher, 2026-11-03, no job deletes
     * list entries without a record a curator can see) and kept for the clean-up's line (rowsRemoved, namesRemoved).
     * In the caller's transaction.
     */
    public int removeGenusRowsWithoutSpeciesOrSpecimen(String projectName) throws SQLException {
        return removeGenusRows(projectName, ListRemovalLog.JOB_GENUS_NO_SPECIES, null);
    }

    /**
     * The rule for one list. job: the record's job name; recordOnly: when not null, only the genus rows named in it are
     * recorded (the rebuild of the AntWeb-wide list: the rows that were on the list before it; a row the rebuild put on
     * the list a moment ago, in the same transaction, was never a list entry anyone saw).
     */
    private int removeGenusRows(String projectName, String job, Set<String> recordOnly) throws SQLException {
        List<String[]> candidates = new ArrayList<>();   // genus row's name, subfamily, genus
        try (PreparedStatement stmt = getConnection().prepareStatement(GENUS_ROWS_WITHOUT_SPECIES_SQL)) {
            stmt.setString(1, projectName);
            stmt.setString(2, projectName);
            try (ResultSet r = stmt.executeQuery()) {
                while (r.next()) candidates.add(new String[] {r.getString(1), r.getString(2), r.getString(3)});
            }
        }
        int removed = 0;
        for (String[] genus : candidates) {
            if (Project.ALLANTWEBANTS.equals(projectName) && antwebHasSpecimenOf(genus[1], genus[2])) continue;
            boolean record = recordOnly == null || recordOnly.contains(genus[0]);
            deleteProjTaxon(genus[0], projectName, null, record ? job : null, ListRemovalLog.REASON_GENUS_NO_SPECIES);
            ++removed;
        }
        return removed;
    }

    /**
     * The genus rows of one project's list (two parameters: the project name, twice) with no row on the same list whose
     * name has them as parent: the candidates of the rule above. One pass over the list's rows.
     */
    public static final String GENUS_ROWS_WITHOUT_SPECIES_SQL =
        "select pt.taxon_name, t.subfamily, t.genus from proj_taxon pt"
        + " join taxon t on t.taxon_name = pt.taxon_name and t.taxarank = 'genus'"
        + " left join (select distinct ct.parent_taxon_name p from proj_taxon c join taxon ct on ct.taxon_name = c.taxon_name"
        + "   where c.project_name = ? and ct.parent_taxon_name is not null) k on k.p = pt.taxon_name"
        + " where pt.project_name = ? and k.p is null order by pt.taxon_name";

    /**
     * "select 1 ... limit 1": a specimen of the genus (parameter 1) and, withSubfamily, of the subfamily (parameter 2)
     * that is not red-flagged. Read through the index genus_species_idx (genus, species) of the specimen table
     * (docs/data-model.md, "Indexes and the place queries"): the genus is its first column, so the query reads the
     * specimens of that one genus and stops at the first that fits; no scan of the table. Asked once per genus row
     * without a species row on the AntWeb-wide list, at each rebuild of that list (every specimen upload): a handful
     * of genera. Test: ListCleanupOrderDbTest (EXPLAIN).
     */
    public static String antwebSpecimenOfGenusSql(boolean withSubfamily) {
        return "select 1 from specimen s where s.genus = ?" + (withSubfamily ? " and s.subfamily = ?" : "")
            + " and " + SpecimenDb.notRedFlagged("s") + " limit 1";
    }

    /** Whether AntWeb has a specimen of the genus (its genus and, when the genus row has one, its subfamily), not red-flagged. */
    private boolean antwebHasSpecimenOf(String subfamily, String genus) throws SQLException {
        if (genus == null || genus.isEmpty()) return false;
        try (PreparedStatement stmt = getConnection().prepareStatement(antwebSpecimenOfGenusSql(subfamily != null))) {
            stmt.setString(1, genus);
            if (subfamily != null) stmt.setString(2, subfamily);
            try (ResultSet r = stmt.executeQuery()) {
                return r.next();
            }
        }
    }

    /**
     * The daily clean-up of the genus rows of every project list but the AntCat list (worldants lists every AntCat
     * name): removeGenusRowsWithoutSpeciesOrSpecimen for each. Returns "deleteProjTaxon:N" (the rows removed), or null
     * when none. A database error is logged and kept for cleanupLists' line; the projects before it are done.
     */
    public String verifyProjTaxon() throws SQLException {
        int removed = 0;
        try {
            List<String> projects = new ArrayList<>();
            try (Statement stmt = getConnection().createStatement();
                 ResultSet r = stmt.executeQuery("select distinct project_name from proj_taxon where project_name != 'worldants' order by project_name")) {
                while (r.next()) projects.add(r.getString(1));
            }
            for (String project : projects) removed += removeGenusRowsWithoutSpeciesOrSpecimen(project);
        } catch (SQLException | RuntimeException e) {
            s_log.error("verifyProjTaxon() e:" + e);
            cleanupErrors.add("genus rows: " + e);
        }
        return removed == 0 ? null : "deleteProjTaxon:" + removed;
    }

// ---------------------------------------------------------------------------------------    

    // Get all records.
    public ArrayList<ProjTaxon> getProjTaxa(String projectName) throws SQLException {
      return getProjTaxa(projectName, null);
    }
    
    public ArrayList<ProjTaxon> getProjTaxa(String projectName, String rank) 
          throws SQLException {
        String rankClause = "";
        if (rank != null) {
          if (Rank.SPECIES.equals(rank)) {
            rank = "'" + Rank.SPECIES + "', '" + Rank.SUBSPECIES + "'";        
          } else {
            rank = DBUtil.sqlQuote(rank);        
          }
          rankClause = " and t.taxarank in (" + rank + ")";
        }

        ArrayList<ProjTaxon> projTaxa = new ArrayList<>();
        
        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getProjTaxa(" + projectName + ")");

            theQuery = " select pt.taxon_name, pt.project_name, t.taxarank, pt.created, pt.subfamily_count, pt.genus_count, pt.species_count, pt.specimen_count, pt.image_count"
              + " from proj_taxon pt, taxon t " 
              + " where pt.taxon_name = t.taxon_name"
              + rankClause;
            if (projectName != null) theQuery += " and pt.project_name = " + DBUtil.sqlQuote(projectName);
            theQuery += " order by pt.project_name, subfamily, genus, species, subspecies, author_date";

            //A.log("getProjTaxa() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                String taxonName = rset.getString("taxon_name");
                ProjTaxon projTaxon = new ProjTaxon(rset.getString("project_name"), taxonName, rset.getString("taxarank"));
                projTaxon.setCreated(rset.getTimestamp("created"));
                projTaxon.setSubfamilyCount(rset.getInt("subfamily_count"));
                projTaxon.setGenusCount(rset.getInt("genus_count"));
                projTaxon.setSpeciesCount(rset.getInt("species_count"));
                projTaxon.setSpecimenCount(rset.getInt("specimen_count"));
                projTaxon.setImageCount(rset.getInt("image_count"));
                projTaxa.add(projTaxon);
                
                //if (taxonName.contains("myrmicinaepheidole minima catella")) A.log("getProjTaxa() query:" + theQuery + " taxonName:" + taxonName + " projectName:" + projectName + " childCount:" + projTaxon.getGlobalChildCount() + " specimen_count:" + rset.getInt("specimen_count"));
            }


            //if (count == 0) A.log("getProjTaxa() not found project:" + projectName);
        } catch (SQLException e) {
            s_log.error("getProjTaxa() projectName:" + projectName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getProjTaxa(" + projectName + ")");
        }

        //A.log("getTaxa() name:" + taxonName + " query:" + theQuery);        
        return projTaxa;
    }  
    
    public ArrayList<Taxon> getTaxa(String projectName) {
        ArrayList<Taxon> taxa = new ArrayList<>();
        
        String theQuery = "";
        ResultSet rset = null;
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "getTaxa() projectName:" + projectName);

            theQuery = " select t.taxon_name, t.subfamily, t.genus, t.species, t.subspecies, t.author_date" 
              + " from proj_taxon pt, taxon t " 
              + " where pt.taxon_name = t.taxon_name"
              + " and t.taxarank in ('species', 'subspecies')";
              theQuery += " and pt.project_name=" + DBUtil.sqlQuote(projectName);
            theQuery += " order by subfamily, genus, species, subspecies, author_date";

            //A.log("getTaxa() query:" + theQuery);
            rset = stmt.executeQuery(theQuery);

            int count = 0;
            while (rset.next()) {
                ++count;
                Taxon taxon = new Taxon();
                //taxon.setTaxonName(rset.getString("taxon_name"));
                taxon.setSubfamily(rset.getString("subfamily"));
                taxon.setGenus(rset.getString("genus"));
                taxon.setSpecies(rset.getString("species"));
                taxon.setSubspecies(rset.getString("subspecies"));
                taxon.setAuthorDate(rset.getString("author_date"));                
                taxa.add(taxon);
            }

            if (count == 0) s_log.debug("getTaxa() not found project:" + projectName);
        } catch (SQLException e) {
            s_log.error("getTaxa() projectName:" + projectName + " exception:" + e + " theQuery:" + theQuery);
        } finally {
            DBUtil.close(stmt, rset, "this", "getTaxa() projectName:" + projectName);
        }

        //A.log("getTaxa() name:" + taxonName + " query:" + theQuery);        
        return taxa;
    }   

    private int getProjectCount(String taxonName) throws SQLException {
    
      ResultSet rset = null;
      Statement stmt = null;
      String query = null;
      try {
          stmt = DBUtil.getStatement(getConnection(), "getProjectCount()");
            
  		  query = "select count(project_name) from proj_taxon " 
  		    + " where taxon_name = " + DBUtil.sqlQuote(taxonName) 
		    + "   and project_name != '" + Project.ALLANTWEBANTS + "'";
	  
		  //A.log("getProjectCount() query:" + query);

		  rset = stmt.executeQuery(query);
		  while (rset.next()) {
		    int count = rset.getInt(1);    
		    return count;
          }		  

      } catch (SQLException e) {
        s_log.error("getProjectCount() query:" + query + " e:" + e);
        return 1;  // as getSpecimenCount(): on an error never 0, which would allow delete() to delete the taxon
      } finally {
        DBUtil.close(stmt, rset, "this", "getProjectCount()");
      } 
      return 0;
    }
    
    private int getSpecimenCount(String taxonName) {

      ResultSet rset = null;
 	  Statement stmt = null;
 	  String query = null;
	  try {
        stmt = DBUtil.getStatement(getConnection(), "getSpecimenCount()");

		query = "select count(code) from specimen " 
		  + " where taxon_name = " + DBUtil.sqlQuote(taxonName); 

		//A.log("getSpecimenCount() query:" + query);

		rset = stmt.executeQuery(query);
		while (rset.next()) {
          return rset.getInt(1);
        }        
      } catch (SQLException e) {
        s_log.error("getSpecimenCount() query:" + query);
      } finally {
        DBUtil.close(stmt, rset, "this", "getSpecimenCount()");
      }        
      return 1;  // Should not happen. Wouldn't want to return 0 as that would indicate 
                 // permission to delete.
    }

    public static ArrayList<ArrayList<String>> getStatisticsByProject(Connection connection) //ArrayList<ArrayList<String>>
        throws SQLException {
        Statement stmt = connection.createStatement();              

        String query = "select project_name, count(*) from proj_taxon group by project_name order by count(*) desc";

        ResultSet resultSet = stmt.executeQuery(query);

        ArrayList<ArrayList<String>> statistics = new ArrayList<>();
        //ArrayList<HashMap<String, String>> statsArray = new ArrayList<HashMap<String, String>>();

        while (resultSet.next()) {
            String project = resultSet.getString(1);
            statistics.add(ProjTaxonDb.getProjectStatistics(project, connection));
            //statsArray.add(ProjTaxonDb.getProjectStatistics(project, connection));
        }                        
        stmt.close();
        // return statsArray;
        return statistics;
    }
    
    public static ArrayList<String> getProjectStatistics(String project, Connection connection) 
        throws SQLException {
        ArrayList<String> statistics = new ArrayList<>();
        //HashMap<String, String> stats = new HashMap<String, String>();

        Statement stmt2 = connection.createStatement();
        String query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank=\"subfamily\"";
        ResultSet resultSet2 = stmt2.executeQuery(query);
        int extinctSubfamily = 0;
        while (resultSet2.next()) {
            extinctSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extinctGenera= 0;
        while (resultSet2.next()) {
            extinctGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extinctSpecies = 0;
        while (resultSet2.next()) {
            extinctSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantSubfamily = 0;
        while (resultSet2.next()) {
            extantSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantGenera = 0;
        while (resultSet2.next()) {
            extantGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extantSpecies = 0;
        while (resultSet2.next()) {
            extantSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name  and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and status='valid' and taxarank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int validSubfamily = 0;
        while (resultSet2.next()) {
            validSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + "  and status='valid' and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int validGenera = 0;
        while (resultSet2.next()) {
            validGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + DBUtil.sqlLiteral(project) + " and status='valid' and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int validSpecies = 0;
        while (resultSet2.next()) {
            validSpecies = resultSet2.getInt(1);
        }
                
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + DBUtil.sqlLiteral(project)
          + " and taxon.status = 'valid' and taxarank in ('species', 'subspecies') and proj_taxon.image_count > 0";
        resultSet2 = stmt2.executeQuery(query);
        int validImagedSpecies = 0;
        while (resultSet2.next()) {
            validImagedSpecies = resultSet2.getInt(1);
        }
                        
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + DBUtil.sqlLiteral(project);
        resultSet2 = stmt2.executeQuery(query);
        int totalTaxa = 0;
        while (resultSet2.next()) {
            totalTaxa = resultSet2.getInt(1);
        }

       statistics.add(project); // 0
       statistics.add(""+extinctSubfamily);
       statistics.add(""+extantSubfamily); 
       statistics.add(""+validSubfamily);
       statistics.add(""+(extinctSubfamily + extantSubfamily)); // 4
       statistics.add(""+extinctGenera); 
       statistics.add(""+extantGenera);
       statistics.add(""+validGenera);
       statistics.add(""+(extinctGenera + extantGenera)); // 8 Total Genera
       statistics.add(""+extinctSpecies); 
       statistics.add(""+extantSpecies); 
       statistics.add(""+validSpecies); 
       statistics.add(""+validImagedSpecies); 
       statistics.add(""+(extinctSpecies + extantSpecies)); // 13 Total Species
       statistics.add(""+totalTaxa);

        // A.log("getProjectStatistics() statistics:" + statistics);                    
        return statistics;
    }

    /**
     * The AntWeb-wide list (allantwebants, the "Global" column) built again and counted: at the end of every specimen
     * upload, after the name update and the parent repair of set 1, and by the admin job. The list first
     * (rebuildAllAntwebList), then its counts.
     */
    public void regenerateAllAntweb() throws SQLException {
        rebuildAllAntwebList();

        // This was in the UtilData.java regenerateAllAntweb

        new ProjTaxonCountDb(getConnection()).childrenCountCrawl("allantwebants"); // Proj_taxon counts
        // The rows were re-inserted with image_count 0: count their Images again now, not at the next nightly set 4
        // (every list's Images column shows the AntWeb-wide number, 2026-10-28).
        new ProjTaxonCountDb(getConnection()).allAntwebImageCount();
        finishRegenerateAllAntweb();
        new ProjectDb(getConnection()).updateCounts("allantwebants");      // Project counts

        s_log.info("regenerateAllAntweb() completed.");

        //s_log.warn("regenerateAllAntweb() execute complete.");
        //  LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "After regenerateAllAntweb Proj_taxon worldants counts");
        //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "after regenerateAllAntweb Proj_taxon worldants counts");
    }

    /**
     * The rows of the AntWeb-wide list, built again: every name of a counted status (StatusSet.getCountables), without
     * the names a curator removed from it (proj_taxon_dispute), and then by the rule of the genus rows (agreed with
     * Brian Fisher, 2026-11-02: "A genus stays on a place's list only with a species row of that genus or a specimen
     * of it there"; removeGenusRowsWithoutSpeciesOrSpecimen, the method the daily clean-up uses).
     *
     * Until 2026-11-12 the list took every counted name, a genus without species or specimens among them (a genus
     * AntCat lists as valid while none of its names is of a counted status), and the daily clean-up took that genus
     * off again: the list's Genera moved by one between an upload and the next daily update (production 11-12: 536
     * after the rebuild, 535 after the clean-up). The builder and the clean-up read the list's rule differently; now
     * the builder applies the clean-up's own method to its list, after the disputed rows are gone and before anything
     * is counted, in the same transaction, so no page and no count ever sees such a row. An error there (database or
     * other) that leaves the transaction intact is logged and the list stays as built (the daily clean-up applies the
     * rule again); it does not fail an upload. A deadlock is passed on: the transaction, the upload's own, is gone
     * (DBUtil.transactionRolledBack).
     *
     * A genus row that was on the list before the rebuild and is off it afterwards by the rule is recorded
     * (ListRemovalLog, JOB_REBUILD_GENUS; Brian Fisher, 2026-11-03: no job deletes list entries without a record a
     * curator can see). A genus the rebuild put on the list and took off again in the same transaction was not on the
     * list before and is not after: no record (it would be one for every such genus at every upload).
     */
    /** Tests only: called in rebuildAllAntwebList when the list's rows are in, before the rule of the genus rows. */
    private static volatile Runnable s_beforeGenusRuleForTests = null;

    public static void setBeforeGenusRuleForTests(Runnable beforeGenusRule) {
        s_beforeGenusRuleForTests = beforeGenusRule;
    }

    public void rebuildAllAntwebList() throws SQLException {
        //LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "Before regenerateAllAntweb Proj_taxon worldants counts");
        //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "Before regenerateAllAntweb Proj_taxon worldants counts");

        // the genus rows on the list before the rebuild: one of them that the rule takes off below is a removal, and recorded
        Set<String> generaBefore = new HashSet<>();
        try (Statement before = getConnection().createStatement();
             ResultSet r = before.executeQuery("select pt.taxon_name from proj_taxon pt join taxon t on t.taxon_name = pt.taxon_name"
                 + " and t.taxarank = 'genus' where pt.project_name = 'allantwebants'")) {
            while (r.next()) generaBefore.add(r.getString(1));
        } catch (SQLException | RuntimeException e) {
            s_log.warn("rebuildAllAntwebList() genus rows before e:" + e);
        }

        String deleteDML = "delete from proj_taxon where project_name = 'allantwebants'";
        // s_log.warn("regenerateAllAntweb() deleteDML:" + deleteDML);
        Statement stmt = DBUtil.getStatement(getConnection(), "regenerateAllAntweb1()");
        try {
            stmt.executeUpdate(deleteDML);
        } finally {
            DBUtil.close(stmt, "regenerateAllAntweb1()");
        }
            
        // Source discernment is insufficient here. Must depend mostly on if specimens exist.          
        String insertDML = "insert into proj_taxon (taxon_name, project_name, source) " 
            + " (select taxon_name, 'allantwebants', (case when source like 'specimen%' then " + DBUtil.sqlLiteral(Source.SPECIMEN) + " when source = 'worldants' then 'worldants' else '' end) "
            + " from taxon "   
            + " where status in " + StatusSet.getCountables()
            + ")";
        //A.log("regenerateAllAntweb() insertDML:" + insertDML);
        stmt = DBUtil.getStatement(getConnection(), "regenerateAllAntweb2()");
        try {
            stmt.executeUpdate(insertDML);
        } finally {
            DBUtil.close(stmt, "regenerateAllAntweb2()");
        }

        UtilDb utilDb = new UtilDb(getConnection());
        int deleteCount = utilDb.deleteFrom("proj_taxon", "where (project_name, taxon_name) in (select project_name, taxon_name from proj_taxon_dispute)");
        int updateCount = utilDb.updateField("proj_taxon", "source", "'specimen'", "project_name = 'allantwebants' and specimen_count > 0");
        s_log.debug("rebuildAllAntwebList() deleteCount:" + deleteCount + " updateCount:" + updateCount);

        try {
            Runnable hook = s_beforeGenusRuleForTests;
            if (hook != null) hook.run();
            int removed = removeGenusRows(Project.ALLANTWEBANTS, ListRemovalLog.JOB_REBUILD_GENUS, generaBefore);
            if (removed > 0) s_log.info("rebuildAllAntwebList() genus rows without a species row or a specimen left out: " + removedRowsText());
        } catch (SQLException | RuntimeException e) {
            s_log.error("rebuildAllAntwebList() genus rows e:" + e);
            // A deadlock: the database has rolled back the whole transaction, which is the upload's own (its specimens
            // and this list share one connection, committed once at the end: SpecimenUploadJob, TaxonWorksNightly).
            // Going on would commit an upload without its specimens and call it done. Passed on: the upload fails and
            // says so. Any other error leaves the transaction as it was and the list as built.
            if (DBUtil.transactionRolledBack(e)) {
                throw e instanceof SQLException ? (SQLException) e
                    : new SQLException("rebuildAllAntwebList(): the transaction was rolled back by a deadlock", "40001", 1213, e);
            }
        }
    }


    public void finishRegenerateAllAntweb() throws SQLException {

        UtilDb utilDb = new UtilDb(getConnection());
        int deleteCount = utilDb.deleteFrom("proj_taxon", "where (project_name, taxon_name) in (select project_name, taxon_name from proj_taxon_dispute)");

        //int updateCount = 0;
        //updateCount = utilDb.updateField("proj_taxon", "source", "'specimen'", "project_name = 'allantwebants' and specimen_count > 0");
        s_log.debug("finishRegenerateAllAntweb() deleteCount:" + deleteCount); // + " updateCount:" + updateCount);

        // LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "after finishRegenerateAllAntweb Proj_taxon worldants counts");
        //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "after finishRegenerateAllAntweb Proj_taxon worldants counts");

    }

    public void addProjectFamily(String project) {
      /* Called from SpeciesListUpload.  The Family Formicidae record does not get included in the uploaded
         species list (except in the case of worldants).  Here we only have to add it to proj_taxon */
        Statement stmt = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "addProjectFamily()");

            String query = "insert into proj_taxon (project_name, taxon_name, source) values ("
                + DBUtil.sqlQuote(project) + ",'formicidae', 'addProjectFamily')";
            stmt.executeUpdate(query);
            stmt.close();
        } catch (SQLException e) {
            if (! (e instanceof SQLIntegrityConstraintViolationException)) {
                s_log.error("addProjectFamily() e:" + e);
            } else {
                //s_log.info("addProjectFamily() expected - e:" + e);            
            }
        } finally {
            DBUtil.close(stmt, "addProjectFamily()");
        }
    }

// ------------------------------------------------------------------------------------

    public void testProjTaxon() throws SQLException {
      fixProjTaxonParentage();
    }
    public void fixProjTaxonParentage() throws SQLException {
      // Get all the ProjTaxon.  Verify that each proj_taxon's taxon parent exists in proj_taxon for that project.

      String projectName = null;
      //projectName = "europaislandants";
      //projectName = "madagascarants";
      
      int foundCount = 0;
      int notFoundCount = 0;
      
      ArrayList<Project> projects = null;
      if (projectName != null) {
        projects = new ArrayList<>();
        Project tProject = ProjectMgr.getProject(projectName);
        projects.add(tProject); 
      } else {
        projects = ProjectMgr.getAllProjects();
      }
      
      for (Project project : projects) {

        //s_log.warn("testProjTaxon() project:" + project);
        
        HashSet<String> messages = new HashSet<>();
      
        
        ArrayList<ProjTaxon> projTaxa = null;
        try {
          projTaxa = getProjTaxa(project.getName());
        } catch (SQLException e) {
          s_log.warn("fixProjTaxonParentage() e:" + e);
          return;
        }
        for (ProjTaxon projTaxon : projTaxa) {
          projectName = projTaxon.getProjectName();
          Taxon taxon = new TaxonDb(getConnection()).getTaxon(projTaxon.getTaxonName());
          if (taxon != null) {
            String parent = taxon.getParentTaxonName();
            if (! exists(projectName, parent)) {
              ++notFoundCount;
              String message = "testProjTaxon() not exist projectName:" + projectName + " taxonName:" + parent + " date:" + projTaxon.getCreated(); 

              // Not sure we should try to fix. The taxa do not necessarily exist...
              //try {
              //  insertProjTaxon(projectName, parent, "fixProjTaxonParentage");
              //} catch (SQLException e) {
              //  s_log.warn("fixProjTaxonParentage() e:" + e);
              //}

              messages.add(message);
            } else ++foundCount;
          } else {
            s_log.warn("testProjTaxon() taxon is null");
          }
        }

        for (String message : messages) {
          s_log.warn("testProjTaxon() message:" + message);
        }

      }
      s_log.warn("testProjTaxon() foundCount:" + foundCount + " notFoundCount:" + notFoundCount);
    }


}

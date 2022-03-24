package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class ProjTaxonDb extends EditableTaxonSetDb {
    
    private static Log s_log = LogFactory.getLog(ProjTaxonDb.class);
        
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
               + " where project_name = '" + projectName + "'"
               + " and taxon_name = '" + taxonName + "'";

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
               + " where project_name = '" + projectName + "'"
               + " and taxon_name = '" + taxonName + "'";
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

            dml = "update proj_taxon set source = '" + source + "' where project_name = '" + projectName + "'"
              + " and taxon_name = '" + taxonName + "'";
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
          + " where project_name = '" + projectName + "'" 
          + "   and taxon_name = '" + taxonName + "'"; 
        //A.log("delete() 1 delete:" + delete);
        c = stmt1.executeUpdate(delete);

        // if taxon is in no other project than allantweb, and has no specimens!? 
        // and is not in worldants, and is not valid, then delete the taxon outright, 
        // and the allantweb projTaxon record.
        
        String rank = Taxon.getRankFromName(taxonName);     
           
        boolean isSubfamily = rank.equals(Rank.SUBFAMILY);
        boolean hasOtherProjects = getProjectCount(taxonName) > 0;
        boolean hasSpecimen = getSpecimenCount(taxonName) > 0;
        
        s_log.debug("delete() taxonName:" + taxonName + " isSubfamily:" + isSubfamily + " hasOtherProjects:" + hasOtherProjects + " hasSpecimen:" + hasSpecimen);

        if ("incertae_sedis".equals(taxonName)) s_log.debug("delete() EXISTS:" + exists("fossilants", taxonName));

        if (isSubfamily) return c;
        if (hasOtherProjects) return c;

        if (!"genus".equals(rank) && hasSpecimen) return c;
        
        if (Taxon.isMorpho(taxonName)) {
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
            whereClause = "project_name = " + stmt.enquoteLiteral(projectName);

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
        String fromWhereClause = "from proj_taxon where project_name = '" + speciesListName + "'";
		//A.log("ProjTaxonDb.hasTaxonSetSpecies() fromWhereClause:" + fromWhereClause);
        return super.hasTaxonSetSpecies(speciesListName, genus, fromWhereClause);
    }
    // Satisfies the EditableTaxonSetDb abstract method.
    public boolean hasTaxonSetGenera(String speciesListName, String subfamily) throws SQLException {
        String fromWhereClause = "from proj_taxon where project_name = '" + speciesListName + "'";
		//A.log("ProjTaxonDb.hasTaxonSetGenera() fromWhereClause:" + fromWhereClause);
        return super.hasTaxonSetGenera(speciesListName, subfamily, fromWhereClause);
    }


    String deleteUncuratedMorphosWithoutSpecimen() throws SQLException {
        String dml = "delete from proj_taxon where taxon_name in (select taxon_name from taxon t where t.taxarank in ('species', 'subspecies') and t.status = 'morphotaxon' and taxon_name not in (select taxon_name from specimen))";
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
                String taxonName = (String) rset.getString("taxonName");
                String projectName = (String) rset.getString("projectName");
                if (hasNoChildren(taxonName, projectName)) {
                    A.log("cleanupSpeciesListProxyRecords() taxonName:" + taxonName + " projectName:" + projectName);
                    result = deleteProjTaxon(taxonName, projectName, "and source = 'proxyspeciesListTool'");
                }
            }
        } catch (SQLException e) {
            s_log.error("cleanupSpeciesListProxyRecords()");
        } finally {
            DBUtil.close(stmt, rset, "cleanupSpeciesListProxyRecords()");
        }
        return result;
    }

    private boolean hasNoChildren(String taxonName, String projectName) throws SQLException {
        int count = 0;
        String query = "select pt.taxon_name, pt.project_name from proj_taxon pt where pt.project_name = '" + projectName + "'"
            + " and pt.taxon_name in (select taxon_name from taxon where parent_taxon_name = '" + taxonName + "')";

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "hasNoChildren()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                count = count + 1;
            }
        } catch (SQLException e) {
            s_log.error("hasNoChildren() projectName:" + projectName + " taxonName:" + taxonName);
        } finally {
            DBUtil.close(stmt, rset, "hasNoChildren()");
        }
        return count == 0;
    }


    private String deleteProjTaxon(String taxonName, String projectName) throws SQLException {
      return deleteProjTaxon(taxonName, projectName, null);
    }
    private String deleteProjTaxon(String taxonName, String projectName, String extraClause) throws SQLException {
        String dml = "delete from proj_taxon "
            + " where taxon_name = '" + taxonName + "'"
            + " and project_name = '" + projectName + "'";
        if (extraClause != null) dml += extraClause;
        Statement stmt = null;
        int c = 0;
        try {
            stmt = DBUtil.getStatement(getConnection(), "deleteProjTaxon()");
            c = stmt.executeUpdate(dml);
        } catch (SQLException e) {
            s_log.error("deleteProjTaxon() e:" + e + " dml:" + dml);
            throw e;
        } finally {
            DBUtil.close(stmt, "deleteProjTaxon()");
        }
        String message = "deleteProjTaxon:" + c;
        return message;
    }


    // Make sure that all of the genus records have the correct number of species records according to count.
    // If a genus does not have species in the project, remove it.
    public String verifyProjTaxon() throws SQLException {
        String result = null;
        String query = "select pt.taxon_name taxonName, pt.project_name projectName, pt.species_count from proj_taxon pt, taxon t "
            + " where pt.taxon_name = t.taxon_name and t.taxarank = 'genus'"
            + "   and pt.project_name != 'worldants'";
        // For each proxyspeciesListTool find if it has children.

        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = DBUtil.getStatement(getConnection(), "verifyProjTaxon()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
                String genusName = (String) rset.getString("taxonName");
                String projectName = (String) rset.getString("projectName");
                int speciesCount = rset.getInt("species_count");
                if (hasNoChildren(genusName, projectName)) {
                    A.log("verifyProjTaxon() No children for genusName:" + genusName + " projectName:" + projectName + " speciesCount:" + speciesCount);
                    // All, or just those with speciesCount > 0?
                    result = deleteProjTaxon(genusName, projectName);
                }
            }
        } catch (SQLException e) {
            s_log.error("verifyProjTaxon() e:" + e);
        } finally {
            DBUtil.close(stmt, rset, "verifyProjTaxon()");
        }
        return result;
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
            rank = "'" + rank + "'";        
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
            if (projectName != null) theQuery += " and pt.project_name = '" + projectName + "'";
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
              theQuery += " and pt.project_name='" + projectName + "'";
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
  		    + " where taxon_name = '" + taxonName + "'" 
		    + "   and project_name != '" + Project.ALLANTWEBANTS + "'";
	  
		  //A.log("getProjectCount() query:" + query);

		  rset = stmt.executeQuery(query);
		  while (rset.next()) {
		    int count = rset.getInt(1);    
		    return count;
          }		  

      } catch (SQLException e) {
        s_log.error("getProjectCount() query:" + query);
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
		  + " where taxon_name = '" + taxonName + "'"; 

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
        String query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank=\"subfamily\"";
        ResultSet resultSet2 = stmt2.executeQuery(query);
        int extinctSubfamily = 0;
        while (resultSet2.next()) {
            extinctSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extinctGenera= 0;
        while (resultSet2.next()) {
            extinctGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 1 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extinctSpecies = 0;
        while (resultSet2.next()) {
            extinctSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantSubfamily = 0;
        while (resultSet2.next()) {
            extantSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int extantGenera = 0;
        while (resultSet2.next()) {
            extantGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and taxon.fossil = 0 and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int extantSpecies = 0;
        while (resultSet2.next()) {
            extantSpecies = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name  and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and status='valid' and taxarank=\"subfamily\"";
        resultSet2 = stmt2.executeQuery(query);
        int validSubfamily = 0;
        while (resultSet2.next()) {
            validSubfamily = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + "  and status='valid' and taxarank=\"genus\"";
        resultSet2 = stmt2.executeQuery(query);
        int validGenera = 0;
        while (resultSet2.next()) {
            validGenera = resultSet2.getInt(1);
        }
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + stmt2.enquoteLiteral(project) + " and status='valid' and taxarank in ('species', 'subspecies')";
        resultSet2 = stmt2.executeQuery(query);
        int validSpecies = 0;
        while (resultSet2.next()) {
            validSpecies = resultSet2.getInt(1);
        }
                
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + stmt2.enquoteLiteral(project)
          + " and taxon.status = 'valid' and taxarank in ('species', 'subspecies') and proj_taxon.image_count > 0";
        resultSet2 = stmt2.executeQuery(query);
        int validImagedSpecies = 0;
        while (resultSet2.next()) {
            validImagedSpecies = resultSet2.getInt(1);
        }
                        
        query = "select count(*) from taxon, proj_taxon where taxon.taxon_name = proj_taxon.taxon_name and proj_taxon.project_name = " + stmt2.enquoteLiteral(project);
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

    // XXX Insufficient below. Perhaps fix in allCountCrawl? Performance matters.
    public void regenerateAllAntweb() throws SQLException {
        //s_log.warn("regenerateAllAntweb() DONT execute too often. Expensive? About a minute.");

        //LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "Before regenerateAllAntweb Proj_taxon worldants counts");
        //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "Before regenerateAllAntweb Proj_taxon worldants counts");

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
            + " (select taxon_name, 'allantwebants', (case when source like 'specimen%' then " + stmt.enquoteLiteral(Source.SPECIMEN) + " when source = 'worldants' then 'worldants' else '' end) "
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
        s_log.debug("regenerateAllAntweb() deleteCount:" + deleteCount + " updateCount:" + updateCount);

        // This was in the UtilData.java regenerateAllAntweb

        new ProjTaxonCountDb(getConnection()).childrenCountCrawl("allantwebants"); // Proj_taxon counts
        finishRegenerateAllAntweb();
        new ProjectDb(getConnection()).updateCounts("allantwebants");      // Project counts

        s_log.info("regenerateAllAntweb() completed.");

        //s_log.warn("regenerateAllAntweb() execute complete.");
        //  LogMgr.logAntQuery(getConnection(), "projectTaxaCountByProjectRank", "After regenerateAllAntweb Proj_taxon worldants counts");
        //LogMgr.logAntBattery(getConnection(), "projectTaxonCounts", "after regenerateAllAntweb Proj_taxon worldants counts");
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

            String query = "insert into proj_taxon (project_name, taxon_name, source) values ('"
                + project + "','formicidae', 'addProjectFamily')";
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
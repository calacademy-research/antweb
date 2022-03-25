package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class OrphansDb extends AntwebDb {

    private static final Log s_log = LogFactory.getLog(OrphansDb.class);
    
    public OrphansDb(Connection connection) {
      super(connection);
    }

    public static String getOrphanedSpeciesQuery() {
      return OrphansDb.getOrphanedSpeciesQuery("specimen");
    }

    public static String getOrphanedSpeciesQuery(String source) {
      return getOrphanedSpeciesQuery(source, " order by source desc, taxon_name");
    }
    
    public static String getOrphanedSpeciesQuery(String source, String orderBy) {
      String query = "select source, taxon_name from taxon " 
        + " where taxon_name not in (select taxon_name from specimen)";
      //if (!AntwebProps.isDevMode()) 
      query += " and taxarank in ('species', 'subspecies')";   // Added in Aug 28th 2013
      query += " and source like '%" + source + "%'" // or source like 'addMissingGenera')"
        + orderBy;

      s_log.info("getOrphanedSpeciesQuery() query:" + query);        
      return query;
    }

/*
Genera not yet well thought out.  What should source be?  addMissingGenera?
*/
    public static String getOrphanedGeneraQuery() {
      return OrphansDb.getOrphanedGeneraQuery(null);
    }

    // This query used to be merged with the code above.  How to handle these orphans?
    // Outstanding question.  How to get rid of outdated "addMissingGenera" genera?  See below...
    // This is not yet utilized...
    public static String getOrphanedGeneraQuery(String source) {
      String query = "select source, taxon_name from taxon " 
        + " where taxarank = 'genus'"
        + " and taxon_name not in (select parent_taxon_name from taxon)";
      if (source != null) {
        query += " and source like '%" + source + "%'";
      }
      query += " order by source desc, taxon_name";
      s_log.debug("getOrphanedGeneraQuery() query:" + query);
      return query;
    }
    
    public static String getOtherOrphanedGeneraQuery() {
      String query = "select taxon_name, subfamily, genus, source, created from taxon " 
        + " where taxarank = 'genus' and status = 'morphotaxon' "
        + " and (subfamily, genus) not in (select subfamily, genus from taxon where (taxarank = 'species' or taxarank = 'subspecies')) "
        + " order by taxon_name";    
      return query;
    }
    
    private static String getOrphanedSubfamiliesQuery() {
      return OrphansDb.getOrphanedSubfamiliesQuery(null);
    }

    private static String getOrphanedSubfamiliesQuery(String source) {
      String query = "select source, subfamily from taxon " 
        + " where taxarank = 'subfamily'"
        + " and taxon_name not in (select parent_taxon_name from taxon)";   
      if (source != null) {
        query += " and source like '%" + source + "%'";
      }
      query += " order by source desc, taxon_name";
      s_log.debug("getOrphanedSubfamiliesQuery() query:" + query);
      return query;
    }

    private boolean hasDescEdit(Connection connection, String taxonName) 
        // taxonName may be a subfamily, which is fine.
        throws SQLException {
        Statement stmt1 = connection.createStatement();
        String query = "select taxon_name from description_edit where taxon_name = '" + taxonName + "' ";
        
        // Do we want to work on specimen description edits?
        // query += " and code is null ";

        ResultSet rset = stmt1.executeQuery(query);
        while (rset.next()) {
          return true;
        }
        return false;
    }

    private boolean subfamilyHasSpecimen(Connection connection, String subfamily)
        throws SQLException {
        Statement stmt1 = connection.createStatement();
        String query = "select code from specimen where subfamily = '" + subfamily + "' ";

        ResultSet rset = stmt1.executeQuery(query);
        while (rset.next()) {
          return true;
        }
        return false;
    }
    
    // This gets the set of specimen upload taxons without specimens, without description edits.
    public ArrayList<Taxon> getOrphanSpeciesList() throws SQLException {
            ArrayList orphanTaxonList = new ArrayList();
                        
            Statement stmt1 = getConnection().createStatement();
            String query = OrphansDb.getOrphanedSpeciesQuery();
            
            ResultSet rset1 = stmt1.executeQuery(query);
            s_log.debug("getOrphanSpeciesList() query:" + query);
            while (rset1.next()) {
                String taxonName = rset1.getString("taxon_name");
                String source = rset1.getString("source");

                if (hasDescEdit(getConnection(), taxonName)) {
                  s_log.debug("execute() continue on " + taxonName);
                  continue;
                }
                 
                //s_log.warn("orphan() q:" + query);
                Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
                orphanTaxonList.add(taxon);
            }
            stmt1.close();
            return orphanTaxonList;
    }

    public ArrayList<Taxon> getOrphanGeneraList() throws SQLException {
		ArrayList orphanTaxonList = new ArrayList();
					
		Statement stmt1 = getConnection().createStatement();
		String query = OrphansDb.getOrphanedGeneraQuery();
		ResultSet rset1 = stmt1.executeQuery(query);
		s_log.debug("getOrphanGeneraList() 1 query:" + query);
		while (rset1.next()) {
			String taxonName = rset1.getString("taxon_name");
			String source = rset1.getString("source");

			if (hasDescEdit(getConnection(), taxonName)) {
			  s_log.debug("execute() continue on " + taxonName);
			  continue;
			}
			 
			//s_log.warn("orphan() q:" + query);
			Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
			orphanTaxonList.add(taxon);
		}
		rset1.close();
		
		query = OrphansDb.getOtherOrphanedGeneraQuery();            
		rset1 = stmt1.executeQuery(query);
		s_log.debug("getOrphanGeneraList() 2 query:" + query);
		while (rset1.next()) {
			String taxonName = rset1.getString("taxon_name");
			String source = rset1.getString("source");

			if (hasDescEdit(getConnection(), taxonName)) {
			  s_log.debug("execute() continue on " + taxonName);
			  continue;
			}
			 
			//s_log.warn("orphan() q:" + query);
			Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
			orphanTaxonList.add(taxon);
		}
		rset1.close();
		stmt1.close();

		
		return orphanTaxonList;
    }

    public ArrayList<Taxon> getDupedGeneraList() throws SQLException {
       /* This will find all of the genus names that are listed in multiple subfamilies.
          This is now blocked during upload, so should not happen.  */
		ArrayList orphanTaxonList = new ArrayList();                        
		Statement stmt1 = getConnection().createStatement();
		String query = "select genus, count(distinct subfamily)from taxon where genus != '' " 
			+ " group by genus having count(distinct subfamily) > 1 " 
			+ " order by count(distinct subfamily), genus";
		ResultSet rset1 = stmt1.executeQuery(query);
		s_log.debug("execute() 1 query:" + query);
		while (rset1.next()) {
			String genus = rset1.getString("genus");
			Statement stmt2 = getConnection().createStatement();
			query = " select subfamily, count(subfamily) from taxon " 
				+ " where genus = '" + genus 
				+ "' group by subfamily order by count(subfamily) limit 1";
			ResultSet rset2 = stmt2.executeQuery(query);
			s_log.debug("execute() 2 query:" + query);
			while (rset2.next()) {
				String subfamily = rset2.getString("subfamily");
				Statement stmt3 = getConnection().createStatement();
				query = "select taxon_name from taxon " 
				  + " where genus = '" + genus + "'" 
				  + "   and subfamily = '" + subfamily + "'"
				  + "   and source != 'worldants.txt'";
				ResultSet rset3 = stmt3.executeQuery(query);
				s_log.debug("execute() 3 query:" + query);
				while (rset3.next()) {                           
					String taxonName = rset3.getString("taxon_name");                                  
					//s_log.warn("orphan() q:" + query);
					Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
					orphanTaxonList.add(taxon);
				}
				stmt3.close();
			}
			stmt2.close();
		}
		stmt1.close();
		return orphanTaxonList;
    }

    public ArrayList<Taxon> getOrphanSubfamiliesList() throws SQLException {
		ArrayList orphanTaxonList = new ArrayList();

		Statement stmt = null;
		ResultSet rset = null;
		String query = OrphansDb.getOrphanedSubfamiliesQuery();
		try {
	        stmt = DBUtil.getStatement(getConnection(), "OrphansDb.getOrphanSubfamiliesList()");
	 	    rset = stmt.executeQuery(query);
		    s_log.debug("execute query:" + query);
        
            while (rset.next()) {
                String subfamily = rset.getString("subfamily");
                String source = rset.getString("source");

                if (hasDescEdit(getConnection(), subfamily)) {
                  //A.log("execute() continue on " + taxonName);
                  continue;
                }

                if (subfamilyHasSpecimen(getConnection(), subfamily)) {
                  //A.oog("execute() continue on " + taxonName);
                  continue;
                }
                 
                //s_log.warn("orphan() q:" + query);
                Taxon taxon = new TaxonDb(getConnection()).getTaxon(subfamily);
                orphanTaxonList.add(taxon);
            }
        } catch (SQLException e) {
          s_log.warn("getOrphanSubfamiliesList() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "OrphansDb.getOrphanSubfamiliesList()");
        }	
        return orphanTaxonList;
    }
    
    public ArrayList<Taxon> getSpecimenOrphanDescEditTaxons() throws SQLException {
        ArrayList<Taxon> list = new ArrayList();
        
        // Do not work on Specimen Description_edit records.  (code is null).
        
		// This gets the set of Description edits for specimen upload taxons without specimens.
		ArrayList<Taxon> orphanTaxonWithDescEditList = new ArrayList();
		orphanTaxonWithDescEditList = getOrphanTaxonWithDescEditList();
	    s_log.debug("execute() orphanTaxonWithDescEditList:" + orphanTaxonWithDescEditList.size());
		for (Taxon taxon : orphanTaxonWithDescEditList) {
		  s_log.debug("getOrphanDescEditTaxons() taxon:" + taxon.getTaxonName());
		  taxon.setDescription(new DescEditDb(getConnection()).getDescEdits(taxon, false));
		  list.add(taxon);
		}

		Collections.sort(list);

        return list;    
    }
        

    public DummyTaxon getOrphanDescEditTaxon(String taxonName) throws SQLException {
      DummyTaxon orphan = null;

      orphan = DummyTaxon.makeDummyTaxon(taxonName);
      
      if (orphan == null) s_log.debug("getOrphanDescEditTaxon() taxonName:" + taxonName + " is null");
      orphan.setDescription(new DescEditDb(getConnection()).getDescEdits(orphan, false));

	  ArrayList<DummyTaxon> possibleValidNames = getPossibleValidNames(orphan);
	  orphan.setPossibleValidNames(possibleValidNames);
	  s_log.debug("getTaxonOrphanDescEditTaxon() possibleValidNames:" + possibleValidNames);

      return orphan;
    }
        
    public ArrayList<Taxon> getTaxonOrphanDescEditTaxons() throws SQLException {
        ArrayList<Taxon> list = new ArrayList();

        Statement stmt = null;
        ResultSet rset = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "OrphansDb.getTaxonOrphanDescEditTaxons()");
  		  String query = "select distinct taxon_name from description_edit where taxon_name not in (select distinct taxon_name from taxon) and code is null";
		  rset = stmt.executeQuery(query);
		  while (rset.next()) {
			String taxonName = rset.getString("taxon_name");
			
			// DummyTaxon orphan = getOrphanDescEditTaxon(taxonName);
			//DummyTaxon orphan = new DummyTaxonDb(getConnection()).getInstance(taxonName);
			DummyTaxon orphan = DummyTaxon.makeDummyTaxon(taxonName);
			//if (orphan == null) A.log("getTaxonOrphanDescEditTaxons() orphan null for taxonName:" + taxonName);
			orphan.setDescription(new DescEditDb(getConnection()).getDescEdits(orphan, false));

            ArrayList<DummyTaxon> possibleValidNames = getPossibleValidNames(orphan);
            orphan.setPossibleValidNames(possibleValidNames);
            //A.log("getTaxonOrphanDescEditTaxons() possibleValidNames:" + possibleValidNames);

			list.add(orphan);
			//s_log.warn("getOrphanDescEdits() taxonName:" + taxonName + " taxon.getTaxonName():" + taxon.getTaxonName());
		  }		
        } catch (SQLException e) {
          s_log.warn("getTaxonOrphanDescEditTaxons() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "OrphansDb.getTaxonOrphanDescEditTaxons()");
        }		

		Collections.sort(list);
		s_log.debug("execute() 2 list.size:" + list.size());
        return list;    
    }    
    
    // This gets the set of specimen upload taxons without specimens, with description edits.
    public ArrayList<Taxon> getOrphanTaxonWithDescEditList() throws SQLException {
	  ArrayList orphanTaxonWithDescEditList = new ArrayList();

	  Statement stmt = null;
	  ResultSet rset = null;
      try {
		String query = getOrphanedSpeciesQuery("specimen", " order by taxon_name");
		stmt = DBUtil.getStatement(getConnection(), "OrphansDb.getOrphanTaxonWithDescEditList()");
		rset = stmt.executeQuery(query);
		s_log.debug("getOrphanTaxonWithDescEditList() query:" + query);
		while (rset.next()) {
			String taxonName = rset.getString("taxon_name");
			String source = rset.getString("source");

			if (hasDescEdit(getConnection(), taxonName)) {
			  s_log.debug("OrphansDb.getOrphanTaxonWithDescEditList() continue on " + taxonName);
			  
			  Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
			  orphanTaxonWithDescEditList.add(taxon);
            }
		}
      } catch (SQLException e) {
          s_log.warn("getOrphanTaxonWithDescEditList() e:" + e);
          throw e;
      } finally {
          DBUtil.close(stmt, rset, "OrphansDb.getOrphanTaxonWithDescEditList()");
      }			
	
	  return orphanTaxonWithDescEditList;
    }

    public ArrayList<DummyTaxon> getPossibleValidNames(Taxon taxon) throws SQLException {
		ArrayList<DummyTaxon> possibleValidNames = new ArrayList<>();

        String commonPhrase = taxon.getSpecies();
        if (Rank.SUBSPECIES.equals(taxon.getRank())) commonPhrase = taxon.getSubspecies();

		Statement stmt = null;
		ResultSet rset = null;
		String query = "select taxon_name, status, genus from taxon where taxon_name like '% " + commonPhrase + "%'" 
		  + " and taxon_name != '" + taxon.getTaxonName() + "'"
		  + " and taxarank in ('species', 'subspecies')"
		  + " order by status desc, genus";
		try {
	        stmt = DBUtil.getStatement(getConnection(), "OrphansDb.getPossibleValidNames()");
	 	    rset = stmt.executeQuery(query);
		    s_log.debug("execute query:" + query);
        
            while (rset.next()) {
                String possibleTaxonName = rset.getString("taxon_name");
                String status = rset.getString("status");
                //DummyTaxon possible = new DummyTaxonDb(getConnection()).getInstance(possibleTaxonName);
			    DummyTaxon possible = new DummyTaxonDb(getConnection()).getDummyTaxon(possibleTaxonName);
			    possible.setStatus(status);                
                possibleValidNames.add(possible);
            }
        } catch (SQLException e) {
          s_log.warn("getPossibleValidNames() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "OrphansDb.getPossibleValidNames()");
        }	
        return possibleValidNames;
    }
    
    public void deleteTaxon(String taxonName) throws SQLException {
        Statement stmt = DBUtil.getStatement(getConnection(), "OrphansDb.deleteTaxon()");
        try {
          // A.log("deleteTaxon() taxon:" + taxonName);
          stmt = getConnection().createStatement();
          String delete = "delete from taxon where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);    
          delete = "delete from proj_taxon where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);    
          delete = "delete from geolocale_taxon where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);    
          delete = "delete from bioregion_taxon where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);    
          delete = "delete from museum_taxon where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);
          delete = "delete from taxon_prop where taxon_name = '" + taxonName + "'"; 
          stmt.executeUpdate(delete);    
        } catch (SQLException e) {
          s_log.warn("deleteTaxon(" + taxonName + ") e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, "OrphansDb.deleteTaxon()");
        }
    }

    public String deleteOrphanedSpeciesFromSource(String source) throws SQLException { 
      return deleteOrphanedSpeciesFromSource(source, false);
    }
    public String deleteOrphanedSpeciesFromSource(String source, boolean governed) throws SQLException {
          
        String message = null;
        
        Statement stmt = null;
        ResultSet rset = null;
        String query = OrphansDb.getOrphanedSpeciesQuery(source);
        try {    

          s_log.debug("deleteOrphanedSpeciesFromSource() source:" + source + " query:" + query);

          ArrayList<String> orphanList = new ArrayList<>();
          stmt = DBUtil.getStatement(getConnection(), "OrphansDb.deleteOrphanedSpeciesFromSource()");
          rset = stmt.executeQuery(query);
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            orphanList.add(taxonName);
          }

          int maxDeleteOrphanSize = 100;
          // A.log("deleteOrphanedTaxonsFromSource() max:" + maxDeleteOrphanSize + " orphanList:" + orphanList);
          boolean devException = AntwebProps.isDevMode() && false;
          if (devException || !governed || orphanList.size() < maxDeleteOrphanSize) {
            String notDeletedList = "";
            for (String taxonName : orphanList) {
              if (hasDescEdit(getConnection(), taxonName)) {
                notDeletedList += taxonName + ", ";
                continue;
              }
              deleteTaxon(taxonName);
            }
            s_log.info("deleteOrphanedSpeciesFromSource() taxonNames not deleted due to description edits:" + notDeletedList);
            //A.log("deleteOrphanedSpeciesFromSource() orphanList:" + orphanList);

          } else {  
            message = "Warning. Please check the inserted specimens count to verify that the full file was uploaded.";
            s_log.warn("deleteOrphanedSpeciesFromSource() message:" + message 
              + " Orphan List too large to delete. size:" + orphanList.size());        
          }           
        } catch (SQLException e) {
          s_log.warn("deleteOrphanedSpeciesFromSource() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "OrphansDb.deleteOrphanedSpeciesFromSource()");
        }	
        return message; // Null is success
    }    

    public void deleteOrphanedGeneraFromSource(String source) throws SQLException {  
        Statement stmt = null;
        ResultSet rset = null;
        try {
          String query = OrphansDb.getOrphanedGeneraQuery(source); 
          stmt = DBUtil.getStatement(getConnection(), "OrphansDb.deleteOrphanedGeneraFromSource()");            
          s_log.warn("deleteOrphanedGeneraFromSource() source:" + source + " query:" + query);

          rset = stmt.executeQuery(query);
          while (rset.next()) {
            String taxonName = rset.getString("taxon_name");
            
            if (hasDescEdit(getConnection(), taxonName)) {
              s_log.warn("deleteOrphanedGeneraFromSource() taxonName:" + taxonName + " hasDescEdit. Skipped");
              continue;
            }            
            
            s_log.debug("deleteOrphanednGeneraFromSource() taxonName:" + taxonName);
            deleteTaxon(taxonName);
          }
        } catch (SQLException e) {
          s_log.warn("deleteOrphanedGeneraFromSource() e:" + e);
          throw e;
        } finally {
          DBUtil.close(stmt, rset, "OrphansDb.deleteOrphanedGeneraFromSource()");
        }	
    }
         
    public void deleteOrphanedSubfamiliesFromSource(String source) throws SQLException {
          
       Statement stmt = null;
       ResultSet rset = null;
       String query = OrphansDb.getOrphanedSubfamiliesQuery(source);
       try {      
          //s_log.warn("deleteOrphanedSubfamiliesFromSource() source:" + source + " query:" + query);
      	  stmt = DBUtil.getStatement(getConnection(), "OrphansDb.deleteOrphanedSubfamiliesFromSource()");
          rset = stmt.executeQuery(query);
          while (rset.next()) {
            String subfamily = rset.getString("subfamily");
            s_log.warn("deleteOrphanedSubfamiliesFromSource(" + source + ") subfamily:" + subfamily);
            if (hasDescEdit(getConnection(), subfamily)) {
              continue;
            }            
            
            s_log.debug("deleteOrphanedSubfamiliesFromSource() subfamily:" + subfamily);
            deleteTaxon(subfamily);
          }     
      } catch (SQLException e) {
          s_log.warn("deleteOrphanedSubfamiliesFromSource() e:" + e);
          throw e;
      } finally {
          DBUtil.close(stmt, rset, "OrphansDb.deleteOrphanedSubfamiliesFromSource()");
      }	
    }      
    

    public static String getOrphanAlternatesQuery() {
        String orphanAlternatesQuery = "select source, taxon_name, (select GROUP_CONCAT(distinct access_group) from specimen where specimen.taxon_name = taxon.taxon_name) as access_groups" 
            + " , (select count(*) from specimen where specimen.taxon_name = taxon.taxon_name) specimen_count" 
            + " , (select count(distinct access_group) from specimen where specimen.taxon_name = taxon.taxon_name) specimen_owner_count" 
            + " , parent_taxon_name, taxarank, created, insert_method "
            + " from taxon " 
            + " where parent_taxon_name not in (select taxon_name from taxon) " 
            + " and source like 'specimen%' " 
            + " and family = 'formicidae' " 
            + " and parent_taxon_name not like '%(formicidae)%'"
            + " order by source";
        return orphanAlternatesQuery;
    }    
    public ArrayList<Taxon> getOrphanAlternatesList() throws SQLException {
            ArrayList orphanTaxonList = new ArrayList();
                        
          Statement stmt = null;
          ResultSet rset = null;
          try {
            String query = OrphansDb.getOrphanAlternatesQuery();
            stmt = DBUtil.getStatement(getConnection(), "OrphansDb.getOrphanAlternatesList()");            
            rset = stmt.executeQuery(query);
            s_log.debug("OrphansDb.getOrphanAlternatesQuery() query:" + query);
            while (rset.next()) {
                String taxonName = rset.getString("taxon_name");
                 
                //s_log.warn("orphan() q:" + query);
                Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);
                orphanTaxonList.add(taxon);
            }
        } finally {
            DBUtil.close(stmt, rset, "OrphansDb.getOrphanAlternatesList()");
        }
        return orphanTaxonList;
    }
            
    /* This code is called from SpecimenUploadFull in the case where a taxon has moved due to taxon name change.  */
    public void moveTaxonSupportingDataToAlternate(String taxonName, String oldTaxonName) throws SQLException {
        s_log.debug("moveTaxonAndSupportingData() taxon:" + taxonName + " oldTaxonName:" + oldTaxonName);
        //moveSupportingDataGeneric(taxonName, oldTaxonName, "favorite_images", "specimen", "project_name");
        moveSupportingDataGeneric(taxonName, oldTaxonName, "description_edit", "title");
        moveSupportingDataGeneric(taxonName, oldTaxonName, "proj_taxon", "project_name");

// taxon_prop? bioregion_taxon, etc... ???

        //moveSupportingDataGeneric(taxonName, oldTaxonName, "taxon", "taxon_name", null);
        nowDeleteTaxon(oldTaxonName);
    }     
    private void nowDeleteTaxon(String taxonName) throws SQLException {
      // now just to distinguish from deleteTaxon.  We do not update because parent name is wrong.  Must insert elsewhere.
      // We could get the proper parent from the taxon_name.  Then we could move it.  But we don't need to.  
      // Specimen files will be correct.  All good.
        Statement stmt = null;
        String update = null;
        try {
          s_log.debug("nowDeleteTaxon() taxon:" + taxonName);
          stmt = DBUtil.getStatement(getConnection(), "OrphansDb.nowDeleteTaxon()");
          try {
            update = "delete from taxon where taxon_name = '" + taxonName + "'";
            stmt.executeUpdate(update);    
          } catch (SQLIntegrityConstraintViolationException e) {
            // ignore.
          }
        } finally {
          LogMgr.appendLog("moveTaxonAndSupportingTaxa.log", update);
          DBUtil.close(stmt, "OrphansDb.nowDeeleteTaxon()");
        }     
    
    }    
    private void moveSupportingDataGeneric(String taxonName, String oldTaxonName, String table, String column1) throws SQLException {
        moveSupportingDataGeneric(taxonName, oldTaxonName, table, column1, null);  
    }    
    private void moveSupportingDataGeneric(String taxonName, String oldTaxonName, String table, String column1, String column2) throws SQLException {
      // See if there are any records to move
        Statement stmt = null;
        ResultSet rset = null;
        String query = "select " + column1;
        if (column2 != null) query += ", " + column2;
        query += " from " + table + " where taxon_name = '" + oldTaxonName + "'";
        try { 
            stmt = DBUtil.getStatement(getConnection(), "OrphansDB.moveSupportingDataGeneric()");
            rset = stmt.executeQuery(query);
            while (rset.next()) {
              if (table.equals("taxon")) s_log.warn("moveTaxonAndSupportingDataToAlternate() taxon:" + taxonName + " oldTaxonName:" + oldTaxonName);
              String value1 = rset.getString(column1);
              String value2 = null;
              if (column2 != null) value2 = rset.getString(column2);
              updateFailGraceful(taxonName, table, column1, value1, column2, value2, oldTaxonName);
            }
        } finally {
            DBUtil.close(stmt, rset,  "OrphansDB.moveSupportingDataGeneric()");
        }    
    }  
    private void updateFailGraceful(String taxonName, String table, String alternateColumn, String alternateValue
      // Move the records, or if they already exist, just delete them
      , String alternateColumn2, String alternateValue2, String oldTaxonName) throws SQLException {
        Statement stmt = null;
        String update = null;
        try {
          stmt = DBUtil.getStatement(getConnection(), "OrphansDb.updateFailGraceful()");
         
          try {
            update = "update " + table + " set taxon_name = '" + taxonName + "' where taxon_name = '" + oldTaxonName + "'";
            if (alternateColumn != null && alternateValue != null) update += " and " + alternateColumn + " = '" + alternateValue + "'";
            if (alternateColumn2 != null && alternateValue2 != null) update += " and " + alternateColumn2 + " = '" + alternateValue2 + "'";
            //if (true) s_log.warn("updateFailGraceful() update:" + update); else
            stmt.executeUpdate(update);    
          } catch (SQLIntegrityConstraintViolationException e) {
            // If a primary key conflict, then delete the old.
            update = "delete from " + table + " where taxon_name = '" + oldTaxonName + "'";
            if (alternateColumn != null && alternateValue != null) update += " and " + alternateColumn + " = '" + alternateValue + "'";
            if (alternateColumn2 != null && alternateValue2 != null) update += " and " + alternateColumn2 + " = '" + alternateValue2 + "'";
          //if (true) s_log.warn("updateFailGraceful() update:" + update); else
            stmt.executeUpdate(update);    
          }
        } finally {
          LogMgr.appendLog("moveTaxonAndSupportingTaxa.log", update);
          DBUtil.close(stmt, "OrphansDb.updateFailGraceful()");
        }     
    }

               
}

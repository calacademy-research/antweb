package org.calacademy.antweb.home;

import java.util.*;
import java.sql.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.calacademy.antweb.curate.speciesList.*;

public class SpeciesListDb extends AntwebDb {

    private static Log s_log = LogFactory.getLog(SpeciesListDb.class);

    public SpeciesListDb(Connection connection) {
      super(connection);
    }
    
    public ArrayList<String> fetchMappableSpecimenLists(Login login) throws SQLException {
      
      ArrayList<String> refListList = new ArrayList<>(); //(ArrayList<String>) speciesListList.clone();
      
      int loginId = login.getId();
        
      //s_log.info("fetchProjects() groupId:" + groupId);        
      Statement stmt = null;
      ResultSet rset = null;
      try {
        stmt = DBUtil.getStatement(getConnection(), "addMappableSpecimenLists()");

          String theQuery;
          if (login.isAdmin()) {
            theQuery = "select distinct source from taxon where source like 'specimen%'";
          } else {            
            theQuery = "select distinct source from taxon where source = 'specimen" + login.getGroupId() + ".txt'";
            //theQuery = "select distinct source from taxon where source like 'specimen%' and source like '%" + login.getGroupId() + ".txt'";
          }
          rset = stmt.executeQuery(theQuery);
          String source;

          while (rset.next()) {
            source = (String) rset.getObject("source");
            //s_log.info("fetchProjects() name:" + projectName + " title:" + projectTitle);   
            //if (!"specimen1.txt".equals(source)) 
              refListList.add(source);
          }
      } catch (SQLException e) {
         s_log.error("addMappableSpecimenLists() e:" + e + " loginId:" + loginId + ": ");
         AntwebUtil.logStackTrace(e);
         throw e;
      } finally {
        DBUtil.close(stmt, rset, "addMappableSpecimenLists()");
      }

      return refListList;
    }
    
// -----------------------------------------------------------------------------------    

  // Called from the Species List Tool
  public ArrayList<Taxon> getSpeciesList(String displaySubfamily, String speciesListName)
    throws SQLException {
    //if (speciesListName == null) return null; // Will break code.
    
    if (Project.isProjectName(speciesListName)) {
       return getProjectSpeciesList(displaySubfamily, speciesListName);
    } else {
       return getGeolocaleSpeciesList(displaySubfamily, speciesListName);
    }
  }

  public ArrayList<Taxon> getSpeciesList(String query) {
    ArrayList<Taxon> speciesList = new ArrayList<>();

    Statement stmt = null;
    ResultSet rset = null;
    try {
      stmt = DBUtil.getStatement(getConnection(), "getSpeciesList()");
  
      rset = stmt.executeQuery(query);

      String lastTaxonName = null; // A second distinct function.  Adding specimen_count handled.
      while (rset.next()) {
        String taxonName = rset.getString("taxon_name");
        if (taxonName.equals(lastTaxonName)) continue;
        lastTaxonName = taxonName;
        
        int specimenCount = rset.getInt("specimen_count");
        Taxon taxon = new TaxonDb(getConnection()).getTaxon(taxonName);

        if (taxon != null) {
          taxon.setSpecimenCount(specimenCount);
          speciesList.add(taxon); // These are dummyTaxons.
        } else {
          s_log.error("getSpeciesList() taxon is null for taxonName:" + taxonName); // + " query:" + query);
        }
      }      
    } catch (Exception e) {
      s_log.warn("getSpeciesList() e:" + e + " query:" + query);
    } finally {
        DBUtil.close(stmt, rset, "getSpeciesList()");
    }      
    
    return speciesList;
  }

  public ArrayList<Taxon> getProjectSpeciesList(String displaySubfamily, String speciesListName) {

      String speciesListCriteria = "(project_name = '" + speciesListName + "')";   
    
      String displaySubfamilyCriteria = "";
      if (!"none".equals(displaySubfamily)) {
        displaySubfamilyCriteria = "   and t.taxon_name like '" + displaySubfamily + "%'";
      }

      String query = "select distinct pt.taxon_name, pt.specimen_count from proj_taxon pt" 
        + " join taxon t on pt.taxon_name = t.taxon_name " 
        + " where ( t.taxarank = 'species'"
        +    " or t.taxarank = 'subspecies' )"
        + " and " + speciesListCriteria 
        + " and t.family = 'formicidae'" // Added Sep19        
        + displaySubfamilyCriteria
        + " order by pt.taxon_name, pt.specimen_count desc ";
      
      //  removed by Mark Oct 27 *** works now on josh's machine.    t.genus, t.species, t.subspecies , 

      if (AntwebProps.isDevMode()) {
        //s_log.warn("getProjectSpeciesList() query:" + query);
        //AntwebUtil.logStackTrace();
        // Why twice each request?
        /*
	at org.calacademy.antweb.curate.speciesList.SpeciesListToolAction.getSpeciesList(SpeciesListToolAction.java:508)
	at org.calacademy.antweb.curate.speciesList.SpeciesListToolAction.setSpeciesListMappings(SpeciesListToolAction.java:331)
	at org.calacademy.antweb.curate.speciesList.SpeciesListToolAction.execute(SpeciesListToolAction.java:205)
	at org.apache.struts.action.RequestProcessor.processActionPerform(RequestProcessor.java:484)
    	*/        
      }

      return getSpeciesList(query);      
  }

  public ArrayList<Taxon> getGeolocaleSpeciesList(String displaySubfamily, String speciesListName) {

      Geolocale country = GeolocaleMgr.getGeolocale(speciesListName);
      if (country == null) {
        s_log.info("getGeolocaleSpeciesList() null country for speciesListName:" + speciesListName);
        return new ArrayList<>();
      }
      int geolocaleId = country.getId();
      String speciesListCriteria = "(geolocale_id = " + geolocaleId + ")";

      String displaySubfamilyCriteria = "";
      if (!"none".equals(displaySubfamily)) {
        displaySubfamilyCriteria = "   and t.taxon_name like '" + displaySubfamily + "%'";
      }

      String query = "select distinct gt.taxon_name, gt.specimen_count from geolocale_taxon gt" 
        + " join taxon t on gt.taxon_name = t.taxon_name " 
        + " where ( t.taxarank = 'species'"
        +    " or t.taxarank = 'subspecies' )"
        + " and " + speciesListCriteria 
        + " and t.family = 'formicidae'" // Added Sep19
        + displaySubfamilyCriteria
        + " order by gt.taxon_name, gt.specimen_count desc ";
      
      if (AntwebProps.isDevMode()) {
        //s_log.warn("getGeolocaleSpeciesList() query:" + query);
        //AntwebUtil.logStackTrace();
      }

      return getSpeciesList(query);
  }

  public String getProjectCriteria(String displaySubfamily, String mapSpeciesList1Name, String mapSpeciesList2Name, String mapSpeciesList3Name
      , String refSpeciesListType, String refSpeciesListName) {
      
      String projectCriteria = " ( false ";

      if (!Utility.isBlank(mapSpeciesList1Name)) {
        if (Project.isProjectName(mapSpeciesList1Name)) {
          projectCriteria += " or project_name = '" + mapSpeciesList1Name + "'";
        }
      }
      if (!Utility.isBlank(mapSpeciesList2Name)) {
        if (Project.isProjectName(mapSpeciesList2Name)) {
          projectCriteria += " or project_name = '" + mapSpeciesList2Name + "'";
        }
      }
      if (!Utility.isBlank(mapSpeciesList3Name)) {
        if (Project.isProjectName(mapSpeciesList3Name)) {
          projectCriteria += " or project_name = '" + mapSpeciesList3Name + "'";
        }
      }

      if (!Utility.isBlank(refSpeciesListType) && !refSpeciesListType.contains("search")) {
        if (refSpeciesListType.contains("specimen")) {
          projectCriteria += " or source = '" + refSpeciesListType + "'";      
        } if (refSpeciesListType.contains("antcatNames")) {
          /*
            Craft shortcut, not recommended. The "and 'antcatNames' = 'antcatNames'" clause is added so that we can make
            an exception below in getRefListSubfamilies() to not exclude incertae_sedis if from the antcatNames list.
          */
          //String antcatClause = " and 'antcatNames' = 'antcatNames'";
          String antcatClause = "";
          projectCriteria += " or t.taxon_name in (select t2.taxon_name from taxon t2 where status in ('valid', 'unidentifiable', 'homonym')" + antcatClause + ") ";
        } else {
          if (Project.isProjectName(refSpeciesListName)) {
            projectCriteria += " or project_name = '" + refSpeciesListName + "'";
          }
        }
      }
      projectCriteria += ")";
      //if (" ( false )".equals(projectCriteria)) projectCriteria = " (true) "; // if empty condition. A bit clumsy logically.
      return projectCriteria;
  }

  public String getGeolocaleCriteria(String displaySubfamily, String mapSpeciesList1Name, String mapSpeciesList2Name, String mapSpeciesList3Name
      , String refSpeciesListType, String refSpeciesListName) {

      String geolocaleCriteria = " ( false ";

      if (!Utility.isBlank(mapSpeciesList1Name)) {
        if (!Project.isProjectName(mapSpeciesList1Name)) {
          //s_log.warn("getCountryCriteria() mapSpeciesList1Name:" + mapSpeciesList1Name);      
          Geolocale geolocale = GeolocaleMgr.getGeolocale(mapSpeciesList1Name);
          if (geolocale == null) s_log.warn("getGeolocaleCriteria() country not found:" + mapSpeciesList1Name); else geolocaleCriteria += " or geolocale_id = " + geolocale.getId();
        }
      }
      if (!Utility.isBlank(mapSpeciesList2Name)) {
        if (!Project.isProjectName(mapSpeciesList2Name)) {
          Geolocale geolocale = GeolocaleMgr.getGeolocale(mapSpeciesList2Name);
          if (geolocale == null) s_log.warn("getGeolocaleCriteria() country not found:" + mapSpeciesList2Name); else geolocaleCriteria += " or geolocale_id = " + geolocale.getId();
        }
      }
      if (!Utility.isBlank(mapSpeciesList3Name)) {
        if (!Project.isProjectName(mapSpeciesList3Name)) {
          Geolocale geolocale = GeolocaleMgr.getGeolocale(mapSpeciesList3Name);
          if (geolocale == null) s_log.warn("getGeolocaleCriteria() country not found:" + mapSpeciesList3Name); else geolocaleCriteria  += " or geolocale_id = " + geolocale.getId();
        }
      }

      if (!Utility.isBlank(refSpeciesListType) && !refSpeciesListType.contains("search")) {
        if (refSpeciesListType.contains("specimen")) {
          geolocaleCriteria += " or source = '" + refSpeciesListName + "'";      
        } 
        if (refSpeciesListType.contains("antcatNames")) {
          /*
            Craft shortcut, not recommended. The "and 'antcatNames' = 'antcatNames'" clause is added so that we can make
            an exception below in getRefListSubfamilies() to not exclude incertae_sedis if from the antcatNames list.
          */
          //String antcatClause = " and 'antcatNames' = 'antcatNames'";
          String antcatClause = "";
          geolocaleCriteria += " or gt.taxon_name in (select t2.taxon_name from taxon t2 where status in ('valid', 'unidentifiable', 'homonym')" + antcatClause + ") ";
        } else {

          if (!Project.isProjectName(refSpeciesListName)) {

            //A.log("getGeolocaleCriteria() displaySubfamily:" + displaySubfamily + " refSpeciesListType:" + refSpeciesListType + " refSpeciesListName:" + refSpeciesListName );
            Geolocale geolocale = GeolocaleMgr.getGeolocale(refSpeciesListName);

            //A.log("getGeolocaleCriteria() geolocale:" + geolocale);

            if (geolocale == null) {
              s_log.debug("getGeolocaleCriteria() refSpeciesListName:" + refSpeciesListName + " geolocale:" + geolocale);
            } else {
              geolocaleCriteria += " or geolocale_id = " + geolocale.getId();
            }
          }
        }
      }

      geolocaleCriteria += ")";
      //A.log("getGeolocaleCriteria() geolocaleCriteria:" + geolocaleCriteria);
      
      return geolocaleCriteria;
  }

  public ArrayList<Taxon> getSumSpeciesList(String displaySubfamilyCriteria, String projectCriteria, String countryCriteria) {

      String projectQuery = "select distinct pt.taxon_name, pt.specimen_count" 
      + " , genus, species"
        + " from proj_taxon pt" 
        + " join taxon t on pt.taxon_name = t.taxon_name " 
        + " where ( t.taxarank = 'species'"
        +    " or t.taxarank = 'subspecies' )"
        + " and t.family = 'formicidae'"   // added Sep19 2016     
        + " and " + projectCriteria 
        + displaySubfamilyCriteria;
        
      String countryQuery = "select distinct gt.taxon_name, gt.specimen_count" 
      + " , genus, species"
        + " from geolocale_taxon gt" 
        + " join taxon t on gt.taxon_name = t.taxon_name " 
        + " where ( t.taxarank = 'species'"
        +    " or t.taxarank = 'subspecies' )"
        + " and t.family = 'formicidae'" //added Sep19 2016 
        + " and " + countryCriteria 
        + displaySubfamilyCriteria;
        
      String query = projectQuery + " union " + countryQuery + " order by genus, species, specimen_count desc "; // was taxon_name
      
      if (AntwebProps.isDevMode()) {
        s_log.warn("getSumSpeciesList() query:" + query);
        //AntwebUtil.logStackTrace();
      }
  
      return getSpeciesList(query);
  }

  public ArrayList<String> getRefListSubfamilies(String projectCriteria, String countryCriteria, ArrayList<Taxon> searchTaxa) 
    throws SQLException {
    
    ArrayList<String> refListSubfamilies = new ArrayList<>();

    String projectQuery;
    String countryQuery;
    String query;
    
    ResultSet rset = null;
    Statement stmt = DBUtil.getStatement(getConnection(), "SpeciesListDb.getRefListSubfamilies()");
    try {

        // Should these checks be not only for subfamilies, but for refList as well?

     projectQuery = "select distinct t.subfamily subfamily from taxon t " 
        + " join proj_taxon pt on t.taxon_name = pt.taxon_name " 
        + " where ( t.taxarank = 'species'"
        +    " or t.taxarank = 'subspecies' )"
        + " and " + projectCriteria 
        + " and t.family = 'formicidae'"
        + " and t.subfamily not like '(%'"
      // + " and t.subfamily != 'incertae_sedis'"
        ;
        
     // See getProjectCriteria() above. AntcatNames is added to the projectCriteria for this purpose.   
     //if (!projectCriteria.contains("antcatNames")) {
     //}
     
     countryQuery = "select distinct t2.subfamily subfamily from taxon t2 " 
        + " join geolocale_taxon gt on t2.taxon_name = gt.taxon_name " 
        + " where ( t2.taxarank = 'species'"
        +    " or t2.taxarank = 'subspecies' )"
        + " and " + countryCriteria 
        + " and t2.family = 'formicidae'"
        + " and t2.subfamily not like '(%'"
       // + " and t2.subfamily != 'incertae_sedis'"
        ;

      query = projectQuery + " union " + countryQuery
        + " order by subfamily ";
      
      s_log.debug("getRefListSubfamilies() projectCriteria:" + projectCriteria + " countryCriteria:" + countryCriteria);
      s_log.debug("getRefListSubfamilies() query:" + query);
      rset = stmt.executeQuery(query);

      while (rset.next()) {
        String subfamily = rset.getString("subfamily");
        refListSubfamilies.add(subfamily);
      }      
    } finally {
        DBUtil.close(stmt, rset, "SpeciesListDb.getRefListSubfamilies()");
    }      
     
    if (searchTaxa != null) {
      for (Taxon taxon : searchTaxa) {
        String subfamily = taxon.getSubfamily();
        if (!refListSubfamilies.contains(subfamily)) refListSubfamilies.add(subfamily);
      } 
    }
    Collections.sort(refListSubfamilies);
       
    return refListSubfamilies;
  }

    // When we delete a proj_taxon record and it is the last project taxon, we would like to
    // delete the taxon (it should be a morpho species).
    public String saveTaxonSet(String[] taxa, String[] chosen
      , ArrayList<String> oldChosenList, String speciesListName, Login login)
      throws SQLException {

        String message = "";
        if (chosen == null) chosen = new String[0];

        //A.log("SpeciesListDb.saveTaxonSet() speciesListName:" + speciesListName + " chosen:" + chosen + " oldChosen:" + oldChosenList);

        if (speciesListName == null || "null".equals(speciesListName) || "none".equals(speciesListName)
         || oldChosenList == null) {
          //A.log("saveTaxonSet() 1 speciesListName:" + speciesListName + " chosen:" + chosen.length + " oldChosen:" + oldChosenList.size());
          return message;
        }

        if (Utility.sameList(oldChosenList, chosen)) {
          s_log.debug("saveTaxonSet() 2 speciesListName:" + speciesListName + " chosen:" + chosen.length + " oldChosen:" + oldChosenList.size());
          return "";
        }

        ArrayList<String> chosenList = new ArrayList<>(Arrays.asList(chosen));
        //ArrayList<String> oldChosenList = new ArrayList(Arrays.asList(oldChosen));

		// Don't just delete.  Select * of them.  Before I delete it, see if it is the only taxon
		// in the project (aside from all antweb).  If it is (delete the taxon outright) assuming
		// it is a morpho species, and has no specimens.

		// No.  Can't.  Because we delete all proj_taxons and then re-insert them to simplify our
		// operation.    We can't just delete all and insert all.  We need to know which ones are
		// now to be deleted.  We need the old chosen and the new chosen.  Could we check the old
		// chosen against the database to verify no conflicting curator changes.
 
        
        EditableTaxonSetDb taxonSetDb = null;
        TaxonSetLogDb taxonSetLogDb = null;

        boolean isProjectAndNotGeolocale = Project.isProjectName(speciesListName);
	    if (isProjectAndNotGeolocale) {
 	      taxonSetDb = new ProjTaxonDb(getConnection());
 	      taxonSetLogDb = new ProjTaxonLogDb(getConnection());
 	    } else {
 	      taxonSetDb = new GeolocaleTaxonDb(getConnection()); 	    
    	  taxonSetLogDb = new GeolocaleTaxonLogDb(getConnection());
   	    }

        //A.log("saveTaxonSet() save! speciesListName:" + speciesListName + " chosen.length:" + chosen.length + " oldChosen.length:" + oldChosenList.size() + " taxonSetDb:" + taxonSetDb.getClass());

 	    taxonSetLogDb.archiveSpeciesList(speciesListName, login); 

        // Take a snapshot of the species list before modification.

        // if the chosen taxon is not in the oldChosen List, insert it.

        for (String taxonName : chosen) {

            String genus = Taxon.getGenusTaxonNameFromName(taxonName);
            String subfamily = Taxon.getSubfamilyFromName(taxonName);

            //A.log("saveTaxonSet() insert taxonName:" + taxonName + " taxonSetDb.class:" + taxonSetDb.getClass());

            Overview overview = OverviewMgr.getOverview(speciesListName);

            taxonSetDb.insert(overview, taxonName, "speciesListTool");
            taxonSetLogDb.removeDispute(speciesListName, taxonName);

            //A.log("saveTaxonSet() taxonName:" + taxonName + " contains:" + oldChosenList.contains(taxonName));
            if (!oldChosenList.contains(taxonName)) {
                s_log.debug("saveTaxonSet() added:" + taxonName);

                String prettySpeciesListName = SpeciesListMgr.getPrettyName(speciesListName);

                LogMgr.appendLog("speciesListTool.txt", "saveProjectTaxa - " + DateUtil.getFormatDateTimeStr() + " curatorId:" + login.getId()
                        + " added taxonName:" + taxonName + " from speciesListName:" + prettySpeciesListName);

                message += "<br>Taxon Project Mapping <font color=green>added</font>:<b>" + Taxon.getPrettyTaxonName(taxonName) + "</b> to " + prettySpeciesListName;
            }
        }

		// if an oldChosen taxon is not in the chosenList, remove it.
		int i = 0;
		for (String taxonName : oldChosenList) {
		  if (!chosenList.contains(taxonName)) {

			String genus = Taxon.getGenusTaxonNameFromName(taxonName);
			String subfamily = Taxon.getSubfamilyFromName(taxonName);

			// Get it before we delete it...
			// If we didn't need the source and rev we could skip the fetch...
			TaxonSet taxonSet = taxonSetDb.get(speciesListName, taxonName);
            taxonSet.setCuratorId(login.getId());

            //if (taxonName.contains("aureocuprea")) s_log.warn("saveTaxonSet() taxonName:" + taxonName + " not in chosenList:" + chosenList.size() + " taxonSet:" + taxonSet);

			if (taxonSet != null) {
			  //A.log("saveTaxonSet() insertDispute curatorId:" + login.getId());
			  taxonSetLogDb.insertDispute(taxonSet);
			} else {
			  s_log.warn("saveTaxonSet() TaxonSet not found to make a dispute.  Skip.  TaxonName:" + taxonName + " speciesListName:" + speciesListName + " taxonSetDb:" + taxonSetDb.getClass());
			  continue;
			}

  	 	    //A.log("saveTaxonSet() remove taxonName:" + taxonName);

			String prettySpeciesListName = SpeciesListMgr.getPrettyName(speciesListName);
			
			LogMgr.appendLog("speciesListTool.txt", "saveProjectTaxa - " + DateUtil.getFormatDateTimeStr() + " curatorId:" + login.getId() 
			 + " remove taxonName:" + taxonName + " from speciesListName:" + prettySpeciesListName);

			taxonSetDb.delete(speciesListName, taxonName);
			boolean hasSpecies = taxonSetDb.hasTaxonSetSpecies(speciesListName, genus);
			if (!hasSpecies) {
			  taxonSetDb.delete(speciesListName, genus);
			}
			boolean hasGenera = taxonSetDb.hasTaxonSetGenera(speciesListName, subfamily);
			if (!hasGenera) {
              s_log.debug("saveTaxonSet() removeFromTaxonSet subfamily:" + subfamily);
			  taxonSetDb.delete(speciesListName, subfamily);
			}  

			//A.log("saveProjectTaxa() hasSpecies:" + hasSpecies + " hasGenera:" + hasGenera);
			message += "<br>Taxon Project Mapping <font color=red>removed</font>:<b>" + Taxon.getPrettyTaxonName(taxonName) + "</b> from " + prettySpeciesListName;
		  }
		  ++i;
		}

        return message;
    }

    /* Get the list of species in the specified species list where the taxon is not valid,
       but the taxonName implies that it is not a morpho species
     */
    public ArrayList<String> noPassWorldantsSpeciesList(String speciesList1Name, String speciesList2Name
      , String speciesList3Name, String refSpeciesList) 
      throws SQLException
    {
        ArrayList<String> taxonNames = new ArrayList<>();
        String query = "select t.taxon_name from taxon t join proj_taxon pt " 
            + " on t.taxon_name = pt.taxon_name "            
            + " where "
            + "   ( pt.project_name = '" + speciesList1Name + "'" 
            + "  or pt.project_name = '" + speciesList2Name + "'" 
            + "  or pt.project_name = '" + speciesList3Name + "'" 
            + "  or pt.project_name = '" + refSpeciesList + "' )" 
            + " and (t.taxarank = 'species' or t.taxarank = 'subspecies')"  // Added subspecies on Nov 16, 2014
            + " and t.status != 'valid'";

        Statement stmt = DBUtil.getStatement(getConnection(), "SpeciesListDb.noPassWorldantsSpeciesList()");      
        
        stmt.execute(query);
        ResultSet rset = stmt.getResultSet();
        while (rset.next()) {
            String taxonName = rset.getString(1);

            if (AntwebProps.isDevMode() && "amblyoponinaemystrium mysticum".equals(taxonName)) {
              s_log.warn("noPassWorldantsSpeciesList() taxonName:" + taxonName + " isMorpho:" + Taxon.isMorpho(taxonName) + " isIndet:" + Taxon.isIndet(taxonName));
            }

            if (!Taxon.isMorpho(taxonName) && !Taxon.isIndet(taxonName))
              taxonNames.add(taxonName);
        }
        DBUtil.close(stmt, "SpeciesListDb.noPassWorldantsSpeciesList()");

        return taxonNames;
    }

}


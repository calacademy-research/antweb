package org.calacademy.antweb.curate.speciesList;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class SpeciesListMgr {

    private static final Log s_log = LogFactory.getLog(SpeciesListMgr.class);

    public static SpeciesListable getSpeciesList(String key) {
		if (Project.isProjectName(key)) {
		   return (SpeciesListable) ProjectMgr.getProject(key);
		} else {
		   return (SpeciesListable) GeolocaleMgr.getGeolocale(key);
		}		
    }

    public static String getName(String speciesListKey) {
      // If we search the specimen records for Adm1, we will need the name.
      // For Country or Adm1, results are the same as getPrettyName().
      
      String name = null;
      
	  if (!Utility.isNumber(speciesListKey)) {
	    name = speciesListKey;
      } else {
		  SpeciesListable speciesList = GeolocaleMgr.getGeolocale(speciesListKey);
		  if (speciesList != null) {
			name = speciesList.getName();
		  }        
      }
      //A.log("SpeciesListMgr.getName() speciesListKey:" + speciesListKey + " name:" + name);

      return name;
	}
	    
    public static String getPrettyName(String speciesListKey) {
      String prettyName = null;
      
	  if (Project.isProjectName(speciesListKey)) {
	    prettyName = Project.getPrettyName(speciesListKey);
      } else {
		  SpeciesListable speciesList = SpeciesListMgr.getSpeciesList(speciesListKey);
		  if (speciesList != null) {
			prettyName = speciesList.getName();
		  }        
      }
      
      //if ("Vermont".equals(speciesListKey)) {
      //  A.log("SpeciesListMgr.getPrettyName() SHOULD be ID? speciesListKey:" + speciesListKey + " prettyName:" + prettyName);
      //  AntwebUtil.logStackTrace();
      //}
      
      return prettyName;
	}
    
	// Kind of a utility method for use on the speciesListTool-body.jsp.
	public static boolean isDisplayYellowAnt(Taxon taxon, String speciesListName) {
	  if (Project.isProjectName(speciesListName)) {
		return false;
	  }
      boolean isDisplayYellowAnt = false;
      if (Utility.isNumber(speciesListName)) speciesListName = SpeciesListMgr.getName(speciesListName);
	  if ( (taxon.getCountryList() != null && taxon.getCountryList().contains(speciesListName) )
			 || (taxon.getAdm1List() != null && taxon.getAdm1List().contains(speciesListName) )           
		 ) { 
		 isDisplayYellowAnt = true;
	  }
	  //A.log("isDisplayYellowAnt() speciesListName:" + speciesListName + " return:" + isDisplayYellowAnt + " adm1List:" + taxon.getAdm1List());
	  return isDisplayYellowAnt;
	}
    
}


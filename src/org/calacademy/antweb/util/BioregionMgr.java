package org.calacademy.antweb.util;

import java.util.*;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class BioregionMgr {

    private static final Log s_log = LogFactory.getLog(BioregionMgr.class);

    private static ArrayList<Bioregion> s_bioregions;
    private static ArrayList<String> s_bioregionProjectNames;
        
    public static void populate(Connection connection) throws SQLException {
      populate(connection, false);
    }
    
    public static void populate(Connection connection, boolean forceReload) throws SQLException {
    
      if (AntwebProps.isDevMode()) {
        //s_log.warn("populate()");
        //AntwebUtil.logStackTrace();
      }
      
      if (!forceReload && (s_bioregions != null)) return;      
      
      BioregionDb bioregionDb = (new BioregionDb(connection));

      s_bioregions = bioregionDb.getBioregions(true); // deep copy
      //s_bioregionProjectNames = bioregionDb.getBioregionProjectNames();
    }

    public static ArrayList<Bioregion> getBioregions() {      
      return s_bioregions;
    }
    
    public static ArrayList<String> getBioregionNames() {
      ArrayList<String> bioregionNames = new ArrayList<>();
      for (Bioregion bioregion : s_bioregions) {
        bioregionNames.add(bioregion.getName());      
      }
      return bioregionNames;
    }

/*
As in Taxon table (coming from Antcat):
+-------------+
| bioregion   |
+-------------+
| NULL        |
| Afrotropic  |
| Nearctic    |
| Neotropic   |
| Malagasy    |
| Australasia |
| Palearctic  |
| Indomalaya  |
| Oceania     |
| Antarctic   |
+-------------+
10 rows in set (0.04 sec)

As in Bioregion and Specimen:;
+--------------+
| bioregion    |
+--------------+
| Neotropical  |
| Afrotropical |
| Malagasy     |
| Indomalaya   |
| Nearctic     |
| Australasia  |
| Palearctic   |
| NULL         |
| Oceania      |
| Antarctica   |
*/

    public static Bioregion getBioregion(String name) {    
      if ("Neotropic".equals(name)) name = "Neotropical";
      if ("Afrotropic".equals(name)) name = "Afrotropical";
      AntwebMgr.isPopulated();
      if (s_bioregions == null) return null;
      for (Bioregion bioregion : s_bioregions) {
          if (name.equals(bioregion.getName())) {
              return bioregion;
          }
      }

      //A.log("getBioregion(" + name + ") not found");

      return null;
    }
    
    public static String getValidBioregionName(String element) {
        if (element.contains("ntarctica")) element = "Antarctica";
		if (element.contains("frotropic")) element = "Afrotropical";
		if (element.contains("eotropic")) element = "Neotropical";
		if (element.contains("ndomalayan")) element = "Indomalaya";
		if (element.contains("ustralasian")) element = "Australasia";
		if (element.contains("earctical")) element = "Nearctic";
		if (element.contains("alearctical")) element = "Palearctic";
		boolean validBioregion = BioregionMgr.isValid(element);
        if (validBioregion) {
          return element;		
        } else {
          return null;
        }
    }

    public static boolean isValid(String bioregionName) { 
      boolean contains = getBioregionNames().contains(bioregionName);
      //A.log("isValid() contains:" + contains + " bioregionName:" + bioregionName);
      return contains;
    }
    public static ArrayList<String> getList(Connection connection) {
      return getBioregionNames();
    }
    public static String toList(Connection connection){
      return getBioregionNames().toString();
    }
    //public static ArrayList<String> getBioregionProjectNames() {
    //  return s_bioregionProjectNames;
    //}
    

	public static String getBioregionsDisplayHtml() {
	  String newLine = "<br>";
	  String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
      String display = "<h3>Bioregions</h3><br>";
	  for (Bioregion bioregion : s_bioregions) {
        display += newLine + bioregion.getTag();

	    for (Project project : bioregion.getProjects()) {
          String projectTag = project.getTag();
          display += newLine + indent + projectTag;
	    }
	  }
	  return display;
	}


}


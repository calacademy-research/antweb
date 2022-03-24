package org.calacademy.antweb.util;

import java.util.*;
import java.sql.Connection;

import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public abstract class TaxonPropMgr {

    private static final Log s_log = LogFactory.getLog(TaxonPropMgr.class);

    private static HashMap<String, String> s_taxaIntroducedMaps;

    public static void populate(Connection connection) {
      populate(connection, false);
    }

    public static void populate(Connection connection, boolean forceReload) {
      if (!forceReload && s_taxaIntroducedMaps != null) return;
      
      TaxonPropDb taxonPropDb = new TaxonPropDb(connection);      
      s_taxaIntroducedMaps = taxonPropDb.getTaxaIntroducedMaps();
    }

    public static HashMap<String, String> getTaxaIntroducedMaps() {
      return s_taxaIntroducedMaps;    
    }

	public static String getIntroducedMap(String taxonName) {
	  if (s_taxaIntroducedMaps == null) {
	    if (A.loopCount()) {
  	      s_log.warn("getIntroducedMaps() WST taxonName:" + taxonName);
	      AntwebUtil.logShortStackTrace();
	    }
	    return null;
	  }
      return (String) s_taxaIntroducedMaps.get(taxonName);    
    }
    
    public static boolean isIntroduced(String taxonName, String bioregion) {
      String introducedMap = TaxonPropMgr.getIntroducedMap(taxonName);
      if (introducedMap == null) return false;
      boolean isMapped = TaxonPropMgr.isMapped(introducedMap, bioregion);
      //A.log("TaxonPropMgr.isIntroduced() taxonName:" + taxonName + " bioregion:" + bioregion + " isMapped:" + isMapped + " introducedMap:" + introducedMap);
      return !isMapped;  // The checkmarks indicate where they are native. So not mapped is introduced.
    }

    public static boolean isIntroducedSomewhere(String taxonName) {
      String introducedMap = TaxonPropMgr.getIntroducedMap(taxonName);
      boolean isIntroducedSomewhere = introducedMap != null;
      return isIntroducedSomewhere;
    }

    public static boolean isMapped(String taxonPropMap, String bioregion) {     
      // bioregionMap and introducedMap are strings stored in the value field of the taxon_prop table. Values might look like this:
      // Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true

      if (taxonPropMap != null) {
     
        String[] mapArray = taxonPropMap.split(" ");

          for (String s : mapArray) {
              String[] pair = s.split(":");
              try {
                  if (pair[0].equals(bioregion)) return "true".equals(pair[1]);
              } catch (ArrayIndexOutOfBoundsException e) {
                  s_log.warn("isBioregionMapped() bioregionMap:" + taxonPropMap + " bioregion:" + bioregion + " e:" + e);
              }
          }
      }
      return false; 
    }  
 
    // Bioregion Map methods behave more like utility methods.
    // was Bioregion.isBioregionInMap()
    public static boolean isBioregionNative(String bioregion, String bioregionsMap) {      

      // default is true?
      if (bioregion == null || bioregionsMap == null) return true;
      
      String nativeBioregionsStr = TaxonPropMgr.getNativeBioregionsStr(bioregionsMap);

      //A.log("TaxonPropMgr.isBioregionNative() bioregion:" + bioregion + " bioregionsMap:" + bioregionsMap + " nativeBioregionsStr:" + nativeBioregionsStr);

      if (nativeBioregionsStr == null) return true;
        return nativeBioregionsStr.contains(bioregion);
    }
     
   // was Bioregion.displayBioregionsMap()
   // Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true'
    public static String getNativeBioregionsStr(String bioregionsMap) {
      return TaxonPropMgr.getNativeBioregionsStr("true", bioregionsMap);
    }    
    public static String getNonNativeBioregionsStr(String bioregionsMap) {
      return TaxonPropMgr.getNativeBioregionsStr("false", bioregionsMap);
    }
    public static String getNativeBioregionsStr(String isNative, String bioregionsMap) {
    
      String display = "";
      if (bioregionsMap == null || "".equals(bioregionsMap)) return "";
      
      for (String bioregion : Bioregion.list) {
		  if (bioregionsMap.contains(bioregion + ":" + isNative)) {
			if (!"".equals(display)) display += ", ";
			display += bioregion;
		  }
      }

      if (!"".equals(display)) {
        display += " bioregion";
        if (display.contains(",")) display += "s";
        return display;
      }
      return null;
    } 
        
    public static ArrayList<Bioregion> getNativeBioregionList(String bioregionsMap) {
      return TaxonPropMgr.getNativeBioregionsList("true", bioregionsMap);
    }             
    public static ArrayList<Bioregion> getNativeBioregionsList(String isNative, String bioregionsMap) {
      ArrayList<Bioregion> list = new ArrayList<>();
      if (bioregionsMap == null || "".equals(bioregionsMap)) return list;
      
      for (String bioregion : Bioregion.list) {
		  if (bioregionsMap.contains(bioregion + ":" + isNative)) {
			list.add(BioregionMgr.getBioregion(bioregion));
		  }
      }
      return list;
    } 
         
}    
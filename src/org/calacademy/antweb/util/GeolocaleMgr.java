package org.calacademy.antweb.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class GeolocaleMgr {

    private static final Log s_log = LogFactory.getLog(GeolocaleMgr.class);

    // This is a deep copy. Used for menus.
    private static ArrayList<Region> s_regions = null;
    
    // Used for most of the getGeolocale() methods.
    private static ArrayList<Geolocale> s_geolocales = null;
    
    // For Taxon Name Search Autocomplete    
    private static List<String> placeNamesList = null;
    
    public static void populate(Connection connection) {
      populate(connection, false);
    }
    
    public static boolean isInitialized() {
      return s_regions != null;
    }

    private static boolean s_oneAtATime = false;

    public static void populate(Connection connection, boolean forceReload) {
      //A.log("GeolocaleMgr.populate()");    
      //if (AntwebProps.isDevMode()) AntwebUtil.logShortStackTrace();

      java.util.Date startTime = new java.util.Date();     

      if (s_regions == null || forceReload) {
          if (!s_oneAtATime) {
            s_oneAtATime = true;
          } else {
            return;
          }

          GeolocaleMgr.populateDeep(connection, forceReload);
          //A.log("GeolocaleMgr.populate() after populateDeep()");    

          //A.log("populate() 1:" + AntwebUtil.millisSince(startTime) + " millis");
          
          GeolocaleMgr.populateShallow(connection, forceReload);
          //A.log("populate() after populateShallow() 2:" + AntwebUtil.millisSince(startTime) + " millis");

		  // For Place Name Search Autocomplete
		  placeNamesList = (new GeolocaleDb(connection)).getPlaceNames();

          s_oneAtATime = false;            
/*
          if (GeolocaleMgr.getGeolocale(8817) == null) {
            A.log("GeolocaleMgr.populate Sao not found");
          } else {
            A.log("GeolocaleMgr.populate Sao exists!"); 
          }
*/
      }      
      logDeep(s_regions);
    }

    public static void populateDeep(Connection connection, boolean forceReload) {
      if (!forceReload && (s_regions != null)) return;      
      
      GeolocaleDb geolocaleDb = new GeolocaleDb(connection);

      // java.util.Date startTime = new java.util.Date();     
      
      // deep crawl through subregion, countries and adm1.  Use for Georegion menu.
      s_regions = geolocaleDb.getRegions(true);       
    }
          
    public static void populateShallow(Connection connection, boolean forceReload) {

      if (!forceReload && (s_geolocales != null)) return;

      //A.log("populateShallow forceReload:" + forceReload + " s_geolocales:" + s_geolocales);

      s_geolocales = new ArrayList<Geolocale>();
      for (Region region : s_regions) {
        s_geolocales.add(region);
        for (Subregion subregion : region.getSubregions()) {
          s_geolocales.add(subregion);
          for (Country country : subregion.getAllCountries()) {
            s_geolocales.add(country);
            if (country.isIsland()) islands.add(country);
            //A.log("populateShallow() country:" + country);
            //if ("Albania".equals(country.toString())) A.log("populateShallow() country:" + country + " id:" + country.getId());
            for (Adm1 adm1 : country.getAllAdm1s()) {
              //if (adm1.getParent().equals("Venezuela")) A.log("populateShallow() country:" + country + " id:" + adm1.getId() + " adm1:" + adm1.getName());
              s_geolocales.add(adm1);
            }
          }
        }      
      }
      
      GeolocaleDb geolocaleDb = new GeolocaleDb(connection);
      //A.log("populateShallow() 1 c:" + countInstances("Albania", s_geolocales)); 
      
      // Get all of the countries that don't have parents set, as we won't find them in the above process.
      s_geolocales.addAll(geolocaleDb.getGeolocales("country", "none", false));        
      //A.log("populateShallow() 2 c:" + countInstances("Albania", s_geolocales));      

      Collections.sort(s_geolocales);
    }

private static int countInstances(String instance, ArrayList<Geolocale> geolocales) {
  if (instance == null) return 0;
  int c = 0;
  for (Geolocale g : geolocales) {
    if (instance.equals(g.toString())) {
      ++c;      
    }
  }
  return c;
}

    public static void logDeep(ArrayList<Region> regions) {
      int geolocaleCount = 0;
      for (Region region : regions) {
        ++geolocaleCount;
        //A.log("region:" + region + " size:" + region.getSubregions().size());    
        for (Subregion subregion : region.getSubregions()) {
          ++geolocaleCount;
          //A.log("  subregion:" + subregion + " size:" + subregion.getCountries().size());    
          for (Country country : subregion.getAllCountries()) {
            ++geolocaleCount;
            //if ("Venezuela".equals(country.getName())) A.log("    country:" + country + " size:" + country.getAllAdm1s().size());    
            for (Adm1 adm1 : country.getAllAdm1s()) {
              ++geolocaleCount;
                //if ("Venezuela".equals(adm1.getParent())) A.log("      adm1:" + adm1); 
            }
          }
        }
      }
      //A.log("logDeep() geolocaleCount:" + geolocaleCount);
    }

    // For Taxon Name Search Autocomplete
    public static List<String> getPlaceNames(String text) {
      if (placeNamesList == null) {
        A.log("GeolocaleMgr.getPlaceNames(text) initializing...");
        return null;
      }
      if (text == null) {
        s_log.warn("GeolocaleMgr.getPlaceNames(text) text is null");
        return null;
      }
      text = text.toLowerCase();
      //A.log("TaxonMgr.getPrettyTaxaNames(text) text:" + text + " prettyTaxaListSize:" + prettyTaxaNamesList.size());      
      String[] texts = text.split(" ");
      List<String> placeNamesSubset = new ArrayList<String>();
      int i = 0;
      for (String placeName : placeNamesList) {
        boolean containsAll = true;
        for (int j=0 ; j < texts.length ; ++j) {
          //log("getPlaceNames() text:" + text + " j:" + texts[j] + " placeName:" + placeName);
          if (!placeName.toLowerCase().contains(texts[j])) containsAll = false;
          if (!containsAll) break;
        }
        if (containsAll) {
          placeNamesSubset.add(placeName);
          ++i;
          if (i > 6000) break; // Greater than 8044 - the number of valid adm1 (5516)
        }  
      } 
      //A.log("TaxonMgr.getPrettyTaxaNames(q) returning size:" + prettyTaxaNamesSubset.size());
      return placeNamesSubset;
    }




    public static ArrayList<Geolocale> getValidChildren(String parent, String georank) {
	  //A.log("getValidChildren()");    
      for (Region region : GeolocaleMgr.getDeepRegions()) {
		//A.log("getValidChildren() region:" + region + " size:" + region.getSubregions().size());    
        if (Georank.region.equals(georank)) {
            if (region.getName().equals(parent)) {
              return region.getValidChildren(); //region.getSubregions();
            }
        } else {                
			//A.log("region:" + region + " size:" + region.getSubregions().size());    
			for (Subregion subregion : region.getSubregions()) {
              if (Georank.subregion.equals(georank)) {
                if (subregion.getName().equals(parent)) {
                  return subregion.getValidChildren(); //.getCountries();
                }
              } else {                
				//A.log("  subregion:" + subregion + " size:" + subregion.getCountries().size());    
				  for (Country country : subregion.getAllCountries()) {  // Do not excludes ones not live.
				  //if ("South America".equals(subregion.getName())) A.log("    country:" + country + " size:" + subregion.getCountries().size() + " georank:" + georank  + " parent:" + parent);    
					if (Georank.country.equals(georank)) {
					  if (country.getName().equals(parent)) {
					    return country.getValidChildren(); //.getAdm1s();
					  }
					} else {                
                      return null;
                    }
				  }
              }
			}
        }
      }
      return null;
    }
    
    // Deep copy.
    public static ArrayList<Region> getDeepRegions() {
      return s_regions;
    }


    // These need to be removed/changed. Affecting Species List Tool. Use Ids there.
    // Convenience method
    public static int getGeolocaleId(String name) {
      Geolocale geolocale = getGeolocale(name);
      if (geolocale == null) return 0;
      return geolocale.getId();
    }

    public static Geolocale getGeolocale(String name) {    
      if (!AntwebMgr.isPopulated()) return null;
//      try {
      if (Utility.isNumber(name)) {
        Integer i = Integer.valueOf(name);
        if (i != null) {
          Geolocale geolocale = getGeolocale(i.intValue());
          //A.log("GeolocaleMgr.getGeolocale(String) i:" + i + " geo:" + geolocale);
          return geolocale;
        }
      }

      Geolocale geolocale = getRegion(name);
      if (geolocale != null) return geolocale;
      
      geolocale = getSubregion(name);
      if (geolocale != null) return geolocale;
      
      geolocale = getCountry(name);
      if (geolocale != null) return geolocale;

      geolocale = getAdm1(name);
      if (geolocale != null) {
  	    //A.log("getGeolocale(name) BEING USED for adm1. Searching for Adm1 by name not allowed. Stop it. name:" + name);
        // This can be used in non-critical ways. For instance, Place Names search box. Will almost always be right.
        return geolocale;
      }
      
      return null;
    }

    // To be removed... because not unique.
	public static Geolocale getAdm1(String adm1Name) {
      for (Geolocale geolocale : s_geolocales) {
        if ("adm1".equals(geolocale.getGeorank()) && geolocale.getName().equals(adm1Name)) return geolocale;
      }
      return null;
    }


    public static Adm1 getAdm1(int geolocaleId) {
      Geolocale geolocale = getGeolocale(geolocaleId);
      if (geolocale != null) return (Adm1) geolocale;
      return null;
    }

    public static Geolocale getGeolocale(int geolocaleId) {
      if (!AntwebMgr.isPopulated()) return null;
      
      //A.log("getGeolocale() id:" + geolocaleId);

      int c = 0;
      for (Geolocale geolocale : s_geolocales) {
          ++c;
          //if (c % 1000 == 0) A.log("getGeolocale() c:" + c);
                
                
          if (geolocaleId == geolocale.getId()) {
              return geolocale;
          }
      }
      return null;
    }
    
    public static Geolocale getGeolocale(String name, String georank) {   

      if ("adm1".equals(georank)) {
		  s_log.warn("getGeolocale(name, georank) Illegal adm1 need parent to be unique");
		  // We should return her
	  }

      if (!"region".equals(georank) && !"subregion".equals(georank) && !"country".equals(georank)) {
        s_log.warn("getGeolocale() invalid call. Adm1 must include country.");
        AntwebUtil.logShortStackTrace();
      }
     
      if (name == null) {
        //s_log.error("getGeolocale() georank:" + georank + ", name is null, ");
        return null;
      }
      
      if (!AntwebMgr.isPopulated()) return null;


      //if (GeolocaleMgr.isIslandCountry(name) && "adm1".equals(georank)) {
      //  return GeolocaleMgr.getGeolocale(name, "country" );
      //}

      for (Geolocale geolocale : s_geolocales) {
          if (georank.equals(geolocale.getGeorank()) && name.equals(geolocale.getName())) {
               //A.log("getGeolocale() name:" + name + " georank:" + georank + " found:" + geolocale);
              return geolocale;
          }
      }
      //A.log("getGeolocale() name:" + name + " georank:" + georank + " not found.");
      return null;
    }
        
    public static ArrayList<Geolocale> getGeolocales() {
      return s_geolocales;
    }
    public static ArrayList<Geolocale> getLiveGeolocales() {
      ArrayList<Geolocale> geolocales = new ArrayList<Geolocale>();
      if (s_geolocales == null) return null; // Could happen due to server initialization.
      for (Geolocale geolocale : s_geolocales) {
        if (geolocale.isLive()) {
          geolocales.add(geolocale);
        }
      }
      return geolocales;
    }
    public static ArrayList<Geolocale> getGeolocales(String georank) {
      return GeolocaleMgr.getGeolocales(georank, null);
    }
    public static ArrayList<Geolocale> getGeolocales(String georank, boolean isValid) {
      if (isValid) return GeolocaleMgr.getGeolocales(georank, "true");
        else return GeolocaleMgr.getGeolocales(georank, "false");
    }    
    
    public static ArrayList<Geolocale> getGeolocales(String georank, String isValid) {
    
      //A.log("getGeolocales(" + georank + ", " + isValid + ") " + AntwebUtil.getShortStackTrace());    

      AntwebMgr.isPopulated();

      // A non "true" value for isValid will return all.
      ArrayList<Geolocale> geolocales = new ArrayList<Geolocale>();
      if (s_geolocales == null) return null; // Could happen due to server initialization.      
      for (Geolocale geolocale : s_geolocales) {
        if ((georank == null) || geolocale.getGeorank().equals(georank)) {

          if ( !"true".equals(isValid) || geolocale.getIsValid()) {
/*            
            if ("Albania".equals(geolocale.toString())) {
              A.log("getGeolocales(" + georank + ", " + isValid + ") IS " + geolocale.getName() + " " + geolocale.getId());
            } else {
              //A.log("getGeolocales(" + georank + ", " + isValid + ") NOT " + geolocale.getName() + " " + geolocale.getId());
            }
*/
            geolocales.add(geolocale);
          } 
        }
      }
      //A.log("getGeolocales(" + georank + ", " + isValid + ") geolocales:" + geolocales);
      return geolocales;
    }

    // Convenience methods:
    public static ArrayList<Geolocale> getAllCountries() {
      return GeolocaleMgr.getGeolocales("country");
    }
    
    public static ArrayList<Geolocale> getValidCountries() {
      ArrayList<Geolocale> validCountries = GeolocaleMgr.getGeolocales("country", true);
      Collections.sort(validCountries);
      return validCountries;
    }
    public static ArrayList<Geolocale> getValidAdm1s() {
      return GeolocaleMgr.getGeolocales("adm1", true);
    }

    public static ArrayList<Geolocale> getAllAdm1s() {
      return GeolocaleMgr.getGeolocales("adm1", false);
    }
        
    // Return deep copies
    public static Geolocale getDeepGeolocale(String name, String georank) {
      if (Geolocale.ADM1.equals(georank)) {
        s_log.warn("getDeepGeolocale() fix. Adm1 not supported.");
        AntwebUtil.logShortStackTrace();
      }
      if (Geolocale.REGION.equals(georank)) return GeolocaleMgr.getRegion(name);
      if (Geolocale.SUBREGION.equals(georank)) return GeolocaleMgr.getSubregion(name);
      if (Geolocale.COUNTRY.equals(georank)) return GeolocaleMgr.getCountry(name);
      // if (Geolocale.ADM1.equals(georank)) return GeolocaleMgr.getAdm1(name);  // Unsupported.
      return null;
    }
    public static Geolocale getDeepGeolocale(int id) {
      Geolocale geolocale = getGeolocale(id);
      if (geolocale == null) return null;
      return getDeepGeolocale(geolocale.getName(), geolocale.getGeorank());
    }
    public static Region getRegion(String name) {
      if (s_regions == null || name == null) return null;
      for (Region region : s_regions) {
        if (name.equals(region.getName())) return region;
      }
      return null;
    }
    
// To Be deprecated. Replace with getDeepRegions()    
    public static ArrayList<Geolocale> getRegions() {
      ArrayList<Geolocale> regionList = GeolocaleMgr.getGeolocales("region");
      return regionList;
    }
    public static ArrayList<Geolocale> getSubregions() {
      ArrayList<Geolocale> subregionList = GeolocaleMgr.getGeolocales("subregion");
      return subregionList;
    }
        
    public static Subregion getSubregion(String name) {
      if (name == null) return null;
      if (s_regions == null) return null;    
      for (Region region : s_regions) {
        for (Subregion subregion : region.getSubregions()) {
          if (name.equals(subregion.getName())) return subregion;
        }
      }
      return null;
    }
    
    public static Country getCountry(String name) {
      if (name == null) return null;
      if (s_regions == null) return null;
//A.log("r:" + s_regions);
      for (Region region : s_regions) {
        for (Subregion subregion : region.getSubregions()) {
//A.log("s:" + subregion);
          for (Country country : subregion.getAllCountries()) {
//A.log("c:" + country);
            if (name.equals(country.getName())) return country;
          }
        }
      }
      return null;
    }
    /*
    public static Country getLiveCountry(String name) {
      if (name == null) return null;
      if (s_regions == null) return null;
      for (Region region : s_regions) {
        if (region.getIsLive()) 
        for (Subregion subregion : region.getSubregions()) {
          if (subregion.getIsLive())
          for (Country country : subregion.getAllCountries()) {
            if (country.getIsLive())
            if (name.equals(country.getName())) return country;
          }
        }
      }
      return null;
    }
*/
    public static Country getCountryWithLowerCaseNoSpace(String name) {
      if (s_regions == null) return null;
      for (Region region : s_regions) {
        for (Subregion subregion : region.getSubregions()) {
          for (Country country : subregion.getAllCountries()) {
            String countryName = country.getName().toLowerCase();
            countryName = (new org.calacademy.antweb.Formatter()).removeSpaces(countryName);
            if (name.equals(countryName)) return country;
          }
        }
      }
      return null;
    }

    public static ArrayList<Geolocale> getCountries() {
      ArrayList<Geolocale> countryList = GeolocaleMgr.getGeolocales("country");
      return countryList;
    }
    // if (!AntwebMgr.isPopulated()) return null;
      
    public static Geolocale getAnyCountry(String countryName) {   
      if (countryName == null) return null;
      ArrayList<Geolocale> countries = getCountries();
      if (countries == null) return null; // Could happen due to server initialization.
      for (Geolocale geolocale : getCountries()) {
        if (countryName.equals(geolocale.getName())) return geolocale;
      }  
      return null;
    }
    
	public static Country getValidCountry(String country) {
	  /*
	    A country name may be for an invalid or non-UN country.  The (Antweb) valid country will be returned.
        I.E: "Iran (Islamic Republic of)" will return Iran.
	  */
   
      ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales(Georank.country, false);
      if (geolocales == null) return null;
      for (Geolocale geolocale : geolocales) {
        if (geolocale.getName().equals(country)) {
          if (geolocale.getIsValid()) {
             return (Country) geolocale;          
          } else {
            for (Geolocale geolocale2 : geolocales) {
              //A.log("getProjectNameFromValidCountry() 2 geolocale2.name:" + geolocale2.getName() + " validName:" + geolocale.getValidName());
              if (geolocale2.getName().equals(geolocale.getValidName())) {
                 return (Country) geolocale2;          
              }
            }
          }
        }      
      }
      return null;	  
	}                  


    // Convenience method
    public static int getCountryId(String name) {
      Geolocale country = getCountry(name); 
      if (country == null) return 0;
      return country.getId();
    }
        
    public static ArrayList<String> getValidCountryList() {
      // Used by generic list.do and ListAction.java.  Better to use objects (getValidCountries()).
      ArrayList<String> validCountryList = new ArrayList<String>();

      ArrayList<Geolocale> validCountries = GeolocaleMgr.getGeolocales("country", true);
      for (Geolocale country : validCountries) {
        validCountryList.add(country.getName());
      } 
      return validCountryList;
    }

    public static boolean isValid(String adm1Name, String countryName) {    
		Geolocale adm1 = GeolocaleMgr.getAdm1(adm1Name, countryName);  // Could be a country (Galapagos Islands).
		if (adm1 != null) {
		  if (adm1.getIsValid()) return true;
        }
        return false;
    }
    public static boolean isValid(String countryName) {    
		Geolocale country = GeolocaleMgr.getCountry(countryName);  // Could be a country (Galapagos Islands).
		if (country != null) {
		  if (country.getIsValid()) return true;
        }
        return false;
    }
    
    
    
/*
298 getCountries
250 getValidCountries

    public static boolean isValid(String name) {
      ArrayList<Geolocale> geolocales = getGeolocales();
      for (Geolocale geolocale : geolocales) {
A.log("isValid() " + name + " = " + geolocale.getName() + "?");
        if (geolocale.getName().equals(name)) {
          return true;
        }
      }      
      return false; 
    }
*/

    public static boolean isValidCountry(String name) {
      ArrayList<Geolocale> validCountries = getValidCountries();
      for (Geolocale geolocale : validCountries) {
        if (geolocale.getName().equals(name)) 
          return true;
      }      
      return false; 
    }
    public static boolean isValidAdm1(String name) {
      ArrayList<Geolocale> validAdm1s = getValidAdm1s();
      for (Geolocale geolocale : validAdm1s) {
        if (geolocale.getName().equals(name)) 
          return true;
      }      
      return false; 
    }
    
    public static ArrayList<Geolocale> getAdm1s() {
      return GeolocaleMgr.getGeolocales("adm1");
    }    

    public static ArrayList<Geolocale> getAdm1sWithSpecimen() {
      ArrayList<Geolocale> adm1sWithSpecimen = new ArrayList<Geolocale>();
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getGeolocales("adm1");
      for (Geolocale adm1 : adm1s) {
        if (adm1.getSpecimenCount() > 0) {
          adm1sWithSpecimen.add(adm1);
        }
      }
      return adm1sWithSpecimen;
    }    

    public static Geolocale inferCountry(String adm1Name) {
      // Will only return if unique
      ArrayList<Geolocale> adm1s = getAdm1s();
      if (adm1s == null) return null; // Could happen due to server initialization.
      Geolocale adm1 = null;
      for (Geolocale geolocale : adm1s) {
        if (adm1Name == null) return null;
        if (adm1Name.equals(geolocale.getName())) {
          if (adm1 != null) return null; // Didn't find a unique adm1.
          adm1 = geolocale;
        }
      }
      if (adm1 != null) {
        return getValidCountry(adm1.getParent());              
      }
      return null;
    }
    
    public static Geolocale getAnyAdm1(String adm1Name, String countryName) {
      if (!AntwebMgr.isPopulated()) return null;
      if (adm1Name == null) return null;
      if (countryName == null) {
        s_log.warn("getAnyAdm1(" + adm1Name + ", " + countryName + ") must included countryName.");
        return null;
      }
      ArrayList<Geolocale> adm1s = getAdm1s();
      if (adm1s == null) return null; // Could happen due to server initialization.
      for (Geolocale geolocale : adm1s) {
        if (adm1Name.equals(geolocale.getName()) && countryName.equals(geolocale.getParent())) {
          //A.log("getAnyAdm1(" + adm1Name + ", " + countryName + ") geolocale:" + geolocale + " bounds:" + geolocale.getBoundingBox());
          return geolocale;
        }
      }
      return null;
    }

    // Only from valid countries!
	public static Geolocale getAdm1(String adm1Name, String countryName) {
	  Geolocale country = GeolocaleMgr.getValidCountry(countryName);
	  if (country == null) return null; // Could be server initializing.
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
      if (adm1s == null) return null; // Could be server initializing
      for (Geolocale adm1 : adm1s) {
        if (adm1.getParent() == null) {
          //A.log("getAdm1() adm1:" + adm1.getName() + " parent:" + adm1.getParent());
          continue;
        }
        if (adm1.getName().equals(adm1Name) && adm1.getParent() != null && country != null && adm1.getParent().equals(country.getName())) {
          //A.log("GeolocaleMgr.getAdm1() adm1:" + adm1.getName() + " parent:" + adm1.getParent() + " country:" + country);
          return adm1; 
        }
      }
      return null;
    }
        
    
    // Will return the adm1, or the validName adm1 if the found adm1 is not valid.
	public static Geolocale getValidAdm1(String adm1, String country) {
      ArrayList<Geolocale> adm1s = GeolocaleMgr.getAdm1s();
      for (Geolocale loopAdm1 : adm1s) {
        if (loopAdm1.getName().equals(adm1) && loopAdm1.getParent() != null && loopAdm1.getParent().equals(country)) {
          if (loopAdm1.getIsValid()) {
             // if it is a valid adm1, return it.
//A.log("getValidAdm1(" + adm1 + ", " + country + ") validAmd1(1):" + loopAdm1);
             return loopAdm1;          
          } else {
            for (Geolocale loop2Adm1 : adm1s) {
              //A.log("getProjectNameFromValidCountry() 2 geolocale2.name:" + geolocale2.getName() + " validName:" + geolocale.getValidName());              
              if (loop2Adm1.getIsValid() && loop2Adm1.getName().equals(loopAdm1.getValidName()) && loopAdm1.getParent().equals(country)) {
                 // This is the validName adm1.
//A.log("getValidAdm1(" + adm1 + ", " + country + ") validAmd1(2):" + loop2Adm1);
                 return loop2Adm1;
              }
            }
          }
        }      
      }
      return null;	  
    }

/*
  // Not only valid anymore...?
    // Deep copy. Valid only. Was getAdm1().
    public static Adm1 getDeepAdm1(String countryName, String adm1Name) {
    
      if (s_regions == null) return null;

      int geolocaleCount = 0;
      
      for (Region region : s_regions) {
        ++geolocaleCount;
        for (Subregion subregion : region.getSubregions()) {
          ++geolocaleCount;
          for (Country country : subregion.getCountries()) {
            ++geolocaleCount;
            for (Adm1 adm1 : country.getAdm1s()) {
              ++geolocaleCount;
              if ("Central".equals(adm1Name)) A.log("getAdm1() countryName:" + countryName + " adm1:" + adm1 + " adm1Name:" + adm1.getName() + " parent:" + adm1.getParent());    
              if (adm1Name.equals(adm1.getName())) {
                if (countryName.equals(adm1.getParent())) {
                  if ("Central".equals(adm1.getName())) A.log("GeolocaleMgr.getAdm1() adm1:" + adm1 + " country:" + countryName);    
                  return adm1;
                }
              }
            }
          }
        }
      }

 //     ArrayList<Geolocale> adm1s = GeolocaleMgr.getAllAdm1s();
 //     for (Geolocale adm1 : adm1s) {
 //       if (adm1Name.equals(adm1.getName())) {
 //         return (Adm1) adm1;
 //       }
 //     }

      //if ("Sucre".equals(name)) A.log("GeolocaleMgr.getAdm1() Sucre not found. geolocaleCount:" + geolocaleCount);
      return null;
    }
*/
	public static String getRegionsDisplay() {
	  String newLine = "\r\n";
	  String indent = "  ";
      String display = "";
	  for (Region region : s_regions) {
        display += newLine + region;
	    for (Subregion subregion : region.getSubregions()) {
          display += newLine + indent + subregion;
	      for (Country country : subregion.getLiveCountries()) {
            display += newLine + indent + indent + country;
	        for (Adm1 adm1 : country.getAllAdm1s()) {
              display += newLine + indent + indent + indent + adm1;
	        }
	      }
	    }
	  }
	  return display;
	}

	public static String getGeoregionsDisplayHtml() {
	  String newLine = "<br>";
	  String indent = "&nbsp;&nbsp;&nbsp;&nbsp;";
      String display = "<h3>Georegions</h3><br>";
	  for (Region region : s_regions) {
        display += newLine + region.getTag();
	    for (Subregion subregion : region.getSubregions()) {
          display += newLine + indent + subregion.getTag();
	      for (Country country : subregion.getLiveCountries()) {
	        if (country.getIsValid()) {
              display += newLine + indent + indent + country.getTag();
              display += " - " + country.getBioregion() + " + " + country.getSpecimenCount();
            }
	        for (Adm1 adm1 : country.getValidAdm1s()) {
              display += newLine + indent + indent + indent + adm1.getTag();
	        }
	      }
	    }
	  }
	  return display;
	}

	public static String getGeolocaleBioregion(String countryName, String adm1Name) { 	  
      String useBioregion = null;

      Geolocale adm1 = getAdm1(adm1Name, countryName);
      if (adm1 != null) {
        useBioregion = adm1.getTheOneBioregion();
      }
      if (useBioregion == null) {
        Geolocale country = getCountry(countryName);
        if (country != null) {
          useBioregion = country.getTheOneBioregion();
        }
      }  
      //A.log("GeolocaleMgr.getGeolocaleBioregion() countryName:" + countryName + " adm1Name:" + adm1Name + " useBioregion:" + useBioregion);
      return useBioregion;
    }
      

	public static String getAdm1CountryData() {
      String adm1CountryData = "";

      ArrayList<Geolocale> geolocales = GeolocaleMgr.getGeolocales();        
      ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

      for (Region region : regions) { 

        for (Subregion subregion : region.getSubregions()) { 

          for (Country country : subregion.getAllCountries()) { 
            if (!country.isValid()) continue;
            for (Adm1 adm1 : country.getAllAdm1s()) {
              if (adm1.getIsValid()) {
                String line = adm1 + "\t" + country + "\r";
                adm1CountryData += line;
              }
            }
          }
        }
      }
      return adm1CountryData;	
	}


	public static String getAcceptedAdm1CountryData() {
      String adm1CountryData = "";

      ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

      for (Region region : regions) { 

        for (Subregion subregion : region.getSubregions()) { 

          for (Country country : subregion.getAllCountries()) { 
            if (!country.isValid()) continue;
            for (Adm1 adm1 : country.getAllAdm1s()) {
                if (!adm1.isValid() && adm1.getValidName() == null) continue;
                String line = adm1 + "\t" + country + "\r";
                adm1CountryData += line;
              }
          }
        }
      }
      return adm1CountryData;	
	}

    public static boolean isAccepted(Adm1 candidate) {
      ArrayList<Region> regions = GeolocaleMgr.getDeepRegions();

      for (Region region : regions) { 

        for (Subregion subregion : region.getSubregions()) { 

          for (Country country : subregion.getAllCountries()) { 
            if (!country.isValid()) continue;
            for (Adm1 adm1 : country.getAllAdm1s()) {
                if (!adm1.isValid() && adm1.getValidName() == null) continue;
                if (candidate.getId() == adm1.getId()) return true;
              }
          }
        }  
      }  
      return false;
    }

    /*
         Because Hawaii is in a separate bioregion from United States we treat it as a country
        (stored in Geolocale table with georank = "country".
    */
    
    private static ArrayList<Country> islands = new ArrayList<Country>();
    public static ArrayList<Country> getIslands() {
      return islands;
    }    
    public static Country getIsland(String element) {        
      for (Country island : islands) {
  	      if (island.getName().equals(element)) {
  	        return island;
  	      }
  	  }
  	  return null;
  	}
    public static boolean isIsland(String element) {        
        for (Country island : islands) {
  	      if (island.getName().equals(element)) {
            return true;
          }
        }
        return false;
    }

}


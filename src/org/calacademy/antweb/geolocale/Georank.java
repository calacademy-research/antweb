package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class Georank {

    private static Log s_log = LogFactory.getLog(Georank.class);
    
    
    public static String region = "region";
    public static String subregion = "subregion";
    public static String country = "country";
    public static String adm1 = "adm1";
    public static String adm2 = "adm2";

    public static String REGION = "region";
    public static String SUBREGION = "subregion";
    public static String COUNTRY = "country";
    public static String ADM1 = "adm1";
    public static String ADM2 = "adm2";

    public Georank() {
    }

    public static String getChild(String georank) {
      if (region.equals(georank)) return subregion;
      if (subregion.equals(georank)) return country;
      if (country.equals(georank)) return adm1;
      //if (adm1.equals(georank)) return adm2;
      return null;      
    }      

    public static String getParent(String georank) {
      if (subregion.equals(georank)) return region;
      if (country.equals(georank)) return subregion;
      if (adm1.equals(georank)) return country;
      if (adm2.equals(georank)) return adm1;
      return null;      
    }      

    public static String getParentHeading(String georank) {
      if (subregion.equals(georank)) return "Region";
      if (country.equals(georank)) return "Subregion";
      if (adm1.equals(georank)) return "Country";
      if (adm2.equals(georank)) return "Adm1";
      return null;      
    }      
    
    public static String getChildPluralRank(Geolocale geolocale) {
      //A.log("getChildPluralRank() geolocale:" + geolocale);
      if (geolocale instanceof Region) return getChildPluralRank(Georank.region);
      if (geolocale instanceof Subregion) return getChildPluralRank(Georank.subregion);
      if (geolocale instanceof Country) return getChildPluralRank(Georank.country);
      if (geolocale instanceof Adm1) return getChildPluralRank(Georank.adm1);
      
      return "";
    }    
    
    public static String getChildPluralRank(String georank) {
      return getPluralRank(getChildRank(georank));
    }    
    
    public static String getChildRank(String georank) {
		if (georank == null)
			return null;
		String retval = "";
		if (region.equals(georank)) {
			retval = "subregion";
		} else if (subregion.equals(georank)) {
			retval = "country";
		} else if (country.equals(georank)) {
			retval = "adm1";
		} else if (adm1.equals(georank)) {
			retval = "adm2";		
		}
		return retval;    
    }
    	
	public static String getPluralRank(String georank) {
		if (georank == null)
			return null;
		String pluralGeorank;
		if (region.equals(georank)) {
			pluralGeorank = "regions";
		} else if (subregion.equals(georank)) {
			pluralGeorank = "subregions";
		} else if (country.equals(georank)) {
			pluralGeorank = "countries";
		} else if (adm1.equals(georank)) {
			pluralGeorank = "adm1s";	
		} else if (adm2.equals(georank)) {
			pluralGeorank = "adm2s";	
		} else {
			pluralGeorank = georank;
		}
		return pluralGeorank;
	}    
    
    public static int getGeorankLevel(Geolocale geolocale) {
      if (geolocale instanceof Region) return getGeorankLevel(REGION);
      if (geolocale instanceof Subregion) return getGeorankLevel(SUBREGION);
      if (geolocale instanceof Country) return getGeorankLevel(COUNTRY);
      if (geolocale instanceof Adm1) return getGeorankLevel(ADM1);
      //if (geolocale instanceof Adm2) return getGeorankLevel(ADM2);
      return 0;
    }        
    public static int getGeorankLevel(String georank) {
      // These are relative and can be changed here safely.  Should end at 1.
      if (REGION.equals(georank)) return 5;
      if (SUBREGION.equals(georank)) return 4;
      if (COUNTRY.equals(georank)) return 3;
      if (ADM1.equals(georank)) return 2;
      if (ADM2.equals(georank)) return 1;
      return 0;
    }    
}

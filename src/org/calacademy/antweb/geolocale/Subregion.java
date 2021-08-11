package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Subregion extends Geolocale {

    private static Log s_log = LogFactory.getLog(Subregion.class);

    public Subregion() {
    }
    
    public String getRegion() {
        return getParent();
    }
    public void setRegion(String region) {
        setParent(region);
    }

// What was getCountries() is now getLiveCountries(). Fix codebase.

    public ArrayList<Country> getLiveCountries() {      
        ArrayList<Country> countries = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          if (geolocale.getIsLive())
            countries.add((Country) geolocale);
        }
        return countries;      
    }

    public ArrayList<Country> getAllCountries() {      
        ArrayList<Country> countries = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          countries.add((Country) geolocale);
        }
        return countries;      
    }
    
    public String fullReport() {
      String fullReport = "";
      fullReport += "\r\n  " + getName() + " {";

      for (Country country : getLiveCountries()) {
          fullReport += country.fullReport();
      }
      fullReport += "\r\n  }";

      return fullReport;
    }
 
    public String getHeading() {
      return "Subregion";
    }
    public String getPluralTargetDo() {
      return "subregions.do";
    }
    public String getChildrenHeading() {
      return "Countries";
    }
    
    public String getTargetDo() {
      return "subregion.do";
    }
    
    public String getParams() {
      return "subregionName=" + getName();   
    }      
    public String getSearchCriteria() {
      return "subregion=" + getName();   
    }  
    
        
}

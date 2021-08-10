package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

public class Region extends Geolocale {

    private static Log s_log = LogFactory.getLog(Region.class);
    
    public Region() {
    }

    public ArrayList<Subregion> getSubregions() {      
        ArrayList<Subregion> subregions = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          subregions.add((Subregion) geolocale);
        }
        return subregions;      
    }

    public String fullReport() {
      String fullReport = "";
      fullReport += "\r\n" + getName() + " {";
      
      for (Subregion subregion : getSubregions()) {
        fullReport += subregion.fullReport();
        A.log("fullReport() fullReport:" + fullReport + " subregion.fullReport:" + subregion.fullReport());      
      }
      fullReport += "\r\n}";
      return fullReport;
    }
    
    
// --- Implement Overviewable  

    public String getHeading() {
      return "Region";
    }
    public String getPluralTargetDo() {
      return "regions.do";
    }    
    public String getChildrenHeading() {
      return "Subregions";
    }
    
    public String getTargetDo() {
      return "region.do";
    }

    public String getParams() {
      return "regionName=" + getName();   
    }  
    
    public String getSearchCriteria() {
      return "region=" + getName();   
    }  
}

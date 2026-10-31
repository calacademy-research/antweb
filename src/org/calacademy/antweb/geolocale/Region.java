package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A region (geolocale row with georank region, e.g. Africa) with its subregions as children. Page region.do?name=
 * (region.jsp), list regions.do; parameters regionName= (lists, maps) and region= (searches). Its specimens are
 * those of its countries (Geolocale.regionSpecimensSql). Made by GeolocaleDb, held by GeolocaleMgr (the Georegions
 * menu). Docs: docs/flows/places.md, docs/data-model.md (geolocale).
 */
public class Region extends Geolocale {

    private static final Log s_log = LogFactory.getLog(Region.class);
    
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
        s_log.debug("fullReport() fullReport:" + fullReport + " subregion.fullReport:" + subregion.fullReport());
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

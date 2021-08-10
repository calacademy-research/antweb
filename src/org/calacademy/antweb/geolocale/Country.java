package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

public class Country extends Geolocale {

    private static Log s_log = LogFactory.getLog(Country.class);
    
    public Country() {
    }
    
    // What was getAdm1s() is now getValidAdm1s(). Fix codebase.

    public ArrayList<Adm1> getValidAdm1s() {      
      return getAdm1s(false);
    }

    public boolean hasAdm1() {
      //A.log("Country.hasAdm1() country:" + getName() + " getChildren:" + getChildren());
      if (getChildren() == null || getChildren().isEmpty()) return false;
      return true;
    }

    public boolean hasLiveValidAdm1() {
        //A.log("Country.hasAdm1() country:" + getName() + " getChildren:" + getChildren());
        if (getChildren() == null || getChildren().isEmpty()) return false;
        for (Geolocale geolocale : getChildren()) {
          if (geolocale.getIsLive() && geolocale.getIsValid())
            return true;
        }
        return false;
    }

    private ArrayList<Adm1> getAdm1s(boolean includeInvalids) {      
        ArrayList<Adm1> adm1s = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          if (includeInvalids || geolocale.getIsValid())
            adm1s.add((Adm1) geolocale);
        }
        return adm1s;      
    }

    public ArrayList<Adm1> getLiveAdm1s() {      
        ArrayList<Adm1> adm1s = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          if (geolocale.getIsLive())
            adm1s.add((Adm1) geolocale);
        }
        return adm1s;      
    }

    public ArrayList<Adm1> getAllAdm1s() {      
        ArrayList<Adm1> adm1s = new ArrayList<>();
        for (Geolocale geolocale : getChildren()) {
          adm1s.add((Adm1) geolocale);
        }
        return adm1s;      
    }
    
    // Map to superclass
    public String getSubregion() {
        return getParent();
    }
    public void setSubregion(String subregion) {
        setParent(subregion);
    }

    public String fullReport() {
      String fullReport = "";
      fullReport += "\r\n    " + getName() + " {";

      for (Adm1 adm1 : getValidAdm1s()) {
        fullReport += adm1.toString() + ",";
      }
      fullReport += "}";
      return fullReport;
    }

    public String getHeading() {
      if (this.isIsland()) {
        return "Island";   
      } 
      return "Country";
    }
    public String getPluralTargetDo() {
      return "countries.do";
    }
    public String getChildrenHeading() {
      return "Adm1s";
    }
    
    public String getTargetDo() {
      if (this.isIsland()) {
        return "island.do";
      }
      return "country.do";
    }

    public String getParams() {
      return "countryName=" + getName();   
    }  
    
    public String getSearchCriteria() {
      return "subregion=" + getParent() + "&country=" + getName();   
    }  
    
    
    public ArrayList<Specimen> sort(ArrayList children) {
        ArrayList<Specimen> sortedChildren = new ArrayList<>();

        String name = getName();
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (name.equals(s.getCountry())) {
            A.log("sort() name:" + name + " s:" + s.getCountry());
            sortedChildren.add(s);
          }
        }
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (!name.equals(s.getCountry())) {
            sortedChildren.add(s);          
          }
        }
        return sortedChildren;
    }

    public boolean isCanShowSpeciesListTool(Login accessLogin) {
      if (accessLogin == null || !getIsValid()) return false;
      
      // A.log("Country.isCanShowSpeciesListTool() isUseChildren:" + getIsUseChildren() + " name:" + getName() + " names:" + accessGroup.getCountryNames());
      
      if (!getIsUseChildren()
        && accessLogin.getCountryNames().contains(getName())
      ) return true;
      return false;
    }

    
}

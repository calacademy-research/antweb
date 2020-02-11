package org.calacademy.antweb.geolocale;

import java.sql.*;
import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

public class Adm1 extends Geolocale {

    private static Log s_log = LogFactory.getLog(Adm1.class);
    
    public Adm1() {
    }

    // SpeciesListable
    public String getKey() {
      // return getName();
      return "" + getId();
    }


    public String getCountry() {
        return getParent();
    }
    public void setCountry(String country) {
        setParent(country);
    }
/*
    public ArrayList<Adm2> getAdm2() {      
        ArrayList<Adm2> countries = new ArrayList<Adm2>();
        for (Geolocale geolocale : getChildren()) {
          adm2s.add((Adm2) geolocale);
        }
        return adm2s;      
    }
*/

    public String getTitle() {
      return getNameCommaCountry();
    }

    public String getNameCommaCountry() {
      return getName() + ", " + getParent();
    }
        
    public String toString() {
      return getName();
    }

    public String getPrettyGeorank() {
      return "state/province";
    }

    public String getHeading() {
      return "State/Province";
    }
    public String getChildrenHeading() {
      return null;
    }    
    public String getTargetDo() {
      return "adm1.do";
    }
    public String getPluralTargetDo() {
      return "adm1s.do";
    }
    public String getParams() {
      return "adm1Name=" + getName() + "&countryName=" + getParent();   
    }

    public String getSearchCriteria() {
      return "country=" + getParent() + "&adm1=" + getName();   
    }  
    
    public ArrayList<Specimen> sort(ArrayList children) {
        // The logic here will sort with the specified museum at the top.
        ArrayList<Specimen> sortedChildren = new ArrayList<Specimen>();

        String name = getName();
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (name.equals(s.getAdm1())) {
            sortedChildren.add(s);
          }
        }
        for (Object o : children) {
          Specimen s = (Specimen) o;
          if (!name.equals(s.getAdm1())) {
            sortedChildren.add(s);          
          }
        }
        return sortedChildren;
    }      

    public boolean isCanShowSpeciesListTool(Login accessLogin) {
      //A.log("isCanShowSpeciesListTool() ? group:" + accessGroup);
    
      if (accessLogin == null) return false;
      
      ArrayList<String> countryNames = accessLogin.getCountryNames();

      if (!countryNames.contains(getCountry())) {
        A.log("isCanShowSpeciesListTool() can't edit country:" + getCountry() + " so can edit adm1:" + getName());
        return false;
      }  // If you can't edit the country, you can't edit the adm1. True?

      //A.log("Adm1.isCanShowSpeciesListTool() name:" + getName() + " country:" + getCountry() + " names:" + accessLogin.getCountryNames());

  	  if (countryNames.size() == 0) {
	    s_log.warn("isCanShowSpeciesListTool() accessLogin:" + accessLogin + " country:" + getCountry()); // + " target:" + HttpUtil.getTarget(request));
	  }
	  
	  Country country = GeolocaleMgr.getCountry(getParent());
	  if (country != null && country.getIsUseChildren()) {
	    return true;
	  }
      
      //A.log("isCanShowSpeciesListTool this:" + this + " this.type:" + this.getClass() + " geolocales.type:" + geolocales.getClass() + " contained:" + geolocales.contains(this));
      
      // This will work for Adm1 listed in the login.
      ArrayList<SpeciesListable> geolocales = accessLogin.getGeolocales();
      for (SpeciesListable speciesListable : geolocales) {
        //A.log("isCanShowSpeciesListTool: speciesListable:" + speciesListable);        
        if (speciesListable.getName().equals(getName())) return true;
      }
      
      // if (getIsSpeciesListMappable()) ...
	  	  
      return false;
    }
    
    public String getThisPageTarget() {
	  String encodedAdm1 = java.net.URLEncoder.encode(getName());
	  String encodedCountry = null;
	  if (getParent() == null) {
	    encodedCountry = "null";
	  } else {
	    encodedCountry = java.net.URLEncoder.encode(getParent());
	  }
	  return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?name=" + encodedAdm1 + "&country=" + encodedCountry; 
    }
    
}


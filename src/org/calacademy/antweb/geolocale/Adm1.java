package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.*;

/**
 * An adm1 (state or province; geolocale row with georank adm1) of the country named by its parent. Title "&lt;name&gt;,
 * &lt;country&gt;"; page adm1.do?name=..&country=.. (adm1.jsp), list adm1s.do; parameters adm1Name=..&countryName=..
 * (lists, maps) and country=..&adm1=.. (searches). getKey() is the id (an adm1 name alone is not unique). sort()
 * puts the adm1's specimens first. The species list tool (isCanShowSpeciesListTool): any curator or administrator
 * (SpeciesListAccess.canEdit, Brian Fisher 2026-11-02; until then a login whose countries included the adm1's
 * country). Made by GeolocaleDb, held by GeolocaleMgr. Docs: docs/flows/places.md, docs/data-model.md
 * (geolocale).
 */
public class Adm1 extends Geolocale {

    private static final Log s_log = LogFactory.getLog(Adm1.class);
    
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
        ArrayList<Specimen> sortedChildren = new ArrayList<>();

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

    /** Any curator may edit any place's list (Brian Fisher, 2026-11-02; was: an adm1 of a country assigned to the login). */
    public boolean isCanShowSpeciesListTool(Login accessLogin) {
      return org.calacademy.antweb.util.SpeciesListAccess.canEdit(accessLogin, String.valueOf(getId()));
    }
    
    public String getThisPageTarget() {
	  String encodedAdm1 = HttpUtil.encode(getName());
	  String encodedCountry = null;
	  if (getParent() == null) {
	    encodedCountry = "null";
	  } else {
	    encodedCountry = HttpUtil.encode(getParent());
	  }
	  return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?name=" + encodedAdm1 + "&country=" + encodedCountry; 
    }
    
}


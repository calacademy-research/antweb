package org.calacademy.antweb.geolocale;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;

/**
 * A country, or an island (is_island: heading "Island", page island.do), of the geolocale table, with its
 * subregion as parent and its adm1s as children (valid, live or all). Page country.do?name= (country.jsp), list
 * countries.do; parameters countryName= (lists, maps) and subregion=..&country=.. (searches). sort() puts the
 * country's specimens first. The species list tool (isCanShowSpeciesListTool): a valid country not built from its
 * adm1s (is_use_children), for any curator or administrator (SpeciesListAccess.canEdit, 2026-11-02). Made by GeolocaleDb, held by GeolocaleMgr.
 * Docs: docs/flows/places.md, docs/data-model.md (geolocale).
 */
public class Country extends Geolocale {

    private static final Log s_log = LogFactory.getLog(Country.class);
    
    public Country() {
    }
    
    // What was getAdm1s() is now getValidAdm1s(). Fix codebase.

    public ArrayList<Adm1> getValidAdm1s() {      
      return getAdm1s(false);
    }

    public boolean hasAdm1() {
      //A.log("Country.hasAdm1() country:" + getName() + " getChildren:" + getChildren());
        return getChildren() != null && !getChildren().isEmpty();
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

    /**
     * The line on a country's page for the islands that are politically part of it and are countries of their own in
     * AntWeb (Brian Fisher, 2026-11-11: their specimens never count toward the political parent): "Hawaii has its own
     * page and is not counted here." (HTML, each island linked to its page; "" when there is none). The wording is
     * only here: change it in this method.
     */
    public static String ownPagesNote(List<Country> islands) {
      if (islands == null || islands.isEmpty()) return "";
      StringBuilder names = new StringBuilder();
      for (int i = 0; i < islands.size(); i++) {
        Country island = islands.get(i);
        if (i > 0) names.append(i == islands.size() - 1 ? " and " : ", ");
        names.append("<a href=\"").append(org.calacademy.antweb.util.Html.escAttr(island.getThisPageTarget())).append("\">")
            .append(org.calacademy.antweb.util.Html.esc(island.getName())).append("</a>");
      }
      return names + (islands.size() == 1 ? " has its own page and is not counted here." : " have their own pages and are not counted here.");
    }
    /** The countries whose ISO 3166-2 names are in another script: their adm1s are written in English, ISO's form kept
     *  as an old name. Must equal ENGLISH in tools/place/iso_states.py (tests/unit/test_adm1_names_note.py checks it). */
    public static final Set<String> ENGLISH_NAMED = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(
        "AE", "AF", "AM", "BG", "BH", "BY", "CN", "CY", "EG", "ER", "GE", "GR", "IL", "IQ", "IR", "JO", "JP", "KG", "KH",
        "KP", "KR", "KW", "KZ", "LA", "LB", "LK", "LY", "MK", "MM", "MN", "OM", "PS", "QA", "RS", "RU", "SA", "SD", "SY",
        "TH", "TJ", "TW", "UA", "YE", "IN")));

    /**
     * The line under a country's adm1s saying where their names come from (Brian Fisher, 2026-11-20: adopt ISO 3166-2,
     * "we just follow their usage", and say so on the site). Two countries keep newer units than ISO lists (Madagascar,
     * the Central African Republic); an island flagged as its own country (is_island) and a country with no ISO code have
     * none. Plain text; "" for none.
     */
    public static String adm1NamesNote(String isoCode, boolean island) {
      if (island) return "";
      isoCode = isoCode == null ? "" : isoCode.trim().toUpperCase(java.util.Locale.ROOT);   // as stored, any case (review of #406)
      if (isoCode.isEmpty()) return "";            // no ISO 3166 code: no claim about ISO 3166-2
      if ("MG".equals(isoCode)) return "Adm1s are Madagascar's regions (since 2004); ISO 3166-2 still lists the former provinces.";
      if ("CF".equals(isoCode)) return "Adm1s are the prefectures since the 2020 reform; ISO 3166-2 lists them as before it.";
      if (ENGLISH_NAMED.contains(isoCode)) return "Adm1 names follow ISO 3166-2, in English.";
      return "Adm1 names follow ISO 3166-2.";
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
            //A.log("sort() name:" + name + " s:" + s.getCountry());
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

        // Any curator may edit any place's list (Brian Fisher, 2026-11-02; was: the countries assigned to the login).
        return !getIsUseChildren() && org.calacademy.antweb.util.SpeciesListAccess.canEdit(accessLogin, String.valueOf(getId()));
    }

    
}

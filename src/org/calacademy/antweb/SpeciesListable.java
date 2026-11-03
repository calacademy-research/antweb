package org.calacademy.antweb;

/**
 * A place with a species list that curators edit: type (country, adm1, project), key, name, title, whether children
 * are used, and links to its page and list. Implemented by geolocale/Geolocale and Project; a login's lists
 * (Login.getProjects, getCountries, getGeolocales) are of this type, as are the lists of the Species List Tool.
 * Docs: docs/data-model.md (login_project, login_country).
 */
public interface SpeciesListable {
  // Implemented by Geolocale and Project

    String COUNTRY = "country";
    String ADM1 = "adm1";
    String PROJECT = "project";

    String getType();
    void setType(String type);

    String getKey();

    String getName();
    
    String getTitle();
    
    boolean getIsUseChildren();

    String getOverviewLink();

    String getListLink();
 
}

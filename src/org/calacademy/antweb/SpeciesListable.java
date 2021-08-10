package org.calacademy.antweb;

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

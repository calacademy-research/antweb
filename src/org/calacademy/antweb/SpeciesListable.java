package org.calacademy.antweb;

import java.util.Hashtable;
import java.sql.*;

public interface SpeciesListable {
  // Implemented by Geolocale and Project

    public static String COUNTRY = "country";
    public static String ADM1 = "adm1";
    public static String PROJECT = "project";

    public String getType();
    public void setType(String type);

    public String getKey();

    public String getName();
    
    public String getTitle();
    
    public boolean getIsUseChildren();

    public String getOverviewLink();

    public String getListLink();
 
}

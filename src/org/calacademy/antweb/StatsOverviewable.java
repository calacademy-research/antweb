package org.calacademy.antweb;

import java.util.Hashtable;
import java.sql.*;

public interface StatsOverviewable extends Overviewable {
  
  public int getNumSpeciesImaged();
  
  public int getSubfamilyCount();
  public int getGenusCount();
  public int getSpeciesCount();
  public int getSpecimenCount();
  public int getImageCount();
  public int getImagedSpecimenCount();
  
  public String getSpecimenSubfamilyDistJson();  
  public String getTaxonSubfamilyDistJson();  
  
  public String getChartColor();
  
}

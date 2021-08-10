package org.calacademy.antweb;

public interface StatsOverviewable extends Overviewable {
  
  int getNumSpeciesImaged();
  
  int getSubfamilyCount();
  int getGenusCount();
  int getSpeciesCount();
  int getSpecimenCount();
  int getImageCount();
  int getImagedSpecimenCount();
  
  String getSpecimenSubfamilyDistJson();
  String getTaxonSubfamilyDistJson();
  
  String getChartColor();
  
}

package org.calacademy.antweb;

/**
 * A place with the numbers of a statistics block (species imaged, subfamily, genus, species, specimen, image and
 * imaged-specimen counts, subfamily distributions as JSON, chart colour). Unused as of 2026-11-02: nothing
 * implements or refers to StatsOverviewable (src, web, test, struts-config.xml, web.xml).
 */
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

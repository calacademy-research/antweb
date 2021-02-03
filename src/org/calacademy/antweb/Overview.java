package org.calacademy.antweb;

import java.util.*;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.geolocale.*;

// Overview objects are Museum and LocalityOverview(Bioregion, Project, Geolocale).
public abstract class Overview implements Overviewable, Headerable, Describable {
    
    protected int subfamilyCount;
    protected int genusCount;
    protected int speciesCount;
    private int endemicSpeciesCount;
    private int introducedSpeciesCount;
    private int validSpeciesCount;
    private int specimenCount;
    private int imageCount;
    private int imagedSpecimenCount;
    private transient String specimenSubfamilyDistJson;  
    private transient String taxonSubfamilyDistJson;  
    private String chartColor;
    private String parentName;
        
    public static String selectSeparator = "<li>____________________________</li>";

    // Will be null for most overviews.  Project returns self.  Geolocale of type Country
    // or ADM1 will return the associated project, if it exists.
    
    public Project getProject() {
      return null;
    }
    
    public String getParentName() {
      return parentName;
    }
    public void setParentName(String parentName) {
      this.parentName = parentName;
    }
    
    public int getSubfamilyCount() {
        return subfamilyCount;
    }
    public void setSubfamilyCount(int subfamilyCount) {
        this.subfamilyCount = subfamilyCount;
    }

    public int getGenusCount() {
        return genusCount;
    }
    public void setGenusCount(int genusCount) {
        this.genusCount = genusCount;
    }

    public int getSpeciesCount() {
        return speciesCount;
    }
    public void setSpeciesCount(int speciesCount) {
        this.speciesCount = speciesCount;
    }    

    public int getEndemicSpeciesCount() {
        return endemicSpeciesCount;
    }
    public void setEndemicSpeciesCount(int endemicSpeciesCount) {
        this.endemicSpeciesCount = endemicSpeciesCount;
    }    

    public int getIntroducedSpeciesCount() {
        return introducedSpeciesCount;
    }
    public void setIntroducedSpeciesCount(int introducedSpeciesCount) {
        this.introducedSpeciesCount = introducedSpeciesCount;
    }    

    public int getValidSpeciesCount() {
        return validSpeciesCount;
    }
    public void setValidSpeciesCount(int validSpeciesCount) {
        this.validSpeciesCount = validSpeciesCount;
    }
    
    public int getSpecimenCount() {
        return specimenCount;
    }
    public void setSpecimenCount(int specimenCount) {
        this.specimenCount = specimenCount;
    }
     
    public int getImageCount() {
        return imageCount;
    }
    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }
    // For instance:
    // select sum(image_count) from geolocale_taxon where geolocale_id = 19 
    //   and taxon_name in (select taxon_name from taxon where rank = "species" or rank = "subspecies");
    public int getNumSpeciesImaged() { return getImageCount(); }
      

    public int getImagedSpecimenCount() {
      return imagedSpecimenCount;
    }
    public void setImagedSpecimenCount(int count) {
      imagedSpecimenCount = count;
    }
    
    public String getSpecimenSubfamilyDistJson() {
        return specimenSubfamilyDistJson;
    }
    public void setSpecimenSubfamilyDistJson(String specimenSubfamilyDistJson) {
        this.specimenSubfamilyDistJson = specimenSubfamilyDistJson;
    }    
    
    public String getTaxonSubfamilyDistJson() {
        return taxonSubfamilyDistJson;
    }
    public void setTaxonSubfamilyDistJson(String taxonSubfamilyDistJson) {
        this.taxonSubfamilyDistJson = taxonSubfamilyDistJson;
    }    
    
    public String getChartColor() {
        return chartColor;
    }
    public void setChartColor(String chartColor) {
        this.chartColor = chartColor;
    }       
    
    public ArrayList sort(ArrayList children) {
        return children;
    }
    
    public String getKeyStr() {
      if (this instanceof Museum) {
        return ((Museum) this).getCode();
      } else {
        return getName();
      }
    }    
    
    public String getTitle() {
      return getName();
    }
    public String toString() {
      return getName();
    } 

    public String getRecalcLink() {
      return "";    
    }

    public String getCountCrawlLink() {
      return "";
    }    

    public String getGoogleMapFunctionLink() {
      return "";
    }    
    
    public String getGoogleMapFunctionLinkDesc() {
      if (!"".equals(getGoogleMapFunctionLink())) return "(Generate Google Map Function).";
      return "";
    }
    
    public String getThisPageTarget() {
      return AntwebProps.getDomainApp() + "/" + getTargetDo() + "?name=" + getName();    
    }
    
    public String getChangeViewOptions(String taxonName, String otherUrl, Connection connection) {
      return ""; //<li>Overview</li>";
    }

    public String getChangeViewOptions(String otherUrl) {
      return "";
    }
    
    // To be deprecated in favor of getValidChildrenListDisplay and getLiveChildrenListDisplay
    public String getChildrenListDisplay() {      
      // Will be overridden by Geolocales.  Only they contain hierarchy.
      return "";
    }
    // To be deprecated in favor of getValidChildrenListDisplay and getLiveChildrenListDisplay
    public String getAdminChildrenListDisplay() {      
      // Will be overridden by Geolocales.  Only they contain hierarchy.
      return "";
    }
  
    public String getChildrenListDisplay(String validOrLive, String overviewOrList, String rank) {      
      // Will be overridden by Geolocales.  Only they contain hierarchy.
      return "";
    }
    
/*    
    public String getValidChildrenListDisplay() {      
      // Will be overridden by Geolocales.  Only they contain hierarchy.
      return "";
    }
    public String getLiveChildrenListDisplay() {      
      // Will be overridden by Geolocales.  Only they contain hierarchy.
      return "";
    }
*/

    public boolean isCanShowSpeciesListTool(Login accessLogin) {
      A.log("Overview.isCanShowSpeciesListTool() FALSE group:" + accessLogin);
      return false;
    }
    
    public boolean isCanShowSpecimenTaxa() {
      // overridden by geolocale.  True for country or adm1
      return false;
    }    


    public String getOverviewCriteria() {
      String overviewCriteria = "";
      if (this instanceof Adm1) {
        overviewCriteria = " and specimen.adm1 = '" + getName() + "'";
      }
      if (this instanceof Country) {
        overviewCriteria = " and (specimen.country = '" + getName() + "' or specimen.island_country = '" + getName() + "')";
      }
      if (this instanceof Museum) {
        overviewCriteria = " and specimen.museum = '" + ((Museum) this).getCode() + "'";
      }
      if (this instanceof Bioregion) {
        overviewCriteria = " and specimen.bioregion = '" + getName() + "'";
      }
      
      A.log("getOverviewCritieria() this:" + this.getClass() + " criteria:" + overviewCriteria);
      return overviewCriteria;
    }

/*
    public String getLocalCriteria() {
      if (this instanceof Adm1) {
        return " and specimen.adm1 = '" + getName() + "'";
      }
      if (this instanceof Country) {
        return " and specimen.country = '" + getName() + "'";
      }
      return "";
    }
       */  
}
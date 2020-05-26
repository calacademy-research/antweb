package org.calacademy.antweb;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.geolocale.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public abstract class TaxonSet {

    private static Log s_log = LogFactory.getLog(TaxonSet.class);

    protected int subfamilyCount = 0;
    protected int genusCount = 0;
    protected int speciesCount = 0;
    protected int specimenCount = 0;
    private int globalChildCount = 0;
    protected int imageCount = 0;    
    protected boolean isEndemic = false;
        
    protected String taxonName;
    protected String rank;
    protected Timestamp created;

    private String source;
    
    private int curatorId;

    public static final String PROXY = "proxy";
        
    protected boolean exists = false;
    public boolean exists() {
      return exists;
    }
    
  /* Next Subtaxon functionality enables a taxon to report how many subtaxons it has.
     For instance, for formicidae family:
       26 subfamilies, 476 genera, 30000 species, 150000 specimens)
   */

    public TaxonSet() {
    }
    
    public abstract void init(Connection connection) throws SQLException ;
    
    public String getNextGlobalSubtaxon(String rank) {
      if (globalChildCount == 0 && !"specimen".equals(rank)) return "No " + Rank.getNextPluralRank(rank, 1); //"ex: No Specimens";
      return "<span class='numbers'>" + globalChildCount + "</span> " + Rank.getNextPluralRank(rank, 1);    
    }

    public String getNextSubtaxon() {
      return getNextSubtaxon(rank, 1);
    }
    public String getNextSubtaxon(int depth) {
      return getNextSubtaxon(rank, depth);
    }
    public String getNextSubtaxon(String rank, int depth) {
      if (depth > Rank.getRankLevel(rank)) return "";
      int subtaxonCount = getSubtaxonCount(rank, depth);
      if (subtaxonCount == -1) return "";
      //A.log("getNextSubtaxon rank:" + rank + " depth:" + depth + " count:" + subtaxonCount);      
      if (subtaxonCount == 0 && depth > 1) return "";
      if (subtaxonCount == 0 && !"specimen".equals(rank)) return "No " + Rank.getNextPluralRank(rank, depth); //"ex: No Specimens";
      return "<span class='numbers'>" + subtaxonCount + "</span> " + Rank.getNextPluralRank(rank, depth);
    }

    public int getSubtaxonCount(int depth) {
      return getSubtaxonCount(rank, depth);
    }
    public int getSubtaxonCount(String rank, int depth) {
      if (depth == 1) {
        if (Rank.FAMILY.equals(rank)) return subfamilyCount;
        if (Rank.SUBFAMILY.equals(rank)) return genusCount;
        if (Rank.GENUS.equals(rank)) return speciesCount;
        if (Rank.SPECIES.equals(rank)) return specimenCount; //globalChildCount; // was:       
        if (Rank.SUBSPECIES.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;      // added Feb 13, 2013
      } else if (depth == 2) {
        if (Rank.FAMILY.equals(rank)) return genusCount;
        if (Rank.SUBFAMILY.equals(rank)) return speciesCount;
        if (Rank.GENUS.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      } else if (depth == 3) {
        if (Rank.FAMILY.equals(rank)) return speciesCount;
        if (Rank.SUBFAMILY.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      } else if (depth == 4) {
        if (Rank.FAMILY.equals(rank)) return specimenCount; //globalChildCount; // was: specimenCount;
      }
      return -1;
    }
    
    public String getImageCountStr() {
      if (getImageCount() == 0) return "No Images";
      return "<span class='numbers'>" + getImageCount() + "</span> Images";
    }
        
    public String toString() {
      return "TaxonSet taxonName:" + getTaxonName() + " rank:" + rank
        + " subfamilyCount:" + getSubfamilyCount() + " genusCount:" + getGenusCount()
        + " speciesCount:" + getSpeciesCount() + " specimenCount:" + getSpecimenCount()
        + " imageCount:" + getImageCount() + " source:" + getSource()
        ;
    }

    public String getTaxonName() {
      return taxonName;
    }
    public void setTaxonName(String taxonName) {
      this.taxonName = taxonName;
    }

    public String getRank() {
      return rank;
    }
    public void setRank(String rank) {
      this.rank = rank;
    }

    public Timestamp getCreated() {
      return created;
    }
    public void setCreated(Timestamp created) {
      this.created = created;
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
    public int getSpecimenCount() {
        return specimenCount;
    }
    public void setSpecimenCount(int specimenCount) {
        this.specimenCount = specimenCount;
    }
    
    // Loaded during OverviewTaxon.init() from Allantweb values.
    // Global specimenCount in case of species or subspecies.
    public int getGlobalChildCount() {
        return globalChildCount;
    }
    public void setGlobalChildCount(int globalChildCount) {
        this.globalChildCount = globalChildCount;
    }  
    
    public int getImageCount() {
        return imageCount;
    }
    public void setImageCount(int imageCount) {
        this.imageCount = imageCount;
    }

    public boolean getIsEndemic() {
        return isEndemic;
    }
    public void setIsEndemic(boolean isEndemic) {
        this.isEndemic = isEndemic;
    }
        
    public String getSource() {
      return source;
    }
    public void setSource(String source) {
      this.source = source;
    }
    
    public int getCuratorId() {
        return curatorId;
    }
    public void setCuratorId(int curatorId) {
        this.curatorId = curatorId;
    }
        
    //public abstract String getSourceAnchor();    
    //public abstract String getSourceStr();
    public String getSourceStr() {
      return Source.getSourceStr(getSource());
    }

    public String getSourceDisplay() {
       String source = getSource();

       if (source == null) {
         if (this instanceof MuseumTaxon) {
           return ""; // Museum does not have source field.
         }
         String detail = "";
         if (this instanceof BioregionTaxon) detail += " bioregionTaxa:" + ((BioregionTaxon) this).getBioregionName();
         if (this instanceof GeolocaleTaxon) detail += " geolocaleTaxa:" + ((GeolocaleTaxon) this).getGeolocaleId();
         if (this instanceof ProjTaxon) detail += " projTaxa:" + ((ProjTaxon) this).getProjectName();
         s_log.warn("getSourceDisplay() effective source is null for getTaxonName:" + getTaxonName() + " source:" + source + " detail:" + detail);
         return "";
       }
       
       if (source != null && source.contains(PROXY)) {
         source = source.substring(PROXY.length());
         //A.log("getSourceDisplay() source:" + source);
       }

       return Source.getSourceDisplay(source);
    }

}

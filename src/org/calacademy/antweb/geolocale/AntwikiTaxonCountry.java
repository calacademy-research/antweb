package org.calacademy.antweb.geolocale;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AntwikiTaxonCountry implements Comparable<AntwikiTaxonCountry> {

    private static final Log s_log = LogFactory.getLog(AntwikiTaxonCountry.class);

    private int id = 0;
    private int rev = 0; 
    private String shortTaxonName;
    private String originalTaxonName;
    private String taxonName;
    private String country;
    private boolean isIntroduced = false;
    private Timestamp created;
    private String projectName;
    
    public AntwikiTaxonCountry() {
    }

        
    public int compareTo(AntwikiTaxonCountry other) {
        //A.log("compareTo() fullName:" + getFullName() + " vs " + other.getFullName());
        if (getTaxonName() == null) return 1;
        if (other.getTaxonName() == null) return 1;
        return getTaxonName().compareTo(other.getTaxonName());
        //return getTaxonName().compareTo(other.getTaxonName());
    }    
    
    public int getId() {
      return id;
    }
    public void setId(int id) {
      this.id = id;
    }

    public int getRev() {
      return rev;
    }
    public void setRev(int rev) {
      this.rev = rev;
    }
    
    public String getShortTaxonName() {
        return shortTaxonName;
    }
    public void setShortTaxonName(String shortTaxonName) {
        this.shortTaxonName = shortTaxonName;
    }

    public String getOriginalTaxonName() {
        return originalTaxonName;
    }
    public void setOriginalTaxonName(String originalTaxonName) {
        this.originalTaxonName = originalTaxonName;
    }

    public String getTaxonName() {
        return taxonName;
    }
    public void setTaxonName(String taxonName) {
        this.taxonName = taxonName;
    }

    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public boolean getIsIntroduced() {
        return isIntroduced;
    }
    public void setIsIntroduced(boolean isIntroduced) {
        this.isIntroduced = isIntroduced;
    }

    public Timestamp getCreated() {
        return created;
    }
    public void setCreated(Timestamp created) {
        this.created = created;
    }    
    
    public String getProjectName() {
      return this.projectName;
    }
    public void  setProjectName(String projectName) {
       this.projectName = projectName; 
    }
}


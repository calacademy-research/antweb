package org.calacademy.antweb.curate.speciesList;

import java.util.*;
import java.io.Serializable;
import java.sql.*;

import org.calacademy.antweb.*;
import org.calacademy.antweb.geolocale.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
        
import org.calacademy.antweb.util.*;

public class GeolocaleTaxonLogDetail extends GeolocaleTaxon implements Comparable<GeolocaleTaxonLogDetail>  {

    private static Log s_log = LogFactory.getLog(GeolocaleTaxonLogDetail.class);

    private int logId = 0;
    private Taxon taxon = null;

    public GeolocaleTaxonLogDetail() {
    }
    
    public GeolocaleTaxonLogDetail(String taxonName, int geolocaleId) {
    // Used for creating dummies to be used for comparison
      setTaxonName(taxonName);
      setGeolocaleId(geolocaleId);
    }
    
    public int compareTo(GeolocaleTaxonLogDetail o) {
        // Only operates on taxonName.  All details in a given ProjTaxonLog are assumed to be of the same species list.
        GeolocaleTaxonLogDetail other = (GeolocaleTaxonLogDetail) o;

        return Taxon.getPrettyTaxonName(getTaxonName()).compareTo(Taxon.getPrettyTaxonName(other.getTaxonName()));
    }
        
    public boolean equals(Object o) {
        // This is a bit sloppy as we are testing for equality on taxonName and projectName, but not date, or log_id.
        GeolocaleTaxonLogDetail other = (GeolocaleTaxonLogDetail) o;
        if (other.getTaxonName().equals(getTaxonName()))
            if (other.getGeolocaleId() == getGeolocaleId())
                return true;

        //s_log.warn("equals() taxonName:" + getTaxonName() + " other:" + other.getTaxonName() + " projectName:" + getProjectName() + " other:" + other.getProjectName());  
                
        return false;
    }   
    
    public String toString() {
      return "GeolocaleTaxonLogDetail logId:" + getLogId() + " " + super.toString();
    }

    public int getLogId() {
      return logId;
    }
    public void setLogId(int logId) {
      this.logId = logId;
    }

    // Transient method
    public Taxon getTaxon() {
      return taxon;
    }
    public void setTaxon(Taxon taxon) {
      this.taxon = taxon;
    }
}

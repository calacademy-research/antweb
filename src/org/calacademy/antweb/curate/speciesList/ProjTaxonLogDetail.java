package org.calacademy.antweb.curate.speciesList;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class ProjTaxonLogDetail extends ProjTaxon implements Comparable<ProjTaxonLogDetail>  {

    private static final Log s_log = LogFactory.getLog(ProjTaxonLogDetail.class);

    private int logId = 0;
    private Taxon taxon;

    public ProjTaxonLogDetail() {
    }
    
    public ProjTaxonLogDetail(String taxonName, String projectName) {
    // Used for creating dummies to be used for comparison
      setTaxonName(taxonName);
      setProjectName(projectName);
    }
    
    public int compareTo(ProjTaxonLogDetail o) {
        // Only operates on taxonName.  All details in a given ProjTaxonLog are assumed to be of the same species list.
        ProjTaxonLogDetail other = o;

        return Taxon.getPrettyTaxonName(getTaxonName()).compareTo(Taxon.getPrettyTaxonName(other.getTaxonName()));
    }
        
    public boolean equals(Object o) {
        // This is a bit sloppy as we are testing for equality on taxonName and projectName, but not date, or log_id.
        ProjTaxonLogDetail other = (ProjTaxonLogDetail) o;
        if (other.getTaxonName().equals(getTaxonName()))
            return other.getProjectName().equals(getProjectName());

//s_log.warn("equals() taxonName:" + getTaxonName() + " other:" + other.getTaxonName() + " projectName:" + getProjectName() + " other:" + other.getProjectName());  
                
        return false;
    }   
    
    public String toString() {
      return "ProjTaxonLogDetail logId:" + getLogId() + " " + super.toString();
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

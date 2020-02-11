package org.calacademy.antweb.curate.orphans;

import org.calacademy.antweb.*;
import org.apache.struts.action.*;
import javax.servlet.http.HttpServletRequest;

public class OrphanTaxonsForm extends ActionForm {

    protected String action;
    protected Taxon taxon;
    protected String taxonName;
    protected String toTaxonName;
    protected String suggestedTaxonName;
    protected String source;
    protected String subfamily;
    protected String genus;
    protected String species;
    protected String subspecies;
    protected String browse;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        this.action = null;
        this.taxon = null;
        this.taxonName = null;
        this.toTaxonName = null;
        this.suggestedTaxonName = null;
        this.source = null;
        this.subfamily = null;
        this.genus = null;
        this.species = null;
        this.subspecies = null;
        this.browse = null;
    }

    public Taxon getTaxon() {
        return taxon;
    }
    public void setTaxon(Taxon taxon) {
        this.taxon = taxon;
    }

    public String getAction() {
	    return action;
    }
    public void setAction(String action) {
    	this.action = action;
    }

    public String getTaxonName() {
	    return taxonName;
    }
    public void setTaxonName(String taxonName) {
    	this.taxonName = taxonName;
    }
    
    public String getToTaxonName() {
	    return toTaxonName;
    }
    public void setToTaxonName(String taxonName) {
    	this.toTaxonName = taxonName;
    }
    
    public String getSuggestedTaxonName() {
	    return suggestedTaxonName;
    }
    public void setSuggestedTaxonName(String taxonName) {
    	this.suggestedTaxonName = taxonName;
    }
    
    public String getSource() {
	    return source;
    }
    public void setSource(String source) {
    	this.source = source;
    }  

    public String getSubfamily() {
	    return subfamily;
    }
    public void setSubfamily(String subfamily) {
    	this.subfamily = subfamily;
    }  
    public String getGenus() {
	    return genus;
    }
    public void setGenus(String genus) {
    	this.genus = genus;
    }  

    public String getSpecies() {
	    return species;
    }
    public void setSpecies(String species) {
    	this.species = species;
    }  

    public String getSubspecies() {
	    return subspecies;
    }
    public void setSubspecies(String subspecies) {
    	this.subspecies = subspecies;
    }  

    public String getBrowse() {
	    return browse;
    }
    public void setBrowse(String browse) {
    	this.browse = browse;
    }  
}



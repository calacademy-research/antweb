package org.calacademy.antweb.search;


import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;


/**
 * Form bean for the summary page.  This form has the following fields.
 * <ul>
 * <li><b>subfamily</b> 
 * <li><b>genus</b> 
 * <li><b>species</b>
 * <li><b>project</b>
 * </ul>
*/

public final class FieldGuideForm extends ActionForm {

    private String subfamily = null;
    private String genus = null;
    private String species = null;
    private String subspecies = null;
	private String project = null;
    private String museum = null;
	private String rank = null;
    private String getCache = "";
    private String caste = "useDefaults";

	public String getCaste() {
		return (this.caste);
	}
	public void setCaste(String caste) {
		this.caste = caste;
	}
	
    
    public String getGetCache() {
      return this.getCache;
    }
    public void setGetCache(String getCache) {   
      this.getCache = getCache;
    }
    private String genCache = "";
    
    public String getGenCache() {
      return this.genCache;
    }
    public void setGenCache(String genCache) {   
      this.genCache = genCache;
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
		
	public String getProject() {
		return project;
	}
	public void setProject(String project) {
		this.project = project;
	}
	
	public String getMuseum() {
		return museum;
	}
	public void setMuseum(String museum) {
		this.museum = museum;
	}

	public String toString() {
	  return 
	    // "project:" + project + 
	      " subfamily:" + subfamily + " genus:" + genus
	    + " species:" + species;
	}
	
    public void reset(ActionMapping mapping, HttpServletRequest request) {
    	this.subfamily = null;
    	this.genus = null;
    	this.species = null;
    	this.project = null;
        this.museum = null;
    	this.rank = null;
    	this.genCache = null;
    	this.getCache = null;
    }

	public String getRank() {
		return rank;
	}
	public void setRank(String rank) {
		this.rank = rank;
	}
}

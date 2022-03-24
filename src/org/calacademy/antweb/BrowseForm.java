package org.calacademy.antweb;


import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Form bean for the browser page and related pages such as
 * navigateHierarchy, getComparison, oneView, chooseComparison,
 * and description.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>rank</b> - the rank of the taxon to browse
 * <li><b>name</b> - the name of the taxon to browse
 * <li><b>project</b> - the project of the taxon to browse
 *  <li><b>chosen</b> - the options to display of the taxon to browse
 * </ul>
*/

public final class BrowseForm extends DescriptionForm {

    private static Log s_log = LogFactory.getLog(BrowseForm.class);

    private int taxonId = 0;
    private String rank;
    private String name;  // Be nice to get rid of this.  It differs depending on rank.  Take the guesswork out.
    private String family;
    private String subfamily;
    private String genus;
    private String subgenus;
    private String species;
    private String subspecies;
    private String code;

    private String project;
    private String chosen[];
    private String childMaps = "";
    private String childImages = "";
    private String taxonName;
    private String getCache = "";
    private int antcatId = 0;
    private String statusSet;
    private String status;
    private String authorDate;
    private String caste;
    private boolean resetProject = false;
    private boolean local = false;
    private boolean global = false;

    public void reset(ActionMapping mapping, HttpServletRequest request) {
        super.reset(mapping, request);
        this.rank = null;
        this.subfamily = null;
        this.genus = null;
        this.subgenus = null;
        this.species = null;
        this.subspecies = null;
        this.code = null;
        this.name = null;
        this.project=null;
        this.chosen=null;
        this.taxonName = null;
        this.taxonId = 0;
        this.genCache = null;
        this.getCache = null;
        this.antcatId = 0;
        this.statusSet = null;
        this.status = null;
        this.authorDate = null;
        this.resetProject = false;
        //this.local = false;
        this.global = false;
    }
                
    

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

    public String getRank() {
         return (this.rank);
    }
    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getName() {
        return (this.name);
    }
    public void setName(String name) {
        this.name = name;
    }

    public int getTaxonId() {
        return taxonId;
    }
    public void setTaxonId(int taxonId) {
        this.taxonId = taxonId;
    }

    public String getFamily() {
        return (this.family);
    }
    public void setFamily(String family) {
        this.family = family;
    }

    public String getSubfamily() {
        return (this.subfamily);
    }
    public void setSubfamily(String subfamily) {
        this.subfamily = subfamily;
    }

    public String getGenus() {
        return this.genus;
    }
    public void setGenus(String genus) {
        this.genus = genus;
    }

    public String getSubgenus() {
        return this.subgenus;
    }
    public void setSubgenus(String subgenus) {
        this.subgenus = subgenus;
    }

    public String getSpecies() {
        return this.species;
    }
    public void setSpecies(String species) {
        this.species = species;
    }

    public String getSubspecies() {
        return this.subspecies;
    }
    public void setSubspecies(String subspecies) {
        this.subspecies = subspecies;
    }

    public String getCode() {
        return (this.code);
    }
    public void setCode(String code) {
        this.code = code;
    }    
        
    public String getProject() {
        return (this.project);
    }
    public void setProject(String project) {
        this.project = project;
    }    

   public String[] getChosen() {
    return (this.chosen);
    }
    public void setChosen(String [] chosen) {
        this.chosen = chosen;
    }    

    public String getChildMaps() {
        return childMaps;
    }

    public void setChildMaps(String childMaps) {
        this.childMaps = childMaps;
    }

    public String getChildImages() {
        return childImages;
    }

    public void setChildImages(String childImages) {
        this.childImages = childImages;
    }
    
    public String getTaxonName() {
      return taxonName;
    }
    public void setTaxonName(String taxonName) {
      this.taxonName = taxonName;
    }
    
    public int getAntcatId() {
      return antcatId;
    }
    public void setAntcatId(int antcatId) {
      this.antcatId = antcatId;
    }
    
    public String getStatusSet() {
      return statusSet;
    }
    public void setStatusSet(String statusSet) {
      this.statusSet = statusSet;
    }

    public String getStatus() {
      return status;
    }
    public void setStatus(String status) {
      this.status = status;
    }
     
    public String getAuthorDate() {
      return authorDate;
    }
    public void setAuthorDate(String authorDate) {
      this.authorDate = authorDate;
    }
            
    public boolean getResetProject() {
      return this.resetProject;
    }      
    public void setResetProject(boolean resetProject) {
      this.resetProject = resetProject;
    }      

    public boolean getLocal() {
      return this.local;
    }      
    public void setLocal(boolean local) {
      this.local = local;
    }     

    public boolean getGlobal() {
      return this.global;
    }      
    public void setGlobal(boolean global) {
      this.global = global;
    }   
}

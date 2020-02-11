package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import javax.servlet.http.*;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.util.*;

/**
 * Form bean for the search page.  This form has the following fields,
 * with default values in square brackets:
 * <ul>
 * <li><b>searchType</b> - the type of search (equals, contains)
 * <li><b>name</b> - the name of the thing to find
 * <li><b>types</b> - show only things that have types
 * <li><b>images</b> - show only things that have images
 * </ul>
*/

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AdvancedSearchForm extends TaxaFromSearchForm {

    protected static Log s_log = LogFactory.getLog(AdvancedSearchForm.class);

    //Search parameters common to all search types
    String types = null;
    
    //Search parameters common to basic searches
    String name = "";
    String searchType = "equals";

    String taxonName = "";    

    String family = "";
    String familySearchType = "equals";
    String subfamily = "";
    String subfamilySearchType = "equals";
    String genus = "";
    String genusSearchType = "equals";
    String species = "";
    String speciesSearchType = "equals";
    String subspecies = "";
    String subspeciesSearchType = "equals";

    String methodSearchType = "equals";
    String microhabitatSearchType = "equals";
    String habitatSearchType = "equals";

    String localityNameSearchType = "equals";
    String localityCodeSearchType = "equals";
    String collectedBySearchType = "equals";
    String museumCodeSearchType = "equals";

    String collectionCodeSearchType = "equals";
    String specimenCodeSearchType = "equals";
    String locatedAtSearchType = "equals";
    String ownedBySearchType = "equals";

    String adm2SearchType = "equals";

    String locatedAt = null;
    String project = null;
    String country = null;

    int geolocaleId = 0;

    String adm1 = null;    // was province
    String adm2 = null;   // was county
    String[] adm2s = null;  // was counties    
    String bioregion = null;

    String typeDesignation = null;
    String localityName = null;
    String localityCode = null;
    String collectionCode = null;
    String specimenCode = null;

    String habitat = null;
    String method = null;
    String microhabitat = null;
    String ownedBy = null;
	String collectedBy = null;
	String museumCode = null;

	String caste = null;
	String casteSearchType = null;
	String subcaste = null;
	String subcasteSearchType = null;
	String lifeStage = null;
	String lifeStageSearchType = null;
	String medium = null;
	String mediumSearchType = "equals";
	String specimenNotes = null;
	String specimenNotesSearchType = "equals";
	String dnaExtractionNotes = null;
	String dnaExtractionNotesSearchType = "equals";
	
	String elevation = null;
	String elevationSearchType = null;
	String dateCollected = null;
	String dateCollectedSearchType = null;
	String validNames = null;
	String statusSet = null;

	String created = null;
	String createdSearchType = null;	

    String groupName;

    boolean ignoreInsufficientCriteria = true;
    public boolean isIgnoreInsufficientCriteria() {
      return ignoreInsufficientCriteria;
    }
    public void setIsIgnoreInsufficientCriteria(boolean ignoreInsufficientCriteria) {
      A.log("setIsIgnoreInsufficientCriteria() val:" + ignoreInsufficientCriteria);
      this.ignoreInsufficientCriteria = ignoreInsufficientCriteria;
    }

    // These are not in SearchParameters	
	String collGroupOpen = "none";
	String specGroupOpen = "none";
	String typeGroupOpen = "none";
	String geoGroupOpen = "none";

    String resultRank = "";
    String output = "";

    int uploadId = 0;

	public ActionErrors validate(ActionMapping mapping,
    				HttpServletRequest request) {

        ActionErrors errors = new ActionErrors();
/*
        if ((name == null) || (name.length() < 1)) {
          if (((images == null) || (images.length() < 1))  && ((types == null) || (types.length() < 1))) {
            errors.add("name", new ActionError("error.name.required"));
          } 
        }

        if ((searchType == null) || (searchType.length() < 1))
            errors.add("searchType", new ActionError("error.searchType.required"));
*/

        AntwebUtil.blockFishingAttack(request, errors);

        return errors;
    }

    static String s_defaultOperator = "contains";  //"equals";

/**/
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        //s_log.warn("reset");

        taxonName = null;
		name = "";
		searchType = s_defaultOperator;
                
		family = "";
		familySearchType = s_defaultOperator;
		subfamily = "";
		subfamilySearchType = s_defaultOperator;
		genus = "";
		genusSearchType = s_defaultOperator;
		species = "";
		speciesSearchType = s_defaultOperator;
		subspecies = "";
		subspeciesSearchType = s_defaultOperator;
		localityNameSearchType = s_defaultOperator;
		localityCodeSearchType = s_defaultOperator;
		methodSearchType=s_defaultOperator;
		habitatSearchType=s_defaultOperator;
		locatedAtSearchType = s_defaultOperator;
		locatedAt = null;
		method = null;
		adm2 = null;
        this.adm2s = null;
		adm2SearchType = s_defaultOperator;
		collectionCodeSearchType = s_defaultOperator;
		specimenCodeSearchType = s_defaultOperator;
		types = null;
		imagesOnly = null;
		country = null;
		bioregion = null;
		adm1 = null;
		typeDesignation = null;
		localityName = null;
		localityCode = null;
		collectionCode = null;
		specimenCode = null;
		project = null;
		ownedBySearchType = s_defaultOperator;
		ownedBy = null;
		collectedBy = null;
		museumCode = null;
		medium = null;
		mediumSearchType = s_defaultOperator;
		caste = null;
		casteSearchType = s_defaultOperator;
		subcaste = null;
		subcasteSearchType = s_defaultOperator;
		lifeStage = null;
		lifeStageSearchType = s_defaultOperator;
		specimenNotes = null;
		specimenNotesSearchType = s_defaultOperator;
		dnaExtractionNotes = null;
		dnaExtractionNotesSearchType = s_defaultOperator;
		elevation = null;
		elevationSearchType = "greaterThanOrEqual";
		validNames = null;
		statusSet = null;
		dateCollected = null;
		dateCollectedSearchType = "greaterThanOrEqual";
		//speciesOnly = null;
        resultRank = "";
        output = "";
        
        groupName = null;
        uploadId = 0;
        ignoreInsufficientCriteria = true;  
    }

    // Used to support the Species List Tool permalink feature.
    public String getLinkParams() {
      String linkParams = "";
      linkParams += 
          "name=" + getName()
        + "&subfamily=" + getSubfamily()
        + "&genus=" + getGenus()
        + "&species=" + getSpecies()
        + "&subspecies=" + getSubspecies()
        
        + "&bioregion=" + getBioregion()
        + "&country=" + getCountry()
        + "&adm1=" + getAdm1()
        + "&adm2=" + getAdm2()
        + "&localityName=" + getLocalityName()
        + "&specimenCode=" + getSpecimenCode()
        + "&locatedAt=" + getLocatedAt()
        
        + "&go=go"; 
      return linkParams;
    }
    public void resetSearch() {
      setName(null);
      setFamily(null);
      setSubfamily(null);
      setGenus(null);
      setSpecies(null);
      setSubspecies(null);
      setBioregion(null);
      setCountry(null);
      setAdm1(null);
      setAdm2(null);
      setLocalityName(null);
      setLocalityCode(null);
      setLocatedAt(null);
    }
    public void persist(AdvancedSearchForm toolForm) {

        if (toolForm.getName() != null)
          setName(toolForm.getName());
        if (toolForm.getFamily() != null)
          setFamily(toolForm.getFamily());
        if (toolForm.getSubfamily() != null)
          setSubfamily(toolForm.getSubfamily());
        if (toolForm.getGenus() != null)
          setGenus(toolForm.getGenus());
        if (toolForm.getSpecies() != null)
          setSpecies(toolForm.getSpecies());
        if (toolForm.getSubspecies() != null)
          setSubspecies(toolForm.getSubspecies());
        
		if (toolForm.getBioregion() != null) 
		  setBioregion(toolForm.getBioregion());		
		if (toolForm.getCountry() != null)
		  setCountry(toolForm.getCountry());
		if (toolForm.getAdm1() != null)
		  setAdm1(toolForm.getAdm1());
		if (toolForm.getAdm2() != null)
		  setAdm2(toolForm.getAdm2());
		if (toolForm.getLocalityName() != null) 
		  setLocalityName(toolForm.getLocalityName());
		if (toolForm.getSpecimenCode() != null) 
		  setSpecimenCode(toolForm.getSpecimenCode());
		if (toolForm.getLocatedAt() != null) 
		  setLocatedAt(toolForm.getLocatedAt());
    }    
    

    public String getResultRank() {
		return (this.resultRank);
    }
    public void setResultRank(String resultRank) {
        this.resultRank = resultRank;
    }		

    public String getOutput() {
		return (this.output);
    }
    public void setOutput(String output) {
        this.output = output;
    }		

		
	public String getCollGroupOpen() {
		return collGroupOpen;
	}
	public void setCollGroupOpen(String collGroupOpen) {
		this.collGroupOpen = collGroupOpen;
	}

	public String getGeoGroupOpen() {
		return geoGroupOpen;
	}
	public void setGeoGroupOpen(String geoGroupOpen) {
		this.geoGroupOpen = geoGroupOpen;
	}

	public String getSpecGroupOpen() {
		return specGroupOpen;
	}
	public void setSpecGroupOpen(String specGroupOpen) {
		this.specGroupOpen = specGroupOpen;
	}

	public String getTypeGroupOpen() {
		return typeGroupOpen;
	}
	public void setTypeGroupOpen(String typeGroupOpen) {
		this.typeGroupOpen = typeGroupOpen;
	}
	
	
// Fields below used to be in SearchParameters

    public String getTaxonName() {
        return taxonName;
    }
    public void setTaxonName(String name) {
      taxonName = name;
    }
	
    public String getLifeStage() {
        return lifeStage;
    }
    public void setlifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
    }
    public String getLifeStageSearchType() {
        return lifeStageSearchType;
    }
    public void setLifeStageSearchType(String lifeStageSearchType) {
        this.lifeStageSearchType = lifeStageSearchType;
    }	
	public String getCaste() {
		return caste;
	}
	public void setCaste(String caste) {
		this.caste = caste;
	}
	public String getCasteSearchType() {
		return casteSearchType;
	}
	public void setCasteSearchType(String casteSearchType) {
		this.casteSearchType = casteSearchType;
	}

    // Not used from the jsp but is used by SearchParameters
    public String getSubcaste() {
        return subcaste;
    }
    public void setSubcaste(String subcaste) {
        //A.log("AdvancedSearchForm.setSubcaste() subcaste:" + subcaste);
        //AntwebUtil.logStackTrace();
        this.subcaste = subcaste;
    }
    public String getSubcasteSearchType() {
        return subcasteSearchType;
    }
    public void setSubcasteSearchType(String subcasteSearchType) {
        this.subcasteSearchType = subcasteSearchType;
    }
    
    public String getMedium() {
        return medium;
    }
    public void setMedium(String medium) {
        this.medium = medium;
    }
    public String getMediumSearchType() {
        return mediumSearchType;
    }
    public void setMediumSearchType(String mediumSearchType) {
        this.mediumSearchType = mediumSearchType;
    }
    
	public String getSpecimenNotes() {
		return specimenNotes;
	}
	public void setSpecimenNotes(String specimenNotes) {
	   //A.log("AdvancedSearchForm.setSpecimenNotes(" + specimenNotes + ")");
		this.specimenNotes = specimenNotes;
	}
	public String getSpecimenNotesSearchType() {
		return specimenNotesSearchType;
	}
	public void setSpecimenNotesSearchType(String specimenNotesSearchType) {
		this.specimenNotesSearchType = specimenNotesSearchType;
	}

	public String getDnaExtractionNotes() {
		return dnaExtractionNotes;
	}
	public void setDnaExtractionNotes(String dnaExtractionNotes) {
	   //A.log("AdvancedSearchForm.setDnaExtractionNotes(" + dnaExtractionNotes + ")");
		this.dnaExtractionNotes = dnaExtractionNotes;
	}
	public String getDnaExtractionNotesSearchType() {
		return dnaExtractionNotesSearchType;
	}
	public void setDnaExtractionNotesSearchType(String dnaExtractionNotesSearchType) {
		this.dnaExtractionNotesSearchType = dnaExtractionNotesSearchType;
	}

    public String getGenus() {
		return (this.genus);
    }
    public void setGenus(String genus) {
        this.genus = genus;
    }

    public String getSpecies() {
		return (this.species);
    }
    public void setSpecies(String species) {
        this.species = species;
    }
    public String getSubspecies() {
		return (this.subspecies);
    }
    public void setSubspecies(String subspecies) {
        this.subspecies = subspecies;
    }

    public String getGenusSearchType() {
		return (this.genusSearchType);
    }
    public void setGenusSearchType(String genusSearchType) {
        this.genusSearchType = genusSearchType;
    }

    public String getSpeciesSearchType() {
		return (this.speciesSearchType);
    }
    public void setSpeciesSearchType(String speciesSearchType) {
        this.speciesSearchType = speciesSearchType;
    }
    public String getSubspeciesSearchType() {
		return (this.subspeciesSearchType);
    }
    public void setSubspeciesSearchType(String subspeciesSearchType) {
        this.subspeciesSearchType = subspeciesSearchType;
    }

    public String getLocalityNameSearchType() {
		return (this.localityNameSearchType);
    }
    public void setLocalityNameSearchType(String localityNameSearchType) {
        this.localityNameSearchType = localityNameSearchType;
    }

    public String getLocalityCodeSearchType() {
		return (this.localityCodeSearchType);
    }
    public void setLocalityCodeSearchType(String localityCodeSearchType) {
        this.localityCodeSearchType = localityCodeSearchType;
    }

    public String getCollectionCodeSearchType() {
		return (this.collectionCodeSearchType);
    }
    public void setCollectionCodeSearchType(String collectionCodeSearchType) {
        this.collectionCodeSearchType = collectionCodeSearchType;
    }

    public String getCollectedBySearchType() {
		return (this.collectedBySearchType);
    }
    public void setCollectedBySearchType(String collectedBySearchType) {
        this.collectedBySearchType = collectedBySearchType;
    }

    public String getMuseumCodeSearchType() {
		return (this.museumCodeSearchType);
    }
    public void setMuseumCodeSearchType(String museumCodeSearchType) {
        this.museumCodeSearchType = museumCodeSearchType;
    }

    public String getAdm2SearchType() {
		return (this.adm2SearchType);
    }
    public void setAdm2SearchType(String adm2SearchType) {
        this.adm2SearchType = adm2SearchType;
    }

    public String getSpecimenCodeSearchType() {
		return (this.specimenCodeSearchType);
    }
    public void setSpecimenCodeSearchType(String specimenCodeSearchType) {
        this.specimenCodeSearchType = specimenCodeSearchType;
    }

    public String getTypeDesignation() {
		return (this.typeDesignation);
    }
    public void setTypeDesignation(String typeDesignation) {
        this.typeDesignation = typeDesignation;
    }
 
	/**
	 * This method mirrors the method getTypeDesignation. It is used so that the
	 * html input names generated and included by the system for the advanced search 
	 * do not have to modify the database name, type, which is used in generating the file.
	 * @return
	 */
	public String getType() {
		return (this.typeDesignation);
	}

	/**
	 * This method mirrors the method setType. It is used so that the
	 * html input names generated and included by the system for the advanced search 
	 * do not have to modify the database name, type, which is used in generating the file.
	 * @return
	 */
	public void setType(String typeDesignation) {
		this.typeDesignation = typeDesignation;
	}

    public String getAdm2() {
		return (this.adm2);
    }
    public void setAdm2(String adm2) {
        this.adm2 = adm2;
    }

    public String getLocalityName() {
		return (this.localityName);
    }
    public void setLocalityName(String localityName) {
        this.localityName = localityName;
    }

    public String getLocalityCode() {
		return (this.localityCode);
    }
    public void setLocalityCode(String localityCode) {
        this.localityCode = localityCode;
    }

    public String getCollectionCode() {
		return (this.collectionCode);
    }
    public void setCollectionCode(String collectionCode) {
        this.collectionCode = collectionCode;
    }

    public String getSpecimenCode() {
		return (this.specimenCode);
    }
    public void setSpecimenCode(String specimenCode) {
        this.specimenCode = specimenCode;
    }

    public String getBioregion() {
		return (this.bioregion);
    }
    public void setBioregion(String bioregion) {
        this.bioregion = bioregion;
    }

    /**
     * This method mirrors the method getOwnedBy. It is used so that the
     * html input names generated and included by the system for the advanced search 
     * do not have to modify the database name, ownedby, which is all lowercase.
     * @return
     */
	public String getOwnedby() {
	   return (this.ownedBy);
  	}
   	public void setOwnedby(String ownedBy) {
	   this.ownedBy = ownedBy;
   	}

	public String getCollectedby() {
	 return (this.collectedBy);
	}
	public void setCollectedby(String collectedBy) {
	 this.collectedBy = collectedBy;
	}

	public String getMuseumcode() {
	 return (this.museumCode);
	}
	public void setMuseumcode(String museumCode) {
	 this.museumCode = museumCode;
	}

    public String getCountry() {
		return (this.country);
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public String getAdm1() {
		return (this.adm1);
    }
    public void setAdm1(String adm1) {
        this.adm1 = adm1;
    }

    public String getTypes() {
		return (this.types);
    }
    public void setTypes(String types) {
        this.types = types;
    }

	public String getProject() {
		return project;
	}
	public void setProject(String project) {
		this.project = project;
	}

	public int getGeolocaleId() {
		return geolocaleId;
	}
	public void setGeolocaleId(int geolocaleId) {
		this.geolocaleId = geolocaleId;
	}
		
	public String getFamily() {
		return family;
	}
	public void setFamily(String family) {
   	    //A.log("AdvancedSearchForm.setFamily() family:" + family);
		this.family = family;
	}

	public String getSubfamily() {
		return subfamily;
	}
	public void setSubfamily(String subfamily) {
		this.subfamily = subfamily;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
		
	public String[] getAdm2s() {
		return adm2s;
	}
	public void setAdm2s(String[] adm2) {
		this.adm2s = adm2s;
	}
	
	public String getHabitat() {
		return habitat;
	}
	public void setHabitat(String habitat) {
		this.habitat = habitat;
	}

	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}

	public String getMicrohabitat() {
		return microhabitat;
	}
	public void setMicrohabitat(String microhabitat) {
		this.microhabitat = microhabitat;
	}
	
	public String getOwnedBySearchType() {
		return ownedBySearchType;
	}
	public void setOwnedBySearchType(String ownedBySearchType) {
		this.ownedBySearchType = ownedBySearchType;
	}

	public String getOwnedBy() {
		return ownedBy;
	}
	public void setOwnedBy(String string) {
		ownedBy = string;
	}

	public String getCollectedBy() {
		return collectedBy;
	}
	public void setCollectedBy(String string) {
		collectedBy = string;
	}

	public String getMuseumCode() {
	 return (this.museumCode);
	}
	public void setMuseumCode(String museumCode) {
	 this.museumCode = museumCode;
	}

	public String getMethodSearchType() {
		return methodSearchType;
	}	
	public void setMethodSearchType(String methodSearchType) {
		this.methodSearchType = methodSearchType;
	}
	
	public String getMicrohabitatSearchType() {
		return microhabitatSearchType;
	}	
	public void setMicrohabitatSearchType(String microhabitatSearchType) {
		this.microhabitatSearchType = microhabitatSearchType;
	}
		
	public String getHabitatSearchType() {
		return habitatSearchType;
	}	
	public void setHabitatSearchType(String habitatSearchType) {
		this.habitatSearchType = habitatSearchType;
	}
	
	public String getLocatedAt() {
		return locatedAt;
	}
	public void setLocatedAt(String locatedAt) {
		this.locatedAt = locatedAt;
	}

	public String getLocatedAtSearchType() {
		return locatedAtSearchType;
	}
	public void setLocatedAtSearchType(String locatedAtSearchType) {
		this.locatedAtSearchType = locatedAtSearchType;
	}

	public String getFamilySearchType() {
		return familySearchType;
	}
	public void setFamilySearchType(String familySearchType) {
		this.familySearchType = familySearchType;
	}

	public String getSubfamilySearchType() {
		return subfamilySearchType;
	}
	public void setSubfamilySearchType(String subfamilySearchType) {
		this.subfamilySearchType = subfamilySearchType;
	}

	public String getSearchType() {
		return searchType;
	}
	public void setSearchType(String searchType) {
		this.searchType = searchType;
	}
		
	public String getElevation() {
        return this.elevation;	
	}
	public void setElevation(String elevation) {
	    this.elevation = elevation;
	}
	public String getElevationSearchType() {
	    return this.elevationSearchType;
	}
	public void setElevationSearchType(String elevationSearchType) {
	    this.elevationSearchType = elevationSearchType;
	}    
	public String getValidNames() {
	    return this.validNames;
	}
	public void setValidNames(String validNames) {
  	    this.validNames = validNames;
	}
	public String getStatusSet() {
	    return this.statusSet;
	}
	public void setStatusSet(String statusSet) {
  	    this.statusSet = statusSet;
	}
	public String getDateCollected() {
	    return this.dateCollected;
	}
	public void setDateCollected(String dateCollected) {
	    this.dateCollected = dateCollected;
	}
	public String getDateCollectedSearchType() {
	    return this.dateCollectedSearchType;
	}
	public void setDateCollectedSearchType(String dateCollectedSearchType) {
	    this.dateCollectedSearchType = dateCollectedSearchType;
	}
	
	public String getCreated() {
	    return this.created;
	}
	public void setCreated(String created) {
	    this.created = created;
	}	    		

	public String getCreatedSearchType() {
	    return this.createdSearchType;
	}
	public void setCreatedSearchType(String createdSearchType) {
	    this.createdSearchType = createdSearchType;
	}

    public String getGroupName() {
      return groupName;
    }
    public void setGroupName(String groupName) {
      this.groupName = groupName;
    }
    
    public int getUploadId() {
      return uploadId;
    }
    public void setUploadId(int uploadId) {
      this.uploadId = uploadId;
    }


}

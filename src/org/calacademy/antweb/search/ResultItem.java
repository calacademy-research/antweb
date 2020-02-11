package org.calacademy.antweb.search;

import java.io.Serializable;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;


/** Class ResultItem keeps track of the information about a search item */
public final class ResultItem extends Item implements Serializable, Comparable<ResultItem> {

    private static Log s_log = LogFactory.getLog(ResultItem.class);

    public int compareTo(ResultItem other) {
        int compareInt = getFullName().compareTo(other.getFullName());
        //A.log("compareTo() compareInt:" + compareInt + " 1:" + getFullName() + "2:" + other.getFullName());
        return compareInt;
    }

    // These are not found in SearchItem
	private String fullName;
	private boolean types;
	private String pageParams;
	private ResultItem synonym;
    private boolean isFossil;

	public ResultItem() {
	}
	
// String typeOriginalCombination,  SearchItem synonym, 
	public ResultItem (String name, String code, String family, String subfamily, String genus, String species, String subspecies,
		String rank, int imageCount, String type, String favoriteImage,   //was boolean hasImages instead of imageCount
		String status, String country, String adm1, String adm2,  // int valid
		String localityName, String localityCode, String collectionCode
		, String lifeStage, String caste, String subcaste
		, String medium, String specimenNotes, String artist
		, String group, String shotType, String shotNumber, String uploadDate, int imageId
		, String habitat, String microhabitat, String method, String dnaExtractionNotes
		, String determinedBy, String collectedBy, String museumCode, String dateCollectedStart, int accessGroup
		, String groupName, String ownedBy, String locatedAt, String elevation, float decimalLatitude, float decimalLongitude
		, String museum, String created, String bioregion, int uploadId
		) {

		this.name = name;
		this.code = code;
		this.family = family;
		this.subfamily = subfamily;
		this.genus = genus;
		this.species = species;
		this.subspecies = subspecies;
		this.rank = rank;

// These were in results. Now deprecated. Not sure how they were intended to be used.
//		this.synonym = synonym;
//		this.typeOriginalCombination = typeOriginalCombination;


		//this.hasImages = hasImages;
		
		this.imageCount = imageCount;
        if (imageCount > 0) {
           hasImages = true;
        } else {
           hasImages = false;
        }		
		
		this.type = type;
		this.favoriteImage = favoriteImage;
		this.status = status;
		this.country=country;
		this.adm1=adm1;
		this.adm2=adm2;

		this.localityName = localityName;
		this.localityCode = localityCode;
		this.collectionCode = collectionCode;
        this.lifeStage = lifeStage;
		this.caste = caste;
		this.subcaste = subcaste;
		this.medium = medium;
		this.specimenNotes = specimenNotes;
		this.artist = artist;
		setGroup(group);
		this.shotType = shotType;
		this.shotNumber = shotNumber;
		setUploadDate(uploadDate);
		this.imageId = imageId;
		
		this.habitat = habitat;
		this.microhabitat = microhabitat;
		this.method = method;
		this.dnaExtractionNotes = dnaExtractionNotes;
		this.determinedBy = determinedBy;
		this.collectedBy = collectedBy;
		this.museumCode = museumCode;
		this.dateCollectedStart = dateCollectedStart;
		//this.dateCollectedStartStr = dateCollectedStart;
		this.accessGroup = accessGroup;
		setGroupName(groupName);
		this.ownedBy = ownedBy;
		this.locatedAt = locatedAt;
        this.elevation = elevation;
        this.decimalLatitude = decimalLatitude;
        this.decimalLongitude = decimalLongitude;
        this.museum = museum;
        this.created = created;
        this.bioregion = bioregion;
        this.uploadId = uploadId;
        
        // A.log("SearchItem() lifeStage:" + lifeStage);   // We have it here.     
	}		

	
	public void setItem(
		String name, String code, String rank, String pageParams, boolean hasImages,
		boolean types, String adm1,  // province/state
		String adm2,  // county
		String localityName) {
		
		setName(name);
		setCode(code);
		setRank(rank);
		setHasImages(hasImages);
		setTypes(types);
		setPageParams(pageParams);
		setCountry(country);
		setAdm1(adm1);
        setAdm2(adm2);  // added
		setLocalityName(localityName);
	}

	public void clear() {
		name = null;
		rank = null;
		code = null;
		fullName = null;
		hasImages = false;
		types = false;
		type = null;
		pageParams = null;
		favoriteImage = null;
        adm1 = null;
        adm2 = null;  
		synonym = null;
	}

	public String getFullName() {  // ex: temnothorax andrei    
		return (this.fullName);
	}
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}
	
	public String getTaxonName() {  // how to get ex: myrmicinaetemnothorax andrei 
	    String taxonName = getSubfamily() + getGenus() + " " + getSpecies();
        if (getSubspecies() != null) taxonName += " " + getSubspecies();
        return taxonName;
    }

	public String getPageParams() {
		return (this.pageParams);
	}
	public void setPageParams(String pageParams) {
		this.pageParams = pageParams;
	}

	public boolean getTypes() {
		return (this.types);
	}
	public void setTypes(boolean types) {
		this.types = types;
	}

    public boolean getIsFossil() {
      return isFossil;
    }
    public void setIsFossil(boolean isFossil) {
      this.isFossil = isFossil;
    }

	public String getLocalityString() {
		return country + ":" + adm1 + ":" + localityName;  // was province
	}
	
	public String getLocalityKey() {
	  if (getLocalityCode() != null) return getLocalityCode();
	  return getLocalityName();
	}

	public ResultItem getSynonym() {
		return synonym;
	}
	public void setSynonym(ResultItem item) {
		synonym = item;
	}
	
	// ***
	public String toString() {
        //return getFullName();
        return getCode();
	}
	
	public String toDebugString() {
		return "SearchItem name:" + name + " group:" + group + " groupName:" + groupName;
	}	   
	
}


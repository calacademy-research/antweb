package org.calacademy.antweb.search;

class Item {
	String name;
	String status;
	String rank;
	String code;
	String family;
	String subfamily;
	String genus;
	String species;
	String subspecies;
	boolean hasImages;
	int imageCount;	
	String type;
	String favoriteImage;
	String country;
    String adm1;  // province/stage
    String adm2;  // county
	String localityName;
    String localityCode;
    String collectionCode;
	String caste;
	String subcaste;
	String lifeStage;
	String medium;
	String specimenNotes;
	String localityString;
	String imagesString;
	String artist;
	String group;
	String shotType;
	String shotNumber;
	int imageId;
	String uploadDate;
	String habitat;
	String microhabitat;
	String method;
	String dnaExtractionNotes;
	String determinedBy;
	String collectedBy;
	String museumCode;
	String dateCollectedStart;
	int accessGroup;
	String groupName;
	String ownedBy;
	String locatedAt;
	String elevation;
	float decimalLatitude;
	float decimalLongitude;	
	String museum;
	String created;	
	String bioregion;
    int uploadId;	

	public String getCode() {
		return code;
	}
	public void setCode(String string) {
		code = string;
	}
	
	public String getGenus() {
		return genus;
	}
	public void setGenus(String string) {
		genus = string;
	}
	
	public boolean isHasImages() {
		return getHasImages();
	}

	public boolean getHasImages() {
		return hasImages;
	}
	public void setHasImages(boolean b) {
		hasImages = b;
	}
	
	public int getImageCount() {
	    return imageCount;
	}
	public void setImageCount(int imageCount) {
	    this.imageCount = imageCount;
	}
	
	public String getType() {
		return type;
	}
	public void setType(String string) {
		type = string;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String string) {
		name = string;
	}

	public String getSpecies() {
		return species;
	}
	public void setSpecies(String string) {
		species = string;
	}

	public String getSubspecies() {
		return subspecies;
	}
	public void setSubspecies(String string) {
		subspecies = string;
	}

	public String getFamily() {
		return family;
	}
	public void setFamily(String family) {
		this.family = family;
	}
	
	public String getSubfamily() {
		return subfamily;
	}
	public void setSubfamily(String string) {
		subfamily = string;
	}

	public String getRank() {
		return rank;
	}
	public void setRank(String string) {
		rank = string;
	}

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

	public String getFavoriteImage() {
		return favoriteImage;
	}
	public void setFavoriteImage(String string) {
		favoriteImage = string;
	}
	

	public String getAdm1() {
		return adm1;
	}
    public void setAdm1(String adm1) {
		this.adm1 = adm1;
	}

	public String getAdm2() {
		return adm2;
	}
	public void setAdm2(String adm2) {
		this.adm2 = adm2;
	}
	
	public String getCountry() {
		return country;
	}
	public void setCountry(String string) {
		country = string;
	}
	
	public String getLocalityName() {
		return localityName;
	}
	public void setLocalityName(String string) {
		localityName = string;
	} 

	public String getLocalityCode() {
		return localityCode;
	}
	public void setLocalityCode(String string) {
		localityCode = string;
	} 
	
	public String getCollectionCode() {
		return collectionCode;
	}
	public void setCollectionCode(String string) {
		collectionCode = string;
	} 
		
	public String getLifeStage() {
		return lifeStage;
	}
	public void setLifeStage(String lifeStage) {
	    // Got it.
	    //A.log("Item.setLifeStage() lifeStage:" + lifeStage);
		//AntwebUtil.logShortStackTrace(7);	    
		this.lifeStage = lifeStage;
	}		
	public String getCaste() {
		return caste;
	}
	public void setCaste(String caste) {
	    //A.log("Item.setCaste() caste:" + caste);
		//AntwebUtil.logShortStackTrace();
		this.caste = caste;
	}
	public String getSubcaste() {
		return subcaste;
	}
	public void setSubcaste(String subcaste) {
		this.subcaste = subcaste;
	}

    public String getMedium() {
        return medium;
    }
    public void setMedium(String medium) {
        this.medium = medium;
    }
    
	public String getSpecimenNotes() {
		return specimenNotes;
	}
	public void setSpecimenNotes(String specimenNotes) {
		this.specimenNotes = specimenNotes;
	}
	
	public String getArtist() {
		return artist;
	}
	public void setArtist(String artist) {
		this.artist = artist;
	}

	public String getGroup() {
		return group;
	}
	public void setGroup(String group) {
	    //A.log("setGroup() group:" + group);
		this.group = group;
	}

	public String getShotType() {
		return shotType;
	}
	public void setShotType(String shotType) {
		this.shotType = shotType;
	}

	public String getShotNumber() {
		return shotNumber;
	}
	public void setShotNumber(String shotNumber) {
		this.shotNumber = shotNumber;
	}

	public int getImageId() {
		return imageId;
	}
	public void setImageId(int imageId) {
		this.imageId = imageId;
	}

	public String getUploadDate() {
		return uploadDate;
	}
	public void setUploadDate(String uploadDate) {
		this.uploadDate = uploadDate;
	}	

	public String getHabitat() {
		return habitat;
	}
	public void setHabitat(String habitat) {
		this.habitat = habitat;
	}
	public String getMicrohabitat() {
		return microhabitat;
	}
	public void setMicrohabitat(String microhabitat) {
		this.microhabitat = microhabitat;
	}
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}

	public String getDnaExtractionNotes() {
		return dnaExtractionNotes;
	}
	public void setDnaExtractionNotes(String dnaExtractionNotes) {
		this.dnaExtractionNotes = dnaExtractionNotes;
	}

	public String getDeterminedBy() {
		return determinedBy;
	}	
	public void setDeterminedBy(String determinedBy) {
		this.determinedBy = determinedBy;
	}

	public String getCollectedBy() {
		return collectedBy;
	}
	public void setCollectedBy(String collectedBy) {
		this.collectedBy = collectedBy;
	}

	public String getMuseumCode() {
	 return this.museumCode;
	}
	public void setMuseumCode(String museumCode) {
	 this.museumCode = museumCode;
	}	

	public String getDateCollectedStart() {
		return dateCollectedStart;
	}
	public void setDateCollectedStart(String dateCollectedStart) {
		this.dateCollectedStart = dateCollectedStart;
	}
	public int getAccessGroup() {
		return accessGroup;
	}
	public void setAccessGroup(int accessGroup) {
		this.accessGroup = accessGroup;
	}
	
	public String getOwnedBy() {
	    return this.ownedBy;
	}
	public void setOwnedBy(String ownedBy) {
	    this.ownedBy = ownedBy;
	}

	public String getLocatedAt() {
	    return this.locatedAt;
	}
	public void setLocatedAt(String locatedAt) {
	    this.locatedAt = locatedAt;
	}
	
    public String getElevation() {
        return elevation;
    }
    public void setElevation(String elevation) {
        this.elevation = elevation;
    }
    public float getDecimalLatitude() {
        return decimalLatitude;
    }
    public void setDecimalLatitude(float decimalLatitude) {
        this.decimalLatitude = decimalLatitude;
    }
    public float getDecimalLongitude() {
        return decimalLongitude;
    }
    public void setDecimalLongitude(float decimalLongitude) {
        this.decimalLongitude = decimalLongitude;
    }	
	
    public String getMuseum () {
        return museum;
    }
    public void setMuseum(String museum) {
        this.museum = museum;
    }	

    public String getGroupName () {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }	
        	
    public String getCreated() {
        return created;
    }
    public void setCreated(String created) {
        this.created = created;
    }
    public String getBioregion() {
        return bioregion;
    }
    public void setBioregion(String bioregion) {
        this.bioregion = bioregion;
    }		
    public int getUploadId() {
      return this.uploadId;
    }
    public void setUploadId(int uploadId) {
      this.uploadId = uploadId;
    }
	
}
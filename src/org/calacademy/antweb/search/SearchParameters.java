package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
 
public final class SearchParameters extends AdvancedSearchForm {

    private static Log s_log = LogFactory.getLog(SearchParameters.class);
    
    String daysAgo = null;
    String numToShow;
    String fromDate, toDate;
    //String group;

    public SearchParameters () {
        fromDate = null;
        toDate = null;

    }
    
    public SearchParameters (AdvancedSearchForm asForm) {
        name = asForm.getName();
        family = asForm.getFamily();
        subfamily = asForm.getSubfamily();
        genus = asForm.getGenus();
        species = asForm.getSpecies();
        subspecies = asForm.getSubspecies();
        searchType = asForm.getSearchType();
        familySearchType = asForm.getFamilySearchType();
        subfamilySearchType = asForm.getSubfamilySearchType();
        genusSearchType = asForm.getGenusSearchType();
        speciesSearchType = asForm.getSpeciesSearchType();
        subspeciesSearchType = asForm.getSubspeciesSearchType();
        localityNameSearchType = asForm.getLocalityNameSearchType();
        localityCodeSearchType = asForm.getLocalityCodeSearchType();
        collectedBySearchType = asForm.getCollectedBySearchType();
        museumCodeSearchType = asForm.getMuseumCodeSearchType();
        adm2 = asForm.getAdm2();
        adm2SearchType = asForm.getAdm2SearchType();
        collectionCodeSearchType = asForm.getCollectionCodeSearchType();
        specimenCodeSearchType = asForm.getSpecimenCodeSearchType();
        methodSearchType = asForm.getMethodSearchType();
        microhabitatSearchType = asForm.getMicrohabitatSearchType();
        habitatSearchType = asForm.getHabitatSearchType();
        locatedAtSearchType = asForm.getLocatedAtSearchType();
        locatedAt = asForm.getLocatedAt();
        types = asForm.getTypes();
        imagesOnly = asForm.getImagesOnly();
        country = asForm.getCountry();
        bioregion = asForm.getBioregion();
        adm1 = asForm.getAdm1();
        typeDesignation = asForm.getTypeDesignation();
        localityName = asForm.getLocalityName();
        localityCode = asForm.getLocalityCode();
        collectionCode = asForm.getCollectionCode();
        specimenCode = asForm.getSpecimenCode();
        habitat = asForm.getHabitat();
        method = asForm.getMethod();
        microhabitat = asForm.getMicrohabitat();
        project = asForm.getProject();
        geolocaleId = asForm.getGeolocaleId();  
        ownedBySearchType = asForm.getOwnedBySearchType();
        ownedBy = asForm.getOwnedBy();
        collectedBy = asForm.getCollectedBy();
        museumCode = asForm.getMuseumCode();
        
        if (asForm.getCaste() != null) {
          if (asForm.getCaste().equals("male")) caste = "male";
          if (asForm.getCaste().equals("ergatoidMale")) { caste = "male"; subcaste = "ergatoid"; }
          if (asForm.getCaste().equals("alateMale")) { caste = "male"; subcaste = "alate"; }
          if (asForm.getCaste().equals("queen")) caste = "queen";
          if (asForm.getCaste().equals("ergatoidQueen")) { caste = "queen"; subcaste = "ergatoid"; }
          if (asForm.getCaste().equals("alateDealateQueen")) { caste = "queen"; subcaste = "alateDealate"; }
          if (asForm.getCaste().equals("brachypterous")) { caste = "queen"; subcaste = "brachypterous"; }
          if (asForm.getCaste().equals("worker")) caste = "worker";
          if (asForm.getCaste().equals("majorSoldier")) { caste = "worker"; subcaste = "majorSoldier"; }
          if (asForm.getCaste().equals("normal")) { caste = "worker"; subcaste = "normal"; }
          if (asForm.getCaste().equals("other")) caste = "other";
          if (asForm.getCaste().equals("intercaste")) { caste = "other"; subcaste = "intercaste"; }
          if (asForm.getCaste().equals("gynandromorph")) { caste = "other"; subcaste = "gynandromorph"; }
          if (asForm.getCaste().equals("larvaPupa")) { caste = "other"; subcaste = "larvaPupa"; }

          casteSearchType = asForm.getCasteSearchType();
          subcasteSearchType = asForm.getCasteSearchType(); 
        }
        lifeStage = asForm.getLifeStage();
        lifeStageSearchType = asForm.getLifeStageSearchType();
        //A.log("SearchParameters() caste:" + getCaste() + " caste:" + caste + " subcaste:" + subcaste + " lifeStage:" + asForm.getLifeStage());
        medium = asForm.getMedium();
        mediumSearchType = asForm.getMediumSearchType();
        specimenNotes = asForm.getSpecimenNotes();
        specimenNotesSearchType = asForm.getSpecimenNotesSearchType();
                
        //A.log("SearchParameters() dna:" + asForm.getDnaExtractionNotes());

        dnaExtractionNotes = asForm.getDnaExtractionNotes();
        dnaExtractionNotesSearchType = asForm.getDnaExtractionNotesSearchType();
        elevation = asForm.getElevation();
        elevationSearchType = asForm.getElevationSearchType();
        dateCollected = asForm.getDateCollected();
        dateCollectedSearchType = asForm.getDateCollectedSearchType();
        validNames = asForm.getValidNames();
        statusSet = asForm.getStatusSet();
        
        taxonName = asForm.getTaxonName();
        created = asForm.getCreated();
        createdSearchType = asForm.getCreatedSearchType();
        
        groupName = asForm.getGroupName();

        uploadId = asForm.getUploadId();
        setIsIgnoreInsufficientCriteria(asForm.isIgnoreInsufficientCriteria());

        //A.log("SearchParameters(as) groupName:" + groupName);
    }
    
    public SearchParameters (SearchForm sForm) {
        name = sForm.getName();
        searchType = sForm.getSearchType();
        imagesOnly = sForm.getImagesOnly();
        types = sForm.getTypes();    
        project = sForm.getProject();     
        geolocaleId = sForm.getGeolocaleId();     
               
        taxonName = sForm.getTaxonName();
    }

    // equals was added here to get working with AdvancedSearch
    public SearchParameters (BayAreaSearchForm sForm) {
        adm2s = sForm.getAdm2s();
        name = ""; family = ""; subfamily = ""; genus = ""; species = ""; subspecies = ""; searchType = ""; familySearchType = ""; subfamilySearchType = "";
          genusSearchType = ""; speciesSearchType = ""; subspeciesSearchType = ""; localityNameSearchType = ""; localityCodeSearchType = "";
          collectedBySearchType = ""; museumCodeSearchType = ""; adm2 = ""; adm2SearchType = "equals"; collectionCodeSearchType = "";
          specimenCodeSearchType = ""; methodSearchType = ""; microhabitatSearchType = ""; habitatSearchType = "";
          locatedAtSearchType = ""; locatedAt = ""; types = ""; imagesOnly = ""; country = ""; bioregion = "";
          adm1 = ""; typeDesignation = ""; localityName = ""; localityCode = ""; collectionCode = "";
          specimenCode = ""; habitat = ""; method = ""; microhabitat = ""; project = ""; geolocaleId = 0; ownedBy = ""; collectedBy = ""; museumCode = ""; 
          caste = ""; casteSearchType = "";  
          subcaste = ""; subcasteSearchType = "";  
          lifeStage = ""; lifeStageSearchType = "";  
          medium = ""; mediumSearchType = "";
          specimenNotes = ""; specimenNotesSearchType = "";
          dnaExtractionNotes = ""; dnaExtractionNotesSearchType = "";
          elevation = ""; elevationSearchType = ""; dateCollected = ""; dateCollectedSearchType = ""; validNames = ""; statusSet = "";
        
        taxonName = "";
        created = ""; createdSearchType = "";
        
        int i = 0;

        for (String anAdm2 : adm2s) {
          if (i > 0) adm2 += " ";
          if (anAdm2.contains(" ")) anAdm2 = "\"" + anAdm2 + "\"";  // This will put quotes around something like "San Francisco"
          adm2 += anAdm2;        
          ++i;
        }
        //A.log("SearchParameters(bayAreaSearchForm) adm2:" + adm2 + " adm2s:" + adm2s[0]);
    }
        
    public void oldSearchParameters (BayAreaSearchForm sForm) {
        adm2s = sForm.getAdm2s();
    }
    
    public SearchParameters (RecentImagesForm sForm) {
        daysAgo = sForm.getDaysAgo();
        numToShow = sForm.getNumToShow();
        fromDate = sForm.getFromDate();
        toDate = sForm.getToDate();
        groupName = sForm.getGroup();
    }
    
    public void resetSearchParameters() {
        taxonName = null;
        
        name = null;
        family = null;
        subfamily = null;
        genus = null;
        species = null;
        subspecies = null;
        searchType = null;
        familySearchType = null;
        subfamilySearchType = null;
        genusSearchType = null;
        speciesSearchType = null;
        subspeciesSearchType = null;
        adm2SearchType = null;
        localityNameSearchType = null;
        localityCodeSearchType = null;
        collectionCodeSearchType = null;
        collectedBySearchType = null;
        museumCodeSearchType = null;
        specimenCodeSearchType = null;
        locatedAtSearchType = null;
        habitatSearchType = null;
        methodSearchType = null;
        microhabitatSearchType = null;
        locatedAt = null;
        types = null;
        imagesOnly = null;
        project = null;
        geolocaleId = 0;
        country = null;
        adm1 = null;
        typeDesignation = null;
        adm2 = null;
        localityName = null;
        localityCode = null;
        collectionCode = null;
        specimenCode = null;
        bioregion = null;        
        habitat = null;
        method = null;
        microhabitat = null;
        ownedBy = null;
        adm2s = null;    
        caste = null;
        casteSearchType = null;
        subcaste = null;
        subcasteSearchType = null;
        lifeStage = null;
        lifeStageSearchType = null;
        medium = null;
        mediumSearchType = null;
        specimenNotes = null;
        specimenNotesSearchType = null;        
		dnaExtractionNotes = null;
		dnaExtractionNotesSearchType = null;
        daysAgo = null;
        fromDate = null;
        toDate = null;
        elevation = null;
        elevationSearchType = null;
        dateCollected = null;
        dateCollectedSearchType = null;
        validNames = null;
        statusSet = null;
        created = null;
        createdSearchType = null;
        
        groupName = null;
        
        uploadId = 0;
    }

    public String getDaysAgo() {
        return daysAgo;
    }
    public void setDaysAgo(String daysAgo) {
        this.daysAgo = daysAgo;
    }

    public String getNumToShow() {
        return numToShow;
    }
    public void setNumToShow(String numToShow) {
        this.numToShow = numToShow;
    }

    public String getFromDate() {
        return fromDate;
    }
    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }
    public void setToDate(String toDate) {
        this.toDate = toDate;
    }

    public String toString() {
        return
        "types = "+ types
        + ", images=" + imagesOnly
        + ", project=" + project 
        + ", geolocaleId=" + geolocaleId    
        //Search parameters common to basic searches
        + ", name=" + name
        + ", searchType=" + searchType
    
        //Search parameters for to advanced searches
        + ", genus=" + genus
        + ", species=" + species
        + ", subspecies=" + subspecies
        + ", genusSearchType=" + genusSearchType
        + ", speciesSearchType=" + speciesSearchType
        + ", subspeciesSearchType=" + subspeciesSearchType
        + ", adm2SearchType=" + adm2SearchType
        + ", localityNameSearchType=" + localityNameSearchType
        + ", localityCodeSearchType=" + localityCodeSearchType
        + ", collectionCodeSearchType=" + collectionCodeSearchType
        + ", specimenCodeSearchType=" + specimenCodeSearchType
        + ", locatedAtSearchType=" + locatedAtSearchType
        + ", locatedAt=" + locatedAt
        + ", country=" + country
        + ", adm1=" + adm1
        + ", typeDesignation=" + typeDesignation
        + ", adm2=" + adm2
        + ", localityName=" + localityName
        + ", localityCode=" + localityCode
        + ", collectionCode=" + collectionCode
        + ", specimenCode=" + specimenCode
        + ", habitat=" + habitat
        + ", method=" + method
        + ", microhabitat=" + microhabitat
        + ", ownedBy=" + ownedBy
        + ", collectedBy =" + collectedBy
        + ", museumCode =" + museumCode
        + ", bioregion=" + bioregion 
        + ", caste=" + caste
        + ", casteSearchType=" + casteSearchType
        + ", subcaste=" + subcaste
        + ", subcasteSearchType=" + subcasteSearchType
        + ", lifeStage=" + lifeStage
        + ", lifeStageSearchType=" + lifeStageSearchType
        + ", medium=" + medium
        + ", mediumSearchType=" + mediumSearchType
        + ", specimenNotes=" + specimenNotes
        + ", specimenNotesSearchType=" + specimenNotesSearchType       
        + ", dnaExtractionNotes=" + dnaExtractionNotes
        + ", dnaExtractionNotesSearchType=" + dnaExtractionNotesSearchType 
        + ", elevation=" + elevation
        + ", elevationSearchType=" + elevationSearchType
        + ", dateCollected=" + dateCollected
        + ", dateCollectedSearchType=" + dateCollectedSearchType
        + ", validNames=" + validNames
        + ", statusSet=" + statusSet
        
        // Search paramters for Bay Area Search
        + ",  adm2s=" + adm2s

        // For Image Pick        
        + ",  taxonName=" + taxonName
        + ",  created=" + created
        + ",  createdSearchType=" + createdSearchType
        + ",  groupName=" + groupName  
        + ",  uploadId=" + uploadId      
        ;
    }
        	       
}

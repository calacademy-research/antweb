package org.calacademy.antweb.search;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.SpecimenDb;

import java.util.*;
import java.util.Date;
import java.io.Serializable;
import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
/** Class Advanced does the searching for the advanced_search.jsp page */
public class AdvancedSearch extends GenericSearch implements Serializable {

/*
    To effect Advanced Search, fields must be included/touched in these places...
      web/search/advancedSearch-body.jsp
      Here.  src/org/calacademy/antweb/search/AdvancedSearch.java
      src/org/calacademy/antweb/search/AdvancedSearchForm.java
      src/org/calacademy/antweb/search/AdvancedSearchAction.java
      src/org/calacademy/antweb/search/SearchParameters.java
      src/org/calacademy/antweb/search/ResultItem.java
      src/org/calacademy/antweb/search/GenericSearchResults.java

      Maybe:
        src/org/calacademy/antweb/search/GenericSearch.java
*/

    private static final Log s_log = LogFactory.getLog(AdvancedSearch.class);

    private String family;
    private String familySearchType;
    private String subfamily;
    private String subfamilySearchType;
    private String genus;
    private String genusSearchType;
    private String species;
    private String speciesSearchType;
    private String subspecies;
    private String subspeciesSearchType;
    private String locatedAt;
    private String locatedAtSearchType;

    private String country;
    private String bioregion;
    private String adm1;

    private String adm2;
    private String adm2SearchType;

    private String typeDesignation;

    private String localityName;
    private String localityNameSearchType;
    private String localityCode;
    private String localityCodeSearchType;
    private String collectionCode;
    private String collectionCodeSearchType;
    private String specimenCode;
    private String specimenCodeSearchType;
    private String habitat;
    private String habitatSearchType;
    private String ownedBy;
    private String ownedBySearchType;
    private String collectedBy;
    private String collectedBySearchType;
    private String museumCode;
    private String museumCodeSearchType;
    private String method;
    private String methodSearchType;
    private String microhabitat;
    private String microhabitatSearchType;
    private String lifeStage;
    private String lifeStageSearchType;
    private String caste;
    private String casteSearchType;
    private String subcaste;
    private String subcasteSearchType;    
    private String medium;
    private String mediumSearchType;
    private String specimenNotes;
    private String specimenNotesSearchType;    
	private String dnaExtractionNotes;
	private String dnaExtractionNotesSearchType;
	private String determinedBy;
	private String determinedBySearchType;
	private String dateCollectedStart;
	private String dateCollectedStartSearchType;

	// Currently these are also here. Useful for incoming parameters?
	private String dateCollected;
	private String dateCollectedSearchType;


	private String groupName;
	private String groupNameSearchType;	    
	                	
	private String elevation;
	private String elevationSearchType;
		
	private String validNames;
	private String statusSet;

	private String created;
	private String createdSearchType;
	
	private int uploadId;
	
	public static String s_query;
	    
    protected ArrayList<ResultItem> createInitialResults() throws SearchException, SQLException {

		int sufficientCriteria = 3;   
        //  We expect to receive status and family so have 2 already by default. If family
        // is removed, or a special status is selected, we might lower the sufficientCriteria.        	

        ResultSet rset = null;
        String theQuery = null;
        try {
            String fieldList = " sp.taxon_name, sp.family, sp.subfamily, sp.genus, sp.species, sp.subspecies, sp.status, sp.type_status, sp.code " 
                + ", sp.toc, sp.country, sp.adm1, sp.adm2, sp.localityname" 
                + ", sp.life_stage, sp.caste, sp.subcaste"
                + ", sp.medium, sp.specimennotes, sp.localitycode, sp.collectioncode, sp.created"  // Added Dec, 6, 2012 Mark
                + ", sp.habitat, sp.microhabitat, sp.collectedby, sp.museum, sp.datecollectedstart, sp.access_group " 
                + ", sp.determinedby, sp.method, sp.dnaextractionnotes, sp.ownedby, sp.locatedat"
                + ", sp.elevation, sp.decimal_longitude, sp.decimal_latitude"  //Added Feb 1, 2013.                  
                + ", ant_group.name, sp.museum"
                + ", sp.bioregion, upload_id";

            theQuery = "select " + fieldList
                + ", count(image.id) as imagecount"
                + " from specimen as sp " 
                + " left outer join image on (sp.code = image.image_of_id)"  //  and image.shot_type != \"l\"
                + " left outer join ant_group on sp.access_group = ant_group.id "
                + " where " + SpecimenDb.getFlagCriteria()
                ; 

/* 
  if you want synoynms, which doesn't seem to make sense here
theQuery += " from taxon left outer join specimen as sp on taxon.taxon_name = sp.taxon_name left outer " +
                     " join image on sp.code = image.image_of_id  ";    

*/    
            ArrayList<String> where = new ArrayList<>();

            //where.add(" taxon.family = \"formicidae\"");
            //where.add(" 1=1 ");

            // Name is taxonName. duh.
            if (name != null && name.length() > 0) {
                if (searchType.equals("equals")) {
                    searchType = "ends";
                }
                where.add(getSearchString("sp.taxon_name", searchType, name));
            }

            if (family != null && family.length() > 0) {
                where.add(getSearchString("sp.family", familySearchType, family));
                if (!"Formicidae".equals(family)) --sufficientCriteria;  // bar is lowered
                if ("notEquals".equals(familySearchType)) --sufficientCriteria;  // bar is lowered            
                  //sp.family != 'Formicidae'                
            } else {
                --sufficientCriteria;  // if Formicidae is removed, one less criteria but bar is just as high
            }

            if (subfamily != null && subfamily.length() > 0 && !"none".equals(subfamily)) {
                where.add(getSearchString("sp.subfamily", subfamilySearchType, subfamily));
            }
            if (genus != null && genus.length() > 0 && !"none".equals(genus)) {
                where.add(getSearchString("sp.genus", genusSearchType, genus));
            }
            if (species != null && species.length() > 0) {
                where.add(getSearchString("sp.species", speciesSearchType, species));
            }
            if (subspecies != null && subspecies.length() > 0) {
                where.add(getSearchString("sp.subspecies", subspeciesSearchType, subspecies));
            }
            if (country != null && country.length() > 0) {
              if ("null".equals(country)) {
                where.add("sp.country is null");
              } else {
                String safeCountry = DBUtil.escapeQuotes(country);
                if (GeolocaleMgr.isIsland(safeCountry)) {
                    where.add("sp.island_country='" + safeCountry + "'");
                } else {
                    where.add("sp.country='" + safeCountry + "'");
                }
              }
            }
            if (adm1 != null && adm1.length() > 0) {
              if ("null".equals(adm1)) {
                where.add("sp.adm1 is null");
              } else {
                String safeAdm1 = DBUtil.escapeQuotes(adm1);              
                where.add("sp.adm1='" + safeAdm1 + "'");
              }
            }

            if (adm2 != null && adm2.length() > 0) {
                String adm2SearchStr = getSearchString("sp.adm2", adm2SearchType, adm2);
                where.add(" (" + adm2SearchStr + ") ");
                //A.log("createInitialResults() adm2:" + adm2 + " adm2SearchType:" + adm2SearchType + " searchString:" + adm2SearchStr);
            }

            if (bioregion != null
                && bioregion.length() > 0) {
                where.add("sp.bioregion='" + bioregion + "'");
            }
            if (typeDesignation != null && typeDesignation.length() > 0) {
                //where.add("sp.type='" + typeDesignation + "'");
                where.add("sp.type_status like '%" + typeDesignation + "%'");
            }
            if (method != null && method.length() > 0) {
                where.add(getSearchString("sp.method", methodSearchType, method));
            }
            if (microhabitat != null && microhabitat.length() > 0) {
                where.add(getSearchString("sp.microhabitat", microhabitatSearchType, microhabitat));
            }
            if (habitat != null && habitat.length() > 0) {
                where.add(getSearchString("sp.habitat", habitatSearchType, habitat));
            }
            if (localityName != null && localityName.length() > 0) {
                where.add(getSearchString("sp.localityname", localityNameSearchType, localityName));
            }
            if (localityCode != null && localityCode.length() > 0) {
                where.add(getSearchString("sp.localitycode", localityCodeSearchType, localityCode));
            }
            if (locatedAt != null && locatedAt.length() > 0) {
                where.add(getSearchString("sp.locatedat", locatedAtSearchType, locatedAt));
            }
            if (collectionCode != null && collectionCode.length() > 0) {
                where.add(getSearchString("sp.collectioncode", collectionCodeSearchType, collectionCode));
            }
            if (collectedBy != null && collectedBy.length() > 0) {
                where.add(getSearchString("sp.collectedby", collectedBySearchType, collectedBy));
            }
            
            if (museumCode != null && museumCode.length() > 0) {
                where.add(getSearchString("sp.museum", museumCodeSearchType, museumCode));
            }
            //A.log("createInitialResults() museumCode:" + museumCode + " where:" + where);  
            if (specimenCode != null && specimenCode.length() > 0) {
                where.add(getSearchString("sp.code", specimenCodeSearchType, specimenCode));
            }
            if (lifeStage != null && lifeStage.length() > 0) {
                where.add(getSearchString("sp.life_stage", lifeStageSearchType, lifeStage));
            }
            if (caste != null && caste.length() > 0) {
                where.add(getSearchString("sp.caste", casteSearchType, caste));
            }
            //A.log("AdvancedSearch().createInitialResults() antwebSucaste:" + subcaste);
            if (subcaste != null && subcaste.length() > 0) {
                String translateSubcaste = subcaste;
                if (subcaste.equals("alateDealate")) translateSubcaste = "alate/dealate";
                if (subcaste.equals("majorSoldier")) translateSubcaste = "major/soldier";
                if (subcaste.equals("larvaPupa")) translateSubcaste = "larva/pupa";
                where.add(getSearchString("sp.subcaste", subcasteSearchType, translateSubcaste));
            }
            if (medium != null && medium.length() > 0) {
                where.add(getSearchString("sp.medium", mediumSearchType, medium));
            }
            if (specimenNotes != null && specimenNotes.length() > 0) {
                where.add(getSearchString("sp.specimennotes", specimenNotesSearchType, specimenNotes));
            }
            
			//A.log("createInitialResults() medium:" + medium);
            
            // This would not work for: DMNH (Denver Museum).  Handled above.
            if (ownedBy != null && ownedBy.length() > 0) {
                where.add(getSearchString("sp.ownedBy", ownedBySearchType, ownedBy));
            }

			if (false && AntwebProps.isDevMode()) {
  			  //A.log("microhabitat:" + microhabitat + " microhabitatSearchType:" + microhabitatSearchType);
			  AntwebUtil.logStackTrace();
			}

            if (microhabitat != null && microhabitat.length() > 0) {
                where.add(getSearchString("sp.microhabitat", microhabitatSearchType, microhabitat));
            }
           
            //A.log("createInitialResults() dnaExtractionNotes:" + dnaExtractionNotes);
            if (dnaExtractionNotes != null && dnaExtractionNotes.length() > 0) {
                where.add(getSearchString("sp.dnaextractionnotes", dnaExtractionNotesSearchType, dnaExtractionNotes));
            }
            if (determinedBy != null && determinedBy.length() > 0) {
                where.add(getSearchString("sp.determinedby", determinedBySearchType, determinedBy));
            }
            

            if (dateCollected != null && dateCollected.length() > 0) {
                //A.log("createInitialResults() dateCollectedSearchType:" + dateCollectedSearchType + " dateCollected:" + dateCollected); // + " str:" + getSearchString("name", groupNameSearchType, groupName));

                where.add(getDateSearchString("sp.datecollectedstart", dateCollectedSearchType, dateCollected));
            }

            //A.log("createInitialResults() groupName:" + groupName + " groupNameSearchType:" + groupNameSearchType);
            if (groupName != null && groupName.length() > 0) {
//                where.add(getSearchString("ant_group.name", groupNameSearchType, groupName)); // This don't for group searches. Break map generation. Why was it like this?
                  String groupNameStr = getSearchString("groupName", groupNameSearchType, groupName);
                  A.log("groupNameStr:" + groupNameStr);
                  where.add(groupNameStr);
            }

/*             
http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&isIgnoreInsufficientCriteria=false&collGroupOpen=none&specGroupOpen=&geoGroupOpen=none&typeGroupOpen=&searchType=contains&name=&familySearchType=equals&family=Formicidae&subfamilySearchType=contains&subfamily=&genusSearchType=contains&genus=&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&created=&groupNameSearchType=equals&groupName="Archbold+Biological+Station"&uploadId=0&type=&types=off&statusSet=all&imagesOnly=off&resultRank=specimen&output=list&x=29&y=16
            if ((accessGroup > 0)) {
                where.add(getSearchString("access_group", "equals", (new Integer(accessGroup)).toString()));
            }
*/

            if (getElevation() != null && getElevation().length() > 0) {
                where.add(getSearchString("sp.elevation", elevationSearchType, elevation));
            }
            // dateCollectedStart?  What about the criteria above?  Reconcile.
            //if ((getDateCollected() != null) && (getDateCollected().length() > 0)) {
            //    where.add(getSearchString("sp.dateCollectedStart", dateCollectedSearchType, dateCollected));
            //} // *** this commented out Nov 28, 2017

/*
            // If we are not logged in, a checkbox for valid.  If we are, a drop down list of statuses.
            if ((getValidNames() != null) && (getValidNames().length() > 0)) {
                //where.add("taxon.valid=1");
                where.add("sp.status = 'valid'");
            } else {
              // A.log("createInitialResults() statusSet:" + getStatusSet());        
           }
*/ 
            //A.log("createInitialResults() validNames:" + getValidNames() + " statusSet:" + getStatusSet());

            if (getStatusSet() != null && getStatusSet().length() > 0) {
                String statusCriteria = new StatusSet(getStatusSet()).getCriteria("sp");

                if (!"all".equals(getStatusSet()) 
                 && !"valid".equals(getStatusSet())
                 && !"complete".equals(getStatusSet())
                   ) --sufficientCriteria;

                //A.log("createInitialResults() statusSet:" + getStatusSet() + " statusCriteria:" + statusCriteria);

                where.add(statusCriteria);
            }
            
            if (created != null && created.length() > 0) {
                String createdStr = getSearchString("sp.created", createdSearchType, created);
                A.log("createdStr:" + createdStr);
                where.add(createdStr);
            }
            
            if (uploadId > 0) {
                where.add("sp.upload_id= " + uploadId );

			}
            for (String whereCondition : where) {
                theQuery += " and (" + whereCondition + ")";
            }

            //String sortString = " sp.genus, sp.species, sp.subspecies";
            String sortString = " sp.code";

            // Every other search invert the results. This is useful for generating maps.
            // If we don't like a set of searchResults we can have another.
            if (flipSort) sortString += " desc";
            flipSort = !flipSort;
            
            theQuery
                += " group by " + fieldList 
                + " order by " + sortString
                + " limit " + SearchAction.getSpecimenSearchLimit();

            s_query = theQuery;
            
            A.log("createInitialResults() theQuery:" + theQuery);
            //s_log.warn("createInitialResults() theQuery:" + theQuery);
              //AntwebUtil.logStackTrace();

            //A.log("createInitialResults() ignore:" + isIgnoreInsufficientCriteria() + " where.size:" + where.size() + " sufficientCriteria:" + sufficientCriteria);            
            if (!isIgnoreInsufficientCriteria() && where.size() < sufficientCriteria) {
              // defaults criteria:[sp.family = 'Formicidae',  sp.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable')]
              //A.log("createInitialResults() where.size:" + where.size() + " insufficient criteria in query:" + theQuery);
              s_log.warn("createInitialResults() sufficientCriteria:" + sufficientCriteria + " insufficient criteria:" + where);
              throw new SearchException("Insufficient criteria in search");            
            }

  		    Statement stmt = connection.createStatement();
            Date startTime = new Date();
		    rset = stmt.executeQuery(theQuery);
            DBUtil.profileQuery("AdvancedSearch", startTime, theQuery);
		    ArrayList<ResultItem> itemList = getListFromRset(GenericSearch.ADVANCED, rset, null, theQuery);
		    String message = " listSize:" + itemList.size();
		    if (itemList != null && !itemList.isEmpty()) message += " 1st:" + itemList.get(0);
		    //A.log("createInitialResults()" + message + " query:" + theQuery);
		    //AntwebUtil.logShortStackTrace();
		    return itemList;
        } catch (SQLException e) {
            s_log.error("createInitialResults() e:" + e + " query:" + theQuery);
            //AntwebUtil.logShortStackTrace(e, 18);
            throw e;
        }
    }

    public static String getQuery() {
      return s_query;
    }
    
    private static boolean flipSort = false;    
    
/*
remove as...
group by all select fields except count
remove taxon entirely
add status to specimen
*/
    
    public void setResults() throws SearchException, SQLException {
        //  first do a big search getting images, types, and the validity
        //  then for the invalid ones - do another search to get the valid names
        //  put all of these in an array
        //  then filter out the ones that aren't in the project, if there is one
        //  the result classes will figure out how to deal with the image only
        //  searches and type only searches

        Date startDate = new Date();
        
        ArrayList<ResultItem> initialResults = createInitialResults();

        Date now = new Date();
        //A.log("setResults() took:" + (now.getTime() - startDate.getTime()) + " size:" + initialResults.size());
        startDate = now;
        this.results = initialResults;
    }

    public String getFamily() {
        return family;
    }
    public void setFamily(String family) {
        this.family = family;
    }
    public String getFamilySearchType() {
        return familySearchType;
    }
    public void setFamilySearchType(String familySearchType) {
        this.familySearchType = familySearchType;
    }
        
    public String getSubfamily() {
        return subfamily;
    }
    public void setSubfamily(String subfamily) {
        this.subfamily = subfamily;
    }
    public String getSubfamilySearchType() {
        return subfamilySearchType;
    }
    public void setSubfamilySearchType(String subfamilySearchType) {
        this.subfamilySearchType = subfamilySearchType;
    }
    
    public String getGenus() {
        return this.genus;
    }
    public void setGenus(String genus) {
        this.genus = genus;
    }
    public String getGenusSearchType() {
        return this.genusSearchType;
    }
    public void setGenusSearchType(String genusSearchType) {
        this.genusSearchType = genusSearchType;
    }
    
    public String getSpecies() {
        return this.species;
    }
    public void setSpecies(String species) {
        this.species = species;
    }
    public String getSpeciesSearchType() {
        return this.speciesSearchType;
    }
    public void setSpeciesSearchType(String speciesSearchType) {
        this.speciesSearchType = speciesSearchType;
    }
    
    public String getSubspecies() {
        return this.subspecies;
    }
    public void setSubspecies(String subspecies) {
        this.subspecies = subspecies;
    }
    public String getSubspeciesSearchType() {
        return this.subspeciesSearchType;
    }
    public void setSubspeciesSearchType(String subspeciesSearchType) {
        this.subspeciesSearchType = subspeciesSearchType;
    }
        
    public String getLifeStage() {
        return lifeStage;
    }
    public void setLifeStage(String lifeStage) {
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
        //A.log("AdvancedSearch.setSubcaste() caste:" + caste);
        this.caste = caste;
    }
    public String getCasteSearchType() {
        return casteSearchType;
    }
    public void setCasteSearchType(String casteSearchType) {
        this.casteSearchType = casteSearchType;
    }
    public String getSubcaste() {
        return subcaste;
    }
    public void setSubcaste(String subcaste) {
        //A.log("AdvancedSearch.setSubcaste() subcaste:" + subcaste);
        //AntwebUtil.logShortStackTrace();
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
      //s_log.warn("setMedium:" + medium);    
      //if (medium == null) AntwebUtil.logStackTrace();
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
        // s_log.warn("setSpecimenNotes:" + specimenNotes);    
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
        // s_log.warn("setdnaExtractionNotes:" + dnaExtractionNotes);    
        this.dnaExtractionNotes = dnaExtractionNotes;
    }
    public String getDnaExtractionNotesSearchType() {
        return dnaExtractionNotesSearchType;
    }
    public void setDnaExtractionNotesSearchType(String dnaExtractionNotesSearchType) {
        this.dnaExtractionNotesSearchType = specimenNotesSearchType;
    }
                
    public String getAdm2() {
        return this.adm2;
    }
    public void setAdm2(String adm2)
    {
        this.adm2 = adm2;
       // AntwebUtil.logShortStackTrace();
    }
    public String getAdm2SearchType() {
        return this.adm2SearchType;
    }
    public void setAdm2SearchType(String adm2SearchType) {
        this.adm2SearchType = adm2SearchType;
    }

    public String getHabitat() {
        return habitat;
    }
    public void setHabitat(String string) {
        habitat = string;
    }    
    public String getHabitatSearchType() {
        return habitatSearchType;
    }
    public void setHabitatSearchType(String habitatSearchType) {
        this.habitatSearchType = habitatSearchType;
    }
    
    public String getLocalityName() {
        return this.localityName;
    }
    public void setLocalityName(String localityName) {
        this.localityName = localityName;
    }  
    public String getLocalityNameSearchType() {
        return this.localityNameSearchType;
    }
    public void setLocalityNameSearchType(String localityNameSearchType) {
        this.localityNameSearchType = localityNameSearchType;
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


    public String getLocalityCode() {
        return this.localityCode;
    }
    public void setLocalityCode(String localityCode) {
        this.localityCode = localityCode;
    }  
    public String getLocalityCodeSearchType() {
        return localityCodeSearchType;
    }
    public void setLocalityCodeSearchType(String localityCodeSearchType) {
        this.localityCodeSearchType = localityCodeSearchType;
    }


    public String getCollectionCode() {
        return this.collectionCode;
    }
    public void setCollectionCode(String collectionCode) {
        this.collectionCode = collectionCode;
    }
    public String getCollectionCodeSearchType() {
        return this.collectionCodeSearchType;
    }
    public void setCollectionCodeSearchType(String collectionCodeSearchType) {
        this.collectionCodeSearchType = collectionCodeSearchType;
    }

    public String getCollectedBy() {
        return collectedBy;
    }
    public void setCollectedBy(String string) { 
        collectedBy = string;
    }
    public String getCollectedBySearchType() {
        return this.collectedBySearchType;
    }
    public void setCollectedBySearchType(String collectedBySearchType) {
        this.collectedBySearchType = collectedBySearchType;
    }

    public String getMuseumCode() {
        return museumCode;
    }
    public void setMuseumCode(String museumCode) { 
        this.museumCode = museumCode;
    }
    public String getMuseumCodeSearchType() {
        return this.museumCodeSearchType;
    }
    public void setMuseumCodeSearchType(String museumCodeSearchType) {
        this.museumCodeSearchType = museumCodeSearchType;
    }

    public String getSpecimenCode() {
        return this.specimenCode;
    }
    public void setSpecimenCode(String specimenCode) {
        this.specimenCode = specimenCode;
    }
    public String getSpecimenCodeSearchType() {
        return this.specimenCodeSearchType;
    }
    public void setSpecimenCodeSearchType(String specimenCodeSearchType) {
        this.specimenCodeSearchType = specimenCodeSearchType;
    }    
    
    public String getMethod() {
        return method;
    }
    public void setMethod(String string) {
        method = string;
    }
    public String getMethodSearchType() {
        return methodSearchType;
    }
    public void setMethodSearchType(String methodSearchType) {
        this.methodSearchType = methodSearchType;
    }

	public String getMicrohabitat() {
		return microhabitat;
	}
	public void setMicrohabitat(String microhabitat) {
		this.microhabitat = microhabitat;
	}

    public String getMicrohabitatSearchType() {
        return microhabitatSearchType;
    }
    public void setMicrohabitatSearchType(String microhabitatSearchType) {
        this.microhabitatSearchType = microhabitatSearchType;
    }
    
    public String getCountry() {
        return this.country;
    }
    public void setCountry(String country) {
        this.country = country;
    }

    public String getBioregion() {
        return this.bioregion;
    }
    public void setBioregion(String bioregion) {
        this.bioregion = bioregion;
    }
    
    public String getadm1() {
        return this.adm1;
    }
    public void setAdm1(String adm1) {
        this.adm1 = adm1;
    }

    public String getTypeDesignation() {
        return this.typeDesignation;
    }
    public void setTypeDesignation(String typeDesignation) {
        this.typeDesignation = typeDesignation;
    }

    public String getOwnedBy() {
        return ownedBy;
    }
    public void setOwnedBy(String string) {
        ownedBy = string;
    }
    public String getOwnedBySearchType() {
        return ownedBySearchType;
    }
    public void setOwnedBySearchType(String ownedBySearchType) {
        this.ownedBySearchType = ownedBySearchType;
    }    

	public String getDeterminedBy() {
		return determinedBy;
	}	
	public void setDeterminedBy(String determinedBy) {	
		this.determinedBy = determinedBy;
	}

	public String getDateCollectedStart() {
		return dateCollectedStart;
	}
	public void setDateCollectedStart(String dateCollectedStart) {
		this.dateCollectedStart = dateCollectedStart;
	}
   	
	public String getGroupName() {
	    return this.groupName;
	}
	public void setGroupName(String groupName) {
        //A.log("AdvancedSearch.getGroupName() groupName:" + groupName);	
	    this.groupName = groupName;
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
	
	public int getUploadId() {
	  return uploadId;
	} 
	public void setUploadId(int uploadId) {
	  this.uploadId = uploadId;
	}

    boolean ignoreInsufficientCriteria = false;
    public boolean isIgnoreInsufficientCriteria() {
      return ignoreInsufficientCriteria;
    }
    public void setIsIgnoreInsufficientCriteria(boolean ignoreInsufficientCriteria) {
      this.ignoreInsufficientCriteria = ignoreInsufficientCriteria;
    }
    
}

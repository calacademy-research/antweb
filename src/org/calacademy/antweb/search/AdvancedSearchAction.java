package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class AdvancedSearchAction extends Action {

    private static final Log s_log = LogFactory.getLog(AdvancedSearchAction.class);

    /*
	public ActionForward execute(ActionMapping mapping, ActionForm form,
								 HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		A.log("execute()");
		HttpSession session = request.getSession();
		AntwebProps.resetSessionProperties(session);

		return (mapping.findForward("success"));
	}
*/

	public String getSearchTitle(SearchParameters searchParameters) {

		StringBuffer sb = new StringBuffer();
		Formatter format = new Formatter();

		addToSearch(sb, "genus", searchParameters.getGenus(), searchParameters.getGenusSearchType());
		addToSearch(sb, "family", searchParameters.getFamily(), searchParameters.getFamilySearchType());
		addToSearch(sb, "subfamily", searchParameters.getSubfamily(), searchParameters.getSubfamilySearchType());
		addToSearch(sb, "name", searchParameters.getName(), searchParameters.getSearchType());
		addToSearch(sb, "species", searchParameters.getSpecies(), searchParameters.getSpeciesSearchType());
		addToSearch(sb, "subspecies", searchParameters.getSubspecies(), searchParameters.getSubspeciesSearchType());
		addToSearch(sb, "locality name", searchParameters.getLocalityName(), searchParameters.getLocalityNameSearchType());
		addToSearch(sb, "adm2", searchParameters.getAdm2(), searchParameters.getAdm2SearchType());

		addToSearch(sb, "locality code", searchParameters.getLocalityCode(), searchParameters.getLocalityCodeSearchType());
		addToSearch(sb, "collection code", searchParameters.getCollectionCode(), searchParameters.getCollectionCodeSearchType());
		addToSearch(sb, "specimen code", searchParameters.getSpecimenCode(), searchParameters.getSpecimenCodeSearchType());
		addToSearch(sb, "collected by", searchParameters.getCollectedBy(), searchParameters.getCollectedBySearchType());
		addToSearch(sb, "museum code", searchParameters.getMuseumCode(), searchParameters.getMuseumCodeSearchType());		
		addToSearch(sb, "method", searchParameters.getMethod(), searchParameters.getMethodSearchType());
		addToSearch(sb, "microhabitat", searchParameters.getMicrohabitat(), searchParameters.getMicrohabitatSearchType());
		addToSearch(sb, "habitat", searchParameters.getHabitat(), searchParameters.getHabitatSearchType());
		addToSearch(sb, "caste", searchParameters.getCaste(), searchParameters.getCasteSearchType());
		addToSearch(sb, "subcaste", searchParameters.getSubcaste(), searchParameters.getSubcasteSearchType());
		addToSearch(sb, "lifeStage", searchParameters.getLifeStage(), searchParameters.getLifeStageSearchType());
		addToSearch(sb, "medium", searchParameters.getMedium(), searchParameters.getMediumSearchType());
		addToSearch(sb, "specimennotes", searchParameters.getSpecimenNotes(), searchParameters.getSpecimenNotesSearchType());
		addToSearch(sb, "dnaExtractionNotes", searchParameters.getDnaExtractionNotes(), searchParameters.getDnaExtractionNotesSearchType());		
		addToSearch(sb, "project", searchParameters.getProject(), null);
		addToSearch(sb, "images", searchParameters.getImagesOnly(), "boolean");
		addToSearch(sb, "types", searchParameters.getTypes(), "boolean");
		addToSearch(sb, "country", searchParameters.getCountry(), null);
		addToSearch(sb, "biogeographic region", searchParameters.getBioregion(), null);
		addToSearch(sb, "adm1", searchParameters.getAdm1(), null);
		addToSearch(sb, "type", searchParameters.getTypeDesignation(), null);
		addToSearch(sb, "owned by", searchParameters.getOwnedBy(), null);
		addToSearch(sb, "located at", searchParameters.getLocatedAt(), null);

        addToSearch(sb, "elevation", searchParameters.getElevation(), searchParameters.getElevationSearchType());
        addToSearch(sb, "dateCollected", searchParameters.getDateCollected(), searchParameters.getDateCollectedSearchType());

       // A.log("getSearchTitle() dateCollected:" + searchParameters.getDateCollected() + " searchType:" + searchParameters.getDateCollectedSearchType());
                
        addToSearch(sb, "validNames", searchParameters.getValidNames(), "boolean");
        //A.log("getSearchTitle() validNames:" + searchParameters.getValidNames());

        addToSearch(sb, "statusSet", searchParameters.getStatusSet(), null);
 		
		String prefix = ""; //"Results from Searching AntWeb where: ";
		return prefix + sb;
	}
	
	private void addToSearch(StringBuffer sb, String field, String value, String searchModifier) {
        //s_log.warn("addToSearch()");		
	
		Formatter format = new Formatter();
		if (value != null && value.length() > 0) {

			if (searchModifier == null) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(format.capitalizeFirstLetter(field) + " is " + format.capitalizeFirstLetter(value));
			} else if (searchModifier.equals("boolean") && value.equals("on")) {
				// no need to list images being on, since only things with images are shown
				if (!field.equals("images")) {
					if (sb.length() > 0) {
						sb.append(", ");
					}
					sb.append("only " + format.capitalizeFirstLetter(field));
				}
			} else if (value != null && value.length() > 0) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(format.capitalizeEachWord(field) + " is " + format.capitalizeEachWord(value));
			}
		}	
	}

    // This is a method called by ObjectMapDb. Used on geolocale pages.
    public Map getGoogleMap(String country, String adm1, String resultRank, String output, Connection connection) throws SQLException {
        //A.log("getGoogleMapFunction() country:" + country + " adm1:" + adm1 + " resultRank:" + resultRank + " output:" + output);
        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setFamily(Family.FORMICIDAE);
        searchParameters.setCountry(country);
        if (!Utility.isBlank(adm1)) searchParameters.setAdm1(adm1);     
        //searchParameters.setSearchMethod("advancedSearch");
        searchParameters.setResultRank(resultRank);
        
        String title = "Map of ";
        if (adm1 != null) title += adm1;
        else title += country;
        
        //http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=&typeGroupOpen=none&searchType=contains&name=&subfamilySearchType=contains&subfamily=&genusSearchType=contains&genus=&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=Ohio&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&created=&groupName=&uploadId=0&type=&types=off&statusSet=all&images=off&resultRank=specimen&x=49&y=22      

        ArrayList<ResultItem> searchResults = null;
        try {
          searchResults = getSearchResults(connection, searchParameters);
          //if (searchResults != null) A.log("getGoogleMapFunction() searchResults:" + searchResults.size());
        } catch (IOException e) {
            s_log.error("getGoogleMap() 1a e:" + e);                
        } catch (ServletException e) {
            s_log.error("getGoogleMap() 2a e:" + e);        
        } catch (SearchException e) {
            s_log.error("getGoogleMap() 3a e:" + e);        
        }
  
        return getGoogleMap(searchResults, resultRank, output, title, connection);
    }

    // This is a method called by ObjectMapDb. Used on museum pages.
    public Map getGoogleMap(Museum museum, String resultRank, String output, Connection connection) throws SQLException {
        String museumCode = museum.getCode();
        //A.log("getGoogleMapFunction() country:" + country + " adm1:" + adm1 + " resultRank:" + resultRank + " output:" + output);
        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setFamily(Family.FORMICIDAE);
        searchParameters.setMuseumCode(museumCode);   
        //searchParameters.setSearchMethod("advancedSearch");
        searchParameters.setResultRank(resultRank);
        
        String title = "Map of ";
        if (museum != null) title += museum.toString();
        else title += museumCode;
        
        //http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=&typeGroupOpen=none&searchType=contains&name=&subfamilySearchType=contains&subfamily=&genusSearchType=contains&genus=&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=Ohio&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&created=&groupName=&uploadId=0&type=&types=off&statusSet=all&images=off&resultRank=specimen&x=49&y=22      

        ArrayList<ResultItem> searchResults = null;
        try {
          searchResults = getSearchResults(connection, searchParameters);
          if (searchResults != null) s_log.debug("getGoogleMap() searchResults:" + searchResults.size());
        } catch (IOException e) {
            s_log.error("getGoogleMap() 1b e:" + e);                
        } catch (ServletException e) {
            s_log.error("getGoogleMap() 2b e:" + e);        
        } catch (SearchException e) {
            s_log.error("getGoogleMap() 3b e:" + e);        
        }
  
        return getGoogleMap(searchResults, resultRank, output, title, connection);
    }
    
    // This is a method called by ObjectMapDb. Used on group pages.
    public Map getGoogleMap(Group group, String resultRank, String output, Connection connection) throws SQLException {
        int groupId = group.getId();
        //A.log("getGoogleMap() groupId:" + groupId + " resultRank:" + resultRank + " output:" + output);
        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setFamily(Family.FORMICIDAE);
        searchParameters.setGroupName(group.getName());
        //searchParameters.setGroupId(group.getId());
        searchParameters.setResultRank(resultRank);
        
        String title = "Map of ";
        if (group != null) title += group;

        ArrayList<ResultItem> searchResults = null;
        try {
          searchResults = getSearchResults(connection, searchParameters);
          //if (searchResults != null) A.log("getGoogleMap() searchResults:" + searchResults.size());
        } catch (IOException e) {
            s_log.error("getGoogleMap() 1c e:" + e);                
        } catch (ServletException e) {
            s_log.error("getGoogleMap() 2c e:" + e);        
        } catch (SearchException e) {
            s_log.error("getGoogleMap() 3c e:" + e);        
        }
  
        return getGoogleMap(searchResults, resultRank, output, title, connection);
    }
    
    private Map getGoogleMap(ArrayList<ResultItem> searchResults, String resultRank, String output, String title, Connection connection) {
        if (searchResults == null) {
          s_log.warn("getGoogleMap() null searchResults()");
          return null;
        }

		Map map = new MapResultsAction().getMap(searchResults, null, null, resultRank, output, title, connection);
		//if (title.contains("AFRC")) s_log.warn("getGoogleMap() title:" + title + " map.points.size:" + map.getPoints().size());
        return map;
    }
    
	public ArrayList<ResultItem> getSearchResults(HttpServletRequest request,
		SearchParameters searchParameters) throws SQLException, IOException, ServletException, SearchException {

		ArrayList<ResultItem> searchResults = null;

		Connection connection = null;
		try {
			DataSource dataSource = getDataSource(request, "mediumConPool");
			connection = DBUtil.getConnection(dataSource, "AdvancedSearchAction.getSearchResults()");

            searchResults = getSearchResults(connection, searchParameters);
            
        //} catch (SearchException e) {
		//	s_log.error("getSearchResults() 1 e:" + e);
		} catch (SQLException e) {
			s_log.error("getSearchResults() 2 e:" + e);
			throw e;
		} finally {
			DBUtil.close(connection, this, "AdvancedSearchAction.getSearchResults()");
		}

		return searchResults;
    }

    // This version is used internally, for instance to create the geolocale maps. (Object_Maps table)
	public ArrayList<ResultItem> getSearchResults(Connection connection, SearchParameters searchParameters) 
		throws IOException, ServletException, SearchException, SQLException {
        // s_log.warn("getSearchResults()");		

		AdvancedSearchResults results = new AdvancedSearchResults();
		AdvancedSearch search = new AdvancedSearch();
		ArrayList<ResultItem> searchResults = null;
		
        Date startTime = new Date();
        
//        A.log("getSearchResults() family:" + searchParameters.getFamily());	
// http://localhost/antweb/advancedSearch.do?searchMethod=advancedSearch&advanced=true&collGroupOpen=none&specGroupOpen=none&geoGroupOpen=&typeGroupOpen=none&searchType=contains&name=&familySearchType=notEquals&family=Formicidae&subfamilySearchType=contains&subfamily=&genusSearchType=contains&genus=&speciesSearchType=contains&species=&subspeciesSearchType=contains&subspecies=&bioregion=&country=&adm1=Ohio&adm2SearchType=contains&adm2=&localityNameSearchType=contains&localityName=&localityCodeSearchType=contains&localityCode=&habitatSearchType=contains&habitat=&elevationSearchType=greaterThanOrEqual&elevation=&methodSearchType=contains&method=&microhabitatSearchType=equals&microhabitat=&collectedBySearchType=equals&collectedBy=&collectionCodeSearchType=contains&collectionCode=&dateCollectedSearchType=greaterThanOrEqual&dateCollected=&specimenCodeSearchType=contains&specimenCode=&locatedAtSearchType=contains&locatedAt=&lifeStageSearchType=contains&lifeStage=&casteSearchType=contains&caste=&mediumSearchType=contains&medium=&specimenNotesSearchType=contains&specimenNotes=&dnaExtractionNotesSearchType=contains&dnaExtractionNotes=&ownedBySearchType=contains&ownedBy=&createdSearchType=equals&created=&groupName=&uploadId=0&type=&types=off&statusSet=all&imagesOnly=off&resultRank=specimen&output=list&x=49&y=14	
	    search.setFamily(searchParameters.getFamily().trim());
		search.setSubfamily(searchParameters.getSubfamily().trim());
		search.setName(searchParameters.getName().trim());
		search.setGenus(searchParameters.getGenus().trim());
		search.setSpecies(searchParameters.getSpecies().trim());
		search.setSubspecies(searchParameters.getSubspecies().trim());
		search.setSpeciesSearchType(searchParameters.getSpeciesSearchType());
		search.setSubspeciesSearchType(searchParameters.getSubspeciesSearchType());
		search.setSearchType(searchParameters.getSearchType());
		search.setFamilySearchType(searchParameters.getFamilySearchType());
		search.setSubfamilySearchType(searchParameters.getSubfamilySearchType());
		search.setGenusSearchType(searchParameters.getGenusSearchType());
		search.setMethodSearchType(searchParameters.getMethodSearchType());
		search.setMicrohabitatSearchType(searchParameters.getMicrohabitatSearchType());
		search.setHabitatSearchType(searchParameters.getHabitatSearchType());
		search.setLocalityNameSearchType(searchParameters.getLocalityNameSearchType());
		search.setAdm2SearchType(searchParameters.getAdm2SearchType());
		search.setLocalityCodeSearchType(searchParameters.getLocalityCodeSearchType());
		search.setLocatedAtSearchType(searchParameters.getLocatedAtSearchType());
		search.setCollectionCodeSearchType(searchParameters.getCollectionCodeSearchType());
		search.setSpecimenCodeSearchType(searchParameters.getSpecimenCodeSearchType());
		search.setCollectedBySearchType(searchParameters.getCollectedBySearchType());
		search.setMuseumCodeSearchType(searchParameters.getMuseumCodeSearchType());
		search.setOwnedBySearchType(searchParameters.getOwnedBySearchType());
		search.setCasteSearchType(searchParameters.getCasteSearchType());
		search.setSubcasteSearchType(searchParameters.getSubcasteSearchType());			
		search.setLifeStageSearchType(searchParameters.getLifeStageSearchType());
		search.setMediumSearchType(searchParameters.getMediumSearchType());
		search.setSpecimenNotesSearchType(searchParameters.getSpecimenNotesSearchType());
		search.setDnaExtractionNotesSearchType(searchParameters.getDnaExtractionNotesSearchType());
		search.setConnection(connection);
		
		//A.log("getSearchResults imagesOnly:" + searchParameters.getImagesOnly());
		//search.setImagesOnly(searchParameters.getImagesOnly());
		
		search.setTypes(searchParameters.getTypes());
		search.setProject(searchParameters.getProject());

		//s_log.warn("getSearchResults() adm2:" + search.getAdm2());
		search.setCountry(searchParameters.getCountry());
		search.setBioregion(searchParameters.getBioregion());
		search.setAdm1(searchParameters.getAdm1());
		if (searchParameters.getAdm2() != null) {
			search.setAdm2(searchParameters.getAdm2().trim());
		} else {
			search.setAdm2("");
		}
		search.setLocatedAt(searchParameters.getLocatedAt());
		search.setTypeDesignation(searchParameters.getTypeDesignation());
		if (searchParameters.getLocalityName() != null) {
		  search.setLocalityName(searchParameters.getLocalityName().trim());
		} else { 
		  search.setLocalityName(""); 
		}
		if (searchParameters.getLocalityCode() != null) {
		  search.setLocalityCode(searchParameters.getLocalityCode().trim());
		} else { search.setLocalityCode(""); }
		if (searchParameters.getCollectionCode() != null) {
		  search.setCollectionCode(searchParameters.getCollectionCode().trim());
		} else { search.setCollectionCode(""); }
		if (searchParameters.getSpecimenCode() != null) {
		  search.setSpecimenCode(searchParameters.getSpecimenCode().trim());
		} else { search.setSpecimenCode(""); }
		search.setHabitat(searchParameters.getHabitat());
		search.setMicrohabitat(searchParameters.getMicrohabitat());
		search.setMethod(searchParameters.getMethod());
		search.setOwnedBy(searchParameters.getOwnedBy());
		if (searchParameters.getCollectedBy() != null) {
		  search.setCollectedBy(searchParameters.getCollectedBy().trim());
		} else { search.setCollectedBy(""); }
		search.setMuseumCode(searchParameters.getMuseumCode());
		search.setCaste(searchParameters.getCaste());
		search.setSubcaste(searchParameters.getSubcaste());
		//s_log.warn("getSubcaste() caste:" + searchParameters.getCaste() + " subcaste:" + searchParameters.getSubcaste());
		search.setLifeStage(searchParameters.getLifeStage());
		search.setMedium(searchParameters.getMedium());
		search.setSpecimenNotes(searchParameters.getSpecimenNotes());
		search.setDnaExtractionNotes(searchParameters.getDnaExtractionNotes());
		//s_log.warn("getSearchResults() elevationSearchType:" + searchParameters.getElevationSearchType());
		
		search.setElevation(searchParameters.getElevation());
		search.setElevationSearchType(searchParameters.getElevationSearchType());
		search.setDateCollected(searchParameters.getDateCollected());

		//A.log("getSearchResults() dateCollected:" + searchParameters.getDateCollected() + " searchType:" + searchParameters.getDateCollectedSearchType());

		search.setDateCollectedSearchType(searchParameters.getDateCollectedSearchType());

		search.setValidNames(searchParameters.getValidNames());
		search.setStatusSet(searchParameters.getStatusSet());

		search.setCreated(searchParameters.getCreated());
		search.setCreatedSearchType(searchParameters.getCreatedSearchType());

		search.setGroupName(searchParameters.getGroupName());
		//A.log("getSearchResults() groupName:" + searchParameters.getGroupName());
		
		search.setUploadId(searchParameters.getUploadId());
        search.setIsIgnoreInsufficientCriteria(searchParameters.isIgnoreInsufficientCriteria());
		//A.log("getSearchResults() ignore:" + search.isIgnoreInsufficientCriteria() + " paramIgnore:" + searchParameters.isIgnoreInsufficientCriteria());

		searchResults = search.getResults();
		//A.log("getSearchResults() list:" + searchResults.get(0).getDateCollectedStart());

		QueryProfiler.profile("advSearch", startTime);	 

		return searchResults;
	}
}

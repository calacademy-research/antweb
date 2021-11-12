package org.calacademy.antweb.curate.speciesList;

import java.util.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.io.*;
import javax.servlet.ServletException;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.geolocale.*;

import org.calacademy.antweb.search.*;

public class SpeciesListToolAction extends SpeciesListSuperAction {

  private static Log s_log = LogFactory.getLog(SpeciesListToolAction.class);

  private static ArrayList<Geolocale> s_blackList = null;
  
  private static final int s_maxSpeciesListSizeBeforeDisplaySubfamily = 1000;
  
/*
Questions
  Is it a problem that someone could move a taxon onto another taxon not visible on the list?
  In move, to change specimen records?

To Do
  Only Brian can upload, and at beginning after list creation.  True?
  Remove Madagascar from species list list?  Or ask about.
  Must have a speciesList1 on the jsp?  Allow any.  Fix.
  
  Brian security.  Only he can upload files?  Only he creates?
    
  If a taxon is removed from a species list and there are no remaining species lists,
    or specimens, then delete the taxon from table.
    Perhaps put the addProjTaxon and removeProjTaxon methods in a ProjTaxonHome class?
  AND If a taxon is removed from a species list, it may need to be removed from it's
    bioregion - if that bioregion is an aggregation.  Verify adding as well.
  Can we change some of our bioregions to be aggregational?

  Color coding lines for easy viewing
  SpeciesListCopy tool added above the Add/Create taxon tool (lower priority)
  A checkout mechanism to detect curator collisions would be good.  
*/        

  public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {

    HttpSession session = request.getSession();

    ActionForward c = Check.login(request, mapping); if (c != null) return c;  
    Login accessLogin = LoginMgr.getAccessLogin(request);

    if (false) {
      String message = "Species List Tool is down for maintenance.";
      request.setAttribute("message", message);
      return mapping.findForward("message");
    }

    int loginId = accessLogin.getId();

    SpeciesListToolForm toolForm = (SpeciesListToolForm) form;

    // Both parameters (isFresh, mapSpeciesList1Name) may be set from a call from UploadAction.
    //A.log("execute() req:" + (String) request.getAttribute("isFreshen") + " form:" + toolForm.getIsFreshen());
    SpeciesListToolProps toolProps = null;

    String reqFreshen = (String) request.getAttribute("isFreshen");
    if ("true".equals(reqFreshen)) toolForm.setIsFreshen(true);
    if (toolForm.getIsFreshen()) {
      //A.log("execute() TRUE reqFreshen:" + toolForm.getIsFreshen());
      toolProps = new SpeciesListToolProps();
    } else {
      toolProps = (SpeciesListToolProps) session.getAttribute("speciesListToolProps");
      if (toolProps == null) toolProps = new SpeciesListToolProps();
    }
    String tMap = (String) request.getAttribute("mapSpeciesList1Name");
    if (tMap != null) toolForm.setMapSpeciesList1Name(tMap);    

    toolProps.persist(toolForm);
    
    session.setAttribute("speciesListToolProps", toolProps);

    //A.log("SpeciesListToolAction.execute() toolForm:" + toolForm);
    //A.log("execute() toolProps:" + toolProps);
    
    String message = "";
    java.sql.Connection connection = null;
    try {
      javax.sql.DataSource dataSource = getDataSource(request, "longConPool");
      connection = DBUtil.getConnection(dataSource, "SpeciesListToolAction.execute()");
      connection.setAutoCommit(false);
      SpeciesListDb speciesListDb = (new SpeciesListDb(connection));


      //A.log("SpeciesListTool.execute() action:" + toolProps.getAction() + " refSpeciesListType:" + toolProps.getRefSpeciesListType() + " doSearch:" + toolProps.getDoSearch());

      String action = toolForm.getAction();
      if ("changeRefSpeciesList".equals(action)) {
          String refSpeciesListType = toolProps.getRefSpeciesListType();
		  if ("advSearch".equals(refSpeciesListType) ) {    
			  request.setAttribute("action", "changeRefSpeciesList");
			  request.setAttribute("refSpeciesListType", "advSearch");
			  return mapping.findForward("advSearch");
			  //http://localhost/antweb/speciesListToolSearch.do?action=changeRefSpeciesList&refSpeciesListType=advSearch
		  } 
		  if ("none".equals(refSpeciesListType)) {
            //A.log("SpeciesListTool.execute() resetSearch()");		  
			toolProps.resetSearch();
		  }	 
		  if ("speciesListHistory".equals(refSpeciesListType)) {
			return mapping.findForward("speciesListHistory");
		  }
		  if ("speciesList".equals(refSpeciesListType)) {
			return mapping.findForward("speciesListSearch");
		  } if ("specimenList".equals(refSpeciesListType)) {
			ArrayList<String> refListList = speciesListDb.fetchMappableSpecimenLists(accessLogin);
			toolProps.setRefListList(refListList);
			return mapping.findForward("specimenListSearch");
		  }		  
      }

      String mapSpeciesList1Name = toolProps.getMapSpeciesList1Name();
      String mapSpeciesList2Name = toolProps.getMapSpeciesList2Name();
      String mapSpeciesList3Name = toolProps.getMapSpeciesList3Name();

      String blockName = null;
      if (isBlockOnName(mapSpeciesList1Name, connection)) blockName = mapSpeciesList1Name;
      if (isBlockOnName(mapSpeciesList2Name, connection)) blockName = mapSpeciesList2Name;
      if (isBlockOnName(mapSpeciesList3Name, connection)) blockName = mapSpeciesList3Name;
      if (blockName != null) {
        message = "Species List Tool failed. Can not Map name:" + blockName;
        request.setAttribute("message", message);
        return mapping.findForward("message");
      }

      String doSearch = toolProps.getDoSearch();
      if ("searchResults".equals(doSearch)) {
        int maxResultSetCount = 300000;
        ArrayList<ResultItem> searchSpeciesList = null;
        try {
          SearchAction.setTempSpecimenSearchLimit(maxResultSetCount);
          searchSpeciesList = doAdvancedSearch(toolProps, request);

        } catch (SearchException e) {
          s_log.error("setResults() e:" + e);
          message = "<b><font color=red>Search failed:" + e.toString() + "</font></b>";           
        }
         
        if (searchSpeciesList != null && !searchSpeciesList.isEmpty()) {
		  ArrayList<Taxon> advSearchTaxa = (new TaxonDb(connection)).getTaxa(searchSpeciesList, toolProps.getDisplaySubfamily());
          countSearchSpecimen(connection, advSearchTaxa);
          toolProps.setAdvSearchTaxa(advSearchTaxa);
        }
      }
      
      if ("save".equals(action)) {
        //s_log.warn("execute() chosen:" + toolForm.getChosen1()[0] + " refSpeciesListType:" + refSpeciesListType + " refSpeciesListName:" + refSpeciesListName);
        //LogMgr.appendLog("speciesListTool.txt", "save - " + AntwebUtil.getFormatDateTimeStr() + " curatorId:" + accessGroup.getLogin().getId() 
        //  + " mapSpeciesList1Name:" + mapSpeciesList1Name + " mapSpeciesList2Name:" + mapSpeciesList2Name + " mapSpeciesList3Name:" + mapSpeciesList3Name );

        ArrayList<String> oldChosenList1 = toolProps.getOldChosenList1();
        ArrayList<String> oldChosenList2 = toolProps.getOldChosenList2();
        ArrayList<String> oldChosenList3 = toolProps.getOldChosenList3();

        if (oldChosenList1 == null && oldChosenList2 == null && oldChosenList3 == null) {
            return mapping.findForward("sessionExpired");       
        }
        message += speciesListDb.saveTaxonSet(toolForm.getTaxa()
          , toolForm.getChosen1(), oldChosenList1, mapSpeciesList1Name, accessLogin); 
        message += speciesListDb.saveTaxonSet(toolForm.getTaxa()
          , toolForm.getChosen2(), oldChosenList2, mapSpeciesList2Name, accessLogin); 
        message += speciesListDb.saveTaxonSet(toolForm.getTaxa()
          , toolForm.getChosen3(), oldChosenList3, mapSpeciesList3Name, accessLogin);
      } 

      if (message != null) request.setAttribute("message", message);
      setSpeciesListMappings(toolForm, toolProps, connection);

      toolProps.setNoPassWorldantsSpeciesList(speciesListDb.noPassWorldantsSpeciesList(mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name, toolProps.getRefSpeciesListName())); 

      A.log("execute() toolProps:" + toolProps);
 
    } catch (SQLException e) {
      s_log.error("execute() e:" + e);
      AntwebUtil.logStackTrace(e);
      DBUtil.rollback(connection);
      message = "Species List Tool failed due to e:" + e;
      request.setAttribute("message", message);
      return mapping.findForward("message");      
    } finally {
      DBUtil.close(connection, "SpeciesListToolAction.execute()");
    }
  
    return mapping.findForward("speciesListTool");
  }
  
  private boolean isBlockOnName(String speciesListName, Connection connection) {
    //Fetch the duplicate names. Store it in static variable.

    if (speciesListName == null) return false;
/*    
    if (s_blackList == null) {
      s_blackList = (new GeolocaleDb(connection)).getBlackList();
    }

    for (Geolocale geolocale : s_blackList) {
      if (speciesListName.equals(geolocale.getName())) return true;
    }
*/
    return false;
  }
  
  private void setSpeciesListMappings(SpeciesListToolForm toolForm, SpeciesListToolProps toolProps, Connection connection) 
    throws SQLException {
    // Note: can call itself recursively in case of extra large lists.
          
    String mapSpeciesList1Name = toolProps.getMapSpeciesList1Name();
    String mapSpeciesList2Name = toolProps.getMapSpeciesList2Name();
    String mapSpeciesList3Name = toolProps.getMapSpeciesList3Name();
    ArrayList<Taxon> advSearchTaxa = toolProps.getAdvSearchTaxa();
    String displaySubfamily = toolProps.getDisplaySubfamily();
    String refSpeciesListType = toolProps.getRefSpeciesListType();
    String refSpeciesListName = toolProps.getRefSpeciesListName();    
    int projLogId = toolProps.getProjLogId();
    int geoLogId = toolProps.getGeoLogId();

    SpeciesListDb speciesListDb = new SpeciesListDb(connection);     
        
    int maxListSize = 0;    
    ArrayList<Taxon> mapSpeciesList1 = null;
    if ((mapSpeciesList1Name != null) && (!"none".equals(mapSpeciesList1Name))) {
      mapSpeciesList1 = speciesListDb.getSpeciesList(displaySubfamily, mapSpeciesList1Name);
      if (mapSpeciesList1.size() > maxListSize) maxListSize = mapSpeciesList1.size();
    }
    ArrayList<Taxon> mapSpeciesList2 = null;
    if ((mapSpeciesList2Name != null) && (!"none".equals(mapSpeciesList2Name))) {
      mapSpeciesList2 = speciesListDb.getSpeciesList(displaySubfamily, mapSpeciesList2Name);
      if (mapSpeciesList2.size() > maxListSize) maxListSize = mapSpeciesList2.size();
    }
    ArrayList<Taxon> mapSpeciesList3 = null;
    if ((mapSpeciesList3Name != null) && (!"none".equals(mapSpeciesList3Name))) {
      mapSpeciesList3 = speciesListDb.getSpeciesList(displaySubfamily, mapSpeciesList3Name);
      if (mapSpeciesList3.size() > maxListSize) maxListSize = mapSpeciesList3.size();
    }
    ArrayList<Taxon> refSpeciesList = null;

/*
     && (!"none".equals(refSpeciesListType)) 
     && (!refSpeciesListType.contains("search"))  
     && (!refSpeciesListType.contains("antcatNames")) 
    ) {
      // toolProps.setRefSpeciesListName(getSearchName(toolForm));
      if (!"speciesListHistory".equals(refSpeciesListType)) {
*/
    if ("speciesList".equals(refSpeciesListType)) {
        refSpeciesList = speciesListDb.getSpeciesList(displaySubfamily, refSpeciesListName);
        if (refSpeciesList.size() > maxListSize) {
          if ("none".equals(displaySubfamily)) displaySubfamily = "amblyoponinae";
        }
    }    

  String size = " refSpeciesList"; if (refSpeciesList == null) size += ":null"; else size += ".size:" + refSpeciesList.size();
  //A.log("setSpeciesListMappings() refSpeciesListType:" + refSpeciesListType + " refSpeciesListName:" + refSpeciesListName + size);

    if ((refSpeciesListType != null) && refSpeciesListType.contains("antcatNames")) {
      toolProps.setRefSpeciesListParams("");
      if ("none".equals(displaySubfamily)) displaySubfamily = "amblyoponinae";
    }
        
    //A.log("setSpeciesListMappings() mapSpeciesList1Name:" + mapSpeciesList1Name + " mapSpeciesList2Name:" + mapSpeciesList2Name + " refSpeciesListName:" + refSpeciesListName + " refSpeciesList:" + refSpeciesList);

    // if we find the lists too large, use an displaySubfamily to restrict...
    // maxListsSize only considers the lists to post, not the sum list.
    if (maxListSize > s_maxSpeciesListSizeBeforeDisplaySubfamily) {
      toolProps.setDisplaySubfamily("amblyoponinae");
      setSpeciesListMappings(toolForm, toolProps, connection);
      A.log("returning with displaySubfamily:" + displaySubfamily);
      return;
    } else {
      toolProps.setDisplaySubfamily("none");
    }

    String projectCriteria = speciesListDb.getProjectCriteria(displaySubfamily, mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name
        , refSpeciesListType, refSpeciesListName);

    String geolocaleCriteria = speciesListDb.getGeolocaleCriteria(displaySubfamily, mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name
        , refSpeciesListType, refSpeciesListName);
  
    String displaySubfamilyCriteria = "";
    if (!"none".equals(displaySubfamily)) {
        displaySubfamilyCriteria = "   and t.taxon_name like '" + displaySubfamily + "%'";
    }

    //A.log("SpeciesListToolAction.setSpeciesListMappines() refSpeciesListName:" + refSpeciesListName + " geolocaleCriteria:" + geolocaleCriteria + " refSpeciesListName:" + refSpeciesListName + " displaySubfamily:" + displaySubfamily);
    ArrayList<Taxon> sumSpeciesList = speciesListDb.getSumSpeciesList(displaySubfamilyCriteria, projectCriteria, geolocaleCriteria);

// Geo?
    // A species list history.  Add to the sumSpeciesList and to the refSpeciesList (to notice for taxa that should be deleted - red x's). 
    //ArrayList<OverviewTaxon> logDetails = null;
    if (projLogId != 0 || geoLogId != 0) {
      if (projLogId != 0) {
		  refSpeciesList = new ArrayList<>();
		  ArrayList<ProjTaxonLogDetail> logDetails = (new ProjTaxonLogDb(connection)).getProjTaxonLogDetails(projLogId, displaySubfamily);  
	  
		  //TaxonDb taxonDb = new TaxonDb(connection);
		  for (ProjTaxonLogDetail  logDetail : logDetails) {  // was: ProjTaxonLogDetail 
			Taxon detailTaxon = new TaxonDb(connection).getTaxon(logDetail.getTaxonName());

			//A.log("setSpeciesListMappings() projLogId:" + projLogId + " detailTaxon:" + detailTaxon);        
			if (detailTaxon == null) {
				// This was adding a null to the list and creating for a null pointer below.  proceratiinaeproceratium scm02 was not found.
				s_log.warn("setSpeciesListMappings() detailTaxon is null for logDetail.taxonName:" + logDetail.getTaxonName());
			} else {
				sumSpeciesList.remove(detailTaxon);
				sumSpeciesList.add(detailTaxon);
				refSpeciesList.add(detailTaxon);
			}
		  }
      } else { // OK. GeoLogId:
		  refSpeciesList = new ArrayList<>();
		  ArrayList<GeolocaleTaxonLogDetail> logDetails = (new GeolocaleTaxonLogDb(connection)).getGeolocaleTaxonLogDetails(geoLogId, displaySubfamily);  
	  
//		  TaxonDb taxonDb = new TaxonDb(connection);
		  for (GeolocaleTaxonLogDetail  logDetail : logDetails) {  // was: ProjTaxonLogDetail 
			Taxon detailTaxon = new TaxonDb(connection).getTaxon(logDetail.getTaxonName());

			//A.log("setSpeciesListMappings() geoLogId:" + geoLogId + " detailTaxon:" + detailTaxon);        
			if (detailTaxon == null) {
				// This was adding a null to the list and creating for a null pointer below.  proceratiinaeproceratium scm02 was not found.
				s_log.warn("setSpeciesListMappings() detailTaxon is null for logDetail.taxonName:" + logDetail.getTaxonName());
			} else {
				sumSpeciesList.remove(detailTaxon);
				sumSpeciesList.add(detailTaxon);
				refSpeciesList.add(detailTaxon);
			}
		  }
      }      
      Collections.sort(sumSpeciesList);
    }      
      
    if (advSearchTaxa != null) {      
        refSpeciesList = new ArrayList<>();
        for (Taxon searchTaxon : advSearchTaxa) {  // displaySubfamily == null added recently.  Unreplicated or tested.
        String geoSubfamily = null;
        if (searchTaxon != null) geoSubfamily = searchTaxon.getSubfamily();
        // Why would this be null? ***       
        if ("none".equals(displaySubfamily) || (displaySubfamily == null) || (geoSubfamily != null && displaySubfamily.equals(geoSubfamily))) {
          //A.log("setSpeciesListMappings() add searchTaxon:" + searchTaxon);
          //sumSpeciesList.remove(searchTaxon);  // So that the list remains unique.
          if (!sumSpeciesList.contains(searchTaxon)) sumSpeciesList.add(searchTaxon);
          refSpeciesList.add(searchTaxon);
        }
      }
      Collections.sort(sumSpeciesList);
    }

    // Here we populate the transient Taxon country and adm1 lists so that when displayed they
    // can match and display yellow ant indicators.

    // Slow!  setCountryLists and setAdm1Lists are slow.  
    String countryLocalityCriteria = getCountryLocalityCriteria(mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name);
    A.log("setSpeciesListMappings() countryLocalityCriteria:" + countryLocalityCriteria);
    setCountryLists(connection, sumSpeciesList, countryLocalityCriteria);

    String adm1LocalityCriteria = getAdm1LocalityCriteria(mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name);
    if (adm1LocalityCriteria == null) s_log.warn("setSpeciesListMappings() adm1LocalityCriteria null for lists:" 
      + mapSpeciesList1Name + ", " + mapSpeciesList2Name + ", " + mapSpeciesList3Name);
    setAdm1Lists(connection, sumSpeciesList, adm1LocalityCriteria);

    //A.log("setSpeciesListMappings() countryCriteria:" + countryLocalityCriteria + " adm1Criteria:" + adm1LocalityCriteria + " displaySubfamily:" + displaySubfamily);

    toolProps.setMapSpeciesList1(mapSpeciesList1);
    toolProps.setMapSpeciesList2(mapSpeciesList2);
    toolProps.setMapSpeciesList3(mapSpeciesList3);
    toolProps.setRefSpeciesList(refSpeciesList);
    toolProps.setSumSpeciesList(sumSpeciesList);
    ArrayList<String> refListSubfamilies = speciesListDb.getRefListSubfamilies(projectCriteria, geolocaleCriteria, advSearchTaxa);    
    toolProps.setRefListSubfamilies(refListSubfamilies);
    toolProps.setDisplaySubfamily(displaySubfamily);
    saveCheckedLists(toolProps, mapSpeciesList1, mapSpeciesList2, mapSpeciesList3, sumSpeciesList);

    size = " refSpeciesList"; if (refSpeciesList == null) size += ":null"; else size += ".size:" + refSpeciesList.size();
    //A.log("setSpeciesListMappings() refSpeciesListName:" + refSpeciesListName + " refListSubfamilies:" + refListSubfamilies + size);
  }

  /** 
      This method replicates the work of speciesListMapping-body.jsp and saves the checked
      list on the server so that the post size is not to great.
   */
  private void saveCheckedLists(SpeciesListToolProps toolProps, ArrayList<Taxon> mapSpeciesList1
      , ArrayList<Taxon> mapSpeciesList2, ArrayList<Taxon> mapSpeciesList3
      , ArrayList<Taxon> sumSpeciesList
    ) {
    
    if  (sumSpeciesList == null) s_log.warn("saveCheckedLists() sumSpeciesList:" + sumSpeciesList);
    
    ArrayList<String> oldChosenList1 = new ArrayList<>();
    ArrayList<String> oldChosenList2 = new ArrayList<>();
    ArrayList<String> oldChosenList3 = new ArrayList<>();
  
    for (Taxon taxon : sumSpeciesList) { 
     if (taxon == null) s_log.warn("saveCheckedLists() taxon:null sumSpeciesList:" + sumSpeciesList);
      String taxonName = taxon.getTaxonName(); 

      if ((mapSpeciesList1 != null) && (!mapSpeciesList1.isEmpty())) { 
        if (mapSpeciesList1.contains(taxon))
          oldChosenList1.add(taxonName);
      }
      if ((mapSpeciesList2 != null) && (!mapSpeciesList2.isEmpty())) { 
        if (mapSpeciesList2.contains(taxon))
          oldChosenList2.add(taxonName);
      }
      if ((mapSpeciesList3 != null) && (!mapSpeciesList3.isEmpty())) { 
        if (mapSpeciesList3.contains(taxon))
          oldChosenList3.add(taxonName);
      }
    }
    toolProps.setOldChosenList1(oldChosenList1);
    toolProps.setOldChosenList2(oldChosenList2);
    toolProps.setOldChosenList3(oldChosenList3);
  }

  private String getCountryLocalityCriteria(String mapSpeciesList1Name, String mapSpeciesList2Name
      , String mapSpeciesList3Name) {
    String criteria = "";
    boolean nonEmpty = false;
    if (!"none".equals(mapSpeciesList1Name) && !Project.isProjectName(mapSpeciesList1Name) && !Utility.isNumber(mapSpeciesList1Name)) {
      criteria += "'" + mapSpeciesList1Name + "'"; // Project.getLocalityName(mapSpeciesList1Name)
      nonEmpty = true;
    }
    if (!"none".equals(mapSpeciesList2Name) && !Project.isProjectName(mapSpeciesList2Name) && !Utility.isNumber(mapSpeciesList2Name)) {
      if (nonEmpty) criteria += ", ";
      criteria += "'" + mapSpeciesList2Name + "'";
      nonEmpty = true;
    }
    if (!"none".equals(mapSpeciesList3Name) && !Project.isProjectName(mapSpeciesList3Name) && !Utility.isNumber(mapSpeciesList3Name)) {
      if (nonEmpty) criteria += ", ";
      criteria += "'" + mapSpeciesList3Name + "'";
      nonEmpty = true;
    }
    
/*    
    if (AntwebProps.isDevMode()) {
		String exceptionalLocality = getExceptionalLocality(mapSpeciesList1Name, mapSpeciesList2Name, mapSpeciesList3Name);
		if (exceptionalLocality != null) {
		  if (nonEmpty) criteria += ", ";
		  criteria += "'" + exceptionalLocality + "'";
		  nonEmpty = true;
		}    
    }    
 */
    if (nonEmpty) {
      criteria = " country in (" + criteria + ")";
    }
    //A.log("getCountryLocalityCriteria() criteria:" + criteria + " mapSpeciesList1Name:" + mapSpeciesList1Name);
    return criteria;
  }
  
  private String getAdm1LocalityCriteria(String mapSpeciesList1Name, String mapSpeciesList2Name
      , String mapSpeciesList3Name) {
    String criteria = "";
    boolean nonEmpty = false;
    
    // The mapSpeciesList?Name variables are the key. For Adm1, an integer.
    // So, get the speciesListable and use the name?
    
    if (!"none".equals(mapSpeciesList1Name) && Utility.isNumber(mapSpeciesList1Name)) {
      criteria += "'" + SpeciesListMgr.getName(mapSpeciesList1Name) + "'";  // Project.getLocalityName(mapSpeciesList1Name) + "'";
      nonEmpty = true;
    }
    if (!"none".equals(mapSpeciesList2Name) && Utility.isNumber(mapSpeciesList2Name)) {
      if (nonEmpty) criteria += ", ";
      criteria += "'" + SpeciesListMgr.getName(mapSpeciesList2Name) + "'";
      nonEmpty = true;
    }
    if (!"none".equals(mapSpeciesList3Name) && Utility.isNumber(mapSpeciesList3Name)) {
      if (nonEmpty) criteria += ", ";
      criteria += "'" + SpeciesListMgr.getName(mapSpeciesList3Name) + "'";
      nonEmpty = true;
    }

    if (nonEmpty) {
      criteria = " adm1 in (" + criteria + ")";
    }
    return criteria;
  }

  private void setCountryLists(Connection connection, ArrayList<Taxon> speciesList, String countryCriteria) 
    throws SQLException {

    // This is used to determine if the yellow ant indicator is present.

    if (countryCriteria == null || "".equals(countryCriteria)) {
      s_log.info("setCountryLists empty countryCriteria.");
      return;
    }

    String query = null;
    Statement stmt = DBUtil.getStatement(connection, "SpeciesListToolAction.setCountryLists");
    ResultSet rset = null;    
    try {
      for (Taxon taxon : speciesList) {
      String taxonName = taxon.getTaxonName();
      query = "select distinct country as country from specimen" 
        + " where taxon_name = '" + taxonName + "'"
        + " and " + countryCriteria;
      
      boolean debug = "myrmicinaeacanthognathus brevicornis".equals(taxonName);
      if (debug) A.log("setCountryLists() SLOW query:" + query);
      
      ArrayList<String> countries = new ArrayList<>();
        rset = stmt.executeQuery(query);
        while (rset.next()) {
          String country = rset.getString("country");
          countries.add(country);
        }
        if (debug) A.log("setCountryLists() taxonName:" + taxonName + " countries:" + countries);
        taxon.setCountryList(countries);
      }
    } catch (Exception e) {
      s_log.warn("setCountryLists() e:" + e + " query:" + query);
    } finally {
      DBUtil.close(stmt, rset, "SpeciesListToolAction.setCountryLists");
    }      
  }  
  
  private void setAdm1Lists(Connection connection, ArrayList<Taxon> speciesList, String adm1Criteria) 
    throws SQLException {
    
    if (adm1Criteria == null || "".equals(adm1Criteria)) {
      s_log.warn("setAdm1Lists() adm1Criteria:" + adm1Criteria + " speciesList.size():" + speciesList.size());
      return;
    }
    
    String query = null;
    Statement stmt = DBUtil.getStatement(connection, "SpeciesListToolAction.setAdm1Lists");
    ResultSet rset = null;    
    try {
      for (Taxon taxon : speciesList) {
      String taxonName = taxon.getTaxonName();
      query = "select distinct adm1 as adm1 from specimen" 
        + " where taxon_name = '" + taxonName + "'"
        + " and " + adm1Criteria;
      //A.log("setAdm1Lists query:" + query);
      ArrayList<String> adm1s = new ArrayList<>();
        rset = stmt.executeQuery(query);
        while (rset.next()) {
          String adm1 = rset.getString("adm1");
          adm1s.add(adm1);
        }
        //A.log("setAdm1Lists() taxonName:" + taxonName + " adm1:" + adm1);
        taxon.setAdm1List(adm1s);
      }
    } catch (Exception e) {
      s_log.warn("setAdm1Lists() e:" + e + " query:" + query);
    } finally {
      DBUtil.close(stmt, rset, "SpeciesListToolAction.setAdm1Lists");
    }      
  }      
  
  private void countSearchSpecimen(Connection connection, ArrayList<Taxon> advSearchTaxa) 
    throws SQLException {

    String query;
  
    Statement stmt = DBUtil.getStatement(connection, "SpeciesListToolAction.countSearchSpecimen()");
    ResultSet rset = null;
    try {

      String lastTaxonName = null;
      for (Taxon taxon : advSearchTaxa) {

        if (taxon == null) {
           s_log.warn("countSearchSpecimen() a taxon is null?!?! Why?  lastTaxon:" + lastTaxonName + " advSearchTaxa:" + advSearchTaxa);
           continue;
        } else {
           lastTaxonName = taxon.getTaxonName();
        }

        query = "select pt.specimen_count specimenCount from proj_taxon pt" 
          + " where pt.taxon_name = '" + taxon.getTaxonName() + "'"
          + "   and pt.project_name = '" + Project.ALLANTWEBANTS + "'"
          ;
      
        //A.log("countSearchSpecimen() query:" + query);
        rset = stmt.executeQuery(query);

        while (rset.next()) {
          int specimenCount = rset.getInt("specimenCount");
          taxon.setSpecimenCount(specimenCount);
        }      
      }
    } finally {
        DBUtil.close(stmt, rset, "SpeciesListToolAction.countSearchSpecimen()");
    }      
  }      
      
    private ArrayList<ResultItem> doAdvancedSearch(SpeciesListToolForm toolForm, HttpServletRequest request) 
        throws SearchException {

        //A.log("doAdvancedSearch() toolForm:" + toolForm);

        SearchParameters searchParameters = new SearchParameters(toolForm);

		java.util.Date startTime = new java.util.Date();
        String execTime = null;
        
       // String types = searchParameters.getTypes();
       // String images = searchParameters.getImagesOnly();
         
        ArrayList<ResultItem> searchResults = null;
        try {
          searchResults = getSearchResults(request, searchParameters);
        } catch (IOException | ServletException e) {
          s_log.warn("doAdvancedSearch() e:" + e);
        }
        if (searchResults == null) {
            s_log.warn("doAdvancedSearch() null search results for searchParameters:" + searchParameters);
            return null;
        }
        if (AntwebProps.isDevMode()) {
          //s_log.warn("advancedSearch() searchParameters:" + searchParameters);
          s_log.info("search results returned " + searchResults.size() + " results");
        }
        
        AdvancedSearchResults results = new AdvancedSearchResults();
        results.setRset(searchResults);
        results.setResultsWithFilters(new ArrayList());

        //s_log.info("doAdvancedSearch() with filters:" + myFilters + " it has " + results.getSpecimens().size() + " specimens");

        if (results != null) {

          java.util.ArrayList<ResultItem> searchSpeciesList = results.getSpeciesList(); 

          return searchSpeciesList;   
        }
        return null;
    }    
    
}


package org.calacademy.antweb.search;

import org.calacademy.antweb.*;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import org.apache.struts.actions.DispatchAction;

import java.util.Date;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class SearchAction extends DispatchAction {

    private static Log s_log = LogFactory.getLog(SearchAction.class);
    private static final Log s_searchLog = LogFactory.getLog("searchLog");

    public static int tempSpecimenSearchLimit = -1;
    public static int specimenSearchLimit = 50000;   // default can be overridden with setTempSpecimenSearchLimit()
    public static int devSpecimenSearchLimit = 10000;
    public static int noSpecimenSearchLimit = 1000000;


	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        String requestInfo = AntwebUtil.getRequestInfo(request);

        //if (true || AntwebProps.isDevMode()) s_log.warn("execute() set activeSession.");
        request.getSession().setAttribute("activeSession", Boolean.valueOf(true));

        // These needs to happen, if only because MapResults will use the project from the session.
        OverviewMgr.setOverview(request, null);

        try {
          ActionForward b = Check.busy(getDataSource(request, "conPool"), request, mapping); if (b != null) return b; 

          String target = HttpUtil.getTarget(request);

          // recentImageSearch, descEditSearch, bayAreaSearch, advancedSearch
          String searchMethod = HttpUtil.getParamValue("searchMethod", request);
          //A.log("execute() searchMethod:" + searchMethod);
          String searchType = null;
          if (searchMethod != null) {
	        if (searchMethod.equals("advancedSearch")) searchType = "advancedSearch";
            if (searchMethod.equals("descEditSearch")) searchType = "descEditSearch";
            if (searchMethod.equals("bayAreaSearch")) searchType = "bayAreaSearch";
            if (searchMethod.equals("recentImageSearch")) {
              int searchTypeIndex = target.indexOf("recentImageSearch");
              int ampersandIndex = target.indexOf("&", searchTypeIndex);
              if (ampersandIndex < 0) {
                String message = "RecentImageSearch was expecting &parameter";
                return HttpUtil.sendMessage(request, mapping, message);
              }
              String nextChar = target.substring(ampersandIndex, ampersandIndex + 1);
              A.log("execute() nextChar:" + nextChar);
              if ("&".equals(nextChar)) {
                searchType = "recentImageSearch";
              } else {
                String message = "RecentImageSearch with bad nextChar:" + nextChar + " target:" + target;
                return HttpUtil.sendMessage(request, mapping, message);
              }
            }
          }

          if (form instanceof RecentImagesForm && HttpUtil.isPost(request)) {
            A.log("execute() form is RecentImagesForm");
            searchType = "recentImageSearch";
          }
          
          //A.log("searchType:" + searchType + " target:" + target);
          if (searchType != null) {
            return super.execute(mapping, form, request, response);
          } else {
             int caseNumber = AntwebUtil.getCaseNumber();
             String shortTarget = target;
             shortTarget = target.substring(0);
             s_log.warn("execute() case#:" + caseNumber + " searchType null for target:" + shortTarget + "...");
             return HttpUtil.sendMessage(request, mapping, "Invalid searchType:" + searchType + " not found. Case#:" + caseNumber + ". Please report behavior to " + AntwebUtil.getAdminEmail());
          }           
        } catch (SearchException e) {
          s_log.error("execute() se:" + e);
          return HttpUtil.sendMessage(request, mapping, e.toString());
        } catch (NoSuchMethodException e) {
          s_log.error("execute() nsme:" + e);
        } catch (Exception e) {
          s_log.error("execute() e:" + e);
          AntwebUtil.logStackTrace(e);
        }
        return null;        
    }

    private void searchLog(String str) {
      s_searchLog.info(str);
      LogMgr.appendLog("searches.txt", str, true);
    }

    public ActionForward advancedSearch(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException, SearchException {

        AdvancedSearchForm advancedSearchForm = (AdvancedSearchForm) form;

        SearchParameters searchParameters = new SearchParameters(advancedSearchForm);

        String resultRank = advancedSearchForm.getResultRank();
        if (!Rank.isLegit(resultRank)) resultRank = "specimen";
        String output = advancedSearchForm.getOutput();
        A.log("advancedSearch() resultRank:" + resultRank + " output:" + output); // statusSet:" + searchParameters.getStatusSet());

        /* Just use specimen as default. See above 3 lines.
        if (!Rank.isLegit(resultRank)) {
            int caseNumber = AntwebUtil.getCaseNumber();
            String message = "case#:" + caseNumber + ". Rank:" + resultRank + " not valid. Please report behavior to " + AntwebUtil.getAdminEmail();
            s_log.warn("advancedSearch() " + message);
            return HttpUtil.sendMessage(request, mapping, message);
        }
*/
        return doAdvancedSearch(searchParameters, resultRank, output, request, mapping);
    }

/*
	at org.calacademy.antweb.search.GenericSearch.getListFromRset(GenericSearch.java:621)
	at org.calacademy.antweb.search.AdvancedSearch.createInitialResults(AdvancedSearch.java:308)
	at org.calacademy.antweb.search.AdvancedSearch.setResults(AdvancedSearch.java:340)
	at org.calacademy.antweb.search.GenericSearch.getResults(GenericSearch.java:46)
	at org.calacademy.antweb.search.AdvancedSearchAction.getSearchResults(AdvancedSearchAction.java:198)
	at org.calacademy.antweb.search.SearchAction.doAdvancedSearch(SearchAction.java:117)
	at org.calacademy.antweb.search.SearchAction.advancedSearch(SearchAction.java:100)
	*/
    private ActionForward doAdvancedSearch(SearchParameters searchParameters, String resultRank, String output
        , HttpServletRequest request, ActionMapping mapping) 
        throws IOException, ServletException, SearchException {

      try {
        if (LoginMgr.isAdmin(request)) {
          SearchAction.setTempSpecimenSearchLimit(noSpecimenSearchLimit);   
          A.log("doAdvancedSearch() use limit:" + noSpecimenSearchLimit);
        }

        HttpSession session = request.getSession();
		java.util.Date startTime = new java.util.Date();
        String execTime = null;
        
        String types = searchParameters.getTypes();
        String imagesOnly = searchParameters.getImagesOnly();
         
        //A.log("SearchAction.doAdvancedSearch() imagesOnly:" + imagesOnly);         
        AdvancedSearchAction searchAction = new AdvancedSearchAction();
        searchAction.setServlet(servlet);

        ArrayList<ResultItem> searchResults = searchAction.getSearchResults(request, searchParameters);

		//A.log("SearchAction.doAdvancedSearch() 1 list:" + searchResults.get(0).getDateCollectedStart());
			                 
        if (searchResults == null) {
            //s_log.warn("doAdvancedSearch() searchResults=null for searchParameters:" + searchParameters);
            return (mapping.findForward("failure"));
        }
        if (AntwebProps.isDevMode()) {
          //s_log.warn("advancedSearch() searchParameters:" + searchParameters);
          s_log.info("search results returned " + searchResults.size() + " results");
        }
        
        String title = searchAction.getSearchTitle(searchParameters);
        
        AdvancedSearchResults results = new AdvancedSearchResults();
        results.setRset(searchResults);
        ArrayList<String> myFilters = new ArrayList<String>();
        if ((imagesOnly != null) && (imagesOnly.equals("on"))) {
            myFilters.add("images");
        }
        if ((types != null) && (types.equals("on"))) {
            myFilters.add("types");
        }
        //s_log.info("doAdvancedSearch() with filters:" + myFilters + " it has " + results.getSpecimens().size() + " specimens");
        results.setResultsWithFilters(myFilters);   // throws Exception    
          
        //A.log("SearchAction.doAdvancedSearch() size:" + results.getResults().size() + " params:" + searchParameters);        

		//A.log("SearchAction.doAdvancedSearch() 2 list:" + results.getResults().get(0).getDateCollectedStart());			                      
        // setMap(connection, session);

        session.setAttribute("advancedSearchResults", results);
        session.setAttribute("fullAdvancedSearchResults", results.getResults());
        session.removeAttribute("searchResults");
        session.setAttribute("searchTitle", title);
        //A.log("SearchAction.doAdvancedSearch() resultRank:" + resultRank);
        
        ActionForward forward = null;
        
        if (results != null) {
          request.setAttribute("isSearchPage", "true");
          String greaterThanModifier = "";
          if (resultRank == null || "".equals(resultRank)) resultRank = ResultRank.SPECIMEN;

          if (ResultRank.SPECIMEN.equals(resultRank)) {
            int resultSetSize = results.getResults().size();

            if (resultSetSize >= SearchAction.getSpecimenSearchLimit()) 
                greaterThanModifier = " (Search Results truncated due to result set size).";  

            String resultSetModifier = resultSetSize + " specimens" + greaterThanModifier;
            session.setAttribute("resultSetModifier", resultSetModifier);          
          } else {                    
            java.util.ArrayList taxonList = null;
            if (resultRank.equals(ResultRank.SUBFAMILY)) taxonList = results.getSubfamilyList(); 
            if (resultRank.equals(ResultRank.GENUS)) taxonList = results.getGenusList(); 
            if (resultRank.equals(ResultRank.SPECIES)) taxonList = results.getSpeciesList(); 
                      
            session.setAttribute("taxonList", taxonList);

            int resultSetSize = results.getResults().size();

            if (resultSetSize >= SearchAction.getSpecimenSearchLimit()) 
                greaterThanModifier = "  (Search Results truncated due to result set size).";  

            if (taxonList == null || resultRank == null) AntwebUtil.log("SearchAction.doAdvancedSearch() taxonList:" + taxonList + " resultRank:" + resultRank);

            String resultSetModifier = "";
            if (taxonList != null) resultSetModifier += taxonList.size() + " " + Rank.getPluralRank(resultRank);
            resultSetModifier += " (from " + resultSetSize + " specimens)." + greaterThanModifier;
            session.setAttribute("resultSetModifier", resultSetModifier);
          }

          if (output == null || "".equals(output)) output = Output.LIST;
          if (Output.LIST.equals(output)) {
            if (ResultRank.SPECIMEN.equals(resultRank)) {
              forward = mapping.findForward("advanced");
            } else {
              forward = mapping.findForward("advancedTaxon");          
            }
          }
          if (Output.MAP_LOCALITY.equals(output) || Output.MAP_SPECIMEN.equals(output)) {

            // Code copied from MapResultsAction.java.  
			Map map = null;
			Connection connection = null;
			try {
				javax.sql.DataSource dataSource = getDataSource(request,"conPool");
				connection = DBUtil.getConnection(dataSource, "SearchAction.doAdvancedSearch()");  
				title = "Mapping Search Results";
				map = (new MapResultsAction()).getMap(results.getResults(), null, null, resultRank, output, title, connection); // nulls are taxonList, chosenList
			} catch (IndexOutOfBoundsException e2) {
			  String message = "Case#:" + AntwebUtil.getCaseNumber() + " e:" + e2 + " target:" + HttpUtil.getTarget(request);
			  s_log.warn("execute() message:" + message);
			  request.setAttribute("message", message);
			  return mapping.findForward("message");                   
			} catch (SQLException e) {
				s_log.error("execute() e:" + e);
				org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
			} finally {
				DBUtil.close(connection, this, "SearchAction.doAdvancedSearch()");
			}  
			
			String sizeStr = (map.getChosenList() == null) ? "null" : "" + map.getChosenList().size();
			A.log("SearchAction.doAdvancedSearch() resultRank:" + resultRank + " title:" + map.getTitle() + " chosenList.size:" + sizeStr + " map:" + map);

            String pageTitle = "";
            //if (Output.MAP_LOCALITY.equals(output)) pageTitle += map.getPointCounter() + " localities.";
            //if (Output.MAP_SPECIMEN.equals(output)) pageTitle += map.getPointCounter() + " specimen.";

            A.log("SearchAction.doAdvancedSearch() title:" + title + " pageTitle:" + pageTitle);

            pageTitle = " " + map.getTitle();
            
			//session.setAttribute("title", pageTitle);  // now redundant. Could change the client code as well.
			session.setAttribute("map", map);
			session.setAttribute("chosenList", map.getChosenList());

			return (mapping.findForward("dynamicMap"));                  
          }

          session.setAttribute("resultRank", resultRank);        
          execTime = AntwebUtil.finish(request, startTime);
          searchLog("doAdvancedSearch() time:" + execTime + " title:" + title);		    

          //A.log("doAdvancedSearch() forward:" + forward);          
          return forward;

        } else {
            //A.log("doAdvancedSearch() failure");  
            return (mapping.findForward("failure"));
        }
      } finally {
        SearchAction.undoSetTempSpecimenSearchLimit();        
      }
    }

    // This attempts to get values from the form and invoke the advanced search process
    public ActionForward bayAreaSearch(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException, SearchException {

        HttpSession session = request.getSession();
		java.util.Date startTime = new java.util.Date(); // for AntwebUtil.finish(request, startTime);

        BayAreaSearchForm bayForm = (BayAreaSearchForm) form;

        SearchParameters searchParameters = new SearchParameters(bayForm);
        
        String resultRank = ResultRank.SPECIES;
        String output = Output.LIST;
        return doAdvancedSearch(searchParameters, resultRank, output, request, mapping);
    }

    public ActionForward descEditSearch(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException, SearchException {
            
        HttpSession session = request.getSession();
        SearchParameters searchParameters =
            new SearchParameters((RecentImagesForm) form);
            
        String project = searchParameters.getProject();

        searchLog("descEditSearch() project:" + project);
        
        DescEditSearchAction searchAction = new DescEditSearchAction();
        searchAction.setServlet(servlet);
        ArrayList<ResultItem> searchResults = searchAction.getSearchResults(request, response, searchParameters);
        DescEditSearchResults results = new DescEditSearchResults();
        try {
            results.setRset(searchResults);
            results.setProject(project);
            results.setResults();
        } catch (Exception e) {
            s_log.error("descEditSearch() e:" + e);
        } 
        session.setAttribute("searchResults", results);

        if (results != null) {
            return (mapping.findForward("success"));
        } else {
            return (mapping.findForward("failure"));
        }
    }

    public ActionForward recentImageSearch(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException, SearchException {
            
		java.util.Date startTime = new java.util.Date(); // for AntwebUtil.finish(request, startTime);
            
        HttpSession session = request.getSession();
        SearchParameters searchParameters = new SearchParameters((RecentImagesForm) form);
            
        String project = searchParameters.getProject();
        searchLog("recentImageSearch() shortParams:" + searchParameters.toStringShort());

        RecentImageSearchAction searchAction = new RecentImageSearchAction();
        searchAction.setServlet(servlet);
        ArrayList<ResultItem> searchResults = searchAction.getSearchResults(request, response, searchParameters);
        RecentImageSearchResults results = new RecentImageSearchResults();
        try {
            results.setRset(searchResults);
            results.setProject(project);
            results.setResults();
        } catch (Exception e) {
            s_log.error("error setting results in new search action " + e);
        } 
        session.setAttribute("searchResults", results);

		AntwebUtil.finish(request, startTime);

        if (results != null) {
            return (mapping.findForward("success"));
        } else {
            return (mapping.findForward("failure"));
        }
    }    

    public static void setSpecimenSearchLimit(int limit) {
      specimenSearchLimit = limit;
    }
    public static void setTempSpecimenSearchLimit(int limit) {
      tempSpecimenSearchLimit = limit;
    }
    public static void undoSetTempSpecimenSearchLimit() {
      tempSpecimenSearchLimit = -1;
    }
    public static int getSpecimenSearchLimit() {
      if (tempSpecimenSearchLimit > 0) return tempSpecimenSearchLimit;
      
      if (AntwebProps.isDevMode()) {
        return devSpecimenSearchLimit;
      } else {
        if (tempSpecimenSearchLimit > 0) return tempSpecimenSearchLimit;
        return specimenSearchLimit;
      }
    }
    public static boolean isTempSpecimenSearchLimit() {
      if (tempSpecimenSearchLimit > 0) return true;
      return false;
    }    

/*    private void finalizeOldResults(HttpSession session, String searchResultsType) {
        GenericSearchResults oldResults = (GenericSearchResults) session.getAttribute(searchResultsType);
        if (oldResults != null) {
            try {
                oldResults.finalize();
            } catch (Throwable e) {
                s_log.error("finalizeOldResults() e:" + e);
            }
        }
    }
   */ 

}

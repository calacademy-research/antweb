package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import java.sql.*;

import org.calacademy.antweb.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class PrepareFieldGuideResultsAction extends PrepareAction {

    private static final Log s_log = LogFactory.getLog(PrepareFieldGuideResultsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        if (session.getAttribute("activeSession") == null) return mapping.findForward("sessionExpired");
        String resultRank = request.getParameter("resultRank");
        if (resultRank == null) return mapping.findForward("error");
          
        String resultSetModifier = "unset";
        GenericSearchResults results = null;
        String forwardString = "failure";
        if (resultRank.equals("specimen")) {
            results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
			if (session.getAttribute("fullAdvancedSearchResults") != null) results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            forwardString = "advancedSearch"; 
        } else if ((resultRank.equals("species")) || (resultRank.equals("genus")) || (resultRank.equals("subfamily"))) {
            results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
			if (session.getAttribute("fullAdvancedSearchResults") != null) results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            request.setAttribute("resultRank", resultRank);            
            forwardString = "advancedSearchByTaxon";
        }
        /* else if (resultRank.equals("taxa")) {
            results = (SearchResults) session.getAttribute("searchResults");
            forwardString = "search";
        } else if  (resultRank.equals("bayArea")) { 
            results = (BayAreaSearchResults) session.getAttribute("searchResults");
            forwardString = "bayAreaSearch";
        }*/
         
        if (results == null) {
          s_log.error("Stale session?  No results.  resultRank:" + resultRank);
          return mapping.findForward("error");
        }

		session.setAttribute("fullAdvancedSearchResults", results.getResults());  // This is held for PrepareCompareResults, download

        resultSetModifier = results.getResults().size() + " specimens";

        if (resultRank.equals("species")) {
            ArrayList<ResultItem> taxonList = ((AdvancedSearchResults) results).getSpeciesList();
            resultSetModifier = taxonList.size() + " species " + "(from " + results.getResults().size() + " specimens)";
			session.setAttribute("taxonList", taxonList);
        }

        if (session.getAttribute("advancedSearchResults") != null) {
            session.setAttribute("advancedSearchResults", results);
        } else if (session.getAttribute("searchResults") != null) {
            session.setAttribute("searchResults", results);
        }

        request.setAttribute("resultSetModifier", resultSetModifier);

        return (mapping.findForward(forwardString));
    }

    public ArrayList<ResultItem> getModifiedSet(String resultRank, ArrayList theResults, HttpServletRequest request) {
        // In the case of field guides, the result set is not modified.  Not just the geo-located or imaged results.
		return theResults;
    }
        
 }

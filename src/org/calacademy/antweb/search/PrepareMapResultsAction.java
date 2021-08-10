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
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class PrepareMapResultsAction extends PrepareAction {

    private static final Log s_log = LogFactory.getLog(PrepareMapResultsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        Locale locale = getLocale(request);
        HttpSession session = request.getSession();
        
/*
Session objects:
  activeSession
  fullAdvancedSearchResults - 
  taxonList
  advancedSearchResults
  searchResults
  resultSetModifier

Request objects:
  resultRank  
*/        
        
        if (session.getAttribute("activeSession") == null) return mapping.findForward("sessionExpired");
        
        String resultRank = request.getParameter("resultRank");
        if (resultRank == null) {
          return mapping.findForward("error");
        }
        
        String resultSetModifier = "unset";
        GenericSearchResults results = null;
        String forwardString = "failure";
        switch (resultRank) {
            case "specimen":
                results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
                if (session.getAttribute("fullAdvancedSearchResults") != null)
                    results.setResults((ArrayList) session.getAttribute("fullAdvancedSearchResults")); // added to fix session
                forwardString = "advancedSearch";
                break;
            case "locality":
                results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
                if (session.getAttribute("fullAdvancedSearchResults") != null)
                    results.setResults((ArrayList) session.getAttribute("fullAdvancedSearchResults")); // added to fix session
                forwardString = "advancedSearch";
                break;
            case "species":
            case "genus":
            case "subfamily":
                results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
                if (session.getAttribute("fullAdvancedSearchResults") != null)
                    results.setResults((ArrayList) session.getAttribute("fullAdvancedSearchResults")); // added to fix session
                request.setAttribute("resultRank", resultRank);
                forwardString = "advancedSearchByTaxon";
                break;
            case "bayArea":
                results = (BayAreaSearchResults) session.getAttribute("searchResults");
                forwardString = "bayAreaSearch";
                break;
        }
         
        /* 
        if (results == null) {
          s_log.error("Stale session?  No results.  resultRank:" + resultRank);
          return mapping.findForward("error");
        }*/

		session.setAttribute("fullAdvancedSearchResults", results.getResults());  // This is held for PrepareCompareResults, download

		ArrayList newResults = getModifiedSet(resultRank, results.getResults(), request);
		results.setResults(newResults);

        resultSetModifier = newResults.size() + " georeferenced specimens";

        if (resultRank.equals("species")) {
            ArrayList taxonList = ((AdvancedSearchResults) results).getSpeciesList();
            resultSetModifier = taxonList.size() + " georeferenced species "
               + "(from " + results.getResults().size() + " specimens)";
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
        ArrayList newResults = new ArrayList();
        Iterator iter = theResults.iterator();
        ResultItem thisItem = null;
        Connection connection = null;
        try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, "PrepareMapResultsAction.getModifiedSet()");                    
          while (iter.hasNext()) {
            thisItem = (ResultItem) iter.next();            
            if (hasGeoRefInfo(thisItem, connection, resultRank)) {
                newResults.add(thisItem);
            }
          }            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
		} finally {
            DBUtil.close(connection, this, "PrepareMapResultsAction.getModifiedSet()");
		}
		return newResults;
    }
        
    private boolean hasGeoRefInfo(ResultItem theItem, Connection connection, String resultRank) 
          throws SQLException {
        boolean hasInfo = false;
        String theQuery = "";

        //if (util.notBlank(theItem.getCode())) {
        if ((resultRank != null ) && (resultRank.equals("specimen"))) {
            theQuery = "select decimal_latitude, decimal_longitude from specimen where code = '" + theItem.getCode() + "'";
        } else if ((resultRank != null ) && (resultRank.equals("locality"))) {
            AntwebUtil.log("hasGeoRefInfo() resultRank=locality needs to be handled! item:" + theItem);
        } else {
            ArrayList terms = new ArrayList();
            theQuery = "select decimal_latitude, decimal_longitude from specimen ";
            if (Utility.notBlank(theItem.getSubfamily())) {
                terms.add("subfamily='" + theItem.getSubfamily() + "'");
            }
            if (Utility.notBlank(theItem.getGenus())) {
                terms.add("genus='" + theItem.getGenus() + "'");
            }
            //if ((Utility.notBlank(theItem.getSpecies())) && (theItem.getRank().equals("species"))) {
            if (Utility.notBlank(theItem.getSpecies())) {
                terms.add("species='" + theItem.getSpecies() + "'");
            }
            if (terms.size() > 0) {
                theQuery += " where " + Utility.andify(terms);
            }
        }
        
        Statement stmt = null;
        ResultSet rset = null;
        try {
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            if (rset.next() && (hasInfo == false)) {
                int lat = rset.getInt(1);
                int lon = rset.getInt(2);
                if (lat != 0 && lon != 0)
                  hasInfo = true;
            }
        } catch (SQLException e) {
            s_log.error("hasGeoRefInfo() theQuery:" + theQuery + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "hasGeoRefInfo()");
        }
        
        //s_log.warn("hasGeoRefInfo code:" + theItem.getCode() + " hasInfo:" + hasInfo + " theQuery:" + theQuery);
        return hasInfo;
    }
 }

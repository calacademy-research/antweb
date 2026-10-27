package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

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

        ActionForward loginNeeded = org.calacademy.antweb.util.Check.loginForFeature(request); if (loginNeeded != null) return loginNeeded;

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

        return mapping.findForward(forwardString);
    }

    public ArrayList<ResultItem> getModifiedSet(String resultRank, ArrayList theResults, HttpServletRequest request) {
        ArrayList newResults = new ArrayList();
        Iterator iter = theResults.iterator();
        ResultItem thisItem = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("PrepareMapResultsAction.getModifiedSet()");
        try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);
          while (iter.hasNext()) {
            thisItem = (ResultItem) iter.next();            
            if (hasGeoRefInfo(thisItem, connection, resultRank)) {
                newResults.add(thisItem);
            }
          }            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
		} finally {
            DBUtil.close(connection, this, dbMethodName);
		}
		return newResults;
    }
        
    private boolean hasGeoRefInfo(ResultItem theItem, Connection connection, String resultRank) 
          throws SQLException {
        boolean hasInfo = false;
        String theQuery = "";

        //if (util.notBlank(theItem.getCode())) {
        if (resultRank != null && resultRank.equals("specimen")) {
            theQuery = "select decimal_latitude, decimal_longitude from specimen where code = '" + DBUtil.escapeQuotes(theItem.getCode()) + "'";
        } else if (resultRank != null && resultRank.equals("locality")) {
            AntwebUtil.log("hasGeoRefInfo() resultRank=locality needs to be handled! item:" + theItem);
        } else {
            ArrayList terms = new ArrayList();
            theQuery = "select decimal_latitude, decimal_longitude from specimen ";
            if (Utility.notBlank(theItem.getSubfamily())) {
                terms.add("subfamily='" + DBUtil.escapeQuotes(theItem.getSubfamily()) + "'");
            }
            if (Utility.notBlank(theItem.getGenus())) {
                terms.add("genus='" + DBUtil.escapeQuotes(theItem.getGenus()) + "'");
            }
            //if ((Utility.notBlank(theItem.getSpecies())) && (theItem.getRank().equals("species"))) {
            if (Utility.notBlank(theItem.getSpecies())) {
                terms.add("species='" + DBUtil.escapeQuotes(theItem.getSpecies()) + "'");
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

            if (rset.next() && !hasInfo) {
                // Read as decimals (B53, 2026-10-27): getInt cut the coordinates to whole degrees, so every specimen
                // within one degree of the equator or of the prime meridian counted as not georeferenced.
                double lat = rset.getDouble(1);
                boolean noLat = rset.wasNull();
                double lon = rset.getDouble(2);
                boolean noLon = rset.wasNull();
                if (!noLat && !noLon && lat != 0 && lon != 0)
                  hasInfo = true;
            }
        } catch (SQLException e) {
            s_log.error("hasGeoRefInfo() theQuery:" + theQuery + " e:" + e);
            throw e;
        } finally {
            DBUtil.close(stmt, rset, this, "hasGeoRefInfo()"); // This will not close the connetion. Name not used.
        }
        
        //s_log.warn("hasGeoRefInfo code:" + theItem.getCode() + " hasInfo:" + hasInfo + " theQuery:" + theQuery);
        return hasInfo;
    }
 }

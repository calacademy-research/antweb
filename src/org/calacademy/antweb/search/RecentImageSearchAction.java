package org.calacademy.antweb.search;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Helper of SearchAction.recentImageSearch (recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=N, the
 * home page's link to recently added images): runs RecentImageSearch, one row per image uploaded in the last N days
 * (1 when not given) or between two dates, optionally of one upload group (tables image, group_image, ant_group,
 * artist, specimen), and puts the upload group names in the session (groups, groupArray). Docs:
 * docs/flows/search.md. Tests: tests/e2e/test_search_pages.py::test_recent_images, test_numbers_images.py.
 */
public final class RecentImageSearchAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(RecentImageSearchAction.class);

    public String getSearchTitle(SearchParameters searchParameters) {
        return "Specimens with Images Recently Uploaded to Antweb";
    }
        
    public ArrayList<ResultItem> getSearchResults(HttpServletRequest request, HttpServletResponse response,
        SearchParameters searchParameters)
          throws IOException, ServletException {

        HttpSession session = request.getSession();

        RecentImageSearchResults results = new RecentImageSearchResults();
        RecentImageSearch search = new RecentImageSearch();
    
        if (searchParameters.getDaysAgo() == null) {
            search.setDaysAgo("1");
        } else {
            search.setDaysAgo(searchParameters.getDaysAgo());
        }
        
        if (searchParameters.getNumToShow() != null) {
            search.setNumToShow(searchParameters.getNumToShow());
        }

        if (searchParameters.getFromDate() != null) {
            search.setFromDate(searchParameters.getFromDate());
        }
        if (searchParameters.getToDate() != null) {
            search.setToDate(searchParameters.getToDate());
        }
        if (searchParameters.getGroupName() != null) {
            search.setGroup(searchParameters.getGroupName());
        }

        ArrayList<ResultItem> searchResults = null;        
	    Date startTime = new Date();

        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        String dbMethodName = DBUtil.getDbMethodName("RecentImageSearchAction.getSearchResults()");
        try {
            String theQuery = "select distinct name from ant_group";

            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            ArrayList<String> groupsArray = new ArrayList<>();

            while (rset.next()) {
                //s_log.warn("Rset:" + rset.getString(1));
                groupsArray.add(rset.getString(1));
            }
            session.setAttribute("groups", groupsArray); // To be replaced with:
            
            session.setAttribute("groupArray", GroupMgr.getGroups());  // getUploadGroups()?
            search.setConnection(connection);
            searchResults = search.getResults();
        } catch (NumberFormatException e) {
            s_log.error("getSearchResults() e:" + e);            
        } catch (Exception e) {
            s_log.error("getSearchResults() e:" + e);            
            AntwebUtil.logStackTrace(e);
        } finally {
            QueryProfiler.profile("recentImageSearch", startTime);        
            DBUtil.close(connection, stmt, rset, this, dbMethodName);
        }        
        return searchResults;
    }
}

package org.calacademy.antweb.search;

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

public final class RecentImageSearchAction extends Action {

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
        try {
            String theQuery = "select distinct name from ant_group";

            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "RecentImageSearchAction.getSearchResults()");
            stmt = connection.createStatement();
            rset = stmt.executeQuery(theQuery);

            ArrayList groupsArray = new ArrayList();

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
            DBUtil.close(connection, stmt, rset, this, "RecentImageSearchAction.getSearchResults()");
        }        
        return searchResults;
    }
}

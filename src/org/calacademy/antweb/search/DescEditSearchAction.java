package org.calacademy.antweb.search;

import org.calacademy.antweb.AntwebAction;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * Helper of SearchAction.descEditSearch, left over: reachable only as
 * recentSearchResults.do?searchMethod=descEditSearch (the /descEditSearch mapping is commented out). Despite the
 * name it runs DescEditSearch, a copy of the recent-images query (tables image, group_image, ant_group, artist,
 * specimen), not a search of page-text edits; that query names columns that do not exist (specimen.valid,
 * artist.artist), so it finds nothing. Also puts the upload group names in the session (groups).
 */
public final class DescEditSearchAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DescEditSearchAction.class);

    public String getSearchTitle(SearchParameters searchParameters) {
        return "Taxons with Description Edits Made to Antweb";
    }
        
    public ArrayList<ResultItem> getSearchResults(HttpServletRequest request, HttpServletResponse response,
        SearchParameters searchParameters)
          throws IOException, ServletException {

        HttpSession session = request.getSession();

        DescEditSearchResults results = new DescEditSearchResults();
        DescEditSearch search = new DescEditSearch();
    
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
            search.setGroupName(searchParameters.getGroupName());
        }

        ArrayList<ResultItem> searchResults = null;
        
        Connection connection = null;
        Statement stmt = null;
        ResultSet rset = null;
        String dbMethodName = DBUtil.getDbMethodName("DescEditSearchAction.getSearchResults()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            stmt = connection.createStatement();
            rset = stmt.executeQuery("select distinct name from ant_group"); // was title
            ArrayList<String> groupsArray = new ArrayList<>();

            while (rset.next()) {
                //s_log.warn("Rset:" + rset.getString(1));
                groupsArray.add(rset.getString(1));
            }
            session.setAttribute("groups", groupsArray);
            search.setConnection(connection);
            searchResults = search.getResults();
        } catch (Exception e) {
            s_log.error(e);            
            AntwebUtil.logStackTrace(e);
        } finally {
            DBUtil.close(connection, stmt, rset, this, dbMethodName);
        }

        return searchResults;
    }
}

package org.calacademy.antweb.search;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class BayAreaSearchAction extends Action {

    private static Log s_log = LogFactory.getLog(BayAreaSearchAction.class);

	public String getSearchTitle(SearchParameters searchParameters) {
		
		String[] adm2s = searchParameters.getAdm2s();
		StringBuffer sb = new StringBuffer();
		Formatter format = new Formatter();
		for (int loop=0; loop < adm2s.length; loop++) {
			if (loop > 0) {
				if (loop < adm2s.length - 1) {
					sb.append(", ");
				} else {
					sb.append(" and ");
				}
			}
			sb.append(adm2s[loop]);
		}
		String prefix = "Ants From Bay Area";
		if (adm2s.length == 1) {
			prefix += " Secondary administrative division: ";
		} else {
			prefix += " Secondary administrative divisions: ";
		}
		return prefix + format.capitalizeEachWord(sb.toString());
	}
	
	public ArrayList<ResultItem> getSearchResults(HttpServletRequest request, HttpServletResponse response,
		SearchParameters searchParameters)
		throws IOException, ServletException, SearchException {

		HttpSession session = request.getSession();

		String[] adm2s = searchParameters.getAdm2s();
		ArrayList searchResults = null;
	//	BayAreaSearchResults results = new BayAreaSearchResults();

    //s_log.warn("getSearchResults() adm2s:" + adm2s);


/*
select distinct taxon.subfamily, taxon.genus, taxon.species, taxon.taxon_name, taxon.valid, sp.adm2 
from taxon, specimen as sp  
where taxon.species != '' 
  and taxon.genus != '' 
  and taxon.valid = 1 
  and taxon.taxon_name = sp.taxon_name 
  and  (sp.adm2='yolo') 
  
searching for yolo counties in specimen table alone takes 50 seconds.  Need index.  
*/

		if (adm2s != null) {
			BayAreaSearch bayAreaSearch = new BayAreaSearch();

			Connection connection = null;
			try {
				DataSource dataSource = getDataSource(request, "conPool");
				connection = DBUtil.getConnection(dataSource, "BayAreaSearchAction.getSearchResults");

				bayAreaSearch.setAdm2s(adm2s);
				bayAreaSearch.setConnection(connection);
				searchResults = bayAreaSearch.getResults();
				
				//results.setResults(searchResults);
			} catch (SQLException sqle) {
				s_log.error("getSearchResults() e:" + sqle);
			} finally {
				DBUtil.close(connection, this, "BayAreaSearchAction.getSearchResults");
			}
		}
		return searchResults;
	}
}

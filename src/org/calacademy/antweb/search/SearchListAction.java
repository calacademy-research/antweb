package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class SearchListAction extends Action {
/* This class returns a link to the file generated for search results. */

    private static Log s_log = LogFactory.getLog(SearchListAction.class);

    private static int specimenCount = 0;

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        //String data = Specimen.getDataHeader() + "\n";
        StringBuffer data = new StringBuffer();
        data.append(Specimen.getDataHeader() + "\n");
        
        HttpSession session = request.getSession();

		GenericSearchResults results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");

		if (results == null) {
          s_log.debug("execute() advancedSearchResults not found in session");
		  //return mapping.findForward("permissionDenied"); //sessionExpired
          request.setAttribute("message", "Your session seems to have expired. Perhaps re-login.");
          return mapping.findForward("message");
          
          // if troubles, see layout.jsp:55 Known to be blank: http://localhost/antweb/getSearchList.do
        }
		
    	if (session.getAttribute("fullAdvancedSearchResults") != null) {
    	    results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
        }
        
        //A.log("execute() 1");

        Connection connection = null;
        try {
          connection = getDataSource(request, "conPool").getConnection();

  		  ArrayList theResults = results.getResults();
		  //ArrayList newResults = new ArrayList();
		  Iterator iter = theResults.iterator();
		  ResultItem thisItem = null;
		  while (iter.hasNext()) {
			thisItem = (ResultItem) iter.next();
            String code = thisItem.getCode();
            //s_log.warn("code:" + code);            

            Specimen specimen = new Specimen(code, null, connection, false); // Don't get images
            //data += specimen.getData() + "\n";
		    data.append(specimen.getData(connection) + "\n");
		  }
        } catch (SQLException e) {
          s_log.warn("execute() fetchTaxon e:" + e);
        } finally {
          try {
            connection.close();
		  } catch (SQLException e) {
			s_log.error("execute() Connection.close e:" + e);
		  }
        }

        String dir = "/web/data/search/";
        String fileName = DateUtil.getFormatDateTimeStr(new Date()) + "search.txt";
        AntwebUtil.writeDataFile(dir, fileName, data.toString());
        String url = AntwebProps.getDomainApp() + dir + fileName;
        String message = "<li>&middot; <a href=\"" + url + "\" target=\"new\">Tab-delimited data</a></li>";
        //String message = data.toString();
        
        s_log.debug("execute() url:" + url + " message:" + message);

        request.setAttribute("message", message);
        return mapping.findForward("bareMessage");
    }

}

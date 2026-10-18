package org.calacademy.antweb.search;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class SearchListAction extends AntwebAction {
/* This class returns a link to the file generated for search results. */

    private static final Log s_log = LogFactory.getLog(SearchListAction.class);

    private static final int specimenCount = 0;

    // Downloads are kept a day: long enough to fetch, and the folder does not grow.
    static final long KEEP_MILLIS = 24L * 60 * 60 * 1000;

    /** Deletes the earlier downloads (regular files ending in search.txt, never links) older than the cutoff. */
    public static int removeOldFiles(java.io.File dir, long cutoffMillis) {
        int removed = 0;
        java.io.File[] files = dir.listFiles();
        if (files == null) return 0;
        for (java.io.File f : files) {
            java.nio.file.Path p = f.toPath();
            if (!f.getName().endsWith("search.txt")) continue;
            if (!java.nio.file.Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            if (f.lastModified() >= cutoffMillis) continue;
            try {
                java.nio.file.Files.delete(p);
                removed++;
            } catch (IOException e) {
                s_log.warn("removeOldFiles() " + f.getName() + " e:" + e);
            }
        }
        return removed;
    }

    public ActionForward execute(
        ActionMapping mapping,
        ActionForm form,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {

        // The download is written to a file under web/data/search/: logged-in users only.
        Login accessLogin = LoginMgr.getAccessLogin(request);
        if (accessLogin == null) {
          request.setAttribute("message", "Please log in to download search results.");
          return mapping.findForward("message");
        }

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

  		  ArrayList<ResultItem> theResults = results.getResults();
		  //ArrayList newResults = new ArrayList();
		  Iterator<ResultItem> iter = theResults.iterator();
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
        removeOldFiles(new java.io.File(AntwebProps.getDocRoot() + dir), System.currentTimeMillis() - KEEP_MILLIS);
        String fileName = DateUtil.getFormatDateTimeStr(new Date()) + "-" + accessLogin.getId() + "search.txt";
        AntwebUtil.writeDataFile(dir, fileName, data.toString());
        String url = AntwebProps.getDomainApp() + dir + fileName;
        String message = "<li>&middot; <a href=\"" + url + "\" target=\"new\">Tab-delimited data</a></li>";
        //String message = data.toString();
        
        s_log.debug("execute() url:" + url + " message:" + message);

        request.setAttribute("message", message);
        return mapping.findForward("bareMessage");
    }

}

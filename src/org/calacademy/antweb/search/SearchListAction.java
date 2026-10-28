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

    private static final java.security.SecureRandom s_random = new java.security.SecureRandom();

    /** 32 hex characters (128 bits) for the name of a download file. */
    public static String randomPart() {
        byte[] b = new byte[16];
        s_random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
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

        StringBuffer data = new StringBuffer();
        data.append(Specimen.getDataHeader() + "\n");
        
        HttpSession session = request.getSession();

        // At most 5,000 specimens (administrators: all). DownloadLimits, 2026-10-21.
        int cap = DownloadLimits.capFor(LoginMgr.isAdmin(accessLogin));
        int matching = 0;
        ArrayList<String> codes = new ArrayList<>();

        Connection connection = null;
        try {
          connection = getDataSource(request, "conPool").getConnection();

          SearchPaging paging = (SearchPaging) session.getAttribute("advancedSearchPaging");
          if (paging != null && paging.getSearchParameters() != null) {
            // A paged specimen search: read its specimens again, in the order shown, up to the cap.
            AdvancedSearch search = new AdvancedSearchAction().buildSearch(connection, paging.getSearchParameters());
            matching = search.countPaged();
            int limit = cap > 0 ? cap : matching;
            for (ResultItem item : search.getPage(paging.getOrderBy(), limit, 0)) codes.add(item.getCode());
          } else {
            GenericSearchResults results = (AdvancedSearchResults) session.getAttribute("advancedSearchResults");
            if (results == null) {
              s_log.debug("execute() advancedSearchResults not found in session");
              request.setAttribute("message", "Your session seems to have expired. Perhaps re-login.");
              return mapping.findForward("message");
              // if troubles, see layout.jsp:55 Known to be blank: http://localhost/antweb/getSearchList.do
            }
            if (session.getAttribute("fullAdvancedSearchResults") != null) {
              results.setResults((ArrayList)session.getAttribute("fullAdvancedSearchResults")); // added to fix session
            }
            ArrayList<ResultItem> theResults = results.getResults();
            matching = theResults.size();
            for (ResultItem item : theResults) {
              if (cap > 0 && codes.size() >= cap) break;
              codes.add(item.getCode());
            }
          }

          for (String code : codes) {
            Specimen specimen = new Specimen(code, null, connection, false); // Don't get images
		    data.append(specimen.getData(connection) + "\n");
		  }
        } catch (SQLException | SearchException e) {
          s_log.warn("execute() e:" + e);
          request.setAttribute("message", "The download could not be made.");
          return mapping.findForward("bareMessage");
        } finally {
          try {
            if (connection != null) connection.close();
		  } catch (SQLException e) {
			s_log.error("execute() Connection.close e:" + e);
		  }
        }

        String dir = "/web/data/search/";
        removeOldFiles(new java.io.File(AntwebProps.getDocRoot() + dir), System.currentTimeMillis() - KEEP_MILLIS);
        // A random part (2026-10-28): the file is served from the web directory, and date, time and login id
        // alone could be guessed.
        String fileName = DateUtil.getFormatDateTimeStr(new Date()) + "-" + accessLogin.getId() + "-" + randomPart() + "search.txt";
        AntwebUtil.writeDataFile(dir, fileName, data.toString());
        String url = AntwebProps.getDomainApp() + dir + fileName;
        String message = "<li>&middot; <a href=\"" + url + "\" target=\"new\">Tab-delimited data</a></li>";
        if (DownloadLimits.isCapped(matching, cap)) {
          message += "<p>" + Html.esc(DownloadLimits.capMessage(matching)) + "</p>";
        }
        //String message = data.toString();
        
        s_log.debug("execute() url:" + url + " message:" + message);

        request.setAttribute("message", message);
        return mapping.findForward("bareMessage");
    }

}

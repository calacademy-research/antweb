package org.calacademy.antweb;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.upload.UploadUtil;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
/**
 * /collection.do?name=&lt;collection code&gt; (collection.jsp, collection-body.jsp): the Collection page, "Collection:
 * &lt;code&gt;", with its collecting data, locality, small map and specimens. Reads CollectionDb.getCollection(name)
 * (table specimen). Without a name parameter: "Must enter a collection name." (an empty name goes on to "Collection not found"); an unknown code or one without specimens:
 * "Collection not found - name:..." (a link to the cleaned code when UploadUtil.cleanCode changes it; a note when
 * an upload is running); a database error: a message, not the page. Sets the session attributes collection (also
 * as advancedSearchResults, for the Download Data box) and, when it has a locality, map. Docs:
 * docs/flows/places.md, docs/numbers.md row 65. Tests: tests/e2e/test_specimen_pages.py::
 * test_collection_page_lists_the_specimen,
 * test_numbers_search.py::test_collection_counts_equal_a_search_and_the_api.
 */
public final class CollectionAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(CollectionAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward invalid = HttpUtil.invalidRequest(request, mapping); if (invalid != null) return invalid;        

		HttpSession session = request.getSession();

        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name"); 
        s_log.debug("CollectionAction.execute() name:" + name);

        String message = null;
        if (name == null) {
          message = "Must enter a collection name.";
		  request.setAttribute("message", message);
		  return mapping.findForward("message");
        } 
        
        String queryString = request.getQueryString();
        s_log.debug("CollectionAction.execute() queryString:" + queryString + " p:" + request.getParameter("name"));

        if (HttpUtil.tooBusyForBots(request)) { return HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

		Collection collection = null;
		Connection connection = null;
        Date startTime = new Date();
        String dbMethodName = DBUtil.getDbMethodName("CollectionAction.execute()");
		try {
	 		DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
            AntwebMgr.populate(connection);            

            collection = new CollectionDb(connection).getCollection(name);

			// (debug line removed 2026-10-29: it read collection before the null check, so an unknown name gave a 500)

			//if (!success) {
			if (collection == null || 0 == collection.getSpecimenResults().getResults().size()) {
			  message = "Collection not found - name:" + Html.esc(name) + ".";

			  String cleanCode = UploadUtil.cleanCode(name);
			  //A.log("execute() cleanCode:" + cleanCode);
			  if (!name.equals(cleanCode)) {
				message = "Unsatisfactory collection code name. Perhaps looking for <a href='" + AntwebProps.getDomainApp() + "/collection.do?name=" + Html.escAttr(Html.url(cleanCode)) + "'>" + Html.esc(cleanCode) + "</a>?";
				request.setAttribute("message", message);
				return mapping.findForward("message");
			  }
        			  
              if (UploadAction.isInUploadProcess()) {
                        // An upload is currently in process.  Request that this process be re-attempted shortly.
                message += "  A curator is currently in the process of an Upload.";
			  }	else {
                // The request details go to the log only, not to the visitor (2026-10-26, finding F12).
			    String logMessage = message + "  No upload in process.  RequestInfo:" + AntwebUtil.getRequestInfo(request);
                s_log.debug("execute() " + logMessage);
                LogMgr.appendLog("badRequest.log", logMessage);
			  }
              request.setAttribute("message", message);
              return mapping.findForward("message");
			}			
			
            session.setAttribute("advancedSearchResults", collection);
            session.removeAttribute("advancedSearchPaging");  // the Download Data box reads this collection, not an earlier search
            //session.setAttribute("activeSession", new Object());
            session.setAttribute("activeSession", Boolean.TRUE);

            if (collection.getLocality() != null) {
              Map map = new Map(collection);
              //s_log.warn("Collection Map: " + map);
              session.removeAttribute("taxon");  // otherwise bigMap.do would use it by default
              session.setAttribute("map", map);              
            }
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
			// Not the page with a null collection (a blank page or an error).
			request.setAttribute("message", "The collection could not be loaded (database error). Please try again later.");
			return mapping.findForward("message");
		} finally {
		    // java.util.Date startTime = new java.util.Date();
            QueryProfiler.profile("collection", startTime);	
            DBUtil.close(connection, this, dbMethodName);
		}

        //mapping.getScope() is "session"
		session.setAttribute("collection", collection);

		// Set a transactional control token to prevent double posting
		saveToken(request);

        return mapping.findForward("success");
	}
}

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
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * /locality.do?code=&lt;locality code&gt; (or name=, tried as a code and then as a name; locality.jsp,
 * locality-body.jsp): the Locality page with the place, coordinates, small map, links to search its species and
 * specimens, and its collections. Reads LocalityDb (table specimen). Bots must use code= ("Locality access by name
 * is restricted. Please use the code."). No parameter: "Enter a locality name or code."; not found: "Locality not
 * found for name:... or code:...". Sets the session attribute map (and removes taxon, so bigMap.do does not reuse
 * it) and the request attribute locality. Docs: docs/flows/places.md, docs/numbers.md row 66. Tests:
 * tests/e2e/test_specimen_pages.py::test_locality_page_lists_the_specimens_collection,
 * test_numbers_search.py::test_locality_collections_equal_the_codes_of_its_specimens.
 */
public final class LocalityAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(LocalityAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

		// Extract attributes we will need
		HttpSession session = request.getSession();
			
        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name"); // Was: Name could be code or name. We try code first.
        String code = (String) df.get("code");

		if (AntwebUtil.isEmpty(name) && AntwebUtil.isEmpty(code)) {
			request.setAttribute("message", "Enter a locality name or code.");
            s_log.warn("Enter a locality name or code.");
			return mapping.findForward("message");
		}

        if (HttpUtil.isBot(request) && code == null) {
			request.setAttribute("message", "Locality access by name is restricted. Please use the code.");
			return mapping.findForward("message");
		}

        // This is weird. Initially name= was used for code. Now we also allow code=.
        // name= can still be used for the code and now also for the name.
        Locality locality = null;

		if (HttpUtil.tooBusyForBots(request)) { return HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

		Date startTime = new Date();
		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("LocalityAction.execute()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
            LocalityDb localityDb = new LocalityDb(connection);
            locality = localityDb.getLocalityByCode(code);    
            //A.log("execute() code:" + code + " locality:" + locality);
            if (locality == null) {
              locality = localityDb.getLocalityByCodeOrName(name);
              s_log.debug("execute() code:" + code + " name:" + name + " locality:" + locality);
            } // else A.log("NOT");
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
            QueryProfiler.profile("locality", startTime);	 		
			DBUtil.close(connection, this, dbMethodName);
		}

        if (locality == null) {        
		  String message = "Locality not found for name:" + Html.esc(name) + " or code:" + Html.esc(code);
		  if (UploadAction.isInUploadProcess()) {
			// An upload is currently in process.  Request that this process be re-attempted shortly.
			message += "  A curator is currently in the process of an Upload.";
		  } else {
			String referer = HttpUtil.getRequestReferer(request);
			if (referer != null && !referer.contains("referer:null")) {
				// The request details go to the log only, not to the visitor (2026-10-26, finding F12).
				String logMessage = "execute() " + message + " (Not upload). requestInfo:" + HttpUtil.getRequestInfo(request)
				  + " isBot:" + HttpUtil.isBot(request);
              if (!HttpUtil.isBot(request)) {
				  s_log.error(logMessage);
			  } else {
				  s_log.info(logMessage);
			  }
			}
		  } 
		  request.setAttribute("message", message);
		  return mapping.findForward("message");
        }

        // We have the locality
		Map map = new Map(locality);
		//s_log.warn("Locality Map: " + map);
		session.removeAttribute("taxon");  // otherwise bigMap.do would use it by default
		session.setAttribute("map", map);
		
		//s_log.warn("execute() set request attribute locality:" + locality);
   	    request.setAttribute("locality", locality);

		// Set a transactional control token to prevent double posting
		saveToken(request);

		return mapping.findForward("success");
	}
	
}

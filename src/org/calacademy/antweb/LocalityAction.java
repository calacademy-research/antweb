package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;
import java.sql.*;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class LocalityAction extends Action {

    private static Log s_log = LogFactory.getLog(LocalityAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward d = Check.valid(request, mapping); if (d != null) return d;

		// Extract attributes we will need
		HttpSession session = request.getSession();
			
        DynaActionForm df = (DynaActionForm) form;
        String name = (String) df.get("name"); // Name could be code or name. We try code first.
        String code = (String) df.get("code");                

        // This is weird. Initially name= was used for code. Now we also allow code=.
        // name= can still be used for the code and now also for the name.
        Locality locality = null;
        
		java.util.Date startTime = new java.util.Date();
		java.sql.Connection connection = null;		
		try {
			javax.sql.DataSource dataSource = getDataSource(request, "conPool");
			
            if (HttpUtil.tooBusyForBots(dataSource, request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }
			
            connection = DBUtil.getConnection(dataSource, "LocalityAction.execute()");
            LocalityDb localityDb = new LocalityDb(connection);
            locality = localityDb.getLocalityByCode(code);    
            //A.log("execute() code:" + code + " locality:" + locality);
            if (locality == null) {
              locality = localityDb.getLocalityByCodeOrName(name);
              A.log("execute() code:" + code + " name:" + name + " locality:" + locality);
            } // else A.log("NOT");
		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {
            QueryProfiler.profile("locality", startTime);	 		
			DBUtil.close(connection, this, "LocalityAction.execute()");
		}
		        
/*        
        if (code != null && !"".equals(code)) {
          A.slog("-1:" + code);
  		  locality = getLocalityWithCode(code, request);
        } else {
		  A.slog("0");
		  locality = getLocalityWithCode(name, request);
		  A.slog("1:" + locality);
		  if (locality == null) locality = getLocalityWithName(name, request);
        }
*/        
        if (locality == null) {        
		  String message = "  Locality not found for name:" + name + " or code:" + code;
		  if (org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) {
			// An upload is currently in process.  Request that this process be re-attempted shortly.
			message += "  A curator is currently in the process of an Upload.";
		  } else {
			String referer = HttpUtil.getRequestReferer(request);
			if (referer != null && !referer.contains("referer:null")) {
			  s_log.error("execute() " + message + " (Not upload). requestInfo:" + referer);
			}
		  } 
		  request.setAttribute("message", message);
		  return (mapping.findForward("message"));
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

		return (mapping.findForward("success"));
	}
	
}

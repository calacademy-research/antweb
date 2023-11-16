package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;
import java.sql.*;
import java.util.Date;

import org.calacademy.antweb.upload.UploadAction;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class LocalityAction extends Action {

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

		if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

		Date startTime = new Date();
		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("LocalityAction.execute()");
		try {
			DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
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
		  String message = "  Locality not found for name:" + name + " or code:" + code;
		  if (UploadAction.isInUploadProcess()) {
			// An upload is currently in process.  Request that this process be re-attempted shortly.
			message += "  A curator is currently in the process of an Upload.";
		  } else {
			String referer = HttpUtil.getRequestReferer(request);
			if (referer != null && !referer.contains("referer:null")) {
				message = message + "execute() " + message + " (Not upload). requestInfo:" + HttpUtil.getRequestInfo(request);
				message += " isBot:" + HttpUtil.isBot(request);
              if (!HttpUtil.isBot(request)) {
				  s_log.error(message);
			  } else {
				  s_log.info(message);
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

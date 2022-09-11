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
import org.calacademy.antweb.upload.UploadUtil;
import org.calacademy.antweb.home.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
    
public final class CollectionAction extends Action {

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

        if (HttpUtil.tooBusyForBots(request)) { HttpUtil.sendMessage(request, mapping, "Too busy for bots."); }

		Collection collection = null;
		Connection connection = null;
        Date startTime = new Date();

		try {
	 		DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "CollectionAction.execute()", HttpUtil.getTarget(request));	
            AntwebMgr.populate(connection);            

            collection = new CollectionDb(connection).getCollection(name);

			s_log.debug("collection:" + collection + " size:" + collection.getSpecimenResults().getResults().size());

			//if (!success) {
			if (collection == null || 0 == collection.getSpecimenResults().getResults().size()) {
			  message = "Collection not found - name:" + name + ".";

			  String cleanCode = UploadUtil.cleanCode(name);
			  //A.log("execute() cleanCode:" + cleanCode);
			  if (!name.equals(cleanCode)) {
                // Frequently will fail. cleanCode is often a "tc" + a random number. use UploadUtil.genKey()?
				message = "Unsatisfactory collection code name. Perhaps looking for <a href='" + AntwebProps.getDomainApp() + "/collection.do?name=" + cleanCode + "'>" + cleanCode + "</a>?";
				request.setAttribute("message", message);
				return mapping.findForward("message");
			  }
        			  
              if (UploadAction.isInUploadProcess()) {
                        // An upload is currently in process.  Request that this process be re-attempted shortly.
                message += "  A curator is currently in the process of an Upload.";
			  }	else {
			    message += "  No upload in process.  RequestInfo:" + AntwebUtil.getRequestInfo(request);
                s_log.debug("execute() " + message);
                LogMgr.appendLog("badRequest.log", message);
			  }
              request.setAttribute("message", message);
              return mapping.findForward("message");
			}			
			
            session.setAttribute("advancedSearchResults", collection);
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
		} finally {
		    // java.util.Date startTime = new java.util.Date();
            QueryProfiler.profile("collection", startTime);	
            DBUtil.close(connection, this, "CollectionAction.execute()");
		}

        //mapping.getScope() is "session"
		session.setAttribute("collection", collection);

		// Set a transactional control token to prevent double posting
		saveToken(request);

        return mapping.findForward("success");
	}
}

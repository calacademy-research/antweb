package org.calacademy.antweb;

import java.io.*;

import java.net.URL;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import java.sql.*;
import java.util.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class EOLAction extends Action {

    private static Log s_log = LogFactory.getLog(EOLAction.class);
    
    private static final Log s_antwebEventLog = LogFactory.getLog("antwebEventLog");

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		HttpSession session = request.getSession();
		
		java.util.Date startTime = new java.util.Date(); // for AntwebUtil.finish(request, startTime);
		
		String results = null;
		java.sql.Connection connection = null;
		try {
			javax.sql.DataSource dataSource = getDataSource(request, "conPool");

            if (DBUtil.isServerBusy(dataSource, request)) {
              return mapping.findForward("message");            
            }			
			
			connection = DBUtil.getConnection(dataSource, "EOLAction.execute()");

            s_antwebEventLog.info("EOL Start");

			EOL eol = new EOL();
			results = eol.generateXml(connection);

		} catch (SQLException e) {
			s_log.error("execute() e: " + e);
			org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
		} finally {
		    // java.util.Date startTime = new java.util.Date();
            QueryProfiler.profile("eol", startTime);	 		
            DBUtil.close(connection, this, "EOLAction.execute()");
		}

        request.setAttribute("results", results);

		// Set a transactional control token to prevent double posting
		saveToken(request);

        AntwebUtil.finish(request, startTime);

		// Forward control results page
		if (results == null) {
			return (mapping.findForward("failure"));
		} else {
			return (mapping.findForward("success"));
		}
	}

}

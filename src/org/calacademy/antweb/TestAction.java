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

public final class TestAction extends Action {

    private static Log s_log = LogFactory.getLog(TestAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		HttpSession session = request.getSession();
			
        if (HttpUtil.getTarget(request).contains("mobile")) {
          return mapping.findForward("mobile");
        }			
			
		java.sql.Connection connection = null;		
		try {
			javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "TestAction.execute()");

            UploadDb uploadDb = new UploadDb(connection);
            uploadDb.updateCounts(21);

		} catch (SQLException e) {
			s_log.error("execute() e:" + e);
		} finally {	 		
			DBUtil.close(connection, this, "TestAction.execute()");
		}
		        
        if (true) {        
		  String message = "Tested.";
		  request.setAttribute("message", message);
		  return (mapping.findForward("message"));
        }

		return (mapping.findForward("success"));
	}
	
}

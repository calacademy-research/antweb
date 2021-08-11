package org.calacademy.antweb.curate.login;

import javax.servlet.http.*;
import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class ManageLoginsAction extends Action {

    private static Log s_log = LogFactory.getLog(ManageLoginsAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response) {

        ActionForward a = Check.init("geolocale", request, mapping); if (a != null) return a;

		ArrayList loginList = new ArrayList();

        java.sql.Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ManageLoginsAction.execute()");

            loginList = LoginMgr.getLogins(); //(new LoginDb(connection)).getAllLogins();
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);            
        } finally {
            DBUtil.close(connection, this, "ManageLoginsAction.execute()");
        }            

        if (loginList != null) {
          request.getSession().setAttribute("antwebLogins", loginList);      
          return (mapping.findForward("success"));
        } else {
          return (mapping.findForward("error"));
        }		
		
	}
}

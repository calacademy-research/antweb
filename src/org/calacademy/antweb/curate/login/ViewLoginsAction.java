package org.calacademy.antweb.curate.login;

import org.calacademy.antweb.AntwebAction;

import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.*;

/**
 * This class takes the UploadForm and retrieves the text value
 * and file attributes and puts them in the request for the display.jsp
 * page to display them
 *
 */

public class ViewLoginsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ViewLoginsAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response) {
        // Administrators only (the list shows every login).
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;

        ActionForward a = Check.init("geolocale", request, mapping); if (a != null) return a;

		ArrayList<Login> loginList = LoginMgr.getLogins();   // the in-memory list; no database query here

        if (loginList != null) {
          // For this page only: a request attribute, not the session (the list holds every login).
          request.getSession().removeAttribute("antwebLogins");
          request.setAttribute("antwebLogins", loginList);      
          return mapping.findForward("success");
        } else {
          return mapping.findForward("error");
        }		
		
	}
}

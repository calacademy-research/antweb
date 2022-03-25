package org.calacademy.antweb;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class OrphanedImagesAction extends Action {

    private static final Log s_log = LogFactory.getLog(OrphanedImagesAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		boolean success = false;

		// Extract attributes we will need
		HttpSession session = request.getSession();

		OrphanedImages orphans = new OrphanedImages();
		ArrayList searchResults = null;
		
		Connection connection = null;
		try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "OrphanedImagesAction");

			orphans.setConnection(connection);
			orphans.setOrphans();
			searchResults = orphans.getOrphans();
			success = true;
			
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "OrphanedImagesAction");
        }

        if ("request".equals(mapping.getScope())) {
            request.setAttribute("orphanedImages", searchResults);
        } else {
            session.setAttribute("orphanedImages", searchResults);
        }

		// Set a transactional control token to prevent double posting

		saveToken(request);

		// Forward control to the edit user registration page

		if (success) {
			return mapping.findForward("success");
		} else {
			return mapping.findForward("failure");
		}

	}

}

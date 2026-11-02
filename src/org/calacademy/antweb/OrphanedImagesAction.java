package org.calacademy.antweb;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.ArrayList;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

/**
 * /orphanedImages.do, admins only (Check.admin): the Orphaned Images page (orphanedImages.jsp), linked from the
 * curator links (curate/curatorLinks.jsp). Lists the specimen codes of images whose specimen record does not exist,
 * and their number of images (OrphanedImages). Reads tables image and specimen, writes nothing. Docs:
 * docs/guides/curate-page.md, docs/numbers.md row 99. Tests: tests/e2e/test_count_check.py.
 */
public final class OrphanedImagesAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(OrphanedImagesAction.class);

	public ActionForward execute(ActionMapping mapping, ActionForm form,
			HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		// Administrators only: a report of images whose specimen record no longer exists.
		ActionForward c = Check.admin(request, mapping); if (c != null) return c;

		boolean success = false;

		// Extract attributes we will need
		HttpSession session = request.getSession();

		OrphanedImages orphans = new OrphanedImages();
		ArrayList<String> searchResults = null;
		
		Connection connection = null;
		String dbMethodName = DBUtil.getDbMethodName("OrphanedImagesAction.execute()");
		try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

			orphans.setConnection(connection);
			orphans.setOrphans();
			searchResults = orphans.getOrphans();
			success = true;
			
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, dbMethodName);
        }

        if ("request".equals(mapping.getScope())) {
            request.setAttribute("orphanedImages", searchResults);
        } else {
            session.setAttribute("orphanedImages", searchResults);
        }
        // Q9 (2026-10-22): the page shows the images as well as their specimen codes.
        request.setAttribute("orphanedImageCount", orphans.getImageCount());

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

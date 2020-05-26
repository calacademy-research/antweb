package org.calacademy.antweb.curate.ancFile;

import java.io.IOException;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public final class AncEditAction extends Action {

    private static Log s_log = LogFactory.getLog(AncEditAction.class);

	public ActionForward execute( ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;  

		HttpSession session = request.getSession();
		AncFile inSessionPage = (AncFile) session.getAttribute("ancFile");
		
		int id = Integer.valueOf(request.getParameter("id")).intValue();
		
		if ((inSessionPage != null) && (inSessionPage.getId() != id)) inSessionPage = null;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);
		
		AncFile ancFile = new AncFile();
		Connection connection = null;
		String returnValue = "success";
		try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "AncEditAction.execute()");

			s_log.info("getting from db, ancillary file=" + id);
			ancFile.getFromDb(connection, id);
			if (inSessionPage != null) {
			
			    s_log.info("inSessionAncFile:" + inSessionPage.toString());
				if (!ancFile.getTitle().equals(inSessionPage.getTitle())) {
					ancFile.setTitle(inSessionPage.getTitle());
				}
				s_log.info("got from db, ancillary page " + ancFile.getId() + " with title " + ancFile.getTitle());
				
				String contents = inSessionPage.getContents();
				
				if (HttpUtil.abortAction(contents)) {
                  AntwebUtil.eventLog("Invalid Ancillary File content.  Edit Page " + ancFile.getId() + " with title " + ancFile.getTitle());
				  return (mapping.findForward("error"));
				}
				
				if (!ancFile.getContents().equals(contents)) {
					ancFile.setContents(inSessionPage.getContents());
				}
			}
			ArrayList<String> projects = accessLogin.getProjectNames();
			String projectName = ancFile.getProject();
			s_log.info("project name is " + projectName);
			if (! (
			     (projects.contains(ancFile.getProject()))
			  || (accessLogin.isAdmin()) 
			  || (accessGroup.getId() == 19)                    // Bonnie Blaimer is 19.  Exception made to expedite her work.
			  || ("Antweb Curators".equals(ancFile.getTitle())) // any curator can edit...
			) ) 
            {
				returnValue = "permissionDenied";
				session.removeAttribute("ancillary");
			} else {
				session.setAttribute("ancFile", ancFile);
				Utility util = new Utility();
				String oldFile = ancFile.getDirLoc() + "/" + ancFile.getFileName() + ".jsp";
				String newFile = ancFile.getDirLoc() + "/" + ancFile.getFileName() + "-preview.jsp";
				if ((new File(oldFile)).exists() && (!(new File(newFile)).exists())) {
				  A.log("execute() copy oldFile:" + oldFile + " to newFile:" + newFile);
					util.copyFile(oldFile, newFile);
				}
			}          

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "AncEditAction.execute()");
        }
		
		return (mapping.findForward(returnValue));
	}
}

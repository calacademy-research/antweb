package org.calacademy.antweb.curate.project;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;


import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;


public final class EditProjectAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(EditProjectAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.curator(request, mapping); if (c != null) return c;   
        // Project settings (title, live, map, coordinates; create and delete) are for administrators:
        // the settings page and the Project Manager are admin pages. Curators edit a project's species
        // list in the Species List Tool and its page text on the project page.
        if (!LoginMgr.isAdmin(request)) {
          request.setAttribute("message", "Only an AntWeb administrator may change project settings.");
          return mapping.findForward("message");
        }
        ActionForward f = Check.upload(request, mapping); if (f != null) return f;

        HttpSession session = request.getSession();
        
        EditProjectForm editProjectForm = (EditProjectForm) form;
        String projectName = editProjectForm.getProjectName();
        projectName = ProjectMgr.getProjectName(projectName);
        if (projectName == null) projectName = editProjectForm.getProjectName();
        
        ActionForward forward = mapping.findForward("error");
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "EditProjectAction.execute()");

            if ("delete".equals(editProjectForm.getAction()) && !LoginMgr.isAdmin(request)) {
              request.setAttribute("message", "Only an AntWeb administrator may delete a project.");
              return mapping.findForward("message");
            }
            if ("delete".equals(editProjectForm.getAction()) && !HttpUtil.isPost(request)) {
              // Deleting changes data: POST only, so the CSRF filter checks the form token.
              request.setAttribute("message", "Use the Delete button on the project edit page.");
              return mapping.findForward("message");
            }
            // A curator may edit only a project assigned to them; administrators any project.
            if (!SpeciesListAccess.canEdit(LoginMgr.getAccessLogin(request), projectName)) {
              request.setAttribute("message", SpeciesListAccess.NOT_ASSIGNED);
              return mapping.findForward("message");
            }
            if ("delete".equals(editProjectForm.getAction())) {
              ProjectDb projectDb = new ProjectDb(connection);
              projectDb.deleteSpeciesList(projectName);

              LoginDb loginDb = new LoginDb(connection);
              loginDb.refreshLogin(session);
                    
              String message = "Project deleted:" + Html.esc(projectName) + ".";
              s_log.warn("execute() " + message);

			  ProjectMgr.populate(connection, true);
			              
              request.setAttribute("message", message);
              return mapping.findForward("message");
            }

            if (HttpUtil.isPost(request)) {
              if (new ProjectDb(connection).getProject(projectName) == null && !Project.isProjectName(projectName)) {
                request.setAttribute("message", "Nothing was saved: a project key is one lowercase word ending with \"ants\".");
                return mapping.findForward("message");
              }
              String message = handlePost(mapping, form, request, connection);
              if (message != null) {
                request.setAttribute("message", message);
                forward = mapping.findForward("message");
                return forward;
              }

              // This unfortunately results in a page always not logged in.
              // We prefer to redirect.
              String displayKey = ProjectMgr.getDisplayKey(projectName);
              if (displayKey == null || "null".equals(displayKey)) displayKey = projectName;
              
              s_log.debug("execute() displayKey:" + displayKey + " projectName:" + projectName);
              
              String url = AntwebProps.getDomainApp() + "/project.do?name=" + displayKey;
              s_log.debug("execute() url:" + url);

			  ProjectMgr.populate(connection, true);
			
              HttpUtil.sendRedirect(url, request, response);
              return null;                      
            }
            // Get the project and return it to the projectEdit.jsp
            Project thisProject = new ProjectDb(connection).getProject(projectName);
            if (thisProject == null) {
              if (!Project.isProjectName(projectName)) {
                request.setAttribute("message", "No project " + Html.esc(projectName) + ". A new project's key is one"
                    + " lowercase word without spaces, ending with \"ants\" (for example malagasyants).");
                return mapping.findForward("message");
              }
              thisProject = editProjectForm.freshProject();
              request.setAttribute("isNewProject", Boolean.TRUE);
            }

            s_log.debug("execute() Putting in request thisProject:" + thisProject);

            request.setAttribute("thisProject", thisProject);
            forward = mapping.findForward("success");

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "EditProjectAction.execute()");
        }
        return forward;                     
    }

    private String handlePost(ActionMapping mapping, ActionForm form, HttpServletRequest request
    , Connection connection) throws SQLException {
    
        EditProjectForm editProjectForm = (EditProjectForm) form;
            
        if (editProjectForm.getExtent() != null
          && !"".equals(editProjectForm.getExtent())
          && editProjectForm.getExtent().contains(",")
          ) {
            return "Nothing was saved: the Geographic Extent must not contain commas (separate the four numbers with spaces).";
        }
        // (The former "Locality (Mapping Range Criteria)" field was never stored, so it was removed.)

        Project project = editProjectForm.getProject();
        // The settings form does not carry the project's source: keep the stored one (the update used to blank it).
        Project stored = new ProjectDb(connection).getProject(project.getName());
        if (stored != null && project.getSource() == null) project.setSource(stored.getSource());

        s_log.debug("handlePost() form.isLive:" + editProjectForm.getIsLive() + " Project.isLive:" + project.getIsLive());

        saveProject(project, connection, request);
    
        return null; // success
    }

     
    public static void saveProject(Project project, Connection connection, HttpServletRequest request) 
    /** Saves the project's settings (editProject.do). The page text (contents, author, author bio,
      * page images) is not saved here: it is edited on the project page (DescriptionAction).
      */
      throws SQLException {
        HttpSession session = request.getSession();

        ProjectDb projectDb = new ProjectDb(connection);

        try {
            projectDb.save(project);        
        } catch (SQLIntegrityConstraintViolationException e) {
            if (AntwebProps.isDevMode()) s_log.error("no worries on save.");
        }
                
        projectDb.update(project);

        // if this is the antweb administrator, update the project resource file
        AntwebMgr.populate(connection, true);

        // Update the project lists...
        LoginDb loginDb = new LoginDb(connection);
        loginDb.refreshLogin(request.getSession());

    }
    
}

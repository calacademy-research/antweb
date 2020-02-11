package org.calacademy.antweb.curate.project;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;


import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.upload.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;


public final class EditProjectAction extends Action {

    private static Log s_log = LogFactory.getLog(EditProjectAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward c = Check.login(request, mapping); if (c != null) return c;   
        ActionForward f = Check.upload(request, mapping); if (f != null) return f;

        HttpSession session = request.getSession();
        
        EditProjectForm editProjectForm = (EditProjectForm) form;
        String projectName = editProjectForm.getProjectName();
        projectName = ProjectMgr.getProjectName(projectName);
        if (projectName == null) projectName = editProjectForm.getProjectName();
        
        ActionForward forward = (mapping.findForward("error"));
        Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "EditProjectAction.execute()");

            if ("delete".equals(editProjectForm.getAction())) {
              ProjectDb projectDb = new ProjectDb(connection);
              projectDb.deleteSpeciesList(projectName);
        
              (new LoginDb(connection)).refreshLogin(session);
                    
              String message = "Project deleted:" + projectName + ".";
              s_log.warn("execute() " + message);

			  ProjectMgr.populate(connection, true);
			              
              request.setAttribute("message", message);
              return mapping.findForward("message");
            }

            if (HttpUtil.isPost(request)) {
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
              
              A.log("execute() displayKey:" + displayKey + " projectName:" + projectName);
              
              String url = AntwebProps.getDomainApp() + "/project.do?name=" + displayKey;
              A.log("execute() url:" + url);

			  ProjectMgr.populate(connection, true);
			
              HttpUtil.sendRedirect(url, request, response);
              return null;                      
            }
            // Get the project and return it to the projectEdit.jsp
            Project thisProject = (new ProjectDb(connection)).getProject(projectName);
            if (thisProject == null) {
              thisProject = editProjectForm.freshProject();
            }

            A.log("execute() Putting in request thisProject:" + thisProject);

            request.setAttribute("thisProject", thisProject);
            forward = mapping.findForward("success");

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "EditProjectAction.execute()");
        }
        return forward;                     
    }

    private String handlePost(ActionMapping mapping, ActionForm form, HttpServletRequest request
    , Connection connection) throws SQLException {
    
        EditProjectForm editProjectForm = (EditProjectForm) form;
            
        if ((editProjectForm.getExtent() != null) 
          && (!"".equals(editProjectForm.getExtent())) 
          && (editProjectForm.getExtent().contains(","))
          ) {
            ActionMessages messages = new ActionMessages();
            ActionMessage msg = new ActionMessage("error.extent.commas");
            return msg.toString();
        }
        
        String locality = editProjectForm.getLocality();
        boolean isAllAntwebAnts = false;
        if (editProjectForm.getProjectName() != null && editProjectForm.getProjectName().equals("allantwebants")) isAllAntwebAnts = true;
        
        if ( (!isAllAntwebAnts) && (locality != null) && (!"".equals(locality)) && (!( 
                 locality.contains("bioregion") 
              || locality.contains("country")
              || locality.contains("adm1")
              ))) {
            String message = "Mapping Range Criteria must be bioregion, country or adm1.";
            return message;
        }        
    
        Project project = editProjectForm.getProject();

        A.log("handlePost() form.isLive:" + editProjectForm.getIsLive() + " Project.isLive:" + project.getIsLive());

        saveProject(project, connection, request);
    
        return null; // success
    }

     
    public static void saveProject(Project project, Connection connection, HttpServletRequest request) 
    /** Notice that this method is static.  It is called not only from the saveProject.do struts action
      * but also from the EditProjectAction.generateAllHomePages() method, invoked from the editProject.do
      */
      throws SQLException {
        HttpSession session = request.getSession();

        ProjectDb projectDb = new ProjectDb(connection);

        try {
            projectDb.save(project);        
        } catch (com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException e) {
            if (AntwebProps.isDevMode()) s_log.error("no worries on save.");
        }
                
        projectDb.update(project);

        // if this is the antweb administrator, update the project resource file
        AntwebMgr.populate(connection, true);

        // Update the project lists...
        (new LoginDb(connection)).refreshLogin(request.getSession());            

    }
    
}

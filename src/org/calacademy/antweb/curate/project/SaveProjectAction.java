package org.calacademy.antweb.curate.project;

import java.io.*;

import java.sql.*;

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
import org.calacademy.antweb.home.*;

public final class SaveProjectAction extends Action {

    private static Log s_log = LogFactory.getLog(SaveProjectAction.class);

    public ActionForward execute(
        ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();

        EditProjectForm editProjectForm = (EditProjectForm) form;
//        A.log("execute() editProjectForm bioR:" + editProjectForm.getBiogeographicRegion() + " root:" + editProjectForm.getRoot() 
//          + " formSpeciesListMappable:" + editProjectForm.getSpeciesListMappable());        

        String query;
        Project thisProject = editProjectForm.getProject();

        if (AntwebProps.isDevMode()) AntwebUtil.log("SaveProjectAction.java geolocaleId:" + editProjectForm.getGeolocaleId());

        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveProjectAction");

            connection.setAutoCommit(true);
        
            SaveProjectAction.saveProject(thisProject, connection, request);
            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "SaveProjectAction");
        }
          
        return mapping.findForward("success");
    }
    
        
    public static void saveProject(Project project, Connection connection, HttpServletRequest request) 
    /** Notice that this method is static.  It is called not only from the saveProject.do struts action
      * but also from the EditProjectAction.generateAllHomePages() method, invoked from the editProject.do
      */
      throws IOException, SQLException {
        HttpSession session = request.getSession();

        ProjectDb projectDb = new ProjectDb(connection);

        try {
            projectDb.save(project);        
        } catch (SQLIntegrityConstraintViolationException e) {
            s_log.error("no worries on save.");
        }
                
        projectDb.update(project);

        // Update the project lists...
        new LoginDb(connection).refreshLogin(request.getSession());
        
        //String docBase = util.getDocRoot();
        
      /* This may be deprecated when the files below are in use */
      // Should not have been written here in the first place.  Get the stub initially written to the proper place
      // Then we don't need to copy

       s_log.info("saveProject() project:" + project.getName() + " root:" + project.getRoot());

      if (!(project.getRoot() == null || project.getRoot().equals(""))) {
        String preview = Project.getSpeciesListPath() + project.getRoot() + "/" + project.getRoot() + "-preview.jsp";
        String genFile = Project.getSpeciesListPath() + project.getRoot() + "/" + project.getRoot() + "-body.jsp";
//        String preview = docBase + project.getRoot() + "-preview.jsp";
//        String genFile = docBase + project.getRoot() + "-body.jsp";
       s_log.info("saveProject() Preview:" + preview + " genFile:" + genFile);

        try {
          Utility util = new Utility();
          util.copyFile(preview, genFile);
        
          /* We have stored all of the project files in the root webapp dir.  Beginning move of the
             to the webapps/antweb/projects/ directory. */
          //String webProjectsDir = docBase + "web/speciesList/";
          //(new Utility()).makeDirTree(webProjectsDir);
        
          // copy the body, preview and stub
          //util.copyFile(docBase + project.getRoot() + "-body.jsp", webProjectsDir + project.getRoot() + "-body.jsp");
          //util.copyFile(docBase + project.getRoot() + "-preview.jsp", webProjectsDir + project.getRoot() + "-preview.jsp");
          //util.copyFile(docBase + project.getRoot() + ".jsp", webProjectsDir + project.getRoot() + ".jsp");
        } catch (FileNotFoundException e) {
          s_log.warn("saveProject() project:" + project + " file not found.  preview:" + preview + "  genFile:" + genFile);
        }

      } else {
        s_log.error("saveProject() aborted.  getRoot is " + project.getRoot());
      }
    }
    
}

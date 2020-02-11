package org.calacademy.antweb.curate;


import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.curate.project.*;

public final class NewBioRegionAction extends Action {

    private static Log s_log = LogFactory.getLog(NewBioRegionAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();
        java.sql.Connection connection = null;
        String query = null;
        String title = null;
        String name = null;
        String root = null;

        EditProjectForm theForm = (EditProjectForm) form;
        if (theForm.getTitle() != null) title = theForm.getTitle();
        root = Project.getRootName(title); 
        
        Project project = new Project();
        project.setTitle(title);
        String bioregion = root + "ants";
        project.setName(bioregion);
        project.setScope(null);
        
        //s_log.warn("execute() root:" + root + " title:" + title + " );
        
        if ((title != null) && (title.length() > 0) && (root != null) && (root.length() > 0)) {
        
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "NewBioRegionAction");
          
                connection.setAutoCommit(true);

                (new ProjectDb(connection)).save(project);
                  
                String dirName = AntwebProps.getDocRoot() + "/" + Project.getSpeciesListDir() + root;
                s_log.warn("execute() creating:" + dirName);
                (new Utility()).makeDirTree(dirName);
                                
                //boolean success = createDirectory(theForm.getRoot());
                //if (! success) return (mapping.findForward("failure"));
                
                A.log("execute() bioregion:" + bioregion);
                request.setAttribute("project", bioregion);
                
            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                return (mapping.findForward("error"));
            } finally { 		
                DBUtil.close(connection, this, "NewBioRegionAction");
            }

            return (mapping.findForward("success")); 
        } else {
            return (mapping.findForward("failure"));
        }
    }
    
    private boolean createDirectory(String projectRoot) {
//        Utility util = new Utility();
//        String docRoot = util.getDocRoot();
        String directoryName = AntwebProps.getDocRoot() + "/" + projectRoot;

/*
        // Create a directory; all ancestor directories must exist
        try {
            boolean success = (new File(directoryName)).mkdir();
            if (!success) {
                s_log.error("createDirectory() problem creating:" + directoryName);
            } else {
                return true;
            }
        } catch (Exception e1) {
             s_log.error("createDirectory() dir:" + directoryName + " e:" + e1);
        }
*/
        return false;
    }

}

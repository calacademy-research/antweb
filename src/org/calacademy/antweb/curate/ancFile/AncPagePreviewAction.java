package org.calacademy.antweb.curate.ancFile;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
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

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class AncPagePreviewAction extends Action {

    private static Log s_log = LogFactory.getLog(AncPagePreviewAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();
        AncFile sessionAncFile = (AncFile) session.getAttribute("ancFile");
        
        if (sessionAncFile == null) {
          request.setAttribute("message", "Session expired."); 
          return mapping.findForward("message");         
        }
        
        s_log.info("sessionAncFile:" + sessionAncFile);
        
        AncForm theForm = (AncForm) form;
        AncFile ancFile = new AncFile();
        ancFile.setId(sessionAncFile.getId());
        ancFile.setTitle(theForm.getTitle());
        
        ancFile.setLastChanged(sessionAncFile.getLastChanged());
        
        String contents = theForm.getContents();
		if (HttpUtil.abortAction(contents)) {
          AntwebUtil.eventLog("Invalid Ancillary File content.  Page " + ancFile.getId() + " with title " + ancFile.getTitle());
		  return (mapping.findForward("error"));
		}        
        
        ancFile.setContents(contents);
        ancFile.setProject(sessionAncFile.getProject());
        if (theForm.getFileName() != null) {
            s_log.info("getting filename *" + theForm.getFileName() + "* from the form");
            ancFile.setFileName(theForm.getFileName());
        } else {
            ancFile.setFileName(sessionAncFile.getFileName());
        }
        ancFile.setDirectory(sessionAncFile.getDirectory());
        ancFile.setAccessLoginId(sessionAncFile.getAccessLoginId());
        s_log.info("setting loginId:" + sessionAncFile.getAccessLoginId() + " directory:" + sessionAncFile.getDirectory());
        Connection connection = null;
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "AncPagePreviewAction");
    
            connection.setAutoCommit(true);
            ancFile.setConnection(connection);
            ancFile.generatePage();
            s_log.info("anc page generated");
            ancFile.setConnection(null);
          
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "AncPagePreviewAction");
        }
        
        session.setAttribute("ancFile", ancFile);

        return (mapping.findForward("success"));
    }

}

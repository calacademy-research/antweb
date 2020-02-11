package org.calacademy.antweb.curate.ancFile;

import java.io.IOException;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;

import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class AncNewPageAction extends Action {

    private static Log s_log = LogFactory.getLog(AncNewPageAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        A.log("In AncNewPageAction");        
        
        // Extract attributes we will need
        HttpSession session = request.getSession();
        String successString = "failure";

        ActionForward c = Check.login(request, mapping); if (c != null) return c;
        Login accessLogin = LoginMgr.getAccessLogin(request);
		
        AncFile ancFile = (AncFile) session.getAttribute("ancFile");
        String query;
        AncForm theForm = (AncForm) form;
        ancFile.setAccessLoginId(accessLogin.getId());
        ancFile.setFileName(theForm.getFileName());
        ancFile.setTitle(theForm.getTitle());
        //s_log.info("anc new page sets title:" + theForm.getTitle());
        
        String contents = theForm.getContents();
		if (HttpUtil.abortAction(contents)) {
          AntwebUtil.eventLog("Invalid Ancillary File content.  New Page " + ancFile.getId() + " with title " + ancFile.getTitle());
		  return (mapping.findForward("error"));
		}
				        
        ancFile.setContents(contents);
        s_log.info("anc new page sets contents:" + theForm.getContents());
        ActionMessages messages = new ActionMessages();
        ActionMessage msg = null;
        
        Utility util = new Utility();

        s_log.info("ancFileDir:" + ancFile.getDirLoc() + " fileName:" + theForm.getFileName());
        
        if (!(util.directoryExists(ancFile.getDirLoc()))) {
            msg = new ActionMessage("error.ancPage.badDirectory");
            s_log.error("execute() dir not found:" + ancFile.getDirLoc());
            messages.add("message", msg);
        } else if (util.badFileName(ancFile.getFileName())) {
            msg = new ActionMessage("error.ancPage.badFileName");
            messages.add("message", msg);
            s_log.info("XXX:" + msg.getKey());
        } else if (new File(ancFile.getDirLoc() + "/" + ancFile.getFileName() + ".jsp").exists())  {            
            msg = new ActionMessage("error.ancPage.fileExists");
            messages.add("message", msg);
        } else {

            // TODO: make sure this file name doesn't exist in this directory
    
            Connection connection = null;
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "AncNewPageAction.execute()");
         
                connection.setAutoCommit(true);
                
                // save everything to the db
                int id = ancFile.saveToDb(connection);
                if (id == 0) {
                  s_log.error("execute() id is 0 indicating a trapped exception in AncFile.saveToDb(c) for " + theForm.getFileName());
                  return (mapping.findForward(successString));
                }

                // create the physical preview file
                ancFile.generatePage(connection, id);
                        
                successString = "success";
                
                s_log.info("AncFile save success");

            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                return (mapping.findForward("error"));
            } finally { 		
                DBUtil.close(connection, this, "AncNewPageAction.execute()");
            }
        }
        
        if (!messages.isEmpty()) {
            saveMessages(request, messages);
            s_log.info("Save messages" + msg.toString());
        } else {
            s_log.info("putting title: " + ancFile.getTitle());
            s_log.info("putting contents: " + ancFile.getContents());
            //if ("request".equals(mapping.getScope())) {
            //    request.setAttribute("ancFile", ancFile);
            //} else {
                session.setAttribute("ancFile", ancFile);
            //}
            theForm.reset(mapping, request);
        }
        
        s_log.info("Returning:" + successString);        
        return (mapping.findForward(successString));
    }

}

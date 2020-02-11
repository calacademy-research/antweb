package org.calacademy.antweb.curate.ancFile;


import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.*;

import org.calacademy.antweb.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public final class AncSaveAction extends Action {

    private static Log s_log = LogFactory.getLog(AncSaveAction.class);

    public ActionForward execute( ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();
        AncFile ancFile = (AncFile) session.getAttribute("ancFile");
        if (ancFile == null) return (mapping.findForward("failure"));
        
        AncForm theForm = (AncForm) form;        
        
        s_log.info("from the ancfile title: " + ancFile.getTitle() + " accessLoginId:" + ancFile.getAccessLoginId());

        java.sql.Connection connection = null;    
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "AncSaveAction");

            connection.setAutoCommit(true);

            // save anc page
            ancFile.save(connection);

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "AncSaveAction");
        }
        
        if ((ancFile.getDirLoc() != null) && (ancFile.getDirLoc().length() > 0) && (ancFile.getFileName() != null) && (ancFile.getFileName().length() > 0)) {
            return (mapping.findForward("success"));
        } else {
            return (mapping.findForward("failure"));
        }
        
    }
}

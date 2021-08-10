package org.calacademy.antweb.curate;

import java.io.IOException;
import java.lang.reflect.Field;

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

public final class SaveHomePageAction extends Action {

    private static Log s_log = LogFactory.getLog(SaveHomePageAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();
        java.sql.Connection connection = null;
        Statement stmt = null;
        
        HomePageForm theForm = (HomePageForm) form;
        
        try {
            javax.sql.DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveHomePageAction");

            connection.setAutoCommit(true);
            stmt = connection.createStatement();

            String update = null;
            try {
                java.lang.Class formClass = java.lang.Class.forName("org.calacademy.antweb.curate.HomePageForm");
                Field[] fields = formClass.getDeclaredFields();
                String fieldName;
                String fieldValue;
                Formatter format = new Formatter();
                for (Field field : fields) {
                    fieldName = field.getName();
                    fieldValue = field.toString();
                    update = "update homepage set content='" + AntFormatter.escapeQuotes((String) field.get(theForm)) + "' where content_type='" + fieldName + "'";
                    //s_log.info("execute update:" + update);
                    stmt.executeUpdate(update);
                }
            } catch (IllegalAccessException e) {
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } catch (SecurityException e) {
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            } catch (ClassNotFoundException e) {
                org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);
            }
            
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, stmt, this, "SaveHomePageAction");
        }
        
        Utility util = new Utility();
        String docRoot = util.getDocRoot();
        String previewBody = docRoot + "homePagePreview-body.jsp";
        String indexBody = docRoot + "web/homepage/index-body.jsp";
        s_log.info("execute() copy previewBody:" + previewBody + " to " + indexBody);
        util.copyFile(previewBody, indexBody);
        
        return (mapping.findForward("success"));
    }
}

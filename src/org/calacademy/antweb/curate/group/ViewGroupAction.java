package org.calacademy.antweb.curate.group;


import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.LoginMgr;

public final class ViewGroupAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ViewGroupAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Administrators only (the JSP checks again).
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;

        // Extract attributes we will need
        HttpSession session = request.getSession();

        // get the intended groupId from the form; without one, the list of upload groups
        String idStr = ((SaveGroupForm) form).getId();
        int id;
        try {
            id = Integer.parseInt(idStr == null ? "" : idStr.trim());
        } catch (NumberFormatException e) {
            return new ActionForward("/viewGroups.do", true);
        }
        s_log.info("looking up group " + id);
            
        //Group group = GroupMgr.getGroup(id);
        Group group = null; 

        Connection connection = null;
        try {
            connection = getDataSource(request, "conPool").getConnection();

            group = new GroupDb(connection).getGroup(id);

			LoginDb loginDb = new LoginDb(connection);
			group.setCurators(loginDb.getCurators(group.getId()));
            //A.log("execute() setCurators() groupId:" + group.getId());
			UploadDb uploadDb = new UploadDb(connection);
			group.setLastUpload(uploadDb.getLastUploadByGroup(group.getId()));

        } catch (Exception e) {
            s_log.error("execute() e:" + e); 
        } finally {	
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }
        
        if (group == null || group.getId() <= 0) {
            request.setAttribute("message", "Upload group " + id + " not found. See the list of <a href=\""
                + AntwebProps.getDomainApp() + "/viewGroups.do\">upload groups</a>.");
            return mapping.findForward("message");
        }
        session.setAttribute("thisGroup", group);
        return mapping.findForward("success");
    }
}

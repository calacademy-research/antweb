package org.calacademy.antweb.curate.group;


import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

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
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.util.*;

public final class ViewGroupAction extends Action {

    private static Log s_log = LogFactory.getLog(ViewGroupAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Extract attributes we will need
        HttpSession session = request.getSession();

        // get the intended groupId from the form
        String idStr = ((SaveGroupForm) form).getId();
        if (idStr == null) {
          s_log.warn("execute() no id");
          return mapping.findForward("error");
        }
        int id = (Integer.valueOf(idStr)).intValue();
        s_log.info("looking up group " + id);
            
        //Group group = GroupMgr.getGroup(id);
        Group group = null; 

        java.sql.Connection connection = null;
        try {
            connection = getDataSource(request, "conPool").getConnection();

            group = (new GroupDb(connection)).getGroup(id);

			LoginDb loginDb = new LoginDb(connection);
			group.setCurators(loginDb.getCurators(group.getId()));
            //A.log("execute() setCurators() groupId:" + group.getId());
			UploadDb uploadDb = new UploadDb(connection);
			group.setLastUpload(uploadDb.getLastUploadByGroup(group.getId()));

        } catch (Exception e) {
            s_log.error("execute() e:" + e); 
        } finally {	
            try {
                connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }
        
        session.setAttribute("thisGroup", group);
        return (mapping.findForward("success"));
    }
}

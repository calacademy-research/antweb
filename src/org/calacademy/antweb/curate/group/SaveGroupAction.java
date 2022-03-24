package org.calacademy.antweb.curate.group;


import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

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
import org.calacademy.antweb.home.GroupDb;

public final class SaveGroupAction extends Action {

    private static Log s_log = LogFactory.getLog(SaveGroupAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm f,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        HttpSession session = request.getSession();

        Connection connection = null;
        boolean isNew = false;
        SaveGroupForm form = (SaveGroupForm) f;
                
        int id = Integer.parseInt(form.getId());
        Group group = null;
        
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveGroupAction");          

            if ("delete".equals(form.getStep())) {
                s_log.warn(" step:" + form.getStep() + " id:" + id);      
                new GroupDb(connection).deleteById(id);
                return mapping.findForward("success");
            }

            group = new GroupDb(connection).getGroup(id);

            s_log.info("execute() groupId:" + id + " found:" + group);

            if (group.getId() == 0) {
                isNew = true;
                group = new Group();
                group.setId(id);
            }
            group.setName(form.getName());
// Use parseInt(String) to convert a string to a int primitive
// Use valueOf(String) to convert a string to an Integer object.

            //Integer adminLoginId = new Integer(form.getAdminLoginId());
            Integer adminLoginId = Integer.valueOf(form.getAdminLoginId());

            group.setAdminLoginId(adminLoginId);
            group.setAbbrev(form.getAbbrev());

            if (isNew) {
                new GroupDb(connection).saveGroup(group);
            } else {
                new GroupDb(connection).updateGroup(group);
            }            
            
            GroupMgr.populate(connection, true);     

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "SaveGroupAction");
        }

        return mapping.findForward("success");
    }
}

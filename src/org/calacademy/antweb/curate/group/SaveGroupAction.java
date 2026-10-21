package org.calacademy.antweb.curate.group;


import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
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

public final class SaveGroupAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(SaveGroupAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm f,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        // Require a logged-in administrator: this action changes data.
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        ActionForward confirm = PostOnly.confirm(request, mapping, "Save or delete group " + request.getParameter("id"));
        if (confirm != null) return confirm;

        HttpSession session = request.getSession();

        Connection connection = null;
        boolean isNew = false;
        SaveGroupForm form = (SaveGroupForm) f;
                
        int id;
        try {
            id = Integer.parseInt(form.getId() == null ? "" : form.getId().trim());
        } catch (NumberFormatException e) {
            request.setAttribute("message", "No upload group number given.");
            return mapping.findForward("message");
        }
        Group group = null;
        
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "SaveGroupAction");          

            if ("delete".equals(form.getStep())) {
                s_log.warn(" step:" + form.getStep() + " id:" + id);
                // A group that still has logins or specimens is not deleted: its specimens would
                // belong to no group and its logins would upload into a group that does not exist.
                String inUse = GroupDb.whyNotDeletable(connection, id);
                if (inUse != null) {
                    request.setAttribute("message", Html.esc(inUse));
                    return mapping.findForward("message");
                }
                new GroupDb(connection).deleteById(id);
                GroupMgr.populate(connection, true);
                request.setAttribute("savedGroupText", "Upload group " + id + " was deleted.");
                return mapping.findForward("success");
            }

            // A new group (form from newGroup.do) is always inserted with the next free number,
            // even if another group took the number shown on the form in the meantime.
            // An existing group's number cannot be changed: it comes from a hidden field.
            boolean newGroupForm = "true".equals(form.getNewGroup());
            group = newGroupForm ? new Group() : new GroupDb(connection).getGroup(id);

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
            int adminLoginId = Integer.parseInt(form.getAdminLoginId());

            group.setAdminLoginId(adminLoginId);
            group.setAbbrev(form.getAbbrev());

            if (isNew) {
                new GroupDb(connection).saveGroup(group);
            } else {
                new GroupDb(connection).updateGroup(group);
            }            
            
            GroupMgr.populate(connection, true);     
            request.setAttribute("savedGroupText", (isNew ? "Created the upload group " : "Saved the upload group ")
                + group.getName() + " (number " + group.getId() + ").");
            request.setAttribute("savedGroupId", group.getId());

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "SaveGroupAction");
        }

        return mapping.findForward("success");
    }
}

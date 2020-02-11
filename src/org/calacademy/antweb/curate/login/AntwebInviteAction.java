package org.calacademy.antweb.curate.login;

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
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class AntwebInviteAction extends Action {

    private static Log s_log = LogFactory.getLog(AntwebInviteAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
            
        // Extract attributes we will need
        HttpSession session = request.getSession();

        Login accessLogin = LoginMgr.getAccessLogin(request);

        boolean isInvitee = (accessLogin == null);    // administrators are NOT invitees,
        
        Login login = null; 
        java.sql.Connection connection = null;
        try {
            connection = getDataSource(request, "conPool").getConnection();

            boolean isInvitedLogin = false;

            String email = ((SaveLoginForm) form).getEmail();
            String idStr = (new LoginDb(connection)).findInviteId(email);
            if (idStr != null) {
                isInvitedLogin = true;  // we found a pending invitation
            } else {
                return mapping.findForward("goToLogin");  // if user later follows invitation link...
            }
            int id = (new Integer(idStr)).intValue();
            s_log.info("looking up login " + id);
             
            login = (new LoginDb(connection)).getLogin(id);
            session.setAttribute("thisLogin", login);

            ArrayList groupList = (new GroupDb(connection)).getAllGroups();
            request.getSession().setAttribute("antwebGroups", groupList);  

            if (!isInvitee) return mapping.findForward("manageLogin");   

            if (isInvitedLogin) {  // The invitee needs to be logged in to see and save the page...
              session.setAttribute("accessLogin", accessLogin);
              session.setAttribute("isInvitee", true);
              return (mapping.findForward("manageLogin")); //antwebInvite 
            }           
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            org.calacademy.antweb.util.AntwebUtil.logStackTrace(e);            
            return (mapping.findForward("error"));
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                s_log.error("execute() finally e:" + e);
            }
        }

        return mapping.findForward("error");
    }
}

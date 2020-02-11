package org.calacademy.antweb.curate;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class ChangePasswordAction extends Action {

    private static Log s_log = LogFactory.getLog(ChangePasswordAction.class);
    
	public ActionForward execute(ActionMapping mapping, ActionForm form,
		HttpServletRequest request, HttpServletResponse response)
		throws IOException, ServletException {

		// Extract attributes we will need
		Locale locale = getLocale(request);
		HttpSession session = request.getSession();
		
        ActionForward c = Check.login(request, mapping); if (c != null) return c; 		
		Login accessLogin = LoginMgr.getAccessLogin(request);
		//Group group = accessLogin.getGroup();
		
		ChangePasswordForm theForm = (ChangePasswordForm) form;
		ActionMessages messages = new ActionMessages();
		ActionMessage msg = null;
		
		String oldPassword = (String) theForm.getOldPassword();
		String newPassword = (String) theForm.getNewPassword1();
		String newPassword2 = (String) theForm.getNewPassword2();
		
		if (!accessLogin.getPassword().equals(oldPassword)) {
			msg = new ActionMessage("error.login.failedLogin");
			messages.add("message",msg);
		}
		
		if ((newPassword == null) || (newPassword.length() == 0)) {
			msg = new ActionMessage("error.login.needPassword");
			messages.add("message",msg);
		} else if (!newPassword.equals(newPassword2)) {
			msg = new ActionMessage("error.login.passwordsMustMatch");
			messages.add("message", msg);
		}
		
		if (messages.isEmpty()) {
		
			java.sql.Connection connection = null;
	
			try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "ChangePasswordAction()");
	
				(new LoginDb(connection)).changePassword(accessLogin, newPassword); 
                
            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                return (mapping.findForward("error"));
            } finally { 		
                DBUtil.close(connection, this, "ChangePasswordAction()");
            }			
		}
		
		if (messages.isEmpty()) {
			session.setAttribute("accessLogin", accessLogin);
			return (mapping.findForward("success"));
		} else {
			saveMessages(request,messages);
			return (mapping.findForward("failure"));
		}
	}
}

package org.calacademy.antweb.curate;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

/**
 * Old /changePassword action: checks the old password, then LoginDb.changePassword (table login). Not used:
 * its mapping in struts-config.xml is commented out. Passwords are changed on the login page (saveLogin.do,
 * SaveLoginAction) and with a reset link (ResetPasswordAction). Docs: docs/flows/email-and-password-reset.md.
 */
public final class ChangePasswordAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ChangePasswordAction.class);
    
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
		
		String oldPassword = theForm.getOldPassword();
		String newPassword = theForm.getNewPassword1();
		String newPassword2 = theForm.getNewPassword2();
		
		if (!PasswordHash.matches(accessLogin.getPassword(), oldPassword)) {
			msg = new ActionMessage("error.login.failedLogin");
			messages.add("message",msg);
		}
		
		if (newPassword == null || newPassword.length() == 0) {
			msg = new ActionMessage("error.login.needPassword");
			messages.add("message",msg);
		} else if (!newPassword.equals(newPassword2)) {
			msg = new ActionMessage("error.login.passwordsMustMatch");
			messages.add("message", msg);
		}
		
		if (messages.isEmpty()) {
		
			Connection connection = null;
	
			try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, "ChangePasswordAction()");
	
				new LoginDb(connection).changePassword(accessLogin, newPassword);
                
            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                return mapping.findForward("error");
            } finally { 		
                DBUtil.close(connection, this, "ChangePasswordAction()");
            }			
		}
		
		if (messages.isEmpty()) {
			session.setAttribute("accessLogin", accessLogin);
			return mapping.findForward("success");
		} else {
			saveMessages(request,messages);
			return mapping.findForward("failure");
		}
	}
}

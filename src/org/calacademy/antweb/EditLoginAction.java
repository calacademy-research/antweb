package org.calacademy.antweb;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.home.PasswordResetDb;
import org.calacademy.antweb.util.*;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public final class EditLoginAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(EditLoginAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

  // We want this...
  //        ActionForward c = Check.login(request, mapping); if (c != null) return c;

        if (!LoginMgr.isLoggedIn(request)) {
            String theMessage = "Must be logged in to access this resource";
            request.setAttribute("message", theMessage);
            return mapping.findForward("message");
        }

        Login accessLogin = LoginMgr.getAccessLogin(request);

        boolean isAdmin = LoginMgr.isAdmin(accessLogin);

        HttpSession session = request.getSession();
        EditLoginForm editForm = (EditLoginForm) form;

        A.log("execute() id:" + editForm.getId() + " accessLogin(): " + accessLogin);

        String idStr = editForm.getId();
        if (idStr == null) return mapping.findForward("goToLogin");
        int id = Integer.parseInt(idStr);
        //s_log.info("looking up login " + id);

        if (!(isAdmin || accessLogin.getId() == id)) {
            String theMessage = "Insufficient privileges to modify user: " + Html.esc(idStr);
            request.setAttribute("message", theMessage);
            return mapping.findForward("message");
        }

        Login login = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("EditLoginAction.execute()");
        try {
          DataSource dataSource = getDataSource(request, "conPool");
          connection = DBUtil.getConnection(dataSource, dbMethodName);

          LoginDb loginDb = new LoginDb(connection);
          login = loginDb.getLogin(id);

          if (login == null) {
            s_log.warn("execute() login not found:" + id);
            return mapping.findForward("error");
          }

          boolean isSubmit = "true".equals(editForm.getIsSubmit());
          boolean isPost = HttpUtil.isPost(request);
          s_log.debug("execute() ContentType:" + request.getContentType());
          s_log.debug("execute() method:" + request.getMethod());
          s_log.debug("execute() isSubmit:" + editForm.getIsSubmit() + " isPost:" + isPost);

          if (isSubmit && !isPost) {
            // Changes are saved only from the form (POST), never from a link.
            request.setAttribute("message", "Please use the form to save changes.");
          } else if (isPost) {

            String message = validate(editForm, login, accessLogin);
            if (message != null) {
               s_log.debug("execute() validate() failure message:" + message);
               request.setAttribute("message", message);
               request.getSession().setAttribute("thisLogin", login);
               return mapping.findForward("editLogin");
            }
            
            //if (isNoChange(editForm, login)) 
            //HttpUtil.redirectInsecure(request, response, AntwebProps.getDomainApp());
            //return (mapping.findForward("home"));
            
            login.setName(editForm.getName());
            login.setEmail(editForm.getEmail());
            // A blank password means: keep the current one (the form no longer shows it).
            String newPassword = editForm.getPassword();
            boolean passwordChanged = newPassword != null && !newPassword.isEmpty();
            if (passwordChanged) login.setPassword(newPassword);
            login.setFirstName(editForm.getFirstName());
            login.setLastName(editForm.getLastName());
            
            s_log.debug("execute() loginEmail:" + login.getEmail());

            request.setAttribute("message", "User Account Saved.");

            loginDb.updateLogin(login, accessLogin);

            // updateLogin() stores a password only for your own login: end your other sessions.
            if (passwordChanged && accessLogin.getId() == login.getId()) {
              int version = new PasswordResetDb(connection).afterPasswordChange(login.getId());
              PasswordResetMgr.noteVersion(login.getId(), version);
              session.setAttribute(PasswordResetMgr.SESSION_VERSION_ATTR, version);  // keep this session
              PasswordResetMgr.logEvent("password changed by the user login:" + login.getId());
            }
            editForm.setPassword(null);
            editForm.setRetypePassword(null);
            editForm.setCurrentPassword(null);

            // A.log("execute() form must be submitted. Test with: https://localhost/editLogin.do?id=8564&isSubmit=true");
            // request.getSession().setAttribute("thisLogin", new Login());
          }
          
          s_log.debug("execute() login:" + login);
          request.getSession().setAttribute("thisLogin", login);
          return mapping.findForward("editLogin");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", Html.esc(e.toString()));
            return mapping.findForward("message");
        } finally { 		
            DBUtil.close(connection, this, dbMethodName);
        }
        
        //return mapping.findForward("goToLogin");
    }
    
    private boolean isNoChange(EditLoginForm editForm, Login login) {
        return editForm.getEmail().equals(login.getEmail())
                && editForm.getName().equals(login.getName())
                && (editForm.getPassword() == null || editForm.getPassword().isEmpty())
                && editForm.getFirstName().equals(login.getFirstName())
                && editForm.getLastName().equals(login.getLastName());
    }
    
    private String validate(EditLoginForm editForm, Login login, Login accessLogin) {

        String password = editForm.getPassword();
        String retypePassword = editForm.getRetypePassword();
        String email = editForm.getEmail();
        String message = null;

        if (email != null && !"".equals(email) && !AntwebUtil.validEmail(email)) {
            message = "Invalid Email:" + Html.esc(editForm.getEmail());
        }

        if (editForm.getName() == null || "".equals(editForm.getName())) {
            message = "Name must be valid";
        }

        String nameProblem = LoginNames.problem(editForm.getName(), editForm.getFirstName(), editForm.getLastName(), email, login);
        if (nameProblem != null) message = Html.esc(nameProblem);

        // Both password fields blank: keep the current password.
        if (password == null) password = "";
        if (retypePassword == null) retypePassword = "";
        if (!password.equals(retypePassword)) {
            message = "Password and Re-type Password must match";
        } else if (!password.isEmpty()) {
            // Changing the password of your own login needs your current password.
            if (accessLogin.getId() == login.getId()
                    && !PasswordHash.matches(login.getPassword(), editForm.getCurrentPassword())) {
                message = "To change your password, please enter your current password.";
            } else {
                String problem = PasswordPolicy.check(password, retypePassword, editForm.getName(), editForm.getEmail());
                if (problem != null) message = Html.esc(problem);
            }
        }
        return message;  
    }
}

package org.calacademy.antweb;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public final class EditLoginAction extends Action {

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
            String theMessage = "Insufficient privileges to modify user: " + idStr;
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

          if (isSubmit || isPost) {

            String message = validate(editForm, request);
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
            login.setPassword(editForm.getPassword()); 
            login.setFirstName(editForm.getFirstName());
            login.setLastName(editForm.getLastName());
            
            s_log.debug("execute() loginEmail:" + login.getEmail());

            request.setAttribute("message", "User Account Saved.");

            loginDb.updateLogin(login, accessLogin);

            // A.log("execute() form must be submitted. Test with: https://localhost/editLogin.do?id=8564&isSubmit=true");
            // request.getSession().setAttribute("thisLogin", new Login());
          }
          
          s_log.debug("execute() login:" + login);
          request.getSession().setAttribute("thisLogin", login);
          return mapping.findForward("editLogin");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            request.setAttribute("message", e.toString());
            return mapping.findForward("message");
        } finally { 		
            DBUtil.close(connection, this, dbMethodName);
        }
        
        //return mapping.findForward("goToLogin");
    }
    
    private boolean isNoChange(EditLoginForm editForm, Login login) {
        return editForm.getEmail().equals(login.getEmail())
                && editForm.getName().equals(login.getName())
                && editForm.getPassword().equals(login.getPassword())
                && editForm.getFirstName().equals(login.getFirstName())
                && editForm.getLastName().equals(login.getLastName());
    }
    
    private String validate(EditLoginForm editForm, HttpServletRequest request) {

        String password = editForm.getPassword();
        String retypePassword = editForm.getRetypePassword();
        String email = editForm.getEmail();
        String message = null;

        if (email != null && !"".equals(email) && !AntwebUtil.validEmail(email)) {
            message = "Invalid Email:" + editForm.getEmail();
        }

        if (editForm.getName() == null || "".equals(editForm.getName())) {
            message = "Name must be valid";
        }

        if (retypePassword == null || password == null
            || !password.equals(retypePassword)) {
            message = "Password and Re-type Password must match";
        }

        if (
                password == null || password.equals("") ||
                        retypePassword == null || retypePassword.equals("")
           ) {
            s_log.debug("validate() password:" + password + " retype:" + retypePassword);
             message = "Password fields may not be empty";
        }  
        return message;  
    }
}

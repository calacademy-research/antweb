package org.calacademy.antweb;


import java.io.IOException;
import java.sql.SQLException;

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

public final class EditLoginAction extends Action {

    private static Log s_log = LogFactory.getLog(EditLoginAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

  // We want this...
  //        ActionForward c = Check.login(request, mapping); if (c != null) return c;


        Login accessLogin = LoginMgr.getAccessLogin(request);

        HttpSession session = request.getSession();
        EditLoginForm editForm = (EditLoginForm) form;

        A.log("execute() id:" + editForm.getId() + " accessLogin(): " + accessLogin);

        Login login = null;
        java.sql.Connection connection = null;
        try {
          javax.sql.DataSource dataSource = getDataSource(request, "conPool");        
          connection = DBUtil.getConnection(dataSource, "updateDefaultSpecimen()");

          String idStr = editForm.getId();
          if (idStr == null) return (mapping.findForward("goToLogin"));
          int id = (Integer.valueOf(idStr)).intValue();
          //s_log.info("looking up login " + id);

          LoginDb loginDb = new LoginDb(connection);
          login = loginDb.getLogin(id);

          if (login == null) {
            s_log.warn("execute() login not found:" + id);
            return (mapping.findForward("error"));
          }


          boolean isSubmit = "true".equals(editForm.getIsSubmit());
          boolean isPost = HttpUtil.isPost(request);
          A.log("execute() ContentType:" + request.getContentType());
          A.log("execute() method:" + request.getMethod());
          A.log("execute() isSubmit:" + editForm.getIsSubmit() + " isPost:" + isPost);

          if (isSubmit || isPost) {

            String message = validate(editForm, request);
            if (message != null) {
               A.log("execute() validate() failure message:" + message);
               request.setAttribute("message", message);
               request.getSession().setAttribute("thisLogin", login);
               return (mapping.findForward("editLogin"));
            }
            
            //if (isNoChange(editForm, login)) 
            //HttpUtil.redirectInsecure(request, response, AntwebProps.getDomainApp());
            //return (mapping.findForward("home"));
            
            login.setName(editForm.getName());
            login.setEmail(editForm.getEmail());
            login.setPassword(editForm.getPassword()); 
            login.setFirstName(editForm.getFirstName());
            login.setLastName(editForm.getLastName());
            
            A.log("execute() loginEmail:" + login.getEmail());

            request.setAttribute("message", "User Account Saved.");          
            loginDb.userUpdateLogin(login);
          //} else {
          //    A.log("execute() form must be submitted. Test with: https://localhost/editLogin.do?id=8564&isSubmit=true");
          //    request.getSession().setAttribute("thisLogin", new Login());
          }
          
          A.log("execute() login:" + login);             
          request.getSession().setAttribute("thisLogin", login);
          return mapping.findForward("editLogin");
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return (mapping.findForward("error"));
        } finally { 		
            DBUtil.close(connection, this, "EditLoginAction()");
        }
        
        //return mapping.findForward("goToLogin");
    }
    
    private boolean isNoChange(EditLoginForm editForm, Login login) {
        if ( (editForm.getEmail().equals(login.getEmail()))
          && (editForm.getName().equals(login.getName()))
          && (editForm.getPassword().equals(login.getPassword()))
          && (editForm.getFirstName().equals(login.getFirstName()))
          && (editForm.getLastName().equals(login.getLastName())) )
        {
          return true;
        } else {
          return false;
        }
    }
    
    private String validate(EditLoginForm editForm, HttpServletRequest request) {

        String password = editForm.getPassword();
        String retypePassword = editForm.getRetypePassword();
        String email = editForm.getEmail();
        String message = null;

        if ((email != null) && (!"".equals(email)) && (!AntwebUtil.validEmail(email))) {
            message = "Invalid Email:" + editForm.getEmail();
        }

        if ((editForm.getName() == null) || ("".equals(editForm.getName()))) {
            message = "Name must be valid";
        }

        if ((retypePassword == null) || (password == null)
            || (!password.equals(retypePassword))) {
            message = "Password and Re-type Password must match";
        }

        if (
           ((password == null) || (password.equals(""))) ||
           ((retypePassword == null) || (retypePassword.equals("")))
           ) {
            A.log("validate() password:" + password + " retype:" + retypePassword);
             message = "Password fields may not be empty";
        }  
        return message;  
    }
}

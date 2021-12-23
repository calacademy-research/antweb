package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.*;

import java.sql.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class LoginAction extends Action {

    private static Log s_log = LogFactory.getLog(LoginAction.class);

    public ActionForward execute(ActionMapping mapping,  ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        String message = null;
        Locale locale = getLocale(request);
        HttpSession session = request.getSession();

        ActionForward a = Check.init(Check.LOGIN, request, mapping); if (a != null) return a;

        // This would prevent some passwords

        if (!LoginMgr.isAdmin(request)) {
          ActionForward d = Check.valid(request, mapping); if (d != null) return d;
        }
        //s_log.warn("Login.execute() attempt userName:" + userNameOrEmail);    
             
        LoginForm loginForm = (LoginForm) form;             
        String target = loginForm.getTarget();

        String userNameOrEmail = loginForm.getUserName();  // we allow email as a login term.
        String password = loginForm.getPassword();

        ActionMessages messages = new ActionMessages();

/*
    // Crazy characters don't seem to get this far. Filtered out sooner? Wtf.
    // Example: http://localhost/antweb/login.do?userName=mmprebus&password=1|
        if (!HttpUtil.isAlphaNumeric(password)) message = "password must be alphanumic";
        if (!HttpUtil.isAlphaNumeric(userNameOrEmail)) message = "username and email must be alphanumic";
        if (message != null) {
            A.log("Illegal character:" + message);
            ActionMessage msg = new ActionMessage("error.login.dbFailure");
            messages.add("message", msg);
            return (mapping.findForward("failure"));
            //request.getSession().setAttribute("message", message);
            //return (mapping.findForward("message"));
        }
*/
        String value = loginForm.getValue();
        
        s_log.debug("execute() value:" + value + " target:" + target + " userNameOrEmail:" + userNameOrEmail);
        //AntwebUtil.logShortStackTrace(10);

        Login login = null;
        
        if ("Browse".equals(value)) {
          login = LoginMgr.getAnonLogin();
            //browseAnonymously(request, messages);
        }

        if ("Create Account".equals(value)) {

            /*
            if (userNameOrEmail == null) {
                ActionMessage msg = new ActionMessage("error.login.needName");
                messages.add("message", msg);
            } else {
             */

                login = createAccount(request, userNameOrEmail, password, messages);
                if (login == null) {
                    s_log.debug("execute() createAccount login is null for userNameOrEmail:" + userNameOrEmail);
                    // bots do this.  Ignore.
                }

                if (login != null) {
                    message = "User Account created:" + userNameOrEmail;
                    request.getSession().setAttribute("thisLogin", login);
                    //if (messages.isEmpty())
                    target = "/editLogin.do?id=" + login.getId();
                    s_log.debug("createAccount() attempt " + userNameOrEmail + " login:" + login + " messages:" + messages.get() + " isEmpty:" + messages.isEmpty());

                    login = login(request, userNameOrEmail, password, messages);

                    // If the user is editing their own.
                    request.getSession().setAttribute("accessLogin", login);  // Log in the user.

                    request.getSession().setAttribute("thisLogin", login);  // The one being edited.

                    return new ActionForward(target, true);
                }
            }
            if ((value == null) || ("Login".equals(value))) {
                login = login(request, userNameOrEmail, password, messages);
                request.getSession().setAttribute("thisLogin", login);
                //A.log("login() attempt name:" + userNameOrEmail + " login:" + login + " messages:" + messages.size() + " target:" + target);
            }
        //}
        //request.setAttribute("message", message); 

        if (messages.isEmpty()) {
            //session.setAttribute("userName", userName);
            if (login == null) {
              s_log.error("execute() login is null for value:" + value);
            }
            session.setAttribute("accessLogin", login);
            //s_log.warn("Successful login of " + userNameOrEmail + " target:" + target);

            if ((target != null) && (!target.equals("")) 
              && (!target.contains("forgotPassword"))
              && (!target.contains("login.do"))) { 
              s_log.info("Target is " + target);
              if ( (target.contains("fieldGuideResults.do"))
                 ) {
                return new ActionForward(AntwebProps.getDomainApp(), true);
              }               
              return new ActionForward(target, true);  // target must be a physical page
            } else {
              // If logging in from the home page or the login page, go to the curate page.
              if ("".equals(target)) return new ActionForward(AntwebProps.getDomainApp() + "/index.do", true);
  
                return new ActionForward(AntwebProps.getDomainApp(), true);              
//              return (mapping.findForward("success"));
            }
        } else {
            s_log.debug("execute() Has messages  user:" + userNameOrEmail + " message:" + message + " messages:" + messages.size());
            saveMessages(request, messages);        
            LoginMgr.removeAccessLogin(request);
            return (mapping.findForward("failure"));
        }
    }

    private Login createAccount(HttpServletRequest request, String userNameOrEmail, String password, ActionMessages messages) {
        ActionMessage msg = null;

        Login login = null;
        
        if ((userNameOrEmail == null) || (userNameOrEmail.length() == 0)) {
            msg = new ActionMessage("error.login.needName");
            messages.add("message", msg);
        }
        
        if ((password == null) || (password.length() == 0)) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message", msg);
        }
        
        if (messages.isEmpty()) {

            java.sql.Connection connection = null;
            String connName = "LoginAction.createAccount()" + AntwebUtil.getRandomNumber();
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, connName);
                LoginDb loginDb = new LoginDb(connection);
            
                login = loginDb.createAccount(userNameOrEmail, password);           
                login.setGroupId(-1);
                login.setEmail("");
                login.setIsAdmin(false);
        
                if ((login == null) || (login.getPassword() == null) || (!login.getPassword().equals(password))) {
                    msg = new ActionMessage("error.login.failedLogin");
                    messages.add("message",msg);
                } else {
                  // Successful login
                  //s_log.warn("Login.execute()2 userName:" + userNameOrEmail + " login:" + login);                
                  LogMgr.appendLog("logins.txt", userNameOrEmail + " - " + (new java.util.Date()).toString());
                  loginDb.updateLastLogin(login);
                }
            } catch (AntwebException e) {
                if (e.toString().contains("already in use")) { 
                  msg = new ActionMessage("error.login.nameInUse");
                  s_log.debug("createAccount() msg:" + msg.toString());
                  messages.add("message",msg);
                }
                return null;
            } catch (SQLException e) {
                s_log.warn("Login.createAccount() e:" + e);
                if (e.toString().contains("already in use")) { 
                  msg = new ActionMessage("error.login.nameInUse");
                  s_log.debug("createAccount() msg:" + msg.toString());
                  messages.add("message",msg);
                } else {
                  msg = new ActionMessage("error.login.dbFailure");
                  messages.add("message",msg);
                  s_log.error("createAccount() e:" + e);
                }
                return null;
            } finally {
                DBUtil.close(connection, this, connName);
            }
        }    
        return login;
    }   
            
    private Login login(HttpServletRequest request, String userNameOrEmail, String password, ActionMessages messages) {
        ActionMessage msg = null;
        Login login = null;
        
        if ((userNameOrEmail == null) || (userNameOrEmail.length() == 0)) {
            msg = new ActionMessage("error.login.needName");
            messages.add("message", msg);
            s_log.debug("login() 1 msg:" + msg);
        }
        
        if ((password == null) || (password.length() == 0)) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message", msg);
            s_log.debug("login() 2 msg:" + msg);
        }

        if (messages.isEmpty()) {
            java.sql.Connection connection = null;
            String connName = "LoginAction.login()" + AntwebUtil.getRandomNumber();
            try {
                javax.sql.DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, connName);
                LoginDb loginDb = new LoginDb(connection);
                login = loginDb.getLoginByName(userNameOrEmail);
//A.log("login() A userNameOrEmail:" + userNameOrEmail + " login:" + login);
                if (login == null) login = loginDb.getLoginByEmail(userNameOrEmail);
//A.log("login() B userNameOrEmail:" + userNameOrEmail + " login:" + login);
                if ((login == null) || (login.getPassword() == null) || (!login.getPassword().equals(password))) {
                    msg = new ActionMessage("error.login.failedLogin");                    
                    messages.add("message",msg);
                    //A.log("login() 3 msg:" + msg + " pwd:" + password);            
                } else {
                  // Successful login
                  //s_log.warn("Login.execute()2 userName:" + userNameOrEmail + " login:" + login);                
                  LogMgr.appendLog("logins.txt", userNameOrEmail + " - " + (new java.util.Date()).toString());
                  loginDb.updateLastLogin(login);
                }      
            } catch (Exception sqle) {
                msg = new ActionMessage("error.login.dbFailure");
                messages.add("message",msg);
                s_log.debug("login() 4 msg:" + msg);
                s_log.error("Connection.process", sqle);
            } finally {
                DBUtil.close(connection, this, connName);
            }
        }   
        return login; 
    }    
    
}

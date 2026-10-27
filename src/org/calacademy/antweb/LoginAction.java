package org.calacademy.antweb;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.struts.action.*;

import java.sql.*;
import java.util.Date;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.util.*;
import org.calacademy.antweb.home.*;

public final class LoginAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(LoginAction.class);

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
        if (target != null && !target.isEmpty() && !HttpUtil.isSafeRedirectTarget(target)) {
            s_log.warn("execute() ignoring unsafe login target");
            target = null;
        }

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

        // Just opening the login page: show the form, without "Please provide a username" messages.
        if (userNameOrEmail == null && password == null && value == null) {
            // "Keep me logged in": the AW_REMEMBER cookie just logged this browser in (RememberMeMgr,
            // on GET /login.do), so go on to the page instead of asking for the password again.
            if (request.getAttribute(RememberMeMgr.AUTO_LOGGED_IN_ATTR) != null && LoginMgr.getAccessLogin(request) != null) {
                return new ActionForward(afterAutoLogin(target), true);
            }
            return mapping.findForward("failure");
        }

        // A password is only accepted in a POST: the login rate limit counts posts, and a password in the
        // address ends up in logs and browser history. Otherwise the form is shown again.
        if (password != null && !HttpUtil.isPost(request)) {
            s_log.info("execute() password not sent as a form post; showing the login form");
            return mapping.findForward("failure");
        }
        
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

                Turnstile.Result botCheck = Turnstile.check(request);
                if (botCheck != Turnstile.Result.PASSED) {
                    // Bot check (Cloudflare Turnstile) missing, failed or could not be verified: no account.
                    String key = botCheck == Turnstile.Result.UNAVAILABLE ? "error.login.botCheckUnavailable" : "error.login.botCheck";
                    messages.add("message", new ActionMessage(key));
                    saveMessages(request, messages);
                    return mapping.findForward("failure");
                }
                login = createAccount(request, userNameOrEmail, password, messages);
                if (login == null) {
                    s_log.debug("execute() createAccount login is null for userNameOrEmail:" + userNameOrEmail);
                    // bots do this.  Ignore.
                }

                if (login != null) {
                    message = "User Account created:" + Html.esc(userNameOrEmail);
                    request.getSession().setAttribute("thisLogin", login);
                    //if (messages.isEmpty())
                    target = "/editLogin.do?id=" + login.getId();
                    s_log.debug("createAccount() attempt " + userNameOrEmail + " login:" + login + " messages:" + messages.get() + " isEmpty:" + messages.isEmpty());

                    login = login(request, userNameOrEmail, password, messages);

                    // If the user is editing their own.
                    LoginMgr.logIn(request, login);  // Log in the user (new session id).

                    request.getSession().setAttribute("thisLogin", login);  // The one being edited.

                    return new ActionForward(target, true);
                }
            }
            if (value == null || "Login".equals(value)) {
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
            if (login != null) {
                LoginMgr.logIn(request, login);  // new session id at login (prevents session fixation)
            } else {
                LoginMgr.setAccessLogin(request, null);
            }
            if (login != null && login.getId() > 0 && (value == null || "Login".equals(value))) {
                // "Keep me logged in": drop any earlier token of this browser, then make a new one if asked.
                RememberMeMgr.forget(request, response);
                if (RememberMeMgr.isRequested(request)) RememberMeMgr.remember(request, response, login);
            }
            //s_log.warn("Successful login of " + userNameOrEmail + " target:" + target);

            if (target != null && !target.equals("")
                    && !target.contains("forgotPassword")
                    && !target.contains("resetPassword")
                    && !target.contains("curate.do")
                    && !target.contains("login.do")) {
              s_log.info("Target is " + target);
              if (target.contains("fieldGuideResults.do")
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
            return mapping.findForward("failure");
        }
    }

    /** Where to go after "Keep me logged in" logged the user in on opening the login page. */
    static String afterAutoLogin(String target) {
        if (target != null && !target.isEmpty() && HttpUtil.isSafeRedirectTarget(target)
                && !target.contains("login.do") && !target.contains("logout.do")
                && !target.contains("forgotPassword") && !target.contains("resetPassword")) {
            return target;
        }
        return AntwebProps.getDomainApp() + "/";
    }

    private Login createAccount(HttpServletRequest request, String userNameOrEmail, String password, ActionMessages messages) {
        ActionMessage msg = null;

        Login login = null;
        
        if (userNameOrEmail == null || userNameOrEmail.length() == 0) {
            msg = new ActionMessage("error.login.needName");
            messages.add("message", msg);
        } else if (!LoginNames.isValidName(userNameOrEmail)) {
            // Letters (any language), digits, space and . _ - @ ' + only.
            msg = new ActionMessage("error.login.invalidName");
            messages.add("message", msg);
        }
        
        if (password == null || password.length() == 0) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message", msg);
        }
        
        if (messages.isEmpty()) {

            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("LoginAction.createAccount()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                LoginDb loginDb = new LoginDb(connection);
            
                login = loginDb.createAccount(userNameOrEmail, password);           
                login.setGroupId(-1);
                login.setEmail("");
                login.setIsAdmin(false);
        
                if (login == null || !PasswordHash.matches(login.getPassword(), password)) {
                    msg = new ActionMessage("error.login.failedLogin");
                    messages.add("message",msg);
                } else {
                  // Successful login
                  //s_log.warn("Login.execute()2 userName:" + userNameOrEmail + " login:" + login);                
                  LogMgr.appendLog("logins.txt", userNameOrEmail + " - " + new Date());
                  loginDb.updateLastLogin(login);
                }
            } catch (AntwebException e) {
                if (e.toString().contains("already in use")) { 
                  msg = new ActionMessage("error.login.nameInUse");
                  s_log.debug("createAccount() msg:" + msg);
                  messages.add("message",msg);
                }
                return null;
            } catch (SQLException e) {
                s_log.warn("Login.createAccount() e:" + e);
                if (e.toString().contains("already in use")) { 
                  msg = new ActionMessage("error.login.nameInUse");
                  s_log.debug("createAccount() msg:" + msg);
                  messages.add("message",msg);
                } else {
                  msg = new ActionMessage("error.login.dbFailure");
                  messages.add("message",msg);
                  s_log.error("createAccount() e:" + e);
                }
                return null;
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
        }    
        return login;
    }   
            
    private Login login(HttpServletRequest request, String userNameOrEmail, String password, ActionMessages messages) {
        ActionMessage msg = null;
        Login login = null;
        
        if (userNameOrEmail == null || userNameOrEmail.length() == 0) {
            msg = new ActionMessage("error.login.needName");
            messages.add("message", msg);
            s_log.debug("login() 1 msg:" + msg);
        }
        
        if (password == null || password.length() == 0) {
            msg = new ActionMessage("error.login.needPassword");
            messages.add("message", msg);
            s_log.debug("login() 2 msg:" + msg);
        }

        if (messages.isEmpty()) {
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("LoginAction.login()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                LoginDb loginDb = new LoginDb(connection);
                login = loginDb.getLoginByName(userNameOrEmail);
//A.log("login() A userNameOrEmail:" + userNameOrEmail + " login:" + login);
                if (login == null) login = loginDb.getLoginByEmail(userNameOrEmail);
//A.log("login() B userNameOrEmail:" + userNameOrEmail + " login:" + login);
                if (login != null && new InactiveAccountDb(connection).isInactive(login.getId())) {
                    // Inactive account (regular account not used for three years, see InactiveAccounts):
                    // no password login; a reactivation link is emailed instead.
                    String ip = HttpUtil.getClientIpAddress(request);
                    if (PasswordHash.matches(login.getPassword(), password)) {
                        String result = PasswordResetMgr.sendReactivationLink(connection, login.getId(), login.getDisplayName(),
                            login.getName(), login.getEmail(), ip);
                        PasswordResetMgr.logEvent("login refused, account inactive login:" + login.getId() + " reactivation link:" + result);
                        String text = PasswordResetMgr.NO_EMAIL.equals(result)
                            ? InactiveAccounts.LOGIN_MESSAGE_NO_EMAIL : InactiveAccounts.LOGIN_MESSAGE;
                        messages.add("message", new ActionMessage(text, false));
                    } else {
                        // Wrong password: the same answer as for any wrong password, so the page tells
                        // someone who guesses nothing new; the owner is still emailed a link (background).
                        PasswordResetMgr.requestReactivationAsync(login.getId(), ip);
                        messages.add("message", new ActionMessage("error.login.failedLogin"));
                    }
                    login = null;
                } else if (login == null || !PasswordHash.matches(login.getPassword(), password)) {
                    msg = new ActionMessage("error.login.failedLogin");                    
                    messages.add("message",msg);
                    //A.log("login() 3 msg:" + msg + " pwd:" + password);            
                } else if (isMustReset(connection, login)) {
                    // Correct password, but this account must set a new one first: email a reset link.
                    String result = PasswordResetMgr.sendResetLink(connection, login.getId(), login.getDisplayName(), login.getEmail(),
                        HttpUtil.getClientIpAddress(request), PasswordResetMgr.SUBJECT_RESET,
                        "For security, AntWeb asks you to set a new password before you log in again.",
                        PasswordResetMgr.VALID_MINUTES, true);
                    PasswordResetMgr.logEvent("login refused until password reset (must_reset) login:" + login.getId() + " link:" + result);
                    String text = PasswordResetMgr.NO_EMAIL.equals(result)
                        ? "For security, please set a new password. There is no email address on this account, so please write to antweb@antweb.org."
                        : "For security, please set a new password. We have emailed you a link to do so (please check your inbox and spam folder).";
                    messages.add("message", new ActionMessage(text, false));
                    login = null;
                } else {
                  // Successful login
                  //s_log.warn("Login.execute()2 userName:" + userNameOrEmail + " login:" + login);                
                  LogMgr.appendLog("logins.txt", userNameOrEmail + " - " + new Date());
                  loginDb.updateLastLogin(login);
                  if (PasswordHash.needsRehash(login.getPassword())) {
                    // Still stored as plain text: store the hash instead.
                    try {
                      loginDb.changePassword(login, password);
                    } catch (SQLException e) {
                      s_log.error("login() could not hash the password of login id:" + login.getId() + " e:" + e);
                    }
                  }
                }      
            } catch (Exception sqle) {
                msg = new ActionMessage("error.login.dbFailure");
                messages.add("message",msg);
                s_log.debug("login() 4 msg:" + msg);
                s_log.error("Connection.process", sqle);
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
        }   
        return login; 
    }    

    private static boolean isMustReset(Connection connection, Login login) {
        try {
            return new PasswordResetDb(connection).isMustReset(login.getId());
        } catch (SQLException e) {
            s_log.warn("isMustReset() login:" + login.getId() + " e:" + e);  // e.g. column not created yet
            return false;
        }
    }
    
}

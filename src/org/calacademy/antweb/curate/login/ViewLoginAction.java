package org.calacademy.antweb.curate.login;


import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

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
import org.calacademy.antweb.home.*;

/**
 * /viewLogin.do?id=N: one login's form, for admins any login, for others only their own (table login, with its
 * projects and countries: LoginDb.getDeepLogin). Puts thisLogin, the form data (LoginEditOptions) and the upload
 * groups in the session; curators and admins get curate/viewLogin.jsp, other users editLogin.jsp. Read-only.
 * Docs: docs/guides/curate-page.md (Editing a login). Tests: tests/e2e/test_login_form.py, test_curator.py.
 */
public final class ViewLoginAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ViewLoginAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
            
        ActionForward a = Check.init("project", request, mapping); if (a != null) return a;
        a = Check.init("geolocale", request, mapping); if (a != null) return a;
        ActionForward c = Check.login(request, mapping); if (c != null) return c;        
        
        Login accessLogin = LoginMgr.getAccessLogin(request);
        
        HttpSession session = request.getSession();

        int id;
        try {
            id = Integer.parseInt(((SaveLoginForm) form).getId());
        } catch (NumberFormatException e) {
            request.setAttribute("message", "viewLogin.do needs id=<login id>");
            return mapping.findForward("message");
        }
        // Only your own account, or any account for an administrator. Checked before anything
        // is loaded, so another user's login is never put in the session.
        if (!accessLogin.isAdmin() && accessLogin.getId() != id) {
            s_log.warn("execute() denied: login:" + accessLogin.getId() + " tried to view id:" + id);
            request.setAttribute("message", "You are not allowed to view that account.");
            return mapping.findForward("message");
        }

        Login login = null;
        Connection connection = null;
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, "ViewLoginAction()");

            LoginDb loginDb = new LoginDb(connection);
            login = loginDb.getDeepLogin(id);
            if (login == null) {
                request.setAttribute("message", "Login not found.");
                return mapping.findForward("message");
            }
            session.setAttribute("thisLogin", login);
            // The form's data (stored assignments, upload groups, lists to add from): LoginEditPage.
            LoginEditOptions.prepare(connection, request, id, false);

            // This fetches the full list for select box population
            ArrayList<Group> groupList = new GroupDb(connection).getAllGroups();
            request.getSession().setAttribute("antwebGroups", groupList);             

        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            return mapping.findForward("error");
        } finally { 		
            DBUtil.close(connection, this, "ViewLoginAction()");
        }
        
        if (accessLogin.isAdmin()) {    
          return mapping.findForward("viewLogin");
        } else if (accessLogin.getId() == login.getId()) {
          // This user is visiting their own user account
          if (accessLogin.isCurator()) {
            return mapping.findForward("viewLogin");
          } else {
            return mapping.findForward("editLogin");
          }
        } else {
          // This user is not authorized to view this page
          return mapping.findForward("error");
        }

    }
}

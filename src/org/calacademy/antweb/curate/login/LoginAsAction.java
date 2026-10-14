package org.calacademy.antweb.curate.login;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.LoginDb;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.LogMgr;
import org.calacademy.antweb.util.LoginMgr;

/**
 * "Login as": lets an administrator act as another login (for example to
 * upload something for a curator), without knowing that login's password.
 *
 *   /loginAs.do?id=1234
 *
 * Administrators only. The switch is written to logins.txt. Log out to end it.
 */
public final class LoginAsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(LoginAsAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Require a logged-in administrator.
        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);

        int id;
        try {
            id = Integer.parseInt(request.getParameter("id"));
        } catch (NumberFormatException e) {
            request.setAttribute("message", "loginAs.do needs id=<login id>");
            return mapping.findForward("message");
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("LoginAsAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            Login target = new LoginDb(connection).getLogin(id);
            if (target == null) {
                request.setAttribute("message", "No login with id " + id);
                return mapping.findForward("message");
            }
            String note = "admin " + admin.getName() + " (id " + admin.getId() + ") logged in as "
                + target.getName() + " (id " + target.getId() + ") - " + new Date();
            LogMgr.appendLog("logins.txt", note);
            s_log.warn("execute() " + note);

            // Start a fresh session for the target login (drops the admin's session state).
            request.getSession().invalidate();
            request.getSession(true).setAttribute("accessLogin", target);
        } catch (SQLException e) {
            s_log.error("execute() id:" + id + " e:" + e);
            request.setAttribute("message", "Could not log in as id " + id);
            return mapping.findForward("message");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return new ActionForward(AntwebProps.getDomainApp() + "/curate.do", true);
    }
}

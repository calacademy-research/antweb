package org.calacademy.antweb;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.home.InactiveAccountDb;
import org.calacademy.antweb.home.PasswordResetDb;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.PasswordPolicy;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * /resetPassword.do?token=... : the page a reset email links to.
 *
 * GET shows the form (new password twice) if the token is valid. POST checks
 * the token again (unused, not expired), applies PasswordPolicy, then stores
 * the new password, marks the token used, cancels the login's other tokens,
 * clears must_reset and ends all of the login's sessions. An inactive account
 * (InactiveAccounts) becomes active again.
 *
 * Request attributes for resetPassword-body.jsp:
 *   resetState  "invalid", "form" or "done"
 *   resetToken  the token, for the form's hidden field
 *   resetError  a message to show with the form, or absent
 */
public final class ResetPasswordAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(ResetPasswordAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Keep the token out of caches and out of Referer headers sent to other sites.
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        String token = request.getParameter("token");
        String state = "invalid";

        if (PasswordResetMgr.isWellFormed(token)) {
            String tokenHash = PasswordResetMgr.hashToken(token);
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("ResetPasswordAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                PasswordResetDb db = new PasswordResetDb(connection);
                int loginId = db.findValidLoginId(tokenHash);
                PasswordResetDb.Account account = loginId > 0 ? db.getAccount(loginId) : null;
                if (account != null) {
                    state = "form";
                    request.setAttribute("resetToken", token);
                    if (HttpUtil.isPost(request)) {
                        String password = request.getParameter("password");
                        String retype = request.getParameter("retypePassword");
                        String problem = PasswordPolicy.check(password, retype, account.name, account.email);
                        if (problem != null) {
                            request.setAttribute("resetError", problem);
                        } else if (!db.consumeToken(tokenHash, loginId)) {
                            state = "invalid";  // used or expired meanwhile
                        } else {
                            int version = db.setNewPassword(loginId, password);
                            db.invalidateOutstanding(loginId);
                            // The link proves the email address: an inactive account is active again.
                            if (InactiveAccountDb.reactivateQuietly(connection, loginId)) {
                                PasswordResetMgr.logEvent("inactive account reactivated with a reset link login:" + loginId);
                                request.setAttribute("reactivated", Boolean.TRUE);
                            }
                            PasswordResetMgr.noteVersion(loginId, version);  // ends all sessions at once
                            PasswordResetMgr.logEvent("password set with reset link login:" + loginId);
                            state = "done";
                        }
                    }
                }
            } catch (SQLException e) {
                s_log.error("execute() e:" + e);
                request.setAttribute("resetError", "Sorry, there is a problem with our database. Please try again later.");
                state = "invalid";
            } finally {
                DBUtil.close(connection, this, dbMethodName);
            }
        }
        if (!"form".equals(state)) request.removeAttribute("resetToken");
        request.setAttribute("resetState", state);
        return mapping.findForward("success");
    }
}

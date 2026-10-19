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

import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * /resetPassword.do?token=... : the page a reset email links to.
 *
 * GET shows the form (new password twice) if the token is valid, and changes
 * nothing (link scanners in email systems open links first). POST checks
 * the token again (unused, not expired), applies PasswordPolicy, then stores
 * the new password, marks the token used, cancels the login's other tokens,
 * clears must_reset and ends all of the login's sessions. An inactive account
 * (InactiveAccounts) becomes active again. The logic is in
 * PasswordResetMgr.handleResetPage() (unit-tested without a servlet container).
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
            Connection connection = null;
            String dbMethodName = DBUtil.getDbMethodName("ResetPasswordAction.execute()");
            try {
                DataSource dataSource = getDataSource(request, "conPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName);
                // Only the POST that sets a valid new password uses up the token. Opening the link
                // (GET), any number of times, changes nothing: email security scanners open links
                // before the person does.
                boolean isPost = HttpUtil.isPost(request);
                PasswordResetMgr.ResetPage page = PasswordResetMgr.handleResetPage(connection, token, isPost,
                    isPost ? request.getParameter("password") : null,
                    isPost ? request.getParameter("retypePassword") : null);
                state = page.state;
                if ("form".equals(state)) request.setAttribute("resetToken", token);
                if (page.error != null) request.setAttribute("resetError", page.error);
                if (page.reactivated) request.setAttribute("reactivated", Boolean.TRUE);
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

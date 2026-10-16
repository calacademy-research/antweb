package org.calacademy.antweb.curate.login;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

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
import org.calacademy.antweb.home.PasswordResetDb;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * Ask every curator and administrator to set a new password. Administrators only.
 *
 *   /passwordResetCampaign.do                  preview: counts and the list (emails masked)
 *   POST action=mark&confirm=yes               must_reset=1 and end all sessions, for every
 *                                              listed account except the administrator doing it
 *   POST action=send&confirm=yes               email each listed account a reset link
 *                                              (background, about 10 a second; no email = skipped)
 *
 * Who is listed: PasswordResetDb.getCampaignAccounts(). Every step is written
 * to admin.log. Nothing here runs on its own.
 */
public final class PasswordResetCampaignAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(PasswordResetCampaignAction.class);

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);

        String action = request.getParameter("action");
        if (action == null || action.isEmpty()) action = "preview";
        boolean confirmed = HttpUtil.isPost(request) && "yes".equals(request.getParameter("confirm"));
        String who = admin.getName() + " (id " + admin.getId() + ")";

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("PasswordResetCampaignAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            PasswordResetDb db = new PasswordResetDb(connection);

            String result = null;
            if ("mark".equals(action) || "send".equals(action)) {
                if (!confirmed) {
                    result = "Nothing done: this step needs the form below (POST) with the confirm box ticked.";
                } else if ("mark".equals(action)) {
                    int marked = 0;
                    for (PasswordResetDb.Account a : db.getCampaignAccounts()) {
                        if (a.id == admin.getId()) continue;  // do not log out the administrator running the campaign
                        db.markMustReset(a.id);
                        PasswordResetMgr.forgetVersion(a.id);
                        marked++;
                    }
                    result = "Marked " + marked + " accounts: they must set a new password, and their sessions have ended.";
                    PasswordResetMgr.logEvent("campaign mark by " + who + ": " + marked + " accounts set must_reset=1, sessions ended");
                } else {
                    if (PasswordResetMgr.startCampaignSend(admin.getName(), admin.getId())) {
                        result = "Sending started. Reload this page to follow progress; the summary is also written to admin.log.";
                        PasswordResetMgr.logEvent("campaign send started by " + who);
                    } else {
                        result = "A send is already running.";
                    }
                }
            } else if (!"preview".equals(action)) {
                result = "Unknown action.";
            }

            // After a confirmed mark or send, redirect to a plain GET of this page, so reloading
            // the result page cannot run the step again (POST/redirect/GET).
            if (confirmed && ("mark".equals(action) || "send".equals(action))) {
                request.getSession().setAttribute("campaignFlash", result);
                return new ActionForward("/passwordResetCampaign.do", true);
            }
            Object flash = request.getSession().getAttribute("campaignFlash");
            if (flash != null) {
                request.getSession().removeAttribute("campaignFlash");
                if (result == null) result = flash.toString();
            }

            List<PasswordResetDb.Account> accounts = db.getCampaignAccounts();
            if ("preview".equals(action)) {
                PasswordResetMgr.logEvent("campaign preview by " + who + ": " + accounts.size() + " accounts");
            }
            request.setAttribute("campaignAccounts", accounts);
            request.setAttribute("campaignResult", result);
            request.setAttribute("campaignStatus", PasswordResetMgr.getCampaignStatus());
        } catch (SQLException e) {
            s_log.error("execute() action:" + action + " e:" + e);
            request.setAttribute("campaignResult", "Database error: " + e.getMessage());
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        return mapping.findForward("success");
    }
}

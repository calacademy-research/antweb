package org.calacademy.antweb.curate.login;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
import org.calacademy.antweb.home.InactiveAccountDb;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.InactiveAccounts;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * Make regular accounts not used for three years inactive. Administrators only.
 *
 *   GET  /inactiveAccounts.do                preview: the rule, counts (with and without email,
 *                                            never logged in, linked data) and a sample list.
 *                                            Changes nothing.
 *   POST /inactiveAccounts.do confirm=yes    apply: back up the login rows to a new table
 *                                            login_inactive_backup_YYYYMMDD, then set inactive = 1,
 *                                            inactive_since = now(), end their sessions and delete
 *                                            their "Keep me logged in" tokens. Nothing is deleted
 *                                            and no email is sent.
 *
 * The rule is InactiveAccounts.select(), evaluated again at apply time. Both steps are written
 * to admin.log.
 *
 * Undo (all rows of one run, BACKUP = the table the run reports):
 *   update login l join BACKUP b on b.id = l.id
 *     set l.inactive = 0, l.inactive_since = null, l.created = l.created;
 * Ended sessions and deleted "Keep me logged in" tokens do not come back (people log in again).
 */
public final class InactiveAccountsAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(InactiveAccountsAction.class);
    private static final int SAMPLES = 50;
    private static final String FLASH = "inactiveAccountsFlash";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);
        String who = admin.getName() + " (id " + admin.getId() + ")";
        boolean apply = HttpUtil.isPost(request) && "yes".equals(request.getParameter("confirm"));

        StringBuilder msg = new StringBuilder();
        msg.append("<h2>Inactive accounts</h2>");

        Object flash = request.getSession().getAttribute(FLASH);
        if (flash != null) {
            request.getSession().removeAttribute(FLASH);
            msg.append("<p><font color=\"green\"><b>").append(Html.esc(flash.toString())).append("</b></font></p>");
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("InactiveAccountsAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            InactiveAccountDb db = new InactiveAccountDb(connection);
            db.ensureSchema();  // normally done at startup

            LocalDateTime now = LocalDateTime.now();
            List<InactiveAccounts.Candidate> all = db.getCandidates();
            List<InactiveAccounts.Candidate> selected = InactiveAccounts.select(all, now);

            if (apply) {
                String result;
                if (selected.isEmpty()) {
                    result = "Nothing to do: no account matches the rule.";
                } else {
                    InactiveAccountDb.ApplyResult r = db.apply(selected, now);
                    for (InactiveAccounts.Candidate c : selected) PasswordResetMgr.forgetVersion(c.id);  // sessions end at once
                    result = "Done: " + r.updated + " accounts made inactive (" + r.backedUp + " rows backed up in table "
                        + r.backupTable + "; " + r.rememberTokensDeleted + " \"Keep me logged in\" tokens deleted).";
                    PasswordResetMgr.logEvent("inactive accounts applied by " + who + ": " + r.updated + " made inactive, backup "
                        + r.backupTable + " (" + r.backedUp + " rows), remember tokens deleted " + r.rememberTokensDeleted);
                    s_log.warn("execute() " + result + " by " + who);
                }
                // POST/redirect/GET: reloading the result page cannot run it again.
                request.getSession().setAttribute(FLASH, result);
                return new ActionForward("/inactiveAccounts.do", true);
            }

            int regular = 0, alreadyInactive = 0;
            for (InactiveAccounts.Candidate c : all) {
                if (InactiveAccounts.isRegular(c)) regular++;
                if (c.inactive) alreadyInactive++;
            }
            int withEmail = 0, never = 0;
            for (InactiveAccounts.Candidate c : selected) {
                if (c.hasEmail()) withEmail++;
                if (c.lastLogin == null) never++;
            }
            LocalDateTime cutoff = InactiveAccounts.cutoff(now);
            int idLimit = InactiveAccounts.oldIdLimit(all, cutoff);

            msg.append("<p>Preview only; nothing has been changed.</p>");
            msg.append("<p><b>Rule:</b> a regular account (not an administrator, no group, no specimen or image upload rights, "
                + "no assigned projects or countries) that was last used before ").append(cutoff.format(DAY))
               .append(" (three years ago), or that never logged in and has a login id below ").append(idLimit)
               .append(" (the newest account that had already logged in before ").append(cutoff.format(DAY))
               .append("; login.created cannot be used, it was overwritten in October 2026). Accounts already inactive are skipped.</p>");

            msg.append("<ul>");
            msg.append("<li>Logins: ").append(all.size()).append(" (regular ").append(regular)
               .append(", already inactive ").append(alreadyInactive).append(")</li>");
            msg.append("<li><b>Would become inactive: ").append(selected.size()).append("</b>")
               .append(" (with an email address ").append(withEmail)
               .append(", without ").append(selected.size() - withEmail)
               .append("; never logged in ").append(never).append(")</li>");
            msg.append("</ul>");

            Map<String, Integer> linked = db.countLinkedData(selected);
            if (!linked.isEmpty()) {
                msg.append("<p>Of these, accounts with linked data (kept as it is; nothing is deleted):</p><ul>");
                for (Map.Entry<String, Integer> e : linked.entrySet()) {
                    msg.append("<li>").append(Html.esc(e.getKey())).append(": ").append(e.getValue()).append("</li>");
                }
                msg.append("</ul>");
            }

            msg.append("<p>What happens to each: the login row is copied to a backup table (login_inactive_backup_")
               .append(Html.esc(InactiveAccounts.backupTableName(now).substring("login_inactive_backup_".length())))
               .append("), then inactive = 1 and inactive_since = now; its sessions end and its \"Keep me logged in\" tokens are deleted. "
                + "No email is sent now. Someone who later logs in to an inactive account, or uses \"Forgot your password?\", "
                + "is emailed a link that reactivates it.</p>");

            if (!selected.isEmpty()) {
                msg.append("<form method=\"POST\" action=\"").append(Html.escAttr(AntwebProps.getSecureDomainApp()))
                   .append("/inactiveAccounts.do\">").append(Csrf.field(request))
                   .append("<label><input type=\"checkbox\" name=\"confirm\" value=\"yes\"> Yes, make these ")
                   .append(selected.size()).append(" accounts inactive</label> ")
                   .append("<input type=\"submit\" value=\"Make inactive\"></form>");

                msg.append("<p>Sample (first ").append(Math.min(SAMPLES, selected.size())).append(" by login id):</p>");
                msg.append("<table border=\"1\" cellpadding=\"3\" cellspacing=\"0\">")
                   .append("<tr><th>Login id</th><th>Username</th><th>Name</th><th>Email</th><th>Last login</th></tr>");
                for (int i = 0; i < selected.size() && i < SAMPLES; i++) {
                    InactiveAccounts.Candidate c = selected.get(i);
                    msg.append("<tr><td>").append(c.id).append("</td><td>").append(Html.esc(c.name))
                       .append("</td><td>").append(Html.esc(c.getDisplayName()))
                       .append("</td><td>").append(c.hasEmail() ? Html.esc(PasswordResetMgr.maskEmail(c.email)) : "<i>none</i>")
                       .append("</td><td>").append(c.lastLogin == null ? "<i>never</i>" : c.lastLogin.format(DAY))
                       .append("</td></tr>");
                }
                msg.append("</table>");
            }
            PasswordResetMgr.logEvent("inactive accounts preview by " + who + ": " + selected.size() + " would become inactive");
        } catch (SQLException e) {
            s_log.error("execute() apply:" + apply + " e:" + e);
            msg.append("<p>Database error; nothing was changed: ").append(Html.esc(e.getMessage())).append("</p>");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        request.setAttribute("message", msg.toString());
        return mapping.findForward("message");
    }
}

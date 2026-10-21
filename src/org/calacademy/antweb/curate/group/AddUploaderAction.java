package org.calacademy.antweb.curate.group;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.Emailer;
import org.calacademy.antweb.util.GroupMgr;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.PasswordResetMgr;

/**
 * Add an uploader: one admin page for "someone wants to upload specimens". Administrators only.
 *
 *   GET  /addUploader.do                     the form (email, names, existing or new upload group)
 *   POST /addUploader.do step=save           UploaderOnboarding.apply(): find or create the login,
 *                                            find or create the upload group, attach, upload rights;
 *                                            a one-time set-password link when the login has no
 *                                            password (or is inactive). Then redirects to the summary.
 *   GET  /addUploader.do?done=1              summary of what was done and the welcome email, as it
 *                                            would be sent, with a Send button
 *   POST /addUploader.do step=send           sends the welcome email (Amazon SES), once
 *
 * The welcome email is never sent without the administrator clicking Send. The set-password
 * link lives only in this administrator's session until it is sent or the page is left for a
 * new uploader. Every change is written to admin.log (PasswordResetMgr.logEvent).
 */
public final class AddUploaderAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(AddUploaderAction.class);
    static final String DONE = "addUploaderDone";
    public static final String PAGE = "addUploaderHtml";

    /** What the summary page shows (kept in the administrator's session). */
    static final class Done implements Serializable {
        private static final long serialVersionUID = 1L;
        List<String> lines = new ArrayList<>();
        String email;
        String subject;
        String body;
        int loginId;
        String loginName;
        boolean hasLink;
        String sent;   // null until Send was clicked: the outcome
    }

    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        ActionForward denied = LoginMgr.mustBeAdmin(request, mapping); if (denied != null) return denied;
        Login admin = LoginMgr.getAccessLogin(request);
        HttpSession session = request.getSession();
        String step = request.getParameter("step");
        boolean post = HttpUtil.isPost(request);

        // The form token did not match this session (SessionRequestFilter let the request through
        // only for this page, see Csrf.mayRefill): typically AntWeb restarted while the page was open
        // and "Keep me logged in" started a new session, or the admin logged in again in another tab.
        // Nothing is done with this request: the same page is shown again, filled in, with the new
        // session's token, so one more click does it.
        boolean sessionChanged = request.getAttribute(Csrf.REFUSED_ATTR) != null;
        String notice = null;
        if (sessionChanged) {
            s_log.warn("execute() form token did not match the session (" + request.getAttribute(Csrf.REFUSED_ATTR)
                + "): page shown again, nothing done. login:" + admin.getId() + " step:" + step);
            notice = "send".equals(step) ? SESSION_CHANGED_SEND : SESSION_CHANGED;
            post = false;
        }

        if (post && "send".equals(step)) {
            Done done = (Done) session.getAttribute(DONE);
            if (done != null && done.sent == null) {
                boolean ok = Emailer.sendTransactional(done.email, done.subject, done.body);
                done.sent = ok ? "Sent to " + done.email + "." : "Not sent: the mail server did not accept it (see emails.log). Copy the text below and send it from your own email.";
                PasswordResetMgr.logEvent("welcome email for uploader login:" + done.loginId + (ok ? " sent" : " NOT sent") + " by login:" + admin.getId());
                session.setAttribute(DONE, done);
            }
            return new ActionForward("/addUploader.do?done=1", true);
        }

        List<UploaderOnboarding.GroupRow> groups = new ArrayList<>();
        UploaderOnboarding.Request in = fromRequest(request);
        List<String> problems = new ArrayList<>();

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("AddUploaderAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            if (post && "save".equals(step)) {
                UploaderOnboarding.Result r = UploaderOnboarding.apply(connection, in);
                if (r.ok()) {
                    Done done = new Done();
                    done.lines.addAll(r.done);
                    String link = null;
                    if (r.needsPasswordLink) {
                        link = PasswordResetMgr.newInviteLink(connection, r.login.id, admin.getId());
                        done.lines.add("Made a one-time link (" + days() + " days) for " + r.login.name
                            + " to choose a password; it is in the welcome email below.");
                    }
                    done.email = r.login.email;
                    done.loginId = r.login.id;
                    done.loginName = r.login.name;
                    done.hasLink = link != null;
                    done.subject = UploaderOnboarding.welcomeSubject();
                    done.body = UploaderOnboarding.welcomeBody(AntwebProps.getSecureDomainApp(), r.login, r.group, link, days());
                    session.setAttribute(DONE, done);
                    PasswordResetMgr.logEvent("add uploader by login:" + admin.getId() + ": login:" + r.login.id
                        + (r.newLogin ? " (new)" : "") + " group:" + r.group.id + (r.newGroup ? " (new)" : ""));
                    s_log.warn("execute() add uploader login:" + r.login.id + " group:" + r.group.id + " by " + admin.getId());
                    // The in-memory lists (Group Manager, Login Manager, Upload as) show the change at once.
                    GroupMgr.populate(connection, true);
                    LoginMgr.populate(connection, true, false);
                    return new ActionForward("/addUploader.do?done=1", true);
                }
                problems.addAll(r.problems);
            }
            groups = UploaderOnboarding.allGroups(connection);
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            problems.add("Database error, nothing was changed: " + e.getMessage());
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }

        Done done = "1".equals(request.getParameter("done")) || (sessionChanged && "send".equals(step))
            ? (Done) session.getAttribute(DONE) : null;
        if (!post && done == null && !sessionChanged) session.removeAttribute(DONE);  // a fresh form: forget the last link
        if (sessionChanged && done == null) notice = SESSION_CHANGED;
        request.setAttribute(PAGE, done != null
            ? summaryHtml(request, done, notice)
            : formHtml(request, in, groups, problems, notice));
        return mapping.findForward("success");
    }

    static int days() {
        return PasswordResetMgr.INVITE_VALID_MINUTES / (24 * 60);
    }

    static UploaderOnboarding.Request fromRequest(HttpServletRequest request) {
        UploaderOnboarding.Request r = new UploaderOnboarding.Request();
        r.email = param(request, "email");
        r.firstName = param(request, "firstName");
        r.lastName = param(request, "lastName");
        // groupId: "new", or "existing" with the group's id in existingGroupId (a choice in the
        // select alone counts too). Nothing chosen: -1.
        String g = param(request, "groupId");
        String existing = param(request, "existingGroupId");
        if ("new".equals(g)) {
            r.groupId = 0;
        } else {
            try {
                r.groupId = existing.isEmpty() ? -1 : Integer.parseInt(existing);
            } catch (NumberFormatException e) {
                r.groupId = -1;
            }
            if (r.groupId == 0) r.groupId = -1;
        }
        r.newGroupName = param(request, "newGroupName");
        r.newGroupAbbrev = param(request, "newGroupAbbrev");
        r.uploadImages = request.getParameter("uploadImages") != null;
        r.move = request.getParameter("move") != null;
        return r.normalized();
    }

    private static String param(HttpServletRequest request, String name) {
        String v = request.getParameter(name);
        return v == null ? "" : v;
    }

    // ------------------------------------------------------------------ pages

    static final String INTRO =
        "<p>Use this page when someone asks to upload specimen data. In one step it finds the person's AntWeb login"
        + " (or creates one and makes a link for them to choose a password), puts the login in an <b>upload group</b>"
        + " (an existing one, or a new one made here), gives it the right to upload specimens, and shows a welcome"
        + " email with what to do next. The email is sent only when you click Send.</p>"
        + "<p>An <b>upload group</b> is the institution, lab or personal collection that owns specimen records on AntWeb."
        + " Each specimen record belongs to one upload group, and <b>an upload replaces all specimens of the uploader's"
        + " upload group</b>. Give two people the same upload group only when they upload one shared data set.</p>";

    static final String SESSION_CHANGED = "Your session changed (AntWeb restarted or you logged in again)."
        + " Nothing was done. Check the form and press Add uploader again.";
    static final String SESSION_CHANGED_SEND = "Your session changed (AntWeb restarted or you logged in again)."
        + " The email was not sent. Check it and press Send again.";

    static String noticeHtml(String notice) {
        return notice == null ? "" : "<div id=\"session-changed\" style=\"border:2px solid #c80;background:#fff6e0;padding:8px 12px;margin:10px 0;max-width:760px\"><b>"
            + Html.esc(notice) + "</b></div>\n";
    }

    static String formHtml(HttpServletRequest request, UploaderOnboarding.Request in,
                           List<UploaderOnboarding.GroupRow> groups, List<String> problems) {
        return formHtml(request, in, groups, problems, null);
    }

    static String formHtml(HttpServletRequest request, UploaderOnboarding.Request in,
                           List<UploaderOnboarding.GroupRow> groups, List<String> problems, String notice) {
        String app = AntwebProps.getDomainApp();
        StringBuilder b = new StringBuilder();
        b.append("<h1>Add an uploader</h1>\n").append(noticeHtml(notice)).append(INTRO);
        if (!problems.isEmpty()) {
            b.append("<div style=\"color:#b00;font-weight:bold\"><p>Nothing was changed:</p><ul>");
            for (String p : problems) b.append("<li>").append(Html.esc(p)).append("</li>");
            b.append("</ul></div>\n");
        }
        b.append("<form method=\"POST\" action=\"").append(app).append("/addUploader.do\">").append(Csrf.field(request))
         .append("<input type=\"hidden\" name=\"step\" value=\"save\">\n");

        b.append("<h3>1. The person</h3>\n")
         .append("<p><label><b>Email address</b><br><input type=\"text\" name=\"email\" size=\"40\" maxlength=\"128\" value=\"")
         .append(Html.escAttr(in.email)).append("\"></label><br><span style=\"color:#666\">If they already have an AntWeb account,"
            + " their login is found by this email address (or enter its username). If not, a login is created with the email"
            + " address as username, and the welcome email holds a one-time link to choose a password.</span></p>\n")
         .append("<p><label>First name <input type=\"text\" name=\"firstName\" size=\"20\" maxlength=\"64\" value=\"")
         .append(Html.escAttr(in.firstName)).append("\"></label> &nbsp; <label>Last name <input type=\"text\" name=\"lastName\" size=\"20\" maxlength=\"64\" value=\"")
         .append(Html.escAttr(in.lastName)).append("\"></label><br><span style=\"color:#666\">For a new login, and for an"
            + " existing login that has no name yet. Shown on their curator page.</span></p>\n");

        b.append("<h3>2. Their upload group</h3>\n")
         .append("<p><label><input type=\"radio\" name=\"groupId\" value=\"existing\" id=\"ug-existing\"")
         .append(in.groupId > 0 ? " checked" : "").append(" onclick=\"document.getElementById('ug-select').focus()\"> <b>Existing upload group</b></label><br>\n")
         .append("<select id=\"ug-select\" name=\"existingGroupId\" aria-label=\"Existing upload group\" onchange=\"document.getElementById('ug-existing').checked=true\">")
         .append("<option value=\"\">Choose an upload group</option>");
        for (UploaderOnboarding.GroupRow g : groups) {
            b.append("<option value=\"").append(g.id).append("\"").append(g.id == in.groupId ? " selected" : "").append(">")
             .append(Html.esc(g.label())).append("</option>");
        }
        b.append("</select><br><span style=\"color:#666\">For example when a second person joins a lab that already uploads.</span></p>\n");
        b.append("<p><label><input type=\"radio\" name=\"groupId\" value=\"new\"").append(in.groupId == 0 ? " checked" : "")
         .append("> <b>New upload group</b></label><br>\n")
         .append("<label>Full name <input type=\"text\" name=\"newGroupName\" size=\"40\" maxlength=\"100\" value=\"")
         .append(Html.escAttr(in.newGroupName)).append("\"></label><br><span style=\"color:#666\">The institution, the lab, or"
            + " &quot;Curator&quot; and the person's name for a personal collection; shown on specimen pages and in the list of"
            + " contributors. Examples: <code>Archbold Biological Station</code>, <code>Economo Ant Lab</code>, <code>Curator Kiko Gomez</code>.</span><br>\n")
         .append("<label>Short name <input type=\"text\" name=\"newGroupAbbrev\" size=\"12\" maxlength=\"20\" value=\"")
         .append(Html.escAttr(in.newGroupAbbrev)).append("\"></label><br><span style=\"color:#666\">Use the institution's code from"
            + " <a href=\"https://antcat.org/institutions\" target=\"_blank\" rel=\"noopener\">AntCat's list of institutions</a> when"
            + " there is one; otherwise the institution's own abbreviation, or the person's surname for a personal collection."
            + " Examples: <code>ABS</code>, <code>MEM</code>, <code>OIST</code>, <code>Gomez</code>. Shown in upload reports and summaries.</span></p>\n");

        b.append("<h3>3. Rights</h3>\n")
         .append("<p>The login gets the right to upload specimens.<br>")
         .append("<label><input type=\"checkbox\" name=\"uploadImages\"").append(in.uploadImages ? " checked" : "")
         .append("> Can also upload specimen images</label><br>")
         .append("<label><input type=\"checkbox\" name=\"move\"").append(in.move ? " checked" : "")
         .append("> Move them to this upload group if they are in another one</label>"
            + " <span style=\"color:#666\">(their uploads then replace the specimens of this upload group, no longer the old one)</span></p>\n");

        b.append("<p><input type=\"submit\" value=\"Add uploader\"> &nbsp; <a href=\"").append(app).append("/curate.do\">Cancel</a></p>\n")
         .append("</form>\n")
         .append("<p style=\"color:#666\">Projects, countries, \"Upload as\" and administrator rights are not needed for uploading;"
            + " change them in the <a href=\"").append(app).append("/viewLogins.do\">Login Manager</a> if ever needed."
            + " All upload groups: <a href=\"").append(app).append("/viewGroups.do\">Upload groups</a>.</p>\n");
        return b.toString();
    }

    static String summaryHtml(HttpServletRequest request, Done done, String notice) {
        String app = AntwebProps.getDomainApp();
        StringBuilder b = new StringBuilder();
        b.append("<h1>Add an uploader: done</h1>\n").append(noticeHtml(notice)).append("<ul>");
        for (String l : done.lines) b.append("<li>").append(Html.esc(l)).append("</li>");
        b.append("</ul>\n<p><a href=\"").append(app).append("/viewLogin.do?id=").append(done.loginId).append("\">Open the login</a>")
         .append(" | <a href=\"").append(app).append("/addUploader.do\">Add another uploader</a>")
         .append(" | <a href=\"").append(app).append("/curate.do\">Curator Tools</a></p>\n");

        b.append("<h2>Welcome email</h2>\n");
        boolean canSend = done.email != null && done.email.contains("@");
        if (done.sent != null) {
            b.append("<p><b>").append(Html.esc(done.sent)).append("</b></p>\n");
        } else if (!canSend) {
            b.append("<p><b>This login has no email address, so AntWeb cannot send the email.</b> Add the address on the login"
                + " (Open the login) or copy the text below into your own email.</p>\n");
        } else {
            b.append("<p>Check the text, then send it. It goes from antweb@antweb.org to <b>").append(Html.esc(done.email)).append("</b>.");
            if (done.hasLink) b.append(" It holds the person's one-time link to choose a password: send it only to them.");
            b.append("</p>\n<form method=\"POST\" action=\"").append(app).append("/addUploader.do\">").append(Csrf.field(request))
             .append("<input type=\"hidden\" name=\"step\" value=\"send\"><input type=\"submit\" value=\"Send this email to ")
             .append(Html.escAttr(done.email)).append("\"></form>\n");
        }
        b.append("<div style=\"border:1px solid #ccc;padding:8px 12px;margin:10px 0;max-width:760px\">")
         .append("<p><b>To:</b> ").append(Html.esc(canSend ? done.email : "(no email address)")).append("<br><b>Subject:</b> ").append(Html.esc(done.subject)).append("</p>")
         .append(done.body).append("</div>\n");
        return b.toString();
    }
}

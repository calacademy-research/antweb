package org.calacademy.antweb.curate.group;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 *   GET  /addUploader.do?done=1              summary of what was done and the welcome email
 *                                            (To, Subject, plain-text body, all editable), signed
 *                                            with the administrator's name
 *   POST /addUploader.do step=send           sends the edited welcome email (sendProblem() checks:
 *                                            only to the email address of the login just added, as
 *                                            stored in the database now; the one-time link still in
 *                                            the text; length limits), from antweb@antweb.org with
 *                                            Reply-To and a Bcc copy to the administrator. Then
 *                                            redirects to the summary, which says when it was sent.
 *
 * 2026-10-21 to 2026-10-29 the page only showed a draft to copy (each new uploader needs details
 * only the administrator knows); since 2026-10-29 (Brian Fisher) the administrator edits it on the
 * page and presses Send, or still copies it. The mailto: link was dropped (a long body made it do
 * nothing in some browsers and email programs). The set-password link lives only in this
 * administrator's session until the page is left for a new uploader, and is never logged. Every
 * change and every sent welcome email is written to admin.log (PasswordResetMgr.logEvent).
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
        /** The one-time link in body (null without one): it must still be in the text that is sent. */
        String link;
        /** Set once sent: "Sent to ... at ...". */
        String sent;
    }

    static final int MAX_SUBJECT = 200;
    static final int MAX_BODY = 20000;
    private static final DateTimeFormatter SENT_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US);

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
            notice = SESSION_CHANGED;
            post = false;
        }

        List<UploaderOnboarding.GroupRow> groups = new ArrayList<>();
        UploaderOnboarding.Request in = fromRequest(request);
        List<String> problems = new ArrayList<>();

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("AddUploaderAction.execute()");
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);

            if (post && "send".equals(step)) {
                Done done = (Done) session.getAttribute(DONE);
                String to = param(request, "to").trim();
                String subject = param(request, "subject").trim();
                String body = param(request, "body").replace("\r\n", "\n");
                int loginId = intParam(request, "loginId");
                String problem;
                UploaderOnboarding.LoginRow person = null;
                if (done == null || done.loginId != loginId || loginId <= 0) {
                    problem = NOT_CURRENT;
                } else if (done.sent != null) {
                    problem = "This welcome email was sent already. " + done.sent;
                } else {
                    person = UploaderOnboarding.loginById(connection, loginId);
                    problem = person == null ? "Not sent: login " + loginId + " does not exist any more."
                        : sendProblem(to, person.email, subject, body, done.link);
                }
                if (problem == null) {
                    String adminEmail = validEmail(admin.getEmail()) ? admin.getEmail().trim() : null;
                    String reason = Emailer.sendPersonal(person.email.trim(), adminEmail, subject, body, adminEmail);
                    if (reason == null) {
                        String when = ZonedDateTime.now(ZoneId.of("America/Los_Angeles")).format(SENT_AT);
                        done.sent = sentNote(person, when, adminEmail);
                        done.subject = subject;
                        done.body = body;
                        done.email = person.email.trim();
                        session.setAttribute(DONE, done);
                        // No body in the log: it holds the one-time link.
                        PasswordResetMgr.logEvent("add uploader: welcome email sent by login:" + admin.getId() + " to login:" + person.id
                            + " <" + person.email.trim() + "> subject:" + subject + (adminEmail == null ? " (no copy: admin has no email)" : " (copy to admin)"));
                        s_log.warn("execute() welcome email sent to login:" + person.id + " by " + admin.getId());
                        return new ActionForward("/addUploader.do?done=1", true);
                    }
                    problem = "Not sent: " + reason + ". Copy the text into your own email instead.";
                    PasswordResetMgr.logEvent("add uploader: welcome email to login:" + loginId + " NOT sent by login:" + admin.getId() + ": " + reason);
                } else {
                    s_log.warn("execute() welcome email refused for login:" + loginId + " by " + admin.getId());
                }
                // Shown again with what the administrator typed, and why it was not sent.
                Done view = copyOf(done, loginId, to, subject, body);
                request.setAttribute(PAGE, summaryHtml(request, view, null, problem));
                return mapping.findForward("success");
            }

            if (post && "save".equals(step)) {
                UploaderOnboarding.Result r = UploaderOnboarding.apply(connection, in);
                if (r.ok()) {
                    Done done = new Done();
                    done.lines.addAll(r.done);
                    String link = null;
                    if (r.needsPasswordLink) {
                        // The login and group are saved (committed) by now: a failure here must not say "nothing was changed".
                        try {
                            link = PasswordResetMgr.newInviteLink(connection, r.login.id, admin.getId());
                            done.lines.add("Made a one-time link (" + days() + " days) for " + r.login.name
                                + " to choose a password; it is in the draft welcome email below.");
                        } catch (SQLException e) {
                            s_log.error("execute() invite link for login:" + r.login.id + " e:" + e);
                            PasswordResetMgr.logEvent("add uploader: invite link for login:" + r.login.id + " could not be made: " + e.getMessage());
                            done.lines.add(INVITE_LINK_FAILED);
                        }
                    }
                    done.email = r.login.email;
                    done.loginId = r.login.id;
                    done.loginName = r.login.name;
                    done.hasLink = link != null;
                    done.link = link;
                    done.subject = UploaderOnboarding.welcomeSubject();
                    done.body = UploaderOnboarding.welcomeBody(AntwebProps.getSecureDomainApp(), r.login, r.group, link, days(),
                        UploaderOnboarding.signature(admin.getFirstName(), admin.getLastName()));
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

        Done done = "1".equals(request.getParameter("done")) ? (Done) session.getAttribute(DONE) : null;
        if (!post && done == null && !sessionChanged) session.removeAttribute(DONE);  // a fresh form: forget the last link
        if (sessionChanged && done == null && "send".equals(step)) {
            // A Send from the old session: nothing is sent; the text typed is shown again to copy.
            Done view = copyOf(null, intParam(request, "loginId"), param(request, "to").trim(), param(request, "subject").trim(),
                param(request, "body").replace("\r\n", "\n"));
            request.setAttribute(PAGE, summaryHtml(request, view, SESSION_CHANGED_SEND, null));
            return mapping.findForward("success");
        }
        if (sessionChanged && done == null) notice = SESSION_CHANGED;
        request.setAttribute(PAGE, done != null
            ? summaryHtml(request, done, notice, null)
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

    private static int intParam(HttpServletRequest request, String name) {
        try {
            return Integer.parseInt(param(request, name).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean validEmail(String s) {
        return s != null && org.calacademy.antweb.util.LoginNames.isValidEmail(s.trim());
    }

    /** A view of the draft with what the administrator typed (done null or for another login: not sendable). */
    private static Done copyOf(Done done, int loginId, String to, String subject, String body) {
        Done v = new Done();
        boolean same = done != null && done.loginId == loginId;
        if (same) {
            v.lines.addAll(done.lines);
            v.loginName = done.loginName;
            v.hasLink = done.hasLink;
            v.link = done.link;
            v.sent = done.sent;
        } else {
            v.hasLink = body != null && body.contains("resetPassword.do");
        }
        v.loginId = same ? loginId : 0;
        v.email = to;
        v.subject = subject;
        v.body = body;
        return v;
    }

    // ------------------------------------------------------------------ sending

    public static final String NOT_CURRENT = "Not sent: this welcome email belongs to an earlier Add an uploader (or your session"
        + " changed). Copy the text into your own email, or add the uploader again.";

    static final String SESSION_CHANGED_SEND = "Your session changed (AntWeb restarted or you logged in again)."
        + " Nothing was sent. Copy the text below into your own email, or add the uploader again.";

    /**
     * Why the edited welcome email may not be sent, or null. loginEmail is the email address of the
     * login just added, read from the database again; link is the one-time set-password link of the
     * draft (null when there is none). No message contains the link.
     */
    public static String sendProblem(String to, String loginEmail, String subject, String body, String link) {
        if (!validEmail(loginEmail)) {
            return "Not sent: this login has no valid email address. Add it on the login (Open the login), or copy the text into your own email.";
        }
        String t = to == null ? "" : to.trim();
        if (!t.equalsIgnoreCase(loginEmail.trim())) {
            return "Not sent: the To address must be the email address of the login just added (" + loginEmail.trim()
                + "). To use another address, change it on the login first.";
        }
        String s = subject == null ? "" : subject.trim();
        if (s.isEmpty()) return "Not sent: enter a subject.";
        if (s.length() > MAX_SUBJECT || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "Not sent: the subject must be one line of at most " + MAX_SUBJECT + " characters.";
        }
        if (body == null || body.trim().isEmpty()) return "Not sent: the email text is empty.";
        if (body.length() > MAX_BODY) return "Not sent: the email text is longer than " + MAX_BODY + " characters.";
        if (link != null && !body.contains(link)) {
            return "Not sent: the one-time link to choose a password is no longer in the text, and without it the person cannot log in."
                + " Put it back (Show the original text), then press Send again.";
        }
        return null;
    }

    /** The confirmation shown after sending. */
    public static String sentNote(UploaderOnboarding.LoginRow person, String when, String adminEmail) {
        String name = person.displayName();
        String to = person.email.trim();
        return "Sent to " + (name.equalsIgnoreCase(to) ? to : name + " <" + to + ">") + " at " + when
            + (adminEmail == null ? "; no copy to you: your login has no email address." : "; a copy went to you (" + adminEmail + ").");
    }

    // ------------------------------------------------------------------ pages

    static final String INTRO =
        "<p>Use this page when someone asks to upload specimen data. In one step it finds the person's AntWeb login"
        + " (or creates one and makes a link for them to choose a password), puts the login in an <b>upload group</b>"
        + " (an existing one, or a new one made here), gives it the right to upload specimens, and drafts a welcome"
        + " email with what to do next, for you to edit and send from this page (or copy into your own email).</p>"
        + "<p>An <b>upload group</b> is the institution, lab or personal collection that owns specimen records on AntWeb."
        + " Each specimen record belongs to one upload group, and <b>an upload replaces all specimens of the uploader's"
        + " upload group</b>. Give two people the same upload group only when they upload one shared data set.</p>";

    /** Shown when the login and group were saved but the one-time password link could not be made. */
    public static final String INVITE_LINK_FAILED = "The login and upload group were saved, but the one-time link to choose a password"
        + " could not be made. Make one in the Login Manager (open the login, \"Save and make a set-password link\"), or ask them to use Forgot password.";

    static final String SESSION_CHANGED = "Your session changed (AntWeb restarted or you logged in again)."
        + " Nothing was done. Check the form and press Add uploader again.";

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

    static String summaryHtml(HttpServletRequest request, Done done, String notice, String problem) {
        String app = AntwebProps.getDomainApp();
        StringBuilder b = new StringBuilder();
        b.append("<h1>Add an uploader: done</h1>\n").append(noticeHtml(notice));
        if (!done.lines.isEmpty()) {
            b.append("<ul>");
            for (String l : done.lines) b.append("<li>").append(Html.esc(l)).append("</li>");
            b.append("</ul>\n");
        }
        b.append("<p>");
        if (done.loginId > 0) b.append("<a href=\"").append(app).append("/viewLogin.do?id=").append(done.loginId).append("\">Open the login</a> | ");
        b.append("<a href=\"").append(app).append("/addUploader.do\">Add another uploader</a>")
         .append(" | <a href=\"").append(app).append("/curate.do\">Curator Tools</a></p>\n");

        // Sendable: this session's draft for this login, not sent yet.
        boolean sendable = done.loginId > 0 && done.sent == null && notice == null;
        b.append(draftHtml(app, done.email, done.subject, done.body, done.hasLink, done.loginId,
            sendable ? Csrf.field(request) : null, done.sent, problem));
        return b.toString();
    }

    /** The Copy button's script: the clipboard where the browser allows it, else the text is selected. */
    static final String COPY_SCRIPT = "<script>\n"
        + "function awCopyDraft(btn) {\n"
        + "  var t = document.getElementById('draft-body'), note = document.getElementById('draft-copied');\n"
        + "  function selectAll() { t.focus(); t.select(); t.setSelectionRange(0, t.value.length); }\n"
        + "  function say(text) { note.textContent = text; }\n"
        + "  function fallback() {\n"
        + "    selectAll();\n"
        + "    var ok = false;\n"
        + "    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }\n"
        + "    say(ok ? 'Copied.' : 'Selected: press Ctrl+C (Cmd+C on a Mac) to copy.');\n"
        + "  }\n"
        + "  if (navigator.clipboard && window.isSecureContext) {\n"
        + "    navigator.clipboard.writeText(t.value).then(function () { say('Copied.'); }, fallback);\n"
        + "  } else {\n"
        + "    fallback();\n"
        + "  }\n"
        + "}\n"
        + "</script>\n";

    /**
     * The "Welcome email" box of the summary page: To, Subject and the plain-text body, editable,
     * with Send (a POST with the form token, step=send) and Copy. Every value is escaped.
     * csrfField null: not sendable (sent already, or a stale session): read-only, Copy only.
     * sent: the confirmation ("Sent to ... at ..."), or null. problem: why the last Send was refused, or null.
     * to may be null or have no @ (a login without an email address): then no Send.
     */
    public static String draftHtml(String app, String to, String subject, String body, boolean hasLink, int loginId,
                                   String csrfField, String sent, String problem) {
        boolean hasTo = to != null && to.contains("@");
        boolean sendable = csrfField != null && hasTo && sent == null && loginId > 0;
        String ro = sendable ? "" : " readonly";
        StringBuilder b = new StringBuilder();
        b.append("<h2>Welcome email</h2>\n");
        if (sent != null) {
            b.append("<div id=\"draft-sent\" role=\"status\" style=\"border:2px solid #080;background:#eefbea;padding:8px 12px;margin:10px 0;max-width:760px\"><b>")
             .append(Html.esc(sent)).append("</b></div>\n");
        }
        if (problem != null) {
            b.append("<div id=\"draft-problem\" role=\"alert\" style=\"border:2px solid #b00;background:#fff0f0;padding:8px 12px;margin:10px 0;max-width:760px;color:#b00\"><b>")
             .append(Html.esc(problem)).append("</b>");
            if (loginId > 0) b.append(" <a href=\"").append(app).append("/addUploader.do?done=1\">Show the original text</a>");
            b.append("</div>\n");
        }
        if (sendable) {
            b.append("<p>Edit the text if this person needs to know more, then press <b>Send</b>. AntWeb sends it from")
             .append(" antweb@antweb.org; replies go to you, and you get a copy. Or Copy it into your own email.</p>\n");
        } else if (sent == null) {
            b.append("<p>Copy the text into your own email and send it to the address below.</p>\n");
        }
        if (hasLink && sent == null) {
            b.append("<p id=\"draft-link-warning\" style=\"color:#b00\"><b>The text holds the person's one-time link to choose a password:")
             .append(" send it only to them, and do not share a screenshot or copy of this page.</b></p>\n");
        }
        b.append("<div id=\"welcome-draft\" style=\"border:1px solid #ccc;padding:8px 12px;margin:10px 0;max-width:760px\">\n");
        if (sendable) {
            b.append("<form method=\"POST\" action=\"").append(app).append("/addUploader.do\" onsubmit=\"document.getElementById('draft-send').disabled=true\">")
             .append(csrfField)
             .append("<input type=\"hidden\" name=\"step\" value=\"send\">")
             .append("<input type=\"hidden\" name=\"loginId\" value=\"").append(loginId).append("\">\n");
        }
        b.append("<p><label><b>To:</b> <input type=\"text\" id=\"draft-to\" name=\"to\"").append(ro).append(" size=\"50\" maxlength=\"128\" value=\"")
         .append(Html.escAttr(hasTo ? to : "")).append("\"></label>");
        if (!hasTo) b.append(" <b>This login has no email address.</b> Add it on the login (Open the login), or enter it in your email.");
        b.append("<br>\n<label><b>Subject:</b> <input type=\"text\" id=\"draft-subject\" name=\"subject\"").append(ro)
         .append(" size=\"50\" maxlength=\"").append(MAX_SUBJECT).append("\" value=\"")
         .append(Html.escAttr(subject)).append("\"></label></p>\n")
         .append("<textarea id=\"draft-body\" name=\"body\"").append(ro).append(" rows=\"24\" maxlength=\"").append(MAX_BODY)
         .append("\" style=\"width:100%;box-sizing:border-box;font-family:inherit\" aria-label=\"Email text\">")
         .append(Html.esc(body)).append("</textarea>\n")
         .append("<p>");
        if (sendable) b.append("<input type=\"submit\" id=\"draft-send\" value=\"Send\"> &nbsp; ");
        b.append("<button type=\"button\" id=\"draft-copy\" onclick=\"awCopyDraft(this)\">Copy</button>")
         .append(" <span id=\"draft-copied\" role=\"status\"></span></p>\n");
        if (sendable) b.append("</form>\n");
        b.append("</div>\n").append(COPY_SCRIPT);
        return b.toString();
    }
}

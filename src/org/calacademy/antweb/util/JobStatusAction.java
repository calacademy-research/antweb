package org.calacademy.antweb.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import org.calacademy.antweb.AntwebAction;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;

/**
 * The status page of a job started from a page and run in the background (BackgroundJobs):
 * jobStatus.do?id=N. Without id: the recent jobs ("Upload status" on the Curate page).
 *
 * Curators see the uploads of their own group (and their own uploads); administrators see every
 * job, admin data jobs included. While a job is waiting or running the page reloads itself every
 * 5 seconds (HTTP "Refresh" header; no script). Read-only.
 */
public final class JobStatusAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(JobStatusAction.class);

    public static final int REFRESH_SECONDS = 5;
    static final int LIST_LIMIT = 30;

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
        ActionForward c = Check.curator(request, mapping); if (c != null) return c;

        Login accessLogin = LoginMgr.getAccessLogin(request);
        boolean isAdmin = LoginMgr.isAdmin(accessLogin);
        Group group = accessLogin.getGroup();
        int groupId = group == null ? -1 : group.getId();

        BackgroundJobDb.setDataSource(getDataSource(request, "conPool"));
        String domainApp = AntwebProps.getDomainApp();

        String idParam = request.getParameter("id");
        StringBuilder html = new StringBuilder();
        if (idParam == null || idParam.isBlank()) {
            List<BackgroundJobs.Job> jobs = BackgroundJobs.get().recent(isAdmin ? 0 : groupId, LIST_LIMIT);
            boolean anyActive = false;
            html.append("<h1>Upload status</h1>");
            html.append(navigation(domainApp));
            html.append("<p>").append(isAdmin ? "Uploads and data jobs started from AntWeb pages, newest first."
                : "The specimen uploads of your group, newest first.").append("</p>");
            html.append(listHtml(jobs, accessLogin, isAdmin, groupId, domainApp));
            for (BackgroundJobs.Job j : jobs) if (j.isActive() && mayView(j, accessLogin, isAdmin, groupId)) anyActive = true;
            if (anyActive) response.setHeader("Refresh", String.valueOf(REFRESH_SECONDS));
            request.setAttribute("html", html.toString());
            return mapping.findForward("success");
        }

        int id;
        try {
            id = Integer.parseInt(idParam.trim());
        } catch (NumberFormatException e) {
            id = 0;
        }
        BackgroundJobs.Job job = id > 0 ? BackgroundJobs.get().get(id) : null;
        if (job == null || !mayView(job, accessLogin, isAdmin, groupId)) {
            request.setAttribute("message", "Not available.");
            return mapping.findForward("message");
        }
        if (job.isActive()) response.setHeader("Refresh", String.valueOf(REFRESH_SECONDS));
        boolean already = "1".equals(request.getParameter("already"));
        html.append(jobHtml(job, already, isAdmin, domainApp));
        request.setAttribute("html", html.toString());
        return mapping.findForward("success");
    }

    /** Curators: uploads of their group, or their own. Admins: everything. */
    public static boolean mayView(BackgroundJobs.Job job, Login login, boolean isAdmin, int groupId) {
        if (job == null || login == null) return false;
        if (isAdmin) return true;
        if (!BackgroundJobs.UPLOAD.equals(job.getKind())) return false;
        return (groupId > 0 && job.getGroupId() == groupId) || job.getLoginId() == login.getId();
    }

    static String navigation(String domainApp) {
        return "<p><a href=\"" + domainApp + "\">Home</a> | <a href=\"" + domainApp + "/curate.do\">Curator Tools</a>"
            + " | <a href=\"" + domainApp + "/jobStatus.do\">Upload status</a></p>";
    }

    /** The word shown for a status. */
    public static String statusWord(BackgroundJobs.Status status) {
        switch (status) {
            case queued: return "Waiting";
            case running: return "Running";
            case finished: return "Finished";
            case failed: return "Did not complete";
            default: return "Interrupted";
        }
    }

    static String time(Date d) {
        return d == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /** The page of one job. Upload results are plain text (escaped here); admin job results are AntWeb's own HTML. */
    public static String jobHtml(BackgroundJobs.Job job, boolean already, boolean isAdmin, String domainApp) {
        boolean isUpload = BackgroundJobs.UPLOAD.equals(job.getKind());
        BackgroundJobs.Status status = job.getStatus();
        StringBuilder h = new StringBuilder();
        h.append("<h1>").append(isUpload ? "Specimen upload" : "Data job").append(" ").append(job.getId()).append("</h1>");
        h.append(navigation(domainApp));

        if (already) {
            h.append("<p style=\"color:#a00;font-weight:bold\">")
             .append(isUpload
                 ? "You already have an upload waiting or running (below). The file you just sent was not used: "
                   + "a group's uploads run one after the other. When this upload has finished, upload again if you need to."
                 : "This job was already waiting or running (below); it was not started a second time.")
             .append("</p>");
        }

        h.append("<p style=\"font-size:1.2em\">Status: <b><span id=\"job-status\" data-status=\"").append(status.name()).append("\">")
         .append(statusWord(status)).append("</span></b></p>");

        switch (status) {
            case queued:
                h.append("<p>").append(isUpload ? "Your file has been received and is waiting its turn: uploads run one at a time."
                    : "The job is waiting its turn: jobs run one at a time.").append("</p>");
                h.append("<p>Now: <span id=\"job-step\">").append(Html.esc(job.getStep())).append("</span></p>");
                break;
            case running:
                h.append("<p>").append(isUpload ? "Your file has been received and is being processed. Large files take several minutes."
                    : "The job is running.").append("</p>");
                h.append("<p>Now: <span id=\"job-step\">").append(Html.esc(job.getStep())).append("</span></p>");
                break;
            case finished:
                h.append("<p><b>").append(isUpload ? "Upload complete." : "Finished.").append("</b></p>");
                break;
            case failed:
                h.append("<p><b>").append(isUpload ? "The upload did not complete." : "The job did not complete.").append("</b></p>");
                break;
            default:
                h.append("<p><b>").append(Html.esc(BackgroundJobs.INTERRUPTED_STEP)).append("</b> ")
                 .append(isUpload
                     ? "An upload runs as one database transaction, so an upload that did not finish is undone: "
                       + "the group's specimens are as they were before. Please upload the file again."
                     : "Run it again if it is still needed.")
                 .append("</p>");
        }

        if (job.isActive()) {
            h.append("<p>This page updates itself every ").append(REFRESH_SECONDS).append(" seconds (<a href=\"")
             .append(domainApp).append("/jobStatus.do?id=").append(job.getId()).append("\">update now</a>). ")
             .append("You may close it: the ").append(isUpload ? "upload" : "job")
             .append(" continues on the server, and you can come back to it from the Curate page (Upload status).</p>");
        }

        String result = job.getResult();
        if (result != null && !result.isEmpty() && !job.isActive()) {
            h.append("<div id=\"job-result\" style=\"margin:1em 0\">")
             .append(isUpload ? Html.esc(result) : result)
             .append("</div>");
        }
        if (job.getResultLink() != null) {
            String text = job.getResultLinkText() == null ? "Report" : job.getResultLinkText();
            h.append("<p style=\"font-size:1.2em\"><a id=\"job-report\" href=\"").append(Html.escAttr(job.getResultLink())).append("\">")
             .append(Html.esc(text)).append("</a></p>");
        }

        h.append("<table class=\"job_details\" style=\"margin-top:1em\">");
        row(h, "What", Html.esc(job.getTitle()));
        row(h, "Started by", Html.esc(job.getLoginName()));
        row(h, "Received", time(job.getCreated()));
        if (job.getStarted() != null) row(h, "Started", time(job.getStarted()));
        if (job.getFinished() != null) row(h, "Ended", time(job.getFinished()));
        h.append("</table>");
        if (isUpload && isAdmin) {
            h.append("<p><a href=\"").append(domainApp).append("/listSpecimenUploads.do?groupId=").append(job.getGroupId())
             .append("\">Upload reports of this group</a></p>");
        }
        return h.toString();
    }

    private static void row(StringBuilder h, String label, String value) {
        h.append("<tr><td style=\"padding-right:1em\"><b>").append(label).append("</b></td><td>").append(value == null ? "" : value).append("</td></tr>");
    }

    static String listHtml(List<BackgroundJobs.Job> jobs, Login login, boolean isAdmin, int groupId, String domainApp) {
        StringBuilder h = new StringBuilder();
        int shown = 0;
        h.append("<table class=\"job_list\" cellpadding=\"4\"><tr><th align=left>Job</th><th align=left>What</th>")
         .append("<th align=left>Started by</th><th align=left>Status</th><th align=left>Received</th><th align=left>Ended</th></tr>");
        for (BackgroundJobs.Job j : jobs) {
            if (!mayView(j, login, isAdmin, groupId)) continue;
            shown++;
            h.append("<tr><td><a href=\"").append(domainApp).append("/jobStatus.do?id=").append(j.getId()).append("\">")
             .append(j.getId()).append("</a></td><td>").append(Html.esc(j.getTitle())).append("</td><td>")
             .append(Html.esc(j.getLoginName())).append("</td><td>").append(statusWord(j.getStatus())).append("</td><td>")
             .append(time(j.getCreated())).append("</td><td>").append(time(j.getFinished())).append("</td></tr>");
        }
        h.append("</table>");
        if (shown == 0) return "<p>No uploads yet.</p>";
        return h.toString();
    }
}

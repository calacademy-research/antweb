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
            html.append(navigation(domainApp));
            html.append("<h1>Upload status</h1>");
            html.append("<div class=\"aw-intro\"><p>").append(isAdmin ? "Uploads and data jobs started from AntWeb pages, newest first."
                : "The specimen uploads of your group, newest first.")
             .append(" Open a job to follow it or to read its result; a job that is waiting or running updates itself.</p></div>");
            html.append("<div class=\"aw-card\" id=\"job-list-card\">").append(listHtml(jobs, accessLogin, isAdmin, groupId, domainApp)).append("</div>");
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
        return "<p class=\"aw-crumbs\"><a href=\"" + domainApp + "\">Home</a> | <a href=\"" + domainApp + "/curate.do\">Curator Tools</a>"
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
        h.append(navigation(domainApp));
        h.append("<h1>").append(isUpload ? "Specimen upload" : "Data job").append(" ").append(job.getId()).append("</h1>");
        h.append("<div class=\"aw-intro\"><p>").append(isUpload
            ? "The progress and the result of a specimen upload: the server works through the file in the background."
            : "The progress and the result of a job started from an AntWeb page: it runs in the background on the server.")
         .append("</p></div>");

        if (already) {
            h.append("<p class=\"aw-warn\" id=\"job-already\">")
             .append(isUpload
                 ? "You already have an upload waiting or running (below). The file you just sent was not used: "
                   + "a group's uploads run one after the other. When this upload has finished, upload again if you need to."
                 : "This job was already waiting or running (below); it was not started a second time.")
             .append("</p>");
        }

        h.append("<div class=\"aw-card\" id=\"job-status-card\">");
        h.append("<p>Status: <b><span id=\"job-status\" data-status=\"").append(status.name()).append("\" class=\"aw-status aw-status-")
         .append(status.name()).append("\">").append(statusWord(status)).append("</span></b></p>");

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
            h.append("<div id=\"job-result\" class=\"aw-sub\">")
             .append(isUpload ? Html.esc(result) : JobSummary.display(result))   // never a whole page inside the job page
             .append("</div>");
        }
        if (job.getResultLink() != null) {
            String text = job.getResultLinkText() == null ? "Report" : job.getResultLinkText();
            h.append("<p class=\"aw-sub\"><a id=\"job-report\" class=\"aw-btn\" href=\"").append(Html.escAttr(job.getResultLink())).append("\">")
             .append(Html.esc(text)).append("</a></p>");
        }

        h.append("</div>");
        h.append("<div class=\"aw-card\" id=\"job-details-card\"><h2>Details</h2>");
        h.append("<table class=\"job_details aw-kv\">");
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
        h.append("</div>");
        return h.toString();
    }

    private static void row(StringBuilder h, String label, String value) {
        h.append("<tr><th scope=\"row\">").append(label).append("</th><td>").append(value == null ? "" : value).append("</td></tr>");
    }

    static String listHtml(List<BackgroundJobs.Job> jobs, Login login, boolean isAdmin, int groupId, String domainApp) {
        StringBuilder h = new StringBuilder();
        int shown = 0;
        h.append("<table class=\"job_list\"><tr><th>Job</th><th>What</th>")
         .append("<th>Started by</th><th>Status</th><th>Received</th><th>Ended</th></tr>");
        for (BackgroundJobs.Job j : jobs) {
            if (!mayView(j, login, isAdmin, groupId)) continue;
            shown++;
            h.append("<tr><td><a href=\"").append(domainApp).append("/jobStatus.do?id=").append(j.getId()).append("\">")
             .append(j.getId()).append("</a></td><td>").append(Html.esc(j.getTitle())).append("</td><td>")
             .append(Html.esc(j.getLoginName())).append("</td><td><span class=\"aw-status aw-status-").append(j.getStatus().name()).append("\">")
             .append(statusWord(j.getStatus())).append("</span></td><td>")
             .append(time(j.getCreated())).append("</td><td>").append(time(j.getFinished())).append("</td></tr>");
        }
        h.append("</table>");
        if (shown == 0) return "<p>No uploads yet.</p>";
        return h.toString();
    }
}

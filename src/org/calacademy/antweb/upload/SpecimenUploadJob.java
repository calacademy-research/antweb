package org.calacademy.antweb.upload;

import java.io.File;
import java.sql.Connection;
import java.util.Date;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Curator;
import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.home.ServerDb;
import org.calacademy.antweb.util.*;

/**
 * A curator's specimen upload (AntWeb specimen file or TaxonWorks archive), run in the background
 * (BackgroundJobs) after the upload page has saved the file and answered. Does what
 * UploadAction.execute() did in the request until 2026-10-20: the same steps, one database
 * transaction, the same report, the same upload-complete email.
 *
 * The saved file (in the working folder's jobs/ folder) is deleted when the job ends.
 */
public final class SpecimenUploadJob implements BackgroundJobs.Work {

    private static final Log s_log = LogFactory.getLog(SpecimenUploadJob.class);

    /** Seconds between tries while another upload holds the upload flag. */
    static final int CLAIM_WAIT_MS = 5000;

    final String action;
    final File saved;
    final String originalName;
    final Curator curator;
    final Login accessLogin;
    final HttpSession session;
    final DataSource dataSource;
    final String userAgent;
    final String encoding;

    public SpecimenUploadJob(String action, File saved, String originalName, Curator curator, Login accessLogin,
                             HttpSession session, DataSource dataSource, String userAgent, String encoding) {
        this.action = action;
        this.saved = saved;
        this.originalName = originalName;
        this.curator = curator;
        this.accessLogin = accessLogin;
        this.session = session;
        this.dataSource = dataSource;
        this.userAgent = userAgent;
        this.encoding = encoding;
    }

    /** The upload flag's text for this upload (shown on serverStatus.do while it runs). */
    String processName() {
        Group g = curator.getGroup();
        return accessLogin.getName() + ":" + (g == null ? "" : g.getName());
    }

    @Override
    public BackgroundJobs.Outcome run(BackgroundJobs.Job job) throws Exception {
        // The gate let the job start when no upload was running; something else (an image
        // upload) may have started since. Wait for it rather than share the flag.
        while (!UploadAction.claimUploadProcess(processName())) {
            BackgroundJobs.step("Waiting for another upload to finish. Your upload starts right after it.");
            Thread.sleep(CLAIM_WAIT_MS);
        }
        BackgroundJobs.step("Starting the upload.");

        Group noticeGroup = curator.getGroup();
        UploadNotice.Facts notice = new UploadNotice.Facts();
        notice.fileName = originalName;
        notice.fileType = "taxonWorksUpload".equals(action) ? "TaxonWorks file" : "AntWeb specimen file";

        UploadDetails uploadDetails = new UploadDetails();
        String failure = null;
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("SpecimenUploadJob.run()");
        Date start = new Date();
        try {
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            connection.setAutoCommit(false);

            AntwebMgr.incrementSpecimenUploadId(connection);

            String downTimeMessage = ServerDb.isDownTime(action, connection);
            if (!"".equals(downTimeMessage)) {
                DBUtil.rollback(connection);
                failure = downTimeMessage;
                return BackgroundJobs.Outcome.failed(failureText(failure));
            }

            if ("taxonWorksUpload".equals(action)) {
                uploadDetails = new TaxonWorksUploader(connection).uploadSavedFile(saved, originalName, action, curator, userAgent, encoding);
            } else {
                uploadDetails = new SpecimenUploader(connection).uploadSavedFile(saved, originalName, action, curator, userAgent, encoding);
            }

            if (uploadDetails.getForwardPage() != null) {
                // Refused (file format, zip content, ...): nothing was changed.
                DBUtil.rollback(connection);
                failure = uploadDetails.getMessage();
                return BackgroundJobs.Outcome.failed(failureText(failure));
            }

            BackgroundJobs.step("Updating the group's counts, map and upload record.");
            UploadAction.postProcessSpecimenUpload(connection, curator, uploadDetails);
            rememberMuseums(uploadDetails);

            BackgroundJobs.step("Updating site-wide lists (endemic species, unused indet names).");
            UploadAction.specimenPostProcessGlobal(connection);

            BackgroundJobs.step("Writing the upload report.");
            uploadDetails.finish(accessLogin, null, connection);

            connection.commit();
            notice.complete = true;
            notice.uploadId = uploadDetails.getReportUploadId();   // the upload_id recorded in the upload table
            AntwebUtil.logCount();

            return BackgroundJobs.Outcome.ok(successText(uploadDetails, notice.uploadId), reportUrl(uploadDetails), "Your upload report");

        } catch (Exception e) {
            int caseNumber = AntwebUtil.getCaseNumber();
            failure = "Error. Case number:" + caseNumber + ". No changes made. e:" + Html.esc(e.toString());
            s_log.error("run() " + failure + " action:" + action + " " + AntwebUtil.getShortStackTrace(e));
            DBUtil.rollback(connection);
            return BackgroundJobs.Outcome.failed(failureText(failure), reportUrl(uploadDetails), "The upload report");
        } finally {
            Group g = curator.getGroup();
            s_log.warn("run() finished action:" + action + " group:" + (g == null ? "" : g.getAbbrev())
                    + " in " + AntwebUtil.getMinsPassed(start));
            UploadAction.releaseUploadProcess(processName());

            UploadMgr.populate(connection, true); // Repopulate UploadMgr.
            DBUtil.close(connection, this, dbMethodName);

            UploadAction.sendUploadNotice(notice, noticeGroup, uploadDetails, accessLogin, failure);

            if (saved != null && saved.exists() && !saved.delete()) s_log.warn("run() could not delete " + saved);

            Profiler.profile("uploadAction", uploadDetails.getStartTime());
            Profiler.report();
        }
    }

    /** "Recalculate Your Museums" on the Curate page uses the museums of this login's last upload. */
    private void rememberMuseums(UploadDetails uploadDetails) {
        if (session == null) return;
        try {
            session.setAttribute("museumMap", uploadDetails.getMuseumMap());
            session.setAttribute("museumMapLoginId", accessLogin == null ? null : accessLogin.getId());
        } catch (IllegalStateException e) {
            s_log.info("rememberMuseums() session ended: " + e);   // logged out meanwhile
        }
    }

    /** The full address of the upload report, or null if it was not written. */
    public static String reportUrl(UploadDetails uploadDetails) {
        try {
            String path = uploadDetails.getLogDirFile();
            if (path == null || !new File(LogMgr.getWebLogRoot() + path).isFile()) return null;
            return AntwebProps.getDomainApp() + "/web/log/" + path;
        } catch (Exception e) {
            return null;
        }
    }

    /** What the status page says when the upload is complete (plain text). */
    static String successText(UploadDetails d, int uploadId) {
        StringBuilder s = new StringBuilder("Upload complete: ")
            .append(String.format("%,d", d.getInsertedSpecimenCount())).append(" specimens loaded");
        int red = d.getMessageMgr().getRedFlagCount();
        if (red > 0) s.append(", ").append(String.format("%,d", red)).append(" of them red-flagged");
        s.append(". Upload ID ").append(uploadId).append(".");
        if (!d.getMessageMgr().getMessages().isEmpty()) {
            s.append(" The upload report lists lines that need your attention (some may not have been loaded).");
        }
        String note = UploadNotice.plain(d.getMessage());
        if (note != null && !"success".equals(note)) s.append(" Note: ").append(note);
        return s.toString();
    }

    /** What the status page says when the upload did not complete (plain text, no Java details). */
    static String failureText(Object message) {
        String s = UploadNotice.failureText(message);
        if (s == null) return "The upload did not complete.";
        return s.replace("Please reply to this email so we can look into it.",
                         "Please write to antweb@antweb.org (with the case number) so we can look into it.");
    }
}

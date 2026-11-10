package org.calacademy.antweb.util;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A data extract (dataExtract.do) as a background job (BackgroundJobs, kind "extract"): the page starts it, the
 * browser follows it on jobStatus.do ("Uploads and data jobs"), and its result there is the manifest text,
 * VERIFIED or the differences, and the download links.
 *
 * One extract at a time: every extract has the same action, so BackgroundJobs gives back the one already waiting
 * or running. Like every background job it waits while the daily update, another data job or an upload runs (the
 * application gate), and no upload started from a page runs beside it (one background job at a time).
 *
 * Who ran which extract: the job's row in background_job (kind extract; who, when, the countries in its title,
 * the result with the note) stays; request.json in the extract's folder holds the same while the files are kept
 * (DataExtractFiles), and admin.log gets a line.
 *
 * An extract whose counts differ from the database's ends as "Did not complete" (as the command-line tool ends
 * with an error): its files are there, marked DIFFERENT, to look at, not to send.
 */
public final class DataExtractJob {

    private static final Log s_log = LogFactory.getLog(DataExtractJob.class);

    /** The job's action: the same for every extract, so only one waits or runs at a time. */
    public static final String ACTION = "dataExtract";

    /** Free space the server must have before an extract starts. */
    public static final long MIN_FREE_BYTES = 1024L * 1024 * 1024;

    /** The manifest text is shown whole on the job page up to this many characters (the job's result holds 60,000 bytes). */
    static final int MAX_TEXT_SHOWN = 30000;

    public static final int MAX_NOTE = 200;

    private DataExtractJob() {}

    /** The waiting or running extract, or null. */
    public static BackgroundJobs.Job active() {
        return BackgroundJobs.get().findDuplicate(new BackgroundJobs.Job(BackgroundJobs.EXTRACT, ACTION, "", 0, "", 0));
    }

    public static String title(List<String> countries) {
        return "Data extract: " + String.join(", ", countries);
    }

    /** Starts the extract (or returns the one already waiting or running). request: names already resolved. */
    public static BackgroundJobs.Submitted submit(DataSource dataSource, int loginId, String loginName, String note,
                                                  DataExtract.Request request) {
        BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.EXTRACT, ACTION, title(request.countries), loginId, loginName, 0);
        final File root = DataExtractFiles.root();
        return BackgroundJobs.get().submit(job, j -> run(j, dataSource, root, note, request,
            new DataExtract.Site(new Date(), GitCommit.full(), AntwebUtil.getReleaseNum()), AntwebProps.getDomainApp()));
    }

    static BackgroundJobs.Outcome run(BackgroundJobs.Job job, DataSource dataSource, File root, String note,
                                      DataExtract.Request request, DataExtract.Site site, String domainApp) throws Exception {
        BackgroundJobs.step("Preparing the extract.");
        File folder = DataExtractFiles.create(root, LocalDate.now(), job.getId());
        DataExtractFiles.writeRequest(folder, job.getId(), job.getLoginId(), job.getLoginName(), note, request);
        String name = DBUtil.getDbMethodName("DataExtractJob.run()");
        Connection connection = DBUtil.getConnection(dataSource, name);
        DataExtract.Result result;
        try {
            BackgroundJobs.step("Writing specimens.");
            result = DataExtract.run(connection, folder, request, site,
                rows -> BackgroundJobs.step("Writing specimens: " + String.format("%,d", rows) + " so far."));
        } catch (DataExtract.BadRequest e) {
            DataExtractFiles.deleteData(folder);
            return BackgroundJobs.Outcome.failed(Html.esc(e.getMessage()), domainApp + "/dataExtract.do", "Data extract");
        } catch (Exception e) {
            // Half-written files are of no use and must never be downloaded.
            DataExtractFiles.deleteData(folder);
            throw e;
        } finally {
            DBUtil.close(connection, name);
        }
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " data extract (job " + job.getId() + ") by " + job.getLoginName()
            + ": " + String.join(", ", request.countries) + (request.islands ? ", with islands" : ", without islands")
            + (request.bioregion.isEmpty() ? "" : ", bioregion " + request.bioregion) + "; " + result.specimens + " specimens; "
            + (result.verified ? "verified" : "DIFFERENT from the database's count") + (note == null || note.isEmpty() ? "" : "; note: " + note));
        String html = resultHtml(job.getId(), result, note, domainApp);
        String link = domainApp + "/dataExtract.do";
        return result.verified ? BackgroundJobs.Outcome.ok(html, link, "All data extracts")
            : BackgroundJobs.Outcome.failed(html, link, "All data extracts");
    }

    /** The links to the files of a finished extract (the zip first), as list items. */
    public static String downloadLinks(int jobId, List<String> files, String domainApp) {
        String base = domainApp + "/dataExtractFile.do?job=" + jobId + "&file=";
        StringBuilder h = new StringBuilder();
        h.append("<li><a class=\"extract-file\" href=\"").append(Html.escAttr(base + DataExtractFiles.ZIP))
         .append("\">Everything in one zip file</a> (the file to send)</li>");
        for (String f : DataExtractFiles.DOWNLOADS) {
            if (!files.contains(f)) continue;
            h.append("<li><a class=\"extract-file\" href=\"").append(Html.escAttr(base + f)).append("\">").append(Html.esc(f)).append("</a>: ")
             .append(describe(f)).append("</li>");
        }
        return h.toString();
    }

    static String describe(String file) {
        switch (file) {
            case DataExtract.SPECIMENS: return "every specimen, one line each (open with a spreadsheet or R after unzipping)";
            case DataExtract.GENUS_SUMMARY: return "specimens and names per genus";
            case DataExtract.BIOREGION_LIST: return "AntWeb's species list of the bioregion, with each name's specimens in this extract";
            case DataExtract.MANIFEST_TXT: return "what is in the extract and the check, to read";
            default: return "the same for a program";
        }
    }

    /** The job's result: one line that says it all, the manifest text, the downloads. AntWeb's own HTML, every value escaped. */
    public static String resultHtml(int jobId, DataExtract.Result result, String note, String domainApp) {
        StringBuilder h = new StringBuilder();
        String specimens = String.format("%,d", result.specimens);
        if (result.verified) {
            h.append("<p id=\"extract-check\" data-verified=\"true\"><span class=\"aw-ok\">VERIFIED</span> ").append(specimens)
             .append(" specimens. The file holds exactly the specimens the database has for these places: every count is equal.</p>");
        } else {
            h.append("<p id=\"extract-check\" data-verified=\"false\"><span class=\"aw-bad\">NOT VERIFIED</span> ").append(specimens)
             .append(" specimens were written, but the database's own count is different (the differences are at the end of the text below).")
             .append(" Specimens were probably uploaded while the extract ran. Do not send these files: start the extract again.</p>");
        }
        if (note != null && !note.isEmpty()) h.append("<p id=\"extract-note\">Note: ").append(Html.esc(note)).append("</p>");
        String text = result.text;
        boolean cut = text.length() > MAX_TEXT_SHOWN;
        if (cut) text = text.substring(0, text.lastIndexOf('\n', MAX_TEXT_SHOWN) + 1);
        h.append("<pre id=\"extract-manifest\" style=\"white-space:pre-wrap\">").append(Html.esc(text)).append("</pre>");
        if (cut) h.append("<p class=\"aw-note\">The text is longer; all of it is in manifest.txt.</p>");
        h.append("<h3>Files</h3><ul class=\"aw-links\" id=\"extract-files\">").append(downloadLinks(jobId, result.files, domainApp)).append("</ul>");
        h.append("<p class=\"aw-note\">The files stay on the server for ").append(DataExtractFiles.KEEP_DAYS)
         .append(" days and are then removed. Only administrators can download them.</p>");
        return h.toString();
    }
}

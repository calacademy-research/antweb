package org.calacademy.antweb.util;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
 * with an error): its files are there, marked NOT VERIFIED also in their download names and without the zip, to
 * look at, not to send.
 *
 * Guards while it runs: the disk is checked when the job starts and every 5,000 rows (it stops below
 * MIN_FREE_WHILE_RUNNING), and it stops after MAX_MINUTES (the long pool takes back a connection held for an hour:
 * struts-configDb.xml, unreturnedConnectionTimeout). A stopped or failed extract leaves no data file.
 */
public final class DataExtractJob {

    private static final Log s_log = LogFactory.getLog(DataExtractJob.class);

    /** The job's action: the same for every extract, so only one waits or runs at a time. */
    public static final String ACTION = "dataExtract";

    /** Free space the server must have before an extract starts. */
    public static final long MIN_FREE_BYTES = 1024L * 1024 * 1024;

    /** Free space below which a running extract stops. */
    public static final long MIN_FREE_WHILE_RUNNING = 512L * 1024 * 1024;

    /** A running extract stops after this many minutes (the pool closes its connection after 60). */
    public static final int MAX_MINUTES = 50;

    /** The manifest text is shown whole on the job page up to this many characters (the job's result holds 60,000 bytes). */
    static final int MAX_TEXT_SHOWN = 30000;

    public static final int MAX_NOTE = 200;

    private DataExtractJob() {}

    /** The waiting or running extract, or null. */
    public static BackgroundJobs.Job active() {
        return BackgroundJobs.get().findDuplicate(new BackgroundJobs.Job(BackgroundJobs.EXTRACT, ACTION, "", 0, "", 0));
    }

    /** True while the extract with this job number waits or runs. */
    public static boolean isActive(int jobId) {
        BackgroundJobs.Job job = active();
        return job != null && job.getId() == jobId;
    }

    public static String title(List<String> countries) {
        return "Data extract: " + String.join(", ", countries);
    }

    /** A note on one line: line breaks and other control characters become a blank (it goes into admin.log). */
    public static String cleanNote(String note) {
        if (note == null) return "";
        return note.replaceAll("[\\p{Cntrl}\\x{2028}\\x{2029}]+", " ").replaceAll(" {2,}", " ").trim();
    }

    /** Why the disk does not allow an extract (less than minFree free, or the extracts over their limit), or null. */
    public static String diskRefusal(File root, long minFree) {
        File disk = root;
        while (disk != null && !disk.exists()) disk = disk.getParentFile();
        if (disk != null && disk.getTotalSpace() > 0 && disk.getUsableSpace() < minFree) {
            return "The server has less than " + mb(minFree) + " of free disk space (" + mb(disk.getUsableSpace()) + ").";
        }
        long used = DataExtractFiles.totalBytes(root);
        if (used >= DataExtractFiles.MAX_TOTAL_BYTES) {
            return "The extracts kept on the server use " + mb(used) + " (the limit is " + mb(DataExtractFiles.MAX_TOTAL_BYTES)
                + "). Delete one you no longer need in the list on the Data extract page, then start again.";
        }
        return null;
    }

    static String mb(long bytes) {
        return String.format("%,d MB", bytes / 1024 / 1024);
    }

    /** Starts the extract (or returns the one already waiting or running). request: names already resolved. */
    public static BackgroundJobs.Submitted submit(DataSource dataSource, int loginId, String loginName, String note,
                                                  DataExtract.Request request) {
        BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.EXTRACT, ACTION, title(request.countries), loginId, loginName, 0);
        final File root = DataExtractFiles.root();
        final String oneLine = cleanNote(note);
        return BackgroundJobs.get().submit(job, j -> run(j, dataSource, root, oneLine, request,
            new DataExtract.Site(new Date(), GitCommit.full(), AntwebUtil.getReleaseNum()), AntwebProps.getDomainApp()));
    }

    static BackgroundJobs.Outcome run(BackgroundJobs.Job job, DataSource dataSource, File root, String note,
                                      DataExtract.Request request, DataExtract.Site site, String domainApp) throws Exception {
        BackgroundJobs.step("Preparing the extract.");
        String link = domainApp + "/dataExtract.do";
        // The disk may have filled since the job was started (it may have waited for the daily update).
        String refusal = diskRefusal(root, MIN_FREE_BYTES);
        if (refusal != null) return BackgroundJobs.Outcome.failed(Html.esc(refusal + " No extract was written."), link, "Data extract");
        final File folder = DataExtractFiles.create(root, LocalDate.now(), job.getId());
        DataExtractFiles.writeRequest(folder, job.getId(), job.getLoginId(), job.getLoginName(), note, request);
        final long started = System.currentTimeMillis();
        String name = DBUtil.getDbMethodName("DataExtractJob.run()");
        Connection connection = DBUtil.getConnection(dataSource, name);
        DataExtract.Result result;
        try {
            BackgroundJobs.step("Writing specimens.");
            result = DataExtract.run(connection, folder, request, site, rows -> {
                if (folder.getUsableSpace() < MIN_FREE_WHILE_RUNNING && folder.getTotalSpace() > 0) {
                    throw new DataExtract.Stopped("The server ran low on disk space (" + mb(folder.getUsableSpace()) + " free) after "
                        + String.format("%,d", rows) + " specimens. The extract was stopped and its files removed.");
                }
                if (System.currentTimeMillis() - started > MAX_MINUTES * 60000L) {
                    throw new DataExtract.Stopped("The extract ran for more than " + MAX_MINUTES + " minutes and was stopped ("
                        + String.format("%,d", rows) + " specimens written, files removed). Ask for fewer countries at a time.");
                }
                BackgroundJobs.step("Writing specimens: " + String.format("%,d", rows) + " so far.");
            });
        } catch (DataExtract.BadRequest | DataExtract.Stopped e) {
            DataExtractFiles.deleteData(folder);
            return BackgroundJobs.Outcome.failed(Html.esc(e.getMessage()), link, "Data extract");
        } catch (Throwable t) {
            // Half-written files are of no use and must never be downloaded (also after an Error, e.g. out of memory).
            DataExtractFiles.deleteData(folder);
            throw t;
        } finally {
            DBUtil.close(connection, name);
        }
        LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " data extract (job " + job.getId() + ") by " + cleanNote(job.getLoginName())
            + ": " + String.join(", ", request.countries) + (request.islands ? ", with islands" : ", without islands")
            + (request.allFamilies ? ", all families" : ", ants only") + (request.noCountry ? ", with the no-country file" : "")
            + (request.bioregion.isEmpty() ? "" : ", bioregion " + request.bioregion) + "; " + result.specimens + " specimens; "
            + (result.verified ? "verified" : "DIFFERENT from the database's count") + (note == null || note.isEmpty() ? "" : "; note: " + cleanNote(note)));
        String html = resultHtml(job.getId(), result, note, domainApp);
        return result.verified ? BackgroundJobs.Outcome.ok(html, link, "All data extracts")
            : BackgroundJobs.Outcome.failed(html, link, "All data extracts");
    }

    /**
     * The links to the files of a finished extract, as list items. Verified: the zip first ("the file to send").
     * Not verified: no zip, and every link says so (the saved file's name says it too).
     */
    public static String downloadLinks(int jobId, List<String> files, boolean verified, String domainApp) {
        String base = domainApp + "/dataExtractFile.do?job=" + jobId + "&file=";
        StringBuilder h = new StringBuilder();
        if (verified) {
            h.append("<li><a class=\"extract-file\" href=\"").append(Html.escAttr(base + DataExtractFiles.ZIP))
             .append("\">Everything in one zip file</a> (the file to send)</li>");
        }
        for (String f : DataExtractFiles.DOWNLOADS) {
            if (!files.contains(f)) continue;
            h.append("<li><a class=\"extract-file\" href=\"").append(Html.escAttr(base + f)).append("\">").append(Html.esc(f)).append("</a>: ")
             .append(verified ? describe(f) : "<b>NOT VERIFIED, do not send</b>").append("</li>");
        }
        return h.toString();
    }

    static String describe(String file) {
        switch (file) {
            case DataExtract.SPECIMENS: return "every specimen, one line each (unzip it, then open it in R or Python, or import it into a spreadsheet as text)";
            case DataExtract.SPECIMENS_NO_COUNTRY: return "every specimen of AntWeb that has no country at all (some may belong to these countries)";
            case DataExtract.GENUS_SUMMARY: return "specimens and names per genus";
            case DataExtract.BIOREGION_LIST: return "AntWeb's species list of the bioregion, with each name's specimens in this extract";
            case DataExtract.MANIFEST_TXT: return "what is in the extract and the check, to read";
            default: return "the same for a program";
        }
    }

    /** The job's result: one line that says it all, the manifest text, the downloads. AntWeb's own HTML, every value escaped. */
    @SuppressWarnings("unchecked")
    public static String resultHtml(int jobId, DataExtract.Result result, String note, String domainApp) {
        StringBuilder h = new StringBuilder();
        String specimens = String.format("%,d", result.specimens);
        if (result.verified) {
            h.append("<p id=\"extract-check\" data-verified=\"true\"><span class=\"aw-ok\">VERIFIED</span> ").append(specimens)
             .append(" specimens. The file holds exactly the specimens the database has for these places: every count is equal.</p>");
        } else {
            h.append("<p id=\"extract-check\" data-verified=\"false\"><span class=\"aw-bad\">NOT VERIFIED</span> ").append(specimens)
             .append(" specimens were written, but the database's own count is different (the differences are at the end of the text below).")
             .append(" Specimens were probably uploaded or changed while the extract ran. Do not send these files: start the extract again.</p>");
        }
        Map<String, Object> m = result.manifest;
        Object empty = m == null ? null : m.get("countries_without_specimens");
        if (empty instanceof List && !((List<Object>) empty).isEmpty()) {
            h.append("<p class=\"aw-warn\" id=\"extract-empty\">AntWeb has no specimen for: <b>").append(Html.esc(join((List<Object>) empty)))
             .append("</b>. Check that these are the countries you meant.</p>");
        }
        Object outdated = m == null ? null : m.get("outdated_names");
        if (outdated instanceof List && !((List<Object>) outdated).isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (Object o : (List<Object>) outdated) {
                if (names.length() > 0) names.append(", ");
                names.append(((Map<String, Object>) o).get("name")).append(" (now ").append(((Map<String, Object>) o).get("valid_name")).append(")");
            }
            h.append("<p id=\"extract-outdated\">Specimens still filed under an old country name are included: ").append(Html.esc(names))
             .append(". Specimens filed that way: ").append(String.format("%,d", ((Number) m.get("filed_under_outdated_name")).longValue())).append(".</p>");
        }
        if (note != null && !note.isEmpty()) h.append("<p id=\"extract-note\">Note: ").append(Html.esc(note)).append("</p>");
        String text = result.text;
        boolean cut = text.length() > MAX_TEXT_SHOWN;
        if (cut) text = text.substring(0, text.lastIndexOf('\n', MAX_TEXT_SHOWN) + 1);
        h.append("<pre id=\"extract-manifest\" style=\"white-space:pre-wrap\">").append(Html.esc(text)).append("</pre>");
        if (cut) h.append("<p class=\"aw-note\">The text is longer; all of it is in manifest.txt.</p>");
        h.append("<h3>Files</h3><ul class=\"aw-links\" id=\"extract-files\">").append(downloadLinks(jobId, result.files, result.verified, domainApp)).append("</ul>");
        h.append("<p class=\"aw-note\">The files stay on the server for ").append(DataExtractFiles.KEEP_DAYS)
         .append(" days and are then removed. Only administrators can download them.</p>");
        return h.toString();
    }

    private static String join(List<Object> values) {
        StringBuilder b = new StringBuilder();
        for (Object v : values) {
            if (b.length() > 0) b.append(", ");
            b.append(v);
        }
        return b.toString();
    }

    /**
     * The daily tidying (Scheduler, after the daily update), as one line for compute.log: extracts older than 30
     * days are removed, and the half-written files of an extract the server stopped in the middle of. Never throws.
     */
    public static String dailyCleanup() {
        try {
            File root = DataExtractFiles.root();
            int old = DataExtractFiles.removeOlderThan(root, DataExtractFiles.KEEP_DAYS, LocalDate.now());
            int unfinished = DataExtractFiles.removeUnfinished(root, DataExtractJob::isActive);
            return "data extracts older than " + DataExtractFiles.KEEP_DAYS + " days removed: " + old + "; unfinished ones tidied: " + unfinished;
        } catch (RuntimeException e) {
            s_log.warn("dailyCleanup() e:" + e);
            return "data extracts not checked for removal e:" + e;
        }
    }
}

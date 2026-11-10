package org.calacademy.antweb.curate;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.BackgroundJobDb;
import org.calacademy.antweb.util.BackgroundJobs;
import org.calacademy.antweb.util.Check;
import org.calacademy.antweb.util.Csrf;
import org.calacademy.antweb.util.DBUtil;
import org.calacademy.antweb.util.DataExtract;
import org.calacademy.antweb.util.DataExtractFiles;
import org.calacademy.antweb.util.DataExtractJob;
import org.calacademy.antweb.util.Html;
import org.calacademy.antweb.util.HttpUtil;
import org.calacademy.antweb.util.JobStatusAction;
import org.calacademy.antweb.util.LoginMgr;
import org.calacademy.antweb.util.Scheduler;
import org.calacademy.antweb.util.UtilDataAction;

/**
 * Data extract (dataExtract.do; Brian Fisher, 2026-11-10). Administrators only. Lets an administrator make a
 * complete, verified specimen extract for a collaborator from the website: every specimen of the chosen countries
 * (with the islands AntWeb files under them), a genus summary, a bioregion's species list and a manifest whose
 * counts are checked against the database (DataExtract; the same files as tools/export/specimen_extract.py).
 *
 *   GET  /dataExtract.do   the form (countries, bioregion, islands, other families, the specimens without a
 *                          country, note) and the extracts kept on the server
 *                          (date, who, filters, specimens, verified, download links).
 *   POST /dataExtract.do   (form token) checks the names against the geolocale and bioregion tables and starts
 *                          the background job (DataExtractJob); the answer is a redirect to the job's page
 *                          (jobStatus.do?id=N). With a wrong name, or while it cannot start, the form comes back
 *                          with a sentence saying why and nothing is started.
 *   POST op=delete, job=N  (form token) removes that extract's files from the server (not one that is running).
 * A GET never starts or deletes anything, whatever its parameters.
 *
 * Not started while the daily update or another data job runs (the check utilData.do makes for data jobs), while
 * another extract waits or runs, when the server has less than 1 GB of free disk, or when the extracts kept use
 * more than 5 GB. Reads the database only; the files go to a folder outside the web application (DataExtractFiles)
 * and are downloaded through dataExtractFile.do. Extracts older than 30 days and half-written ones are removed
 * here and after each daily update.
 *
 * Tests: tests/e2e/test_data_extract.py. Docs: docs/guides/data-extract.md.
 */
public final class DataExtractAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(DataExtractAction.class);

    static final int MAX_COUNTRIES = 300;

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        ActionForward a = Check.initLogin(request, mapping); if (a != null) return a;
        ActionForward c = Check.admin(request, mapping); if (c != null) return c;
        Login login = LoginMgr.getAccessLogin(request);
        response.setHeader("Cache-Control", "no-store");
        BackgroundJobDb.setDataSource(getDataSource(request, "conPool"));

        File root = DataExtractFiles.root();
        DataExtractFiles.removeOlderThan(root, DataExtractFiles.KEEP_DAYS, LocalDate.now());

        String countriesText = param(request, "countries", 4000);
        String bioregion = param(request, "bioregion", 64);
        String note = DataExtractJob.cleanNote(param(request, "note", 1000));
        boolean isPost = HttpUtil.isPost(request);
        boolean islands = !isPost || "on".equals(request.getParameter("islands"));   // on unless the box was cleared
        boolean allFamilies = "on".equals(request.getParameter("allFamilies"));        // off unless ticked: ants only
        boolean noCountry = "on".equals(request.getParameter("noCountry"));
        String message = null;
        List<String> picked = new ArrayList<>();
        if (request.getParameterValues("country") != null) {
            for (String p : request.getParameterValues("country")) if (p != null && !p.trim().isEmpty()) picked.add(p.trim());
        }

        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("DataExtractAction.execute()");
        StringBuilder html = new StringBuilder();
        try {
            DataSource dataSource = getDataSource(request, "conPool");
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            if (isPost && "delete".equals(request.getParameter("op"))) {
                message = delete(root, request.getParameter("job"), login);
                countriesText = "";
                note = "";
            } else if (isPost) {
                try {
                    DataExtract.Request extract = check(connection, countriesText, picked, islands, allFamilies, noCountry, bioregion, note);
                    String refused = cannotStartNow(root);
                    if (refused != null) throw new DataExtract.BadRequest(refused);
                    BackgroundJobs.Submitted submitted = DataExtractJob.submit(getDataSource(request, "longConPool"),
                        login.getId(), login.getName(), note, extract);
                    if (submitted.job == null) throw new DataExtract.BadRequest(submitted.refused);
                    s_log.warn("execute() data extract job " + submitted.job.getId() + " by " + login.getName()
                        + (submitted.isNew ? "" : " (one was already waiting or running)"));
                    return new ActionForward("/jobStatus.do?id=" + submitted.job.getId() + (submitted.isNew ? "" : "&already=1"), true);
                } catch (DataExtract.BadRequest e) {
                    message = e.getMessage() + " No extract was started.";
                }
            }
            // An extract the server stopped in the middle of (a restart) leaves a half-written file: removed here.
            DataExtractFiles.removeUnfinished(root, DataExtractJob::isActive);
            BackgroundJobs.Job active = DataExtractJob.active();
            html.append(page(request, message, countriesText, picked, bioregion, islands, allFamilies, noCountry, note,
                countries(connection), bioregions(connection), DataExtractFiles.list(root), active, AntwebProps.getDomainApp()));
        } catch (SQLException e) {
            s_log.error("execute() e:" + e);
            html.append("<div class=\"aw-msg\">Database error; nothing was started: ").append(Html.esc(e.getMessage())).append("</div>");
        } finally {
            DBUtil.close(connection, this, dbMethodName);
        }
        request.setAttribute("html", html.toString());
        return mapping.findForward("success");
    }

    private static String param(HttpServletRequest request, String name, int max) {
        String v = request.getParameter(name);
        if (v == null) return "";
        v = v.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** The request as it will run (names as the database spells them), or a BadRequest saying what to correct. */
    static DataExtract.Request check(Connection connection, String countriesText, List<String> picked, boolean islands,
                                     boolean allFamilies, boolean noCountry,
                                     String bioregion, String note) throws SQLException, DataExtract.BadRequest {
        Set<String> names = new LinkedHashSet<>(DataExtract.parseCountries(countriesText));
        names.addAll(DataExtract.parseCountries(String.join("\n", picked)));
        if (names.size() > MAX_COUNTRIES) throw new DataExtract.BadRequest("Too many countries (at most " + MAX_COUNTRIES + ").");
        if (note != null && note.length() > DataExtractJob.MAX_NOTE) {
            throw new DataExtract.BadRequest("The note is too long (at most " + DataExtractJob.MAX_NOTE + " characters).");
        }
        List<String> countries = DataExtract.resolve(connection, new ArrayList<>(names));
        return new DataExtract.Request(countries, islands, DataExtract.resolveBioregion(connection, bioregion), allFamilies, noCountry);
    }

    /**
     * Why an extract cannot start now, or null: the daily update or another data job is running (as utilData.do
     * refuses a data job started by hand), another extract waits or runs, or the disk is nearly full.
     */
    static String cannotStartNow(File root) {
        if (UtilDataAction.isInComputeProcess() || Scheduler.isRunning()) {
            return "The daily update (or another data job) is running. Please start the extract when it has finished;"
                + " the Daily update page shows how far it is.";
        }
        BackgroundJobs.Job active = DataExtractJob.active();
        if (active != null) {
            return "Another extract is " + (active.getStatus() == BackgroundJobs.Status.running ? "running" : "waiting to start")
                + " (job " + active.getId() + ", " + active.getTitle() + "). Extracts run one at a time: please wait until it has finished.";
        }
        return DataExtractJob.diskRefusal(root, DataExtractJob.MIN_FREE_BYTES);
    }

    /** Removes the files of one extract (the Delete button); returns the sentence shown afterwards. */
    static String delete(File root, String jobParam, Login login) {
        int jobId = 0;
        if (jobParam != null && jobParam.trim().matches("\\d{1,9}")) jobId = Integer.parseInt(jobParam.trim());
        File folder = DataExtractFiles.find(root, jobId);
        if (folder == null) return "There is no extract with that number on the server.";
        if (DataExtractJob.isActive(jobId)) return "Extract " + jobId + " is still running; it was not deleted.";
        if (!DataExtractFiles.delete(folder)) return "The files of extract " + jobId + " could not all be removed.";
        org.calacademy.antweb.util.LogMgr.appendLog("admin.log", org.calacademy.antweb.util.DateUtil.getFormatDateTimeStr()
            + " data extract files deleted by " + DataExtractJob.cleanNote(login.getName()) + ": job " + jobId);
        return "The files of extract " + jobId + " were removed from the server. Its job stays listed under " + JobStatusAction.PAGE_TITLE + ".";
    }

    /** Country names for the pick list: the valid country rows, by name. */
    static List<String> countries(Connection connection) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rset = stmt.executeQuery("select distinct name from geolocale where georank = 'country' and is_valid = 1 order by name")) {
            while (rset.next()) if (rset.getString(1) != null) out.add(rset.getString(1));
        }
        return out;
    }

    static List<String> bioregions(Connection connection) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rset = stmt.executeQuery("select name from bioregion order by name")) {
            while (rset.next()) if (rset.getString(1) != null) out.add(rset.getString(1));
        }
        return out;
    }

    // ---------------------------------------------------------------- page parts

    public static final String HELP = "<div class=\"aw-intro\" id=\"data-extract-help\">"
        + "<p><b>What it does:</b> makes a complete specimen extract for a collaborator: every ant specimen record AntWeb has for the"
        + " countries you choose (other families too, if you tick the box), and a check that proves nothing is missing. It only reads the database.</p>"
        + "<p><b>The files:</b> <i>specimens.csv.gz</i> (one line per specimen, every specimen column, plus the status, rank,"
        + " current valid name and author of its name and the name of its upload group), <i>genus_summary.csv</i> (specimens and"
        + " names per genus), <i>bioregion_list.csv</i> (if you choose a bioregion: AntWeb's species list of it, with each name's"
        + " specimens in the extract) and <i>manifest.txt</i> (what was asked, the counts by country and bioregion, and the check).</p>"
        + "<p><b>Nothing is left out:</b> specimens without coordinates are in the file and marked (has_coordinates = 0), and so are"
        + " specimens whose name AntWeb does not know (no_taxon_row = 1). The manifest says how many there are of each.</p>"
        + "<p><b>The check:</b> when the file is written, the database is counted again, country by country. <b>VERIFIED</b> means"
        + " the two agree; send only a verified extract.</p>"
        + "</div>";

    static String page(HttpServletRequest request, String message, String countriesText, List<String> picked, String bioregion,
                       boolean islands, boolean allFamilies, boolean noCountry, String note, List<String> countries, List<String> bioregions,
                       List<DataExtractFiles.Entry> extracts, BackgroundJobs.Job active, String domainApp) {
        StringBuilder h = new StringBuilder(HELP);
        if (message != null) {
            h.append("<div class=\"aw-msg\" id=\"data-extract-message\">").append(Html.esc(message)).append("</div>");
        }
        if (active != null) {
            h.append("<p class=\"aw-warn\" id=\"data-extract-active\">An extract is ")
             .append(active.getStatus() == BackgroundJobs.Status.running ? "running" : "waiting to start").append(": <a href=\"")
             .append(Html.escAttr(domainApp + "/jobStatus.do?id=" + active.getId())).append("\">job ").append(active.getId()).append(", ")
             .append(Html.esc(active.getTitle())).append("</a>. Extracts run one at a time.</p>");
        }
        h.append(formHtml(Csrf.field(request), countriesText, picked, bioregion, islands, allFamilies, noCountry, note, countries, bioregions, domainApp));
        h.append(listHtml(extracts, active == null ? 0 : active.getId(), Csrf.field(request), domainApp));
        return h.toString();
    }

    public static String formHtml(String csrfField, String countriesText, List<String> picked, String bioregion, boolean islands,
                                  boolean allFamilies, boolean noCountry, String note,
                           List<String> countries, List<String> bioregions, String domainApp) {
        StringBuilder h = new StringBuilder("<div class=\"aw-card\" id=\"data-extract-form-card\"><h2>Start an extract</h2>");
        h.append("<form class=\"aw-form\" method=\"post\" action=\"").append(Html.escAttr(domainApp + "/dataExtract.do")).append("\" id=\"data-extract-form\">")
         .append(csrfField);
        h.append("<div class=\"aw-row\"><label for=\"extract-countries\">Countries</label>")
         .append("<input type=\"text\" id=\"extract-countries\" name=\"countries\" size=\"60\" maxlength=\"4000\" value=\"")
         .append(Html.escAttr(countriesText)).append("\" placeholder=\"United States, Canada, Mexico\"></div>")
         .append("<p class=\"aw-note\">Names as on AntWeb's country pages, separated by commas. A name AntWeb does not know stops the extract before it starts.</p>");
        h.append("<details id=\"extract-country-list\"").append(picked.isEmpty() ? "" : " open").append("><summary>Or pick from AntWeb's countries (")
         .append(countries.size()).append(")</summary><div class=\"aw-row\"><select name=\"country\" multiple size=\"12\" aria-label=\"Countries\">");
        for (String name : countries) {
            h.append("<option value=\"").append(Html.escAttr(name)).append("\"").append(picked.contains(name) ? " selected" : "").append(">")
             .append(Html.esc(name)).append("</option>");
        }
        h.append("</select></div><p class=\"aw-note\">Hold Ctrl (Windows) or Command (Mac) to pick several. Picked countries are added to the ones typed above.</p></details>");
        h.append("<div class=\"aw-row\"><label><input type=\"checkbox\" name=\"islands\" value=\"on\"").append(islands ? " checked" : "")
         .append("> Include islands filed under these countries</label></div>")
         .append("<p class=\"aw-note\">AntWeb treats some islands as countries of their own (Hawaii, for example, under United States). With the box ticked their specimens are in the extract, and the manifest lists the islands.</p>");
        h.append("<div class=\"aw-row\"><label><input type=\"checkbox\" name=\"allFamilies\" value=\"on\"").append(allFamilies ? " checked" : "")
         .append("> Include specimens that are not ants</label></div>")
         .append("<p class=\"aw-note\">Without a tick the extract holds ants only (family Formicidae, and specimens with no family given). AntWeb also holds beetles, wasps, spiders and other animals found with ants; the manifest says how many were left out, by family. Tick the box to have them in the file.</p>");
        h.append("<div class=\"aw-row\"><label><input type=\"checkbox\" name=\"noCountry\" value=\"on\"").append(noCountry ? " checked" : "")
         .append("> Also a file of the specimens that have no country</label></div>")
         .append("<p class=\"aw-note\">Some specimen records have no country at all, so no choice of countries finds them, and some of them belong to the countries you ask for. With the box ticked they come in a file of their own (specimens_no_country.csv.gz: all of AntWeb's, the same columns), for you or the collaborator to look through.</p>");
        h.append("<div class=\"aw-row\"><label for=\"extract-bioregion\">Species list of a bioregion (optional)</label><select id=\"extract-bioregion\" name=\"bioregion\">")
         .append("<option value=\"\">none</option>");
        for (String name : bioregions) {
            h.append("<option value=\"").append(Html.escAttr(name)).append("\"").append(name.equalsIgnoreCase(bioregion) ? " selected" : "").append(">")
             .append(Html.esc(name)).append("</option>");
        }
        h.append("</select></div>")
         .append("<p class=\"aw-note\">Adds bioregion_list.csv: the species AntWeb lists for that bioregion, each with its number of specimens in this extract. A listed species with 0 comes from the literature or from a country outside the extract.</p>");
        h.append("<div class=\"aw-row\"><label for=\"extract-note\">Note (optional)</label><input type=\"text\" id=\"extract-note\" name=\"note\" size=\"60\" maxlength=\"")
         .append(DataExtractJob.MAX_NOTE).append("\" value=\"").append(Html.escAttr(note)).append("\" placeholder=\"Who it is for\"></div>")
         .append("<p class=\"aw-note\">For your own records (shown here and on the job's page). It is not written into the files.</p>");
        h.append("<div class=\"aw-actions\"><input class=\"aw-btn\" type=\"submit\" value=\"Start the extract\"></div>")
         .append("<p class=\"aw-note\">The extract runs in the background and takes a few minutes for a large country. You can follow it under")
         .append(" <a href=\"").append(Html.escAttr(domainApp + "/jobStatus.do")).append("\">").append(JobStatusAction.PAGE_TITLE).append("</a>.")
         .append(" It cannot be started while the daily update runs (05:00 to about 08:00 Pacific).</p>");
        h.append("</form></div>");
        return h.toString();
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return String.format("%,d KB", Math.max(1, bytes / 1024));
        return String.format("%,d MB", bytes / 1024 / 1024);
    }

    public static String listHtml(List<DataExtractFiles.Entry> extracts, int activeJobId, String csrfField, String domainApp) {
        StringBuilder h = new StringBuilder("<div class=\"aw-card\" id=\"data-extract-list\"><h2>Extracts on the server (").append(extracts.size()).append(")</h2>");
        h.append("<p class=\"aw-lead\">Kept for ").append(DataExtractFiles.KEEP_DAYS).append(" days, then removed automatically. Only administrators can download them.</p>");
        if (extracts.isEmpty()) return h.append("<p class=\"aw-note\">None yet.</p></div>").toString();
        long total = 0;
        h.append("<table id=\"data-extract-table\"><tr><th>Date</th><th>Started by</th><th>Countries</th><th>Note</th><th>Specimens</th>"
            + "<th>Check</th><th>Files</th><th>Job</th><th></th></tr>");
        for (DataExtractFiles.Entry e : extracts) {
            total += e.bytes;
            String filters = String.join(", ", e.countries) + (e.islands ? "; with islands" : "; without islands")
                + (e.allFamilies ? "; all families" : "; ants only") + (e.noCountry ? "; with the no-country file" : "")
                + (e.bioregion.isEmpty() ? "" : "; species list of " + e.bioregion);
            h.append("<tr id=\"extract-").append(e.jobId).append("\"><td>").append(e.day).append("</td><td>").append(Html.esc(e.loginName)).append("</td><td>")
             .append(Html.esc(filters)).append("</td><td>").append(Html.esc(e.note)).append("</td><td class=\"aw-num\">")
             .append(e.specimens == null ? "" : String.format("%,d", e.specimens)).append("</td><td>");
            if (e.verified == null) {
                h.append(e.jobId == activeJobId ? "in progress" : "<span class=\"aw-bad\">did not finish</span>");
            } else {
                h.append(e.verified ? "<span class=\"aw-ok\">VERIFIED</span>" : "<span class=\"aw-bad\">NOT VERIFIED</span> do not send");
                if (!e.emptyCountries.isEmpty()) {
                    h.append("<div class=\"aw-warn\">No specimen for: ").append(Html.esc(String.join(", ", e.emptyCountries))).append("</div>");
                }
            }
            h.append("</td><td>");
            if (!e.files.isEmpty()) {
                h.append("<ul class=\"aw-links\">").append(DataExtractJob.downloadLinks(e.jobId, e.files, Boolean.TRUE.equals(e.verified), domainApp))
                 .append("</ul>").append(size(e.bytes));
            }
            h.append("</td><td><a href=\"").append(Html.escAttr(domainApp + "/jobStatus.do?id=" + e.jobId)).append("\">").append(e.jobId).append("</a></td><td>");
            if (e.jobId != activeJobId) {
                h.append("<form class=\"aw-inline-form\" method=\"post\" action=\"").append(Html.escAttr(domainApp + "/dataExtract.do")).append("\">")
                 .append(csrfField).append("<input type=\"hidden\" name=\"op\" value=\"delete\"><input type=\"hidden\" name=\"job\" value=\"")
                 .append(e.jobId).append("\"><input class=\"aw-btn aw-btn-secondary\" type=\"submit\" value=\"Delete\" aria-label=\"Delete the files of extract ")
                 .append(e.jobId).append("\"></form>");
            }
            h.append("</td></tr>");
        }
        h.append("</table><p class=\"aw-note\">Together these files use ").append(size(total)).append(" on the server (limit ")
         .append(size(DataExtractFiles.MAX_TOTAL_BYTES)).append("; above it no new extract starts). Delete removes an extract's files at once;")
         .append(" its job stays listed under ").append(JobStatusAction.PAGE_TITLE).append(".</p></div>");
        return h.toString();
    }
}

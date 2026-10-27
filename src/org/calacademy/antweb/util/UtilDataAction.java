package org.calacademy.antweb.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.calacademy.antweb.*;
import org.calacademy.antweb.curate.OperationDetails;
import org.calacademy.antweb.curate.speciesList.SpeciesListUploader;
import org.calacademy.antweb.geolocale.Geolocale;
import org.calacademy.antweb.home.*;
import org.calacademy.antweb.search.AdvancedSearchAction;
import org.calacademy.antweb.search.Output;
import org.calacademy.antweb.search.ResultRank;
import org.calacademy.antweb.search.SearchAction;
import org.calacademy.antweb.upload.UploadDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/*
// Requests look something like:  
   https://www.antweb.org/utilData.do?action=deleteConflictedDefaultImages
*/

public class UtilDataAction extends AntwebAction {

    private static final Log s_log = LogFactory.getLog(UtilDataAction.class);

    private static String m_inComputeProcess;
    private static volatile long m_inComputeProcessSince = 0; // when m_inComputeProcess was set (ms), 0 if none

    // Every sub-action utilData.do handles. Anything else is answered with 404 before any other check
    // (cleanup batch 3 removed 30 unused, duplicate, test and retired jobs). 2026-10-19: the three
    // taxon count crawls are part of each page's Recalculate, testGeolocaleTaxonParentage is the
    // preview step of fixGeolocaleTaxonParentage, taxaOutsideOfNativeBioregion is a report
    // (query.do?action=taxaOutsideOfNativeBioregion).
    static final java.util.Set<String> ACTIONS = java.util.Set.of(
        "set1", "set2", "set3", "set4", "set5", "taxonWorksNightly",
        "worldantsFetchAndReload", "worldantsReload", "taxonFinish", "dataCleanup", "restoreParkedTaxonSets",
        "specimenNameUpdate", "specimenNameUpdatePreview",
        "generateGeolocaleTaxaFromSpecimens", "geolocaleCountCrawl", "geolocaleTaxonFix",
        "fixGeolocaleTaxonParentage", "updateGeolocaleParentHierarchy",
        "projectCountCrawl", "populateMuseum", "populateBioregion", "updateTaxonSets", "crawlForType",
        "imageCountCrawl", "placeImageCounts", "deleteConflictedDefaultImages", "genObjectMaps", "cleanupSpeciesListProxyRecords",
        "verifyProjTaxon", "regenerateAllAntweb", "runCountCrawls", "countReport",
        "genGoogleMapFunction", "delGoogleMapFunction", "genGroupObjectMaps",
        "calcCaste", "setIsRestart", "siteWarning", "genRecentContent");

    // Started by an admin from a page, a data job runs in the background (BackgroundJobs) and the
    // page answers at once with its status page (jobStatus.do), since 2026-10-20. These few stay
    // in the request: they are quick, or (fixGeolocaleTaxonParentage) their answer is a page with
    // a second button. The Scheduler's own calls (nightly run) always stay in the request.
    static final java.util.Set<String> IN_REQUEST = java.util.Set.of(
        "siteWarning", "setIsRestart", "fixGeolocaleTaxonParentage");

    public static boolean runsInBackground(String action, boolean isScheduler) {
        return !isScheduler && action != null && ACTIONS.contains(action) && !IN_REQUEST.contains(action);
    }


    public ActionForward execute(ActionMapping mapping, ActionForm form,
        HttpServletRequest request, HttpServletResponse response) {
                         
        HttpSession session = request.getSession();                
        HttpUtil.setUtf8(request, response); 
        
        Connection connection = null;
        
		UtilForm theForm = (UtilForm) form;
		String action = theForm.getAction();

        Login accessLogin = LoginMgr.getAccessLogin(request);
        Group accessGroup = GroupMgr.getAccessGroup(request);

        /*
          Indication of reloading of Manager objects can happen in the request with:
             example: &reload=geolocale
          Or it can just happen in 
        */        
        String reload = theForm.getReload();

        //ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;

        boolean isAllow = "allow".equals(theForm.getParam()); // Used by scheduler.

        String loginName = "Undefined";
        if (isAllow) loginName = "Scheduler";
        if (accessLogin != null) loginName = accessLogin.getName();

        String message = null;

        if (action == null || !ACTIONS.contains(action)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            request.setAttribute("message", "action not found:" + Html.esc(action));
            return mapping.findForward("message");
        }

        // Data jobs change or recompute data: administrators only, or the scheduler
        // (which passes the one-time secureCode). "param=allow" alone is not enough.
        boolean isAdmin = accessLogin != null && accessLogin.isAdmin();
        // The code comes in a request header from the container itself (SchedulerCall); a code in the URL is refused.
        boolean isScheduler = SchedulerCall.isScheduler(request);
        if (!isAdmin && !isScheduler) {
            s_log.warn("execute() refused action:" + action + " for " + loginName + " target:" + HttpUtil.getTarget(request));
            ActionForward denied = LoginMgr.mustBeAdmin(request, mapping);
            if (denied != null) return denied;
        }
        // From a browser, a job runs only by POST (CSRF-checked); a GET link shows a Run button.
        // The Scheduler's own GET calls carry the secureCode and go straight through.
        if (!isScheduler) {
            ActionForward confirm = PostOnly.confirm(request, mapping, "Run data job: " + action);
            if (confirm != null) return confirm;
        }

        // Being called from a getUrl() by Schedule.do, Scheduler.java.
        if (!isAllow) { 

          List<String> needNothingInit = new ArrayList<>(Arrays.asList("delGoogleMapFunction", "genGoogleMapFunction", "genObjectMaps"
                  , "genGroupObjectMaps"
                  , "populateBioregion", "siteWarning"));
          List<String> needTaxonInit = new ArrayList<>(List.of("worldantsReload"));

          if (needNothingInit.contains(action)) { 
            // do nothing.
          } else
          if (needTaxonInit.contains(action)) { 
            ActionForward a = Check.init(Check.TAXON, request, mapping); if (a != null) return a;
          }    

          if (accessGroup == null) {
              message = "Not Logged in";
          }
        }

        // A data job started by hand while another one runs (for example during the nightly
        // run) would overwrite the shared compute flag and clear it when it ends: refused.
        // The Scheduler's own calls are part of the running job and go through.
        if (message == null && !isScheduler && isInComputeProcess()) {
            message = "Another data job is running (" + Html.esc(getIsInComputeProcess()) + "). Please try again when it has finished.";
        }

        if (message == null && runsInBackground(action, isScheduler)) {
            return submitDataJob(action, theForm, accessLogin, accessGroup, loginName, request, mapping);
        }

        if (message == null) {
            String dbMethodName = DBUtil.getDbMethodName("UtilDataAction.execute()");
            try {
                UtilDataAction.setInComputeProcess(action);

                // The nightly downloads the AntCat file before set 1 takes its connection: it may try for up to an
                // hour (FetchRetry), and the long pool closes a connection held over 60 minutes (2026-10-25).
                if (isScheduler && "set1".equals(action)) SpeciesListUploader.prefetchWorldantsList();

                DataSource dataSource = getDataSource(request, "longConPool");
                connection = DBUtil.getConnection(dataSource, dbMethodName, HttpUtil.getTarget(request));
                connection.setAutoCommit(false);

                if (action != null) {

                    s_log.info("execute() " + form);

                    //if (AntwebProps.isDevMode()) throw new AntwebException("Test! XXX");

                    OperationDetails operationDetails = doAction(action, theForm, accessLogin, accessGroup, connection, request, mapping);

                    if (operationDetails instanceof UploadDetails) {
                        ((UploadDetails) operationDetails).finish(accessLogin, request, connection);
                    }

                    connection.commit();

                    ActionForward forward = operationDetails.findForward(mapping, request);
                    A.log("execute() action:" + action + " operationDetails:" + operationDetails + " forward:" + forward);
                    if (forward != null) return forward;
                }

                if (!isAllow) { // Scheduler will manage it's own reloading.
                    s_log.info("execute() AntwebMgr.reload: " + reload);
                    String t = AntwebMgr.reload(reload, true, connection);
                } else {
                    s_log.info("execute() AntwebMgr.reload NOT reloaded because allow.");
                }

                request.setAttribute("message", "action not found:" + Html.esc(action));
                return mapping.findForward("message");

            } catch (Exception e) {
                message = handleException(e, connection, action, loginName);
            } finally {
                UtilDataAction.setInComputeProcess(null);
                DBUtil.close(connection, this, dbMethodName);
            }
        }
        request.setAttribute("message", message);
        return mapping.findForward("message");
    }

    /** Whether the nightly name update moved specimens to other names, so the Global counts must be rebuilt. */
    public static boolean globalRecountNeeded(org.calacademy.antweb.upload.SpecimenNameUpdate.Summary summary) {
        return summary != null && org.calacademy.antweb.upload.SpecimenNameUpdate.CHANGED.equals(summary.rowMode)
            && !summary.capped && summary.specimens > 0;
    }

    /**
     * Nightly set 1: the name update (SpecimenNameUpdate) with the setting in etc/name-update.properties.
     * Runs only if tonight's AntCat import succeeded. Its own commit and rollback: a failure here
     * does not undo the AntCat import or the TaxonWorks upload. Writes one line to compute.log.
     */
    static String nightlyNameUpdate(Connection connection, String worldantsMessage) {
        org.calacademy.antweb.upload.SpecimenNameUpdate.Settings settings = org.calacademy.antweb.upload.SpecimenNameUpdate.readSettings();
        String line;
        if (org.calacademy.antweb.upload.SpecimenNameUpdate.OFF.equals(settings.mode)) {
            line = "name update: off" + (settings.note != null ? " (" + settings.note + ")" : "");
        } else if (!"success".equals(worldantsMessage)) {
            line = "name update: skipped, tonight's AntCat import did not succeed (" + worldantsMessage + ")";
        } else {
            try {
                connection.commit();   // what set 1 did so far stays, whatever happens below
                org.calacademy.antweb.upload.SpecimenNameUpdate.Summary summary =
                    org.calacademy.antweb.upload.SpecimenNameUpdate.runLocked(connection, settings.mode, settings.maxChanges);
                connection.commit();
                line = summary.line();
                if (globalRecountNeeded(summary)) {
                    // The "Global" column (allantwebants) is rebuilt only by uploads; sets 2 to 5 skip it, so
                    // after the name update moved specimens it showed the old counts until the next upload
                    // (2026-10-21). Same rebuild as after an upload.
                    try {
                        new ProjTaxonDb(connection).regenerateAllAntweb();
                        connection.commit();
                        line += "; Global column recounted";
                    } catch (Exception e) {
                        try { connection.rollback(); } catch (SQLException e2) { s_log.error("nightlyNameUpdate() rollback e:" + e2); }
                        s_log.error("nightlyNameUpdate() Global recount e:" + e);
                        line += "; Global column NOT recounted (" + e + ")";
                    }
                }
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException e2) { s_log.error("nightlyNameUpdate() rollback e:" + e2); }
                s_log.error("nightlyNameUpdate() e:" + e);
                line = "name update: FAILED, nothing changed (" + e + ")";
            }
        }
        LogMgr.appendLog("compute.log", "  " + line, true);
        return line;
    }

    /**
     * Nightly set 1 (B36, docs/numbers.md): every ant species and subspecies of a counted status that is its own current
     * valid name and is identified to genus gets its genus row as parent, where that genus row exists
     * (TaxonParentDb.repair). No genus row is ever made (Brian Fisher, 2026-10-22): names whose genus has no row are
     * reported in the line, and valid ones (an AntCat import problem) in an admin alert, at most once a week.
     * Its own commit and rollback, like the name update. When it changed a row, the AntCat list (worldants, counted by
     * the import before this step) is recounted and the Global column (allantwebants) rebuilt, as after the name update
     * (sets 2 to 5 do neither). Writes one line to compute.log.
     */
    /** The Global column (allantwebants): rows counted, then the project's totals (B65). One line for compute.log. */
    static String recountGlobal(Connection connection) {
        long start = System.currentTimeMillis();
        String line;
        try {
            new ProjTaxonCountDb(connection).childrenCountCrawl(Project.ALLANTWEBANTS);
            new ProjectDb(connection).updateCounts(Project.ALLANTWEBANTS);
            connection.commit();
            line = "Global column recounted after the clean-up in " + (System.currentTimeMillis() - start) / 1000 + " s";
        } catch (Exception e) {
            try { connection.rollback(); } catch (SQLException e2) { s_log.error("recountGlobal() rollback e:" + e2); }
            s_log.error("recountGlobal() e:" + e);
            line = "Global column NOT recounted after the clean-up (" + e + ")";
        }
        LogMgr.appendLog("compute.log", "  " + line, true);
        return line;
    }

    static String nightlyTaxonParents(Connection connection) {
        String line;
        try {
            connection.commit();   // what set 1 did so far stays, whatever happens below
            org.calacademy.antweb.home.TaxonParentDb.Result result = new org.calacademy.antweb.home.TaxonParentDb(connection).repair();
            connection.commit();
            line = result.line();
            if (result.alert() != null) {
                AdminAlertMgr.addIfFresh(result.alert(), org.calacademy.antweb.home.TaxonParentDb.MISSING_VALID_ALERT, AdminAlertMgr.WEEK, connection);
            }
            if (result.changed()) {
                try {
                    // the AntCat list (worldants) was counted by the import, before this step; sets 2 to 5 skip it
                    new ProjTaxonCountDb(connection).childrenCountCrawl(Project.WORLDANTS);
                    new ProjTaxonDb(connection).regenerateAllAntweb();
                    connection.commit();
                    line += "; AntCat list and Global column recounted";
                } catch (Exception e) {
                    try { connection.rollback(); } catch (SQLException e2) { s_log.error("nightlyTaxonParents() rollback e:" + e2); }
                    s_log.error("nightlyTaxonParents() recount e:" + e);
                    line += "; AntCat list and Global column NOT recounted (" + e + ")";
                }
            }
        } catch (Exception e) {
            try { connection.rollback(); } catch (SQLException e2) { s_log.error("nightlyTaxonParents() rollback e:" + e2); }
            s_log.error("nightlyTaxonParents() e:" + e);
            line = "taxon parents (B36): FAILED, nothing changed (" + e + ")";
        }
        LogMgr.appendLog("compute.log", "  " + line, true);
        return line;
    }

    /** A data job started from a page: runs in the background; the answer is a redirect to its status page. */
    private ActionForward submitDataJob(String action, UtilForm form, Login accessLogin, Group accessGroup, String loginName,
                                        HttpServletRequest request, ActionMapping mapping) {
        BackgroundJobDb.setDataSource(getDataSource(request, "conPool"));
        final DataSource dataSource = getDataSource(request, "longConPool");
        StringBuilder title = new StringBuilder("Data job: " + action);
        if (form.getNum() != 0) title.append(" num=").append(form.getNum());
        if (form.getName() != null && !form.getName().isEmpty()) title.append(" name=").append(form.getName());
        if (form.getCode() != null && !form.getCode().isEmpty()) title.append(" code=").append(form.getCode());
        if (form.getGroupId() != 0) title.append(" groupId=").append(form.getGroupId());
        BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.DATA, action, title.toString(),
            accessLogin == null ? 0 : accessLogin.getId(), loginName, 0);
        BackgroundJobs.Submitted submitted = BackgroundJobs.get().submit(job,
            j -> runDataJob(action, form, accessLogin, accessGroup, loginName, dataSource));
        if (submitted.job == null) {
            request.setAttribute("message", Html.esc(submitted.refused));
            return mapping.findForward("message");
        }
        s_log.warn("submitDataJob() job " + submitted.job.getId() + " " + title + " by " + loginName + (submitted.isNew ? "" : " (already queued)"));
        return new ActionForward("/jobStatus.do?id=" + submitted.job.getId() + (submitted.isNew ? "" : "&already=1"), true);
    }

    /** The work of a data job started from a page (what execute() does in the request). */
    private BackgroundJobs.Outcome runDataJob(String action, UtilForm form, Login accessLogin, Group accessGroup, String loginName,
                                              DataSource dataSource) throws Exception {
        while (!claimComputeProcess(action)) {
            BackgroundJobs.step("Waiting for the nightly data update (or another data job) to finish. This job starts right after it.");
            Thread.sleep(5000);
        }
        BackgroundJobs.step("Running " + action + ".");
        Connection connection = null;
        String dbMethodName = DBUtil.getDbMethodName("UtilDataAction.runDataJob()");
        try {
            connection = DBUtil.getConnection(dataSource, dbMethodName);
            connection.setAutoCommit(false);
            OperationDetails operationDetails = doAction(action, form, accessLogin, accessGroup, connection, null, null);
            String link = null;
            if (operationDetails instanceof UploadDetails) {
                ((UploadDetails) operationDetails).finish(accessLogin, null, connection);
                link = org.calacademy.antweb.upload.SpecimenUploadJob.reportUrl((UploadDetails) operationDetails);
            }
            connection.commit();
            LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " " + action + " (background) by " + loginName + " done");
            return BackgroundJobs.Outcome.ok(JobSummary.dataJob(operationDetails.getMessage()), link, "Report");
        } catch (Exception e) {
            return BackgroundJobs.Outcome.failed(handleException(e, connection, action, loginName));
        } finally {
            releaseComputeProcess(action);
            DBUtil.close(connection, this, dbMethodName);
        }
    }

    private static String handleException(Exception e, Connection connection, String action, String loginName) {
        String message = "Error. No changes made for action:" + Html.esc(action) + " user:" + Html.esc(loginName) + " e:" + Html.esc(e);
        s_log.error("handleException() " + message);
        if (e instanceof NullPointerException) s_log.error(AntwebUtil.getShortStackTrace(e));
        try {
            if (connection != null) connection.rollback();
        } catch (SQLException e2) {
            s_log.error("handleException() e2:" + e2 + " for action:" + action + " user:" + loginName);
            AntwebUtil.logStackTrace(e2);
        }
        return message;
    }

    private OperationDetails doAction(String action, UtilForm form, Login accessLogin, Group accessGroup, Connection connection, HttpServletRequest request, ActionMapping mapping) 
      throws AntwebException, IOException, SQLException {
      
        OperationDetails operationDetails = new OperationDetails(action);
        
		String message = null;
		Date startTime = new Date();

        String param = form.getParam();
        String param2 = form.getParam2();
		int num = form.getNum();
		String name = form.getName();
		int groupId = form.getGroupId();
        String code = form.getCode();	
        String text = form.getText();	
        String taxonName = form.getTaxonName();
        String prop = form.getProp();

        boolean isAdmin = accessLogin != null && accessLogin.isAdmin();
        // request is null for a job run in the background (started by an admin).
        boolean isSecure = (request != null && SchedulerCall.isScheduler(request)) || isAdmin;
        
        s_log.info("doAction() " + action + " request:" + request + " isSecure:" + isSecure + " isAdmin:" + isAdmin);

        if (isSecure) {
            //Must be followed with the Count Crawls
            // dev: 11.68 mins  Or 24.98 mins? Prod: 11.23 mins. Dev2: 0.90 mins (presumably failure to access worldants file)
            if ("set1".equals(action)) {
                message = "set1 - ";
                OperationDetails worldants = doAction("worldantsFetchAndReload", form, accessLogin, accessGroup, connection, request, mapping);
                message += worldants;
                message += " " + doAction("taxonFinish", form, accessLogin, accessGroup, connection, request, mapping);
                // CAS specimens from TaxonWorks (GitHub #27). Does nothing without etc/taxonworks.properties.
                // Waits up to 30 minutes (Brian Fisher, 2026-10-24; was 90) for the archive TaxonWorks builds after this run's
                // request (see TaxonWorksNightly.run); usually a few minutes.
                message += " taxonWorksNightly: " + org.calacademy.antweb.upload.TaxonWorksNightly.run(connection, 30);
                // Existing specimens follow AntCat name changes (etc/name-update.properties; off without it).
                // After the AntCat import and the TaxonWorks upload, before sets 2 to 5 rebuild lists and counts.
                message += " " + nightlyNameUpdate(connection, worldants == null ? null : worldants.getMessage());
                // Every counted ant name has its genus as parent, so the genus rows of the lists count it (B36,
                // 2026-10-22). After the import, the upload and the name update; before sets 2 to 5 build the lists.
                message += " " + nightlyTaxonParents(connection);
                TaxonMgr.populate(connection, true, false);
            }

            // Dev: 31.50 mins and 25.75 mins. Prod: 43.93. Dev2: 26.87 mins
            if ("set2".equals(action)) {
                message = "set2 - ";
                message += doAction("dataCleanup", form, accessLogin, accessGroup, connection, request, mapping);
                // The clean-up deletes list rows (morphospecies without specimens, names no longer in the taxon table) from every
                // project, the Global column (allantwebants) too, which the specimen upload had counted before it and nothing
                // recounts after it: its genus and subfamily rows then counted more rows than they have (count check 2026-10-27,
                // e.g. Leptogenys 631 species with 623 rows; finding B65). Recount it here, as the upload does.
                message += " " + recountGlobal(connection);
                message += " " + doAction("generateGeolocaleTaxaFromSpecimens", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 12.30 mins
                // Synonym rows of the lists renamed to (or merged into) their current valid name BEFORE the counts
                // (2026-10-21). It ran only in set 3, after the geolocale and project totals: a merged synonym row
                // left its specimens in the total but not in any row, so Specimens was larger than the sum of the
                // species rows on every country, adm1, region and project (count check, place.totals.specimens).
                // Museum, bioregion and project lists are renamed in set 3, each just before it is counted (2026-10-22).
                message += " " + doAction("updateTaxonSets", form, accessLogin, accessGroup, connection, request, mapping);
                message += " " + doAction("geolocaleCountCrawl", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 30.48 mins
                // geolocaleTaxonFix no longer runs here (2026-10-22). It rewrote the Species number of every genus row of the
                // countries and live adm1s AFTER the count, from every list row whose parent is the genus, of any status and
                // rank: US formicinaeformica showed 104 with an unavailable name, 103 is right (count check inv:list.genus).
                // The count (GeolocaleTaxonCountDb.childrenCountCrawl, same places) already writes every genus row by the
                // rule of the lists (species and subspecies of a counted status), after resetting the row. It stays
                // available as an admin job (utilData.do?action=geolocaleTaxonFix), now by the same rule.
                //GeolocaleMgr.populate(connection, true, false); // Unnecessary. We reload all in set5.
            }

            // dev: 7.05 mins. Prod: 14.52 mins. Dev2: 12.85
            if ("set3".equals(action)) {
                message = "set3 - ";
                message += " " + doAction("projectCountCrawl", form, accessLogin, accessGroup, connection, request, mapping);   // Prod: 0.67 mins
                message += " " + doAction("populateMuseum", form, accessLogin, accessGroup, connection, request, mapping);  // was 12.05 mins now 22.68 mins. Prod: 8.17
                message += " " + doAction("populateBioregion", form, accessLogin, accessGroup, connection, request, mapping); // was 6.80 mins. Now 15.63 mins. // Prod: 5.52 mins
                // The rename of synonym rows (updateTaxonSets) ran here, after the project, museum and bioregion counts and
                // totals, so their pages counted lists that changed afterwards (2026-10-22, museum AFRC: 14 valid species rows
                // not in its Species number). Each list is now renamed right before it is counted (ProjTaxonCountDb.recount,
                // MuseumDb.populate, BioregionDb.populate; geolocales in set 2); only the source column is updated here.
                message += " " + TaxonSetDb.updateTaxonSetSources(connection);
                // Upload groups: today's numbers of each group's specimens (Brian Fisher, 2026-10-22, Q13).
                message += " " + new GroupCountDb(connection).recountAll();
                message += " " + doAction("crawlForType", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // dev: 6.58 mins. Prod: 15.17 mins. Dev2: 12.97
            if ("set4".equals(action)) {
                message = "set4 - ";
                // message += doAction("allCountCrawls", form, accessLogin, accessGroup, connection, request, mapping);
                message += doAction("imageCountCrawl", form, accessLogin, accessGroup, connection, request, mapping);
                // Images and imaged specimens of each place, from the image table (2026-10-20). After the
                // per-taxon image counts, so set 4 leaves every image number of the day in place.
                message += " " + doAction("placeImageCounts", form, accessLogin, accessGroup, connection, request, mapping);
                // Home page and statsPage numbers (2026-10-21): written only after specimen uploads, so images added
                // since were missing. Now also every night, after the image counts. Its own try: a failure here
                // must not stop set 4.
                try {
                    new StatisticsDb(connection).populateStatistics("nightly", 0, "set4", AntwebProps.getDocRoot());
                    message += " statistics";
                } catch (Exception e) {
                    s_log.error("set4 populateStatistics() e:" + e);
                    message += " statistics not written (" + e + ")";
                }
                // countCheck.do reads this line: tonight's numbers follow the rules of 2026-10-21 (PlaceTotals).
                // Since 2026-10-27 also: the lists' Images are ant images (StatisticsDb.ANT_IMAGES_RULE_LOG; CountCheck).
                LogMgr.appendLog("compute.log", "  Set 4 done (" + CountInvariants.RULES_LOG + "; " + StatisticsDb.ANT_IMAGES_RULE_LOG + ")", true);
                // message += " " + doAction("calcEndemic", form, accessLogin, accessGroup, connection, request, mapping);
                // message += " " + doAction("calcIntroduced", form, accessLogin, accessGroup, connection, request, mapping);
            }

            // dev: 15.22 mins. Prod: 50.98 mins. Dev2: 3.98 mins
            if ("set5".equals(action)) {
                message = "set5 - ";
                message += " " + doAction("deleteConflictedDefaultImages", form, accessLogin, accessGroup, connection, request, mapping);
                s_log.info("Done deleteConflictedDefaultImages. Starting genObjectMaps...");

                message += " " + doAction("genObjectMaps", form, accessLogin, accessGroup, connection, request, mapping); // Prod: 48.93 mins
                s_log.info("Done genObjectMaps. Starting cleanupSpeciesListProxyRecords...");

                message += " " + doAction("cleanupSpeciesListProxyRecords", form, accessLogin, accessGroup, connection, request, mapping);
                s_log.info("Done cleanupSpeciesListProxyRecords. Starting verifyProjTaxon...");

                message += " " + doAction("verifyProjTaxon", form, accessLogin, accessGroup, connection, request, mapping);
                s_log.info("Done verifyProjTaxon.");

                AntwebMgr.populate(connection, true);
            }

        }
		// ---------- Data Loading --------------------

		// Fetch the CAS TaxonWorks Darwin Core archive and upload it (GitHub #27).
		if (action.equals("taxonWorksNightly")) {
		  message = "taxonWorksNightly: " + org.calacademy.antweb.upload.TaxonWorksNightly.run(connection);
		}

		// 17.67 mins. success in 13.45 mins. success in 12.28 mins (after 1 constraint check removed).
		// success in 12.97 mins.		
		if (action.equals("worldantsFetchAndReload")) { 
		  // The nightly (set 1) uses the file downloaded before it took its connection (prefetchWorldantsList, up to an
		  // hour of tries) and alerts when there is none; an admin's run downloads once (FetchRetry).
		  boolean nightly = request != null && SchedulerCall.isScheduler(request);
		  operationDetails = new SpeciesListUploader(connection).worldantsFetchAndReload(nightly);

		  // UtilData.do?action=worldantsCount Feb2022
          new ProjTaxonCountDb(connection).childrenCountCrawl("worldants");

          LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " worldantsFetchAndReload: " + operationDetails.getMessage());
		  
          ((UploadDetails) operationDetails).finish(accessLogin, request, connection);
    		  
          return operationDetails;		  
		}
		if (action.equals("worldantsReload")) {
		  operationDetails = new SpeciesListUploader(connection).worldantsReload();
		  if ("success".equals(operationDetails.getMessage())) message = "worldantsReload";		
		  LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " worldantsReload: " + operationDetails.getMessage());
          s_log.debug("doAction() operationDetails:" + operationDetails + " message:" + operationDetails.getMessage());
          return operationDetails;
		}

        // 35 sec
		if (action.equals("taxonFinish")) { 
		  new TaxonDb(connection).setSubfamilyChartColor();
		  message = "taxonFinished";
		}

/*
        // The country and bioregion are included in worldants file. Set source (and links)
        // for these records (as they are most authoritative).
		if (action.equals("worldantsSource")) { 
		  (new GeolocaleTaxonDb(connection)).worldantsSource();
		  (new BioregionTaxonDb(connection)).worldantsSource();
		  message = "worldantsSource";
		}


		if (action.equals("plaziData")) {
		  PlaziDataAction plaziDataAction = new PlaziDataAction();                    
		  plaziDataAction.fetchPlaziData(connection);
          message = "Plazi Data fetched";
		}
*/
		if (action.equals("geolocaleTaxonFix")) {
		  GeolocaleTaxonDb geolocaleTaxonDb = new GeolocaleTaxonDb(connection);
		  String result = geolocaleTaxonDb.geolocaleTaxonFix();
		  message = result;
		}		

        // ---------------Overviews -------------------
			  	
		// Update to Current Valid Name.  3.77 mins.
		// Test if needed: http://localhost/antweb/util.do?action=curiousQuery&name=geolocaleTaxaNotUsingCurrentValidName	  	
 		if (action.equals("updateTaxonSets")) {
            message = TaxonSetDb.updateTaxonSetTaxonNames(connection); // works on all taxon sets.
            message += TaxonSetDb.updateTaxonSetSources(connection); // works on all taxon sets.
        }
		   
        // This should only need to be run once. No! Species list tool will not allow addition.
        // But could have a specimen removed from a specimen upload. Keep it in the scheduler.
		if (action.equals("dataCleanup")) {
		  // First put back curator list rows parked on an earlier night whose name exists again
		  // (set 1 has just reloaded the specimens), then clean up (which parks before deleting).
		  message = new CuratorTaxonSetDb(connection).restore();
		  message += TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource(connection);
		  message += TaxonDb.deleteGeneraWithoutSpecimenOrAntcatSource(connection);
          message += TaxonSetDb.dataCleanup(connection);
		}

		// The nightly name update by hand (admin, POST): specimenNameUpdate uses the setting in
		// etc/name-update.properties; specimenNameUpdatePreview always runs in report mode (the
		// "Preview now" button of the Name updates page). See org.calacademy.antweb.upload.SpecimenNameUpdate.
		if (action.equals("specimenNameUpdate") || action.equals("specimenNameUpdatePreview")) {
		  org.calacademy.antweb.upload.SpecimenNameUpdate.Settings settings = org.calacademy.antweb.upload.SpecimenNameUpdate.readSettings();
		  String mode = action.equals("specimenNameUpdatePreview") ? org.calacademy.antweb.upload.SpecimenNameUpdate.REPORT : settings.mode;
		  org.calacademy.antweb.upload.SpecimenNameUpdate.Summary summary =
		      org.calacademy.antweb.upload.SpecimenNameUpdate.runLocked(connection, mode, settings.maxChanges);
		  org.calacademy.antweb.upload.SpecimenNameUpdate.log(summary);
		  LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " " + action + " by " + (accessLogin == null ? "scheduler" : accessLogin.getName()) + ": " + summary.line());
		  message = Html.esc(summary.line()) + "<br><br><a href='" + AntwebProps.getDomainApp() + "/nameUpdates.do'>Name update report</a>";
		}

		// Curator rows of region and project lists kept in taxon_set_parked (CuratorTaxonSetDb):
		// put back those whose name exists again. Also runs at the start of dataCleanup (nightly set 2).
		if (action.equals("restoreParkedTaxonSets")) {
		  message = new CuratorTaxonSetDb(connection).restore();
		}	

		/*
        if (action.equals("processLocalityNames")) {
            // DateUtil.runTests();
            SpecimenDb specimenDb = new SpecimenDb(connection);
            message = specimenDb.processLocalityNames();
        }
       */
		    		
		// ---- Geolocale ----------
		
		// 1.85 min
		//   Must come before the geolocale crawls
		if (action.equals("generateGeolocaleTaxaFromSpecimens")) {
		  message = new GeolocaleTaxonDb(connection).generateGeolocaleTaxaFromSpecimens();
		  message = "Finished Generate Geolocale Taxa From Specimens message:" + message;
		}

		// Very fast. Run occasionally to get adm1s in line with hierarchy. Should be rare. Unnecessary.
		//if (action.equals("geolocaleTaxonCheckIntegrity")) {
		// Two steps: first a preview that only counts the missing parent records, then (apply=true,
		// a second button) the fix that deletes the proxy records and inserts the missing ones.
		if (action.equals("fixGeolocaleTaxonParentage")) {
		  if ("true".equals(request.getParameter("apply"))) {
		    message = new GeolocaleTaxonDb(connection).fixGeolocaleTaxonParentage();
		  } else {
		    message = parentagePreviewHtml(new GeolocaleTaxonDb(connection).testGeolocaleTaxonParentage(), request);
		  }
		}
		
		// Geolocale counts updated 6.30 mins	  
		if (action.equals("geolocaleCountCrawl")) {
		  int geolocaleId = form.getNum();
		  if (geolocaleId > 0) {
			new GeolocaleDb(connection).updateCounts(geolocaleId);
			new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.GEOLOCALE, "" + geolocaleId);
		  } else {			    
            CountDb.s_isBulk = true;
			new GeolocaleDb(connection).updateCounts();   // ~13.38 mins.
            CountDb.s_isBulk = false;
		  }                 
		  message = "Updated Geolocale Counts. ";
		}

		if (action.equals("updateGeolocaleParentHierarchy")) {
		  // Geolocales form a hierarchy but we would also like to know region, subregion, country
		  // up the parent chain for any geolocale (not just parent). Easy enought in SQL to touch up
		  // region, subregion and country, but hard to get the subregion for adm1. Here we do that.
		  new GeolocaleDb(connection).updateGeolocaleParentHierarchy();
		  message = "Finished update Geolocale Parent Hierarchy";
		}		
		
			
		// ---- Bioregion ----------
		// Bioregion populated 1.93 mins
		// for instance: /utilData.do?action=populateBioregion&name=indomalaya  
		// 7 sec for afrotropical
		if (action.equals("populateBioregion")) {
		  String bioregion = form.getName();   // ex: Afrotropical		  
		  String appendStr = "";
		  if (bioregion != null) {
		    bioregion = Formatter.initCap(bioregion);
			appendStr += ":" + bioregion;
			new BioregionDb(connection).populate(bioregion);
		  } else {
			new BioregionDb(connection).populate();
		  }
		  message = "Bioregion populated" + appendStr;                
		}		  

        // ------ Museums ---------
		//Museum populated 7.22 mins
		if (action.equals("populateMuseum")) {
		  String museumCode = form.getCode();   
		  // ex: /utilData.do?action=populateMuseum&code=ZMHB finished in 4.07 mins
		  // JTLC, CASC

		  MuseumDb museumDb = new MuseumDb(connection);
		  String appendStr = "";
		  if (museumCode != null && !"".equals(museumCode)) {
			appendStr += ":" + museumCode;
			museumDb.populate(museumCode);
		  } else {
			museumDb.populate();
		  }
		  museumDb.updateMuseum();

		  message = "Museum populated" + appendStr;                 
		}

        // ------ Project ----------

		// Fast.  0.40 min.
		// http://localhost/antweb/utilData.do?action=updateProjectCounts&name=allantwebants
		if (action.equals("projectCountCrawl")) {
		  String projectName = form.getName();
		  String appendStr = "";
		  ProjectDb projectDb = new ProjectDb(connection);
		  // The list (proj_taxon counts) first, then the project totals, which sum the list's species rows
		  // (2026-10-21: the totals ran first and showed the day before's list counts every night).
		  if (projectName != null) {
			Project project = ProjectMgr.getProject(projectName);
			if (project != null && !Project.WORLDANTS.equals(projectName) && !Project.ALLANTWEBANTS.equals(projectName)) {
			  new ProjTaxonCountDb(connection).recount(project);
			}
			projectDb.updateCounts(projectName);
			new PlaceImageCountDb(connection).updatePlace(PlaceImageCountDb.PROJECT, projectName);
			appendStr += ":" + projectName;
		  } else {
			// The counts on the project species lists (proj_taxon), not only the project totals (2026-10).
			appendStr += " " + new ProjTaxonCountDb(connection).curatedProjectsCountCrawl();
			projectDb.updateCounts();
		  }
		  message = "Project" + appendStr + " counts updated";
		}

		if (action.equals("regenerateAllAntweb")) {
            ProjTaxonDb projTaxonDb = new ProjTaxonDb(connection);
            projTaxonDb.regenerateAllAntweb();            // Proj_taxon records
            message = "All Antweb regenerated";
        }


        if (action.equals("runCountCrawls")) {
            ProjTaxonCountDb projTaxonCountDb = (new ProjTaxonCountDb(connection));
            projTaxonCountDb.childrenCountCrawl(); // Proj_taxon counts
		    //projTaxonDb.finishRegenerateAllAntweb();
		    //projTaxonDb.updateCounts("allantwebants");      // Project counts

		    BioregionTaxonCountDb bioregionTaxonCountDb = (new BioregionTaxonCountDb(connection));
		    bioregionTaxonCountDb.childrenCountCrawl();

   		    GeolocaleTaxonCountDb geolocaleTaxonCountDb = (new GeolocaleTaxonCountDb(connection));
		    geolocaleTaxonCountDb.childrenCountCrawl(num);

  		    MuseumTaxonCountDb museumTaxonCountDb = (new MuseumTaxonCountDb(connection));
		    museumTaxonCountDb.childrenCountCrawl();

            TaxonSetDb.updateTaxonSetSources(connection);

            message = "Count Crawls Ran";
		}


    // ---------- Count Crawls -------------------------

        // Don't just do worldants. Do all projects...
        // Added Mar2022 to fix missing worldant counts...
        // https://antweb-stg/utilData.do?action=geolocaleTaxonCountCrawl&num=392
        // Useful for debugging? Code to execute and get results or a particular count.
		if (action.equals("countReport")) {
		  GeolocaleTaxonCountDb geolocaleTaxonCountDb = new GeolocaleTaxonCountDb(connection);
          if (num == 0) {
            message = "countCrawl expecting num:" + num + ". Running ChildrenCountCrawls for all geolocales.";
            s_log.info(message);
		  } else {
		    message = "Finished Geolocale Count Report (" + form.getNum() + ") " + geolocaleTaxonCountDb.countReport(num);
		  }
		}

		if (action.equals("imageCountCrawl")) {
		  ImageCountDb imageCountDb = new ImageCountDb(connection);
		  imageCountDb.imageCountCrawls();
		  message = "Finished Image Count Crawls";
		}

		// Images (all files, labels too), images per view and imaged specimens (an image of the ant,
		// not only of the label) of every place: PlaceImageCountDb. Nightly set 4. name=<type> does one
		// kind of place only (geolocale, bioregion, museum, project).
		if (action.equals("placeImageCounts")) {
		  PlaceImageCountDb placeImageCountDb = new PlaceImageCountDb(connection);
		  String type = form.getName();
		  if (type != null && java.util.Arrays.asList(PlaceImageCountDb.TYPES).contains(type)) {
		    message = "Place image counts: " + type + " " + placeImageCountDb.updateType(type) + " places with images";
		  } else {
		    message = placeImageCountDb.updateAll();
		  }
		  LogMgr.appendLog("compute.log", "  " + message, true);
		}

        // 37 sec.  Called following specimen upload.
		if (action.equals("crawlForType")) {
		  s_log.info("execute() crawlForType");
		  new TaxonDb(connection).crawlForType();
		  message = "Finished crawl for type";
		}


// ----------------- Assorted --------------------

        if ("setIsRestart".equals(action)) {
            boolean is = false;
            if ("true".equals(param)) is = true;
            message = "isRestart set:" + is;
            AntwebSystem.setIsRestart(is);
        }

        // Full set is 8.27 mins.  After transactions - 9.17 mins!
        // caste:1 in 3.45 mins
        // This is not run after specimen upload, but process does it.
        // Not sure if this is needed in the scheduler.
        if (action.equals("calcCaste")) {
          SpecimenDb specimenDb = new SpecimenDb(connection);
          //groupId = num;		
          // One of: a group, a specimen, or (neither given) every specimen.
          if (groupId > 0) {
            specimenDb.calcCaste(groupId);
            message = "calcCaste groupId:" + groupId;
          } else if (code != null) {
            specimenDb.calcCaste(code);
            message = "calcCaste code:" + Html.esc(code);
          } else {
            specimenDb.calcCaste();
            message = "calcCaste";
          }
        }
        
        if (action.equals("siteWarning")) {
          s_log.debug("text:" + text);
          // Shown on every page: only the page-text HTML allowlist is kept (HtmlSanitizer).
          text = HtmlSanitizer.sanitize(text == null ? "" : text);
          AntwebMgr.createSiteWarning(text);
          message = "SiteWarning set:" + Html.esc(text);
        }


        // If a species list proxy record is created for a species list species that is then removed... cleanup.
        if (action.equals("cleanupSpeciesListProxyRecords")) {
            String result = new ProjTaxonDb(connection).cleanupSpeciesListProxyRecords();
            message = "Cleanup Species List Proxy Records:" + result;
        }

        // If a species list proxy record is created for a species list species that is then removed... cleanup.
        if (action.equals("verifyProjTaxon")) {
            String result = new ProjTaxonDb(connection).verifyProjTaxon();
            message = "verifyProjTaxon Records:" + result;
        }

     // --- Map functions

        if (action.contains("delGoogleMapFunction")) {
          int id = num;
          if (id > 0) {
              ObjectMapDb objectMapDb = new ObjectMapDb(connection);
              objectMapDb.deleteMap("geolocale_id = " + id);                 
          }
          message = "Map deleted for geolocale id:" + id;
        }

        if (action.contains("genGoogleMapFunction")) {
            // param is country. param2 is adm1
            String country = param;
            String adm1 = param2;
            int id = num;

            //HttpUtil.getUrl(AntwebProps.getDomainApp() + "/utilData.do?action=genGoogleMapFunction&num=" + id + "&param=allow");
  
            s_log.info("action=genGoogleMapFunction id:" + id + " country:" + country + " adm1:" + adm1);
            Geolocale geolocale = null;
            if (adm1 != null) {
              geolocale = GeolocaleMgr.getAdm1(adm1, country);
            } else if (country != null) {
              geolocale = GeolocaleMgr.getCountry(country);
            } else if (id != 0) {
              geolocale = GeolocaleMgr.getGeolocale(num);
              if ("country".equals(geolocale.getGeorank())) {
                country = geolocale.getName();
              } else if ("adm1".equals(geolocale.getGeorank())) {
                country = geolocale.getParent();
                adm1 = geolocale.getName();                  
              }
              s_log.debug("UtilDataAction.genGoogleMapFunction georank:" + geolocale.getGeorank() + " geolocale:" + geolocale + " country:" + country + " adm1:" + adm1);
            }
            if (geolocale == null || !("country".equals(geolocale.getGeorank()) || "adm1".equals(geolocale.getGeorank()))) {
              message = "Geolocale not found for num(id):" + num + " param(country):" + Html.esc(param) + " param2(adm1):" + Html.esc(param2);
            } else {
              s_log.debug("UtilData.genGoogleMapFunction() id:" + id + " country:" + country + " adm1:" + adm1 + " geolocale:" + geolocale);
              SearchAction.setTempSpecimenSearchLimit(SearchAction.noSpecimenSearchLimit);

              Map map = new AdvancedSearchAction().getGoogleMap(country, adm1, ResultRank.SPECIMEN, Output.MAP_LOCALITY, connection);

              SearchAction.undoSetTempSpecimenSearchLimit();
            
              //String googleMapFunction = map.getGoogleMapFunction();
              //A.log("title:" + map.getTitle() + " subtitle:" + map.getSubtitle());
              //A.log("UtilData.genGoogleMapFunction googleMapFunction:" + googleMapFunction);

              if (map != null) {
                ObjectMapDb objectMapDb = new ObjectMapDb(connection);     
                objectMapDb.setGeolocaleMap(geolocale.getId(), map);                     
              }

              message = "title:" + map.getTitle() + " subtitle:" + map.getSubtitle() + " info:" + map.getInfo() + " googleMapFunction.length:" + map.getGoogleMapFunction().length();
            }
            if (message == null) message = "Map rebuilt.";
        }		
        
        if (action.contains("genObjectMaps")) {
            ObjectMapDb objectMapDb = new ObjectMapDb(connection);
            objectMapDb.genObjectMaps(); 
            message = "message: genObject Maps executed.";
        }

        if ("genGroupObjectMaps".equals(action)) {
            ObjectMapDb objectMapDb = new ObjectMapDb(connection);
            if (num > 0) {
              objectMapDb.genGroupObjectMap(num); 
            } else {
              objectMapDb.genGroupObjectMaps(); 				  
            }
            message = "genObject Maps executed.";
        }			  

        if (action.equals("deleteConflictedDefaultImages")) {
          message = new TaxonPropDb(connection).deleteConflictedDefaultImages();
        }

// ----------- Debug -------------
        
        if (action.equals("genRecentContent")) {
            AntwebMgr.genRecentContent(connection);
            message = "Recent content generated.";
        }
                    
/*
        // This to get geolocale_taxon in alignment with specimen.
        if (action.equals("processSpecimen")) {
            (new GeolocaleTaxonDb(connection)).processSpecimen();
            message = "Geolocale Taxon Process Specimen";
        }
        
        // To be called after data change. endemic data into geolocale_taxon and geolocale.
        if (action.equals("finishCountryUpload")) {
          message = (new GeolocaleTaxonDb(connection)).finishCountryUpload();
        }
*/
	   
        if ("".equals(message) || message == null) {
          s_log.info("doAction() message:" + message + " for action:" + action);
          operationDetails.setMessage("action:" + action + " not found");
          return operationDetails;
        }

		message += "<br><br> in " + AntwebUtil.getMinsPassed(startTime) + ". ";	   
	    s_log.info("doAction() Completed action:" + action + " message:" + message);
        operationDetails.setMessage(message);
        operationDetails.setForwardPage(null);
        return operationDetails;
    }    
    
    public static boolean isInComputeProcess() {
      return m_inComputeProcess != null;
    }
    public static String getIsInComputeProcess() {
      return m_inComputeProcess;
    }
    public static synchronized void setInComputeProcess(String inComputeProcess) {
      m_inComputeProcessSince = inComputeProcess == null ? 0 : System.currentTimeMillis();
      m_inComputeProcess = inComputeProcess;
    }
    /** Sets the compute flag if no data job holds it. True if this caller now holds it (background jobs). */
    public static synchronized boolean claimComputeProcess(String inComputeProcess) {
      if (m_inComputeProcess != null) return false;
      setInComputeProcess(inComputeProcess);
      return true;
    }
    /** Clears the compute flag if the caller still holds it. */
    public static synchronized void releaseComputeProcess(String inComputeProcess) {
      if (inComputeProcess != null && inComputeProcess.equals(m_inComputeProcess)) setInComputeProcess(null);
    }
    /** When the current compute process (set1, set2, ...) started, in ms; 0 if none. */
    public static long getInComputeProcessSince() {
      return m_inComputeProcessSince;
    }
  

    /** Preview page of fixGeolocaleTaxonParentage: what the check found, and a button that applies the fix. */
    static String parentagePreviewHtml(String preview, HttpServletRequest request) {
        String url = AntwebProps.getDomainApp() + "/utilData.do?action=fixGeolocaleTaxonParentage&apply=true";
        return "<h2>Geolocale taxon parentage: preview</h2>"
            + "<p>Nothing has been changed yet. The check found:</p><p>" + Html.esc(preview) + "</p>"
            + "<p>Apply the fix: remove the proxy records and insert the missing parent records "
            + "(country for each adm1 record, parent taxon for each record).</p>"
            + Csrf.postButton(request, url, "Apply the fix", "Change geolocale_taxon records now?");
    }
}

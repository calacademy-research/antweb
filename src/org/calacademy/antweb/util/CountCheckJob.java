package org.calacademy.antweb.util;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The count check (countCheck.do) as a background job (BackgroundJobs, 2026-10-21): the check of every place
 * and the rotating recount took 107 seconds on staging, and Cloudflare ends a request after 100. countCheck.do
 * starts this job (or shows the latest report), the browser follows it on jobStatus.do, and the job's result
 * links to the full report (countCheck.do?job=N). The nightly run starts the same job when it has finished
 * (Scheduler), so the check runs every day on staging and production, read-only. The end-to-end test starts
 * it, waits for it and reads the report.
 *
 * Reports are kept in memory (the last KEEP); after a restart the page offers to run the check again.
 */
public final class CountCheckJob {

    private static final Log s_log = LogFactory.getLog(CountCheckJob.class);

    /** The job's action (one count check at a time: BackgroundJobs does not start a second one). */
    public static final String ACTION = "countCheck";

    /** Seconds the whole check may run before the rotating recount stops (the rest is listed as not reached). */
    public static final int SECONDS = 300;

    static final int KEEP = 5;

    private static final Map<Integer, String> s_reports = new LinkedHashMap<>();
    private static volatile int s_latest;

    private CountCheckJob() {}

    /** The queued or running count check, or null. */
    public static BackgroundJobs.Job active() {
        return BackgroundJobs.get().findDuplicate(new BackgroundJobs.Job(BackgroundJobs.DATA, ACTION, "", 0, "", 0));
    }

    /** Starts the check (or returns the one already queued or running). by: who started it, for the title. */
    public static BackgroundJobs.Submitted submit(DataSource dataSource, int loginId, String loginName, int seconds, String by) {
        BackgroundJobs.Job job = new BackgroundJobs.Job(BackgroundJobs.DATA, ACTION, "Count check (" + by + ")", loginId, loginName, 0);
        return BackgroundJobs.get().submit(job, j -> run(j, dataSource, seconds));
    }

    static BackgroundJobs.Outcome run(BackgroundJobs.Job job, DataSource dataSource, int seconds) throws Exception {
        BackgroundJobs.step("Counting: every place, then the sample places (up to " + seconds + " seconds).");
        String name = DBUtil.getDbMethodName("CountCheckJob.run()");
        Connection connection = DBUtil.getConnection(dataSource, name);
        try {
            connection.setReadOnly(true);
            List<String> notes = new ArrayList<>();
            List<CountCheck.Place> places = CountCheckAction.places(null, null, null, null, connection, notes);
            List<CountCheck.Place> rotating = CountCheckAction.rotatingSample(connection, java.time.LocalDate.now().getDayOfYear());
            List<String> log = CountCheckAction.computeLog();
            Integer statsPage = null;
            try { statsPage = TaxonMgr.getValidSpeciesCount(); } catch (RuntimeException e) { /* taxa not loaded */ }
            CountCheck countCheck = new CountCheck(connection);
            countCheck.curatorNumbers = CountCheckAction.curatorNumbers();
            countCheck.artistNumbers = CountCheckAction.artistNumbers();
            CountCheck.Result result = countCheck.run(places, rotating, seconds, CountCheck.lastCompletedRun(log),
                CountCheck.regionRuleCounted(log), CountInvariants.rulesCounted(log), true, statsPage);
            result.notes.addAll(notes);
            String domainApp = AntwebProps.getDomainApp();
            String html = CountCheckAction.html(result, domainApp);
            keep(job.getId(), html);
            String summary = "Real differences: " + result.real() + ". Pending: " + result.pending() + ". " + CountCheckAction.partsText(result);
            LogMgr.appendLog("admin.log", DateUtil.getFormatDateTimeStr() + " count check (job " + job.getId() + "): " + summary);
            return BackgroundJobs.Outcome.ok(Html.esc(summary), domainApp + "/countCheck.do?job=" + job.getId(), "The count check report");
        } finally {
            try { connection.setReadOnly(false); } catch (Exception e) { /* closing */ }
            DBUtil.close(connection, name);
        }
    }

    public static synchronized void keep(int id, String html) {
        s_reports.put(id, html);
        s_latest = id;
        while (s_reports.size() > KEEP) s_reports.remove(s_reports.keySet().iterator().next());
    }

    /** The full report of a finished check, or null (not kept, or the server restarted). */
    public static synchronized String report(int id) {
        return s_reports.get(id);
    }

    /** The job number of the latest report, 0 when none. */
    public static int latest() {
        return s_latest;
    }

    /**
     * Started by the nightly run when it has finished (Scheduler): the job waits until the run's flags are clear,
     * then runs read-only, as on the page.
     */
    public static void startAfterNightly() {
        try {
            DataSource ds = DBUtilSimple.getDataSource();
            BackgroundJobDb.setDataSourceIfMissing(ds);
            BackgroundJobs.Submitted s = submit(ds, 0, "nightly", SECONDS, "after the nightly run");
            LogMgr.appendLog("compute.log", "  count check started (job " + (s.job == null ? "none: " + s.refused : s.job.getId()) + ")", true);
        } catch (RuntimeException e) {
            s_log.error("startAfterNightly() e:" + e);
        }
    }
}

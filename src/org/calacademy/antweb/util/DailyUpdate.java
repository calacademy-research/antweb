package org.calacademy.antweb.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The "Daily update" (the nightly run of the Scheduler, 05:00 Pacific; Brian Fisher, 2026-11-07: pages say "daily
 * update", never "nightly"): which step is running, what comes next, and whether a step is taking much longer than
 * usual or failed. Shown on dailyUpdate.do (DailyUpdateAction; JSON for the morning check), in the site's server
 * message (AntwebMgr) and emailed to antweb@antweb.org when a step fails or runs over twice its usual time, and when
 * the count check at its end finds real differences outside the known findings (2026-11-11, Brian Fisher: "how did we
 * not get an alert that the counts were not true": the check wrote "Real differences: N" into its job and the step
 * said done). That step then ends with the result warning: done, shown yellow with the number, one email.
 *
 * The steps are defined once (STEPS). The Scheduler and UtilDataAction mark them where they already write their
 * compute.log lines: start(key) ends the step that was running (ok, or failed / skipped when noted) and starts the next.
 * Only a full run (schedule run, all sets) is recorded; a set started by hand is not. Each step's start and end goes to
 * the table daily_update_step (DailyUpdateDb) on a thread of its own, on its own autocommit connection: recording never
 * touches the run's transaction, never waits for the database and never throws into the run (every exception is caught
 * and logged). "Usual" is the median duration of the step in the last USUAL_RUNS completed runs (DailyUpdateDb.usual).
 *
 * The pure functions (median, slow and overrun flags, the step view, the JSON) are unit-tested in DailyUpdateTest; the
 * table in DailyUpdateDbTest; the page and JSON on staging in tests/e2e/test_daily_update.py. Docs: docs/flows/nightly.md.
 */
public final class DailyUpdate {

    private static final Log s_log = LogFactory.getLog(DailyUpdate.class);

    public static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");

    /** Runs whose durations make the usual time of a step (median). */
    public static final int USUAL_RUNS = 7;
    /** A running or finished step is shown yellow over this times its usual time ... */
    public static final double SLOW_FACTOR = 1.5;
    /** ... and only when it is also at least this many seconds over (short steps: seconds of noise). */
    public static final long SLOW_MIN_EXTRA_SECONDS = 60;
    /** An email goes out once a step runs over this times its usual time ... */
    public static final double ALERT_FACTOR = 2.0;
    /** ... and at least this many seconds over its usual time (no email for a 10-second step that took 25). */
    public static final long ALERT_MIN_EXTRA_SECONDS = 300;
    /** How often a running step is checked against its usual time. */
    static final long WATCH_PERIOD_SECONDS = 60;
    /** The run ends without its count check's answer after this long (the job normally takes a few minutes). */
    static final long COUNT_CHECK_MAX_MS = 90L * 60 * 1000;

    // Step states (page and JSON) and stored results.
    public static final String DONE = "done", RUNNING = "running", WAITING = "waiting", SKIPPED = "skipped", FAILED = "failed";
    public static final String OK = "ok";
    /** A step that ran to its end and found something to look at (the count check with real differences): yellow, done. */
    public static final String WARNING = "warning";
    // Overall states of a run (JSON).
    public static final String RUN_RUNNING = "running", RUN_OK = "ok", RUN_FAILED = "failed";

    /** One step of the daily update. key: stored in daily_update_step.step and in the JSON; label: shown. */
    public static final class Step {
        public final String key;
        public final String label;
        public final String what;
        Step(String key, String label, String what) { this.key = key; this.label = label; this.what = what; }
    }

    public static final String END = "end";
    public static final String COUNT_CHECK = "countCheck";

    /** The steps, in the order they run. */
    public static final List<Step> STEPS = Collections.unmodifiableList(java.util.Arrays.asList(
        new Step("antcatImport", "AntCat import", "AntCat's names loaded into AntWeb (set 1)"),
        new Step("taxonWorks", "TaxonWorks upload", "CAS specimens from TaxonWorks (set 1; waits up to 30 minutes for the file)"),
        new Step("nameUpdate", "Name update", "specimens follow AntCat's name changes (set 1)"),
        new Step("set1Finish", "Set 1 clean-up", "every name gets its genus as parent; the name cache is reloaded"),
        new Step("set2", "Set 2: lists and place counts", "data clean-up, country and adm1 lists and their counts"),
        new Step("set3", "Set 3: projects, museums, bioregions, groups", "their lists and counts, type specimens"),
        new Step("set4", "Set 4: images and Not curated", "image counts of names and places, the Not curated column"),
        new Step("imagingProgress", "Imaging progress", "species imaged by bioregion, country, genus and museum (set 4; imagingProgress.do)"),
        new Step("placeListTotals", "Place list totals", "the Subfamilies, Genera and Species of each place page"),
        new Step("set5", "Set 5: maps and cache reload", "object maps; every cache reloaded (skipped on staging)"),
        new Step("logSizeCheck", "Log size check", "warns when a log or the disk grows too large"),
        new Step("statsPage", "Statistics page", "the tables of the statistics page"),
        new Step("apiCounts", "API taxon counts", "the counts the API gives for each name"),
        new Step("cacheReload", "Cache reload", "only where set 5 was skipped (staging)"),
        new Step(COUNT_CHECK, "Count check", "read-only check of the numbers just written (report: Check the counts)"),
        new Step(END, "End", "the daily update has finished")));

    private static final Map<String, Integer> INDEX = new HashMap<>();
    static {
        for (int i = 0; i < STEPS.size(); i++) INDEX.put(STEPS.get(i).key, i);
    }

    /** Position (0-based) of a step, -1 if unknown. */
    public static int index(String key) {
        Integer i = INDEX.get(key);
        return i == null ? -1 : i;
    }

    public static Step step(String key) {
        int i = index(key);
        return i < 0 ? null : STEPS.get(i);
    }

    private DailyUpdate() {}

    // ------------------------------------------------------------------ pure functions (unit-tested)

    /** The median of the values (the mean of the two middle ones for an even count, rounded down); null when none. */
    public static Long median(List<Long> values) {
        if (values == null || values.isEmpty()) return null;
        List<Long> v = new ArrayList<>(values);
        Collections.sort(v);
        int n = v.size();
        if (n % 2 == 1) return v.get(n / 2);
        return (v.get(n / 2 - 1) + v.get(n / 2)) / 2;
    }

    /** Yellow: over SLOW_FACTOR times the usual time and at least SLOW_MIN_EXTRA_SECONDS over it. */
    public static boolean isSlow(long seconds, Long usual) {
        return usual != null && seconds > SLOW_FACTOR * usual && seconds - usual >= SLOW_MIN_EXTRA_SECONDS;
    }

    /** Email: over ALERT_FACTOR times the usual time and at least ALERT_MIN_EXTRA_SECONDS over it. */
    public static boolean isOverrun(long seconds, Long usual) {
        return usual != null && seconds > ALERT_FACTOR * usual && seconds - usual >= ALERT_MIN_EXTRA_SECONDS;
    }

    /** A stored step: one row of daily_update_step. result: ok, failed, skipped, or null while it runs. */
    public static final class Row {
        public final String step;
        public final Date started;
        public final Date ended;
        public final String result;
        public final String detail;
        public Row(String step, Date started, Date ended, String result, String detail) {
            this.step = step; this.started = started; this.ended = ended; this.result = result; this.detail = detail;
        }
    }

    /** One line of the page and of the JSON. */
    public static final class StepView {
        public Step step;
        public int number;          // 1-based
        public String state;        // done, running, waiting, skipped, failed
        public Date started;
        public Date ended;
        public Long seconds;        // duration (done, failed) or elapsed (running)
        public Long usual;          // usual seconds, null when not known
        public Date estimatedEnd;   // running: started + usual
        public boolean slow;        // yellow
        public boolean warning;     // yellow: it ended with something to look at (result warning; the message says what)
        public String message;
    }

    /**
     * The steps of one run, in order. active: the run is going on in this server now (a step without an end is
     * running, a step without a row is waiting); otherwise a step without an end was interrupted (failed) and a step
     * without a row did not run (skipped).
     */
    public static List<StepView> view(List<Row> rows, Map<String, Long> usual, boolean active, long now) {
        Map<String, Row> byKey = new HashMap<>();
        if (rows != null) for (Row r : rows) byKey.put(r.step, r);
        List<StepView> out = new ArrayList<>();
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
            Row r = byKey.get(s.key);
            StepView v = new StepView();
            v.step = s;
            v.number = i + 1;
            v.usual = usual == null ? null : usual.get(s.key);
            if (r == null) {
                v.state = active ? WAITING : SKIPPED;
                if (!active) v.message = "did not run";
            } else {
                v.started = r.started;
                v.ended = r.ended;
                v.message = r.detail;
                if (FAILED.equals(r.result)) v.state = FAILED;
                else if (SKIPPED.equals(r.result)) v.state = SKIPPED;
                else if (r.ended != null) {
                    v.state = DONE;
                    v.warning = WARNING.equals(r.result);
                }
                else if (active) v.state = RUNNING;
                else {
                    v.state = FAILED;
                    v.message = "interrupted (the server restarted or the run stopped)";
                }
                if (r.started != null && !SKIPPED.equals(v.state)) {
                    long end = r.ended != null ? r.ended.getTime() : (RUNNING.equals(v.state) ? now : -1);
                    if (end >= 0) v.seconds = Math.max(0, (end - r.started.getTime()) / 1000);
                }
                if (RUNNING.equals(v.state) && v.usual != null && r.started != null) {
                    v.estimatedEnd = new Date(r.started.getTime() + v.usual * 1000);
                }
                v.slow = v.seconds != null && (DONE.equals(v.state) || RUNNING.equals(v.state)) && isSlow(v.seconds, v.usual);
            }
            out.add(v);
        }
        return out;
    }

    /** running while active; failed when a step failed or was interrupted; ok otherwise. */
    public static String overallState(List<StepView> steps, boolean active) {
        if (active) return RUN_RUNNING;
        for (StepView v : steps) if (FAILED.equals(v.state)) return RUN_FAILED;
        for (StepView v : steps) if (END.equals(v.step.key) && DONE.equals(v.state)) return RUN_OK;
        return RUN_FAILED;
    }

    /** Minutes left by the usual times (the running step's rest and the waiting steps), null when none is known. */
    public static Long minutesLeft(List<StepView> steps) {
        long seconds = 0;
        boolean known = false;
        for (StepView v : steps) {
            if (v.usual == null) continue;
            if (RUNNING.equals(v.state)) {
                known = true;
                seconds += Math.max(0, v.usual - (v.seconds == null ? 0 : v.seconds));
            } else if (WAITING.equals(v.state)) {
                known = true;
                seconds += v.usual;
            }
        }
        return known ? (seconds + 59) / 60 : null;
    }

    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** HH:mm in Pacific time, "" for null. */
    public static String hhmm(Date d) {
        return d == null ? "" : LocalDateTime.ofInstant(d.toInstant(), PACIFIC).format(HHMM);
    }

    /** ISO 8601 without zone, Pacific time (agreed with the morning check), null for null. */
    public static String iso(Date d) {
        return d == null ? null : LocalDateTime.ofInstant(d.toInstant(), PACIFIC).format(ISO);
    }

    /** "12 min", "1 h 05 min", "40 s". */
    public static String duration(Long seconds) {
        if (seconds == null) return "";
        if (seconds < 60) return seconds + " s";
        long m = seconds / 60;
        if (m < 60) return m + " min";
        return (m / 60) + " h " + String.format("%02d", m % 60) + " min";
    }

    /** "Step N of M: <step>, started HH:MM, usually X min; next: <step>" for the running step, null when none runs. */
    public static String hover(List<StepView> steps) {
        for (int i = 0; i < steps.size(); i++) {
            StepView v = steps.get(i);
            if (!RUNNING.equals(v.state)) continue;
            StringBuilder b = new StringBuilder("Step " + v.number + " of " + steps.size() + ": " + v.step.label
                + ", started " + hhmm(v.started) + " Pacific");
            if (v.usual != null) b.append(", usually ").append(v.usual < 60 ? "under 1 min" : duration(v.usual));
            for (int j = i + 1; j < steps.size(); j++) {
                if (WAITING.equals(steps.get(j).state)) {
                    b.append("; next: ").append(steps.get(j).step.label);
                    break;
                }
            }
            return b.toString();
        }
        return null;
    }

    /** The plain line for visitors: "AntWeb is running its daily update (about N minutes left)." */
    public static String publicLine(Long minutesLeft) {
        if (minutesLeft == null) return "AntWeb is running its daily update; some services may be slow or down.";
        if (minutesLeft <= 1) return "AntWeb is running its daily update (almost done).";
        return "AntWeb is running its daily update (about " + minutesLeft + " minutes left).";
    }

    /** One run for the JSON (keys agreed with the production morning check, 2026-11-07). */
    public static JsonObject runJson(Date runStart, List<StepView> steps, boolean active) {
        JsonObject run = new JsonObject();
        run.addProperty("started", iso(runStart));
        Date end = null;
        for (StepView v : steps) if (END.equals(v.step.key) && v.ended != null) end = v.ended;
        if (end == null || active) run.add("ended", JsonNull.INSTANCE); else run.addProperty("ended", iso(end));
        run.addProperty("state", overallState(steps, active));
        JsonArray arr = new JsonArray();
        for (StepView v : steps) {
            JsonObject s = new JsonObject();
            s.addProperty("name", v.step.key);
            s.addProperty("label", v.step.label);
            s.addProperty("state", v.state);
            if (v.started == null) s.add("started", JsonNull.INSTANCE); else s.addProperty("started", iso(v.started));
            if (v.ended == null) s.add("ended", JsonNull.INSTANCE); else s.addProperty("ended", iso(v.ended));
            if (v.usual == null) s.add("usual_seconds", JsonNull.INSTANCE); else s.addProperty("usual_seconds", v.usual);
            // a step that ended with a warning (the count check with real differences) is "done"; its message says what
            // (the keys are agreed with the production morning check: none is added)
            s.addProperty("message", v.message == null ? "" : v.message);
            arr.add(s);
        }
        run.add("steps", arr);
        return run;
    }

    /** The whole JSON answer: {"runs":[newest first], "time_zone":...}. */
    public static String json(List<JsonObject> runs) {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (JsonObject r : runs) arr.add(r);
        o.add("runs", arr);
        o.addProperty("time_zone", PACIFIC.getId());
        return o.toString();
    }

    // ------------------------------------------------------------------ the run going on in this server

    /** The run being recorded (null when none). Guarded by the class lock. */
    private static final class Run {
        final Date start;
        final Map<String, Long> usual;
        final Map<String, Row> rows = new LinkedHashMap<>();   // in memory, for the banner (no database read per page)
        final Set<String> emailed = new HashSet<>();
        String current;              // key of the running step, or null
        String currentResult;        // result to give the running step when it ends (null: ok)
        String currentDetail;
        boolean countCheckSubmitted; // the count check job was started for this run (it ends the run when done)
        boolean countCheckPending;   // the Scheduler has finished; the count check job still runs
        Run(Date start, Map<String, Long> usual) { this.start = start; this.usual = usual; }
    }

    private static Run s_run;
    private static ExecutorService s_writer;               // database writes, in order, off the run's thread
    private static ScheduledExecutorService s_watcher;     // overrun check of the running step
    private static ScheduledFuture<?> s_watch;

    /** Seconds truncated: daily_update_step stores datetime (MySQL would round the milliseconds). */
    static Date nowSeconds() {
        return new Date(System.currentTimeMillis() / 1000 * 1000);
    }

    private static synchronized ExecutorService writer() {
        if (s_writer == null) s_writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DailyUpdate writer");
            t.setDaemon(true);
            return t;
        });
        return s_writer;
    }

    private interface DbWork { void run(java.sql.Connection c) throws Exception; }

    /** A database write on the writer thread, its own autocommit connection; never throws. */
    private static void write(String what, DbWork work) {
        if (s_testMode) return;
        try {
            writer().execute(() -> {
                try (java.sql.Connection c = DBUtilSimple.getDataSource().getConnection()) {
                    c.setAutoCommit(true);
                    DailyUpdateDb.ensureSchema(c);
                    work.run(c);
                } catch (Throwable e) {
                    s_log.warn("write() " + what + " e:" + e);
                }
            });
        } catch (Throwable e) {
            s_log.warn("write() " + what + " not queued e:" + e);
        }
    }

    /** True while a run is recorded in this server (from its start until its end row, after the count check). */
    public static synchronized boolean isActive() {
        return s_run != null;
    }

    /** The start of the run recorded now, or null. */
    public static synchronized Date activeRunStart() {
        return s_run == null ? null : s_run.start;
    }

    /** The current run's steps from memory (for the banner), or null when none runs. */
    public static synchronized List<StepView> activeView() {
        if (s_run == null) return null;
        return view(new ArrayList<>(s_run.rows.values()), s_run.usual, true, System.currentTimeMillis());
    }

    /** The hover line of the running step, or null. */
    public static String activeHover() {
        try {
            List<StepView> v = activeView();
            return v == null ? null : hover(v);
        } catch (RuntimeException e) {
            s_log.warn("activeHover() e:" + e);
            return null;
        }
    }

    /** The line for visitors, or null when no daily update runs. */
    public static String activePublicLine() {
        try {
            List<StepView> v = activeView();
            return v == null ? null : publicLine(minutesLeft(v));
        } catch (RuntimeException e) {
            s_log.warn("activePublicLine() e:" + e);
            return null;
        }
    }

    /** A full run starts (Scheduler.doAction, all sets). Reads the usual times (short query, its own connection). */
    public static void beginRun() {
        try {
            Date start = nowSeconds();
            Map<String, Long> usual = new HashMap<>(s_testUsual);
            if (!s_testMode) try (java.sql.Connection c = DBUtilSimple.getDataSource().getConnection()) {
                c.setAutoCommit(true);
                DailyUpdateDb.ensureSchema(c);
                usual = DailyUpdateDb.usual(c, start, USUAL_RUNS);
            } catch (Throwable e) {
                s_log.warn("beginRun() usual times not read e:" + e);
            }
            synchronized (DailyUpdate.class) {
                // A run that never ended in this server (its count check never answered): it ends as failed.
                if (s_run != null) finishLocked(s_run, FAILED, "a new daily update started before this one had ended");
                s_run = new Run(start, usual);
            }
            write("prune", c -> DailyUpdateDb.prune(c, DailyUpdateDb.KEEP_DAYS));
            startWatch();
        } catch (Throwable e) {
            s_log.warn("beginRun() e:" + e);
        }
    }

    /** The step key starts; the step running until now ends (ok, or as noted). No-op when no run is recorded. */
    public static void start(String key) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null || index(key) < 0 || run.countCheckPending) return;
                Date now = nowSeconds();
                closeStepLocked(run, now);
                run.current = key;
                run.currentResult = null;
                run.currentDetail = null;
                run.rows.put(key, new Row(key, now, null, null, null));
                final Date runStart = run.start;
                final int order = index(key) + 1;
                write("start " + key, c -> DailyUpdateDb.start(c, runStart, key, order, now));
            }
        } catch (Throwable e) {
            s_log.warn("start() " + key + " e:" + e);
        }
    }

    /** The step key did not run (for example set 5 on staging); ends the step running until now. */
    public static void skip(String key, String detail) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null || index(key) < 0 || run.countCheckPending) return;
                Date now = nowSeconds();
                closeStepLocked(run, now);
                run.current = null;
                run.rows.put(key, new Row(key, now, now, SKIPPED, cut(detail)));
                final Date runStart = run.start;
                final int order = index(key) + 1;
                write("skip " + key, c -> {
                    DailyUpdateDb.start(c, runStart, key, order, now);
                    DailyUpdateDb.end(c, runStart, key, now, SKIPPED, cut(detail));
                });
            }
        } catch (Throwable e) {
            s_log.warn("skip() " + key + " e:" + e);
        }
    }

    /** The running step key failed, but the run goes on (its own try): it ends as failed and an email goes out. */
    public static void noteFailed(String key, String detail) {
        note(key, FAILED, detail);
    }

    /** The running step key did nothing on purpose (switched off, or skipped): it ends as skipped. */
    public static void noteSkipped(String key, String detail) {
        note(key, SKIPPED, detail);
    }

    /**
     * The result of a step from its compute.log line: failed when it says FAILED or NOT (done), skipped when it says it
     * skipped or is off, ok otherwise. Unit-tested.
     */
    public static String resultOf(String line) {
        if (line == null) return OK;
        // SKIPPED BY GUARD: the TaxonWorks archive looked wrong (far fewer records): someone must look at it.
        if (line.contains("FAILED") || line.contains(" NOT ") || line.contains("SKIPPED BY GUARD")) return FAILED;
        String l = line.toLowerCase();
        if (l.startsWith("skipped") || l.contains(": skipped") || l.contains(": off")) return SKIPPED;
        return OK;
    }

    /** The running step key's own result line (compute.log): kept as its message, and its result (resultOf). */
    public static void noteResult(String key, String line) {
        note(key, resultOf(line), line);
    }

    private static void note(String key, String result, String detail) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null || !key.equals(run.current)) return;
                if (FAILED.equals(run.currentResult) && !FAILED.equals(result)) return;   // a failure noted first stays
                run.currentResult = OK.equals(result) ? null : result;
                run.currentDetail = cut(detail);
            }
        } catch (Throwable e) {
            s_log.warn("note() " + key + " e:" + e);
        }
    }

    /** A set failed and the run stopped: the running step fails, the run ends. alreadyEmailed: Scheduler sent its own. */
    public static void failRun(String detail, boolean alreadyEmailed) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null) return;
                if (run.current != null) {
                    run.currentResult = FAILED;
                    run.currentDetail = cut(detail);
                    if (alreadyEmailed) run.emailed.add(run.current + ":failed");
                }
                finishLocked(run, FAILED, "the run stopped: " + cut(detail));
            }
        } catch (Throwable e) {
            s_log.warn("failRun() e:" + e);
        }
    }

    /**
     * The Scheduler's run has ended (its finally block). normal: it reached its end. Unless the count check job was
     * started (it ends the run when done, countCheckFinished), the run ends now.
     */
    public static void schedulerFinished(boolean normal) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null) return;
                if (!normal) {
                    if (run.current != null) {
                        run.currentResult = FAILED;
                        if (run.currentDetail == null) run.currentDetail = "the run stopped unexpectedly (see compute.log)";
                    }
                    finishLocked(run, FAILED, "the run stopped unexpectedly");
                    return;
                }
                if (COUNT_CHECK.equals(run.current) && run.countCheckSubmitted) {
                    run.countCheckPending = true;
                    return;
                }
                finishLocked(run, null, null);
            }
        } catch (Throwable e) {
            s_log.warn("schedulerFinished() e:" + e);
        }
    }

    /**
     * The count check job was started for the running count check step (CountCheckJob.startAfterNightly); returns
     * the run's start, for countCheckFinished, or null when no run is recorded.
     */
    public static synchronized Date countCheckSubmitted() {
        if (s_run == null || !COUNT_CHECK.equals(s_run.current)) return null;
        s_run.countCheckSubmitted = true;
        return s_run.start;
    }

    /** The count check job of the run has ended (error null: done). Ends the run once the Scheduler has finished. */
    public static void countCheckFinished(Date runStart, String error) {
        countCheckFinished(runStart, error, null);
    }

    /**
     * As above; warning (CountCheckJob.warning: "N real differences: see Check the counts ...", null when there is none):
     * the check ran to its end and found real differences. The step ends with the result warning (done, yellow on the
     * page, the text as its message) and one email goes to the administrators, by the same path as a failed or slow
     * step (emailOnce: no mail file, no mail). An error wins over a warning.
     */
    public static void countCheckFinished(Date runStart, String error, String warning) {
        try {
            synchronized (DailyUpdate.class) {
                Run run = s_run;
                if (run == null || runStart == null || !runStart.equals(run.start) || !COUNT_CHECK.equals(run.current)) return;
                if (error != null) {
                    run.currentResult = FAILED;
                    run.currentDetail = cut(error);
                } else if (warning != null && !FAILED.equals(run.currentResult)) {
                    run.currentResult = WARNING;
                    run.currentDetail = cut(warning);
                }
                if (run.countCheckPending) finishLocked(run, null, null);
                else closeStepLocked(run, nowSeconds());   // the Scheduler ends the run (schedulerFinished)
            }
        } catch (Throwable e) {
            s_log.warn("countCheckFinished() e:" + e);
        }
    }

    /** Ends the running step and writes the end row; the run is no longer active. */
    private static void finishLocked(Run run, String endResult, String endDetail) {
        Date now = nowSeconds();
        closeStepLocked(run, now);
        run.current = null;
        final Date runStart = run.start;
        final String result = endResult == null ? OK : endResult;
        final String detail = cut(endDetail);
        run.rows.put(END, new Row(END, now, now, result, detail));
        write("end", c -> {
            DailyUpdateDb.start(c, runStart, END, index(END) + 1, now);
            DailyUpdateDb.end(c, runStart, END, now, result, detail);
        });
        s_run = null;
        stopWatch();
    }

    /** Ends the running step at now (ok unless noted) and emails on a failure or an overrun. */
    private static void closeStepLocked(Run run, Date now) {
        String key = run.current;
        if (key == null) return;
        Row r = run.rows.get(key);
        Date started = r == null ? now : r.started;
        String result = run.currentResult == null ? OK : run.currentResult;
        String detail = run.currentDetail;
        run.rows.put(key, new Row(key, started, now, result, detail));
        final Date runStart = run.start;
        write("end " + key, c -> DailyUpdateDb.end(c, runStart, key, now, result, detail));
        long seconds = (now.getTime() - started.getTime()) / 1000;
        if (FAILED.equals(result)) emailOnce(run, key, "failed", "failed: " + (detail == null ? "" : detail));
        else {
            if (WARNING.equals(result)) emailOnce(run, key, "differences", detail == null ? "" : detail);
            if (isOverrun(seconds, run.usual.get(key))) {
                emailOnce(run, key, "overrun", "took " + duration(seconds) + ", usually " + duration(run.usual.get(key)));
            }
        }
        run.current = null;
        run.currentResult = null;
        run.currentDetail = null;
    }

    /** One email per step and kind (failed, overrun, differences) per run, on a thread of its own. */
    private static void emailOnce(Run run, String key, String kind, String what) {
        // A step that ran long and then failed gets both emails; an overrun noticed while it ran is not sent again at its end.
        String id = key + ":" + kind;
        if (!run.emailed.add(id)) return;
        if (s_testMode) {
            s_testEmails.add(id);
            return;
        }
        Step s = step(key);
        String label = s == null ? key : s.label;
        String env = AntwebProps.isStageMode() ? "staging" : "production";
        boolean differences = "differences".equals(kind);
        String subject = differences ? "AntWeb " + env + ": the count check after the daily update found differences"
            : "AntWeb " + env + ": daily update step " + ("failed".equals(kind) ? "failed" : "running long") + ": " + label;
        String body = "Daily update of " + iso(run.start) + " (Pacific), step " + (index(key) + 1) + " of " + STEPS.size()
            + ", " + label + ": " + what + (what.endsWith(".") ? "" : ".") + "\n\n"
            + (differences ? "The report (administrators): " + AntwebProps.getDomainApp() + "/countCheck.do\n"
                + "A real difference is a number on the site that does not equal its recount and that no change since the daily update explains.\n\n" : "")
            + "Where the daily update is: " + AntwebProps.getDomainApp()
            + "/dailyUpdate.do (curators and admins). Details: compute.log (Log Manager).";
        try {
            // On the writer thread (SES can take seconds), without a database connection (the email must go out when
            // the database is the problem).
            writer().execute(() -> {
                try {
                    Emailer.sendMail(AntwebUtil.getDevEmail(), subject, body);
                } catch (Throwable e) {
                    s_log.warn("emailOnce() " + id + " e:" + e);
                }
            });
        } catch (Throwable e) {
            s_log.warn("emailOnce() " + id + " not queued e:" + e);
        }
    }

    private static synchronized void startWatch() {
        try {
            if (s_watcher == null) s_watcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DailyUpdate watch");
                t.setDaemon(true);
                return t;
            });
            if (s_watch != null) s_watch.cancel(false);
            s_watch = s_watcher.scheduleAtFixedRate(DailyUpdate::watch, WATCH_PERIOD_SECONDS, WATCH_PERIOD_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable e) {
            s_log.warn("startWatch() e:" + e);
        }
    }

    private static synchronized void stopWatch() {
        if (s_watch != null) s_watch.cancel(false);
        s_watch = null;
    }

    /** Every minute while a run is recorded: the running step against its usual time. */
    static synchronized void watch() {
        try {
            Run run = s_run;
            if (run == null || run.current == null) return;
            Row r = run.rows.get(run.current);
            if (run.countCheckPending && r != null && r.started != null
                    && System.currentTimeMillis() - r.started.getTime() > COUNT_CHECK_MAX_MS) {
                // The count check job never reported back (refused, lost in a restart of the job queue): the run ends.
                run.currentResult = FAILED;
                run.currentDetail = "no answer from the count check job after " + (COUNT_CHECK_MAX_MS / 60000) + " minutes";
                finishLocked(run, null, null);
                return;
            }
            if (r == null || r.started == null) return;
            long seconds = (System.currentTimeMillis() - r.started.getTime()) / 1000;
            Long usual = run.usual.get(run.current);
            if (isOverrun(seconds, usual)) {
                emailOnce(run, run.current, "overrun", "running for " + duration(seconds) + " so far, usually " + duration(usual)
                    + " (started " + hhmm(r.started) + " Pacific)");
            }
        } catch (Throwable e) {
            s_log.warn("watch() e:" + e);
        }
    }

    static String cut(String s) {
        if (s == null) return null;
        String plain = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() > 480 ? plain.substring(0, 480) + "..." : plain;
    }

    /** For tests: forget the run in memory. */
    public static synchronized void resetForTest() {
        s_run = null;
        stopWatch();
    }

    // Unit tests: no database and no email (AntwebProps ends a JVM without the site's resources); the emails that would
    // have gone out are listed instead.
    private static volatile boolean s_testMode = false;
    private static final Map<String, Long> s_testUsual = new HashMap<>();
    private static final List<String> s_testEmails = java.util.Collections.synchronizedList(new ArrayList<>());

    /** Tests: on, no database or email; usual: the usual times the next run starts with. */
    public static synchronized void testMode(boolean on, Map<String, Long> usual) {
        s_testMode = on;
        s_testUsual.clear();
        if (usual != null) s_testUsual.putAll(usual);
        s_testEmails.clear();
    }

    /** Tests: "step:failed", "step:overrun" or "step:differences" for each email that would have gone out. */
    public static List<String> testEmails() {
        return new ArrayList<>(s_testEmails);
    }

    /** Tests: the running step started seconds ago (for the overrun check), then the check runs once. */
    public static synchronized void testWatch(long secondsAgo) {
        if (s_run == null || s_run.current == null) return;
        Row r = s_run.rows.get(s_run.current);
        s_run.rows.put(r.step, new Row(r.step, new Date(System.currentTimeMillis() - secondsAgo * 1000), null, null, null));
        watch();
    }

    /** A Pacific local time ("2026-11-07T05:00:00") as a Date (tests). */
    public static Date pacific(String isoLocal) {
        return Date.from(LocalDateTime.parse(isoLocal).atZone(PACIFIC).toInstant());
    }
}

package org.calacademy.antweb.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Long jobs started from a web page run here, in the background, so the page answers at once
 * (Cloudflare ends any request that takes longer than 100 seconds with a "524 timeout" page,
 * while the server keeps working; curators then thought the upload had failed and sent it again).
 *
 * The page that starts a job gets a job number and sends the browser to jobStatus.do?id=N, which
 * shows the job's status and current step and reloads itself until the job has ended.
 *
 * Jobs run one at a time, in the order they were started, on one thread ("antweb-background-job"):
 * a specimen upload never runs at the same time as another upload or an admin data job started
 * here. Before a job starts, the gate is asked whether it may start; while the nightly run (or a
 * data job started some other way) or another upload (an image upload, the nightly TaxonWorks
 * upload) is running, the job waits in the queue and its status says what it is waiting for.
 *
 * Status (queued, running, finished, failed, interrupted), step text, who started it, times and
 * the result are kept in memory and in the table background_job (BackgroundJobDb), so the status
 * page works after a reload and, after a restart, shows a job that had not ended as "interrupted".
 *
 * Duplicates: while an upload of a group (or by a login) is queued or running, a new upload of
 * the same group or by the same login is not started; the caller gets the job already there.
 * The same for an admin data job with the same action.
 *
 * The nightly run (Scheduler, utilData.do called with the scheduler's code) does not use this:
 * it runs as before, in its own request.
 */
public final class BackgroundJobs {

    private static final Log s_log = LogFactory.getLog(BackgroundJobs.class);

    /** Job kinds. */
    public static final String UPLOAD = "upload";
    public static final String DATA = "data";

    /** How many jobs may wait or run at a time (all kinds). */
    public static final int MAX_ACTIVE = 20;

    /** Finished jobs kept in memory (older ones are read from the table). */
    static final int KEEP_IN_MEMORY = 200;

    public enum Status {
        queued, running, finished, failed, interrupted;

        public boolean isActive() {
            return this == queued || this == running;
        }

        public static Status of(String s) {
            for (Status v : values()) if (v.name().equals(s)) return v;
            return interrupted;
        }
    }

    /** Text of a job that had not ended when the server stopped. */
    public static final String INTERRUPTED_STEP = "The server restarted before this job finished.";

    /** One job. Its fields are read by the status page while the worker thread changes them. */
    public static final class Job {
        volatile int id;
        public final String kind;
        public final String action;
        public final String title;
        public final int loginId;
        public final String loginName;
        public final int groupId;
        volatile Status status = Status.queued;
        volatile String step;
        volatile String result;
        volatile String resultLink;
        volatile String resultLinkText;
        volatile Date created = new Date();
        volatile Date started;
        volatile Date finished;
        // Progress through a file (line n of total), shown in the step text.
        volatile String progressLabel;
        volatile long progressTotal;
        volatile long lastSaved;

        public Job(String kind, String action, String title, int loginId, String loginName, int groupId) {
            this.kind = kind;
            this.action = action;
            this.title = title;
            this.loginId = loginId;
            this.loginName = loginName;
            this.groupId = groupId;
        }

        public int getId() { return id; }
        public String getKind() { return kind; }
        public String getAction() { return action; }
        public String getTitle() { return title; }
        public int getLoginId() { return loginId; }
        public String getLoginName() { return loginName; }
        public int getGroupId() { return groupId; }
        public Status getStatus() { return status; }
        public String getStep() { return step; }
        /** The result: HTML made by AntWeb (admin jobs) or plain text (uploads; see JobStatusAction). */
        public String getResult() { return result; }
        public String getResultLink() { return resultLink; }
        public String getResultLinkText() { return resultLinkText; }
        public Date getCreated() { return created; }
        public Date getStarted() { return started; }
        public Date getFinished() { return finished; }
        public boolean isActive() { return status.isActive(); }

        /** For BackgroundJobDb, reading a row back. */
        public void restore(int id, Status status, String step, String result, String resultLink, String resultLinkText,
                            Date created, Date started, Date finished) {
            this.id = id;
            this.status = status;
            this.step = step;
            this.result = result;
            this.resultLink = resultLink;
            this.resultLinkText = resultLinkText;
            this.created = created;
            this.started = started;
            this.finished = finished;
        }

        /** The worker's progress through a file: label "Saving specimens", total lines (0: unknown). */
        public void setProgress(String label, long total) {
            this.progressLabel = label;
            this.progressTotal = total;
        }

        public String toString() {
            return "{job " + id + " " + kind + ":" + action + " " + status + " group:" + groupId + " login:" + loginId + "}";
        }
    }

    /** What a job ended with. */
    public static final class Outcome {
        final boolean ok;
        final String result;
        final String link;
        final String linkText;

        private Outcome(boolean ok, String result, String link, String linkText) {
            this.ok = ok;
            this.result = result;
            this.link = link;
            this.linkText = linkText;
        }

        public static Outcome ok(String result) { return new Outcome(true, result, null, null); }
        public static Outcome ok(String result, String link, String linkText) { return new Outcome(true, result, link, linkText); }
        public static Outcome failed(String result) { return new Outcome(false, result, null, null); }
        public static Outcome failed(String result, String link, String linkText) { return new Outcome(false, result, link, linkText); }
    }

    /** The work of a job. An exception ends the job as failed. */
    public interface Work {
        Outcome run(Job job) throws Exception;
    }

    /** Null when a job may start now, otherwise what it is waiting for (shown as its step). */
    public interface Gate {
        String waitReason(Job job);
    }

    /** Where jobs are recorded. BackgroundJobDb in the application, a list in the tests. */
    public interface Store {
        /** Records a new job and returns its number. */
        int insert(Job job) throws Exception;
        void update(Job job) throws Exception;
        /** A job by number, or null. */
        Job load(int id) throws Exception;
        /** Newest first; groupId 0 = all groups. */
        List<Job> recent(int groupId, int limit) throws Exception;
    }

    /** The answer to a submit: the job, and whether it is new (false: one was already there). */
    public static final class Submitted {
        public final Job job;
        public final boolean isNew;
        public final String refused;
        /** Why the job could not be recorded (exception text, for admin logs only; never shown to curators), or null. */
        public final String error;

        Submitted(Job job, boolean isNew, String refused) {
            this(job, isNew, refused, null);
        }

        Submitted(Job job, boolean isNew, String refused, String error) {
            this.job = job;
            this.isNew = isNew;
            this.refused = refused;
            this.error = error;
        }
    }

    // ------------------------------------------------------------------ the runner

    /** One queue of jobs. The application uses one (get()); the tests make their own. */
    public static final class Runner {
        private final Store store;
        private final Gate gate;
        private final long pollMs;
        private final long saveEveryMs;
        private final ExecutorService executor;
        private final Map<Integer, Job> jobs = new LinkedHashMap<>();

        public Runner(Store store, Gate gate, long pollMs, long saveEveryMs) {
            this.store = store;
            this.gate = gate;
            this.pollMs = pollMs;
            this.saveEveryMs = saveEveryMs;
            this.executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "antweb-background-job");
                t.setDaemon(true);
                return t;
            });
        }

        /**
         * Starts a job, unless the same one is already queued or running (an upload of the same
         * group or by the same login; a data job with the same action): then that job comes back
         * with isNew false and nothing is started.
         */
        public synchronized Submitted submit(Job job, Work work) {
            Job existing = findDuplicate(job);
            if (existing != null) return new Submitted(existing, false, null);
            if (activeCount() >= MAX_ACTIVE) {
                return new Submitted(null, false, "Too many jobs are waiting. Please try again later.");
            }
            try {
                job.status = Status.queued;
                job.created = new Date();
                job.step = "Waiting to start.";
                job.id = store.insert(job);
            } catch (Exception e) {
                String error = describe(e);
                s_log.error("submit() " + job + " not recorded: " + error, e);
                return new Submitted(null, false, "The job could not be recorded. Please try again.", error);
            }
            jobs.put(job.id, job);
            trim();
            executor.submit(() -> execute(job, work));
            return new Submitted(job, true, null);
        }

        /** An exception as one line for the logs: class, message, SQL state and error code, and its causes. */
        static String describe(Throwable e) {
            StringBuilder b = new StringBuilder();
            for (Throwable t = e; t != null && b.length() < 1000; t = t.getCause() == t ? null : t.getCause()) {
                if (b.length() > 0) b.append(" / caused by ");
                b.append(t.getClass().getName()).append(": ").append(t.getMessage());
                if (t instanceof java.sql.SQLException) {
                    java.sql.SQLException q = (java.sql.SQLException) t;
                    b.append(" [SQLState ").append(q.getSQLState()).append(", error ").append(q.getErrorCode()).append("]");
                }
            }
            return b.toString();
        }

        /** The queued or running job that a new one would duplicate, or null. */
        public synchronized Job findDuplicate(Job job) {
            for (Job j : jobs.values()) {
                if (!j.isActive() || !j.kind.equals(job.kind)) continue;
                if (UPLOAD.equals(job.kind)) {
                    if (j.groupId == job.groupId || (job.loginId != 0 && j.loginId == job.loginId)) return j;
                } else if (j.action.equals(job.action)) {
                    return j;
                }
            }
            return null;
        }

        private int activeCount() {
            int n = 0;
            for (Job j : jobs.values()) if (j.isActive()) n++;
            return n;
        }

        private void trim() {
            if (jobs.size() <= KEEP_IN_MEMORY) return;
            List<Integer> ended = new ArrayList<>();
            for (Job j : jobs.values()) if (!j.isActive()) ended.add(j.id);
            for (int i = 0; i < ended.size() && jobs.size() > KEEP_IN_MEMORY; i++) jobs.remove(ended.get(i));
        }

        /** A job of this run (memory) or an earlier one (store), or null. */
        public Job get(int id) {
            synchronized (this) {
                Job j = jobs.get(id);
                if (j != null) return j;
            }
            try {
                Job j = store.load(id);
                if (j != null && j.isActive()) {
                    // In the table as queued or running, but not in this run: the server stopped.
                    j.status = Status.interrupted;
                    j.step = INTERRUPTED_STEP;
                }
                return j;
            } catch (Exception e) {
                s_log.error("get() id:" + id + " e:" + e);
                return null;
            }
        }

        /** Newest first: this run's jobs, then the table's. groupId 0 = all groups. */
        public List<Job> recent(int groupId, int limit) {
            Map<Integer, Job> out = new LinkedHashMap<>();
            synchronized (this) {
                for (Job j : jobs.values()) if (groupId == 0 || j.groupId == groupId) out.put(j.id, j);
            }
            try {
                for (Job j : store.recent(groupId, limit)) {
                    if (out.containsKey(j.id)) continue;
                    if (j.isActive()) {
                        j.status = Status.interrupted;
                        j.step = INTERRUPTED_STEP;
                    }
                    out.put(j.id, j);
                }
            } catch (Exception e) {
                s_log.error("recent() e:" + e);
            }
            List<Job> list = new ArrayList<>(out.values());
            list.sort(Comparator.comparingInt((Job j) -> j.id).reversed());
            return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
        }

        /** The ids of this run's jobs that are queued or running. */
        public synchronized List<Integer> activeIds() {
            List<Integer> ids = new ArrayList<>();
            for (Job j : jobs.values()) if (j.isActive()) ids.add(j.id);
            return ids;
        }

        public synchronized boolean isRunning() {
            for (Job j : jobs.values()) if (j.status == Status.running) return true;
            return false;
        }

        public synchronized Job running() {
            for (Job j : jobs.values()) if (j.status == Status.running) return j;
            return null;
        }

        void execute(Job job, Work work) {
            CURRENT.set(job);
            CURRENT_RUNNER.set(this);
            try {
                String reason;
                while ((reason = gate == null ? null : gate.waitReason(job)) != null) {
                    if (!reason.equals(job.step)) {
                        job.step = reason;
                        save(job);
                    }
                    Thread.sleep(pollMs);
                }
                job.status = Status.running;
                job.started = new Date();
                job.step = "Started.";
                save(job);
                Outcome outcome = work.run(job);
                if (outcome == null) outcome = Outcome.ok("Done.");
                job.result = outcome.result;
                job.resultLink = outcome.link;
                job.resultLinkText = outcome.linkText;
                job.step = outcome.ok ? "Finished." : "Stopped.";
                job.finished = new Date();
                job.status = outcome.ok ? Status.finished : Status.failed;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                job.result = "The job was stopped (the server is shutting down).";
                job.step = INTERRUPTED_STEP;
                job.finished = new Date();
                job.status = Status.interrupted;
            } catch (Throwable t) {
                s_log.error("execute() " + job + " e:" + t + " " + AntwebUtil.getShortStackTrace(t));
                job.result = "The job stopped with an error (" + t.getClass().getSimpleName() + ").";
                job.step = "Stopped.";
                job.finished = new Date();
                job.status = Status.failed;
            } finally {
                save(job);
                CURRENT.remove();
                CURRENT_RUNNER.remove();
                s_log.warn("execute() ended " + job);
            }
        }

        void save(Job job) {
            job.lastSaved = System.currentTimeMillis();
            try {
                store.update(job);
            } catch (Exception e) {
                s_log.error("save() " + job + " e:" + e);
            }
        }

        /** Saves a step change at most every saveEveryMs (status changes are saved at once). */
        void saveSoon(Job job) {
            if (System.currentTimeMillis() - job.lastSaved >= saveEveryMs) save(job);
        }

        /** Stops taking jobs (tests). */
        public void shutdown() {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ the worker thread's job

    private static final ThreadLocal<Job> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Runner> CURRENT_RUNNER = new ThreadLocal<>();

    /** The job the calling thread is running, or null (a web request, the nightly run). */
    public static Job current() {
        return CURRENT.get();
    }

    /** Sets the current job's step text. Does nothing outside a background job. */
    public static void step(String text) {
        Job job = CURRENT.get();
        if (job == null) return;
        job.step = text;
        job.progressLabel = null;
        Runner r = CURRENT_RUNNER.get();
        if (r != null) r.save(job);
    }

    /**
     * Progress through a file: "label: line n of total". Called for every line; the step text
     * changes every 500 lines and is saved to the table at most every few seconds.
     * Does nothing outside a background job or when no progress label is set.
     */
    public static void progress(long n) {
        Job job = CURRENT.get();
        if (job == null || job.progressLabel == null || n % 500 != 0) return;
        job.step = progressText(job.progressLabel, n, job.progressTotal);
        Runner r = CURRENT_RUNNER.get();
        if (r != null) r.saveSoon(job);
    }

    /**
     * Starts progress through a file: the step becomes "label: line n of total" (total = the
     * file's lines without the header). Does nothing outside a background job (the file is then
     * not read).
     */
    public static void startProgress(String label, java.nio.file.Path file) {
        Job job = CURRENT.get();
        if (job == null) return;
        long total = 0;
        if (file != null) {
            try (java.util.stream.Stream<String> lines = java.nio.file.Files.lines(file, java.nio.charset.StandardCharsets.ISO_8859_1)) {
                total = Math.max(0, lines.count() - 1);
            } catch (Exception e) {
                total = 0;
            }
        }
        job.step = label + ".";
        job.progressLabel = label;
        job.progressTotal = total;
        Runner r = CURRENT_RUNNER.get();
        if (r != null) r.save(job);
    }

    public static String progressText(String label, long n, long total) {
        String s = label + ": line " + String.format("%,d", n);
        if (total > 0) s += " of " + String.format("%,d", total);
        return s + ".";
    }

    // ------------------------------------------------------------------ the application's runner

    private static volatile Runner s_runner;

    /** The application's runner (table background_job, the application gate). */
    public static Runner get() {
        Runner r = s_runner;
        if (r == null) {
            synchronized (BackgroundJobs.class) {
                if (s_runner == null) s_runner = new Runner(new BackgroundJobDb(), BackgroundJobs::applicationWaitReason, 5000, 5000);
                r = s_runner;
            }
        }
        return r;
    }

    /** True while a job started here is running (the restart check waits for it). */
    public static boolean isJobRunning() {
        Runner r = s_runner;
        return r != null && r.isRunning();
    }

    /** "Upload of <group> by <login>" etc. of the running job, or null. */
    public static String runningDescription() {
        Runner r = s_runner;
        Job j = r == null ? null : r.running();
        return j == null ? null : j.title + " (job " + j.id + ", " + j.loginName + ")";
    }

    /**
     * The application gate: a job waits while AntWeb is starting, while the nightly run or another
     * data job runs, and while another upload runs (an image upload, the nightly TaxonWorks upload).
     */
    static String applicationWaitReason(Job job) {
        if (!AntwebMgr.isPostInitialized()) return "Waiting for AntWeb to finish starting up.";
        if (UtilDataAction.isInComputeProcess() || Scheduler.isRunning()) {
            return "Waiting for the nightly data update (or another data job) to finish. Your job starts right after it.";
        }
        if (org.calacademy.antweb.upload.UploadAction.isInUploadProcess()) {
            return "Waiting for another upload to finish. Your job starts right after it.";
        }
        return null;
    }
}

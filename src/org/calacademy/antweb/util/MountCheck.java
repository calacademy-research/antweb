package org.calacademy.antweb.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Hourly check of the two storage mounts the container uses (production and staging, not dev). Added 2026-10-23,
 * after both production mounts failed for 26 hours unnoticed (a Spaces access key was deleted): no upload report
 * could be read, the CAS file copy failed and the 01:00 database dump was not written.
 *
 * - /mnt/antweb (images, upload files and reports): a small file is written and read back
 *   (/mnt/antweb/web/log/specimen/mountcheck-<production|staging>.txt: the folder the specimen upload reports are
 *   written to, so the check writes where the app already must. It used /mnt/antweb/mountcheck/ until 2026-10-23
 *   (the app may not create it on staging) and web/log/upload/ until 2026-10-24 (that folder does not exist on
 *   production): both sent false alarms. Checked on both servers 2026-10-24: web/log/specimen exists and user
 *   tomcat (2001) can write there).
 * - the daily database dump to /mnt/backup (production only, staging runs no dump): the app cannot read /mnt/backup
 *   (the rclone mount is root-only; the first check on 2026-10-23 reported "cannot list"), so the host's
 *   bin/db-daily-dump.sh writes one line to web/log/db-dump-status.txt ("ok <time> <bytes> <file>" or
 *   "failed <time> <reason>"). The line must say ok, be under 26 hours old and name a dump over 1 MB, so a dead
 *   /mnt/backup (the dump cannot be written) and a dump that did not run are both reported.
 *
 * Each probe runs in its own thread with a 60-second limit, so a hung mount is reported instead of blocking.
 * On a problem: an email to antweb@antweb.org (forwarded to Brian) at once and every 6 hours while it lasts, an
 * admin alert (at most one a week), a line in compute.log; and one email when all is well again.
 * docs/runbook.md, "Storage mounts (/mnt/antweb, /mnt/backup)".
 */
public final class MountCheck {

    private static final Log s_log = LogFactory.getLog(MountCheck.class);

    static final String TITLE = "Mount check";
    /** Where the probe file goes, under /mnt/antweb: the upload reports' folder (the app writes there already). */
    public static final String PROBE_DIR = "web/log/specimen";
    public static final long PERIOD_MS = 60L * 60 * 1000;           // every hour
    static final long FIRST_DELAY_MS = 5L * 60 * 1000;              // 5 minutes after start (caches load first)
    static final long PROBE_TIMEOUT_MS = 60 * 1000;
    static final long REPEAT_EMAIL_MS = 6L * 60 * 60 * 1000;
    public static final long DUMP_MAX_AGE_MS = 26L * 60 * 60 * 1000;
    public static final long DUMP_MIN_BYTES = 1024L * 1024;

    private static volatile String s_lastResult = "not run yet";
    private static long s_lastEmail = 0;
    private static boolean s_failing = false;
    private static Thread s_hung = null;   // a probe thread that did not return; no new probe while it lives

    private MountCheck() {}

    /** The result of the last run, for the Server Status page and the log. */
    public static String lastResult() { return s_lastResult; }

    /** Writes content to file and reads it back; null when ok, otherwise what is wrong. */
    public static String writeReadProblem(File file, String content) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory()) return dir.getPath() + " is missing (mount missing or failing)";
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            String back = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!content.equals(back)) return file.getPath() + " read back differs from what was written";
            return null;
        } catch (Exception e) {
            return "cannot write and read " + file.getPath() + ": " + e;
        }
    }

    public static final String DUMP_STATUS = "db-dump-status.txt";

    /** When this JVM started (tests can set it). */
    public static long s_startedAt = -1;
    static long startedAt() {
        return s_startedAt >= 0 ? s_startedAt : java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    /** The daily dump's status line (bin/db-daily-dump.sh) must say ok, be under maxAgeMs old and name a dump over minBytes; null when ok. */
    public static String dumpStatusProblem(File status, long now, long maxAgeMs, long minBytes) {
        if (!status.isFile()) {
            // Right after the deploy that added the status line, until the next 01:00 dump writes it: not a problem
            // while the app has run for less than maxAgeMs.
            if (now - startedAt() < maxAgeMs) return null;
            return "no " + status.getName() + ": the daily 01:00 database dump has not run since bin/db-daily-dump.sh writes it";
        }
        String line;
        try {
            line = new String(Files.readAllBytes(status.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "cannot read " + status.getPath() + ": " + e;
        }
        String[] w = line.split("\\s+");
        long age = now - status.lastModified();
        if (age > maxAgeMs) return "the daily database dump last reported " + (age / 3600000) + " hours ago (" + line + "): the 01:00 dump did not run";
        if (w.length < 2 || !"ok".equals(w[0])) return "the daily database dump failed: " + line;
        long bytes = -1;
        try { bytes = Long.parseLong(w[2]); } catch (Exception e) { /* reported below */ }
        if (bytes < minBytes) return "the daily database dump is only " + bytes + " bytes: " + line;
        return null;
    }

    /** Runs probe in a thread, at most timeoutMs; its answer, or a line saying it did not return. */
    static String withTimeout(String what, java.util.concurrent.Callable<String> probe, long timeoutMs) {
        if (s_hung != null && s_hung.isAlive()) return what + ": an earlier check is still waiting for the mount (hung)";
        final String[] out = {what + ": no answer in " + (timeoutMs / 1000) + " s (the mount hangs)"};
        Thread t = new Thread(() -> {
            try {
                String r = probe.call();
                out[0] = r == null ? null : what + ": " + r;
            } catch (Exception e) {
                out[0] = what + ": " + e;
            }
        }, "MountCheck " + what);
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) s_hung = t;
        return out[0];
    }

    /** What is wrong, one line each; empty when all is well. */
    public static List<String> problems(File antwebMount, File dumpStatus, boolean checkDump, String env, long now, long timeoutMs) {
        List<String> out = new ArrayList<>();
        File probe = new File(antwebMount, PROBE_DIR + "/mountcheck-" + env + ".txt");
        String a = withTimeout(antwebMount.getPath(), () -> writeReadProblem(probe, "AntWeb " + env + " mount check " + now + "\n"), timeoutMs);
        if (a != null) out.add(a);
        if (checkDump) {
            String b = dumpStatusProblem(dumpStatus, now, DUMP_MAX_AGE_MS, DUMP_MIN_BYTES);
            if (b != null) out.add("database dump: " + b);
        }
        return out;
    }

    /** The alert text, or null when nothing is wrong. */
    public static String alert(List<String> problems) {
        if (problems.isEmpty()) return null;
        return TITLE + ": " + String.join("; ", problems)
            + ". See docs/runbook.md, \"Storage mounts (/mnt/antweb, /mnt/backup)\".";
    }

    /** Should an email go out now? At once when a problem starts, then every repeatMs; once at recovery. */
    static boolean shouldEmail(boolean wasFailing, boolean failing, long lastEmail, long now, long repeatMs) {
        if (failing) return !wasFailing || now - lastEmail >= repeatMs;
        return wasFailing;
    }

    /** One run: check, then alert and email as needed. Never throws. */
    public static synchronized String run() {
        try {
            String env = AntwebProps.isStageMode() ? "staging" : "production";
            long now = System.currentTimeMillis();
            List<String> problems = problems(new File("/mnt/antweb"), new File(LogMgr.getWebLogRoot(), DUMP_STATUS), !AntwebProps.isStageMode(), env, now, PROBE_TIMEOUT_MS);
            String alert = alert(problems);
            boolean failing = alert != null;
            String subject = null;
            if (shouldEmail(s_failing, failing, s_lastEmail, now, REPEAT_EMAIL_MS)) {
                subject = failing ? "AntWeb " + env + ": storage mount problem" : "AntWeb " + env + ": storage mounts working again";
                Emailer.sendMail(AntwebUtil.getDevEmail(), subject, failing ? alert : TITLE + ": /mnt/antweb"
                    + (AntwebProps.isStageMode() ? "" : " and the daily database dump") + " work again.");
                s_lastEmail = now;
            }
            if (failing) {
                s_log.warn("run() " + alert);
                LogMgr.appendLog("compute.log", "  " + alert, true);
                try (Connection connection = DBUtilSimple.getDataSource().getConnection()) {
                    AdminAlertMgr.addIfFresh(alert, TITLE, AdminAlertMgr.WEEK, connection);
                }
            } else if (s_failing) {
                LogMgr.appendLog("compute.log", "  " + TITLE + ": ok again", true);
            }
            s_failing = failing;
            s_lastResult = (failing ? alert : TITLE + ": ok") + " (" + new java.util.Date(now) + ")";
            return s_lastResult;
        } catch (Exception e) {
            s_log.warn("run() e:" + e);
            return TITLE + " failed: " + e;
        }
    }

    /** Starts the hourly check (called once at start-up on production and staging). */
    public static void start() {
        Timer timer = new Timer("MountCheck", true);
        timer.schedule(new TimerTask() {
            @Override public void run() { MountCheck.run(); }
        }, FIRST_DELAY_MS, PERIOD_MS);
        s_log.warn("start() hourly mount check scheduled");
    }
}

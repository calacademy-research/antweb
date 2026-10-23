package org.calacademy.antweb.util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Hourly check of the two storage mounts the container uses (production and staging, not dev). Added 2026-10-23,
 * after both production mounts failed for 26 hours unnoticed (a Spaces access key was deleted): no upload report
 * could be read, the CAS file copy failed and the 01:00 database dump was not written.
 *
 * - /mnt/antweb (images, upload files and reports): a small file is written and read back
 *   (/mnt/antweb/mountcheck/<production|staging>.txt).
 * - /mnt/backup (database dumps; production only, staging runs no dump): the folder db/daily is listed and its newest
 *   dump (YYYY-MM-DD.sql.gz) must be under 26 hours old and over 1 MB, so a dump that failed is reported too.
 *
 * Each probe runs in its own thread with a 60-second limit, so a hung mount is reported instead of blocking.
 * On a problem: an email to antweb@antweb.org (forwarded to Brian) at once and every 6 hours while it lasts, an
 * admin alert (at most one a week), a line in compute.log; and one email when all is well again.
 * docs/runbook.md, "Storage mounts (/mnt/antweb, /mnt/backup)".
 */
public final class MountCheck {

    private static final Log s_log = LogFactory.getLog(MountCheck.class);

    static final String TITLE = "Mount check";
    public static final long PERIOD_MS = 60L * 60 * 1000;           // every hour
    static final long FIRST_DELAY_MS = 5L * 60 * 1000;              // 5 minutes after start (caches load first)
    static final long PROBE_TIMEOUT_MS = 60 * 1000;
    static final long REPEAT_EMAIL_MS = 6L * 60 * 60 * 1000;
    public static final long DUMP_MAX_AGE_MS = 26L * 60 * 60 * 1000;
    public static final long DUMP_MIN_BYTES = 1024L * 1024;
    static final Pattern DUMP_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.sql\\.gz");

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
            if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                return "cannot create " + dir.getPath();
            }
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            String back = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (!content.equals(back)) return file.getPath() + " read back differs from what was written";
            return null;
        } catch (Exception e) {
            return "cannot write and read " + file.getPath() + ": " + e;
        }
    }

    /** The newest dump in dailyDir must be under maxAgeMs old and over minBytes; null when ok. */
    public static String dumpProblem(File dailyDir, long now, long maxAgeMs, long minBytes) {
        File[] files = dailyDir.listFiles();
        if (files == null) return "cannot list " + dailyDir.getPath() + " (mount missing or failing)";
        File newest = null;
        for (File f : files) {
            if (!DUMP_NAME.matcher(f.getName()).matches()) continue;
            if (newest == null || f.getName().compareTo(newest.getName()) > 0) newest = f;
        }
        if (newest == null) return "no database dump in " + dailyDir.getPath();
        long age = now - newest.lastModified();
        if (age > maxAgeMs) {
            return "the newest database dump " + newest.getName() + " is " + (age / 3600000) + " hours old (the daily 01:00 dump did not run or failed)";
        }
        if (newest.length() < minBytes) {
            return "the newest database dump " + newest.getName() + " is only " + newest.length() + " bytes";
        }
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
    public static List<String> problems(File antwebMount, File backupMount, boolean checkDump, String env, long now, long timeoutMs) {
        List<String> out = new ArrayList<>();
        File probe = new File(antwebMount, "mountcheck/" + env + ".txt");
        String a = withTimeout(antwebMount.getPath(), () -> writeReadProblem(probe, "AntWeb " + env + " mount check " + now + "\n"), timeoutMs);
        if (a != null) out.add(a);
        if (checkDump) {
            String b = withTimeout(backupMount.getPath(), () -> dumpProblem(new File(backupMount, "db/daily"), now, DUMP_MAX_AGE_MS, DUMP_MIN_BYTES), timeoutMs);
            if (b != null) out.add(b);
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
            List<String> problems = problems(new File("/mnt/antweb"), new File("/mnt/backup"), !AntwebProps.isStageMode(), env, now, PROBE_TIMEOUT_MS);
            String alert = alert(problems);
            boolean failing = alert != null;
            String subject = null;
            if (shouldEmail(s_failing, failing, s_lastEmail, now, REPEAT_EMAIL_MS)) {
                subject = failing ? "AntWeb " + env + ": storage mount problem" : "AntWeb " + env + ": storage mounts working again";
                Emailer.sendMail(AntwebUtil.getDevEmail(), subject, failing ? alert : TITLE + ": /mnt/antweb"
                    + (AntwebProps.isStageMode() ? "" : " and /mnt/backup") + " work again.");
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

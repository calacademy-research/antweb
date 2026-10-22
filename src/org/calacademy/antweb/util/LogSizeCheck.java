package org.calacademy.antweb.util;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Daily check, after the nightly run (Scheduler): warns the administrators when a log file is over
 * 1 GB or the disk is over 85% full. Added 2026-10-22, after messages.txt grew to 11 GB on staging
 * unnoticed. The warning is an admin alert (count in the header of administrators, Admin Alert
 * Manager; at most one a week) and an email to antweb@antweb.org (forwarded to Brian), sent each day
 * the problem lasts. DigitalOcean also emails when a droplet's disk is over 85% for 30 minutes.
 */
public final class LogSizeCheck {

    private static final Log s_log = LogFactory.getLog(LogSizeCheck.class);

    public static final long FILE_LIMIT = 1024L * 1024 * 1024;  // 1 GB
    public static final int DISK_PERCENT = 85;
    static final String TITLE = "Log size check";

    private LogSizeCheck() {}

    /** The folders checked: data logs (host ./logbak), web/log (host ./logs/web), Tomcat's logs (host ./logs). */
    static List<File> folders() {
        return Arrays.asList(new File(AntwebProps.getDataRoot() + "log/"), new File(LogMgr.getWebLogRoot()),
            new File("/usr/local/tomcat/logs/"));
    }

    /** What is wrong, one line each; empty when all is well. Top-level files only (not the report folders). */
    public static List<String> problems(List<File> folders, long fileLimit, File disk, int diskPercent) {
        List<String> out = new ArrayList<>();
        for (File folder : folders) {
            File[] files = folder.listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && f.length() > fileLimit) {
                    out.add(f.getPath() + " is " + mb(f.length()) + " (over " + mb(fileLimit) + ")");
                }
            }
        }
        long total = disk.getTotalSpace();
        if (total > 0) {
            long used = total - disk.getUsableSpace();
            int percent = (int) (used * 100 / total);
            if (percent > diskPercent) {
                out.add("the disk (" + disk.getPath() + ") is " + percent + "% full (over " + diskPercent + "%), "
                    + mb(disk.getUsableSpace()) + " free");
            }
        }
        return out;
    }

    static String mb(long bytes) {
        return String.format("%,d MB", bytes / 1024 / 1024);
    }

    /** The alert text, or null when nothing is wrong. */
    public static String alert(List<String> problems) {
        if (problems.isEmpty()) return null;
        return TITLE + ": " + String.join("; ", problems)
            + ". See docs/runbook.md, \"A log grows too big\".";
    }

    /** Run the check; alert and email if something is wrong. Never throws. */
    public static String run(Connection connection) {
        try {
            String alert = alert(problems(folders(), FILE_LIMIT, new File("/"), DISK_PERCENT));
            if (alert == null) return TITLE + ": ok";
            s_log.warn("run() " + alert);
            // One admin alert a week while the problem lasts (the email is daily).
            AdminAlertMgr.addIfFresh(alert, TITLE, AdminAlertMgr.WEEK, connection);
            Emailer.sendMail(AntwebUtil.getDevEmail(), "AntWeb " + AntwebProps.getDomainApp() + ": log or disk too big", alert);
            return alert;
        } catch (Exception e) {
            s_log.warn("run() e:" + e);
            return TITLE + " failed: " + e;
        }
    }
}

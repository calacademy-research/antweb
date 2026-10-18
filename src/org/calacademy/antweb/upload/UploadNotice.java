package org.calacademy.antweb.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.calacademy.antweb.Group;
import org.calacademy.antweb.Login;
import org.calacademy.antweb.util.AntwebProps;
import org.calacademy.antweb.util.Emailer;
import org.calacademy.antweb.util.LogMgr;

/**
 * Emails the curator who made a specimen or image upload when it has finished (or failed),
 * with a link to the upload report. Uploads can take a long time; the curator can wait for
 * this mail instead of checking back.
 *
 * Sent only for uploads made by a logged-in person on the upload pages (upload.do and the
 * Image Uploader's imageUploadBatch.do). Never for the nightly TaxonWorks job or anything
 * else without a web session, never for excluded groups (default: group 1, CAS, which uploads
 * every day), and never when the login has no email address.
 *
 * Switch: etc/upload-email.properties on the server (~/antweb/etc/, which the container sees
 * as /antweb/deploy/etc/; gitignored). Changes are picked up within a minute.
 *   enabled=on|off        default: on in production, off on staging and dev
 *   excludeGroups=1       group ids that never get this mail (default 1 = CAS)
 *   onlyDomains=antweb.org,calacademy.org
 *                         send only to addresses at these domains. Default: anyone in
 *                         production; on staging and dev always limited (default
 *                         antweb.org,calacademy.org), so staging never mails real curators.
 * Mail goes through Emailer.sendNotice() (etc/mail.properties); with no mail settings the
 * message is only logged in emails.log.
 */
public class UploadNotice {

    private static final Log s_log = LogFactory.getLog(UploadNotice.class);

    static final String CONFIG = "/antweb/deploy/etc/upload-email.properties";

    /** Group 1 is CAS: it uploads every day, so its curators get no upload mail. */
    public static final int CAS_GROUP_ID = 1;
    public static final Set<Integer> DEFAULT_EXCLUDED_GROUPS = Set.of(CAS_GROUP_ID);
    public static final List<String> NON_PROD_DEFAULT_DOMAINS = List.of("antweb.org", "calacademy.org");

    public static final String REPLY_TO = "antweb@antweb.org";

    // ---------------- settings ----------------

    /** The settings in effect. */
    public static class Settings {
        public final boolean enabled;
        public final Set<Integer> excludedGroups;
        public final List<String> onlyDomains;   // empty: anyone

        public Settings(boolean enabled, Set<Integer> excludedGroups, List<String> onlyDomains) {
            this.enabled = enabled;
            this.excludedGroups = excludedGroups;
            this.onlyDomains = onlyDomains;
        }

        /** Settings from the properties file's contents (empty if no file) on a live or non-live server. */
        public static Settings from(Properties p, boolean live) {
            String en = p.getProperty("enabled", "").trim().toLowerCase();
            boolean enabled = en.isEmpty() ? live : (en.equals("on") || en.equals("true") || en.equals("yes") || en.equals("1"));

            Set<Integer> groups = new HashSet<>();
            String eg = p.getProperty("excludeGroups");
            if (eg == null) {
                groups.addAll(DEFAULT_EXCLUDED_GROUPS);
            } else {
                for (String s : eg.split(",")) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    try {
                        groups.add(Integer.parseInt(s));
                    } catch (NumberFormatException e) {
                        s_log.warn("Settings excludeGroups: not a number: " + s);
                    }
                }
            }

            List<String> domains = new ArrayList<>();
            String od = p.getProperty("onlyDomains", "");
            for (String s : od.split(",")) {
                s = s.trim().toLowerCase();
                if (s.startsWith("@")) s = s.substring(1);
                if (!s.isEmpty()) domains.add(s);
            }
            if (!live && domains.isEmpty()) domains.addAll(NON_PROD_DEFAULT_DOMAINS);

            return new Settings(enabled, Collections.unmodifiableSet(groups), Collections.unmodifiableList(domains));
        }
    }

    private static Settings s_settings = null;
    private static long s_loaded = 0;

    public static synchronized Settings settings() {
        long now = System.currentTimeMillis();
        if (s_settings == null || now - s_loaded > 60_000) {
            s_loaded = now;
            Properties p = new Properties();
            File f = new File(CONFIG);
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                } catch (Exception e) {
                    s_log.error("settings() could not read " + CONFIG + " e:" + e);
                }
            }
            boolean live = false;
            try {
                live = AntwebProps.isLiveMode();
            } catch (Exception e) {
                s_log.warn("settings() could not tell if this is production; treating it as not. e:" + e);
            }
            s_settings = Settings.from(p, live);
        }
        return s_settings;
    }

    // ---------------- the decision ----------------

    /**
     * Null if the mail should be sent, else why not.
     * humanSession: the upload came from a logged-in person's web request (not the nightly
     * job or another automatic task). groupIds: the uploader's group and the group the
     * upload was made for (they differ when an admin uploads as another curator); if any
     * of them is excluded, nothing is sent.
     */
    public static String skipReason(Settings s, boolean humanSession, String email, int... groupIds) {
        if (s == null || !s.enabled) return "upload emails are turned off on this server";
        if (!humanSession) return "automatic upload (no logged-in person)";
        for (int g : groupIds) {
            if (s.excludedGroups.contains(g)) return "group " + g + " is excluded";
        }
        if (email == null || email.trim().isEmpty()) return "the login has no email address";
        String e = email.trim().toLowerCase();
        int at = e.lastIndexOf('@');
        if (at < 1 || at == e.length() - 1 || e.contains(" ") || e.contains(",") || e.contains(";")) {
            return "the login's email address is not valid";
        }
        if (!s.onlyDomains.isEmpty()) {
            String domain = e.substring(at + 1);
            boolean ok = false;
            for (String d : s.onlyDomains) {
                if (domain.equals(d) || domain.endsWith("." + d)) ok = true;
            }
            if (!ok) return "recipient domain not allowed on this server (onlyDomains)";
        }
        return null;
    }

    // ---------------- the messages ----------------

    /** What the mail says. Fill what is known; null or negative values are left out. */
    public static class Facts {
        public boolean images = false;      // false: specimen upload
        public boolean complete = false;    // false: did not complete
        public String fileName;             // specimen file as uploaded
        public String fileType;             // e.g. "AntWeb specimen file"
        public String groupName;
        public Date when = new Date();
        public int uploadId = 0;
        public int specimensLoaded = -1;
        public int redFlagged = -1;
        public int imagesSaved = -1;
        public String reportUrl;            // null: no report was written
        public boolean reportNeedsLogin = false;
        public String failure;              // the message shown on the page, when it did not complete
        public String uploadPageUrl;
    }

    static String n(int i) {
        return NumberFormat.getIntegerInstance(Locale.US).format(i);
    }

    static String plural(int i, String one, String many) {
        return n(i) + " " + (i == 1 ? one : many);
    }

    static String time(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy, h:mm a z", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
        return f.format(d);
    }

    public static String subject(Facts f) {
        String kind = f.images ? "image" : "specimen";
        if (!f.complete) return "AntWeb: your " + kind + " upload did not complete";
        String s = "AntWeb: your " + kind + " upload is complete";
        if (f.images && f.imagesSaved >= 0) s += " (" + plural(f.imagesSaved, "image", "images") + ")";
        if (!f.images && f.specimensLoaded >= 0) s += " (" + plural(f.specimensLoaded, "specimen", "specimens") + ")";
        return s;
    }

    public static String body(Facts f) {
        String kind = f.images ? "image" : "specimen";
        StringBuilder b = new StringBuilder();
        b.append("Your ").append(kind).append(" upload to AntWeb ")
         .append(f.complete ? "is complete." : "did not complete.").append("\n\n");

        if (f.fileName != null && !f.fileName.isEmpty()) b.append("File: ").append(f.fileName).append("\n");
        if (f.fileType != null && !f.fileType.isEmpty()) b.append("Type: ").append(f.fileType).append("\n");
        if (f.groupName != null && !f.groupName.isEmpty()) b.append("Group: ").append(f.groupName).append("\n");
        b.append(f.complete ? "Finished: " : "Stopped: ").append(time(f.when)).append("\n");
        if (f.complete) {
            if (f.uploadId > 0) b.append("Upload ID: ").append(f.uploadId).append("\n");
            if (f.specimensLoaded >= 0) b.append("Specimens loaded: ").append(n(f.specimensLoaded)).append("\n");
            if (f.redFlagged >= 0) b.append("Red-flagged specimens: ").append(n(f.redFlagged)).append("\n");
            if (f.imagesSaved >= 0) b.append("Images saved: ").append(n(f.imagesSaved)).append("\n");
        } else if (f.failure != null && !f.failure.isEmpty()) {
            b.append("Message: ").append(f.failure).append("\n");
        }
        b.append("\n");

        if (f.reportUrl != null) {
            if (f.complete && !f.images) {
                b.append("Upload report, with any problems found in your data:\n");
            } else if (f.reportNeedsLogin) {
                b.append("Upload report (log in to AntWeb to see it):\n");
            } else {
                b.append("Upload report:\n");
            }
            b.append(f.reportUrl).append("\n\n");
            if (f.images) {
                b.append("The report lists the images that were saved. Files the Image Uploader refused were listed on the upload page and are not in the report.\n\n");
            }
        } else {
            b.append("No upload report was written for this upload.\n\n");
        }

        if (!f.complete && f.uploadPageUrl != null) {
            b.append("You can upload the file again at ").append(f.uploadPageUrl).append("\n\n");
        }
        b.append("Replies to this message go to ").append(REPLY_TO).append(".\n");
        return b.toString();
    }

    private static final java.util.regex.Pattern CASE_NUMBER = java.util.regex.Pattern.compile("(?i)case number:\\s*(\\d+)");
    // A Java class name such as java.io.IOException or org.calacademy.antweb.AntwebException.
    private static final java.util.regex.Pattern JAVA_CLASS = java.util.regex.Pattern.compile("\\b[a-z][a-z0-9_]*(\\.[A-Za-z0-9_$]+)+(Exception|Error)\\b");

    /**
     * The "Message:" text for an upload that did not complete. Ordinary validation messages
     * are passed on as plain text. Crashes and unexpected errors (a case number, a Java
     * exception) become a fixed sentence, so no exception
     * names or stack text reach a curator.
     */
    public static String failureText(Object message) {
        String s = plain(message);
        if (s == null) return null;
        java.util.regex.Matcher m = CASE_NUMBER.matcher(s);
        boolean hasCase = m.find();
        boolean crash = hasCase || JAVA_CLASS.matcher(s).find() || s.contains("Exception")
            || s.contains(" ex:") || s.contains(" e:");
        if (!crash) return s;
        return "AntWeb hit an unexpected problem" + (hasCase ? " (case " + m.group(1) + ")" : "")
            + " and made no changes. Please reply to this email so we can look into it.";
    }

    /** The page's message as plain text, at most 500 characters. */
    public static String plain(Object message) {
        if (message == null) return null;
        String s = message.toString()
            .replaceAll("(?i)<br\\s*/?>", " ")
            .replaceAll("<[^>]*>", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&#x27;", "'").replace("&nbsp;", " ").replace("&amp;", "&")
            .replaceAll("\\s+", " ").trim();
        if (s.length() > 500) s = s.substring(0, 497) + "...";
        return s.isEmpty() ? null : s;
    }

    // ---------------- sending ----------------

    private static final ExecutorService s_sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "upload-notice");
        t.setDaemon(true);
        return t;
    });

    /**
     * Sends the mail to the uploader if skipReason() allows it, on a background thread so
     * the upload's page is not held up by the mail server. Never throws.
     */
    public static void send(Login uploader, boolean humanSession, Group uploadGroup, Facts f) {
        try {
            String email = uploader == null ? null : uploader.getEmail();
            int loginGroup = uploader != null && uploader.getGroup() != null ? uploader.getGroup().getId() : 0;
            int targetGroup = uploadGroup != null ? uploadGroup.getId() : loginGroup;
            if (f.groupName == null && uploadGroup != null) f.groupName = uploadGroup.getName();

            String reason = skipReason(settings(), humanSession, email, loginGroup, targetGroup);
            String what = (f.images ? "image" : "specimen") + " upload " + (f.complete ? "complete" : "did not complete")
                + " login:" + (uploader == null ? "none" : uploader.getId()) + " group:" + targetGroup;
            if (reason != null) {
                s_log.info("send() upload notice not sent (" + reason + ") " + what);
                LogMgr.appendDataLog("emails.log", "upload notice NOT SENT (" + reason + ") " + what);
                return;
            }
            final String to = email.trim();
            final String subject = subject(f);
            final String body = body(f);
            s_log.info("send() upload notice to <" + to + "> " + what);
            s_sender.submit(() -> {
                try {
                    Emailer.sendNotice(to, subject, body, REPLY_TO);
                } catch (Throwable t) {
                    s_log.error("send() upload notice to <" + to + "> e:" + t);
                }
            });
        } catch (Throwable t) {
            s_log.error("send() e:" + t);
        }
    }
}

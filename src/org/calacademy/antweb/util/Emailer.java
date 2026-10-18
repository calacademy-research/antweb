package org.calacademy.antweb.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Sends AntWeb's email through Amazon SES (SMTP).
 *
 * Settings: etc/mail.properties on the server (~/antweb/etc/, which the container
 * sees as /antweb/deploy/etc/; gitignored, chmod 600, never in the repository):
 *   host=email-smtp.us-west-2.amazonaws.com
 *   port=587
 *   username=...   SES SMTP user (send-only IAM user antweb-ses-smtp)
 *   password=...
 *   from=antweb@antweb.org
 * With no file, nothing is sent: the message is written to emails.log instead
 * (as before 2026-10). Changes are picked up within a minute.
 *
 * Safety limits: the same subject is sent at most once an hour, and at most
 * 100 messages a day, so an alert loop cannot flood anyone's inbox.
 *
 * Transactional mail to one user (password reset links) goes through
 * sendTransactional() instead: no per-subject limit, its own cap of
 * 2000 a day, and the body is never written to a log (it holds a secret link).
 *
 * Notices to one user (e.g. "your upload is complete") go through sendNotice():
 * plain text, no per-subject limit, at most 20 a day to one address and 500 a day in all.
 */
public class Emailer {

    private static final Log s_log = LogFactory.getLog(Emailer.class);
    private static final Log email_log = LogFactory.getLog("emailLog");

    static final String CONFIG = "/antweb/deploy/etc/mail.properties";
    static final int MAX_PER_DAY = 100;
    static final long SAME_SUBJECT_MS = 60 * 60 * 1000L;
    static final int MAX_TRANSACTIONAL_PER_DAY = 2000;
    static final int MAX_NOTICES_PER_DAY = 500;
    static final int MAX_NOTICES_PER_ADDRESS_PER_DAY = 20;

    private static Properties s_props = new Properties();
    private static long s_loaded = 0;
    private static final Map<String, Long> s_lastBySubject = new HashMap<>();
    private static int s_sentToday = 0;
    private static long s_dayStart = 0;
    private static int s_transactionalToday = 0;
    private static long s_transactionalDayStart = 0;
    private static int s_noticesToday = 0;
    private static long s_noticeDayStart = 0;
    private static final Map<String, Integer> s_noticesByAddress = new HashMap<>();

    public static void main(String[] args) {
    }

    private static synchronized Properties props() {
        long now = System.currentTimeMillis();
        if (now - s_loaded > 60_000) {
            s_loaded = now;
            Properties p = new Properties();
            File f = new File(CONFIG);
            if (f.isFile()) {
                try (InputStream in = new FileInputStream(f)) {
                    p.load(in);
                } catch (Exception e) {
                    s_log.error("props() could not read " + CONFIG + " e:" + e);
                }
            }
            s_props = p;
        }
        return s_props;
    }

    /** True when mail.properties has a host, username and password. */
    public static boolean isConfigured() {
        Properties p = props();
        return notBlank(p.getProperty("host")) && notBlank(p.getProperty("username")) && notBlank(p.getProperty("password"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Applies the hourly-per-subject and daily limits. Returns null if allowed, else the reason. */
    public static synchronized String throttle(String subject, long now) {
        if (now - s_dayStart > 24 * 60 * 60 * 1000L) {
            s_dayStart = now;
            s_sentToday = 0;
        }
        if (s_sentToday >= MAX_PER_DAY) return "daily limit of " + MAX_PER_DAY + " reached";
        String key = subject == null ? "" : subject;
        Long last = s_lastBySubject.get(key);
        if (last != null && now - last < SAME_SUBJECT_MS) return "same subject sent less than an hour ago";
        s_lastBySubject.put(key, now);
        s_sentToday++;
        return null;
    }

    /** The daily limit for transactional mail. Returns null if allowed, else the reason. */
    public static synchronized String throttleTransactional(long now) {
        if (now - s_transactionalDayStart > 24 * 60 * 60 * 1000L) {
            s_transactionalDayStart = now;
            s_transactionalToday = 0;
        }
        if (s_transactionalToday >= MAX_TRANSACTIONAL_PER_DAY) return "daily transactional limit of " + MAX_TRANSACTIONAL_PER_DAY + " reached";
        s_transactionalToday++;
        return null;
    }

    /** The daily limits for notices. Returns null if allowed, else the reason. */
    public static synchronized String throttleNotice(String to, long now) {
        if (now - s_noticeDayStart > 24 * 60 * 60 * 1000L) {
            s_noticeDayStart = now;
            s_noticesToday = 0;
            s_noticesByAddress.clear();
        }
        if (s_noticesToday >= MAX_NOTICES_PER_DAY) return "daily notice limit of " + MAX_NOTICES_PER_DAY + " reached";
        String key = to == null ? "" : to.trim().toLowerCase();
        int n = s_noticesByAddress.getOrDefault(key, 0);
        if (n >= MAX_NOTICES_PER_ADDRESS_PER_DAY) return "daily limit of " + MAX_NOTICES_PER_ADDRESS_PER_DAY + " notices to this address reached";
        s_noticesByAddress.put(key, n + 1);
        s_noticesToday++;
        return null;
    }

    /** Resets the limits (tests only). */
    public static synchronized void resetThrottle() {
        s_lastBySubject.clear();
        s_sentToday = 0;
        s_dayStart = 0;
        s_transactionalToday = 0;
        s_transactionalDayStart = 0;
        s_noticesToday = 0;
        s_noticeDayStart = 0;
        s_noticesByAddress.clear();
    }

    public static void sendMail(String to, String subject, String content) {
        if (!isConfigured()) {
            s_log.info("sendMail() mail not configured. Email NOT sent to <" + to + "> with subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "to:" + to + " subject:" + subject + " content:" + content);
            return;
        }
        String reason = throttle(subject, System.currentTimeMillis());
        if (reason != null) {
            s_log.warn("sendMail() not sent (" + reason + ") to <" + to + "> subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "NOT SENT (" + reason + ") to:" + to + " subject:" + subject);
            return;
        }
        deliver(to, subject, content);
    }

    /**
     * Mail to one user that must not be held back by the per-subject limit
     * (e.g. password reset links). Capped at MAX_TRANSACTIONAL_PER_DAY.
     * The content is never logged. Returns true if handed to the mail server.
     */
    public static boolean sendTransactional(String to, String subject, String content) {
        if (!isConfigured()) {
            s_log.warn("sendTransactional() mail not configured. Email NOT sent to <" + to + "> with subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "NOT SENT (mail not configured) to:" + to + " subject:" + subject + " (content withheld)");
            return false;
        }
        String reason = throttleTransactional(System.currentTimeMillis());
        if (reason != null) {
            s_log.warn("sendTransactional() not sent (" + reason + ") to <" + to + "> subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "NOT SENT (" + reason + ") to:" + to + " subject:" + subject);
            return false;
        }
        return deliver(to, subject, content);
    }

    /**
     * A plain-text notice to one user (e.g. "your upload is complete"), with Reply-To set.
     * Not held back by the per-subject limit; capped per address and per day (see throttleNotice).
     * Returns true if handed to the mail server.
     */
    public static boolean sendNotice(String to, String subject, String text, String replyTo) {
        if (!isConfigured()) {
            s_log.info("sendNotice() mail not configured. Email NOT sent to <" + to + "> with subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "NOT SENT (mail not configured) to:" + to + " subject:" + subject + " content:" + text);
            return false;
        }
        String reason = throttleNotice(to, System.currentTimeMillis());
        if (reason != null) {
            s_log.warn("sendNotice() not sent (" + reason + ") to <" + to + "> subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "NOT SENT (" + reason + ") to:" + to + " subject:" + subject);
            return false;
        }
        return deliver(to, subject, text, false, replyTo);
    }

    private static boolean deliver(String to, String subject, String content) {
        return deliver(to, subject, content, true, null);
    }

    private static boolean deliver(String to, String subject, String content, boolean html, String replyTo) {

        // Mark mail from anything but the live site, e.g. "[staging.antweb.org] ...".
        String site = AntwebProps.getDomainApp();
        if (site != null && !site.contains("www.antweb.org")) {
            String host = site.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
            subject = "[" + host + "] " + subject;
            content = content + (html ? "<br><br><small>Sent from " + host + "</small>" : "\n(Sent from " + host + ")\n");
        }

        Properties p = props();
        final String username = p.getProperty("username").trim();
        final String passwd = p.getProperty("password").trim();
        String from = p.getProperty("from", "antweb@antweb.org").trim();

        Properties smtp = new Properties();
        smtp.put("mail.smtp.auth", "true");
        smtp.put("mail.smtp.starttls.enable", "true");
        smtp.put("mail.smtp.starttls.required", "true");
        smtp.put("mail.smtp.host", p.getProperty("host").trim());
        smtp.put("mail.smtp.port", p.getProperty("port", "587").trim());
        smtp.put("mail.smtp.connectiontimeout", "15000");
        smtp.put("mail.smtp.timeout", "30000");
        smtp.put("mail.smtp.writetimeout", "30000");

        Session session = Session.getInstance(smtp, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, passwd);
                    }
                }
        );

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from, "AntWeb"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (replyTo != null) message.setReplyTo(InternetAddress.parse(replyTo));
            message.setSubject(subject);
            if (html) {
                message.setContent("<html>\n<body>\n" + content + "\n</body>\n</html>", "text/html; charset=UTF-8");
            } else {
                ((MimeMessage) message).setText(content, "UTF-8");
            }

            Transport.send(message);

            email_log.info("Email sent to <" + to + "> with subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "SENT to:" + to + " subject:" + subject);
            return true;

        } catch (Exception e) {
            s_log.error("Email to <" + to + "> with subject '" + subject + "' raised exception: " + e);
            LogMgr.appendDataLog("emails.log", "FAILED to:" + to + " subject:" + subject + " e:" + e);
            return false;
        }
    }

    public static void send(String body) {
      String recipients = AntwebUtil.getDevEmail();
      String subject = "Antweb Server Message";
      Emailer.sendMail(recipients, subject, body);
    }

}

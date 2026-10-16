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
 */
public class Emailer {

    private static final Log s_log = LogFactory.getLog(Emailer.class);
    private static final Log email_log = LogFactory.getLog("emailLog");

    static final String CONFIG = "/antweb/deploy/etc/mail.properties";
    static final int MAX_PER_DAY = 100;
    static final long SAME_SUBJECT_MS = 60 * 60 * 1000L;

    private static Properties s_props = new Properties();
    private static long s_loaded = 0;
    private static final Map<String, Long> s_lastBySubject = new HashMap<>();
    private static int s_sentToday = 0;
    private static long s_dayStart = 0;

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

    /** Resets the limits (tests only). */
    public static synchronized void resetThrottle() {
        s_lastBySubject.clear();
        s_sentToday = 0;
        s_dayStart = 0;
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

        // Mark mail from anything but the live site, e.g. "[staging.antweb.org] ...".
        String site = AntwebProps.getDomainApp();
        if (site != null && !site.contains("www.antweb.org")) {
            String host = site.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
            subject = "[" + host + "] " + subject;
            content = content + "<br><br><small>Sent from " + host + "</small>";
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
            message.setSubject(subject);
            message.setContent("<html>\n<body>\n" + content + "\n</body>\n</html>", "text/html; charset=UTF-8");

            Transport.send(message);

            email_log.info("Email sent to <" + to + "> with subject '" + subject + "'");
            LogMgr.appendDataLog("emails.log", "SENT to:" + to + " subject:" + subject);

        } catch (Exception e) {
            s_log.error("Email to <" + to + "> with subject '" + subject + "' raised exception: " + e);
            LogMgr.appendDataLog("emails.log", "FAILED to:" + to + " subject:" + subject + " e:" + e);
        }
    }

    public static void send(String body) {
      String recipients = AntwebUtil.getDevEmail();
      String subject = "Antweb Server Message";
      Emailer.sendMail(recipients, subject, body);
    }

}

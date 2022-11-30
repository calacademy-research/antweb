package org.calacademy.antweb.util;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Emailer {

    private static final Log s_log = LogFactory.getLog(Emailer.class);
    private static final Log email_log = LogFactory.getLog("emailLog");

    public static void main(String[] args) {
    }

    // Testable: https://www.antweb.org/util.do?action=emailTest
    public static void sendMail(String to, String subject, String content) {

        final String username = "antweb@calacademy.org";
        final String passwd = "75txAKc8&%By";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, passwd);
                    }
                }
        );

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("antweb@calacademy.org"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);

            //content = "<b>Content:</b>" + content;
            message.setContent("<html>\n" +
                    "<body>\n" +
                    content + "\n" +
                    "</body>\n" +
                    "</html>", "text/html");

            Transport.send(message);

            email_log.info("Email sent to <" + to + "> with subject '" + subject + "'");

        } catch (MessagingException e) {
            s_log.error("Email to <" + to + "> with subject '" + subject + "' raised exception: " + e);
//            throw new RuntimeException(e);
        }
    }

    public static void send(String body) {
      String recipients = AntwebUtil.getDevEmail(); // + ", " + AntwebUtil.getAdminEmail();
      String subject = "Antweb Server Message";
      Emailer.sendMail(recipients, subject, body);
    }

}
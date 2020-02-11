package org.calacademy.antweb.util;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

public class Emailer
{

   private static final Log s_log = LogFactory.getLog(Emailer.class);  

   public static void main(String [] args) {
   }

   public static void sendMail(String to, String subject, String content) {

		final String username = "antweb@calacademy.org";
		final String passwd = "75txAKc8&%By";

		Properties props = new Properties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");

		Session session = Session.getInstance(props,
		  new javax.mail.Authenticator() {
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

			s_log.warn("sendMail() done");

		} catch (MessagingException e) {
			throw new RuntimeException(e);
		}
    }  

}
/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.utils;

import com.crapi.config.MailConfiguration;
import com.crapi.config.MailHogConfiguration;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.*;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SMTPMailServer {
  @Autowired MailConfiguration mailConfiguration;

  @Autowired MailHogConfiguration mailhogConfiguration;

  /**
   * @param sendMail
   * @param body
   * @param subject send mail to given email with dynamic subject and body
   */
  public void sendMail(String sendMail, String body, String subject) {
    String mhogDomain = mailhogConfiguration.getDomain();
    Session session = mailhogConfiguration.sendmail();
    boolean useMailHog = false;
    try {
      // Reject CR/LF and other control characters before they reach any mail header.
      String safeRecipients = requireNoControlChars(sendMail, "recipient");
      String safeSubject = requireNoControlChars(subject, "subject");
      String safeBody = stripUnsafeBodyChars(body);
      log.info("sendMail  mhogDomain: {}, emails: {}", mhogDomain, safeRecipients);
      InternetAddress[] emails = InternetAddress.parse(safeRecipients);
      if (mhogDomain != null && !mhogDomain.isEmpty()) {
        if (mailConfiguration.getHost().trim().endsWith(mhogDomain)) {
          log.info(
              "SMTP host matches MailHog host. Using MailHog Configuration for sending emails");
          useMailHog = true;
        }
        for (InternetAddress emailAddress : emails) {
          String email = emailAddress.toString();
          String domain = email.substring(email.indexOf("@") + 1).trim();
          log.debug("sendMail  mhogDomain: {}, email: {}, domain: {}", mhogDomain, email, domain);
          if (mhogDomain.trim().equals(domain)) {
            log.info("Using MailHog Configuration for sending email for domain: " + domain);
            useMailHog = true;
          }
        }
      }
      if (!useMailHog) {
        session = mailConfiguration.sendmail();
        log.info("Using Mail Configuration for sending email: " + safeRecipients);
      }

      Message msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));

      msg.setRecipients(Message.RecipientType.TO, emails);
      msg.setSubject(safeSubject);
      msg.setContent(safeBody, "text/html");
      msg.setSentDate(new Date());

      MimeBodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setContent(safeBody, "text/html");

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Fails closed when a value destined for a mail header contains CR, LF or any other ISO control
   * character (prevents header / SMTP command injection).
   */
  static String requireNoControlChars(String value, String field) {
    if (value == null) {
      throw new IllegalArgumentException("Mail " + field + " must not be null");
    }
    for (int i = 0; i < value.length(); i++) {
      if (Character.isISOControl(value.charAt(i))) {
        throw new IllegalArgumentException("Mail " + field + " contains control characters");
      }
    }
    return value;
  }

  /** Removes control characters from the body, keeping tab, CR and LF which are valid in HTML. */
  static String stripUnsafeBodyChars(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!Character.isISOControl(c) || c == '\t' || c == '\n' || c == '\r') {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

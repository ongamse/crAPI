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
      log.info("sendMail  mhogDomain: {}, emails: {}", mhogDomain, sendMail);
      if (containsControlChars(sendMail)) {
        log.error("sendMail rejected: recipient contains control characters");
        return;
      }
      // Strict parsing rejects malformed addresses; fail closed before any header is built.
      InternetAddress[] emails = InternetAddress.parse(sendMail, true);
      for (InternetAddress emailAddress : emails) {
        emailAddress.validate();
      }
      if (emails.length == 0) {
        log.error("sendMail rejected: no recipient provided");
        return;
      }
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
        log.info("Using Mail Configuration for sending email: " + sendMail);
      }

      String configuredFrom = mailhogConfiguration.getFrom();
      if (containsControlChars(configuredFrom)) {
        log.error("sendMail rejected: sender contains control characters");
        return;
      }
      InternetAddress[] fromAddresses = InternetAddress.parse(configuredFrom, true);
      if (fromAddresses.length != 1) {
        log.error("sendMail rejected: exactly one sender address is required");
        return;
      }
      fromAddresses[0].validate();

      // Header values are rebuilt from validated/sanitized variables; body is stripped of
      // control characters (other than tab/CR/LF) before it reaches the MIME content.
      String safeSubject = removeControlChars(subject);
      String safeBody = removeNonPrintableBodyChars(body);

      MimeMessage msg = new MimeMessage(session);
      msg.setFrom(fromAddresses[0]);
      msg.setRecipients(Message.RecipientType.TO, emails);
      msg.setSubject(safeSubject);
      msg.setContent(safeBody, "text/html");
      msg.setSentDate(new Date());

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static boolean containsControlChars(String value) {
    if (value == null) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c == 0x7f) {
        return true;
      }
    }
    return false;
  }

  /** Replaces CR/LF runs with a space and drops all other control characters. */
  private static String removeControlChars(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    boolean lastWasBreak = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\r' || c == '\n') {
        if (!lastWasBreak) {
          sb.append(' ');
        }
        lastWasBreak = true;
      } else if (c < 0x20 || c == 0x7f) {
        lastWasBreak = false;
      } else {
        sb.append(c);
        lastWasBreak = false;
      }
    }
    return sb.toString();
  }

  /** Drops control characters from the body except tab, CR and LF. */
  private static String removeNonPrintableBodyChars(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\t' || c == '\r' || c == '\n' || (c >= 0x20 && c != 0x7f)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

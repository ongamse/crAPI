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
      InternetAddress[] emails = parseRecipients(sendMail);
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

      Message msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));

      String safeSubject = sanitizeHeaderValue(subject);
      String safeBody = sanitizeBody(body);

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
   * Parse and strictly validate recipient addresses. Rejects any value containing control
   * characters (CR/LF header injection) or that is not a syntactically valid address.
   *
   * @param recipients comma separated list of addresses
   * @return validated addresses
   * @throws AddressException if the value is empty, contains control characters or is invalid
   */
  static InternetAddress[] parseRecipients(String recipients) throws AddressException {
    if (recipients == null || recipients.trim().isEmpty()) {
      throw new AddressException("Recipient address is empty");
    }
    for (int i = 0; i < recipients.length(); i++) {
      if (Character.isISOControl(recipients.charAt(i))) {
        throw new AddressException("Recipient address contains control characters");
      }
    }
    InternetAddress[] addresses = InternetAddress.parse(recipients, true);
    if (addresses.length == 0) {
      throw new AddressException("Recipient address is empty");
    }
    for (InternetAddress address : addresses) {
      address.validate();
    }
    return addresses;
  }

  /**
   * Neutralize header values: strip CR/LF and any other control characters so the value cannot
   * terminate the header and inject new ones.
   */
  static String sanitizeHeaderValue(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      sb.append(Character.isISOControl(c) ? ' ' : c);
    }
    return sb.toString().trim();
  }

  /**
   * Remove control characters from the body other than normal whitespace (tab, CR, LF). Line breaks
   * are legitimate inside a MIME body, which is encoded separately from the headers.
   */
  static String sanitizeBody(String value) {
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

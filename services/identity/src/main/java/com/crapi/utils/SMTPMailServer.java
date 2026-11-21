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
    InternetAddress[] safeRecipients;
    String safeSubject;
    try {
      safeRecipients = validateRecipients(sendMail);
      safeSubject = validateHeaderValue(subject, "subject");
    } catch (AddressException | IllegalArgumentException e) {
      log.warn("Mail not sent: rejected unsafe recipient or subject");
      return;
    }
    sendValidatedMail(safeRecipients, body, safeSubject);
  }

  /**
   * Reject header values containing CR, LF or other control characters, which can be used to inject
   * additional mail headers.
   */
  static String validateHeaderValue(String value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is missing");
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029) {
        throw new IllegalArgumentException(field + " contains illegal control characters");
      }
    }
    return value;
  }

  /**
   * Validate the recipient list: no control characters and every address must be a syntactically
   * valid e-mail address. The list is strictly parsed once and the parsed addresses are returned so
   * callers do not need to re-parse the raw string.
   */
  static InternetAddress[] validateRecipients(String recipients) throws AddressException {
    String checked = validateHeaderValue(recipients, "recipient");
    InternetAddress[] parsed = InternetAddress.parseHeader(checked, true);
    if (parsed.length == 0) {
      throw new AddressException("No recipient address", checked);
    }
    for (InternetAddress address : parsed) {
      address.validate();
    }
    return parsed;
  }

  private void sendValidatedMail(InternetAddress[] emails, String body, String subject) {
    String mhogDomain = mailhogConfiguration.getDomain();
    Session session = mailhogConfiguration.sendmail();
    boolean useMailHog = false;
    String sendMail = InternetAddress.toString(emails);
    try {
      log.info("sendMail  mhogDomain: {}, emails: {}", mhogDomain, sendMail);
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

      msg.setRecipients(Message.RecipientType.TO, emails);
      msg.setSubject(subject);
      msg.setContent(body, "text/html");
      msg.setSentDate(new Date());

      MimeBodyPart messageBodyPart = new MimeBodyPart();
      messageBodyPart.setContent(body, "text/html");

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

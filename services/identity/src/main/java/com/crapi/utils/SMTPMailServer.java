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
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SMTPMailServer {
  private static final Pattern SAFE_ADDRESS =
      Pattern.compile("[^\\s<>(),;:\\\\\"\\[\\]@]+@[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?");

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
      if (containsLineBreak(sendMail)) {
        log.error("sendMail rejected: CR/LF characters are not allowed in recipient");
        return;
      }
      InternetAddress[] emails = toValidatedAddresses(sendMail);
      String safeSubject = sanitizeHeaderValue(subject);
      log.info("sendMail  mhogDomain: {}, recipient count: {}", mhogDomain, emails.length);
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
      msg.setSubject(safeSubject, "UTF-8");
      msg.setContent(body, "text/html");
      msg.setSentDate(new Date());

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Parse the recipient list strictly, validate every address and re-create it from the validated
   * bare address only (display names, comments and group syntax are dropped).
   */
  private static InternetAddress[] toValidatedAddresses(String recipients) throws AddressException {
    InternetAddress[] parsed = InternetAddress.parse(recipients, true);
    InternetAddress[] validated = new InternetAddress[parsed.length];
    for (int i = 0; i < parsed.length; i++) {
      String bare = parsed[i].getAddress();
      if (bare == null || !SAFE_ADDRESS.matcher(bare).matches()) {
        throw new AddressException("Invalid recipient address");
      }
      InternetAddress rebuilt = new InternetAddress(bare, true);
      rebuilt.validate();
      validated[i] = rebuilt;
    }
    return validated;
  }

  /** Replace any CR/LF in a header value with a single space so no extra header can be injected. */
  private static String sanitizeHeaderValue(String value) {
    return value == null ? "" : value.replaceAll("[\\r\\n]+", " ");
  }

  private static boolean containsLineBreak(String value) {
    return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
  }
}

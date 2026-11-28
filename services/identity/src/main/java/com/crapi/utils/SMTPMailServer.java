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
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.MimeMessageHelper;
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
      if (containsLineBreak(sendMail) || containsLineBreak(subject)) {
        log.warn("sendMail rejected: CR/LF characters are not allowed in recipient or subject");
        return;
      }
      log.info("sendMail  mhogDomain: {}, emails: {}", mhogDomain, sendMail);
      // strict parsing: only RFC 822 compliant addresses, no free-form header content
      InternetAddress[] emails = InternetAddress.parse(sendMail, true);
      for (InternetAddress emailAddress : emails) {
        emailAddress.validate();
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

      MimeMessage msg = new MimeMessage(session);
      MimeMessageHelper helper = new MimeMessageHelper(msg, StandardCharsets.UTF_8.name());

      helper.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));
      helper.setTo(emails);
      // setSubject encodes the value as an RFC 2047 header word, never as raw header text.
      helper.setSubject(subject == null ? "" : subject);
      helper.setText(body == null ? "" : body, true);
      helper.setSentDate(new Date());

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** Header values (recipients, subject) must not contain CR or LF to prevent header injection. */
  private static boolean containsLineBreak(String value) {
    return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
  }
}

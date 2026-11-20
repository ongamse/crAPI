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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SMTPMailServer {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("([A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]{1,64})@([A-Za-z0-9.-]{1,255})");
  private static final Pattern SUBJECT_PATTERN =
      Pattern.compile("([\\p{L}\\p{N} .,:;'!?()_@/&-]{1,200})");

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
      // Every value reaching JavaMail is rebuilt from strictly validated components.
      InternetAddress[] emails = parseRecipients(sendMail);
      String safeRecipients = joinAddresses(emails);
      String safeSubject = buildSubject(subject);
      String safeBody = stripUnsafeBodyChars(body);
      log.info("sendMail  mhogDomain: {}, emails: {}", mhogDomain, safeRecipients);
      if (mhogDomain != null && !mhogDomain.isEmpty()) {
        if (mailConfiguration.getHost().trim().endsWith(mhogDomain)) {
          log.info(
              "SMTP host matches MailHog host. Using MailHog Configuration for sending emails");
          useMailHog = true;
        }
        for (InternetAddress emailAddress : emails) {
          String email = emailAddress.getAddress();
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

      MimeMessage msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));

      msg.setRecipients(Message.RecipientType.TO, emails);
      msg.setSubject(MimeUtility.encodeText(safeSubject, "UTF-8", "B"));
      msg.setContent(safeBody, "text/html; charset=UTF-8");
      msg.setSentDate(new Date());

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Parses recipients strictly and rebuilds each address from the regex-matched, validated address
   * part only (no display name, comments or group syntax survive).
   */
  static InternetAddress[] parseRecipients(String recipients) throws AddressException {
    String checked = requireNoControlChars(recipients, "recipient");
    // Split on commas ourselves; every element must fully match the strict address pattern, so
    // display names, quoted strings, comments, angle brackets and group syntax are all rejected.
    String[] parts = checked.split(",", -1);
    InternetAddress[] rebuilt = new InternetAddress[parts.length];
    for (int i = 0; i < parts.length; i++) {
      Matcher m = EMAIL_PATTERN.matcher(parts[i].trim());
      if (!m.matches()) {
        throw new IllegalArgumentException("Mail recipient is not a valid address");
      }
      // strict=true runs JavaMail's RFC 822 address check on the matched value.
      rebuilt[i] = new InternetAddress(m.group(1) + "@" + m.group(2), true);
    }
    return rebuilt;
  }

  private static String joinAddresses(InternetAddress[] addresses) {
    StringBuilder sb = new StringBuilder();
    for (InternetAddress a : addresses) {
      if (sb.length() > 0) {
        sb.append(',');
      }
      sb.append(a.getAddress());
    }
    return sb.toString();
  }

  /** Subject is limited to a conservative character allowlist and rebuilt from the match. */
  static String buildSubject(String subject) {
    String checked = requireNoControlChars(subject, "subject");
    Matcher m = SUBJECT_PATTERN.matcher(checked);
    if (!m.matches()) {
      throw new IllegalArgumentException("Mail subject contains unsupported characters");
    }
    return m.group(1);
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

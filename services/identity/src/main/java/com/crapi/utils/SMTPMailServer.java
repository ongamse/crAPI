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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
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
      // Rebuild recipients and subject from validated, canonical data only.
      InternetAddress[] emails = buildValidatedRecipients(sendMail);
      String safeSubject = sanitizeSubject(subject);
      String safeRecipients = joinAddresses(emails);
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
            log.info("Using MailHog Configuration for sending email for domain: {}", domain);
            useMailHog = true;
          }
        }
      }
      if (!useMailHog) {
        session = mailConfiguration.sendmail();
        log.info("Using Mail Configuration for sending email: {}", safeRecipients);
      }

      MimeMessage msg = new MimeMessage(session);

      msg.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));

      msg.setRecipients(Message.RecipientType.TO, emails);
      msg.setSubject(safeSubject, "UTF-8");
      msg.setText(rebuildHtmlBody(body), "UTF-8", "html");
      msg.setSentDate(new Date());

      Transport.send(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final Pattern PLAIN_ADDRESS =
      Pattern.compile("^[A-Za-z0-9._%+'-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,}$");

  private static final int MAX_SUBJECT_LENGTH = 255;

  /** Constant alphabet of characters copied verbatim into the body: printable ASCII, TAB, LF, CR. */
  private static final String BODY_ALPHABET;

  static {
    StringBuilder alphabet = new StringBuilder();
    for (char c = 0x20; c <= 0x7E; c++) {
      alphabet.append(c);
    }
    alphabet.append('\t').append('\n').append('\r');
    BODY_ALPHABET = alphabet.toString();
  }

  /**
   * Rebuild the HTML body from the constant {@link #BODY_ALPHABET}. Every character that is in the
   * alphabet is copied from the constant (so the HTML markup built by the application still
   * renders); every other character (control characters, non-ASCII) is emitted as a numeric
   * character reference, which is valid HTML and cannot carry raw control characters. Callers are
   * responsible for HTML-escaping user supplied values (see {@link MailBody}).
   */
  static String rebuildHtmlBody(String body) {
    if (body == null) {
      throw new IllegalArgumentException("Mail body must not be null");
    }
    StringBuilder sb = new StringBuilder(body.length());
    int i = 0;
    while (i < body.length()) {
      int codePoint = body.codePointAt(i);
      i += Character.charCount(codePoint);
      int index = BODY_ALPHABET.indexOf(codePoint);
      if (index >= 0) {
        sb.append(BODY_ALPHABET.charAt(index));
      } else if (!Character.isISOControl(codePoint)) {
        sb.append("&#").append(codePoint).append(';');
      }
    }
    return sb.toString();
  }

  /**
   * Split the comma separated recipient list, accept only entries that fully match a strict plain
   * address pattern (no display name, whitespace or control characters) and construct fresh strict
   * {@link InternetAddress} objects from the validated strings. Throws if any entry is invalid so
   * that no mail is sent.
   */
  static InternetAddress[] buildValidatedRecipients(String recipients) throws AddressException {
    if (recipients == null || recipients.isEmpty()) {
      throw new IllegalArgumentException("Mail recipient must not be empty");
    }
    String[] parts = recipients.split(",", -1);
    List<InternetAddress> validated = new ArrayList<>();
    for (String part : parts) {
      String candidate = part.trim();
      if (!PLAIN_ADDRESS.matcher(candidate).matches()) {
        throw new IllegalArgumentException("Mail recipient is not a valid plain email address");
      }
      InternetAddress address = new InternetAddress(candidate, true);
      address.validate();
      validated.add(address);
    }
    return validated.toArray(new InternetAddress[0]);
  }

  /** Remove every ISO control character from the subject and cap its length. */
  static String sanitizeSubject(String subject) {
    if (subject == null) {
      throw new IllegalArgumentException("Mail subject must not be null");
    }
    StringBuilder sb = new StringBuilder(Math.min(subject.length(), MAX_SUBJECT_LENGTH));
    for (int i = 0; i < subject.length() && sb.length() < MAX_SUBJECT_LENGTH; i++) {
      char c = subject.charAt(i);
      if (!Character.isISOControl(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private static String joinAddresses(InternetAddress[] addresses) {
    StringBuilder sb = new StringBuilder();
    for (InternetAddress address : addresses) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(address.getAddress());
    }
    return sb.toString();
  }
}

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
import com.crapi.exception.CRAPIExceptionHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SMTPMailServer {
  private static final int MAX_ADDRESS_LENGTH = 254;
  private static final int MAX_LOCAL_PART_LENGTH = 64;
  private static final int MAX_BODY_LENGTH = 100_000;
  private static final String INVALID_RECIPIENT_MESSAGE = "Invalid e-mail address";
  private static final String DEFAULT_SUBJECT = "crAPI Notification";

  /** Single mailbox addr-spec: no whitespace, control chars, commas, semicolons or quotes. */
  private static final Pattern ADDRESS_PATTERN =
      Pattern.compile(
          "^([A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*)"
              + "@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*)$");

  /** Server-controlled subject lines, keyed by the value the callers pass. */
  private static final Map<String, String> ALLOWED_SUBJECTS =
      Map.of(
          "crAPI OTP", "crAPI OTP",
          "Welcome to crAPI", "Welcome to crAPI",
          "Unlock your account", "Unlock your account",
          "crAPI: Change Email Token", "crAPI: Change Email Token",
          "crAPI: Change Phone Number OTP", "crAPI: Change Phone Number OTP");

  @Autowired MailConfiguration mailConfiguration;

  @Autowired MailHogConfiguration mailhogConfiguration;

  /**
   * @param sendMail
   * @param body
   * @param subject send mail to given email with dynamic subject and body
   */
  public void sendMail(String sendMail, String body, String subject) {
    sendMail(toSafeAddress(sendMail), body, subject);
  }

  /**
   * Send mail to an already validated, typed recipient. The address is re-checked here so the
   * transport never sees an address that did not pass {@link #validateRecipient(String)}.
   *
   * @param validatedRecipient single mailbox, normally produced by {@link
   *     #validateRecipient(String)}
   * @param body HTML body, dynamic values must already be encoded
   * @param subject key selecting one of the server-controlled subject lines
   */
  public void sendMail(InternetAddress validatedRecipient, String body, String subject) {
    // Validate everything that is attacker influenced before any I/O. Invalid input is rejected
    // with a CRAPIExceptionHandler (HTTP 400) that the controller advice turns into a response.
    if (validatedRecipient == null) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    final InternetAddress recipient = toSafeAddress(validatedRecipient.getAddress());
    final String fixedSubject = resolveSubject(subject);
    final String safeBody = sanitizeBody(body);

    String mhogDomain = mailhogConfiguration.getDomain();
    Session session = mailhogConfiguration.sendmail();
    boolean useMailHog = false;
    try {
      log.info("sendMail  mhogDomain: {}", mhogDomain);
      if (mhogDomain != null && !mhogDomain.isEmpty()) {
        if (mailConfiguration.getHost().trim().endsWith(mhogDomain)) {
          log.info(
              "SMTP host matches MailHog host. Using MailHog Configuration for sending emails");
          useMailHog = true;
        }
        String email = recipient.getAddress();
        String domain = email.substring(email.indexOf("@") + 1).trim();
        if (mhogDomain.trim().equals(domain)) {
          log.info("Using MailHog Configuration for sending email for domain: {}", domain);
          useMailHog = true;
        }
      }
      if (!useMailHog) {
        session = mailConfiguration.sendmail();
        log.info("Using Mail Configuration for sending email");
      }

      MimeMessage msg = buildMessage(session, recipient, fixedSubject, safeBody);

      Transport.send(msg);
    } catch (Exception e) {
      log.error("Failed to send mail", e);
    }
  }

  /**
   * Assemble the outgoing message exclusively from allowlist-rebuilt values: a recipient produced
   * by {@link #toSafeAddress(String)}, a subject from the fixed table and a body produced by {@link
   * #sanitizeBody(String)}.
   */
  private MimeMessage buildMessage(
      Session session, InternetAddress safeRecipient, String fixedSubject, String safeBody)
      throws MessagingException {
    MimeMessage msg = new MimeMessage(session);
    msg.setFrom(new InternetAddress(mailhogConfiguration.getFrom(), false));
    msg.setRecipient(Message.RecipientType.TO, safeRecipient);
    msg.setSubject(fixedSubject, StandardCharsets.UTF_8.name());
    msg.setText(safeBody, StandardCharsets.UTF_8.name(), "html");
    msg.setSentDate(new Date());
    return msg;
  }

  /**
   * Rebuild the recipient from a character allowlist. A new string is assembled one character at a
   * time from {@code [A-Za-z0-9._%+-@]}; any other character, or a result that differs from the
   * input, is rejected. The {@link InternetAddress} is created from that rebuilt copy only.
   *
   * @throws CRAPIExceptionHandler (400) when the value is not a safe single mailbox
   */
  static InternetAddress toSafeAddress(String recipient) {
    if (recipient == null || recipient.length() > MAX_ADDRESS_LENGTH) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    StringBuilder rebuilt = new StringBuilder(recipient.length());
    for (int i = 0; i < recipient.length(); i++) {
      char c = recipient.charAt(i);
      boolean allowed =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '.'
              || c == '_'
              || c == '%'
              || c == '+'
              || c == '-'
              || c == '@';
      if (!allowed) {
        throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
      }
      rebuilt.append(c);
    }
    String safe = rebuilt.toString();
    if (!safe.equals(recipient) || !ADDRESS_PATTERN.matcher(safe).matches()) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    try {
      InternetAddress address = new InternetAddress(safe, true);
      address.validate();
      return address;
    } catch (AddressException e) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
  }

  /**
   * Strictly validate a single recipient mailbox. Rejects null/blank values, anything containing
   * whitespace, control characters (CR/LF header injection), commas or semicolons (multiple
   * recipients), display names, or anything that is not a plain addr-spec. The returned address is
   * rebuilt from the regex-validated parts.
   *
   * @param recipient the single address to validate
   * @return validated address
   * @throws CRAPIExceptionHandler (400) when the value is not a valid single mailbox
   */
  public static InternetAddress validateRecipient(String recipient) {
    if (recipient == null || recipient.length() > MAX_ADDRESS_LENGTH) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    Matcher matcher = ADDRESS_PATTERN.matcher(recipient);
    if (!matcher.matches()) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    String local = matcher.group(1);
    String domain = matcher.group(2);
    if (local.length() > MAX_LOCAL_PART_LENGTH) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
    try {
      InternetAddress address = new InternetAddress(local + "@" + domain, true);
      address.validate();
      return address;
    } catch (AddressException e) {
      throw new CRAPIExceptionHandler(INVALID_RECIPIENT_MESSAGE, 400);
    }
  }

  /**
   * The subject is never taken from the caller's string. The caller value is only used as a key to
   * select one of the server-controlled subject lines; unknown values map to a generic subject.
   */
  static String resolveSubject(String requested) {
    String fixed = requested == null ? null : ALLOWED_SUBJECTS.get(requested);
    return fixed != null ? fixed : DEFAULT_SUBJECT;
  }

  /**
   * Remove control characters from the body other than normal whitespace (tab, CR, LF). Line breaks
   * are legitimate inside a MIME body, which is encoded separately from the headers. Dynamic
   * values must already be HTML encoded by the MailBody builders.
   */
  static String sanitizeBody(String value) {
    if (value == null) {
      return "";
    }
    int limit = Math.min(value.length(), MAX_BODY_LENGTH);
    StringBuilder sb = new StringBuilder(limit);
    for (int i = 0; i < limit; i++) {
      char c = value.charAt(i);
      if (!Character.isISOControl(c) || c == '\t' || c == '\n' || c == '\r') {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}

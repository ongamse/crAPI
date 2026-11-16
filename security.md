# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Raw cursor queries in the workshop service pass request values as bound %s parameters, never by string concatenation.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-url-header -->
### sec-outbound-url-header — Validate outbound URLs and header values

User-supplied URLs go through utils.helper.is_safe_outbound_url (http/https only, globally routable hosts, redirects disabled) and request-derived header values through is_safe_header_value before any outbound request.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-path-containment -->
### sec-path-containment — Contain file paths

Report files are resolved with resolve_report_path (realpath plus containment under the reports directory); reuse it for any new file access driven by request input.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWT signatures before trusting claims

JwtProvider verifies RSA signatures against the server's own public key only; never derive verification keys from token headers (jku, kid) or accept unsigned or HMAC tokens.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-owner-check -->
### sec-owner-check — Check resource ownership

Identity endpoints that take a resource id (carId, video_id) resolve the caller from the token and fail closed unless the caller owns the object; new endpoints should do the same.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secrets-env -->
### sec-secrets-env — Keep credentials and keys out of git

Passwords, JWT/Django secrets and signing keys come from required environment variables or the crapi-secrets Secret, and TLS keys and the JWKS are mounted at deploy time. Never commit them; any value already in history must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Keep TLS verification on

Outbound HTTPS uses settings.TLS_VERIFY (system CAs, or TLS_CA_BUNDLE for an internal CA) and the JVM default trust store; never set verify=False or use a trust-all context.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-cors-hosts -->
### sec-cors-hosts — Allowlist origins and hosts

CORS is configured centrally from CORS_ALLOWED_ORIGINS and Django ALLOWED_HOSTS from the ALLOWED_HOSTS env var; add trusted values there instead of using wildcards or per-controller @CrossOrigin.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-injection -->
### sec-mail-injection — Sanitize mail headers and bodies

SMTPMailServer rejects CR/LF in recipient and subject, parses recipients strictly, and MailBody HTML-escapes user values; apply the same to any new mail built from user input.
<!-- /harness-remediation -->

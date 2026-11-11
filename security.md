# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Request values such as coupon codes go to cursor.execute as a params list with %s placeholders, never string concatenation.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-path-confine -->
### sec-path-confine — Confine request-built file paths

Paths built from request data are resolved with realpath and checked with os.path.commonpath against the reports directory, and numeric ids are coerced with int().
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-requests -->
### sec-outbound-requests — Validate outbound URLs, headers and TLS

Use utils.helper.validate_outbound_url (with the MECHANIC_API_ALLOWED_HOSTS allowlist, no redirects) and validate_header_value before server-side requests, and never use verify=False or a trust-all TrustManager; private CAs go through TLS_CA_BUNDLE or the JVM truststore.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWT signatures before trusting claims

JwtProvider.getVerifiedClaims accepts only signed RS256 tokens verified with the configured RSA key plus an expiry check; route all token reads through it and never honour jku or unsigned tokens.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-ownership-checks -->
### sec-ownership-checks — Check ownership on by-id endpoints

Identity endpoints resolve the caller from the token and compare it to the owner (ProfileServiceImpl.isOwnerOrAdmin, vehicle owner check) before returning or mutating a record.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-input -->
### sec-mail-input — Sanitize mail headers and HTML

SMTPMailServer strictly parses recipients and rejects CR/LF in header values, and MailBody HTML-escapes user-supplied fields; send any new user-controlled mail data through them.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-committed-secrets -->
### sec-no-committed-secrets — Supply credentials and keys at deploy time

DB, gateway and mechanic passwords, TLS private keys and the JWKS are generated or mounted at deploy time (generate-credentials.sh, create-credentials.sh, Helm credentials and tls secrets, JWKS env); add new secrets to those generators, never as literal env fallbacks or committed files, and rotate anything already in git history.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secure-random -->
### sec-secure-random — Use SecureRandom for generated values

Seed data and any generated values use java.security.SecureRandom with default seeding; never call setSeed with a constant.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-cors-hosts -->
### sec-cors-hosts — Allowlist CORS origins and Host headers

CORS comes from one CorsConfigurationSource driven by CORS_ALLOWED_ORIGINS and Django ALLOWED_HOSTS comes from an env list that ignores a bare wildcard; add legitimate origins and hostnames through those env vars, not @CrossOrigin or '*'.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-postman-tokens -->
### sec-postman-tokens — Use Postman variables for tokens

Postman collections reference the {{token}} variable instead of pasted JWTs.
<!-- /harness-remediation -->

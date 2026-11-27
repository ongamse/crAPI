# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Pass request values to cursor.execute as a params list with %s placeholders, never by string concatenation (see ApplyCouponView).
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-url -->
### sec-outbound-url — Rebuild and constrain outbound requests

Validate user-supplied URLs (scheme, host allowlist, port, no userinfo) and request the rebuilt URL, never the raw input. Do not forward end-user Authorization headers to user-chosen targets; send a fixed header set.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Keep TLS verification on

Outbound calls from workshop and identity verify certificates against the default trust store. For self-signed deployments supply a CA bundle or truststore through env (TLS_CA_BUNDLE, API_GATEWAY_TLS_TRUSTSTORE) rather than disabling verification.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-file-paths -->
### sec-file-paths — Derive file names on the server

Never join request values into file paths. Extract a validated numeric id and build the server-side name (report_<id>) in a fixed directory.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Read JWT claims only from verified tokens

All claim reads in JwtProvider go through one verified parse (RS256 only, signature and expiry checked). Add new claim accessors on top of it instead of parsing tokens directly.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-idor-owner-scope -->
### sec-idor-owner-scope — Authorize by the authenticated principal

Resolve the caller from the token and load objects through principal-scoped queries (for example findByUser_id), then compare any path id against the loaded entity. Never use a client-supplied id as the data key.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-recipient -->
### sec-mail-recipient — Validate and persist mail recipients

Validate user-chosen email addresses, persist them first, and send to the persisted value through SMTPMailServer's allowlist rebuild, fixed subject table and sanitized body.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secrets-in-vcs -->
### sec-secrets-in-vcs — Keep credentials and key material out of git

Load passwords, signing keys and TLS keys from env or mounted Secrets (compose TLS_KEY_FILE, helm tlsKeySecret, jwt-signing-keys, DB_PASSWORD required) and generate fixtures in tests. Previously committed values remain in git history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-origins-hosts -->
### sec-origins-hosts — No wildcard CORS origins or allowed hosts

CORS origins (CORS_ALLOWED_ORIGINS) and workshop ALLOWED_HOSTS come from env with no wildcard default. Add new origins or hostnames to those variables, never per-controller @CrossOrigin or '*'.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secure-random -->
### sec-secure-random — Use SecureRandom for generated values

Seed data and token generators use java.security.SecureRandom without constant seeds; do not use Math.random or fixed seeds for anything an attacker could predict.
<!-- /harness-remediation -->

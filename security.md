# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-no-committed-secrets -->
### sec-no-committed-secrets — Keep secrets and keys out of the repo

Passwords, signing keys (JWKS), TLS private keys and captured JWTs must be supplied at runtime from env vars or mounted secrets, never committed, defaulted, or embedded in URLs; anything previously committed remains in git history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-db-credentials-out-of-urls -->
### sec-db-credentials-out-of-urls — Pass DB credentials as driver arguments

The chatbot service passes Mongo and Postgres credentials from env via client/driver arguments instead of building user:pass@host URLs or using a default password literal.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-allowlist -->
### sec-outbound-allowlist — Never build outbound requests from user input

contact_mechanic only lets the user select an entry from settings.MECHANIC_API_ALLOWED_URLS and requests that configured URL with whitelisted params and TLS verification on; do not forward inbound headers or disable certificate checks (verify=False, trust-all contexts).
<!-- /harness-remediation -->

<!-- harness-remediation:sec-escape-mail-content -->
### sec-escape-mail-content — Escape and validate mail content

Values interpolated into outgoing mail are HTML-escaped in MailBody, the subject is MIME-encoded, and email/phone/otp request forms are validated at the boundary with @Email/@Pattern.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-owner-scoped-lookups -->
### sec-owner-scoped-lookups — Resolve resources from the authenticated user

Profile videos and vehicles are loaded through the token user's own records and the path id is only compared against them, with no admin bypass, so a client-supplied id never selects another user's object.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify-first -->
### sec-jwt-verify-first — Verify JWT signatures before reading claims

JwtProvider verifies tokens in one call against the fixed RSA public key and requires RS256, which rejects alg=none and HMAC-confusion tokens; use it for any new token handling.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-csrf-exemptions-explicit -->
### sec-csrf-exemptions-explicit — Keep CSRF exemptions explicit

Only an explicit POST-only list of pre-authentication identity endpoints and requests with a Bearer or ApiKey Authorization scheme skip CSRF; add new unauthenticated routes to that list deliberately rather than widening a path wildcard.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-parameterized-sql -->
### sec-parameterized-sql — Bind SQL parameters

Coupon validation passes request values to cursor.execute as %s parameters instead of concatenating them into SQL; use placeholders for every raw query.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-config-hardening -->
### sec-config-hardening — Avoid wildcard and predictable defaults

Django ALLOWED_HOSTS is read from an env var with explicit hosts instead of ['*'], and seeded data uses an unseeded SecureRandom instead of a fixed seed.
<!-- /harness-remediation -->

# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-secrets-from-config -->
### sec-secrets-from-config — Keep credentials and keys out of the repo

Database, TLS and JWT signing secrets are read from environment or Kubernetes/Helm Secrets (secretKeyRef, optional JWKS env var, DB_PASSWORD required with no default) and credentials are passed to clients as separate parameters rather than embedded in URIs. Anything that was ever committed stays in git history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-single-verification -->
### sec-jwt-single-verification — Verify JWTs once, with the server key only

JwtProvider.getVerifiedSubject is the only way to read a token subject: it checks structure and size, requires RS256, verifies the signature with the server key and checks subject and expiry. Never read claims from an unverified token or pick a verification key from a token header (jku/kid).
<!-- /harness-remediation -->

<!-- harness-remediation:sec-owner-scoped-lookups -->
### sec-owner-scoped-lookups — Scope data access to the authenticated owner

Identity repositories look up videos and vehicles by id and owner (findByIdAndUser_Id, findByUuidAndOwner_Id) so foreign and unknown ids behave the same. Admin-only unscoped access lives in an explicitly named method behind an explicit role check.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-requests -->
### sec-outbound-requests — Validate and verify outbound requests

User-driven fetches go through utils.helper.build_safe_outbound_url (allowlisted host, http/https only, public IPs, URL rebuilt from validated parts) with redirects disabled and a timeout. TLS verification stays on; use a CA bundle for internal CAs instead of disabling it, and rebuild forwarded Authorization headers from a validated token.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-file-access -->
### sec-file-access — Never open request-derived paths

Report files are chosen from a directory listing or generated from an integer primary key, then opened relative to the reports directory fd with O_NOFOLLOW and an fstat regular-file check instead of exists/isfile pre-checks.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Raw SQL in the workshop service uses cursor.execute with %s placeholders and a params list; request values are never concatenated into the query string.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-input -->
### sec-mail-input — Validate and escape user text in mail

Recipients are strictly parsed once, rebuilt from the validated address, capped in number, and the subject is length-bounded; user-supplied values are HTML-escaped in the mail body and the form enforces an email pattern at the controller boundary.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-error-responses -->
### sec-error-responses — Return generic error bodies

Exception handlers return fixed messages or field:message pairs and log full exceptions server-side; never put exception text or BindingResult/object toString output in a response.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-cors-hosts-random -->
### sec-cors-hosts-random — Restrict origins, hosts and randomness

CORS is configured centrally from CORS_ALLOWED_ORIGINS (same-origin by default) and Django ALLOWED_HOSTS comes from an env var without wildcards. Use SecureRandom for generated data and never call setSeed with a constant.
<!-- /harness-remediation -->

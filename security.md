# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-no-committed-secrets -->
### sec-no-committed-secrets — Never commit keys, tokens or passwords

Private keys, JWKS, TLS keystores, JWTs and default passwords are supplied at runtime (env, mounted files, Helm required values, fail-closed startup) and never committed. Anything that was ever committed stays in git history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-credentials-out-of-uris -->
### sec-credentials-out-of-uris — Keep credentials out of connection strings

Pass database passwords to the driver as separate arguments (for example sqlalchemy URL.create or Motor keyword arguments) instead of interpolating user:password into a URI, and require the password from the environment with no in-code default.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-bind-sql-params -->
### sec-bind-sql-params — Bind SQL parameters

Request values go to cursor.execute as bound parameters, never through string concatenation. Tests assert that an injection payload is neutralised, not that it works.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-requests -->
### sec-outbound-requests — Constrain outbound requests

Client-supplied URLs are checked against an env-driven host allowlist with redirects disabled, forwarded headers are rebuilt from a known scheme and a restricted charset, and TLS verification stays on (supply internal CAs via REQUESTS_CA_BUNDLE or the JVM truststore).
<!-- /harness-remediation -->

<!-- harness-remediation:sec-owner-scoped-access -->
### sec-owner-scoped-access — Scope per-user resources to the authenticated owner

Resolve the caller from the Spring Security principal, never from client input, and look resources up by id plus owner id. Keep an explicit ownership check after the load, and bind update endpoints to request DTOs that carry no identifiers.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-safe-file-paths -->
### sec-safe-file-paths — Keep request strings out of filesystem paths

Select report files from a directory listing or a server-generated integer instead of joining request strings into paths, and confine the result to the reports directory.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-content -->
### sec-mail-content — Escape user values in e-mail

Validate recipients against a strict plain-address pattern, strip control characters from subjects, and HTML-escape every user-supplied value in MailBody builders before it reaches sendMail.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-origin-host-allowlists -->
### sec-origin-host-allowlists — Use explicit origin and host allowlists

CORS origins and Django ALLOWED_HOSTS come from environment-driven allowlists (CORS_ALLOWED_ORIGINS, ALLOWED_HOSTS) with credentials disabled, never wildcards. Add new origins and hostnames through those variables.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secure-random -->
### sec-secure-random — Use SecureRandom for generated data

Use java.security.SecureRandom, without a fixed seed, for any generated value that should not be predictable.
<!-- /harness-remediation -->

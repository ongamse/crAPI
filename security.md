# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Never disable TLS verification

Outbound HTTP clients (workshop requests, community Go transport, identity RestTemplate) verify certificates and hostnames by default. For private CAs set TLS_CA_BUNDLE (workshop, community) or the JVM trust store instead of skipping verification, and never mutate http.DefaultTransport.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWT signatures before trusting claims

Workshop and community read tokens only after RS256 verification against the identity service JWKS, using keys from the fetched set and never from token headers. Reuse decode_verified_token (workshop) and parseVerifiedToken (community) for any new token reading.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Raw cursor.execute calls in crapi views use %s placeholders with a params list. Never concatenate request values into SQL.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-shell -->
### sec-no-shell — Do not build shell strings from request data

Video conversion runs through ProcessBuilder with an argument list, an executable allowlist and per-argument character checks. Never pass request values to bash -c.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secrets-not-in-git -->
### sec-secrets-not-in-git — Keep keys and tokens out of git

Private keys are gitignored and generated locally with services/gencert.sh, and Postman requests reference {{token}} variables instead of literal JWTs. Anything already committed stays in history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-container-nonroot -->
### sec-container-nonroot — Run containers without root or privilege escalation

Service Dockerfiles create an app user and set USER, and Kubernetes containers set allowPrivilegeEscalation: false. Use the net.ipv4.ip_unprivileged_port_start sysctl for low ports rather than setcap-dependent binding.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-actions-pinning -->
### sec-actions-pinning — Pin workflow actions and isolate untrusted input

Third-party GitHub Actions are pinned to full commit SHAs with the tag kept as a comment, and github.head_ref is passed through env: instead of being interpolated into run scripts.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-csrf-enabled -->
### sec-csrf-enabled — Keep CSRF protection on for cookie-based flows

Identity uses CookieCsrfTokenRepository and ignores only requests carrying an explicit Authorization header plus the unauthenticated auth paths. Do not add cookie or session authentication to those exempt paths.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-crypto-random -->
### sec-crypto-random — Use SecureRandom for identifiers

VIN and pincode generation use java.security.SecureRandom. Use it for any identifier or token that must not be predictable.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mail-header-injection -->
### sec-mail-header-injection — Strip CR/LF from mail headers

SMTPMailServer replaces CR/LF in the subject before setSubject. Sanitize any value placed in a mail header the same way.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-secret-logging -->
### sec-no-secret-logging — Log presence, not credential content

The chatbot logs only that an API key was received, never any slice of it. Gateway and community handlers log errors server-side instead of echoing them to clients.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-timeouts -->
### sec-outbound-timeouts — Set timeouts and throttles on network edges

Workshop requests calls pass OUTBOUND_REQUEST_TIMEOUT and fail closed on timeout, and the REST framework throttles are tunable through THROTTLE_RATE_ANON and THROTTLE_RATE_USER.
<!-- /harness-remediation -->

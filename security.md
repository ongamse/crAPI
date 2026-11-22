# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-secrets-from-env -->
### sec-secrets-from-env — Credentials come from env or Secrets

Passwords, gateway credentials and TLS keystore passwords are read from required environment variables (compose `${VAR:?}`, Kubernetes/Helm Secrets via secretKeyRef) and code fails closed when they are unset. Never add literal defaults to os.environ.get or commit .env credentials; rotate any value that remains in git history.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-committed-keys -->
### sec-no-committed-keys — Keep private keys and JWKS out of git

TLS private keys (server.key) and static jwks.json are git-ignored and supplied at runtime through a bind mount or Secret mount; JwtProvider generates an ephemeral key when no JWKS is configured. Do not paste captured bearer tokens into Postman collections; reference the `{{token}}` variable.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Raw cursor.execute calls in the workshop service use DB-API placeholders with a params list, never string concatenation of request values.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify-once -->
### sec-jwt-verify-once — Verify JWTs in a single entry point

JwtProvider.verifyAndGetClaims parses and RSA-verifies a token in one step (RS256/384/512, server key only) and throws otherwise. Do not add parse-only helpers that return claims from an unverified token.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-owner-checks -->
### sec-owner-checks — Resolve owned resources through the authenticated user

Video and vehicle lookups go through owner-filtered helpers (findOwnedVideo, findByUuidAndOwner_id) and controllers resolve the principal from the token, ignoring client-supplied ids in the body. Only explicit admin paths use unfiltered lookups, after a role check.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-requests -->
### sec-outbound-requests — Do not forward request data into outbound calls

Mechanic API destinations are looked up from settings.MECHANIC_API_BASE_URLS, only a regex-validated bearer token is forwarded via an auth handler, and only allowlisted fields are sent; redirects are disabled. Add new destinations to settings, not to the request.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Never disable TLS verification

Outbound HTTP uses settings.REQUESTS_VERIFY (system trust or a TLS_CA_BUNDLE path) and the identity RestTemplate uses the JVM trust store or an optional truststore with default hostname verification. Supply a CA bundle for private CAs instead of trusting all certificates.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-file-access -->
### sec-file-access — Access report files through rooted storage

Report reads and writes use a FileSystemStorage rooted at the reports directory, and names are checked with a pure allowlist validator that builds no paths. Use that storage for any new file access instead of joining request-supplied names onto paths.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-output-encoding -->
### sec-output-encoding — Escape values in mail and headers

MailBody HTML-escapes every interpolated value, and SMTPMailServer strips control characters from subjects and rebuilds recipients from validated addresses. Escape any new value added to mail templates.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-cors-hosts -->
### sec-cors-hosts — Restrict CORS origins and allowed hosts

CORS origins come from one configured allowlist (CORS_ALLOWED_ORIGINS) and Django ALLOWED_HOSTS is an explicit env list without wildcards. Add new web origins or hostnames to those settings rather than using @CrossOrigin or wildcards.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secure-random -->
### sec-secure-random — Use SecureRandom for unguessable values

Tokens and seed data that must not be predictable use java.security.SecureRandom with no constant seed; never call setSeed with a constant.
<!-- /harness-remediation -->

# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Pass request-derived values to cursor.execute as bound %s parameters instead of concatenating them into the SQL string.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-path-confine -->
### sec-path-confine — Confine file paths to their base directory

Resolve request-derived paths with os.path.realpath and check os.path.commonpath against the reports directory; coerce numeric ids to int before building paths.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWT signatures before reading claims

Call validateJwtToken before reading any claim, and reject unsigned (alg=none / PlainJWT) tokens. Never trust the subject from an unverified token.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-ssrf-allowlist -->
### sec-ssrf-allowlist — Allowlist outbound request targets

Outbound fetches built from request data go through is_allowed_mechanic_api(): http(s) only, no userinfo, fixed path, host in MECHANIC_API_ALLOWED_HOSTS, and redirects disabled. Add new deployment hostnames to the setting, not a wildcard.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-header-ctl -->
### sec-header-ctl — Reject control characters in headers and mail fields

Values from the request that go into an outbound HTTP header or an e-mail recipient/subject are rejected if they contain CR, LF or other control characters; recipients are parsed strictly and validated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Keep TLS certificate validation on

Never pass verify=False or a trust-all strategy. Python uses settings.OUTBOUND_TLS_VERIFY (system CAs or an OUTBOUND_CA_BUNDLE path); Java uses the default trust store, with private CAs via the javax.net.ssl.trustStore properties.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secrets-env -->
### sec-secrets-env — Load credentials from env or Secrets

Passwords, signing keys and seed credentials are read from environment variables or Kubernetes Secrets and fail closed when unset. Do not put them in ConfigMaps, compose defaults, .env files or Helm values; the old values are in git history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-committed-keys -->
### sec-no-committed-keys — Never commit private keys or JWKS private members

TLS server.key files and jwks.json files with private members (d, p, q, dp, dq, qi) are supplied at runtime (mounted path, TLS_KEY_FILE, JWKS env) and are git-ignored. Do not paste real key values into docs or the chatbot corpus.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-object-ownership -->
### sec-object-ownership — Check object ownership on id lookups

Services that look up an object by a path or query id take the HttpServletRequest, resolve the caller with getUserFromToken and verify the owner id before use. Admin access is allowed only on the admin delete path.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-prng -->
### sec-prng — Do not seed random generators with constants

Use an unseeded java.security.SecureRandom; never call setSeed with a constant, and do not write tests that depend on deterministic seed output.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-allowed-hosts -->
### sec-allowed-hosts — Use an explicit ALLOWED_HOSTS list

The workshop service uses an explicit host list extended by the ALLOWED_HOSTS env var. Add a new hostname there instead of a wildcard.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-captured-tokens -->
### sec-no-captured-tokens — Do not commit captured Authorization headers

The Postman collection references the {{token}} variable set by the Login request instead of pasted Bearer tokens.
<!-- /harness-remediation -->

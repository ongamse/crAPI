# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Raw cursor queries in the workshop service pass request values as bound %s parameters, never through string concatenation.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-path-containment -->
### sec-path-containment — Contain user-influenced file paths

Resolve the real path and confirm it stays inside the base directory before opening it, and coerce numeric ids to integers before building file names.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWTs with a pinned algorithm and server key

Tokens are verified with RS256 against the server's own public key only. Never pick the algorithm or key (alg, jku, kid) from token headers, and never accept unsigned tokens.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-outbound-requests -->
### sec-outbound-requests — Validate outbound request targets and headers

User-supplied URLs must pass an allowlist check (http/https, no link-local, metadata or private addresses unless explicitly configured) with redirects disabled. Reject CR/LF and control characters in values placed in outbound headers or mail headers.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Keep TLS verification on

Outbound HTTPS calls use certificate verification. Private CAs are configured through TLS_CA_BUNDLE (Python) or the JVM trust store, never verify=False or trust-all strategies.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secrets-runtime -->
### sec-secrets-runtime — Supply secrets and keys at runtime

Passwords, signing keys, JWKS and TLS private keys are not committed. Compose, helm and k8s read them from required env vars, the crapi-credentials Secret or mounted files, and services fail closed when they are missing. Rotate anything that was ever committed.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-object-ownership -->
### sec-object-ownership — Check object ownership

Before using a client-supplied id for a video or vehicle, compare the entity's owner to the user resolved from the token and fail closed.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-origin-host-allowlist -->
### sec-origin-host-allowlist — Use explicit CORS origins and allowed hosts

CORS origins and Django ALLOWED_HOSTS come from CORS_ALLOWED_ORIGINS and ALLOWED_HOSTS env vars with wildcards ignored. Add deployment-specific values there instead of using '*' or per-controller annotations.
<!-- /harness-remediation -->

# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-sql-bind -->
### sec-sql-bind — Bind SQL parameters

Request values reach SQL only through %s placeholders and a params list in cursor.execute, never string concatenation. Tests should assert injected payloads are treated as plain data.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tls-verify -->
### sec-tls-verify — Verify TLS for outbound calls

Outbound HTTP uses verified TLS (workshop settings.HTTP_CLIENT_VERIFY and HTTP_CLIENT_TIMEOUT, community utils.NewHTTPClient, identity default hostname verifier with TLS 1.2+). Trust private CAs through TLS_CA_BUNDLE and never set verify=False, InsecureSkipVerify or NoopHostnameVerifier.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-jwt-verify -->
### sec-jwt-verify — Verify JWT signatures

Workshop and community services verify RS256 tokens against the identity service JWKS using a kid lookup and an algorithm allowlist. Never decode or trust claims from an unverified token.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-committed-secrets -->
### sec-no-committed-secrets — Keep secrets out of the repo

TLS private keys and bearer tokens are supplied at runtime (gencert.sh, TLS_KEY_FILE mounts, Postman {{token}} variables) and are gitignored. Anything once committed stays in history and must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-secret-logging -->
### sec-no-secret-logging — Never log key material

Log only that a credential is configured, never any of its bytes, including prefixes.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-container-nonroot -->
### sec-container-nonroot — Run containers as non-root

Service Dockerfiles create uid 10001 and end with USER app, with setcap for low ports, and k8s containers set allowPrivilegeEscalation: false. Web and gateway pods use the ip_unprivileged_port_start sysctl so port binding does not rely on file capabilities.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-csrf-on -->
### sec-csrf-on — Keep CSRF protection enabled

Identity keeps Spring CSRF on with a cookie token repository and exempts only unauthenticated auth endpoints and Authorization-header requests. Do not call csrf().disable() or add csrf_exempt to DRF views.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-no-shell-strings -->
### sec-no-shell-strings — Pass argv, not shell strings

Stored user input is never joined into a bash -c string. Run an allowlisted executable through ProcessBuilder with discrete arguments.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-header-injection -->
### sec-header-injection — Neutralize CR/LF in headers

Values placed into mail headers have CR/LF replaced before MimeMessage.setSubject. Sanitize any user value before it enters a protocol header.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-secure-random -->
### sec-secure-random — Use SecureRandom for identifiers

Security-relevant identifiers such as VINs and pincodes come from java.security.SecureRandom, not java.util.Random.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-gha-hardening -->
### sec-gha-hardening — Harden GitHub workflows

Pass github context values to run steps through env variables rather than inline expressions, and pin every action to a full commit SHA with the tag as a comment.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-json-responses -->
### sec-json-responses — Send JSON with nosniff

Go handlers set application/json and X-Content-Type-Options: nosniff and encode with json.NewEncoder instead of writing raw strings.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-api-throttle -->
### sec-api-throttle — Throttle API requests

Workshop REST_FRAMEWORK has default anon and user throttles, tunable via THROTTLE_RATE_ANON and THROTTLE_RATE_USER.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-bind-loopback -->
### sec-bind-loopback — Bind services to loopback by default

The chatbot MCP server reads MCP_SERVER_HOST and defaults to 127.0.0.1. Set the variable explicitly only when another container must reach it.
<!-- /harness-remediation -->

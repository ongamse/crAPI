# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:PE6tioYvlHHDl3ttC3tICg -->
### PE6tioYvlHHDl3ttC3tICg — Bind SQL values as parameters

Never concatenate request values (user id, coupon code) into a raw cursor.execute string. Pass values as the second argument with %s placeholders, and keep tests asserting the safe behaviour, not the old injection.
<!-- /harness-remediation -->

<!-- harness-remediation:Pume8qY5D8LUn5sm8O-Owx -->
### Pume8qY5D8LUn5sm8O-Owx — Derive report file paths from directory entries

Validate report names with a strict allowlist regex, then take the path from an os.listdir() entry of the canonical reports directory (find_existing_report) instead of the request string. Rebuild generated file names from int(report_id) and check commonpath containment before open(). A realpath check alone was not recognised as a sanitizer. Do not echo server paths in error messages.
<!-- /harness-remediation -->

<!-- harness-remediation:2xwtkvh_bK7Xa6UOOz5iGa -->
### 2xwtkvh_bK7Xa6UOOz5iGa — Read JWT claims only after verification

JwtProvider reads claims only through verifiedClaims(), a Nimbus DefaultJWTProcessor with SingleKeyJWSKeySelector(RS256, server public key), so claims exist only after signature and exp/nbf checks. Add new getters on top of verifiedClaims(); never parse a token directly or trust token-supplied alg, kid or jku.
<!-- /harness-remediation -->

<!-- harness-remediation:tN6VEEO_NcqeTH-xWZ-76H -->
### tN6VEEO_NcqeTH-xWZ-76H — Build outbound URLs from allowlist constants

A validate-then-reuse allowlist did not break the taint flow into requests.get. build_mechanic_api_url matches scheme, host and path against server-side allowlists (MECHANIC_API_ALLOWED_HOSTS, ALLOWED_MECHANIC_API_PATHS) and rebuilds the URL from those entries plus an int port. Build outbound URLs from constants, not from the request string.
<!-- /harness-remediation -->

<!-- harness-remediation:542wVQcVYFb0XRlk0pAkFD -->
### 542wVQcVYFb0XRlk0pAkFD — Pass DB credentials as parameters, not URIs

Do not build user:pass@host URIs by string. Connection constants in chatbot dbconnections.py are credential-free; Mongo credentials go to the client as username/password kwargs (MONGO_CLIENT_KWARGS) and Postgres uses sqlalchemy.engine.URL.create. Credentials come from required environment variables with no hard-coded fallback.
<!-- /harness-remediation -->

<!-- harness-remediation:D5EBmhI64agCI2tMQASpjL -->
### D5EBmhI64agCI2tMQASpjL — Keep service credentials out of files and manifests

Service passwords come from compose variable interpolation, the crapi-credentials Kubernetes Secret, or the Helm-generated Secret, consumed via secretKeyRef; env files, ConfigMaps and values.yaml carry no literals. Test and seed passwords are generated at runtime. The old defaults remain in git history and must be rotated. Vendored mongo-driver connstring.go:635 is a keyword false positive that needs a scanner path exclusion, not a code change.
<!-- /harness-remediation -->

<!-- harness-remediation:F7GWMB4T9y5-Gkpv4l-PR1 -->
### F7GWMB4T9y5-Gkpv4l-PR1 — Use SecureRandom for generated data

InitialDataConfig used java.util.Random with a constant seed, making seed data predictable. It now uses java.security.SecureRandom without a seed. Do not call setSeed with constants.
<!-- /harness-remediation -->

<!-- harness-remediation:KIhmKilariFK8-L23jMC98 -->
### KIhmKilariFK8-L23jMC98 — Resolve owned records from the authenticated user

Owner-scoped queries that still took the request id as an argument were flagged. ProfileServiceImpl loads the user's own video with findByUser_id and compares ids in memory; the admin path matches the request id against the server-side findAllIds() list. VehicleServiceImpl filters findAllByOwner_id in memory. The controller validates VideoForm with bean validation and passes only the video name and conversion params to the service.
<!-- /harness-remediation -->

<!-- harness-remediation:Okl8GtVopGlT2RgaaIAwyK -->
### Okl8GtVopGlT2RgaaIAwyK — Rebuild forwarded headers from allowlisted characters

A boolean control-character check is not treated as a sanitizer for outbound headers. build_authorization_header full-matches a Bearer token and rebuilds a fresh string character by character from an allowlist, returning 400 otherwise. Apply the same to any other request-derived outbound header.
<!-- /harness-remediation -->

<!-- harness-remediation:XTRkC2YjTB_rMPkT7fQf4b -->
### XTRkC2YjTB_rMPkT7fQf4b — Never disable TLS verification

Workshop requests use settings.OUTBOUND_TLS_VERIFY (system trust store or the OUTBOUND_CA_BUNDLE path) and identity uses HttpClients.custom().useSystemProperties(). Supply a private CA for self-signed dev certificates instead of turning verification off.
<!-- /harness-remediation -->

<!-- harness-remediation:T4P16zW9HtneA_QNDcxTqI -->
### T4P16zW9HtneA_QNDcxTqI — Avoid InternetAddress.parse

InternetAddress.parse is flagged as an obsolete function. parseRecipients splits on commas itself, requires each element to full-match EMAIL_PATTERN (no display names, quotes, comments, angle brackets or groups) and builds new InternetAddress(addr, true). Do not reintroduce InternetAddress.parse.
<!-- /harness-remediation -->

<!-- harness-remediation:oEf-scmVYnZuYjaLHQEf_V -->
### oEf-scmVYnZuYjaLHQEf_V — Never commit private keys

server.key was committed in six service folders. It is removed and **/server.key is git-ignored. Services read the key via TLS_KEY and the certificate via TLS_CERTIFICATE. Supply keys at deploy time with docker-compose.tls.yml (TLS_DIR) or the Helm tlsSecretName Secret, and rotate the old key.
<!-- /harness-remediation -->

<!-- harness-remediation:rsB-gCiajYBkdDyVJDU_fd -->
### rsB-gCiajYBkdDyVJDU_fd — One central CORS allowlist

Identity uses com.crapi.config.CorsConfig driven by CORS_ALLOWED_ORIGINS, and workshop uses CORS_ALLOWED_ORIGINS in settings.py. Wildcards are ignored and credentials are never allowed. Add origins through that variable, not with new @CrossOrigin annotations or CORS_ORIGIN_ALLOW_ALL.
<!-- /harness-remediation -->

<!-- harness-remediation:vm04F3uzOcT1cPLF2p2A6r -->
### vm04F3uzOcT1cPLF2p2A6r — No wildcard ALLOWED_HOSTS

Workshop settings.py reads a comma-separated ALLOWED_HOSTS environment variable, drops wildcards, and defaults to localhost, 0.0.0.0 and the crapi-workshop and crapi-web service names. Add public hostnames through ALLOWED_HOSTS, never '*'.
<!-- /harness-remediation -->

<!-- harness-remediation:yHTwW1wjFHOVBfqEhY2Ali -->
### yHTwW1wjFHOVBfqEhY2Ali — No literal tokens in Postman exports

The Postman collection now uses its existing {{token}} and {{mechanicToken}} variables set by the login scripts. After fixing, grep the whole collection for the JWT header prefix because the same token can appear in several requests. Rotate any token that was committed.
<!-- /harness-remediation -->

<!-- harness-remediation:z6uUznqYpadcyMq9etJjAM -->
### z6uUznqYpadcyMq9etJjAM — Never commit signing keys

A private JWK was committed as jwks.json. JwtProvider now generates an ephemeral RSAKeyGenerator key when JWKS is empty and still honours a supplied keys/jwks.json or JWKS variable. Supply keys through the git-ignored keys folder, a Kubernetes Secret or the JWKS variable, and rotate the old key. Remaining high-entropy hits were non-secrets (an encoded message constant and alphabet constants).
<!-- /harness-remediation -->

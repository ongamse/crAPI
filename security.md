# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:2xwtkvh_bK7Xa6UOOz5iGa -->
### 2xwtkvh_bK7Xa6UOOz5iGa — Verify the JWT signature before reading claims

JwtProvider parsed the token (SignedJWT.parse) and read header/claims objects before verification; it now splits the compact token, verifies the RS256 signature over the raw signing input with RSASSAVerifier using a locally built RS256 header (token header alg/kid/jku/jwk never consulted), and only then builds JWTClaimsSet from the verified payload via the private verifiedClaims helper. Route any new claim reads through verifiedClaims.
<!-- /harness-remediation -->

<!-- harness-remediation:542wVQcVYFb0XRlk0pAkFD -->
### 542wVQcVYFb0XRlk0pAkFD — No credentials embedded in connection URLs

chatbot dbconnections.py built user:pass@host URIs for Mongo and Postgres; now Mongo uses discrete AsyncIOMotorClient kwargs (MONGO_CLIENT_KWARGS) and Postgres uses sqlalchemy URL.create passed to create_engine/SQLDatabase, so no credential-in-URL strings exist. Keep tests free of literal user:pass@ URLs; vendored Go doc/example strings are not hand-edited.
<!-- /harness-remediation -->

<!-- harness-remediation:D5EBmhI64agCI2tMQASpjL -->
### D5EBmhI64agCI2tMQASpjL — Keep deployment and seed credentials in Secrets or env

Earlier passes moved DB/gateway/Django secrets to env and Secrets (crapi-credentials, compose ${VAR:?}, helm credentials-secret.yaml). This pass also moved the seeded admin password to an optional ADMIN_PASSWORD (TestUsers.java reads it, chatbot gets it as API_PASSWORD from the same Secret/compose var; unset keeps the documented demo default), made the local identity .env TLS passwords env references, and generated unit-test password fixtures at runtime (secrets.token_urlsafe / UUID). Add new credentials to those same Secret/env paths, never to ConfigMaps or values files; the committed server.p12 password and the documented demo mechanic logins cannot be externalized without regenerating keystores or breaking the documented demo logins.
<!-- /harness-remediation -->

<!-- harness-remediation:F7GWMB4T9y5-Gkpv4l-PR1 -->
### F7GWMB4T9y5-Gkpv4l-PR1 — Use SecureRandom, never a fixed-seed PRNG

InitialDataConfig seeded java.util.Random with a fixed value (0), making seed-time random choices (vehicle model, profile video bytes) predictable. Replaced with an unseeded java.security.SecureRandom (a Random subclass, so call sites are unchanged); do not call setSeed on PRNGs and no tests depend on the deterministic output.
<!-- /harness-remediation -->

<!-- harness-remediation:KIhmKilariFK8-L23jMC98 -->
### KIhmKilariFK8-L23jMC98 — Scope id lookups by the authenticated owner

Request-supplied ids (video_id, carId) were loaded with unscoped findById/findByUuid and the owner compared afterwards; the id is now only used in ownership-scoped repository queries (ProfileVideoRepository.findByIdAndUser_id / findByIdForRequester, VehicleDetailsRepository.findByUuidAndOwner_id). ProfileController and VehicleController now resolve the caller via userService.getUserFromToken(request) and pass the User explicitly into ProfileService/VehicleService, so the owner is a required parameter of every id-addressed service method; follow the same pattern for any new id-addressed resource.
<!-- /harness-remediation -->

<!-- harness-remediation:Okl8GtVopGlT2RgaaIAwyK -->
### Okl8GtVopGlT2RgaaIAwyK — Do not forward inbound headers to user-supplied URLs

ContactMechanicView forwarded the caller's inbound Authorization header (request-derived, CWE-113) to a user-supplied mechanic_api URL, leaking the bearer token to any allowlisted receiver; the in-repo ReceiveReportView does not use it. The outbound requests.get now sends no Authorization header (caller auth stays on jwt_auth_required, SSRF check via validate_mechanic_api_url and allow_redirects=False kept); never copy inbound request headers into outbound requests to user-controlled URLs.
<!-- /harness-remediation -->

<!-- harness-remediation:PE6tioYvlHHDl3ttC3tICg -->
### PE6tioYvlHHDl3ttC3tICg — Bind SQL parameters instead of concatenating

ApplyCouponView.post concatenated user.id and the request coupon_code into a raw cursor.execute string; it now uses Django DB-API bound parameters (%s placeholders with a params list). Always pass user input as the params argument to cursor.execute; the test_sql_injection test now asserts the payload is treated as a plain, unknown coupon.
<!-- /harness-remediation -->

<!-- harness-remediation:Pume8qY5D8LUn5sm8O-Owx -->
### Pume8qY5D8LUn5sm8O-Owx — Never join request data into file paths

Report paths in crapi/mechanic/views.py were built from request values (filename query param, report_id) and then confined with realpath. Now DownloadReportView uses find_report_file(), which returns entry.path from os.scandir of the reports dir only when entry.name equals the request value (so the request never becomes a path component), and service_report_pdf builds the name from the DB ServiceRequest.id via a %d format. Do the same for any new file-serving endpoint: look up by listing or by DB key, never join request data into a path.
<!-- /harness-remediation -->

<!-- harness-remediation:XTRkC2YjTB_rMPkT7fQf4b -->
### XTRkC2YjTB_rMPkT7fQf4b — Keep TLS certificate validation on

Outbound HTTPS calls disabled certificate validation (requests verify=False in workshop jwt/merchant/shop/seed_database; Java TrustAllStrategy + NoopHostnameVerifier in VehicleOwnershipServiceImpl). Now the defaults are used (requests verifies against certifi/REQUESTS_CA_BUNDLE; the httpclient5 client uses the default verifier and honours javax.net.ssl.trustStore*), so internal/self-signed CAs must be supplied via REQUESTS_CA_BUNDLE (workshop) or JVM truststore properties (identity) instead of turning verification off.
<!-- /harness-remediation -->

<!-- harness-remediation:oEf-scmVYnZuYjaLHQEf_V -->
### oEf-scmVYnZuYjaLHQEf_V — Do not commit private keys

A shared TLS private key (server.key) was committed in six service directories. Removed the keys and added a .gitignore rule; keys must now be produced locally by services/gencert.sh (which regenerates the cert pair and copies it to each service) or supplied at runtime via TLS_KEY/TLS_CERTIFICATE. The committed key is compromised via git history and must be rotated; never commit server.key again.
<!-- /harness-remediation -->

<!-- harness-remediation:rsB-gCiajYBkdDyVJDU_fd -->
### rsB-gCiajYBkdDyVJDU_fd — Restrict CORS to configured origins

Identity controllers used bare @CrossOrigin (all origins) and workshop used CORS_ORIGIN_ALLOW_ALL; replaced with one CorsConfigurationSource bean in WebSecurityConfig driven by cors.allowed-origins (CORS_ALLOWED_ORIGINS env) and CORS_ALLOWED_ORIGINS in workshop settings.py; add new web origins to that env var (docker-compose, helm corsAllowedOrigins, k8s configmaps) rather than re-adding @CrossOrigin.
<!-- /harness-remediation -->

<!-- harness-remediation:tN6VEEO_NcqeTH-xWZ-76H -->
### tN6VEEO_NcqeTH-xWZ-76H — Allowlist user-supplied outbound URLs

ContactMechanicView.post passed the client-supplied mechanic_api straight to requests.get (SSRF). It now goes through utils.helper.validate_mechanic_api_url, which requires http(s), no userinfo, a path ending in /api/mechanic/receive_report (the only URL the web client sends), and rejects hosts resolving to link-local/unspecified/multicast/reserved addresses; redirects are disabled. Route any new user-supplied outbound URL through a similar allowlist helper.
<!-- /harness-remediation -->

<!-- harness-remediation:vm04F3uzOcT1cPLF2p2A6r -->
### vm04F3uzOcT1cPLF2p2A6r — Restrict Django ALLOWED_HOSTS

workshop settings.py used ALLOWED_HOSTS=['*'] (DNS rebinding / Host header risk); it now reads a comma-separated ALLOWED_HOSTS env var (wildcard entries dropped) defaulting to localhost, 127.0.0.1, [::1], 0.0.0.0 and the crapi-workshop/workshop service names, mirroring the CORS_ALLOWED_ORIGINS pattern. Compose, k8s base and helm configmaps pass it through; when adding a new hostname or ingress for workshop, set ALLOWED_HOSTS (helm: allowedHosts) rather than widening to '*'.
<!-- /harness-remediation -->

<!-- harness-remediation:yHTwW1wjFHOVBfqEhY2Ali -->
### yHTwW1wjFHOVBfqEhY2Ali — No captured JWTs in the Postman collection

The crAPI Postman collection had hard-coded Bearer JWTs in Authorization headers on requests that already use {{token}} in their auth block; these now use Bearer {{token}}. Never paste captured JWTs into Postman headers; use the {{token}} collection/environment variable and rotate any token committed to git.
<!-- /harness-remediation -->

<!-- harness-remediation:z6uUznqYpadcyMq9etJjAM -->
### z6uUznqYpadcyMq9etJjAM — Generate tokens and VINs with SecureRandom

EmailTokenGenerator used Math.random() for email-change tokens (CWE-338) and an alphabet missing y; it now uses a static SecureRandom and a full a-z/A-Z alphabet. GenerateVIN defaulted to java.util.Random for VIN and ownership pincode; the unseeded constructor now uses SecureRandom (seeded constructor kept for deterministic tests). UserMessage CONVERSION_VIDEO_OK is a base64 lab message returned as-is to clients, not a secret, so it was left unchanged.
<!-- /harness-remediation -->

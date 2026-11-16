# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sto-2xwtkvh_bK7Xa6UOOz5iGa -->
### sto-2xwtkvh_bK7Xa6UOOz5iGa — Verify JWT signatures with the server's own RSA key only

JwtProvider.getUserNameFromJwtToken used JWTParser.parse and trusted unsigned claims, and validateJwtToken accepted HS256 with the public key as secret, attacker jku/kid keys and PlainJWT. Both now go through one private verifySignature() that only accepts RSA-family algorithms verified with the server's own public RSAKey via RSASSAVerifier; do not select keys or algorithms from token headers.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-542wVQcVYFb0XRlk0pAkFD -->
### sto-542wVQcVYFb0XRlk0pAkFD — Inject DB credentials via secretKeyRef, never ConfigMaps

chatbot dbconnections.py had a hard-coded default DB password; it now fails closed via _require_env, and the k8s chatbot deployment injects DB_PASSWORD/MONGO_DB_PASSWORD through secretKeyRef to the Secret crapi-chatbot-db-credentials (not defined in git). Never put password values in ConfigMaps; create the Secret out-of-band and rotate the old password since it remains in git history.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-D5EBmhI64agCI2tMQASpjL -->
### sto-D5EBmhI64agCI2tMQASpjL — Read shared secrets from env or Secrets, never commit them

Shared DB/Mongo passwords, the vendor gateway password (Pa$$4Vendor_1 in workshop settings, identity application.properties and gateway-service main.go), Django SECRET_KEY and the unused JWT_SECRET were committed in compose, k8s ConfigMaps, helm values and .env files. They are now read from env (compose ${VAR:?}, get_env_value, Spring ${API_GATEWAY_PASSWORD}, os.Getenv with constant-time compare in Go), k8s secretKeyRef (Secrets created by deploy.sh) and helm required/Secret templates; CI generates ephemeral values. Rotate all removed values since they remain in git history, and never add new
<!-- /harness-remediation -->

<!-- harness-remediation:sto-F7GWMB4T9y5-Gkpv4l-PR1 -->
### sto-F7GWMB4T9y5-Gkpv4l-PR1 — Use SecureRandom, never a constant-seeded Random

InitialDataConfig.setup seeded java.util.Random with a constant (0), making seeded vehicle-model picks and profile video bytes predictable; it now uses an unseeded java.security.SecureRandom. Do not call setSeed with a constant on any Random in this repo; use SecureRandom, and note GenerateVIN also has a seeded Random path that is a separate finding.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-KIhmKilariFK8-L23jMC98 -->
### sto-KIhmKilariFK8-L23jMC98 — Check object ownership against the token user

Identity service looked up vehicles/videos by client-supplied id (findByUuid/findById) with no check against the token user. Added ownership checks using userService.getUserFromToken(request) compared to entity owner id (ProfileServiceImpl.isVideoOwnedBy, vehicle owner id compare), failing closed; updateProfileVideo now takes the path videoId and requires it to match the caller's own video. The Django ApplyCouponView sink already uses the JWT-derived user with bound params, so no change was needed there.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-Okl8GtVopGlT2RgaaIAwyK -->
### sto-Okl8GtVopGlT2RgaaIAwyK — Validate request-derived values before outgoing headers

ContactMechanicView.post forwarded the raw incoming HTTP_AUTHORIZATION value as an outgoing request header; it now passes through is_safe_header_value (rejects CR/LF and other control characters) and returns 400 with messages.INVALID_AUTHORIZATION_HEADER before any outbound request. Validate any request-derived value with that helper before placing it in an outgoing header.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-PE6tioYvlHHDl3ttC3tICg -->
### sto-PE6tioYvlHHDl3ttC3tICg — Bind SQL values via cursor.execute params

ApplyCouponView.post built a raw applied_coupon SELECT by concatenating user.id and the request coupon_code; it now uses cursor.execute with %s placeholders and a params list. Always bind values via the params argument of cursor.execute; test_sql_injection now asserts the payload is treated as a literal (COUPON_NOT_FOUND).
<!-- /harness-remediation -->

<!-- harness-remediation:sto-Pume8qY5D8LUn5sm8O-Owx -->
### sto-Pume8qY5D8LUn5sm8O-Owx — Contain file paths with realpath and commonpath

DownloadReportView joined a user-supplied (URL-decoded) filename onto the reports dir with only abspath, so %2e%2e%2f could escape it. Now the reports dir and target are resolved with os.path.realpath and os.path.commonpath enforces containment (400 otherwise); service_report_pdf applies the same containment check on its write path. Use this realpath+commonpath pattern for any file path built from request data.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-XTRkC2YjTB_rMPkT7fQf4b -->
### sto-XTRkC2YjTB_rMPkT7fQf4b — Never disable TLS verification; configure a truststore

Outbound HTTPS calls disabled certificate verification (Java TrustAllStrategy/NoopHostnameVerifier in VehicleOwnershipServiceImpl; requests verify=False in workshop). Java now uses a verified httpclient5 SSLContext with optional truststore from API_GATEWAY_TRUSTSTORE; workshop uses settings.TLS_CA_BUNDLE (env TLS_CA_BUNDLE, default True) as the verify argument. Use these knobs for private CAs instead of disabling verification.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-_BU3dZ96NK4EHj-FQr1F_0 -->
### sto-_BU3dZ96NK4EHj-FQr1F_0 — Strip control characters from mail headers and body

SMTPMailServer.sendMail passed user-influenced recipient, subject and body straight into the MimeMessage, allowing CR/LF header injection. It now rejects control characters in the recipient list, strips all control characters from the subject, and strips non-whitespace control characters from the body (private helpers in SMTPMailServer); route any new mail sending through this method.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-oEf-scmVYnZuYjaLHQEf_V -->
### sto-oEf-scmVYnZuYjaLHQEf_V — Supply TLS private keys at runtime, never commit them

Per-service TLS private keys (server.key) and the key-bearing identity server.p12/server.keystore were committed and baked into images; they are now deleted and supplied at runtime (compose TLS_KEY_DIR volume at /app/tls, helm optional Secret tlsSecretName mounted at /app/tls, TLS_KEY env read by runner.sh/entrypoint.sh/nginx-wrapper.sh/community routes.go). Generate keys locally with services/gencert.sh into git-ignored ./tls and never commit them; the exposed keys must be rotated.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-rsB-gCiajYBkdDyVJDU_fd -->
### sto-rsB-gCiajYBkdDyVJDU_fd — Use a central CORS allowlist, not allow-all

Identity controllers used bare @CrossOrigin (allow all origins) and workshop used CORS_ORIGIN_ALLOW_ALL; replaced with one central CorsConfig bean (identity, wildcard rejected at startup) and CORS_ALLOWED_ORIGINS allowlist in workshop settings, both driven by the CORS_ALLOWED_ORIGINS env var wired in compose/helm/k8s. Add new origins via that env var, never per-controller annotations.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-tN6VEEO_NcqeTH-xWZ-76H -->
### sto-tN6VEEO_NcqeTH-xWZ-76H — Allowlist outbound callback hosts to prevent SSRF

ContactMechanicView.post passed the user-supplied mechanic_api straight to requests.get (SSRF). It now validates the URL with is_allowed_mechanic_api (http/https only, no credentials, hostname must be in settings.MECHANIC_API_ALLOWED_HOSTS, default localhost/127.0.0.1 plus the MECHANIC_API_ALLOWED_HOSTS env var) and disables redirects; add any new callback hostname to that env var rather than loosening the check.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-vm04F3uzOcT1cPLF2p2A6r -->
### sto-vm04F3uzOcT1cPLF2p2A6r — Set ALLOWED_HOSTS from env, never a wildcard

Workshop settings.py used ALLOWED_HOSTS=["*"], allowing DNS rebinding via arbitrary Host headers. It now reads a comma separated ALLOWED_HOSTS env var (wildcard entries dropped) defaulting to localhost, 127.0.0.1, [::1] and crapi-workshop; compose, k8s and helm set it, so add any new public hostname via WORKSHOP_ALLOWED_HOSTS / workshop.config.allowedHosts.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-yHTwW1wjFHOVBfqEhY2Ali -->
### sto-yHTwW1wjFHOVBfqEhY2Ali — Use Postman variables, never captured tokens

The crAPI Postman collection had hardcoded JWT Bearer tokens in Authorization headers; replaced with the collection's existing {{token}} variable (already used by the bearer auth blocks). Never paste captured tokens into Postman headers; use variables and rotate any token committed to git.
<!-- /harness-remediation -->

<!-- harness-remediation:sto-z6uUznqYpadcyMq9etJjAM -->
### sto-z6uUznqYpadcyMq9etJjAM — Never commit private JWK members; supply JWKS at runtime

jwks.json files (identity service, deploy/docker|helm|k8s keys, chatbot keys.md) embedded the private RSA JWK members (d,p,q,dp,dq,qi) used by JwtProvider to sign JWTs. They are now removed from git; identity/entrypoint.sh requires a runtime-supplied JWKS (mounted /app/keys/jwks.json or JWKS env) and fails closed, compose/helm/k8s mount a user-created secret or JWKS_KEY_DIR. Never commit private JWK members; the previous key stays in git history and must be rotated.
<!-- /harness-remediation -->

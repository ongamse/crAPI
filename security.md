# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:2xwtkvh_bK7Xa6UOOz5iGa -->
### 2xwtkvh_bK7Xa6UOOz5iGa — Verify JWT signature, exp and nbf before reading claims

JwtProvider verified the RS256 signature but never checked exp/nbf, so a validly signed expired token was accepted on both validateJwtToken and getUserNameFromJwtToken. Both paths now go through the private getVerifiedClaims helper, which verifies the signature with the server public key first, then reads claims and enforces exp/nbf (exp-less API keys still pass); keep any new claim reads behind that helper.
<!-- /harness-remediation -->

<!-- harness-remediation:542wVQcVYFb0XRlk0pAkFD -->
### 542wVQcVYFb0XRlk0pAkFD — No default passwords in chatbot connection URIs

services/chatbot/src/chatbot/dbconnections.py had hardcoded DB/Mongo password defaults in its connection URIs; it now reads MONGO_DB_PASSWORD and DB_PASSWORD via os.environ[...] with no default and url-encodes them with quote_plus. Credentials stay in the URI because SQLDatabase.from_uri needs one. Rotate the old password since it remains in git history; vendored Go under services/community/vendor is third-party and must not be edited.
<!-- /harness-remediation -->

<!-- harness-remediation:D5EBmhI64agCI2tMQASpjL -->
### D5EBmhI64agCI2tMQASpjL — Source credentials from env or Secrets, never literals

identity application.properties carried an inline placeholder mailhog password literal; it is now read from the optional MAILHOG_PASSWORD env var with an empty default (mail.mailhog.auth=false so it is unused). Other Secret Keyword hits in this repo are secret names, API paths, action types, messages, vendored Go and test fixtures, not credentials; source real credentials from env.
<!-- /harness-remediation -->

<!-- harness-remediation:F7GWMB4T9y5-Gkpv4l-PR1 -->
### F7GWMB4T9y5-Gkpv4l-PR1 — Use SecureRandom for generated data

InitialDataConfig seeded java.util.Random with a fixed value (0), making seeded vehicle model choice and profile video bytes predictable; replaced with java.security.SecureRandom (still assigned to the Random field) and removed the unused seed constant. Use SecureRandom for any generated data here; no tests or other code depended on the fixed-seed determinism.
<!-- /harness-remediation -->

<!-- harness-remediation:KIhmKilariFK8-L23jMC98 -->
### KIhmKilariFK8-L23jMC98 — Scope by-id lookups to the authenticated user

Identity service looked up ProfileVideo by id and VehicleDetails by uuid straight from the URL/query without checking the JWT user owned them. Added owner-scoped repository finders (ProfileVideoRepository.findByIdAndUser_id, VehicleDetailsRepository.findByUuidAndOwner_id) and resolve the caller with userService.getUserFromToken; admin delete keeps unscoped findById only for ROLE_ADMIN. Scope any new by-id lookup by the authenticated user in the query.
<!-- /harness-remediation -->

<!-- harness-remediation:Okl8GtVopGlT2RgaaIAwyK -->
### Okl8GtVopGlT2RgaaIAwyK — Validate request values placed in outbound headers

ContactMechanicView forwarded request.META HTTP_AUTHORIZATION straight into the outbound requests.get headers; it now passes through utils.helper.validate_header_value, which rejects empty values and any character outside printable ASCII (CR/LF/NUL/DEL/non-ASCII) and returns 400 before any request is made. Use that helper for any other request-derived value placed in an outbound header.
<!-- /harness-remediation -->

<!-- harness-remediation:PE6tioYvlHHDl3ttC3tICg -->
### PE6tioYvlHHDl3ttC3tICg — Bind SQL parameters, never concatenate

ApplyCouponView.post built raw SQL by concatenating user.id and the request coupon_code into cursor.execute; it now keeps the same cursor.execute call but binds both values as %s parameters. Always pass user data as the params argument of cursor.execute; the test_sql_injection test now asserts the payload is treated as a literal coupon (COUPON_NOT_FOUND) with no data leak.
<!-- /harness-remediation -->

<!-- harness-remediation:Pume8qY5D8LUn5sm8O-Owx -->
### Pume8qY5D8LUn5sm8O-Owx — Open report files once with O_NOFOLLOW after allowlist

mechanic/views.py validated the report filename only before unquote() and used exists/isfile then open() on the path (TOCTOU, symlink following), and built the report path from a string id. Now the decoded name is re-validated against a strict allowlist regex, the file name is built from int(report_id) with a fixed pattern, and both read and write use a single os.open(..., O_NOFOLLOW) on the realpath/commonpath-checked path with fstat S_ISREG; keep new report file access on this pattern.
<!-- /harness-remediation -->

<!-- harness-remediation:XTRkC2YjTB_rMPkT7fQf4b -->
### XTRkC2YjTB_rMPkT7fQf4b — Never disable TLS certificate validation

Outbound HTTPS calls disabled certificate validation (requests verify=False in workshop; TrustAllStrategy + NoopHostnameVerifier in identity VehicleOwnershipServiceImpl). Workshop now passes settings.OUTBOUND_TLS_VERIFY (TLS_CA_BUNDLE/REQUESTS_CA_BUNDLE path or True) and identity builds an SSLContext from the JVM default or an operator trust store (api.gateway.truststore*) with default hostname verification; never set verify=False or a trust-all manager, supply a CA bundle/trust store via env instead.
<!-- /harness-remediation -->

<!-- harness-remediation:BU3dZ96NK4EHj-FQr1F_0 -->
### BU3dZ96NK4EHj-FQr1F_0 — Single validated mail recipient and escaped bodies

SMTPMailServer.sendMail parsed the recipient with InternetAddress.parse, which accepts comma-separated lists, so a user-supplied new_email could add extra recipients; it now rejects anything but exactly one validated address (in addition to existing control-char stripping, validate() and html-escaped MailBody values). Keep all mail sending routed through SMTPMailServer and escape every new MailBody value via MailBody.escape.
<!-- /harness-remediation -->

<!-- harness-remediation:oEf-scmVYnZuYjaLHQEf_V -->
### oEf-scmVYnZuYjaLHQEf_V — Never commit TLS private keys

Six TLS private keys (plus the PKCS12/JKS keystores embedding the identity key) were committed under services/*/certs and services/gateway-service; they were removed, ignored via .gitignore, and services now read key material from a runtime-mounted /app/certs (compose TLS_CERTS_DIR volume, Helm tlsSecretName Secret) with keystore passwords from env. gencert.sh/keystore.sh now write keys only to git-ignored paths and take TLS_KEYSTORE_PASSWORD from the environment; never commit generated keys and rotate the old ones.
<!-- /harness-remediation -->

<!-- harness-remediation:rsB-gCiajYBkdDyVJDU_fd -->
### rsB-gCiajYBkdDyVJDU_fd — Central CORS allowlist from environment

Identity controllers used bare @CrossOrigin (wildcard) and workshop used CORS_ORIGIN_ALLOW_ALL plus a no-op method-level @csrf_exempt. CORS is now centralized in WebSecurityConfig.corsConfigurationSource (property cors.allowed-origins from env CORS_ALLOWED_ORIGINS, '*' filtered out) and workshop settings.CORS_ALLOWED_ORIGINS; add new front-end origins via that env var (compose/helm/k8s) instead of annotating controllers.
<!-- /harness-remediation -->

<!-- harness-remediation:tN6VEEO_NcqeTH-xWZ-76H -->
### tN6VEEO_NcqeTH-xWZ-76H — Route user-supplied outbound fetches through outbound_get

contact_mechanic fetched a user URL after a resolve-then-fetch check (DNS rebinding gap) and passed the raw request value to requests.get; now validate_outbound_url returns a URL rebuilt from validated components and utils.url_validation.outbound_get fetches it through a SafeOutboundAdapter that re-checks the connected peer IP at connect time (no redirects, no env proxies). Route any new user-supplied outbound fetch through outbound_get.
<!-- /harness-remediation -->

<!-- harness-remediation:vm04F3uzOcT1cPLF2p2A6r -->
### vm04F3uzOcT1cPLF2p2A6r — No wildcard ALLOWED_HOSTS

Workshop settings.py used ALLOWED_HOSTS=['*'] (DNS rebinding / Host header risk); it now reads a comma separated ALLOWED_HOSTS env var, drops wildcard entries, and defaults to localhost, loopback, 0.0.0.0 (container health check) and crapi-workshop (nginx proxy Host header). When adding a new ingress or service name, set ALLOWED_HOSTS (compose WORKSHOP_ALLOWED_HOSTS, k8s configmap, helm workshop.config.extraAllowedHosts) rather than re-adding a wildcard.
<!-- /harness-remediation -->

<!-- harness-remediation:yHTwW1wjFHOVBfqEhY2Ali -->
### yHTwW1wjFHOVBfqEhY2Ali — No captured JWTs in Postman requests

crAPI.postman_collection.json had hardcoded Bearer JWTs in Authorization headers; the collection already sets a {{token}} collection variable from the login test script, so headers now use 'Bearer {{token}}'. Never paste captured JWTs into Postman requests; reference the {{token}} variable.
<!-- /harness-remediation -->

<!-- harness-remediation:z6uUznqYpadcyMq9etJjAM -->
### z6uUznqYpadcyMq9etJjAM — Never commit private JWKS material

The identity RS256 JWKS (with private d/p/q/dp/dq/qi) was committed in services/identity and each deploy/*/keys folder and baked into the image as default_jwks.json; it is now removed, git-ignored, and entrypoint.sh loads it only from JWKS env, JWKS_FILE, /app/keys or /.keys at runtime and fails closed if absent. Message/alphabet constants and the keys.md example are not secrets and must not be suppressed; rotate the old key since it remains in git history.
<!-- /harness-remediation -->

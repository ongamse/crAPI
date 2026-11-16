# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec--FEt04pKqvJtBtWdn01qOw -->
### sec--FEt04pKqvJtBtWdn01qOw — Sanitize mail header values

SMTPMailServer.sendMail passed the caller-supplied subject straight to MimeMessage.setSubject, allowing CR/LF header injection. The subject now has control characters (including CR/LF) replaced before setSubject(subject, "UTF-8"); sanitize any user-influenced value placed in a mail header the same way.
<!-- /harness-remediation -->

<!-- harness-remediation:sec--eBb9TfRMTOEIYbJqPh5Yw -->
### sec--eBb9TfRMTOEIYbJqPh5Yw — Use SecureRandom for guessable identifiers

GenerateVIN used java.util.Random for VIN/pincode generation; the default constructor now uses java.security.SecureRandom (still a Random subtype, so callers are unchanged). Use SecureRandom for any identifier an attacker could predict; the seeded constructor is unused and left deterministic.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-4AFesvm8aBptjZMMBzLrSp -->
### sec-4AFesvm8aBptjZMMBzLrSp — Never disable TLS verification in requests

Four requests.get/post calls in the workshop service (merchant/views.py, utils/jwt.py, shop/views.py, seed_database.py) passed verify=False, disabling TLS certificate validation. Removed it so requests uses its default verification. For self-signed internal certs, set REQUESTS_CA_BUNDLE to a trusted CA bundle rather than disabling verification.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-8YwIg5IB7sUedg1UaygX3Q -->
### sec-8YwIg5IB7sUedg1UaygX3Q — Run containers as a non-root user

The service Dockerfiles (web, gateway-service, chatbot, workshop, identity, community) ran as root with no USER; each now creates an unprivileged uid 10001 'app' user, chowns /app (and nginx config/log/temp dirs for web) and ends with USER app. Ports 80/443 (web, gateway-service) use setcap cap_net_bind_service on the server binary; new images should always add a USER and grant any privileged-port or writable-path needs explicitly.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-AyzjyaIxBeU1fUBeCyM8Xg -->
### sec-AyzjyaIxBeU1fUBeCyM8Xg — Bind SQL parameters in raw cursors

ApplyCouponView concatenated user.id and the request coupon_code into a raw cursor.execute SQL string (SQLi); it now uses Django DB-API bound parameters (%s placeholders with a params list) on the same cursor. Always pass params to cursor.execute; the test_sql_injection test now asserts the payload is treated as a literal (COUPON_NOT_FOUND).
<!-- /harness-remediation -->

<!-- harness-remediation:sec-DOAehNHIkp1SyIQSaN9CRR -->
### sec-DOAehNHIkp1SyIQSaN9CRR — Configure DRF throttling by default

REST_FRAMEWORK in crapi_site/settings.py had no throttling; added DRF AnonRateThrottle/UserRateThrottle as defaults with generous env-overridable rates (THROTTLE_RATE_ANON/THROTTLE_RATE_USER). Tune rates per deployment and override per-view for sensitive endpoints.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-DyT2WSzpoTnT-cKuF0-8oc -->
### sec-DyT2WSzpoTnT-cKuF0-8oc — Do not csrf_exempt DRF views

SignUpView.post in crapi/mechanic/views.py carried a redundant method-level @csrf_exempt on a DRF APIView (as_view() already exempts it and DRF only enforces CSRF for session-authenticated users); removed the decorator and unused import. Do not add csrf_exempt to DRF APIView methods; rely on jwt_auth_required for token auth.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-HSjHpAUojAYJfHV8F9p14k -->
### sec-HSjHpAUojAYJfHV8F9p14k — Encode JSON responses with json.NewEncoder

gateway-service main.go wrote json.Marshal output straight to http.ResponseWriter via w.Write; handlers now stream through json.NewEncoder(w) with an explicit application/json Content-Type and X-Content-Type-Options: nosniff. Use json.NewEncoder(w) for JSON responses in this service rather than raw w.Write.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-KaubYgUZZorOG844RCMuQ8 -->
### sec-KaubYgUZZorOG844RCMuQ8 — Pin supported TLS protocols

VehicleOwnershipServiceImpl built an SSLConnectionSocketFactory with no protocol restriction, so legacy TLS versions could be negotiated. It now passes an explicit supportedProtocols list of TLSv1.2 and TLSv1.3 to the 4-arg SSLConnectionSocketFactory constructor. Always pin supportedProtocols when building socket factories.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-NioRuN6wkxdNblLqCffyiW -->
### sec-NioRuN6wkxdNblLqCffyiW — Avoid dynamic bracket access in components

Multi-step wizard components indexed the steps array with a state variable (steps[currentStep]); replaced with array destructuring of the fixed steps and an explicit currentStep comparison so no dynamic bracket access remains. Avoid obj[variable] in components; select from explicit named values.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-P9AzhWqQtNiCV8Ln2I6eMH -->
### sec-P9AzhWqQtNiCV8Ln2I6eMH — Set allowPrivilegeEscalation false in k8s

K8s base manifests had containers with no securityContext; added allowPrivilegeEscalation: false per container (no runAsUser/runAsNonRoot, so stateful DB images and mailhog still start). web and gateway-service rely on setcap cap_net_bind_service, which no_new_privs disables, so their pod spec sets the safe sysctl net.ipv4.ip_unprivileged_port_start=0; new containers in deploy/k8s/base should always carry allowPrivilegeEscalation: false.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-RI2Z1uU7n2lbZe3HP7lkKn -->
### sec-RI2Z1uU7n2lbZe3HP7lkKn — Pin GitHub Actions to commit SHAs

Workflows in .github/workflows used mutable action tags (e.g. actions/checkout@v4). All 56 uses: refs are now pinned to full commit SHAs resolved via git ls-remote (peeled commit for annotated tags) with the original tag kept as a trailing '# vX' comment; pin new actions the same way and bump tag+SHA together.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-WURS6Du3oV6agzyq1QIhVH -->
### sec-WURS6Du3oV6agzyq1QIhVH — Do not commit TLS private keys

Six services committed the same TLS private key as services/*/certs/server.key (and gateway-service/server.key). The key files are now comment-only placeholders, so the Dockerfile COPY lines still work. services/gencert.sh generates a fresh key at deploy time, and the chatbot, workshop and web entrypoints now exit if the key is not a PEM. The old keys are in git history and must be rotated. Do not commit generated certs/server.key files again.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-XaVfRFQQ17vmUonDUiIXtN -->
### sec-XaVfRFQQ17vmUonDUiIXtN — Keep default hostname verification and trust store

VehicleOwnershipServiceImpl.restTemplate() built an HttpClient with TrustAllStrategy and NoopHostnameVerifier (and mixed httpclient4 with the hc5 HttpClient interface); it now uses RestTemplateBuilder defaults so the JDK default trust store, TLS 1.2/1.3 and default hostname verification apply. Internal HTTPS gateways must present a cert trusted by the JVM trust store (import the CA via javax.net.ssl.trustStore) rather than disabling verification.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-YTonx4mF0k7_KP2Zp8fCUp -->
### sec-YTonx4mF0k7_KP2Zp8fCUp — Keep CSRF protection enabled

identity WebSecurityConfig disabled CSRF globally although the service is stateless (JwtAuthTokenFilter reads Authorization Bearer/ApiKey headers, no cookies/sessions). CSRF is now enabled with CookieCsrfTokenRepository and ignores only the permitAll credential-body endpoints (/identity/api/auth/**, /identity/management/user/**) and requests carrying an explicit Bearer/ApiKey Authorization header; do not call http.csrf().disable() again, extend the ignore list narrowly if new stateless paths are added.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-YXpU3hw0pNykpvtncKiEOA -->
### sec-YXpU3hw0pNykpvtncKiEOA — Verify JWT signatures against JWKS

community/api/auth/token.go parsed the bearer JWT with ParseUnverified and relied only on a separate call to the identity verify endpoint; it now uses jwt.Parser.ParseWithClaims with a keyfunc that pins RS256 and loads the RSA public key from the identity service /identity/api/auth/jwks.json (fetchIdentityPublicKey). Never use ParseUnverified for auth decisions; verify against the identity JWKS.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-ZBHqRDUCbHBTha0pZCo9OS -->
### sec-ZBHqRDUCbHBTha0pZCo9OS — Never pass user data to a shell

BashCommand.executeBashCommand ran user-influenced conversion params via `bash -c`; it now splits on whitespace, allowlists each token against a conservative regex, and runs ProcessBuilder with an argv list and no shell. Never pass user data to a shell; build argv and validate tokens.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-_80H5OJE3h3VGgI26wvdLd -->
### sec-_80H5OJE3h3VGgI26wvdLd — No live tokens in Postman collections

The crAPI Postman collection had hardcoded Bearer JWTs in explicit Authorization headers; replaced with the collection's existing {{token}} variable (already used by the bearer auth blocks). Never paste live tokens into Postman headers; set the token variable at runtime. The old tokens remain in git history and should be treated as compromised.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-aIS1n60I68rBR5_wNePJhC -->
### sec-aIS1n60I68rBR5_wNePJhC — Verify JWT signatures locally

workshop/utils/jwt.py decoded the bearer token with verify_signature=False after only a remote /verify call. It now verifies the RS256 signature locally via _decode_verified, using PyJWKClient against the identity service jwks.json (new settings.IDENTITY_JWKS), with the algorithm pinned to RS256 and the key chosen only from that trusted set. Never call jwt.decode without a key and algorithms list; reuse _decode_verified.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-b1ywMLW7y-VimxanHvV_gI -->
### sec-b1ywMLW7y-VimxanHvV_gI — Never log any part of a credential

chat_api.py logged the first 5 characters of the user's OpenAI API key at debug level (init and state handlers). Now only the session id and key presence are logged; never log any part of a credential variable, log presence/session id instead.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-kBLY1t01dZdZol6gSwAZhx -->
### sec-kBLY1t01dZdZol6gSwAZhx — Set content type and nosniff on responses

responses.JSON wrote encoder errors to the ResponseWriter via fmt.Fprintf with no content type. It now encodes into a buffer, sets Content-Type application/json and X-Content-Type-Options nosniff, and on failure returns a static JSON 500 body with w.Write. Never write error text or user data directly to http.ResponseWriter.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-kpT5uKHaKudZqnCWVLdnxH -->
### sec-kpT5uKHaKudZqnCWVLdnxH — Reuse a verifying TLS config helper in Go

The community Go service set http.DefaultTransport TLSClientConfig to InsecureSkipVerify in server.go and auth/token.go; both now use utils.ClientTLSConfig(), which verifies certificates against the system pool plus an optional SSL_CERT_FILE CA bundle and sets MinVersion TLS1.2. Reuse that helper for any new outbound HTTPS client instead of building a tls.Config inline.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-mx-pCe82r6wCJUMP8bbBYA -->
### sec-mx-pCe82r6wCJUMP8bbBYA — Set timeouts on outbound HTTP calls

Outbound requests.get/post calls in the workshop service had no timeout and could hang workers; added settings.HTTP_REQUEST_TIMEOUT (env-overridable, default 10s) in crapi_site/settings.py and passed it as timeout= on every requests call; new requests calls should do the same.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-sHHnXvqFY_IPoeJND6DqVY -->
### sec-sHHnXvqFY_IPoeJND6DqVY — Pass github context values via env in workflows

QwietAI.yml interpolated github.head_ref (attacker-controlled branch name) directly into a run script; it is now passed via a BRANCH_NAME env var and referenced as a quoted shell variable. Always route github.* context values through env: in run steps.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-tNEFX-szlCNRllk6l-aDWy -->
### sec-tNEFX-szlCNRllk6l-aDWy — Bind servers to loopback by default

mcpserver/__main__.py bound the MCP streamable-http server to 0.0.0.0, but its only consumer (chatbot/mcp_client.py) connects to http://localhost:5500 in the same container and no compose/k8s publishes 5500; it now binds to MCP_SERVER_HOST (default 127.0.0.1). Bind servers to loopback by default and make the host env-overridable if cross-container access is ever needed.
<!-- /harness-remediation -->

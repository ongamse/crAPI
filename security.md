# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:D5EBmhI64agCI2tMQASpjL -->
### D5EBmhI64agCI2tMQASpjL — Never commit credential defaults

Deploy and service config now read DB, Mongo, Django SECRET_KEY, gateway and keystore passwords from env (compose ${VAR:?}, helm required(), crapi-credentials k8s Secret). Never commit a credential default; add new consumers to the Secret and compose env. Test fixture passwords are generated per process.
<!-- /harness-remediation -->

<!-- harness-remediation:542wVQcVYFb0XRlk0pAkFD -->
### 542wVQcVYFb0XRlk0pAkFD — Require DB passwords from env, build URLs from parts

Chatbot Postgres and Mongo passwords have no default and are required from env at import. Build connection URLs from separate parameters (SQLAlchemy URL.create) instead of user:pass@ string literals, and give every consumer the existing crapi-credentials secret.
<!-- /harness-remediation -->

<!-- harness-remediation:F7GWMB4T9y5-Gkpv4l-PR1 -->
### F7GWMB4T9y5-Gkpv4l-PR1 — Use SecureRandom without a fixed seed

InitialDataConfig now uses java.security.SecureRandom. Use SecureRandom for any value that need not be reproducible, and never call setSeed with a constant.
<!-- /harness-remediation -->

<!-- harness-remediation:KIhmKilariFK8-L23jMC98 -->
### KIhmKilariFK8-L23jMC98 — Resolve per-user resources from the token user

Profile video and vehicle-location lookups load the token user's own records (findByUser_id, findAllByOwner_id) and match the requested id in memory with Objects.equals, so the request id never reaches a repository call. Do the same for new per-user resources; deleteAdminProfileVideo is the only intentionally unscoped lookup.
<!-- /harness-remediation -->

<!-- harness-remediation:Okl8GtVopGlT2RgaaIAwyK -->
### Okl8GtVopGlT2RgaaIAwyK — Rebuild outbound headers from allowlisted captures

ContactMechanicView builds Authorization via build_authorization_header(): a strict JWT-charset fullmatch regex, then 'Bearer ' plus the captured token; anything else gets 400. Never forward raw request headers to outbound calls.
<!-- /harness-remediation -->

<!-- harness-remediation:PE6tioYvlHHDl3ttC3tICg -->
### PE6tioYvlHHDl3ttC3tICg — Bind parameters on raw SQL cursors

ApplyCouponView passes coupon_code as a %s parameter to cursor.execute instead of concatenating it into SQL. Always bind params on raw cursors.
<!-- /harness-remediation -->

<!-- harness-remediation:Pume8qY5D8LUn5sm8O-Owx -->
### Pume8qY5D8LUn5sm8O-Owx — Do not build report paths from request values

DownloadReportView selects the file through find_report_entry(), which returns the matching name from os.listdir of the reports dir. service_report_pdf formats the DB primary key with %d. Select from a directory listing or use DB-sourced ids.
<!-- /harness-remediation -->

<!-- harness-remediation:XTRkC2YjTB_rMPkT7fQf4b -->
### XTRkC2YjTB_rMPkT7fQf4b — Keep TLS verification on for outbound calls

Identity builds its SSLContext from the JVM default or an optional API_GATEWAY_TRUSTSTORE, and workshop passes settings.TLS_CA_BUNDLE to verify=. For internal CAs configure these env vars instead of disabling verification.
<!-- /harness-remediation -->

<!-- harness-remediation:lbgfZZVP-uqKaqE98iLnp3 -->
### lbgfZZVP-uqKaqE98iLnp3 — Validate on the open handle, not the path

DownloadReportView opens once with O_NOFOLLOW|O_NONBLOCK, checks stat.S_ISREG on the descriptor, and serves os.fdopen(fd). Do not isfile/islink-check a path and then open it.
<!-- /harness-remediation -->

<!-- harness-remediation:oEf-scmVYnZuYjaLHQEf_V -->
### oEf-scmVYnZuYjaLHQEf_V — Never commit TLS private keys

Per-service server.key files are removed and git-ignored; consumers fall back to plain HTTP when no key is mounted. Generate keys with services/gencert.sh or mount them via TLS_KEY, and rotate the old key since it remains in git history.
<!-- /harness-remediation -->

<!-- harness-remediation:rsB-gCiajYBkdDyVJDU_fd -->
### rsB-gCiajYBkdDyVJDU_fd — Use an explicit CORS origin allowlist

CORS is centralized in WebSecurityConfig.corsConfigurationSource (identity) and CORS_ALLOWED_ORIGINS in settings.py (workshop), both driven by the CORS_ALLOWED_ORIGINS env var (empty means same-origin; wildcard ignored). Do not re-add per-controller @CrossOrigin or csrf_exempt.
<!-- /harness-remediation -->

<!-- harness-remediation:tN6VEEO_NcqeTH-xWZ-76H -->
### tN6VEEO_NcqeTH-xWZ-76H — Pin user-influenced outbound requests to validated IPs

call_mechanic_api connects to an IP validated by build_mechanic_api_url through PinnedHostAdapter, keeping SNI, cert verification and Host on the original name, with no re-resolution. Only allowlisted params are forwarded. Resolve once, validate, and connect to that address for any user-influenced URL.
<!-- /harness-remediation -->

<!-- harness-remediation:vm04F3uzOcT1cPLF2p2A6r -->
### vm04F3uzOcT1cPLF2p2A6r — Do not use a wildcard ALLOWED_HOSTS

Workshop ALLOWED_HOSTS defaults to localhost and service names plus an ALLOWED_HOSTS env var (wildcard ignored). Add new external hostnames via the env var.
<!-- /harness-remediation -->

<!-- harness-remediation:yHTwW1wjFHOVBfqEhY2Ali -->
### yHTwW1wjFHOVBfqEhY2Ali — Use {{token}} in Postman collections

The Postman collection uses the {{token}} variable instead of pasted Bearer JWTs. Never commit captured tokens; rotate any JWT that was committed.
<!-- /harness-remediation -->

<!-- harness-remediation:z6uUznqYpadcyMq9etJjAM -->
### z6uUznqYpadcyMq9etJjAM — Never commit key material

RSA private JWKS material was removed; JwtProvider loads a runtime-supplied JWKS (JWKS env or mounted file) or generates an ephemeral key. Mount your own gitignored jwks.json and rotate the old key, which remains in git history.
<!-- /harness-remediation -->

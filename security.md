# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-no-committed-tls-keys -->
### sec-no-committed-tls-keys — Do not commit TLS private keys

Service TLS keys (server.key) are generated locally with services/gencert.sh or at image build time and are gitignored. Supply key and certificate paths through environment variables such as TLS_KEY and TLS_CERTIFICATE.
<!-- /harness-remediation -->

<!-- harness-remediation:sec-postman-token-variable -->
### sec-postman-token-variable — Use variables for tokens in Postman collections

Authorization headers in postman_collections must reference the {{token}} collection variable set by the login script, never a literal JWT.
<!-- /harness-remediation -->

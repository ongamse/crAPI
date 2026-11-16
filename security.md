# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-postman-no-hardcoded-jwt -->
### sec-postman-no-hardcoded-jwt — Do not hardcode JWTs in Postman collections

The crAPI Postman collection must reference the {{token}} collection variable, which the Login request sets from the login response, instead of pasted Bearer tokens. Never paste captured JWTs into request headers.
<!-- /harness-remediation -->

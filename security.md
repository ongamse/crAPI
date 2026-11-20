# Security

Security rules for this repository. Marked blocks are maintained by Harness remediation. Text outside those blocks is left as written.


<!-- harness-remediation:sec-requests-timeout -->
### sec-requests-timeout — Set timeouts on outbound HTTP calls

Calls to `requests` in the workshop service must pass an explicit `timeout=` (this repo uses 5 seconds, as in `crapi/shop/views.py`), so a stalled upstream cannot hold sockets open and exhaust resources.
<!-- /harness-remediation -->

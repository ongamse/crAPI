import ipaddress
import socket
from base64 import b64encode
from urllib.parse import urlsplit


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def is_safe_header_value(value):
    """
    Header injection guard for values copied from an incoming request into an
    outbound HTTP header.
    :returns True for a str made only of printable ASCII (plus space/tab), so
        it contains no CR, LF, NUL or other control characters
    """
    if not isinstance(value, str):
        return False
    return all(ch == "\t" or " " <= ch <= "~" for ch in value)


def _resolve_ips(hostname, port):
    """
    Resolve hostname to every IP address it points to.
    Returns an empty list when it cannot be resolved.
    """
    try:
        infos = socket.getaddrinfo(hostname, port, proto=socket.IPPROTO_TCP)
    except (socket.gaierror, UnicodeError):
        return []
    ips = []
    for info in infos:
        try:
            ips.append(ipaddress.ip_address(info[4][0].split("%")[0]))
        except ValueError:
            return []
    return ips


def is_safe_outbound_url(
    url, allowed_hosts=(), same_origin_host=None, same_origin_path=None
):
    """
    SSRF guard for user supplied URLs.
    - only http/https, no embedded credentials
    - link-local (cloud metadata), multicast, reserved and unspecified
      addresses are always rejected
    - host must resolve only to globally routable addresses, unless it is
      explicitly allowlisted (allowed_hosts) or it is this server's own
      host (same_origin_host) and the path is exactly same_origin_path
    :returns True when the url may be requested
    """
    if not isinstance(url, str):
        return False
    try:
        parts = urlsplit(url)
        hostname = parts.hostname
        port = parts.port or (443 if parts.scheme == "https" else 80)
    except ValueError:
        return False
    if parts.scheme not in ("http", "https") or not hostname:
        return False
    if parts.username is not None or parts.password is not None:
        return False

    ips = _resolve_ips(hostname, port)
    if not ips:
        return False
    for ip in ips:
        if (
            ip.is_link_local
            or ip.is_multicast
            or ip.is_reserved
            or ip.is_unspecified
        ):
            return False

    hostname = hostname.lower()
    netloc = parts.netloc.lower()
    allowed = {h.strip().lower() for h in allowed_hosts if h and h.strip()}
    if hostname in allowed or netloc in allowed:
        return True
    if (
        same_origin_host
        and same_origin_path
        and netloc == same_origin_host.lower()
        and parts.path == same_origin_path
    ):
        return True

    return all(ip.is_global for ip in ips)

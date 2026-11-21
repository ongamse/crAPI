import ipaddress
import re
import socket
from base64 import b64encode
from urllib.parse import urlparse


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


_BEARER_AUTHORIZATION_RE = re.compile(r"Bearer [\x21-\x7e]+")


def get_safe_bearer_authorization(value):
    """
    Validate a client supplied Authorization header value before it is
    forwarded in an outbound request (HTTP header injection guard).

    Only a 'Bearer <token>' value made of printable, non-space ASCII is
    accepted, which rules out CR/LF and any other control characters.

    :param value: raw Authorization header value from the incoming request
    :returns the value if it is safe to forward, else None
    """
    if isinstance(value, str) and _BEARER_AUTHORIZATION_RE.fullmatch(value):
        return value
    return None


def _is_public_address(address):
    """
    True only for globally routable addresses. Loopback, private, link-local
    (including the 169.254.169.254 metadata address), multicast, reserved and
    unspecified addresses are rejected.
    """
    ip = ipaddress.ip_address(address.split("%", 1)[0])
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    return ip.is_global and not ip.is_multicast


def is_safe_outbound_url(url, allowed_hosts=()):
    """
    Validate a user supplied URL before the server fetches it (SSRF guard).

    - only http / https, no embedded credentials
    - every address the host resolves to must be public, unless the host
      (or host:port) is explicitly listed in allowed_hosts

    :param url: url supplied by the client
    :param allowed_hosts: iterable of 'host' or 'host:port' entries that are
        trusted internal destinations
    :returns True if the url may be requested, else False
    """
    if not isinstance(url, str):
        return False
    try:
        parsed = urlparse(url.strip())
        host = parsed.hostname
        port = parsed.port
    except ValueError:
        return False
    if parsed.scheme not in ("http", "https") or not host:
        return False
    if parsed.username is not None or parsed.password is not None:
        return False
    host = host.lower()
    port = port or (443 if parsed.scheme == "https" else 80)

    allowed = {entry.strip().lower() for entry in allowed_hosts if entry.strip()}
    if host in allowed or f"{host}:{port}" in allowed:
        return True

    try:
        addresses = {
            info[4][0]
            for info in socket.getaddrinfo(host, port, proto=socket.IPPROTO_TCP)
        }
    except (socket.gaierror, UnicodeError):
        return False
    if not addresses:
        return False
    try:
        return all(_is_public_address(address) for address in addresses)
    except ValueError:
        return False

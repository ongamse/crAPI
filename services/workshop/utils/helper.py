import ipaddress
import socket
from base64 import b64encode
from urllib.parse import urlsplit


class UnsafeURLError(ValueError):
    """Raised when an outbound URL targets a disallowed destination."""


class UnsafeHeaderError(ValueError):
    """Raised when a value forwarded in an HTTP header contains unsafe characters."""


def validate_header_value(value):
    """
    Validate a client supplied value before it is forwarded in an HTTP header.
    Rejects CR, LF and other control characters (header injection / splitting)
    and non-ASCII data.
    :param value: header value taken from the incoming request (or None)
    :returns the value unchanged
    :raises UnsafeHeaderError if the value is not safe to forward
    """
    if value is None:
        return None
    if not isinstance(value, str):
        raise UnsafeHeaderError("Invalid header value.")
    for char in value:
        code = ord(char)
        if code > 126 or (code < 32 and char != "\t") or code == 127:
            raise UnsafeHeaderError("Invalid header value.")
    return value


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def validate_outbound_url(url, allowed_hosts=()):
    """
    Validate a user supplied URL before the server makes a request to it.
    - scheme must be http or https, no embedded credentials
    - every address the host resolves to must be a public (global) address,
      unless the hostname is explicitly listed in allowed_hosts
    :param url: url supplied by the client
    :param allowed_hosts: iterable of lowercase hostnames explicitly trusted
    :returns the validated url
    :raises UnsafeURLError if the destination is not permitted
    """
    if not isinstance(url, str):
        raise UnsafeURLError("Invalid URL.")
    try:
        parts = urlsplit(url.strip())
        hostname = parts.hostname
        port = parts.port
    except ValueError:
        raise UnsafeURLError("Invalid URL.")
    if parts.scheme not in ("http", "https") or not hostname:
        raise UnsafeURLError("Only absolute http and https URLs are allowed.")
    if parts.username is not None or parts.password is not None:
        raise UnsafeURLError("Credentials in URL are not allowed.")
    if hostname.lower() in {h.lower() for h in allowed_hosts}:
        return url.strip()
    try:
        addr_infos = socket.getaddrinfo(
            hostname, port or (443 if parts.scheme == "https" else 80)
        )
    except socket.gaierror:
        raise UnsafeURLError("Could not resolve the host.")
    if not addr_infos:
        raise UnsafeURLError("Could not resolve the host.")
    for info in addr_infos:
        ip = ipaddress.ip_address(info[4][0].split("%")[0])
        if ip.version == 6 and ip.ipv4_mapped:
            ip = ip.ipv4_mapped
        if not ip.is_global:
            raise UnsafeURLError("The destination host is not allowed.")
    return url.strip()

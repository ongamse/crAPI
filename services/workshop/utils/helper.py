import ipaddress
import socket
from base64 import b64encode
from urllib.parse import urlsplit

# Only the mechanic "receive_report" endpoint is a legitimate callback target
# for the contact mechanic feature.
MECHANIC_API_PATH_SUFFIX = "/api/mechanic/receive_report"


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def validate_header_value(value):
    """
    Validates a request derived value before it is forwarded in an outbound
    HTTP header. Rejects CR/LF and any other control character to prevent
    header injection / response splitting.
    :param value: header value taken from the incoming request
    :returns the value unchanged if safe, or None if it is not allowed
    """
    if not isinstance(value, str) or not value:
        return None
    for char in value:
        if not 0x20 <= ord(char) <= 0x7E:
            return None
    return value


def _is_blocked_address(address):
    """
    True for addresses that must never be reached from user supplied URLs:
    link-local (cloud metadata), unspecified, multicast and reserved ranges.
    """
    ip = ipaddress.ip_address(address)
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return (
        ip.is_link_local or ip.is_unspecified or ip.is_multicast or ip.is_reserved
    )


def validate_mechanic_api_url(url):
    """
    Validates the user supplied mechanic api url used for outbound requests.
    :param url: url string supplied by the client
    :returns validated url string, or None if the url is not allowed
    """
    if not isinstance(url, str):
        return None
    try:
        parsed = urlsplit(url.strip())
        hostname = parsed.hostname
        port = parsed.port
    except ValueError:
        return None
    if parsed.scheme not in ("http", "https") or not hostname:
        return None
    if parsed.username is not None or parsed.password is not None or parsed.fragment:
        return None
    if not parsed.path.rstrip("/").endswith(MECHANIC_API_PATH_SUFFIX):
        return None
    try:
        addresses = {
            info[4][0]
            for info in socket.getaddrinfo(
                hostname, port or (443 if parsed.scheme == "https" else 80)
            )
        }
    except socket.gaierror:
        # unresolvable host: nothing internal to reach, the request itself
        # will fail with a connection error
        addresses = set()
    for address in addresses:
        if _is_blocked_address(address.split("%")[0]):
            return None
    return parsed.geturl()

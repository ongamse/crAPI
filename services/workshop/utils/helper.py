from base64 import b64encode
from urllib.parse import urlparse


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def is_safe_header_value(value):
    """
    Check that a client-supplied value can be forwarded as an HTTP header value.
    :param value: header value taken from the incoming request
    :returns True only for non-empty str values made of printable characters
        (no CR, LF, NUL or other control characters)
    """
    if not isinstance(value, str) or not value:
        return False
    return all(ch == "\t" or 0x20 <= ord(ch) < 0x7F for ch in value)


def is_url_destination_allowed(url, allowed_hosts):
    """
    Check that an outbound request URL targets an allowlisted destination.
    :param url: url supplied by the client
    :param allowed_hosts: iterable of "hostname" or "hostname:port" entries
    :returns True only for http/https urls, without embedded credentials,
        whose hostname (or hostname:port) is in allowed_hosts
    """
    if not isinstance(url, str):
        return False
    try:
        parsed = urlparse(url.strip())
        hostname = parsed.hostname
        port = parsed.port
    except ValueError:
        return False
    if parsed.scheme not in ("http", "https") or not hostname:
        return False
    if parsed.username is not None or parsed.password is not None:
        return False
    hostname = hostname.lower()
    allowed = {entry.strip().lower() for entry in allowed_hosts if entry.strip()}
    return hostname in allowed or (port is not None and f"{hostname}:{port}" in allowed)

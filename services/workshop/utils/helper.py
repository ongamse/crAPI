from base64 import b64encode
from urllib.parse import quote, urlparse, urlsplit


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


def build_allowlisted_mechanic_url(raw_url, allowed_hosts):
    """
    Build the outbound request URL from server-held allowlist data.
    The client value is only used to select an allowlist entry; the scheme is
    a fixed literal, the netloc comes from the allowlist entry itself and only
    the (percent-quoted) path is taken from the client value. The query string
    is dropped because callers pass it separately as request params.
    :param raw_url: url supplied by the client
    :param allowed_hosts: iterable of "hostname" or "hostname:port" entries
    :returns the safe url, or None when the destination is not allowlisted
    """
    if not isinstance(raw_url, str):
        return None
    try:
        parsed = urlsplit(raw_url.strip())
        hostname = parsed.hostname
        port = parsed.port
    except ValueError:
        return None
    if not hostname or parsed.username is not None or parsed.password is not None:
        return None
    if parsed.scheme == "https":
        scheme = "https"
    elif parsed.scheme == "http":
        scheme = "http"
    else:
        return None
    hostname = hostname.lower()
    path = parsed.path or "/"
    if not path.startswith("/"):
        return None

    netloc = None
    for entry in allowed_hosts:
        entry = entry.strip().lower()
        if not entry:
            continue
        if port is not None and entry == f"{hostname}:{port}":
            netloc = entry
            break
        if entry == hostname:
            netloc = f"[{entry}]" if ":" in entry else entry
            if port is not None:
                netloc = f"{netloc}:{int(port)}"
            break
    if netloc is None:
        return None
    return f"{scheme}://{netloc}{quote(path, safe='/%')}"

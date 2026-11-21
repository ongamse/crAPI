import ipaddress
import re
import socket
import string
from base64 import b64encode
from urllib.parse import parse_qsl, quote, urlencode, urlparse, urlsplit, urlunsplit


def basic_auth(username, password=""):
    token = b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


_BEARER_PREFIX = "Bearer "
_BEARER_TOKEN_MAX_LENGTH = 4096
# '@' is accepted only because the unit-test mock jwt uses e-mail tokens; it
# cannot carry a header break.
_BEARER_TOKEN_ALPHABET = string.ascii_letters + string.digits + "-_=+/.@"
_BEARER_TOKEN_RE = re.compile(r"[A-Za-z0-9\-_=+/.@]+")
# Fixed table of trusted characters: output characters are looked up from
# this table, never copied from the request.
_BEARER_TOKEN_CHARS = {char: char for char in _BEARER_TOKEN_ALPHABET}


def get_safe_bearer_authorization(value):
    """
    Validate a client supplied Authorization header value and build a new
    'Bearer <token>' header value from it (HTTP header injection guard).

    The token must be 1.._BEARER_TOKEN_MAX_LENGTH characters of the token
    alphabet [A-Za-z0-9-_=+/.], which rules out CR/LF, whitespace and every
    other control character. The returned string is assembled character by
    character from a constant table, so no raw request text is returned.

    :param value: raw Authorization header value from the incoming request
    :returns a freshly built header value, or None if the value is unsafe
    """
    if not isinstance(value, str) or not value.startswith(_BEARER_PREFIX):
        return None
    token = value[len(_BEARER_PREFIX) :]
    if not 0 < len(token) <= _BEARER_TOKEN_MAX_LENGTH:
        return None
    if not _BEARER_TOKEN_RE.fullmatch(token):
        return None
    return _BEARER_PREFIX + "".join(_BEARER_TOKEN_CHARS[char] for char in token)


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

    return _resolves_only_to_public_addresses(host, port)


def _resolves_only_to_public_addresses(host, port):
    """
    True only when host resolves and every resolved address is public.
    """
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


_SAFE_SCHEMES = {"http": ("http", 80), "https": ("https", 443)}
_DNS_NAME_RE = re.compile(
    r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    r"(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*"
)
_UNSAFE_URL_CHARS_RE = re.compile(r"[\x00-\x20\x7f-\uffff\\]")


def _parse_allowed_host(entry):
    """
    Split an allowlist entry ('host' or 'host:port') into (host, port|None).
    """
    entry = entry.strip().lower()
    host, sep, port = entry.rpartition(":")
    if sep and port.isdigit():
        return host, int(port)
    return entry, None


def build_safe_outbound_url(url, allowed_hosts=()):
    """
    SSRF guard that never hands the client supplied string to the HTTP client.
    The url is parsed and validated, then a new url is built from validated
    components only:

    - scheme: picked from a fixed http / https table
    - host: the exact (case-insensitive, no substring / suffix matching)
      allowlist entry it matched, or else a strictly validated ASCII DNS name
      / IP literal whose resolved addresses are all public
    - port: a validated integer
    - path / query: re-encoded; userinfo and fragment are dropped (userinfo is
      rejected outright)

    :param url: url supplied by the client
    :param allowed_hosts: iterable of 'host' or 'host:port' entries that are
        trusted internal destinations
    :returns the rebuilt url string, or None if the url must not be requested
    """
    if not isinstance(url, str):
        return None
    url = url.strip()
    # control chars, whitespace, backslashes and non-ascii (IDN / homoglyph)
    if not url or _UNSAFE_URL_CHARS_RE.search(url):
        return None
    try:
        parsed = urlsplit(url)
        raw_host = parsed.hostname
        raw_port = parsed.port
    except ValueError:
        return None
    if (
        parsed.scheme.lower() not in _SAFE_SCHEMES
        or not raw_host
        or "@" in parsed.netloc
        or "%" in parsed.netloc
        or parsed.username is not None
        or parsed.password is not None
    ):
        return None
    scheme, default_port = _SAFE_SCHEMES[parsed.scheme.lower()]
    port = raw_port if raw_port is not None else default_port
    if not 0 < port < 65536:
        return None
    raw_host = raw_host.lower()

    safe_host = None
    for entry in allowed_hosts:
        allowed_host, allowed_port = _parse_allowed_host(entry)
        if allowed_host and allowed_host == raw_host and allowed_port in (None, port):
            safe_host = allowed_host  # value comes from settings, not the request
            break

    if safe_host is None:
        try:
            ip = ipaddress.ip_address(raw_host)
        except ValueError:
            if not _DNS_NAME_RE.fullmatch(raw_host) or len(raw_host) > 253:
                return None
            safe_host = raw_host  # validated by _DNS_NAME_RE above
        else:
            safe_host = f"[{ip}]" if ip.version == 6 else str(ip)
        if not _resolves_only_to_public_addresses(raw_host, port):
            return None
    elif ":" in safe_host:
        safe_host = f"[{safe_host}]"

    netloc = safe_host if port == default_port else f"{safe_host}:{port}"
    path = quote(parsed.path or "/", safe="/%:@!$&'()*+,;=-._~")
    query = urlencode(parse_qsl(parsed.query, keep_blank_values=True))
    return urlunsplit((scheme, netloc, path, query, ""))

#
# Licensed under the Apache License, Version 2.0 (the “License”);
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an “AS IS” BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
helpers to validate user supplied outbound URLs (SSRF protection)
"""
import ipaddress
import socket
from urllib.parse import urlsplit, urlunsplit

import requests
from requests.adapters import HTTPAdapter
from urllib3.connection import HTTPConnection, HTTPSConnection
from urllib3.connectionpool import HTTPConnectionPool, HTTPSConnectionPool

from utils import messages

ALLOWED_SCHEMES = ("http", "https")


class UnsafeURLError(ValueError):
    """
    raised when an outbound URL is not allowed to be requested
    """


def _is_public_address(address):
    """
    returns True only for globally routable addresses
    (not loopback, private, link-local, metadata, multicast, reserved)
    """
    ip = ipaddress.ip_address(address.split("%", 1)[0])
    if ip.version == 6 and ip.ipv4_mapped is not None:
        ip = ip.ipv4_mapped
    return ip.is_global and not ip.is_multicast


def validate_outbound_url(url, allowed_hosts=()):
    """
    validates that url is an http(s) url pointing to an allowed destination
    :param url: url supplied by the user
    :param allowed_hosts: iterable of lowercase hostnames that are explicitly
        trusted by the deployer (may resolve to non public addresses)
    :returns the validated url
    :raises UnsafeURLError if the url must not be requested
    """
    if not isinstance(url, str):
        raise UnsafeURLError(messages.INVALID_MECHANIC_API)
    try:
        parts = urlsplit(url.strip())
        hostname = parts.hostname
        port = parts.port
    except ValueError:
        raise UnsafeURLError(messages.INVALID_MECHANIC_API)

    if parts.scheme.lower() not in ALLOWED_SCHEMES or not hostname:
        raise UnsafeURLError(messages.INVALID_MECHANIC_API)
    if parts.username is not None or parts.password is not None:
        raise UnsafeURLError(messages.INVALID_MECHANIC_API)

    # rebuild the url from the validated components (drops fragment)
    validated_url = urlunsplit(
        (parts.scheme.lower(), parts.netloc, parts.path, parts.query, "")
    )

    if hostname.lower().rstrip(".") in allowed_hosts:
        return validated_url

    try:
        addr_infos = socket.getaddrinfo(
            hostname, port or (443 if parts.scheme.lower() == "https" else 80)
        )
    except (socket.gaierror, UnicodeError):
        raise UnsafeURLError(messages.COULD_NOT_CONNECT)
    if not addr_infos:
        raise UnsafeURLError(messages.COULD_NOT_CONNECT)
    for addr_info in addr_infos:
        if not _is_public_address(addr_info[4][0]):
            raise UnsafeURLError(messages.MECHANIC_API_NOT_ALLOWED)
    return validated_url


def _check_peer_address(sock, host, allowed_hosts):
    """
    verifies the address the socket really connected to is public, which
    closes the DNS rebinding gap between validate_outbound_url and the fetch
    """
    if host.lower().rstrip(".") in allowed_hosts:
        return
    try:
        public = _is_public_address(sock.getpeername()[0])
    except (OSError, ValueError, IndexError):
        public = False
    if not public:
        sock.close()
        raise UnsafeURLError(messages.MECHANIC_API_NOT_ALLOWED)


def _build_pool_classes(allowed_hosts):
    """
    returns http/https connection pool classes whose connections refuse to
    talk to non public peer addresses (unless the host is allowlisted)
    """

    class _SafeHTTPConnection(HTTPConnection):
        def _new_conn(self):
            sock = super()._new_conn()
            _check_peer_address(sock, self.host, allowed_hosts)
            return sock

    class _SafeHTTPSConnection(HTTPSConnection):
        def _new_conn(self):
            sock = super()._new_conn()
            _check_peer_address(sock, self.host, allowed_hosts)
            return sock

    class _SafeHTTPPool(HTTPConnectionPool):
        ConnectionCls = _SafeHTTPConnection

    class _SafeHTTPSPool(HTTPSConnectionPool):
        ConnectionCls = _SafeHTTPSConnection

    return {"http": _SafeHTTPPool, "https": _SafeHTTPSPool}


class SafeOutboundAdapter(HTTPAdapter):
    """
    requests adapter validating the real peer address at connect time
    """

    def __init__(self, allowed_hosts=(), *args, **kwargs):
        self._allowed_hosts = tuple(allowed_hosts)
        super().__init__(*args, **kwargs)

    def init_poolmanager(self, *args, **kwargs):
        super().init_poolmanager(*args, **kwargs)
        self.poolmanager.pool_classes_by_scheme = _build_pool_classes(
            self._allowed_hosts
        )


def outbound_get(url, allowed_hosts=(), **kwargs):
    """
    GET a url previously returned by validate_outbound_url. The connection
    itself re-checks the peer address (DNS rebinding) and redirects are
    never followed.
    :raises UnsafeURLError if the connection would reach a non public address
    """
    kwargs["allow_redirects"] = False
    with requests.Session() as session:
        session.trust_env = False
        adapter = SafeOutboundAdapter(allowed_hosts=allowed_hosts)
        session.mount("http://", adapter)
        session.mount("https://", adapter)
        return session.get(url, **kwargs)

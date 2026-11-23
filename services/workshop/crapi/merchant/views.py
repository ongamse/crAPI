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
contains all the views related to Merchant
"""
import ipaddress
import logging
import re
import socket
from typing import NamedTuple, Tuple
from urllib.parse import quote, urlparse, urlsplit, urlunsplit
import requests
from requests.adapters import HTTPAdapter
from requests.exceptions import MissingSchema, InvalidURL
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView
from crapi.mechanic.serializers import (
    ServiceCommentViewSerializer,
    ServiceCommentCreateSerializer,
)
from utils.jwt import jwt_auth_required
from utils import messages
from rest_framework.pagination import LimitOffsetPagination
from utils.logging import log_error
from crapi_site import settings
from crapi.mechanic.models import ServiceRequest, ServiceComment
from .serializers import ContactMechanicSerializer, UserServiceRequestSerializer


logger = logging.getLogger()

RECEIVE_REPORT_PATH_SUFFIX = "/api/mechanic/receive_report"


def _is_blocked_address(address):
    """
    True for addresses that must never be reachable from this feature
    (link-local / cloud metadata, multicast, unspecified, reserved)
    """
    return (
        address.is_link_local
        or address.is_multicast
        or address.is_unspecified
        or address.is_reserved
    )


# "Bearer <header>.<payload>.<signature>" using the base64url charset of a JWT
BEARER_JWT_PATTERN = re.compile(
    r"Bearer ([A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]*)", re.ASCII
)


def build_authorization_header(value):
    """
    parses the incoming Authorization header with a strict allowlist pattern
    and rebuilds the outbound header value from the captured token only, so
    the raw request header (and any CR/LF / control characters in it) is
    never forwarded
    :param value: raw Authorization header of the incoming request
    :returns rebuilt "Bearer <token>" string, or None when not a valid bearer jwt
    """
    if not isinstance(value, str):
        return None
    match = BEARER_JWT_PATTERN.fullmatch(value)
    if match is None:
        return None
    return "Bearer " + match.group(1)


ALLOWED_SCHEMES = {"http": "http", "https": "https"}
# characters kept as-is when re-encoding the path / query of the outbound url
URL_SAFE_CHARS = "/%:@!$&'()*+,;=-._~"


class MechanicApiTarget(NamedTuple):
    """
    validated outbound target
    url: url rebuilt from validated components
    addresses: ip addresses the host resolved to at validation time, all of
        which passed the blocked-address check; the request is pinned to
        these so the name is not resolved a second time
    """

    url: str
    addresses: Tuple[str, ...]


def build_mechanic_api_url(url, request_host):
    """
    validates the user supplied mechanic_api url and rebuilds the outbound
    url from validated components, so the raw user string is never requested:
        scheme: taken from a fixed allowed set (http / https)
        host: taken from the matching settings.MECHANIC_API_ALLOWED_HOSTS entry,
            or the host this server was addressed with (its own receive_report
            endpoint only)
        port: validated integer
        path / query: parsed, '..' segments rejected, re-encoded
    never allowed: embedded credentials, hosts resolving to link-local /
        metadata / reserved addresses
    :param url: url supplied by the user
    :param request_host: host (with port, if any) the current request was sent to
    :returns MechanicApiTarget (rebuilt url + validated addresses), or None
        when the url is not allowed
    """
    try:
        parsed = urlparse(url)
        hostname = parsed.hostname
        port = parsed.port  # raises ValueError for an invalid port
    except ValueError:
        return None
    scheme = ALLOWED_SCHEMES.get(parsed.scheme)
    if scheme is None or not hostname:
        return None
    if parsed.username or parsed.password:
        return None
    hostname = hostname.lower()
    path_segments = parsed.path.split("/")
    if ".." in path_segments or "." in path_segments:
        return None

    allowed_hosts = getattr(settings, "MECHANIC_API_ALLOWED_HOSTS", [])
    matched_host = next((host for host in allowed_hosts if host == hostname), None)
    if matched_host is not None:
        netloc_host = f"[{matched_host}]" if ":" in matched_host else matched_host
        netloc = netloc_host if port is None else f"{netloc_host}:{port}"
    elif (
        request_host
        and parsed.netloc.lower() == request_host.lower()
        and parsed.path.rstrip("/").endswith(RECEIVE_REPORT_PATH_SUFFIX)
    ):
        # own receive_report endpoint: host already validated by Django
        # against ALLOWED_HOSTS when request.get_host() was evaluated
        netloc = request_host
    else:
        return None

    try:
        addr_infos = socket.getaddrinfo(hostname, port)
    except (socket.gaierror, UnicodeError):
        # unresolvable: no validated address, the call fails with a
        # connection error (see call_mechanic_api)
        addr_infos = []
    validated_addresses = []
    for addr_info in addr_infos:
        try:
            address = ipaddress.ip_address(addr_info[4][0].split("%")[0])
        except ValueError:
            return None
        if _is_blocked_address(address):
            return None
        if str(address) not in validated_addresses:
            validated_addresses.append(str(address))

    safe_path = quote(parsed.path, safe=URL_SAFE_CHARS)
    safe_query = quote(parsed.query, safe=URL_SAFE_CHARS + "?")
    return MechanicApiTarget(
        urlunsplit((scheme, netloc, safe_path, safe_query, "")),
        tuple(validated_addresses),
    )


# the only fields of the contact mechanic request that are sent on to the
# mechanic api (they match what ReceiveReportSerializer reads)
MECHANIC_API_FORWARDED_PARAMS = ("mechanic_code", "problem_details", "vin", "owner_id")


def build_mechanic_api_query(validated_data):
    """
    builds the outbound query parameters from an explicit allowlist of
    serializer validated fields; control fields (mechanic_api,
    repeat_request_if_failed, number_of_repeats) and unknown keys are never sent
    :param validated_data: validated data of ContactMechanicSerializer
    :returns new dict of allowlisted query parameters
    """
    return {
        key: str(validated_data[key])
        for key in MECHANIC_API_FORWARDED_PARAMS
        if key in validated_data
    }


class PinnedHostAdapter(HTTPAdapter):
    """
    HTTPS adapter for a request whose url already carries the validated ip
    address instead of the host name. SNI and certificate host name checking
    keep using the original host name, so TLS verification is unchanged.
    """

    def __init__(self, tls_hostname, *args, **kwargs):
        # must be set before HTTPAdapter.__init__, which builds the pool manager
        self._tls_hostname = tls_hostname
        super().__init__(*args, **kwargs)

    def init_poolmanager(self, connections, maxsize, block=False, **pool_kwargs):
        if self._tls_hostname:
            pool_kwargs["server_hostname"] = self._tls_hostname
            pool_kwargs["assert_hostname"] = self._tls_hostname
        super().init_poolmanager(connections, maxsize, block=block, **pool_kwargs)


def call_mechanic_api(target, query, authorization_header):
    """
    single place that performs the outbound mechanic api call. The connection
    is made to an ip address validated by build_mechanic_api_url (the host name
    is not resolved again, which closes the DNS rebinding window between the
    address check and the request); the Host header, SNI and certificate
    verification keep using the original host name.
    :param target: MechanicApiTarget returned by build_mechanic_api_url
    :param query: query parameters built by build_mechanic_api_query
    :param authorization_header: header value built by build_authorization_header
    :returns requests response; redirects are never followed and TLS is verified
    :raises requests.exceptions.ConnectionError: no validated address, or none
        of the validated addresses could be connected to
    """
    parts = urlsplit(target.url)
    hostname = parts.hostname
    last_error = requests.exceptions.ConnectionError(messages.COULD_NOT_CONNECT)
    for address in target.addresses:
        address_host = f"[{address}]" if ":" in address else address
        pinned_netloc = (
            address_host if parts.port is None else f"{address_host}:{parts.port}"
        )
        pinned_url = urlunsplit(
            (parts.scheme, pinned_netloc, parts.path, parts.query, "")
        )
        with requests.Session() as session:
            # connect directly to the validated address (no env proxies)
            session.trust_env = False
            session.mount(
                parts.scheme + "://",
                PinnedHostAdapter(hostname if parts.scheme == "https" else None),
            )
            try:
                return session.get(
                    pinned_url,
                    params=query,
                    headers={
                        "Authorization": authorization_header,
                        "Host": parts.netloc,
                    },
                    verify=settings.TLS_CA_BUNDLE,
                    allow_redirects=False,
                )
            except requests.exceptions.ConnectionError as error:
                last_error = error
    raise last_error


class ContactMechanicView(APIView):
    """
    View for contact mechanic feature
    """

    @jwt_auth_required
    def post(self, request, user=None):
        """
        contact_mechanic view to call the mechanic api
        :param request: http request for the view
            method allowed: POST
            http request should be authorised by the jwt token of the user
            mandatory fields: ['mechanic_api']
        :param user: User object of the requesting user
        :returns Response object with
            response_from_mechanic_api and 200 status if no error
            message and corresponding status if error
        """
        request_data = request.data
        serializer = ContactMechanicSerializer(data=request_data)
        if not serializer.is_valid():
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                serializer.errors,
            )
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        repeat_request_if_failed = request_data.get("repeat_request_if_failed", False)
        number_of_repeats = request_data.get("number_of_repeats", 1)
        if repeat_request_if_failed and number_of_repeats < 1:
            return Response(
                {"message": messages.MIN_NO_OF_REPEATS_FAILED},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )
        elif repeat_request_if_failed and number_of_repeats > 100:
            return Response(
                {"message": messages.NO_OF_REPEATS_EXCEEDED},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        outbound_target = build_mechanic_api_url(
            request_data["mechanic_api"], request.get_host()
        )
        if outbound_target is None:
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.COULD_NOT_CONNECT,
            )
            return Response(
                {"message": messages.COULD_NOT_CONNECT},
                status=status.HTTP_400_BAD_REQUEST,
            )

        authorization_header = build_authorization_header(
            request.META.get("HTTP_AUTHORIZATION")
        )
        if authorization_header is None:
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.COULD_NOT_CONNECT,
            )
            return Response(
                {"message": messages.COULD_NOT_CONNECT},
                status=status.HTTP_400_BAD_REQUEST,
            )

        outbound_query = build_mechanic_api_query(serializer.validated_data)
        repeat_count = 0
        while True:
            logger.info(f"Repeat count: {repeat_count}, mechanic_api: {outbound_target.url}")
            try:
                mechanic_response = call_mechanic_api(
                    outbound_target, outbound_query, authorization_header
                )
                if mechanic_response.status_code == status.HTTP_200_OK:
                    logger.info(f"Got a valid response at repeat count: {repeat_count}")
                    break
                if not repeat_request_if_failed:
                    break
                if repeat_count == number_of_repeats:
                    break
                repeat_count += 1
            except (MissingSchema, InvalidURL) as e:
                log_error(request.path, request.data, status.HTTP_400_BAD_REQUEST, e)
                return Response({"message": str(e)}, status=status.HTTP_400_BAD_REQUEST)
            except requests.exceptions.ConnectionError as e:
                if not repeat_request_if_failed:
                    return Response(
                        {"message": messages.COULD_NOT_CONNECT},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                if repeat_count == number_of_repeats:
                    return Response(
                        {"message": messages.COULD_NOT_CONNECT},
                        status=status.HTTP_400_BAD_REQUEST,
                    )
                repeat_count += 1
                continue
        mechanic_response_status = mechanic_response.status_code
        try:
            mechanic_response = mechanic_response.json()
        except ValueError:
            mechanic_response = mechanic_response.text
        return Response(
            {
                "response_from_mechanic_api": mechanic_response,
                "status": mechanic_response_status,
            },
            status=mechanic_response_status,
        )


class UserServiceCommentView(APIView):
    """
    View to add a comment to a service request
    """

    @jwt_auth_required
    def get(self, request, user=None, service_request_id=None):
        """
        get all comments for a service request
        """
        service_request = ServiceRequest.objects.get(id=service_request_id)
        if not service_request:
            return Response(
                {"message": messages.NO_OBJECT_FOUND},
                status=status.HTTP_404_NOT_FOUND,
            )
        if service_request.vehicle.owner.id != user.id:
            return Response(
                {"message": messages.NO_OBJECT_FOUND},
                status=status.HTTP_404_NOT_FOUND,
            )
        comments = ServiceComment.objects.filter(service_request=service_request)
        serializer = ServiceCommentViewSerializer(comments, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)


class UserServiceRequestsView(APIView, LimitOffsetPagination):
    """
    View to return all the service requests
    """

    def __init__(self):
        super(UserServiceRequestsView, self).__init__()
        self.default_limit = settings.DEFAULT_LIMIT

    def get(self, request, vin: str):
        """
        fetch all service requests assigned to the particular mechanic
        :param request: http request for the view
            method allowed: GET
            http request should be authorised by the jwt token of the mechanic
        :param user: User object of the requesting user
        :returns Response object with
            list of service request object and 200 status if no error
            message and corresponding status if error
        """

        service_requests = ServiceRequest.objects.filter(vehicle__vin=vin).order_by(
            "-created_on"
        )
        paginated = self.paginate_queryset(service_requests, request)
        if paginated is None:
            return Response(
                {"message": messages.NO_OBJECT_FOUND},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = UserServiceRequestSerializer(service_requests, many=True)
        response_data = dict(
            service_requests=serializer.data,
            next_offset=(
                self.offset + self.limit
                if self.offset + self.limit < self.count
                else None
            ),
            previous_offset=(
                self.offset - self.limit if self.offset - self.limit >= 0 else None
            ),
            count=self.get_count(paginated),
        )
        return Response(response_data, status=status.HTTP_200_OK)

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
import socket
from urllib.parse import quote, urlparse, urlunsplit
import requests
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


def is_safe_header_value(value):
    """
    True when value can be forwarded in an HTTP header: it must be a string
    (or absent) and must not contain CR, LF or any other control character
    that could split the header / inject additional headers
    :param value: candidate header value
    :returns bool
    """
    if value is None:
        return True
    if not isinstance(value, str):
        return False
    for char in value:
        code_point = ord(char)
        if (code_point < 32 and char != "\t") or code_point == 127:
            return False
    return True


ALLOWED_SCHEMES = {"http": "http", "https": "https"}
# characters kept as-is when re-encoding the path / query of the outbound url
URL_SAFE_CHARS = "/%:@!$&'()*+,;=-._~"


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
    :returns rebuilt url string, or None when the url is not allowed
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
        # unresolvable: let the request fail with a connection error
        addr_infos = []
    for addr_info in addr_infos:
        try:
            address = ipaddress.ip_address(addr_info[4][0].split("%")[0])
        except ValueError:
            return None
        if _is_blocked_address(address):
            return None

    safe_path = quote(parsed.path, safe=URL_SAFE_CHARS)
    safe_query = quote(parsed.query, safe=URL_SAFE_CHARS + "?")
    return urlunsplit((scheme, netloc, safe_path, safe_query, ""))


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

        outbound_url = build_mechanic_api_url(
            request_data["mechanic_api"], request.get_host()
        )
        if outbound_url is None:
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

        authorization_header = request.META.get("HTTP_AUTHORIZATION")
        if not is_safe_header_value(authorization_header):
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

        repeat_count = 0
        while True:
            logger.info(f"Repeat count: {repeat_count}, mechanic_api: {outbound_url}")
            try:
                mechanic_response = requests.get(
                    outbound_url,
                    params=request_data,
                    headers={"Authorization": authorization_header},
                    verify=settings.TLS_CA_BUNDLE,
                    allow_redirects=False,
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

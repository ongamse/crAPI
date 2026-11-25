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
from urllib.parse import urlparse
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


def resolves_to_safe_address(hostname, port):
    """
    checks that an (already allowlisted) host does not resolve to a link-local
    (cloud metadata), multicast or unspecified address
    :param hostname: allowlisted hostname
    :param port: port that will be connected to
    :returns True if the host may be requested, False otherwise
    """
    try:
        addr_infos = socket.getaddrinfo(hostname, port)
    except socket.gaierror:
        # cannot resolve: the request itself will fail to connect
        return True
    for addr_info in addr_infos:
        ip = ipaddress.ip_address(addr_info[4][0].split("%")[0])
        if ip.is_link_local or ip.is_multicast or ip.is_unspecified:
            return False
    return True


SAFE_HEADER_VALUE_PATTERN = re.compile(r"[\x20-\x7e]+")


def get_safe_authorization_header(request):
    """
    returns the request's Authorization header value if it is safe to forward
    in an outbound http header (printable ASCII only, so no CR/LF or other
    control characters), None otherwise
    :param request: http request for the view
    :returns the header value or None if missing / unsafe
    """
    value = request.META.get("HTTP_AUTHORIZATION")
    if not isinstance(value, str) or not SAFE_HEADER_VALUE_PATTERN.fullmatch(value):
        return None
    return value


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

        # Validate the user supplied url and rebuild the outbound url from the
        # allowlisted host (from settings) plus the validated port/path/query.
        request_url = None
        mechanic_api = request_data["mechanic_api"]
        if isinstance(mechanic_api, str):
            try:
                parsed_api = urlparse(mechanic_api)
                api_hostname = (parsed_api.hostname or "").lower()
                api_port = parsed_api.port
            except ValueError:
                parsed_api = None
            if (
                parsed_api is not None
                and parsed_api.scheme in ("http", "https")
                and parsed_api.username is None
                and parsed_api.password is None
                and (parsed_api.path == "" or parsed_api.path.startswith("/"))
                and api_hostname in settings.MECHANIC_API_ALLOWED_HOSTS
            ):
                allowed_host = settings.MECHANIC_API_ALLOWED_HOSTS[
                    settings.MECHANIC_API_ALLOWED_HOSTS.index(api_hostname)
                ]
                api_scheme = "https" if parsed_api.scheme == "https" else "http"
                effective_port = api_port or (443 if api_scheme == "https" else 80)
                if resolves_to_safe_address(allowed_host, effective_port):
                    netloc = allowed_host
                    if api_port:
                        netloc = f"{netloc}:{int(api_port)}"
                    request_url = f"{api_scheme}://{netloc}{parsed_api.path}"
                    if parsed_api.query:
                        request_url = f"{request_url}?{parsed_api.query}"
        if request_url is None:
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.INVALID_MECHANIC_API,
            )
            return Response(
                {"message": messages.INVALID_MECHANIC_API},
                status=status.HTTP_400_BAD_REQUEST,
            )

        authorization = get_safe_authorization_header(request)
        if authorization is None:
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.INVALID_TOKEN,
            )
            return Response(
                {"message": messages.INVALID_TOKEN},
                status=status.HTTP_400_BAD_REQUEST,
            )

        repeat_count = 0
        while True:
            logger.info(f"Repeat count: {repeat_count}, mechanic_api: {request_url}")
            try:
                mechanic_response = requests.get(
                    request_url,
                    params=request_data,
                    allow_redirects=False,
                    headers={"Authorization": authorization},
                    verify=True,
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

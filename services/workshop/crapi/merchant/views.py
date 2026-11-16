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


MECHANIC_RECEIVE_REPORT_PATHS = (
    "/workshop/api/mechanic/receive_report",
    "/api/mechanic/receive_report",
)


def is_allowed_mechanic_api_url(url, request_host=None):
    """
    Check that the user supplied mechanic api url is safe to request (SSRF guard).
    Only http/https urls are accepted. The host must either be listed in
    settings.MECHANIC_API_ALLOWED_HOSTS, resolve exclusively to publicly
    routable addresses, or (for non-public hosts) be the host this request was
    addressed to with the app's own mechanic receive_report path (the default
    web client flow). Link-local/metadata/unspecified/multicast addresses are
    always rejected unless explicitly listed in the settings allowlist.
    :param url: url supplied by the user
    :param request_host: host (optionally with port) of the incoming request
    :returns True if the url may be requested, False otherwise
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
    if hostname.lower() in settings.MECHANIC_API_ALLOWED_HOSTS:
        return True
    try:
        addresses = socket.getaddrinfo(hostname, port or None, proto=socket.IPPROTO_TCP)
    except (socket.gaierror, UnicodeError):
        return False
    if not addresses:
        return False
    ip_addresses = []
    for address in addresses:
        ip_address = ipaddress.ip_address(address[4][0].split("%")[0])
        if getattr(ip_address, "ipv4_mapped", None):
            ip_address = ip_address.ipv4_mapped
        ip_addresses.append(ip_address)
    # Never allow link-local (cloud metadata), unspecified, multicast or reserved
    # addresses, regardless of any other exemption below.
    if any(
        ip.is_link_local or ip.is_unspecified or ip.is_multicast or ip.is_reserved
        for ip in ip_addresses
    ):
        return False
    if all(ip.is_global for ip in ip_addresses):
        return True
    # Non-public host: only the app's own mechanic receive_report endpoint on
    # the host the request was addressed to (default web client flow).
    if request_host and parsed.path.rstrip("/") in MECHANIC_RECEIVE_REPORT_PATHS:
        try:
            own_hostname = urlparse("//" + request_host).hostname
        except ValueError:
            own_hostname = None
        return bool(own_hostname) and own_hostname.lower() == hostname.lower()
    return False


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

        if not is_allowed_mechanic_api_url(
            request_data["mechanic_api"], request.get_host()
        ):
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.MECHANIC_API_NOT_ALLOWED,
            )
            return Response(
                {"message": messages.MECHANIC_API_NOT_ALLOWED},
                status=status.HTTP_400_BAD_REQUEST,
            )

        repeat_count = 0
        while True:
            request_url = request_data["mechanic_api"]
            logger.info(f"Repeat count: {repeat_count}, mechanic_api: {request_url}")
            try:
                mechanic_response = requests.get(
                    request_url,
                    params=request_data,
                    headers={"Authorization": request.META.get("HTTP_AUTHORIZATION")},
                    verify=False,
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

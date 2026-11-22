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
import logging
import re
import requests
from urllib.parse import urlparse
from requests.auth import AuthBase
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

# the only endpoint the contact mechanic feature calls on an allowlisted host
MECHANIC_API_RECEIVE_REPORT_PATH = "/workshop/api/mechanic/receive_report"


def is_allowed_mechanic_api(url):
    """
    checks that the mechanic api url is http(s) and points to an allowlisted host
    :param url: url supplied by the requester
    :returns True if the url may be requested by the server, else False
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
    allowed_hosts = settings.MECHANIC_API_ALLOWED_HOSTS
    hostname = hostname.lower()
    return hostname in allowed_hosts or (
        port is not None and f"{hostname}:{port}" in allowed_hosts
    )


def resolve_mechanic_api_url(url):
    """
    resolves the url the server will call from the server-side configuration.
    The client supplied url is only used to select an entry of
    settings.MECHANIC_API_BASE_URLS (by its scheme://host[:port] origin); the
    returned url is the configured base url plus the fixed receive_report path.
    :param url: url supplied by the requester
    :returns configured url to request, or None if the url is not allowed
    """
    if not is_allowed_mechanic_api(url):
        return None
    parsed = urlparse(url.strip())
    origin = f"{parsed.scheme}://{parsed.netloc}".lower()
    base_url = settings.MECHANIC_API_BASE_URLS.get(origin)
    if base_url is None:
        return None
    return base_url + MECHANIC_API_RECEIVE_REPORT_PATH


# strict shape of the Authorization header the workshop accepts: a Bearer token
# made only of JWT (base64url + dot separated) characters, plus '@' and '+' so
# that the email based mock tokens used by the test suite are accepted
BEARER_AUTHORIZATION_PATTERN = re.compile(r"Bearer ([A-Za-z0-9._~+/=@-]{1,4096})")


def extract_bearer_token(value):
    """
    extracts the validated token part of the incoming Authorization header,
    so the raw request value is never forwarded (no CR/LF or other control
    characters can pass through)
    :param value: Authorization header value taken from the incoming request
    :returns the matched token, or None if the value is not a well formed
        Bearer authorization header
    """
    if not isinstance(value, str):
        return None
    match = BEARER_AUTHORIZATION_PATTERN.fullmatch(value)
    if match is None:
        return None
    return match.group(1)


class BearerTokenAuth(AuthBase):
    """
    requests auth handler that sets the Authorization header of the outbound
    request from an already validated bearer token
    """

    def __init__(self, token):
        self.token = token

    def __call__(self, r):
        r.headers["Authorization"] = "Bearer " + self.token
        return r


# the only fields the mechanic receive_report endpoint reads
# (see crapi.mechanic.serializers.ReceiveReportSerializer)
RECEIVE_REPORT_FIELDS = ("mechanic_code", "problem_details", "vin", "owner_id")


def build_receive_report_params(data):
    """
    builds the query params forwarded to the mechanic receive_report endpoint
    from the allowlisted fields only
    :param data: request body of the contact mechanic request
    :returns dict of allowlisted field names to str values
    """
    return {
        field: str(data[field])
        for field in RECEIVE_REPORT_FIELDS
        if field in data and data[field] is not None
    }


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

        request_url = resolve_mechanic_api_url(request_data["mechanic_api"])
        if request_url is None:
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

        token = extract_bearer_token(request.META.get("HTTP_AUTHORIZATION"))
        if token is None:
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                messages.INVALID_AUTHORIZATION_HEADER,
            )
            return Response(
                {"message": messages.INVALID_AUTHORIZATION_HEADER},
                status=status.HTTP_400_BAD_REQUEST,
            )

        report_params = build_receive_report_params(request_data)
        repeat_count = 0
        while True:
            logger.info(f"Repeat count: {repeat_count}, mechanic_api: {request_url}")
            try:
                mechanic_response = requests.get(
                    request_url,
                    params=report_params,
                    auth=BearerTokenAuth(token),
                    verify=settings.REQUESTS_VERIFY,
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

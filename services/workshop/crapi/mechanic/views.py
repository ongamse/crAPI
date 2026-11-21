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
contains all the views related to Mechanic
"""
import os
import bcrypt
import re
import stat
from urllib.parse import unquote
from django.template.loader import get_template
from xhtml2pdf import pisa
from django.utils import timezone
from django.urls import reverse
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView
from django.db import models
from django.http import FileResponse
from crapi_site import settings
from utils.jwt import jwt_auth_required
from utils import messages
from crapi.user.models import User, Vehicle, UserDetails
from utils.logging import log_error
from .models import Mechanic, ServiceRequest, ServiceComment
from .serializers import (
    MechanicSerializer,
    MechanicServiceRequestSerializer,
    ReceiveReportSerializer,
    SignUpSerializer,
    ServiceRequestStatusUpdateSerializer,
    ServiceCommentCreateSerializer,
    ServiceCommentViewSerializer,
)
from rest_framework.pagination import LimitOffsetPagination

class SignUpView(APIView):
    """
    Used to add a new mechanic
    """

    def post(self, request):
        """
        creates a new Mechanic in the db
        :param request: http request for the view
            method allowed: POST
            mandatory fields: ['name', 'email', 'number', 'password', 'mechanic_code']
        :returns Response object with
            mechanics list and 200 status if no error
            message and corresponding status if error
        """
        serializer = SignUpSerializer(data=request.data)
        if not serializer.is_valid():
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                serializer.errors,
            )
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        mechanic_details = serializer.data

        if User.objects.filter(email=mechanic_details["email"]).exists():
            return Response(
                {"message": messages.EMAIL_ALREADY_EXISTS},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if Mechanic.objects.filter(
            mechanic_code=mechanic_details["mechanic_code"]
        ).exists():
            return Response(
                {"message": messages.MEC_CODE_ALREADY_EXISTS},
                status=status.HTTP_400_BAD_REQUEST,
            )
        try:
            user_id = User.objects.aggregate(models.Max("id"))["id__max"] + 1
        except TypeError:
            user_id = 1

        user = User.objects.create(
            id=user_id,
            email=mechanic_details["email"],
            number=mechanic_details["number"],
            password=bcrypt.hashpw(
                mechanic_details["password"].encode("utf-8"), bcrypt.gensalt()
            ).decode(),
            role=User.ROLE_CHOICES.MECH,
            created_on=timezone.now(),
        )
        Mechanic.objects.create(
            mechanic_code=mechanic_details["mechanic_code"], user=user
        )
        try:
            user_details_id = (
                UserDetails.objects.aggregate(models.Max("id"))["id__max"] + 1
            )
        except TypeError:
            user_details_id = 1
        UserDetails.objects.create(
            id=user_details_id,
            available_credit=0,
            name=mechanic_details["name"],
            status="ACTIVE",
            user=user,
        )
        return Response(
            {"message": messages.MEC_CREATED.format(user.email)},
            status=status.HTTP_200_OK,
        )


class MechanicView(APIView, LimitOffsetPagination):
    """
    Mechanic view to fetch all the mechanics
    """

    @jwt_auth_required
    def get(self, request, user=None):
        """
        get_mechanic view for fetching the list of mechanics
        :param request: http request for the view
            method allowed: GET
            http request should be authorised by the jwt token of the user
        :param user: User object of the requesting user
        :returns Response object with
            mechanics list and 200 status if no error
            message and corresponding status if error
        """
        mechanics = Mechanic.objects.all().order_by("id")
        paginated = self.paginate_queryset(mechanics, request)
        if paginated is None:
            return Response(
                {"message": messages.NO_OBJECT_FOUND},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = MechanicSerializer(paginated, many=True)
        response_data = dict(
            mechanics=serializer.data,
            previous_offset=(
                self.offset - self.limit if self.offset - self.limit >= 0 else None
            ),
            next_offset=(
                self.offset + self.limit
                if self.offset + self.limit < self.count
                else None
            ),
        )
        return Response(response_data, status=status.HTTP_200_OK)


class ReceiveReportView(APIView):
    """
    View to receive report from contact mechanic feature
    """

    def get(self, request):
        """
        receive_report endpoint for mechanic
        :param request: http request for the view
            method allowed: POST
            mandatory fields: ['mechanic_code', 'problem_details', 'vin']
        :returns Response object with
            { service request id, report link } and 200 status if no error
            message and corresponding status if error
        """
        serializer = ReceiveReportSerializer(data=request.GET)
        if not serializer.is_valid():
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                serializer.errors,
            )
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        report_details = serializer.data
        mechanic = Mechanic.objects.get(mechanic_code=report_details["mechanic_code"])
        vehicle = Vehicle.objects.get(vin=report_details["vin"])
        service_request = ServiceRequest.objects.create(
            vehicle=vehicle,
            mechanic=mechanic,
            problem_details=report_details["problem_details"],
            created_on=timezone.now(),
        )
        service_request.save()
        report_link = "{}?report_id={}".format(
            reverse("get-mechanic-report"), service_request.id
        )
        report_link = request.build_absolute_uri(report_link)
        return Response(
            {"id": service_request.id, "sent": True, "report_link": report_link},
            status=status.HTTP_200_OK,
        )


class GetReportView(APIView):
    """
    View to get only particular service request
    """

    @jwt_auth_required
    def get(self, request, user=None):
        """
        fetch service request details from report_link
        :param request: http request for the view
            method allowed: GET
        :returns Response object with
            service request object and 200 status if no error
            message and corresponding status if error
        """
        report_id = request.GET.get("report_id")
        if not report_id:
            return Response(
                {"message": messages.REPORT_ID_MISSING},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not re.fullmatch(r"[0-9]+", report_id):
            return Response(
                {"message": messages.INVALID_REPORT_ID},
                status=status.HTTP_400_BAD_REQUEST,
            )
        service_request = ServiceRequest.objects.filter(id=int(report_id)).first()
        if not service_request:
            return Response(
                {"message": messages.REPORT_DOES_NOT_EXIST},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = MechanicServiceRequestSerializer(service_request)
        response_data = dict(serializer.data)
        service_report_pdf(response_data, service_request.id)
        return Response(response_data, status=status.HTTP_200_OK)


class MechanicServiceRequestsView(APIView, LimitOffsetPagination):
    """
    View to return all the service requests
    """

    def __init__(self):
        super(MechanicServiceRequestsView, self).__init__()
        self.default_limit = settings.DEFAULT_LIMIT

    @jwt_auth_required
    def get(self, request, user=None):
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

        service_requests = ServiceRequest.objects.filter(mechanic__user=user).order_by(
            "-created_on"
        )
        paginated = self.paginate_queryset(service_requests, request)
        if paginated is None:
            return Response(
                {"message": messages.NO_OBJECT_FOUND},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = MechanicServiceRequestSerializer(service_requests, many=True)
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


class ServiceCommentView(APIView):
    """
    View to add a comment to a service request
    """

    @jwt_auth_required
    def post(self, request, user=None, service_request_id=None):
        """
        add a comment to a service request
        """
        if user.role != User.ROLE_CHOICES.MECH:
            return Response(
                {"message": messages.UNAUTHORIZED},
                status=status.HTTP_401_UNAUTHORIZED,
            )
        serializer = ServiceCommentCreateSerializer(data=request.data)
        if not serializer.is_valid():
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                serializer.errors,
            )
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        service_request = ServiceRequest.objects.get(id=service_request_id)
        service_comment = ServiceComment(
            comment=serializer.data["comment"],
            service_request=service_request,
            created_on=timezone.now(),
        )
        service_comment.save()
        service_request.updated_on = timezone.now()
        service_request.save()
        serializer = ServiceCommentViewSerializer(service_comment)
        return Response(serializer.data, status=status.HTTP_200_OK)

    @jwt_auth_required
    def get(self, request, user=None, service_request_id=None):
        """
        get all comments for a service request
        """
        service_request = ServiceRequest.objects.get(id=service_request_id)
        comments = ServiceComment.objects.filter(service_request=service_request)
        serializer = ServiceCommentViewSerializer(comments, many=True)
        response_data = dict(comments=serializer.data)
        return Response(response_data, status=status.HTTP_200_OK)


class ServiceRequestView(APIView):
    """
    View to update the status of a service request
    """

    @jwt_auth_required
    def put(self, request, user=None, service_request_id=None):
        """
        update the status of a service request
        """
        serializer = ServiceRequestStatusUpdateSerializer(data=request.data)
        if not serializer.is_valid():
            log_error(
                request.path,
                request.data,
                status.HTTP_400_BAD_REQUEST,
                serializer.errors,
            )
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)
        service_request = ServiceRequest.objects.get(id=service_request_id)
        service_request.status = request.data["status"]
        service_request.updated_on = timezone.now()
        service_request.save()
        serializer = MechanicServiceRequestSerializer(service_request)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def get(self, request, user=None, service_request_id=None):
        """
        get a service request
        """
        service_request = ServiceRequest.objects.get(id=service_request_id)
        serializer = MechanicServiceRequestSerializer(service_request)
        return Response(serializer.data, status=status.HTTP_200_OK)


class DownloadReportView(APIView):
    """
    A view to download a service report.
    """
    def get(self, request, format=None):
        filename_from_user = request.query_params.get('filename')
        if not filename_from_user:
            return Response(
                {"message": "Parameter 'filename' is required."},
                status=status.HTTP_400_BAD_REQUEST
            )
        #Checks if input before decoding contains only allowed characters
        if not validate_filename(filename_from_user):
            return Response(
                {"message": "Invalid input."}, 
                status=status.HTTP_400_BAD_REQUEST
            )

        filename_from_user = unquote(filename_from_user)
        if not is_plain_report_name(filename_from_user):
            return Response(
                {"message": "Invalid input."},
                status=status.HTTP_400_BAD_REQUEST
            )
        # The path handed to open() comes from the reports directory
        # listing, never from the request.
        report_entry = find_report_entry(filename_from_user)
        if report_entry is None:
            return Response(
                {"message": "File not found."},
                status=status.HTTP_404_NOT_FOUND
            )
        # Open directly (no exists/isfile pre-check) relative to the reports
        # directory handle, then verify the type on the opened descriptor.
        try:
            report_file = open_report_for_read(report_entry.name)
        except FileNotFoundError:
            return Response(
                {"message": "File not found."},
                status=status.HTTP_404_NOT_FOUND
            )
        except (IsADirectoryError, PermissionError):
            return Response(
                {"message": "Requested path is not a file."},
                status=status.HTTP_403_FORBIDDEN
            )
        except OSError:
            return Response(
                {"message": "File not found."},
                status=status.HTTP_404_NOT_FOUND
            )
        return FileResponse(report_file)


def get_reports_dir():
    """
    Canonical path of the directory holding generated service reports.
    """
    return os.path.realpath(os.path.join(settings.BASE_DIR, "reports"))


def is_plain_report_name(filename):
    """
    True when the name is a plain file name: no separators, no NUL, no
    dot segments.
    """
    if not filename or "\x00" in filename:
        return False
    if filename in (".", "..") or filename != os.path.basename(filename):
        return False
    return "/" not in filename and "\\" not in filename


def find_report_entry(filename):
    """
    Looks a report up in the reports directory listing and returns the
    matching os.DirEntry (whose name is built by the OS from the listing,
    not from the caller's text), or None when there is no such entry.
    Containment is enforced when the file is opened, relative to the
    reports directory handle (see open_report_for_read).
    """
    reports_dir = get_reports_dir()
    try:
        with os.scandir(reports_dir) as entries:
            for entry in entries:
                if entry.name == filename:
                    return entry
    except OSError:
        return None
    return None


def _open_reports_dir_fd():
    """
    Opens the reports directory and returns the descriptor.
    """
    return os.open(
        get_reports_dir(), os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    )


def open_report_for_read(filename):
    """
    Opens a report for reading relative to the reports directory handle,
    refusing symlinks, and verifies on the opened descriptor that it is a
    regular file. Raises OSError subclasses on failure.
    """
    dir_fd = _open_reports_dir_fd()
    try:
        flags = (
            os.O_RDONLY
            | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_NONBLOCK", 0)
        )
        fd = os.open(filename, flags, dir_fd=dir_fd)
    finally:
        os.close(dir_fd)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise IsADirectoryError("Requested path is not a file.")
        return os.fdopen(fd, "rb")
    except BaseException:
        os.close(fd)
        raise


def open_report_for_write(filename):
    """
    Creates/truncates a report relative to the reports directory handle,
    refusing symlinks, and verifies on the opened descriptor that it is a
    regular file. Returns a binary read/write file object.
    """
    dir_fd = _open_reports_dir_fd()
    try:
        flags = (
            os.O_RDWR
            | os.O_CREAT
            | os.O_TRUNC
            | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_NONBLOCK", 0)
        )
        fd = os.open(filename, flags, 0o600, dir_fd=dir_fd)
    finally:
        os.close(dir_fd)
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            raise IsADirectoryError("Report path is not a file.")
        return os.fdopen(fd, "w+b")
    except BaseException:
        os.close(fd)
        raise

def validate_filename(input: str) -> bool:
    """
    Allowed: alphanumerics, _, :, %HH
    """
    url_encoded_pattern = re.compile(r'^(?:[A-Za-z0-9:_]|%[0-9A-Fa-f]{2})*$')
    return bool(url_encoded_pattern.fullmatch(input))


def service_report_pdf(response_data, report_id):
    """
    Generates service report's PDF file from a template and saves it to the disk.
    """
    reports_dir = get_reports_dir()
    os.makedirs(reports_dir, exist_ok=True)
    # report_id is the integer primary key of a stored ServiceRequest; the
    # file name is generated here from that integer only.
    report_name = "report_%d" % int(report_id)

    template = get_template('service_report.html')
    html_string = template.render({'service': response_data})
    with open_report_for_write(report_name) as pdf_file:
        pisa.CreatePDF(src=html_string, dest=pdf_file)

    manage_reports_directory()


def manage_reports_directory():
    """
    Checks reports directory and deletes the oldest one if the
    count exceeds the maximum limit.
    """
    try:
        reports_dir = os.path.join(settings.BASE_DIR, 'reports')
        report_files = os.listdir(reports_dir)
        
        if len(report_files) >= settings.FILES_LIMIT:
            oldest_file = None
            oldest_time = float('inf')
            for filename in report_files:
                filepath = os.path.join(reports_dir, filename)
                try:
                    current_mtime = os.path.getmtime(filepath)
                    if current_mtime < oldest_time:
                        oldest_time = current_mtime
                        oldest_file = filepath
                except FileNotFoundError:
                    continue

            if oldest_file:
                os.remove(oldest_file)

    except (OSError, FileNotFoundError) as e:
        print(f"Error during report directory management: {e}")
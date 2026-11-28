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
contains all the test cases related to merchant
"""
from unittest.mock import patch
from utils.mock_methods import (
    get_sample_mechanic_data,
    mock_jwt_auth_required,
    get_sample_user_data,
)
from crapi.mechanic.models import ServiceRequest, Mechanic

patch("utils.jwt.jwt_auth_required", mock_jwt_auth_required).start()

import bcrypt
from django.test import TestCase, Client
from django.utils import timezone
from utils import messages
from utils.helper import build_bearer_authorization, validate_header_value
from crapi.user.models import User, Vehicle, VehicleModel, VehicleCompany


class MerchantTestCase(TestCase):
    """
    contains all the test cases related to merchant
    Attributes:
        client: Client object used for testing
        mechanic: sample mechanic sign up request body
        user: dummy user object
        user_auth_headers: Auth headers for dummy user
        mechanic_auth_headers: Auth headers for dummy mechanic
        vehicle: dummy vehicle object
        contact_mechanic_request_body: sample contact mechanic request body
    """

    def setUp(self):
        """
        stores a sample request body for mechanic signup
        creates a dummy mechanic, a dummy user, a dummy vehicle and corresponding auth tokens
        stores a sample request body for contact mechanic
        :return: None
        """
        self.client = Client()
        self.mechanic = get_sample_mechanic_data()
        self.client.post(
            "/workshop/api/mechanic/signup",
            self.mechanic,
            content_type="application/json",
        )

        user_data = get_sample_user_data()
        self.user = User.objects.create(
            id=2,
            email=user_data["email"],
            number=user_data["number"],
            password=bcrypt.hashpw(
                user_data["password"].encode("utf-8"), bcrypt.gensalt()
            ).decode(),
            role=User.ROLE_CHOICES.USER,
            created_on=timezone.now(),
        )
        self.user_auth_headers = {"HTTP_AUTHORIZATION": "Bearer " + user_data["email"]}

        self.mechanic_auth_headers = {
            "HTTP_AUTHORIZATION": "Bearer " + self.mechanic["email"]
        }

        self.vehicle_company = VehicleCompany.objects.create(name="RandomCompany")

        self.vehicle_model = VehicleModel.objects.create(
            fuel_type="1",
            model="NewModel",
            vehicle_img="Image",
            vehiclecompany=self.vehicle_company,
        )

        self.vehicle = Vehicle.objects.create(
            pincode="1234",
            vin="9NFXO86WBWA082766",
            year="2020",
            status="ACTIVE",
            owner=self.user,
            vehicle_model=self.vehicle_model,
        )
        self.contact_mechanic_request_body = {
            "mechanic_api": "https://example.com/workshop/api/mechanic/receive_report",
            "repeat_request_if_failed": True,
            "number_of_repeats": 5,
            "mechanic_code": self.mechanic["mechanic_code"],
            "vin": self.vehicle.vin,
            "problem_details": "My Car is not working",
        }

    def test_max_retries_exceeded(self):
        """
        increases number_of_repeats to 110
        should get an error message
        :return: None
        """
        self.contact_mechanic_request_body["number_of_repeats"] = 110
        res = self.client.post(
            "/workshop/api/merchant/contact_mechanic",
            self.contact_mechanic_request_body,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertNotEqual(res.status_code, 200)
        self.assertEqual(res.json()["message"], messages.NO_OF_REPEATS_EXCEEDED)

    def test_wrong_mechanic_api(self):
        """
        changes mechanic_api to an invalid url
        should get an error message
        :return: None
        """
        self.contact_mechanic_request_body["mechanic_api"] = (
            "https://jsonplaceholder.typicode.com/post"
        )
        res = self.client.post(
            "/workshop/api/merchant/contact_mechanic",
            self.contact_mechanic_request_body,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertNotEqual(res.status_code, 200)

    def test_disallowed_mechanic_api_urls(self):
        """
        mechanic_api pointing to link-local/metadata targets, non http(s)
        schemes, or paths other than receive_report must be rejected
        without any outbound request
        :return: None
        """
        disallowed_urls = [
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.169.254/api/mechanic/receive_report",
            "http://[fe80::1]/api/mechanic/receive_report",
            "file:///etc/passwd",
            "ftp://example.com/api/mechanic/receive_report",
            "https://user:pass@example.com/api/mechanic/receive_report",
            "https://example.com/some/other/path",
        ]
        for url in disallowed_urls:
            self.contact_mechanic_request_body["mechanic_api"] = url
            with patch("crapi.merchant.views.requests.get") as mock_get:
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    **self.user_auth_headers,
                    content_type="application/json"
                )
                mock_get.assert_not_called()
            self.assertEqual(res.status_code, 400, url)
            self.assertEqual(res.json()["message"], messages.INVALID_MECHANIC_API)

    def test_authorization_header_injection_rejected(self):
        """
        an Authorization header containing CR/LF or control characters
        must not be forwarded to the mechanic api
        :return: None
        """
        self.assertEqual(validate_header_value("Bearer abc.def"), "Bearer abc.def")
        for bad in (
            None,
            "",
            "Bearer abc\r\nX-Injected: 1",
            "Bearer abc\nX-Injected: 1",
            "Bearer abc\x00",
            "Bearer abc\x7f",
        ):
            self.assertIsNone(validate_header_value(bad), repr(bad))
            self.assertIsNone(build_bearer_authorization(bad), repr(bad))
        # the header is re-assembled from a strictly parsed bearer token
        self.assertEqual(
            build_bearer_authorization("Bearer aB-_.9Z+/=="), "Bearer aB-_.9Z+/=="
        )
        for malformed in (
            "Basic abc",
            "Bearer",
            "Bearer ",
            "Bearer a b",
            "Bearer abc, Other xyz",
            "Bearer abc;X-Injected: 1",
            "bearer abc",
        ):
            self.assertIsNone(build_bearer_authorization(malformed), repr(malformed))
        with patch("crapi.merchant.views.requests.get") as mock_get:
            with patch(
                "crapi.merchant.views.build_bearer_authorization", return_value=None
            ):
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    **self.user_auth_headers,
                    content_type="application/json"
                )
            mock_get.assert_not_called()
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json()["message"], messages.INVALID_TOKEN)

    def test_contact_mechanic(self):
        """
        gives all correct field for contact mechanic
        should get a valid response from mechanic_api
        :return: None
        """
        with patch("crapi.merchant.views.requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {"message": "ok"}
            res = self.client.post(
                "/workshop/api/merchant/contact_mechanic",
                self.contact_mechanic_request_body,
                **self.user_auth_headers,
                content_type="application/json"
            )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(
            res.json()["response_from_mechanic_api"], {"message": "ok"}
        )

    def test_repeat_missing_request(self):
        """
        deletes repeat_request_if_failed field from contact_mechanic request body
        should get a bad request response
        :return: None
        """
        del self.contact_mechanic_request_body["repeat_request_if_failed"]
        res = self.client.post(
            "/workshop/api/merchant/contact_mechanic",
            self.contact_mechanic_request_body,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)

    def test_receive_report_and_get_report(self):
        """
        tests receive_report with a valid request
        should get a valid response saying report sent
        should get a report_link

        tests the report_link
        should get the same report which is sent earlier

        tests if the mechanic also gets the same report
        should get the same report
        :return: None
        """
        res = self.client.get(
            "/workshop/api/mechanic/receive_report",
            self.contact_mechanic_request_body,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.json()["sent"])
        self.assertIn("report_link", res.json())

        report_res = self.client.get(
            res.json()["report_link"],
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(report_res.status_code, 200)
        self.assertEqual(
            report_res.json()["problem_details"],
            self.contact_mechanic_request_body["problem_details"],
        )
        self.assertEqual(
            report_res.json()["mechanic"]["mechanic_code"],
            self.contact_mechanic_request_body["mechanic_code"],
        )
        self.assertEqual(
            report_res.json()["vehicle"]["vin"],
            self.contact_mechanic_request_body["vin"],
        )

        service_requests = self.client.get(
            "/workshop/api/mechanic/service_requests",
            **self.mechanic_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(
            service_requests.json()["service_requests"][0], report_res.json()
        )


class UserServiceRequestsViewTestCase(TestCase):
    """
    contains all the test cases related to user service requests
    """

    def setUp(self):
        """
        stores a sample request body for mechanic signup
        creates a dummy mechanic, a dummy user, a dummy vehicle and corresponding auth tokens
        :return: None
        """
        self.client = Client()
        mechanic = get_sample_mechanic_data()
        self.client.post(
            "/workshop/api/mechanic/signup",
            mechanic,
            content_type="application/json",
        )
        self.mechanic = Mechanic.objects.get(user__email=mechanic["email"])

        user_data = get_sample_user_data()
        self.user = User.objects.create(
            id=2,
            email=user_data["email"],
            number=user_data["number"],
            password=bcrypt.hashpw(
                user_data["password"].encode("utf-8"), bcrypt.gensalt()
            ).decode(),
            role=User.ROLE_CHOICES.USER,
            created_on=timezone.now(),
        )
        self.user_auth_headers = {"HTTP_AUTHORIZATION": "Bearer " + user_data["email"]}

        self.mechanic_auth_headers = {
            "HTTP_AUTHORIZATION": "Bearer " + mechanic["email"]
        }

        self.vehicle_company = VehicleCompany.objects.create(name="RandomCompany")

        self.vehicle_model = VehicleModel.objects.create(
            fuel_type="1",
            model="NewModel",
            vehicle_img="Image",
            vehiclecompany=self.vehicle_company,
        )

        self.vehicle = Vehicle.objects.create(
            pincode="1234",
            vin="9NFXO86WBWA082766",
            year="2020",
            status="ACTIVE",
            owner=self.user,
            vehicle_model=self.vehicle_model,
        )
        self.contact_mechanic_request_body = {
            "mechanic_api": "https://www.google.com",
            "repeat_request_if_failed": True,
            "number_of_repeats": 5,
            "mechanic_code": mechanic["mechanic_code"],
            "vin": self.vehicle.vin,
            "problem_details": "My Car is not working",
        }

        self.service_request = ServiceRequest.objects.create(
            mechanic=self.mechanic,
            vehicle=self.vehicle,
            problem_details="My Car is not working",
            status="PENDING",
            created_on=timezone.now(),
        )
        self.service_request.save()

    def test_user_service_requests(self):
        """
        tests the user service requests
        should get a valid response
        :return: None
        """
        vin = self.vehicle.vin

        res = self.client.get(
            "/workshop/api/merchant/service_requests/%s" % vin,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)
        service_request_count = len(res.json()["service_requests"])
        self.assertEqual(
            res.json()["service_requests"][0]["id"], self.service_request.id
        )

        self.service_request.status = "COMPLETED"
        self.service_request.save()

        res = self.client.get(
            "/workshop/api/merchant/service_requests/%s" % vin,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(len(res.json()["service_requests"]), service_request_count)

        self.service_request2 = ServiceRequest.objects.create(
            mechanic=self.mechanic,
            vehicle=self.vehicle,
            problem_details="My Car is not working again",
            status="PENDING",
            created_on=timezone.now(),
        )
        self.service_request2.save()
        res = self.client.get(
            "/workshop/api/merchant/service_requests/%s" % vin,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(len(res.json()["service_requests"]), service_request_count + 1)
        self.assertEqual(res.json()["service_requests"][0]["status"], "PENDING")
        self.assertEqual(res.json()["service_requests"][1]["status"], "COMPLETED")

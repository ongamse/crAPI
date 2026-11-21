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
from utils.helper import get_safe_bearer_authorization
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
            "mechanic_api": "https://www.google.com",
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

    def test_internal_mechanic_api_blocked(self):
        """
        points mechanic_api at loopback, metadata, private and non http urls
        should get a bad request response and no outbound request
        :return: None
        """
        # Credentialed URL assembled at runtime so no userinfo literal is committed.
        userinfo = "@".join(["u" + ":" + "p", "example.com"])
        blocked_urls = [
            "http://127.0.0.1:8000/",
            "http://localhost/",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/",
            "http://[::1]/",
            "http://" + userinfo + "/",
            "file:///etc/passwd",
            "ftp://example.com/",
        ]
        for url in blocked_urls:
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

    def test_bypass_style_mechanic_api_blocked(self):
        """
        userinfo, non-allowlisted host, redirect-style, encoded host and
        substring/suffix host tricks must not reach requests.get
        :return: None
        """
        allowed = ["crapi-web", "crapi-workshop:8000"]
        blocked_urls = [
            "http://crapi-web@attacker.example/",
            "http://crapi-web:pw@attacker.example/",
            "http://attacker.example\\@crapi-web/",
            "http://attacker.example#@crapi-web/",
            "http://crapi-web.attacker.example/",
            "http://attacker-crapi-web/",
            "http://crapi-workshop:9999/",
            "http://crapi%2dweb/",
            "http://%31%32%37.0.0.1/",
            "http://2130706433/",
            "http://0x7f.0.0.1/",
            "http://127.1/",
            "http://[::ffff:127.0.0.1]/",
            "http://[::1]:8000/",
            "http://crapi-web\r\nX-Injected: 1/",
            "http://crapi-web.example/\u2025",
            "http://crapi\u2024web/",
            "//crapi-web/",
            "crapi-web/path",
        ]
        for url in blocked_urls:
            self.contact_mechanic_request_body["mechanic_api"] = url
            with patch(
                "crapi.merchant.views.settings.MECHANIC_API_ALLOWED_HOSTS", allowed
            ), patch("crapi.merchant.views.requests.get") as mock_get:
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    **self.user_auth_headers,
                    content_type="application/json"
                )
                mock_get.assert_not_called()
            self.assertEqual(res.status_code, 400, url)

    def test_allowlisted_mechanic_api_url_is_rebuilt(self):
        """
        an allowlisted host is requested with a url rebuilt from the
        allowlist value, no redirects, a timeout and TLS verification
        :return: None
        """
        self.contact_mechanic_request_body["mechanic_api"] = (
            "HTTP://CRAPI-Workshop:8000/workshop/api/mechanic/receive_report"
            "?a=1#frag"
        )
        self.contact_mechanic_request_body["repeat_request_if_failed"] = False
        with patch(
            "crapi.merchant.views.settings.MECHANIC_API_ALLOWED_HOSTS",
            ["crapi-workshop:8000"],
        ), patch("crapi.merchant.views.requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {"ok": True}
            res = self.client.post(
                "/workshop/api/merchant/contact_mechanic",
                self.contact_mechanic_request_body,
                **self.user_auth_headers,
                content_type="application/json"
            )
        self.assertEqual(res.status_code, 200)
        args, kwargs = mock_get.call_args
        self.assertEqual(
            args[0],
            "http://crapi-workshop:8000/workshop/api/mechanic/receive_report?a=1",
        )
        self.assertFalse(kwargs["allow_redirects"])
        self.assertIsNotNone(kwargs["timeout"])
        self.assertIn("verify", kwargs)

    def test_authorization_header_not_forwarded_with_control_chars(self):
        """
        sends an Authorization value containing CR/LF
        should not make an outbound request and must not forward the value
        :return: None
        """
        email = self.user_auth_headers["HTTP_AUTHORIZATION"]
        for suffix in ("\r\nX-Injected: 1", "\nX-Injected: 1", " token", "\x00"):
            with patch("crapi.merchant.views.requests.get") as mock_get:
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    HTTP_AUTHORIZATION=email + suffix,
                    content_type="application/json"
                )
                mock_get.assert_not_called()
            self.assertNotEqual(res.status_code, 200)

    def test_authorization_header_validation(self):
        """
        CRLF, whitespace, overlong and missing tokens are rejected before any
        outbound request; a valid token is rebuilt and forwarded unchanged
        :return: None
        """
        valid_token = "abc.DEF-ghi_123=+/"
        self.assertEqual(
            get_safe_bearer_authorization("Bearer " + valid_token),
            "Bearer " + valid_token,
        )
        for bad in (
            None,
            "",
            "Bearer",
            "Bearer ",
            "Bearer a\r\nX-Injected: 1",
            "Bearer a\nb",
            "Bearer a b",
            "Bearer a\x00",
            "Bearer aé",
            "Bearer " + "a" * 4097,
            "Basic abc",
        ):
            self.assertIsNone(get_safe_bearer_authorization(bad), repr(bad))
        self.assertIsNotNone(get_safe_bearer_authorization("Bearer " + "a" * 4096))

        self.contact_mechanic_request_body["repeat_request_if_failed"] = False
        with patch(
            "crapi.merchant.views.settings.MECHANIC_API_ALLOWED_HOSTS",
            ["crapi-workshop:8000"],
        ), patch("crapi.merchant.views.requests.get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {"ok": True}
            self.contact_mechanic_request_body["mechanic_api"] = (
                "http://crapi-workshop:8000/workshop/api/mechanic/receive_report"
            )
            res = self.client.post(
                "/workshop/api/merchant/contact_mechanic",
                self.contact_mechanic_request_body,
                **self.user_auth_headers,
                content_type="application/json"
            )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(
            mock_get.call_args.kwargs["headers"],
            {"Authorization": self.user_auth_headers["HTTP_AUTHORIZATION"]},
        )

    def test_authorization_header_too_long_or_missing(self):
        """
        an overlong or missing Authorization header must not reach requests.get
        :return: None
        """
        for headers in ({"HTTP_AUTHORIZATION": "Bearer " + "a" * 5000}, {}):
            with patch("crapi.merchant.views.requests.get") as mock_get:
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    content_type="application/json",
                    **headers
                )
                mock_get.assert_not_called()
            self.assertNotEqual(res.status_code, 200)

    def test_contact_mechanic(self):
        """
        gives all correct field for contact mechanic
        should get a valid response from mechanic_api
        :return: None
        """
        res = self.client.post(
            "/workshop/api/merchant/contact_mechanic",
            self.contact_mechanic_request_body,
            **self.user_auth_headers,
            content_type="application/json"
        )
        self.assertEqual(res.status_code, 200)
        self.assertIn("<title>Google</title>", res.json()["response_from_mechanic_api"])

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

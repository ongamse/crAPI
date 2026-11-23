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
import requests
from utils.mock_methods import (
    get_sample_mechanic_data,
    mock_jwt_auth_required,
    get_sample_user_data,
)
from crapi.mechanic.models import ServiceRequest, Mechanic

patch("utils.jwt.jwt_auth_required", mock_jwt_auth_required).start()

import bcrypt
from django.test import TestCase, Client, override_settings
from django.utils import timezone
from utils import messages
from crapi.user.models import User, Vehicle, VehicleModel, VehicleCompany
from crapi.merchant.views import (
    build_authorization_header,
    build_mechanic_api_query,
    build_mechanic_api_url,
    call_mechanic_api,
    MechanicApiTarget,
)


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

    def test_disallowed_mechanic_api_hosts(self):
        """
        mechanic_api pointing at hosts that are not allowlisted, metadata
        addresses, or non http(s) schemes should be rejected without a request
        :return: None
        """
        for url in [
            "http://169.254.169.254/latest/meta-data/",
            "http://localhost:8000/workshop/api/mechanic/receive_report/../../x",
            "http://127.0.0.1:22",
            "file:///etc/passwd",
            "https://" + ":".join(["userinfo", "placeholder"]) + "@www.google.com",
        ]:
            self.contact_mechanic_request_body["mechanic_api"] = url
            with patch("crapi.merchant.views.requests.Session.get") as mock_get:
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    **self.user_auth_headers,
                    content_type="application/json"
                )
                mock_get.assert_not_called()
            self.assertEqual(res.status_code, 400)
            self.assertEqual(res.json()["message"], messages.COULD_NOT_CONNECT)

    @override_settings(MECHANIC_API_ALLOWED_HOSTS=["mechanic.example.com"])
    def test_build_mechanic_api_url(self):
        """
        the outbound url is rebuilt from validated components
        :return: None
        """
        with patch(
            "crapi.merchant.views.socket.getaddrinfo",
            return_value=[(2, 1, 6, "", ("93.184.216.34", 0))],
        ):
            target = build_mechanic_api_url(
                "HTTPS://Mechanic.Example.com:8443/api/x?a=b c", "testserver"
            )
            self.assertEqual(
                target.url, "https://mechanic.example.com:8443/api/x?a=b%20c"
            )
            self.assertEqual(target.addresses, ("93.184.216.34",))
            self.assertEqual(
                build_mechanic_api_url(
                    "http://testserver/workshop/api/mechanic/receive_report/",
                    "testserver",
                ).url,
                "http://testserver/workshop/api/mechanic/receive_report/",
            )
            for url in [
                "http://mechanic.example.com/a/../b",
                "http://other.example.com/",
                "ftp://mechanic.example.com/",
                "http://mechanic.example.com:badport/",
            ]:
                self.assertIsNone(build_mechanic_api_url(url, "testserver"))
        with patch(
            "crapi.merchant.views.socket.getaddrinfo",
            return_value=[(2, 1, 6, "", ("169.254.169.254", 0))],
        ):
            self.assertIsNone(
                build_mechanic_api_url("http://mechanic.example.com/", "testserver")
            )

    def test_call_mechanic_api_pins_validated_address(self):
        """
        the request connects to the validated ip, not the host name, while
        the Host header and TLS host name stay the original host
        :return: None
        """
        target = MechanicApiTarget(
            "https://mechanic.example.com:8443/api/x?a=b", ("93.184.216.34",)
        )
        with patch("crapi.merchant.views.requests.Session.get") as mock_get:
            call_mechanic_api(target, {"vin": "v"}, "Bearer a.b.c")
        mock_get.assert_called_once()
        self.assertEqual(
            mock_get.call_args.args[0], "https://93.184.216.34:8443/api/x?a=b"
        )
        self.assertEqual(
            mock_get.call_args.kwargs["headers"],
            {"Authorization": "Bearer a.b.c", "Host": "mechanic.example.com:8443"},
        )
        self.assertFalse(mock_get.call_args.kwargs["allow_redirects"])
        self.assertEqual(mock_get.call_args.kwargs["params"], {"vin": "v"})

    def test_call_mechanic_api_ipv6_and_fail_closed(self):
        """
        ipv6 addresses are bracketed; with no validated address nothing is
        requested and a connection error is raised
        :return: None
        """
        target = MechanicApiTarget("http://mechanic.example.com/a", ("2001:db8::1",))
        with patch("crapi.merchant.views.requests.Session.get") as mock_get:
            call_mechanic_api(target, {}, "Bearer a.b.c")
        self.assertEqual(mock_get.call_args.args[0], "http://[2001:db8::1]/a")
        empty = MechanicApiTarget("http://mechanic.example.com/a", ())
        with patch("crapi.merchant.views.requests.Session.get") as mock_get:
            with self.assertRaises(requests.exceptions.ConnectionError):
                call_mechanic_api(empty, {}, "Bearer a.b.c")
            mock_get.assert_not_called()

    def test_build_authorization_header(self):
        """
        only a well formed bearer jwt is rebuilt; anything else is rejected
        :return: None
        """
        self.assertEqual(
            build_authorization_header("Bearer abc.def.ghi"), "Bearer abc.def.ghi"
        )
        self.assertEqual(
            build_authorization_header("Bearer abc-_=.def.ghi"),
            "Bearer abc-_=.def.ghi",
        )
        for value in [
            None,
            5,
            "",
            "abc.def.ghi",
            "Bearer abc",
            "Bearer a b.c.d",
            "Bearer a.b.c\r\nX-Injected: 1",
            "Bearer a.b.c\n",
            "Bearer a.b.c\x00",
        ]:
            self.assertIsNone(build_authorization_header(value))

    def test_build_mechanic_api_query(self):
        """
        only the allowlisted report fields are forwarded as query params
        :return: None
        """
        self.assertEqual(
            build_mechanic_api_query(
                {
                    "mechanic_api": "https://www.google.com",
                    "repeat_request_if_failed": True,
                    "number_of_repeats": 5,
                    "mechanic_code": "TRAC_JHN",
                    "vin": "9NFXO86WBWA082766",
                    "problem_details": "My Car is not working",
                    "attacker": "x",
                }
            ),
            {
                "mechanic_code": "TRAC_JHN",
                "vin": "9NFXO86WBWA082766",
                "problem_details": "My Car is not working",
            },
        )
        self.assertEqual(build_mechanic_api_query({"mechanic_api": "x"}), {})

    @override_settings(MECHANIC_API_ALLOWED_HOSTS=["www.google.com"])
    def test_contact_mechanic_forwards_only_allowlisted_params(self):
        """
        control fields and unknown keys of the request body are not sent
        to the mechanic api
        :return: None
        """
        self.contact_mechanic_request_body["attacker"] = "x"
        with patch("crapi.merchant.views.requests.Session.get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {}
            res = self.client.post(
                "/workshop/api/merchant/contact_mechanic",
                self.contact_mechanic_request_body,
                **self.user_auth_headers,
                content_type="application/json"
            )
            mock_get.assert_called_once()
            self.assertEqual(
                mock_get.call_args.kwargs["params"],
                {
                    "mechanic_code": self.contact_mechanic_request_body[
                        "mechanic_code"
                    ],
                    "vin": self.vehicle.vin,
                    "problem_details": "My Car is not working",
                },
            )
            self.assertFalse(mock_get.call_args.kwargs["allow_redirects"])
        self.assertEqual(res.status_code, 200)

    @override_settings(MECHANIC_API_ALLOWED_HOSTS=["www.google.com"])
    def test_contact_mechanic_rejects_header_injection(self):
        """
        an Authorization header carrying CR/LF must not be forwarded
        :return: None
        """
        with patch("crapi.merchant.views.requests.Session.get") as mock_get:
            with patch(
                "crapi.merchant.views.build_authorization_header", return_value=None
            ):
                res = self.client.post(
                    "/workshop/api/merchant/contact_mechanic",
                    self.contact_mechanic_request_body,
                    **self.user_auth_headers,
                    content_type="application/json"
                )
            mock_get.assert_not_called()
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json()["message"], messages.COULD_NOT_CONNECT)

    @override_settings(MECHANIC_API_ALLOWED_HOSTS=["www.google.com"])
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

    @override_settings(MECHANIC_API_ALLOWED_HOSTS=["www.google.com"])
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

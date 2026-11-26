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
Tests for host header validation settings.
"""

from django.conf import settings
from django.http.request import validate_host
from django.test import SimpleTestCase


class AllowedHostsTest(SimpleTestCase):
    def test_no_wildcard_allowed_hosts(self):
        """ALLOWED_HOSTS must never contain a wildcard entry"""
        self.assertTrue(settings.ALLOWED_HOSTS)
        for host in settings.ALLOWED_HOSTS:
            self.assertNotIn("*", host)
            self.assertNotEqual(host, ".")

    def test_bundled_hosts_are_accepted(self):
        """Hosts used by the bundled deployments must pass validation"""
        for host in ("localhost", "127.0.0.1", "0.0.0.0", "crapi-workshop"):
            self.assertTrue(
                validate_host(host, settings.ALLOWED_HOSTS), msg=f"{host} rejected"
            )

    def test_foreign_host_is_rejected(self):
        """A rebound attacker controlled hostname must be rejected"""
        self.assertFalse(
            validate_host("attacker.example.com", settings.ALLOWED_HOSTS)
        )
        self.assertFalse(
            validate_host("crapi-workshop.attacker.example", settings.ALLOWED_HOSTS)
        )

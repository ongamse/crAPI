/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

public class WebSecurityConfigCorsTest {

  private CorsConfiguration resolve(String origins) {
    WebSecurityConfig config = new WebSecurityConfig();
    ReflectionTestUtils.setField(config, "corsAllowedOrigins", origins);
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/identity/api/v2/user/videos/1");
    return config.corsConfigurationSource().getCorsConfiguration(request);
  }

  @Test
  public void allowsOnlyConfiguredOrigins() {
    CorsConfiguration cors = resolve("https://crapi.example.com, http://localhost:8888");
    Assertions.assertEquals(
        "https://crapi.example.com", cors.checkOrigin("https://crapi.example.com"));
    Assertions.assertEquals("http://localhost:8888", cors.checkOrigin("http://localhost:8888"));
    Assertions.assertNull(cors.checkOrigin("https://evil.example.com"));
  }

  @Test
  public void neverAllowsWildcardWhenUnset() {
    CorsConfiguration cors = resolve("");
    Assertions.assertTrue(cors.getAllowedOrigins().isEmpty());
    Assertions.assertNull(cors.checkOrigin("https://evil.example.com"));
  }
}

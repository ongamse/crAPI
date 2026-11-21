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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.mock.web.MockHttpServletRequest;

public class JwtAuthTokenFilterTest {

  private final JwtAuthTokenFilter filter = new JwtAuthTokenFilter();

  private String tokenFor(String header) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    if (header != null) {
      request.addHeader("Authorization", header);
    }
    return filter.getToken(request);
  }

  @Test
  public void extractsBearerAndApiKeyValues() {
    Assertions.assertEquals("a.b.c", tokenFor("Bearer a.b.c"));
    Assertions.assertEquals("a.b.c", tokenFor("ApiKey a.b.c"));
  }

  @Test
  public void rejectsMissingOrMalformedHeaders() {
    Assertions.assertNull(tokenFor(null));
    Assertions.assertNull(tokenFor("Basic abc"));
    Assertions.assertNull(tokenFor("Bearer "));
    Assertions.assertNull(tokenFor("Bearer a b"));
    Assertions.assertNull(tokenFor("Bearer a\tb"));
    Assertions.assertNull(tokenFor("Bearer " + "a".repeat(8193)));
  }
}

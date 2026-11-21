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

import com.crapi.entity.User;
import com.crapi.enums.ERole;
import com.crapi.enums.EStatus;
import java.text.ParseException;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

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

  private JwtAuthTokenFilter filterWithProvider(JwtProvider provider) {
    JwtAuthTokenFilter withProvider = new JwtAuthTokenFilter();
    ReflectionTestUtils.setField(withProvider, "tokenProvider", provider);
    return withProvider;
  }

  private static MockHttpServletRequest requestWith(String header) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", header);
    return request;
  }

  @Test
  public void verifiedUsernameComesFromVerifiedJwtOnly() throws ParseException {
    JwtProvider provider = new JwtProvider("");
    ReflectionTestUtils.setField(provider, "jwtExpiration", "600000");
    User user = new User("email@example.com", "9798789212", "Pass", ERole.ROLE_USER);
    JwtAuthTokenFilter withProvider = filterWithProvider(provider);

    String jwt = provider.generateJwtToken(user);
    Assertions.assertEquals(
        "email@example.com", withProvider.getVerifiedUsername(requestWith("Bearer " + jwt)));

    String apiKey = provider.generateApiKey(user);
    Assertions.assertEquals(
        "email@example.com", withProvider.getVerifiedUsername(requestWith("ApiKey " + apiKey)));
    // API keys carry no expiry and are therefore not accepted as a Bearer JWT
    Assertions.assertThrows(
        ParseException.class,
        () -> withProvider.getVerifiedUsername(requestWith("Bearer " + apiKey)));
    Assertions.assertEquals(
        EStatus.INVALID.toString(), withProvider.getUserFromToken(requestWith("Bearer " + apiKey)));
  }

  @Test
  public void invalidOrMissingTokenIsNeverTrusted() throws ParseException {
    JwtProvider provider = new JwtProvider("");
    ReflectionTestUtils.setField(provider, "jwtExpiration", "600000");
    JwtAuthTokenFilter withProvider = filterWithProvider(provider);

    Assertions.assertThrows(
        ParseException.class, () -> withProvider.getVerifiedUsername(new MockHttpServletRequest()));
    Assertions.assertThrows(
        ParseException.class, () -> withProvider.getVerifiedUsername(requestWith("Bearer a.b.c")));
    Assertions.assertEquals(
        EStatus.INVALID.toString(), withProvider.getUserFromToken(requestWith("Bearer a.b.c")));
    Assertions.assertEquals(
        EStatus.INVALID.toString(), withProvider.getUserFromToken(new MockHttpServletRequest()));
  }
}

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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtProviderTest {

  private static final String EMAIL = "victim@example.com";

  private JwtProvider jwtProvider;

  @Before
  public void setUp() {
    // blank JWKS -> provider generates its own ephemeral RSA signing key
    jwtProvider = new JwtProvider("");
    ReflectionTestUtils.setField(jwtProvider, "jwtExpiration", "3600000");
  }

  private User user() {
    return new User(EMAIL, "9798789212", "Pass", ERole.ROLE_USER);
  }

  private static JWTClaimsSet claims(String subject) {
    return new JWTClaimsSet.Builder()
        .subject(subject)
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 3600000))
        .build();
  }

  @Test
  public void validTokenIsAccepted() throws ParseException {
    String token = jwtProvider.generateJwtToken(user());
    Assert.assertTrue(jwtProvider.validateJwtToken(token));
    Assert.assertEquals(EMAIL, jwtProvider.verifyAndGetClaims(token).getSubject());
  }

  @Test
  public void validApiKeyIsAccepted() throws ParseException {
    String apiKey = jwtProvider.generateApiKey(user());
    Assert.assertTrue(jwtProvider.validateJwtToken(apiKey));
    Assert.assertEquals(EMAIL, jwtProvider.verifyAndGetClaims(apiKey).getSubject());
  }

  @Test
  public void tamperedPayloadIsRejected() throws ParseException {
    String[] parts = jwtProvider.generateJwtToken(user()).split("\\.");
    String forgedPayload =
        Base64URL.encode(claims("admin@example.com").toString().getBytes(StandardCharsets.UTF_8))
            .toString();
    String tampered = parts[0] + "." + forgedPayload + "." + parts[2];
    Assert.assertFalse(jwtProvider.validateJwtToken(tampered));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims(tampered));
  }

  @Test
  public void algNoneTokenIsRejected() {
    Base64URL header =
        Base64URL.encode("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    Base64URL payload =
        Base64URL.encode(claims("admin@example.com").toString().getBytes(StandardCharsets.UTF_8));
    String unsigned = header + "." + payload + ".";
    Assert.assertFalse(jwtProvider.validateJwtToken(unsigned));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims(unsigned));
  }

  @Test
  public void hs256TokenIsRejected() throws Exception {
    byte[] secret = new byte[32];
    java.util.Arrays.fill(secret, (byte) 7);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims("admin@example.com"));
    jwt.sign(new MACSigner(secret));
    String token = jwt.serialize();
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims(token));
  }

  @Test
  public void tokenSignedByForeignRsaKeyIsRejected() throws Exception {
    RSAKey foreign = new RSAKeyGenerator(2048).generate();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims("admin@example.com"));
    jwt.sign(new RSASSASigner(foreign));
    String token = jwt.serialize();
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims(token));
  }

  @Test
  public void malformedOrMissingTokenIsRejected() {
    Assert.assertFalse(jwtProvider.validateJwtToken("not-a-jwt"));
    Assert.assertFalse(jwtProvider.validateJwtToken(null));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims("not-a-jwt"));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.verifyAndGetClaims(null));
  }
}

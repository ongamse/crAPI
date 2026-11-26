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
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtProviderTest {

  private static final String EMAIL = "victim@example.com";

  private RSAKey rsaKey;
  private JwtProvider jwtProvider;

  @Before
  public void setUp() throws Exception {
    rsaKey =
        new RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID("test-key")
            .generate();
    String jwks =
        Base64.getEncoder()
            .encodeToString(new JWKSet(rsaKey).toString(false).getBytes(StandardCharsets.UTF_8));
    jwtProvider = new JwtProvider(jwks);
    ReflectionTestUtils.setField(jwtProvider, "jwtExpiration", "604800000");
  }

  private User user() {
    User user = new User();
    user.setEmail(EMAIL);
    user.setRole(ERole.ROLE_USER);
    return user;
  }

  private JWTClaimsSet claims() {
    return new JWTClaimsSet.Builder().subject(EMAIL).build();
  }

  @Test
  public void issuedTokenIsValidAndYieldsUsername() throws ParseException {
    String token = jwtProvider.generateJwtToken(user());
    Assert.assertTrue(jwtProvider.validateJwtToken(token));
    Assert.assertEquals(EMAIL, jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void issuedApiKeyYieldsUsername() throws ParseException {
    String token = jwtProvider.generateApiKey(user());
    Assert.assertEquals(EMAIL, jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test(expected = ParseException.class)
  public void tamperedSignatureIsRejectedForUsername() throws Exception {
    SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims());
    forged.sign(
        new RSASSASigner(
            new RSAKeyGenerator(2048).algorithm(JWSAlgorithm.RS256).generate().toRSAPrivateKey()));
    jwtProvider.getUserNameFromJwtToken(forged.serialize());
  }

  @Test(expected = ParseException.class)
  public void unsignedTokenIsRejectedForUsername() throws Exception {
    jwtProvider.getUserNameFromJwtToken(new PlainJWT(claims()).serialize());
  }

  @Test
  public void unsignedTokenFailsValidation() {
    Assert.assertFalse(jwtProvider.validateJwtToken(new PlainJWT(claims()).serialize()));
  }

  @Test
  public void hs256TokenFailsValidation() throws Exception {
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims());
    jwt.sign(new MACSigner(new byte[32]));
    Assert.assertFalse(jwtProvider.validateJwtToken(jwt.serialize()));
    Assert.assertThrows(
        ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(jwt.serialize()));
  }

  @Test
  public void garbageTokenFailsValidation() {
    Assert.assertFalse(jwtProvider.validateJwtToken("not-a-jwt"));
  }
}

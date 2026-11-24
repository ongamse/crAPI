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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JwtProviderTest {

  private static final String EMAIL = "victim@example.com";

  private RSAKey rsaKey;
  private JwtProvider jwtProvider;

  @BeforeEach
  public void setUp() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("test").algorithm(JWSAlgorithm.RS256).generate();
    String jwksJson =
        Base64.getEncoder()
            .encodeToString(new JWKSet(rsaKey).toString(false).getBytes(StandardCharsets.UTF_8));
    jwtProvider = new JwtProvider(jwksJson);
  }

  private String sign(RSAKey key, String subject) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder().subject(subject).build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  @Test
  public void validTokenIsAccepted() throws Exception {
    String token = sign(rsaKey, EMAIL);
    Assertions.assertTrue(jwtProvider.validateJwtToken(token));
    Assertions.assertEquals(EMAIL, jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void tokenSignedWithOtherKeyIsRejected() throws Exception {
    RSAKey other = new RSAKeyGenerator(2048).keyID("test").generate();
    String token = sign(other, EMAIL);
    Assertions.assertFalse(jwtProvider.validateJwtToken(token));
    Assertions.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void tamperedPayloadIsRejected() throws Exception {
    String[] parts = sign(rsaKey, EMAIL).split("\\.");
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"sub\":\"admin@example.com\"}".getBytes(StandardCharsets.UTF_8));
    String token = parts[0] + "." + payload + "." + parts[2];
    Assertions.assertFalse(jwtProvider.validateJwtToken(token));
    Assertions.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void unsignedTokenIsRejected() throws Exception {
    String token = new PlainJWT(new JWTClaimsSet.Builder().subject(EMAIL).build()).serialize();
    Assertions.assertFalse(jwtProvider.validateJwtToken(token));
    Assertions.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void hmacTokenKeyedWithPublicKeyIsRejected() throws Exception {
    String secret = Base64.getEncoder().encodeToString(rsaKey.toPublicKey().getEncoded());
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder().subject(EMAIL).build());
    jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
    String token = jwt.serialize();
    Assertions.assertFalse(jwtProvider.validateJwtToken(token));
    Assertions.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }
}

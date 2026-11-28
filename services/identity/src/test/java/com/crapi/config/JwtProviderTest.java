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
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JwtProviderTest {

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
  }

  private String signRs256(RSAKey key, Date expiration) throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder().subject("user@example.com").expirationTime(expiration).build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  @Test
  public void validTokenIsAccepted() throws Exception {
    String token = signRs256(rsaKey, new Date(System.currentTimeMillis() + 60000));
    Assert.assertTrue(jwtProvider.validateJwtToken(token));
    Assert.assertEquals("user@example.com", jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void missingJwksGeneratesEphemeralKey() throws Exception {
    JwtProvider ephemeral = new JwtProvider("");
    String token = signRs256(rsaKey, new Date(System.currentTimeMillis() + 60000));
    Assert.assertFalse(ephemeral.validateJwtToken(token));
    Assert.assertTrue(ephemeral.getPublicJwkSet().contains("\"RSA\""));
    Assert.assertFalse(ephemeral.getPublicJwkSet().contains("\"d\""));
  }

  @Test
  public void expiredTokenIsRejected() throws Exception {
    String token = signRs256(rsaKey, new Date(System.currentTimeMillis() - 60000));
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
  }

  @Test
  public void tokenSignedWithOtherKeyIsRejected() throws Exception {
    RSAKey other = new RSAKeyGenerator(2048).algorithm(JWSAlgorithm.RS256).generate();
    String token = signRs256(other, new Date(System.currentTimeMillis() + 60000));
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void unsignedTokenIsRejected() throws Exception {
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("admin@example.com").build();
    String token = new PlainJWT(claims).serialize();
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }

  @Test
  public void hmacTokenSignedWithPublicKeyIsRejected() throws Exception {
    byte[] secret = rsaKey.toPublicKey().getEncoded();
    byte[] padded = java.util.Arrays.copyOf(secret, Math.max(secret.length, 32));
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("admin@example.com").build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(padded));
    String token = jwt.serialize();
    Assert.assertFalse(jwtProvider.validateJwtToken(token));
    Assert.assertThrows(ParseException.class, () -> jwtProvider.getUserNameFromJwtToken(token));
  }
}

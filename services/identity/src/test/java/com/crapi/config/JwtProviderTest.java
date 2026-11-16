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
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class JwtProviderTest {

  private static final String SUBJECT = "user@example.com";

  private static RSAKey newKey(String kid) throws Exception {
    return new RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .keyID(kid)
        .generate();
  }

  private static JwtProvider newProvider(RSAKey key) {
    String jwks =
        Base64.getEncoder()
            .encodeToString(new JWKSet(key).toString(false).getBytes(StandardCharsets.UTF_8));
    return new JwtProvider(jwks);
  }

  private static String sign(RSAKey key) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder().subject(SUBJECT).build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  @Test
  public void validSignatureYieldsSubject() throws Exception {
    RSAKey key = newKey("k1");
    JwtProvider provider = newProvider(key);
    Assertions.assertEquals(SUBJECT, provider.getUserNameFromJwtToken(sign(key)));
  }

  @Test
  public void tokenSignedByOtherKeyIsRejected() throws Exception {
    JwtProvider provider = newProvider(newKey("k1"));
    String forged = sign(newKey("k1"));
    Assertions.assertFalse(provider.validateJwtToken(forged));
    Assertions.assertThrows(ParseException.class, () -> provider.getUserNameFromJwtToken(forged));
  }

  @Test
  public void unsignedTokenIsRejected() throws Exception {
    JwtProvider provider = newProvider(newKey("k1"));
    String unsigned =
        new PlainJWT(new JWTClaimsSet.Builder().subject(SUBJECT).build()).serialize();
    Assertions.assertFalse(provider.validateJwtToken(unsigned));
    Assertions.assertThrows(ParseException.class, () -> provider.getUserNameFromJwtToken(unsigned));
  }
}

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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.test.util.ReflectionTestUtils;

public class JwtProviderTest {

  private static final String EMAIL = "email@example.com";

  private JwtProvider newProvider() {
    JwtProvider provider = new JwtProvider("");
    ReflectionTestUtils.setField(provider, "jwtExpiration", "600000");
    return provider;
  }

  private static String sign(RSAKey key, JWSHeader header, Date exp, Date nbf, String subject)
      throws JOSEException {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder().subject(subject).issueTime(new Date()).expirationTime(exp);
    if (nbf != null) {
      claims.notBeforeTime(nbf);
    }
    SignedJWT jwt = new SignedJWT(header, claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static User testUser() {
    return new User(EMAIL, "9798789212", "Pass", ERole.ROLE_USER);
  }

  private static Date in(long millis) {
    return new Date(System.currentTimeMillis() + millis);
  }

  @Test
  public void validTokenIssuedByServer() throws ParseException {
    JwtProvider provider = newProvider();
    String token = provider.generateJwtToken(testUser());
    Assertions.assertTrue(provider.validateJwtToken(token));
    Assertions.assertEquals(EMAIL, provider.getVerifiedSubject(token, true));
  }

  @Test
  public void apiKeyWithoutExpiryIsAcceptedOnlyAsApiKey() throws ParseException {
    JwtProvider provider = newProvider();
    String apiKey = provider.generateApiKey(testUser());
    Assertions.assertTrue(provider.validateApiKey(apiKey));
    Assertions.assertFalse(provider.validateJwtToken(apiKey));
    Assertions.assertEquals(EMAIL, provider.getVerifiedSubject(apiKey, false));
    Assertions.assertThrows(ParseException.class, () -> provider.getVerifiedSubject(apiKey, true));
  }

  @Test
  public void tamperedSignatureIsRejected() {
    JwtProvider provider = newProvider();
    String token = provider.generateJwtToken(testUser());
    String[] parts = token.split("\\.");
    byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
    sig[0] ^= 0x01;
    String newSig = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
    String tampered = parts[0] + "." + parts[1] + "." + newSig;
    Assertions.assertFalse(provider.validateJwtToken(tampered));
    Assertions.assertThrows(
        ParseException.class, () -> provider.getVerifiedSubject(tampered, true));
  }

  @Test
  public void tokenSignedByOtherKeyIsRejected() throws JOSEException {
    JwtProvider provider = newProvider();
    RSAKey other = new RSAKeyGenerator(2048).keyID("other").generate();
    String token =
        sign(other, new JWSHeader.Builder(JWSAlgorithm.RS256).build(), in(60000), null, EMAIL);
    Assertions.assertFalse(provider.validateJwtToken(token));
  }

  @Test
  public void algNoneIsRejected() {
    JwtProvider provider = newProvider();
    String token =
        new PlainJWT(
                new JWTClaimsSet.Builder().subject(EMAIL).expirationTime(in(60000)).build())
            .serialize();
    Assertions.assertFalse(provider.validateJwtToken(token));
    Assertions.assertThrows(ParseException.class, () -> provider.getVerifiedSubject(token, true));
  }

  @Test
  public void hs256ConfusionTokenIsRejected() throws Exception {
    JwtProvider provider = newProvider();
    RSAKey publicKey = (RSAKey) ReflectionTestUtils.getField(provider, "publicRSAKey");
    String secret =
        Base64.getEncoder().encodeToString(publicKey.toPublicKey().getEncoded());
    for (String secretValue : new String[] {secret, "AA=="}) {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("../../../dev/null").build(),
              new JWTClaimsSet.Builder().subject(EMAIL).expirationTime(in(60000)).build());
      jwt.sign(new MACSigner(padTo32(secretValue.getBytes(StandardCharsets.UTF_8))));
      String token = jwt.serialize();
      Assertions.assertFalse(provider.validateJwtToken(token));
      Assertions.assertThrows(
          ParseException.class, () -> provider.getVerifiedSubject(token, true));
    }
  }

  private static byte[] padTo32(byte[] in) {
    if (in.length >= 32) {
      return in;
    }
    byte[] out = new byte[32];
    System.arraycopy(in, 0, out, 0, in.length);
    return out;
  }

  @Test
  public void jkuHeaderIsNeverFetched() throws Exception {
    JwtProvider provider = newProvider();
    RSAKey attacker = new RSAKeyGenerator(2048).keyID("attacker").generate();
    try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      listener.setSoTimeout(500);
      URI jku = URI.create("http://127.0.0.1:" + listener.getLocalPort() + "/jwks.json");
      String token =
          sign(
              attacker,
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("attacker").jwkURL(jku).build(),
              in(60000),
              null,
              EMAIL);
      Assertions.assertFalse(provider.validateJwtToken(token));
      Assertions.assertThrows(
          SocketTimeoutException.class, listener::accept, "jku must not be fetched");
    }
  }

  @Test
  public void expiredTokenIsRejected() throws JOSEException {
    JwtProvider provider = newProvider();
    RSAKey privateKey = signingKey(provider);
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
    String token = sign(privateKey, header, in(-60000), null, EMAIL);
    Assertions.assertFalse(provider.validateJwtToken(token));
  }

  @Test
  public void notYetValidTokenIsRejected() throws JOSEException {
    JwtProvider provider = newProvider();
    RSAKey privateKey = signingKey(provider);
    String token =
        sign(
            privateKey,
            new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
            in(120000),
            in(60000),
            EMAIL);
    Assertions.assertFalse(provider.validateJwtToken(token));
  }

  @Test
  public void emptySubjectIsRejected() throws JOSEException {
    JwtProvider provider = newProvider();
    RSAKey privateKey = signingKey(provider);
    String token =
        sign(privateKey, new JWSHeader.Builder(JWSAlgorithm.RS256).build(), in(60000), null, " ");
    Assertions.assertFalse(provider.validateJwtToken(token));
  }

  @Test
  public void malformedTokensAreRejected() {
    JwtProvider provider = newProvider();
    String headerOnlyToken = b64Url("{\"alg\":\"RS256\"}") + "." + b64Url("{}") + ".";
    String[] samples = {
      null,
      "",
      "abc",
      "a.b.c",
      "a.b",
      "....",
      "a..c",
      "a.b.c.d",
      "a.b.c d",
      "a.b.c=",
      "a".repeat(8193),
      "a.b." + "c".repeat(8193),
      headerOnlyToken
    };
    for (String bad : samples) {
      Assertions.assertFalse(provider.validateJwtToken(bad));
      Assertions.assertFalse(provider.validateApiKey(bad));
      Assertions.assertThrows(ParseException.class, () -> provider.getVerifiedSubject(bad, true));
      Assertions.assertThrows(ParseException.class, () -> provider.getVerifiedSubject(bad, false));
    }
  }

  @Test
  public void expiredTokenIsRejectedByVerifiedSubject() throws JOSEException {
    JwtProvider provider = newProvider();
    String token =
        sign(
            signingKey(provider),
            new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
            in(-60000),
            null,
            EMAIL);
    Assertions.assertThrows(ParseException.class, () -> provider.getVerifiedSubject(token, true));
  }

  private static String b64Url(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static RSAKey signingKey(JwtProvider provider) {
    KeyPair keyPair = (KeyPair) ReflectionTestUtils.getField(provider, "keyPair");
    RSAKey publicKey = (RSAKey) ReflectionTestUtils.getField(provider, "publicRSAKey");
    return new RSAKey.Builder(publicKey).privateKey(keyPair.getPrivate()).build();
  }
}

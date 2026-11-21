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
import com.crapi.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.text.ParseException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

  private static final int MAX_TOKEN_LENGTH = 8192;

  @Autowired private UserRepository userRepository;

  @Value("${app.jwtExpiration}")
  private String jwtExpiration;

  private KeyPair keyPair;

  private RSAKey publicRSAKey;

  private Map<String, Object> publicJwkSet;

  /**
   * @param jwksJson base64 encoded JWKS (with the RSA private key) supplied at runtime through the
   *     JWKS environment variable / mounted keys file. When it is blank or holds no keys, a fresh
   *     RSA signing key is generated in memory so no private key material has to be committed.
   */
  public JwtProvider(@Value("${app.jwksJson:}") String jwksJson) {
    try {
      JWKSet jwkSet = null;
      if (jwksJson != null && !jwksJson.isBlank()) {
        Base64.Decoder decoder = Base64.getDecoder();
        InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson.trim()));
        jwkSet = JWKSet.load(jwksStream);
        jwksStream.close();
      }
      if (jwkSet == null || jwkSet.getKeys().isEmpty()) {
        log.warn("No JWKS key supplied, generating an ephemeral RSA signing key");
        RSAKey generated =
            new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
        jwkSet = new JWKSet(generated);
      }
      List<JWK> keys = jwkSet.getKeys();
      if (keys.size() != 1
          || keys.get(0).getAlgorithm() == null
          || !Objects.equals(keys.get(0).getAlgorithm().getName(), "RS256")) {
        throw new RuntimeException("Invalid JWKS key passed!!!");
      }

      RSAKey rsaKey = keys.get(0).toRSAKey();
      this.publicRSAKey = rsaKey.toPublicJWK();
      this.keyPair = rsaKey.toKeyPair();
      this.publicJwkSet = jwkSet.toJSONObject();
    } catch (IOException | ParseException | JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  public String getPublicJwkSet() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(this.publicJwkSet);
  }

  /**
   * @param user
   * @return generated token with expire date
   */
  public String generateJwtToken(User user) {
    int jwtExpirationInt;
    if (jwtExpiration.contains("e+")) jwtExpirationInt = new BigDecimal(jwtExpiration).intValue();
    else jwtExpirationInt = Integer.parseInt(jwtExpiration);
    JwtBuilder builder =
        Jwts.builder()
            .subject(user.getEmail())
            .issuedAt(new Date())
            .expiration(new Date((new Date()).getTime() + jwtExpirationInt))
            .claim("role", user.getRole().getName())
            .signWith(this.keyPair.getPrivate());
    String jwt = builder.compact();
    return jwt;
  }

  /**
   * @param user
   * @return generated apikey token without expiry date
   */
  public String generateApiKey(User user) {
    JwtBuilder builder =
        Jwts.builder()
            .subject(user.getEmail())
            .issuedAt(new Date())
            .claim("role", user.getRole().getName())
            .signWith(this.keyPair.getPrivate());
    String jwt = builder.compact();
    return jwt;
  }

  /**
   * Verifies a token against the server's own RSA public key only. The token header is never used
   * to select a key or an algorithm: anything but RS256 (including none and HS*) is rejected and
   * jku/jwk/x5u/kid headers are ignored.
   *
   * @param token compact serialized JWT
   * @return the claims when the signature is valid, otherwise a ParseException is thrown
   */
  private JWTClaimsSet verifiedClaims(String token) throws ParseException {
    requireCompactJwsShape(token);
    // This is the only place a raw token is parsed. It is parsed and verified in the same step and
    // no caller ever sees claims of an unverified token. Unsigned (alg=none) tokens have an empty
    // signature part and were already rejected by the structural pre-check above.
    SignedJWT signedJWT = SignedJWT.parse(token);
    if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
      throw new ParseException("Unsupported JWT algorithm", 0);
    }
    try {
      if (!signedJWT.verify(new RSASSAVerifier(this.publicRSAKey))) {
        throw new ParseException("Invalid JWT signature", 0);
      }
    } catch (JOSEException e) {
      log.error("JWT signature verification failed", e);
      throw new ParseException("JWT signature verification failed", 0);
    }
    return signedJWT.getJWTClaimsSet();
  }

  /**
   * Cheap structural check done with plain string operations before any JWT parsing: bounded
   * length and exactly three non-empty dot separated base64url parts.
   */
  private static void requireCompactJwsShape(String token) throws ParseException {
    if (token == null || token.isEmpty()) {
      throw new ParseException("Missing JWT", 0);
    }
    if (token.length() > MAX_TOKEN_LENGTH) {
      throw new ParseException("JWT too long", 0);
    }
    int parts = 1;
    int partLength = 0;
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (c == '.') {
        if (partLength == 0) {
          throw new ParseException("Malformed JWT", i);
        }
        parts++;
        partLength = 0;
      } else if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        partLength++;
      } else {
        throw new ParseException("Malformed JWT", i);
      }
    }
    if (parts != 3 || partLength == 0) {
      throw new ParseException("Malformed JWT", 0);
    }
  }

  /**
   * Single verification gate for reading a user out of a token. The token is verified once (RS256
   * signature with the server key, subject, expiry and not-before) and only then is the subject
   * returned.
   *
   * @param token compact serialized JWT or API key
   * @param expirationRequired true for session JWTs, false for API keys (issued without expiry)
   * @return the subject of the verified token
   * @throws ParseException when the token is malformed, not signed by this server or not valid
   */
  public String getVerifiedSubject(String token, boolean expirationRequired)
      throws ParseException {
    JWTClaimsSet claims = verifiedClaims(token);
    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new ParseException("JWT has no subject", 0);
    }
    if (!hasValidTimeClaims(claims, expirationRequired)) {
      throw new ParseException("JWT is expired or not yet valid", 0);
    }
    return subject;
  }

  private static boolean hasValidTimeClaims(JWTClaimsSet claims, boolean expirationRequired) {
    Date now = new Date();
    Date exp = claims.getExpirationTime();
    if (exp == null) {
      if (expirationRequired) {
        return false;
      }
    } else if (!exp.after(now)) {
      return false;
    }
    Date nbf = claims.getNotBeforeTime();
    return nbf == null || !nbf.after(now);
  }

  private boolean validate(String authToken, boolean expirationRequired) {
    try {
      getVerifiedSubject(authToken, expirationRequired);
      return true;
    } catch (ParseException e) {
      // Unsigned, badly signed or malformed tokens are never valid
      log.error("JWT token rejected -> Message: {}", e.getMessage());
    }
    return false;
  }

  /**
   * @param authToken
   * @return true only for an RS256 token signed by this server that has a subject, an expiry that
   *     has not passed and (if present) a not-before that has passed
   */
  public boolean validateJwtToken(String authToken) {
    return validate(authToken, true);
  }

  /**
   * API keys are issued without an expiry; they must still be signed by this server and carry a
   * subject.
   *
   * @param apiKey
   * @return true when the API key token is validly signed
   */
  public boolean validateApiKey(String apiKey) {
    return validate(apiKey, false);
  }
}

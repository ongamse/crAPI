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
    if (token == null || token.isBlank()) {
      throw new ParseException("Missing JWT", 0);
    }
    // Unsigned (alg=none) and otherwise unparseable tokens are rejected by SignedJWT.parse.
    SignedJWT signedJWT = SignedJWT.parse(token);
    if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
      throw new ParseException("Unsupported JWT algorithm", 0);
    }
    try {
      if (!signedJWT.verify(new RSASSAVerifier(this.publicRSAKey))) {
        throw new ParseException("Invalid JWT signature", 0);
      }
    } catch (JOSEException e) {
      throw new ParseException("JWT signature verification failed: " + e.getMessage(), 0);
    }
    return signedJWT.getJWTClaimsSet();
  }

  /**
   * @param token
   * @return username from JWT Token, only when it is signed with the server's own RSA key
   */
  public String getUserNameFromJwtToken(String token) throws ParseException {
    return verifiedClaims(token).getSubject();
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
      JWTClaimsSet claims = verifiedClaims(authToken);
      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        return false;
      }
      return hasValidTimeClaims(claims, expirationRequired);
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

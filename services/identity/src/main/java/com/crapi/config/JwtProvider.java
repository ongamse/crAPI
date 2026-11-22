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

  public JwtProvider(@Value("${app.jwksJson}") String jwksJson) {
    try {
      JWKSet jwkSet = null;
      RSAKey rsaKey = null;
      if (jwksJson != null && !jwksJson.isBlank()) {
        Base64.Decoder decoder = Base64.getDecoder();
        InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson));
        jwkSet = JWKSet.load(jwksStream);
        jwksStream.close();
        List<JWK> keys = jwkSet.getKeys();
        if (keys.size() != 1 || !Objects.equals(keys.get(0).getAlgorithm().getName(), "RS256")) {
          throw new RuntimeException("Invalid JWKS key passed!!!");
        }
        rsaKey = keys.get(0).toRSAKey();
      }

      if (rsaKey == null || !rsaKey.isPrivate()) {
        // No private key material was supplied (no key is committed to the repository).
        // Provide a complete JWKS (including private components) through the JWKS
        // environment variable or a mounted keys/jwks.json for a stable signing key;
        // otherwise generate an ephemeral signing key for this process.
        log.warn("No JWKS private key supplied; generating an ephemeral RSA signing key");
        rsaKey = generateEphemeralKey();
        jwkSet = new JWKSet(rsaKey);
      }
      this.publicRSAKey = rsaKey.toPublicJWK();
      this.keyPair = rsaKey.toKeyPair();
      this.publicJwkSet = jwkSet.toJSONObject();
    } catch (IOException | ParseException | JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  private static RSAKey generateEphemeralKey() throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .keyIDFromThumbprint(true)
        .generate();
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
   * Verify the signature of a signed JWT against the server's own RSA public key. The algorithm is
   * restricted to RSA signature algorithms (matching the RSA key) and is never taken from the token header; verification keys referenced by the
   * token (jku/jwk/kid) are never used.
   */
  private boolean hasValidSignature(SignedJWT signedJWT) throws JOSEException {
    JWSAlgorithm alg = signedJWT.getHeader().getAlgorithm();
    if (!JWSAlgorithm.RS256.equals(alg)
        && !JWSAlgorithm.RS384.equals(alg)
        && !JWSAlgorithm.RS512.equals(alg)) {
      log.debug("Rejected JWT with unsupported algorithm");
      return false;
    }
    return signedJWT.verify(new RSASSAVerifier(this.publicRSAKey));
  }

  /**
   * @param token
   * @return username from JWT Token, only after its signature has been verified
   * @throws ParseException if the token is malformed or its signature is not valid
   */
  public String getUserNameFromJwtToken(String token) throws ParseException {
    SignedJWT signedJWT = SignedJWT.parse(token);
    try {
      if (!hasValidSignature(signedJWT)) {
        throw new ParseException("Invalid JWT signature", 0);
      }
    } catch (JOSEException e) {
      throw new ParseException("JWT signature verification failed", 0);
    }
    return signedJWT.getJWTClaimsSet().getSubject();
  }

  /**
   * @param authToken
   * @return true only if the token is a well formed JWT signed by this service (RSA)
   */
  public boolean validateJwtToken(String authToken) {
    try {
      SignedJWT signedJWT = SignedJWT.parse(authToken);
      boolean valid = hasValidSignature(signedJWT);
      log.debug("JWT valid?: " + valid);
      return valid;
    } catch (ParseException e) {
      log.error("Could not parse JWT Token -> Message: %d", e);
    } catch (JOSEException e) {
      log.error("JWT signature verification failed -> Message: %d", e);
    }

    return false;
  }
}

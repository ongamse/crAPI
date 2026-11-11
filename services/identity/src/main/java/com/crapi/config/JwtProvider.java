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

  public JwtProvider(@Value("${app.jwksJson}") String jwksJson) {
    try {
      List<JWK> keys = Collections.emptyList();
      JWKSet jwkSet = null;
      if (jwksJson != null && !jwksJson.isBlank()) {
        Base64.Decoder decoder = Base64.getDecoder();
        InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson));
        jwkSet = JWKSet.load(jwksStream);
        jwksStream.close();
        keys = jwkSet.getKeys();
      }

      RSAKey rsaKey;
      if (keys.isEmpty()) {
        // No private JWKS was supplied at runtime (mounted keys/jwks.json). Private key material
        // is never committed to the repository, so generate an ephemeral signing key instead.
        log.warn(
            "No JWKS private key supplied; generated an ephemeral RS256 key. "
                + "Mount a keys/jwks.json to use a persistent key.");
        rsaKey =
            new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
        jwkSet = new JWKSet(rsaKey);
      } else {
        if (keys.size() != 1 || !Objects.equals(keys.get(0).getAlgorithm().getName(), "RS256")) {
          throw new RuntimeException("Invalid JWKS key passed!!!");
        }
        rsaKey = keys.get(0).toRSAKey();
      }

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
   * @param token
   * @return username from JWT Token, only after signature and expiry verification
   * @throws ParseException when the token is malformed, unsigned, badly signed or expired
   */
  public String getUserNameFromJwtToken(String token) throws ParseException {
    return getVerifiedClaims(token).getSubject();
  }

  /**
   * Parse the token and verify it against the configured RS256 public key before any claim is
   * trusted. Fails closed on any problem.
   */
  private JWTClaimsSet getVerifiedClaims(String token) throws ParseException {
    if (token == null) {
      throw new ParseException("Missing JWT token", 0);
    }
    SignedJWT signedJWT = SignedJWT.parse(token);
    // Only the algorithm of the configured key is accepted; the header never selects the key
    if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
      throw new ParseException("Unsupported JWT algorithm", 0);
    }
    try {
      if (!signedJWT.verify(new RSASSAVerifier(this.publicRSAKey))) {
        throw new ParseException("Invalid JWT signature", 0);
      }
    } catch (JOSEException e) {
      throw new ParseException("JWT signature verification failed", 0);
    }
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
    Date expiration = claims.getExpirationTime();
    if (expiration != null && expiration.before(new Date())) {
      throw new ParseException("JWT token expired", 0);
    }
    return claims;
  }

  /**
   * @param authToken
   * @return true only when the token signature (RS256, configured key) and expiry are valid
   */
  public boolean validateJwtToken(String authToken) {
    try {
      getVerifiedClaims(authToken);
      return true;
    } catch (ParseException e) {
      log.error("Could not validate JWT Token -> Message: {}", e.getMessage());
    }
    return false;
  }
}

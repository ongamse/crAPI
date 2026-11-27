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
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
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

  public JwtProvider(@Value("${app.jwksJson:}") String jwksJson) {
    try {
      JWKSet jwkSet;
      if (jwksJson == null || jwksJson.isBlank()) {
        // No signing key supplied (JWKS env / mounted /app/keys/jwks.json): generate an ephemeral
        // key so no private key material ever has to be committed. Tokens do not survive restarts.
        log.warn("No JWKS signing key supplied; generated an ephemeral RS256 key for this process");
        RSAKey generated =
            new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
        jwkSet = new JWKSet(generated);
      } else {
        Base64.Decoder decoder = Base64.getDecoder();
        InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson));
        jwkSet = JWKSet.load(jwksStream);
        jwksStream.close();
      }
      List<JWK> keys = jwkSet.getKeys();
      if (keys.size() != 1 || !Objects.equals(keys.get(0).getAlgorithm().getName(), "RS256")) {
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
   * @param token
   * @return username from JWT Token
   */
  public String getUserNameFromJwtToken(String token) throws ParseException {
    return parseVerifiedClaims(token).getSubject();
  }

  /**
   * Single verified-parse path for every claim read. The token must be a signed JWS whose
   * signature verifies against the server's own RSA public key (the key id / key URL headers
   * supplied by the token are never used to select the key). Unsigned tokens (alg=none), HS*
   * tokens, any algorithm other than RS256, malformed tokens and expired tokens are rejected.
   *
   * @param token compact serialized JWT
   * @return claims, only when signature, algorithm and expiry checks all pass
   * @throws ParseException when the token is not acceptable
   */
  private Claims parseVerifiedClaims(String token) throws ParseException {
    try {
      Jws<Claims> jws =
          Jwts.parser().verifyWith(this.keyPair.getPublic()).build().parseSignedClaims(token);
      if (!JWSAlgorithm.RS256.getName().equals(jws.getHeader().getAlgorithm())) {
        throw new ParseException("Unsupported JWT algorithm", 0);
      }
      return jws.getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("JWT rejected: {}", e.getClass().getSimpleName());
      throw new ParseException("Invalid JWT", 0);
    }
  }

  /**
   * @param authToken
   * @return true when the token signature is valid and the token has not expired
   */
  public boolean validateJwtToken(String authToken) {
    try {
      parseVerifiedClaims(authToken);
      log.debug("JWT valid?: true");
      return true;
    } catch (ParseException e) {
      log.error("Could not validate JWT Token -> Message: {}", e.getMessage());
    }

    return false;
  }
}

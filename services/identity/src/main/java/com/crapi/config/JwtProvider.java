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

  private Map<String, Object> publicJwkSet;

  public JwtProvider(@Value("${app.jwksJson:}") String jwksJson) {
    try {
      RSAKey rsaKey;
      JWKSet jwkSet;
      if (jwksJson == null || jwksJson.isBlank()) {
        // No signing key supplied (none is committed to the repository): generate an ephemeral
        // one. Tokens issued with it are invalid after a restart; mount a jwks.json to persist.
        log.warn("No JWKS signing key supplied; generating an ephemeral RSA key");
        rsaKey =
            new RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint(true)
                .generate();
        jwkSet = new JWKSet(rsaKey);
      } else {
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
    // Claims are only read from a token whose signature has been verified (fail closed)
    Jws<Claims> verifiedJws = parseAndVerify(token);
    if (verifiedJws == null) {
      throw new ParseException("JWT signature verification failed", 0);
    }
    return verifiedJws.getPayload().getSubject();
  }

  /**
   * Parse the token and verify its signature in one step with the server's own RSA public key. The
   * key is fixed by server configuration and the algorithm must be RS256; the token header is never
   * allowed to choose the algorithm or the verification key. Unsigned (alg=none) tokens and HMAC
   * tokens are rejected by the parser.
   *
   * @param authToken
   * @return the verified token, or null when it is malformed, unsigned or not validly signed
   */
  private Jws<Claims> parseAndVerify(String authToken) {
    if (authToken == null) {
      return null;
    }
    try {
      Jws<Claims> jws =
          Jwts.parser().verifyWith(this.keyPair.getPublic()).build().parseSignedClaims(authToken);
      if (!"RS256".equals(jws.getHeader().getAlgorithm())) {
        log.debug("Rejected JWT with unexpected algorithm");
        return null;
      }
      return jws;
    } catch (JwtException | IllegalArgumentException e) {
      // Unsigned, malformed, expired or not validly signed tokens are never trusted
      log.error("Could not verify JWT Token -> Message: {}", e.getMessage());
    }
    return null;
  }

  /**
   * @param authToken
   * @return true only when the token carries a valid signature from this service's key
   */
  public boolean validateJwtToken(String authToken) {
    return parseAndVerify(authToken) != null;
  }
}

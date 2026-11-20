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
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
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

  private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

  private Map<String, Object> publicJwkSet;

  public JwtProvider(@Value("${app.jwksJson}") String jwksJson) {
    try {
      JWKSet jwkSet;
      RSAKey rsaKey;
      if (jwksJson == null || jwksJson.isBlank()) {
        // No signing key supplied (JWKS env / keys/jwks.json): generate one for this run instead
        // of shipping a private key in the repository.
        log.warn("No JWKS signing key supplied; generating an ephemeral RSA key.");
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

      this.publicRSAKey = rsaKey.toPublicJWK();
      DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(
          new SingleKeyJWSKeySelector<>(JWSAlgorithm.RS256, this.publicRSAKey.toRSAPublicKey()));
      this.jwtProcessor = processor;
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
    try {
      return verifiedClaims(token).getSubject();
    } catch (BadJOSEException | JOSEException e) {
      throw new ParseException("JWT verification failed: " + e.getMessage(), 0);
    }
  }

  /**
   * Single entry point for reading claims. The Nimbus processor parses the token, selects only the
   * server's own RSA public key (algorithm pinned to RS256; token-supplied alg, kid, jku never
   * select the key or the verification method), verifies the signature, runs the standard
   * time-claim checks (exp/nbf when present), and only then returns the claims set. Unverified
   * claims are never exposed to callers.
   */
  private JWTClaimsSet verifiedClaims(String token)
      throws ParseException, BadJOSEException, JOSEException {
    return this.jwtProcessor.process(token, null);
  }

  /**
   * @param authToken
   * @return true when the token carries a valid signature from this server's RSA key
   */
  public boolean validateJwtToken(String authToken) {
    try {
      verifiedClaims(authToken);
      log.debug("JWT valid?: true");
      return true;
    } catch (ParseException e) {
      log.error("Could not parse JWT Token", e);
    } catch (BadJOSEException | JOSEException e) {
      log.error("JWT verification failed", e);
    }

    return false;
  }
}

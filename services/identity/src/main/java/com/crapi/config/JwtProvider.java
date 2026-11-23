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
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
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

  private Map<String, Object> publicJwkSet;

  private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

  public JwtProvider(@Value("${app.jwksJson:}") String jwksJson) {
    try {
      JWKSet jwkSet;
      if (jwksJson == null || jwksJson.isBlank()) {
        // No runtime-provided key: generate an ephemeral RSA signing key so that no private key
        // material needs to be committed. Tokens are invalidated on restart. Mount a jwks.json
        // (see docs/setup.md) to use a persistent key.
        log.warn("No JWKS key supplied; generating an ephemeral RSA signing key.");
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
      DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
      processor.setJWSKeySelector(
          new JWSVerificationKeySelector<>(
              JWSAlgorithm.RS256, new ImmutableJWKSet<>(new JWKSet(this.publicRSAKey))));
      // Signature verification only (existing behaviour): claim validation such as exp is not
      // enforced here, as before; API keys intentionally carry no expiry.
      processor.setJWTClaimsSetVerifier((claims, context) -> {});
      this.jwtProcessor = processor;
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
   * Single entry point for token handling: parsing and signature verification happen in one
   * processor call and only the verified claims set is returned. The key selector only offers the
   * server's own RS256 public key, so attacker-controlled header values (alg, jku, kid) never
   * select the verification key; unsecured (alg=none) and non-RS256 tokens are rejected.
   */
  private JWTClaimsSet parseVerifiedClaims(String token) throws ParseException {
    try {
      return this.jwtProcessor.process(token, null);
    } catch (BadJOSEException | JOSEException e) {
      log.debug("JWT verification failed: {}", e.getMessage());
      ParseException parseException = new ParseException("JWT signature verification failed", 0);
      parseException.initCause(e);
      throw parseException;
    }
  }

  /**
   * @param authToken
   * @return validate token signature and true boolean
   */
  public boolean validateJwtToken(String authToken) {
    try {
      parseVerifiedClaims(authToken);
      return true;
    } catch (ParseException e) {
      log.error("Could not verify JWT Token -> Message: {}", e.getMessage());
    }

    return false;
  }
}

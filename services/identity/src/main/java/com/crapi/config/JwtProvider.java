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
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.jsonwebtoken.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
        // No key supplied: generate an ephemeral signing key instead of shipping one in git.
        // Supply a persistent key via a mounted jwks.json (or the JWKS env var) if tokens must
        // survive restarts.
        log.warn("No JWKS key supplied; generating an ephemeral RSA signing key");
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
    return verifiedClaims(token).getSubject();
  }

  /**
   * Verify the token signature against the server's own RSA public key and only then decode the
   * claims from the verified payload. Verification always uses a locally constructed RS256 header,
   * so the algorithm, key id and key URL supplied in the (unverified) token header are never used
   * to select the key or algorithm, and no claim is read before the signature has been checked.
   *
   * @throws ParseException when the token is malformed, unsigned or the signature is invalid
   */
  private JWTClaimsSet verifiedClaims(String token) throws ParseException {
    if (token == null) {
      throw new ParseException("Missing JWT", 0);
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new ParseException("Invalid JWT format", 0);
    }
    try {
      byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
      boolean valid =
          new RSASSAVerifier(this.publicRSAKey)
              .verify(new JWSHeader(JWSAlgorithm.RS256), signingInput, Base64URL.from(parts[2]));
      if (!valid) {
        throw new ParseException("Invalid JWT signature", 0);
      }
    } catch (JOSEException e) {
      log.error("JWT signature verification failed -> Message: %d", e);
      throw new ParseException("Invalid JWT signature", 0);
    }
    return JWTClaimsSet.parse(Base64URL.from(parts[1]).decodeToString());
  }

  /**
   * @param authToken
   * @return true when the token signature is valid and the token has not expired
   */
  public boolean validateJwtToken(String authToken) {
    try {
      Date expiration = verifiedClaims(authToken).getExpirationTime();
      if (expiration != null && expiration.before(new Date())) {
        log.debug("JWT expired");
        return false;
      }
      return true;
    } catch (ParseException e) {
      log.error("Could not validate JWT Token -> Message: %d", e);
    }

    return false;
  }
}

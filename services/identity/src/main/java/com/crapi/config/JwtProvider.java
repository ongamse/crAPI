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
import com.nimbusds.jose.jwk.RSAKey;
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
      Base64.Decoder decoder = Base64.getDecoder();
      InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson));
      JWKSet jwkSet = JWKSet.load(jwksStream);
      jwksStream.close();
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
    return getVerifiedClaims(SignedJWT.parse(token)).getSubject();
  }

  /**
   * Verify the signature first and only then read claims. Also enforces exp/nbf when present (API
   * keys are issued without exp and remain accepted).
   *
   * @throws ParseException when the signature is invalid, or the token is expired / not yet valid
   */
  private JWTClaimsSet getVerifiedClaims(SignedJWT signedJWT) throws ParseException {
    if (!isSignatureValid(signedJWT)) {
      throw new ParseException("JWT signature verification failed", 0);
    }
    JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
    Date now = new Date();
    Date exp = claims.getExpirationTime();
    if (exp != null && !exp.after(now)) {
      throw new ParseException("JWT is expired", 0);
    }
    Date nbf = claims.getNotBeforeTime();
    if (nbf != null && nbf.after(now)) {
      throw new ParseException("JWT is not yet valid", 0);
    }
    return claims;
  }

  /**
   * Verify the token signature using only the server's own RSA public key. The algorithm is pinned
   * to RS256; attacker-controlled header values (alg, jku, kid) never select the verification key.
   */
  private boolean isSignatureValid(SignedJWT signedJWT) {
    try {
      if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
        log.debug("Rejected JWT with unexpected algorithm");
        return false;
      }
      JWSVerifier verifier = new RSASSAVerifier(this.publicRSAKey);
      boolean valid = signedJWT.verify(verifier);
      log.debug("JWT valid?: " + valid);
      return valid;
    } catch (JOSEException e) {
      log.error("JWT signature verification failed -> Message: %d", e);
      return false;
    }
  }

  /**
   * @param authToken
   * @return true only when the token is a signed JWT with a valid RS256 signature
   */
  public boolean validateJwtToken(String authToken) {
    try {
      getVerifiedClaims(SignedJWT.parse(authToken));
      return true;
    } catch (ParseException e) {
      log.error("Could not validate JWT Token -> Message: %d", e);
    }

    return false;
  }
}

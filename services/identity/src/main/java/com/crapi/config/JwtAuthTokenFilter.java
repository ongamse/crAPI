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

import com.crapi.constant.UserMessage;
import com.crapi.enums.EStatus;
import com.crapi.service.Impl.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

enum ApiType {
  JWT,
  APIKEY;
}

@Slf4j
public class JwtAuthTokenFilter extends OncePerRequestFilter {

  private static final int MAX_TOKEN_LENGTH = 8192;

  @Autowired private JwtProvider tokenProvider;

  @Autowired private UserDetailsServiceImpl userDetailsService;

  /**
   * @param request
   * @param response
   * @param filterChain
   * @throws ServletException
   * @throws IOException
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    try {
      String username = getUserFromToken(request);
      if (username != null && !username.equalsIgnoreCase(EStatus.INVALID.toString())) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails == null) {
          log.error("User not found");
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED, UserMessage.INVALID_CREDENTIALS);
        }
        if (userDetails.isAccountNonLocked()) {
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
          log.error(UserMessage.ACCOUNT_LOCKED_MESSAGE);
          response.sendError(
              HttpServletResponse.SC_UNAUTHORIZED, UserMessage.ACCOUNT_LOCKED_MESSAGE);
        }
      }
    } catch (Exception e) {
      log.error("Can NOT set user authentication", e);
    }

    filterChain.doFilter(request, response);
  }

  /**
   * @param request
   * @return key/token
   */
  public String getToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null) {
      return null;
    }
    String token;
    if (authHeader.startsWith("Bearer ")) {
      token = authHeader.substring("Bearer ".length());
    } else if (authHeader.startsWith("ApiKey ")) {
      token = authHeader.substring("ApiKey ".length());
    } else {
      return null;
    }
    if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
      return null;
    }
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (c <= ' ' || c == 127) {
        return null;
      }
    }
    return token;
  }

  /**
   * @param request
   * @return api type from HttpServletRequest
   */
  public ApiType getKeyType(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    ApiType apiType = ApiType.JWT;
    if (authHeader != null && authHeader.startsWith("ApiKey ")) {
      apiType = ApiType.APIKEY;
    }
    return apiType;
  }

  /**
   * @param request
   * @return return username from HttpServletRequest if request have token we are returning username
   *     from request token
   */
  public String getUserFromToken(HttpServletRequest request) throws ParseException {
    try {
      return getVerifiedUsername(request);
    } catch (ParseException e) {
      log.debug("Request carries no valid token -> Message: {}", e.getMessage());
      return EStatus.INVALID.toString();
    }
  }

  /**
   * Single gate for reading the user out of a request token: the token is verified exactly once
   * (signature, subject, expiry for JWTs, not-before) and the subject is only returned afterwards.
   *
   * @param request
   * @return username of the verified token
   * @throws ParseException when the request has no token or the token is not valid
   */
  public String getVerifiedUsername(HttpServletRequest request) throws ParseException {
    String token = getToken(request);
    if (token == null) {
      throw new ParseException("Missing or malformed Authorization token", 0);
    }
    boolean isApiKey = getKeyType(request) == ApiType.APIKEY;
    log.debug(isApiKey ? "Token is api token" : "Token is jwt token");
    return tokenProvider.getVerifiedSubject(token, !isApiKey);
  }
}

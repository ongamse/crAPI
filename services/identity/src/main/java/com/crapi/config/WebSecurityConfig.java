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

import com.crapi.service.Impl.UserDetailsServiceImpl;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@Slf4j
@ComponentScan(basePackages = {"com.crapi"})
public class WebSecurityConfig {

  @Autowired UserDetailsServiceImpl userDetailsService;

  @Autowired JwtAuthEntryPoint jwtUnauthorizedHandler;

  @Bean
  public JwtAuthTokenFilter authenticationJwtTokenFilter() {
    return new JwtAuthTokenFilter();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());

    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager() throws Exception {
    DaoAuthenticationProvider authProvider = authenticationProvider();
    return new AuthenticationManager() {
      @Override
      public Authentication authenticate(Authentication authentication)
          throws org.springframework.security.core.AuthenticationException {
        return authProvider.authenticate(authentication);
      }
    };
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static RequestMatcher explicitAuthorizationCredentialMatcher() {
    return request -> {
      String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
      return authHeader != null
          && (authHeader.startsWith("Bearer ") || authHeader.startsWith("ApiKey "));
    };
  }

  private static RequestMatcher preAuthenticationPostMatcher() {
    String[] paths = {
      "/identity/api/auth/login",
      "/identity/api/auth/signup",
      "/identity/api/auth/verify",
      "/identity/api/auth/forget-password",
      "/identity/api/auth/v2/check-otp",
      "/identity/api/auth/v3/check-otp",
      "/identity/api/auth/v4.0/user/login-with-token",
      "/identity/api/auth/v2.7/user/login-with-token",
      "/identity/api/auth/reset-test-users",
      "/identity/api/auth/unlock",
      "/identity/management/user/apikey"
    };
    List<RequestMatcher> matchers = new ArrayList<>();
    for (String path : paths) {
      matchers.add(new AntPathRequestMatcher(path, HttpMethod.POST.name()));
    }
    return new OrRequestMatcher(matchers);
  }

  @Bean
  public SecurityFilterChain securityFilterChainWeb(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            (requests) ->
                requests
                    .requestMatchers("/identity/api/auth/**")
                    .permitAll()
                    .requestMatchers("/identity/health_check")
                    .permitAll()
                    .requestMatchers("/identity/api/v2/user/dashboard")
                    .permitAll()
                    .requestMatchers("/identity/management/user/**")
                    .permitAll()
                    .requestMatchers("/identity/management/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtUnauthorizedHandler));
    http.authenticationProvider(authenticationProvider());
    // CSRF stays enabled for every route. The only exemptions are:
    //  1. requests that explicitly present a Bearer JWT or ApiKey credential in the Authorization
    //     header (the sole way this stateless API authenticates; browsers never attach it
    //     ambiently, unlike cookies), and
    //  2. an explicit list of unauthenticated POST endpoints (credentials / OTP in the body, no
    //     ambient authority) that must work before any token or CSRF cookie exists.
    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                    explicitAuthorizationCredentialMatcher(), preAuthenticationPostMatcher()));
    http.cors(Customizer.withDefaults());
    return http.build();
  }
}

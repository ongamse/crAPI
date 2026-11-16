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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central CORS policy for the identity service. Only the origins listed in the {@code
 * app.cors.allowed-origins} property (env {@code CORS_ALLOWED_ORIGINS}, comma separated) may make
 * cross-origin requests. A wildcard origin is rejected at startup.
 */
@Configuration
public class CorsConfig {

  private final List<String> allowedOrigins;

  public CorsConfig(@Value("${app.cors.allowed-origins:}") String[] allowedOrigins) {
    this.allowedOrigins =
        Arrays.stream(allowedOrigins)
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toList());
    if (this.allowedOrigins.contains("*")) {
      throw new IllegalStateException(
          "app.cors.allowed-origins must list explicit origins; wildcard is not allowed");
    }
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/identity/**", configuration);
    return source;
  }
}

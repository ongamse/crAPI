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

package com.crapi.service.Impl;

import com.crapi.model.VehicleOwnership;
import com.crapi.repository.*;
import com.crapi.service.UserService;
import com.crapi.service.VehicleOwnershipService;
import com.crapi.utils.SMTPMailServer;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class VehicleOwnershipServiceImpl implements VehicleOwnershipService {

  @Autowired VehicleModelRepository vehicleModelRepository;

  @Autowired VehicleLocationRepository vehicleLocationRepository;

  @Autowired VehicleDetailsRepository vehicleDetailsRepository;

  @Autowired UserDetailsRepository userDetailsRepository;

  @Autowired UserService userService;

  @Autowired SMTPMailServer smtpMailServer;

  @Value("${api.gateway.url}")
  private String apiGatewayURL;

  @Value("${api.gateway.username}")
  private String apiGatewayUsername;

  @Value("${api.gateway.password}")
  private String apiGatewayPassword;

  // Optional truststore (CA bundle) used to verify the API gateway certificate, e.g. when the
  // gateway uses a private/self-signed CA. When unset the JVM default truststore is used.
  @Value("${api.gateway.truststore:}")
  private String apiGatewayTruststore;

  @Value("${api.gateway.truststore.password:}")
  private String apiGatewayTruststorePassword;

  private SSLContext sslContext() throws GeneralSecurityException, IOException {
    if (apiGatewayTruststore == null || apiGatewayTruststore.isBlank()) {
      return SSLContexts.createSystemDefault();
    }
    char[] password =
        (apiGatewayTruststorePassword == null || apiGatewayTruststorePassword.isEmpty())
            ? null
            : apiGatewayTruststorePassword.toCharArray();
    File truststoreFile = new File(apiGatewayTruststore);
    return SSLContexts.custom().loadTrustMaterial(truststoreFile, password).build();
  }

  public RestTemplate restTemplate() throws GeneralSecurityException, IOException {
    RestTemplateBuilder builder = new RestTemplateBuilder();
    // Certificate chain and hostname are verified (default hostname verifier).
    HttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(
                SSLConnectionSocketFactoryBuilder.create().setSslContext(sslContext()).build())
            .build();
    HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
    builder = builder.requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient));

    // Add basic auth header
    builder = builder.basicAuthentication(apiGatewayUsername, apiGatewayPassword);
    RestTemplate restTemplate = builder.build();
    return restTemplate;
  }

  /**
   * @param vin
   * @return List<VehicleOwnership>
   */
  @Override
  public List<VehicleOwnership> getPreviousOwners(String vin) {
    try {
      log.info("Getting vehicle ownership details for vin: " + vin);
      // get vehicle ownership from crapi. vin query param is required
      RestTemplate restTemplate = restTemplate();
      String ownershipUrl = apiGatewayURL + "/v1/vin/ownership?vin=" + vin;
      VehicleOwnership[] vehicleOwnerships =
          restTemplate.getForObject(ownershipUrl, VehicleOwnership[].class);
      if (vehicleOwnerships == null) {
        log.error("Fail to get vehicle ownerships");
        return List.of();
      }
      return Arrays.asList(vehicleOwnerships);
    } catch (Exception e) {
      log.error("Fail to get vehicle ownerships -> Message: {}", e);
    }
    return List.of();
  }
}

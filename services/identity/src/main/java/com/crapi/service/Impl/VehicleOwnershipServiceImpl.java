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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
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

  /** Optional operator-provided trust store (path) for the API gateway TLS certificate chain. */
  @Value("${api.gateway.truststore:}")
  private String apiGatewayTrustStore;

  @Value("${api.gateway.truststore.type:PKCS12}")
  private String apiGatewayTrustStoreType;

  @Value("${api.gateway.truststore.password:}")
  private String apiGatewayTrustStorePassword;

  /**
   * Builds an SSLContext that validates server certificates and hostnames. Uses the JVM default
   * trust store unless an operator-provided trust store is configured.
   */
  private SSLContext buildSslContext() throws GeneralSecurityException, IOException {
    if (apiGatewayTrustStore == null || apiGatewayTrustStore.isBlank()) {
      return SSLContext.getDefault();
    }
    KeyStore trustStore = KeyStore.getInstance(apiGatewayTrustStoreType);
    char[] password = null;
    if (apiGatewayTrustStorePassword != null && !apiGatewayTrustStorePassword.isEmpty()) {
      password = apiGatewayTrustStorePassword.toCharArray();
    }
    try (InputStream in = Files.newInputStream(Paths.get(apiGatewayTrustStore))) {
      trustStore.load(in, password);
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(trustStore);
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), null);
    return sslContext;
  }

  public RestTemplate restTemplate() throws GeneralSecurityException, IOException {
    RestTemplateBuilder builder = new RestTemplateBuilder();
    SSLConnectionSocketFactory csf =
        SSLConnectionSocketFactoryBuilder.create().setSslContext(buildSslContext()).build();
    HttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(csf).build();
    CloseableHttpClient httpClient =
        HttpClients.custom().setConnectionManager(connectionManager).build();
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

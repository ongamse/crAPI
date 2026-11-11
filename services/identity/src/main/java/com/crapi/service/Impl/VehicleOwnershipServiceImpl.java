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
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

  public RestTemplate restTemplate() {
    // Uses the JVM default SSL context: certificate chain and hostname are always verified.
    // To trust a private CA, point the JVM at a truststore via the standard
    // javax.net.ssl.trustStore / javax.net.ssl.trustStorePassword system properties
    // (for example through JAVA_TOOL_OPTIONS).
    RestTemplateBuilder builder = new RestTemplateBuilder();
    builder = builder.requestFactory(SimpleClientHttpRequestFactory::new);

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

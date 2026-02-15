package com.restaurant.kds_service.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for REST client to communicate with Order Service via API Gateway
 * Uses Apache HttpClient5 to support PATCH HTTP method
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Create Apache HttpClient5 which supports PATCH
        CloseableHttpClient httpClient = HttpClients.createDefault();

        // Configure RestTemplate to use Apache HttpClient
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}


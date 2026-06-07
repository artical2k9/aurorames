package com.mes.iam.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class KeycloakAdminConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Keycloak keycloak(
            @Value("${keycloak.admin.server-url:http://localhost:8080}") String serverUrl,
            @Value("${keycloak.admin.username:admin}") String username,
            @Value("${keycloak.admin.password:admin}") String password) {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(username)
                .password(password)
                .build();
    }
}

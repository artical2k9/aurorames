package com.mikemes.gateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityIT {

    static final WireMockServer DOWNSTREAM;

    static {
        DOWNSTREAM = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        DOWNSTREAM.start();
        DOWNSTREAM.stubFor(any(anyUrl()).willReturn(ok()));
    }

    @AfterAll
    static void stopWireMock() {
        DOWNSTREAM.stop();
    }

    @TestConfiguration
    static class TestRouteConfig {
        @Bean
        RouteLocator testRoutes(RouteLocatorBuilder builder) {
            return builder.routes()
                .route("downstream", r -> r.path("/api/**")
                    .uri("http://localhost:" + DOWNSTREAM.port()))
                .build();
        }
    }

    @LocalServerPort
    private int port;

    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @Test
    void no_jwt_returns_401() {
        client.get().uri("/api/roles")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void expired_jwt_returns_401() {
        // InvalidBearerTokenException is what JwtReactiveAuthenticationManager
        // produces from a JwtException; using it directly avoids onErrorMap binding issues with @MockBean
        when(jwtDecoder.decode(anyString()))
            .thenReturn(Mono.error(new InvalidBearerTokenException("Token expired")));

        client.get().uri("/api/roles")
            .header("Authorization", "Bearer expired.token")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void wrong_issuer_jwt_returns_401() {
        when(jwtDecoder.decode(anyString()))
            .thenReturn(Mono.error(new InvalidBearerTokenException("Invalid issuer")));

        client.get().uri("/api/roles")
            .header("Authorization", "Bearer wrong.issuer.token")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void valid_jwt_is_forwarded_to_downstream() {
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(validJwt()));

        client.get().uri("/api/roles")
            .header("Authorization", "Bearer valid.token")
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void internal_path_with_valid_jwt_returns_404() {
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(validJwt()));

        client.get().uri("/internal/keycloak-events")
            .header("Authorization", "Bearer valid.token")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void actuator_health_is_public() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk();
    }

    private static Jwt validJwt() {
        return Jwt.withTokenValue("valid.token")
            .header("alg", "RS256")
            .claim("sub", "test-user")
            .claim("iss", "http://localhost:8080/realms/mikemes")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    }

}

package com.mes.quality.inspectionplan.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads item-master records from inventory-service (item existence + part-number denorm).
 * Forwards the caller's JWT so inventory-service applies org scoping. Item data is referenced
 * by id only — quality-service never queries the inventory schema (§XI service boundary).
 */
@Component
public class InventoryServiceClient {

    private final RestClient restClient;

    public InventoryServiceClient(@Value("${mes.quality.inventory-service-url}") String inventoryServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(inventoryServiceUrl).build();
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() { };

    /** Returns the item summary if it exists for the caller's org, else empty (404 or no part number). */
    public Optional<ItemSummary> findItem(UUID itemId) {
        return restClient.get()
                .uri("/api/v1/item-master/{id}", itemId)
                .headers(headers -> currentBearerToken().ifPresent(headers::setBearerAuth))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return Optional.empty();
                    }
                    Map<String, Object> body = response.bodyTo(MAP_TYPE);
                    if (body == null || body.get("partNumber") == null) {
                        return Optional.empty();
                    }
                    return Optional.of(new ItemSummary(itemId, body.get("partNumber").toString()));
                });
    }

    private static Optional<String> currentBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    public record ItemSummary(UUID id, String partNumber) {
    }
}

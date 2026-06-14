package com.mes.engineering.workinstruction.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads skill definitions and evaluates operator qualifications against labour-service
 * (MES-11 contract). Forwards the caller's JWT so labour-service applies org scoping; skills
 * are referenced by id only — engineering-service never queries the labour schema
 * (Constitution §XI service boundary). A short timeout plus empty-on-failure semantics let the
 * caller fail closed when labour-service is slow or unavailable.
 */
@Component
public class LabourServiceClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public LabourServiceClient(
            @Value("${mes.engineering.labour-service-url}") String labourServiceUrl,
            @Value("${mes.engineering.labour-service-timeout-ms:2000}") long timeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(timeoutMs))
                .withReadTimeout(Duration.ofMillis(timeoutMs));
        ClientHttpRequestFactory requestFactory =
                ClientHttpRequestFactoryBuilder.detect().build(settings);
        this.restClient = RestClient.builder()
                .baseUrl(labourServiceUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Skill code + name for denormalisation; empty if the skill does not exist for the caller's org. */
    public Optional<SkillSummary> findSkill(UUID skillId) {
        try {
            return restClient.get()
                    .uri("/api/v1/labour/skills/{id}", skillId)
                    .headers(headers -> currentBearerToken().ifPresent(headers::setBearerAuth))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return Optional.empty();
                        }
                        Map<String, Object> body = response.bodyTo(MAP_TYPE);
                        if (body == null || body.get("skillCode") == null) {
                            return Optional.<SkillSummary>empty();
                        }
                        return Optional.of(new SkillSummary(
                                skillId,
                                String.valueOf(body.get("skillCode")),
                                body.get("name") == null ? "" : String.valueOf(body.get("name"))));
                    });
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * Evaluate an operator's qualification for the given skills. Returns empty when labour-service
     * cannot be reached or returns an error — the caller treats empty as a fail-closed signal
     * (VERIFICATION_UNAVAILABLE), never as "qualified".
     */
    @SuppressWarnings("unchecked")
    public Optional<QualificationResponse> evaluate(UUID employeeId, String iamUserId,
                                                    List<UUID> skillIds) {
        Map<String, Object> requestBody = new HashMap<>();
        if (employeeId != null) {
            requestBody.put("employeeId", employeeId.toString());
        }
        if (iamUserId != null) {
            requestBody.put("iamUserId", iamUserId);
        }
        requestBody.put("skillIds", skillIds.stream().map(UUID::toString).toList());

        try {
            return restClient.post()
                    .uri("/api/v1/labour/qualifications/evaluate")
                    .headers(headers -> currentBearerToken().ifPresent(headers::setBearerAuth))
                    .body(requestBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return Optional.<QualificationResponse>empty();
                        }
                        Map<String, Object> body = response.bodyTo(MAP_TYPE);
                        if (body == null) {
                            return Optional.<QualificationResponse>empty();
                        }
                        boolean active = Boolean.TRUE.equals(body.get("employeeActive"));
                        List<Map<String, Object>> rawResults =
                                (List<Map<String, Object>>) body.getOrDefault("results", List.of());
                        List<SkillStatus> results = rawResults.stream()
                                .map(LabourServiceClient::toSkillStatus)
                                .toList();
                        return Optional.of(new QualificationResponse(active, results));
                    });
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static SkillStatus toSkillStatus(Map<String, Object> r) {
        Object skillId = r.get("skillId");
        Object expiry = r.get("expiryDate");
        return new SkillStatus(
                skillId == null ? null : UUID.fromString(String.valueOf(skillId)),
                r.get("skillCode") == null ? null : String.valueOf(r.get("skillCode")),
                r.get("status") == null ? null : String.valueOf(r.get("status")),
                expiry == null ? null : LocalDate.parse(String.valueOf(expiry)));
    }

    private static Optional<String> currentBearerToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    public record SkillSummary(UUID id, String skillCode, String name) {
    }

    public record QualificationResponse(boolean employeeActive, List<SkillStatus> results) {
    }

    public record SkillStatus(UUID skillId, String skillCode, String status, LocalDate expiryDate) {
    }
}

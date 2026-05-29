package com.mes.common.security.privilege;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class PrivilegeRegistryClient {

    private final RestClient restClient;
    private final String webhookToken;
    private final int maxAttempts;
    private final long retryDelayMs;

    public PrivilegeRegistryClient(
            RestClient.Builder builder,
            @Value("${mes.security.iam-service-url:http://localhost:8085}") String iamServiceUrl,
            @Value("${mes.security.webhook-token:}") String webhookToken) {
        this(builder, iamServiceUrl, webhookToken, 3, 100L);
    }

    PrivilegeRegistryClient(RestClient.Builder builder, String iamServiceUrl, String webhookToken,
                            int maxAttempts, long retryDelayMs) {
        this.restClient = builder.baseUrl(iamServiceUrl).build();
        this.webhookToken = webhookToken;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public PrivilegeManifest fetchManifest() {
        return executeWithRetry(
                () -> restClient.get()
                        .uri("/internal/privileges")
                        .header("Authorization", "Bearer " + webhookToken)
                        .retrieve()
                        .body(PrivilegeManifest.class),
                "Failed to fetch privilege manifest");
    }

    public void register(String moduleName, List<PrivilegeRegistration> privileges) {
        Map<String, Object> body = Map.of("moduleName", moduleName, "privileges", privileges);
        executeWithRetry(() -> {
            restClient.post()
                    .uri("/internal/privileges/register")
                    .header("Authorization", "Bearer " + webhookToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        }, "Failed to register privileges");
    }

    private <T> T executeWithRetry(Supplier<T> action, String errorContext) {
        int attempt = 0;
        Exception lastException = null;
        while (attempt < maxAttempts) {
            try {
                return action.get();
            } catch (HttpClientErrorException e) {
                throw new PrivilegeRegistryException("Registry returned " + e.getStatusCode(), e);
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                attempt++;
                lastException = e;
                if (attempt < maxAttempts && retryDelayMs > 0) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new PrivilegeRegistryException("Retry interrupted", ie);
                    }
                }
            } catch (Exception e) {
                throw new PrivilegeRegistryException(errorContext, e);
            }
        }
        throw new PrivilegeRegistryException(
                "Registry unavailable after " + maxAttempts + " attempts", lastException);
    }
}

package com.mikemes.common.security.privilege;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@Component
public class PrivilegeRegistryClient {

    private final RestClient restClient;
    private final int maxAttempts;
    private final long retryDelayMs;

    public PrivilegeRegistryClient(
            RestClient.Builder builder,
            @Value("${mikemes.security.iam-service-url:http://localhost:8085}") String iamServiceUrl) {
        this(builder, iamServiceUrl, 3, 100L);
    }

    PrivilegeRegistryClient(RestClient.Builder builder, String iamServiceUrl, int maxAttempts, long retryDelayMs) {
        this.restClient = builder.baseUrl(iamServiceUrl).build();
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    public PrivilegeManifest fetchManifest() {
        int attempt = 0;
        Exception lastException = null;
        while (attempt < maxAttempts) {
            try {
                return restClient.get()
                        .uri("/internal/privileges")
                        .retrieve()
                        .body(PrivilegeManifest.class);
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
                throw new PrivilegeRegistryException("Failed to fetch privilege manifest", e);
            }
        }
        throw new PrivilegeRegistryException(
                "Registry unavailable after " + maxAttempts + " attempts", lastException);
    }
}

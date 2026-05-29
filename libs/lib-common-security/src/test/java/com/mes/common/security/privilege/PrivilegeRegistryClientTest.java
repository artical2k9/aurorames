package com.mes.common.security.privilege;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivilegeRegistryClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private PrivilegeRegistryClient client;

    @BeforeEach
    void setUp() {
        client = new PrivilegeRegistryClient(RestClient.builder(), wm.baseUrl(), "test-token", 3, 0L);
    }

    @Test
    void fetchManifest_200_returnsManifest() {
        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"rolePrivileges":{"ROLE_OPERATOR":["part.read","workorder.execute"]}}
                                """)));

        var manifest = client.fetchManifest();

        assertThat(manifest.getPrivilegesForRole("ROLE_OPERATOR"))
                .containsExactlyInAnyOrder("part.read", "workorder.execute");
    }

    @Test
    void fetchManifest_404_throwsPrivilegeRegistryException() {
        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.fetchManifest())
                .isInstanceOf(PrivilegeRegistryException.class);
    }

    @Test
    void fetchManifest_503ThenSuccess_retriesAndSucceeds() {
        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt2"));

        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt2")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt3"));

        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .inScenario("retry")
                .whenScenarioStateIs("attempt3")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"rolePrivileges":{"ROLE_OPERATOR":["part.read"]}}
                                """)));

        var manifest = client.fetchManifest();

        assertThat(manifest.getPrivilegesForRole("ROLE_OPERATOR")).containsExactly("part.read");
    }

    @Test
    void fetchManifest_allAttemptsReturn503_throwsPrivilegeRegistryException() {
        wm.stubFor(get(urlEqualTo("/internal/privileges"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.fetchManifest())
                .isInstanceOf(PrivilegeRegistryException.class)
                .hasMessageContaining("unavailable");
    }
}

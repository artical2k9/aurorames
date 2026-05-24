package com.mikemes.keycloak.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.events.EventListenerProvider;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventListenerProviderFactoryTest {

    private final AuditEventListenerProviderFactory factory = new AuditEventListenerProviderFactory();

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void getIdReturnsMesAuditListener() {
        assertThat(factory.getId()).isEqualTo("mes-audit-listener");
    }

    @Test
    void initWithoutBootstrapServersCreatesNoOpPublisher() {
        // ENV var not set in test environment — init should fall back to no-op properties
        factory.init(null);
        // No exception means no-op path succeeded
    }

    @Test
    void createReturnsNonNullProvider() {
        factory.init(null);
        EventListenerProvider provider = factory.create(null);
        assertThat(provider).isNotNull();
    }

    @Test
    void postInitDoesNotThrow() {
        factory.postInit(null);
    }

    @Test
    void closeBeforeInitDoesNotThrow() {
        // publisher is null before init — close must handle null gracefully
        factory.close();
    }

    @Test
    void closeAfterInitDoesNotThrow() {
        factory.init(null);
        factory.close();
    }
}

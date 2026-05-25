package com.mes.common.security.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OrganisationContextHolderTest {

    @AfterEach
    void cleanup() {
        OrganisationContextHolder.clear();
    }

    @Test
    void setAndGet_roundTrip() {
        OrganisationContextHolder.setOrgId("org-123");

        assertThat(OrganisationContextHolder.getOrgId()).contains("org-123");
    }

    @Test
    void clear_makesGetReturnEmpty() {
        OrganisationContextHolder.setOrgId("org-123");
        OrganisationContextHolder.clear();

        assertThat(OrganisationContextHolder.getOrgId()).isEmpty();
    }

    @Test
    void threadLocal_isolationBetweenThreads() throws InterruptedException {
        OrganisationContextHolder.setOrgId("org-main");

        var otherThreadValue = new AtomicReference<String>();
        var thread = new Thread(() -> otherThreadValue.set(
                OrganisationContextHolder.getOrgId().orElse("EMPTY")));
        thread.start();
        thread.join();

        assertThat(otherThreadValue.get()).isEqualTo("EMPTY");
        assertThat(OrganisationContextHolder.getOrgId()).contains("org-main");
    }
}

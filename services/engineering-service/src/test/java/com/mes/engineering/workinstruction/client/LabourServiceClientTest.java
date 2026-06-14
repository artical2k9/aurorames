package com.mes.engineering.workinstruction.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the fail-closed error mapping: when labour-service is unreachable, both calls return
 * empty (never a partial/"qualified" result). Uses a short timeout against a closed local port so
 * the connection is refused fast.
 */
class LabourServiceClientTest {

    private LabourServiceClient unreachableClient() {
        // Port 1 is not listening — connection is refused promptly; the 200ms timeout bounds any wait.
        return new LabourServiceClient("http://localhost:1", 200);
    }

    @Test
    void evaluateReturnsEmptyWhenLabourServiceUnreachable() {
        Optional<LabourServiceClient.QualificationResponse> result =
                unreachableClient().evaluate(UUID.randomUUID(), null, List.of(UUID.randomUUID()));
        assertThat(result).isEmpty();
    }

    @Test
    void findSkillReturnsEmptyWhenLabourServiceUnreachable() {
        Optional<LabourServiceClient.SkillSummary> result =
                unreachableClient().findSkill(UUID.randomUUID());
        assertThat(result).isEmpty();
    }
}

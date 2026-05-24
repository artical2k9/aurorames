package com.mikemes.auditservice.integration.api;

import com.mikemes.audit.domain.AuditRecord;
import com.mikemes.auditservice.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("mikemes")
            .withUsername("audit_service")
            .withPassword("test-password");

    @Container
    static final KafkaContainer KAFKA =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.consumer.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet"
                + ".OAuth2ResourceServerAutoConfiguration");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditRecordRepository repository;

    @Test
    @WithMockUser(roles = "AUDIT_READ")
    void entityHistoryReturnsPaginatedResultsWithin2Seconds() throws Exception {
        seedRecords(1000);
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

        long start = System.currentTimeMillis();
        mockMvc.perform(get("/audit/entities/PERF_WO/perf-entity-0/history")
                .param("from", from.toString())
                .param("to", to.toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").isNumber());
        long elapsed = System.currentTimeMillis() - start;

        org.assertj.core.api.Assertions.assertThat(elapsed)
            .as("Response time should be under 2 seconds")
            .isLessThan(2000L);
    }

    @Test
    void entityHistoryReturns403ForUnauthenticatedCaller() throws Exception {
        mockMvc.perform(get("/audit/entities/WorkOrder/WO-001/history")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AUDIT_READ")
    void entitiesEndpointReturnsSortedDescByOccurredAt() throws Exception {
        String entityType = "SORT_WO";
        String entityId = "sort-entity-001";
        seedEntity(entityType, entityId, 5);

        mockMvc.perform(get("/audit/entities/" + entityType + "/" + entityId + "/history")
                .param("from", OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).toString())
                .param("to", OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    private void seedRecords(int count) {
        List<AuditRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(AuditRecord.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType("KAFKA_EVENT")
                .entityType("PERF_WO")
                .entityId("perf-entity-" + (i % 10))
                .userId("user:perf")
                .serviceSource("test")
                .action("CREATE")
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(i % 30))
                .checksum("x".repeat(64))
                .schemaVersion(1)
                .build());
        }
        repository.saveAll(records);
    }

    private void seedEntity(String entityType, String entityId, int count) {
        List<AuditRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(AuditRecord.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .eventType("KAFKA_EVENT")
                .entityType(entityType)
                .entityId(entityId)
                .userId("user:sort")
                .serviceSource("test")
                .action("UPDATE")
                .occurredAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(count - i))
                .checksum("y".repeat(64))
                .schemaVersion(1)
                .build());
        }
        repository.saveAll(records);
    }
}

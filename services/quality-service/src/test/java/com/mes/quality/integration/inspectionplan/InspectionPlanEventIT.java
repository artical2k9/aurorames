package com.mes.quality.integration.inspectionplan;

import com.mes.quality.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class InspectionPlanEventIT extends BaseIntegrationTest {

    private static final String ORG_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PLANS = "/api/v1/inspection-plans";
    private static final String TOPIC = "quality.inspection-plan.approved";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void resetStubs() {
        INVENTORY_WIREMOCK.resetAll();
    }

    private String adminToken() {
        return buildToken(ORG_ID, List.of("SYSTEM_ADMIN"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void approvalPublishesEventWithAllFields() {
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("quality-event-it", "true", embeddedKafka);
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", StringDeserializer.class);

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);

            String itemId = UUID.randomUUID().toString();
            INVENTORY_WIREMOCK.stubFor(get(urlPathEqualTo("/api/v1/item-master/" + itemId))
                    .willReturn(okJson("{\"id\":\"" + itemId + "\",\"partNumber\":\"PN-EVT\"}")));
            ResponseEntity<Map> created = restTemplate.exchange(
                    PLANS, HttpMethod.POST,
                    jsonRequest(adminToken(), Map.of("itemId", itemId, "name", "Event plan")),
                    Map.class);
            String planId = created.getBody().get("id").toString();

            Map<String, Object> charBody = new HashMap<>();
            charBody.put("characteristicNumber", 10);
            charBody.put("name", "Bore");
            charBody.put("source", "DESIGN");
            charBody.put("characteristicType", "SPECIFIC");
            charBody.put("sampleSizeRule", "ALL");
            charBody.put("nominalValue", 10.0);
            restTemplate.exchange(PLANS + "/" + planId + "/characteristics", HttpMethod.POST,
                    jsonRequest(adminToken(), charBody), Map.class);
            restTemplate.exchange(PLANS + "/" + planId + "/submit", HttpMethod.POST,
                    jsonRequest(adminToken(), Map.of()), Map.class);
            ResponseEntity<Map> approved = restTemplate.exchange(
                    PLANS + "/" + planId + "/approve", HttpMethod.POST,
                    jsonRequest(adminToken(), Map.of()), Map.class);
            assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Other IT classes share the embedded topic — find the record for this unique plan.
            ConsumerRecords<String, String> records =
                    KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(10));
            ConsumerRecord<String, String> match = null;
            for (ConsumerRecord<String, String> r : records.records(TOPIC)) {
                if (planId.equals(r.key())) {
                    match = r;
                    break;
                }
            }
            assertThat(match).as("approval event for plan " + planId).isNotNull();
            assertThat(match.value())
                    .contains("\"eventType\":\"quality.inspection-plan.approved\"")
                    .contains("\"planId\":\"" + planId + "\"")
                    .contains("\"itemId\":\"" + itemId + "\"")
                    .contains("\"partNumber\":\"PN-EVT\"")
                    .contains("\"revision\":0");
        }
    }
}

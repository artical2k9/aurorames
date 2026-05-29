package com.mes.workorder.integration.itemmaster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.workorder.integration.BaseIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AS5553IT extends BaseIntegrationTest {

    static final String ORG_ID = "00000000-0000-0000-0000-000000000001";
    static final String ITEM_BASE = "/api/v1/item-master";
    static final String BOM_BASE = "/api/v1/boms";
    static final String ITEM_MASTER_TOPIC = "work-order.item-master.events";

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void patchAs5553FieldsArePersistedAndReturnedOnGet() {
        String token = engineerToken();
        ResponseEntity<Map> created = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST,
                jsonRequest(token, baseItemRequest("AS5553-PATCH-001", "A")), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String itemId = extractId(created.getHeaders().getLocation().getPath());

        Map<String, Object> patch = new HashMap<>();
        patch.put("counterfeitRiskLevel", "HIGH");
        patch.put("approvedSuppliers", List.of("Supplier-A", "Supplier-B"));
        patch.put("verificationRequired", true);

        ResponseEntity<Map> patched = restTemplate.exchange(
                ITEM_BASE + "/" + itemId, HttpMethod.PATCH, jsonRequest(token, patch), Map.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patched.getBody()).extractingByKey("counterfeitRiskLevel").isEqualTo("HIGH");
        assertThat(patched.getBody()).extractingByKey("verificationRequired").isEqualTo(true);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> got = restTemplate.exchange(
                ITEM_BASE + "/" + itemId, HttpMethod.GET, bearerRequest(token), Map.class);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody()).extractingByKey("counterfeitRiskLevel").isEqualTo("HIGH");
        @SuppressWarnings("unchecked")
        List<Object> suppliers = (List<Object>) got.getBody().get("approvedSuppliers");
        assertThat(suppliers).containsExactlyInAnyOrder("Supplier-A", "Supplier-B");
        assertThat(got.getBody()).extractingByKey("verificationRequired").isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByCounterfeitRiskLevelHighReturnsOnlyHighRiskItems() {
        String token = engineerToken();

        Map<String, Object> highReq = new HashMap<>(baseItemRequest("AS5553-FILTER-HIGH-001", "A"));
        highReq.put("counterfeitRiskLevel", "HIGH");
        restTemplate.exchange(ITEM_BASE, HttpMethod.POST, jsonRequest(token, highReq), Map.class);

        Map<String, Object> lowReq = new HashMap<>(baseItemRequest("AS5553-FILTER-LOW-001", "A"));
        lowReq.put("counterfeitRiskLevel", "LOW");
        restTemplate.exchange(ITEM_BASE, HttpMethod.POST, jsonRequest(token, lowReq), Map.class);

        ResponseEntity<Map> result = restTemplate.exchange(
                ITEM_BASE + "?counterfeitRiskLevel=HIGH",
                HttpMethod.GET, bearerRequest(token), Map.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allMatch(item -> "HIGH".equals(item.get("counterfeitRiskLevel")));
    }

    @Test
    void explosionNodeHasCounterfeitRiskAlertTrueForHighRiskComponent() {
        String token = engineerToken();
        String parentId = createItem(token, "AS5553-EXPL-PARENT-001", "A", null);
        String highRiskId = createItem(token, "AS5553-EXPL-HIGH-001", "A", "HIGH");

        String bomId = createBom(token, parentId, "REV-A");
        addLine(token, bomId, highRiskId, "010");

        ResponseEntity<List> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/explosion?format=flat",
                HttpMethod.GET, bearerRequest(token), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody())
                .extracting(n -> ((Map<?, ?>) n).get("counterfeitRiskAlert"))
                .containsExactly(Boolean.TRUE);
    }

    @Test
    void as5553RiskAddedEventPublishedWhenHighRiskComponentAddedToBom() throws Exception {
        String token = engineerToken();
        String parentId = createItem(token, "AS5553-EVT-PARENT-001", "A", null);
        String highRiskId = createItem(token, "AS5553-EVT-HIGH-001", "A", "HIGH");
        String bomId = createBom(token, parentId, "REV-A");

        try (KafkaConsumer<String, String> consumer = openConsumerAtEnd(ITEM_MASTER_TOPIC)) {
            ResponseEntity<Map> lineResp = restTemplate.exchange(
                    BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                    jsonRequest(token, Map.of(
                            "componentItemId", highRiskId,
                            "quantity", 1.0,
                            "unitOfMeasure", "EA",
                            "findNumber", "010")),
                    Map.class);
            assertThat(lineResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode event = pollForEvent(consumer, "compliance.as5553-risk-added", 5);
            assertThat(event)
                    .as("compliance.as5553-risk-added event not published for HIGH-risk component")
                    .isNotNull();
            assertThat(event.path("entityId").asText()).isEqualTo(highRiskId);
        }
    }

    @Test
    void as5553RiskAddedEventNotPublishedForLowRiskComponent() throws Exception {
        String token = engineerToken();
        String parentId = createItem(token, "AS5553-EVT-PARENT-002", "A", null);
        String lowRiskId = createItem(token, "AS5553-EVT-LOW-001", "A", "LOW");
        String bomId = createBom(token, parentId, "REV-A");

        try (KafkaConsumer<String, String> consumer = openConsumerAtEnd(ITEM_MASTER_TOPIC)) {
            addLine(token, bomId, lowRiskId, "010");

            JsonNode event = pollForEvent(consumer, "compliance.as5553-risk-added", 3);
            assertThat(event)
                    .as("compliance.as5553-risk-added must NOT be published for LOW-risk component")
                    .isNull();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String engineerToken() {
        return buildToken(ORG_ID, List.of("ENGINEER"));
    }

    private HttpEntity<?> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String extractId(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String createItem(String token, String partNumber, String revision, String riskLevel) {
        Map<String, Object> req = new HashMap<>(baseItemRequest(partNumber, revision));
        if (riskLevel != null) {
            req.put("counterfeitRiskLevel", riskLevel);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                ITEM_BASE, HttpMethod.POST, jsonRequest(token, req), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return extractId(response.getHeaders().getLocation().getPath());
    }

    private String createBom(String token, String parentItemId, String bomRevision) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE, HttpMethod.POST,
                jsonRequest(token, Map.of("parentItemId", parentItemId, "bomRevision", bomRevision)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private void addLine(String token, String bomId, String componentItemId, String findNumber) {
        ResponseEntity<Map> response = restTemplate.exchange(
                BOM_BASE + "/" + bomId + "/lines", HttpMethod.POST,
                jsonRequest(token, Map.of(
                        "componentItemId", componentItemId,
                        "quantity", 1.0,
                        "unitOfMeasure", "EA",
                        "findNumber", findNumber)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private KafkaConsumer<String, String> openConsumerAtEnd(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-as5553-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    private JsonNode pollForEvent(KafkaConsumer<String, String> consumer, String eventType, int timeoutSeconds)
            throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (var msg : records) {
                JsonNode node = objectMapper.readTree(msg.value());
                if (eventType.equals(node.path("eventType").asText())) {
                    return node;
                }
            }
        }
        return null;
    }
}

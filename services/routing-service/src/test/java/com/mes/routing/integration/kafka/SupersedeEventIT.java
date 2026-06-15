package com.mes.routing.integration.kafka;

import com.mes.routing.integration.BaseIntegrationTest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SupersedeEventIT extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAP =
            new ParameterizedTypeReference<>() { };

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private String admin() {
        return buildToken(DEV_ORG, List.of("SYSTEM_ADMIN"));
    }

    private String standardRouteTypeId() {
        ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                "/api/v1/routing/route-types", HttpMethod.GET, bearerRequest(admin()), LIST_OF_MAP);
        return (String) resp.getBody().stream()
                .filter(m -> "STANDARD".equals(m.get("code"))).findFirst().orElseThrow().get("id");
    }

    private String createRoute(String bomRevisionId) {
        ResponseEntity<Map> resp = restTemplate.exchange("/api/v1/routes", HttpMethod.POST,
                jsonRequest(admin(), Map.of("partId", UUID.randomUUID().toString(), "partRevision", "A",
                        "routeTypeId", standardRouteTypeId(), "bomRevisionId", bomRevisionId,
                        "reasonForRevision", "Initial")), Map.class);
        return (String) resp.getBody().get("id");
    }

    private void publish(String topic, String json) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, json));
            producer.flush();
        }
    }

    @Test
    void bomRevisionSuperseded_flagsPinnedRoute() {
        String bomRevisionId = UUID.randomUUID().toString();
        String routeId = createRoute(bomRevisionId);

        publish("inventory.bom.revision.superseded", "{\"revisionId\":\"" + bomRevisionId + "\"}");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ResponseEntity<Map> r = restTemplate.exchange("/api/v1/routes/" + routeId,
                    HttpMethod.GET, bearerRequest(admin()), Map.class);
            assertThat(r.getBody().get("bomRevisionSuperseded")).isEqualTo(true);
            assertThat(r.getBody().get("status")).isEqualTo("DRAFT"); // never auto-changes status
        });
    }
}

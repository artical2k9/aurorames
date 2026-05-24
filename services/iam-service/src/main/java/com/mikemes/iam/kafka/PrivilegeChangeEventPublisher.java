package com.mikemes.iam.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PrivilegeChangeEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public PrivilegeChangeEventPublisher(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${mikemes.security.privilege-changes-topic:iam.privilege-changes}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishGrant(String roleName, String privilegeKey, UUID orgId) {
        publish(roleName);
    }

    public void publishRevoke(String roleName, String privilegeKey, UUID orgId) {
        publish(roleName);
    }

    private void publish(String roleName) {
        kafkaTemplate.send(topic, roleName, roleName);
    }
}

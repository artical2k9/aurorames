package com.mes.keycloak.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mes.events.audit.AuditEventMessage;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.Properties;

public class KafkaAuditPublisher {

    private static final Logger LOG = Logger.getLogger(KafkaAuditPublisher.class.getName());

    private final Producer<String, String> producer;
    private final String topic;
    private final ObjectMapper mapper;

    KafkaAuditPublisher(Producer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
        this.mapper = buildMapper();
    }

    public KafkaAuditPublisher(Properties producerConfig, String publishTopic) {
        this(
            new org.apache.kafka.clients.producer.KafkaProducer<>(
                producerConfig,
                new StringSerializer(),
                new StringSerializer()
            ),
            publishTopic
        );
    }

    public void publish(AuditEventMessage message) {
        try {
            String json = mapper.writeValueAsString(message);
            String partitionKey = message.entityType() + ":" + message.entityId();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, json);
            producer.send(record);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to serialise AuditEventMessage eventId=" + message.eventId(), e);
        }
    }

    public void close() {
        producer.close();
    }

    private ObjectMapper buildMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }
}

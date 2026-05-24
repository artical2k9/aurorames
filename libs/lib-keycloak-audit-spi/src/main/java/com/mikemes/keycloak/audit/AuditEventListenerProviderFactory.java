package com.mikemes.keycloak.audit;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Properties;
import java.util.logging.Logger;

public class AuditEventListenerProviderFactory implements EventListenerProviderFactory {

    private static final Logger LOG =
        Logger.getLogger(AuditEventListenerProviderFactory.class.getName());
    private static final String ID = "mes-audit-listener";
    private static final String ENV_BOOTSTRAP_SERVERS = "KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS";
    private static final String ENV_TOPIC = "KEYCLOAK_AUDIT_KAFKA_TOPIC";
    private static final String DEFAULT_TOPIC = "mes.audit.events";

    private KafkaAuditPublisher publisher;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new AuditEventListenerProvider(publisher);
    }

    @Override
    public void init(Config.Scope config) {
        String bootstrapServers = System.getenv(ENV_BOOTSTRAP_SERVERS);
        String topic = System.getenv().getOrDefault(ENV_TOPIC, DEFAULT_TOPIC);
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            LOG.warning("KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS not set — audit SPI will not publish events");
            publisher = new KafkaAuditPublisher(buildNoOpProducerProperties(), topic);
        } else {
            Properties props = buildProducerProperties(bootstrapServers);
            publisher = new KafkaAuditPublisher(props, topic);
            LOG.info("MES audit SPI initialised — topic=" + topic + " servers=" + bootstrapServers);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        if (publisher != null) {
            publisher.close();
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    private Properties buildProducerProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty("acks", "1");
        props.setProperty("retries", "3");
        props.setProperty("max.block.ms", "2000");
        return props;
    }

    private Properties buildNoOpProducerProperties() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:9092");
        props.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty("max.block.ms", "100");
        props.setProperty("request.timeout.ms", "100");
        return props;
    }
}

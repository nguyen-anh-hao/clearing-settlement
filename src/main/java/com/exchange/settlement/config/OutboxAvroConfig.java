package com.exchange.settlement.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures the Confluent Avro serializer used to frame outbox payloads. It
 * writes Confluent's wire format (magic byte + schema id + Avro bytes) into the
 * outbox `payload` column; Debezium CDC later relays those bytes verbatim to
 * Kafka and Schema Registry holds the schemas.
 *
 * <p>Subject naming uses {@link RecordNameStrategy} so the subject is the
 * record's fully-qualified name (e.g. {@code com.exchange.accounting.TradeSettledEvent})
 * rather than the topic — required for the outbox pattern where the topic is
 * not known at write time.</p>
 */
@Configuration
public class OutboxAvroConfig {

    @Value("${app.accounting.schema-registry-url:http://localhost:8081}")
    private String schemaRegistryUrl;

    @Bean
    public KafkaAvroSerializer outboxAvroSerializer() {
        Map<String, Object> props = new HashMap<>();
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                RecordNameStrategy.class.getName());
        props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
        props.put(AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION, true);

        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        serializer.configure(props, false);
        return serializer;
    }
}

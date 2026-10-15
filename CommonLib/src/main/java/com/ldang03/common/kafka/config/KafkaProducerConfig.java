package com.ldang03.common.kafka.config;

//import com.ldang03.common.kafka.interceptor.CorrelationIdProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Messages are JSON strings (see EventEnvelope / EventJsonMapper), so key and value
 * both use StringSerializer: the JSON is sent exactly as built, never encoded twice.
 */
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProperties properties) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, properties.getProducer().getAcks());
        configProps.put(ProducerConfig.RETRIES_CONFIG, properties.getProducer().getRetries());
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, properties.getProducer().isEnableIdempotence());
//        configProps.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, CorrelationIdProducerInterceptor.class.getName());

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}

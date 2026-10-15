package com.ldang03.common.kafka.publisher;

import com.ldang03.common.kafka.config.KafkaProperties;
import com.ldang03.common.kafka.event.EventEnvelope;
import com.ldang03.common.kafka.event.EventJsonMapper;
import com.ldang03.common.kafka.event.EventType;
import com.ldang03.common.kafka.exception.EventPublishException;
import com.ldang03.common.kafka.util.KafkaTopicUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaProperties properties;

    private final EventJsonMapper eventJsonMapper;

    @Override
    public <T> EventEnvelope<T> send(String topic, String key, EventType eventType, String traceId, T payload) {
        EventEnvelope<T> envelope = eventJsonMapper.wrap(eventType, traceId, payload);
        sendJson(topic, key, eventJsonMapper.toJson(envelope));
        return envelope;
    }

    @Override
    public void sendJson(String topic, String key, String json) {
        String resolvedTopic = KafkaTopicUtil.resolveTopic(properties, topic);

        try {
            kafkaTemplate.send(resolvedTopic, key, json)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // the body is not logged: it may contain account numbers / amounts
            log.info("Published message key={} topic={}", key, resolvedTopic);

        } catch (Exception ex) {

            log.error("Failed to publish message key={} topic={}", key, resolvedTopic, ex);

            throw new EventPublishException(
                    "Failed to publish message to topic " + resolvedTopic,
                    ex);
        }
    }
}

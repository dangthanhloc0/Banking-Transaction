package com.ldang03.common.kafka.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldang03.common.uuid.UuidUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class EventJsonMapper {

    private final ObjectMapper objectMapper;

    private final String source;

    public EventJsonMapper(ObjectMapper objectMapper,
                           @Value("${spring.application.name:unknown}") String source) {
        this.objectMapper = objectMapper;
        this.source = source;
    }


    public <T> EventEnvelope<T> wrap(EventType eventType, String traceId, T payload) {
        String resolvedTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        return new EventEnvelope<>(
                UuidUtils.randomV7().toString(),
                eventType.name(),
                source,
                Instant.now(),
                resolvedTraceId,
                payload);
    }

    public String toJson(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + envelope.eventId(), e);
        }
    }


    public <T> EventEnvelope<T> read(String json, Class<T> payloadType) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        return objectMapper.readValue(json, type);
    }
}

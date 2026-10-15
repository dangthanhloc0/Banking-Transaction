package com.ldang03.common.kafka.publisher;

import com.ldang03.common.kafka.event.EventEnvelope;
import com.ldang03.common.kafka.event.EventType;

public interface EventPublisher {

    /**
     * Wraps the payload in an {@link EventEnvelope}, serializes it to JSON and sends it.
     * Do not call this inside a DB transaction: use the outbox and {@link #sendJson} instead.
     *
     * @return the envelope that was sent (eventId, traceId...)
     */
    <T> EventEnvelope<T> send(String topic, String key, EventType eventType, String traceId, T payload);

    /**
     * Sends an already-built JSON string as-is, e.g. an envelope stored in the outbox.
     */
    void sendJson(String topic, String key, String json);
}

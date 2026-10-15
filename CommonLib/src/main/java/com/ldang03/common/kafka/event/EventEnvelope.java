package com.ldang03.common.kafka.event;

import java.time.Instant;

/**
 * Standard JSON shape of every Kafka message:
 * <pre>
 * {
 *   "eventId": "...",          unique id, used by consumers to drop duplicates
 *   "eventType": "TRANSFER_REQUESTED",
 *   "source": "transaction-service",
 *   "occurredAt": "2026-10-10T08:00:00Z",
 *   "traceId": "...",
 *   "payload": { ...business data... }
 * }
 * </pre>
 * The schema version lives in the topic name (e.g. {@code payment.transfer.requested.v1}).
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String source,
        Instant occurredAt,
        String traceId,
        T payload) {
}

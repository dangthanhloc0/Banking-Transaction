package com.ldang03.transaction.entity;

import com.ldang03.common.customGenerateUUID.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "outbox_events", indexes = {@Index(name = "idx_outbox_pending", columnList = "status, next_retry_at, created_at")})
@Getter
@Setter
public class OutBoxEvent {

    @Id
    @GeneratedUuidV7
    private UUID outboxEventId;

    @Column(name = "aggregate_id", nullable = false, unique = true, length = 100)
    private String aggregateId ;

    @Column(name = "event_key", nullable = false, unique = true, length = 100)
    private String eventKey;

    private String eventType = "AccountTransfer";

    @Column(name = "max_attempt", nullable = false)
    private int maxAttempt = 0 ;

    @Column(name = "attempt", nullable = false)
    private int attempt = 0;

    @Column(name = "wait_time_in_seconds", nullable = false)
    private int waitTimeInSeconds;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "trace_context", length = 500)
    private String traceContext;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    private String topic;

    private String lastError;

    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;




}

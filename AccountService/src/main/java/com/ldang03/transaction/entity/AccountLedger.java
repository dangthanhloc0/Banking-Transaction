package com.ldang03.transaction.entity;


import com.ldang03.common.customGenerateUUID.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "account_ledgers", uniqueConstraints = {@UniqueConstraint(
        name = "idx_idempotency_key",
        columnNames = "idempotency_key"
)}, indexes = {@Index(name = "idx_idempotency_key", columnList = "idempotency_key")})
@Getter
@Setter
public class AccountLedger {
    @Id
    @GeneratedUuidV7
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    private Status statusEnum;

    private String failedReason;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;


    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

//    @Column(name = "request_hash", nullable = false, length = 64)
//    private String requestHash;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Version
    private Long version;

    @CreationTimestamp
    private java.time.Instant createdAt;

    @UpdateTimestamp
    private java.time.Instant updatedAt;


}


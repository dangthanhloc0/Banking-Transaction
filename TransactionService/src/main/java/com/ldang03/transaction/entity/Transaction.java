package com.ldang03.transaction.entity;

import com.ldang03.common.customGenerateUUID.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedUuidV7
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "debtor_account", nullable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    private TransactionStatus transactionStatus;

    private String failedReason;

    @CreationTimestamp
    private LocalDateTime transactionCreatedAt;

    @UpdateTimestamp
    private LocalDateTime transactionUpdatedAt;

    private LocalDateTime transactionDeletedAt;
}

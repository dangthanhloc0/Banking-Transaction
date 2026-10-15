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
@Table(name = "accounts", indexes =
        {@Index(name = "idx_account_number", columnList = "account_number,user_id")})
@Getter
@Setter
public class Account {

    @Id
    @GeneratedUuidV7
    private UUID accountId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, length = 20, unique = true)
    private String accountNumber;

    @Column(name = "account_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal accountBalance;

    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @CreationTimestamp
    private LocalDateTime accountCreatedAt;

    @UpdateTimestamp
    private LocalDateTime accountUpdatedAt;

    private LocalDateTime accountDeletedAt;
}

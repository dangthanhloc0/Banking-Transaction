package com.ldang03.transaction.repository;

import com.ldang03.transaction.entity.Transaction;
import com.ldang03.transaction.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("""
        UPDATE Transaction t
        SET t.transactionStatus = :status, t.failedReason = :failedReason , t.transactionUpdatedAt = :now
        WHERE t.transactionId = :transactionId AND t.transactionStatus = :expectStatus
    """)
    int updateStatusIf(@Param("transactionId") UUID transactionId, @Param("status") TransactionStatus status, @Param("expectStatus") TransactionStatus expectStatus , @Param("failedReason") String failedReason,   @Param("now") LocalDateTime now);

}

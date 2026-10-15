package com.ldang03.transaction.repository;

import com.ldang03.transaction.entity.OutBoxEvent;
import com.ldang03.transaction.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Repository
public interface OutboxRepository extends JpaRepository<OutBoxEvent, UUID> {
    @Query(value = """
    SELECT * FROM outbox_events 
    WHERE status IN ('PENDING', 'PROCESSING') AND next_retry_at <= :now
    ORDER BY created_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
""",nativeQuery = true)
    List<OutBoxEvent> findOutboxEvent(@Param("limit") int limit, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
    UPDATE OutBoxEvent o
    SET o.status = :status, o.nextRetryAt = :lease
    WHERE o.outboxEventId IN :ids
    """)
    int claim(@Param("ids") List<UUID> ids, @Param("status") OutboxStatus status, @Param("lease") LocalDateTime lease);

    @Modifying
    @Transactional
    @Query("""
    UPDATE OutBoxEvent o
    SET o.status = :status, o.publishedAt = :now
    WHERE o.outboxEventId IN :ids AND o.status = :expected
    """)
    int markPublished(@Param("ids") List<UUID> ids, @Param("status") OutboxStatus status,
                      @Param("now") LocalDateTime now, @Param("expected") OutboxStatus expected);

}

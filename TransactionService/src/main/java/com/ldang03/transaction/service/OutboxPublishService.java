package com.ldang03.transaction.service;

import com.ldang03.common.kafka.publisher.EventPublisher;
import com.ldang03.common.trace.TraceSupport;
import com.ldang03.transaction.entity.OutBoxEvent;
import com.ldang03.transaction.entity.OutboxStatus;
import com.ldang03.transaction.repository.OutboxRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class OutboxPublishService {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OutboxPublishService.class);


    private static final int MAX_ERROR_LENGTH = 255;

    private final OutboxRepository outboxRepository;

    private final EventPublisher eventPublisher;

    private final TraceSupport traceSupport;

    private final ExecutorService executor = Executors.newFixedThreadPool(20);


    private static final int LEASE_SECONDS = 30;

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private final TransactionTemplate transactionTemplate;

    public List<OutBoxEvent> claimBatch() {
        return transactionTemplate.execute(status -> {
            List<OutBoxEvent> outboxEvents = outboxRepository.findOutboxEvent(200, LocalDateTime.now());
            if (!outboxEvents.isEmpty()) {
                List<UUID> ids = outboxEvents.stream().map(OutBoxEvent::getOutboxEventId).toList();
                outboxRepository.claim(ids, OutboxStatus.PROCESSING, LocalDateTime.now().plusSeconds(LEASE_SECONDS));
            }
            return outboxEvents;
        });
    }


    @Scheduled(fixedDelay = 200)
    public void publishOutboxMessages() {
        List<OutBoxEvent> outboxEvents = claimBatch();
        if(outboxEvents.isEmpty()) {
            return;
        }
        Queue<UUID> published = new ConcurrentLinkedQueue<>();
        List<CompletableFuture<Void>> tasks = outboxEvents.stream()
                .map(outboxEvent -> CompletableFuture.runAsync(() -> {
                    traceSupport.runIn(outboxEvent.getTraceContext(), "outbox-relay", () -> {
                        try {
                            log.info("Process event id={} topic={}", outboxEvent.getOutboxEventId(), outboxEvent.getTopic());
                            eventPublisher.sendJson(outboxEvent.getTopic(), outboxEvent.getEventKey(), outboxEvent.getPayload());
                            published.add(outboxEvent.getOutboxEventId());
                            log.info("Successfully published outbox event id={} topic={}", outboxEvent.getOutboxEventId(), outboxEvent.getTopic());
                        } catch (Exception e) {
                            log.error("Failed to publish outbox event id={} topic={}", outboxEvent.getOutboxEventId(), outboxEvent.getTopic(), e);
                            try {
                                retryOutboxEvent(outboxEvent, e);
                            } catch (Exception updateError) {
                                log.error("Cannot update outbox event id={}", outboxEvent.getOutboxEventId(), updateError);
                            }
                        }
                    });
                }, executor))
                .toList();
        tasks.forEach(CompletableFuture::join);

        if (!published.isEmpty()) {
            outboxRepository.markPublished(new ArrayList<>(published), OutboxStatus.PUBLISHED,
                    LocalDateTime.now(), OutboxStatus.PROCESSING);
        }
    }




    public void retryOutboxEvent(OutBoxEvent outboxEvent, Exception e) {
        int attemptCount = outboxEvent.getAttempt() + 1;
        outboxEvent.setAttempt(attemptCount);
        outboxEvent.setLastError(truncate(e.getMessage() != null ? e.getMessage() : e.getClass().getName()));

        if (attemptCount < outboxEvent.getMaxAttempt()) {
            log.info("Retrying outbox event id={} topic={} attempt={} nextRetryAt={}", outboxEvent.getOutboxEventId(), outboxEvent.getTopic(), attemptCount, LocalDateTime.now().plusSeconds((long) outboxEvent.getWaitTimeInSeconds() * attemptCount));
            outboxEvent.setNextRetryAt(LocalDateTime.now().plusSeconds((long) outboxEvent.getWaitTimeInSeconds() * attemptCount));
            outboxEvent.setStatus(OutboxStatus.PENDING);
        } else {
            log.info("Outbox event id={} topic={} attempt={} reached max attempts. Marking as FAILED.", outboxEvent.getOutboxEventId(), outboxEvent.getTopic(), attemptCount);
            outboxEvent.setStatus(OutboxStatus.FAILED);
        }

        outboxRepository.save(outboxEvent);
    }

    private String truncate(String value) {
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}

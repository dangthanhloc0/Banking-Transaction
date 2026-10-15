package com.ldang03.transaction.consumer;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.ldang03.common.kafka.event.EventEnvelope;
import com.ldang03.common.kafka.event.EventJsonMapper;
import com.ldang03.common.kafka.publisher.EventPublisher;
import com.ldang03.common.kafka.util.KafkaTopics;
import com.ldang03.transaction.entity.TransactionStatus;
import com.ldang03.transaction.model.TransferCompletedRequest;
import com.ldang03.transaction.model.TransferFailedRequest;
import com.ldang03.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(TransferRequestedListener.class);

    private final EventJsonMapper eventJsonMapper;
    private final EventPublisher eventPublisher;
    private final TransactionService transactionService;


    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            dltTopicSuffix = ".DLT",
            exclude = JsonProcessingException.class,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = KafkaTopics.TRANSFER_COMPLETED, concurrency = "3")
    public void processCompleteTransaction(String json) throws JsonProcessingException {
        EventEnvelope<TransferCompletedRequest> event = eventJsonMapper.read(json, TransferCompletedRequest.class);
        log.info("Update Transaction eventId={} transactionId={}", event.eventId(), event.payload().transactionId());
        transactionService.updateTransactionStatus(event.payload().transactionId(), TransactionStatus.SUCCESS, null);

    }


    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            dltTopicSuffix = ".DLT",
            exclude = JsonProcessingException.class,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = KafkaTopics.TRANSFER_FAILED, concurrency = "3")
    public void processFailedTransaction(String json) throws JsonProcessingException {

        EventEnvelope<TransferFailedRequest> event = eventJsonMapper.read(json, TransferFailedRequest.class);
        log.info("Update Transaction eventId={} transactionId={}", event.eventId(), event.payload().transactionId());
        transactionService.updateTransactionStatus(event.payload().transactionId(), TransactionStatus.FAILED, event.payload().reason());

    }

}

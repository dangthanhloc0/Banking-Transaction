package com.ldang03.transaction.consumer;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.ldang03.common.exception.BusinessException;
import com.ldang03.common.exception.NotActiveException;
import com.ldang03.common.exception.NotEnoughMoneyException;
import com.ldang03.common.exception.NotFoundException;
import com.ldang03.common.kafka.event.EventEnvelope;
import com.ldang03.common.kafka.event.EventJsonMapper;
import com.ldang03.common.kafka.event.EventType;
import com.ldang03.common.kafka.publisher.EventPublisher;
import com.ldang03.common.kafka.util.KafkaTopics;
import com.ldang03.transaction.model.AccountTransfer;
import com.ldang03.transaction.model.TransferCompletedPayload;
import com.ldang03.transaction.model.TransferFailedPayload;
import com.ldang03.transaction.model.TransferRequestedPayload;
import com.ldang03.transaction.service.AccountService;
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
    private final AccountService accountService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            dltTopicSuffix = ".DLT",
            exclude = JsonProcessingException.class,
            kafkaTemplate = "kafkaTemplate")
    @KafkaListener(topics = KafkaTopics.TRANSFER_REQUESTED, concurrency = "3")
    public void onTransferRequested(String json) throws JsonProcessingException {
        EventEnvelope<TransferRequestedPayload> event = eventJsonMapper.read(json, TransferRequestedPayload.class);
        TransferRequestedPayload payload = event.payload();
        log.info("Received TRANSFER_REQUESTED eventId={} transactionId={}", event.eventId(), payload.transactionId());
        AccountTransfer command = new AccountTransfer();
        command.setIdempotencyKey(payload.transactionId());
        command.setDebtorAccount(payload.debtorAccount());
        command.setCreditorAccount(payload.creditorAccount());
        command.setAmount(payload.amount());
        command.setTraceId(event.traceId());

        try {
            accountService.transferMoney(command);
            eventPublisher.send(KafkaTopics.TRANSFER_COMPLETED, payload.transactionId(), EventType.TRANSFER_COMPLETED, event.traceId(), new TransferCompletedPayload(payload.transactionId()));
        } catch (NotFoundException | NotActiveException | NotEnoughMoneyException | BusinessException e) {
            // business rejection: report it, never retry it
            log.info("Transfer rejected transactionId={} reason={}", payload.transactionId(), e.getMessage());
            eventPublisher.send(KafkaTopics.TRANSFER_FAILED, payload.transactionId(), EventType.TRANSFER_FAILED, event.traceId(), new TransferFailedPayload(payload.transactionId(), e.getMessage()));
        }
    }

}

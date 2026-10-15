package com.ldang03.transaction.service;

import com.ldang03.common.constants.CommonConstants;
import com.ldang03.common.exception.BusinessException;
import com.ldang03.common.exception.NotFoundException;
import com.ldang03.common.http.RestTemplateConfig;
import com.ldang03.common.kafka.event.EventEnvelope;
import com.ldang03.common.kafka.event.EventJsonMapper;
import com.ldang03.common.kafka.event.EventType;
import com.ldang03.common.kafka.util.KafkaTopics;
import com.ldang03.common.response.ApiResponse;
import com.ldang03.common.trace.TraceSupport;
import com.ldang03.transaction.entity.OutBoxEvent;
import com.ldang03.transaction.entity.Transaction;
import com.ldang03.transaction.entity.TransactionStatus;
import com.ldang03.transaction.model.TransactionRequest;
import com.ldang03.transaction.model.TransferRequestedPayload;
import com.ldang03.transaction.repository.OutboxRepository;
import com.ldang03.transaction.repository.TransactionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.ldang03.common.util.MaskingUtils.maskAccountNumber;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final int OUTBOX_MAX_ATTEMPT = 3;
    private static final int OUTBOX_WAIT_SECONDS = 2;

    private final org.slf4j.Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;

    private final OutboxRepository outboxRepository;

    private final RestTemplateConfig rs;
    private final RestTemplate restTemplate;

    private final TransactionTemplate transactionTemplate;
    private final EventJsonMapper eventJsonMapper;

    private  final TraceSupport traceSupport;

    @Value("${url.user}")
    private String userServiceUrl;


    @Override
    public String processTransaction(TransactionRequest request, String key) {
        log.info("Process transaction : userId : {} , DebtorAccount : {} , CreditorAccount : {} , Amount : {} , IdempotenceKey : {}",
                request.getUserId(), maskAccountNumber(request.getDebtorAccount()), maskAccountNumber(request.getCreditorAccount()),
                request.getAmount(), key);

        if (UUID.fromString(key).version() != 4 && UUID.fromString(key).version() != 7) {
            log.info("Not correct format UUID: {} ", key);
            throw new IllegalArgumentException ("Invalid Idempotency-Key");
        }

        if (request.getDebtorAccount().equalsIgnoreCase(request.getCreditorAccount())) {
            log.info("The same account: {} ", request.getDebtorAccount());
            throw new BusinessException("Cannot transfer money to the same account");
        }

        // 1. same idempotency key already processed (or still processing): return the same result, no HTTP call needed
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            log.info("Duplicate idempotency key {} already exists", key);
            return existing.get().getTransactionId().toString();
        }

        // 2. validate user (HTTP, outside DB transaction)
        UUID userId = parseUserId(request.getUserId());
        if (!checkUserExist(request.getUserId())) {
            log.info("User not found with userId : {} ", request.getUserId());
            throw new NotFoundException("Not found user with user Id : " + request.getUserId());
        }

        // 3. short DB transaction: transaction + outbox are saved together or not at all
        try {
            return transactionTemplate.execute(status -> {
                // save transaction
                Transaction saved = saveTransaction(buildTransaction(request, userId,key));
                // save outbox event
                saveOutBoxEvent(buildOutBoxEvent(saved, request));
                log.info("Completed transaction : userId : {} , DebtorAccount : {} , CreditorAccount : {} , Amount : {} , IdempotenceKey : {}",
                        request.getUserId(), maskAccountNumber(request.getDebtorAccount()), maskAccountNumber(request.getCreditorAccount()),
                        request.getAmount(), key);
                return saved.getTransactionId().toString();
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent request with idempotency key {}", key);
            return transactionRepository.findByIdempotencyKey(key)
                    .orElseThrow(() -> e)
                    .getTransactionId().toString();
        }
    }

    private Transaction buildTransaction(TransactionRequest request, UUID userId, String key) {
        Transaction trs = new Transaction();
        trs.setUserId(userId);
        trs.setDebtorAccount(request.getDebtorAccount());
        trs.setCreditorAccount(request.getCreditorAccount());
        trs.setAmount(request.getAmount());
        trs.setIdempotencyKey(key);
        trs.setTransactionStatus(TransactionStatus.PENDING);
        return trs;
    }

    private OutBoxEvent buildOutBoxEvent(Transaction trs, TransactionRequest request) {
        String transactionId = trs.getTransactionId().toString();

        TransferRequestedPayload payload = new TransferRequestedPayload(
                transactionId,
                trs.getUserId().toString(),
                trs.getDebtorAccount(),
                trs.getCreditorAccount(),
                trs.getAmount());

        EventEnvelope<TransferRequestedPayload> envelope =
                eventJsonMapper.wrap(EventType.TRANSFER_REQUESTED, MDC.get(CommonConstants.TRACE_ID_MDC_KEY), payload);

        OutBoxEvent outbox = new OutBoxEvent();
        outbox.setAggregateId(transactionId);
        outbox.setEventKey(transactionId);
        outbox.setEventType(envelope.eventType());
        outbox.setTopic(KafkaTopics.TRANSFER_REQUESTED);
        outbox.setMaxAttempt(OUTBOX_MAX_ATTEMPT);
        outbox.setWaitTimeInSeconds(OUTBOX_WAIT_SECONDS);
        outbox.setNextRetryAt(LocalDateTime.now());
        outbox.setPayload(eventJsonMapper.toJson(envelope));
        outbox.setTraceContext(traceSupport.capture());
        return outbox;
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid userId");
        }
    }



    @Transactional
    public void updateTransactionStatus(String transactionId, TransactionStatus newStatus, String failedReason) {
        UUID id = UUID.fromString(transactionId);
        int updated = transactionRepository.updateStatusIf(id, newStatus, TransactionStatus.PENDING,
                failedReason, LocalDateTime.now());
        if (updated == 1) {
            log.info("Transaction {} -> {}", transactionId, newStatus);
            return;
        }
        Transaction current = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));
        if (current.getTransactionStatus() == newStatus) {
            log.info("Duplicate result ignored transactionId={} status={}", transactionId, newStatus);
            return;
        }

        log.error("Conflicting result transactionId={} current={} incoming={}",
                transactionId, current.getTransactionStatus(), newStatus);
        throw new RuntimeException("Transaction " + transactionId + " is "
                + current.getTransactionStatus() + ", incoming " + newStatus);

    }


    private Transaction saveTransaction(Transaction trs) {
        return transactionRepository.saveAndFlush(trs);
    }

    private void saveOutBoxEvent(OutBoxEvent outbox) {
        outboxRepository.saveAndFlush(outbox);
    }


    private boolean checkUserExist(String userId) {
        String url = userServiceUrl + "/v1/checkUserExist";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(userId, headers);
        ResponseEntity<ApiResponse<Boolean>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<ApiResponse<Boolean>>() {
                });
        ApiResponse<Boolean> body = response.getBody();
        return body != null && Boolean.TRUE.equals(body.getData());
    }
}

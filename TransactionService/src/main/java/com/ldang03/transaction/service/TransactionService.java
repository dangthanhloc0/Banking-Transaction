package com.ldang03.transaction.service;

import com.ldang03.transaction.entity.TransactionStatus;
import com.ldang03.transaction.model.TransactionRequest;

public interface TransactionService {
    String processTransaction(TransactionRequest request, String key);

    void updateTransactionStatus(String transactionId, TransactionStatus status, String failedReason);
}

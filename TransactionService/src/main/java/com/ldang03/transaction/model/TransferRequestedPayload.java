package com.ldang03.transaction.model;

import java.math.BigDecimal;


public record TransferRequestedPayload(
        String transactionId,
        String userId,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount
) {
}

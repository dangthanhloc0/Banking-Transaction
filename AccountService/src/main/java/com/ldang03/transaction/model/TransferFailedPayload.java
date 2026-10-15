package com.ldang03.transaction.model;

public record TransferFailedPayload(String transactionId, String reason) {}

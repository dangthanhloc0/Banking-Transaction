package com.ldang03.transaction.model;

public record TransferFailedRequest(String transactionId, String reason) {}

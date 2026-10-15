package com.ldang03.common.kafka.util;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String TRANSFER_REQUESTED = "payment.transfer.requested.v1";

    public static final String TRANSFER_COMPLETED = "payment.transfer.completed.v1";

    public static final String TRANSFER_FAILED = "payment.transfer.failed.v1";
}

package com.ldang03.common.kafka.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "common.kafka")
public class KafkaProperties {

    private String bootstrapServers = "localhost:9092";
    private String consumerGroupId = "";
    private String topicPrefix = "";



    private Producer producer = new Producer();
    private Consumer consumer = new Consumer();

    @Data
    public static class Producer {
        private String acks = "all";
        private int retries = 3;
        private boolean enableIdempotence = true;
    }

    @Data
    public static class Consumer {
        private String autoOffsetReset = "earliest";
        private int concurrency = 1;
        private long retryIntervalMs = 2000;
        private int retryMaxAttempts = 2;
    }
}

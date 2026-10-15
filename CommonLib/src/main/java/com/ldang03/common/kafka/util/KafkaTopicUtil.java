package com.ldang03.common.kafka.util;

import com.ldang03.common.kafka.config.KafkaProperties;

public final class KafkaTopicUtil {

    private KafkaTopicUtil() {
    }

    public static String resolveTopic(KafkaProperties properties, String logicalName) {
        String prefix = properties.getTopicPrefix();
        return (prefix == null || prefix.isBlank()) ? logicalName : prefix + logicalName;
    }
}

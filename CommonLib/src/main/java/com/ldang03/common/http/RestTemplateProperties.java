package com.ldang03.common.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "common.resttemplate")
public class RestTemplateProperties {

    private long connectTimeoutMs = 5000;
    private long readTimeoutMs = 30000;
    private long longRunReadTimeoutMs = 36_000_000;
    private boolean loggingEnabled = true;
    private String correlationIdHeader = "X-Correlation-Id";

    private Pool pool = new Pool();

    @Data
    public static class Pool {
        private int maxTotalConnections = 100;
        private int maxConnectionsPerRoute = 20;
    }


}

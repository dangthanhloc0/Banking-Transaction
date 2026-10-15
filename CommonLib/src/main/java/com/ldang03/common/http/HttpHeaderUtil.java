package com.ldang03.common.http;

import java.util.UUID;

public final class HttpHeaderUtil {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private HttpHeaderUtil() {
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}

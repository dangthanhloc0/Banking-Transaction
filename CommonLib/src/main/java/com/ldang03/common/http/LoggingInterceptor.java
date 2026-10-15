package com.ldang03.common.http;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private final RequestResponseLogger logger;

    public LoggingInterceptor(RequestResponseLogger logger) {
        this.logger = logger;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        long start = System.currentTimeMillis();
        String method = request.getMethod().name();

        logger.logRequest(method, request.getURI(), request.getHeaders(), body);

        try {
            ClientHttpResponse response = execution.execute(request, body);
            logger.logResponse(method, request.getURI(), response.getStatusCode(), System.currentTimeMillis() - start);
            return response;

        } catch (IOException ex) {
            logger.logError(method, request.getURI(), ex, System.currentTimeMillis() - start);
            throw ex;
        }
    }
}

package com.ldang03.common.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RequestResponseLogger {

    public void logRequest(String method, URI uri, HttpHeaders headers, byte[] body) {
        log.info(
                "HTTP --> {} {} headers={} body={}",
                method,
                uri,
                headers,
                body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "");
    }

    public void logResponse(String method, URI uri, HttpStatusCode status, long tookMs) {
        log.info(
                "HTTP <-- {} {} status={} took={}ms",
                method,
                uri,
                status,
                tookMs);
    }

    public void logError(String method, URI uri, Exception ex, long tookMs) {
        log.error(
                "HTTP xxx {} {} took={}ms error={}",
                method,
                uri,
                tookMs,
                ex.getMessage());
    }
}
